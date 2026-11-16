package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskStatus;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskData;
import com.windasoft.eam.entity.maintenance.MaintenanceTask;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 任务主体的唯一写入归属；关系视图只作历史读取，不协调人员、执行及通知的跨表写入。 */
@Repository
public class MaintenanceTaskRepository {
  private final MaintenanceTaskMapper mapper;
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public MaintenanceTaskRepository(
      MaintenanceTaskMapper mapper, JdbcTemplate db, ObjectMapper json) {
    this.mapper = mapper;
    this.db = db;
    this.json = json;
  }

  public int insertTask(MaintenanceTask row) {
    return mapper.insertIfAbsent(row);
  }

  /** 动作用例已加任务行锁；先按旧状态更新提交时间，再推进状态，保留原MySQL赋值顺序。 */
  public int updateTask(String next, Long owner, String snapshot, long tenant, long task) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceTask>()
            .eq(MaintenanceTask::getTenantId, tenant)
            .eq(MaintenanceTask::getId, task)
            .setSql(
                "submitted_at=IF({0}={1} AND status<>{1},CURRENT_TIMESTAMP(6),submitted_at)",
                next, MaintenanceTaskStatus.PENDING_ACCEPTANCE.code())
            .set(MaintenanceTask::getStatus, next)
            .set(MaintenanceTask::getOwnerId, owner)
            .set(MaintenanceTask::getSnapshot, snapshot)
            .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int softDeleteTask(long tenant, long task, long actor) {
    return mapper.update(
        null,
        active(tenant)
            .eq(MaintenanceTask::getId, task)
            .set(MaintenanceTask::getDeleted, true)
            .set(MaintenanceTask::getDeletedBy, actor)
            .setSql(
                "deleted_at=CURRENT_TIMESTAMP(6),row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int postponeTask(long tenant, long task, LocalDate date) {
    return mapper.update(
        null,
        active(tenant)
            .eq(MaintenanceTask::getId, task)
            .set(MaintenanceTask::getExpiresDate, date)
            .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int expireTask(long tenant, long task) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<MaintenanceTask>()
            .eq(MaintenanceTask::getTenantId, tenant)
            .eq(MaintenanceTask::getId, task)
            .set(MaintenanceTask::getStatus, MaintenanceTaskStatus.EXPIRED_CANCELLED.code())
            .setSql("row_version=row_version+1"));
  }

  public long countPlanTasks(long tenant, long plan) {
    return mapper.selectCount(
        new LambdaQueryWrapper<MaintenanceTask>()
            .eq(MaintenanceTask::getTenantId, tenant)
            .eq(MaintenanceTask::getPlanId, plan)
            .eq(MaintenanceTask::getDeleted, false));
  }

  public long taskTypeReferences(long tenant, long type) {
    return mapper.selectCount(
        new LambdaQueryWrapper<MaintenanceTask>()
            .eq(MaintenanceTask::getTenantId, tenant)
            .eq(MaintenanceTask::getTypeId, type)
            .eq(MaintenanceTask::getDeleted, false));
  }

  public long countOpenScheduleTasks(long tenant, long schedule) {
    return mapper.selectCount(
        new LambdaQueryWrapper<MaintenanceTask>()
            .eq(MaintenanceTask::getTenantId, tenant)
            .eq(MaintenanceTask::getScheduleId, schedule)
            .eq(MaintenanceTask::getDeleted, false)
            .notIn(
                MaintenanceTask::getStatus,
                List.of(
                    MaintenanceTaskStatus.COMPLETED.code(),
                    MaintenanceTaskStatus.CANCELLED.code(),
                    MaintenanceTaskStatus.EXPIRED_CANCELLED.code())));
  }

  private LambdaUpdateWrapper<MaintenanceTask> active(long tenant) {
    return new LambdaUpdateWrapper<MaintenanceTask>()
        .eq(MaintenanceTask::getTenantId, tenant)
        .eq(MaintenanceTask::getDeleted, false);
  }

  public LocalDate latestDueDate(long tenant, long schedule) {
    var rows =
        mapper.selectList(
            new LambdaQueryWrapper<MaintenanceTask>()
                .select(MaintenanceTask::getDueDate)
                .eq(MaintenanceTask::getTenantId, tenant)
                .eq(MaintenanceTask::getScheduleId, schedule)
                .eq(MaintenanceTask::getDeleted, false)
                .orderByDesc(MaintenanceTask::getDueDate)
                .last("LIMIT 1"));
    return rows.isEmpty() ? null : rows.getFirst().getDueDate();
  }

  public List<MaintenanceTaskData> loadTask(long tenantId, long id, boolean lock) {
    if (lock) {
      mapper.lockIncludingDeleted(tenantId, id);
    }
    return db
        .queryForList("SELECT * FROM v_maintenance_task WHERE tenant_id=? AND id=?", tenantId, id)
        .stream()
        .map(row -> taskData(row, json))
        .toList();
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

  /** 存在完成任务但没有执行事实时返回空模型；不存在任务时返回empty，保留旧预填差异。 */
  public Optional<MaintenanceExecutionResult> findLastCompletedResult(
      long tenant, long equipment, long schedule) {
    var rows =
        db.query(
            """
            SELECT result FROM v_maintenance_task
            WHERE tenant_id=? AND equipment_id=? AND schedule_id=? AND status=?
            ORDER BY updated_at DESC,id DESC LIMIT 1
            """,
            (row, index) -> row.getString("result"),
            tenant,
            equipment,
            schedule,
            MaintenanceTaskStatus.COMPLETED.code());
    if (rows.isEmpty()) {
      return Optional.empty();
    }
    try {
      return Optional.of(
          rows.getFirst() == null
              ? new MaintenanceExecutionResult()
              : json.readValue(rows.getFirst(), MaintenanceExecutionResult.class));
    } catch (JsonProcessingException e) {
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
  }

  /** 原锁集合包括同条件的已删除任务，仍予锁定；只对未删除记录产生过期动作。 */
  public List<Long> lockExpiredTasks(long tenant, LocalDate today) {
    return mapper
        .lockExpiredIncludingDeleted(tenant, today, MaintenanceTaskStatus.PENDING.code())
        .stream()
        .filter(row -> !Boolean.TRUE.equals(row.getDeleted()))
        .map(MaintenanceTask::getId)
        .toList();
  }

  public List<MaintenanceTaskData> listPendingTasks(long tenantId) {
    return db
        .queryForList(
            "SELECT * FROM v_maintenance_task WHERE tenant_id=? AND status=?",
            tenantId,
            MaintenanceTaskStatus.PENDING.code())
        .stream()
        .map(row -> taskData(row, json))
        .toList();
  }

  /** 仅在JDBC行映射边界转换固定结构，Service不接收字符串列名和任意值。 */
  static MaintenanceTaskData taskData(Map<String, Object> row, ObjectMapper json) {
    try {
      return new MaintenanceTaskData(
          id(row, "id"),
          id(row, "tenant_id"),
          (String) row.get("code"),
          id(row, "plan_id"),
          id(row, "schedule_id"),
          id(row, "equipment_id"),
          id(row, "type_id"),
          id(row, "company_id"),
          id(row, "department_id"),
          id(row, "owner_id"),
          (String) row.get("status"),
          ((Number) row.get("row_version")).intValue(),
          date(row.get("due_date")),
          date(row.get("expires_date")),
          dateTime(row.get("created_at")),
          (ObjectNode) json.readTree(row.get("snapshot").toString()),
          row.get("result") == null
              ? null
              : json.readValue(row.get("result").toString(), MaintenanceExecutionResult.class),
          (String) row.get("department"),
          (String) row.get("owner"),
          row.containsKey("department"),
          row.containsKey("owner"));
    } catch (JsonProcessingException e) {
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
  }

  private static Long id(Map<String, Object> row, String key) {
    return row.get(key) == null ? null : ((Number) row.get(key)).longValue();
  }

  private static LocalDate date(Object value) {
    return value == null
        ? null
        : value instanceof LocalDate date ? date : ((Date) value).toLocalDate();
  }

  private static LocalDateTime dateTime(Object value) {
    return value == null
        ? null
        : value instanceof LocalDateTime time ? time : ((Timestamp) value).toLocalDateTime();
  }
}
