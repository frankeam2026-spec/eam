package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Qdrant contains identifiers and vectors, never the authoritative text or authorization. */
@Component
public class KnowledgeVectorClient {
  private final ObjectMapper json;
  private final String endpoint,key;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  public KnowledgeVectorClient(ObjectMapper json,@Value("${eam.ai.qdrant-url:http://127.0.0.1:6333}") String endpoint,
      @Value("${eam.ai.qdrant-api-key:}") String key){this.json=json;this.endpoint=endpoint;this.key=key;}
  static String collection(String model,int dimension){
    return "eam_knowledge_"+UUID.nameUUIDFromBytes(model.getBytes(StandardCharsets.UTF_8)).toString().replace("-","")+"_"+dimension;
  }
  private HttpResponse<String> send(String method,String path,Object body) throws Exception {
    URI base=URI.create(endpoint);
    if(!Set.of("http","https").contains(base.getScheme()) || base.getHost()==null || base.getUserInfo()!=null || base.getQuery()!=null || base.getFragment()!=null)
      throw new IllegalStateException("Qdrant地址配置无效");
    URI uri=URI.create(endpoint.replaceAll("/+$","")+path);
    var builder=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(30)).header("Content-Type","application/json");
    if(!key.isBlank())builder.header("api-key",key);
    builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)));
    try{return http.send(builder.build(),HttpResponse.BodyHandlers.ofString());}
    catch(InterruptedException error){Thread.currentThread().interrupt();throw new IllegalStateException("索引操作已中断");}
    catch(java.io.IOException error){throw new IllegalStateException("无法连接Qdrant，请检查本地索引服务");}
  }
  private JsonNode checked(HttpResponse<String> response) throws Exception {
    if(response.statusCode()<200 || response.statusCode()>=300)throw new IllegalStateException("Qdrant操作失败（HTTP "+response.statusCode()+"）");
    if(response.body().length()>2_000_000)throw new IllegalStateException("Qdrant响应超限");
    return json.readTree(response.body());
  }
  public void ensure(String model,int dimension) throws Exception {
    String path="/collections/"+collection(model,dimension);
    var existing=send("GET",path,null);
    if(existing.statusCode()==404){
      var created=send("PUT",path,Map.of("vectors",Map.of("size",dimension,"distance","Cosine")));
      if(created.statusCode()!=409)checked(created);
      existing=send("GET",path,null);
    }
    var config=checked(existing).path("result").path("config").path("params").path("vectors");
    if(config.path("size").asInt()!=dimension || !config.path("distance").asText().equals("Cosine"))
      throw new IllegalStateException("Qdrant集合与当前向量模型不匹配");
  }
  public record Point(long chunk,long tenant,long company,long version,List<Double> vector){}
  public List<Long> search(String model,List<Double> vector,long tenant,List<Long> versions) throws Exception {
    if(versions.isEmpty())return List.of();
    var filter=Map.of("must",List.of(
        Map.of("key","tenant_id","match",Map.of("value",Long.toString(tenant))),
        Map.of("key","version_id","match",Map.of("any",versions.stream().map(String::valueOf).toList()))));
    var result=checked(send("POST","/collections/"+collection(model,vector.size())+"/points/query",
        Map.of("query",vector,"filter",filter,"limit",12,"with_payload",false,"with_vector",false)));
    var points=result.path("result").path("points");
    if(!points.isArray())throw new IllegalStateException("检索服务返回结构无效");
    var ids=new LinkedHashSet<Long>();
    for(var point:points){if(!point.path("id").isIntegralNumber() || !point.path("id").canConvertToLong())throw new IllegalStateException("检索切片标识无效");ids.add(point.path("id").asLong());}
    return List.copyOf(ids);
  }
  public void upsert(String model,List<Point> points) throws Exception {
    if(points.isEmpty())return;
    int dimension=points.getFirst().vector().size();
    checked(send("PUT","/collections/"+collection(model,dimension)+"/points?wait=true",Map.of("points",
        points.stream().map(p->Map.of("id",p.chunk(),"vector",p.vector(),"payload",Map.of(
            "tenant_id",Long.toString(p.tenant()),"company_id",Long.toString(p.company()),"version_id",Long.toString(p.version())))).toList())));
  }
}
