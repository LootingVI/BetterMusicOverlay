package dev.bettermusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LyricsTest {
    @Test void repeatedTimestampsFractionsAndMetadata(){
        var lines=Lyrics.parse("[ar:Test]\n[00:12.50][01:02.005]Repeated line\n[00:01]Opening\n[00:08.2]\n[00:99]Invalid");
        assertEquals(4,lines.size());assertEquals("Opening",lines.get(0).text());
        assertEquals(8.2,lines.get(1).time(),.0001);assertEquals("",lines.get(1).text());
        assertEquals(12.5,lines.get(2).time());assertEquals(62.005,lines.get(3).time(),.0001);
    }
    @Test void boundariesAndSeeking(){
        var lines=Lyrics.parse("[00:10]One\n[00:20]Two\n[00:30]Three");
        assertEquals(-1,Lyrics.current(lines,9.99));assertEquals(0,Lyrics.current(lines,10));
        assertEquals(2,Lyrics.current(lines,100));assertEquals(0,Lyrics.current(lines,11));
    }
    @Test void offsetAndUntimedText(){
        assertTrue(Lyrics.parse("Plain lyrics must not get invented timestamps").isEmpty());
        var lines=Lyrics.parse("[offset:-1500]\n[00:01]Start\n[00:03]Next");
        assertEquals(0,lines.get(0).time());assertEquals(1.5,lines.get(1).time());
    }
    @Test void unknownDurationAndPausedPlayback(){
        var track=new Track("Title","Artist","Test",false,12,100,"",System.nanoTime()-5_000_000_000L);
        assertEquals(12,track.elapsed());
        var playing=new Track("Title","Artist","Test",true,99,100,"",System.nanoTime()-5_000_000_000L);
        assertEquals(100,playing.elapsed());
    }
}
