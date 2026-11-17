package com.windasoft.eam.service.workflow;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.repository.inventory.InventoryInboxRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.workflow.WorkflowApprovalRepository;
import com.windasoft.eam.repository.workflow.WorkflowTodoRepository;
import com.windasoft.eam.repository.workflow.WorkflowTodoRepository.InboxLink;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 待办只按来源与精确关联关闭，不能退回按业务单据整体关闭的旧写法。 */
class WorkflowJournalServiceTest {
  @Test
  void closePreservesSourceAndExistingLockOrder() {
    var tasks = mock(WorkflowApprovalRepository.class);
    var links = mock(WorkflowTodoRepository.class);
    var inventory = mock(InventoryInboxRepository.class);
    var repair = mock(RepairInboxRepository.class);
    when(links.closedApprovals(7L, 12L))
        .thenReturn(List.of(new InboxLink(21L, "REPAIR"), new InboxLink(22L, "INVENTORY")));
    var service =
        new WorkflowJournalService(
            tasks, links, inventory, repair, mock(NotificationRepository.class));
    service.closeTodos(7L, 12L);
    var order = inOrder(inventory, repair);
    order.verify(inventory).complete(7L, List.of(22L));
    order.verify(repair).complete(7L, List.of(21L));
    verifyNoMoreInteractions(inventory, repair);
  }
}
