package dev.bettermusic;

import com.google.gson.*;
import java.text.Normalizer;
import java.util.Locale;

/** Song identity and timing are strict; album editions are a ranking preference. */
public final class LyricsMatch {
    private LyricsMatch(){}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('&',',').replaceAll("[^\\p{L}\\p{N}]+"," ").strip();}
    public static String artist(Track track){
        String artist=track.artist();
        // Apple Music can append the album to the artist. Only remove an independently confirmed album.
        int separator=artist.lastIndexOf(" — ");
        if(separator>0&&!track.album().isBlank()&&album(artist.substring(separator+3)).equals(album(track.album())))return artist.substring(0,separator).strip();
        return artist;
    }
    public static String string(JsonObject object,String key){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";}
    private static String album(String value){return normalized(value.replaceFirst("(?i)\\s+[-–—]\\s*(single|ep)\\s*$",""));}
    private static int albumRank(JsonObject record,Track track){
        if(track.album().isBlank())return 1;
        return album(string(record,"albumName")).equals(album(track.album()))?0:1;
    }
    private static boolean knownDuration(Track track){return Double.isFinite(track.duration())&&track.duration()>0;}
    public static boolean matches(JsonObject record,Track track,boolean search){
        try {
            if(track.title().isBlank()||artist(track).isBlank())return false;
            if(!normalized(string(record,"trackName")).equals(normalized(track.title())))return false;
            if(!normalized(string(record,"artistName")).equals(normalized(artist(track))))return false;
            if(search||knownDuration(track)){double duration=record.get("duration").getAsDouble();if(!Double.isFinite(duration)||duration<=0||knownDuration(track)&&Math.abs(duration-track.duration())>2)return false;}
            return true;
        }catch(RuntimeException e){return false;}
    }
    public static JsonObject best(JsonArray records,Track track){
        var eligible=new java.util.ArrayList<JsonObject>();
        for(JsonElement item:records){if(!item.isJsonObject())continue;JsonObject record=item.getAsJsonObject();
            if(!matches(record,track,true))continue;
            var rich=Lyricsfile.parse(string(record,"lyricsfile"));
            boolean synced=!rich.lines().isEmpty()||!string(record,"syncedLyrics").isBlank();if(!synced&&rich.plain().isBlank()&&string(record,"plainLyrics").isBlank())continue;
            eligible.add(record);
        }
        if(!knownDuration(track)&&!eligible.isEmpty()){
            int rank=eligible.stream().mapToInt(record->albumRank(record,track)).min().orElse(1);
            eligible.removeIf(record->albumRank(record,track)>rank);
            double min=eligible.stream().mapToDouble(record->record.get("duration").getAsDouble()).min().orElse(0);
            double max=eligible.stream().mapToDouble(record->record.get("duration").getAsDouble()).max().orElse(0);
            // Without a player duration, different-length versions need an explicit user choice.
            if(max-min>2)return null;
        }
        JsonObject best=null;double distance=Double.MAX_VALUE;
        for(JsonObject record:eligible){
            var rich=Lyricsfile.parse(string(record,"lyricsfile"));boolean synced=!rich.lines().isEmpty()||!string(record,"syncedLyrics").isBlank();
            double delta=(knownDuration(track)?Math.abs(record.get("duration").getAsDouble()-track.duration()):0)+albumRank(record,track)*10+(rich.wordSync()?0:synced?100:1000);
            if(delta<distance){best=record;distance=delta;}
        }
        return best;
    }
}
