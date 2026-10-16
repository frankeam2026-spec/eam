package com.windasoft.eam.repository.experience;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {
  private final JdbcTemplate db;

  public AnalyticsRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Source(String sql, List<Object> args) {}

  public List<Map<String, Object>> rows(
      Source source, String select, String where, List<Object> extra, String tail) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    return db.queryForList(
        "SELECT " + select + " FROM (" + source.sql() + ") s WHERE " + where + " " + tail,
        args.toArray());
  }

  public List<Map<String, Object>> cte(Source source, String query, List<Object> extra) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    return db.queryForList("WITH base AS (" + source.sql() + ") " + query, args.toArray());
  }

  public List<Map<String, Object>> organizations(long tenant) {
    return db.queryForList(
        "SELECT id,parent_id,name,org_type,status FROM eam_org_unit WHERE tenant_id=? AND deleted=0"
            + " ORDER BY name,id",
        tenant);
  }

  public List<Map<String, Object>> warehouses(long tenant) {
    return db.queryForList("SELECT id,company_id FROM inv_warehouse WHERE tenant_id=?", tenant);
  }
}
