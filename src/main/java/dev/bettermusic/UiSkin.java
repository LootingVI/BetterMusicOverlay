package dev.bettermusic;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;

/** One cached, antialiased nine-slice atlas shared by every settings screen. */
final class UiSkin {
    static final int PANEL=0,CARD=1,HOVER=2,SELECTED=3,FIELD=4,ACCENT=5,MUTED=6;
    private static final Identifier ID=Identifier.fromNamespaceAndPath("bettermusic","studio_skin");
    private static boolean ready;
    private static void init(){
        if(ready)return;
        var bitmap=new BufferedImage(512,256,BufferedImage.TYPE_INT_ARGB);var g=bitmap.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        int[] colors={0xff11131c,0xff1c1f2c,0xff282c3e,0xff342946,0xff171923,0xffc4a1ee,0xff161821,0xffffffff};
        for(int i=0;i<8;i++){int x=i%4*128,y=i/4*128;var shape=new RoundRectangle2D.Float(x+.5f,y+.5f,127,127,64,64);
            g.setColor(new Color(colors[i],true));g.fill(shape);
            if(i<7){g.setColor(new Color(255,255,255,i==SELECTED?38:12));g.setStroke(new BasicStroke(2));g.draw(shape);}
        }
        g.dispose();int[] data=bitmap.getRGB(0,0,512,256,null,0,512);RenderBudget.toAbgr(data,1);
        var pixels=new NativeImage(512,256,false);org.lwjgl.system.MemoryUtil.memIntBuffer(pixels.getPointer(),data.length).put(data);
        Minecraft.getInstance().getTextureManager().register(ID,new DynamicTexture(()->"Better Music UI atlas",pixels));ready=true;
    }
    static void surface(GuiGraphics g,int x,int y,int w,int h,int style){patch(g,x,y,w,h,8,style,0xffffffff);}
    static void rounded(GuiGraphics g,int x,int y,int w,int h,int radius,int color){patch(g,x,y,w,h,radius,7,color);}
    private static void patch(GuiGraphics g,int x,int y,int w,int h,int radius,int style,int color){
        if(w<=0||h<=0)return;init();int c=Math.min(radius,Math.min(w,h)/2);
        if(c<=0){g.fill(x,y,x+w,y+h,color);return;}
        int u=style%4*128,v=style/4*128;
        for(int j=0;j<3;j++)for(int i=0;i<3;i++){
            int dw=i==1?w-2*c:c,dh=j==1?h-2*c:c;if(dw<=0||dh<=0)continue;
            g.blit(RenderPipelines.GUI_TEXTURED,ID,i==0?x:i==1?x+c:x+w-c,j==0?y:j==1?y+c:y+h-c,u+(i==0?0:i==1?32:96),v+(j==0?0:j==1?32:96),dw,dh,i==1?64:32,j==1?64:32,512,256,color);
        }
    }
    static void backdrop(GuiGraphics g,int width,int height,int x,int y,int w,int h){
        g.fill(0,0,width,height,0xb9080a11);rounded(g,x-3,y+5,w+6,h+3,12,0x50000000);surface(g,x,y,w,h,PANEL);
    }
    static void close(){if(ready){Minecraft.getInstance().getTextureManager().release(ID);ready=false;}}
}
