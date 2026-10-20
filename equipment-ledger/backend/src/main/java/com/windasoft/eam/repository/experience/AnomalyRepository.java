package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AnomalyRepository {
  private final JdbcTemplate db;
  private final com.windasoft.eam.repository.common.AttachmentRelationsRepository attachments;
  private final EquipmentMapper equipment;
  private final NumberCounterMapper numbers;

  public Equipment findEquipment(long id) {
    return equipment.selectById(id);
  }

  public void nextMonthlyNumber(String rule, String period) {
    numbers.nextMonthlyNumber(rule, period);
  }

  public long currentNumber() {
    return numbers.currentValue();
  }

  public AnomalyRepository(
      JdbcTemplate db,
      EquipmentMapper equipment,
      NumberCounterMapper numbers,
      com.windasoft.eam.repository.common.AttachmentRelationsRepository attachments) {
    this.db = db;
    this.equipment = equipment;
    this.numbers = numbers;
    this.attachments = attachments;
  }

  public int insertEvent(
      long recordId,
      long tenantId,
      Object kind,
      Object id,
      Object action,
      long userId,
      Object value,
      String payload) {
    return db.update(
        "INSERT INTO"
            + " eam_experience_event(id,tenant_id,object_kind,object_id,action,actor_id,actor_name,details)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        recordId,
        tenantId,
        kind,
        id,
        action,
        userId,
        value,
        payload);
  }

  public Map<String, Object> loadAnomalyState(long tenantId, Object id) {
    return db.queryForMap(
        "SELECT status,discovered_by,description FROM v_anomaly WHERE tenant_id=?" + " AND id=?",
        tenantId,
        id);
  }

  public int syncAnomalyTodos(Object value, long tenantId, Object id) {
    return db.update(
        "UPDATE eam_experience_inbox SET done=? WHERE tenant_id=? AND object_kind='anomaly' AND"
            + " object_id=? AND is_todo=1",
        value,
        tenantId,
        id);
  }

  public Map<String, Object> loadPlanState(long tenantId, Object id) {
    return db.queryForMap(
        "SELECT status,owner_id,description FROM v_single_repair_plan WHERE tenant_id=? AND"
            + " id=?",
        tenantId,
        id);
  }

  public int syncPlanTodos(long tenantId, Object id, Object ownerId, Object value) {
    return db.update(
        "UPDATE eam_experience_inbox SET done=1 WHERE tenant_id=? AND object_kind='repairPlan'"
            + " AND object_id=? AND (user_id<>? OR ?<>'待生成') AND is_todo=1",
        tenantId,
        id,
        ownerId,
        value);
  }

  public int insertInbox(
      long recordId,
      long tenantId,
      Object kind,
      Object id,
      Object user,
      Object label,
      Object value,
      Object value8) {
    int count =
        db.update(
            "INSERT IGNORE INTO"
                + " eam_experience_inbox(id,tenant_id,object_kind,object_id,user_id,kind,title,is_todo)"
                + " VALUES(?,?,?,?,?,?,?,?)",
            recordId,
            tenantId,
            kind,
            id,
            user,
            label,
            value,
            value8);
    db.update(
        "UPDATE eam_experience_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key(label),
        com.windasoft.eam.common.NotificationMessages.arguments(label, value),
        tenantId,
        recordId);
    return count;
  }

  public List<Map<String, Object>> loadAnomaly(long tenantId, Object id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM v_anomaly WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
        tenantId,
        id);
  }

  public List<Map<String, Object>> searchAnomalies(long tenant, Long equipmentId) {
    return db.queryForList(
        "SELECT * FROM v_anomaly WHERE tenant_id=?"
            + (equipmentId == null ? "" : " AND equipment_id=?")
            + " ORDER BY discovered_at DESC,id DESC",
        equipmentId == null ? new Object[] {tenant} : new Object[] {tenant, equipmentId});
  }

  public Map<String, Object> findMaintenanceEquipment(long tenantId, Object task) {
    return db.queryForMap(
        "SELECT equipment_id FROM v_maintenance_task WHERE tenant_id=? AND" + " id=?",
        tenantId,
        task);
  }

  public List<Map<String, Object>> sourceAnomalies(long tenant, String kind, long task) {
    return db.queryForList(
        "SELECT * FROM v_anomaly WHERE tenant_id=? AND "
            + (kind.equals("维修") ? "repair_id=?" : "source_kind=? AND source_task_id=?")
            + " ORDER BY discovered_at,id",
        kind.equals("维修") ? new Object[] {tenant, task} : new Object[] {tenant, kind, task});
  }

  public List<Map<String, Object>> listEvents(long tenantId, Object kind, Object id) {
    return db.queryForList(
        "SELECT action,actor_name actor,created_at time,details FROM eam_experience_event WHERE"
            + " tenant_id=? AND object_kind=? AND object_id=? ORDER BY created_at,id",
        tenantId,
        kind,
        id);
  }

  public List<Map<String, Object>> findAttachment(long tenantId, long userId, Object fid) {
    return db.queryForList(
        "SELECT id,name,content_type contentType,size FROM eam_anomaly_file WHERE tenant_id=?"
            + " AND user_id=? AND id=?",
        tenantId,
        userId,
        fid);
  }

  public Map<String, Object> findCaptureEquipment(long tenantId, Object taskId) {
    return db.queryForMap(
        "SELECT equipment_id FROM v_maintenance_task WHERE tenant_id=? AND" + " id=?",
        tenantId,
        taskId);
  }

  public void captureOrganization(long tenant, long anomaly) {
    db.update(
        "UPDATE eam_equipment_anomaly a JOIN eam_equipment e ON e.tenant_id=a.tenant_id AND"
            + " e.id=a.equipment_id SET"
            + " a.company_org_id=e.company_org_id,a.department_org_id=e.department_org_id WHERE"
            + " a.tenant_id=? AND a.id=? AND a.company_org_id IS NULL",
        tenant,
        anomaly);
  }

  public int insertAnomaly(
      Object aid,
      long tenantId,
      Object value,
      Object eqId,
      Object kind,
      Object value6,
      Object value7,
      Object description,
      Object severity,
      String payload,
      long userId,
      Object value12,
      Object value13) {
    int changed =
        db.update(
            "INSERT INTO"
                + " eam_equipment_anomaly(id,tenant_id,code,equipment_id,source_kind,source_task_id,source_item_id,description,severity,discovered_by,discovered_name,discovered_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
            aid,
            tenantId,
            value,
            eqId,
            kind,
            value6,
            value7,
            description,
            severity,
            userId,
            value12,
            value13);
    if (changed > 0)
      attachments.save(
          com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.ANOMALY,
          tenantId,
          Long.parseLong(aid.toString()),
          payload);
    return changed;
  }

  public Map<String, Object> findAnomalyBySource(
      long tenantId, Object kind, Object taskId, Object eqId, Object itemId) {
    return db.queryForMap(
        "SELECT id FROM v_anomaly WHERE tenant_id=? AND source_kind=? AND"
            + " source_task_id=? AND equipment_id=? AND source_item_id=?",
        tenantId,
        kind,
        taskId,
        eqId,
        itemId);
  }

  public int updateEvidence(String payload, long tenantId, Object aid) {
    attachments.save(
        com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.ANOMALY,
        tenantId,
        Long.parseLong(aid.toString()),
        payload);
    return 1;
  }

  public int resolveAnomaly(Object value, Object reason, Object value3, long tenantId, Object aid) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET"
            + " status=?,resolution=?,resolved_at=?,row_version=row_version+1 WHERE tenant_id=?"
            + " AND id=?",
        value,
        reason,
        value3,
        tenantId,
        aid);
  }

  public int linkRepair(Object repair, long tenantId, Object aid) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET repair_id=?,status='已安排',row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        repair,
        tenantId,
        aid);
  }

  public List<Map<String, Object>> listPlanAnomalies(long tenantId, Object id) {
    return db.queryForList(
        "SELECT * FROM v_anomaly WHERE tenant_id=? AND" + " plan_id=? ORDER BY id", tenantId, id);
  }

  public List<Map<String, Object>> lockRepairAnomalies(long tenantId, Object value) {
    return db.queryForList(
        "SELECT * FROM v_anomaly WHERE tenant_id=? AND repair_id=? AND status NOT"
            + " IN ('已消除','已作废') ORDER BY id FOR UPDATE",
        tenantId,
        value);
  }

  public int updateRepairAnomaly(
      Object status,
      Object value,
      Object value3,
      Object status4,
      Object status5,
      long tenantId,
      Object id) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET"
            + " status=?,resolved_at=?,repair_id=?,plan_id=IF(?='待处理',NULL,plan_id),resolution=IF(?='已消除','关联维修已验收通过',resolution),row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        status,
        value,
        value3,
        status4,
        status5,
        tenantId,
        id);
  }

  public List<Map<String, Object>> findSourceActor(
      long tenantId, Object task, Object value, String kind) {
    return db.queryForList(
        "SELECT actor_id,actor_name,created_at FROM "
            + (kind.equals("保养") ? "eam_maintenance_event" : "eam_inspection_event")
            + " WHERE tenant_id=? AND task_id=? AND action=? ORDER BY created_at,id LIMIT 1",
        tenantId,
        task,
        value);
  }

  public Map<String, Object> findBackfillEquipment(long tenantId, Object task) {
    return db.queryForMap(
        "SELECT equipment_id FROM v_maintenance_task WHERE tenant_id=? AND" + " id=?",
        tenantId,
        task);
  }

  public int insertCapturedAnomaly(
      Object aid,
      long tenantId,
      Object value,
      Object eid,
      Object kind,
      Object task,
      Object key,
      Object value8,
      String payload,
      Object discoveredBy,
      Object discoveredName,
      Object discoveredAt,
      Object resolved,
      Object value14,
      Object resolved15) {
    int changed =
        db.update(
            "INSERT IGNORE INTO"
                + " eam_equipment_anomaly(id,tenant_id,code,equipment_id,source_kind,source_task_id,source_item_id,description,discovered_by,discovered_name,discovered_at,status,resolution,resolved_at)"
                + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
            aid,
            tenantId,
            value,
            eid,
            kind,
            task,
            key,
            value8,
            discoveredBy,
            discoveredName,
            discoveredAt,
            resolved,
            value14,
            resolved15);
    if (changed > 0)
      attachments.save(
          com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.ANOMALY,
          tenantId,
          Long.parseLong(aid.toString()),
          payload);
    return changed;
  }

  public List<Map<String, Object>> loadPlan(long tenantId, Object id, boolean lock) {
    return db.queryForList(
        "SELECT * FROM v_single_repair_plan WHERE tenant_id=? AND id=?"
            + (lock ? " FOR UPDATE" : ""),
        tenantId,
        id);
  }

  public int countPersonalPlans(long tenantId, long userId, long userId3) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM v_single_repair_plan WHERE tenant_id=? AND (owner_id=? OR"
            + " creator_id=?)",
        Integer.class,
        tenantId,
        userId,
        userId3);
  }

  public List<Map<String, Object>> listPlans(long tenantId) {
    return db.queryForList(
        "SELECT * FROM v_single_repair_plan WHERE tenant_id=? ORDER BY planned_at,id", tenantId);
  }

  public List<Map<String, Object>> listPlanAnomalyIds(long tenantId, Object id) {
    return db.queryForList(
        "SELECT CAST(id AS CHAR) id FROM v_anomaly WHERE tenant_id=? AND" + " plan_id=?",
        tenantId,
        id);
  }

  public List<Map<String, Object>> lockGenerationFailure(long tenantId, Object plan) {
    return db.queryForList(
        "SELECT owner_id,generation_error FROM v_single_repair_plan WHERE tenant_id=? AND"
            + " id=? AND status='待生成' FOR UPDATE",
        tenantId,
        plan);
  }

  public int storeGenerationError(Object message, long tenantId, Object plan) {
    return db.update(
        "UPDATE eam_single_repair_plan SET generation_error=? WHERE tenant_id=? AND id=?",
        message,
        tenantId,
        plan);
  }

  public int insertFailureEvent(long recordId, long tenantId, Object plan, String payload) {
    return db.update(
        "INSERT INTO"
            + " eam_experience_event(id,tenant_id,object_kind,object_id,action,actor_id,actor_name,detail)"
            + " VALUES(?,?,'repairPlan',?,'生成失败',0,'系统',?)",
        recordId,
        tenantId,
        plan,
        payload);
  }

  public int insertFailureNotice(
      long recordId, long tenantId, Object plan, Object ownerId, Object value) {
    int count =
        db.update(
            "INSERT INTO"
                + " eam_experience_inbox(id,tenant_id,object_kind,object_id,user_id,kind,title,is_todo)"
                + " VALUES(?,?,'repairPlan',?,?,'生成失败',?,0) ON DUPLICATE KEY UPDATE"
                + " title=VALUES(title),read_at=NULL",
            recordId,
            tenantId,
            plan,
            ownerId,
            value);
    db.update(
        "UPDATE eam_experience_inbox SET message_key=?,message_args=? WHERE tenant_id=? AND id=?",
        com.windasoft.eam.common.NotificationMessages.key("生成失败"),
        com.windasoft.eam.common.NotificationMessages.arguments("生成失败", value),
        tenantId,
        recordId);
    return count;
  }

  public int countActiveUser(long tenantId, Object owner) {
    return db.queryForObject(
        "SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND status=1 AND"
            + " deleted=0",
        Integer.class,
        tenantId,
        owner);
  }

  public int insertPlan(
      Object pid,
      long tenantId,
      Object value,
      Object eqId,
      Object description,
      Object planned,
      Object owner,
      long userId,
      Object fault,
      Object severity) {
    return db.update(
        "INSERT INTO"
            + " eam_single_repair_plan(id,tenant_id,code,equipment_id,description,planned_at,owner_id,creator_id,fault_type,severity)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        pid,
        tenantId,
        value,
        eqId,
        description,
        planned,
        owner,
        userId,
        fault,
        severity);
  }

  public int updatePlan(
      Object description,
      Object planned,
      Object owner,
      Object fault,
      Object severity,
      long tenantId,
      Object pid) {
    return db.update(
        "UPDATE eam_single_repair_plan SET"
            + " description=?,planned_at=?,owner_id=?,fault_type=?,severity=?,row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        description,
        planned,
        owner,
        fault,
        severity,
        tenantId,
        pid);
  }

  public void setPlanFaultTypeId(long tenant, long id, long type) {
    db.update(
        "UPDATE eam_single_repair_plan SET fault_type_id=? WHERE tenant_id=? AND id=?",
        type,
        tenant,
        id);
  }

  public List<Map<String, Object>> lockPlanAnomalyIds(long tenantId, Object pid) {
    return db.queryForList(
        "SELECT id FROM v_anomaly WHERE tenant_id=? AND plan_id=? ORDER BY id FOR" + " UPDATE",
        tenantId,
        pid);
  }

  public int detachPlanAnomaly(long tenantId, Object aid) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET plan_id=NULL,status='待处理',row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        tenantId,
        aid);
  }

  public int attachPlanAnomaly(Object pid, long tenantId, Object aid) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET plan_id=?,status='已安排',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        pid,
        tenantId,
        aid);
  }

  public int cancelPlan(long tenantId, Object pid) {
    return db.update(
        "UPDATE eam_single_repair_plan SET status='已取消',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        pid);
  }

  public List<Map<String, Object>> lockCancelledAnomalies(long tenantId, Object pid) {
    return db.queryForList(
        "SELECT id FROM v_anomaly WHERE tenant_id=? AND plan_id=? AND repair_id"
            + " IS NULL ORDER BY id FOR UPDATE",
        tenantId,
        pid);
  }

  public int detachCancelledAnomaly(long tenantId, Object id) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET plan_id=NULL,status='待处理',row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        tenantId,
        id);
  }

  public int reopenPlan(long tenantId, Object pid) {
    return db.update(
        "UPDATE eam_single_repair_plan SET status='待生成',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        tenantId,
        pid);
  }

  public int finishGeneration(Object repair, long tenantId, Object pid) {
    return db.update(
        "UPDATE eam_single_repair_plan SET"
            + " status='已生成',repair_id=?,generation_error='',row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        repair,
        tenantId,
        pid);
  }

  public List<Map<String, Object>> lockPendingAnomalies(long tenantId, Object pid) {
    return db.queryForList(
        "SELECT id FROM v_anomaly WHERE tenant_id=? AND plan_id=? AND repair_id IS"
            + " NULL ORDER BY id FOR UPDATE",
        tenantId,
        pid);
  }

  public int linkGeneratedRepair(Object repair, long tenantId, Object id) {
    return db.update(
        "UPDATE eam_equipment_anomaly SET repair_id=?,status='已安排',row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        repair,
        tenantId,
        id);
  }

  public List<Map<String, Object>> listOwnerChoices(long tenantId) {
    return db.queryForList(
        "SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'')"
            + " department,u.org_id orgId,(SELECT COUNT(*) FROM eam_repair_order r WHERE"
            + " r.tenant_id=u.tenant_id AND r.assignee_user_id=u.id AND r.deleted=0 AND"
            + " r.status NOT IN ('已完成','已撤销')) unfinished FROM eam_sys_user u LEFT JOIN"
            + " eam_org_unit o ON o.tenant_id=u.tenant_id AND o.id=u.org_id WHERE"
            + " u.tenant_id=? AND u.status=1 AND u.deleted=0 ORDER BY u.display_name,u.id",
        tenantId);
  }
}
