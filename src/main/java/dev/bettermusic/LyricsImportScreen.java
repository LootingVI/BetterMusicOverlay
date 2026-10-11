package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import java.nio.file.Path;
import java.util.List;

/** File drop and clipboard import require no provider account. */
final class LyricsImportScreen extends Screen {
    private final Screen parent;
    private int x,y,w,h;
    private LyricImport.Preview preview;
    private Track destination;
    private String message="Drop one .lrc or .txt file onto this screen.";
    private StudioScreen.StudioButton save;
    LyricsImportScreen(Screen parent){super(Component.literal("Import lyrics"));this.parent=parent;}
    @Override protected void init(){
        w=Math.min(560,width-24);h=Math.min(310,height-24);x=(width-w)/2;y=(height-h)/2;
        button(x+14,y+h-58,(w-34)/2,"Paste clipboard",()->{try{prepare(LyricImport.inspect(minecraft.keyboardHandler.getClipboard()));}catch(Exception e){message=e.getMessage();}});
        button(x+20+(w-34)/2,y+h-58,(w-34)/2,"Open local folder",()->{try{java.awt.Desktop.getDesktop().open(BetterMusic.lyrics.folder().toFile());}catch(Exception e){message="Folder: config/better-music-lyrics";}});
        save=button(x+w-176,y+h-28,90,"Save lyrics",()->{try{
            if(destination==null||!LyricCorrections.key(destination).equals(LyricCorrections.key(BetterMusic.media.track())))throw new IllegalArgumentException("Track changed. Paste or drop the lyrics again.");
            LyricImport.save(BetterMusic.lyrics.folder(),destination,preview);BetterMusic.lyrics.reload();minecraft.setScreen(new LyricsScreen(parent));
        }catch(Exception e){message=e.getMessage();}});save.active=preview!=null;
        button(x+w-80,y+h-28,66,"Back",this::onClose);
    }
    private StudioScreen.StudioButton button(int x,int y,int w,String text,Runnable action){return addRenderableWidget(new StudioScreen.StudioButton(x,y,w,22,text,action));}
    private void prepare(LyricImport.Preview value){preview=value;destination=BetterMusic.media.track();save.active=destination.present();message=destination.present()?"Ready to save. Previous local lyrics are backed up.":"Start a track, then paste or drop the lyrics again.";}
    @Override public void onFilesDrop(List<Path> files){if(files.size()!=1){message="Drop one lyrics file at a time.";return;}try{prepare(LyricImport.read(files.getFirst()));}catch(Exception e){message=e.getMessage();}}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        UiSkin.backdrop(g,width,height,x,y,w,h);Overlay.text(g,"Bring your own lyrics",x+16,y+16,0xfff0e8fc);
        Overlay.text(g,"LRC, Enhanced LRC, or plain text. No account needed.",x+16,y+34,0xff9e90b1);
        UiSkin.surface(g,x+14,y+56,w-28,h-133,UiSkin.FIELD);
        Track shown=Experience.privateTrack(preview==null?BetterMusic.media.track():destination,BetterMusic.settings);
        Overlay.text(g,Overlay.fit(shown.present()?shown.title():"Play a song to select the destination",w-52),x+26,y+70,0xffd7b9f7);
        String detail=preview==null?"Drop a file here or paste from your clipboard.":preview.lines()+" lines / "+(preview.timed()?"SYNCHRONIZED":"PLAIN TEXT");
        Overlay.text(g,Overlay.fit(detail,w-52),x+26,y+88,0xffb9acc7);
        if(h>=270&&preview!=null&&!BetterMusic.settings.streamer)Overlay.text(g,Overlay.fit(preview.text().lines().findFirst().orElse(""),w-52),x+26,y+110,0xff8e819f);
        Overlay.text(g,Overlay.fit(message,w-28),x+14,y+h-73,0xffa89ab9);super.render(g,mx,my,delta);
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
