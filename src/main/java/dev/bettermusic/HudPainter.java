package dev.bettermusic;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.util.List;

/** Resolution-aware, antialiased renderer, independent of Minecraft for visual tests. */
public final class HudPainter {
    private static final Font INTER=loadFont();
    public record Style(int width,int radius,int accent,int background,int foreground,double opacity,String layout,
                        boolean artwork,boolean progress,boolean motion,boolean provider,boolean lyrics,int lyricLines,
                        boolean adaptive,boolean ambient,boolean vinyl,boolean transitions,boolean lyricFocus) {
        public static Style from(Settings s){return new Style(s.width,s.radius,s.accent,s.background,s.foreground,s.opacity,s.layout,s.artwork,s.progress,s.animation,s.provider,s.lyrics,s.lyricsLines,s.adaptiveColor,s.ambient,s.vinyl,s.transitions,s.lyricFocus);}
        public int baseHeight(){return layout.equals("AURORA")?148:layout.equals("MINIMAL")?42:layout.equals("COMPACT")?66:86;}
        public int height(){return baseHeight()+(lyrics?28+lyricLines*(lyricFocus?26:21):0);}
        public int controlY(){return layout.equals("AURORA")?91:layout.equals("MINIMAL")?20:layout.equals("COMPACT")?29:30;}
        public int controlX(int index){return width-87+index*32;}
        public double seekFraction(double x,double y){
            if(!progress||layout.equals("MINIMAL"))return -1;
            double start=layout.equals("AURORA")?18:47,end=layout.equals("AURORA")?width-18:width-52;
            int line=layout.equals("AURORA")?122:baseHeight()-14;
            return x<start||x>end||Math.abs(y-line)>7?-1:(x-start)/(end-start);
        }
    }
    private static BufferedImage paletteImage;
    private static int paletteAccent;
    private record Background(int width,int height,int radius,int tint,int surface,double opacity,boolean ambient,double density){}
    private record CachedBackground(Background key,BufferedImage image){}
    private static final ThreadLocal<CachedBackground> BACKGROUND=new ThreadLocal<>();
    private static BufferedImage background(Style s,double density,int tint){
        var key=new Background(s.width(),s.height(),s.radius(),tint,s.background(),s.opacity(),s.ambient(),density);var cached=BACKGROUND.get();
        if(cached!=null&&cached.key().equals(key))return cached.image();
        int w=s.width(),h=s.height();var image=new BufferedImage((int)Math.ceil(w*density),(int)Math.ceil(h*density),BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();g.scale(density,density);g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        var card=new RoundRectangle2D.Double(.5,.5,w-1,h-1,s.radius()*2,s.radius()*2);g.setClip(card);
        Color surface=color(s.background(),(int)(255*s.opacity())),glow=blend(surface,color(tint,surface.getAlpha()),s.ambient()?.22:0);
        g.setPaint(new GradientPaint(0,0,surface,w,h,glow));g.fill(card);
        if(s.ambient()){g.setPaint(new RadialGradientPaint(new Point2D.Float(w,h),w*.8f,new float[]{0,1},new Color[]{color(tint,30),color(tint,0)}));g.fill(card);}
        g.setColor(new Color(255,255,255,18));g.setStroke(new BasicStroke(.6f));g.draw(card);g.dispose();BACKGROUND.set(new CachedBackground(key,image));return image;
    }
    private static synchronized int palette(BufferedImage image,int fallback){
        if(image==null)return fallback;
        if(image!=paletteImage){paletteImage=image;paletteAccent=ArtworkPalette.accent(image,-1);}
        return paletteAccent<0?fallback:paletteAccent;
    }
    private static Font loadFont(){try(var in=HudPainter.class.getResourceAsStream("/assets/bettermusic/font/inter.ttf")){return Font.createFont(Font.TRUETYPE_FONT,in);}catch(Exception e){return new Font("SansSerif",Font.PLAIN,12);}}
    public static BufferedImage paint(Style s,Track t,BufferedImage cover,List<String> lyrics,double density,double clock) {
        return paint(s,t,cover,lyrics,density,clock,clock);
    }
    public static BufferedImage paint(Style s,Track t,BufferedImage cover,List<String> lyrics,double density,double clock,double lyricClock) {
        return paint(s,t,cover,lyrics,density,clock,lyricClock,t.source().equals("PREVIEW")?"DEMO LYRICS":"");
    }
    public static BufferedImage paint(Style s,Track t,BufferedImage cover,List<String> lyrics,double density,double clock,double lyricClock,String lyricSource) {
        return paint(s,t,cover,lyrics,density,clock,lyricClock,lyricSource,"");
    }
    public static BufferedImage paint(Style s,Track t,BufferedImage cover,List<String> lyrics,double density,double clock,double lyricClock,String lyricSource,String words) {
        return paintInto(null,s,t,cover,lyrics,density,clock,lyricClock,lyricSource,words);
    }
    /** The worker owns this reusable surface; callers must not read it while another paint is running. */
    public static BufferedImage paintInto(BufferedImage reuse,Style s,Track t,BufferedImage cover,List<String> lyrics,double density,double clock,double lyricClock,String lyricSource,String words) {
        int w=s.width(),h=s.height();
        int pw=(int)Math.ceil(w*density),ph=(int)Math.ceil(h*density);
        var image=reuse!=null&&reuse.getWidth()==pw&&reuse.getHeight()==ph?reuse:new BufferedImage(pw,ph,BufferedImage.TYPE_INT_ARGB);
        var g=image.createGraphics();g.setComposite(AlphaComposite.Clear);g.fillRect(0,0,pw,ph);g.setComposite(AlphaComposite.SrcOver);g.scale(density,density);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,RenderingHints.VALUE_FRACTIONALMETRICS_ON);
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        var card=new RoundRectangle2D.Double(.5,.5,w-1,h-1,s.radius()*2,s.radius()*2);
        g.setClip(card);
        int tint=s.adaptive()?palette(cover,s.accent()):s.accent();
        Color surface=color(s.background(),(int)(255*s.opacity()));
        g.drawImage(background(s,density,tint),0,0,w,h,null);
        Color fg=color(s.foreground(),255),muted=blend(fg,surface,.40),accent=color(tint,255);
        if(s.transitions()&&s.motion())g.setComposite(AlphaComposite.SrcOver.derive((float)(.35+.65*Math.min(1,Math.max(0,clock)/.35))));
        if(s.layout().equals("AURORA")) {
            aurora(g,s,t,cover,clock,fg,muted,accent,surface);
            lyrics(g,s,lyrics,fg,muted,accent,lyricClock,lyricSource,words);
            g.dispose();return image;
        }
        boolean minimal=s.layout().equals("MINIMAL"),compact=s.layout().equals("COMPACT");
        int tx=12,coverSize=compact?34:40;
        if(s.artwork()&&!minimal) {
            var artShape=new RoundRectangle2D.Float(11,11,coverSize,coverSize,4,4);
            Shape previous=g.getClip();g.clip(artShape);
            if(cover!=null)g.drawImage(cover,11,11,coverSize,coverSize,null);
            else {
                g.setPaint(new GradientPaint(11,11,accent,coverSize,coverSize,surface));g.fill(artShape);
                g.setColor(color(s.background(),190));g.fill(new Ellipse2D.Float(17,17,coverSize-12,coverSize-12));
                g.setColor(accent);g.fill(new Ellipse2D.Float(11+coverSize/2f-3,11+coverSize/2f-3,6,6));
            }
            g.setClip(previous);tx=coverSize+23;
        }
        int controlsStart=w-98;
        String title=t.present()?t.title():"Your soundtrack starts here";
        String artist=t.present()?t.artist():"Play music in your favorite app";
        if(minimal) {
            g.setColor(t.playing()?accent:muted);g.fill(new Ellipse2D.Float(11,18,5,5));tx=24;
            label(g,title,tx,16,controlsStart-tx-8,12,true,fg,s.motion(),clock);
            label(g,artist,tx,31,controlsStart-tx-8,10,false,muted,false,clock);
        } else {
            label(g,artist,tx,25,controlsStart-tx-8,11,false,muted,false,clock);
            label(g,title,tx,compact?41:46,controlsStart-tx-8,15,true,fg,s.motion(),clock);
        }
        int cy=s.controlY();
        icon(g,w-87,cy,"previous",fg);icon(g,w-55,cy,t.playing()?"pause":"play",fg);icon(g,w-23,cy,"next",fg);
        if(s.progress()&&!minimal) {
            int py=s.baseHeight()-14;
            g.setColor(color(s.foreground(),28));g.draw(new Line2D.Double(10,py-13,w-10,py-13));
            label(g,format(t.elapsed()),11,py+3,34,11,false,fg,false,clock);
            String remaining=t.duration()>0?"-"+format(t.duration()-t.elapsed()):"--:--";
            label(g,remaining,w-40,py+3,36,11,false,muted,false,clock);
            int barStart=47,barWidth=w-99;
            g.setColor(color(s.foreground(),42));g.fill(new RoundRectangle2D.Float(barStart,py-3,barWidth,6,6,6));
            if(t.duration()>0){g.setColor(fg);g.fill(new RoundRectangle2D.Double(barStart,py-3,barWidth*Math.min(1,t.elapsed()/t.duration()),6,6,6));}
        }
        lyrics(g,s,lyrics,fg,muted,accent,lyricClock,lyricSource,words);
        g.dispose();return image;
    }
    private static void aurora(Graphics2D g,Style s,Track t,BufferedImage cover,double clock,Color fg,Color muted,Color accent,Color surface) {
        int w=s.width(),tx=s.artwork()?122:18,textWidth=w-tx-18;
        if(s.artwork()) {
            Shape clip=g.getClip();Shape shape=s.vinyl()?new Ellipse2D.Double(16,16,90,90):new RoundRectangle2D.Double(16,16,90,90,18,18);
            g.clip(shape);
            g.setPaint(new GradientPaint(16,16,accent,106,106,surface));g.fill(shape);
            var transform=g.getTransform();
            if(s.vinyl()&&s.motion()&&t.playing())g.rotate(clock*.28,61,61);
            if(cover!=null)g.drawImage(cover,16,16,90,90,null);
            else {g.setColor(new Color(0,0,0,95));g.fill(new Ellipse2D.Double(29,29,64,64));g.setColor(accent);g.fill(new Ellipse2D.Double(53,53,16,16));}
            g.setTransform(transform);
            if(s.vinyl()) {
                g.setStroke(new BasicStroke(.5f));g.setColor(new Color(255,255,255,50));
                for(int d=38;d<=82;d+=9)g.draw(new Ellipse2D.Double(61-d/2.0,61-d/2.0,d,d));
                g.setColor(surface);g.fill(new Ellipse2D.Double(57,57,8,8));
            }
            g.setClip(clip);
        }
        String badge=t.source().equals("PREVIEW")?"AURORA / PREVIEW":t.playing()?"NOW PLAYING":"ON PAUSE";
        label(g,badge,tx,25,textWidth,8,true,accent,false,clock);
        label(g,t.present()?t.title():"Find your frequency",tx,49,textWidth,17,true,fg,s.motion(),clock);
        label(g,t.present()?t.artist():"Start music in any media app",tx,68,textWidth,11,false,muted,false,clock);
        if(textWidth>165&&s.provider())label(g,source(t.source()),tx,96,textWidth-110,8,false,muted,false,clock);
        g.setColor(new Color(accent.getRed(),accent.getGreen(),accent.getBlue(),38));g.fill(new Ellipse2D.Double(s.controlX(1)-14,s.controlY()-14,28,28));
        icon(g,s.controlX(0),s.controlY(),"previous",muted);icon(g,s.controlX(1),s.controlY(),t.playing()?"pause":"play",fg);icon(g,s.controlX(2),s.controlY(),"next",muted);
        if(s.progress()) {
            int py=122,bw=w-36;double ratio=t.duration()>0?Math.min(1,t.elapsed()/t.duration()):0;
            g.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),28));g.fill(new RoundRectangle2D.Double(18,py,bw,3,3,3));
            g.setColor(accent);g.fill(new RoundRectangle2D.Double(18,py,bw*ratio,3,3,3));
            if(t.duration()>0)g.fill(new Ellipse2D.Double(15+bw*ratio,py-1.5,6,6));
            label(g,format(t.elapsed()),18,139,45,8,false,muted,false,clock);
            label(g,t.duration()>0?"-"+format(t.duration()-t.elapsed()):"LIVE",w-51,139,36,8,false,muted,false,clock);
        }
    }
    private static void lyrics(Graphics2D g,Style s,List<String> lines,Color fg,Color muted,Color accent,double clock,String source,String words) {
        if(!s.lyrics())return;
        int top=s.baseHeight(),step=s.lyricFocus()?26:21;
        g.setColor(new Color(fg.getRed(),fg.getGreen(),fg.getBlue(),22));g.draw(new Line2D.Double(16,top,s.width()-16,top));
        for(int i=0;i<Math.min(s.lyricLines(),lines.size());i++) {
            int baseline=top+25+i*step;
            Composite original=g.getComposite();
            if(i==0&&s.motion()&&s.transitions())g.setComposite(AlphaComposite.SrcOver.derive((float)(.3+.7*Math.min(1,Math.max(0,clock)/.22))));
            if(i==0){g.setColor(accent);g.fill(new RoundRectangle2D.Double(15,baseline-12,2,14,2,2));}
            label(g,lines.get(i),25,baseline,s.width()-43,i==0?(s.lyricFocus()?16:13):11,i==0,i==0?fg:muted,s.motion()&&i==0,clock);
            if(i==0&&!words.isEmpty()&&lines.get(i).startsWith(words)){
                // Use exactly the same font and marquee origin as the full line.
                String full=lines.get(i);int size=s.lyricFocus()?16:13,available=s.width()-43;
                int fullWidth=g.getFontMetrics().stringWidth(full),prefixWidth=g.getFontMetrics().stringWidth(words);
                double shift=0;if(fullWidth>available&&s.motion()){double distance=fullWidth-available+16,phase=(clock*22)%(distance*2+88);shift=phase<44?0:phase<44+distance?phase-44:phase<88+distance?distance:2*distance+88-phase;}
                Shape clip=g.getClip();g.clip(new Rectangle2D.Double(25,baseline-size-3,Math.max(0,Math.min(available,prefixWidth-shift)),size+7));
                label(g,full,25,baseline,available,size,true,accent,s.motion(),clock);g.setClip(clip);
            }
            g.setComposite(original);
        }
        label(g,source,25,s.height()-8,s.width()-43,7,false,muted,false,clock);
    }
    private static String source(String raw) {
        String id=raw.toLowerCase(java.util.Locale.ROOT);
        if(id.contains("spotify"))return "SPOTIFY";if(id.contains("apple"))return "APPLE MUSIC";
        if(id.contains("chrome")||id.contains("edge")||id.contains("firefox"))return "BROWSER";
        return "BETTER MUSIC";
    }
    private static void label(Graphics2D g,String text,int x,int baseline,int width,int size,boolean bold,Color c,boolean scroll,double clock) {
        if(width<=0)return;Font font=INTER.canDisplayUpTo(text)<0?INTER:new Font("SansSerif",Font.PLAIN,size);g.setFont(font.deriveFont(bold?Font.BOLD:Font.PLAIN,(float)size));g.setColor(c);
        int textWidth=g.getFontMetrics().stringWidth(text);Shape clip=g.getClip();g.clip(new Rectangle2D.Double(x,baseline-size-3,width,size+7));
        if(textWidth>width&&scroll) {
            double distance=textWidth-width+16,phase=(clock*22)%(distance*2+88);
            double offset=phase<44?0:phase<44+distance?phase-44:phase<88+distance?distance:2*distance+88-phase;
            g.drawString(text,(float)(x-offset),baseline);
        } else {
            if(textWidth>width){while(!text.isEmpty()&&g.getFontMetrics().stringWidth(text+"…")>width)text=text.substring(0,text.offsetByCodePoints(text.length(),-1));text+="…";}
            g.drawString(text,x,baseline);
        }
        g.setClip(clip);
    }
    private static void icon(Graphics2D g,int x,int y,String type,Color color) {
        g.setColor(color);
        if(type.equals("pause")){g.fill(new RoundRectangle2D.Float(x-4,y-5,3,10,1,1));g.fill(new RoundRectangle2D.Float(x+1,y-5,3,10,1,1));return;}
        boolean prev=type.equals("previous");int direction=prev?-1:1;
        var triangle=new Path2D.Float();triangle.moveTo(x-4*direction,y-5);triangle.lineTo(x+4*direction,y);triangle.lineTo(x-4*direction,y+5);triangle.closePath();g.fill(triangle);
        if(!type.equals("play"))g.fill(new RoundRectangle2D.Float(x+5*direction-(prev?1:0),y-5,1.5f,10,1,1));
    }
    private static Color color(int rgb,int alpha){return new Color((rgb>>16)&255,(rgb>>8)&255,rgb&255,Math.max(0,Math.min(255,alpha)));}
    private static Color blend(Color a,Color b,double t){return new Color((int)(a.getRed()*(1-t)+b.getRed()*t),(int)(a.getGreen()*(1-t)+b.getGreen()*t),(int)(a.getBlue()*(1-t)+b.getBlue()*t),a.getAlpha());}
    private static String format(double seconds){int n=(int)Math.max(0,seconds);return n/60+":"+String.format(java.util.Locale.ROOT,"%02d",n%60);}
}
