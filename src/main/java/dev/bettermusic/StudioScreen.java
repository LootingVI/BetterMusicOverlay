package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;
import java.util.function.DoubleConsumer;
import java.util.function.IntConsumer;

public final class StudioScreen extends Screen {
    private final Screen parent;
    private int tab,left,top,panelWidth,panelHeight,body,previewHeight;
    private String validation="";
    public StudioScreen(Screen parent) { super(Component.literal("Better Music Studio"));this.parent=parent; }
    private Settings s() { return BetterMusic.settings; }
    @Override protected void init() {
        panelWidth=Math.min(540,width-16);panelHeight=Math.min(326,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        previewHeight=Math.max(42,Math.min(88,panelHeight-182));
        int tabsY=top+38+previewHeight;
        int column=(panelWidth-32)/2;
        for(int i=0;i<5;i++) { final int index=i; button(left+12+i*(panelWidth-24)/5,tabsY,(panelWidth-40)/5,20,new String[]{"Style","Layout","Playback","Lyrics","Identity"}[i],()->{tab=index;rebuildWidgets();}); }
        body=tabsY+26;
        int right=left+20+column;
        switch(tab) {
            case 0 -> {
                button(left+12,body,column,20,"Theme: "+s().theme,()->{String[] values={"MIDNIGHT","MATCHA","ROSE","PAPER","OCEAN"};s().preset(next(values,s().theme));rebuildWidgets();});
                button(right,body,column,20,"Layout: "+s().layout,()->{s().selectLayout(next(new String[]{"STUDIO","AURORA","COMPACT","MINIMAL"},s().layout));rebuildWidgets();});
                color(left+12,body+22,column,"Accent",s().accent,v->s().accent=v);
                color(right,body+22,column,"Surface",s().background,v->s().background=v);
                color(left+12,body+44,column,"Text",s().foreground,v->s().foreground=v);
                slider(right,body+44,column,"Opacity",s().opacity,0.15,1,v->s().opacity=v);
                slider(left+12,body+66,column,"Corners",s().radius,0,20,v->s().radius=(int)v);
                button(right,body+66,column,20,"Reset appearance",()->{s().preset("MIDNIGHT");s().radius=12;s().opacity=.92;s().selectLayout("STUDIO");rebuildWidgets();});
            }
            case 1 -> {
                slider(left+12,body,column,"Scale",s().scale,.6,2,v->s().scale=v);
                slider(right,body,column,"Width",s().width,220,420,v->s().width=(int)v);
                slider(left+12,body+22,column,"Horizontal",s().x,0,1,v->s().x=v);
                slider(right,body+22,column,"Vertical",s().y,0,1,v->s().y=v);
                button(left+12,body+44,column,20,"Move overlay with mouse",()->{BetterMusic.save();minecraft.setScreen(new PositionScreen(this));});
                button(right,body+44,column,20,"Anchor: top right",()->{s().x=.97;s().y=.04;rebuildWidgets();});
                button(left+12,body+66,column,20,"Anchor: bottom left",()->{s().x=.03;s().y=.96;rebuildWidgets();});
                button(right,body+66,column,20,"Reset position",()->{s().x=.03;s().y=.08;s().scale=1;s().width=310;rebuildWidgets();});
            }
            case 2 -> {
                button(left+12,body,column,20,"Overlay: "+on(s().enabled),()->{s().enabled=!s().enabled;rebuildWidgets();});
                button(right,body,column,20,"Hide when empty: "+on(s().hideIdle),()->{s().hideIdle=!s().hideIdle;rebuildWidgets();});
                button(left+12,body+22,column,20,"Artwork: "+on(s().artwork),()->{s().artwork=!s().artwork;rebuildWidgets();});
                button(right,body+22,column,20,"Progress: "+on(s().progress),()->{s().progress=!s().progress;rebuildWidgets();});
                button(left+12,body+44,column,20,"Motion: "+on(s().animation),()->{s().animation=!s().animation;rebuildWidgets();});
                button(right,body+44,column,20,"Quick controls [M]",()->minecraft.setScreen(new ControlScreen(this)));
                EditBox source=new EditBox(font,left+12,body+67,column,18,Component.literal("Preferred app ID contains (blank = automatic)"));
                source.addFormatter((text,start)->Overlay.smoothText(text).getVisualOrderText());
                source.setMaxLength(120);source.setValue(s().streamer?"Hidden in streamer mode":s().preferred);source.setEditable(!s().streamer);source.setHint(Component.literal("Auto / spotify / apple ..."));source.setResponder(value->{if(!s().streamer)s().preferred=value.trim();});addRenderableWidget(source);
                int bw=(column-8)/3;
                button(right,body+66,bw,20,"Prev",()->BetterMusic.media.command("previous"));
                button(right+bw+4,body+66,bw,20,"Play",()->BetterMusic.media.command("play"));
                button(right+2*(bw+4),body+66,bw,20,"Next",()->BetterMusic.media.command("next"));
            }
            case 3 -> {
                button(left+12,body,column,20,"Show lyrics: "+on(s().lyrics),()->{s().lyrics=!s().lyrics;rebuildWidgets();});
                button(right,body,column,20,"Online lookup: "+on(s().onlineLyrics),()->{s().onlineLyrics=!s().onlineLyrics;BetterMusic.lyrics.reload();rebuildWidgets();});
                slider(left+12,body+22,column,"Timing (seconds)",s().lyricsOffset,-10,10,v->s().lyricsOffset=v);
                button(right,body+22,column,20,"Visible lines: "+s().lyricsLines,()->{s().lyricsLines=s().lyricsLines%3+1;rebuildWidgets();});
                button(left+12,body+44,column,20,"Open local LRC folder",()->{try{java.awt.Desktop.getDesktop().open(BetterMusic.lyrics.folder().toFile());}catch(Exception e){validation="Folder: config/better-music-lyrics";}});
                button(right,body+44,column,20,"Open lyrics reader",()->minecraft.setScreen(new LyricsScreen(this)));
                button(left+12,body+66,column,20,"View lyrics source",()->{String url=BetterMusic.lyrics.sourceUrl();try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(url.isEmpty()?"https://lrclib.net/docs":url));}catch(Exception e){validation="Source: "+(url.isEmpty()?"LRCLIB docs":url);}});
                button(right,body+66,column,20,"Copy LRC filename",()->minecraft.keyboardHandler.setClipboard(LyricsService.filename(BetterMusic.media.track())));
            }
            default -> {
                button(left+12,body,column,20,"Album colors: "+on(s().adaptiveColor),()->{s().adaptiveColor=!s().adaptiveColor;rebuildWidgets();});
                button(right,body,column,20,"Ambient glow: "+on(s().ambient),()->{s().ambient=!s().ambient;rebuildWidgets();});
                button(left+12,body+22,column,20,"Track fade: "+on(s().transitions),()->{s().transitions=!s().transitions;rebuildWidgets();});
                button(right,body+22,column,20,"Large lyrics: "+on(s().lyricFocus),()->{s().lyricFocus=!s().lyricFocus;rebuildWidgets();});
                button(left+12,body+44,column,20,"Scene: Aurora",()->{s().scene("AURORA");rebuildWidgets();});
                button(right,body+44,column,20,"Scene: Vinyl",()->{s().scene("VINYL");rebuildWidgets();});
                button(left+12,body+66,column,20,"Scene: Lyric Lounge",()->{s().scene("LYRIC LOUNGE");rebuildWidgets();});
                button(right,body+66,column,20,"Scene: Reference",()->{s().scene("REFERENCE");rebuildWidgets();});
            }
        }
        button(left+panelWidth-84,top+panelHeight-28,72,20,"Done",this::onClose);
        button(left+panelWidth-160,top+panelHeight-28,72,20,"More ...",()->minecraft.setScreen(new AdvancedScreen(this)));
    }
    private static String on(boolean value) { return value?"On":"Off"; }
    void showTab(int value){tab=Math.max(0,Math.min(4,value));rebuildWidgets();}
    private static String next(String[] list,String value) { return list[(java.util.Arrays.asList(list).indexOf(value)+1)%list.length]; }
    private void button(int x,int y,int w,int h,String label,Runnable action) { addRenderableWidget(new StudioButton(x,y,w,h,label,action)); }
    private void color(int x,int y,int w,String label,int value,IntConsumer change) {
        int labelWidth=Math.min(64,w/3);
        EditBox box=new EditBox(font,x+labelWidth,y,w-labelWidth,20,Component.literal(label+" hex color"));
        box.addFormatter((text,start)->Overlay.smoothText(text).getVisualOrderText());
        box.setMaxLength(7);box.setValue(String.format(java.util.Locale.ROOT,"#%06X",value));
        box.setResponder(text->{if(text.matches("#?[0-9a-fA-F]{6}")) { change.accept(Integer.parseInt(text.replace("#",""),16));s().theme="CUSTOM";validation="";box.setTextColor(0xffe8e4ff); }else {validation="Use six hex digits, e.g. #A99BFF";box.setTextColor(0xffff9caa);} });
        addRenderableWidget(box);
    }
    private void slider(int x,int y,int w,String label,double current,double min,double max,DoubleConsumer change) {
        addRenderableWidget(new AbstractSliderButton(x,y,w,20,Component.empty(),(current-min)/(max-min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal(label+": "+String.format(java.util.Locale.ROOT,max>2?"%.0f":"%.2f",min+value*(max-min)))); }
            @Override protected void applyValue() { change.accept(min+value*(max-min)); }
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
                Overlay.round(g,getX(),getY(),getWidth(),getHeight(),5,isHoveredOrFocused()?0xff343447:0xff242431);
                Overlay.round(g,getX()+3,getY()+getHeight()-4,(int)((getWidth()-6)*value),2,1,0xff000000|s().accent);
                Overlay.text(g,getMessage().getString(),getX()+8,getY()+6,0xffeceafa);
            }
        });
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x9b070910);
        Overlay.round(g,left,top,panelWidth,panelHeight,12,0xfa11121b);
        Overlay.text(g,"BETTER MUSIC / 1.3",left+14,top+12,0xffeae7fa);
        Overlay.text(g,Overlay.fit(tab==3?"Online lookup sends track + artist to LRCLIB.":"LAYOUT: "+s().layout+" / "+(s().smartHud?"AUTO COLLAPSE ON":"ALWAYS EXPANDED"),panelWidth-28),left+14,top+26,0xff8c899f);
        Track track=BetterMusic.media.track();
        float previewScale=(float)Math.min((panelWidth-32.0)/s().width,(previewHeight-2.0)/Overlay.height());
        previewScale=Math.min(1,previewScale);
        Overlay.draw(g,track.present()?track:Track.demo(),left+(int)((panelWidth-s().width*previewScale)/2),top+34,previewScale);
        if(tab==0) {
            int column=(panelWidth-32)/2;
            Overlay.text(g,"Accent",left+12,body+28,0xff9f9bad);
            Overlay.text(g,"Surface",left+20+column,body+28,0xff9f9bad);
            Overlay.text(g,"Text",left+12,body+50,0xff9f9bad);
        }
        String state=validation.isEmpty()?(s().streamer?"Streamer privacy enabled":tab==3?BetterMusic.lyrics.status():BetterMusic.media.status()):validation;
        Overlay.text(g,Overlay.fit(state,panelWidth-182),left+14,top+panelHeight-21,validation.isEmpty()?0xff9e9aad:0xffff9caa);
        super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) { }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick) {
        float scale=(float)Math.min(1,Math.min((panelWidth-32.0)/s().width,(previewHeight-2.0)/Overlay.height()));
        int x=left+(int)((panelWidth-s().width*scale)/2),y=top+34;
        if(e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&Overlay.control((e.x()-x)/scale,(e.y()-y)/scale))return true;
        return super.mouseClicked(e,doubleClick);
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { BetterMusic.save();minecraft.setScreen(parent); }
    @Override public void removed() { BetterMusic.save(); }

    static final class StudioButton extends Button {
        StudioButton(int x,int y,int w,int h,String label,Runnable action) { super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION); }
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta) {
            Overlay.round(g,getX(),getY(),getWidth(),getHeight(),5,!active?0xff1b1b25:isHoveredOrFocused()?0xff414056:0xff252532);
            if(isFocused()) { Overlay.round(g,getX()+3,getY()+getHeight()-2,getWidth()-6,1,0,0xffb9abff); }
            Overlay.text(g,Overlay.fit(getMessage().getString(),getWidth()-12),getX()+6,getY()+(getHeight()-8)/2,active?0xffeeebfa:0xff777283);
        }
    }
}
