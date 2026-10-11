package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LyricImportTest {
    @TempDir Path folder;
    private Track track(){return new Track("Original","Example","TEST",false,0,30,"",System.nanoTime());}
    @Test void replacingTimedLyricsWithPlainTextKeepsBackupAndRemovesStalePriority()throws Exception{
        var first=LyricImport.inspect("[00:01]Original words");Path timed=LyricImport.save(folder,track(),first);assertTrue(first.timed());
        Path plain=LyricImport.save(folder,track(),LyricImport.inspect("New original text"));
        assertFalse(Files.exists(timed));assertEquals("[00:01]Original words",Files.readString(timed.resolveSibling(timed.getFileName()+".bak")));
        assertEquals("New original text",Files.readString(plain));
        LyricImport.save(folder,track(),LyricImport.inspect("Replacement"));assertEquals("New original text",Files.readString(plain.resolveSibling(plain.getFileName()+".bak")));
    }
    @Test void importRejectsEmptyBinaryOversizedAndMissingTrack()throws Exception{
        assertThrows(IllegalArgumentException.class,()->LyricImport.inspect("  "));
        assertThrows(IllegalArgumentException.class,()->LyricImport.inspect("binary\0text"));
        assertThrows(IllegalArgumentException.class,()->LyricImport.inspect("é".repeat(500001)));
        assertThrows(IllegalArgumentException.class,()->LyricImport.save(folder,Track.EMPTY,LyricImport.inspect("Original")));
        Path wrong=folder.resolve("wrong.exe");Files.writeString(wrong,"lyrics");assertThrows(IllegalArgumentException.class,()->LyricImport.read(wrong));
    }
    @Test void bomAndEnhancedWordTimingSurviveFileImport()throws Exception{
        Path file=folder.resolve("original.lrc");Files.writeString(file,"\ufeff[00:01]<00:01>One <00:02>line<00:03>");
        var preview=LyricImport.read(file);assertTrue(preview.timed());assertEquals(1,preview.lines());
        assertEquals(3,Lyrics.parse(preview.text()).getFirst().end());
    }
}
