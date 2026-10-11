package dev.bettermusic;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class PositionScreen extends Screen {
    private final Screen parent;
    private boolean moving;
    private boolean resizing;
    private double startScale,startMouseX;
    private final java.util.ArrayDeque<Settings> history=new java.util.ArrayDeque<>();
    private double dx,dy;
    public PositionScreen(Screen parent) { super(Component.literal("Position your music overlay"));this.parent=parent; }
    @Override protected void init() {
        addRenderableWidget(new StudioScreen.StudioButton(width/2-104,height-30,100,20,"Save position",this::onClose));
        addRenderableWidget(new StudioScreen.StudioButton(width/2+4,height-30,100,20,"Undo",()->{if(!history.isEmpty())BetterMusic.settings=history.removeLast();}));
    }
    @Override public void render(GuiGraphics g,int mx,int my,float delta) {
        g.fill(0,0,width,height,0x40070910);
        for(int x=0;x<width;x+=20)g.fill(x,0,x+1,height,0x10ffffff);
        for(int y=0;y<height;y+=20)g.fill(0,y,width,y+1,0x10ffffff);
        Track track=BetterMusic.media.track();
        Overlay.drawAtPosition(g,track.present()?track:Track.demo(),width,height);
        int x=Overlay.x(width,height),y=Overlay.y(width,height);float scale=Overlay.effectiveScale(width,height);
        int right=x+(int)(BetterMusic.settings.width*scale),bottom=y+(int)(Overlay.height()*scale);
        Overlay.round(g,right-10,bottom-10,10,10,3,0xffc4b2ff);
        if(BetterMusic.settings.snap&&moving){
            if(Math.abs(BetterMusic.settings.x-.5)<.001)g.fill(width/2,0,width/2+1,height,0x88c4b2ff);
            if(Math.abs(BetterMusic.settings.y-.5)<.001)g.fill(0,height/2,width,height/2+1,0x88c4b2ff);
            if(BetterMusic.settings.x==0||BetterMusic.settings.x==1)g.fill(BetterMusic.settings.x==0?0:width-1,0,BetterMusic.settings.x==0?1:width,height,0x88c4b2ff);
            if(BetterMusic.settings.y==0||BetterMusic.settings.y==1)g.fill(0,BetterMusic.settings.y==0?0:height-1,width,BetterMusic.settings.y==0?1:height,0x88c4b2ff);
        }
        Overlay.round(g,8,height-49,width-16,15,4,0xdd11121b);
        Overlay.text(g,Overlay.fit("Drag to move. Drag the corner to resize. Undo restores your last edit.",width-30),14,height-45,0xffeae7fa);
        super.render(g,mx,my,delta);
    }
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean doubleClick) {
        if(super.mouseClicked(e,doubleClick))return true;
        double x=Overlay.x(width,height),y=Overlay.y(width,height),scale=Overlay.effectiveScale(width,height);
        if(e.button()==com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT&&e.x()>=x&&e.x()<=x+BetterMusic.settings.width*scale&&e.y()>=y&&e.y()<=y+Overlay.height()*scale) {
            if(history.size()>=30)history.removeFirst();history.addLast(BetterMusic.settings.copy());
            resizing=e.x()>x+BetterMusic.settings.width*scale-14&&e.y()>y+Overlay.height()*scale-14;
            moving=!resizing;startScale=BetterMusic.settings.scale;startMouseX=e.x();dx=e.x()-x;dy=e.y()-y;return true;
        }
        return false;
    }
    @Override public boolean mouseDragged(MouseButtonEvent e,double deltaX,double deltaY) {
        if(resizing){BetterMusic.settings.scale=Settings.clamp(startScale+(e.x()-startMouseX)/BetterMusic.settings.width,.6,2);return true;}
        if(moving) {
            double scale=Overlay.effectiveScale(width,height);
            BetterMusic.settings.x=Settings.clamp((e.x()-dx)/Math.max(1,width-BetterMusic.settings.width*scale),0,1);
            BetterMusic.settings.y=Settings.clamp((e.y()-dy)/Math.max(1,height-Overlay.height()*scale),0,1);
            if(BetterMusic.settings.snap){BetterMusic.settings.x=snap(BetterMusic.settings.x,width-BetterMusic.settings.width*scale);BetterMusic.settings.y=snap(BetterMusic.settings.y,height-Overlay.height()*scale);}
            return true;
        }
        return super.mouseDragged(e,deltaX,deltaY);
    }
    static double snap(double value,double range){for(double anchor:new double[]{0,.5,1})if(Math.abs(value-anchor)*Math.max(1,range)<6)return anchor;return value;}
    @Override public boolean mouseReleased(MouseButtonEvent e) { moving=false;resizing=false;return super.mouseReleased(e); }
    @Override public void renderBackground(GuiGraphics g,int mx,int my,float delta) { }
    @Override public boolean isPauseScreen() { return false; }
    @Override public void onClose() { BetterMusic.save();minecraft.setScreen(parent); }
    @Override public void removed() { BetterMusic.save(); }
}
