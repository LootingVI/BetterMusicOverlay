package dev.bettermusic;

import com.google.gson.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

public final class LyricsService implements AutoCloseable {
    private record Result(List<Lyrics.Line> lines,List<String> plain,String message,String source,String url) {
        Result(List<Lyrics.Line> lines,String message,String source,String url){this(lines,List.of(),message,source,url);}
        static Result status(String message){return new Result(List.of(),message,"","");}
    }
    public record Transcript(List<Lyrics.Line> lines,List<String> plain,String status,String source,double position,int current){}
    @FunctionalInterface interface Provider {JsonElement get(String path)throws Exception;}
    private final Provider provider;
    private record Cached(Result result,long expires){}
    private final ExecutorService worker=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"BetterMusic-lyrics");t.setDaemon(true);return t;});
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final Path folder;
    private final LyricCorrections corrections;
    private volatile LyricCorrections.Correction correction=new LyricCorrections.Correction(0,0,false);
    public record Candidate(long id,String title,String artist,String album,double duration,boolean synced){}
    private volatile List<Candidate> candidates=List.of();
    private volatile String candidateStatus="Search alternate recordings with online lookup enabled";
    private volatile String candidateTrack="";
    private String correctionKey="";
    private Future<?> candidatePending;
    private long searchGeneration;
    private String policy="";
    private boolean readerOpen;
    public List<Candidate> candidates(){return candidates;}
    public boolean candidatesFor(Track track){return candidateTrack.equals(LyricCorrections.key(track));}
    public String candidateStatus(){return candidateStatus;}
    public LyricCorrections.Correction correction(){return correction;}
    private volatile String key="";
    private volatile Result result=Result.status("Lyrics are off");
    private volatile long generation,nextAllowed;
    private volatile boolean closed;
    private Future<?> pending;
    private final Map<String,Cached> cache=Collections.synchronizedMap(new LinkedHashMap<>());
    public LyricsService(Path folder){this(folder,null);}
    LyricsService(Path folder,Provider provider){this.folder=folder.toAbsolutePath().normalize();this.provider=provider;corrections=new LyricCorrections(this.folder.resolve("corrections"));try{Files.createDirectories(this.folder);}catch(Exception e){BetterMusic.LOG.warn("Cannot create lyrics folder",e);}}
    public synchronized void readerOpen(boolean value){readerOpen=value;}
    public synchronized void saveCorrection(Track t,double offset,long recording,boolean explicit)throws java.io.IOException{
        var value=new LyricCorrections.Correction(Settings.clamp(offset,-30,30),Math.max(0,recording),explicit);corrections.save(t,value);
        if(LyricCorrections.key(t).equals(correctionKey)){
            boolean reload=correction.recording()!=value.recording();correction=value;if(reload)reload();
        }
    }
    public synchronized void nudge(Track t,double seconds)throws java.io.IOException{
        var current=corrections.get(t);saveCorrection(t,current.offset()+seconds,current.recording(),current.explicit());
    }
    public synchronized void resetCorrection(Track t)throws java.io.IOException{corrections.reset(t);correction=new LyricCorrections.Correction(0,0,false);reload();}
    public synchronized void searchCandidates(Track track,Settings settings){
        update(track,settings);cancelSearch();
        if(closed||!settings.onlineLyrics||!track.present()){candidateStatus="Enable online lookup and start a track first";return;}
        long ticket=generation,searchTicket=searchGeneration;String expected=key;candidates=List.of();candidateTrack=LyricCorrections.key(track);candidateStatus="Searching LRCLIB ...";
        candidatePending=worker.submit(()->{try{
            JsonElement response=request("search?track_name="+enc(track.title())+"&artist_name="+enc(LyricsMatch.artist(track)),ticket);
            var found=new ArrayList<Candidate>();
            if(response!=null&&response.isJsonArray())for(JsonElement row:response.getAsJsonArray()){
                if(!row.isJsonObject())continue;var j=row.getAsJsonObject();boolean synced=!LyricsMatch.string(j,"syncedLyrics").isBlank();
                if(!synced&&LyricsMatch.string(j,"plainLyrics").isBlank())continue;
                try{long recordId=j.get("id").getAsLong();double duration=j.get("duration").getAsDouble();if(recordId<=0||!Double.isFinite(duration))continue;
                    found.add(new Candidate(recordId,LyricsMatch.string(j,"trackName"),LyricsMatch.string(j,"artistName"),LyricsMatch.string(j,"albumName"),duration,synced));}catch(RuntimeException ignored){}
                if(found.size()==30)break;
            }
            found.sort(Comparator.comparing((Candidate c)->!c.synced()).thenComparingDouble(c->Math.abs(c.duration()-track.duration())));
            synchronized(this){if(generation==ticket&&searchGeneration==searchTicket&&expected.equals(key)){candidates=List.copyOf(found);candidateStatus=found.isEmpty()?"No alternative lyrics found":"Choose the recording that matches your music";}}
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){synchronized(this){if(generation==ticket&&searchGeneration==searchTicket)candidateStatus="Search unavailable; try again later";}}});
    }
    public String status(){return result.message();}
    public String attribution(){return result.source();}
    public String sourceUrl(){return result.url();}
    public Path folder(){return folder;}
    private void cancelSearch(){searchGeneration++;if(candidatePending!=null)candidatePending.cancel(true);candidates=List.of();candidateTrack="";candidateStatus="Search alternate recordings with online lookup enabled";}
    public synchronized void reload(){generation++;key="";result=Result.status("Reloading lyrics ...");if(pending!=null)pending.cancel(true);cancelSearch();cache.clear();}
    public static String filename(Track t){
        String name=(t.artist()+" - "+t.title()).replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("[. ]+$","");
        if(name.length()>180)name=name.substring(0,140)+"-"+UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return name+".lrc";
    }
    private static String identity(Track t,Settings s){return LyricCorrections.key(t)+"\n"+s.onlineLyrics;}
    public synchronized void update(Track t,Settings s){
        if(closed)return;
        String nextPolicy=LyricCorrections.key(t)+":"+s.onlineLyrics;
        if(!policy.equals(nextPolicy)){
            policy=nextPolicy;generation++;key="";if(pending!=null)pending.cancel(true);cancelSearch();result=Result.status("Looking for lyrics ...");
        }
        String currentCorrection=LyricCorrections.key(t);
        if(!currentCorrection.equals(correctionKey)){
            generation++;
            correctionKey=currentCorrection;correction=corrections.get(t);candidates=List.of();candidateTrack="";
            if(candidatePending!=null)candidatePending.cancel(true);
        }
        if((!s.lyrics&&!readerOpen)||!t.present()){
            if(!key.isEmpty()){generation++;if(pending!=null)pending.cancel(true);key="";}
            result=Result.status(s.lyrics?"Waiting for a track":"Lyrics are off");return;
        }
        String id=identity(t,s);if(id.equals(key))return;
        if(pending!=null)pending.cancel(true);
        long ticket=++generation;key=id;candidates=List.of();correction=corrections.get(t);result=Result.status("Looking for lyrics ...");boolean online=s.onlineLyrics;
        long selected=correction.recording();pending=worker.submit(()->load(t,id,ticket,online,selected));
    }
    private void load(Track track,String id,long ticket,boolean online,long selected){
        try {
            check(ticket);
            Path local=folder.resolve(filename(track)).normalize();
            if(!Files.isRegularFile(local))local=folder.resolve(filename(track).replaceFirst("\\.lrc$",".txt"));
            if(local.getParent().equals(folder)&&Files.isRegularFile(local)){
                if(Files.size(local)>1_000_000){finish(ticket,Result.status("Local LRC exceeds 1 MB"));return;}
                String content=Files.readString(local);var lines=Lyrics.parse(content);
                boolean words=lines.stream().anyMatch(l->!l.words().isEmpty());
                if(lines.isEmpty())finish(ticket,new Result(List.of(),plain(content),"Local lyrics · read without sync","LOCAL · PLAIN TEXT",""));
                else finish(ticket,new Result(lines,words?"Local Enhanced LRC · word sync":"Local LRC · line sync",words?"LOCAL LRC · WORD SYNC":"LOCAL LRC · LINE SYNC",""));return;
            }
            if(!online){finish(ticket,Result.status("Add a local LRC or enable online lookup"));return;}
            if(selected>0){
                JsonElement chosen=request("get/"+selected,ticket);
                if(chosen==null){finish(ticket,Result.status("Saved recording unavailable · reset correction"));return;}
                finish(ticket,record(chosen.getAsJsonObject(),true));return;
            }
            Cached saved=cache.get(id);
            if(saved!=null&&saved.expires()>System.nanoTime()){finish(ticket,saved.result());return;}
            String query="track_name="+enc(track.title())+"&artist_name="+enc(LyricsMatch.artist(track));
            if(!track.album().isBlank())query+="&album_name="+enc(track.album());
            String exact=query;
            if(track.duration()>=1&&track.duration()<=3600)exact+="&duration="+Math.round(track.duration());
            JsonElement response=request("get?"+exact,ticket);
            JsonObject chosen=response!=null&&response.isJsonObject()&&LyricsMatch.matches(response.getAsJsonObject(),track,false)?response.getAsJsonObject():null;
            boolean instrumental=chosen!=null&&chosen.has("instrumental")&&chosen.get("instrumental").getAsBoolean();
            if(!instrumental&&(chosen==null||LyricsMatch.string(chosen,"syncedLyrics").isBlank())&&track.duration()>0){
                try{JsonElement search=request("search?"+query,ticket);
                    if(search!=null&&search.isJsonArray()){JsonObject alternative=LyricsMatch.best(search.getAsJsonArray(),track);if(alternative!=null)chosen=alternative;}
                }catch(InterruptedException e){throw e;}catch(Exception e){if(chosen==null)throw e;}
            }
            Result loaded;
            if(chosen==null)loaded=Result.status("No matching lyrics found");
            else loaded=record(chosen,false);
            check(ticket);
            synchronized(cache){if(cache.size()>=30)cache.remove(cache.keySet().iterator().next());cache.put(id,new Cached(loaded,System.nanoTime()+(loaded.lines().isEmpty()?60_000_000_000L:1_800_000_000_000L)));}
            finish(ticket,loaded);
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(Exception e){finish(ticket,Result.status(e instanceof RateLimited?"LRCLIB rate limit · wait before reloading":"Lyrics unavailable · Reload to retry"));BetterMusic.LOG.debug("Lyrics lookup failed",e);}
    }
    private JsonElement request(String path,long ticket)throws Exception{
        if(provider!=null){check(ticket);JsonElement response=provider.get(path);check(ticket);return response;}
        for(int attempt=0;attempt<2;attempt++){
            check(ticket);if(System.nanoTime()<nextAllowed)throw new RateLimited();
            var request=HttpRequest.newBuilder(URI.create("https://lrclib.net/api/"+path)).timeout(Duration.ofSeconds(8)).header("User-Agent","BetterMusicOverlay/1.3.0").header("Accept","application/json").GET().build();
            var response=http.send(request,info->new BoundedBody());check(ticket);
            if(response.statusCode()==404)return null;
            if(response.statusCode()==429){long seconds=60;try{seconds=Long.parseLong(response.headers().firstValue("Retry-After").orElse("60"));}catch(NumberFormatException ignored){}nextAllowed=System.nanoTime()+Math.max(1,Math.min(300,seconds))*1_000_000_000L;throw new RateLimited();}
            if(response.statusCode()>=500&&attempt==0){Thread.sleep(750);continue;}
            if(response.statusCode()!=200)throw new java.io.IOException("LRCLIB HTTP "+response.statusCode());
            return JsonParser.parseString(response.body());
        }
        throw new java.io.IOException("LRCLIB unavailable");
    }
    private static final class RateLimited extends java.io.IOException{}
    private void check(long ticket)throws InterruptedException{if(closed||generation!=ticket||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private synchronized void finish(long ticket,Result value){if(!closed&&generation==ticket)result=value;}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static List<String> plain(String value){return value.lines().map(String::strip).filter(line->!line.isBlank()&&!line.matches("\\[[a-zA-Z]+:.*]" )).limit(5000).toList();}
    private static Result record(JsonObject chosen,boolean manual){
        String url="";try{long id=chosen.get("id").getAsLong();if(id>0)url="https://lrclib.net/api/get/"+id;}catch(RuntimeException ignored){}
        if(chosen.has("instrumental")&&chosen.get("instrumental").getAsBoolean())return new Result(List.of(),"LRCLIB · instrumental track","LRCLIB · INSTRUMENTAL",url);
        var lines=Lyrics.parse(LyricsMatch.string(chosen,"syncedLyrics"));
        if(!lines.isEmpty())return new Result(lines,manual?"LRCLIB · manually selected recording":"LRCLIB · recording matched",manual?"LRCLIB · MANUAL MATCH":"LRCLIB · LINE SYNC",url);
        var plain=plain(LyricsMatch.string(chosen,"plainLyrics"));
        return plain.isEmpty()?new Result(List.of(),"This recording has no lyrics","LRCLIB",url):new Result(List.of(),plain,"Plain lyrics available · open Lyrics reader","LRCLIB · PLAIN TEXT",url);
    }
    public synchronized Transcript transcript(Track track,Settings s){
        if(Experience.hidden(track,s)||s.streamer&&(s.hideLyrics||correction.explicit()))return new Transcript(List.of(),List.of(),"Lyrics hidden by streamer privacy","",0,-1);
        if(!identity(track,s).equals(key))return new Transcript(List.of(),List.of(),"Waiting for lyrics ...","",0,-1);
        Result r=result;double time=track.elapsed()+s.lyricsOffset+correction.offset();return new Transcript(r.lines(),r.plain(),r.message(),r.source(),time,Lyrics.current(r.lines(),time));
    }
    public List<String> visible(Track track,Settings s){
        if(!s.lyrics||s.streamer&&(s.hideLyrics||correction.explicit()))return List.of();
        if(!identity(track,s).equals(key))return List.of("Looking for lyrics ...");
        Result current=result;List<Lyrics.Line> snapshot=current.lines();
        if(snapshot.isEmpty())return List.of(current.message());
        int index=Lyrics.current(snapshot,track.elapsed()+s.lyricsOffset+correction.offset());
        double now=track.elapsed()+s.lyricsOffset+correction.offset();
        if(index<0)return List.of("Lyrics in "+(int)Math.ceil(snapshot.get(0).time()-now)+"s",snapshot.get(0).text());
        if(Double.isFinite(snapshot.get(index).end())&&now>=snapshot.get(index).end())return index+1<snapshot.size()?List.of("♪ Instrumental",snapshot.get(index+1).text()):List.of("♪");
        var visible=new ArrayList<String>();
        for(int i=index;i<Math.min(snapshot.size(),index+s.lyricsLines);i++)visible.add(snapshot.get(i).text().isBlank()?"♪":snapshot.get(i).text());
        return List.copyOf(visible);
    }
    public String highlighted(Track track,Settings s){
        if(!s.lyrics||!identity(track,s).equals(key)||s.streamer&&(s.hideLyrics||correction.explicit()))return "";
        var lines=result.lines();double time=track.elapsed()+s.lyricsOffset+correction.offset();int index=Lyrics.current(lines,time);
        return index<0||Double.isFinite(lines.get(index).end())&&time>=lines.get(index).end()?"":lines.get(index).highlighted(time);
    }
    @Override public synchronized void close(){closed=true;generation++;if(pending!=null)pending.cancel(true);if(candidatePending!=null)candidatePending.cancel(true);worker.shutdownNow();http.shutdownNow();}
}
