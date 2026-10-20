package com.windasoft.eam.service.inventory;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.inventory.InventorySelectorRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import java.util.function.Supplier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class InventorySelectorService {
  private final InventorySelectorRepository repository;
  private final PermissionService permissions;
  private final AuthContext auth;
  private final InventoryService masters;

  public InventorySelectorService(
      InventorySelectorRepository repository,
      PermissionService permissions,
      AuthContext auth,
      InventoryService masters) {
    this.repository = repository;
    this.permissions = permissions;
    this.auth = auth;
    this.masters = masters;
  }

  private PermissionScope scope(Supplier<PermissionScope> source) {
    try {
      return source.get();
    } catch (ResponseStatusException e) {
      if (e.getStatusCode().value() != 403) throw e;
      return new PermissionScope(false, null, Set.of(), false);
    }
  }

  private long warehouseCompany(long warehouse) {
    permissions.checkInventoryWarehouse("inventoryOutbound", warehouse, "查看");
    return ((Number) masters.row("warehouses", warehouse, false).get("company_id")).longValue();
  }

  public PageResult<Map<String, Object>> tasks(
      String mode, ListQuery q, Long warehouse, String kind, Long id) {
    if (!Set.of("requisition", "outbound").contains(mode))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0017");
    if (!kind.isBlank() && !Set.of("维修", "保养", "点巡检").contains(kind))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0018");
    Long company = null;
    if (mode.equals("outbound")) {
      permissions.require("inventoryOutbound", "查看");
      if (warehouse == null) return new PageResult<>(List.of(), q.page(), q.size(), 0);
      company = warehouseCompany(warehouse);
    }
    boolean personal =
        mode.equals("requisition") && !permissions.allowed("inventoryRequisition", "新增");
    return repository.tasks(
        auth.tenantId(),
        auth.userId(),
        q,
        scope(() -> permissions.repair("查看")),
        scope(() -> permissions.maintenance("tasks", "查看")),
        scope(() -> permissions.inspection("daily", "查看")),
        scope(() -> permissions.inspection("periodic", "查看")),
        personal,
        company,
        kind,
        id);
  }

  public PageResult<Map<String, Object>> users(ListQuery q, long warehouse, Set<Long> ids) {
    return repository.users(auth.tenantId(), warehouseCompany(warehouse), q, ids);
  }
}
