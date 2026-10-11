package dev.bettermusic;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.Flow;

class BoundedBodyTest {
    private static class Subscription implements Flow.Subscription {boolean cancelled;public void request(long n){}public void cancel(){cancelled=true;}}
    @Test void acceptsUtf8AndRejectsOversizedBodyDuringReceipt(){
        var body=new BoundedBody();var sub=new Subscription();body.onSubscribe(sub);body.onNext(List.of(ByteBuffer.wrap("{\"ok\":true}".getBytes(java.nio.charset.StandardCharsets.UTF_8))));body.onComplete();assertEquals("{\"ok\":true}",body.getBody().toCompletableFuture().join());
        var large=new BoundedBody();var upstream=new Subscription();large.onSubscribe(upstream);large.onNext(List.of(ByteBuffer.allocate(700000)));large.onNext(List.of(ByteBuffer.allocate(400000)));assertTrue(upstream.cancelled);assertTrue(large.getBody().toCompletableFuture().isCompletedExceptionally());
    }
}
