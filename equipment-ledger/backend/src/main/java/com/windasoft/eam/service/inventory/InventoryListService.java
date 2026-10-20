package com.windasoft.eam.service.inventory;

import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.repository.inventory.InventoryListRepository;
import com.windasoft.eam.service.business.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
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

  public InventoryListService(
      InventoryListRepository repository,
      InventoryViewAssembler views,
      PermissionService permissions,
      AuthContext auth,
      BusinessQueryService custom,
      BusinessValueService values) {
    this.repository = repository;
    this.views = views;
    this.permissions = permissions;
    this.auth = auth;
    this.custom = custom;
    this.values = values;
  }

  private Set<Long> warehouses(String menu, boolean export) {
    var ids = new HashSet<Long>();
    for (long id : repository.warehouseIds(auth.tenantId()))
      if (permissions.inventoryWarehouseAllowed(menu, id, "查看")
          && (!export || permissions.inventoryWarehouseAllowed(menu, id, "导出"))) ids.add(id);
    return ids;
  }

  private Set<Long> warehouseAction(String menu, String action) {
    var result = new HashSet<Long>();
    for (long id : repository.warehouseIds(auth.tenantId()))
      if (permissions.inventoryWarehouseAllowed(menu, id, action)) result.add(id);
    return result;
  }

  private ListQuery query(String q, String filters, String conditions, int page, int size) {
    if (size > 100)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0004");
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
            kind.equals("materials") ? Set.of() : warehouses(menu, export),
            money);
    var rows = repository.page(selected, query).stream().map(views::view).toList();
    if (!money) rows.forEach(InventoryMoney::hide);
    return Map.of(
        "data",
        rows,
        "page",
        Map.of("number", page, "size", size, "total", repository.count(selected)));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportMasters(String kind, String q, String filters) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var batch = masters(kind, q, filters, page, 100, true);
      var rows = (List<Map<String, Object>>) batch.get("data");
      result.addAll(rows);
      if ((long) page * 100 >= ((Number) ((Map<?, ?>) batch.get("page")).get("total")).longValue())
        return result;
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> receipts(
      String q, String filters, String conditions, int page, int size, boolean export) {
    String menu = "inventoryReceipt";
    permissions.require(menu, export ? "导出" : "查看");
    permissions.require(menu, "查看");
    var query = query(q, filters, conditions, page, size);
    var selected =
        repository.receipts(
            auth.tenantId(),
            query,
            warehouses(menu, export),
            custom.predicate("inventory_receipt", conditions, "t"),
            custom.orderExpression("inventory_receipt", query.sortBy(), "t"));
    var rows = repository.page(selected, query).stream().map(views::view).toList();
    var moneyWarehouses = warehouseAction(menu, "查看金额");
    for (var row : rows)
      if (!moneyWarehouses.contains(InventoryService.id(row.get("warehouseId"))))
        InventoryMoney.hide(row);
    values.attach("inventory_receipt", rows);
    return Map.of(
        "data",
        rows,
        "page",
        Map.of("number", page, "size", size, "total", repository.count(selected)));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportReceipts(String q, String filters, String conditions) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var batch = receipts(q, filters, conditions, page, 100, true);
      result.addAll((List<Map<String, Object>>) batch.get("data"));
      if ((long) page * 100 >= ((Number) ((Map<?, ?>) batch.get("page")).get("total")).longValue())
        return result;
    }
  }

  private boolean usesMoney(String filters, String conditions) {
    try {
      var json = new com.fasterxml.jackson.databind.ObjectMapper();
      if (json.readTree(filters).has("totalAmount")) return true;
      for (var condition : json.readTree(conditions))
        if (condition.path("field").asText().equals("totalAmount")) return true;
      return false;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0009");
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> outbounds(
      String q, String filters, String conditions, int page, int size, boolean export) {
    String menu = "inventoryOutbound";
    permissions.require(menu, export ? "导出" : "查看");
    permissions.require(menu, "查看");
    var query = query(q, filters, conditions, page, size);
    var allowed = warehouses(menu, export);
    boolean money = permissions.allowed(menu, "查看金额");
    if (usesMoney(filters, conditions)) {
      permissions.require(menu, "查看金额");
      allowed.removeIf(id -> !permissions.inventoryWarehouseAllowed(menu, id, "查看金额"));
    }
    var selected =
        repository.outbounds(
            auth.tenantId(),
            query,
            allowed,
            money,
            custom.predicate("inventory_outbound", conditions, "t"),
            custom.orderExpression("inventory_outbound", query.sortBy(), "t"));
    var rows = repository.page(selected, query).stream().map(views::view).toList();
    var moneyWarehouses = warehouseAction(menu, "查看金额");
    for (var row : rows) {
      row.remove("legacy");
      if (!moneyWarehouses.contains(InventoryService.id(row.get("warehouseId"))))
        InventoryMoney.hide(row);
    }
    values.attach(
        "inventory_outbound",
        rows.stream().filter(r -> !r.get("id").toString().startsWith("g")).toList());
    return Map.of(
        "data",
        rows,
        "page",
        Map.of("number", page, "size", size, "total", repository.count(selected)));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportOutbounds(String q, String filters, String conditions) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var batch = outbounds(q, filters, conditions, page, 100, true);
      result.addAll((List<Map<String, Object>>) batch.get("data"));
      if ((long) page * 100 >= ((Number) ((Map<?, ?>) batch.get("page")).get("total")).longValue())
        return result;
    }
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
    Set<Long> allowed;
    String menu = pending ? "inventoryOutbound" : "inventoryRequisition";
    if (pending) {
      permissions.require(menu, export ? "导出" : "查看");
      allowed = warehouses(menu, export);
    } else {
      allowed = warehouses("inventoryRequisition", false);
      allowed.addAll(warehouses("inventoryOutbound", false));
      if (export) permissions.require(menu, "导出");
    }
    var query = query(q, filters, conditions, page, size);
    var selected =
        repository.requisitions(auth.tenantId(), auth.userId(), query, allowed, !pending, pending);
    if (export && !pending)
      selected = repository.restrictWarehouse(selected, warehouseAction(menu, "导出"));
    var rows = repository.page(selected, query).stream().map(views::view).toList();
    rows.forEach(row -> row.put("status", row.remove("businessStatus")));
    return Map.of(
        "data",
        rows,
        "page",
        Map.of("number", page, "size", size, "total", repository.count(selected)));
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportRequisitions(
      String q, String filters, String conditions, boolean pending) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var batch = requisitions(q, filters, conditions, page, 100, true, pending);
      result.addAll((List<Map<String, Object>>) batch.get("data"));
      if ((long) page * 100 >= ((Number) ((Map<?, ?>) batch.get("page")).get("total")).longValue())
        return result;
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> operations(
      String kind, String q, String filters, int page, int size, boolean export) {
    if (!InventoryFlowService.NAMES.containsKey(kind))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0022");
    String menu = InventoryFlowService.menu(kind);
    permissions.require(menu, export ? "导出" : "查看");
    var query = query(q, filters, "[]", page, size);
    var selected =
        repository.operations(
            auth.tenantId(),
            auth.userId(),
            kind,
            query,
            warehouseAction(menu, "查看"),
            export ? warehouseAction(menu, "导出") : Set.of(),
            export);
    var rows = repository.page(selected, query).stream().map(views::view).toList();
    rows.forEach(row -> row.remove("payload"));
    return Map.of(
        "data",
        rows,
        "page",
        Map.of("number", page, "size", size, "total", repository.count(selected)));
  }

  @Transactional(readOnly = true)
  public Map<String, Object> exportOperations(String kind, String q, String filters) {
    var result = new ArrayList<Map<String, Object>>();
    for (int page = 1; ; page++) {
      var batch = operations(kind, q, filters, page, 100, true);
      result.addAll((List<Map<String, Object>>) batch.get("data"));
      if ((long) page * 100 >= ((Number) ((Map<?, ?>) batch.get("page")).get("total")).longValue())
        return Map.of("data", result);
    }
  }
}
