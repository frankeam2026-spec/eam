package com.windasoft.eam.repository.inspection;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class InspectionRepository {
  private final JdbcTemplate db;
  private final com.windasoft.eam.repository.common.TaskSourceRelationsRepository taskSources;
  private final InspectionExecutionStorage executions;
  private final NumberCounterMapper counter;
  private final InspectionDefinitionStorage definitions;

  public InspectionRepository(
      JdbcTemplate db,
      NumberCounterMapper counter,
      InspectionDefinitionStorage definitions,
      InspectionExecutionStorage executions,
      com.windasoft.eam.repository.common.TaskSourceRelationsRepository taskSources) {
    this.db = db;
    this.taskSources = taskSources;
    this.counter = counter;
    this.executions = executions;
    this.definitions = definitions;
  }

  public void nextMonthlyNumber(String rule, String month) {
    counter.nextMonthlyNumber(rule, month);
  }

  public long currentNumber() {
    return counter.currentValue();
  }

  public List<Map<String, Object>> loadDefinition(
      long tenantId, Object kind, long id, boolean lock) {
    if (lock) definitions.lock(tenantId, kind.toString(), id);
    return db.queryForList(
        "SELECT * FROM v_inspection_definition WHERE tenant_id=? AND kind=? AND id=?",
        tenantId,
        kind,
        id);
  }

  public List<Map<String, Object>> listScheduleDates(Object tenantId, Object id) {
    return db.queryForList(
        "SELECT next_at FROM eam_inspection_schedule WHERE tenant_id=? AND plan_id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> listDefinitions(long tenantId, Object kind) {
    return db.queryForList(
        "SELECT * FROM v_inspection_definition WHERE tenant_id=? AND kind=? ORDER BY"
            + " created_at DESC,id DESC",
        tenantId,
        kind);
  }

  public List<Map<String, Object>> listKindTasks(long tenantId, Object value) {
    return db.queryForList(
        "SELECT * FROM v_inspection_task WHERE tenant_id=? AND kind=? AND deleted=0",
        tenantId,
        value);
  }

  public List<Map<String, Object>> listKindDefinitions(long tenantId, Object kind) {
    return db.queryForList(
        "SELECT * FROM v_inspection_definition WHERE tenant_id=? AND kind=?", tenantId, kind);
  }

  public List<Map<String, Object>> loadEquipment(long tenantId, long id) {
    return db.queryForList(
        "SELECT * FROM eam_equipment WHERE tenant_id=? AND id=? AND deleted=0", tenantId, id);
  }

  public int countActiveUser(long tenantId, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
            + " status=1",
        Integer.class,
        tenantId,
        id);
  }

  public int countAttachment(long tenantId, String id, boolean images) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_inspection_file WHERE tenant_id=? AND id=?"
            + (images ? " AND extension IN ('.jpg','.jpeg','.png','.gif','.webp')" : ""),
        Integer.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> listCategoryParents(long tenantId) {
    return db.queryForList(
        "SELECT id,parent_id,name FROM eam_base_item WHERE tenant_id=? AND item_type='CATEGORY'"
            + " AND deleted=0",
        tenantId);
  }

  public List<Map<String, Object>> findDepartmentParent(long tenantId, Object department) {
    return db.queryForList(
        "SELECT parent_id FROM eam_org_unit WHERE tenant_id=? AND id=? AND org_type='部门' AND"
            + " deleted=0 AND status=1",
        tenantId,
        department);
  }

  public int countCategory(long tenantId, Object category) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND item_type='CATEGORY'"
            + " AND name=? AND deleted=0",
        Integer.class,
        tenantId,
        category);
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
    return definitions.insert(
        definitionId, tenantId, kind.toString(), code, value, value6, userId, payload);
  }

  public int updateDefinition(
      Object code, String value, Object value3, String payload, long tenantId, long definitionId) {
    return definitions.update(tenantId, definitionId, code, value, value3, payload);
  }

  public List<Map<String, Object>> lockPlanSchedules(long tenantId, long definitionId) {
    return db.queryForList(
        "SELECT * FROM eam_inspection_schedule WHERE tenant_id=? AND plan_id=? FOR UPDATE",
        tenantId,
        definitionId);
  }

  public int insertSchedule(
      long recordId, long tenantId, long definitionId, Object next, Object next5) {
    return db.update(
        "INSERT INTO eam_inspection_schedule(id,tenant_id,plan_id,next_at,anchor_at)"
            + " VALUES(?,?,?,?,?)",
        recordId,
        tenantId,
        definitionId,
        next,
        next5);
  }

  public int countTasksAtOrAfter(long tenantId, long definitionId, Object next) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND plan_id=? AND" + " due_at>=?",
        Integer.class,
        tenantId,
        definitionId,
        next);
  }

  public int resetScheduleDate(Object next, Object next2, long tenantId, long definitionId) {
    return db.update(
        "UPDATE eam_inspection_schedule SET next_at=?,anchor_at=?,cycle_index=0 WHERE"
            + " tenant_id=? AND plan_id=?",
        next,
        next2,
        tenantId,
        definitionId);
  }

  public int revisitSchedule(Object revisit, long tenantId, long definitionId) {
    return db.update(
        "UPDATE eam_inspection_schedule SET next_at=? WHERE tenant_id=? AND plan_id=?",
        revisit,
        tenantId,
        definitionId);
  }

  public int countPlanTasks(long tenantId, long id) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND plan_id=?",
        Integer.class,
        tenantId,
        id);
  }

  public List<Map<String, Object>> listOtherDefinitionPayloads(long tenantId, long id) {
    return db.queryForList(
        "SELECT kind,payload FROM v_inspection_definition WHERE tenant_id=? AND id<>?",
        tenantId,
        id);
  }

  public int deletePlanSchedules(long tenantId, long id) {
    return db.update(
        "DELETE FROM eam_inspection_schedule WHERE tenant_id=? AND plan_id=?", tenantId, id);
  }

  public int deleteDefinition(long tenantId, long id) {
    return definitions.delete(tenantId, id);
  }

  public List<Map<String, Object>> listEquipment(long tenantId) {
    return db.queryForList(
        "SELECT * FROM eam_equipment WHERE tenant_id=? AND deleted=0 ORDER BY" + " equipment_code",
        tenantId);
  }

  public List<Map<String, Object>> listOrganizations(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,COALESCE(CAST(parent_id AS CHAR),'')"
            + " parentId,COALESCE(display_type,org_type) type FROM eam_org_unit WHERE"
            + " tenant_id=? AND deleted=0 AND status=1",
        tenantId);
  }

  public List<Map<String, Object>> listUsers(long tenantId) {
    return db.queryForList(
        "SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'')"
            + " department FROM eam_sys_user u LEFT JOIN eam_org_unit o ON"
            + " o.tenant_id=u.tenant_id AND o.id=u.org_id WHERE u.tenant_id=? AND u.deleted=0"
            + " AND u.status=1 ORDER BY u.display_name",
        tenantId);
  }

  public List<Map<String, Object>> listCategories(long tenantId) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id,name,COALESCE(CAST(parent_id AS CHAR),'') parentId FROM"
            + " eam_base_item WHERE tenant_id=? AND item_type='CATEGORY' AND deleted=0 AND"
            + " status=1",
        tenantId);
  }

  public List<Map<String, Object>> loadTask(long tenantId, long id, boolean lock) {
    if (lock)
      db.queryForList(
          "SELECT id FROM eam_inspection_task WHERE tenant_id=? AND id=? FOR UPDATE", tenantId, id);
    return db.queryForList(
        "SELECT * FROM v_inspection_task WHERE tenant_id=? AND id=? AND deleted=0", tenantId, id);
  }

  public List<Map<String, Object>> listTaskEvents(long tenantId, long id) {
    return db.queryForList(
        "SELECT action,actor_name actor,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s')"
            + " time,details FROM eam_inspection_event WHERE tenant_id=? AND task_id=?"
            + " ORDER BY created_at,id",
        tenantId, id);
  }

  public List<Map<String, Object>> findUserName(long tenantId, long id) {
    return db.queryForList(
        "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?", tenantId, id);
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
        "INSERT INTO eam_inspection_event(id,tenant_id,task_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        taskId,
        actorId,
        name,
        action,
        payload);
  }

  public int completeTodos(long tenantId, long taskId) {
    return db.update(
        "UPDATE eam_inspection_inbox SET done=1 WHERE tenant_id=? AND task_id=? AND is_todo=1 AND"
            + " done=0",
        tenantId,
        taskId);
  }

  public int countNotice(long tenantId, long taskId, Object user, Object kind, boolean once) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_inspection_inbox WHERE tenant_id=? AND task_id=? AND"
            + " user_id=? AND kind=?"
            + (once ? "" : " AND done=0"),
        Integer.class,
        tenantId,
        taskId,
        user,
        kind);
  }

  public int insertInbox(
      long recordId,
      long tenantId,
      long taskId,
      Object user,
      Object kind,
      Object title,
      boolean todo) {
    int count =
        db.update(
            "INSERT INTO eam_inspection_inbox(id,tenant_id,task_id,user_id,kind,title,is_todo)"
                + " VALUES(?,?,?,?,?,?,?)",
            recordId,
            tenantId,
            taskId,
            user,
            kind,
            title,
            todo);
    db.update(
        "UPDATE eam_inspection_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key(kind),
        com.windasoft.eam.common.NotificationMessages.arguments(kind, title),
        tenantId,
        recordId);
    return count;
  }

  public List<Map<String, Object>> listActiveUsers(long tenantId) {
    return db.queryForList(
        "SELECT id FROM eam_sys_user WHERE tenant_id=? AND deleted=0 AND status=1", tenantId);
  }

  public int countDailyTask(
      long tenantId, Object planId, Object equipmentId, Object shiftId, Object due) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND plan_id=? AND"
            + " equipment_id=? AND shift_id=? AND due_at=?",
        Integer.class,
        tenantId,
        planId,
        equipmentId,
        shiftId,
        due);
  }

  public String findOrganizationName(long tenantId, Object departmentId) {
    return db.queryForObject(
        "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?",
        String.class,
        tenantId,
        departmentId);
  }

  public List<Map<String, Object>> findOrganizationParent(long tenantId, Object departmentId) {
    return db.queryForList(
        "SELECT parent_id FROM eam_org_unit WHERE tenant_id=? AND id=?", tenantId, departmentId);
  }

  public long taskTypeReferences(long tenant, long typeId) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND type_id=?",
        Long.class,
        tenant,
        typeId);
  }

  public int insertTask(
      long taskId,
      long tenantId,
      Object kind,
      Object code,
      Object planId,
      Object typeId,
      Object equipmentId,
      Object shiftId,
      Object company,
      Object departmentId,
      Object due,
      Object expires,
      Object status,
      String payload) {
    int inserted =
        db.update(
            "INSERT INTO"
                + " eam_inspection_task(id,tenant_id,kind,code,plan_id,type_id,equipment_id,shift_id,company_id,department_id,due_at,expires_at,status,snapshot)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            taskId,
            tenantId,
            kind,
            code,
            planId,
            typeId,
            equipmentId,
            shiftId,
            company,
            departmentId,
            due,
            expires,
            status,
            executions.historicalSnapshot(payload));
    if (inserted > 0) {
      executions.participants(tenantId, taskId, payload);
      taskSources.inspection(tenantId, taskId, payload);
    }
    return inserted;
  }

  public void setPlanType(long tenant, long planId, Long typeId) {
    definitions.setPlanType(tenant, planId, typeId);
  }

  public List<Map<String, Object>> lockPlan(long tenantId, Object planId) {
    definitions.lockPlan(tenantId, planId);
    return db.queryForList(
        "SELECT * FROM v_inspection_definition WHERE tenant_id=? AND id=? AND kind IN"
            + " ('daily-plans','periodic-plans')",
        tenantId,
        planId);
  }

  public Map<String, Object> lockSchedule(long tenantId, Object planId) {
    return db.queryForMap(
        "SELECT * FROM eam_inspection_schedule WHERE tenant_id=? AND plan_id=? FOR UPDATE",
        tenantId,
        planId);
  }

  public int advanceSchedule(Object next, Object index, long tenantId, Object planId) {
    return db.update(
        "UPDATE eam_inspection_schedule SET next_at=?,cycle_index=? WHERE tenant_id=? AND"
            + " plan_id=?",
        next,
        index,
        tenantId,
        planId);
  }

  public List<Map<String, Object>> lockOpenPlanTasks(long tenantId, Object planId) {
    return lockedTaskRows(
        "SELECT * FROM v_inspection_task WHERE tenant_id=? AND plan_id=? AND deleted=0 AND"
            + " status NOT IN ('已完成','已审核','已作废','过期作废') ORDER BY id FOR UPDATE",
        tenantId,
        planId);
  }

  public int activateTask(long tenantId, long id) {
    return db.update(
        "UPDATE eam_inspection_task SET status='待执行',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        id);
  }

  public int changeTaskStatus(Object next, long tenantId, long id) {
    return db.update(
        "UPDATE eam_inspection_task SET status=?,row_version=row_version+1 WHERE tenant_id=?"
            + " AND id=?",
        next,
        tenantId,
        id);
  }

  public int countEquipmentHistory(long tenantId, Object id, Object code) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_equipment_history WHERE tenant_id=? AND equipment_id=? AND"
            + " record_type='INSPECTION' AND record_no=?",
        Integer.class,
        tenantId,
        id,
        code);
  }

  public int insertEquipmentHistory(
      long recordId,
      long tenantId,
      Object id,
      Object code,
      String value,
      Object ownerId,
      Object status,
      Object summary) {
    return db.update(
        "INSERT INTO"
            + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
            + " VALUES(?,?,?,'INSPECTION',?,NOW(),?,?,?,?)",
        recordId,
        tenantId,
        id,
        code,
        value,
        ownerId,
        status,
        summary);
  }

  public int updateTask(
      Object next,
      Object owner,
      String payload,
      String payload4,
      Object value,
      long tenantId,
      long id) {
    executions.participants(tenantId, id, payload);
    executions.save(tenantId, id, payload4);
    return db.update(
        "UPDATE eam_inspection_task SET"
            + " status=?,owner_id=?,snapshot=?,row_version=row_version+1,deleted=?,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=?",
        next,
        owner,
        executions.historicalSnapshot(payload),
        value,
        tenantId,
        id);
  }

  public List<Map<String, Object>> generationTargets() {
    return db.queryForList(
        "SELECT tenant_id,id FROM v_inspection_definition WHERE kind IN"
            + " ('daily-plans','periodic-plans') ORDER BY tenant_id,id");
  }

  private List<Map<String, Object>> lockedTaskRows(String sql, Object... args) {
    db.queryForList(
        sql.replace("v_inspection_task", "eam_inspection_task").replace("SELECT *", "SELECT id"),
        args);
    return db.queryForList(sql.replace(" FOR UPDATE", ""), args);
  }
}
