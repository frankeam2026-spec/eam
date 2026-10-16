package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.InventoryQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryQueryService {
  private final InventoryQueryRepository repository;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public InventoryQueryService(
      InventoryQueryRepository repository,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  private InventoryQueryRepository.Query query(String kind, String q, String filterJson) {
    permissions.require("inventoryQuery", "查看");
    require(q.length() <= 200 && filterJson.length() <= 6000, "查询条件过长");
    var warehouses = new ArrayList<Long>();
    for (var row : repository.warehouseIds(auth.tenantId())) {
      try {
        permissions.checkInventoryWarehouse("inventoryQuery", id(row.get("id")), "查看");
        warehouses.add(id(row.get("id")));
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      }
    }
    Map<String, String> filters;
    try {
      filters = json.readValue(filterJson, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    return repository.query(auth.tenantId(), kind, q, filters, warehouses);
  }

  public Object list(String kind, String q, String filters, int page, int size) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    var query = query(kind, q, filters);
    long total = repository.count(query);
    var rows =
        repository.list(query, kind, page, size).stream()
            .map(
                r -> {
                  var v = masters.view(r);
                  if (!permissions.inventoryWarehouseAllowed(
                      "inventoryQuery", id(r.get("warehouse_id")), "查看金额")) InventoryMoney.hide(v);
                  return v;
                })
            .toList();
    return Map.of("data", rows, "page", Map.of("total", total));
  }

  public Object export(String kind, String q, String filters) {
    permissions.require("inventoryQuery", "导出");
    var query = query(kind, q, filters);
    return repository.export(query).stream()
        .filter(
            r ->
                permissions.inventoryWarehouseAllowed(
                    "inventoryQuery", id(r.get("warehouse_id")), "导出"))
        .map(
            r -> {
              var v = masters.view(r);
              if (!permissions.inventoryWarehouseAllowed(
                  "inventoryQuery", id(r.get("warehouse_id")), "查看金额")) InventoryMoney.hide(v);
              return v;
            })
        .toList();
  }
}
