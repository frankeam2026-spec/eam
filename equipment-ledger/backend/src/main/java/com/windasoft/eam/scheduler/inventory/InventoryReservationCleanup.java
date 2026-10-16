package com.windasoft.eam.scheduler.inventory;

import com.windasoft.eam.service.inventory.InventoryReservationCleanupService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class InventoryReservationCleanup {
  private final InventoryReservationCleanupService service;

  public InventoryReservationCleanup(InventoryReservationCleanupService service) {
    this.service = service;
  }

  @Scheduled(fixedDelayString = "${eam.inventory.cleanup-delay-ms:30000}")
  public void cleanup() {
    service.cleanup();
  }
}
