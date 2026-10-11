package dev.bettermusic;

public record Track(String title, String artist, String source, boolean playing, double position, double duration, String cover, long received, String album) {
    public Track(String title,String artist,String source,boolean playing,double position,double duration,String cover,long received){this(title,artist,source,playing,position,duration,cover,received,"");}
    public static final Track EMPTY = new Track("","","",false,0,0,"",System.nanoTime());
    public static Track demo() { return new Track("Midnight in the Overworld","Your soundtrack. Your world.","PREVIEW",true,83,224,"",System.nanoTime()); }
    public boolean present() { return !title.isBlank(); }
    public double elapsed() {
        double elapsed=Math.max(0,position+(playing?Math.max(0,(System.nanoTime()-received)/1e9):0));
        return duration>0?Math.min(duration,elapsed):elapsed;
    }
}
