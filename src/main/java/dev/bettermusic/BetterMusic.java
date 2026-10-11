package dev.bettermusic;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import com.mojang.blaze3d.platform.InputConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.nio.file.Path;

public final class BetterMusic implements ClientModInitializer {
    public static final Logger LOG=LoggerFactory.getLogger("BetterMusic");
    public static Settings settings=new Settings();
    public static MediaService media;
    public static LyricsService lyrics;
    public static final CoverArt art=new CoverArt();
    public static final Experience experience=new Experience();
    public static Path profiles(){return config.getParent().resolve("better-music-profiles");}
    private static Path config;
    private static float lastDuck=1;
    public static void save() { settings.save(config); }
    @Override public void onInitializeClient() {
        config=FabricLoader.getInstance().getConfigDir().resolve("better-music-overlay.json");
        settings=Settings.load(config);
        lyrics=new LyricsService(FabricLoader.getInstance().getConfigDir().resolve("better-music-lyrics"));
        media=new MediaService(FabricLoader.getInstance().getGameDir().resolve(".better-music"));
        media.start();
        var category=KeyMapping.Category.register(Identifier.fromNamespaceAndPath("bettermusic","music"));
        var editor=key("editor",InputConstants.KEY_O,category);
        var interact=key("interact",InputConstants.KEY_M,category);
        var toggle=key("toggle",InputConstants.UNKNOWN.getValue(),category);
        var play=key("play",InputConstants.UNKNOWN.getValue(),category);
        var next=key("next",InputConstants.UNKNOWN.getValue(),category);
        var previous=key("previous",InputConstants.UNKNOWN.getValue(),category);
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while(editor.consumeClick()) client.setScreen(new StudioScreen(client.screen));
            while(interact.consumeClick()) client.setScreen(new ControlScreen(client.screen));
            while(toggle.consumeClick()) { settings.enabled=!settings.enabled;save(); }
            while(play.consumeClick()) media.command("play");
            while(next.consumeClick()) media.command("next");
            while(previous.consumeClick()) media.command("previous");
            art.update(media.track().cover());
            lyrics.update(media.track(),settings);
            experience.tick(media.track(),client.player!=null&&client.player.hurtTime>0,System.nanoTime()/1e9,settings);
            float duck=experience.duck(media.track().present()&&media.track().playing(),settings);
            if(settings.duckMusic||lastDuck!=1)client.getSoundManager().updateCategoryVolume(net.minecraft.sounds.SoundSource.MUSIC,duck);
            lastDuck=duck;
        });
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,Identifier.fromNamespaceAndPath("bettermusic","overlay"),(g,delta)-> {
            var mc=net.minecraft.client.Minecraft.getInstance();
            if(mc.screen instanceof StudioScreen || mc.screen instanceof AdvancedScreen || mc.screen instanceof LyricsScreen || mc.screen instanceof LyricsImportScreen || mc.screen instanceof PositionScreen || mc.screen instanceof ControlScreen || !settings.enabled)return;
            Track track=media.track();
            if(!track.present() && settings.hideIdle)return;
            Overlay.drawHud(g,track,mc.getWindow().getGuiScaledWidth(),mc.getWindow().getGuiScaledHeight());
        });
        ClientCommandRegistrationCallback.EVENT.register((dispatcher,access)->dispatcher.register(literal("music").executes(context->{
            var client=context.getSource().getClient();client.schedule(()->client.setScreen(new StudioScreen(client.screen)));return 1;
        })));
        ClientLifecycleEvents.CLIENT_STOPPING.register(client->{if(lastDuck!=1)client.getSoundManager().updateCategoryVolume(net.minecraft.sounds.SoundSource.MUSIC,1);media.close();lyrics.close();art.close();Overlay.close();save();});
    }
    private static KeyMapping key(String name,int code,KeyMapping.Category category) {
        return KeyBindingHelper.registerKeyBinding(new KeyMapping("key.bettermusic."+name,code,category));
    }
}
