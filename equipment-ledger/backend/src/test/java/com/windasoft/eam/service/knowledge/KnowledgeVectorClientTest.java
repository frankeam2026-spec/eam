package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.Test;

class KnowledgeVectorClientTest {
  @Test void sendsStableIdsTenantAndVersionWithoutSourceText() throws Exception {
    var json=new ObjectMapper();var requests=new CopyOnWriteArrayList<String>();
    var server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      requests.add(exchange.getRequestURI()+"\n"+new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
      byte[] body="{\"result\":{\"config\":{\"params\":{\"vectors\":{\"size\":2,\"distance\":\"Cosine\"}}}}}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
    });
    server.start();
    try{
      var client=new KnowledgeVectorClient(json,"http://127.0.0.1:"+server.getAddress().getPort(),"");
      client.ensure("test-model",2);
      var points=List.of(new KnowledgeVectorClient.Point(9007199254740993L,10,20,30,List.of(0.1,0.2)));
      client.upsert("test-model",points);client.upsert("test-model",points);
      assertEquals(3,requests.size());assertEquals(requests.get(1),requests.get(2));
      assertTrue(requests.get(1).contains("wait=true"));
      var point=json.readTree(requests.get(1).split("\n",2)[1]).path("points").get(0);
      assertEquals(9007199254740993L,point.path("id").asLong());
      assertEquals("10",point.path("payload").path("tenant_id").asText());
      assertEquals("30",point.path("payload").path("version_id").asText());
      assertFalse(point.path("payload").has("text_content"));
    }finally{server.stop(0);}
  }
  @Test void modelAndDimensionUseIndependentCollections(){
    assertNotEquals(KnowledgeVectorClient.collection("a",2),KnowledgeVectorClient.collection("b",2));
    assertNotEquals(KnowledgeVectorClient.collection("a",2),KnowledgeVectorClient.collection("a",3));
  }
}
