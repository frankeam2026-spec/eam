package com.windasoft.eam.repository.maintenance;

import static com.windasoft.eam.repository.common.SqlListFilters.Kind.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.knowledge.AiBusinessCatalog;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceAssistantResult;
import com.windasoft.eam.repository.knowledge.AiBusinessQueryRepository;
import com.windasoft.eam.repository.knowledge.AiBusinessFilters;
import java.math.BigDecimal;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceListRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;
  private final AiBusinessQueryRepository assistant;

  public MaintenanceListRepository(JdbcListRepository lists, ObjectMapper json, AiBusinessQueryRepository assistant) {
    this.lists = lists;
    this.json = json;
    this.assistant = assistant;
  }

  public record Selection(String kind, String from, SqlPredicate predicate, String order) {}

  private AssistantReadSource assistantSource(Selection selected){
    String select="CAST(t.id AS CHAR) id,t.code,COALESCE(o.name,'') department,CAST(t.department_id AS CHAR) departmentId,t.created_at createdAt";
    if(selected.kind().equals("tasks")){
      for(String key:List.of("equipmentName","equipmentCode","planName","owner","dueDate","expiresDate","actualStartAt","actualEndAt"))select+=","+fields("tasks").get(key).expression()+" `"+key+"`";
      select+=",t.status,CAST(t.plan_id AS CHAR) planId,CAST(t.equipment_id AS CHAR) equipmentId,CAST(t.owner_id AS CHAR) ownerId,CAST(t.company_id AS CHAR) companyId,(SELECT c.name FROM eam_org_unit c WHERE c.tenant_id=t.tenant_id AND c.id=t.company_id) company";
    }else select+=",t.name,"+fields(selected.kind()).get("enabledLabel").expression()+" status";
    return AssistantReadSource.selected(select,selected.from(),selected.predicate());
  }

  /** 所有模型过滤与Service取得的授权相交；查询源只在仓储间使用，不流出业务Service。 */
  public MaintenanceAssistantResult queryAssistant(Selection selection, MaintenanceAssistantRequest request, long tenant, long user) {
    var plan=request.plan();
    var domain=AiBusinessCatalog.ALL.get(plan.domain());
    var source=assistantSource(selection);
    var filter=AiBusinessFilters.compile(plan.where(),domain,request.evaluatedAt(),user,tenant);
    if(request.equipmentId()!=null) filter=filter.and(assistant.equipmentFilter(plan,tenant,request.equipmentId()));
    if(request.start()!=null) filter=filter.and(assistant.timeFilter(plan.timeField(),request.start(),request.end()));
    checkNames(plan.where(),domain,source,filter);
    var result=assistant.execute(source,filter,plan,request.page(),request.size());
    long total=((Number)result.get("total")).longValue();
    if(plan.operation().equals("COUNT")) {
      var value=result.get("value");
      return new MaintenanceAssistantResult.Count(value==null?null:new BigDecimal(value.toString()),total);
    }
    return new MaintenanceAssistantResult.Table(rows(result.get("data")),total,((Number)result.get("records")).longValue(),strings(result.get("columns")));
  }

  private void checkNames(JsonNode node,AiBusinessCatalog.Domain domain,AssistantReadSource source,SqlPredicate filter) {
    if(node.isMissingNode()||node.isNull()) return;
    if(node.has("children")) {for(var child:node.path("children")) checkNames(child,domain,source,filter);return;}
    var field=domain.fields().get(node.path("field").asText());
    if(node.path("op").asText().equals("eq")&&field!=null&&!field.identity().isEmpty()
        &&assistant.ambiguousIdentity(source,filter,node.path("field").asText(),field.identity(),node.path("values").get(0).asText()))
      throw new IllegalArgumentException("所选"+field.label()+"存在同名对象，请补充公司、部门或编号限定，不能将同名对象合并");
  }

  // 公共动态执行器的框架行映射只在此边界转换；固定总数和列目录不以任意Map跨Service传递。
  @SuppressWarnings("unchecked")
  private static List<Map<String,Object>> rows(Object value){return (List<Map<String,Object>>) value;}
  @SuppressWarnings("unchecked")
  private static List<String> strings(Object value){return (List<String>)value;}

  private static SqlListFilters.Field f(String sql, SqlListFilters.Kind kind) {
    return new SqlListFilters.Field(sql, kind);
  }

  public static Map<String, SqlListFilters.Field> fields(String kind) {
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    fields.put("code", f("t.code", TEXT));
    fields.put("createdAt", f("t.created_at", DATETIME));
    fields.put("department", f("COALESCE(o.name,'')", TEXT));
    if (kind.equals("tasks")) {
      for (String key : List.of("equipmentName", "equipmentCode", "typeName", "planName"))
        fields.put(key, f("JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$." + key + "'))", TEXT));
      fields.put("status", f("t.status", CHOICE));
      fields.put("dueDate", f("t.due_date", DATE));
      fields.put("expiresDate", f("t.expires_date", DATE));
      fields.put(
          "actualStartAt",
          f(
              "(SELECT COALESCE(e.work_start_at,e.started_at) FROM eam_maintenance_execution e"
                  + " WHERE e.tenant_id=t.tenant_id AND e.task_id=t.id)",
              DATETIME));
      fields.put(
          "actualEndAt",
          f(
              "(SELECT e.work_end_at FROM eam_maintenance_execution e WHERE e.tenant_id=t.tenant_id"
                  + " AND e.task_id=t.id)",
              DATETIME));
      fields.put(
          "owner",
          f(
              "COALESCE(u.display_name,(SELECT GROUP_CONCAT(p.display_name ORDER BY selected.ord"
                  + " SEPARATOR '、') FROM"
                  + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.snapshot,'$.owners'),JSON_ARRAY()), '$[*]'"
                  + " COLUMNS(ord FOR ORDINALITY, uid BIGINT PATH '$')) selected JOIN eam_sys_user"
                  + " p ON p.id=selected.uid AND p.tenant_id=t.tenant_id))",
              TEXT));
    } else {
      fields.put("name", f("t.name", TEXT));
      fields.put("remark", f("JSON_UNQUOTE(JSON_EXTRACT(t.payload,'$.remark'))", TEXT));
      fields.put(
          "enabledLabel",
          f("CASE WHEN JSON_EXTRACT(t.payload,'$.enabled')=TRUE THEN '启用' ELSE '停用' END", CHOICE));
      fields.put("itemCount", f("JSON_LENGTH(t.payload,'$.items')", NUMBER));
      fields.put(
          "equipmentCount",
          f(
              "(SELECT COUNT(DISTINCT s.equipment_id) FROM v_maintenance_schedule s WHERE"
                  + " s.tenant_id=t.tenant_id AND s.plan_id=t.id AND s.enabled=1)",
              NUMBER));
      fields.put("calculation", f("JSON_UNQUOTE(JSON_EXTRACT(t.payload,'$.calculation'))", TEXT));
    }
    return fields;
  }

  public Selection select(
      long tenant,
      long user,
      String kind,
      PermissionScope scope,
      boolean export,
      Set<Long> organizationIds,
      ListQuery query,
      SqlPredicate custom,
      String customOrder) {
    if (!Set.of("plans", "standards", "tasks").contains(kind))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0002");
    boolean task = kind.equals("tasks");
    String from =
        "FROM "
            + (task ? "v_maintenance_task" : "v_maintenance_definition")
            + " t LEFT JOIN eam_org_unit o ON o.id=t.department_id AND o.tenant_id=t.tenant_id"
            + (task
                ? " LEFT JOIN eam_sys_user u ON u.id=t.owner_id AND u.tenant_id=t.tenant_id"
                : "");
    var predicate = SqlPredicate.of("t.tenant_id=?", tenant);
    if (!task) predicate = predicate.and(SqlPredicate.of("t.kind=?", kind));
    var access =
        SqlScopes.scope(
            scope,
            task ? "t.company_id" : null,
            "t.department_id",
            task ? "t.owner_id" : "t.creator_id",
            user);
    var owner =
        task
            ? SqlPredicate.of(
                "EXISTS(SELECT 1 FROM eam_maintenance_task_person p WHERE p.tenant_id=t.tenant_id"
                    + " AND p.task_id=t.id AND p.role_code='owner' AND p.user_id=?)",
                user)
            : SqlPredicate.of("1=0");
    var reviewer =
        task
            ? SqlPredicate.of(
                "EXISTS(SELECT 1 FROM eam_maintenance_task_person p WHERE p.tenant_id=t.tenant_id"
                    + " AND p.task_id=t.id AND p.role_code='reviewer' AND p.user_id=?)",
                user)
            : SqlPredicate.of("1=0");
    // Participant visibility is the existing exception; export always also needs its scope.
    if (task && !export) access = access.or(owner).or(reviewer);
    predicate = predicate.and(access);
    if (organizationIds != null)
      predicate = predicate.and(SqlScopes.in("t.department_id", organizationIds));
    if (task && !Set.of("全部", "all", "").contains(query.queue())) {
      if (query.queue().equals("待我执行"))
        predicate =
            predicate
                .and(owner)
                .and(
                    SqlPredicate.of(
                        "(t.owner_id IS NULL OR t.owner_id=?) AND t.status IN ('待执行','执行中','挂起')",
                        user));
      else if (query.queue().equals("待我验收"))
        predicate = predicate.and(reviewer).and(SqlPredicate.of("t.status='待验收'"));
      else if (Set.of("待执行", "执行中", "挂起", "待验收", "已完成", "已作废", "过期作废").contains(query.queue()))
        predicate = predicate.and(SqlPredicate.of("t.status=?", query.queue()));
      else
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.maintenance.message0003");
    }
    var fields = fields(kind);
    predicate = predicate.and(SqlListFilters.apply(json, query, fields)).and(custom);
    if (!query.keyword().isBlank()) {
      var search = SqlPredicate.of("1=0");
      for (var field : fields.values())
        search = search.or(SqlListFilters.contains(field.expression(), query.keyword()));
      predicate = predicate.and(search);
    }
    String order =
        customOrder == null
            ? SqlListFilters.order(
                query, fields, task ? "t.due_date DESC,t.id DESC" : "t.created_at DESC,t.id DESC")
            : customOrder + " " + query.direction() + ",t.id " + query.direction();
    return new Selection(kind, from, predicate, order);
  }

  public Selection restrict(Selection selection, SqlPredicate extra) {
    return new Selection(
        selection.kind(), selection.from(), selection.predicate().and(extra), selection.order());
  }

  public SqlPredicate acceptance(PermissionScope scope, long user) {
    var reviewer =
        SqlPredicate.of(
            "EXISTS(SELECT 1 FROM eam_maintenance_task_person p WHERE p.tenant_id=t.tenant_id AND"
                + " p.task_id=t.id AND p.role_code='reviewer' AND p.user_id=?)",
            user);
    var permission = SqlScopes.scope(scope, "t.company_id", "t.department_id", "t.owner_id", user);
    if (scope.owner()) permission = permission.or(reviewer);
    return reviewer.and(permission);
  }

  public Set<Long> taskOrganizationIds(Selection selection) {
    var result = new HashSet<Long>();
    for (var row :
        lists.distinct("t.company_id,t.department_id", selection.from(), selection.predicate()))
      for (Object value : row.values()) if (value instanceof Number n) result.add(n.longValue());
    return result;
  }

  /**
   * 在调用方的查看范围内匹配真实附件字段。备注、文件名及其他自由文本不构成授权依据。 标准和执行结果使用关系表；任务资料必须读取生成时的历史快照，不能追到已修改的当前标准。 URL
   * 是原接口实际访问的文件；仅旧记录缺少 URL 时回退到附件 ID，避免伪造另一 ID 扩权。
   */
  public boolean referencesFile(Selection selection, String id) {
    String url = "/api/v1/maintenance/files/" + id;
    var match =
        switch (selection.kind()) {
          case "standards" ->
              SqlPredicate.of(
                  """
                  EXISTS(SELECT 1 FROM eam_maintenance_item_file f
                    WHERE f.tenant_id=t.tenant_id AND f.standard_id=t.id
                      AND (f.url_snapshot=? OR (f.url_snapshot='' AND f.file_id=?)))
                  """,
                  url,
                  id);
          case "tasks" ->
              SqlPredicate.of(
                  """
                  (EXISTS(SELECT 1 FROM eam_maintenance_execution_file f
                    WHERE f.tenant_id=t.tenant_id AND f.task_id=t.id
                      AND (f.url_snapshot=? OR (f.url_snapshot='' AND f.file_id=?)))
                  OR EXISTS(SELECT 1 FROM eam_maintenance_execution_image f
                    WHERE f.tenant_id=t.tenant_id AND f.task_id=t.id
                      AND (f.url_snapshot=? OR (f.url_snapshot='' AND f.file_id=?)))
                  OR EXISTS(SELECT 1 FROM JSON_TABLE(t.snapshot, '$.items[*].references[*]'
                    COLUMNS(file_id VARCHAR(64) PATH '$.id', url VARCHAR(1000) PATH '$.url')) f
                    WHERE f.url=? OR (COALESCE(f.url,'')='' AND f.file_id=?)))
                  """,
                  url,
                  id,
                  url,
                  id,
                  url,
                  id);
          // 计划头没有附件字段；项目资料属于标准或生成任务的快照。
          default -> SqlPredicate.of("1=0");
        };
    return !lists
        .rows(
            "t.id",
            selection.from(),
            selection.predicate().and(match),
            "t.id",
            ListQuery.from(Map.of("size", "1")))
        .isEmpty();
  }

  public long count(Selection selection) {
    return lists.count(selection.from(), selection.predicate());
  }

  public List<Map<String, Object>> page(Selection selection, ListQuery query) {
    return lists.rows(
        "t.*,"
            + String.join(
                ",",
                fields(selection.kind()).entrySet().stream()
                    .filter(
                        e ->
                            Set.of(
                                    "department",
                                    "owner",
                                    "equipmentCount",
                                    "itemCount",
                                    "enabledLabel")
                                .contains(e.getKey()))
                    .map(e -> e.getValue().expression() + " AS `" + e.getKey() + "`")
                    .toList()),
        selection.from(),
        selection.predicate(),
        selection.order(),
        query);
  }
}
