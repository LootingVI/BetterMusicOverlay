package dev.bettermusic;

import java.util.*;
import java.util.regex.*;

/** Pure LRC parser. Supports repeated timestamps, fractions, metadata, and file offsets. */
public final class Lyrics {
    public record Word(double time,String text) { }
    public record Line(double time,String text,List<Word> words,double end) {
        public Line(double time,String text){this(time,text,List.of(),Double.NaN);}
        public Line(double time,String text,List<Word> words){this(time,text,words,Double.NaN);}
        public String highlighted(double seconds){StringBuilder out=new StringBuilder();for(Word w:words){if(w.time()>seconds)break;out.append(w.text());}return out.toString();}
    }
    private static final Pattern WORD=Pattern.compile("<(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?>");
    private static final Pattern STAMP=Pattern.compile("\\[(\\d{1,3}):(\\d{2})(?:[.:](\\d{1,3}))?]");
    private static final Pattern OFFSET=Pattern.compile("\\[offset:([+-]?\\d+)]",Pattern.CASE_INSENSITIVE);
    public static List<Line> parse(String text) {
        var result=new ArrayList<Line>();
        double offset=0;
        var om=OFFSET.matcher(text);
        if(om.find())try {offset=Long.parseLong(om.group(1))/1000.0;}catch(NumberFormatException ignored) { }
        for(String raw:text.split("\\R")) {
            String row=raw.replace("\ufeff","").stripLeading();
            var stamps=STAMP.matcher(row);var times=new ArrayList<Double>();int end=0;
            while(stamps.find()&&stamps.start()==end) {
                end=stamps.end();int seconds=Integer.parseInt(stamps.group(2));if(seconds>=60)continue;
                String fraction=stamps.group(3);
                times.add(Integer.parseInt(stamps.group(1))*60.0+seconds+(fraction==null?0:Integer.parseInt(fraction)/Math.pow(10,fraction.length()))+offset);end=stamps.end();
            }
            String content=row.substring(end).strip();
            var wm=WORD.matcher(content);var words=new ArrayList<Word>();
            int start=-1;double at=0;boolean valid=true;StringBuilder plain=new StringBuilder();
            while(wm.find()){
                if(start<0&&wm.start()>0){plain.append(content,0,wm.start());valid=false;}
                if(start>=0){String part=content.substring(start,wm.start());words.add(new Word(at,part));plain.append(part);}
                String fraction=wm.group(3);double next=Integer.parseInt(wm.group(1))*60.0+Integer.parseInt(wm.group(2))+(fraction==null?0:Integer.parseInt(fraction)/Math.pow(10,fraction.length()))+offset;
                if(Integer.parseInt(wm.group(2))>=60||(start>=0&&next<at))valid=false;
                at=next;start=wm.end();
            }
            if(start>=0){String part=content.substring(start);if(!part.isEmpty()){words.add(new Word(at,part));plain.append(part);}content=plain.toString();}
            for(double time:times){
                // Repeated line stamps do not specify how to shift absolute word stamps.
                boolean timed=valid&&times.size()==1&&!words.isEmpty()&&words.get(0).time()>=time;
                double finish=start==row.substring(end).strip().length()&&timed?Math.max(0,at):Double.NaN;
                result.add(new Line(Math.max(0,time),content,timed?List.copyOf(words):List.of(),finish));
            }
        }
        result.sort(Comparator.comparingDouble(Line::time));
        var merged=new ArrayList<Line>();
        var groupTexts=new HashSet<String>();
        for(Line line:result){
            if(!merged.isEmpty()&&merged.get(merged.size()-1).time()==line.time()){
                if(!groupTexts.add(line.text()))continue;
                Line previous=merged.remove(merged.size()-1);
                if(previous.text().equals(line.text())||line.text().isBlank())merged.add(previous);
                else if(previous.text().isBlank())merged.add(line);
                else merged.add(new Line(line.time(),previous.text()+" / "+line.text()));
            }else {groupTexts.clear();groupTexts.add(line.text());merged.add(line);}
        }
        return List.copyOf(merged);
    }
    public static int current(List<Line> lines,double seconds) {
        int low=0,high=lines.size()-1,found=-1;
        while(low<=high){int mid=(low+high)>>>1;if(lines.get(mid).time()<=seconds){found=mid;low=mid+1;}else high=mid-1;}
        return found;
    }
}
