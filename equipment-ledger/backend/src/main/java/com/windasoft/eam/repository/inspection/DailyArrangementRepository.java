package com.windasoft.eam.repository.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DailyArrangementRepository {
  private final JdbcTemplate db;

  public DailyArrangementRepository(JdbcTemplate db) {
    this.db = db;
  }

  private boolean flag(Object v) {
    return Boolean.TRUE.equals(v)
        || "1".equals(Objects.toString(v))
        || "true".equals(Objects.toString(v));
  }

  public List<Map<String, Object>> load(long tenant, long plan) {
    var result =
        db.queryForList(
            "SELECT effective_from effectiveFrom,id,name,enabled,frequency,interval_count"
                + " intervalCount,anchor_date anchorDate,start_time startTime,end_time"
                + " endTime,window_minutes windowMinutes,person_mode personMode,team_id"
                + " teamId,follow_up_user_id followUpUserId,event_type eventType,allow_late"
                + " allowLate FROM eam_inspection_daily_arrangement WHERE tenant_id=? AND plan_id=?"
                + " ORDER BY sort_no,id",
            tenant,
            plan);
    for (var r : result) {
      long id = number(r.get("id"));
      for (String key : List.of("id", "anchorDate", "startTime", "endTime", "followUpUserId"))
        r.put(key, Objects.toString(r.get(key), ""));
      r.put("teamId", Objects.toString(r.get("teamId"), ""));
      r.put("enabled", flag(r.get("enabled")));
      r.put("allowLate", flag(r.get("allowLate")));
      r.put("regularRoster", staffing(tenant, id, false));
      r.put("dateRoster", staffing(tenant, id, true));
      r.put(
          "days",
          db.queryForList(
              "SELECT day_no FROM eam_inspection_daily_arrangement_day WHERE tenant_id=? AND"
                  + " arrangement_id=? ORDER BY day_no",
              Integer.class,
              tenant,
              id));
      r.put(
          "slots",
          db.queryForList(
              "SELECT slot_key id,name,CAST(start_time AS CHAR) start,CAST(end_time AS CHAR) end"
                  + " FROM eam_inspection_daily_arrangement_slot WHERE tenant_id=? AND"
                  + " arrangement_id=? ORDER BY sort_no",
              tenant,
              id));
      r.put(
          "owners",
          db.queryForList(
              "SELECT CAST(user_id AS CHAR) FROM eam_inspection_daily_arrangement_person WHERE"
                  + " tenant_id=? AND arrangement_id=? ORDER BY sort_no",
              String.class,
              tenant,
              id));
      r.put(
          "equipmentIds",
          db.queryForList(
              "SELECT CAST(equipment_id AS CHAR) FROM eam_inspection_daily_arrangement_equipment"
                  + " WHERE tenant_id=? AND arrangement_id=? ORDER BY equipment_id",
              String.class,
              tenant,
              id));
      r.put(
          "projectRefs",
          db.queryForList(
              "SELECT CAST(standard_id AS CHAR) standardId,item_key itemId FROM"
                  + " eam_inspection_daily_arrangement_project WHERE tenant_id=? AND"
                  + " arrangement_id=? ORDER BY standard_id,item_key",
              tenant,
              id));
    }
    return result;
  }

  public List<String> workdays(long tenant, long plan) {
    return db.queryForList(
        "SELECT CAST(work_date AS CHAR) FROM eam_inspection_daily_workday WHERE tenant_id=? AND"
            + " plan_id=? ORDER BY work_date",
        String.class,
        tenant,
        plan);
  }

  public void save(
      long tenant, long plan, List<Map<String, Object>> arrangements, Object workdays) {
    db.update(
        "DELETE FROM eam_inspection_daily_arrangement WHERE tenant_id=? AND plan_id=?",
        tenant,
        plan);
    db.update(
        "DELETE FROM eam_inspection_daily_workday WHERE tenant_id=? AND plan_id=?", tenant, plan);
    for (String d : strings(workdays))
      db.update("INSERT INTO eam_inspection_daily_workday VALUES(?,?,?)", tenant, plan, d);
    int order = 0;
    for (var a : arrangements) {
      long id = number(a.get("id"));
      db.update(
          "INSERT INTO"
              + " eam_inspection_daily_arrangement(id,tenant_id,plan_id,name,sort_no,enabled,frequency,interval_count,anchor_date,start_time,end_time,window_minutes,person_mode,team_id,follow_up_user_id,event_type,allow_late)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          id,
          tenant,
          plan,
          text(a, "name"),
          order++,
          yes(a.get("enabled")),
          text(a, "frequency"),
          number(a.get("intervalCount")),
          text(a, "anchorDate"),
          text(a, "startTime"),
          text(a, "endTime"),
          number(a.get("windowMinutes")),
          text(a, "personMode"),
          text(a, "teamId").isBlank() ? null : number(a.get("teamId")),
          text(a, "followUpUserId").isBlank() ? null : number(a.get("followUpUserId")),
          text(a, "eventType"),
          yes(a.get("allowLate")));
      saveStaffing(tenant, id, rows(a.get("regularRoster")), false);
      saveStaffing(tenant, id, rows(a.get("dateRoster")), true);
      for (long d : ids(a.get("days")))
        db.update("INSERT INTO eam_inspection_daily_arrangement_day VALUES(?,?,?)", tenant, id, d);
      int at = 0;
      for (var s : rows(a.get("slots")))
        db.update(
            "INSERT INTO eam_inspection_daily_arrangement_slot VALUES(?,?,?,?,?,?,?)",
            tenant,
            id,
            text(s, "id"),
            text(s, "name"),
            text(s, "start"),
            text(s, "end"),
            at++);
      at = 0;
      for (long u : ids(a.get("owners")))
        db.update(
            "INSERT INTO eam_inspection_daily_arrangement_person VALUES(?,?,?,?)",
            tenant,
            id,
            u,
            at++);
      for (long e : ids(a.get("equipmentIds")))
        db.update(
            "INSERT INTO eam_inspection_daily_arrangement_equipment VALUES(?,?,?)", tenant, id, e);
      for (var p : rows(a.get("projectRefs")))
        db.update(
            "INSERT INTO eam_inspection_daily_arrangement_project VALUES(?,?,?,?)",
            tenant,
            id,
            number(p.get("standardId")),
            text(p, "itemId"));
    }
  }

  public Long ownerPlan(long tenant, long id) {
    var rows =
        db.queryForList(
            "SELECT plan_id FROM eam_inspection_daily_arrangement WHERE tenant_id=? AND id=?",
            Long.class,
            tenant,
            id);
    return rows.isEmpty() ? null : rows.getFirst();
  }

  public boolean belongs(long tenant, long plan, long arrangement) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_daily_arrangement WHERE tenant_id=? AND id=? AND"
                + " plan_id=?",
            Long.class,
            tenant,
            arrangement,
            plan)
        > 0;
  }

  public List<Long> teamMembers(long tenant, long id) {
    return db.queryForList(
        "SELECT p.user_id FROM eam_inspection_operation_team_person p JOIN eam_sys_user u ON"
            + " u.id=p.user_id AND u.tenant_id=p.tenant_id WHERE p.tenant_id=? AND p.team_id=? AND"
            + " u.status=1 ORDER BY p.sort_no",
        Long.class,
        tenant,
        id);
  }

  public List<Map<String, Object>> teams(long tenant) {
    return db.queryForList(
        "SELECT id,name,department_id departmentId,created_by creatorId,row_version rowVersion FROM"
            + " eam_inspection_operation_team WHERE tenant_id=? ORDER BY name,id",
        tenant);
  }

  public Map<String, Object> lockTeam(long tenant, long id) {
    var rows =
        db.queryForList(
            "SELECT id,department_id,created_by,row_version FROM eam_inspection_operation_team"
                + " WHERE tenant_id=? AND id=? FOR UPDATE",
            tenant,
            id);
    return rows.isEmpty() ? Map.of() : rows.getFirst();
  }

  public void updateTeam(long tenant, long id, String name, List<Long> users) {
    db.update(
        "UPDATE eam_inspection_operation_team SET name=?,row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        name,
        tenant,
        id);
    db.update(
        "DELETE FROM eam_inspection_operation_team_person WHERE tenant_id=? AND team_id=?",
        tenant,
        id);
    int at = 0;
    for (long user : users)
      db.update(
          "INSERT INTO eam_inspection_operation_team_person VALUES(?,?,?,?)",
          tenant,
          id,
          user,
          at++);
  }

  public void createTeam(
      long tenant, long id, String name, long department, long creator, List<Long> users) {
    db.update(
        "INSERT INTO eam_inspection_operation_team(id,tenant_id,name,department_id,created_by)"
            + " VALUES(?,?,?,?,?)",
        id,
        tenant,
        name,
        department,
        creator);
    int at = 0;
    for (long u : users)
      db.update(
          "INSERT INTO eam_inspection_operation_team_person VALUES(?,?,?,?)", tenant, id, u, at++);
  }

  public List<Map<String, Object>> activePeople(long tenant) {
    return db.queryForList(
        "SELECT id,org_id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0", tenant);
  }

  public void taskMetadata(
      long tenant, long task, Map<String, Object> a, String businessDate, String eventKey) {
    db.update(
        "UPDATE eam_inspection_task SET"
            + " arrangement_id=?,arrangement_name=?,business_date=?,follow_up_user_id=?,event_key=?,source_assignment_mode=?,source_team_id=?"
            + " WHERE tenant_id=? AND id=?",
        number(a.get("id")),
        text(a, "name"),
        businessDate,
        number(a.get("followUpUserId")),
        eventKey,
        text(a, "personMode"),
        text(a, "teamId").isBlank() ? null : number(a.get("teamId")),
        tenant,
        task);
  }

  public boolean occurrenceExists(
      long tenant, long plan, long equipment, String key, java.time.LocalDateTime due) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND plan_id=? AND"
                + " equipment_id=? AND shift_id=? AND due_at=?",
            Long.class,
            tenant,
            plan,
            equipment,
            key,
            due)
        > 0;
  }

  public List<Map<String, Object>> exemptions(long tenant, long plan) {
    return db.queryForList(
        "SELECT id,equipment_id equipmentId,start_at startAt,end_at endAt,reason,approved_by"
            + " approvedBy,approved_at approvedAt FROM eam_inspection_daily_exemption WHERE"
            + " tenant_id=? AND plan_id=? ORDER BY approved_at DESC,id DESC",
        tenant,
        plan);
  }

  public void addExemption(
      long tenant,
      long id,
      long plan,
      long equipment,
      String start,
      String end,
      String reason,
      long actor) {
    db.update(
        "INSERT INTO"
            + " eam_inspection_daily_exemption(id,tenant_id,plan_id,equipment_id,start_at,end_at,reason,approved_by)"
            + " VALUES(?,?,?,?,?,?,?,?)",
        id,
        tenant,
        plan,
        equipment,
        start.replace('T', ' '),
        end.replace('T', ' '),
        reason,
        actor);
  }

  public List<Map<String, Object>> exemptionCandidates(
      long tenant, long plan, long equipment, String start, String end) {
    db.queryForList(
        "SELECT id FROM eam_inspection_task WHERE tenant_id=? AND plan_id=? AND equipment_id=? AND"
            + " due_at>=? AND due_at<? AND status IN ('未开始','待执行','已过期') AND owner_id IS NULL ORDER"
            + " BY id FOR UPDATE",
        tenant,
        plan,
        equipment,
        start.replace('T', ' '),
        end.replace('T', ' '));
    return db.queryForList(
        "SELECT id,row_version rowVersion FROM eam_inspection_task WHERE tenant_id=? AND plan_id=?"
            + " AND equipment_id=? AND due_at>=? AND due_at<? AND status IN ('未开始','待执行','已过期') AND"
            + " owner_id IS NULL ORDER BY id",
        tenant,
        plan,
        equipment,
        start.replace('T', ' '),
        end.replace('T', ' '));
  }

  public void submission(long tenant, long task, long actor, Map<String, Object> result) {
    long id = com.baomidou.mybatisplus.core.toolkit.IdWorker.getId();
    db.update(
        "INSERT INTO eam_inspection_daily_submission VALUES(?,?,?,?,?,?)",
        id,
        tenant,
        task,
        actor,
        text(result, "finishedAt").replace('T', ' '),
        text(result, "description"));
    for (var answer : rows(result.get("items"))) {
      String key = text(answer, "id");
      db.update(
          "INSERT INTO eam_inspection_daily_submission_item VALUES(?,?,?,?,?,?,?,?,?)",
          tenant,
          id,
          key,
          text(answer, "value"),
          yes(answer.get("abnormal")),
          yes(answer.get("skipped")),
          yes(answer.get("resolved")),
          text(answer, "measure"),
          text(answer, "remark"));
      submissionFiles(tenant, id, key, answer.get("images"));
    }
    submissionFiles(tenant, id, "", result.get("attachments"));
  }

  private void submissionFiles(long tenant, long id, String key, Object files) {
    int at = 0;
    for (var file : rows(files))
      db.update(
          "INSERT INTO eam_inspection_daily_submission_file VALUES(?,?,?,?,?)",
          tenant,
          id,
          key,
          at++,
          text(file, "id"));
  }

  public void submissionReview(
      long tenant, long task, long actor, String decision, String description) {
    var submissions =
        db.queryForList(
            "SELECT id FROM eam_inspection_daily_submission WHERE tenant_id=? AND task_id=? ORDER"
                + " BY submitted_at DESC,id DESC LIMIT 1",
            Long.class,
            tenant,
            task);
    if (!submissions.isEmpty())
      db.update(
          "INSERT INTO eam_inspection_daily_submission_review VALUES(?,?,?,?,?,?,NOW(6))",
          com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
          tenant,
          submissions.getFirst(),
          actor,
          decision,
          description);
  }

  public List<Map<String, Object>> submissions(long tenant, long task) {
    return db.queryForList(
        "SELECT CAST(s.id AS CHAR) id,CAST(s.submitted_by AS CHAR) submittedBy,s.submitted_at"
            + " submittedAt,s.description,r.decision,r.description reviewDescription,r.reviewed_at"
            + " reviewedAt FROM eam_inspection_daily_submission s LEFT JOIN"
            + " eam_inspection_daily_submission_review r ON r.tenant_id=s.tenant_id AND"
            + " r.submission_id=s.id WHERE s.tenant_id=? AND s.task_id=? ORDER BY s.submitted_at"
            + " DESC,s.id DESC",
        tenant,
        task);
  }

  public void scheduledExemption(long tenant, long task, Map<String, Object> record) {
    db.update(
        "UPDATE eam_inspection_task SET exemption_reason=?,exempted_by=?,exempted_at=? WHERE"
            + " tenant_id=? AND id=?",
        text(record, "reason"),
        number(record.get("approvedBy")),
        record.get("approvedAt"),
        tenant,
        task);
  }

  public void exempt(long tenant, long task, long actor, String reason) {
    db.update(
        "UPDATE eam_inspection_task SET exemption_reason=?,exempted_by=?,exempted_at=NOW(6) WHERE"
            + " tenant_id=? AND id=?",
        reason,
        actor,
        tenant,
        task);
  }

  private List<Map<String, Object>> staffing(long tenant, long arrangement, boolean dates) {
    String table =
        dates ? "eam_inspection_daily_staff_override" : "eam_inspection_daily_staff_rule";
    var result =
        db.queryForList(
            "SELECT rule_key `key`,slot_key slotId,CAST(start_time AS CHAR) start,CAST(end_time AS"
                + " CHAR) end,unassigned,"
                + (dates ? "CAST(work_date AS CHAR) date" : "weekday_no weekday")
                + " FROM "
                + table
                + " WHERE tenant_id=? AND arrangement_id=? ORDER BY "
                + (dates ? "work_date" : "weekday_no")
                + ",slot_key,start_time,rule_key",
            tenant,
            arrangement);
    for (var row : result) {
      row.put("unassigned", flag(row.get("unassigned")));
      row.put(
          "users",
          db.queryForList(
              "SELECT CAST(user_id AS CHAR) FROM "
                  + table
                  + "_person WHERE tenant_id=? AND arrangement_id=? AND rule_key=? ORDER BY"
                  + " sort_no,user_id",
              String.class,
              tenant,
              arrangement,
              row.get("key")));
    }
    return result;
  }

  private void saveStaffing(
      long tenant, long arrangement, List<Map<String, Object>> rows, boolean dates) {
    String table =
        dates ? "eam_inspection_daily_staff_override" : "eam_inspection_daily_staff_rule";
    for (var row : rows) {
      db.update(
          "INSERT INTO "
              + table
              + "(tenant_id,arrangement_id,rule_key,"
              + (dates ? "work_date" : "weekday_no")
              + ",slot_key,start_time,end_time,unassigned) VALUES(?,?,?,?,?,?,?,?)",
          tenant,
          arrangement,
          text(row, "key"),
          dates ? text(row, "date") : number(row.get("weekday")),
          text(row, "slotId"),
          text(row, "start"),
          text(row, "end"),
          yes(row.get("unassigned")));
      int i = 0;
      for (long user : ids(row.get("users")))
        db.update(
            "INSERT INTO "
                + table
                + "_person(tenant_id,arrangement_id,rule_key,user_id,sort_no) VALUES(?,?,?,?,?)",
            tenant,
            arrangement,
            text(row, "key"),
            user,
            i++);
    }
  }

  public void reassignPending(
      long tenant, long task, List<Long> owners, String mode, Long followUp) {
    db.update(
        "DELETE FROM eam_inspection_task_person WHERE tenant_id=? AND task_id=? AND"
            + " role_code='owner'",
        tenant,
        task);
    int i = 0;
    for (long user : owners)
      db.update(
          "INSERT INTO eam_inspection_task_person VALUES(?,?,'owner',?,?)",
          tenant,
          task,
          user,
          i++);
    db.update(
        "UPDATE eam_inspection_task SET"
            + " person_mode=?,follow_up_user_id=?,row_version=row_version+1,updated_at=NOW(6) WHERE"
            + " tenant_id=? AND id=? AND owner_id IS NULL AND status IN ('未开始','待执行','已过期')",
        mode,
        followUp,
        tenant,
        task);
  }

  public boolean pendingScheduleTasks(long tenant, long arrangement) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM eam_inspection_task WHERE tenant_id=? AND arrangement_id=? AND"
                + " deleted=0 AND owner_id IS NULL AND status IN ('未开始','待执行') AND"
                + " expires_at>=NOW()",
            Long.class,
            tenant,
            arrangement)
        > 0;
  }

  public com.windasoft.eam.dto.common.PageResult<Map<String, Object>> deviceEquipment(
      long tenant,
      long user,
      com.windasoft.eam.dto.system.PermissionScope scope,
      String keyword,
      int page,
      int size) {
    var predicate =
        com.windasoft.eam.repository.common.SqlScopes.scope(
                scope, "e.company_org_id", "e.department_org_id", "e.owner_user_id", user)
            .and(
                com.windasoft.eam.repository.common.SqlPredicate.of(
                    "e.tenant_id=? AND e.deleted=0", tenant))
            .and(
                com.windasoft.eam.repository.common.SqlListFilters.contains(
                    "CONCAT_WS(' ',e.equipment_code,e.name)", keyword));
    String from = " FROM eam_equipment e WHERE " + predicate.sql();
    long count =
        db.queryForObject("SELECT COUNT(*)" + from, Long.class, predicate.arguments().toArray());
    var args = new ArrayList<>(predicate.arguments());
    args.add(size);
    args.add((page - 1) * size);
    var data =
        db.queryForList(
            "SELECT CAST(e.id AS CHAR) id,e.name,e.equipment_code code"
                + from
                + " ORDER BY e.equipment_code,e.id LIMIT ? OFFSET ?",
            args.toArray());
    return new com.windasoft.eam.dto.common.PageResult<>(data, page, size, count);
  }
}
