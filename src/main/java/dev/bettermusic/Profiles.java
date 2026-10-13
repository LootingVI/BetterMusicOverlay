package dev.bettermusic;

import com.google.gson.*;
import java.nio.file.*;
import java.util.*;

/** Share appearance only. Never import network consent, media selection or privacy controls. */
public final class Profiles {
    private static final Gson JSON=new GsonBuilder().setPrettyPrinting().create();
    private static final Set<String> FIELDS=Set.of("x","y","scale","opacity","width","radius","accent","background","foreground","artwork","progress","animation","provider","lyrics","lyricsLines","adaptiveColor","ambient","vinyl","transitions","lyricFocus","layout","theme","smartHud","combatFade","expandSeconds","wordHighlight","snap","lyricMotion","ambientMotion","artworkOrbit","motionIntensity");
    public static String exportTheme(Settings s){
        JsonObject full=JSON.toJsonTree(s).getAsJsonObject(),visual=new JsonObject();
        for(String key:FIELDS)visual.add(key,full.get(key));
        JsonObject out=new JsonObject();out.addProperty("format","BetterMusicTheme/1");out.add("appearance",visual);return JSON.toJson(out);
    }
    public static Settings importTheme(String text,Settings current){
        if(text.length()>32000)throw new IllegalArgumentException("Theme exceeds 32 KB");
        JsonObject input=JsonParser.parseString(text).getAsJsonObject();
        if(!input.has("format")||!input.get("format").getAsString().equals("BetterMusicTheme/1"))throw new IllegalArgumentException("Not a Better Music theme");
        JsonObject full=JSON.toJsonTree(current).getAsJsonObject(),visual=input.getAsJsonObject("appearance");
        if(visual==null)throw new IllegalArgumentException("Missing appearance");
        for(String key:FIELDS)if(visual.has(key)&&!visual.get(key).isJsonNull())full.add(key,visual.get(key));
        Settings result=JSON.fromJson(full,Settings.class);result.validate();return result;
    }
    public static void save(Path folder,String name,Settings s)throws java.io.IOException{
        Files.createDirectories(folder);Path target=folder.resolve(safe(name)+".json"),temp=folder.resolve(safe(name)+".tmp");
        Files.writeString(temp,exportTheme(s));Files.move(temp,target,StandardCopyOption.REPLACE_EXISTING);
    }
    public static Settings load(Path folder,String name,Settings s)throws java.io.IOException{
        Path file=folder.resolve(safe(name)+".json");if(Files.size(file)>32000)throw new java.io.IOException("Theme exceeds 32 KB");return importTheme(Files.readString(file),s);
    }
    private static String safe(String name){if(!name.matches("[A-Za-z0-9 _-]{1,40}"))throw new IllegalArgumentException("Use letters, numbers, spaces or dashes");return name;}
}
