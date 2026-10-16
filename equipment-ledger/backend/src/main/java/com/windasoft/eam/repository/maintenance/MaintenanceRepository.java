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

  public List<Map<String, Object>> loadDefinition(
      long tenantId, Object kind, long id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind=? AND id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        kind,
        id);
  }

  public List<Map<String, Object>> listDefinitions(long tenantId, Object kind) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind=? ORDER BY"
            + " created_at DESC,id DESC",
        tenantId,
        kind);
  }

  public List<Map<String, Object>> listAllTasks(long tenantId) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_task WHERE tenant_id=? ORDER BY due_date DESC,id DESC",
        tenantId);
  }

  public List<Map<String, Object>> listPlanSchedules(long tenantId, long id) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND"
            + " enabled=1 ORDER BY id",
        tenantId,
        id);
  }

  public int countActiveUser(long tenantId, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
            + " status=1",
        Integer.class,
        tenantId,
        id);
  }

  public int countAttachment(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_file WHERE tenant_id=? AND id=?",
        Integer.class,
        tenantId,
        value);
  }

  public int countImageAttachment(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_file WHERE tenant_id=? AND id=? AND"
            + " extension IN ('.jpg','.jpeg','.png','.gif','.webp')",
        Integer.class,
        tenantId,
        value);
  }

  public List<Map<String, Object>> loadEquipment(long tenantId, long id) {
    return db.queryForList(
        "SELECT * FROM eam_equipment WHERE tenant_id=? AND id=? AND deleted=0", tenantId, id);
  }

  public List<Map<String, Object>> listCategoryParents(long tenantId) {
    return db.queryForList(
        "SELECT id,parent_id,name FROM eam_base_item WHERE tenant_id=? AND item_type='CATEGORY'"
            + " AND deleted=0",
        tenantId);
  }

  public int countDepartment(long tenantId, Object department) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0",
        Integer.class,
        tenantId,
        department);
  }

  public List<Map<String, Object>> loadLegacyStock(long tenantId, Object stockId) {
    return db.queryForList(
        "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=?", tenantId, stockId);
  }

  public int insertDefinition(
      long definitionId,
      long tenantId,
      Object kind,
      Object code,
      String value,
      Object value6,
      long userId,
      String payload) {
    return db.update(
        "INSERT INTO"
            + " eam_maintenance_definition(id,tenant_id,kind,code,name,department_id,creator_id,payload)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        definitionId,
        tenantId,
        kind,
        code,
        value,
        value6,
        userId,
        payload);
  }

  public int updateDefinition(
      Object code, String value, Object value3, String payload, long definitionId, long tenantId) {
    return db.update(
        "UPDATE eam_maintenance_definition SET"
            + " code=?,name=?,department_id=?,payload=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE id=? AND tenant_id=?",
        code,
        value,
        value3,
        payload,
        definitionId,
        tenantId);
  }

  public List<Map<String, Object>> lockSchedule(long scheduleId, long tenantId, long definitionId) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_schedule WHERE id=? AND tenant_id=? AND plan_id=?"
            + " FOR UPDATE",
        scheduleId,
        tenantId,
        definitionId);
  }

  public List<Map<String, Object>> findLatestDueDate(long tenantId, long scheduleId) {
    return db.queryForList(
        "SELECT due_date FROM eam_maintenance_task WHERE tenant_id=? AND schedule_id=?"
            + " ORDER BY due_date DESC LIMIT 1",
        tenantId,
        scheduleId);
  }

  public int updateSchedule(
      Object typeId,
      Object standardId,
      Object date,
      Object anchorDate,
      Object cycleIndex,
      String payload,
      long tenantId,
      long scheduleId) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET"
            + " type_id=?,standard_id=?,next_date=?,anchor_date=?,cycle_index=?,payload=?,enabled=1"
            + " WHERE tenant_id=? AND id=?",
        typeId,
        standardId,
        date,
        anchorDate,
        cycleIndex,
        payload,
        tenantId,
        scheduleId);
  }

  public int insertSchedule(
      long scheduleId,
      long tenantId,
      long definitionId,
      Object equipmentId,
      Object typeId,
      Object standardId,
      Object date,
      Object date8,
      String payload) {
    return db.update(
        "INSERT INTO"
            + " eam_maintenance_schedule(id,tenant_id,plan_id,equipment_id,type_id,standard_id,next_date,anchor_date,payload)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        scheduleId,
        tenantId,
        definitionId,
        equipmentId,
        typeId,
        standardId,
        date,
        date8,
        payload);
  }

  public List<Map<String, Object>> listScheduleIds(long tenantId, long definitionId) {
    return db.queryForList(
        "SELECT id FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=?",
        tenantId,
        definitionId);
  }

  public int disableSchedule(long tenantId, Object id) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET enabled=0 WHERE tenant_id=? AND id=?", tenantId, id);
  }

  public int disablePlanSchedules(long tenantId, long id) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET enabled=0 WHERE tenant_id=? AND plan_id=?",
        tenantId,
        id);
  }

  public int countPlanTasks(long tenantId, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND plan_id=?",
        Integer.class,
        tenantId,
        id);
  }

  public int countScheduleReferences(long tenantId, long id, String column) {
    if (!Set.of("type_id", "standard_id").contains(column))
      throw new IllegalArgumentException("未知保养关联类型");
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_schedule WHERE tenant_id=? AND "
            + column
            + "=? AND enabled=1",
        Integer.class,
        tenantId,
        id);
  }

  public int countProjectReferences(long tenantId, Object value) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_schedule WHERE tenant_id=? AND enabled=1 AND"
            + " JSON_CONTAINS(JSON_EXTRACT(payload,'$.projectRefs'),JSON_OBJECT('standardId',CAST(?"
            + " AS CHAR)))",
        Integer.class,
        tenantId,
        value);
  }

  public List<Map<String, Object>> listStandardPayloads(long tenantId) {
    return db.queryForList(
        "SELECT payload FROM eam_maintenance_definition WHERE tenant_id=? AND"
            + " kind='standards'",
        tenantId);
  }

  public int deleteDefinition(long tenantId, long id) {
    return db.update(
        "DELETE FROM eam_maintenance_definition WHERE tenant_id=? AND id=?", tenantId, id);
  }

  public List<Map<String, Object>> listEquipment(long tenantId) {
    return db.queryForList(
        "SELECT * FROM eam_equipment WHERE tenant_id=? AND deleted=0 ORDER BY" + " equipment_code",
        tenantId);
  }

  public List<Map<String, Object>> listOrganizations(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,CAST(COALESCE(parent_id,0) AS CHAR)"
            + " parentId,COALESCE(display_type,org_type) type FROM eam_org_unit WHERE"
            + " tenant_id=? AND deleted=0 AND status=1",
        tenantId);
  }

  public List<Map<String, Object>> listLegacyStock(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,code,name,model,quantity,company_id FROM"
            + " eam_spare_stock WHERE tenant_id=? ORDER BY name",
        tenantId);
  }

  public List<Map<String, Object>> listUsers(long tenantId) {
    return db.queryForList(
        "SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'')"
            + " department FROM eam_sys_user u LEFT JOIN eam_org_unit o ON o.id=u.org_id AND"
            + " o.tenant_id=u.tenant_id WHERE u.tenant_id=? AND u.deleted=0 AND u.status=1"
            + " ORDER BY u.display_name",
        tenantId);
  }

  public List<Map<String, Object>> listCategories(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,CAST(COALESCE(parent_id,0) AS CHAR) parentId FROM"
            + " eam_base_item WHERE tenant_id=? AND item_type='CATEGORY' AND deleted=0 AND"
            + " status=1",
        tenantId);
  }

  public List<Map<String, Object>> loadTask(long tenantId, long id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_task WHERE tenant_id=? AND id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        id);
  }

  public List<Map<String, Object>> listTaskEvents(long tenantId, long id) {
    return db.queryForList(
        "SELECT action,actor_name actor,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s')"
            + " time,details FROM eam_maintenance_event WHERE tenant_id=? AND task_id=?"
            + " ORDER BY created_at,id",
        tenantId, id);
  }

  public int insertTaskEvent(
      long recordId,
      long tenantId,
      long taskId,
      Long actorId,
      Object name,
      Object action,
      String payload) {
    return db.update(
        "INSERT INTO eam_maintenance_event(id,tenant_id,task_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        taskId,
        actorId,
        name,
        action,
        payload);
  }

  public int insertInbox(
      long recordId,
      long tenantId,
      long taskId,
      Object user,
      Object kind,
      Object title,
      boolean todo) {
    return db.update(
        "INSERT INTO eam_maintenance_inbox(id,tenant_id,task_id,user_id,kind,title,is_todo)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        taskId,
        user,
        kind,
        title,
        todo);
  }

  public int completeTodos(long tenantId, long taskId) {
    return db.update(
        "UPDATE eam_maintenance_inbox SET done=1 WHERE tenant_id=? AND task_id=? AND is_todo=1",
        tenantId,
        taskId);
  }

  public int insertEquipmentHistory(
      long recordId,
      long tenantId,
      Object equipmentId,
      Object code,
      String value,
      Object value6,
      Object value7,
      String value8) {
    return db.update(
        "INSERT INTO"
            + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
            + " VALUES(?,?,?,'MAINTENANCE',?,NOW(),?,?,?,?)",
        recordId,
        tenantId,
        equipmentId,
        code,
        value,
        value6,
        value7,
        value8);
  }

  public int resetScheduleDate(Object date, Object date2, long tenantId, Object scheduleId) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET next_date=?,anchor_date=?,cycle_index=0 WHERE"
            + " tenant_id=? AND id=?",
        date,
        date2,
        tenantId,
        scheduleId);
  }

  public int updateTask(
      Object next, Object owner, String payload, String payload4, long tenantId, long id) {
    return db.update(
        "UPDATE eam_maintenance_task SET"
            + " status=?,owner_id=?,snapshot=?,result=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=?",
        next,
        owner,
        payload,
        payload4,
        tenantId,
        id);
  }

  public List<Map<String, Object>> findUserName(long tenantId, long id) {
    return db.queryForList(
        "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?", tenantId, id);
  }

  public List<Map<String, Object>> lockPlans(long tenant, Long planId) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind='plans'"
            + (planId == null ? "" : " AND id=?")
            + " ORDER BY id FOR UPDATE",
        planId == null ? new Object[] {tenant} : new Object[] {tenant, planId});
  }

  public List<Map<String, Object>> lockPlanSchedules(long tenantId, Object id) {
    return db.queryForList(
        "SELECT * FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND enabled=1"
            + " ORDER BY id FOR UPDATE",
        tenantId,
        id);
  }

  public int countOpenScheduleTasks(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND schedule_id=?"
            + " AND status NOT IN ('已完成','已作废','过期作废')",
        Integer.class,
        tenantId,
        id);
  }

  public int insertTask(
      long taskId,
      long tenantId,
      Object code,
      Object id,
      Object id5,
      Object id6,
      Object companyOrgId,
      Object departmentOrgId,
      Object due,
      Object expires,
      String payload) {
    return db.update(
        "INSERT IGNORE INTO"
            + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,expires_date,snapshot)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        taskId,
        tenantId,
        code,
        id,
        id5,
        id6,
        companyOrgId,
        departmentOrgId,
        due,
        expires,
        payload);
  }

  public List<Map<String, Object>> findLastCompletedResult(long tenantId, Object id, Object id3) {
    return db.queryForList(
        "SELECT result FROM eam_maintenance_task WHERE tenant_id=? AND equipment_id=?"
            + " AND schedule_id=? AND status='已完成' ORDER BY updated_at DESC LIMIT 1",
        tenantId,
        id,
        id3);
  }

  public int updateTaskResult(String payload, long tenantId, long taskId) {
    return db.update(
        "UPDATE eam_maintenance_task SET result=? WHERE tenant_id=? AND id=?",
        payload,
        tenantId,
        taskId);
  }

  public int resetScheduleDate47(Object due, Object due2, long tenantId, Object id) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET next_date=?,anchor_date=?,cycle_index=0"
            + " WHERE tenant_id=? AND id=?",
        due,
        due2,
        tenantId,
        id);
  }

  public int advanceSchedule(Object due, Object index, long tenantId, Object id) {
    return db.update(
        "UPDATE eam_maintenance_schedule SET next_date=?,cycle_index=? WHERE tenant_id=? AND"
            + " id=?",
        due,
        index,
        tenantId,
        id);
  }

  public List<Map<String, Object>> lockExpiredTasks(long tenantId, Object today) {
    return db.queryForList(
        "SELECT id FROM eam_maintenance_task WHERE tenant_id=? AND expires_date<? AND"
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
        "SELECT * FROM eam_maintenance_task WHERE tenant_id=? AND status='待执行'", tenantId);
  }

  public int countExecutionNotices(long tenantId, Object id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_maintenance_inbox WHERE tenant_id=? AND task_id=? AND"
            + " kind='保养待执行'",
        Integer.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> listInbox(long tenantId, long userId) {
    return db.queryForList(
        "SELECT CAST(i.id AS CHAR) id,CAST(t.id AS CHAR) orderId,'maintenance' module,t.code"
            + " orderNo,JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName'))"
            + " equipmentName,t.status,i.kind,i.title,i.is_todo todo,i.done,i.read_at IS NOT NULL"
            + " isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM"
            + " eam_maintenance_inbox i JOIN eam_maintenance_task t ON t.id=i.task_id AND"
            + " t.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.user_id=? ORDER BY i.created_at"
            + " DESC,i.id DESC LIMIT 300",
        tenantId, userId);
  }

  public int markInboxRead(long tenantId, long userId, long id) {
    return db.update(
        "UPDATE eam_maintenance_inbox SET read_at=NOW() WHERE tenant_id=? AND user_id=? AND id=?",
        tenantId,
        userId,
        id);
  }

  public List<Map<String, Object>> generationTargets() {
    return db.queryForList(
        "SELECT tenant_id,id FROM eam_maintenance_definition WHERE kind='plans'");
  }
}
