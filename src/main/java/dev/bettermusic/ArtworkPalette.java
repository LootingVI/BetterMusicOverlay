package dev.bettermusic;

import java.awt.Color;
import java.awt.image.BufferedImage;

/** Quantized chromatic histogram. Neutral/transparent pixels cannot overpower album colors. */
public final class ArtworkPalette {
    private ArtworkPalette() {}
    public static int accent(BufferedImage image,int fallback) {
        if(image==null)return fallback;
        double[] weights=new double[24];double[] red=new double[24],green=new double[24],blue=new double[24];
        for(int y=0;y<image.getHeight();y+=Math.max(1,image.getHeight()/32))for(int x=0;x<image.getWidth();x+=Math.max(1,image.getWidth()/32)) {
            int pixel=image.getRGB(x,y);if((pixel>>>24)<160)continue;
            int r=pixel>>16&255,g=pixel>>8&255,b=pixel&255;
            float[] hsv=Color.RGBtoHSB(r,g,b,null);
            if(hsv[1]<.16||hsv[2]<.12)continue;
            int bin=Math.min(23,(int)(hsv[0]*24));double weight=hsv[1]*Math.sqrt(hsv[2]);
            weights[bin]+=weight;red[bin]+=r*weight;green[bin]+=g*weight;blue[bin]+=b*weight;
        }
        int best=0;for(int i=1;i<24;i++)if(weights[i]>weights[best])best=i;
        if(weights[best]==0)return fallback;
        float[] hsv=Color.RGBtoHSB((int)(red[best]/weights[best]),(int)(green[best]/weights[best]),(int)(blue[best]/weights[best]),null);
        return Color.HSBtoRGB(hsv[0],Math.min(.65f,hsv[1]),Math.max(.82f,hsv[2]))&0xffffff;
    }
}
