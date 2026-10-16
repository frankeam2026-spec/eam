package com.windasoft.eam.repository.inventory;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InventoryLockRepository {
  private final JdbcTemplate db;

  public InventoryLockRepository(JdbcTemplate db) {
    this.db = db;
  }

  public void enter(long tenant) {
    db.update(
        "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " tenant_id=tenant_id",
        tenant);
    db.queryForMap("SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", tenant);
  }
}
