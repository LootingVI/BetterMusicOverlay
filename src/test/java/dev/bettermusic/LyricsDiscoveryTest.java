package dev.bettermusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class LyricsDiscoveryTest {
    @TempDir Path folder;
    // Public recording metadata; lyric text is an original test fixture, not the song's lyrics.
    static Track appleTrack(double duration){return new Track("GERÄT (bitte cancelt mich 2)","Zensery","AppleMusic",false,12,duration,"",System.nanoTime(),"GERÄT (bitte cancelt mich 2) - Single");}
    static JsonObject recording(String title,String album,double duration){
        var j=new JsonObject();j.addProperty("id",32429053);j.addProperty("trackName",title);j.addProperty("artistName","Zensery");j.addProperty("albumName",album);j.addProperty("duration",duration);j.addProperty("syncedLyrics","[00:00]Original fixture opening\n[00:10]Original fixture second line");return j;
    }
    static JsonObject matching(){return recording("GERÄT (bitte cancelt mich 2)","GERÄT (bitte cancelt mich 2)",133);}
    private void loaded(LyricsService service)throws Exception{long end=System.nanoTime()+3_000_000_000L;while(service.status().equals("Looking for lyrics ...")&&System.nanoTime()<end)Thread.sleep(5);assertNotEquals("Looking for lyrics ...",service.status());}
    @Test void windowsAppleMusicCombinedArtistWithEmptyAlbumFindsLyrics()throws Exception{
        var track=new Track("Example song","Example artist — Example album - Single","AppleInc.AppleMusicWin_nzyj5cx40ttqa!App",false,12,133,"",System.nanoTime(),"");
        var queries=new java.util.concurrent.CopyOnWriteArrayList<String>();var settings=new Settings();settings.lyrics=true;settings.onlineLyrics=true;
        try(var service=new LyricsService(folder,path->{
            queries.add(java.net.URLDecoder.decode(path,java.nio.charset.StandardCharsets.UTF_8));
            if(path.startsWith("get?"))return null;
            var records=new JsonArray();var row=recording("Example song","Example album",133);row.addProperty("artistName","Example artist");records.add(row);return records;
        })){
            service.update(track,settings);loaded(service);
            assertEquals("LRCLIB · LINE SYNC",service.attribution(),service.status());
            assertEquals("get?track_name=Example song&artist_name=Example artist&album_name=Example album - Single&duration=133.0",queries.getFirst());
            assertEquals("search?track_name=Example song&artist_name=Example artist",queries.getLast());
            assertEquals("Example artist — Example album - Single",track.artist(),"Keep raw metadata for local filenames and corrections");
        }
    }
    @Test void appleSingleAlbumDoesNotHideAnExistingRecording()throws Exception{
        var calls=new AtomicInteger();var s=new Settings();s.lyrics=true;s.onlineLyrics=true;var track=appleTrack(133);
        try(var service=new LyricsService(folder,path->{calls.incrementAndGet();if(path.startsWith("get?"))return null;var records=new JsonArray();if(!path.contains("album_name="))records.add(matching());return records;})){
            service.update(track,s);loaded(service);assertEquals("LRCLIB · LINE SYNC",service.attribution(),service.status());assertEquals("Original fixture second line",service.visible(track,s).getFirst());assertEquals(2,calls.get());
        }
    }
    @Test void unknownPlayerDurationStillSearchesWhenSignatureLookupMisses()throws Exception{
        var calls=new AtomicInteger();var s=new Settings();s.lyrics=true;s.onlineLyrics=true;var track=appleTrack(0);
        try(var service=new LyricsService(folder,path->{calls.incrementAndGet();assertFalse(path.contains("duration="));if(path.startsWith("get?"))return null;var records=new JsonArray();records.add(matching());return records;})){
            service.update(track,s);loaded(service);assertEquals("LRCLIB · LINE SYNC",service.attribution(),service.status());assertEquals(2,calls.get());
        }
    }
    @Test void albumPreferenceDoesNotOverrideArtistVersionOrTimingChecks(){
        var track=appleTrack(133);var records=new JsonArray();
        records.add(recording(track.title()+" (Live)",track.album(),133));
        records.add(recording(track.title(),track.album(),180));
        var other=matching();other.addProperty("artistName","Another artist");records.add(other);assertNull(LyricsMatch.best(records,track));
        records.add(matching());assertEquals(32429053,LyricsMatch.best(records,track).get("id").getAsLong());
    }
    @Test void unknownDurationRequiresAConsistentRecordingOrMatchingAlbum(){
        var records=new JsonArray();records.add(recording(appleTrack(0).title(),"Other release",133));records.add(recording(appleTrack(0).title(),"Other release",170));
        assertNull(LyricsMatch.best(records,appleTrack(0)));
        records.add(matching());assertEquals(133,LyricsMatch.best(records,appleTrack(0)).get("duration").getAsDouble());
        var ambiguous=new JsonArray();ambiguous.add(matching());ambiguous.add(recording(appleTrack(0).title(),"GERÄT (bitte cancelt mich 2)",170));assertNull(LyricsMatch.best(ambiguous,appleTrack(0)));
    }
    @Test void sameSongOnAnotherAlbumRemainsAValidFallback(){
        var records=new JsonArray();records.add(recording(appleTrack(133).title(),"Compilation",133));assertNotNull(LyricsMatch.best(records,appleTrack(133)));
        records.add(matching());assertEquals("GERÄT (bitte cancelt mich 2)",LyricsMatch.best(records,appleTrack(133)).get("albumName").getAsString());
    }
}
