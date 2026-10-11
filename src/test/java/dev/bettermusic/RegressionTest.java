package dev.bettermusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class RegressionTest {
    @TempDir Path folder;
    private Track track(double duration){return new Track("Original","Example","TEST",false,12,duration,"",System.nanoTime(),"Album");}
    private JsonObject record(String lyrics,String plain){var j=new JsonObject();j.addProperty("id",42);j.addProperty("trackName","Original");j.addProperty("artistName","Example");j.addProperty("albumName","Album");j.addProperty("duration",60);j.addProperty("syncedLyrics",lyrics);j.addProperty("plainLyrics",plain);j.addProperty("instrumental",false);return j;}
    private void loaded(LyricsService service)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<end)Thread.sleep(5);assertNotEquals("Looking for lyrics ...",service.status());}
    @Test void selectedDesignCannotBeOverriddenByExpiredSmartTimer(){
        var s=new Settings();s.smartHud=true;var runtime=new Experience();runtime.tick(track(60),false,0,s);assertEquals("MINIMAL",runtime.presentation(s,100).layout);
        s.selectLayout("AURORA");assertEquals("AURORA",runtime.presentation(s,101).layout);assertEquals("AURORA",runtime.presentation(s,1000).layout);
        for(String name:new String[]{"AURORA","VINYL","LYRIC LOUNGE","REFERENCE"}){s.smartHud=true;s.scene(name);assertFalse(s.smartHud);assertEquals(s.layout,runtime.presentation(s,1000).layout);}
        Path file=folder.resolve("settings.json");s.selectLayout("AURORA");s.save(file);var restored=Settings.load(file);assertEquals("AURORA",runtime.presentation(restored,2000).layout);
    }
    @Test void enablingSmartModeStartsItsOwnCountdown(){
        var s=new Settings();s.layout="AURORA";var runtime=new Experience();runtime.tick(track(60),false,0,s);s.smartHud=true;runtime.tick(track(60),false,100,s);
        assertEquals("AURORA",runtime.presentation(s,101).layout);assertEquals("MINIMAL",runtime.presentation(s,110).layout);
    }
    @Test void unknownDurationDoesNotFreezeClockOrLyrics()throws Exception{
        assertEquals(12,track(0).elapsed());Track playing=new Track("Original","Example","TEST",true,12,0,"",System.nanoTime()-2_000_000_000L);assertTrue(playing.elapsed()>=14);
        Files.writeString(folder.resolve(LyricsService.filename(track(0))),"[00:02]Opening\n[00:10]Current line");var s=new Settings();s.lyrics=true;
        try(var service=new LyricsService(folder)){service.update(track(0),s);loaded(service);assertEquals("Current line",service.visible(track(0),s).get(0));}
    }
    @Test void plainLyricsSurviveEmptySearchAndNeverAcquireTiming()throws Exception{
        var s=new Settings();s.lyrics=true;s.onlineLyrics=true;
        try(var service=new LyricsService(folder,path->path.startsWith("get")?record("","First original line\nSecond original line"):new JsonArray())){
            service.update(track(60),s);loaded(service);var view=service.transcript(track(60),s);
            assertTrue(view.lines().isEmpty());assertEquals(2,view.plain().size());assertEquals(-1,view.current());assertEquals("LRCLIB · PLAIN TEXT",view.source());
            s.streamer=true;assertTrue(service.transcript(track(60),s).plain().isEmpty());
        }
    }
    @Test void failedSearchDoesNotDiscardSuccessfulPlainResult()throws Exception{
        var s=new Settings();s.lyrics=true;s.onlineLyrics=true;
        try(var service=new LyricsService(folder,path->{if(path.startsWith("get"))return record("","Original text");throw new java.io.IOException("offline");})){
            service.update(track(60),s);loaded(service);assertEquals("LRCLIB · PLAIN TEXT",service.attribution());
        }
    }
    @Test void readerLoadsPlainLocalTextWithoutEnablingHudLyrics()throws Exception{
        Files.writeString(folder.resolve(LyricsService.filename(track(60)).replace(".lrc",".txt")),"Original local text");var s=new Settings();
        try(var service=new LyricsService(folder)){service.readerOpen(true);service.update(track(60),s);loaded(service);assertFalse(s.lyrics);assertEquals(1,service.transcript(track(60),s).plain().size());assertTrue(service.visible(track(60),s).isEmpty());}
    }
    @Test void offsetEditsDoNotRefetchOrFlashLoading()throws Exception{
        var calls=new AtomicInteger();var s=new Settings();s.lyrics=true;s.onlineLyrics=true;
        try(var service=new LyricsService(folder,path->{calls.incrementAndGet();return record("[00:10]Original lyrics","");})){
            service.update(track(60),s);loaded(service);service.nudge(track(60),.25);service.update(track(60),s);
            assertEquals(1,calls.get());assertEquals(.25,service.correction().offset());assertEquals("LRCLIB · LINE SYNC",service.attribution());
        }
    }
    @Test void disablingOnlineWhileHudLyricsOffCancelsCandidateRequest()throws Exception{
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var finished=new CountDownLatch(1);var s=new Settings();s.onlineLyrics=true;
        try(var service=new LyricsService(folder,path->{started.countDown();try{while(release.getCount()>0)try{release.await();}catch(InterruptedException ignored){}var records=new JsonArray();records.add(record("[00:01]Original", ""));return records;}finally{finished.countDown();}})){
            service.update(track(60),s);service.searchCandidates(track(60),s);assertTrue(started.await(2,TimeUnit.SECONDS));
            s.onlineLyrics=false;service.update(track(60),s);release.countDown();assertTrue(finished.await(2,TimeUnit.SECONDS));Thread.sleep(30);
            assertTrue(service.candidates().isEmpty());assertFalse(service.candidatesFor(track(60)));assertFalse(service.candidateStatus().startsWith("Searching"));
        }finally{release.countDown();}
    }
    @Test void lrcParserPreservesInlineBracketsAndGroupsTranslations(){
        var lines=Lyrics.parse("\ufeff[00:01]Original [00:20] is literal\n[00:01]Translation\n[00:01]Translation");
        assertEquals(1,lines.size());assertEquals(1,lines.get(0).time());assertEquals("Original [00:20] is literal / Translation",lines.get(0).text());
        var terminal=Lyrics.parse("[00:10]<00:10>Your <00:11>world<00:12>").get(0);assertEquals(12,terminal.end());
    }
    @Test void endTimestampClearsWordsDuringInstrumentalGap()throws Exception{
        Files.writeString(folder.resolve(LyricsService.filename(track(60))),"[00:10]<00:10>Your <00:11>world<00:12>\n[00:20]Next line");var s=new Settings();s.lyrics=true;
        try(var service=new LyricsService(folder)){service.update(track(60),s);loaded(service);assertEquals("",service.highlighted(track(60),s));assertEquals("♪ Instrumental",service.visible(track(60),s).get(0));}
    }
}
