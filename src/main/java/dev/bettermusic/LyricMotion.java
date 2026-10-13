package dev.bettermusic;

import java.util.function.ToDoubleFunction;

/** Visual easing starts at supplied word timestamps; it never estimates missing timestamps. */
public final class LyricMotion {
    private LyricMotion(){}
    public record Highlight(String before,String word,double reveal) {
        public static final Highlight NONE=new Highlight("","",0);
        public double width(ToDoubleFunction<String> measure){
            double start=measure.applyAsDouble(before);
            return start+(measure.applyAsDouble(before+word)-start)*reveal;
        }
        public boolean empty(){return before.isEmpty()&&word.isEmpty();}
    }
    public static double ease(double value){double t=Settings.clamp(value,0,1);return 1-Math.pow(1-t,3);}
    public static double approach(double current,double target,double seconds){return current+(target-current)*(1-Math.exp(-14*Settings.clamp(seconds,0,.1)));}
    public static Highlight highlight(Lyrics.Line line,double position,boolean animate){
        if(line.words().isEmpty()||Double.isFinite(line.end())&&position>=line.end())return Highlight.NONE;
        int active=-1;for(int i=0;i<line.words().size()&&line.words().get(i).time()<=position;i++)active=i;
        if(active<0)return Highlight.NONE;
        var prefix=new StringBuilder();for(int i=0;i<active;i++)prefix.append(line.words().get(i).text());
        var word=line.words().get(active);double duration=.18;
        if(active+1<line.words().size())duration=Math.min(duration,line.words().get(active+1).time()-word.time());
        double reveal=animate&&duration>0?ease((position-word.time())/duration):1;
        return new Highlight(prefix.toString(),word.text(),reveal);
    }
}
