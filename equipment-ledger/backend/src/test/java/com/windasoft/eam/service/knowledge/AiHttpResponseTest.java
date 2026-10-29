package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.sun.net.httpserver.HttpServer;
import java.net.*;
import java.net.http.*;
import java.time.Duration;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;

class AiHttpResponseTest {
  @Test void rejectsOversizedResponseBeforeKeepingWholeBody() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{try(exchange){exchange.sendResponseHeaders(200,1024);exchange.getResponseBody().write(new byte[1024]);}});
    server.start();
    try {
      var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).build();
      assertThrows(ExecutionException.class,()->AiHttpResponse.send(HttpClient.newHttpClient(),request,Duration.ofSeconds(5),100));
    } finally {server.stop(0);}
  }
  @Test void timesOutWhenHeadersArriveButBodyNeverCompletes() throws Exception {
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    var release=new CountDownLatch(1);
    server.createContext("/",exchange->{try(exchange){exchange.sendResponseHeaders(200,0);exchange.getResponseBody().write(1);exchange.getResponseBody().flush();try{release.await(5,TimeUnit.SECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}}});
    server.start();
    try {
      var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+server.getAddress().getPort()+"/")).build();
      assertThrows(TimeoutException.class,()->AiHttpResponse.send(HttpClient.newHttpClient(),request,Duration.ofMillis(300),100));
    } finally {release.countDown();server.stop(0);}
  }
}
