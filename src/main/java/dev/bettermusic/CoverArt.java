package dev.bettermusic;

import javax.imageio.ImageIO;
import java.io.*;
import java.util.Base64;
import java.util.concurrent.*;

/** Decode bounded artwork off-thread for the antialiased card renderer. */
public final class CoverArt implements AutoCloseable {
    private final ExecutorService decoder=Executors.newSingleThreadExecutor(r->{var t=new Thread(r,"BetterMusic-artwork");t.setDaemon(true);return t;});
    private volatile String requested="";
    private volatile boolean closed;
    private volatile java.awt.image.BufferedImage bitmap;
    private volatile java.awt.image.BufferedImage previous;
    private volatile long changed;
    public synchronized java.awt.image.BufferedImage blended(boolean motion){
        if(!motion||previous==null||bitmap==null)return bitmap;
        float phase=(float)Math.min(1,(System.nanoTime()-changed)/650_000_000.0);
        if(phase>=1){previous=null;return bitmap;}
        var image=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();g.drawImage(previous,0,0,null);g.setComposite(java.awt.AlphaComposite.SrcOver.derive(phase));g.drawImage(bitmap,0,0,null);g.dispose();return image;
    }
    public java.awt.image.BufferedImage bitmap() { return bitmap; }
    public void update(String encoded) {
        if(closed || requested.equals(encoded))return;
        requested=encoded;
        if(encoded.isEmpty()){bitmap=null;previous=null;return;}
        decoder.execute(()-> {
            boolean decoded=false;
            try {
                byte[] bytes=Base64.getDecoder().decode(encoded);
                if(bytes.length>1_500_000)return;
                // ImageIO also supports JPEG thumbnails; inspect dimensions before allocating the image.
                try(var stream=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                    var readers=ImageIO.getImageReaders(stream);
                    if(!readers.hasNext())return;
                    var reader=readers.next();
                    try {
                        reader.setInput(stream);
                        int w=reader.getWidth(0),h=reader.getHeight(0);
                        if(w<1||h<1||w>2048||h>2048)return;
                        var original=reader.read(0);
                        var square=new java.awt.image.BufferedImage(256,256,java.awt.image.BufferedImage.TYPE_INT_ARGB);
                        var graphics=square.createGraphics();
                        graphics.setRenderingHint(java.awt.RenderingHints.KEY_INTERPOLATION,java.awt.RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        int side=Math.min(w,h);
                        graphics.drawImage(original,0,0,256,256,(w-side)/2,(h-side)/2,(w+side)/2,(h+side)/2,null);graphics.dispose();
                        synchronized(this){if(!closed&&requested.equals(encoded)){previous=bitmap;bitmap=square;changed=System.nanoTime();decoded=true;}}
                    } finally { reader.dispose(); }
                }
            } catch(Exception e) { BetterMusic.LOG.debug("Cannot decode artwork",e); }
            finally{if(!decoded&&requested.equals(encoded)){bitmap=null;previous=null;}}
        });
    }
    @Override public void close() { closed=true;decoder.shutdownNow();bitmap=null; }
}
