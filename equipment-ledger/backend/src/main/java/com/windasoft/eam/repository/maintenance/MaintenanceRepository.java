package com.windasoft.eam.repository.maintenance;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class MaintenanceRepository {
  private final JdbcTemplate db;
  private final NumberCounterMapper counter;

  public MaintenanceRepository(JdbcTemplate db, NumberCounterMapper counter) {
    this.db = db;
    this.counter = counter;
  }

  public void nextMonthlyNumber(String rule, String month) {
    counter.nextMonthlyNumber(rule, month);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  public List<Map<String, Object>> loadDefinition(long tenantId, String kind, long id) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_definition WHERE tenant_id=? AND kind=? AND id=?",
        tenantId,
        kind,
        id);
  }

  public List<Map<String, Object>> listDefinitions(long tenantId, Object kind) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_definition WHERE tenant_id=? AND kind=? ORDER BY"
            + " created_at DESC,id DESC",
        tenantId,
        kind);
  }

  public List<Map<String, Object>> listPlanSchedules(long tenantId, long id) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND"
            + " enabled=1 ORDER BY id",
        tenantId,
        id);
  }

  public int countScheduleReferences(long tenantId, long id, String column) {
    if (!Set.of("type_id", "standard_id").contains(column)) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0001");
    }
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_schedule WHERE tenant_id=? AND "
            + column
            + "=? AND enabled=1",
        Integer.class,
        tenantId,
        id);
  }

  public int countProjectReferences(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_schedule WHERE tenant_id=? AND enabled=1 AND"
            + " JSON_CONTAINS(JSON_EXTRACT(payload,'$.projectRefs'),JSON_OBJECT('standardId',CAST(?"
            + " AS CHAR)))",
        Integer.class,
        tenantId,
        value);
  }

  public List<Map<String, Object>> listStandardPayloads(long tenantId) {
    return db.queryForList(
        "SELECT payload FROM v_maintenance_definition WHERE tenant_id=? AND" + " kind='standards'",
        tenantId);
  }

  /** 仅取已锁定计划的关系投影；加锁由生成Service调用计划主仓储，不再依赖通用写入器。 */
  public List<Map<String, Object>> planRowsForGeneration(long tenant, Long planId) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_definition WHERE tenant_id=? AND kind='plans'"
            + (planId == null ? "" : " AND id=?")
            + " ORDER BY id",
        planId == null ? new Object[] {tenant} : new Object[] {tenant, planId});
  }

  /** 生成Service先锁定完整安排集合，此处只投影启用安排并保留原ID顺序。 */
  public List<Map<String, Object>> scheduleRowsForGeneration(long tenantId, long id) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND enabled=1"
            + " ORDER BY id",
        tenantId,
        id);
  }

  public long cyclePlanTypeReferences(long tenant, long typeId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_schedule s JOIN v_maintenance_definition p ON"
            + " p.tenant_id=s.tenant_id AND p.id=s.plan_id AND p.kind='plans' WHERE s.tenant_id=?"
            + " AND s.type_id=?",
        Long.class,
        tenant,
        typeId);
  }

  public List<Map<String, Object>> generationTargets() {
    return db.queryForList("SELECT tenant_id,id FROM v_maintenance_definition WHERE kind='plans'");
  }
}
