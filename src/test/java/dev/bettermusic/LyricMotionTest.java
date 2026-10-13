package dev.bettermusic;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class LyricMotionTest {
    private Lyrics.Line line(){return new Lyrics.Line(10,"Original words",List.of(new Lyrics.Word(10,"Original "),new Lyrics.Word(11,"words")),12);}
    @Test void revealStartsOnlyAtSuppliedWordTimestampsAndStopsAtEnd(){
        assertTrue(LyricMotion.highlight(line(),9.9,true).empty());
        assertEquals(0,LyricMotion.highlight(line(),10,true).reveal());
        var half=LyricMotion.highlight(line(),10.09,true);assertTrue(half.reveal()>0&&half.reveal()<1);assertEquals("",half.before());
        assertEquals(1,LyricMotion.highlight(line(),10.3,true).reveal());
        assertEquals("Original ",LyricMotion.highlight(line(),11.09,true).before());
        assertTrue(LyricMotion.highlight(line(),12,true).empty());
        assertTrue(LyricMotion.highlight(new Lyrics.Line(10,"No word timings"),11,true).empty());
    }
    @Test void disabledMotionRevealsWholeStartedWordAndSeekingRewinds(){
        assertEquals(1,LyricMotion.highlight(line(),11,false).reveal());
        assertEquals("Original ",LyricMotion.highlight(line(),11,false).before());
        assertEquals("",LyricMotion.highlight(line(),10.01,true).before());
        assertTrue(LyricMotion.highlight(line(),9,true).empty());
    }
    @Test void highlightClippingUsesMeasuredTextIncludingUnicode(){
        var highlight=new LyricMotion.Highlight("A ","🎵",.5);
        assertEquals(2.5,highlight.width(value->value.codePointCount(0,value.length())));
    }
    @Test void smoothingIsFrameRateIndependentAndBounded(){
        double slow=0,fast=0;for(int i=0;i<20;i++)slow=LyricMotion.approach(slow,100,.05);for(int i=0;i<60;i++)fast=LyricMotion.approach(fast,100,1.0/60);
        assertEquals(slow,fast,1e-8);assertTrue(fast<100);assertEquals(0,LyricMotion.ease(-1));assertEquals(1,LyricMotion.ease(2));
    }
    @Test void reducedMotionPaintIsIndependentOfAnimationClock(){
        var settings=new Settings();settings.scene("LYRIC LOUNGE");settings.animation=false;
        var track=new Track("Example title","Example artist","TEST",false,12,60,"",0);var style=HudPainter.Style.from(settings);
        var first=HudPainter.paint(style,track,null,List.of("Example words","Next line"),1.5,0,0);
        var later=HudPainter.paint(style,track,null,List.of("Example words","Next line"),1.5,20,20);
        assertArrayEquals(first.getRGB(0,0,first.getWidth(),first.getHeight(),null,0,first.getWidth()),later.getRGB(0,0,later.getWidth(),later.getHeight(),null,0,later.getWidth()));
    }
    @Test void motionPreferencesRoundTripWithoutChangingOnlineConsent(){
        var original=new Settings();original.lyricMotion="FADE";original.motionIntensity=.5;original.ambientMotion=false;original.artworkOrbit=false;original.onlineLyrics=true;
        var restored=Profiles.importTheme(Profiles.exportTheme(original),new Settings());assertEquals("FADE",restored.lyricMotion);assertEquals(.5,restored.motionIntensity);assertFalse(restored.artworkOrbit);assertFalse(restored.ambientMotion);assertFalse(restored.onlineLyrics);
        restored.lyricMotion=null;restored.motionIntensity=Double.NaN;restored.validate();assertEquals("FLOW",restored.lyricMotion);assertTrue(Double.isFinite(restored.motionIntensity));
    }
    @Test void lyricOffDisablesTransitionsIndependentlyOfOtherMotion(){
        var settings=new Settings();settings.scene("LYRIC LOUNGE");settings.lyricMotion="OFF";
        var track=new Track("Example title","Example artist","TEST",false,12,60,"",0);var style=HudPainter.Style.from(settings);
        var frame=new HudPainter.LyricFrame(List.of("Previous text"),LyricMotion.Highlight.NONE);
        var first=HudPainter.paintInto(null,style,track,null,List.of("New line"),1.5,2,0,"",frame);
        var later=HudPainter.paintInto(null,style,track,null,List.of("New line"),1.5,2,20,"",frame);
        assertArrayEquals(first.getRGB(0,0,first.getWidth(),first.getHeight(),null,0,first.getWidth()),later.getRGB(0,0,later.getWidth(),later.getHeight(),null,0,later.getWidth()));
    }
}
