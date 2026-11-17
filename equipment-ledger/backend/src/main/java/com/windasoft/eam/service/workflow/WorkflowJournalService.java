package com.windasoft.eam.service.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.entity.inventory.InventoryInbox;
import com.windasoft.eam.entity.workflow.WorkflowApproval;
import com.windasoft.eam.entity.workflow.WorkflowTodo;
import com.windasoft.eam.repository.inventory.InventoryInboxRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.workflow.WorkflowApprovalRepository;
import com.windasoft.eam.repository.workflow.WorkflowTodoRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 审批任务、业务待办与公共通知的原子联动；不决定节点流向或申请业务状态。 */
@Service
@RequiredArgsConstructor
public class WorkflowJournalService {
  private final WorkflowApprovalRepository approvals;
  private final WorkflowTodoRepository todos;
  private final InventoryInboxRepository inventory;
  private final RepairInboxRepository repair;
  private final NotificationRepository notifications;

  /** 加入运行时共享事务；任何一步失败必须连同Flowable及业务状态一起回滚。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void approval(
      long tenant,
      long instance,
      String node,
      String task,
      long user,
      long businessId,
      String title,
      String business) {
    long id = IdWorker.getId(), inbox = IdWorker.getId();
    var row = new WorkflowApproval();
    row.setId(id);
    row.setTenantId(tenant);
    row.setInstanceId(instance);
    row.setNodeKey(node);
    row.setEngineTaskId(task);
    row.setUserId(user);
    approvals.insert(row);
    boolean report = business.equals("repair_report");
    if (report) {
      repair.workflowTodo(inbox, tenant, user, businessId, title);
    } else {
      var message = new InventoryInbox();
      message.setId(inbox);
      message.setTenantId(tenant);
      message.setUserId(user);
      message.setDocumentId(businessId);
      message.setRouteKind("requisitions");
      message.setKind("领料审批");
      message.setTitle(title);
      message.setIsTodo(1);
      inventory.insert(message);
    }
    var link = new WorkflowTodo();
    link.setTenantId(tenant);
    link.setApprovalId(id);
    link.setInboxId(inbox);
    link.setSourceKind(report ? "REPAIR" : "INVENTORY");
    todos.insert(link);
    notifications.captureSource(report ? "eam_repair_inbox" : "inv_inbox", tenant, inbox);
  }

  /** 锁顺序沿用库存待办后维修待办，只关闭本实例已结束人员任务关联的消息。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public void closeTodos(long tenant, long instance) {
    var links = todos.closedApprovals(tenant, instance);
    List<Long> inventoryIds =
        links.stream()
            .filter(t -> "INVENTORY".equals(t.sourceKind()))
            .map(t -> t.inboxId())
            .toList();
    List<Long> repairIds =
        links.stream().filter(t -> "REPAIR".equals(t.sourceKind())).map(t -> t.inboxId()).toList();
    inventory.complete(tenant, inventoryIds);
    repair.complete(tenant, repairIds);
  }
}
