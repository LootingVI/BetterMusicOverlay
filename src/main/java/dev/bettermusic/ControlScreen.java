package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class ControlScreen extends Screen {
    private final Screen parent;
    public ControlScreen(Screen parent){super(Component.literal("Music controls"));this.parent=parent;}
    @Override protected void init(){
        addRenderableWidget(new StudioScreen.StudioButton(width/2-126,height-32,122,20,"Open lyrics reader",()->minecraft.setScreen(new LyricsScreen(this))));
        addRenderableWidget(new StudioScreen.StudioButton(width/2+4,height-32,122,20,"Open music studio",()->minecraft.setScreen(new StudioScreen(parent))));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        Overlay.drawAtPosition(g,BetterMusic.media.track(),width,height);
        Overlay.text(g,"Click the playback controls. Esc to return to the game.",12,height-48,0xffeceafa);
        super.render(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick){
        if(super.mouseClicked(e,doubleClick))return true;
        float scale=Overlay.effectiveScale(width,height);
        if(e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&Overlay.control((e.x()-Overlay.x(width,height))/scale,(e.y()-Overlay.y(width,height))/scale))return true;
        return false;
    }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta){}
    @Override public boolean isPauseScreen(){return false;}
    @Override public void onClose(){minecraft.setScreen(parent);}
}
