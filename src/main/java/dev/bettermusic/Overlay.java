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
    private static final java.util.concurrent.atomic.AtomicReference<int[]> recycled=new java.util.concurrent.atomic.AtomicReference<>();
    private static java.awt.image.BufferedImage canvas;
    private static volatile boolean busy,closed;
    private static DynamicTexture texture;
    private static long lastPaint;
    private static long titleStarted;
    private static String lastTitle="";
    private static String lastLyric="";
    private static long lyricStarted;
    private static List<String> lastLines=List.of(),previousLines=List.of();
    private static int textureWidth,textureHeight;
    private record Identity(String source,String title,String artist,HudPainter.Style style,double density,boolean privateLyrics){}
    private static Identity renderIdentity;
    private record StillFrame(double position,double duration,boolean playing,List<String> lines,String words,String attribution,Object cover,int alpha){}
    private static StillFrame lastStill;
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
        double density=RenderBudget.density(s.renderQuality,Minecraft.getInstance().getWindow().getGuiScale()*scale);
        var identity=new Identity(original.source(),track.title(),track.artist(),style,density,s.streamer&&BetterMusic.lyrics.correction().explicit());
        if(!identity.equals(renderIdentity)){
            renderIdentity=identity;renderGeneration++;Frame obsolete=pending.getAndSet(null);if(obsolete!=null)recycled.set(obsolete.pixels());lastPaint=0;lastStill=null;
            lastLines=List.of();previousLines=List.of();lastLyric="";lyricStarted=now;
            if(texture!=null){Minecraft.getInstance().getTextureManager().release(PANEL);texture=null;}
        }
        String titleIdentity=track.source()+"\n"+track.artist()+"\n"+track.title();
        if(!lastTitle.equals(titleIdentity)){lastTitle=titleIdentity;titleStarted=now;}
        if(!busy&&now-lastPaint>RenderBudget.interval(s.renderQuality,s.animation)) {
            lastPaint=now;
            List<String> lines=original.source().equals("PREVIEW")?List.of("Your lyrics, right here.","Stay in your world.","Find your own frequency."):BetterMusic.lyrics.visible(original,s);
            String words=s.wordHighlight?BetterMusic.lyrics.highlighted(original,s):"";
            String current=lines.isEmpty()?"":lines.get(0);
            if(!lastLyric.equals(current)){previousLines=lastLines;lastLyric=current;lyricStarted=now;}lastLines=lines;
            double animationTime=(now-titleStarted)/1e9;
            double lyricTime=(now-lyricStarted)/1e9;
            String lyricSource=track.source().equals("PREVIEW")?"DEMO LYRICS":BetterMusic.lyrics.attribution();
            var lyricFrame=new HudPainter.LyricFrame(previousLines,BetterMusic.lyrics.visualHighlight(original,s));
            var still=new StillFrame(Math.floor(track.elapsed()),track.duration(),track.playing(),lines,words,lyricSource,s.artwork?BetterMusic.art.bitmap():null,(int)(alpha*255));
            if(s.animation||!still.equals(lastStill)||texture==null){
            lastStill=still;busy=true;
            long generation=renderGeneration;
            PAINTER.execute(()->{
                try {
                    var art=track.source().equals("PREVIEW")||!s.artwork?null:BetterMusic.art.blended(s.animation&&s.transitions);
                    var image=canvas=HudPainter.paintInto(canvas,style,track,art,lines,density,animationTime,lyricTime,lyricSource,lyricFrame);
                    int[] pixels=recycled.getAndSet(null);if(pixels==null||pixels.length!=image.getWidth()*image.getHeight())pixels=new int[image.getWidth()*image.getHeight()];
                    image.getRGB(0,0,image.getWidth(),image.getHeight(),pixels,0,image.getWidth());
                    RenderBudget.toAbgr(pixels,alpha);
                    if(generation==renderGeneration){Frame replaced=pending.getAndSet(new Frame(image.getWidth(),image.getHeight(),pixels,generation));if(replaced!=null)recycled.set(replaced.pixels());}else recycled.set(pixels);
                }catch(Exception e){BetterMusic.LOG.debug("Cannot render music card",e);}finally{busy=false;}
            });
            }
        }
        Frame frame=pending.getAndSet(null);
        if(frame!=null&&frame.generation()==renderGeneration) {
            boolean recreate=texture==null||frame.width()!=textureWidth||frame.height()!=textureHeight;
            NativeImage pixels=recreate?new NativeImage(frame.width(),frame.height(),false):texture.getPixels();
            org.lwjgl.system.MemoryUtil.memIntBuffer(pixels.getPointer(),frame.pixels().length).put(frame.pixels());
            recycled.set(frame.pixels());
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
    public static void close(){closed=true;PAINTER.shutdownNow();Minecraft.getInstance().getTextureManager().release(PANEL);UiSkin.close();}
    private static final java.util.Map<String,Component> textCache=new java.util.LinkedHashMap<>(256,.75f,true){@Override protected boolean removeEldestEntry(java.util.Map.Entry<String,Component> entry){return size()>512;}};
    private static final FontDescription UI_FONT=new FontDescription.Resource(Identifier.fromNamespaceAndPath("bettermusic","ui"));
    public static Component smoothText(String text){return textCache.computeIfAbsent(text,value->Component.literal(value).withStyle(style->style.withFont(UI_FONT)));}
    public static void text(GuiGraphics g,String text,int x,int y,int color){g.drawString(Minecraft.getInstance().font,smoothText(text),x,y,color,false);}
    private record Fit(String text,int width){}
    private static final java.util.Map<Fit,String> fitCache=new java.util.LinkedHashMap<>(256,.75f,true){@Override protected boolean removeEldestEntry(java.util.Map.Entry<Fit,String> entry){return size()>512;}};
    public static String fit(String text,int width){return fitCache.computeIfAbsent(new Fit(text,width),key->{var font=Minecraft.getInstance().font;if(font.width(smoothText(text))<=width)return text;int low=0,high=text.codePointCount(0,text.length());while(low<high){int mid=(low+high+1)/2;String part=text.substring(0,text.offsetByCodePoints(0,mid))+"…";if(font.width(smoothText(part))<=width)low=mid;else high=mid-1;}return text.substring(0,text.offsetByCodePoints(0,low))+"…";});}
    public static void round(GuiGraphics g,int x,int y,int w,int h,int r,int color) {
        UiSkin.rounded(g,x,y,w,h,r,color);
    }
}
