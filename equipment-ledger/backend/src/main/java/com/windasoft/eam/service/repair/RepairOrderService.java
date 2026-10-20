package com.windasoft.eam.service.repair;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.repair.RepairAction;
import com.windasoft.eam.dto.repair.RepairForm;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RepairOrderService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  private final com.windasoft.eam.service.basedata.BaseDataReferenceService references;
  private final RepairOrderRepository repository;
  private final RepairWorkflowService workflow;
  private final ObjectMapper objectMapper;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final RepairViewAssembler views;

  public RepairOrderService(
      RepairOrderRepository repository,
      RepairWorkflowService workflow,
      ObjectMapper objectMapper,
      AuthContext auth,
      PermissionService permissions,
      RepairViewAssembler views,
      com.windasoft.eam.service.basedata.BaseDataReferenceService references) {
    this.references = references;
    this.repository = repository;
    this.workflow = workflow;
    this.objectMapper = objectMapper;
    this.auth = auth;
    this.permissions = permissions;
    this.views = views;
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> create(RepairForm form) {
    var row = new RepairOrder();
    copy(form, row);
    setEquipmentAndReporter(row, form.equipmentCode());
    permissions.checkRepair(row, "报修");
    String month =
        java.time.LocalDate.now(java.time.ZoneId.of("Asia/Shanghai"))
            .format(DateTimeFormatter.ofPattern("yyyyMM"));
    repository.nextMonthlyNumber("repair-" + auth.tenantId(), month);
    row.setOrderNo("wx-" + month + "-" + String.format("%04d", repository.currentValue()));
    row.setStatus("待派工");
    row.setReportedAt(LocalDateTime.now());
    row.setRowVersion(0);
    row.setDeleted(0);
    repository.insert(row);
    custom.save(
        "repair_report",
        row.getId(),
        form.customFields(),
        form.customSchemaVersion(),
        String.valueOf(repository.equipment(auth.tenantId(), form.equipmentCode()).getCategoryId()),
        true);
    workflow.record(
        row,
        "报修",
        Map.of(
            "description",
            row.getDescription(),
            "faultTypeId",
            row.getFaultTypeId().toString(),
            "faultType",
            row.getFaultType(),
            "reportedAt",
            row.getReportedAt().toString(),
            "attachments",
            views.json(row.getAttachmentsJson())));
    return Map.of("data", views.view(row));
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> update(Long id, RepairForm form, int rowVersion) {
    var row = require(id);
    permissions.checkRepair(row, "编辑");
    VersionGuard.check(row.getRowVersion(), rowVersion);
    if (!"待派工".equals(row.getStatus()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0041");
    copy(form, row);
    setEquipment(row, form.equipmentCode());
    permissions.checkRepair(row, "编辑");
    VersionGuard.updated(repository.update(row));
    custom.save(
        "repair_report",
        row.getId(),
        form.customFields(),
        form.customSchemaVersion(),
        String.valueOf(repository.equipment(auth.tenantId(), form.equipmentCode()).getCategoryId()),
        false);
    workflow.record(
        row,
        "编辑报修",
        Map.of(
            "description",
            row.getDescription(),
            "faultTypeId",
            row.getFaultTypeId().toString(),
            "faultType",
            row.getFaultType(),
            "attachments",
            views.json(row.getAttachmentsJson())));
    return Map.of("data", views.view(require(id)));
  }

  @org.springframework.transaction.annotation.Transactional
  public Map<String, Object> action(Long id, RepairAction request, int rowVersion) {
    if (workflow != null) {
      if ("complete".equals(request.action()))
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0042");
      var body = objectMapper.valueToTree(request);
      if ("dispatch".equals(request.action()))
        ((com.fasterxml.jackson.databind.node.ObjectNode) body)
            .set(
                "userIds",
                objectMapper.valueToTree(List.of(permissions.userIdByName(request.assignee()))));
      if (!"cancel".equals(request.action()))
        return Map.of("data", views.view(workflow.action(id, rowVersion, body)));
    }
    var row = require(id);
    String next;
    String requiredAction =
        switch (request.action()) {
          case "dispatch" -> "派工";
          case "complete", "suspend", "resume" -> "处理";
          case "accept", "reject", "return" -> "验收";
          case "cancel" -> "撤回";
          default ->
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.repair.message0025");
        };
    permissions.checkRepair(row, requiredAction);
    VersionGuard.check(row.getRowVersion(), rowVersion);
    switch (request.action()) {
      case "dispatch" -> {
        if (!"待派工".equals(row.getStatus())) throw invalid();
        if (request.assignee() == null || request.assignee().isBlank())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0043");
        row.setAssignee(request.assignee().trim());
        row.setAssigneeUserId(permissions.userIdByName(row.getAssignee()));
        if (row.getAssigneeUserId() == null)
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0044");
        next = "处理中";
      }
      case "complete" -> {
        if (!"处理中".equals(row.getStatus())) throw invalid();
        if (request.repairAction() == null || request.repairAction().isBlank())
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0045");
        row.setRepairAction(request.repairAction().trim());
        row.setDowntimeMinutes(
            Math.max(0, request.downtimeMinutes() == null ? 0 : request.downtimeMinutes()));
        row.setCompletedAt(LocalDateTime.now());
        next = "待验收";
      }
      case "accept" -> {
        if (!"待验收".equals(row.getStatus())) throw invalid();
        next = "已完成";
      }
      case "reject", "return" -> {
        if (!"待验收".equals(row.getStatus())) throw invalid();
        next = "处理中";
      }
      case "suspend" -> {
        if (!"处理中".equals(row.getStatus())) throw invalid();
        next = "挂起";
      }
      case "resume" -> {
        if (!"挂起".equals(row.getStatus())) throw invalid();
        next = "处理中";
      }
      case "cancel" -> {
        if (!"待派工".equals(row.getStatus())) throw invalid();
        next = "已撤销";
      }
      default ->
          throw new com.windasoft.eam.common.LocalizedArgumentException(
              "server.repair.message0025");
    }
    row.setStatus(next);
    VersionGuard.updated(repository.update(row));
    if (workflow != null) workflow.record(row, "撤销工单", Map.of("status", next));
    return Map.of("data", views.view(require(id)));
  }

  private RepairOrder require(Long id) {
    var row = repository.find(auth.tenantId(), id);
    if (row == null || Integer.valueOf(1).equals(row.getDeleted()))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0046");
    return row;
  }

  private static IllegalArgumentException invalid() {
    return new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0029");
  }

  private void copy(RepairForm f, RepairOrder row) {
    var fault =
        references.required(auth.tenantId(), f.faultTypeId(), "FAULT_TYPE", "faultTypeId", "故障类型");
    row.setFaultTypeId(fault.getId());
    row.setFaultType(fault.getName());
    row.setSeverity(f.severity());
    row.setDescription(f.description().trim());
    row.setAttachmentsJson(
        workflow == null ? jsonString(f.attachments()) : workflow.attachments(f.attachments()));
  }

  private void setEquipmentAndReporter(RepairOrder row, String code) {
    setEquipment(row, code);
    row.setTenantId(auth.tenantId());
    row.setReporterUserId(auth.userId());
    row.setReporter(auth.user().getDisplayName());
  }

  private void setEquipment(RepairOrder row, String code) {
    var equipment = repository.equipment(auth.tenantId(), code);
    if (equipment == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    permissions.checkEquipment(equipment, "查看");
    row.setEquipmentId(equipment.getId());
    row.setCompanyOrgId(equipment.getCompanyOrgId());
    row.setEquipmentCode(equipment.getEquipmentCode());
    row.setEquipmentName(equipment.getName());
    row.setDepartment(equipment.getDepartment());
    row.setDepartmentOrgId(equipment.getDepartmentOrgId());
    row.setTenantId(auth.tenantId());
  }

  private String jsonString(JsonNode node) {
    try {
      return node == null || node.isNull() ? "[]" : objectMapper.writeValueAsString(node);
    } catch (Exception e) {
      return "[]";
    }
  }
}
