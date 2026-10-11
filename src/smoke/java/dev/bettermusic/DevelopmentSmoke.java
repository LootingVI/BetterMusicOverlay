package dev.bettermusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Component;
import java.nio.file.Files;

/** Included only by -Psmoke. Opens the real UI, captures it, and exits the development client. */
public final class DevelopmentSmoke implements ClientModInitializer {
    private int ticks;
    private boolean started;
    private Track sample(boolean plain){return new Track(plain?"Original plain lyrics":"Small worlds, wide skies","Better Music demo","SMOKE",false,12,60,"",System.nanoTime(),"Original examples");}
    @Override public void onInitializeClient(){
        BetterMusic.media.close();
        try{
            Files.writeString(BetterMusic.lyrics.folder().resolve(LyricsService.filename(sample(false))),"[00:00]A quiet start, a little room to breathe\n[00:05]Take the long way through the trees\n[00:10]<00:10>Make <00:11>room <00:12>for <00:13>your <00:14>music<00:15>\n[00:20]Small worlds, wide skies\n[00:25]Follow the light beyond the hillside\n[00:30]Your soundtrack travels with you\n[00:40]A new path, another morning\n[00:50]Stay in your world");
            Files.writeString(BetterMusic.lyrics.folder().resolve(LyricsService.filename(sample(true)).replace(".lrc",".txt")),"These original words have no timestamps.\nRead them at your own pace.\nA little music, a little room to breathe.");
            BetterMusic.lyrics.resetCorrection(sample(false));
        }catch(Exception e){throw new RuntimeException(e);}
        ClientTickEvents.END_CLIENT_TICK.register(client->{
            if(!started){if(!(client.screen instanceof TitleScreen))return;started=true;}
            ticks++;
            try{var field=MediaService.class.getDeclaredField("track");field.setAccessible(true);field.set(BetterMusic.media,sample(ticks>=400));var received=MediaService.class.getDeclaredField("lastUpdate");received.setAccessible(true);received.setLong(BetterMusic.media,System.nanoTime());}catch(Exception e){throw new RuntimeException(e);}
            if(ticks==40){BetterMusic.settings.scene("REFERENCE");BetterMusic.settings.streamer=false;BetterMusic.settings.onlineLyrics=false;BetterMusic.settings.smartHud=true;client.options.guiScale().set(2);client.resizeDisplay();var studio=new StudioScreen(client.screen);client.setScreen(studio);studio.showTab(0);}
            if(ticks==60){click(client.screen,"Layout: STUDIO");if(!BetterMusic.settings.layout.equals("AURORA")||BetterMusic.settings.smartHud)throw new AssertionError("Layout button did not disable collapse");BetterMusic.LOG.info("SMOKE PASS: layout button selected Aurora and disabled collapse");}
            if(ticks==80)Screenshot.grab(client.gameDirectory,"studio-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==90)client.setScreen(new HudScreen());
            if(ticks==98){if(!BetterMusic.experience.presentation(BetterMusic.settings,System.nanoTime()/1e9+100).layout.equals("AURORA"))throw new AssertionError("HUD reverted to simple layout");Screenshot.grab(client.gameDirectory,"selected-hud-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));}
            if(ticks==100){BetterMusic.settings.scene("LYRIC LOUNGE");client.setScreen(new PositionScreen(client.screen));}
            if(ticks==140)Screenshot.grab(client.gameDirectory,"overlay-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==160){var more=new AdvancedScreen(client.screen);client.setScreen(more);more.showPage(0);}
            if(ticks==185)Screenshot.grab(client.gameDirectory,"behavior-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==200){var more=new AdvancedScreen(client.screen);client.setScreen(more);more.showPage(4);}
            if(ticks==225)Screenshot.grab(client.gameDirectory,"corrections-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==240){var more=new AdvancedScreen(client.screen);client.setScreen(more);more.showPage(3);}
            if(ticks==265)Screenshot.grab(client.gameDirectory,"privacy-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==280){client.options.guiScale().set(4);client.resizeDisplay();var more=new AdvancedScreen(client.screen);client.setScreen(more);more.showPage(4);}
            if(ticks==305)Screenshot.grab(client.gameDirectory,"compact-corrections-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==320){client.options.guiScale().set(2);client.resizeDisplay();client.setScreen(new LyricsScreen(client.screen));}
            if(ticks==350)Screenshot.grab(client.gameDirectory,"lyrics-reader-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==360){click(client.screen,"Lyrics earlier +0.25s");if(BetterMusic.lyrics.correction().offset()!=.25)throw new AssertionError("Offset button failed");BetterMusic.LOG.info("SMOKE PASS: lyrics reader timing button persisted +0.25s");}
            if(ticks==380){client.options.guiScale().set(4);client.resizeDisplay();}
            if(ticks==390)Screenshot.grab(client.gameDirectory,"compact-reader-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==400)client.setScreen(new LyricsScreen(client.screen));
            if(ticks==420)Screenshot.grab(client.gameDirectory,"plain-reader-smoke.png",client.getMainRenderTarget(),1,message->BetterMusic.LOG.info("Smoke screenshot: {}",message.getString()));
            if(ticks==440)client.stop();
        });
    }
    private static void click(Screen screen,String label){
        for(var child:screen.children())if(child instanceof Button button&&button.getMessage().getString().equals(label)){
            var event=new MouseButtonEvent(button.getX()+button.getWidth()/2.0,button.getY()+button.getHeight()/2.0,new MouseButtonInfo(com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT,0));
            if(!screen.mouseClicked(event,false))throw new AssertionError("Button ignored click: "+label);screen.mouseReleased(event);return;
        }
        throw new AssertionError("Button missing: "+label);
    }
    private static final class HudScreen extends Screen {
        HudScreen(){super(Component.literal("HUD smoke test"));}
        @Override public void render(GuiGraphics g,int mx,int my,float delta){g.fill(0,0,width,height,0xff121722);Overlay.drawHud(g,BetterMusic.media.track(),width,height);}
        @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
        @Override public boolean isPauseScreen(){return false;}
    }
}
