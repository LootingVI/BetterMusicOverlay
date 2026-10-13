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
    private final LrclibClient client=new LrclibClient(http);
    private final Path folder;
    private final LyricCorrections corrections;
    private volatile LyricCorrections.Correction correction=new LyricCorrections.Correction(0,0,false);
    public record Candidate(long id,String title,String artist,String album,double duration,boolean synced,boolean wordSync){}
    private volatile List<Candidate> candidates=List.of();
    private volatile String candidateStatus="Search alternate recordings with online lookup enabled";
    private volatile String candidateTrack="";
    private String correctionKey="";
    private Future<?> candidatePending;
    private long searchGeneration;
    private String policy="";
    private boolean readerOpen;
    private final List<String> lookupSteps=new ArrayList<>();
    public List<Candidate> candidates(){return candidates;}
    public boolean candidatesFor(Track track){return candidateTrack.equals(LyricCorrections.key(track));}
    public String candidateStatus(){return candidateStatus;}
    public LyricCorrections.Correction correction(){return correction;}
    private volatile String key="";
    private volatile Result result=Result.status("Lyrics are off");
    private volatile long generation;
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
            if(response==null||response.isJsonArray()&&response.getAsJsonArray().isEmpty())
                response=request("search?track_name="+enc(track.title()),ticket);
            var found=new ArrayList<Candidate>();
            if(response!=null&&response.isJsonArray())for(JsonElement row:response.getAsJsonArray()){
                if(!row.isJsonObject())continue;var j=row.getAsJsonObject();var rich=Lyricsfile.parse(LyricsMatch.string(j,"lyricsfile"));boolean synced=!rich.lines().isEmpty()||!LyricsMatch.string(j,"syncedLyrics").isBlank();
                if(!synced&&rich.plain().isBlank()&&LyricsMatch.string(j,"plainLyrics").isBlank())continue;
                try{long recordId=j.get("id").getAsLong();double duration=j.get("duration").getAsDouble();if(recordId<=0||!Double.isFinite(duration))continue;
                    found.add(new Candidate(recordId,LyricsMatch.string(j,"trackName"),LyricsMatch.string(j,"artistName"),LyricsMatch.string(j,"albumName"),duration,synced,rich.wordSync()));}catch(RuntimeException ignored){}
                if(found.size()==20)break;
            }
            found.sort(Comparator.comparing((Candidate c)->!LyricsMatch.normalized(c.title()).equals(LyricsMatch.normalized(track.title())))
                .thenComparing(c->!LyricsMatch.normalized(c.artist()).equals(LyricsMatch.normalized(LyricsMatch.artist(track))))
                .thenComparingDouble(c->Math.abs(c.duration()-track.duration())).thenComparing(c->!c.wordSync()).thenComparing(c->!c.synced()));
            synchronized(this){if(generation==ticket&&searchGeneration==searchTicket&&expected.equals(key)){candidates=List.copyOf(found);candidateStatus=found.isEmpty()?"No alternative lyrics found":"Choose the recording that matches your music";}}
        }catch(InterruptedException e){Thread.currentThread().interrupt();}
        catch(Exception e){synchronized(this){if(generation==ticket&&searchGeneration==searchTicket)candidateStatus=e instanceof LrclibClient.CoolingDown?e.getMessage():"Search unavailable; try again later";}}});
    }
    public String status(){return result.message();}
    public String attribution(){return result.source();}
    public String sourceUrl(){return result.url();}
    public Path folder(){return folder;}
    /** Explicit clipboard export only: no lyrics, artwork, filesystem paths, or listening history. */
    public synchronized String diagnostics(Track track,Settings settings){
        var text=new StringBuilder(LrclibClient.USER_AGENT).append("\nLyrics lookup diagnostics\n");
        text.append("HUD lyrics: ").append(settings.lyrics).append("\nOnline lookup: ").append(settings.onlineLyrics).append('\n');
        if(settings.streamer)return text.append("Track metadata and lookup details hidden by streamer mode\n").toString();
        var metadata=LyricsMatch.metadata(track);
        text.append("Player: ").append(debugValue(track.source())).append("\nRaw title: ").append(debugValue(track.title()))
            .append("\nRaw artist: ").append(debugValue(track.artist())).append("\nRaw album: ").append(debugValue(track.album()))
            .append("\nQuery artist: ").append(debugValue(metadata.artist())).append("\nQuery album: ").append(debugValue(metadata.album()))
            .append("\nDuration: ").append(track.duration()).append("s\n");
        if(!policy.equals(identity(track,settings)))return text.append("Track changed; waiting for lookup\n").toString();
        text.append("Status: ").append(result.message()).append("\nSource: ").append(result.url()).append('\n');
        if(candidatesFor(track))text.append("Find recording: ").append(candidateStatus).append(" ( ").append(candidates.size()).append(" candidates )\n");
        for(String step:lookupSteps)text.append("- ").append(step).append('\n');
        return text.toString();
    }
    private static String debugValue(String value){String safe=value.replaceAll("[\\p{Cntrl}]"," ");return safe.substring(0,Math.min(512,safe.length()));}
    private synchronized void trace(long ticket,String detail){if(generation!=ticket)return;if(lookupSteps.size()==8)lookupSteps.removeFirst();lookupSteps.add(detail);}
    private void cancelSearch(){searchGeneration++;if(candidatePending!=null)candidatePending.cancel(true);candidates=List.of();candidateTrack="";candidateStatus="Search alternate recordings with online lookup enabled";}
    public synchronized void reload(){generation++;key="";lookupSteps.clear();result=Result.status("Reloading lyrics ...");if(pending!=null)pending.cancel(true);cancelSearch();cache.clear();}
    public static String filename(Track t){
        String name=(t.artist()+" - "+t.title()).replaceAll("[<>:\"/\\\\|?*\\p{Cntrl}]","_").replaceAll("[. ]+$","");
        if(name.length()>180)name=name.substring(0,140)+"-"+UUID.nameUUIDFromBytes(name.getBytes(StandardCharsets.UTF_8));
        return name+".lrc";
    }
    private static String identity(Track t,Settings s){return LyricCorrections.key(t)+"\n"+t.source()+"\n"+s.onlineLyrics;}
    public synchronized void update(Track t,Settings s){
        if(closed)return;
        String nextPolicy=identity(t,s);
        if(!policy.equals(nextPolicy)){
            policy=nextPolicy;generation++;key="";lookupSteps.clear();if(pending!=null)pending.cancel(true);cancelSearch();result=Result.status("Looking for lyrics ...");
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
            if(saved!=null&&saved.expires()>System.nanoTime()){trace(ticket,"Loaded cached result; Reload lyrics forces a fresh request");finish(ticket,saved.result());return;}
            String query="track_name="+enc(track.title())+"&artist_name="+enc(LyricsMatch.artist(track));
            String exact=query;
            String album=LyricsMatch.metadata(track).album();
            if(!album.isBlank())exact+="&album_name="+enc(album);
            if(track.duration()>=1&&track.duration()<=3600)exact+="&duration="+track.duration();
            JsonElement response=request("get?"+exact,ticket);
            JsonObject chosen=response!=null&&response.isJsonObject()&&LyricsMatch.matches(response.getAsJsonObject(),track,false)?response.getAsJsonObject():null;
            boolean instrumental=chosen!=null&&chosen.has("instrumental")&&chosen.get("instrumental").getAsBoolean();
            if(!instrumental&&(chosen==null||LyricsMatch.string(chosen,"syncedLyrics").isBlank()&&Lyricsfile.parse(LyricsMatch.string(chosen,"lyricsfile")).lines().isEmpty())){
                try{JsonElement search=request("search?"+query,ticket);
                    if(search!=null&&search.isJsonArray()){JsonObject alternative=LyricsMatch.best(search.getAsJsonArray(),track);if(alternative!=null)chosen=alternative;}
                }catch(InterruptedException e){throw e;}catch(Exception e){if(chosen==null)throw e;}
            }
            Result loaded;
            if(chosen==null)loaded=Result.status("No safe automatic match · use Find recording");
            else loaded=record(chosen,false);
            check(ticket);
            synchronized(cache){if(cache.size()>=30)cache.remove(cache.keySet().iterator().next());cache.put(id,new Cached(loaded,System.nanoTime()+(loaded.lines().isEmpty()?60_000_000_000L:1_800_000_000_000L)));}
            finish(ticket,loaded);
        }catch(InterruptedException ignored){Thread.currentThread().interrupt();}
        catch(Exception e){finish(ticket,Result.status(e instanceof LrclibClient.CoolingDown?e.getMessage():"Lyrics unavailable · Reload to retry"));BetterMusic.LOG.debug("Lyrics lookup failed",e);}
    }
    private JsonElement request(String path,long ticket)throws Exception{
        String stage=path.startsWith("get/")?"Saved recording":path.startsWith("get?")?"Exact lookup":path.contains("artist_name=")?"Title + artist search":"Title-only candidate search";
        trace(ticket,stage+": requesting");
        try{
            check(ticket);JsonElement response=provider!=null?provider.get(path):client.get(path,()->check(ticket));check(ticket);
            trace(ticket,stage+": "+(response==null?"no record":response.isJsonArray()?response.getAsJsonArray().size()+" records returned":"record returned"));return response;
        }catch(InterruptedException e){throw e;}
        catch(Exception e){trace(ticket,stage+": "+failureReason(e));throw e;}
    }
    private static String failureReason(Exception e){
        if(e instanceof LrclibClient.CoolingDown)return e.getMessage();
        if(e instanceof HttpTimeoutException)return "request timed out";
        if(e instanceof javax.net.ssl.SSLException)return "TLS connection failed";
        if(e instanceof java.io.IOException&&e.getMessage()!=null&&e.getMessage().startsWith("LRCLIB "))return e.getMessage();
        if(e instanceof JsonParseException)return "invalid JSON response";
        return "request failed ("+e.getClass().getSimpleName()+")";
    }
    private void check(long ticket)throws InterruptedException{if(closed||generation!=ticket||Thread.currentThread().isInterrupted())throw new InterruptedException();}
    private synchronized void finish(long ticket,Result value){if(!closed&&generation==ticket)result=value;}
    private static String enc(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private static List<String> plain(String value){return value.lines().map(String::strip).filter(line->!line.isBlank()&&!line.matches("\\[[a-zA-Z]+:.*]" )).limit(5000).toList();}
    private static Result record(JsonObject chosen,boolean manual){
        String url="";try{long id=chosen.get("id").getAsLong();if(id>0)url="https://lrclib.net/api/get/"+id;}catch(RuntimeException ignored){}
        var rich=Lyricsfile.parse(LyricsMatch.string(chosen,"lyricsfile"));
        if(rich.instrumental()||chosen.has("instrumental")&&!chosen.get("instrumental").isJsonNull()&&chosen.get("instrumental").getAsBoolean())return new Result(List.of(),"LRCLIB · instrumental track","LRCLIB · INSTRUMENTAL",url);
        var lines=rich.lines().isEmpty()?Lyrics.parse(LyricsMatch.string(chosen,"syncedLyrics")):rich.lines();
        boolean words=lines.stream().anyMatch(line->!line.words().isEmpty());
        if(!lines.isEmpty())return new Result(lines,manual?"LRCLIB · manually selected recording":words?"LRCLIB · word timing available":"LRCLIB · recording matched",words?"LRCLIB · WORD SYNC":manual?"LRCLIB · MANUAL MATCH":"LRCLIB · LINE SYNC",url);
        var plain=plain(rich.plain().isBlank()?LyricsMatch.string(chosen,"plainLyrics"):rich.plain());
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
