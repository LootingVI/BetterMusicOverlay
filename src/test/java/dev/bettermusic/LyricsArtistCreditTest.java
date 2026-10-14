package dev.bettermusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.CopyOnWriteArrayList;
import static org.junit.jupiter.api.Assertions.*;

class LyricsArtistCreditTest {
    @TempDir Path folder;
    private Track track(){return new Track("Example song","DJ Example — Example song - Single","AppleInc.AppleMusicWin_nzyj5cx40ttqa!App",false,12,124,"",0,"");}
    private JsonObject row(){var row=new JsonObject();row.addProperty("id",99123);row.addProperty("trackName","Example song");row.addProperty("artistName","Example");row.addProperty("albumName","Example song");row.addProperty("duration",125);row.addProperty("syncedLyrics","[00:00]An original test line\n[00:10]Another original test line");return row;}
    private Settings settings(){var settings=new Settings();settings.lyrics=true;settings.onlineLyrics=true;return settings;}
    private void loaded(LyricsService service)throws Exception{long deadline=System.nanoTime()+3_000_000_000L;while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<deadline)Thread.sleep(5);assertNotEquals("Looking for lyrics ...",service.status());}
    @Test void matchingTitleAlbumAndDurationResolveDjCreditDifference()throws Exception{
        var requests=new CopyOnWriteArrayList<String>();var track=track();var settings=settings();
        try(var service=new LyricsService(folder,path->{requests.add(path);if(path.startsWith("get?"))return null;var rows=new JsonArray();if(path.endsWith("artist_name=Example"))rows.add(row());return rows;})){
            service.update(track,settings);loaded(service);assertEquals("LRCLIB · LINE SYNC",service.attribution(),service.status());
            assertEquals("https://lrclib.net/api/get/99123",service.sourceUrl());assertEquals(3,requests.size());assertTrue(service.status().contains("artist credit"));
            assertTrue(service.diagnostics(track,settings).contains("Alternative artist credit: Example"));
            assertTrue(service.diagnostics(track,settings).contains("Artist credit variant search: 1 records returned"));
        }
    }
    @Test void completeArtistCreditAlwaysWins()throws Exception{
        var calls=new java.util.concurrent.atomic.AtomicInteger();var exact=row();exact.addProperty("artistName","DJ Example");
        try(var service=new LyricsService(folder,path->{calls.incrementAndGet();assertTrue(path.startsWith("get?"));return exact;})){
            service.update(track(),settings());loaded(service);assertEquals(1,calls.get());assertFalse(service.status().contains("artist credit"));assertFalse(service.attribution().isBlank());
        }
    }
    @Test void variantRequiresAlbumDurationAndASeparatedDjPrefix(){
        assertNotNull(LyricsMatch.creditVariant(track()));
        assertNull(LyricsMatch.creditVariant(new Track("Example song","DJ Example","TEST",false,0,0,"",0,"Example song")));
        assertNull(LyricsMatch.creditVariant(new Track("Example song","DJ Example","TEST",false,0,124,"",0,"")));
        assertNull(LyricsMatch.creditVariant(new Track("Example song","DJExample","TEST",false,0,124,"",0,"Example song")));
        assertNull(LyricsMatch.creditVariant(new Track("Example song","Example","TEST",false,0,124,"",0,"Example song")));
    }
    @Test void variantRejectsDifferentArtistAlbumVersionAndDuration(){
        for(String field:java.util.List.of("artistName","albumName","trackName","duration")){
            var candidate=row();if(field.equals("duration"))candidate.addProperty(field,148);else candidate.addProperty(field,"Other version");
            var records=new JsonArray();records.add(candidate);assertNull(LyricsMatch.bestCreditVariant(records,track()),field);
        }
        var candidates=new JsonArray();candidates.add(row());assertNotNull(LyricsMatch.bestCreditVariant(candidates,track()));
    }
    @Test void rejectedVariantLeavesManualSelectionAvailable()throws Exception{
        try(var service=new LyricsService(folder,path->{if(path.startsWith("get?"))return null;var records=new JsonArray();var candidate=row();candidate.addProperty("albumName","Unrelated album");records.add(candidate);return records;})){
            service.update(track(),settings());loaded(service);assertTrue(service.status().contains("Find recording"));assertTrue(service.attribution().isBlank());
        }
    }
    @Test void shortBusyResponseCanRecoverThroughTheValidatedCredit()throws Exception{
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        try(var service=new LyricsService(folder,path->{int call=calls.incrementAndGet();if(call==1)return null;if(call==2)throw new LrclibClient.CoolingDown("LRCLIB busy",1,true);var rows=new JsonArray();rows.add(row());return rows;})){
            service.update(track(),settings());loaded(service);assertEquals("LRCLIB · LINE SYNC",service.attribution(),service.status());assertEquals(3,calls.get());
        }
    }
    @Test void rateLimitsAndLongOverloadsDoNotTriggerMoreRequests()throws Exception{
        for(var limit:java.util.List.of(new LrclibClient.CoolingDown("LRCLIB rate limit",1,false),new LrclibClient.CoolingDown("LRCLIB busy",5000,true))){
            var calls=new java.util.concurrent.atomic.AtomicInteger();
            try(var service=new LyricsService(folder,path->{if(calls.incrementAndGet()==1)return null;throw limit;})){
                service.update(track(),settings());loaded(service);assertTrue(service.attribution().isBlank());assertEquals(2,calls.get());assertEquals(limit.getMessage(),service.status());
            }
        }
    }
}
