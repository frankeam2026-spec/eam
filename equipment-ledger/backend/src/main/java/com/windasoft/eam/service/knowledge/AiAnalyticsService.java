package com.windasoft.eam.service.knowledge;
import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.service.experience.HomeAnalyticsService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class AiAnalyticsService {
  private final HomeAnalyticsService analytics;private final AiGateway gateway;private final PermissionService permissions;private final ObjectMapper json;
  public AiAnalyticsService(HomeAnalyticsService analytics,AiGateway gateway,PermissionService permissions,ObjectMapper json){this.analytics=analytics;this.gateway=gateway;this.permissions=permissions;this.json=json;}
  @SuppressWarnings("unchecked")
  public Object tryAnswer(String question,List<String> previous) throws Exception {
    if(!permissions.allowed("workspace","查看"))return Map.of("answer","你没有统计查看权限，请联系管理员。","citations",List.of());
    if(question.contains("故障率"))return Map.of("answer","目前可查询设备报修次数排名，尚无统一的故障率分母。报修次数不等于故障率。你要查看报修次数排名吗？","citations",List.of());
    var catalog=analytics.catalog();
    var body=Map.of("enable_thinking",false,"max_tokens",700,"response_format",Map.of("type","json_object"),"messages",List.of(
        Map.of("role","system","content","将问题转换为允许的指标查询，不能生成SQL。只返回JSON：operation为QUERY/CLARIFY/KNOWLEDGE。QUERY字段metric、scope(personal/team)、topN(5/10/20)、period{preset,from,to}、companyIds字符串数组、departmentIds字符串数组。只能使用目录指标和组织ID。period.preset支持yesterday/last7/last90/month/lastMonth/quarter/year/last12months/custom。未指定周期默认month。公司或部门有歧义、指标不支持返回CLARIFY及message，不能忽略用户筛选。不涉及统计返回KNOWLEDGE。"),
        Map.of("role","user","content",json.writeValueAsString(Map.of("question",question,"previousUserQuestions",previous,"catalog",catalog)))));
    var response=gateway.post("/chat/completions",body,false);
    var intent=json.readTree(response.path("choices").path(0).path("message").path("content").asText());
    if(intent.path("operation").asText().equals("KNOWLEDGE"))return null;
    if(!intent.path("operation").asText().equals("QUERY"))return Map.of("answer",intent.path("message").asText("请明确指标、时间范围和公司或部门。"),"citations",List.of());
    var metrics=(List<Map<String,Object>>)catalog.get("data");
    String metricId=intent.path("metric").asText();
    var metric=metrics.stream().filter(m->metricId.equals(m.get("id"))).findFirst().orElseThrow(()->new IllegalArgumentException("指标不在当前授权目录中"));
    var widget=new LinkedHashMap<String,Object>();widget.put("id","xiaozhu");widget.put("metric",metricId);
    String scope=intent.path("scope").asText("personal");if(!Set.of("personal","team").contains(scope))throw new IllegalArgumentException("统计范围无效");widget.put("scope",scope);
    int top=intent.path("topN").asInt(10);if(!Set.of(5,10,20).contains(top))throw new IllegalArgumentException("排名数量无效");widget.put("topN",top);
    var period=intent.path("period");if(!period.isObject())throw new IllegalArgumentException("统计周期不明确");
    Map<String,Object> periodMap=json.convertValue(period,Map.class);widget.put("period",periodMap);
    for(String field:List.of("companyIds","departmentIds")){
      var ids=intent.path(field);if(!ids.isArray()||ids.size()>20)throw new IllegalArgumentException("组织筛选参数无效");
      var selected=new ArrayList<String>();for(var id:ids){if(!id.isTextual()||!id.asText().matches("[0-9]+"))throw new IllegalArgumentException("组织ID无效");selected.add(id.asText());}widget.put(field,selected);
    }
    var queryContext=Map.of("period",periodMap,"companyIds",widget.get("companyIds"),"departmentIds",widget.get("departmentIds"));
    var result=(Map<String,Object>)analytics.query(Map.of("widgets",List.of(widget),"context",queryContext));
    var data=((List<Map<String,Object>>)result.get("data")).getFirst();
    var out=new LinkedHashMap<String,Object>();out.put("answer","已按系统统计口径查询："+metric.get("name")+"。统计图表显示本次查询结果；历史会话请重新查询最新结果。");out.put("citations",List.of());
    out.put("analytics",Map.of("metric",metric,"result",data,"widget",widget,"context",queryContext));return out;
  }
}
