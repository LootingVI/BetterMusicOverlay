package dev.bettermusic;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import javax.imageio.*;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.FileImageOutputStream;
import java.nio.file.*;
import java.util.*;

/** Opt-in renderer demo with original words and deliberately authored demo timestamps. */
class MotionPreviewTest {
    @Test @EnabledIfEnvironmentVariable(named="BETTERMUSIC_MOTION_PREVIEW",matches="1")
    void renderAnimatedPreview()throws Exception{
        var settings=new Settings();settings.scene("LYRIC LOUNGE");settings.width=390;settings.motionIntensity=1;settings.adaptiveColor=false;
        var style=HudPainter.Style.from(settings);var output=Path.of("build/previews");Files.createDirectories(output);
        var lines=List.of("Make room for your music","Small worlds, wide skies","Follow the light home","Stay in your world");
        var writer=ImageIO.getImageWritersBySuffix("gif").next();
        try(var stream=new FileImageOutputStream(output.resolve("lyric-motion.gif").toFile())){
            writer.setOutput(stream);writer.prepareWriteSequence(null);
            for(int frame=0;frame<160;frame++){
                double time=frame*.04;int index=Math.min(3,(int)(time/1.6));double local=time-index*1.6;
                String text=lines.get(index);var words=new ArrayList<Lyrics.Word>();String[] pieces=text.split(" ");
                for(int i=0;i<pieces.length;i++)words.add(new Lyrics.Word(index*1.6+i*1.4/pieces.length,pieces[i]+(i+1<pieces.length?" ":"")));
                var line=new Lyrics.Line(index*1.6,text,words,(index+1)*1.6);
                var previous=index==0?List.<String>of():List.of(lines.get(index-1));
                var track=new Track("WIDE SKIES","Better Music · Original demo","PREVIEW",false,12+time,60,"",0);
                var image=HudPainter.paintInto(null,style,track,null,lines.subList(index,lines.size()),2,time+1,local,"ORIGINAL DEMO · WORD SYNC",new HudPainter.LyricFrame(previous,LyricMotion.highlight(line,time,true)));
                if(frame==58)ImageIO.write(image,"png",output.resolve("lyric-motion.png").toFile());
                var metadata=writer.getDefaultImageMetadata(ImageTypeSpecifier.createFromRenderedImage(image),writer.getDefaultWriteParam());
                String format=metadata.getNativeMetadataFormatName();var root=(IIOMetadataNode)metadata.getAsTree(format);
                var control=(IIOMetadataNode)root.getElementsByTagName("GraphicControlExtension").item(0);control.setAttribute("disposalMethod","restoreToBackgroundColor");control.setAttribute("delayTime","4");control.setAttribute("transparentColorFlag","TRUE");control.setAttribute("transparentColorIndex","0");
                if(frame==0){var extensions=new IIOMetadataNode("ApplicationExtensions");var loop=new IIOMetadataNode("ApplicationExtension");loop.setAttribute("applicationID","NETSCAPE");loop.setAttribute("authenticationCode","2.0");loop.setUserObject(new byte[]{1,0,0});extensions.appendChild(loop);root.appendChild(extensions);}
                metadata.setFromTree(format,root);writer.writeToSequence(new IIOImage(image,null,metadata),writer.getDefaultWriteParam());
            }
            writer.endWriteSequence();
        }finally{writer.dispose();}
    }
}
