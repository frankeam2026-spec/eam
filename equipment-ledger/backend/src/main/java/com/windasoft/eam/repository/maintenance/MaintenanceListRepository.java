package com.windasoft.eam.repository.maintenance;

import static com.windasoft.eam.repository.common.SqlListFilters.Kind.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceListRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;

  public MaintenanceListRepository(JdbcListRepository lists, ObjectMapper json) {
    this.lists = lists;
    this.json = json;
  }

  public record Selection(String kind, String from, SqlPredicate predicate, String order) {}

  public AssistantReadSource assistantSource(Selection selected){
    String select="CAST(t.id AS CHAR) id,t.code,COALESCE(o.name,'') department,CAST(t.department_id AS CHAR) departmentId,t.created_at createdAt";
    if(selected.kind().equals("tasks")){
      for(String key:List.of("equipmentName","equipmentCode","planName","owner","dueDate","expiresDate","actualStartAt","actualEndAt"))select+=","+fields("tasks").get(key).expression()+" `"+key+"`";
      select+=",t.status,CAST(t.plan_id AS CHAR) planId,CAST(t.equipment_id AS CHAR) equipmentId,CAST(t.owner_id AS CHAR) ownerId,CAST(t.company_id AS CHAR) companyId,(SELECT c.name FROM eam_org_unit c WHERE c.tenant_id=t.tenant_id AND c.id=t.company_id) company";
    }else select+=",t.name,"+fields(selected.kind()).get("enabledLabel").expression()+" status";
    return AssistantReadSource.selected(select,selected.from(),selected.predicate());
  }

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

  public boolean referencesFile(Selection selection, String id) {
    boolean task = selection.kind().endsWith("tasks");
    String source = task ? "snapshot" : "payload";
    var match =
        com.windasoft.eam.repository.common.SqlListFilters.contains(
            "CAST(t." + source + " AS CHAR)", id);
    if (task)
      match =
          match.or(
              com.windasoft.eam.repository.common.SqlListFilters.contains(
                  "CAST(t.result AS CHAR)", id));
    return !lists
        .rows(
            "t.id",
            selection.from(),
            selection.predicate().and(match),
            "t.id",
            com.windasoft.eam.dto.common.ListQuery.from(Map.of("size", "1")))
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
