package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkCenterRepository {
  private final JdbcTemplate db;
  private final RepairOrderMapper repairs;

  public List<RepairOrder> findRepairs(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder> query) {
    return repairs.selectList(query);
  }

  public WorkCenterRepository(JdbcTemplate db, RepairOrderMapper repairs) {
    this.db = db;
    this.repairs = repairs;
  }

  public List<Map<String, Object>> findUserName(long tenantId, Object value) {
    return db.queryForList(
        "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?", tenantId, value);
  }

  public List<Map<String, Object>> findTaskCreator(long tenantId, Object id, String module) {
    return db.queryForList(
        "SELECT d.creator_id FROM "
            + (module.equals("maintenance")
                ? "v_maintenance_task t JOIN v_maintenance_definition d"
                : "v_inspection_task t JOIN v_inspection_definition d")
            + " ON d.tenant_id=t.tenant_id AND d.id=t.plan_id WHERE t.tenant_id=? AND t.id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> findOrganizationName(long tenantId, Object value) {
    return db.queryForList(
        "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?", tenantId, value);
  }
}
