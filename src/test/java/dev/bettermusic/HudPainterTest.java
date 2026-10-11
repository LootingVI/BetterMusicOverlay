package dev.bettermusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import javax.imageio.ImageIO;
import java.nio.file.*;
import java.util.List;

class HudPainterTest {
    @Test void renderAllLayoutsWithAntialiasedCorners() throws Exception {
        var s=new Settings();s.animation=false;
        var track=new Track("anzerknacker.wav","LENNSKO","PREVIEW",false,82,144,"",System.nanoTime());
        Path folder=Path.of("build","previews");Files.createDirectories(folder);
        for(String layout:List.of("STUDIO","COMPACT","MINIMAL","AURORA")) {
            s.layout=layout;
            for(boolean lyrics:List.of(false,true)) {
                s.lyrics=lyrics;
                var style=HudPainter.Style.from(s);
                var image=HudPainter.paint(style,track,null,List.of("Your lyrics, right here.","Stay in your world."),3,0);
                assertEquals(s.width*3,image.getWidth());assertEquals(style.height()*3,image.getHeight());
                assertEquals(0,image.getRGB(0,0)>>>24);
                boolean antialias=false;
                for(int y=0;y<36;y++)for(int x=0;x<36;x++){int a=image.getRGB(x,y)>>>24;if(a>0&&a<100)antialias=true;}
                assertTrue(antialias,"Rounded corners should have partially covered pixels");
                ImageIO.write(image,"png",folder.resolve(layout.toLowerCase()+(lyrics?"-lyrics":"")+".png").toFile());
            }
        }
    }
    @Test void identityScenesProduceDistinctArtworkDrivenPreviews() throws Exception {
        Path folder=Path.of("build","previews");Files.createDirectories(folder);
        var cover=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=cover.createGraphics();
        g.setPaint(new java.awt.GradientPaint(0,0,new java.awt.Color(0xE8A667),256,256,new java.awt.Color(0x29254A)));g.fillRect(0,0,256,256);
        g.setColor(new java.awt.Color(0xF9DCB4));g.fillOval(76,46,104,104);
        g.setColor(new java.awt.Color(0x343048));g.fillPolygon(new int[]{0,80,147,230,256},new int[]{256,136,211,120,256},5);g.dispose();
        var track=new Track("After the last sunset","BETTER MUSIC / SESSIONS","PREVIEW",false,83,224,"",System.nanoTime());
        for(String scene:List.of("AURORA","VINYL","LYRIC LOUNGE")) {
            var s=new Settings();s.scene(scene);s.animation=false;
            var image=HudPainter.paint(HudPainter.Style.from(s),track,cover,List.of("The world can wait a little longer","We have a whole sky left to find","And a soundtrack of our own"),3,2);
            assertEquals(1050,image.getWidth());assertEquals(0,image.getRGB(0,0)>>>24);
            ImageIO.write(image,"png",folder.resolve(scene.toLowerCase().replace(' ','-')+"-scene.png").toFile());
        }
    }
}
