package com.windasoft.eam.service.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.repository.inventory.InventoryBalanceRepository;
import com.windasoft.eam.repository.inventory.InventoryInboxRepository;
import com.windasoft.eam.repository.inventory.InventoryLockRepository;
import com.windasoft.eam.repository.inventory.InventoryReservationRepository;
import com.windasoft.eam.repository.inventory.RequisitionDraftRepository;
import com.windasoft.eam.repository.inventory.RequisitionRepository;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class InventoryReservationCleanupService {
  private static final Set<String> ENDED_STATUSES =
      Set.of("待验收", "待审核", "已完成", "已验收", "已审核", "已作废", "已免检", "过期作废", "已取消", "已关闭");

  private final RequisitionDraftRepository requests;
  private final RequisitionRepository legacyRequests;
  private final InventoryReservationRepository reservations;
  private final InventoryBalanceRepository balances;
  private final InventoryLockRepository locks;
  private final InventoryInboxRepository inbox;
  private final TransactionTemplate transaction;

  public InventoryReservationCleanupService(
      RequisitionDraftRepository requests,
      RequisitionRepository legacyRequests,
      InventoryReservationRepository reservations,
      InventoryBalanceRepository balances,
      InventoryLockRepository locks,
      InventoryInboxRepository inbox,
      PlatformTransactionManager manager) {
    this.requests = requests;
    this.legacyRequests = legacyRequests;
    this.reservations = reservations;
    this.balances = balances;
    this.locks = locks;
    this.inbox = inbox;
    this.transaction = new TransactionTemplate(manager);
  }

  /** 后台任务使用单据中的真实租户，各租户独立提交，锁的顺序与领料写入一致。 */
  public void cleanup() {
    for (long tenant : requests.tenantsWithPendingReservations()) {
      transaction.executeWithoutResult(status -> cleanupTenant(tenant));
    }
  }

  private void cleanupTenant(long tenant) {
    locks.enter(tenant);
    for (var request : requests.lockPendingReservations(tenant)) {
      String status =
          legacyRequests.cleanupTaskStatus(tenant, request.getTaskId(), request.getTaskKind());
      if (status != null && !ENDED_STATUSES.contains(status)) continue;

      long requestId = request.getId();
      for (var reservation : reservations.lockRemaining(tenant, requestId)) {
        balances.changeReserved(
            tenant, reservation.getBalanceId(), reservation.getRemainingQuantity().negate());
        reservations.release(tenant, reservation.getId());
      }
      requests.closeExpiredReservation(tenant, requestId);
      inbox.completeRequisition(tenant, requestId);
      legacyRequests.insertCleanupEvent(IdWorker.getId(), tenant, requestId);
    }
  }
}
