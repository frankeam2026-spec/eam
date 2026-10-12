package com.windasoft.eam.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;

  static String menu(String kind) {
    return kind.equals("materials") ? "inventoryMaterial" : "inventoryWarehouse";
  }

  private final JdbcTemplate db;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NumberCounterMapper counter;
  private final ObjectMapper json;

  public InventoryService(
      JdbcTemplate db,
      AuthContext auth,
      PermissionService permissions,
      NumberCounterMapper counter,
      ObjectMapper json) {
    this.db = db;
    this.auth = auth;
    this.permissions = permissions;
    this.counter = counter;
    this.json = json;
  }

  static void require(boolean ok, String message) {
    if (!ok) throw new IllegalArgumentException(message);
  }

  static String text(Map<String, Object> body, String key) {
    return Objects.toString(body.get(key), "").trim();
  }

  static long id(Object value) {
    String raw = Objects.toString(value, "").trim();
    if (raw.isEmpty()) return 0;
    try {
      return Long.parseLong(raw);
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("请选择有效的关联记录");
    }
  }

  static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "1".equals(Objects.toString(value));
  }

  static String table(String kind) {
    return switch (kind) {
      case "materials" -> "inv_material";
      case "warehouses" -> "inv_warehouse";
      case "bins" -> "inv_bin";
      default -> throw new IllegalArgumentException("未知库存档案");
    };
  }

  Map<String, Object> row(String kind, long recordId, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM "
                + table(kind)
                + " WHERE tenant_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            auth.tenantId(),
            recordId);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
    return rows.getFirst();
  }

  void company(long companyId, String action) {
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND org_type='公司' AND"
                    + " deleted=0 AND status=1",
                Integer.class,
                auth.tenantId(),
                companyId)
            == 1,
        "请选择有效公司");
    var scope = permissions.scope("inventoryWarehouse", action);
    if (!scope.all() && !Objects.equals(permissions.companyOfUser(), companyId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有该公司的仓库维护权限");
  }

  void access(String kind, Map<String, Object> row, String action) {
    permissions.require(menu(kind), action);
    if (kind.equals("warehouses"))
      permissions.checkInventoryWarehouse("inventoryWarehouse", id(row.get("id")), action);
    if (kind.equals("bins"))
      permissions.checkInventoryWarehouse(
          "inventoryWarehouse", id(row.get("warehouse_id")), action);
  }

  Map<String, Object> view(Map<String, Object> raw) {
    var result = new LinkedHashMap<String, Object>();
    raw.forEach(
        (key, value) -> {
          StringBuilder name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toCharArray()) {
            if (c == '_') upper = true;
            else {
              name.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
          }
          String k = name.toString();
          result.put(
              k,
              k.equals("id") || k.endsWith("Id")
                  ? (value == null ? "" : value.toString())
                  : value instanceof BigDecimal decimal ? decimal.toPlainString() : value);
        });
    // DATETIME filters use MySQL's business calendar text, independent of JVM timezone.
    for (String field : List.of("CreatedAt", "ConfirmedAt"))
      if (result.containsKey("fmt" + field))
        result.put(
            Character.toLowerCase(field.charAt(0)) + field.substring(1),
            result.remove("fmt" + field));
    for (String key : List.of("enabled", "batchEnabled", "expiryEnabled"))
      if (result.containsKey(key)) result.put(key, flag(result.get(key)));
    return result;
  }

  List<Map<String, Object>> matching(String kind, String q, String filterJson) {
    permissions.require(menu(kind), "查看");
    table(kind);
    Map<String, String> filters;
    require(filterJson.length() <= 4000 && q.length() <= 200, "查询条件过长");
    try {
      filters =
          json.readValue(
              filterJson,
              new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    require(filters != null, "筛选条件无效");
    // Permission checks precede pagination so inaccessible warehouses cannot affect counts.
    var visible = new ArrayList<Map<String, Object>>();
    for (var record :
        db.queryForList(
            "SELECT * FROM " + table(kind) + " WHERE tenant_id=? ORDER BY code,id",
            auth.tenantId())) {
      try {
        access(kind, record, "查看");
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() == 403) continue;
        throw denied;
      }
      var rendered = view(record);
      if (kind.equals("materials") && !permissions.allowed("inventoryMaterial", "查看金额"))
        InventoryMoney.hide(rendered);
      if (!kind.equals("materials")) {
        String key = kind.equals("warehouses") ? "companyName" : "warehouseName";
        String source = kind.equals("warehouses") ? "eam_org_unit" : "inv_warehouse";
        Object parent = record.get(kind.equals("warehouses") ? "company_id" : "warehouse_id");
        var names =
            db.queryForList(
                "SELECT name FROM " + source + " WHERE tenant_id=? AND id=?",
                auth.tenantId(),
                parent);
        rendered.put(key, names.isEmpty() ? "" : names.getFirst().get("name"));
        if (record.get("manager_id") != null) {
          var names2 =
              db.queryForList(
                  "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?",
                  auth.tenantId(),
                  record.get("manager_id"));
          rendered.put(
              "managerName", names2.isEmpty() ? "" : names2.getFirst().get("display_name"));
        }
      }
      if (!q.isBlank()
          && !(text(record, "code") + " " + text(record, "name") + " " + text(record, "model"))
              .toLowerCase(Locale.ROOT)
              .contains(q.trim().toLowerCase(Locale.ROOT))) continue;
      boolean match = true;
      for (var filter : filters.entrySet()) {
        String key = filter.getKey(), value = Objects.toString(filter.getValue(), "").trim();
        require(
            Set.of(
                    "code",
                    "name",
                    "model",
                    "unit",
                    "category",
                    "usageType",
                    "referencePrice",
                    "batchEnabled",
                    "expiryEnabled",
                    "enabled",
                    "companyId",
                    "managerId",
                    "warehouseId",
                    "_warehouseId",
                    "allocationRule")
                .contains(key),
            "未知筛选字段");
        if (key.equals("_warehouseId")) {
          if (!text(rendered, "warehouseId").equals(value)) match = false;
          continue;
        }
        String display =
            switch (key) {
              case "companyId" -> text(rendered, "companyName");
              case "managerId" -> text(rendered, "managerName");
              case "warehouseId" -> text(rendered, "warehouseName");
              case "enabled" -> flag(rendered.get(key)) ? "启用" : "停用";
              case "batchEnabled", "expiryEnabled" -> flag(rendered.get(key)) ? "启用" : "未启用";
              case "allocationRule" ->
                  switch (text(rendered, key)) {
                    case "FIFO" -> "先进先出";
                    case "FEFO" -> "先到期先出";
                    default -> "跟随仓库";
                  };
              default -> text(rendered, key);
            };
        if (!display.contains(value)) match = false;
      }
      if (match) visible.add(rendered);
    }
    return visible;
  }

  public Map<String, Object> list(String kind, String q, String filters, int page, int size) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    var visible = matching(kind, q, filters);
    int from = (int) Math.min(((long) page - 1) * size, visible.size());
    return Map.of(
        "data",
        visible.subList(from, Math.min(from + size, visible.size())),
        "page",
        Map.of("number", page, "size", size, "total", visible.size()));
  }

  public List<Map<String, Object>> export(String kind, String q, String filters) {
    permissions.require(menu(kind), "导出");
    return matching(kind, q, filters).stream()
        .filter(
            v ->
                kind.equals("materials")
                    || permissions.inventoryWarehouseAllowed(
                        "inventoryWarehouse",
                        id(v.get(kind.equals("bins") ? "warehouseId" : "id")),
                        "导出"))
        .toList();
  }

  boolean visibleWarehouse(long warehouse) {
    for (String m :
        List.of(
            "inventoryWarehouse",
            "inventoryReceipt",
            "inventoryRequisition",
            "inventoryReturn",
            "inventoryTransfer",
            "inventoryCount",
            "inventoryOpening",
            "inventoryReversal",
            "inventoryQuery"))
      try {
        permissions.checkInventoryWarehouse(m, warehouse, "查看");
        return true;
      } catch (ResponseStatusException ignored) {
      }
    return false;
  }

  long ensureDefaultBin(long warehouseId) {
    var existing =
        db.queryForList(
            "SELECT id FROM inv_bin WHERE tenant_id=? AND warehouse_id=? ORDER BY id LIMIT 1",
            auth.tenantId(),
            warehouseId);
    if (!existing.isEmpty()) return id(existing.getFirst().get("id"));
    long binId = IdWorker.getId();
    db.update(
        "INSERT INTO inv_bin(id,tenant_id,warehouse_id,code,name) VALUES(?,?,?,'DEFAULT','默认库位')",
        binId,
        auth.tenantId(),
        warehouseId);
    return binId;
  }

  public Map<String, Object> choices() {
    require(
        permissions.menuActions().entrySet().stream()
            .anyMatch(e -> e.getKey().startsWith("inventory") && e.getValue().contains("查看")),
        "没有库存查看权限");
    var companies = new ArrayList<Map<String, Object>>();
    for (var record :
        db.queryForList(
            "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND org_type='公司' AND deleted=0 AND"
                + " status=1 ORDER BY name",
            auth.tenantId())) {
      try {
        // Company choices are only metadata; warehouse choices below enforce each menu scope.
        if (!permissions.allowed("inventoryWarehouse", "新增")
            && !Objects.equals(permissions.companyOfUser(), id(record.get("id")))
            && !permissions.allowed("inventoryWarehouse", "查看")) continue;
        companies.add(view(record));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    var users = new ArrayList<Map<String, Object>>();
    for (var record :
        db.queryForList(
            "SELECT id,display_name name,org_id FROM eam_sys_user WHERE tenant_id=? AND deleted=0"
                + " AND status=1 ORDER BY display_name",
            auth.tenantId())) {
      Long companyId = permissions.companyOfOrganization(id(record.get("org_id")));
      if (companyId != null && companies.stream().anyMatch(c -> id(c.get("id")) == companyId)) {
        var v = view(record);
        v.put("companyId", companyId.toString());
        users.add(v);
      }
    }
    var warehouses = new ArrayList<Map<String, Object>>();
    for (var record :
        db.queryForList(
            "SELECT * FROM inv_warehouse WHERE tenant_id=? AND enabled=1 ORDER BY name",
            auth.tenantId())) {
      try {
        if (!visibleWarehouse(id(record.get("id")))) continue;
        warehouses.add(view(record));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    return Map.of("companies", companies, "users", users, "warehouses", warehouses);
  }

  public Map<String, Object> stockChoices() {
    return stockChoices(true);
  }

  public Map<String, Object> stockChoices(boolean includeMaterials) {
    var result = new LinkedHashMap<String, Object>(choices());
    result.put(
        "materials",
        includeMaterials ? db
            .queryForList(
                "SELECT * FROM inv_material WHERE tenant_id=? AND enabled=1 ORDER BY code",
                auth.tenantId())
            .stream()
            .map(
                row -> {
                  var v = view(row);
                  if (!permissions.allowed("inventoryMaterial", "查看金额")) InventoryMoney.hide(v);
                  return v;
                })
            .toList() : List.of());
    var bins = new ArrayList<Map<String, Object>>();
    for (var bin :
        db.queryForList(
            "SELECT * FROM inv_bin WHERE tenant_id=? AND enabled=1 ORDER BY code",
            auth.tenantId())) {
      try {
        if (!visibleWarehouse(id(bin.get("warehouse_id")))) continue;
        bins.add(view(bin));
      } catch (org.springframework.web.server.ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      }
    }
    result.put("bins", bins);
    return result;
  }

  String number(String kind) {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      if (kind.equals("materials")) {
        // Lifetime sequence per tenant, never reset by calendar month.
        counter.nextMonthlyNumber("inv-materials-lifetime-" + auth.tenantId(), "all");
        code = "WL" + String.format("%05d", counter.currentValue());
      } else {
        counter.nextMonthlyNumber("inv-" + kind + "-" + auth.tenantId(), month);
        code = (kind.equals("warehouses") ? "CK" : "KW") + "-" + month + "-"
            + String.format("%04d", counter.currentValue());
      }
    } while (db.queryForObject(
            "SELECT COUNT(*) FROM " + table(kind) + " WHERE tenant_id=? AND code=?",
            Integer.class,
            auth.tenantId(),
            code)
        > 0);
    return code;
  }

  @Transactional
  public Map<String, Object> save(String kind, Long recordId, Map<String, Object> body) {
    table(kind);
    mutex.enter();
    String action = recordId == null ? "新增" : "编辑";
    permissions.require(menu(kind), action);
    Map<String, Object> previous = recordId == null ? null : row(kind, recordId, true);
    if (previous != null) {
      access(kind, previous, action);
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    var fields = new LinkedHashMap<String, Object>();
    String name = text(body, "name"), code = text(body, "code");
    require(!name.isBlank() && name.length() <= 120, "名称须为1至120字");
    if (code.isBlank()) code = previous == null ? number(kind) : text(previous, "code");
    require(code.length() <= 64, "编码不能超过64字");
    require(text(body, "remark").length() <= 1000, "备注不能超过1000字");
    fields.put("code", code);
    fields.put("name", name);
    fields.put("remark", text(body, "remark"));
    fields.put("enabled", flag(body.get("enabled")) ? 1 : 0);
    if (kind.equals("materials")) {
      if (!permissions.allowed("inventoryMaterial", "编辑参考价")) {
        require(
            text(body, "referencePrice").isBlank()
                || (previous != null
                    && previous.get("reference_price") != null
                    && new BigDecimal(text(body, "referencePrice"))
                            .compareTo((BigDecimal) previous.get("reference_price"))
                        == 0),
            "没有编辑参考价权限");
        if (previous != null) body.put("referencePrice", previous.get("reference_price"));
      }
      require(!text(body, "unit").isBlank() && text(body, "unit").length() <= 40, "请填写有效单位");
      require(
          text(body, "model").length() <= 200 && text(body, "category").length() <= 120, "型号或分类过长");
      require(Set.of("消耗型", "可安装").contains(text(body, "usageType")), "请选择物料用途");
      boolean batch = flag(body.get("batchEnabled")), expiry = flag(body.get("expiryEnabled"));
      require(!expiry || batch, "有效期管理必须启用批次");
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
                        + " (quantity<>0 OR reserved_quantity<>0)",
                    Integer.class,
                    auth.tenantId(),
                    recordId)
                == 0,
            "存在库存或预留，不能直接修改追踪规则或用途");
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_legacy_stock_map m JOIN eam_spare_stock s ON"
                        + " s.tenant_id=m.tenant_id AND s.id=m.stock_id WHERE m.tenant_id=? AND"
                        + " m.material_id=? AND s.quantity<>0",
                    Integer.class,
                    auth.tenantId(),
                    recordId)
                == 0,
            "旧库存尚未完成初始化，不能修改追踪规则");
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_receipt_line l JOIN inv_receipt r ON"
                        + " r.tenant_id=l.tenant_id AND r.id=l.receipt_id WHERE l.tenant_id=? AND"
                        + " l.material_id=? AND r.deleted=0 AND r.status='草稿'",
                    Integer.class,
                    auth.tenantId(),
                    recordId)
                == 0,
            "存在未确认入库单，不能修改追踪规则或用途");
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_requisition_line l JOIN inv_requisition r ON"
                        + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=?"
                        + " AND l.material_id=? AND r.deleted=0 AND r.status NOT IN"
                        + " ('已取消','已完成','已关闭')",
                    Integer.class,
                    auth.tenantId(),
                    recordId)
                == 0,
            "存在未完成领料申请，不能修改追踪规则或用途");
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_operation d WHERE d.tenant_id=? AND d.deleted=0 AND"
                        + " d.status IN ('草稿','在途') AND"
                        + " (JSON_CONTAINS(d.payload,JSON_OBJECT('materialId',?), '$.lines') OR"
                        + " EXISTS(SELECT 1 FROM inv_movement_allocation a JOIN inv_balance s ON"
                        + " s.tenant_id=a.tenant_id AND s.id=a.balance_id WHERE"
                        + " a.tenant_id=d.tenant_id AND a.document_id=d.id AND s.material_id=?))",
                    Integer.class,
                    auth.tenantId(),
                    Long.toString(recordId),
                    recordId)
                == 0,
            "存在未完成库存单据或在途库存，不能修改追踪规则或用途");
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM inv_usage WHERE tenant_id=? AND material_id=? AND"
                        + " status='在用'",
                    Integer.class,
                    auth.tenantId(),
                    recordId)
                == 0,
            "设备仍有在用备件，不能更改物料用途或追踪规则");
      }
      BigDecimal price =
          text(body, "referencePrice").isBlank()
              ? null
              : new BigDecimal(text(body, "referencePrice"));
      require(
          price == null
              || (price.signum() >= 0
                  && price.scale() <= 6
                  && price.precision() - price.scale() <= 14),
          "参考价须为非负数，最多6位小数");
      String rule = text(body, "allocationRule");
      require(Set.of("", "FIFO", "FEFO").contains(rule), "出库分配规则无效");
      fields.put("model", text(body, "model"));
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND item_type='UNIT' AND"
                      + " name=? AND deleted=0 AND status=1",
                  Integer.class,
                  auth.tenantId(),
                  text(body, "unit"))
              > 0,
          "请选择基础数据中已启用的单位");
      if (!text(body, "category").isBlank())
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND"
                        + " item_type='MATERIAL_CATEGORY' AND name=? AND deleted=0 AND status=1",
                    Integer.class,
                    auth.tenantId(),
                    text(body, "category"))
                > 0,
            "请选择基础数据中已启用的物料分类");
      fields.put("unit", text(body, "unit"));
      fields.put("category", text(body, "category"));
      fields.put("usage_type", text(body, "usageType"));
      fields.put("reference_price", price);
      fields.put("batch_enabled", batch ? 1 : 0);
      fields.put("expiry_enabled", expiry ? 1 : 0);
      fields.put("allocation_rule", rule);
    } else if (kind.equals("warehouses")) {
      long companyId = id(body.get("companyId"));
      if (previous == null || id(previous.get("company_id")) != companyId)
        company(companyId, action);
      if (previous != null) require(id(previous.get("company_id")) == companyId, "仓库所属公司不能直接变更");
      String rule = text(body, "allocationRule");
      require(Set.of("FIFO", "FEFO").contains(rule), "请选择出库分配规则");
      Long manager = text(body, "managerId").isBlank() ? null : id(body.get("managerId"));
      if (manager != null) {
        var users =
            db.queryForList(
                "SELECT org_id FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
                    + " status=1",
                auth.tenantId(),
                manager);
        require(
            users.size() == 1
                && Objects.equals(
                    permissions.companyOfOrganization(id(users.getFirst().get("org_id"))),
                    companyId),
            "仓管必须是本公司的有效用户");
      }
      fields.put("company_id", companyId);
      fields.put("manager_id", manager);
      fields.put("allocation_rule", rule);
    } else {
      long warehouseId = id(body.get("warehouseId"));
      var warehouse = row("warehouses", warehouseId, true);
      access("warehouses", warehouse, action);
      require(flag(warehouse.get("enabled")), "仓库已停用");
      if (previous != null)
        require(id(previous.get("warehouse_id")) == warehouseId, "库位不能直接变更所属仓库");
      fields.put("warehouse_id", warehouseId);
    }
    long target = recordId == null ? IdWorker.getId() : recordId;
    var params = new ArrayList<Object>(fields.values());
    if (previous == null) {
      params.add(target);
      params.add(auth.tenantId());
      db.update(
          "INSERT INTO "
              + table(kind)
              + " ("
              + String.join(",", fields.keySet())
              + ",id,tenant_id) VALUES ("
              + String.join(",", Collections.nCopies(params.size(), "?"))
              + ")",
          params.toArray());
    } else {
      params.add(auth.tenantId());
      params.add(target);
      db.update(
          "UPDATE "
              + table(kind)
              + " SET "
              + String.join(",", fields.keySet().stream().map(k -> k + "=?").toList())
              + ",row_version=row_version+1 WHERE tenant_id=? AND id=?",
          params.toArray());
    }
    try {
      db.update(
          "INSERT INTO"
              + " inv_master_event(id,tenant_id,resource_kind,resource_id,action,actor_id,actor_name,details_json)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          kind,
          target,
          action,
          auth.userId(),
          auth.user().getDisplayName(),
          json.writeValueAsString(
              Map.of(
                  "before", previous == null ? Map.of() : view(previous), "after", view(fields))));
    } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
      throw new IllegalStateException("无法保存操作记录", e);
    }
    if (kind.equals("warehouses")) ensureDefaultBin(target);
    var output = view(row(kind, target, false));
    if (kind.equals("materials") && !permissions.allowed("inventoryMaterial", "查看金额"))
      InventoryMoney.hide(output);
    return output;
  }
}
