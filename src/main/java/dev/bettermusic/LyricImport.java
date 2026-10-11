package dev.bettermusic;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.io.IOException;

/** Imports are explicit local writes, with a backup of the previous file. */
final class LyricImport {
    record Preview(String text,int lines,boolean timed){}
    static Preview inspect(String text){
        text=text.replace("\ufeff","").strip();
        if(text.isEmpty())throw new IllegalArgumentException("The lyrics are empty.");
        if(text.length()>1_000_000||text.getBytes(StandardCharsets.UTF_8).length>1_000_000)throw new IllegalArgumentException("Use a lyrics file smaller than 1 MB.");
        if(text.indexOf('\0')>=0)throw new IllegalArgumentException("Use a UTF-8 text or LRC file.");
        var parsed=Lyrics.parse(text);return new Preview(text,parsed.isEmpty()?(int)text.lines().count():parsed.size(),!parsed.isEmpty());
    }
    static Preview read(Path file)throws IOException{
        String name=file.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
        if(!name.endsWith(".lrc")&&!name.endsWith(".txt"))throw new IllegalArgumentException("Choose a .lrc or .txt file.");
        if(Files.size(file)>1_000_000)throw new IllegalArgumentException("Use a lyrics file smaller than 1 MB.");
        return inspect(Files.readString(file));
    }
    static Path save(Path folder,Track track,Preview preview)throws IOException{
        if(!track.present())throw new IllegalArgumentException("Start a track before importing lyrics.");
        Preview checked=inspect(preview.text());folder=folder.toAbsolutePath().normalize();Files.createDirectories(folder);
        String base=LyricsService.filename(track);Path target=folder.resolve(checked.timed()?base:base.replaceFirst("\\.lrc$",".txt"));
        if(!target.getParent().equals(folder))throw new IOException("Invalid lyrics filename");
        Path temp=Files.createTempFile(folder,"import-",".tmp");
        try{Files.writeString(temp,checked.text());if(Files.exists(target))Files.copy(target,target.resolveSibling(target.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            // LRC has priority: a plain-text replacement must not leave an older LRC active.
            Path timed=folder.resolve(base);if(!checked.timed()&&Files.exists(timed))Files.move(timed,timed.resolveSibling(timed.getFileName()+".bak"),StandardCopyOption.REPLACE_EXISTING);
            try{Files.move(temp,target,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);}catch(AtomicMoveNotSupportedException e){Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);}
        }finally{Files.deleteIfExists(temp);}return target;
    }
}
