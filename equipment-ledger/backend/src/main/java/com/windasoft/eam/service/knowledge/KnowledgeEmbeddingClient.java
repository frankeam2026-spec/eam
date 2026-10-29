package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Server-configured model transport. Never exposes credentials or provider response bodies. */
@Component
public class KnowledgeEmbeddingClient {
  @org.springframework.beans.factory.annotation.Autowired private AiGateway gateway;
  private final String endpoint,key,model;
  private final ObjectMapper json;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
      .followRedirects(HttpClient.Redirect.NEVER).build();
  public KnowledgeEmbeddingClient(ObjectMapper json,
      @Value("${eam.ai.base-url:}") String endpoint,
      @Value("${DASHSCOPE_API_KEY:}") String key,
      @Value("${eam.ai.embedding-model:}") String model) {
    this.json=json;this.endpoint=endpoint;this.key=key;this.model=model;
  }
  public String model(){return gateway==null?model:gateway.model(true);}
  public List<List<Double>> embed(List<String> texts) throws Exception {
    if(gateway!=null){
      if(texts==null||texts.isEmpty()||texts.size()>10||texts.stream().anyMatch(t->t==null||t.isBlank()||t.length()>4000))throw new IllegalArgumentException("切片数量或长度无效");
      return decode(gateway.post("/embeddings",Map.of("input",texts),true).toString(),texts.size());
    }
    if(endpoint.isBlank() || key.isBlank() || model.isBlank())
      throw new IllegalStateException("请先配置AI服务地址、向量模型和DASHSCOPE_API_KEY");
    URI uri=URI.create(endpoint.replaceAll("/+$","")+"/embeddings");
    if(!"https".equals(uri.getScheme()) || uri.getHost()==null || uri.getUserInfo()!=null || uri.getQuery()!=null)
      throw new IllegalStateException("云端AI服务地址必须为不含凭据或查询参数的HTTPS地址");
    if(texts==null || texts.isEmpty() || texts.size()>10 || texts.stream().anyMatch(t->t==null || t.isBlank() || t.length()>4000))
      throw new IllegalArgumentException("每批向量化须为1至10个有效切片");
    var request=HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(45))
        .header("Authorization","Bearer "+key).header("Content-Type","application/json")
        .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(Map.of("model",model,"input",texts)))).build();
    final HttpResponse<String> response;
    try {response=http.send(request,HttpResponse.BodyHandlers.ofString());}
    catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("向量调用已中断",e);}
    catch(java.io.IOException e){throw new IllegalStateException("向量服务连接失败，请检查网络和服务配置");}
    if(response.statusCode()!=200) throw new IllegalStateException("向量服务调用失败（HTTP "+response.statusCode()+"）");
    if(response.body().length()>2_000_000)throw new IllegalStateException("向量服务响应超限");
    return decode(response.body(),texts.size());
  }
  List<List<Double>> decode(String body,int expected) throws Exception {
    var data=json.readTree(body).path("data");
    if(!data.isArray() || data.size()!=expected)throw new IllegalStateException("向量服务返回数量不符");
    var ordered=new ArrayList<List<Double>>(Collections.nCopies(expected,null));
    int dimension=-1;
    for(var item:data){
      if(!item.path("index").canConvertToInt())throw new IllegalStateException("向量序号无效");
      int index=item.path("index").asInt();
      var vector=item.path("embedding");
      if(index<0 || index>=expected || ordered.get(index)!=null || !vector.isArray() || vector.isEmpty() || vector.size()>8192)
        throw new IllegalStateException("向量服务返回结构无效");
      if(dimension<0)dimension=vector.size();
      if(vector.size()!=dimension)throw new IllegalStateException("向量维度不一致");
      var values=new ArrayList<Double>();
      for(var value:vector){
        if(!value.isNumber() || !Double.isFinite(value.asDouble()))throw new IllegalStateException("向量数值无效");
        values.add(value.asDouble());
      }
      ordered.set(index,List.copyOf(values));
    }
    return List.copyOf(ordered);
  }
}
