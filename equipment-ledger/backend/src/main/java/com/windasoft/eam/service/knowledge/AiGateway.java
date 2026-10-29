package com.windasoft.eam.service.knowledge;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.repository.knowledge.AiRuntimeRepository;
import com.windasoft.eam.service.system.*;
import java.math.*;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AiGateway {
  public static final int MAX_OUTPUT_TOKENS=2400;
  private final AiRuntimeRepository repository;private final AuthContext auth;private final PermissionService permissions;
  private final NotificationSecret secrets;private final TransactionTemplate tx;private final ObjectMapper json;
  private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).followRedirects(HttpClient.Redirect.NEVER).build();
  public AiGateway(AiRuntimeRepository repository,AuthContext auth,PermissionService permissions,NotificationSecret secrets,TransactionTemplate tx,ObjectMapper json){this.repository=repository;this.auth=auth;this.permissions=permissions;this.secrets=secrets;this.tx=tx;this.json=json;}
  static String month(){return YearMonth.now(ZoneId.of("Asia/Shanghai")).toString();}
  static BigDecimal decimal(Object value){return new BigDecimal(value.toString());}
  static boolean yes(Object value){return Boolean.TRUE.equals(value)||"1".equals(Objects.toString(value));}
  public String model(boolean embedding){return repository.settings(auth.tenantId(),false).get(embedding?"embedding_model":"chat_model").toString();}
  public boolean enabled(){return yes(repository.settings(auth.tenantId(),false).get("enabled"));}
  public record Settings(boolean enabled,String baseUrl,String apiKey,String chatModel,String embeddingModel,
      BigDecimal monthlyLimit,int warningPercent,BigDecimal inputPrice,BigDecimal outputPrice,BigDecimal embeddingPrice,long rowVersion,
      String visionModel,String audioModel,BigDecimal visionInputPrice,BigDecimal visionOutputPrice,BigDecimal audioMinutePrice){
    public Settings{visionModel=Objects.toString(visionModel,"");audioModel=Objects.toString(audioModel,"");visionInputPrice=visionInputPrice==null?BigDecimal.ZERO:visionInputPrice;visionOutputPrice=visionOutputPrice==null?BigDecimal.ZERO:visionOutputPrice;audioMinutePrice=audioMinutePrice==null?BigDecimal.ZERO:audioMinutePrice;}
  }
  static void address(String address){
    URI uri;try{uri=URI.create(address);}catch(IllegalArgumentException error){throw new IllegalArgumentException("请填写百炼官方HTTPS兼容接口地址");}
    String host=Objects.toString(uri.getHost(),"").toLowerCase(Locale.ROOT);
    boolean tokenPlan=host.equals("token-plan.maas.qianwenaiapi.com")
        &&Set.of("/compatible-mode/v1","/compatible-mode/v1/").contains(Objects.toString(uri.getPath(),""))
        &&(uri.getPort()==-1||uri.getPort()==443);
    if(!"https".equals(uri.getScheme())||!(host.endsWith(".aliyuncs.com")||tokenPlan)||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null)
      throw new IllegalArgumentException("请填写百炼官方HTTPS兼容接口地址");
  }
  public Object settings(){
    permissions.require("aiSetting","查看");var c=repository.settings(auth.tenantId(),false);var u=repository.usage(auth.tenantId(),month(),false);
    var result=new LinkedHashMap<String,Object>();
    result.put("enabled",yes(c.get("enabled")));result.put("baseUrl",c.get("base_url"));result.put("chatModel",c.get("chat_model"));result.put("embeddingModel",c.get("embedding_model"));
    result.put("hasApiKey",c.get("api_secret")!=null&&!c.get("api_secret").toString().isBlank());
    result.put("rowVersion",c.get("row_version"));result.put("monthlyLimit",c.get("monthly_limit"));result.put("warningPercent",c.get("warning_percent"));
    result.put("inputPrice",c.get("input_price"));result.put("outputPrice",c.get("output_price"));result.put("embeddingPrice",c.get("embedding_price"));
    result.put("estimatedSpent",u.get("estimated_spent"));result.put("reservedAmount",u.get("reserved_amount"));result.put("billingMonth",month());
    result.put("budgetWarning",decimal(c.get("monthly_limit")).signum()>0 && decimal(u.get("estimated_spent")).add(decimal(u.get("reserved_amount"))).multiply(BigDecimal.valueOf(100)).compareTo(decimal(c.get("monthly_limit")).multiply(decimal(c.get("warning_percent"))))>=0);
    result.put("visionModel",c.get("vision_model"));result.put("audioModel",c.get("audio_model"));result.put("visionInputPrice",c.get("vision_input_price"));result.put("visionOutputPrice",c.get("vision_output_price"));result.put("audioMinutePrice",c.get("audio_minute_price"));
    return result;
  }
  static void validate(Settings c){
    if(c.baseUrl()==null||c.baseUrl().isBlank())throw new IllegalArgumentException("请填写百炼接口地址");
    if(c.baseUrl().length()>500)throw new IllegalArgumentException("百炼接口地址不能超过500个字符");
    address(c.baseUrl());
    for(var model:List.of(Map.entry("文本模型版本",Objects.toString(c.chatModel(),"")),Map.entry("向量模型版本",Objects.toString(c.embeddingModel(),"")))){
      if(model.getValue().isBlank())throw new IllegalArgumentException("请填写"+model.getKey());
      if(!model.getValue().matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException(model.getKey()+"格式无效，请填写模型标识，不要填写显示名称或空格");
    }
    if(c.warningPercent()<1||c.warningPercent()>100)throw new IllegalArgumentException("预警比例须为1至100的整数");
    if(c.monthlyLimit()==null)throw new IllegalArgumentException("请填写月度硬限额");
    if(c.inputPrice()==null)throw new IllegalArgumentException("请填写文本输入价格");
    if(c.outputPrice()==null)throw new IllegalArgumentException("请填写文本输出价格");
    if(c.embeddingPrice()==null)throw new IllegalArgumentException("请填写向量输入价格");
  }
  public void save(Settings c){
    permissions.require("aiSetting","编辑");
    validate(c);
    for(var price:List.of(c.monthlyLimit(),c.inputPrice(),c.outputPrice(),c.embeddingPrice(),c.visionInputPrice(),c.visionOutputPrice(),c.audioMinutePrice()))if(price.signum()<0||price.compareTo(new BigDecimal("1000000"))>0||price.scale()>6)throw new IllegalArgumentException("预算与价格须为非负数，最多6位小数");
    for(String mediaModel:List.of(c.visionModel(),c.audioModel()))if(!mediaModel.isBlank()&&!mediaModel.matches("[A-Za-z0-9_.:-]{1,128}"))throw new IllegalArgumentException("媒体模型名称无效");
    if(c.enabled()&&(c.monthlyLimit().signum()==0||c.inputPrice().signum()==0||c.outputPrice().signum()==0||c.embeddingPrice().signum()==0))throw new IllegalArgumentException("启用前须填写预算和各项价格");
    tx.executeWithoutResult(s->{var old=repository.settings(auth.tenantId(),true);
      String secret=Objects.toString(old.get("api_secret"),"");if(c.apiKey()!=null&&!c.apiKey().isBlank()){if(c.apiKey().length()>1000)throw new IllegalArgumentException("密钥长度无效");secret=secrets.encrypt(c.apiKey());}
      if(c.enabled()&&secret.isBlank())throw new IllegalArgumentException("请填写模型API密钥");
      var values=new HashMap<String,Object>();values.put("enabled",c.enabled());values.put("baseUrl",c.baseUrl());values.put("chatModel",c.chatModel());values.put("embeddingModel",c.embeddingModel());values.put("monthlyLimit",c.monthlyLimit());values.put("warningPercent",c.warningPercent());values.put("inputPrice",c.inputPrice());values.put("outputPrice",c.outputPrice());values.put("embeddingPrice",c.embeddingPrice());values.put("rowVersion",c.rowVersion());values.put("visionModel",c.visionModel());values.put("audioModel",c.audioModel());values.put("visionInputPrice",c.visionInputPrice());values.put("visionOutputPrice",c.visionOutputPrice());values.put("audioMinutePrice",c.audioMinutePrice());repository.save(auth.tenantId(),values,secret);
    });
  }
  public Object calls(int page,int size){permissions.require("aiSetting","查看");return repository.calls(auth.tenantId(),page,size);}
  public JsonNode post(String path,Map<String,Object> body,boolean embedding) throws Exception {
    return post(path,body,embedding?"EMBEDDING":"CHAT",0);
  }
  public JsonNode post(String path,Map<String,Object> body,String purpose,double seconds) throws Exception {
    boolean embedding=purpose.equals("EMBEDDING"),vision=purpose.equals("VISION"),audio=purpose.equals("AUDIO");
    if(audio&&(!Double.isFinite(seconds)||seconds<=0||seconds>60))throw new IllegalArgumentException("录音时长无效");
    if(!Set.of("CHAT","EMBEDDING","VISION","AUDIO").contains(purpose))throw new IllegalArgumentException("调用用途无效");
    permissions.require("knowledge","查看");
    long tenant=auth.tenantId(),user=auth.userId(),id=IdWorker.getId(),started=System.nanoTime();String month=month();
    var c=repository.settings(tenant,false);if(!yes(c.get("enabled")))throw new IllegalStateException("小卓尚未启用，请联系管理员");
    String base=c.get("base_url").toString();address(base);
    String model=c.get(embedding?"embedding_model":vision?"vision_model":audio?"audio_model":"chat_model").toString();if(model.isBlank())throw new IllegalStateException("请先配置对应的媒体模型");
    var payload=new LinkedHashMap<>(body);payload.put("model",model);
    byte[] bytes=json.writeValueAsBytes(payload);if(bytes.length>(vision||audio?4_100_000:100_000))throw new IllegalArgumentException("本次问题和资料过长，请缩小范围");
    BigDecimal inputPrice=decimal(c.get(embedding?"embedding_price":vision?"vision_input_price":"input_price")),outputPrice=embedding?BigDecimal.ZERO:decimal(c.get(vision?"vision_output_price":"output_price"));
    if((vision&&(inputPrice.signum()<=0||outputPrice.signum()<=0))||(audio&&decimal(c.get("audio_minute_price")).signum()<=0))throw new IllegalStateException("请先配置媒体模型价格");
    int maxOutput=embedding?0:((Number)payload.getOrDefault("max_tokens",1200)).intValue();
    if(maxOutput<0||maxOutput>MAX_OUTPUT_TOKENS)throw new IllegalArgumentException("输出Token上限无效");
    BigDecimal reserved=audio?decimal(c.get("audio_minute_price")).multiply(BigDecimal.valueOf(seconds)).divide(BigDecimal.valueOf(60),6,RoundingMode.CEILING):cost(bytes.length,maxOutput,inputPrice,outputPrice);
    tx.executeWithoutResult(s->{var current=repository.settings(tenant,true);if(!yes(current.get("enabled"))||!current.get("row_version").equals(c.get("row_version")))throw new IllegalStateException("AI配置已改变，请重试");
      repository.recoverExpired(tenant);
      var usage=repository.usage(tenant,month,true);
      if(decimal(usage.get("estimated_spent")).add(decimal(usage.get("reserved_amount"))).add(reserved).compareTo(decimal(current.get("monthly_limit")))>0)throw new IllegalStateException("本月AI预算已达上限，请联系管理员");
      repository.reserve(id,tenant,user,month,purpose,model,reserved);
    });
    Long input=null,output=null;BigDecimal actual=reserved;boolean success=false;
    try{
      String key=secrets.decrypt(Objects.toString(c.get("api_secret"),""));
      var request=HttpRequest.newBuilder(URI.create(base.replaceAll("/+$","")+path)).timeout(Duration.ofSeconds(60)).header("Authorization","Bearer "+key).header("Content-Type","application/json").POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build();
      var response=AiHttpResponse.send(http,request,Duration.ofSeconds(60),2_000_000);
      byte[] responseBytes=response.body();
      if(response.statusCode()!=200)throw rejected(response.statusCode());
      if(responseBytes.length>2_000_000)throw new IllegalStateException("模型响应超过大小限制");
      var result=json.readTree(responseBytes);var usage=result.path("usage");
      if(usage.path("prompt_tokens").canConvertToLong()&&usage.path("completion_tokens").canConvertToLong()){
        input=usage.path("prompt_tokens").asLong();output=usage.path("completion_tokens").asLong();
      }else if(embedding&&usage.path("total_tokens").canConvertToLong()){input=usage.path("total_tokens").asLong();output=0L;}
      if(!audio&&input!=null&&input>=0&&output>=0)actual=cost(input,output,inputPrice,outputPrice);
      success=true;return result;
    }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException("模型调用已中断");}
    finally{BigDecimal charge=actual;Long in=input,out=output;boolean ok=success;long elapsed=(System.nanoTime()-started)/1_000_000;
      tx.executeWithoutResult(s->{repository.settings(tenant,true);repository.usage(tenant,month,true);repository.finish(id,tenant,month,reserved,charge,in,out,elapsed,ok);});}
  }
  static org.springframework.web.server.ResponseStatusException rejected(int status){
    String reason=switch(status){
      case 401 -> "模型密钥校验未通过（HTTP 401），请检查API密钥与接口所属地域、业务空间";
      case 403 -> "模型服务拒绝访问（HTTP 403），请检查业务空间及模型调用权限";
      case 404 -> "模型或接口未找到（HTTP 404），请检查接口地址与模型标识";
      case 429 -> "模型服务限流或额度不足（HTTP 429），请检查服务商额度后重试";
      case 400 -> "模型服务拒绝了请求参数（HTTP 400），请检查模型标识和接口兼容性";
      default -> "模型服务暂时不可用（HTTP "+status+"），请稍后重试";
    };
    return new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.BAD_GATEWAY,reason);
  }
  static BigDecimal cost(long input,long output,BigDecimal inputPrice,BigDecimal outputPrice){return inputPrice.multiply(BigDecimal.valueOf(input)).add(outputPrice.multiply(BigDecimal.valueOf(output))).divide(BigDecimal.valueOf(1_000_000),6,RoundingMode.CEILING);}
}
