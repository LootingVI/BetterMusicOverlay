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
    private int tab,left,top,panelWidth,panelHeight,body,previewHeight,contentX,contentWidth,step,controlHeight,previewX,previewY,previewWidth,sideWidth;
    private boolean wide;
    private record Field(int x,int y,int w,int h,String label){}
    private final java.util.List<Field> fields=new java.util.ArrayList<>();
    private static final String[] TABS={"Style","Layout","Playback","Lyrics","Identity"};
    private String validation="";
    public StudioScreen(Screen parent) { super(Component.literal("Better Music Studio"));this.parent=parent; }
    private Settings s() { return BetterMusic.settings; }
    @Override protected void init() {
        fields.clear();panelWidth=Math.min(900,width-16);panelHeight=Math.min(350,height-16);
        left=(width-panelWidth)/2;top=(height-panelHeight)/2;
        wide=panelWidth>=680;sideWidth=wide?106:72;contentX=left+sideWidth+24;
        previewWidth=wide?230:panelWidth-sideWidth-40;
        contentWidth=wide?panelWidth-sideWidth-previewWidth-52:previewWidth;
        previewHeight=wide?Math.min(212,panelHeight-122):Math.max(30,Math.min(100,panelHeight-172));
        previewX=wide?left+panelWidth-previewWidth-16:contentX;previewY=top+42;
        body=wide?top+82:previewY+previewHeight+10;
        step=wide?Math.min(44,(panelHeight-132)/4):Math.max(22,Math.min(36,(top+panelHeight-40-body)/4));controlHeight=Math.max(20,step-6);
        int column=(contentWidth-8)/2;
        for(int i=0;i<5;i++) { final int index=i;addRenderableWidget(new StudioButton(left+10,top+46+i*(wide?35:27),sideWidth,wide?29:23,TABS[i],()->{tab=index;rebuildWidgets();}).selected(tab==i)); }
        int right=contentX+column+8;
        if(!wide)addRenderableWidget(new StudioButton(left+panelWidth-78,top+8,64,22,"Preview",()->minecraft.setScreen(new PositionScreen(this))));
        switch(tab) {
            case 0 -> {
                button(contentX,body,column,20,"Theme: "+s().theme,()->{String[] values={"MIDNIGHT","MATCHA","ROSE","PAPER","OCEAN"};s().preset(next(values,s().theme));rebuildWidgets();});
                button(right,body,column,20,"Layout: "+s().layout,()->{s().selectLayout(next(new String[]{"STUDIO","AURORA","COMPACT","MINIMAL"},s().layout));rebuildWidgets();});
                color(contentX,body+step,column,"Accent",s().accent,v->s().accent=v);
                color(right,body+step,column,"Surface",s().background,v->s().background=v);
                color(contentX,body+2*step,column,"Text",s().foreground,v->s().foreground=v);
                slider(right,body+2*step,column,"Opacity",s().opacity,0.15,1,v->s().opacity=v);
                slider(contentX,body+3*step,column,"Corners",s().radius,0,20,v->s().radius=(int)v);
                button(right,body+3*step,column,20,"Reset appearance",()->{s().preset("MIDNIGHT");s().radius=12;s().opacity=.92;s().selectLayout("STUDIO");rebuildWidgets();});
            }
            case 1 -> {
                slider(contentX,body,column,"Scale",s().scale,.6,2,v->s().scale=v);
                slider(right,body,column,"Width",s().width,220,420,v->s().width=(int)v);
                slider(contentX,body+step,column,"Horizontal",s().x,0,1,v->s().x=v);
                slider(right,body+step,column,"Vertical",s().y,0,1,v->s().y=v);
                button(contentX,body+2*step,column,20,"Move overlay with mouse",()->{BetterMusic.save();minecraft.setScreen(new PositionScreen(this));});
                button(right,body+2*step,column,20,"Anchor: top right",()->{s().x=.97;s().y=.04;rebuildWidgets();});
                button(contentX,body+3*step,column,20,"Anchor: bottom left",()->{s().x=.03;s().y=.96;rebuildWidgets();});
                button(right,body+3*step,column,20,"Reset position",()->{s().x=.03;s().y=.08;s().scale=1;s().width=310;rebuildWidgets();});
            }
            case 2 -> {
                button(contentX,body,column,20,"Overlay: "+on(s().enabled),()->{s().enabled=!s().enabled;rebuildWidgets();});
                button(right,body,column,20,"Hide when empty: "+on(s().hideIdle),()->{s().hideIdle=!s().hideIdle;rebuildWidgets();});
                button(contentX,body+step,column,20,"Artwork: "+on(s().artwork),()->{s().artwork=!s().artwork;rebuildWidgets();});
                button(right,body+step,column,20,"Progress: "+on(s().progress),()->{s().progress=!s().progress;rebuildWidgets();});
                button(contentX,body+2*step,column,20,"Motion settings",()->{var more=new AdvancedScreen(this);minecraft.setScreen(more);more.showPage(6);});
                button(right,body+2*step,column,20,"Quick controls [M]",()->minecraft.setScreen(new ControlScreen(this)));
                fields.add(new Field(contentX,body+3*step,column,controlHeight,""));
                EditBox source=new EditBox(font,contentX+8,body+3*step+(controlHeight-8)/2,column-16,12,Component.literal("Preferred app ID contains (blank = automatic)"));source.setBordered(false);source.setTextColor(0xffddd6ef);
                source.addFormatter((text,start)->Overlay.smoothText(text).getVisualOrderText());
                source.setMaxLength(120);source.setValue(s().streamer?"Hidden in streamer mode":s().preferred);source.setEditable(!s().streamer);source.setHint(Overlay.smoothText("Automatic player"));source.setResponder(value->{if(!s().streamer)s().preferred=value.trim();});addRenderableWidget(source);
                int bw=(column-8)/3;
                button(right,body+3*step,bw,20,"Prev",()->BetterMusic.media.command("previous"));
                button(right+bw+4,body+3*step,bw,20,"Play",()->BetterMusic.media.command("play"));
                button(right+2*(bw+4),body+3*step,bw,20,"Next",()->BetterMusic.media.command("next"));
            }
            case 3 -> {
                button(contentX,body,column,20,"Show lyrics: "+on(s().lyrics),()->{s().lyrics=!s().lyrics;rebuildWidgets();});
                button(right,body,column,20,"LRCLIB lookup: "+on(s().onlineLyrics),()->{s().onlineLyrics=!s().onlineLyrics;BetterMusic.lyrics.reload();rebuildWidgets();});
                slider(contentX,body+step,column,"Timing (seconds)",s().lyricsOffset,-10,10,v->s().lyricsOffset=v);
                button(right,body+step,column,20,"Visible lines: "+s().lyricsLines,()->{s().lyricsLines=s().lyricsLines%3+1;rebuildWidgets();});
                button(contentX,body+2*step,column,20,"Import lyrics",()->minecraft.setScreen(new LyricsImportScreen(this)));
                button(right,body+2*step,column,20,"Open lyrics reader",()->minecraft.setScreen(new LyricsScreen(this)));
                button(contentX,body+3*step,column,20,"View lyrics source",()->{String url=BetterMusic.lyrics.sourceUrl();try{java.awt.Desktop.getDesktop().browse(java.net.URI.create(url.isEmpty()?"https://lrclib.net/docs":url));}catch(Exception e){validation="Source: "+(url.isEmpty()?"LRCLIB docs":url);}});
                button(right,body+3*step,column,20,"Copy LRC filename",()->minecraft.keyboardHandler.setClipboard(LyricsService.filename(BetterMusic.media.track())));
            }
            default -> {
                button(contentX,body,column,20,"Album colors: "+on(s().adaptiveColor),()->{s().adaptiveColor=!s().adaptiveColor;rebuildWidgets();});
                button(right,body,column,20,"Ambient glow: "+on(s().ambient),()->{s().ambient=!s().ambient;rebuildWidgets();});
                button(contentX,body+step,column,20,"Track fade: "+on(s().transitions),()->{s().transitions=!s().transitions;rebuildWidgets();});
                button(right,body+step,column,20,"Large lyrics: "+on(s().lyricFocus),()->{s().lyricFocus=!s().lyricFocus;rebuildWidgets();});
                button(contentX,body+2*step,column,20,"Scene: Aurora",()->{s().scene("AURORA");rebuildWidgets();});
                button(right,body+2*step,column,20,"Scene: Vinyl",()->{s().scene("VINYL");rebuildWidgets();});
                button(contentX,body+3*step,column,20,"Scene: Lyric Lounge",()->{s().scene("LYRIC LOUNGE");rebuildWidgets();});
                button(right,body+3*step,column,20,"Scene: Reference",()->{s().scene("REFERENCE");rebuildWidgets();});
            }
        }
        addRenderableWidget(new StudioButton(left+panelWidth-74,top+panelHeight-30,60,22,"Done",this::onClose).selected(true));
        button(left+panelWidth-156,top+panelHeight-30,76,22,"Advanced",()->minecraft.setScreen(new AdvancedScreen(this)));
    }
    private static String on(boolean value) { return value?"On":"Off"; }
    void showTab(int value){tab=Math.max(0,Math.min(4,value));rebuildWidgets();}
    private static String next(String[] list,String value) { return list[(java.util.Arrays.asList(list).indexOf(value)+1)%list.length]; }
    private void button(int x,int y,int w,int h,String label,Runnable action) { addRenderableWidget(new StudioButton(x,y,w,y>=body&&y<top+panelHeight-40?controlHeight:h,label,action)); }
    private void color(int x,int y,int w,String label,int value,IntConsumer change) {
        int labelWidth=Math.min(58,w/3);fields.add(new Field(x,y,w,controlHeight,label));
        EditBox box=new EditBox(font,x+labelWidth,y+(controlHeight-8)/2,w-labelWidth-8,12,Component.literal(label+" hex color"));box.setBordered(false);box.setTextColor(0xffddd6ef);
        box.addFormatter((text,start)->Overlay.smoothText(text).getVisualOrderText());
        box.setMaxLength(7);box.setValue(String.format(java.util.Locale.ROOT,"#%06X",value));
        box.setResponder(text->{if(text.matches("#?[0-9a-fA-F]{6}")) { change.accept(Integer.parseInt(text.replace("#",""),16));s().theme="CUSTOM";validation="";box.setTextColor(0xffe8e4ff); }else {validation="Use six hex digits, e.g. #A99BFF";box.setTextColor(0xffff9caa);} });
        addRenderableWidget(box);
    }
    private void slider(int x,int y,int w,String label,double current,double min,double max,DoubleConsumer change) {
        addRenderableWidget(new AbstractSliderButton(x,y,w,controlHeight,Component.empty(),(current-min)/(max-min)) {
            { updateMessage(); }
            @Override protected void updateMessage() { setMessage(Component.literal(label+": "+String.format(java.util.Locale.ROOT,max>2?"%.0f":"%.2f",min+value*(max-min)))); }
            @Override protected void applyValue() { change.accept(min+value*(max-min)); }
            @Override public void renderWidget(GuiGraphics g,int mx,int my,float delta) {
                UiSkin.surface(g,getX(),getY(),getWidth(),getHeight(),isHoveredOrFocused()?UiSkin.HOVER:UiSkin.CARD);
                int trackY=getY()+getHeight()-5;Overlay.round(g,getX()+8,trackY,getWidth()-16,2,1,0xff393746);
                Overlay.round(g,getX()+8,trackY,(int)((getWidth()-16)*value),2,1,0xffc9a4f5);
                Overlay.round(g,getX()+6+(int)((getWidth()-16)*value),trackY-1,4,4,2,0xffeedcff);
                Overlay.text(g,Overlay.fit(getMessage().getString(),getWidth()-16),getX()+8,getY()+(getHeight()-12)/2,0xffeceafa);
            }
        });
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        UiSkin.backdrop(g,width,height,left,top,panelWidth,panelHeight);
        Overlay.text(g,"Better Music",left+14,top+13,0xfff2eafb);
        Overlay.text(g,TABS[tab]+" studio",contentX,top+13,0xffc5a7e5);
        g.fill(contentX-10,top+40,contentX-9,top+panelHeight-42,0xff262633);
        UiSkin.surface(g,previewX,previewY,previewWidth,previewHeight,UiSkin.FIELD);
        if(wide){Overlay.text(g,"LIVE PREVIEW",previewX+12,previewY+13,0xff938aab);Overlay.text(g,new String[]{"Make it yours","Find the right place","Your music, your controls","Lyrics, in time","Find your look"}[tab],contentX,top+48,0xfff1eafa);Overlay.text(g,Overlay.fit(tab==3?"LRCLIB lookup + your local lyrics.":"Changes appear as you edit.",contentWidth),contentX,top+62,0xff8d869e);}
        Track track=BetterMusic.media.track();
        float previewScale=(float)Math.min((previewWidth-16.0)/s().width,(previewHeight-(wide?40.0:8.0))/Overlay.height());
        previewScale=Math.min(1,previewScale);
        Overlay.draw(g,track.present()?track:Track.demo(),previewX+(int)((previewWidth-s().width*previewScale)/2),previewY+(int)((previewHeight-Overlay.height()*previewScale)/2)+(wide?8:0),previewScale);
        for(var field:fields){UiSkin.surface(g,field.x(),field.y(),field.w(),field.h(),UiSkin.FIELD);Overlay.text(g,field.label(),field.x()+8,field.y()+(field.h()-8)/2,0xffa49ab5);}
        if(wide){Overlay.text(g,s().layout+" / "+(s().smartHud?"AUTO COLLAPSE":"ALWAYS EXPANDED"),previewX+8,previewY+previewHeight+14,0xffaba0ba);Overlay.text(g,"O  Studio     M  Controls",previewX+8,previewY+previewHeight+31,0xff81768e);}
        String state=validation.isEmpty()?(s().streamer?"Streamer privacy enabled":tab==3?BetterMusic.lyrics.status():BetterMusic.media.status()):validation;
        Overlay.text(g,Overlay.fit(state,panelWidth-182),left+14,top+panelHeight-23,validation.isEmpty()?0xff9e9aad:0xffff9caa);
        super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) { }
    @Override public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent e,boolean doubleClick) {
        if(super.mouseClicked(e,doubleClick))return true;
        float scale=(float)Math.min(1,Math.min((previewWidth-16.0)/s().width,(previewHeight-(wide?40.0:8.0))/Overlay.height()));
        int x=previewX+(int)((previewWidth-s().width*scale)/2),y=previewY+(int)((previewHeight-Overlay.height()*scale)/2)+(wide?8:0);
        if(e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&Overlay.control((e.x()-x)/scale,(e.y()-y)/scale))return true;
        return false;
    }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { BetterMusic.save();minecraft.setScreen(parent); }
    @Override public void removed() { BetterMusic.save(); }

    static final class StudioButton extends Button {
        private boolean selected;
        StudioButton(int x,int y,int w,int h,String label,Runnable action) { super(x,y,w,h,Component.literal(label),b->action.run(),DEFAULT_NARRATION); }
        StudioButton selected(boolean value){selected=value;return this;}
        @Override protected void renderContents(GuiGraphics g,int mx,int my,float delta) {
            String text=getMessage().getString();boolean toggle=text.endsWith(": On")||text.endsWith(": Off"),on=text.endsWith(": On");
            UiSkin.surface(g,getX(),getY(),getWidth(),getHeight(),!active?UiSkin.MUTED:selected?UiSkin.SELECTED:isHoveredOrFocused()?UiSkin.HOVER:UiSkin.CARD);
            if(isFocused())Overlay.round(g,getX()+8,getY()+getHeight()-2,getWidth()-16,1,0,0xffc5a0f0);
            if(toggle){text=text.substring(0,text.lastIndexOf(':'));int sx=getX()+getWidth()-27,sy=getY()+(getHeight()-10)/2;Overlay.round(g,sx,sy,19,10,5,on?0xffb88ce5:0xff464151);Overlay.round(g,sx+(on?10:2),sy+2,6,6,3,on?0xff251632:0xffb6aabf);}
            Overlay.text(g,Overlay.fit(text,getWidth()-(toggle?36:16)),getX()+8,getY()+(getHeight()-8)/2,!active?0xff746b82:selected?0xffe0c3ff:0xffded7e8);
        }
    }
}
