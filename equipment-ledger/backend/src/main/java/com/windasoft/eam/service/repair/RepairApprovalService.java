package com.windasoft.eam.service.repair;

import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.*;
import com.windasoft.eam.service.workflow.WorkflowRuntimeService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class RepairApprovalService {
  private final RepairWorkflowRepository repository;
  private final WorkflowRuntimeService runtime;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InventoryMutex mutex;
  private final ObjectProvider<RepairWorkflowService> workflow;

  public RepairApprovalService(
      RepairWorkflowRepository repository,
      WorkflowRuntimeService runtime,
      AuthContext auth,
      PermissionService permissions,
      InventoryMutex mutex,
      ObjectProvider<RepairWorkflowService> workflow) {
    this.repository = repository;
    this.runtime = runtime;
    this.auth = auth;
    this.permissions = permissions;
    this.mutex = mutex;
    this.workflow = workflow;
  }

  public void prepare(RepairOrder row) {
    if (row.getCompanyOrgId() == null) throw new IllegalArgumentException("报修设备尚未设置所属公司");
    boolean required = runtime.repairApprovalRequired(row.getCompanyOrgId());
    row.setApprovalGate(required ? "REQUIRED" : "EXEMPT");
    row.setStatus(required ? "审批中" : "待派工");
    if (!required) row.setApprovalReadyAt(row.getReportedAt());
  }

  public void start(RepairOrder row) {
    if ("REQUIRED".equals(row.getApprovalGate()))
      runtime.start("repair_report", row.getId(), row.getCompanyOrgId(), row.getReporterUserId());
  }

  public void requireReleased(RepairOrder row) {
    if (!Set.of("LEGACY", "EXEMPT").contains(Objects.toString(row.getApprovalGate(), ""))
        && !("REQUIRED".equals(row.getApprovalGate())
            && runtime.approved("repair_report", row.getId())))
      throw new IllegalArgumentException("报修尚未审批通过，不能进入维修处理流程");
  }

  public void requireCompanyChangeAllowed(Long originalCompany, RepairOrder row) {
    if (Objects.equals(originalCompany, row.getCompanyOrgId())) return;
    if (row.getCompanyOrgId() == null) throw new IllegalArgumentException("报修设备尚未设置所属公司");
    if ("REQUIRED".equals(row.getApprovalGate())
        || runtime.repairApprovalRequired(row.getCompanyOrgId()))
      throw new IllegalArgumentException("涉及审批的报修不能通过更换设备改变所属公司");
  }

  public RepairOrder lock(long id) {
    mutex.enter();
    var row = repository.lock(auth.tenantId(), id);
    if (row == null || Integer.valueOf(1).equals(row.getDeleted()))
      throw new IllegalArgumentException("维修工单不存在");
    return row;
  }

  public void requireActive(long id) {
    var row = repository.findOrder(auth.tenantId(), id);
    if (row == null
        || Integer.valueOf(1).equals(row.getDeleted())
        || !"审批中".equals(row.getStatus())
        || !"REQUIRED".equals(row.getApprovalGate()))
      throw new IllegalArgumentException("报修已不处于审批中");
  }

  public void approved(long id) {
    var row = lock(id);
    requireActive(id);
    row.setStatus("待派工");
    row.setApprovalReadyAt(LocalDateTime.now());
    VersionGuard.updated(repository.updateOrder(row));
    record(row, "报修审批通过");
    notifyReporter(row, "报修审批通过", false);
  }

  public void returned(long id, String reason) {
    var row = lock(id);
    requireActive(id);
    row.setStatus("待提交");
    VersionGuard.updated(repository.updateOrder(row));
    record(row, reason);
    repository.closeApprovalReturnTodos(auth.tenantId(), id);
    notifyReporter(row, "报修待修改", true);
  }

  @Transactional
  public void submit(long id, int version) {
    var row = lock(id);
    if (!Objects.equals(row.getReporterUserId(), auth.userId()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅报修人可以重新提交");
    permissions.checkRepair(row, "报修");
    VersionGuard.check(row.getRowVersion(), version);
    if (!"待提交".equals(row.getStatus())) throw new IllegalArgumentException("只有退回的报修可以重新提交");
    row.setStatus("审批中");
    VersionGuard.updated(repository.updateOrder(row));
    start(row);
    repository.closeApprovalReturnTodos(auth.tenantId(), id);
    record(row, "重新提交报修审批");
  }

  public Object history(long id) {
    return runtime.history("repair_report", id);
  }

  public Map<String, Object> values(long id) {
    return repository.approvalValues(auth.tenantId(), id);
  }

  private void record(RepairOrder row, String label) {
    workflow.getObject().record(row, label, Map.of("status", row.getStatus()));
  }

  private void notifyReporter(RepairOrder row, String kind, boolean todo) {
    repository.inbox(
        com.baomidou.mybatisplus.core.toolkit.IdWorker.getId(),
        auth.tenantId(),
        row.getReporterUserId(),
        row.getId(),
        kind,
        row.getOrderNo() + " · " + row.getEquipmentName(),
        todo);
  }
}
