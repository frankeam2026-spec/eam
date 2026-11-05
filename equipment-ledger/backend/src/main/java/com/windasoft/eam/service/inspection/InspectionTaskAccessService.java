package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.domain.inspection.InspectionTaskAction;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionTaskAccessService {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public InspectionTaskAccessService(
      ObjectMapper json, AuthContext auth, PermissionService permissions) {
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public boolean participant(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    if (text(s, "personMode").equals("现场直接点检") && openScope(row)) return true;
    return Objects.equals(nullable(row.get("follow_up_user_id")), auth.userId())
        || ids(s.get("owners")).contains(auth.userId())
        || ids(s.get("reviewers")).contains(auth.userId())
        || Objects.equals(nullable(row.get("owner_id")), auth.userId())
        || text(s, "personMode").equals("任何人可点检");
  }

  public boolean access(Map<String, Object> row, String action) {
    if (action.equals("查看") && participant(row)) return true;
    try {
      return permissions
          .inspection(text(row, "kind"), action)
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("owner_id")),
              auth.userId());
    } catch (ResponseStatusException ex) {
      return false;
    }
  }

  public boolean execute(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    return ((text(s, "personMode").equals("现场直接点检") && openScope(row))
            || text(s, "personMode").equals("任何人可点检")
            || ids(s.get("owners")).contains(auth.userId()))
        && (row.get("owner_id") == null || number(row.get("owner_id")) == auth.userId());
  }

  /** Whether the current user can start this task at the supplied instant. */
  public boolean canStart(Map<String, Object> row, Map<String, Object> snapshot, LocalDateTime at) {
    return InspectionTaskStatus.allows(InspectionTaskAction.START, text(row, "status"))
        && execute(row)
        && startDue(row, at)
        && startWithinDeadline(row, snapshot, at);
  }

  boolean startDue(Map<String, Object> row, LocalDateTime at) {
    return !at.isBefore(dateTime(row.get("due_at")));
  }

  boolean startWithinDeadline(
      Map<String, Object> row, Map<String, Object> snapshot, LocalDateTime at) {
    return !at.isAfter(dateTime(row.get("expires_at"))) || yes(snapshot.get("allowLate"));
  }

  public boolean canDelete(Map<String, Object> row) {
    if (!InspectionTaskStatus.allows(InspectionTaskAction.DELETE, text(row, "status"))
        || !access(row, "删除")) return false;
    if (!text(row, "kind").equals("daily")) return true;
    return unexecutedDailyTask(row);
  }

  public boolean canExempt(Map<String, Object> row, Map<String, Object> result) {
    return text(row, "kind").equals("daily")
        && access(row, "免检")
        && unexecutedDailyTask(row, result);
  }

  public boolean canAmendResult(Map<String, Object> row) {
    return number(row.get("tenant_id")) == auth.tenantId()
        && text(row, "kind").equals("daily")
        && InspectionTaskStatus.allows(InspectionTaskAction.AMEND_RESULT, text(row, "status"))
        && access(row, "修改结果");
  }

  boolean unexecutedDailyTask(Map<String, Object> row) {
    return unexecutedDailyTask(row, decode(row.get("result")));
  }

  boolean unexecutedDailyTask(Map<String, Object> row, Map<String, Object> result) {
    if (!InspectionTaskStatus.allows(InspectionTaskAction.EXEMPT, text(row, "status"))) return false;
    return text(result, "startedAt").isBlank()
        && text(result, "finishedAt").isBlank()
        && text(result, "submittedAt").isBlank()
        && rows(result.get("items")).isEmpty()
        && strings(result.get("scannedEquipmentIds")).isEmpty();
  }

  public boolean review(Map<String, Object> row) {
    return ids(decode(row.get("snapshot")).get("reviewers")).contains(auth.userId())
        || access(row, "审核");
  }

  private boolean openScope(Map<String, Object> row) {
    if (number(row.get("tenant_id")) != auth.tenantId() || !text(row, "kind").equals("daily"))
      return false;
    try {
      return permissions
          .inspection("daily", "执行")
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("owner_id")),
              auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
