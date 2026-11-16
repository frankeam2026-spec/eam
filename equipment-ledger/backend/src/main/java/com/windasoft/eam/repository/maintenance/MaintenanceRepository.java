package com.windasoft.eam.repository.maintenance;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.time.LocalDate;
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

  public LocalDate latestDueDate(long tenant, long schedule) {
    var dates =
        db.query(
            "SELECT due_date FROM v_maintenance_task WHERE tenant_id=? AND schedule_id=? ORDER BY"
                + " due_date DESC LIMIT 1",
            (row, index) -> row.getDate("due_date").toLocalDate(),
            tenant,
            schedule);
    return dates.isEmpty() ? null : dates.getFirst();
  }

  public int countPlanTasks(long tenantId, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND plan_id=?",
        Integer.class,
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

  public List<Map<String, Object>> loadTask(long tenantId, long id, boolean lock) {
    if (lock) {
      db.queryForList(
          "SELECT id FROM eam_maintenance_task WHERE tenant_id=? AND id=? FOR UPDATE",
          tenantId,
          id);
    }
    return db.queryForList(
        "SELECT * FROM v_maintenance_task WHERE tenant_id=? AND id=?", tenantId, id);
  }

  public boolean hasTaskExecutionOrMaterials(long tenant, long id) {
    return db.queryForObject(
        "SELECT (EXISTS(SELECT 1 FROM eam_maintenance_execution e WHERE e.tenant_id=? AND"
            + " e.task_id=? AND (e.started_at IS NOT NULL OR e.work_start_at IS NOT NULL OR"
            + " e.work_end_at IS NOT NULL OR COALESCE(e.description,'')<>'')) OR EXISTS(SELECT 1"
            + " FROM inv_requisition r WHERE r.tenant_id=? AND r.task_kind='保养' AND r.task_id=?) OR"
            + " EXISTS(SELECT 1 FROM inv_outbound_line l WHERE l.tenant_id=? AND l.task_kind='保养'"
            + " AND l.task_id=?))",
        Boolean.class,
        tenant,
        id,
        tenant,
        id,
        tenant,
        id);
  }

  public int softDeleteTask(long tenant, long id, long actor) {
    return db.update(
        "UPDATE eam_maintenance_task SET"
            + " deleted=1,deleted_at=NOW(6),deleted_by=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=? AND deleted=0",
        actor,
        tenant,
        id);
  }

  public int postponeTask(long tenant, long id, java.time.LocalDate date) {
    return db.update(
        "UPDATE eam_maintenance_task SET expires_date=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=? AND deleted=0",
        date,
        tenant,
        id);
  }

  public int updateTask(String next, Long owner, String snapshot, long tenantId, long id) {
    return db.update(
        "UPDATE eam_maintenance_task SET submitted_at=IF(?='待验收' AND"
            + " status<>'待验收',NOW(6),submitted_at),status=?,owner_id=?,snapshot=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=?",
        next,
        next,
        owner,
        snapshot,
        tenantId,
        id);
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

  public int countOpenScheduleTasks(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND schedule_id=?"
            + " AND status NOT IN ('已完成','已作废','过期作废')",
        Integer.class,
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

  public long taskTypeReferences(long tenant, long typeId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND type_id=?",
        Long.class,
        tenant,
        typeId);
  }

  public int insertTask(
      long taskId,
      long tenantId,
      Object code,
      Object id,
      Object id5,
      Object id6,
      Object typeId,
      Object companyOrgId,
      Object departmentOrgId,
      Object due,
      Object expires,
      String payload) {
    int inserted =
        db.update(
            "INSERT IGNORE INTO"
                + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,type_id,company_id,department_id,due_date,expires_date,snapshot)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            taskId,
            tenantId,
            code,
            id,
            id5,
            id6,
            typeId,
            companyOrgId,
            departmentOrgId,
            due,
            expires,
            payload);
    return inserted;
  }

  public List<Map<String, Object>> findLastCompletedResult(long tenantId, Object id, Object id3) {
    return db.queryForList(
        "SELECT result FROM v_maintenance_task WHERE tenant_id=? AND equipment_id=?"
            + " AND schedule_id=? AND status='已完成' ORDER BY updated_at DESC LIMIT 1",
        tenantId,
        id,
        id3);
  }

  public List<Map<String, Object>> lockExpiredTasks(long tenantId, Object today) {
    return lockedTaskRows(
        "SELECT id FROM v_maintenance_task WHERE tenant_id=? AND expires_date<? AND"
            + " status='待执行' FOR UPDATE",
        tenantId,
        today);
  }

  public int expireTask(long tenantId, long id) {
    return db.update(
        "UPDATE eam_maintenance_task SET status='过期作废',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listPendingTasks(long tenantId) {
    return db.queryForList(
        "SELECT * FROM v_maintenance_task WHERE tenant_id=? AND status='待执行'", tenantId);
  }

  public List<Map<String, Object>> listInbox(long tenantId, long userId) {
    return db.queryForList(
        "SELECT CAST(i.id AS CHAR) id,CAST(t.id AS CHAR) orderId,'maintenance' module,t.code"
            + " orderNo,JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName'))"
            + " equipmentName,t.status,i.kind,i.title,i.message_key messageKey,i.message_args"
            + " messageArgs,i.is_todo todo,i.done,i.read_at IS NOT NULL"
            + " isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM"
            + " eam_maintenance_inbox i JOIN v_maintenance_task t ON t.id=i.task_id AND"
            + " t.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.user_id=? ORDER BY i.created_at"
            + " DESC,i.id DESC LIMIT 300",
        tenantId, userId);
  }

  public List<Map<String, Object>> generationTargets() {
    return db.queryForList("SELECT tenant_id,id FROM v_maintenance_definition WHERE kind='plans'");
  }

  private List<Map<String, Object>> lockedTaskRows(String sql, Object... args) {
    db.queryForList(
        sql.replace("v_maintenance_task", "eam_maintenance_task").replace("SELECT *", "SELECT id"),
        args);
    return db.queryForList(sql.replace(" FOR UPDATE", ""), args);
  }
}
