package dev.bettermusic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.nio.file.*;
import java.io.IOException;

public final class Settings implements Cloneable {
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();
    public boolean enabled = true, hideIdle = true, artwork = true, progress = true, animation = true, provider = true;
    public double x = 0.03, y = 0.08, scale = 1, opacity = 0.92;
    public int width = 310, radius = 12, accent = 0xAE7BDC, background = 0x17101F, foreground = 0xF5F3FF;
    public boolean lyrics = false, onlineLyrics = false;
    public double lyricsOffset = 0;
    public int lyricsLines = 2;
    public boolean adaptiveColor = false, ambient = true, vinyl = false, transitions = true, lyricFocus = false;
    public String layout = "STUDIO", theme = "MIDNIGHT", preferred = "";
    public boolean smartHud=false, combatFade=true, duckMusic=false, streamer=false, hideTitles=true, hideLyrics=true, wordHighlight=true, snap=true;
    public double expandSeconds=8, duckLevel=.2;
    public String hiddenPlayers="";
    public String renderQuality="BALANCED";
    public String lyricMotion="FLOW";
    public boolean ambientMotion=true,artworkOrbit=true;
    public double motionIntensity=.75;
    public Settings copy(){try{return (Settings)super.clone();}catch(CloneNotSupportedException e){throw new AssertionError(e);}}
    /** An explicit design choice must not be silently replaced by automatic collapse. */
    public void selectLayout(String value){layout=value;smartHud=false;validate();}
    public void validate() {
        x = clamp(x,0,1); y = clamp(y,0,1); scale = clamp(scale,0.6,2); opacity = clamp(opacity,0.15,1);
        width = Math.max(220,Math.min(420,width)); radius = Math.max(0,Math.min(20,radius));
        if (!java.util.List.of("STUDIO","COMPACT","MINIMAL","AURORA").contains(layout == null ? "" : layout)) layout = "STUDIO";
        if (theme == null) theme = "MIDNIGHT";
        if (preferred == null) preferred = "";
        if(hiddenPlayers==null)hiddenPlayers="";
        if(!java.util.List.of("QUALITY","BALANCED","SAVER").contains(renderQuality==null?"":renderQuality))renderQuality="BALANCED";
        if(!java.util.List.of("FLOW","FADE","OFF").contains(lyricMotion==null?"":lyricMotion))lyricMotion="FLOW";
        motionIntensity=clamp(motionIntensity,.25,1);
        expandSeconds=clamp(expandSeconds,2,30);duckLevel=clamp(duckLevel,0,1);
        accent &= 0xffffff; background &= 0xffffff; foreground &= 0xffffff;
        lyricsOffset=clamp(lyricsOffset,-10,10);lyricsLines=Math.max(1,Math.min(3,lyricsLines));
    }
    static double clamp(double v,double min,double max) { return Double.isFinite(v) ? Math.max(min,Math.min(max,v)) : min; }
    public static Settings load(Path path) {
        if (Files.exists(path)) try (var r = Files.newBufferedReader(path)) {
            Settings s = JSON.fromJson(r,Settings.class);
            if (s != null) { s.validate(); return s; }
        } catch (Exception e) { BetterMusic.LOG.warn("Cannot load overlay configuration; using defaults",e); }
        return new Settings();
    }
    public void save(Path path) {
        validate();
        try {
            Files.createDirectories(path.getParent());
            Path temp = path.resolveSibling(path.getFileName()+".tmp");
            Files.writeString(temp,JSON.toJson(this));
            try { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { BetterMusic.LOG.error("Cannot save overlay settings",e); }
    }
    public void preset(String name) {
        theme = name;
        switch (name) {
            case "MATCHA" -> { background=0x101C19; accent=0xB9F3B0; foreground=0xEDF9ED; }
            case "ROSE" -> { background=0x24151F; accent=0xFFA8C7; foreground=0xFFF0F5; }
            case "PAPER" -> { background=0xF1EEE7; accent=0x6551BD; foreground=0x242131; }
            case "OCEAN" -> { background=0x0C1C29; accent=0x79DFFF; foreground=0xEDF8FF; }
            default -> { theme="MIDNIGHT"; background=0x17101F; accent=0xAE7BDC; foreground=0xF5F3FF; }
        }
    }
    public void scene(String name) {
        smartHud=false;
        preset("MIDNIGHT");artwork=true;progress=true;animation=true;transitions=true;ambient=true;
        width=310;radius=12;opacity=.92;vinyl=false;lyricFocus=false;lyrics=false;
        switch(name) {
            case "AURORA" -> { layout="AURORA";adaptiveColor=true;width=350;radius=18; }
            case "LYRIC LOUNGE" -> {layout="AURORA";adaptiveColor=true;lyrics=true;lyricsLines=3;lyricFocus=true;width=350;radius=18;}
            case "VINYL" -> {layout="AURORA";adaptiveColor=true;vinyl=true;width=350;radius=18;}
            default -> {layout="STUDIO";adaptiveColor=false;lyrics=false;}
        }
    }
}
