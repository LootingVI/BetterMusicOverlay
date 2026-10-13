package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

/** Opt-in network test using public sample metadata, never the user's current listening session. */
class LyricsLiveTest {
    @TempDir Path directory;
    @Test @EnabledIfEnvironmentVariable(named="BETTERMUSIC_LIVE_LYRICS",matches="1")
    void appleMusicWindowsCombinedMetadataFindsZenseryRecording()throws Exception{
        var settings=new Settings();settings.lyrics=true;settings.onlineLyrics=true;
        var catalog=LyricsDiscoveryTest.appleTrack(133);
        var track=new Track(catalog.title(),catalog.artist()+" — "+catalog.album(),"AppleInc.AppleMusicWin_nzyj5cx40ttqa!App",false,12,133,"",System.nanoTime(),"");
        try(var service=new LyricsService(directory)){
            service.update(track,settings);long deadline=System.nanoTime()+25_000_000_000L;
            while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<deadline)Thread.sleep(50);
            assertFalse(service.transcript(track,settings).lines().isEmpty(),service.status());
            assertTrue(service.sourceUrl().matches("https://lrclib.net/api/get/\\d+"));
            System.out.println("APPLE METADATA LOOKUP: "+service.sourceUrl()+" / "+service.attribution());
        }
    }
    @Test @EnabledIfEnvironmentVariable(named="BETTERMUSIC_LIVE_LYRICS",matches="1")
    void officialProviderReturnsAttributedSynchronizedLyrics() throws Exception {
        var settings=new Settings();settings.lyrics=true;settings.onlineLyrics=true;
        var track=new Track("Never Gonna Give You Up","Rick Astley","TEST",false,20,214,"",System.nanoTime(),"Whenever You Need Somebody");
        try(var service=new LyricsService(directory)){
            service.update(track,settings);long deadline=System.nanoTime()+20_000_000_000L;
            while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<deadline)Thread.sleep(50);
            assertTrue(service.attribution().equals("LRCLIB · LINE SYNC")||service.attribution().equals("LRCLIB · WORD SYNC"),service.status());
            assertTrue(service.sourceUrl().matches("https://lrclib.net/api/get/\\d+"));
            assertFalse(service.visible(track,settings).isEmpty());
            service.searchCandidates(track,settings);deadline=System.nanoTime()+20_000_000_000L;
            while(service.candidateStatus().equals("Searching LRCLIB ...")&&System.nanoTime()<deadline)Thread.sleep(50);
            assertFalse(service.candidates().isEmpty(),service.candidateStatus());assertTrue(service.candidatesFor(track));
            long id=service.candidates().get(0).id();service.saveCorrection(track,.25,id,false);service.update(track,settings);deadline=System.nanoTime()+20_000_000_000L;
            while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<deadline)Thread.sleep(50);
            assertTrue(service.attribution().equals("LRCLIB · MANUAL MATCH")||service.attribution().equals("LRCLIB · WORD SYNC"),service.status());assertEquals("https://lrclib.net/api/get/"+id,service.sourceUrl());
            settings.onlineLyrics=false;service.update(track,settings);
            assertTrue(service.attribution().isEmpty(),"Changing consent must immediately clear previous attribution");
        }
    }
}
