package com.windasoft.eam.repository.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.ids;
import static com.windasoft.eam.common.inspection.InspectionSupport.text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Concrete relational writes. JSON input is an API compatibility transport, never a stored payload.
 */
@Repository
public class InspectionDefinitionStorage {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public InspectionDefinitionStorage(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String key, String column, String type, String fallback) {}

  private static final List<Field> TYPES =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("period", "period_count", "INT", "1"),
          new Field("unit", "period_unit", "VARCHAR(8)", "日"),
          new Field("sortOrder", "sort_order", "INT", "0"));
  private static final List<Field> STANDARDS =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("requirement", "requirement", "TEXT", ""));
  private static final List<Field> DAILY_PLANS =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("typeId", "type_id", "BIGINT", null),
          new Field("reviewMode", "review_mode", "VARCHAR(24)", "无需验收"),
          new Field("startDate", "start_date", "DATE", null),
          new Field("endDate", "end_date", "DATE", null),
          new Field("validHours", "valid_duration", "DECIMAL(18,2)", "1"),
          new Field("advanceHours", "advance_duration", "DECIMAL(18,2)", "0"),
          new Field("remindHours", "remind_duration", "DECIMAL(18,2)", "0"),
          new Field("expireRemindHours", "expire_remind_duration", "DECIMAL(18,2)", "0"),
          new Field("validUnit", "valid_unit", "VARCHAR(8)", "时"),
          new Field("advanceUnit", "advance_unit", "VARCHAR(8)", "时"),
          new Field("remindUnit", "remind_unit", "VARCHAR(8)", "时"),
          new Field("expireRemindUnit", "expire_remind_unit", "VARCHAR(8)", "时"),
          new Field("scanMode", "scan_mode", "VARCHAR(40)", "无需扫码"),
          new Field("onlyAbnormal", "only_abnormal", "TINYINT", "0"),
          new Field("randomPhotoCount", "random_photo_count", "INT", "0"),
          new Field("allowLate", "allow_late", "TINYINT", "0"),
          new Field("personMode", "person_mode", "VARCHAR(40)", "指定负责人"),
          new Field("skipUnstaffed", "skip_unstaffed", "TINYINT", "0"),
          new Field("restCycle", "rest_cycle", "VARCHAR(8)", "按周"),
          new Field("useShiftEnd", "use_shift_end", "TINYINT", "0"));
  private static final List<Field> PERIODIC_PLANS =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("typeId", "type_id", "BIGINT", null),
          new Field("reviewMode", "review_mode", "VARCHAR(24)", "无需验收"),
          new Field("startDate", "start_date", "DATE", null),
          new Field("endDate", "end_date", "DATE", null),
          new Field("validHours", "valid_duration", "DECIMAL(18,2)", "1"),
          new Field("advanceHours", "advance_duration", "DECIMAL(18,2)", "0"),
          new Field("remindHours", "remind_duration", "DECIMAL(18,2)", "0"),
          new Field("expireRemindHours", "expire_remind_duration", "DECIMAL(18,2)", "0"),
          new Field("validUnit", "valid_unit", "VARCHAR(8)", "时"),
          new Field("advanceUnit", "advance_unit", "VARCHAR(8)", "时"),
          new Field("remindUnit", "remind_unit", "VARCHAR(8)", "时"),
          new Field("expireRemindUnit", "expire_remind_unit", "VARCHAR(8)", "时"),
          new Field("scanMode", "scan_mode", "VARCHAR(40)", "无需扫码"),
          new Field("onlyAbnormal", "only_abnormal", "TINYINT", "0"),
          new Field("randomPhotoCount", "random_photo_count", "INT", "0"),
          new Field("allowLate", "allow_late", "TINYINT", "0"),
          new Field("period", "period_count", "INT", "1"),
          new Field("unit", "period_unit", "VARCHAR(8)", "日"),
          new Field("nextAt", "next_at", "DATETIME", null));
  private static final List<Field> ITEM =
      List.of(
          new Field("code", "code", "VARCHAR(64)", ""),
          new Field("name", "name", "VARCHAR(200)", ""),
          new Field("content", "content", "TEXT", ""),
          new Field("typeId", "type_id", "BIGINT", null),
          new Field("valueType", "value_type", "VARCHAR(16)", "文本"),
          new Field("lower", "lower_bound", "DECIMAL(30,8)", null),
          new Field("upper", "upper_bound", "DECIMAL(30,8)", null),
          new Field("required", "required", "TINYINT", "0"),
          new Field("photoRequired", "photo_required", "TINYINT", "0"),
          new Field("remark", "remark", "TEXT", ""));

  private record Target(String table, List<Field> fields) {}

  private Target target(String kind) {
    return switch (kind) {
      case "types" -> new Target("eam_inspection_type", TYPES);
      case "standards" -> new Target("eam_inspection_standard", STANDARDS);
      case "daily-plans" -> new Target("eam_inspection_daily_plan", DAILY_PLANS);
      case "periodic-plans" -> new Target("eam_inspection_periodic_plan", PERIODIC_PLANS);
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.inspection.message0001");
    };
  }

  private Map<String, Object> decode(String value) {
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inspection.message0002"), e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private List<?> values(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private Object value(Map<String, Object> data, Field f) {
    Object v = data.get(f.key());
    if (v == null || v.toString().isBlank()) v = f.fallback();
    if (v == null) return null;
    if (f.type().equals("TINYINT"))
      return v.toString().equals("true") || v.toString().equals("1") ? 1 : 0;
    if (f.type().startsWith("DECIMAL")) return new BigDecimal(v.toString());
    if (f.type().equals("BIGINT") || f.type().equals("INT")) return Long.parseLong(v.toString());
    return f.type().equals("DATETIME") ? v.toString().replace('T', ' ') : v.toString();
  }

  private void scalarWrite(
      String table,
      Map<String, Object> header,
      List<Field> fields,
      Map<String, Object> data,
      boolean insert) {
    var columns = new LinkedHashMap<String, Object>(header);
    for (var f : fields) columns.put(f.column(), value(data, f));
    if (insert) {
      db.update(
          "INSERT INTO "
              + table
              + " ("
              + String.join(",", columns.keySet().stream().map(k -> "`" + k + "`").toList())
              + ") VALUES("
              + String.join(",", Collections.nCopies(columns.size(), "?"))
              + ")",
          columns.values().toArray());
    } else {
      Object tenant = columns.remove("tenant_id"), id = columns.remove("id");
      var args = new ArrayList<>(columns.values());
      args.add(tenant);
      args.add(id);
      db.update(
          "UPDATE "
              + table
              + " SET "
              + String.join(",", columns.keySet().stream().map(k -> "`" + k + "`=?").toList())
              + ",row_version=row_version+1,updated_at=CURRENT_TIMESTAMP WHERE tenant_id=? AND"
              + " id=?",
          args.toArray());
    }
  }

  public void lock(long tenant, String kind, long id) {
    db.queryForList(
        "SELECT id FROM " + target(kind).table() + " WHERE tenant_id=? AND id=? FOR UPDATE",
        tenant,
        id);
  }

  public void lockPlan(long tenant, Object id) {
    for (String table : List.of("eam_inspection_daily_plan", "eam_inspection_periodic_plan"))
      db.queryForList(
          "SELECT id FROM " + table + " WHERE tenant_id=? AND id=? FOR UPDATE", tenant, id);
  }

  public void setPlanType(long tenant, long id, Long type) {
    for (String table : List.of("eam_inspection_daily_plan", "eam_inspection_periodic_plan"))
      db.update("UPDATE " + table + " SET type_id=? WHERE tenant_id=? AND id=?", type, tenant, id);
  }

  public int insert(
      long id,
      long tenant,
      String kind,
      Object code,
      Object name,
      Object department,
      long creator,
      String content) {
    var data = decode(content);
    var header = new LinkedHashMap<String, Object>();
    header.put("id", id);
    header.put("tenant_id", tenant);
    header.put("code", code);
    header.put("name", name);
    header.put("department_id", department);
    header.put("creator_id", creator);
    var t = target(kind);
    scalarWrite(t.table(), header, t.fields(), data, true);
    children(tenant, id, kind, data);
    return 1;
  }

  public int update(
      long tenant, long id, Object code, Object name, Object department, String content) {
    String kind =
        db.queryForObject(
            "SELECT kind FROM v_inspection_definition WHERE tenant_id=? AND id=?",
            String.class,
            tenant,
            id);
    var data = decode(content);
    var header = new LinkedHashMap<String, Object>();
    header.put("id", id);
    header.put("tenant_id", tenant);
    header.put("code", code);
    header.put("name", name);
    header.put("department_id", department);
    var t = target(kind);
    scalarWrite(t.table(), header, t.fields(), data, false);
    children(tenant, id, kind, data);
    return 1;
  }

  public int delete(long tenant, long id) {
    String kind =
        db.queryForObject(
            "SELECT kind FROM v_inspection_definition WHERE tenant_id=? AND id=?",
            String.class,
            tenant,
            id);

    return db.update(
        "DELETE FROM " + target(kind).table() + " WHERE tenant_id=? AND id=?", tenant, id);
  }

  private void people(
      String table, String parent, long tenant, Object id, Map<String, Object> data) {
    db.update("DELETE FROM " + table + " WHERE tenant_id=? AND " + parent + "=?", tenant, id);
    for (var role : Map.of("owners", "owner", "reviewers", "reviewer").entrySet()) {
      int at = 0;
      for (Object user : new LinkedHashSet<>(values(data.get(role.getKey()))))
        db.update(
            "INSERT INTO " + table + " VALUES(?,?,?,?,?)",
            tenant,
            id,
            role.getValue(),
            Long.parseLong(user.toString()),
            at++);
    }
  }

  private void children(long tenant, long id, String kind, Map<String, Object> data) {
    if (kind.equals("types")) return;
    String parent =
        switch (kind) {
          case "standards" -> "standard";
          case "daily-plans" -> "daily_plan";
          case "periodic-plans" -> "periodic_plan";
          default ->
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.inspection.message0001");
        };
    people("eam_inspection_" + parent + "_person", "parent_id", tenant, id, data);
    if (kind.equals("standards")) {
      standard(tenant, id, data);
      return;
    }
    String device = "eam_inspection_" + parent + "_device";
    db.update("DELETE FROM " + device + " WHERE tenant_id=? AND plan_id=?", tenant, id);
    int order = 0;
    for (var detail : rows(data.get("details"))) {
      String key = Objects.toString(detail.getOrDefault("key", ++order));
      db.update(
          "INSERT INTO " + device + " VALUES(?,?,?,?,?)",
          tenant,
          id,
          key,
          Long.parseLong(detail.get("equipmentId").toString()),
          order++);
      for (var role : Map.of("owners", "owner", "reviewers", "reviewer").entrySet()) {
        int at = 0;
        for (Object user : new LinkedHashSet<>(values(detail.get(role.getKey()))))
          db.update(
              "INSERT INTO " + device + "_person VALUES(?,?,?,?,?,?)",
              tenant,
              id,
              key,
              role.getValue(),
              Long.parseLong(user.toString()),
              at++);
      }
      int at = 0;
      for (var ref : rows(detail.get("projectRefs")))
        db.update(
            "INSERT INTO " + device + "_project VALUES(?,?,?,?,?,?)",
            tenant,
            id,
            key,
            Long.parseLong(ref.get("standardId").toString()),
            ref.get("itemId").toString(),
            at++);
    }
    if (kind.equals("daily-plans")) calendar(tenant, id, data);
  }

  private void standard(long tenant, long id, Map<String, Object> data) {
    for (var link : Map.of("equipmentIds", "equipment", "categoryIds", "category").entrySet()) {
      String table = "eam_inspection_standard_" + link.getValue();
      db.update("DELETE FROM " + table + " WHERE tenant_id=? AND standard_id=?", tenant, id);
      int at = 0;
      for (Object target : new LinkedHashSet<>(values(data.get(link.getKey()))))
        db.update(
            "INSERT INTO " + table + " VALUES(?,?,?,?)",
            tenant,
            id,
            Long.parseLong(target.toString()),
            at++);
    }
    db.update(
        "DELETE FROM eam_inspection_category_label WHERE tenant_id=? AND standard_id=?",
        tenant,
        id);
    for (var label :
        Map.of("categoryNames", "display", "legacyCategoryNames", "legacy").entrySet()) {
      int at = 0;
      for (Object name : values(data.get(label.getKey())))
        db.update(
            "INSERT INTO eam_inspection_category_label VALUES(?,?,?,?,?)",
            tenant,
            id,
            label.getValue(),
            at++,
            name.toString());
    }
    var items = rows(data.get("items"));
    var keep = items.stream().map(i -> Objects.toString(i.get("id"))).toList();
    for (var old :
        db.queryForList(
            "SELECT item_key FROM eam_inspection_standard_item WHERE tenant_id=? AND standard_id=?",
            tenant,
            id))
      if (!keep.contains(old.get("item_key").toString())) {
        for (String parent : List.of("daily_plan", "periodic_plan")) {
          long count =
              db.queryForObject(
                  "SELECT COUNT(*) FROM eam_inspection_"
                      + parent
                      + "_device_project WHERE tenant_id=? AND standard_id=? AND item_key=?",
                  Long.class,
                  tenant,
                  id,
                  old.get("item_key"));
          if (count > 0)
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.inspection.message0003");
        }
        db.update(
            "DELETE FROM eam_inspection_standard_item WHERE tenant_id=? AND standard_id=? AND"
                + " item_key=?",
            tenant,
            id,
            old.get("item_key"));
      }
    int order = 0;
    for (var item : items) {
      String key = Objects.toString(item.get("id"));
      var fields = new LinkedHashMap<String, Object>();
      fields.put("tenant_id", tenant);
      fields.put("standard_id", id);
      fields.put("item_key", key);
      fields.put("sort_no", order++);
      for (var f : ITEM) fields.put(f.column(), value(item, f));
      db.update(
          "INSERT INTO eam_inspection_standard_item ("
              + String.join(",", fields.keySet().stream().map(k -> "`" + k + "`").toList())
              + ") VALUES("
              + String.join(",", Collections.nCopies(fields.size(), "?"))
              + ") ON DUPLICATE KEY UPDATE "
              + String.join(
                  ",",
                  fields.keySet().stream()
                      .filter(k -> !Set.of("tenant_id", "standard_id", "item_key").contains(k))
                      .map(k -> "`" + k + "`=VALUES(`" + k + "`)")
                      .toList()),
          fields.values().toArray());
      for (String table : List.of("eam_inspection_item_option", "eam_inspection_item_file"))
        db.update(
            "DELETE FROM " + table + " WHERE tenant_id=? AND standard_id=? AND item_key=?",
            tenant,
            id,
            key);
      int at = 0;
      for (Object option : values(item.get("options")))
        db.update(
            "INSERT INTO eam_inspection_item_option VALUES(?,?,?,?,?,?)",
            tenant,
            id,
            key,
            at++,
            option.toString(),
            values(item.get("normalOptions")).contains(option) ? 1 : 0);
      at = 0;
      for (var file : rows(item.get("references")))
        db.update(
            "INSERT INTO eam_inspection_item_file VALUES(?,?,?,?,?,?,?,?,?)",
            tenant,
            id,
            key,
            at++,
            file.get("id"),
            file.getOrDefault("name", ""),
            file.getOrDefault("contentType", ""),
            file.getOrDefault("size", 0),
            file.getOrDefault("url", ""));
    }
  }

  private void calendar(long tenant, long id, Map<String, Object> data) {
    for (String suffix : List.of("rest_day", "holiday", "shift", "roster"))
      db.update(
          "DELETE FROM eam_inspection_daily_" + suffix + " WHERE tenant_id=? AND plan_id=?",
          tenant,
          id);
    int at = 0;
    for (String cycle : List.of("weekly", "monthly")) {
      String field = cycle.equals("weekly") ? "weeklyRestDays" : "monthlyRestDays";
      Object days = data.get(field);
      if (!data.containsKey("weeklyRestDays") && !data.containsKey("monthlyRestDays"))
        days = text(data, "restCycle").equals("按月") == cycle.equals("monthly")
            ? data.get("restDays") : List.of();
      for (long day : new LinkedHashSet<>(ids(days)))
        db.update("INSERT INTO eam_inspection_daily_rest_day(tenant_id,plan_id,sort_no,day_no,cycle_code) VALUES(?,?,?,?,?)",
            tenant, id, at++, day, cycle);
    }
    at = 0;
    for (Object day : values(data.get("holidays")))
      db.update(
          "INSERT INTO eam_inspection_daily_holiday VALUES(?,?,?,?)",
          tenant,
          id,
          at++,
          day.toString());
    at = 0;
    for (var shift : rows(data.get("shifts")))
      db.update(
          "INSERT INTO eam_inspection_daily_shift VALUES(?,?,?,?,?,?,?)",
          tenant,
          id,
          at++,
          shift.get("id"),
          shift.get("name"),
          shift.get("start"),
          shift.get("end"));
    at = 0;
    for (var roster : rows(data.get("roster"))) {
      int position = at++;
      db.update(
          "INSERT INTO eam_inspection_daily_roster VALUES(?,?,?,?,?,?)",
          tenant,
          id,
          position,
          Objects.toString(roster.getOrDefault("key", position)),
          roster.get("date"),
          roster.get("shiftId"));
      int userOrder = 0;
      for (Object user : new LinkedHashSet<>(values(roster.get("users"))))
        db.update(
            "INSERT INTO eam_inspection_daily_roster_user VALUES(?,?,?,?,?)",
            tenant,
            id,
            position,
            Long.parseLong(user.toString()),
            userOrder++);
    }
  }
}
