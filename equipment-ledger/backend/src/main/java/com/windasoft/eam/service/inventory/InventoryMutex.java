package com.windasoft.eam.service.inventory;

import com.windasoft.eam.repository.inventory.InventoryLockRepository;
import com.windasoft.eam.service.system.AuthContext;
import org.springframework.stereotype.Component;

@Component
public class InventoryMutex {
  private final InventoryLockRepository repository;
  private final AuthContext auth;

  public InventoryMutex(InventoryLockRepository repository, AuthContext auth) {
    this.repository = repository;
    this.auth = auth;
  }

  public void enter() {
    repository.enter(auth.tenantId());
  }
}
