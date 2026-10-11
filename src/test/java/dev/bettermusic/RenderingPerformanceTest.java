package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.awt.image.BufferedImage;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RenderingPerformanceTest {
    private Track paused(){return new Track("Original test track","Example artist","TEST",false,12,60,"",System.nanoTime());}
    @Test void bulkUploadPreservesChannelsAndOpacity(){
        int[] pixels={0xff123456,0x80112233,0x00000000};RenderBudget.toAbgr(pixels,.5f);
        assertArrayEquals(new int[]{0x7f563412,0x40332211,0},pixels);
    }
    @Test void reusedRasterClearsOldContentAndMatchesFreshPaint(){
        var settings=new Settings();settings.scene("LYRIC LOUNGE");var style=HudPainter.Style.from(settings);var t=paused();
        BufferedImage first=HudPainter.paint(style,t,null,List.of("Old line"),2,2);
        settings.radius=20;settings.opacity=.3;style=HudPainter.Style.from(settings);
        BufferedImage reused=HudPainter.paintInto(first,style,t,null,List.of("New line"),2,2,2,"","");
        BufferedImage fresh=HudPainter.paint(style,t,null,List.of("New line"),2,2,2,"","");
        assertSame(first,reused);assertArrayEquals(fresh.getRGB(0,0,fresh.getWidth(),fresh.getHeight(),null,0,fresh.getWidth()),reused.getRGB(0,0,reused.getWidth(),reused.getHeight(),null,0,reused.getWidth()));
        settings.layout="MINIMAL";assertNotSame(first,HudPainter.paintInto(first,HudPainter.Style.from(settings),t,null,List.of(),2,2,2,"",""));
    }
    @Test void qualityBudgetsAndOldConfigurationsRemainBounded(){
        var settings=new Settings();settings.renderQuality=null;settings.validate();assertEquals("BALANCED",settings.renderQuality);
        assertEquals(20,RenderBudget.fps(settings.renderQuality));assertEquals(3,RenderBudget.density(settings.renderQuality,12));
        assertEquals(10,RenderBudget.fps("SAVER"));assertEquals(4,RenderBudget.density("QUALITY",5));assertEquals(1,RenderBudget.density("QUALITY",.5));
        assertEquals(200_000_000L,RenderBudget.interval("QUALITY",false));
    }
    @Test @EnabledIfEnvironmentVariable(named="BETTERMUSIC_BENCHMARK",matches="1")
    void reportRasterAllocationAndTime(){
        var settings=new Settings();settings.scene("LYRIC LOUNGE");var style=HudPainter.Style.from(settings);var t=paused();List<String> lines=List.of("An original line","Another original line","A quiet evening");
        var bean=(com.sun.management.ThreadMXBean)java.lang.management.ManagementFactory.getThreadMXBean();bean.setThreadAllocatedMemoryEnabled(true);
        BufferedImage image=null;int[] pixels=null;
        for(int warm=0;warm<40;warm++)image=HudPainter.paintInto(image,style,t,null,lines,3,2,2,"LOCAL LRC","");
        for(boolean reuse:new boolean[]{false,true}){
            long allocated=bean.getThreadAllocatedBytes(Thread.currentThread().threadId()),start=System.nanoTime();int count=60;
            for(int i=0;i<count;i++){image=HudPainter.paintInto(reuse?image:null,style,t,null,lines,3,2,2,"LOCAL LRC","");pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),reuse?pixels:null,0,image.getWidth());RenderBudget.toAbgr(pixels,1);}
            System.out.printf(java.util.Locale.ROOT,"RASTER %s: %.3f ms/frame; %.0f bytes/frame; %dx%d%n",reuse?"reused":"fresh",(System.nanoTime()-start)/1e6/count,(bean.getThreadAllocatedBytes(Thread.currentThread().threadId())-allocated)/(double)count,image.getWidth(),image.getHeight());
        }
    }
}
