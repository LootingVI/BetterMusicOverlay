package dev.bettermusic;

import java.util.*;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

/** Reads LRCLIB's Lyricsfile 1.0 draft; malformed rich data falls back to legacy lyrics. */
final class Lyricsfile {
    record Content(List<Lyrics.Line> lines,String plain,boolean instrumental) {
        boolean wordSync(){return lines.stream().anyMatch(line->!line.words().isEmpty());}
    }
    private static final Content EMPTY=new Content(List.of(),"",false);
    static Content parse(String source){
        if(source.isBlank()||source.length()>1_000_000)return EMPTY;
        try{
            var options=new LoaderOptions();options.setAllowDuplicateKeys(false);options.setMaxAliasesForCollections(0);
            options.setNestingDepthLimit(30);options.setCodePointLimit(1_000_000);
            Object root=new Yaml(new SafeConstructor(options)).load(source);
            if(!(root instanceof Map<?,?> document)||!"1.0".equals(document.get("version")))return EMPTY;
            if(document.get("metadata") instanceof Map<?,?> metadata&&Boolean.TRUE.equals(metadata.get("instrumental")))return new Content(List.of(),"",true);
            var lines=new ArrayList<Lyrics.Line>();
            if(document.get("lines") instanceof List<?> rows){
                if(rows.size()>5000)return EMPTY;
                for(Object row:rows){
                    if(!(row instanceof Map<?,?> line))continue;
                    double start=time(line.get("start_ms")),end=time(line.get("end_ms"));
                    String text=string(line.get("text"));
                    if(!Double.isFinite(start)||!(line.get("text") instanceof String))continue;
                    if(end<start)end=Double.NaN;
                    var words=new ArrayList<Lyrics.Word>();
                    if(line.get("words") instanceof List<?> segments&&!segments.isEmpty()&&segments.size()<=5000){
                        StringBuilder joined=new StringBuilder();double previous=start;boolean valid=true;
                        for(Object segment:segments){
                            if(!(segment instanceof Map<?,?> word)||!(word.get("text") instanceof String)){valid=false;break;}
                            double at=time(word.get("start_ms"));String part=string(word.get("text"));
                            if(!Double.isFinite(at)||at<previous||Double.isFinite(end)&&at>end){valid=false;break;}
                            words.add(new Lyrics.Word(at,part));joined.append(part);previous=at;
                        }
                        if(!valid||!joined.toString().equals(text))words.clear();
                    }
                    lines.add(new Lyrics.Line(start,text,List.copyOf(words),end));
                }
            }
            // The draft leaves offset_ms semantics unresolved. Only the user's explicit offset applies.
            lines.sort(Comparator.comparingDouble(Lyrics.Line::time));
            return new Content(List.copyOf(lines),string(document.get("plain")),false);
        }catch(RuntimeException e){return EMPTY;}
    }
    private static String string(Object value){return value instanceof String text?text:"";}
    private static double time(Object value){
        if(!(value instanceof Number number))return Double.NaN;
        double ms=number.doubleValue();return Double.isFinite(ms)&&ms>=0&&ms==Math.rint(ms)&&ms<=9_007_199_254_740_991d?ms/1000:Double.NaN;
    }
    private Lyricsfile(){}
}
