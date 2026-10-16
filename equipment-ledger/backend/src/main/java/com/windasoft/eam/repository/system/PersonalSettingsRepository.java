package com.windasoft.eam.repository.system;

import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class PersonalSettingsRepository {
  private final JdbcTemplate db;

  public PersonalSettingsRepository(JdbcTemplate db) {
    this.db = db;
  }

  public String theme(long tenant, long user) {
    var values = db.queryForList(
        "SELECT theme FROM eam_user_preference WHERE tenant_id=? AND user_id=?",
        String.class, tenant, user);
    return values.isEmpty() ? "blue" : values.getFirst();
  }

  public List<String> roleNames(long tenant, long user) {
    return db.queryForList(
        "SELECT DISTINCT r.name FROM eam_sys_role r JOIN eam_sys_user_role ur"
            + " ON ur.tenant_id=r.tenant_id AND ur.role_id=r.id"
            + " WHERE ur.tenant_id=? AND ur.user_id=? AND r.deleted=0 AND r.status=1 ORDER BY r.name",
        String.class, tenant, user);
  }

  public void saveTheme(long tenant, long user, String theme) {
    db.update(
        "INSERT INTO eam_user_preference(tenant_id,user_id,theme) VALUES(?,?,?)"
            + " ON DUPLICATE KEY UPDATE theme=VALUES(theme)", tenant, user, theme);
  }
}
