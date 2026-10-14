package dev.bettermusic;

import org.junit.jupiter.api.Test;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class LrclibClientTest {
    @Test void requestsIdentifyTheProjectAndWaitAfterPreviousResponse()throws Exception{
        var clock=new AtomicLong(10_000);var starts=new ArrayList<Long>();
        var client=new LrclibClient(request->{
            starts.add(clock.get());assertTrue(request.headers().firstValue("User-Agent").orElse("").contains("https://github.com/LootingVI/BetterMusicOverlay"));
            assertEquals("application/json",request.headers().firstValue("Accept").orElse(""));clock.addAndGet(100);
            return new LrclibClient.Reply(200,"application/json; charset=utf-8","","{}");
        },clock::get,clock::addAndGet);
        client.get("get/1",()->{});client.get("search?q=Original",()->{});assertEquals(List.of(10_000L,10_350L),starts);
    }
    @Test void htmlRateLimitHonorsEntireCooldownAcrossCalls()throws Exception{
        var clock=new AtomicLong(10_000);var calls=new AtomicInteger();
        var client=new LrclibClient(request->calls.getAndIncrement()==0?new LrclibClient.Reply(429,"text/html","900","<html>busy</html>"):new LrclibClient.Reply(200,"application/json","","{}"),clock::get,clock::addAndGet);
        assertThrows(LrclibClient.CoolingDown.class,()->client.get("get/1",()->{}));clock.addAndGet(300_001);
        assertThrows(LrclibClient.CoolingDown.class,()->client.get("get/2",()->{}));assertEquals(1,calls.get());
        clock.set(910_000);assertNotNull(client.get("get/2",()->{}));assertEquals(2,calls.get());
    }
    @Test void overloadRetriesAfterHeaderAndLongWaitsDoNotBlock()throws Exception{
        var clock=new AtomicLong(10_000);var calls=new AtomicInteger();
        var client=new LrclibClient(request->calls.getAndIncrement()==0?new LrclibClient.Reply(503,"text/plain","2","busy"):new LrclibClient.Reply(200,"application/json","","[]"),clock::get,clock::addAndGet);
        assertTrue(client.get("search?q=Original",()->{}).isJsonArray());assertEquals(12_000,clock.get());assertEquals(2,calls.get());
        var longWait=new LrclibClient(request->new LrclibClient.Reply(503,"text/plain","600","busy"),clock::get,ms->fail("Long cooldown must not occupy the worker"));
        assertThrows(LrclibClient.CoolingDown.class,()->longWait.get("get/1",()->{}));
        assertEquals(1000,LrclibClient.retryMillis("Sun, 11 Oct 2026 00:00:01 GMT",java.time.Instant.parse("2026-10-11T00:00:00Z").toEpochMilli(),60_000));
    }
    @Test void contentTypesAndCancellationAreCheckedBeforeJson()throws Exception{
        var clock=new AtomicLong(10_000);var client=new LrclibClient(request->new LrclibClient.Reply(200,"text/html","","<html>gateway</html>"),clock::get,clock::addAndGet);
        assertThrows(IOException.class,()->client.get("get/1",()->{}));
        var checks=new AtomicInteger();var sends=new AtomicInteger();
        var cancelled=new LrclibClient(request->{sends.incrementAndGet();return new LrclibClient.Reply(200,"application/json","","{}");},clock::get,clock::addAndGet);
        assertThrows(InterruptedException.class,()->cancelled.get("get/1",()->{if(checks.incrementAndGet()==2)throw new InterruptedException();}));assertEquals(0,sends.get());
    }
    @Test void cooldownReportsRemainingDelayAndDistinguishesRateLimits(){
        var clock=new AtomicLong(10_000);
        var busy=new LrclibClient(request->new LrclibClient.Reply(503,"text/plain","1","busy"),clock::get,clock::addAndGet);
        var overload=assertThrows(LrclibClient.CoolingDown.class,()->busy.get("search?q=Original",()->{}));assertTrue(overload.busy);assertEquals(1000,overload.retryAfterMillis);
        var limited=new LrclibClient(request->new LrclibClient.Reply(429,"text/plain","90","limited"),clock::get,clock::addAndGet);
        var rate=assertThrows(LrclibClient.CoolingDown.class,()->limited.get("search?q=Original",()->{}));assertFalse(rate.busy);assertEquals(90_000,rate.retryAfterMillis);
    }
}
