package com.windasoft.eam.inventory;

import static com.windasoft.eam.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.system.*;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryFlowService {
  private final JdbcTemplate db;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NumberCounterMapper counter;
  private final ObjectMapper json;
  private final ReceiptService receipts;

  static String menu(String kind) {
    return "inventory"
        + Map.of(
                "returns",
                "Return",
                "transfers",
                "Transfer",
                "counts",
                "Count",
                "openings",
                "Opening",
                "reversals",
                "Reversal")
            .get(kind);
  }

  static final Map<String, String> NAMES =
      Map.of(
          "returns", "退料", "transfers", "调拨", "counts", "盘点", "openings", "期初", "reversals", "冲销");

  public InventoryFlowService(
      JdbcTemplate db,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      NumberCounterMapper counter,
      ObjectMapper json,
      ReceiptService receipts) {
    this.db = db;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.counter = counter;
    this.json = json;
    this.receipts = receipts;
  }

  public void lock() {
    db.update(
        "INSERT INTO inv_tenant_lock(tenant_id) VALUES(?) ON DUPLICATE KEY UPDATE"
            + " tenant_id=tenant_id",
        auth.tenantId());
    db.queryForMap(
        "SELECT tenant_id FROM inv_tenant_lock WHERE tenant_id=? FOR UPDATE", auth.tenantId());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("单据数据无效", e);
    }
  }

  private Map<String, Object> payload(Map<String, Object> row) {
    try {
      return json.readValue(text(row, "payload"), new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("单据明细不可读取", e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> lines(Map<String, Object> body) {
    require(body.get("lines") instanceof List<?>, "请添加明细");
    var rows = (List<?>) body.get("lines");
    require(!rows.isEmpty() && rows.size() <= 500, "明细须为1至500行");
    for (Object r : rows) require(r instanceof Map<?, ?>, "明细格式无效");
    return (List<Map<String, Object>>) rows;
  }

  static BigDecimal decimal(Object value, boolean zero) {
    try {
      var n = new BigDecimal(Objects.toString(value, ""));
      require(
          n.scale() <= 6
              && n.precision() - n.scale() <= 14
              && (zero ? n.signum() >= 0 : n.signum() > 0),
          "数值必须" + (zero ? "非负" : "大于零") + "，最多6位小数");
      return n;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("数量或单价格式无效");
    }
  }

  private Map<String, Object> one(String sql, Object... args) {
    var rows = db.queryForList(sql, args);
    require(rows.size() == 1, "来源记录不存在或已变化");
    return rows.getFirst();
  }

  private Map<String, Object> operation(String kind, long doc, boolean locked, String action) {
    require(NAMES.containsKey(kind), "单据类型无效");
    permissions.require(menu(kind), action);
    var row =
        one(
            "SELECT * FROM inv_operation WHERE tenant_id=? AND kind=? AND id=? AND deleted=0"
                + (locked ? " FOR UPDATE" : ""),
            auth.tenantId(),
            kind,
            doc);
    long source = id(row.get("warehouse_id"));
    if (kind.equals("transfers") && row.get("target_warehouse_id") != null) {
      long target = id(row.get("target_warehouse_id"));
      if (action.equals("查看")) {
        if (!permissions.inventoryWarehouseAllowed(menu(kind), target, action))
          permissions.checkInventoryWarehouse(menu(kind), source, action);
      } else
        permissions.checkInventoryWarehouse(
            menu(kind), action.equals("入库") ? target : source, action);
    } else permissions.checkInventoryWarehouse(menu(kind), source, action);
    return row;
  }

  private Map<String, Object> checkWarehouse(long warehouse, String action) {
    var w = masters.row("warehouses", warehouse, false);
    return w;
  }

  private void event(long document, String kind, String action) {
    db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,?,?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        NAMES.getOrDefault(kind, kind.equals("receipt") ? "receipt" : kind),
        document,
        action,
        auth.userId(),
        auth.user().getDisplayName());
  }

  private String number(String kind) {
    var month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String prefix =
        Map.of(
                "returns",
                "TL",
                "transfers",
                "DB",
                "counts",
                "PD",
                "openings",
                "QC",
                "reversals",
                "CX")
            .get(kind);
    counter.nextMonthlyNumber("inv-" + prefix + "-" + auth.tenantId(), month);
    return prefix + "-" + month + "-" + String.format("%04d", counter.currentValue());
  }

  public Map<String, Object> detail(String kind, long doc) {
    var raw = operation(kind, doc, false, "查看");
    var value = masters.view(raw);
    value.remove("payload");
    value.put("form", payload(raw));
    value.put(
        "warehouseName", masters.row("warehouses", id(raw.get("warehouse_id")), false).get("name"));
    value.put(
        "movements",
        db
            .queryForList(
                "SELECT a.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
                    + " inv_movement_allocation a JOIN inv_balance s ON s.tenant_id=a.tenant_id AND"
                    + " s.id=a.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
                    + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
                    + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
                    + " pc.id=s.batch_id WHERE a.tenant_id=? AND a.document_id=? ORDER BY a.id",
                auth.tenantId(),
                doc)
            .stream()
            .map(masters::view)
            .toList());
    value.put(
        "events",
        db
            .queryForList(
                "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
                    + " document_id=? ORDER BY created_at,id",
                auth.tenantId(),
                doc)
            .stream()
            .map(masters::view)
            .toList());
    if (kind.equals("counts")) {
      for (var line : lines((Map<String, Object>) value.get("form"))) {
        var b = balance(id(line.get("balanceId")), false);
        var base = decimal(line.get("baselineQuantity"), true);
        var movement = ((BigDecimal) b.get("quantity")).subtract(base);
        line.put("periodMovement", movement.toPlainString());
        line.put("currentQuantity", Objects.toString(b.get("quantity")));
      }
    }
    if (!permissions.inventoryWarehouseAllowed(menu(kind), id(raw.get("warehouse_id")), "查看金额"))
      InventoryMoney.hide(value);
    return value;
  }

  public Object list(String kind, String q, String filters, int page, int size, boolean export) {
    require(NAMES.containsKey(kind) && page > 0 && size > 0 && size <= 100, "查询参数无效");
    permissions.require(menu(kind), export ? "导出" : "查看");
    require(q.length() <= 200 && filters.length() <= 4000, "筛选条件过长");
    Map<String, String> f;
    try {
      f = json.readValue(filters, new TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    require(
        f != null && Set.of("code", "warehouseName", "status", "createdAt").containsAll(f.keySet()),
        "筛选字段无效");
    var values = new ArrayList<Map<String, Object>>();
    for (var r :
        db.queryForList(
            "SELECT d.*,DATE_FORMAT(d.created_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_created_at,DATE_FORMAT(d.confirmed_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_confirmed_at,w.name warehouse_name FROM inv_operation d JOIN inv_warehouse"
                + " w ON w.tenant_id=d.tenant_id AND w.id=d.warehouse_id WHERE d.tenant_id=? AND"
                + " d.kind=? AND d.deleted=0 ORDER BY d.created_at DESC,d.id DESC",
            auth.tenantId(), kind)) {
      try {
        operation(kind, id(r.get("id")), false, "查看");
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() == 403) continue;
        throw denied;
      }
      var v = masters.view(r);
      v.remove("payload");
      if (!(text(v, "code") + " " + text(v, "warehouseName") + " " + text(v, "status"))
          .contains(q.trim())) continue;
      if (f.entrySet().stream()
          .allMatch(
              e ->
                  text(v, e.getKey())
                      .replace('T', ' ')
                      .contains(Objects.toString(e.getValue(), "").trim()))) values.add(v);
    }
    if (export) {
      values.removeIf(
          v -> !permissions.inventoryWarehouseAllowed(menu(kind), id(v.get("warehouseId")), "导出"));
      return Map.of("data", values);
    }
    int from = (int) Math.min(((long) page - 1) * size, values.size());
    return Map.of(
        "data",
        values.subList(from, Math.min(from + size, values.size())),
        "page",
        Map.of("total", values.size()));
  }

  public Object choices(String kind) {
    permissions.require(menu(kind), "查看");
    require(NAMES.containsKey(kind), "单据类型无效");
    @SuppressWarnings("unchecked")
    var result = new LinkedHashMap<String, Object>((Map<String, Object>) masters.stockChoices());
    result.put(
        "warehouses",
        ((List<Map<String, Object>>) result.get("warehouses"))
            .stream()
                .filter(
                    w -> permissions.inventoryWarehouseAllowed(menu(kind), id(w.get("id")), "查看"))
                .toList());
    var balances = new ArrayList<Map<String, Object>>();
    for (var b :
        db.queryForList(
            "SELECT s.*,m.code material_code,m.name material_name,m.unit,b.name bin_name,pc.code"
                + " batch_code FROM inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id"
                + " AND m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
                + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
                + " pc.id=s.batch_id WHERE s.tenant_id=? ORDER BY"
                + " s.warehouse_id,m.code,b.code,s.id",
            auth.tenantId())) {
      try {
        permissions.checkInventoryWarehouse(menu(kind), id(b.get("warehouse_id")), "查看");
        balances.add(masters.view(b));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    result.put("balances", balances);
    var allocations = new ArrayList<Map<String, Object>>();
    for (var a :
        db.queryForList(
            "SELECT a.*,a.quantity-a.used_quantity-a.returned_quantity available_quantity,i.code"
                + " issue_code,i.warehouse_id,i.company_id,COALESCE(ol.task_kind,r.task_kind)"
                + " task_kind,COALESCE(ol.task_code,r.task_code) task_code,m.name"
                + " material_name,m.unit,s.material_id,s.batch_id,pc.code batch_code FROM"
                + " inv_issue_allocation a JOIN inv_issue i ON i.tenant_id=a.tenant_id AND"
                + " i.id=a.issue_id LEFT JOIN inv_outbound_line ol ON ol.tenant_id=a.tenant_id AND"
                + " ol.id=a.outbound_line_id LEFT JOIN inv_requisition_line rl ON"
                + " rl.tenant_id=a.tenant_id AND rl.id=a.requisition_line_id LEFT JOIN"
                + " inv_requisition r ON r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id JOIN"
                + " inv_balance s ON s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN"
                + " inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id LEFT JOIN"
                + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
                + " a.tenant_id=? AND i.reversed=0 AND"
                + " a.quantity>a.used_quantity+a.returned_quantity ORDER BY i.created_at DESC,a.id",
            auth.tenantId())) {
      try {
        permissions.checkInventoryWarehouse(menu(kind), id(a.get("warehouse_id")), "查看");
        var item = masters.view(a);
        if (!permissions.inventoryWarehouseAllowed(menu(kind), id(a.get("warehouse_id")), "查看金额"))
          InventoryMoney.hide(item);
        allocations.add(item);
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    result.put("allocations", allocations);
    result.put(
        "receipts",
        db
            .queryForList(
                "SELECT id,code,warehouse_id FROM inv_receipt WHERE tenant_id=? AND status='已入库'"
                    + " AND deleted=0",
                auth.tenantId())
            .stream()
            .filter(
                r -> {
                  try {
                    permissions.checkInventoryWarehouse(
                        menu(kind), id(r.get("warehouse_id")), "查看");
                    return true;
                  } catch (ResponseStatusException e) {
                    return false;
                  }
                })
            .map(masters::view)
            .toList());
    var correctionSources = new ArrayList<Map<String, Object>>();
    for (var source :
        db.queryForList(
            "SELECT id,code,warehouse_id,'receipt' source_kind FROM inv_receipt WHERE tenant_id=?"
                + " AND status='已入库' AND deleted=0 UNION ALL SELECT id,code,warehouse_id,'issue'"
                + " source_kind FROM inv_issue WHERE tenant_id=? AND reversed=0 AND status='已出库'"
                + " AND deleted=0 UNION ALL SELECT id,code,warehouse_id,kind source_kind FROM"
                + " inv_operation WHERE tenant_id=? AND status IN ('已完成','在途') AND"
                + " kind<>'reversals' AND deleted=0",
            auth.tenantId(),
            auth.tenantId(),
            auth.tenantId()))
      if (permissions.inventoryWarehouseAllowed(menu(kind), id(source.get("warehouse_id")), "查看"))
        correctionSources.add(masters.view(source));
    result.put("correctionSources", correctionSources);
    result.put(
        "legacy",
        db
            .queryForList(
                "SELECT s.*,COALESCE(x.migration_status,'待初始化') migration_status FROM"
                    + " eam_spare_stock s LEFT JOIN inv_legacy_stock_map x ON"
                    + " x.tenant_id=s.tenant_id AND x.stock_id=s.id WHERE s.tenant_id=? AND"
                    + " (x.stock_id IS NULL OR x.migration_status='待初始化')",
                auth.tenantId())
            .stream()
            .filter(
                r -> {
                  if (id(r.get("company_id")) == 0)
                    return permissions.scope("inventoryOpening", "查看").all();
                  return db
                      .queryForList(
                          "SELECT id FROM inv_warehouse WHERE tenant_id=? AND company_id=?",
                          auth.tenantId(),
                          r.get("company_id"))
                      .stream()
                      .anyMatch(
                          w ->
                              permissions.inventoryWarehouseAllowed(
                                  menu(kind), id(w.get("id")), "查看"));
                })
            .map(masters::view)
            .toList());
    if (!permissions.allowed(menu(kind), "查看金额")) InventoryMoney.hide(result);
    return result;
  }

  @Transactional
  public Map<String, Object> save(String kind, Long doc, Map<String, Object> body) {
    lock();
    require(NAMES.containsKey(kind), "单据类型无效");
    permissions.require(menu(kind), doc == null ? "新增" : "编辑");
    var previous = doc == null ? null : operation(kind, doc, true, "编辑");
    if (previous != null) {
      require(text(previous, "status").equals("草稿"), "已生效单据不能编辑");
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    long warehouseId = id(body.get("warehouseId"));
    permissions.checkInventoryWarehouse(menu(kind), warehouseId, doc == null ? "新增" : "编辑");
    var w = checkWarehouse(warehouseId, "新增");
    require(flag(w.get("enabled")), "仓库已停用");
    var data = new LinkedHashMap<String, Object>();
    data.put("remark", text(body, "remark"));
    require(text(body, "remark").length() <= 1000, "备注过长");
    if (kind.equals("openings") && id(body.get("conversionMaterialId")) != 0) {
      long material = id(body.get("conversionMaterialId"));
      var m = masters.row("materials", material, true);
      require(!flag(m.get("batch_enabled")), "物料已启用批次");
      validateConversion(material, doc == null ? 0 : doc);
      var normalized = new ArrayList<Map<String, Object>>();
      Set<Long> seen = new HashSet<>();
      for (var line : lines(body)) {
        long bid = id(line.get("balanceId"));
        require(seen.add(bid), "初始化余额重复");
        var b = balance(bid, true);
        require(
            id(b.get("material_id")) == material
                && id(b.get("batch_id")) == 0
                && ((BigDecimal) b.get("quantity")).signum() > 0,
            "初始化库存已变化");
        permissions.checkInventoryWarehouse(
            "inventoryOpening", id(b.get("warehouse_id")), doc == null ? "新增" : "编辑");
        require(text(line, "batchCode").length() <= 64, "批次号过长");
        var l = new LinkedHashMap<String, Object>();
        l.put("balanceId", Long.toString(bid));
        l.put("materialId", Long.toString(material));
        l.put("materialName", m.get("name"));
        l.put("binId", Objects.toString(b.get("bin_id")));
        l.put("warehouseId", Objects.toString(b.get("warehouse_id")));
        l.put("quantity", Objects.toString(b.get("quantity")));
        l.put("balanceVersion", b.get("row_version"));
        l.put("batchCode", text(line, "batchCode"));
        l.put("productionDate", text(line, "productionDate"));
        l.put("expiryDate", text(line, "expiryDate"));
        normalized.add(l);
      }
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
                      + " quantity>0",
                  Integer.class,
                  auth.tenantId(),
                  material)
              == normalized.size(),
          "必须一次初始化该物料全部仓库库存；请确认所有仓库的权限");
      data.put("conversionMaterialId", Long.toString(material));
      data.put("lines", normalized);
    } else if (kind.equals("counts")) {
      if (previous == null) {
        var snapshots = new ArrayList<Map<String, Object>>();
        for (var b :
            db.queryForList(
                "SELECT s.*,m.name material_name,b.name bin_name,pc.code batch_code FROM"
                    + " inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
                    + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND"
                    + " b.id=s.bin_id LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND"
                    + " pc.id=s.batch_id WHERE s.tenant_id=? AND s.warehouse_id=? ORDER BY s.id FOR"
                    + " UPDATE",
                auth.tenantId(),
                warehouseId)) {
          var line = new LinkedHashMap<String, Object>();
          line.put("balanceId", Objects.toString(b.get("id")));
          line.put("baselineQuantity", ((BigDecimal) b.get("quantity")).toPlainString());
          line.put("countedQuantity", "");
          line.put("unitCost", "");
          line.put("materialName", b.get("material_name"));
          line.put("binName", b.get("bin_name"));
          line.put("batchCode", Objects.toString(b.get("batch_code"), ""));
          snapshots.add(line);
        }
        require(!snapshots.isEmpty(), "仓库暂无库存，不能建立盘点单");
        data.put("lines", snapshots);
        data.put("baselineAt", LocalDateTime.now().toString());
      } else {
        require(id(previous.get("warehouse_id")) == warehouseId, "盘点仓库不能变更");
        var original = payload(previous);
        var old = lines(original);
        var entered = lines(body);
        require(old.size() == entered.size(), "不能更改盘点基准明细");
        var byId = new HashMap<Long, Map<String, Object>>();
        for (var l : entered) require(byId.put(id(l.get("balanceId")), l) == null, "盘点明细重复");
        for (var l : old) {
          var input = byId.get(id(l.get("balanceId")));
          require(input != null, "盘点明细缺失");
          if (!text(input, "countedQuantity").isBlank())
            decimal(input.get("countedQuantity"), true);
          if (!text(input, "unitCost").isBlank()) decimal(input.get("unitCost"), true);
          if (!text(input, "countedQuantity").isBlank()
              && (!text(input, "countedQuantity").equals(text(l, "countedQuantity"))
                  || !l.containsKey("observedQuantity"))) {
            l.put(
                "observedQuantity",
                Objects.toString(balance(id(l.get("balanceId")), false).get("quantity")));
            l.put("countedAt", LocalDateTime.now().toString());
          }
          l.put("countedQuantity", text(input, "countedQuantity"));
          l.put("unitCost", text(input, "unitCost"));
        }
        data.put("lines", old);
        data.put("baselineAt", original.get("baselineAt"));
      }
    } else if (kind.equals("reversals")) {
      String sourceKind = text(body, "sourceKind");
      if (sourceKind.isBlank()) sourceKind = "receipt";
      long sourceId = id(body.get("sourceId"));
      if (sourceId == 0) sourceId = id(body.get("receiptId"));
      require(
          Set.of("receipt", "issue", "returns", "transfers", "counts", "openings")
              .contains(sourceKind),
          "纠正来源无效");
      String table =
          sourceKind.equals("receipt")
              ? "inv_receipt"
              : sourceKind.equals("issue") ? "inv_issue" : "inv_operation";
      var source =
          one("SELECT * FROM " + table + " WHERE tenant_id=? AND id=?", auth.tenantId(), sourceId);
      require(id(source.get("warehouse_id")) == warehouseId, "请选择本仓库生效单据");
      if (sourceKind.equals("issue"))
        require(
            !flag(source.get("reversed")) && text(source, "status").equals("已出库"), "来源出库单尚未生效或已冲销");
      else require(Set.of("已入库", "已完成", "在途").contains(text(source, "status")), "来源单据未生效或已冲销");
      if (table.equals("inv_operation")) {
        require(sourceKind.equals(text(source, "kind")), "单据类型不匹配");
        if (source.get("target_warehouse_id") != null)
          permissions.checkInventoryWarehouse(
              menu(kind), id(source.get("target_warehouse_id")), "查看");
      }
      require(!text(body, "remark").isBlank(), "请填写冲销原因");
      data.put("sourceKind", sourceKind);
      data.put("sourceId", Long.toString(sourceId));
      data.put("sourceCode", source.get("code"));

    } else {
      var input = lines(body);
      var normalized = new ArrayList<Map<String, Object>>();
      var duplicates = new HashSet<Long>();
      for (var line : input) {
        var l = new LinkedHashMap<String, Object>();
        var qty = decimal(line.get("quantity"), false);
        l.put("quantity", qty.toPlainString());
        if (kind.equals("returns")) {
          long allocationId = id(line.get("allocationId"));
          require(duplicates.add(allocationId), "原发料明细不能重复");
          var a = issueAllocation(allocationId, false);
          permissions.checkInventoryWarehouse(menu(kind), id(a.get("warehouse_id")), "查看");
          require(id(a.get("company_id")) == id(w.get("company_id")), "退料不能跨公司");
          var bin = validBin(id(line.get("binId")), warehouseId);
          l.put("allocationId", Long.toString(allocationId));
          l.put("binId", Objects.toString(bin.get("id")));
          l.put("materialName", a.get("material_name"));
          l.put("issueCode", a.get("issue_code"));
        }
        if (kind.equals("transfers")) {
          long balanceId = id(line.get("balanceId"));
          require(duplicates.add(balanceId), "库存明细不能重复");
          var b = balance(balanceId, false);
          require(id(b.get("warehouse_id")) == warehouseId, "库存不属于源仓库");
          l.put("balanceId", Long.toString(balanceId));
          l.put("targetBinId", Long.toString(id(line.get("targetBinId"))));
          l.put(
              "materialName",
              masters.row("materials", id(b.get("material_id")), false).get("name"));
        }
        if (kind.equals("openings")) {
          long materialId = id(line.get("materialId"));
          var m = masters.row("materials", materialId, false);
          require(flag(m.get("enabled")), "物料已停用");
          validBin(id(line.get("binId")), warehouseId);
          decimal(line.get("unitPrice"), true);
          for (String k :
              List.of(
                  "materialId",
                  "binId",
                  "unitPrice",
                  "batchCode",
                  "supplierBatch",
                  "productionDate",
                  "expiryDate",
                  "legacyStockId")) l.put(k, text(line, k));
          l.put("allocatedFee", "0");
          l.put("materialName", m.get("name"));
          if (id(line.get("legacyStockId")) != 0) {
            long stock = id(line.get("legacyStockId"));
            require(duplicates.add(stock), "历史库存不能重复初始化");
            var legacy =
                one(
                    "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=?",
                    auth.tenantId(),
                    stock);
            require(
                id(legacy.get("company_id")) == 0
                    || id(legacy.get("company_id")) == id(w.get("company_id")),
                "历史库存所属公司与仓库不一致");
            require(((BigDecimal) legacy.get("quantity")).compareTo(qty) == 0, "历史库存应按当前结存数量完整初始化");
          }
        }
        normalized.add(l);
      }
      data.put("lines", normalized);
    }
    Long target = null;
    if (kind.equals("transfers")) {
      target = id(body.get("targetWarehouseId"));
      permissions.checkInventoryWarehouse(menu(kind), target, "查看");
      var tw = checkWarehouse(target, "新增");
      require(
          target != warehouseId
              && id(tw.get("company_id")) == id(w.get("company_id"))
              && flag(tw.get("enabled")),
          "请选择同公司其他有效仓库");
      for (var l : lines(data)) validBin(id(l.get("targetBinId")), target);
      data.put("targetWarehouseId", target.toString());
    }
    long record = doc == null ? IdWorker.getId() : doc;
    if (previous == null)
      db.update(
          "INSERT INTO"
              + " inv_operation(id,tenant_id,kind,code,company_id,warehouse_id,target_warehouse_id,payload,created_by)"
              + " VALUES(?,?,?,?,?,?,?,?,?)",
          record,
          auth.tenantId(),
          kind,
          number(kind),
          w.get("company_id"),
          warehouseId,
          target,
          encode(data),
          auth.userId());
    else
      db.update(
          "UPDATE inv_operation SET"
              + " warehouse_id=?,company_id=?,target_warehouse_id=?,payload=?,row_version=row_version+1"
              + " WHERE tenant_id=? AND id=?",
          warehouseId,
          w.get("company_id"),
          target,
          encode(data),
          auth.tenantId(),
          record);
    event(record, kind, previous == null ? "创建草稿" : "修改草稿");
    return detail(kind, record);
  }

  private Map<String, Object> balance(long balance, boolean locked) {
    return one(
        "SELECT * FROM inv_balance WHERE tenant_id=? AND id=?" + (locked ? " FOR UPDATE" : ""),
        auth.tenantId(),
        balance);
  }

  private Map<String, Object> validBin(long bin, long warehouse) {
    var b = masters.row("bins", bin, false);
    require(id(b.get("warehouse_id")) == warehouse && flag(b.get("enabled")), "请选择有效目标库位");
    return b;
  }

  private Map<String, Object> issueAllocation(long allocation, boolean locked) {
    return one(
        "SELECT a.*,i.code"
            + " issue_code,i.company_id,i.warehouse_id,i.requisition_id,s.material_id,s.batch_id,m.name"
            + " material_name FROM inv_issue_allocation a JOIN inv_issue i ON"
            + " i.tenant_id=a.tenant_id AND i.id=a.issue_id JOIN inv_balance s ON"
            + " s.tenant_id=a.tenant_id AND s.id=a.balance_id JOIN inv_material m ON"
            + " m.tenant_id=s.tenant_id AND m.id=s.material_id WHERE a.tenant_id=? AND a.id=? AND"
            + " i.reversed=0"
            + (locked ? " FOR UPDATE" : ""),
        auth.tenantId(),
        allocation);
  }

  private long targetBalance(long warehouse, long bin, long material, long batch, String state) {
    var w = checkWarehouse(warehouse, "入库");
    require(flag(w.get("enabled")), "目标仓库已停用");
    validBin(bin, warehouse);
    db.update(
        "INSERT INTO"
            + " inv_balance(id,tenant_id,company_id,warehouse_id,bin_id,material_id,batch_id,stock_status)"
            + " VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
        IdWorker.getId(),
        auth.tenantId(),
        w.get("company_id"),
        warehouse,
        bin,
        material,
        batch,
        state);
    return id(
        one(
                "SELECT id FROM inv_balance WHERE tenant_id=? AND warehouse_id=? AND bin_id=? AND"
                    + " material_id=? AND batch_id=? AND stock_status=? FOR UPDATE",
                auth.tenantId(),
                warehouse,
                bin,
                material,
                batch,
                state)
            .get("id"));
  }

  private void transaction(
      long doc,
      long allocation,
      Map<String, Object> balance,
      String kind,
      BigDecimal qty,
      BigDecimal amount) {
    db.update(
        "INSERT INTO"
            + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        balance.get("company_id"),
        balance.get("warehouse_id"),
        balance.get("id"),
        doc,
        allocation,
        kind,
        qty,
        amount,
        auth.userId());
  }

  private long add(
      long doc,
      long balance,
      long origin,
      long source,
      BigDecimal qty,
      BigDecimal amount,
      BigDecimal unit,
      Object received,
      String kind) {
    long layer = IdWorker.getId();
    db.update(
        "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        auth.tenantId(),
        balance);
    db.update(
        "INSERT INTO"
            + " inv_cost_layer(id,tenant_id,balance_id,source_line_id,origin_layer_id,origin_received_at,quantity,remaining_quantity,unit_cost,remaining_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        layer,
        auth.tenantId(),
        balance,
        source,
        origin == 0 ? null : origin,
        received,
        qty,
        qty,
        unit,
        amount);
    long allocation = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,target_layer_id,quantity,amount,origin_received_at,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?)",
        allocation,
        auth.tenantId(),
        doc,
        balance,
        origin == 0 ? layer : origin,
        layer,
        qty,
        amount,
        received,
        unit);
    transaction(doc, allocation, balance(balance, false), kind, qty, amount);
    return layer;
  }

  private List<Map<String, Object>> take(
      long doc,
      Map<String, Object> b,
      BigDecimal qty,
      Long targetBin,
      String kind,
      boolean normal) {
    require(
        qty.compareTo(
                ((BigDecimal) b.get("quantity")).subtract((BigDecimal) b.get("reserved_quantity")))
            <= 0,
        "可用库存不足或存在预留");
    if (normal) {
      require(text(b, "stock_status").equals("可用"), "冻结库存不能调出");
      var m = masters.row("materials", id(b.get("material_id")), false);
      require(flag(m.get("enabled")), "物料已停用");
      validBin(id(b.get("bin_id")), id(b.get("warehouse_id")));
      if (id(b.get("batch_id")) != 0) {
        var pc =
            one(
                "SELECT * FROM inv_batch WHERE tenant_id=? AND id=?",
                auth.tenantId(),
                b.get("batch_id"));
        require(
            pc.get("expiry_date") == null
                || !LocalDate.parse(pc.get("expiry_date").toString())
                    .isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
            "过期库存不能正常调出");
        require(!flag(m.get("expiry_enabled")) || pc.get("expiry_date") != null, "批次缺少有效期");
      }
    }
    var result = new ArrayList<Map<String, Object>>();
    var need = qty;
    for (var layer :
        db.queryForList(
            "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND balance_id=? AND"
                + " remaining_quantity>0 ORDER BY origin_received_at,id FOR UPDATE",
            auth.tenantId(),
            b.get("id"))) {
      if (need.signum() == 0) break;
      require(layer.get("remaining_cost") != null && layer.get("unit_cost") != null, "成本待补录，不能确认");
      var remain = (BigDecimal) layer.get("remaining_quantity");
      var taken = remain.min(need);
      var cost = (BigDecimal) layer.get("remaining_cost");
      var unit = (BigDecimal) layer.get("unit_cost");
      var amount =
          taken.compareTo(remain) == 0
              ? cost
              : taken.multiply(unit).setScale(2, RoundingMode.HALF_UP).min(cost);
      long allocation = IdWorker.getId();
      db.update(
          "UPDATE inv_cost_layer SET"
              + " remaining_quantity=remaining_quantity-?,remaining_cost=remaining_cost-? WHERE"
              + " tenant_id=? AND id=?",
          taken,
          amount,
          auth.tenantId(),
          layer.get("id"));
      db.update(
          "INSERT INTO"
              + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,target_bin_id,quantity,amount,origin_received_at,unit_cost)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          allocation,
          auth.tenantId(),
          doc,
          b.get("id"),
          layer.get("id"),
          targetBin,
          taken.negate(),
          amount.negate(),
          layer.get("origin_received_at"),
          unit);
      transaction(doc, allocation, b, kind, taken.negate(), amount.negate());
      result.add(
          Map.of(
              "id",
              allocation,
              "layerId",
              layer.get("id"),
              "quantity",
              taken,
              "amount",
              amount,
              "unitCost",
              unit,
              "receivedAt",
              layer.get("origin_received_at")));
      need = need.subtract(taken);
    }
    require(need.signum() == 0, "成本层与库存数量不一致");
    db.update(
        "UPDATE inv_balance SET quantity=quantity-?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        auth.tenantId(),
        b.get("id"));
    return result;
  }

  @Transactional
  public Map<String, Object> confirm(
      String kind, long doc, Map<String, Object> body, boolean receive) {
    lock();
    var raw =
        operation(
            kind,
            doc,
            true,
            kind.equals("reversals")
                ? "冲销"
                : kind.equals("returns") || kind.equals("openings") || receive ? "入库" : "出库");
    if (text(raw, "status").equals("已完成")) return detail(kind, doc);
    if (kind.equals("transfers") && !receive && text(raw, "status").equals("在途"))
      return detail(kind, doc);
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    require(text(raw, "status").equals(receive ? "在途" : "草稿"), "单据状态不能执行此操作");
    var data = payload(raw);
    long warehouse = id(raw.get("warehouse_id"));
    if (!receive) require(flag(checkWarehouse(warehouse, "出库").get("enabled")), "仓库已停用");
    if (receive) {
      require(kind.equals("transfers"), "仅调拨单可以接收");
      long target = id(raw.get("target_warehouse_id"));
      for (var a :
          db.queryForList(
              "SELECT * FROM inv_movement_allocation WHERE tenant_id=? AND document_id=? AND"
                  + " quantity<0 ORDER BY id FOR UPDATE",
              auth.tenantId(),
              doc)) {
        var source = balance(id(a.get("balance_id")), true);
        long destination =
            targetBalance(
                target,
                id(a.get("target_bin_id")),
                id(source.get("material_id")),
                id(source.get("batch_id")),
                text(source, "stock_status"));
        var original =
            one(
                "SELECT source_line_id FROM inv_cost_layer WHERE tenant_id=? AND id=?",
                auth.tenantId(),
                a.get("cost_layer_id"));
        long targetLayer =
            add(
                doc,
                destination,
                id(a.get("cost_layer_id")),
                id(original.get("source_line_id")),
                ((BigDecimal) a.get("quantity")).negate(),
                ((BigDecimal) a.get("amount")).negate(),
                (BigDecimal) a.get("unit_cost"),
                a.get("origin_received_at"),
                "调拨接收");
        db.update(
            "UPDATE inv_movement_allocation SET target_layer_id=? WHERE tenant_id=? AND id=?",
            targetLayer,
            auth.tenantId(),
            a.get("id"));
      }
    } else
      switch (kind) {
        case "returns" -> {
          for (var l : lines(data)) {
            var a = issueAllocation(id(l.get("allocationId")), true);
            var qty = decimal(l.get("quantity"), false);
            var available =
                ((BigDecimal) a.get("quantity"))
                    .subtract((BigDecimal) a.get("used_quantity"))
                    .subtract((BigDecimal) a.get("returned_quantity"));
            require(qty.compareTo(available) <= 0, "退料数量超过已发未用数量");
            var issued = (BigDecimal) a.get("quantity");
            var remainingAmount =
                ((BigDecimal) a.get("amount")).subtract((BigDecimal) a.get("returned_amount"));
            var amount =
                qty.compareTo(issued.subtract((BigDecimal) a.get("returned_quantity"))) == 0
                    ? remainingAmount
                    : ((BigDecimal) a.get("amount"))
                        .multiply(qty)
                        .divide(issued, 2, RoundingMode.HALF_UP)
                        .min(remainingAmount);
            l.put("returnedAmount", amount.toPlainString());
            var original =
                one(
                    "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=?",
                    auth.tenantId(),
                    a.get("cost_layer_id"));
            long b =
                targetBalance(
                    warehouse,
                    id(l.get("binId")),
                    id(a.get("material_id")),
                    id(a.get("batch_id")),
                    "可用");
            add(
                doc,
                b,
                id(a.get("cost_layer_id")),
                id(original.get("source_line_id")),
                qty,
                amount,
                (BigDecimal) a.get("unit_cost"),
                original.get("origin_received_at"),
                "退料");
            db.update(
                "UPDATE inv_issue_allocation SET"
                    + " returned_quantity=returned_quantity+?,returned_amount=returned_amount+?"
                    + " WHERE tenant_id=? AND id=?",
                qty,
                amount,
                auth.tenantId(),
                a.get("id"));
            event(id(a.get("issue_id")), "出库", "退料 · " + doc);
          }
        }
        case "transfers" -> {
          for (var l : lines(data)) {
            var b = balance(id(l.get("balanceId")), true);
            require(id(b.get("warehouse_id")) == warehouse, "来源仓库不匹配");
            validBin(id(l.get("targetBinId")), id(raw.get("target_warehouse_id")));
            take(doc, b, decimal(l.get("quantity"), false), id(l.get("targetBinId")), "调拨调出", true);
          }
        }
        case "counts" -> {
          for (var l : lines(data)) {
            require(!text(l, "countedQuantity").isBlank(), "请填写所有实盘数量");
            var b = balance(id(l.get("balanceId")), true);
            require(id(b.get("warehouse_id")) == warehouse, "盘点库存不属于本仓库");
            var difference =
                decimal(l.get("countedQuantity"), true)
                    .subtract(decimal(l.get("observedQuantity"), true));
            l.put("difference", difference.toPlainString());
            l.put(
                "periodMovement",
                ((BigDecimal) b.get("quantity"))
                    .subtract(decimal(l.get("baselineQuantity"), true))
                    .toPlainString());
            if (difference.signum() < 0) take(doc, b, difference.negate(), null, "盘亏", false);
            if (difference.signum() > 0) {
              permissions.checkInventoryWarehouse("inventoryCount", warehouse, "入库");
              var price = decimal(l.get("unitCost"), true);
              var amount = price.multiply(difference).setScale(2, RoundingMode.HALF_UP);
              add(
                  doc,
                  id(b.get("id")),
                  0,
                  doc,
                  difference,
                  amount,
                  price,
                  LocalDateTime.now(),
                  "盘盈");
            }
          }
        }
        case "openings" -> {
          if (id(data.get("conversionMaterialId")) != 0) {
            convert(doc, data);
            break;
          }
          var receiptBody = new LinkedHashMap<String, Object>();
          receiptBody.put("warehouseId", Long.toString(warehouse));
          receiptBody.put("supplier", "");
          receiptBody.put("remark", "期初单 " + raw.get("code"));
          receiptBody.put("lines", lines(data));
          for (var l : lines(data))
            if (id(l.get("legacyStockId")) != 0) {
              long stock = id(l.get("legacyStockId"));
              var legacy =
                  one(
                      "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=? FOR UPDATE",
                      auth.tenantId(),
                      stock);
              require(
                  ((BigDecimal) legacy.get("quantity")).compareTo(decimal(l.get("quantity"), false))
                      == 0,
                  "历史库存数量已变化");
              var mapped =
                  db.queryForList(
                      "SELECT * FROM inv_legacy_stock_map WHERE tenant_id=? AND stock_id=? FOR"
                          + " UPDATE",
                      auth.tenantId(),
                      stock);
              require(
                  mapped.isEmpty() || text(mapped.getFirst(), "migration_status").equals("待初始化"),
                  "历史库存已初始化");
              require(
                  db.queryForObject(
                          "SELECT COUNT(*) FROM eam_repair_material WHERE tenant_id=? AND"
                              + " stock_id=? AND status='待出库'",
                          Integer.class,
                          auth.tenantId(),
                          stock)
                      == 0,
                  "请先撤回历史库存待出库申请");
            }
          var receipt = receipts.opening(receiptBody);
          data.put("receiptId", receipt.get("id"));
          data.put("receiptCode", receipt.get("code"));
          var posted = (List<Map<String, Object>>) receipt.get("lines");
          int index = 0;
          for (var l : lines(data)) {
            var source = posted.get(index++);
            if (id(l.get("legacyStockId")) == 0) continue;
            long stock = id(l.get("legacyStockId"));
            db.update(
                "INSERT INTO"
                    + " inv_legacy_stock_map(tenant_id,stock_id,material_id,warehouse_id,opening_line_id,migration_status)"
                    + " VALUES(?,?,?,?,?,'已初始化') ON DUPLICATE KEY UPDATE"
                    + " material_id=VALUES(material_id),warehouse_id=VALUES(warehouse_id),opening_line_id=VALUES(opening_line_id),migration_status='已初始化'",
                auth.tenantId(),
                stock,
                id(l.get("materialId")),
                warehouse,
                source.get("id"));
            db.update(
                "UPDATE eam_spare_stock SET quantity=0 WHERE tenant_id=? AND id=?",
                auth.tenantId(),
                stock);
          }
        }
        case "reversals" -> reverse(doc, data);
        default -> throw new IllegalArgumentException("单据类型无效");
      }
    if (kind.equals("transfers")) {
      if (receive)
        db.update(
            "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND"
                + " route_kind='transfers' AND is_todo=1",
            auth.tenantId(),
            doc);
      else {
        long target = id(raw.get("target_warehouse_id"));
        var recipients =
            db
                .queryForList(
                    "SELECT id FROM eam_sys_user WHERE tenant_id=? AND status=1 AND deleted=0",
                    auth.tenantId())
                .stream()
                .map(v -> id(v.get("id")))
                .filter(
                    u ->
                        permissions.inventoryWarehouseUser(u, "inventoryTransfer", target, "入库")
                            && permissions.inventoryWarehouseUser(
                                u, "inventoryTransfer", target, "查看"))
                .toList();
        Object manager = checkWarehouse(target, "入库").get("manager_id");
        if (manager != null && recipients.contains(id(manager))) recipients = List.of(id(manager));
        for (long user : recipients)
          db.update(
              "INSERT INTO"
                  + " inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                  + " VALUES(?,?,?,?,'transfers','待收货',?,1)",
              IdWorker.getId(),
              auth.tenantId(),
              user,
              doc,
              text(raw, "code") + " · 调拨待收货");
      }
    }
    String status = kind.equals("transfers") && !receive ? "在途" : "已完成";
    db.update(
        "UPDATE inv_operation SET"
            + " status=?,payload=?,confirmed_at=CURRENT_TIMESTAMP(3),row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
        status,
        encode(data),
        auth.tenantId(),
        doc);
    event(doc, kind, receive ? "接收确认" : kind.equals("transfers") ? "调出确认" : "确认单据");
    return detail(kind, doc);
  }

  private void validateConversion(long material, long except) {
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
                    + " reserved_quantity>0",
                Integer.class,
                auth.tenantId(),
                material)
            == 0,
        "存在预留，不能初始化批次");
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_receipt_line l JOIN inv_receipt r ON"
                    + " r.tenant_id=l.tenant_id AND r.id=l.receipt_id WHERE l.tenant_id=? AND"
                    + " l.material_id=? AND r.status='草稿' AND r.deleted=0",
                Integer.class,
                auth.tenantId(),
                material)
            == 0,
        "存在入库草稿，不能初始化批次");
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_requisition_line l JOIN inv_requisition r ON"
                    + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=? AND"
                    + " l.material_id=? AND r.status NOT IN ('已完成','已关闭','已取消') AND r.deleted=0",
                Integer.class,
                auth.tenantId(),
                material)
            == 0,
        "存在未完成领料单，不能初始化批次");
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_operation d WHERE d.tenant_id=? AND d.deleted=0 AND"
                    + " d.id<>? AND d.status IN ('草稿','在途') AND"
                    + " (JSON_CONTAINS(d.payload,JSON_OBJECT('materialId',?), '$.lines') OR"
                    + " EXISTS(SELECT 1 FROM inv_movement_allocation a JOIN inv_balance b ON"
                    + " b.tenant_id=a.tenant_id AND b.id=a.balance_id WHERE a.tenant_id=d.tenant_id"
                    + " AND a.document_id=d.id AND b.material_id=?))",
                Integer.class,
                auth.tenantId(),
                except,
                Long.toString(material),
                material)
            == 0,
        "存在库存草稿或在途库存，不能初始化批次");
  }

  private void convert(long doc, Map<String, Object> data) {
    long material = id(data.get("conversionMaterialId"));
    var m = masters.row("materials", material, true);
    require(!flag(m.get("batch_enabled")), "批次模式已变化");
    validateConversion(material, doc);
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_balance WHERE tenant_id=? AND material_id=? AND"
                    + " quantity>0",
                Integer.class,
                auth.tenantId(),
                material)
            == lines(data).size(),
        "库存范围已变化，请重新保存初始化草稿");
    for (var line : lines(data)) {
      var b = balance(id(line.get("balanceId")), true);
      VersionGuard.checkPayload(
          (Number) b.get("row_version"), Map.of("rowVersion", line.get("balanceVersion")));
      permissions.checkInventoryWarehouse("inventoryOpening", id(b.get("warehouse_id")), "入库");
      require(id(b.get("material_id")) == material && id(b.get("batch_id")) == 0, "来源库存模式已变化");
      String code = text(line, "batchCode");
      if (code.isBlank()) {
        String month =
            LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
        do {
          counter.nextMonthlyNumber("inv-PC-" + auth.tenantId(), month);
          code = "PC-" + month + "-" + String.format("%04d", counter.currentValue());
        } while (db.queryForObject(
                "SELECT COUNT(*) FROM inv_batch WHERE tenant_id=? AND code=?",
                Integer.class,
                auth.tenantId(),
                code)
            > 0);
      }
      Object production =
          text(line, "productionDate").isBlank()
              ? null
              : LocalDate.parse(text(line, "productionDate"));
      Object expiry =
          text(line, "expiryDate").isBlank() ? null : LocalDate.parse(text(line, "expiryDate"));
      require(
          production == null
              || expiry == null
              || !((LocalDate) expiry).isBefore((LocalDate) production),
          "有效期不能早于生产日期");
      var found =
          db.queryForList(
              "SELECT * FROM inv_batch WHERE tenant_id=? AND code=? FOR UPDATE",
              auth.tenantId(),
              code);
      long batch;
      if (found.isEmpty()) {
        batch = IdWorker.getId();
        db.update(
            "INSERT INTO"
                + " inv_batch(id,tenant_id,material_id,code,supplier_batch,production_date,expiry_date)"
                + " VALUES(?,?,?,?,'',?,?)",
            batch,
            auth.tenantId(),
            material,
            code,
            production,
            expiry);
      } else {
        var existing = found.getFirst();
        require(
            id(existing.get("material_id")) == material
                && Objects.equals(
                    Objects.toString(existing.get("production_date"), ""),
                    Objects.toString(production, ""))
                && Objects.equals(
                    Objects.toString(existing.get("expiry_date"), ""),
                    Objects.toString(expiry, "")),
            "已有批次归属或日期不一致");
        batch = id(existing.get("id"));
      }
      line.put("batchCode", code);
      var allocated = take(doc, b, (BigDecimal) b.get("quantity"), null, "批次初始化调出", false);
      long target =
          targetBalance(
              id(b.get("warehouse_id")),
              id(b.get("bin_id")),
              material,
              batch,
              text(b, "stock_status"));
      for (var a : allocated) {
        var origin =
            one(
                "SELECT source_line_id FROM inv_cost_layer WHERE tenant_id=? AND id=?",
                auth.tenantId(),
                a.get("layerId"));
        add(
            doc,
            target,
            id(a.get("layerId")),
            id(origin.get("source_line_id")),
            (BigDecimal) a.get("quantity"),
            (BigDecimal) a.get("amount"),
            (BigDecimal) a.get("unitCost"),
            a.get("receivedAt"),
            "批次初始化调入");
      }
    }
    db.update(
        "UPDATE inv_material SET batch_enabled=1,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        material);
  }

  private void undoLayer(long doc, long layerId, BigDecimal qty, BigDecimal amount) {
    var layer =
        one(
            "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=? FOR UPDATE",
            auth.tenantId(),
            layerId);
    require(
        ((BigDecimal) layer.get("remaining_quantity")).compareTo(qty) == 0
            && ((BigDecimal) layer.get("remaining_cost")).compareTo(amount) == 0,
        "来源库存已被使用、调拨或调整，不能直接冲销");
    var b = balance(id(layer.get("balance_id")), true);
    permissions.checkInventoryWarehouse("inventoryReversal", id(b.get("warehouse_id")), "冲销");
    require(
        ((BigDecimal) b.get("quantity"))
                .subtract((BigDecimal) b.get("reserved_quantity"))
                .compareTo(qty)
            >= 0,
        "库存被预留，不能冲销");
    long allocation = IdWorker.getId();
    db.update(
        "UPDATE inv_cost_layer SET remaining_quantity=0,remaining_cost=0 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        layerId);
    db.update(
        "UPDATE inv_balance SET quantity=quantity-?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        auth.tenantId(),
        b.get("id"));
    db.update(
        "INSERT INTO"
            + " inv_movement_allocation(id,tenant_id,document_id,balance_id,cost_layer_id,quantity,amount,origin_received_at,unit_cost)"
            + " VALUES(?,?,?,?,?,?,?,?,?)",
        allocation,
        auth.tenantId(),
        doc,
        b.get("id"),
        layerId,
        qty.negate(),
        amount.negate(),
        layer.get("origin_received_at"),
        layer.get("unit_cost"));
    transaction(doc, allocation, b, "库存冲销", qty.negate(), amount.negate());
  }

  private void restore(long doc, Map<String, Object> a) {
    var layer =
        one(
            "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND id=?",
            auth.tenantId(),
            a.get("cost_layer_id"));
    var b = balance(id(a.get("balance_id")), true);
    permissions.checkInventoryWarehouse("inventoryReversal", id(b.get("warehouse_id")), "冲销");
    require(flag(checkWarehouse(id(b.get("warehouse_id")), "冲销").get("enabled")), "来源仓库停用，不能冲销");
    add(
        doc,
        id(b.get("id")),
        id(layer.get("id")),
        id(layer.get("source_line_id")),
        ((BigDecimal) a.get("quantity")).abs(),
        ((BigDecimal) a.get("amount")).abs(),
        (BigDecimal) layer.get("unit_cost"),
        layer.get("origin_received_at"),
        "库存冲销");
  }

  private void reverseReceipt(long doc, long receipt) {
    var r =
        one(
            "SELECT * FROM inv_receipt WHERE tenant_id=? AND id=? FOR UPDATE",
            auth.tenantId(),
            receipt);
    require(text(r, "status").equals("已入库"), "来源入库单已冲销");
    for (var layer :
        db.queryForList(
            "SELECT c.* FROM inv_cost_layer c JOIN inv_receipt_line l ON l.tenant_id=c.tenant_id"
                + " AND l.id=c.source_line_id WHERE c.tenant_id=? AND l.receipt_id=? AND"
                + " c.origin_layer_id IS NULL ORDER BY c.balance_id,c.id FOR UPDATE",
            auth.tenantId(),
            receipt))
      undoLayer(
          doc,
          id(layer.get("id")),
          (BigDecimal) layer.get("quantity"),
          (BigDecimal) layer.get("remaining_cost"));
    db.update(
        "UPDATE inv_receipt SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        receipt);
    db.update(
        "UPDATE inv_operation SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND"
            + " kind='openings' AND status='已完成' AND"
            + " JSON_UNQUOTE(JSON_EXTRACT(payload,'$.receiptId'))=?",
        auth.tenantId(),
        Long.toString(receipt));
    event(receipt, "receipt", "关联冲销单 " + doc);
  }

  private void reverse(long doc, Map<String, Object> data) {
    String kind = text(data, "sourceKind");
    long source = id(data.get("sourceId"));
    if (kind.equals("receipt")) {
      reverseReceipt(doc, source);
      return;
    }
    if (kind.equals("issue")) {
      var issue =
          one(
              "SELECT * FROM inv_issue WHERE tenant_id=? AND id=? FOR UPDATE",
              auth.tenantId(),
              source);
      require(!flag(issue.get("reversed")), "出库单已冲销");
      for (var a :
          db.queryForList(
              "SELECT * FROM inv_issue_allocation WHERE tenant_id=? AND issue_id=? ORDER BY id FOR"
                  + " UPDATE",
              auth.tenantId(),
              source)) {
        require(
            ((BigDecimal) a.get("used_quantity")).signum() == 0
                && ((BigDecimal) a.get("returned_quantity")).signum() == 0,
            "出库已使用、安装或退料，不能直接冲销");
        restore(doc, a);
        db.update(
            "UPDATE inv_requisition_line SET issued_quantity=issued_quantity-? WHERE tenant_id=?"
                + " AND id=?",
            a.get("quantity"),
            auth.tenantId(),
            a.get("requisition_line_id"));
      }
      db.update(
          "UPDATE inv_issue SET reversed=1 WHERE tenant_id=? AND id=?", auth.tenantId(), source);
      for (var req :
          db.queryForList(
              "SELECT DISTINCT r.* FROM inv_requisition r JOIN inv_requisition_line l ON"
                  + " l.tenant_id=r.tenant_id AND l.requisition_id=r.id JOIN inv_issue_allocation a"
                  + " ON a.tenant_id=l.tenant_id AND a.requisition_line_id=l.id WHERE a.tenant_id=?"
                  + " AND a.issue_id=? ORDER BY r.id FOR UPDATE",
              auth.tenantId(),
              source)) {
        if (!Set.of("已关闭", "已取消").contains(text(req, "status"))) {
          db.update(
              "UPDATE inv_requisition SET status=IF(EXISTS(SELECT 1 FROM inv_requisition_line l"
                  + " WHERE l.tenant_id=inv_requisition.tenant_id AND"
                  + " l.requisition_id=inv_requisition.id AND"
                  + " l.issued_quantity>0),'部分发料','待预留'),row_version=row_version+1 WHERE"
                  + " tenant_id=? AND id=?",
              auth.tenantId(),
              req.get("id"));
          db.update(
              "UPDATE inv_inbox SET done=0 WHERE tenant_id=? AND document_id=? AND"
                  + " route_kind='requisitions' AND is_todo=1",
              auth.tenantId(),
              req.get("id"));
        }
        event(id(req.get("id")), "领料", "关联冲销单 " + doc);
      }
      db.update(
          "UPDATE inv_issue SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND id=?",
          auth.tenantId(),
          source);
      event(source, "出库", "关联冲销单 " + doc);
      return;
    }
    var original =
        one(
            "SELECT * FROM inv_operation WHERE tenant_id=? AND id=? AND deleted=0 FOR UPDATE",
            auth.tenantId(),
            source);
    require(
        text(original, "kind").equals(kind)
            && Set.of("已完成", "在途").contains(text(original, "status")),
        "来源单据已冲销或状态无效");
    var sourceData = payload(original);
    require(
        id(sourceData.get("conversionMaterialId")) == 0, "批次初始化已改变物料追踪模式，不能直接冲销；请通过库存调拨或盘点纠正数量");
    if (kind.equals("openings") && id(sourceData.get("conversionMaterialId")) == 0)
      reverseReceipt(doc, id(sourceData.get("receiptId")));
    else {
      var movements =
          db.queryForList(
              "SELECT * FROM inv_movement_allocation WHERE tenant_id=? AND document_id=? ORDER BY"
                  + " id FOR UPDATE",
              auth.tenantId(),
              source);
      for (var a : movements) {
        BigDecimal qty = (BigDecimal) a.get("quantity");
        if (qty.signum() > 0)
          undoLayer(doc, id(a.get("target_layer_id")), qty, ((BigDecimal) a.get("amount")));
      }
      for (var a : movements) if (((BigDecimal) a.get("quantity")).signum() < 0) restore(doc, a);
      if (kind.equals("returns"))
        for (var line : lines(sourceData)) {
          var a = issueAllocation(id(line.get("allocationId")), true);
          var q = decimal(line.get("quantity"), false);
          BigDecimal amount = decimal(line.get("returnedAmount"), true);
          db.update(
              "UPDATE inv_issue_allocation SET"
                  + " returned_quantity=returned_quantity-?,returned_amount=returned_amount-? WHERE"
                  + " tenant_id=? AND id=?",
              q,
              amount,
              auth.tenantId(),
              a.get("id"));
        }
    }
    db.update(
        "UPDATE inv_operation SET status='已冲销',row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        auth.tenantId(),
        source);
    db.update(
        "UPDATE inv_inbox SET done=1 WHERE tenant_id=? AND document_id=? AND is_todo=1",
        auth.tenantId(),
        source);
    event(source, kind, "关联冲销单 " + doc);
  }

  @Transactional
  public void delete(String kind, long doc, Map<String, Object> body) {
    lock();
    var r = operation(kind, doc, true, "删除");
    require(text(r, "status").equals("草稿"), "只能删除草稿，生效单据请办理关联纠正");
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    db.update(
        "UPDATE inv_operation SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        doc);
    event(doc, kind, "删除草稿");
  }

  private long balanceIdWarehouse(long id) {
    return id(balance(id, false).get("warehouse_id"));
  }

  @Transactional
  public Object freeze(long balanceId, Map<String, Object> body) {
    lock();
    permissions.require("inventoryQuery", "冻结");
    permissions.checkInventoryWarehouse("inventoryQuery", balanceIdWarehouse(balanceId), "冻结");
    var b = balance(balanceId, true);
    checkWarehouse(id(b.get("warehouse_id")), "冻结");
    VersionGuard.checkPayload((Number) b.get("row_version"), body);
    String target = text(body, "stockStatus");
    require(Set.of("冻结", "可用").contains(target), "库存状态无效");
    require(!text(body, "reason").isBlank() && text(body, "reason").length() <= 1000, "请填写冻结或解冻说明");
    if (text(b, "stock_status").equals(target)) return masters.view(b);
    require(((BigDecimal) b.get("reserved_quantity")).signum() == 0, "库存已预留，请先释放后冻结");
    long destination =
        targetBalance(
            id(b.get("warehouse_id")),
            id(b.get("bin_id")),
            id(b.get("material_id")),
            id(b.get("batch_id")),
            target);
    var qty = (BigDecimal) b.get("quantity");
    db.update(
        "UPDATE inv_cost_layer SET balance_id=? WHERE tenant_id=? AND balance_id=? AND"
            + " remaining_quantity>0",
        destination,
        auth.tenantId(),
        balanceId);
    db.update(
        "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=? AND"
            + " id=?",
        qty,
        auth.tenantId(),
        destination);
    db.update(
        "UPDATE inv_balance SET quantity=0,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        balanceId);
    db.update(
        "INSERT INTO"
            + " inv_master_event(id,tenant_id,resource_kind,resource_id,action,actor_id,actor_name,details_json)"
            + " VALUES(?,?,'balances',?,?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        balanceId,
        target,
        auth.userId(),
        auth.user().getDisplayName(),
        encode(
            Map.of(
                "quantity",
                qty.toPlainString(),
                "targetBalanceId",
                Long.toString(destination),
                "reason",
                text(body, "reason"))));
    return masters.view(balance(destination, false));
  }
}
