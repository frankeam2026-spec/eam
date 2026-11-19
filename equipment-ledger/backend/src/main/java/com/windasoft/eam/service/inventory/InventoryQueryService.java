package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.domain.system.PermissionAction.EXPORT;
import static com.windasoft.eam.domain.system.PermissionAction.VIEW;
import static com.windasoft.eam.domain.system.PermissionAction.VIEW_AMOUNT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.system.PermissionMenu;
import com.windasoft.eam.dto.inventory.InventoryQueryRow;
import com.windasoft.eam.entity.inventory.Warehouse;
import com.windasoft.eam.repository.common.AssistantReadSource;
import com.windasoft.eam.repository.inventory.InventoryQueryRepository;
import com.windasoft.eam.repository.inventory.WarehouseRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryQueryService {
  private static final String MENU = PermissionMenu.INVENTORY_QUERY.code();
  private static final int MAX_PAGE_SIZE = 100;

  private final InventoryQueryRepository repository;
  private final WarehouseRepository warehouses;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public InventoryQueryService(
      InventoryQueryRepository repository,
      WarehouseRepository warehouses,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.warehouses = warehouses;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public record Page(long total) {}

  public record Result(List<InventoryQueryRow> data, Page page) {}

  private InventoryQueryRepository.Query query(
      String kind, String q, String filterJson, Set<Long> allowed) {
    if (q.length() > 200 || filterJson.length() > 6000)
      throw new LocalizedArgumentException("server.inventory.message0187");
    Map<String, String> filters;
    try {
      filters = json.readValue(filterJson, new TypeReference<>() {});
    } catch (JsonProcessingException e) {
      throw new LocalizedArgumentException("server.common.message0009");
    }
    return repository.query(auth.tenantId(), kind, q, filters, allowed);
  }

  @Transactional(readOnly = true)
  public Result list(String kind, String q, String filters, int page, int size) {
    if (page < 1 || size < 1 || size > MAX_PAGE_SIZE)
      throw new LocalizedArgumentException("server.common.message0004");
    permissions.require(MENU, VIEW);
    var candidates = warehouses.scopeRows(auth.tenantId());
    var query = query(kind, q, filters, permissions.inventoryWarehouses(MENU, VIEW, candidates));
    long total = repository.count(query);
    return new Result(
        repository.list(query, page, size, costWarehouses(candidates)), new Page(total));
  }

  @Transactional(readOnly = true)
  public AssistantReadSource assistantSource() {
    permissions.require(MENU, VIEW);
    var candidates = warehouses.scopeRows(auth.tenantId());
    return repository.assistantSource(
        query("balances", "", "{}", permissions.inventoryWarehouses(MENU, VIEW, candidates)),
        auth.tenantId());
  }

  /** 导出仓库同时满足查看和导出，数据库只读取两种权限的交集。 */
  @Transactional(readOnly = true)
  public List<InventoryQueryRow> export(String kind, String q, String filters) {
    permissions.require(MENU, EXPORT);
    permissions.require(MENU, VIEW);
    var candidates = warehouses.scopeRows(auth.tenantId());
    var allowed = permissions.inventoryWarehouses(MENU, VIEW, candidates);
    allowed.retainAll(permissions.inventoryWarehouses(MENU, EXPORT, candidates));
    return repository.export(query(kind, q, filters, allowed), costWarehouses(candidates));
  }

  private Set<Long> costWarehouses(List<Warehouse> candidates) {
    return permissions.inventoryWarehouses(MENU, VIEW_AMOUNT, candidates);
  }
}
