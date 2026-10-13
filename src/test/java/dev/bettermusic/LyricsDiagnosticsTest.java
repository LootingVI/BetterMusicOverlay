package dev.bettermusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LyricsDiagnosticsTest {
    @TempDir Path folder;
    private Track track(){return new Track("Example song","DJ Example — Example album - Single","AppleInc.AppleMusicWin_nzyj5cx40ttqa!App",false,10,200,"private-artwork-url",System.nanoTime(),"");}
    private Settings settings(){var settings=new Settings();settings.lyrics=true;settings.onlineLyrics=true;return settings;}
    private void await(java.util.function.BooleanSupplier pending)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(pending.getAsBoolean()&&System.nanoTime()<end)Thread.sleep(5);assertFalse(pending.getAsBoolean(),"Background lookup did not finish");}
    @Test void titleOnlyCandidatesRequireSelectionAndPersistAsAManualMatch()throws Exception{
        var calls=new CopyOnWriteArrayList<String>();var track=track();var settings=settings();
        var row=LyricsDiscoveryTest.recording(track.title(),"Example album",201);row.addProperty("artistName","Example");
        try(var service=new LyricsService(folder,path->{calls.add(path);if(path.startsWith("get/"))return row;if(path.startsWith("get?"))return null;var rows=new JsonArray();if(!path.contains("artist_name="))rows.add(row);return rows;})){
            service.update(track,settings);await(()->service.status().equals("Looking for lyrics ..."));
            assertTrue(service.attribution().isEmpty(),"An artist mismatch must not silently select a recording");
            service.searchCandidates(track,settings);await(()->service.candidateStatus().equals("Searching LRCLIB ..."));
            assertEquals(1,service.candidates().size());assertTrue(calls.contains("search?track_name=Example+song"));
            service.saveCorrection(track,0,service.candidates().getFirst().id(),false);service.update(track,settings);
            await(()->service.status().equals("Looking for lyrics ..."));assertEquals("LRCLIB · MANUAL MATCH",service.attribution());
        }
        try(var reopened=new LyricsService(folder,path->{assertEquals("get/32429053",path);return row;})){
            reopened.update(track,settings);await(()->reopened.status().equals("Looking for lyrics ..."));assertEquals("LRCLIB · MANUAL MATCH",reopened.attribution());
        }
    }
    @Test void diagnosticsExplainMetadataAndFailuresWithoutLyricsOrPaths()throws Exception{
        var track=track();var settings=settings();
        try(var service=new LyricsService(folder,path->{throw new java.io.IOException("LRCLIB HTTP 502");})){
            service.update(track,settings);await(()->service.status().equals("Looking for lyrics ..."));String report=service.diagnostics(track,settings);
            assertTrue(report.contains("Raw artist: DJ Example — Example album - Single"));assertTrue(report.contains("Query artist: DJ Example\n"));assertTrue(report.contains("Query album: Example album - Single"));
            assertTrue(report.contains("Exact lookup: LRCLIB HTTP 502"));assertFalse(report.contains("private-artwork-url"));assertFalse(report.contains(folder.toString()));
            settings.streamer=true;String redacted=service.diagnostics(track,settings);assertFalse(redacted.contains("Example"));assertFalse(redacted.contains("502"));assertTrue(redacted.contains("hidden by streamer mode"));
        }
    }
    @Test void diagnosticSuccessOmitsLyricTextAndStaleTrackResults()throws Exception{
        var track=LyricsDiscoveryTest.appleTrack(133);var settings=settings();
        try(var service=new LyricsService(folder,path->LyricsDiscoveryTest.matching())){
            service.update(track,settings);await(()->service.status().equals("Looking for lyrics ..."));String report=service.diagnostics(track,settings);
            assertTrue(report.contains("Exact lookup: record returned"));assertTrue(report.contains("https://lrclib.net/api/get/32429053"));assertFalse(report.contains("Original fixture"));
            String changed=service.diagnostics(track(),settings);assertFalse(changed.contains("32429053"));assertFalse(changed.contains("record returned"));assertTrue(changed.contains("Track changed"));
        }
    }
    @Test void unknownDurationUsesRecoveredAlbumToDisambiguate() {
        var track=new Track("Example song","Example artist — Example album - Single","AppleMusic",false,0,0,"",0,"");
        var records=new JsonArray();var original=LyricsDiscoveryTest.recording(track.title(),"Example album",200);original.addProperty("artistName","Example artist");records.add(original);
        var other=original.deepCopy();other.addProperty("albumName","Live album");other.addProperty("duration",260);records.add(other);
        assertSame(original,LyricsMatch.best(records,track));
    }
    @Test void switchingPlayersDoesNotReuseAppleSpecificMetadataMatching()throws Exception{
        var apple=track();var settings=settings();var row=LyricsDiscoveryTest.recording(apple.title(),"Example album",200);row.addProperty("artistName","DJ Example");
        try(var service=new LyricsService(folder,path->path.startsWith("get?")?row:new JsonArray())){
            service.update(apple,settings);await(()->service.status().equals("Looking for lyrics ..."));assertFalse(service.attribution().isBlank());
            var other=new Track(apple.title(),apple.artist(),"Other player",false,10,200,"",0,"");
            service.update(other,settings);await(()->service.status().equals("Looking for lyrics ..."));assertTrue(service.attribution().isBlank());
            assertTrue(service.status().contains("No safe automatic match"));
        }
    }
}
