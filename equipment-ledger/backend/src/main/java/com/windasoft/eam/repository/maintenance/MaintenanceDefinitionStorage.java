package com.windasoft.eam.repository.maintenance;

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
public class MaintenanceDefinitionStorage {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public MaintenanceDefinitionStorage(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String key, String column, String type, String fallback) {}

  private static final List<Field> TYPE =
      List.of(
          new Field("period", "period_count", "INT", "1"),
          new Field("unit", "period_unit", "VARCHAR(8)", "月"),
          new Field("symbol", "symbol", "VARCHAR(40)", ""),
          new Field("system", "is_system", "TINYINT", "0"),
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""));
  private static final List<Field> STANDARD =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""));
  private static final List<Field> PLAN =
      List.of(
          new Field("enabled", "enabled", "TINYINT", "1"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("calculation", "calculation_mode", "VARCHAR(24)", "固定间隔"),
          new Field("advanceDays", "advance_days", "INT", "0"),
          new Field("remindDays", "remind_days", "INT", "0"),
          new Field("autoFill", "auto_fill", "VARCHAR(24)", "无"),
          new Field("scanStart", "scan_start", "TINYINT", "0"),
          new Field("reviewMode", "review_mode", "VARCHAR(40)", ""),
          new Field("reviewRule", "review_rule", "VARCHAR(100)", ""));
  private static final List<Field> ITEM =
      List.of(
          new Field("code", "code", "VARCHAR(64)", ""),
          new Field("module", "module_name", "VARCHAR(150)", ""),
          new Field("name", "name", "VARCHAR(200)", ""),
          new Field("content", "content", "TEXT", ""),
          new Field("typeId", "type_id", "BIGINT", null),
          new Field("valueType", "value_type", "VARCHAR(16)", "文本"),
          new Field("lower", "lower_bound", "DECIMAL(30,8)", null),
          new Field("upper", "upper_bound", "DECIMAL(30,8)", null),
          new Field("required", "required", "TINYINT", "0"),
          new Field("photoRequired", "photo_required", "TINYINT", "0"),
          new Field("remark", "remark", "TEXT", ""),
          new Field("spares", "spare_description", "TEXT", ""));
  private static final List<Field> DEVICE =
      List.of(
          new Field("period", "period_count", "INT", "1"),
          new Field("unit", "period_unit", "VARCHAR(8)", "月"),
          new Field("validDays", "valid_days", "INT", "0"),
          new Field("validDuration", "valid_duration", "INT", "0"),
          new Field("validUnit", "valid_unit", "VARCHAR(8)", "日"),
          new Field("remark", "remark", "TEXT", ""));

  private record Target(String table, List<Field> fields) {}

  private Target target(String kind) {
    return switch (kind) {
      case "types" -> new Target("eam_maintenance_type", TYPE);
      case "standards" -> new Target("eam_maintenance_standard", STANDARD);
      case "plans" -> new Target("eam_maintenance_plan", PLAN);
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.maintenance.message0004");
    };
  }

  private Map<String, Object> decode(String value) {
    try {
      return json.readValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.maintenance.message0005"), e);
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
    return v.toString();
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

  public void lockPlans(long tenant, Long id) {
    db.queryForList(
        "SELECT id FROM eam_maintenance_plan WHERE tenant_id=?"
            + (id == null ? "" : " AND id=?")
            + " ORDER BY id FOR UPDATE",
        id == null ? new Object[] {tenant} : new Object[] {tenant, id});
  }

  public void lockDevices(long tenant, Object plan) {
    db.queryForList(
        "SELECT id FROM eam_maintenance_plan_device WHERE tenant_id=? AND plan_id=? ORDER BY id FOR"
            + " UPDATE",
        tenant,
        plan);
  }

  public void lockDevice(long tenant, long plan, long device) {
    db.queryForList(
        "SELECT id FROM eam_maintenance_plan_device WHERE tenant_id=? AND plan_id=? AND id=? FOR"
            + " UPDATE",
        tenant,
        plan,
        device);
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
            "SELECT kind FROM v_maintenance_definition WHERE tenant_id=? AND id=?",
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
            "SELECT kind FROM v_maintenance_definition WHERE tenant_id=? AND id=?",
            String.class,
            tenant,
            id);
    if (kind.equals("plans"))
      db.update(
          "DELETE FROM eam_maintenance_plan_device WHERE tenant_id=? AND plan_id=?", tenant, id);
    return db.update(
        "DELETE FROM " + target(kind).table() + " WHERE tenant_id=? AND id=?", tenant, id);
  }

  private void people(String table, String parent, long tenant, long id, Map<String, Object> data) {
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
    String parent = kind.equals("standards") ? "standard" : "plan";
    people("eam_maintenance_" + parent + "_person", parent + "_id", tenant, id, data);
    if (!kind.equals("standards")) return;
    for (var link : Map.of("equipmentIds", "equipment", "categoryIds", "category").entrySet()) {
      String table = "eam_maintenance_standard_" + link.getValue();
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
        "DELETE FROM eam_maintenance_standard_category_label WHERE tenant_id=? AND standard_id=?",
        tenant,
        id);
    for (var label :
        Map.of("categoryNames", "display", "legacyCategoryNames", "legacy").entrySet()) {
      int at = 0;
      for (Object name : values(data.get(label.getKey())))
        db.update(
            "INSERT INTO eam_maintenance_standard_category_label VALUES(?,?,?,?,?)",
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
            "SELECT item_key FROM eam_maintenance_standard_item WHERE tenant_id=? AND"
                + " standard_id=?",
            tenant,
            id))
      if (!keep.contains(old.get("item_key").toString())) {
        long references =
            db.queryForObject(
                "SELECT COUNT(*) FROM eam_maintenance_device_project r JOIN"
                    + " eam_maintenance_plan_device d ON d.tenant_id=r.tenant_id AND"
                    + " d.id=r.device_id WHERE r.tenant_id=? AND r.standard_id=? AND r.item_key=?"
                    + " AND d.enabled=1",
                Long.class,
                tenant,
                id,
                old.get("item_key"));
        if (references > 0)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.maintenance.message0006");
        db.update(
            "DELETE FROM eam_maintenance_standard_item WHERE tenant_id=? AND standard_id=? AND"
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
          "INSERT INTO eam_maintenance_standard_item ("
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
      for (String table :
          List.of(
              "eam_maintenance_item_option",
              "eam_maintenance_item_file",
              "eam_maintenance_item_spare"))
        db.update(
            "DELETE FROM " + table + " WHERE tenant_id=? AND standard_id=? AND item_key=?",
            tenant,
            id,
            key);
      int at = 0;
      for (Object option : values(item.get("options")))
        db.update(
            "INSERT INTO eam_maintenance_item_option VALUES(?,?,?,?,?,?)",
            tenant,
            id,
            key,
            at++,
            option.toString(),
            values(item.get("normalOptions")).contains(option) ? 1 : 0);
      at = 0;
      for (var file : rows(item.get("references")))
        db.update(
            "INSERT INTO eam_maintenance_item_file VALUES(?,?,?,?,?,?,?,?,?)",
            tenant,
            id,
            key,
            at++,
            file.get("id"),
            file.getOrDefault("name", ""),
            file.getOrDefault("contentType", ""),
            file.getOrDefault("size", 0),
            file.getOrDefault("url", ""));
      at = 0;
      for (var spare : rows(item.get("spareRequirements")))
        db.update(
            "INSERT INTO eam_maintenance_item_spare VALUES(?,?,?,?,?,?)",
            tenant,
            id,
            key,
            at++,
            Long.parseLong(spare.get("stockId").toString()),
            new BigDecimal(spare.get("quantity").toString()));
    }
  }

  public int device(
      boolean insert,
      long tenant,
      long id,
      long plan,
      Object equipment,
      Object type,
      Object next,
      Object anchor,
      Object cycle,
      String content) {
    var data = decode(content);
    var fields = new LinkedHashMap<String, Object>();
    fields.put("type_id", type);
    fields.put(
        "legacy_standard_id",
        data.containsKey("projectRefs")
            ? null
            : Long.parseLong(Objects.toString(data.getOrDefault("standardId", 0))));
    fields.put("next_date", next);
    fields.put("anchor_date", anchor);
    fields.put("cycle_index", cycle);
    fields.put("enabled", 1);
    for (var f : DEVICE) fields.put(f.column(), value(data, f));
    if (insert) {
      fields.put("id", id);
      fields.put("tenant_id", tenant);
      fields.put("plan_id", plan);
      fields.put("equipment_id", equipment);
      db.update(
          "INSERT INTO eam_maintenance_plan_device ("
              + String.join(",", fields.keySet().stream().map(k -> "`" + k + "`").toList())
              + ") VALUES("
              + String.join(",", Collections.nCopies(fields.size(), "?"))
              + ")",
          fields.values().toArray());
    } else {
      var args = new ArrayList<Object>(fields.values());
      args.add(tenant);
      args.add(id);
      db.update(
          "UPDATE eam_maintenance_plan_device SET "
              + String.join(",", fields.keySet().stream().map(k -> "`" + k + "`=?").toList())
              + " WHERE tenant_id=? AND id=?",
          args.toArray());
    }
    people("eam_maintenance_device_person", "device_id", tenant, id, data);
    db.update(
        "DELETE FROM eam_maintenance_device_project WHERE tenant_id=? AND device_id=?", tenant, id);
    int at = 0;
    var projects = rows(data.get("projectRefs"));
    if (!data.containsKey("projectRefs"))
      projects =
          db.queryForList(
              "SELECT CAST(standard_id AS CHAR) standardId,item_key itemId FROM"
                  + " eam_maintenance_standard_item WHERE tenant_id=? AND standard_id=? AND"
                  + " type_id=? ORDER BY sort_no,item_key",
              tenant,
              fields.get("legacy_standard_id"),
              type);
    for (var project : projects)
      db.update(
          "INSERT INTO eam_maintenance_device_project VALUES(?,?,?,?,?)",
          tenant,
          id,
          Long.parseLong(project.get("standardId").toString()),
          Objects.toString(project.get("itemId")),
          at++);
    return 1;
  }
}
