package dev.bettermusic;

/** Runtime-only behavior: never rewrites the user's design or sound options. */
public final class Experience {
    private String identity="";
    private String design="";
    private double changed,damageUntil,fade=1;
    private float duck=1;
    public void tick(Track track,boolean hurt,double now,Settings settings) {
        String next=track.source()+"\n"+track.artist()+"\n"+track.title();
        if(!identity.equals(next)){identity=next;changed=now;}
        String selected=settings.layout+":"+settings.smartHud+":"+settings.expandSeconds+":"+settings.lyrics+":"+settings.vinyl;
        if(!design.equals(selected)){design=selected;changed=now;}
        if(hurt)damageUntil=now+3;
        double target=settings.combatFade&&now<damageUntil?.25:1;
        fade+=(target-fade)*.22;
    }
    public Settings presentation(Settings original,double now){
        Settings s=original.copy();
        if(s.smartHud&&now-changed>s.expandSeconds){s.layout="MINIMAL";s.lyrics=false;}
        if(s.streamer&&s.hideTitles){s.artwork=false;s.provider=false;}
        if(s.streamer&&s.hideLyrics)s.lyrics=false;
        return s;
    }
    public float alpha(Settings s){return s.combatFade?(float)fade:1;}
    public static boolean hidden(Track t,Settings s){
        if(!s.streamer)return false;
        for(String app:s.hiddenPlayers.split("[,;]"))if(!app.isBlank()&&t.source().toLowerCase(java.util.Locale.ROOT).contains(app.strip().toLowerCase(java.util.Locale.ROOT)))return true;
        return false;
    }
    public static Track privateTrack(Track t,Settings s){
        if(!s.streamer||!s.hideTitles)return t;
        return new Track(t.present()?"Private listening":"Music overlay","Streamer mode","PRIVATE",t.playing(),t.position(),t.duration(),"",t.received(),"");
    }
    public float duck(boolean active,Settings s){
        float target=active&&s.duckMusic?(float)s.duckLevel:1;
        duck+=(target-duck)*.2f;if(Math.abs(duck-target)<.001f)duck=target;return duck;
    }
}
