package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.*;

/** A scrollable transcript: only supplied timestamps can seek or follow playback. */
public final class LyricsScreen extends Screen {
    private record Row(String text,int index,double time,int start,String stamp){}
    private final Screen parent;
    private int left,top,pw,ph,viewTop,viewBottom;
    private double scroll;
    private boolean follow=true;
    private String trackKey="",notice="";
    private List<Row> rows=List.of();
    private Object content;
    private Track displayed=Track.EMPTY;
    private LyricsService.Transcript transcript;
    private StudioScreen.StudioButton followButton;
    private final List<StudioScreen.StudioButton> timingButtons=new ArrayList<>();
    public LyricsScreen(Screen parent){super(Component.literal("Lyrics reader"));this.parent=parent;}
    @Override protected void init(){
        BetterMusic.lyrics.readerOpen(true);content=null;rows=List.of();transcript=null;
        timingButtons.clear();
        pw=Math.min(640,width-16);ph=height-16;left=(width-pw)/2;top=8;viewTop=top+99;viewBottom=top+ph-47;
        int bw=(pw-32)/3;
        timingButtons.add(button(left+12,top+45,bw,"Lyrics earlier +0.25s",()->nudge(.25)));
        timingButtons.add(button(left+16+bw,top+45,bw,"Lyrics later -0.25s",()->nudge(-.25)));
        timingButtons.add(button(left+20+bw*2,top+45,bw,"Reset song offset",()->{if(!currentTrack())return;var c=BetterMusic.lyrics.correction();try{BetterMusic.lyrics.saveCorrection(displayed,0,c.recording(),c.explicit());notice="Song offset reset";}catch(Exception e){notice="Could not save offset";}}));
        followButton=button(left+12,top+69,bw,follow?"Following playback":"Resume following",()->{follow=true;followButton.setMessage(Overlay.smoothText("Following playback"));});
        button(left+16+bw,top+69,bw,"LRCLIB lookup: "+(BetterMusic.settings.onlineLyrics?"On":"Off"),()->{BetterMusic.settings.onlineLyrics=!BetterMusic.settings.onlineLyrics;BetterMusic.lyrics.update(BetterMusic.media.track(),BetterMusic.settings);BetterMusic.save();rebuildWidgets();});
        button(left+20+bw*2,top+69,bw,"Reload lyrics",()->BetterMusic.lyrics.reload());
        button(left+pw-84,top+ph-28,72,"Back",this::onClose);
        button(left+pw-180,top+ph-28,90,"Find recording",()->{var more=new AdvancedScreen(this);minecraft.setScreen(more);more.showPage(4);});
        button(left+12,top+ph-28,80,"Import lyrics",()->minecraft.setScreen(new LyricsImportScreen(this)));
    }
    private StudioScreen.StudioButton button(int x,int y,int w,String label,Runnable action){return addRenderableWidget(new StudioScreen.StudioButton(x,y,w,20,label,action));}
    private boolean currentTrack(){if(!trackKey.equals(LyricCorrections.key(BetterMusic.media.track()))){notice="Track changed; wait for the new lyrics";return false;}return true;}
    private void nudge(double delta){if(!currentTrack())return;try{BetterMusic.lyrics.nudge(displayed,delta);notice="Saved song offset "+String.format(Locale.ROOT,"%+.2fs",BetterMusic.lyrics.correction().offset());}catch(Exception e){notice="Start a track before adjusting sync";}}
    @Override public void tick(){
        displayed=BetterMusic.media.track();String id=LyricCorrections.key(displayed);
        if(!id.equals(trackKey)){trackKey=id;scroll=0;follow=true;notice="";content=null;followButton.setMessage(Overlay.smoothText("Following playback"));}
        transcript=BetterMusic.lyrics.transcript(displayed,BetterMusic.settings);
        boolean timed=!transcript.lines().isEmpty();followButton.active=timed;
        followButton.setMessage(Overlay.smoothText(timed?(follow?"Following playback":"Resume following"):"Manual reading"));
        for(var button:timingButtons)button.active=timed;
        Object next=transcript.lines().isEmpty()?transcript.plain():transcript.lines();
        if(next!=content){content=next;var result=new ArrayList<Row>();
            if(!transcript.lines().isEmpty())for(int i=0;i<transcript.lines().size();i++){
                var line=transcript.lines().get(i);int start=0,seconds=(int)line.time();String stamp=String.format(Locale.ROOT,"%d:%02d",seconds/60,seconds%60);
                for(String text:wrap(line.text().isBlank()?"♪ Instrumental":line.text(),pw-76)){int at=Math.max(0,line.text().indexOf(text,start));result.add(new Row(text,i,line.time(),at,stamp));start=at+text.length();}
            }
            else for(String line:transcript.plain())for(String text:wrap(line,pw-36))result.add(new Row(text,-1,Double.NaN,0,""));
            rows=List.copyOf(result);
        }
        if(follow&&!transcript.lines().isEmpty()){
            int row=0;for(int i=0;i<rows.size();i++)if(rows.get(i).index()==transcript.current()){row=i;break;}
            double target=Math.max(0,row*20-(viewBottom-viewTop)/3.0);
            if(Math.abs(target-scroll)>100||!BetterMusic.settings.animation)scroll=target;else scroll+=(target-scroll)*.35;
        }
        scroll=Settings.clamp(scroll,0,Math.max(0,rows.size()*20-(viewBottom-viewTop)));
    }
    private List<String> wrap(String value,int available){
        var parts=new ArrayList<String>();String rest=value;
        while(!rest.isEmpty()){
            int low=1,high=rest.codePointCount(0,rest.length()),count=0;
            while(low<=high){int mid=(low+high)>>>1,end=rest.offsetByCodePoints(0,mid);if(font.width(Overlay.smoothText(rest.substring(0,end)))<=available){count=mid;low=mid+1;}else high=mid-1;}
            int end=rest.offsetByCodePoints(0,Math.max(1,count));
            if(end<rest.length()){int space=rest.lastIndexOf(' ',end);if(space>0)end=space;}
            parts.add(rest.substring(0,end));rest=rest.substring(end).stripLeading();
        }
        return parts.isEmpty()?List.of(""):parts;
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        UiSkin.backdrop(g,width,height,left,top,pw,ph);
        Track safe=Experience.privateTrack(displayed,BetterMusic.settings);
        String title=Experience.hidden(displayed,BetterMusic.settings)?"Private listening":safe.present()?safe.title():"Lyrics reader";
        Overlay.text(g,Overlay.fit(title,pw-28),left+14,top+12,0xffeeeafa);
        boolean timed=transcript!=null&&!transcript.lines().isEmpty();
        String state=transcript==null?"Waiting for a track":transcript.source().isBlank()?transcript.status():transcript.source();
        Overlay.text(g,Overlay.fit(state+" / "+(timed?"CLICK A LINE TO SEEK":"READ WITHOUT TIMING"),pw-28),left+14,top+28,0xffaaa5bf);
        g.enableScissor(left+8,viewTop,left+pw-8,viewBottom);
        if(rows.isEmpty())Overlay.text(g,Overlay.fit(transcript==null?"Waiting for lyrics ...":transcript.status(),pw-32),left+16,viewTop+10,0xffaaa5bf);
        for(int i=Math.max(0,(int)scroll/20);i<Math.min(rows.size(),(int)((scroll+viewBottom-viewTop)/20)+2);i++){
            Row row=rows.get(i);int y=viewTop+i*20-(int)scroll;
            boolean active=timed&&row.index()==transcript.current();
            var line=timed?transcript.lines().get(row.index()):null;
            if(active&&Double.isFinite(line.end())&&transcript.position()>=line.end())active=false;
            if(active){UiSkin.surface(g,left+10,y-2,pw-20,20,UiSkin.SELECTED);Overlay.round(g,left+10,y+3,2,10,1,0xffcba2ef);}
            if(timed)Overlay.text(g,row.stamp(),left+16,y+4,0xff8e879e);
            Overlay.text(g,row.text(),left+(timed?56:16),y+4,active?0xffeae1f4:0xffb0a7bf);
            if(active&&BetterMusic.settings.wordHighlight&&!line.words().isEmpty()){
                int end=Math.max(0,Math.min(row.text().length(),line.highlighted(transcript.position()).length()-row.start()));
                if(end>0)Overlay.text(g,row.text().substring(0,end),left+56,y+4,0xffcba2ef);
            }
        }
        g.disableScissor();
        String footer=notice.isBlank()?(timed?"Scroll to browse; resume to follow.":"Plain text · scroll to read."):notice;
        Overlay.text(g,Overlay.fit(footer,pw-28),left+14,top+ph-41,0xffaaa5bf);super.render(g,mx,my,delta);
    }
    @Override public boolean mouseScrolled(double x,double y,double horizontal,double vertical){
        if(x>=left&&x<=left+pw&&y>=viewTop&&y<=viewBottom){follow=false;scroll=Settings.clamp(scroll-vertical*40,0,Math.max(0,rows.size()*20-(viewBottom-viewTop)));followButton.setMessage(Overlay.smoothText("Resume following"));return true;}
        return super.mouseScrolled(x,y,horizontal,vertical);
    }
    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(super.mouseClicked(event,doubleClick))return true;
        if(event.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&event.x()>=left+8&&event.x()<=left+pw-8&&event.y()>=viewTop&&event.y()<viewBottom){
            int index=(int)((event.y()-viewTop+scroll)/20);if(index>=0&&index<rows.size()&&Double.isFinite(rows.get(index).time())){
                double offset=BetterMusic.settings.lyricsOffset+BetterMusic.lyrics.correction().offset();
                BetterMusic.media.seekSeconds(rows.get(index).time()-offset,displayed);follow=true;notice="Seek requested (player support required)";return true;
            }
        }
        return false;
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void removed(){BetterMusic.lyrics.readerOpen(false);}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
