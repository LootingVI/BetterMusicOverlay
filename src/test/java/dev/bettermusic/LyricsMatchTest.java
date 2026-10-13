package dev.bettermusic;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricsMatchTest {
    private Track track(String title,double duration){return new Track(title,"Artist","Test",true,0,duration,"",0,"Album");}
    private JsonObject record(String title,double duration){
        var j=new JsonObject();j.addProperty("trackName",title);j.addProperty("artistName","Artist");j.addProperty("albumName","Album");j.addProperty("duration",duration);j.addProperty("syncedLyrics","[00:00]Original test line");return j;
    }
    @Test void durationBoundaryAndVersionQualifiers(){
        assertTrue(LyricsMatch.matches(record("Track",202),track("Track",200),true));
        assertFalse(LyricsMatch.matches(record("Track",202.1),track("Track",200),true));
        assertFalse(LyricsMatch.matches(record("Track (Live)",200),track("Track",200),true));
        assertFalse(LyricsMatch.matches(record("Track",200),track("Track (Remix)",200),true));
    }
    @Test void checksArtistButAllowsAlbumEditionsAndMissingPlayerDuration(){
        var j=record("Track",200);j.addProperty("artistName","Other Artist");assertFalse(LyricsMatch.matches(j,track("Track",200),true));
        j=record("Track",200);j.addProperty("albumName","Different Edition");assertTrue(LyricsMatch.matches(j,track("Track",200),true));
        assertTrue(LyricsMatch.matches(record("Track",200),track("Track",0),true));
        assertTrue(LyricsMatch.matches(record("Track",200),track("Track",0),false));
    }
    @Test void appleAlbumSuffixOnlyRemovedWhenIndependentlyConfirmed(){
        var t=new Track("Track","Artist — Album","AppleMusic",false,0,200,"",0,"Album");assertEquals("Artist",LyricsMatch.artist(t));
        var artist=new Track("Track","Artist — Alias","AppleMusic",false,0,200,"",0,"Album");assertEquals("Artist — Alias",LyricsMatch.artist(artist));
        var single=new Track("Track","Artist — Album","AppleMusic",false,0,200,"",0,"Album - Single");assertEquals("Artist",LyricsMatch.artist(single));
    }
    @Test void choosesClosestSyncedRecordingWithoutDiscardingQualifiers(){
        var list=new JsonArray();list.add(record("Track (Live)",200));list.add(record("Track",202));list.add(record("Track",200.5));
        assertEquals(200.5,LyricsMatch.best(list,track("Track",200)).get("duration").getAsDouble());
        assertNull(LyricsMatch.best(list,track("Completely different track",200)));
    }
    @Test void onlyAppleWindowsCanRecoverAnUnconfirmedAlbum(){
        var apple=new Track("Track","Artist — Album — Deluxe - EP","AppleInc.AppleMusicWin_nzyj5cx40ttqa!App",false,0,200,"",0,"");
        assertEquals(new LyricsMatch.Metadata("Artist","Album — Deluxe - EP"),LyricsMatch.metadata(apple));
        var other=new Track("Track",apple.artist(),"Spotify",false,0,200,"",0,"");
        assertEquals(new LyricsMatch.Metadata(other.artist(),""),LyricsMatch.metadata(other));
        var clean=new Track("Track","Artist","AppleMusic",false,0,200,"",0,"");
        assertEquals(new LyricsMatch.Metadata("Artist",""),LyricsMatch.metadata(clean));
    }
    @Test void confirmedAlbumCanContainAnEmDash(){
        var track=new Track("Track","Artist — Album — Deluxe","Test",false,0,200,"",0,"Album — Deluxe");
        assertEquals("Artist",LyricsMatch.artist(track));
    }
}
