package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.domain.system.PermissionAction.EXPORT;
import static com.windasoft.eam.domain.system.PermissionAction.VIEW;
import static com.windasoft.eam.domain.system.PermissionAction.VIEW_AMOUNT;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.entity.inventory.Warehouse;
import com.windasoft.eam.repository.inventory.InventoryListRepository;
import com.windasoft.eam.repository.inventory.InventoryListRepository.Selection;
import com.windasoft.eam.repository.inventory.WarehouseRepository;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryListService {
  private final InventoryListRepository repository;
  private final InventoryViewAssembler views;
  private final PermissionService permissions;
  private final AuthContext auth;
  private final BusinessQueryService custom;
  private final BusinessValueService values;
  private final WarehouseRepository warehouseRepository;
  private final ObjectMapper json;

  public InventoryListService(
      InventoryListRepository repository,
      InventoryViewAssembler views,
      PermissionService permissions,
      AuthContext auth,
      BusinessQueryService custom,
      BusinessValueService values,
      WarehouseRepository warehouseRepository,
      ObjectMapper json) {
    this.repository = repository;
    this.views = views;
    this.permissions = permissions;
    this.auth = auth;
    this.custom = custom;
    this.values = values;
    this.warehouseRepository = warehouseRepository;
    this.json = json;
  }

  private static final int EXPORT_BATCH_SIZE = 100;

  private record Listing(
      Selection selection, ListQuery query, Consumer<List<Map<String, Object>>> decorate) {}

  private Set<Long> warehouses(List<Warehouse> candidates, String menu, boolean export) {
    var ids = permissions.inventoryWarehouses(menu, VIEW, candidates);
    if (export) ids.retainAll(permissions.inventoryWarehouses(menu, EXPORT, candidates));
    return ids;
  }

  private List<Warehouse> warehouseRows() {
    return warehouseRepository.scopeRows(auth.tenantId());
  }

  private List<Map<String, Object>> read(Listing listing, ListQuery query) {
    var rows = repository.page(listing.selection(), query).stream().map(views::view).toList();
    listing.decorate().accept(rows);
    return rows;
  }

  private Map<String, Object> page(Listing listing) {
    var query = listing.query();
    return Map.of(
        "data",
        read(listing, query),
        "page",
        Map.of(
            "number",
            query.page(),
            "size",
            query.size(),
            "total",
            repository.count(listing.selection())));
  }

  /** 单次导出复用相同的授权和筛选，按稳定顺序分批读取，避免每批重新计数。 */
  private List<Map<String, Object>> export(Listing listing) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var rows = read(listing, listing.query().at(page, EXPORT_BATCH_SIZE));
      result.addAll(rows);
      if (rows.size() < EXPORT_BATCH_SIZE) return result;
    }
  }

  private ListQuery query(String q, String filters, String conditions, int page, int size) {
    if (size > 100) throw new LocalizedArgumentException("server.common.message0004");
    return ListQuery.from(
        Map.of(
            "q",
            q,
            "filters",
            filters,
            "conditions",
            conditions,
            "page",
            Integer.toString(page),
            "size",
            Integer.toString(size)));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> masters(
      String kind, String q, String filters, int page, int size, boolean export) {
    return page(selectMasters(kind, q, filters, page, size, export));
  }

  private Listing selectMasters(
      String kind, String q, String filters, int page, int size, boolean export) {
    String menu = InventoryService.menu(kind);
    permissions.require(menu, export ? "导出" : "查看");
    permissions.require(menu, "查看");
    var query = query(q, filters, "[]", page, size);
    boolean money = !kind.equals("materials") || permissions.allowed(menu, "查看金额");
    var selected =
        repository.masters(
            auth.tenantId(),
            kind,
            query,
            kind.equals("materials") ? Set.of() : warehouses(warehouseRows(), menu, export),
            money);
    return new Listing(
        selected,
        query,
        rows -> {
          if (!money) rows.forEach(InventoryMoney::hide);
        });
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportMasters(String kind, String q, String filters) {
    return export(selectMasters(kind, q, filters, 1, EXPORT_BATCH_SIZE, true));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> receipts(
      String q, String filters, String conditions, int page, int size, boolean export) {
    return page(selectReceipts(q, filters, conditions, page, size, export));
  }

  private Listing selectReceipts(
      String q, String filters, String conditions, int page, int size, boolean export) {
    String menu = "inventoryReceipt";
    permissions.require(menu, export ? "导出" : "查看");
    permissions.require(menu, "查看");
    var query = query(q, filters, conditions, page, size);
    var candidates = warehouseRows();
    var selected =
        repository.receipts(
            auth.tenantId(),
            query,
            warehouses(candidates, menu, export),
            custom.predicate("inventory_receipt", conditions, "t"),
            custom.orderExpression("inventory_receipt", query.sortBy(), "t"));
    var moneyWarehouses = permissions.inventoryWarehouses(menu, VIEW_AMOUNT, candidates);
    return new Listing(
        selected,
        query,
        rows -> {
          hideUnauthorizedAmounts(rows, moneyWarehouses);
          values.attach("inventory_receipt", rows);
        });
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportReceipts(String q, String filters, String conditions) {
    return export(selectReceipts(q, filters, conditions, 1, EXPORT_BATCH_SIZE, true));
  }

  private void hideUnauthorizedAmounts(List<Map<String, Object>> rows, Set<Long> moneyWarehouses) {
    for (var row : rows) {
      if (!moneyWarehouses.contains(InventoryService.id(row.get("warehouseId"))))
        InventoryMoney.hide(row);
    }
  }

  private boolean usesMoney(String filters, String conditions) {
    try {
      if (json.readTree(filters).has("totalAmount")) return true;
      for (var condition : json.readTree(conditions))
        if (condition.path("field").asText().equals("totalAmount")) return true;
      return false;
    } catch (JsonProcessingException e) {
      throw new LocalizedArgumentException("server.common.message0009");
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> outbounds(
      String q, String filters, String conditions, int page, int size, boolean export) {
    return page(selectOutbounds(q, filters, conditions, page, size, export));
  }

  private Listing selectOutbounds(
      String q, String filters, String conditions, int page, int size, boolean export) {
    String menu = "inventoryOutbound";
    permissions.require(menu, export ? "导出" : "查看");
    permissions.require(menu, "查看");
    var query = query(q, filters, conditions, page, size);
    var candidates = warehouseRows();
    var allowed = warehouses(candidates, menu, export);
    var moneyWarehouses = permissions.inventoryWarehouses(menu, VIEW_AMOUNT, candidates);
    boolean money = permissions.allowed(menu, "查看金额");
    if (usesMoney(filters, conditions)) {
      permissions.require(menu, "查看金额");
      allowed.retainAll(moneyWarehouses);
    }
    var selected =
        repository.outbounds(
            auth.tenantId(),
            query,
            allowed,
            money,
            custom.predicate("inventory_outbound", conditions, "t"),
            custom.orderExpression("inventory_outbound", query.sortBy(), "t"));
    return new Listing(
        selected,
        query,
        rows -> {
          rows.forEach(row -> row.remove("legacy"));
          hideUnauthorizedAmounts(rows, moneyWarehouses);
          values.attach(
              "inventory_outbound",
              rows.stream().filter(row -> !row.get("id").toString().startsWith("g")).toList());
        });
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportOutbounds(String q, String filters, String conditions) {
    return export(selectOutbounds(q, filters, conditions, 1, EXPORT_BATCH_SIZE, true));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> requisitions(
      String q,
      String filters,
      String conditions,
      int page,
      int size,
      boolean export,
      boolean pending) {
    return page(selectRequisitions(q, filters, conditions, page, size, export, pending));
  }

  private Listing selectRequisitions(
      String q,
      String filters,
      String conditions,
      int page,
      int size,
      boolean export,
      boolean pending) {
    var candidates = warehouseRows();
    Set<Long> allowed;
    String menu = pending ? "inventoryOutbound" : "inventoryRequisition";
    if (pending) {
      permissions.require(menu, export ? "导出" : "查看");
      allowed = warehouses(candidates, menu, export);
    } else {
      allowed = warehouses(candidates, "inventoryRequisition", false);
      allowed.addAll(warehouses(candidates, "inventoryOutbound", false));
      if (export) permissions.require(menu, "导出");
    }
    var query = query(q, filters, conditions, page, size);
    var selected =
        repository.requisitions(auth.tenantId(), auth.userId(), query, allowed, !pending, pending);
    if (export && !pending)
      selected =
          repository.restrictWarehouse(
              selected, permissions.inventoryWarehouses(menu, EXPORT, candidates));
    return new Listing(
        selected,
        query,
        rows -> rows.forEach(row -> row.put("status", row.remove("businessStatus"))));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportRequisitions(
      String q, String filters, String conditions, boolean pending) {
    return export(selectRequisitions(q, filters, conditions, 1, EXPORT_BATCH_SIZE, true, pending));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> operations(
      String kind, String q, String filters, int page, int size, boolean export) {
    return page(selectOperations(kind, q, filters, page, size, export));
  }

  private Listing selectOperations(
      String kind, String q, String filters, int page, int size, boolean export) {
    if (!InventoryFlowService.NAMES.containsKey(kind))
      throw new LocalizedArgumentException("server.inventory.message0022");
    String menu = InventoryFlowService.menu(kind);
    permissions.require(menu, export ? "导出" : "查看");
    var query = query(q, filters, "[]", page, size);
    var candidates = warehouseRows();
    var selected =
        repository.operations(
            auth.tenantId(),
            auth.userId(),
            kind,
            query,
            permissions.inventoryWarehouses(menu, VIEW, candidates),
            export ? permissions.inventoryWarehouses(menu, EXPORT, candidates) : Set.of(),
            export);
    return new Listing(selected, query, rows -> rows.forEach(row -> row.remove("payload")));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> exportOperations(String kind, String q, String filters) {
    return Map.of("data", export(selectOperations(kind, q, filters, 1, EXPORT_BATCH_SIZE, true)));
  }
}
