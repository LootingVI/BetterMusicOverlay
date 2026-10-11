package dev.bettermusic;

import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.*;

/** One persistent native bridge, isolated from the render thread. No network or account credentials. */
public final class MediaService implements AutoCloseable {
    private final ScheduledExecutorService io = Executors.newSingleThreadScheduledExecutor(r -> { Thread t=new Thread(r,"BetterMusic-media");t.setDaemon(true);return t; });
    private volatile Track track = Track.EMPTY;
    private volatile String status = "Connecting to media sessions ...";
    private volatile Process process;
    private volatile boolean closed;
    private volatile long lastUpdate;
    private BufferedWriter input;
    private final Path folder;
    public record Session(String id,boolean playing){}
    private volatile java.util.List<Session> sessions=java.util.List.of();
    public java.util.List<Session> sessions(){return System.nanoTime()-lastUpdate>8_000_000_000L?java.util.List.of():sessions;}
    public MediaService(Path folder) { this.folder=folder; }
    public Track track() { return System.nanoTime()-lastUpdate > 8_000_000_000L ? Track.EMPTY : track; }
    public String status() { return status; }
    public void start() {
        if (!System.getProperty("os.name").toLowerCase().contains("windows")) {
            status="This release requires Windows 10/11."; return;
        }
        io.scheduleWithFixedDelay(this::poll,0,1,TimeUnit.SECONDS);
    }
    private void poll() {
        if (closed) return;
        try {
            if (process == null || !process.isAlive()) launch();
            JsonObject request=new JsonObject();request.addProperty("action","poll");request.addProperty("preferred",BetterMusic.settings.preferred);
            send(request);
            if (System.nanoTime()-lastUpdate>10_000_000_000L) { status="Media bridge timed out; reconnecting ..."; process.destroyForcibly(); }
        } catch (Exception e) { status="Media bridge unavailable"; if(process!=null)process.destroyForcibly(); BetterMusic.LOG.debug("Media bridge retry",e); }
    }
    private synchronized void launch() throws IOException {
        if(closed)throw new IOException("Media service is closed");
        Files.createDirectories(folder);
        Path script=folder.resolve("windows-media.ps1");
        try(var dll=MediaService.class.getResourceAsStream("/native/ThumbnailReader.dll")) {
            if(dll==null)throw new IOException("Missing thumbnail reader");
            Files.copy(dll,folder.resolve("ThumbnailReader.dll"),StandardCopyOption.REPLACE_EXISTING);
        }
        try(var resource=MediaService.class.getResourceAsStream("/native/windows-media.ps1")) {
            if(resource==null)throw new IOException("Missing media bridge");
            Files.copy(resource,script,StandardCopyOption.REPLACE_EXISTING);
        }
        process=new ProcessBuilder("powershell.exe","-Mta","-NoLogo","-NoProfile","-NonInteractive","-WindowStyle","Hidden","-ExecutionPolicy","Bypass","-File",script.toString())
            .redirectError(ProcessBuilder.Redirect.appendTo(folder.resolve("bridge.log").toFile())).start();
        lastUpdate=System.nanoTime();
        input=new BufferedWriter(new OutputStreamWriter(process.getOutputStream(),StandardCharsets.UTF_8));
        Process current=process;
        Thread reader=new Thread(() -> {
            try(var lines=new BufferedReader(new InputStreamReader(current.getInputStream(),StandardCharsets.UTF_8))) {
                String line;
                while((line=lines.readLine())!=null && !closed && current==process) {
                    if(line.length()>3_000_000)continue;
                    try {
                        JsonObject j=JsonParser.parseString(line).getAsJsonObject();
                        if(j.has("sessions")){
                            var list=new java.util.ArrayList<Session>();
                            for(var row:j.getAsJsonArray("sessions")){var session=row.getAsJsonObject();list.add(new Session(str(session,"id"),session.get("playing").getAsBoolean()));}
                            sessions=java.util.List.copyOf(list);
                        }
                        if(j.has("error")) { status=j.get("error").getAsString();if(!j.has("control")){track=Track.EMPTY;sessions=java.util.List.of();}continue; }
                        track=new Track(str(j,"title"),str(j,"artist"),str(j,"source"),j.has("playing")&&j.get("playing").getAsBoolean(),num(j,"position"),num(j,"duration"),str(j,"cover"),System.nanoTime(),str(j,"album"));lastUpdate=System.nanoTime();
                        status=track.present()?"Connected · "+track.source():"Start music in a media app";
                    } catch(RuntimeException e) { BetterMusic.LOG.debug("Ignoring invalid bridge response",e); }
                }
            } catch(IOException ignored) { }
        },"BetterMusic-reader");reader.setDaemon(true);reader.start();
    }
    private static String str(JsonObject j,String k) { return j.has(k)&&!j.get(k).isJsonNull()?j.get(k).getAsString():""; }
    private static double num(JsonObject j,String k) { double n=j.has(k)?j.get(k).getAsDouble():0;return Double.isFinite(n)?Math.max(0,n):0; }
    private void send(JsonObject request) throws IOException { input.write(request.toString());input.newLine();input.flush(); }
    public void command(String action) {
        if(!java.util.List.of("play","next","previous").contains(action)||closed)return;
        String target=track().source();if(target.isBlank())return;
        io.execute(() -> { try { if(process!=null&&process.isAlive()) { JsonObject j=new JsonObject();j.addProperty("action",action);j.addProperty("preferred",target);send(j); } } catch(IOException e) { status="Playback control failed"; } });
    }
    public void seek(double fraction) {
        Track current=track();
        if(closed||!current.present()||current.duration()<=0||!Double.isFinite(fraction))return;
        seekSeconds(Settings.clamp(fraction,0,1)*current.duration(),current);
    }
    public void seekSeconds(double seconds,Track current){
        if(closed||!current.present()||!Double.isFinite(seconds)||!LyricCorrections.key(current).equals(LyricCorrections.key(track())))return;
        double target=current.duration()>0?Settings.clamp(seconds,0,current.duration()):Math.max(0,seconds);
        io.execute(()->{try{if(process!=null&&process.isAlive()){
            JsonObject j=new JsonObject();j.addProperty("action","seek");j.addProperty("preferred",current.source());j.addProperty("seconds",target);send(j);
        }}catch(IOException e){status="Seeking failed";}});
    }
    @Override public synchronized void close() { closed=true;io.shutdownNow();if(process!=null)process.destroyForcibly(); }
}
