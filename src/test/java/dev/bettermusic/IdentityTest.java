package dev.bettermusic;

import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IdentityTest {
    @Test void paletteIgnoresTransparentAndNeutralPixels(){
        var image=new BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB);
        for(int y=0;y<32;y++)for(int x=0;x<32;x++)image.setRGB(x,y,0xffffffff);
        assertEquals(0xabcdef,ArtworkPalette.accent(image,0xabcdef));
        for(int y=0;y<10;y++)for(int x=0;x<10;x++)image.setRGB(x,y,0xffcc3300);
        int tint=ArtworkPalette.accent(image,0xabcdef);
        assertTrue((tint>>16&255)>(tint&255));
        assertEquals(0xabcdef,ArtworkPalette.accent(new BufferedImage(4,4,2),0xabcdef));
    }
    @Test void scenePreservesPrivacyAndPlacement(){
        var s=new Settings();s.x=.8;s.preferred="apple";s.onlineLyrics=false;
        s.scene("LYRIC LOUNGE");assertTrue(s.lyrics);assertTrue(s.lyricFocus);assertFalse(s.onlineLyrics);assertEquals(.8,s.x);assertEquals("apple",s.preferred);
        s.scene("REFERENCE");assertEquals("STUDIO",s.layout);assertFalse(s.adaptiveColor);
        s.layout="AURORA";s.validate();assertEquals("AURORA",s.layout);
    }
    @Test void seekGeometryMatchesBothRenderers(){
        var s=new Settings();var style=HudPainter.Style.from(s);
        assertEquals(0,style.seekFraction(47,72));assertEquals(1,style.seekFraction(s.width-52,72));assertEquals(-1,style.seekFraction(46,72));
        s.layout="AURORA";style=HudPainter.Style.from(s);assertEquals(.5,style.seekFraction(s.width/2.0,122),.001);
        assertEquals(91,style.controlY());assertEquals(-1,style.seekFraction(100,90));
        s.progress=false;assertEquals(-1,HudPainter.Style.from(s).seekFraction(100,122));
    }
}
