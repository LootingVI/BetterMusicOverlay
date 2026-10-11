package dev.bettermusic;

import com.google.gson.*;
import java.text.Normalizer;
import java.util.Locale;

/** Conservative recording matching: version qualifiers (live, remix, acoustic) are retained. */
public final class LyricsMatch {
    private LyricsMatch(){}
    static String normalized(String value){return Normalizer.normalize(value,Normalizer.Form.NFKC).toLowerCase(Locale.ROOT).replace('&',',').replaceAll("[^\\p{L}\\p{N}]+"," ").strip();}
    public static String artist(Track track){
        String artist=track.artist();
        // Apple Music can append the album to the artist. Only remove an independently confirmed album.
        int separator=artist.lastIndexOf(" — ");
        if(separator>0&&!track.album().isBlank()&&normalized(artist.substring(separator+3)).equals(normalized(track.album())))return artist.substring(0,separator).strip();
        return artist;
    }
    public static String string(JsonObject object,String key){return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";}
    public static boolean matches(JsonObject record,Track track,boolean search){
        try {
            if(!normalized(string(record,"trackName")).equals(normalized(track.title())))return false;
            if(!normalized(string(record,"artistName")).equals(normalized(artist(track))))return false;
            if(search&&track.duration()<=0)return false;
            if(track.duration()>0){double duration=record.get("duration").getAsDouble();if(!Double.isFinite(duration)||Math.abs(duration-track.duration())>2)return false;}
            return track.album().isBlank()||normalized(string(record,"albumName")).equals(normalized(track.album()));
        }catch(RuntimeException e){return false;}
    }
    public static JsonObject best(JsonArray records,Track track){
        JsonObject best=null;double distance=Double.MAX_VALUE;
        for(JsonElement item:records){if(!item.isJsonObject())continue;JsonObject record=item.getAsJsonObject();
            if(!matches(record,track,true))continue;
            var rich=Lyricsfile.parse(string(record,"lyricsfile"));
            boolean synced=!rich.lines().isEmpty()||!string(record,"syncedLyrics").isBlank();if(!synced&&rich.plain().isBlank()&&string(record,"plainLyrics").isBlank())continue;
            double delta=Math.abs(record.get("duration").getAsDouble()-track.duration())+(rich.wordSync()?0:synced?100:1000);
            if(delta<distance){best=record;distance=delta;}
        }
        return best;
    }
}
