package com.windasoft.eam.service.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.inventory.InventoryReservationRepository;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryReservationCleanupService {
  private final InventoryReservationRepository repository;
  private final TransactionTemplate transaction;

  public InventoryReservationCleanupService(
      InventoryReservationRepository repository, PlatformTransactionManager manager) {
    this.repository = repository;
    this.transaction = new TransactionTemplate(manager);
  }

  // The worker uses tenant IDs from stored documents, not a fabricated request identity.
  public void cleanup() {
    for (var tenant : repository.listTenantIds())
      transaction.executeWithoutResult(
          status -> {
            long t = ((Number) tenant.get("tenant_id")).longValue();
            repository.insertLock(t);
            repository.lockTenant(t);
            for (var r : repository.lockRequests(t)) {
              String kind = Objects.toString(r.get("task_kind"));
              String table =
                  kind.equals("维修")
                      ? "eam_repair_order"
                      : kind.equals("保养") ? "eam_maintenance_task" : "eam_inspection_task";
              var tasks = repository.loadTask(t, r.get("task_id"), kind, table);
              boolean ended =
                  tasks.isEmpty()
                      || !kind.equals("保养")
                          && ((Number) tasks.getFirst().get("deleted")).intValue() != 0
                      || !tasks.isEmpty()
                          && Set.of("待验收", "待审核", "已完成", "已验收", "已审核", "已作废", "过期作废", "已取消", "已关闭")
                              .contains(Objects.toString(tasks.getFirst().get("status")));
              if (!ended) continue;
              for (var reservation : repository.lockReservations(t, r.get("id"))) {
                repository.releaseBalance(
                    reservation.get("remaining_quantity"), t, reservation.get("balance_id"));
                repository.releaseReservation(t, reservation.get("id"));
              }
              repository.closeRequest(t, r.get("id"));
              repository.closeTodos(t, r.get("id"));
              repository.insertSystemEvent(IdWorker.getId(), t, r.get("id"));
            }
          });
  }
}
