package com.windasoft.eam.inventory;

import static com.windasoft.eam.inventory.InventoryService.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryQueryService {
  private final JdbcTemplate db;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public InventoryQueryService(
      JdbcTemplate db,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.db = db;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  private record Query(String sql, List<Object> args, Map<String, String> fields) {}

  private Query query(String kind, String q, String filterJson) {
    permissions.require("inventoryQuery", "查看");
    require(q.length() <= 200 && filterJson.length() <= 6000, "查询条件过长");
    var warehouses = new ArrayList<Long>();
    for (var row :
        db.queryForList(
            "SELECT id,company_id FROM inv_warehouse WHERE tenant_id=? ORDER BY id",
            auth.tenantId())) {
      try {
        permissions.checkInventoryWarehouse("inventoryQuery", id(row.get("id")), "查看");
        warehouses.add(id(row.get("id")));
      } catch (ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      }
    }
    var fields = new LinkedHashMap<String, String>();
    fields.put("materialCode", "m.code");
    fields.put("materialName", "m.name");
    fields.put("model", "m.model");
    fields.put("unit", "m.unit");
    fields.put("companyName", "o.name");
    fields.put("warehouseName", "w.name");
    fields.put("binName", "b.name");
    fields.put("batchCode", "COALESCE(pc.code,'')");
    fields.put("supplierBatch", "COALESCE(pc.supplier_batch,'')");
    fields.put("productionDate", "COALESCE(CAST(pc.production_date AS CHAR),'')");
    fields.put("expiryDate", "COALESCE(CAST(pc.expiry_date AS CHAR),'')");
    String extra;
    switch (kind) {
      case "balances" -> {
        fields.put("stockStatus", "s.stock_status");
        fields.put("quantity", "CAST(s.quantity AS CHAR)");
        fields.put("reservedQuantity", "CAST(s.reserved_quantity AS CHAR)");
        extra =
            "s.id,s.row_version,s.quantity,s.reserved_quantity,s.stock_status,CASE WHEN"
                + " s.stock_status='可用' AND m.enabled=1 AND w.enabled=1 AND b.enabled=1 AND"
                + " (pc.expiry_date IS NULL OR pc.expiry_date>=?) AND (m.expiry_enabled=0 OR"
                + " pc.expiry_date IS NOT NULL) THEN s.quantity-s.reserved_quantity ELSE 0 END"
                + " available_quantity,CASE WHEN pc.expiry_date<? THEN '已过期' WHEN"
                + " m.expiry_enabled=1 AND pc.expiry_date IS NULL THEN '待补有效期' ELSE '' END"
                + " expiry_status,(SELECT CASE WHEN COUNT(*)=COUNT(c.remaining_cost) THEN"
                + " SUM(c.remaining_cost) ELSE NULL END FROM inv_cost_layer c WHERE"
                + " c.tenant_id=s.tenant_id AND c.balance_id=s.id) remaining_cost";
      }
      case "layers" -> {
        fields.put("sourceCode", "COALESCE(r.code,d.code)");
        fields.put("costStatus", "c.cost_status");
        fields.put("receivedAt", "CAST(c.origin_received_at AS CHAR)");
        extra =
            "c.id,c.source_line_id,c.origin_received_at"
                + " received_at,c.quantity,c.remaining_quantity,c.unit_cost,c.remaining_cost,c.cost_status,c.currency,COALESCE(r.code,d.code)"
                + " source_code,COALESCE(r.id,d.id) source_id,COALESCE(d.kind,'receipt')"
                + " source_kind";
      }
      case "transactions" -> {
        fields.put("sourceCode", "COALESCE(r.code,i.code,d.code)");
        fields.put("kind", "t.kind");
        fields.put("createdAt", "CAST(t.created_at AS CHAR)");
        fields.put("actorName", "u.display_name");
        extra =
            "t.id,t.kind,t.quantity,t.amount,t.created_at,u.display_name"
                + " actor_name,COALESCE(r.code,i.code,d.code)"
                + " source_code,COALESCE(r.id,i.id,d.id) source_id,CASE WHEN d.id IS"
                + " NOT NULL THEN d.kind WHEN i.id IS NULL THEN 'receipt' ELSE 'issue' END"
                + " source_kind";
      }
      case "transit" -> {
        fields.put("sourceCode", "d.code");
        fields.put("targetWarehouseName", "tw.name");
        extra =
            "a.id,d.code source_code,d.id source_id,'transfers' source_kind,-a.quantity"
                + " quantity,-a.amount amount,tw.name target_warehouse_name,d.confirmed_at"
                + " created_at";
      }
      default -> throw new IllegalArgumentException("未知库存查询");
    }
    var args = new ArrayList<Object>();
    if (kind.equals("balances")) {
      var today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
      args.add(today);
      args.add(today);
    }
    String sql =
        "SELECT "
            + extra
            + ",s.warehouse_id,m.code material_code,m.name material_name,m.model,m.unit,o.name"
            + " company_name,w.name warehouse_name,b.name bin_name,pc.code"
            + " batch_code,pc.supplier_batch,pc.production_date,pc.expiry_date FROM inv_balance s"
            + " JOIN inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id JOIN"
            + " inv_warehouse w ON w.tenant_id=s.tenant_id AND w.id=s.warehouse_id JOIN"
            + " eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id JOIN inv_bin b ON"
            + " b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN inv_batch pc ON"
            + " pc.tenant_id=s.tenant_id AND pc.id=s.batch_id";
    if (kind.equals("layers"))
      sql +=
          " JOIN inv_cost_layer c ON c.tenant_id=s.tenant_id AND c.balance_id=s.id LEFT JOIN"
              + " inv_receipt_line l ON l.tenant_id=c.tenant_id AND l.id=c.source_line_id LEFT JOIN"
              + " inv_receipt r ON r.tenant_id=l.tenant_id AND r.id=l.receipt_id LEFT JOIN"
              + " inv_operation d ON d.tenant_id=c.tenant_id AND d.id=c.source_line_id";
    if (kind.equals("transactions"))
      sql +=
          " JOIN inv_transaction t ON t.tenant_id=s.tenant_id AND t.balance_id=s.id LEFT JOIN"
              + " inv_receipt r ON r.tenant_id=t.tenant_id AND r.id=t.document_id AND t.kind='入库'"
              + " LEFT JOIN inv_issue i ON i.tenant_id=t.tenant_id AND i.id=t.document_id AND"
              + " t.kind='出库' LEFT JOIN inv_operation d ON d.tenant_id=t.tenant_id AND"
              + " d.id=t.document_id LEFT JOIN eam_sys_user u ON u.tenant_id=t.tenant_id AND"
              + " u.id=t.actor_id";
    if (kind.equals("transit"))
      sql +=
          " JOIN inv_movement_allocation a ON a.tenant_id=s.tenant_id AND a.balance_id=s.id AND"
              + " a.quantity<0 JOIN inv_operation d ON d.tenant_id=a.tenant_id AND"
              + " d.id=a.document_id AND d.kind='transfers' AND d.status='在途' JOIN inv_warehouse tw"
              + " ON tw.tenant_id=d.tenant_id AND tw.id=d.target_warehouse_id";
    sql += " WHERE s.tenant_id=?";
    args.add(auth.tenantId());
    if (warehouses.isEmpty()) sql += " AND 1=0";
    else {
      sql +=
          " AND s.warehouse_id IN ("
              + String.join(",", Collections.nCopies(warehouses.size(), "?"))
              + ")";
      args.addAll(warehouses);
    }
    if (!q.isBlank()) {
      sql +=
          " AND (m.code LIKE ? ESCAPE '!' OR m.name LIKE ? ESCAPE '!' OR m.model LIKE ? ESCAPE '!'"
              + " OR pc.code LIKE ? ESCAPE '!')";
      for (int i = 0; i < 4; i++) args.add(like(q));
    }
    Map<String, String> filters;
    try {
      filters = json.readValue(filterJson, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    require(filters != null && fields.keySet().containsAll(filters.keySet()), "筛选字段无效");
    for (var filter : filters.entrySet())
      if (filter.getValue() != null && !filter.getValue().isBlank()) {
        sql += " AND " + fields.get(filter.getKey()) + " LIKE ? ESCAPE '!'";
        args.add(like(filter.getValue()));
      }
    return new Query(sql, args, fields);
  }

  private String like(String value) {
    return "%" + value.trim().replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
  }

  public Object list(String kind, String q, String filters, int page, int size) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    var query = query(kind, q, filters);
    long total =
        db.queryForObject(
            "SELECT COUNT(*) FROM (" + query.sql() + ") results",
            Long.class,
            query.args().toArray());
    var args = new ArrayList<Object>(query.args());
    args.add(size);
    args.add(((long) page - 1) * size);
    String order =
        kind.equals("transit")
            ? "d.confirmed_at DESC,a.id DESC"
            : kind.equals("balances")
                ? "s.id DESC"
                : kind.equals("layers")
                    ? "c.origin_received_at DESC,c.id DESC"
                    : "t.created_at DESC,t.id DESC";
    var rows =
        db
            .queryForList(query.sql() + " ORDER BY " + order + " LIMIT ? OFFSET ?", args.toArray())
            .stream()
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
    return db.queryForList(query.sql() + " ORDER BY s.id", query.args().toArray()).stream()
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
