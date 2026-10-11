package dev.bettermusic;

/** Explicit limits keep raster work independent of the game's frame rate. */
final class RenderBudget {
    static int fps(String quality){return switch(quality){case "QUALITY"->30;case "SAVER"->10;default->20;};}
    static double density(String quality,double physical){return Math.max(1,Math.min(switch(quality){case "QUALITY"->4;case "SAVER"->2;default->3;},physical));}
    static long interval(String quality,boolean motion){return 1_000_000_000L/(motion?fps(quality):5);}
    static void toAbgr(int[] pixels,float alpha){
        for(int i=0;i<pixels.length;i++){int p=pixels[i];int a=alpha<.999f?(int)((p>>>24)*alpha):p>>>24;pixels[i]=a<<24|(p&0xff00)|((p>>>16)&255)|((p&255)<<16);}
    }
}
