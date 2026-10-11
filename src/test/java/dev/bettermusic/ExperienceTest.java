package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;

class ExperienceTest {
    @TempDir Path folder;
    @Test void smartHudExpandsOnArtistChangesAndPreservesDesign(){
        var s=new Settings();s.smartHud=true;s.layout="AURORA";s.lyrics=true;s.expandSeconds=4;
        var experience=new Experience();var t=Track.demo();experience.tick(t,false,10,s);
        assertEquals("AURORA",experience.presentation(s,12).layout);
        assertEquals("MINIMAL",experience.presentation(s,15).layout);
        assertFalse(experience.presentation(s,15).lyrics);assertEquals("AURORA",s.layout);assertTrue(s.lyrics);
        var next=new Track(t.title(),"Different artist",t.source(),true,0,200,"",0);
        experience.tick(next,false,16,s);assertEquals("AURORA",experience.presentation(s,17).layout);
    }
    @Test void duckingRestoresWithoutChangingSavedVolume(){
        var s=new Settings();s.duckMusic=true;s.duckLevel=.2;var exp=new Experience();
        float level=1;for(int i=0;i<60;i++)level=exp.duck(true,s);assertEquals(.2,level,.001);
        for(int i=0;i<60;i++)level=exp.duck(false,s);assertEquals(1,level,.001);assertEquals(.2,s.duckLevel);
        s.duckMusic=false;assertEquals(1,exp.duck(true,s));
    }
    @Test void damageFadeRecovers(){
        var s=new Settings();var exp=new Experience();for(int i=0;i<30;i++)exp.tick(Track.demo(),true,1,s);
        assertEquals(.25,exp.alpha(s),.01);for(int i=0;i<40;i++)exp.tick(Track.demo(),false,6,s);assertEquals(1,exp.alpha(s),.01);
    }
    @Test void privacyRemovesIdentifiersAndMatchesBlockedApps(){
        var s=new Settings();s.streamer=true;s.hiddenPlayers="spotify, Apple";
        Track t=new Track("Secret","Artist","AppleMusic.exe",true,1,10,"encoded",0,"Album");
        assertTrue(Experience.hidden(t,s));var safe=Experience.privateTrack(t,s);
        assertEquals("Private listening",safe.title());assertEquals("",safe.cover());assertEquals("",safe.album());assertEquals("PRIVATE",safe.source());
        s.streamer=false;assertFalse(Experience.hidden(t,s));assertSame(t,Experience.privateTrack(t,s));
    }
    @Test void profileImportsNeverChangeConsentOrPrivacy()throws Exception{
        var shared=new Settings();shared.onlineLyrics=true;shared.preferred="private-app";shared.streamer=false;shared.scene("VINYL");
        var current=new Settings();current.streamer=true;current.hiddenPlayers="apple";current.preferred="spotify";
        String json=Profiles.exportTheme(shared);assertFalse(json.contains("private-app"));
        var imported=Profiles.importTheme(json,current);assertFalse(imported.onlineLyrics);assertTrue(imported.streamer);assertEquals("spotify",imported.preferred);assertEquals("apple",imported.hiddenPlayers);assertTrue(imported.vinyl);
        Profiles.save(folder,"Building",imported);assertTrue(Profiles.load(folder,"Building",current).vinyl);
        assertThrows(IllegalArgumentException.class,()->Profiles.save(folder,"../escape",current));
        assertThrows(RuntimeException.class,()->Profiles.importTheme("{}",current));
        assertThrows(RuntimeException.class,()->Profiles.importTheme("x".repeat(32001),current));
    }
    @Test void correctionsSeparateRecordingsAndReset()throws Exception{
        var corrections=new LyricCorrections(folder);Track a=Track.demo(),b=new Track(a.title(),a.artist(),a.source(),true,0,300,"",0,"Live");
        assertEquals(0,corrections.get(a).recording());try(var files=Files.list(folder)){assertEquals(0,files.count());}
        corrections.save(a,new LyricCorrections.Correction(1.25,123,true));assertEquals(1.25,corrections.get(a).offset());assertTrue(corrections.get(a).explicit());assertEquals(0,corrections.get(b).recording());
        corrections.reset(a);assertEquals(0,corrections.get(a).recording());
    }
    @Test void realWordTimestampsOnly(){
        var line=Lyrics.parse("[offset:250]\n[00:10.00]<00:10.00>Your <00:10.50>own <00:11.00>frequency").get(0);
        assertEquals("Your own frequency",line.text());assertEquals("",line.highlighted(10));assertEquals("Your ",line.highlighted(10.3));assertEquals("Your own ",line.highlighted(10.8));assertEquals(line.text(),line.highlighted(12));
        assertTrue(Lyrics.parse("[00:10]No timestamps").get(0).words().isEmpty());
        assertTrue(Lyrics.parse("[00:10]<00:11>Wrong <00:10>order").get(0).words().isEmpty());
        assertTrue(Lyrics.parse("[00:10][00:20]<00:10>Repeated").get(0).words().isEmpty());
        assertTrue(Lyrics.parse("[00:10]<00:09>Before line").get(0).words().isEmpty());
    }
    @Test void wordHighlightRendersWithClipping()throws Exception{
        var s=new Settings();s.scene("LYRIC LOUNGE");
        String line="Your own frequency across the endless world";
        var image=HudPainter.paint(HudPainter.Style.from(s),Track.demo(),null,java.util.List.of(line,"Stay in your world"),2,3,3,"LOCAL LRC · WORD SYNC","Your own ");
        Path out=Path.of("build/previews/word-sync.png");Files.createDirectories(out.getParent());javax.imageio.ImageIO.write(image,"png",out.toFile());
        assertEquals(s.width*2,image.getWidth());
    }
}
