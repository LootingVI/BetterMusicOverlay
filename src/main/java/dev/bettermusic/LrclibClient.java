package dev.bettermusic;

import com.google.gson.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.function.LongSupplier;

/** Sequential, bounded requests. Cooldowns survive reloads and track changes. */
final class LrclibClient {
    static final String USER_AGENT="BetterMusicOverlay/1.4.1 (https://github.com/LootingVI/BetterMusicOverlay)";
    record Reply(int status,String type,String retryAfter,String body){}
    @FunctionalInterface interface Transport {Reply send(HttpRequest request)throws Exception;}
    @FunctionalInterface interface Pause {void sleep(long millis)throws InterruptedException;}
    @FunctionalInterface interface Check {void run()throws InterruptedException;}
    static final class CoolingDown extends IOException {CoolingDown(String message){super(message);}}
    private final Transport transport;
    private final LongSupplier clock;
    private final Pause pause;
    private long nextRequest,nextAllowed;
    private String cooldown="LRCLIB busy · wait before reloading";
    LrclibClient(HttpClient http){
        this(request->{var response=http.send(request,info->new BoundedBody());return new Reply(response.statusCode(),response.headers().firstValue("Content-Type").orElse(""),response.headers().firstValue("Retry-After").orElse(""),response.body());},System::currentTimeMillis,Thread::sleep);
    }
    LrclibClient(Transport transport,LongSupplier clock,Pause pause){this.transport=transport;this.clock=clock;this.pause=pause;}
    JsonElement get(String path,Check check)throws Exception{
        for(int attempt=0;attempt<2;attempt++){
            check.run();if(clock.getAsLong()<nextAllowed)throw new CoolingDown(cooldown);
            long gap=nextRequest-clock.getAsLong();if(gap>0)pause.sleep(gap);check.run();
            var request=HttpRequest.newBuilder(URI.create("https://lrclib.net/api/"+path)).timeout(Duration.ofSeconds(8)).header("User-Agent",USER_AGENT).header("Accept","application/json").GET().build();
            Reply response;
            try{response=transport.send(request);}finally{nextRequest=after(clock.getAsLong(),250);}
            // Process cooldown headers even if a request was made stale while receiving its response.
            if(response.status()==429||response.status()==503){
                long delay=retryMillis(response.retryAfter(),clock.getAsLong(),response.status()==429?60_000:1000);
                nextAllowed=after(clock.getAsLong(),delay);
                cooldown=response.status()==429?"LRCLIB rate limit · wait before reloading":"LRCLIB busy · wait before reloading";
                check.run();
                if(response.status()==503&&attempt==0&&delay<=2000){pause.sleep(delay);continue;}
                throw new CoolingDown(cooldown);
            }
            check.run();
            if(response.status()==404)return null;
            if(response.status()>=500&&attempt==0){pause.sleep(1000);continue;}
            if(response.status()!=200)throw new IOException("LRCLIB HTTP "+response.status());
            String type=response.type().split(";",2)[0].strip().toLowerCase(Locale.ROOT);
            if(!type.equals("application/json")&&!(type.startsWith("application/")&&type.endsWith("+json")))throw new IOException("LRCLIB returned non-JSON content");
            return JsonParser.parseString(response.body());
        }
        throw new IOException("LRCLIB unavailable");
    }
    static long retryMillis(String header,long now,long fallback){
        try{long seconds=Long.parseLong(header.strip());if(seconds>=0)return seconds>Long.MAX_VALUE/1000?Long.MAX_VALUE:seconds*1000;}catch(NumberFormatException ignored){}
        try{return Math.max(0,ZonedDateTime.parse(header,DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli()-now);}catch(RuntimeException ignored){return fallback;}
    }
    private static long after(long now,long delay){return delay>Long.MAX_VALUE-now?Long.MAX_VALUE:now+delay;}
}
