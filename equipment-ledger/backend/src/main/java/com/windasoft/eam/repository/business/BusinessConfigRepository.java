package com.windasoft.eam.repository.business;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessConfigRepository {
  private final JdbcTemplate db;

  public BusinessConfigRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<String> matchIds(String sql, List<Object> bindings) {
    return db.query(sql, (row, index) -> row.getString(1), bindings.toArray());
  }

  public void removeValues(long tenant, String object, long record) {
    db.update(
        "DELETE FROM eam_business_value WHERE tenant_id=? AND object_key=? AND record_id=?",
        tenant,
        object,
        record);
  }

  public Map<String, Object> schema(long tenant, String object, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM eam_business_schema WHERE tenant_id=? AND object_key=?"
                + (lock ? " FOR UPDATE" : ""),
            tenant,
            object);
    if (rows.isEmpty())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0001");
    return rows.getFirst();
  }

  public void draft(long tenant, String object, String value, int version, long actor) {
    if (db.update(
            "UPDATE eam_business_schema SET draft=CAST(? AS"
                + " JSON),row_version=row_version+1,updated_by=?,updated_at=NOW() WHERE tenant_id=?"
                + " AND object_key=? AND row_version=?",
            value,
            actor,
            tenant,
            object,
            version)
        != 1)
      throw new org.springframework.web.server.ResponseStatusException(
          org.springframework.http.HttpStatus.CONFLICT, "配置已更新，请刷新后重试");
  }

  public void publish(long tenant, String object, String value, int version, long actor) {
    db.update(
        "INSERT INTO"
            + " eam_business_schema_version(tenant_id,object_key,version,configuration,actor_id)"
            + " VALUES(?,?,?,CAST(? AS JSON),?)",
        tenant,
        object,
        version,
        value,
        actor);
    db.update(
        "UPDATE eam_business_schema SET published=CAST(? AS"
            + " JSON),published_version=?,row_version=row_version+1,updated_by=?,updated_at=NOW()"
            + " WHERE tenant_id=? AND object_key=?",
        value,
        version,
        actor,
        tenant,
        object);
    db.update(
        "UPDATE eam_business_field SET ever_published=1 WHERE tenant_id=? AND object_key=?",
        tenant,
        object);
  }

  public List<Map<String, Object>> versions(long tenant, String object) {
    return db.queryForList(
        "SELECT version,actor_id,created_at FROM eam_business_schema_version WHERE tenant_id=? AND"
            + " object_key=? ORDER BY version DESC",
        tenant,
        object);
  }

  public String version(long tenant, String object, int version) {
    return db.queryForObject(
        "SELECT configuration FROM eam_business_schema_version WHERE tenant_id=? AND object_key=?"
            + " AND version=?",
        String.class,
        tenant,
        object,
        version);
  }

  public Map<String, Object> identity(String id) {
    var rows = db.queryForList("SELECT * FROM eam_business_field WHERE id=? FOR UPDATE", id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public void identity(String id, long tenant, String object, String type, String source) {
    db.update(
        "INSERT INTO eam_business_field(id,tenant_id,object_key,data_type,source_key)"
            + " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE"
            + " data_type=VALUES(data_type),source_key=VALUES(source_key)",
        id,
        tenant,
        object,
        type,
        source);
  }

  public List<Map<String, Object>> dictionary(long tenant, String id, boolean lock) {
    return db.queryForList(
        "SELECT id,name,enabled,row_version rowVersion FROM eam_business_dictionary WHERE"
            + " tenant_id=? AND id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenant,
        id);
  }

  public List<Map<String, Object>> options(long tenant, String id) {
    return db.queryForList(
        "SELECT id,name,sort_no sortOrder,enabled,row_version rowVersion FROM eam_business_option"
            + " WHERE tenant_id=? AND dictionary_id=? ORDER BY sort_no,id",
        tenant,
        id);
  }

  public void event(
      long id,
      long tenant,
      String object,
      Long record,
      long actor,
      String name,
      String action,
      String details) {
    db.update(
        "INSERT INTO"
            + " eam_business_config_event(id,tenant_id,object_key,record_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,?,CAST(? AS JSON))",
        id,
        tenant,
        object,
        record,
        actor,
        name,
        action,
        details);
  }

  public List<Map<String, Object>> events(long tenant, String object, Long record) {
    return db.queryForList(
        "SELECT actor_name actor,action,details,created_at time FROM eam_business_config_event"
            + " WHERE tenant_id=? AND object_key=? AND record_id <=> ? ORDER BY created_at DESC,id"
            + " DESC LIMIT 100",
        tenant,
        object,
        record);
  }

  public List<Map<String, Object>> values(long tenant, String object, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    var args = new ArrayList<Object>(List.of(tenant, object));
    args.addAll(ids);
    return db.queryForList(
        "SELECT * FROM eam_business_value WHERE tenant_id=? AND object_key=? AND record_id IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ") ORDER BY field_id,ordinal_no",
        args.toArray());
  }

  public void replace(
      long tenant, String object, long record, String field, List<Map<String, Object>> rows) {
    db.update(
        "DELETE FROM eam_business_value WHERE tenant_id=? AND object_key=? AND record_id=? AND"
            + " field_id=?",
        tenant,
        object,
        record,
        field);
    int i = 0;
    for (var r : rows)
      db.update(
          "INSERT INTO"
              + " eam_business_value(tenant_id,object_key,record_id,field_id,ordinal_no,text_value,number_value,date_value,datetime_value,bool_value,reference_id,reference_name)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
          tenant,
          object,
          record,
          field,
          i++,
          r.get("text_value"),
          r.get("number_value"),
          r.get("date_value"),
          r.get("datetime_value"),
          r.get("bool_value"),
          r.get("reference_id"),
          r.get("reference_name"));
  }

  public List<Map<String, Object>> baseOptions(long tenant, String type) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,status enabled,parent_id parentId FROM eam_base_item WHERE"
            + " tenant_id=? AND item_type=? AND deleted=0 ORDER BY sort_no,id",
        tenant,
        type);
  }

  public List<Map<String, Object>> organizations(long tenant) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,CAST(parent_id AS CHAR) parentId,org_type type FROM"
            + " eam_org_unit WHERE tenant_id=? AND deleted=0 AND status=1",
        tenant);
  }

  public List<Map<String, Object>> users(long tenant) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,display_name name,org_id FROM eam_sys_user WHERE tenant_id=?"
            + " AND deleted=0 AND status=1 ORDER BY display_name,id",
        tenant);
  }

  public Map<String, Object> reference(long tenant, String source, String id) {
    String sql;
    Object[] args;
    if (source.startsWith("dictionary:")) {
      sql =
          "SELECT o.id,o.name,(o.enabled=1 AND d.enabled=1) enabled FROM eam_business_option o JOIN"
              + " eam_business_dictionary d ON d.id=o.dictionary_id AND d.tenant_id=o.tenant_id"
              + " WHERE o.tenant_id=? AND o.dictionary_id=? AND o.id=? FOR SHARE";
      args = new Object[] {tenant, source.substring(11), id};
    } else if (source.startsWith("base:")) {
      sql =
          "SELECT id,name,status enabled FROM eam_base_item WHERE tenant_id=? AND item_type=? AND"
              + " id=? AND deleted=0 FOR SHARE";
      args = new Object[] {tenant, source.substring(5), id};
    } else if (source.equals("user")) {
      sql =
          "SELECT id,display_name name,org_id orgId,status enabled FROM eam_sys_user WHERE"
              + " tenant_id=? AND id=? AND deleted=0 FOR SHARE";
      args = new Object[] {tenant, id};
    } else if (source.equals("department")) {
      sql =
          "SELECT id,name,id orgId,status enabled FROM eam_org_unit WHERE tenant_id=? AND id=? AND"
              + " org_type='部门' AND deleted=0 FOR SHARE";
      args = new Object[] {tenant, id};
    } else
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0002");
    var rows = db.queryForList(sql, args);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public Map<Long, String> repairCategories(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var args = new ArrayList<Object>(List.of(tenant));
    args.addAll(ids);
    var rows =
        db.queryForList(
            "SELECT r.id,CAST(e.category_id AS CHAR) category FROM eam_repair_order r LEFT JOIN"
                + " eam_equipment e ON e.tenant_id=r.tenant_id AND e.id=r.equipment_id WHERE"
                + " r.tenant_id=? AND r.id IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ")",
            args.toArray());
    var result = new HashMap<Long, String>();
    for (var row : rows)
      result.put(((Number) row.get("id")).longValue(), Objects.toString(row.get("category"), ""));
    return result;
  }

  public Map<String, Object> pageOptions(
      long tenant,
      String source,
      String term,
      int page,
      int size,
      boolean inactive,
      Collection<String> organizations,
      Long ownUser) {
    String from, columns, where;
    String order = "name,id";
    var args = new ArrayList<Object>();
    if (source.startsWith("dictionary:")) {
      from =
          "eam_business_option o JOIN eam_business_dictionary d ON d.id=o.dictionary_id AND"
              + " d.tenant_id=o.tenant_id";
      columns = "o.id,o.name,o.enabled,o.sort_no sortOrder";
      where =
          "o.tenant_id=? AND o.dictionary_id=?"
              + (inactive ? "" : " AND o.enabled=1 AND d.enabled=1");
      args.add(tenant);
      args.add(source.substring(11));
      order = "sortOrder,id";
    } else if (source.startsWith("base:")) {
      from = "eam_base_item o";
      columns = "CAST(o.id AS CHAR) id,o.name,o.status enabled,o.sort_no sortOrder";
      where =
          "o.tenant_id=? AND o.item_type=? AND o.deleted=0" + (inactive ? "" : " AND o.status=1");
      args.add(tenant);
      args.add(source.substring(5));
      order = "sortOrder,id";
    } else {
      boolean users = source.equals("user");
      from = users ? "eam_sys_user o" : "eam_org_unit o";
      columns =
          users
              ? "CAST(o.id AS CHAR) id,o.display_name name,o.status enabled"
              : "CAST(o.id AS CHAR) id,o.name,o.status enabled";
      where =
          "o.tenant_id=? AND o.deleted=0 AND o.status=1" + (users ? "" : " AND o.org_type='部门'");
      args.add(tenant);
      String scope = "0=1";
      if (!organizations.isEmpty()) {
        scope =
            (users ? "o.org_id" : "o.id")
                + " IN ("
                + String.join(",", Collections.nCopies(organizations.size(), "?"))
                + ")";
        args.addAll(organizations);
      }
      if (users && ownUser != null) {
        scope += " OR o.id=?";
        args.add(ownUser);
      }
      where += " AND (" + scope + ")";
    }
    where +=
        " AND LOWER("
            + (source.equals("user") ? "o.display_name" : "o.name")
            + ") LIKE ? ESCAPE '!'";
    args.add(
        "%"
            + term.toLowerCase(Locale.ROOT).replace("!", "!!").replace("%", "!%").replace("_", "!_")
            + "%");
    long count =
        db.queryForObject(
            "SELECT COUNT(*) FROM " + from + " WHERE " + where, Long.class, args.toArray());
    var paged = new ArrayList<>(args);
    paged.add(size);
    paged.add((long) (page - 1) * size);
    return Map.of(
        "data",
        db.queryForList(
            "SELECT "
                + columns
                + " FROM "
                + from
                + " WHERE "
                + where
                + " ORDER BY "
                + order
                + " LIMIT ? OFFSET ?",
            paged.toArray()),
        "total",
        count);
  }
}
