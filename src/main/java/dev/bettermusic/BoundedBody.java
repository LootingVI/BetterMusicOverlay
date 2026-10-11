package dev.bettermusic;

import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.Flow;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Enforces the size cap during receipt, so the HTTP timeout covers the complete body. */
final class BoundedBody implements HttpResponse.BodySubscriber<String> {
    private final HttpResponse.BodySubscriber<String> delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
    private Flow.Subscription subscription;
    private int bytes;
    private boolean finished;
    @Override public CompletionStage<String> getBody(){return delegate.getBody();}
    @Override public void onSubscribe(Flow.Subscription s){subscription=s;delegate.onSubscribe(s);}
    @Override public void onNext(List<ByteBuffer> buffers){
        if(finished)return;
        for(var b:buffers){if(b.remaining()>1_000_000-bytes){finished=true;subscription.cancel();delegate.onError(new IOException("Lyrics response exceeds 1 MB"));return;}bytes+=b.remaining();}
        delegate.onNext(buffers);
    }
    @Override public void onError(Throwable error){if(!finished){finished=true;delegate.onError(error);}}
    @Override public void onComplete(){if(!finished){finished=true;delegate.onComplete();}}
}
