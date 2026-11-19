package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.maintenance.MaintenanceTaskStatus;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.dto.maintenance.MaintenanceOrganizationOption;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskData;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskResultView;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskSnapshot;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskView;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.service.system.AuthContext;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceViewAssembler {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final MaintenanceTaskAccessService access;

  public MaintenanceViewAssembler(
      ObjectMapper json, AuthContext auth, MaintenanceTaskAccessService access) {
    this.json = json;
    this.auth = auth;
    this.access = access;
  }

  /** 组织显示类型为空时才沿用组织类型；选择器根节点为空串，列表上下文保留旧0值。 */
  public MaintenanceOrganizationOption organization(OrgUnit row, boolean choice) {
    String parent = Objects.toString(row.getParentId(), choice ? "" : "0");
    return new MaintenanceOrganizationOption(
        Long.toString(row.getId()),
        row.getName(),
        parent,
        row.getDisplayType() == null ? row.getOrgType() : row.getDisplayType());
  }

  public Map<String, Object> definition(Map<String, Object> row) {
    var v = decode(row.get("payload"));
    v.put("id", Objects.toString(row.get("id")));
    v.put("code", row.get("code"));
    v.put("name", row.get("name"));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("creatorId", Objects.toString(row.get("creator_id")));
    v.put("rowVersion", row.get("row_version"));
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    for (String key : List.of("department", "owner", "equipmentCount", "itemCount", "enabledLabel"))
      if (row.containsKey(key)) v.put(key, row.get(key));
    return v;
  }

  /** 任务人员从关系表读取；历史快照不重复保存人员数组，防止转派后出现两份权威。 */
  public Map<String, Object> historicalSnapshot(Map<String, Object> snapshot) {
    var historical = new LinkedHashMap<>(snapshot);
    historical.remove("owners");
    historical.remove("reviewers");
    return historical;
  }

  private static final DateTimeFormatter RESULT_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSSSS");
  private static final Set<String> TASK_FIELDS =
      Set.of(
          "id",
          "code",
          "status",
          "planId",
          "equipmentId",
          "typeId",
          "companyId",
          "departmentId",
          "ownerId",
          "rowVersion",
          "dueDate",
          "expiresDate",
          "createdAt",
          "actualStartAt",
          "actualEndAt",
          "result",
          "canExecute",
          "canAccept",
          "canDelete",
          "canPostpone",
          "customFields",
          "customFieldLabels",
          "customSchemaVersion",
          "events",
          "statistics");

  /** 固定字段由当前关系事实覆盖，历史文档仅透传生成时内容，不把旧快照重新保存为新格式。 */
  public MaintenanceTaskView task(MaintenanceTaskData row) {
    var historical = row.snapshot().deepCopy();
    TASK_FIELDS.forEach(historical::remove);
    var snapshot = json.convertValue(row.snapshot(), MaintenanceTaskSnapshot.class);
    var view = new MaintenanceTaskView();
    view.setId(Long.toString(row.id()));
    view.setCode(row.code());
    view.setStatus(row.status());
    view.setPlanId(id(row.planId()));
    view.setEquipmentId(id(row.equipmentId()));
    view.setTypeId(id(row.typeId()));
    view.setCompanyId(id(row.companyId()));
    view.setDepartmentId(id(row.departmentId()));
    view.setOwnerId(id(row.ownerId()));
    view.setRowVersion(row.rowVersion());
    view.setDueDate(Objects.toString(row.dueDate()));
    view.setExpiresDate(Objects.toString(row.expiresDate(), ""));
    view.setCreatedAt(Objects.toString(row.createdAt()).replace('T', ' '));
    view.setResult(result(row.result()));
    var execution = row.result();
    view.setActualStartAt(
        execution == null
            ? ""
            : execution.getStartAt() != null
                ? time(execution.getStartAt())
                : time(execution.getStartedAt()));
    view.setActualEndAt(execution == null ? "" : time(execution.getEndAt()));
    view.setCanExecute(
        Set.of(
                    MaintenanceTaskStatus.PENDING.code(),
                    MaintenanceTaskStatus.IN_PROGRESS.code(),
                    MaintenanceTaskStatus.SUSPENDED.code())
                .contains(row.status())
            && snapshot.getOwners() != null
            && snapshot.getOwners().contains(auth.userId())
            && (row.ownerId() == null || row.ownerId() == auth.userId()));
    view.setCanAccept(
        MaintenanceTaskStatus.PENDING_ACCEPTANCE.code().equals(row.status())
            && snapshot.getReviewers() != null
            && snapshot.getReviewers().contains(auth.userId())
            && access.allowed(row, PermissionAction.ACCEPT));
    view.setCanDelete(
        access.allowed(row, PermissionAction.DELETE)
            && Set.of(
                    MaintenanceTaskStatus.PENDING.code(),
                    MaintenanceTaskStatus.CANCELLED.code(),
                    MaintenanceTaskStatus.EXPIRED_CANCELLED.code())
                .contains(row.status()));
    view.setCanPostpone(
        access.allowed(row, PermissionAction.EDIT)
            && row.expiresDate() != null
            && Set.of(
                    MaintenanceTaskStatus.PENDING.code(),
                    MaintenanceTaskStatus.IN_PROGRESS.code(),
                    MaintenanceTaskStatus.SUSPENDED.code())
                .contains(row.status()));
    // 列表标签覆盖历史名称；详情没有选出标签时，保留快照中原有字段及缺省/null语义。
    if (row.departmentSelected()) {
      historical.put("department", row.department());
    }
    if (row.ownerSelected()) {
      historical.put("owner", row.owner());
    }
    if (historical.path("department").isTextual()) {
      view.setDepartment(historical.remove("department").asText());
    }
    if (historical.path("owner").isTextual()) {
      view.setOwner(historical.remove("owner").asText());
    }
    view.setHistoricalSnapshot(historical);
    return view;
  }

  private MaintenanceTaskResultView result(MaintenanceExecutionResult value) {
    if (value == null) {
      return new MaintenanceTaskResultView(null, null, null, null, null, null, null, null);
    }
    var review = value.getAcceptance();
    var acceptance =
        new MaintenanceTaskResultView.Acceptance(
            review == null ? "" : Objects.toString(review.getDecision(), ""),
            review == null ? "" : Objects.toString(review.getDescription(), ""),
            review == null ? null : review.getScore(),
            review == null ? "" : id(review.getReviewerId()),
            review == null ? "" : Objects.toString(review.getReviewer(), ""),
            review == null ? "" : time(review.getReviewedAt()),
            review == null || review.getAttachments() == null
                ? List.of()
                : review.getAttachments());
    return new MaintenanceTaskResultView(
        Objects.toString(value.getDescription(), ""),
        time(value.getStartedAt()),
        time(value.getStartAt()),
        time(value.getEndAt()),
        value.getItems() == null ? List.of() : value.getItems(),
        value.getAttachments() == null ? List.of() : value.getAttachments(),
        acceptance,
        value.getCollaborators() == null
            ? List.of()
            : value.getCollaborators().stream().map(Object::toString).toList());
  }

  private static String time(LocalDateTime value) {
    return value == null ? "" : value.format(RESULT_TIME);
  }

  private static String id(Long value) {
    return Objects.toString(value, "");
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"),
          new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0009");
    }
  }

  static long number(Object value) {
    return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
  }

  static List<Long> ids(Object value) {
    return value instanceof List<?> rows
        ? rows.stream()
            .map(MaintenanceViewAssembler::number)
            .filter(id -> id > 0)
            .distinct()
            .toList()
        : List.of();
  }
}
