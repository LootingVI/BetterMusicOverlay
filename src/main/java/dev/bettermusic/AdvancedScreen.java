package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.network.chat.Component;

/** Secondary studio pages keep the visual editor compact. */
public final class AdvancedScreen extends Screen {
    private final Screen parent;
    private int page,left,top,pw,ph,row,col,selected,profile,playerPage;
    private String message="",trackKey="";
    private Track correctionTrack=Track.EMPTY;
    private double offset;
    private StudioScreen.StudioButton candidateChoice;
    private long tooltipId=-1;
    private static final String[] PROFILES={"Building","Exploration","Streaming","Custom 1","Custom 2"};
    public AdvancedScreen(Screen parent){super(Component.literal("Better Music / More"));this.parent=parent;}
    private Settings s(){return BetterMusic.settings;}
    private static String on(boolean value){return value?"On":"Off";}
    private void change(Runnable action){action.run();BetterMusic.save();rebuildWidgets();}
    @Override protected void init(){
        candidateChoice=null;tooltipId=-1;
        pw=Math.min(540,width-16);ph=Math.min(284,height-16);left=(width-pw)/2;top=(height-ph)/2;col=(pw-32)/2;row=top+60;
        for(int i=0;i<5;i++){int p=i;button(left+12+i*(pw-24)/5,top+32,(pw-40)/5,new String[]{"Smart HUD","Players","Profiles","Privacy","Correction"}[i],()->{page=p;message="";rebuildWidgets();});}
        int a=left+12,b=left+20+col;
        switch(page){
            case 0 -> {
                button(a,row,col,"Smart collapse: "+on(s().smartHud),()->change(()->s().smartHud=!s().smartHud));
                button(b,row,col,"Damage fade: "+on(s().combatFade),()->change(()->s().combatFade=!s().combatFade));
                button(a,row+24,col,"Expanded: "+(int)s().expandSeconds+" seconds",()->change(()->s().expandSeconds=s().expandSeconds>=30?2:s().expandSeconds+2));
                button(b,row+24,col,"Artwork crossfade: "+on(s().transitions),()->change(()->s().transitions=!s().transitions));
                button(a,row+48,col,"Duck game music: "+on(s().duckMusic),()->change(()->s().duckMusic=!s().duckMusic));
                button(b,row+48,col,"Ducked volume: "+Math.round(s().duckLevel*100)+"%",()->change(()->s().duckLevel=s().duckLevel>=.99?0:Math.min(1,s().duckLevel+.1)));
                button(a,row+72,col,"Word highlighting: "+on(s().wordHighlight),()->change(()->s().wordHighlight=!s().wordHighlight));
                button(b,row+72,col,"Snap guides: "+on(s().snap),()->change(()->s().snap=!s().snap));
            }
            case 1 -> {
                button(a,row,col,"Select automatic player",()->change(()->s().preferred=""));
                button(b,row,col,"Refresh / next players",()->{playerPage++;rebuildWidgets();});
                var sessions=BetterMusic.media.sessions();
                int start=sessions.isEmpty()?0:(playerPage%((sessions.size()+3)/4))*4;
                for(int i=start;i<Math.min(start+4,sessions.size());i++){
                    var session=sessions.get(i);String label=s().streamer?"Player "+(i+1):session.id();
                    button(a,row+24+(i-start)*24,pw-24,(session.id().equals(s().preferred)?"Selected · ":session.playing()?"Playing · ":"Paused · ")+label,()->change(()->s().preferred=session.id()));
                }
                if(sessions.isEmpty())message="Start a media app, then refresh.";
            }
            case 2 -> {
                button(a,row,col,"Profile: "+PROFILES[profile],()->{profile=(profile+1)%PROFILES.length;rebuildWidgets();});
                button(b,row,col,"Save this appearance",()->{try{Profiles.save(BetterMusic.profiles(),PROFILES[profile],s());message="Saved "+PROFILES[profile];}catch(Exception e){message="Save failed: "+e.getMessage();}});
                button(a,row+24,col,"Load profile",()->{try{BetterMusic.settings=Profiles.load(BetterMusic.profiles(),PROFILES[profile],s());BetterMusic.save();message="Loaded "+PROFILES[profile];}catch(Exception e){message="Save this profile before loading it.";}});
                button(b,row+24,col,"Copy theme JSON",()->{minecraft.keyboardHandler.setClipboard(Profiles.exportTheme(s()));message="Theme copied to clipboard";});
                button(a,row+48,col,"Import clipboard theme",()->{try{BetterMusic.settings=Profiles.importTheme(minecraft.keyboardHandler.getClipboard(),s());BetterMusic.save();message="Theme imported";}catch(Exception e){message="Invalid theme; current settings kept";}});
                button(b,row+48,col,"Open profiles folder",()->{try{java.nio.file.Files.createDirectories(BetterMusic.profiles());java.awt.Desktop.getDesktop().open(BetterMusic.profiles().toFile());}catch(Exception e){message="Folder: config/better-music-profiles";}});
                button(a,row+72,pw-24,"Open drag / resize editor with undo",()->minecraft.setScreen(new PositionScreen(this)));
            }
            case 3 -> {
                button(a,row,col,"Streamer mode: "+on(s().streamer),()->change(()->s().streamer=!s().streamer));
                button(b,row,col,"Hide track identity: "+on(s().hideTitles),()->change(()->s().hideTitles=!s().hideTitles));
                button(a,row+24,pw-24,"Lyrics privacy: "+(s().hideLyrics?"Hide all (safest)":"Hide manually marked explicit tracks"),()->change(()->s().hideLyrics=!s().hideLyrics));
                field(a,row+68,pw-24,"Hide these players (comma separated)",s().hiddenPlayers,v->s().hiddenPlayers=v);
            }
            default -> {
                button(a,row,col,"Song offset: "+String.format(java.util.Locale.ROOT,"%+.2f s",offset),()->{});
                button(b,row,col,"Save song offset",()->saveCorrection(BetterMusic.lyrics.correction().recording(),BetterMusic.lyrics.correction().explicit()));
                button(a,row+24,col,"Offset -0.25 s",()->{offset=Settings.clamp(offset-.25,-30,30);rebuildWidgets();});
                button(b,row+24,col,"Offset +0.25 s",()->{offset=Settings.clamp(offset+.25,-30,30);rebuildWidgets();});
                button(a,row+48,col,"Search alternate recordings",()->{selected=0;BetterMusic.lyrics.searchCandidates(correctionTrack,s());});
                button(b,row+48,col,"Next candidate",()->{selected++;rebuildWidgets();});
                candidateChoice=button(a,row+72,col,"Use displayed recording",()->{var list=BetterMusic.lyrics.candidates();if(list.isEmpty()||!BetterMusic.lyrics.candidatesFor(correctionTrack)){message="Search for this recording first";return;}saveCorrection(list.get(selected%list.size()).id(),BetterMusic.lyrics.correction().explicit());});
                button(b,row+72,col,"Explicit: "+on(BetterMusic.lyrics.correction().explicit()),()->saveCorrection(BetterMusic.lyrics.correction().recording(),!BetterMusic.lyrics.correction().explicit()));
                button(left+pw-78,top+7,66,"Reset song",()->{try{BetterMusic.lyrics.resetCorrection(correctionTrack);offset=0;message="Correction reset";rebuildWidgets();}catch(Exception e){message="Could not reset correction";}});
            }
        }
        button(left+pw-84,top+ph-28,72,"Back",this::onClose);
    }
    private void saveCorrection(long id,boolean explicit){
        if(!LyricCorrections.key(BetterMusic.media.track()).equals(trackKey)){message="Track changed; reopen corrections";return;}
        try{BetterMusic.lyrics.saveCorrection(correctionTrack,offset,id,explicit);message="Correction saved for this recording";rebuildWidgets();}catch(Exception e){message="Could not save correction";}
    }
    @Override public void tick(){
        Track track=BetterMusic.media.track();String key=LyricCorrections.key(track);
        if(!key.equals(trackKey)){trackKey=key;correctionTrack=track;offset=BetterMusic.lyrics.correction().offset();selected=0;if(page==4)rebuildWidgets();}
    }
    private StudioScreen.StudioButton button(int x,int y,int w,String label,Runnable action){return addRenderableWidget(new StudioScreen.StudioButton(x,y,w,20,label,action));}
    private void field(int x,int y,int w,String hint,String value,java.util.function.Consumer<String> action){var box=new EditBox(font,x,y,w,20,Component.literal(hint));box.addFormatter((text,start)->Overlay.smoothText(text).getVisualOrderText());box.setMaxLength(500);box.setValue(value);box.setHint(Overlay.smoothText(hint));box.setResponder(action);addRenderableWidget(box);}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0x9b070910);Overlay.round(g,left,top,pw,ph,12,0xfa11121b);
        Overlay.text(g,Overlay.fit("BETTER MUSIC / "+new String[]{"BEHAVIOR","PLAYERS","PROFILES","STREAMER PRIVACY","LYRIC CORRECTION"}[page],pw-(page==4?105:28)),left+14,top+13,0xffeeeafa);
        String hint=switch(page){case 0->"Word sync requires timed words in an Enhanced LRC file.";case 1->"Players must expose a Windows media session.";case 2->"Profiles share appearance. Network and privacy choices stay yours.";case 3->"No automatic explicit detection. Hide all lyrics for full privacy.";default->BetterMusic.lyrics.candidateStatus();};
        if(page==3)Overlay.text(g,"Hide player IDs containing (comma separated):",left+12,row+54,0xffaaa5bf);
        int info=Math.min(row+126,top+ph-65);
        if(page==4){
            var list=BetterMusic.lyrics.candidates();
            if(candidateChoice!=null&&(list.isEmpty()||s().streamer)){candidateChoice.setTooltip(null);tooltipId=-1;}
            if(candidateChoice!=null&&!list.isEmpty()&&!s().streamer){var c=list.get(selected%list.size());if(tooltipId!=c.id()){tooltipId=c.id();candidateChoice.setTooltip(net.minecraft.client.gui.components.Tooltip.create(Overlay.smoothText(c.title()+"\n"+c.artist()+"\n"+c.album()+"\nDuration: "+Math.round(c.duration())+"s\nLRCLIB ID: "+c.id())));}}
            String candidate=list.isEmpty()?"No alternate recording selected":s().streamer?"Candidate "+(selected%list.size()+1)+" / "+list.size():candidate(list.get(selected%list.size()));
            Overlay.text(g,Overlay.fit(candidate,pw-24),left+12,info,0xffc9b3ff);
        }
        Overlay.text(g,Overlay.fit(hint,pw-24),left+12,top+ph-48,0xffaaa5bf);
        Overlay.text(g,Overlay.fit(message,pw-114),left+12,top+ph-21,0xffc9b3ff);
        super.render(g,mx,my,delta);
    }
    private static String candidate(LyricsService.Candidate c){return (c.synced()?"SYNC · ":"PLAIN · ")+c.title()+" · "+c.artist()+" · "+c.album()+" · "+Math.round(c.duration())+"s";}
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public boolean isPauseScreen(){return false;}
    void showPage(int value){page=Math.max(0,Math.min(4,value));rebuildWidgets();}
    @Override public void onClose(){BetterMusic.save();minecraft.setScreen(parent);}
    @Override public void removed(){BetterMusic.save();}
}
