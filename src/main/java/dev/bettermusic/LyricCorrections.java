package dev.bettermusic;

import com.google.gson.Gson;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/** Only explicitly saved corrections persist; this is not a listening history. */
public final class LyricCorrections {
    public record Correction(double offset,long recording,boolean explicit){}
    private final Path folder;
    public LyricCorrections(Path folder){this.folder=folder;}
    public static String key(Track t){return UUID.nameUUIDFromBytes((t.artist()+"\n"+t.title()+"\n"+t.album()+"\n"+Math.round(t.duration())).getBytes(StandardCharsets.UTF_8)).toString();}
    public Correction get(Track t){
        try{Path p=folder.resolve(key(t)+".json");if(Files.size(p)>4096)return new Correction(0,0,false);Correction c=new Gson().fromJson(Files.readString(p),Correction.class);return new Correction(Settings.clamp(c.offset(),-30,30),Math.max(0,c.recording()),c.explicit());}
        catch(Exception e){return new Correction(0,0,false);}
    }
    public void save(Track t,Correction value)throws java.io.IOException{
        if(!t.present())throw new java.io.IOException("Start a track first");
        Files.createDirectories(folder);Path file=folder.resolve(key(t)+".json"),tmp=folder.resolve(key(t)+".tmp");
        Files.writeString(tmp,new Gson().toJson(value));Files.move(tmp,file,StandardCopyOption.REPLACE_EXISTING);
    }
    public void reset(Track t)throws java.io.IOException{Files.deleteIfExists(folder.resolve(key(t)+".json"));}
}
