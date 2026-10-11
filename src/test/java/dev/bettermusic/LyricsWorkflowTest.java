package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;

class LyricsWorkflowTest {
    @TempDir Path folder;
    private Track track(String title,double position){return new Track(title,"Example artist","TEST",false,position,60,"",System.nanoTime(),"Example album");}
    private void loaded(LyricsService service)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<end)Thread.sleep(10);assertFalse(service.status().equals("Looking for lyrics ..."));}
    @Test void localWordsCorrectionsAndStreamerRulesWorkTogether()throws Exception{
        Track track=track("Original",10);var s=new Settings();s.lyrics=true;
        Files.writeString(folder.resolve(LyricsService.filename(track)),"[00:10]<00:10>Your <00:11>world\n[00:20]Next line");
        try(var service=new LyricsService(folder)){
            service.update(track,s);loaded(service);assertEquals("LOCAL LRC · WORD SYNC",service.attribution());assertEquals("Your ",service.highlighted(track,s));
            service.saveCorrection(track,1.25,0,false);service.update(track,s);loaded(service);assertEquals("Your world",service.highlighted(track,s));
            s.streamer=true;assertTrue(service.visible(track,s).isEmpty());assertEquals("",service.highlighted(track,s));
            s.hideLyrics=false;assertFalse(service.visible(track,s).isEmpty());
            service.saveCorrection(track,1.25,0,true);service.update(track,s);loaded(service);assertTrue(service.visible(track,s).isEmpty());
            service.resetCorrection(track);service.update(track,s);loaded(service);assertFalse(service.visible(track,s).isEmpty());
        }
    }
    @Test void switchingTracksClearsLyricsAndLoadsCorrectionsWhenLyricsOff()throws Exception{
        Track first=track("First",12),second=track("Second",12);var s=new Settings();s.lyrics=true;
        Files.writeString(folder.resolve(LyricsService.filename(first)),"[00:10]First song");
        try(var service=new LyricsService(folder)){
            service.update(first,s);loaded(service);assertEquals("First song",service.visible(first,s).get(0));
            service.update(second,s);assertFalse(service.visible(second,s).contains("First song"));loaded(service);
            service.saveCorrection(second,2,0,true);s.lyrics=false;service.update(first,s);assertFalse(service.correction().explicit());service.update(second,s);assertTrue(service.correction().explicit());assertEquals(2,service.correction().offset());
        }
    }
    @Test void localFileWinsOverSavedOnlineRecording()throws Exception{
        Track track=track("Local priority",15);var s=new Settings();s.lyrics=true;s.onlineLyrics=true;
        Files.writeString(folder.resolve(LyricsService.filename(track)),"[00:10]Local content");
        try(var service=new LyricsService(folder)){
            service.saveCorrection(track,0,123,true);service.update(track,s);loaded(service);
            assertEquals("LOCAL LRC · LINE SYNC",service.attribution());assertEquals("Local content",service.visible(track,s).get(0));
        }
    }
}
