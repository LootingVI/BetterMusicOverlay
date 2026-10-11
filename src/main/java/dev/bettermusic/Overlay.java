package dev.bettermusic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import java.util.List;
import java.util.concurrent.*;

public final class Overlay {
    private static final Identifier PANEL=Identifier.fromNamespaceAndPath("bettermusic","smooth_card");
    private static final ExecutorService PAINTER=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"BetterMusic-render");t.setDaemon(true);return t;});
    private record Frame(int width,int height,int[] pixels,long generation) { }
    private static final java.util.concurrent.atomic.AtomicReference<Frame> pending=new java.util.concurrent.atomic.AtomicReference<>();
    private static volatile boolean busy,closed;
    private static DynamicTexture texture;
    private static long lastPaint;
    private static long titleStarted;
    private static String lastTitle="";
    private static String lastLyric="";
    private static long lyricStarted;
    private static int textureWidth,textureHeight;
    private static String renderIdentity="";
    private static volatile long renderGeneration;
    private Overlay() { }
    public static Settings displaySettings(Settings settings){
        Settings s=settings.copy();if(s.streamer&&s.hideTitles){s.artwork=false;s.provider=false;}
        if(s.streamer&&(s.hideLyrics||BetterMusic.lyrics!=null&&BetterMusic.lyrics.correction().explicit()))s.lyrics=false;return s;
    }
    public static int height(){return HudPainter.Style.from(displaySettings(BetterMusic.settings)).height();}
    public static float effectiveScale(int w,int h){return(float)Math.min(BetterMusic.settings.scale,Math.min((w-8.0)/BetterMusic.settings.width,(h-8.0)/height()));}
    public static int x(int w,int h){return(int)Math.round(BetterMusic.settings.x*Math.max(0,w-BetterMusic.settings.width*effectiveScale(w,h)));}
    public static int y(int w,int h){return(int)Math.round(BetterMusic.settings.y*Math.max(0,h-height()*effectiveScale(w,h)));}
    public static void drawAtPosition(GuiGraphics g,Track t,int w,int h){draw(g,t,x(w,h),y(w,h),effectiveScale(w,h));}
    public static void draw(GuiGraphics g,Track track,int x,int y,float scale) {
        draw(g,track,x,y,scale,BetterMusic.settings,1);
    }
    public static void drawHud(GuiGraphics g,Track track,int w,int h){
        Settings s=displaySettings(BetterMusic.experience.presentation(BetterMusic.settings,System.nanoTime()/1e9));
        int height=HudPainter.Style.from(s).height();
        float scale=(float)Math.min(s.scale,Math.min((w-8.0)/s.width,(h-8.0)/height));
        draw(g,track,(int)(s.x*Math.max(0,w-s.width*scale)),(int)(s.y*Math.max(0,h-height*scale)),scale,s,BetterMusic.experience.alpha(s));
    }
    private static void draw(GuiGraphics g,Track original,int x,int y,float scale,Settings settings,float alpha) {
        if(Experience.hidden(original,settings))return;
        Settings s=displaySettings(settings);
        Track track=Experience.privateTrack(original,s);
        if(closed)return;
        long now=System.nanoTime();
        var style=HudPainter.Style.from(s);
        double density=Math.max(1,Math.min(6,Minecraft.getInstance().getWindow().getGuiScale()*scale));
        String identity=original.source()+"\n"+track.title()+"\n"+track.artist()+"\n"+s.streamer+":"+s.hideTitles+":"+s.hideLyrics+":"+style+":"+density+":"+BetterMusic.lyrics.correction().explicit();
        if(!identity.equals(renderIdentity)){
            renderIdentity=identity;renderGeneration++;pending.set(null);lastPaint=0;
            if(texture!=null){Minecraft.getInstance().getTextureManager().release(PANEL);texture=null;}
        }
        if(!lastTitle.equals(track.title())){lastTitle=track.title();titleStarted=now;}
        if(!busy&&now-lastPaint>(s.animation?33_000_000L:100_000_000L)) {
            busy=true;lastPaint=now;
            List<String> lines=original.source().equals("PREVIEW")?List.of("Your lyrics, right here.","Stay in your world.","Find your own frequency."):BetterMusic.lyrics.visible(original,s);
            String words=s.wordHighlight?BetterMusic.lyrics.highlighted(original,s):"";
            String current=lines.isEmpty()?"":lines.get(0);
            if(!lastLyric.equals(current)){lastLyric=current;lyricStarted=now;}
            double animationTime=(now-titleStarted)/1e9;
            double lyricTime=(now-lyricStarted)/1e9;
            String lyricSource=track.source().equals("PREVIEW")?"DEMO LYRICS":BetterMusic.lyrics.attribution();
            long generation=renderGeneration;
            PAINTER.execute(()->{
                try {
                    var art=track.source().equals("PREVIEW")||!s.artwork?null:BetterMusic.art.blended(s.animation&&s.transitions);
                    var image=HudPainter.paint(style,track,art,lines,density,animationTime,lyricTime,lyricSource,words);
                    int[] pixels=image.getRGB(0,0,image.getWidth(),image.getHeight(),null,0,image.getWidth());
                    if(alpha<.999f)for(int i=0;i<pixels.length;i++)pixels[i]=(pixels[i]&0xffffff)|((int)((pixels[i]>>>24)*alpha)<<24);
                    if(generation==renderGeneration)pending.set(new Frame(image.getWidth(),image.getHeight(),pixels,generation));
                }catch(Exception e){BetterMusic.LOG.debug("Cannot render music card",e);}finally{busy=false;}
            });
        }
        Frame frame=pending.getAndSet(null);
        if(frame!=null&&frame.generation()==renderGeneration) {
            boolean recreate=texture==null||frame.width()!=textureWidth||frame.height()!=textureHeight;
            NativeImage pixels=recreate?new NativeImage(frame.width(),frame.height(),false):texture.getPixels();
            for(int py=0;py<frame.height();py++)for(int px=0;px<frame.width();px++)pixels.setPixel(px,py,frame.pixels()[py*frame.width()+px]);
            if(recreate){texture=new DynamicTexture(()->"Better Music antialiased card",pixels);Minecraft.getInstance().getTextureManager().register(PANEL,texture);textureWidth=frame.width();textureHeight=frame.height();}
            else texture.upload();
        }
        if(texture!=null) {
            g.pose().pushMatrix();g.pose().translate(x,y);g.pose().scale(scale,scale);
            g.blit(RenderPipelines.GUI_TEXTURED,PANEL,0,0,0,0,s.width,HudPainter.Style.from(s).height(),textureWidth,textureHeight,textureWidth,textureHeight);
            g.pose().popMatrix();
        }
    }
    public static boolean control(double localX,double localY) {
        Settings s=displaySettings(BetterMusic.settings);var style=HudPainter.Style.from(s);int cy=style.controlY();
        Track current=BetterMusic.media.track();
        if(!current.present()||Experience.hidden(current,s)||localX<0||localX>s.width||localY<0||localY>style.height())return false;
        double seek=style.seekFraction(localX,localY);
        if(seek>=0&&BetterMusic.media.track().duration()>0){BetterMusic.media.seek(seek);return true;}
        if(Math.abs(localY-cy)>13)return false;
        if(Math.abs(localX-(s.width-87))<13){BetterMusic.media.command("previous");return true;}
        if(Math.abs(localX-(s.width-55))<13){BetterMusic.media.command("play");return true;}
        if(Math.abs(localX-(s.width-23))<13){BetterMusic.media.command("next");return true;}
        return false;
    }
    public static void close(){closed=true;PAINTER.shutdownNow();Minecraft.getInstance().getTextureManager().release(PANEL);}
    public static Component smoothText(String text){return Component.literal(text).withStyle(style->style.withFont(new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettermusic","ui"))));}
    public static void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(Minecraft.getInstance().font,smoothText(text),x,y,color,false);}
    public static String fit(String text,int width){var font=Minecraft.getInstance().font;if(font.width(smoothText(text))<=width)return text;while(!text.isEmpty()&&font.width(smoothText(text+"…"))>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));return text+"…";}
    public static void round(GuiGraphics g,int x,int y,int w,int h,int r,int color) {
        if(w<=0||h<=0)return;r=Math.max(0,Math.min(r,Math.min(w,h)/2));
        for(int row=0;row<h;row++){int d=row<r?r-row-1:row>=h-r?row-(h-r):0;int inset=d==0?0:(int)Math.ceil(r-Math.sqrt(r*r-d*d));g.fill(x+inset,y+row,x+w-inset,y+row+1,color);}
    }
}
