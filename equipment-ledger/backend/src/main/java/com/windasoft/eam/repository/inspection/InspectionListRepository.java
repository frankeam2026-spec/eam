package com.windasoft.eam.repository.inspection;

import static com.windasoft.eam.repository.common.SqlListFilters.Kind.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class InspectionListRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;

  public InspectionListRepository(JdbcListRepository lists, ObjectMapper json) {
    this.lists = lists;
    this.json = json;
  }

  public record Selection(String kind, String from, SqlPredicate predicate, String order) {}

  static String taskPlanDepartment() {
    return "COALESCE(dp.department_id,pp.department_id)";
  }

  static String j(String source, String key) {
    return "JSON_UNQUOTE(JSON_EXTRACT(t." + source + ",'$." + key + "'))";
  }

  static String names(String key) {
    return "(SELECT GROUP_CONCAT(u.display_name ORDER BY selected.ord SEPARATOR '、') FROM"
        + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.snapshot,'$."
        + key
        + "'),JSON_ARRAY()),'$[*]' COLUMNS(ord FOR ORDINALITY,uid BIGINT PATH '$')) selected JOIN"
        + " eam_sys_user u ON u.id=selected.uid AND u.tenant_id=t.tenant_id)";
  }

  static SqlListFilters.Field f(String expression, SqlListFilters.Kind kind) {
    return new SqlListFilters.Field(expression, kind);
  }

  public static Map<String, SqlListFilters.Field> fields(String kind) {
    boolean task = kind.endsWith("tasks");
    String source = task ? "snapshot" : "payload";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    fields.put("code", f("t.code", TEXT));
    fields.put("createdAt", f("t.created_at", DATETIME));
    fields.put("departmentName", f("COALESCE(o.name,'')", TEXT));
    if (task) {
      for (String key : List.of("planName", "equipmentName", "typeName", "shiftName", "reviewMode"))
        fields.put(key, f(j(source, key), TEXT));
      fields.put("shiftDate", f("CAST(" + j(source, "shiftDate") + " AS DATE)", DATE));
      fields.put("status", f("t.status", CHOICE));
      fields.put("dueAt", f("t.due_at", DATETIME));
      fields.put("expiresAt", f("t.expires_at", DATETIME));
      fields.put("ownerNames", f(names("owners"), TEXT));
      fields.put("reviewerNames", f(names("reviewers"), TEXT));
      fields.put("ownerName", f("COALESCE(u.display_name,'')", TEXT));
      fields.put("reviewer", f(j("result", "review.reviewer"), TEXT));
      fields.put(
          "reviewedAt",
          f(
              "CAST(REPLACE(" + j("result", "review.reviewedAt") + ",'T',' ') AS DATETIME)",
              DATETIME));
      for (String key : List.of("startedAt", "finishedAt"))
        fields.put(key, f("CAST(REPLACE(" + j("result", key) + ",'T',' ') AS DATETIME)", DATETIME));
      fields.put("arrangementName", f("t.arrangement_name", TEXT));
      fields.put("businessDate", f("t.business_date", DATE));
      fields.put("equipmentCount", f("COALESCE(JSON_LENGTH(t.snapshot,'$.equipment'),0)", NUMBER));
      fields.put("expected", f("COALESCE(JSON_LENGTH(t.snapshot,'$.items'),0)", NUMBER));
      String checked =
          "(SELECT COUNT(*) FROM"
              + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.result,'$.items'),JSON_ARRAY()),'$[*]'"
              + " COLUMNS(answer JSON PATH '$')) a WHERE"
              + " COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.skipped')),'false')<>'true' AND"
              + " (COALESCE(TRIM(JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.value'))),'')<>'' OR"
              + " JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.implicitNormal'))='true'))";
      fields.put("checked", f(checked, NUMBER));
      fields.put(
          "abnormal",
          f(
              "(SELECT COUNT(*) FROM"
                  + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.result,'$.items'),JSON_ARRAY()),'$[*]'"
                  + " COLUMNS(answer JSON PATH '$')) a WHERE"
                  + " JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.abnormal'))='true' AND"
                  + " COALESCE(JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.skipped')),'false')<>'true')",
              NUMBER));
      fields.put("skipped", f("COALESCE(JSON_LENGTH(t.result,'$.skippedEquipmentIds'),0)", NUMBER));
      fields.put(
          "missing",
          f(
              "(SELECT COUNT(*) FROM"
                  + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.snapshot,'$.items'),JSON_ARRAY()),'$[*]'"
                  + " COLUMNS(item JSON PATH '$')) i WHERE NOT EXISTS(SELECT 1 FROM"
                  + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.result,'$.skippedEquipmentIds'),JSON_ARRAY()),'$[*]'"
                  + " COLUMNS(eid VARCHAR(30) PATH '$')) skipped WHERE"
                  + " skipped.eid=JSON_UNQUOTE(JSON_EXTRACT(i.item,'$.equipmentId'))) AND NOT"
                  + " EXISTS(SELECT 1 FROM"
                  + " JSON_TABLE(COALESCE(JSON_EXTRACT(t.result,'$.items'),JSON_ARRAY()),'$[*]'"
                  + " COLUMNS(answer JSON PATH '$')) a WHERE"
                  + " JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.id'))=JSON_UNQUOTE(JSON_EXTRACT(i.item,'$.id'))"
                  + " AND (COALESCE(TRIM(JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.value'))),'')<>'' OR"
                  + " JSON_UNQUOTE(JSON_EXTRACT(a.answer,'$.implicitNormal'))='true')))",
              NUMBER));
      String now = "CONVERT_TZ(UTC_TIMESTAMP(),'+00:00','+08:00')",
          finish = "COALESCE(NULLIF(" + j("result", "finishedAt") + ",'')," + now + ")";
      fields.put(
          "delayDays",
          f("GREATEST(0,CEIL(TIMESTAMPDIFF(SECOND,t.expires_at," + finish + ")/86400))", NUMBER));
      fields.put(
          "workSeconds",
          f(
              "CASE WHEN COALESCE("
                  + j("result", "startedAt")
                  + ",'')='' THEN 0 ELSE GREATEST(0,TIMESTAMPDIFF(SECOND,REPLACE("
                  + j("result", "startedAt")
                  + ",'T',' '),REPLACE("
                  + finish
                  + ",'T',' '))) END",
              NUMBER));
    } else {
      fields.put("name", f("t.name", TEXT));
      fields.put(
          "enabled",
          f("CASE WHEN JSON_EXTRACT(t.payload,'$.enabled')=TRUE THEN '启用' ELSE '停用' END", CHOICE));
      for (String key : List.of("typeName", "personMode", "remark"))
        fields.put(key, f(j(source, key), TEXT));
      for (String key : List.of("startDate", "endDate"))
        fields.put(key, kind.equals("periodic-plans")
            ? f("CAST(" + j(source, key) + " AS DATETIME)", DATETIME)
            : f("CAST(" + j(source, key) + " AS DATE)", DATE));
      fields.put("itemCount", f("COALESCE(JSON_LENGTH(t.payload,'$.items'),0)", NUMBER));
      fields.put("equipmentCount", f("COALESCE(JSON_LENGTH(t.payload,'$.details'),0)", NUMBER));
      if (kind.equals("daily-plans"))
        fields.put(
            "arrangementCount",
            f(
                "(SELECT COUNT(*) FROM eam_inspection_daily_arrangement a WHERE"
                    + " a.tenant_id=t.tenant_id AND a.plan_id=t.id)",
                NUMBER));
      fields.put("shiftCount", f("COALESCE(JSON_LENGTH(t.payload,'$.shifts'),0)", NUMBER));
      fields.put(
          "periodLabel",
          f("CONCAT(" + j(source, "period") + ",' '," + j(source, "unit") + ")", TEXT));
      fields.put(
          "nextAt",
          f(
              "(SELECT s.next_at FROM eam_inspection_schedule s WHERE s.tenant_id=t.tenant_id AND"
                  + " s.plan_id=t.id LIMIT 1)",
              DATETIME));
    }
    return fields;
  }

  public Selection select(
      long tenant,
      long user,
      String kind,
      PermissionScope scope,
      PermissionScope reviewScope,
      boolean export,
      Set<Long> organizationIds,
      ListQuery query,
      SqlPredicate custom,
      String customOrder) {
    if (!Set.of("standards", "daily-plans", "periodic-plans", "daily-tasks", "periodic-tasks")
        .contains(kind))
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0006");
    boolean task = kind.endsWith("tasks");
    String taskKind = kind.startsWith("daily") ? "daily" : "periodic";
    String from =
        "FROM "
            + (task ? "v_inspection_task" : "v_inspection_definition")
            + " t LEFT JOIN eam_org_unit o ON o.id=t.department_id AND o.tenant_id=t.tenant_id"
            + (task
                ? " LEFT JOIN eam_sys_user u ON u.id=t.owner_id AND u.tenant_id=t.tenant_id"
                    + " LEFT JOIN eam_inspection_daily_plan dp ON t.kind='daily' AND dp.tenant_id=t.tenant_id AND dp.id=t.plan_id"
                    + " LEFT JOIN eam_inspection_periodic_plan pp ON t.kind='periodic' AND pp.tenant_id=t.tenant_id AND pp.id=t.plan_id"
                : "");
    var predicate = SqlPredicate.of("t.tenant_id=? AND t.kind=?", tenant, task ? taskKind : kind);
    if (task) predicate = predicate.and(SqlPredicate.of("t.deleted=0"));
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
                "EXISTS(SELECT 1 FROM eam_inspection_task_person p WHERE p.tenant_id=t.tenant_id"
                    + " AND p.task_id=t.id AND p.role_code='owner' AND p.user_id=?)",
                user)
            : SqlPredicate.of("1=0");
    var reviewers =
        task
            ? SqlPredicate.of(
                "EXISTS(SELECT 1 FROM eam_inspection_task_person p WHERE p.tenant_id=t.tenant_id"
                    + " AND p.task_id=t.id AND p.role_code='reviewer' AND p.user_id=?)",
                user)
            : SqlPredicate.of("1=0");
    var anyone =
        task
            ? SqlPredicate.of("t.person_mode='任何人可点检'")
                .or(
                    SqlPredicate.of("t.person_mode='现场直接点检'")
                        .and(
                            SqlScopes.scope(
                                scope, "t.company_id", "t.department_id", "t.owner_id", user)))
            : SqlPredicate.of("1=0");
    if (task && !export)
      access =
          access
              .or(owner)
              .or(reviewers)
              .or(anyone)
              .or(SqlPredicate.of("t.owner_id=? OR t.follow_up_user_id=?", user, user));
    predicate = predicate.and(access);
    if (organizationIds != null)
      predicate = predicate.and(SqlScopes.in(task ? taskPlanDepartment() : "t.department_id", organizationIds));
    if (task && !Set.of("全部", "all", "").contains(query.queue())) {
      if (query.queue().equals("我的待执行"))
        predicate =
            predicate
                .and(owner.or(anyone))
                .and(
                    SqlPredicate.of(
                        "(t.owner_id IS NULL OR t.owner_id=?) AND t.status IN"
                            + " ('未开始','待执行','执行中','已过期','审核不通过')",
                        user));
      else if (query.queue().equals("我的待审核"))
        predicate =
            predicate
                .and(
                    reviewers.or(
                        SqlScopes.scope(
                            reviewScope, "t.company_id", "t.department_id", "t.owner_id", user)))
                .and(SqlPredicate.of("t.status='待审核'"));
      else predicate = predicate.and(SqlPredicate.of("t.status=?", query.queue()));
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
                query, fields, task ? "t.due_at DESC,t.id DESC" : "t.created_at DESC,t.id DESC")
            : customOrder + " " + query.direction() + ",t.id " + query.direction();
    return new Selection(kind, from, predicate, order);
  }

  public Selection restrict(Selection selection, SqlPredicate extra) {
    return new Selection(
        selection.kind(), selection.from(), selection.predicate().and(extra), selection.order());
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
    var extras = new ArrayList<String>();
    for (var entry : fields(selection.kind()).entrySet())
      if (!Set.of("code", "createdAt", "status").contains(entry.getKey()))
        extras.add(entry.getValue().expression() + " AS `query_" + entry.getKey() + "`");
    return lists.rows(
        "t.*," + String.join(",", extras),
        selection.from(),
        selection.predicate(),
        selection.order(),
        query);
  }

  public Set<Long> taskOrganizationIds(Selection selection) {
    var ids = new HashSet<Long>();
    for (var row :
        lists.distinct(taskPlanDepartment() + " AS plan_department_id", selection.from(), selection.predicate()))
      for (Object value : row.values()) if (value instanceof Number n) ids.add(n.longValue());
    return ids;
  }

  public SqlPredicate deviceMatch(String equipmentId, String code) {
    var device =
        equipmentId.isBlank()
            ? SqlPredicate.of(
                "EXISTS(SELECT 1 FROM eam_equipment de WHERE de.tenant_id=t.tenant_id AND"
                    + " de.id=t.equipment_id AND de.deleted=0 AND de.equipment_code=?)",
                code)
            : SqlPredicate.of("t.equipment_id=?", equipmentId);
    return device.and(
        SqlPredicate.of(
            "t.due_at<=NOW() AND t.status NOT IN ('已作废','过期作废','已免检') AND (t.expires_at>=NOW() OR"
                + " t.status IN ('待执行','执行中','挂起','已过期','审核不通过'))"));
  }
}
