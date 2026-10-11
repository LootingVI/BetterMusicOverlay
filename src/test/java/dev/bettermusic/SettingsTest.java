package dev.bettermusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SettingsTest {
    @Test void invalidConfigCannotBreakGeometry(){
        var s=new Settings();s.scale=Double.NaN;s.opacity=Double.POSITIVE_INFINITY;s.width=-100;s.radius=999;s.x=3;s.y=-4;s.layout=null;s.preferred=null;s.lyricsLines=99;
        s.validate();assertEquals(.6,s.scale);assertEquals(.15,s.opacity);assertEquals(220,s.width);assertEquals(20,s.radius);assertEquals(1,s.x);assertEquals(0,s.y);assertEquals("STUDIO",s.layout);assertEquals("",s.preferred);assertEquals(3,s.lyricsLines);
    }
    @Test void colorsAndFilenameStaySafe(){
        var s=new Settings();s.accent=-1;s.validate();assertEquals(0xffffff,s.accent);
        String name=LyricsService.filename(new Track("../../song?","A/B","",false,0,0,"",0));
        assertFalse(name.contains("/"));assertFalse(name.contains("?"));assertTrue(name.endsWith(".lrc"));
    }
}
