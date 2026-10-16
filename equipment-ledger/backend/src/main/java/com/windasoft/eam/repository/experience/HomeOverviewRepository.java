package com.windasoft.eam.repository.experience;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class HomeOverviewRepository {
  private final JdbcTemplate db;

  public HomeOverviewRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> load(long tenant, long user, long template) {
    return db.queryForList(
        "SELECT * FROM eam_home_overview_layout WHERE tenant_id=? AND user_id=? AND template_id=?",
        tenant,
        user,
        template);
  }

  public List<Map<String, Object>> latest(long tenant, long user) {
    return db.queryForList(
        "SELECT template_id FROM eam_home_overview_layout WHERE tenant_id=? AND user_id=? ORDER BY"
            + " updated_at DESC,template_id LIMIT 1",
        tenant,
        user);
  }

  public void insert(long tenant, long user, long template, long version, String payload) {
    db.update(
        "INSERT INTO"
            + " eam_home_overview_layout(tenant_id,user_id,template_id,template_version,payload)"
            + " VALUES(?,?,?,?,?)",
        tenant,
        user,
        template,
        version,
        payload);
  }

  public int update(
      long tenant, long user, long template, long version, long rowVersion, String payload) {
    return db.update(
        "UPDATE eam_home_overview_layout SET"
            + " template_version=?,payload=?,row_version=row_version+1,updated_at=NOW(3) WHERE"
            + " tenant_id=? AND user_id=? AND template_id=? AND row_version=?",
        version,
        payload,
        tenant,
        user,
        template,
        rowVersion);
  }
}
