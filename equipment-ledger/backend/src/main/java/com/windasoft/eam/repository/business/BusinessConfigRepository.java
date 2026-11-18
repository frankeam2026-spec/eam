package com.windasoft.eam.repository.business;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
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
    } else throw new LocalizedArgumentException("server.business.message0002");
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
