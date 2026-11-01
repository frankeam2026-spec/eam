package com.windasoft.eam.service.repair;

import com.windasoft.eam.domain.repair.RepairOrderStatus;
import com.windasoft.eam.domain.repair.RepairApprovalGate;
import com.windasoft.eam.domain.repair.RepairNoticeKind;
import com.windasoft.eam.domain.repair.RepairEventLabel;
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

/** 报修与审批引擎的业务适配：维护报修审批门禁及回调状态，流程状态由审批运行时管理。 */
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
    row.setApprovalGate(required ? RepairApprovalGate.REQUIRED.code() : RepairApprovalGate.EXEMPT.code());
    row.setStatus(required ? RepairOrderStatus.PENDING_APPROVAL.code() : RepairOrderStatus.PENDING_DISPATCH.code());
    if (!required) row.setApprovalReadyAt(row.getReportedAt());
  }

  public void start(RepairOrder row) {
    if (RepairApprovalGate.REQUIRED.code().equals(row.getApprovalGate()))
      runtime.start("repair_report", row.getId(), row.getCompanyOrgId(), row.getReporterUserId());
  }

  /** 需要审批的报修必须获得实际审批通过；历史与豁免记录按现有策略放行。 */
  public void requireReleased(RepairOrder row) {
    if (!Set.of(RepairApprovalGate.LEGACY.code(), RepairApprovalGate.EXEMPT.code()).contains(Objects.toString(row.getApprovalGate(), ""))
        && !(RepairApprovalGate.REQUIRED.code().equals(row.getApprovalGate())
            && runtime.approved("repair_report", row.getId())))
      throw new IllegalArgumentException("报修尚未审批通过，不能进入维修处理流程");
  }

  public void requireCompanyChangeAllowed(Long originalCompany, RepairOrder row) {
    if (Objects.equals(originalCompany, row.getCompanyOrgId())) return;
    if (row.getCompanyOrgId() == null) throw new IllegalArgumentException("报修设备尚未设置所属公司");
    if (RepairApprovalGate.REQUIRED.code().equals(row.getApprovalGate())
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
        || !RepairOrderStatus.PENDING_APPROVAL.code().equals(row.getStatus())
        || !RepairApprovalGate.REQUIRED.code().equals(row.getApprovalGate()))
      throw new IllegalArgumentException("报修已不处于审批中");
  }

  /** 审批运行时回调：审批通过后释放工单至待派工，并写入事件及通知。 */
  public void approved(long id) {
    var row = lock(id);
    requireActive(id);
    row.setStatus(RepairOrderStatus.PENDING_DISPATCH.code());
    row.setApprovalReadyAt(LocalDateTime.now());
    VersionGuard.updated(repository.updateOrder(row));
    record(row, RepairEventLabel.APPROVAL_APPROVED.code());
    notifyReporter(row, RepairNoticeKind.APPROVAL_APPROVED.code(), false);
  }

  /** 审批运行时回调：退回至待提交，并更新关联待办及通知。 */
  public void returned(long id, String reason) {
    var row = lock(id);
    requireActive(id);
    row.setStatus(RepairOrderStatus.PENDING_SUBMISSION.code());
    VersionGuard.updated(repository.updateOrder(row));
    record(row, reason);
    repository.closeApprovalReturnTodos(auth.tenantId(), id);
    notifyReporter(row, RepairNoticeKind.REPORT_REVISION.code(), true);
  }

  @Transactional
  public void submit(long id, int version) {
    var row = lock(id);
    if (!Objects.equals(row.getReporterUserId(), auth.userId()))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅报修人可以重新提交");
    permissions.checkRepair(row, "报修");
    VersionGuard.check(row.getRowVersion(), version);
    if (!RepairOrderStatus.PENDING_SUBMISSION.code().equals(row.getStatus())) throw new IllegalArgumentException("只有退回的报修可以重新提交");
    row.setStatus(RepairOrderStatus.PENDING_APPROVAL.code());
    VersionGuard.updated(repository.updateOrder(row));
    start(row);
    repository.closeApprovalReturnTodos(auth.tenantId(), id);
    record(row, RepairEventLabel.APPROVAL_RESUBMITTED.code());
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
