package com.windasoft.eam.service.knowledge;

import java.io.ByteArrayOutputStream;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;

/** Bounds both the complete response deadline and bytes retained in memory. */
final class AiHttpResponse {
  private AiHttpResponse() {}
  static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, Duration deadline, int limit) throws Exception {
    var pending=client.sendAsync(request, info -> new BoundedBody(limit));
    try { return pending.get(deadline.toMillis(),TimeUnit.MILLISECONDS); }
    finally { if(!pending.isDone())pending.cancel(true); }
  }
  private static final class BoundedBody implements HttpResponse.BodySubscriber<byte[]> {
    private final int limit;
    private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
    private final CompletableFuture<byte[]> body=new CompletableFuture<>();
    private Flow.Subscription subscription;
    BoundedBody(int limit){this.limit=limit;}
    public CompletionStage<byte[]> getBody(){return body;}
    public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
    public void onNext(List<ByteBuffer> buffers){
      for(var buffer:buffers){
        if(buffer.remaining()>limit-bytes.size()){
          subscription.cancel();body.completeExceptionally(new IllegalStateException("模型响应超过大小限制"));return;
        }
        byte[] part=new byte[buffer.remaining()];buffer.get(part);bytes.writeBytes(part);
      }
      subscription.request(1);
    }
    public void onError(Throwable error){body.completeExceptionally(error);}
    public void onComplete(){body.complete(bytes.toByteArray());}
  }
}
