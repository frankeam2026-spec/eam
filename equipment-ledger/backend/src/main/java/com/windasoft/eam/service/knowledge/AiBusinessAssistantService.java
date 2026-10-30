package com.windasoft.eam.service.knowledge;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.knowledge.AiBusinessPlan;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AiBusinessAssistantService {
  private final AiGateway gateway;
  private final AiBusinessQueryService queries;
  private final AiAnalyticsService analytics;
  private final com.windasoft.eam.service.system.WorkInboxService inbox;
  private final ObjectMapper json;

  public AiBusinessAssistantService(
      AiGateway gateway,
      AiBusinessQueryService queries,
      AiAnalyticsService analytics,
      com.windasoft.eam.service.system.WorkInboxService inbox,
      ObjectMapper json) {
    this.gateway = gateway;
    this.queries = queries;
    this.analytics = analytics;
    this.inbox = inbox;
    this.json = json;
  }

  public Object tryAnswer(String question, List<String> previous, Long equipment) throws Exception {
    return tryAnswer(question, previous, equipment, null);
  }

  public Object tryAnswer(String question, List<String> previous, Long equipment, JsonNode context)
      throws Exception {
    var lastPlans = new ArrayList<AiBusinessPlan>();
    if (context != null && !context.isNull()) {
      if (!context.isArray() || context.size() > 6) throw new IllegalArgumentException("追问上下文无效");
      for (var plan : context) lastPlans.add(AiBusinessPlan.parse(plan));
    }
    String instructions =
        """
你是设备管理系统小卓的只读查询规划器。只识别意图和条件，不编造结果，不生成SQL，不执行写操作。
返回JSON：operation(BUSINESS/ANALYTICS/TODO/KNOWLEDGE/CLARIFY)、queries数组、message字符串。只有明确查询手册/SOP/操作方法/原理/故障排查才KNOWLEDGE；未支持业务数据不能转知识库。
BUSINESS的每项查询格式：{domain,operation:LIST/COUNT/GROUP/TREND,where:null或条件树,groupBy:[],aggregate:count/distinct/sum/avg/min/max,measure:"",sortBy:"",direction:asc/desc,timeField,period:all/today/yesterday/week/lastWeek/month/lastMonth/year/lastYear/last7/last30/last90/custom,from:"",to:"",bucket:day/month/year,topN:0或1至100}。
不适用的可选字段直接省略；aggregate默认count，bucket默认month，不能为不适用字段编造枚举值。相对日期优先用period预设（如lastMonth/month/week），custom的from/to均为包含当日，不要把结束日期填成次日；同一时间范围不在where重复添加。
where叶子为{field,op:eq/ne/contains/in/gte/lte/gt/lt/empty/notEmpty,values:[字符串]}；逻辑节点为{op:AND/OR/NOT,children:[条件]}。最大嵌套4层，24个节点；最多2个分组维度。关联ID不能猜；问名称用名称字段。本人仅ownerId/reporterId可使用$me；日期可使用$today，时间可使用$now；范围由后端算。
“我们公司/本公司”必须使用currentCompany的companyId筛选；currentCompany为空时先询问公司，不能省略公司条件。未指定公司则按授权范围查询。
所有域、字段、类型必须来自目录。缺少所需字段/指标或不能完整表达条件时CLARIFY说明具体缺口，不能丢筛选后返回全量。姓名/组织同名不猜测。不要把“所有设备”当设备名称筛选。
设备按分类、公司、部门、状态、品牌统计：GROUP+count；“几台设备”COUNT；列出/哪些/查某个编号：LIST。GROUP/TREND按数量占比、排行展示，默认value降序；“前10”设置topN=10；未限定数量用topN=0。
维修数量是工单数，非独立故障数。报修时间用reportedAt；接单用claimedAt；提交解决用submittedAt；验收完成用acceptedAt，不混淆。
保养计划用maintenancePlan，任务用maintenanceTask；日常点检与周期巡检任务/计划分别使用dailyTask/dailyPlan/periodicTask/periodicPlan。笼统“点巡检”拆成dailyTask和periodicTask并列，不能漏一种。
保养按计划日期dueDate归属；过期未完成用expiresDate < $today，并排除已完成/人工已作废，自动过期作废仍可呈现。点巡检按dueAt归属；超期用expiresAt < $now并排除已完成/已审核/已免检/人工已作废。
inspection的abnormal/checked/missing/expected为项目数，可以SUM，不当作任务数。当前负责人owner不是实际点检人；问实际点检人或检查点明细若目录没有，CLARIFY。
状态规范：维修：待派工、待接单、处理中、挂起、待验收、已完成、已撤销；保养：待执行、执行中、挂起、待验收、已完成、已作废、过期作废；点巡检：未开始、待执行、执行中、待审核、已完成、已审核、审核不通过、已过期、已作废、过期作废、已免检；计划：启用/停用。设备“闲置”通常是useState，故障是status。
股票/金融与本系统无关。stock是库存余额；问库存多少用sum(quantity现存/reserved预留/available可用)，必须按unit分别统计；问物料品种用distinct(code)。库存记录数不代表备件数量；不推断安全库存和呆滞阈值。
设备没有时间条件默认all；历史明确时间才加period。任务“今天/本周”包括该计划周期内未到时点的任务，不把未来全部任务当成今天。
领导概览最多拆6项真实查询，分别列设备状态、维修当前积压、保养、日常点检、周期巡检执行；期间与当前积压分开，不以无权限当零。跨期对比拆同字段两期查询，不比较半个月与整月后编造增长结论。
已有完成率/按期率、维修/用料金额、故障类型原因分布、人员工作量的统计优先ANALYTICS；MTTR/MTBF/OEE/产量利润损失没有可靠来源时明确缺口，不拿工单等待或接单到验收冒充实际维修时间。
“我的待办/待我审批”使用TODO；queries每项是{module,kind,state:pending/completed,search:""}，module/kind从todoKinds选择。全部待办显式用module=""、kind=""；审批/验收/接单等必须选择对应kind，可拆多项，不能返回所有待办替代待审批。无法表达日期/公司等条件时CLARIFY；当前待办不等于今天到期任务。它只返回本人事项，不代人审批。
根据previousValidatedPlans（上次查询结构）和最多3条最近用户问题理解追问，当前问题的显式条件优先；“只看白班/改成上个月/按部门分/列出这些”保留其他仍有效条件；明确换主题时不沿用旧条件。
KNOWLEDGE/ANALYTICS/CLARIFY的queries必须为空。message使用客户可懂的业务名称，不暴露字段英文名，不建议目录尚未接入的查询。message仅用于澄清或说明数据缺口，不能包含自行编出的数值、结论或声称已经发送报表。
""";
    var previousQuestions = previous.stream().skip(Math.max(0, previous.size() - 3)).toList();
    var todoKinds = inbox.assistantKinds();
    boolean currentCompanyRequested =
        java.util.stream.Stream.concat(
                java.util.stream.Stream.of(question), previousQuestions.stream())
            .anyMatch(text -> text.matches("(?s).*(我们公司|本公司|所在公司|自己公司).*"));
    boolean companyInContext =
        lastPlans.stream()
            .anyMatch(
                plan ->
                    plan.where().findValues("field").stream()
                        .anyMatch(
                            field -> Set.of("company", "companyId").contains(field.asText())));
    boolean resetCompany = question.matches("(?s).*(全部公司|所有公司|整个集团|全集团).*");
    var company =
        !resetCompany && (currentCompanyRequested || companyInContext)
            ? Objects.requireNonNullElse(queries.companyContext(), Map.<String, Object>of())
            : Map.<String, Object>of();
    var payload =
        Map.of(
            "question",
            question,
            "previousUserQuestions",
            previousQuestions,
            "today",
            LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(),
            "catalog",
            queries.catalog(),
            "todoKinds",
            todoKinds,
            "previousValidatedPlans",
            lastPlans,
            "currentCompany",
            company);
    var body =
        Map.of(
            "enable_thinking",
            false,
            "max_tokens",
            2200,
            "response_format",
            Map.of("type", "json_object"),
            "messages",
            List.of(
                Map.of("role", "system", "content", instructions),
                Map.of("role", "user", "content", json.writeValueAsString(payload))));
    var response = gateway.post("/chat/completions", body, false);
    final JsonNode intent;
    try {
      intent =
          json.readTree(response.path("choices").path(0).path("message").path("content").asText());
    } catch (com.fasterxml.jackson.core.JsonProcessingException error) {
      return clarification("本次问题未能转换为有效查询，请明确业务对象和筛选条件。");
    }
    if (intent == null || !intent.isObject()) return clarification("请明确要查询的业务和条件。");
    String operation = intent.path("operation").asText();
    if (operation.equals("KNOWLEDGE")) return null;
    if (operation.equals("ANALYTICS")) {
      var result = analytics.tryAnswer(question, previous);
      return result == null ? clarification("当前指标目录尚未提供该指标，请明确指标口径和数据来源。") : result;
    }
    if (operation.equals("TODO")) {
      var selected = intent.path("queries");
      if (!selected.isArray() || selected.isEmpty() || selected.size() > 6)
        return clarification("请明确要查全部待办，还是审批、验收或接单等具体事项。");
      var cards = new ArrayList<Map<String, Object>>();
      for (var choice : selected) {
        if (!choice.isObject()) return clarification("待办查询条件无效。");
        var keys = choice.fieldNames();
        while (keys.hasNext())
          if (!Set.of("module", "kind", "state", "search").contains(keys.next()))
            return clarification("该待办筛选条件暂不支持，未执行忽略条件的查询。");
        String module = choice.path("module").asText(),
            kind = choice.path("kind").asText(),
            state = choice.path("state").asText("pending"),
            search = choice.path("search").asText("");
        if (!Set.of("pending", "completed").contains(state)
            || search.length() > 200
            || (!module.isEmpty() || !kind.isEmpty())
                && todoKinds.stream()
                    .noneMatch(
                        row ->
                            Objects.toString(row.get("module"), "").equals(module)
                                && Objects.toString(row.get("kind"), "").equals(kind)))
          return clarification("未匹配到你有权查看的待办类型，请明确事项类型。");
        var result =
            new LinkedHashMap<>(
                (Map<String, Object>) inbox.list("todo", state, module, kind, search, 1, 20));
        result.put(
            "criteria", Map.of("module", module, "kind", kind, "state", state, "search", search));
        cards.add(result);
      }
      return Map.of(
          "answer",
          "已查询本人事项，办理资格仍由原业务校验；当前待办不等于今天到期任务。",
          "citations",
          List.of(),
          "workInboxes",
          cards);
    }
    if (!operation.equals("BUSINESS"))
      return clarification(intent.path("message").asText("请明确业务对象、时间和筛选条件。"));
    var plans = intent.path("queries");
    if (!plans.isArray() || plans.isEmpty() || plans.size() > 6)
      return clarification("单次最多查询6组明确业务，请缩小查询范围。");
    // Validate every plan before running any of them; never drop an invalid filter.
    try {
      for (var input : plans) {
        var plan = AiBusinessPlan.parse(input);
        checkIds(
            plan.where(),
            AiBusinessCatalog.ALL.get(plan.domain()),
            question + "\n" + String.join("\n", previousQuestions),
            company);
      }
    } catch (IllegalArgumentException error) {
      return clarification(error.getMessage());
    }
    var results = new ArrayList<Map<String, Object>>();
    var lines = new ArrayList<String>();
    for (var plan : plans) {
      try {
        var result = queries.query(plan, equipment, 1, 20);
        results.add(result);
        lines.add(
            result.get("title")
                + "："
                + (result.containsKey("value")
                    ? result.get("valueLabel")
                        + " "
                        + Objects.toString(result.get("value"), "无有效数值样本")
                    : result.get("records") + "条记录")
                + "；"
                + result.get("range")
                + (result.get("timeField").toString().isBlank()
                    ? ""
                    : "（按" + result.get("timeField") + "）"));
      } catch (IllegalArgumentException error) {
        lines.add(error.getMessage());
      } catch (ResponseStatusException error) {
        if (error.getStatusCode().value() != 403) throw error;
        lines.add("该业务无查看权限，未纳入本次结果。");
      }
    }
    return Map.of(
        "answer",
        String.join("\n", lines) + "\n结果按当前授权范围查询；历史会话请重新查询最新数据。",
        "citations",
        List.of(),
        "businessQueries",
        results);
  }

  private static void checkIds(
      JsonNode node,
      AiBusinessCatalog.Domain domain,
      String question,
      Map<String, Object> company) {
    if (node.isNull() || node.isMissingNode()) return;
    if (node.has("children")) {
      for (var child : node.path("children")) checkIds(child, domain, question, company);
      return;
    }
    String field = node.path("field").asText();
    if (!domain.fields().get(field).type().equals("ID")) return;
    for (var value : node.path("values")) {
      String id = value.asText();
      if (id.equals("$me")
          || field.equals("companyId") && id.equals(Objects.toString(company.get("id"), "")))
        continue;
      if (!java.util.regex.Pattern.compile(
              "(?<![0-9])" + java.util.regex.Pattern.quote(id) + "(?![0-9])")
          .matcher(question)
          .find())
        throw new IllegalArgumentException(
            "无法确认所选" + domain.fields().get(field).label() + "，请提供对象名称或编号，不能猜测关联ID。");
    }
  }

  static Object clarification(String message) {
    return Map.of("answer", message.isBlank() ? "请明确业务对象和条件。" : message, "citations", List.of());
  }
}
