package com.windasoft.eam.inventory;

import com.windasoft.eam.system.AuthContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryMutex {
  private final JdbcTemplate db;
  private final AuthContext auth;

  public InventoryMutex(JdbcTemplate db, AuthContext auth) {
    this.db = db;
    this.auth = auth;
  }

  public void enter() {
    db.update(
        "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " tenant_id=tenant_id",
        auth.tenantId());
    db.queryForMap(
        "SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", auth.tenantId());
  }
}
