package com.windasoft.eam.inventory;

import static com.windasoft.eam.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;
  private final JdbcTemplate db;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NumberCounterMapper counter;

  public ReceiptService(
      JdbcTemplate db,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      NumberCounterMapper counter) {
    this.db = db;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.counter = counter;
  }

  private Map<String, Object> receipt(long recordId, boolean lock, String action) {
    return receipt(recordId, lock, action, "inventoryReceipt");
  }

  private Map<String, Object> receipt(long recordId, boolean lock, String action, String menu) {
    permissions.require(menu, action);
    var found =
        db.queryForList(
            "SELECT * FROM inv_receipt WHERE tenant_id=? AND id=? AND deleted=0"
                + (lock ? " FOR UPDATE" : ""),
            auth.tenantId(),
            recordId);
    require(!found.isEmpty(), "入库单不存在");
    permissions.checkInventoryWarehouse(menu, id(found.getFirst().get("warehouse_id")), action);
    return found.getFirst();
  }

  private String number(String table, String prefix) {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      counter.nextMonthlyNumber("inv-" + prefix + "-" + auth.tenantId(), month);
      code = prefix + "-" + month + "-" + String.format("%04d", counter.currentValue());
    } while (db.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND code=?",
            Integer.class,
            auth.tenantId(),
            code)
        > 0);
    return code;
  }

  private BigDecimal decimal(Map<String, Object> body, String key, boolean optional, int scale) {
    if (optional && text(body, key).isBlank()) return null;
    BigDecimal value;
    try {
      value = new BigDecimal(text(body, key));
    } catch (Exception e) {
      throw new IllegalArgumentException("数量、单价或费用格式不正确");
    }
    require(
        value.signum() >= 0 && value.scale() <= scale && value.precision() - value.scale() <= 14,
        "数值超出范围或小数位过多");
    return value;
  }

  private LocalDate date(Map<String, Object> body, String key) {
    return text(body, key).isBlank() ? null : LocalDate.parse(text(body, key));
  }

  private void event(long document, String action) {
    db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'入库',?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        document,
        action,
        auth.userId(),
        auth.user().getDisplayName());
  }

  public Map<String, Object> detail(long recordId) {
    return detailForMenu(recordId, "inventoryReceipt");
  }

  private Map<String, Object> detailForMenu(long recordId, String menu) {
    var rendered = masters.view(receipt(recordId, false, "查看", menu));
    rendered.put(
        "warehouseName",
        masters.row("warehouses", id(rendered.get("warehouseId")), false).get("name"));
    rendered.put(
        "lines",
        db
            .queryForList(
                "SELECT l.*,m.code material_code,m.name"
                    + " material_name,m.unit,m.batch_enabled,m.expiry_enabled,b.name bin_name FROM"
                    + " inv_receipt_line l JOIN inv_material m ON m.id=l.material_id AND"
                    + " m.tenant_id=l.tenant_id JOIN inv_bin b ON b.id=l.bin_id AND"
                    + " b.tenant_id=l.tenant_id WHERE l.tenant_id=? AND l.receipt_id=? ORDER BY"
                    + " l.line_no",
                auth.tenantId(),
                recordId)
            .stream()
            .map(masters::view)
            .toList());
    rendered.put(
        "events",
        db
            .queryForList(
                "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
                    + " document_kind='入库' AND document_id=? ORDER BY created_at,id",
                auth.tenantId(),
                recordId)
            .stream()
            .map(masters::view)
            .toList());
    if (!permissions.inventoryWarehouseAllowed(menu, id(rendered.get("warehouseId")), "查看金额"))
      InventoryMoney.hide(rendered);
    return rendered;
  }

  public Object list(String q, String filters, int page, int size) {
    permissions.require("inventoryReceipt", "查看");
    require(page >= 1 && size >= 1 && size <= 100 && q.length() <= 200, "查询参数无效");
    var rows = new ArrayList<Map<String, Object>>();
    Map<String, String> conditions;
    require(filters.length() <= 4000, "筛选条件过长");
    try {
      conditions =
          new com.fasterxml.jackson.databind.ObjectMapper()
              .readValue(
                  filters,
                  new com.fasterxml.jackson.core.type.TypeReference<Map<String, String>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    require(
        conditions != null
            && Set.of("code", "warehouseName", "supplier", "status", "actorName", "createdAt", "confirmedAt")
                .containsAll(conditions.keySet()),
        "筛选字段无效");
    for (var r :
        db.queryForList(
            "SELECT r.*,DATE_FORMAT(r.created_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_created_at,DATE_FORMAT(r.confirmed_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_confirmed_at,w.name warehouse_name,COALESCE((SELECT e.actor_name FROM"
                + " inv_document_event e WHERE e.tenant_id=r.tenant_id AND e.document_kind='入库'"
                + " AND e.document_id=r.id ORDER BY e.created_at DESC,e.id DESC LIMIT 1),"
                + " (SELECT u.display_name FROM eam_sys_user u WHERE u.tenant_id=r.tenant_id"
                + " AND u.id=r.created_by),'') actor_name FROM inv_receipt r JOIN inv_warehouse w"
                + " ON w.tenant_id=r.tenant_id AND w.id=r.warehouse_id WHERE r.tenant_id=? AND"
                + " r.deleted=0 AND (r.code LIKE ? OR r.supplier LIKE ? OR w.name LIKE ?) ORDER BY"
                + " r.created_at DESC,r.id DESC",
            auth.tenantId(), "%" + q + "%", "%" + q + "%", "%" + q + "%")) {
      try {
        permissions.checkInventoryWarehouse("inventoryReceipt", id(r.get("warehouse_id")), "查看");
        var rendered = masters.view(r);
        if (conditions.entrySet().stream()
            .allMatch(
                c ->
                    text(rendered, c.getKey())
                        .replace('T', ' ')
                        .contains(Objects.toString(c.getValue(), "").trim()))) rows.add(rendered);
      } catch (org.springframework.web.server.ResponseStatusException e) {
        if (e.getStatusCode().value() != 403) throw e;
      }
    }
    return paginate(rows, page, size);
  }

  private Object paginate(List<Map<String, Object>> rows, int page, int size) {
    int from = (int) Math.min(((long) page - 1) * size, rows.size());
    return Map.of(
        "data",
        rows.subList(from, Math.min(from + size, rows.size())),
        "page",
        Map.of("total", rows.size()));
  }

  public Object export(String q, String filters) {
    permissions.require("inventoryReceipt", "导出");
    // Reuse the same authorized matching and page through all records.
    var rows = new ArrayList<Object>();
    int page = 1, total;
    do {
      @SuppressWarnings("unchecked")
      var result = (Map<String, Object>) list(q, filters, page++, 100);
      rows.addAll((List<?>) result.get("data"));
      total = ((Number) ((Map<?, ?>) result.get("page")).get("total")).intValue();
    } while (rows.size() < total);
    return rows.stream()
        .filter(
            v ->
                permissions.inventoryWarehouseAllowed(
                    "inventoryReceipt", id(((Map<?, ?>) v).get("warehouseId")), "导出"))
        .toList();
  }

  public Object choices() {
    permissions.require("inventoryReceipt", "查看");
    var result = new LinkedHashMap<String, Object>(masters.stockChoices(false));
    var warehouseIds = new HashSet<Long>();
    var companyIds = new HashSet<Long>();
    var warehouses = new ArrayList<Object>();
    for (var value : (List<?>) result.get("warehouses")) {
      var warehouse = (Map<String, Object>) value;
      if (!permissions.inventoryWarehouseAllowed("inventoryReceipt", id(warehouse.get("id")), "查看")) continue;
      warehouseIds.add(id(warehouse.get("id")));
      companyIds.add(id(warehouse.get("companyId")));
      warehouses.add(warehouse);
    }
    result.put("warehouses", warehouses);
    result.put("bins", ((List<?>) result.get("bins")).stream()
        .filter(value -> warehouseIds.contains(id(((Map<?, ?>) value).get("warehouseId")))).toList());
    result.put("companies", ((List<?>) result.get("companies")).stream()
        .filter(value -> companyIds.contains(id(((Map<?, ?>) value).get("id")))).toList());
    return result;
  }

  public Object materials(String q, int page, int size) {
    permissions.require("inventoryReceipt", "查看");
    require(q.length() <= 200 && page >= 1 && size >= 1 && size <= 100, "查询参数无效");
    String match = "%" + q.trim() + "%";
    String where = " WHERE tenant_id=? AND enabled=1 AND (code LIKE ? OR name LIKE ? OR model LIKE ? OR category LIKE ?)";
    var args = new Object[] {auth.tenantId(), match, match, match, match};
    Long total = db.queryForObject("SELECT COUNT(*) FROM inv_material" + where, Long.class, args);
    var data = db.queryForList(
        "SELECT id,code,name,model,unit,category,enabled,batch_enabled,expiry_enabled FROM inv_material"
            + where + " ORDER BY code,id LIMIT ? OFFSET ?", auth.tenantId(), match, match, match,
        match, size, ((long) page - 1) * size).stream().map(masters::view).toList();
    return Map.of("data", data, "page", Map.of("total", total));
  }

  @Transactional
  public Map<String, Object> save(Long recordId, Map<String, Object> body) {
    return saveForMenu(recordId, body, "inventoryReceipt");
  }

  private Map<String, Object> saveForMenu(Long recordId, Map<String, Object> body, String menu) {
    mutex.enter();
    permissions.require(menu, recordId == null ? "新增" : "编辑");
    var previous = recordId == null ? null : receipt(recordId, true, "编辑", menu);
    if (previous != null) {
      require(text(previous, "status").equals("草稿"), "已确认入库单不能编辑");
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    long warehouseId = id(body.get("warehouseId"));
    require(warehouseId > 0, "请选择入库仓库");
    var warehouse = masters.row("warehouses", warehouseId, true);
    permissions.checkInventoryWarehouse(menu, warehouseId, recordId == null ? "新增" : "编辑");
    require(flag(warehouse.get("enabled")), "仓库已停用");
    require(
        text(body, "supplier").length() <= 120 && text(body, "remark").length() <= 1000,
        "供应商或备注过长");
    require(body.get("lines") instanceof List<?>, "请添加入库明细");
    var input = (List<?>) body.get("lines");
    require(!input.isEmpty() && input.size() <= 500, "入库明细须为1至500行");
    var orderedMaterials = new TreeSet<Long>();
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      orderedMaterials.add(id(((Map<?, ?>) item).get("materialId")));
    }
    for (long materialId : orderedMaterials) masters.row("materials", materialId, true);
    var originalLines = new HashMap<Long, Map<String, Object>>();
    if (recordId != null)
      for (var l :
          db.queryForList(
              "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=?",
              auth.tenantId(),
              recordId)) originalLines.put(id(l.get("id")), l);
    long target = recordId == null ? IdWorker.getId() : recordId;
    if (previous == null)
      db.update(
          "INSERT INTO"
              + " inv_receipt(id,tenant_id,code,company_id,warehouse_id,supplier,remark,created_by)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          target,
          auth.tenantId(),
          number("inv_receipt", "RK"),
          warehouse.get("company_id"),
          warehouseId,
          text(body, "supplier"),
          text(body, "remark"),
          auth.userId());
    else {
      db.update(
          "UPDATE inv_receipt SET"
              + " company_id=?,warehouse_id=?,supplier=?,remark=?,row_version=row_version+1 WHERE"
              + " tenant_id=? AND id=?",
          warehouse.get("company_id"),
          warehouseId,
          text(body, "supplier"),
          text(body, "remark"),
          auth.tenantId(),
          target);
      db.update(
          "DELETE FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=?",
          auth.tenantId(),
          target);
    }
    int lineNo = 0;
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      @SuppressWarnings("unchecked")
      var line = (Map<String, Object>) item;
      var old = originalLines.get(id(line.get("id")));
      if (old != null && !permissions.inventoryWarehouseAllowed(menu, warehouseId, "查看金额")) {
        require(id(old.get("material_id")) == id(line.get("materialId")), "隐藏价格的原行不能更换物料，请删除后添加");
        line.put("unitPrice", old.get("unit_price"));
        line.put("allocatedFee", old.get("allocated_fee"));
      }
      var material = masters.row("materials", id(line.get("materialId")), true);
      long binId = id(line.get("binId"));
      if (binId == 0) {
        var available =
            db.queryForList(
                "SELECT id FROM inv_bin WHERE tenant_id=? AND warehouse_id=? AND enabled=1",
                auth.tenantId(),
                warehouseId);
        if (available.isEmpty()) binId = masters.ensureDefaultBin(warehouseId);
        else {
          require(available.size() == 1, "仓库有多个库位，请选择本行入库库位");
          binId = id(available.getFirst().get("id"));
        }
      }
      var bin = masters.row("bins", binId, false);
      require(
          flag(material.get("enabled"))
              && flag(bin.get("enabled"))
              && id(bin.get("warehouse_id")) == warehouseId,
          "请选择有效物料和本仓库库位");
      var qty = decimal(line, "quantity", false, 6);
      require(qty.signum() > 0, "入库数量必须大于零");
      var price = decimal(line, "unitPrice", true, 6);
      var taxRate = decimal(line, "taxRate", true, 6);
      if (taxRate == null) taxRate = BigDecimal.ZERO;
      require(taxRate.compareTo(BigDecimal.ONE) <= 0, "税率须在0至1之间");
      require(
          text(line, "currency").isBlank() || text(line, "currency").equals("CNY"), "当前库存核算币种为人民币");
      var fee = decimal(line, "allocatedFee", true, 2);
      if (fee == null) fee = BigDecimal.ZERO;
      require(
          text(line, "batchCode").length() <= 64 && text(line, "supplierBatch").length() <= 120,
          "批次号过长");
      var production = date(line, "productionDate");
      var expiry = date(line, "expiryDate");
      require(production == null || expiry == null || !expiry.isBefore(production), "有效期不能早于生产日期");
      require(
          flag(material.get("batch_enabled"))
              || (text(line, "batchCode").isEmpty()
                  && text(line, "supplierBatch").isEmpty()
                  && production == null
                  && expiry == null),
          "未启用批次的物料不能填写批次信息");
      db.update(
          "INSERT INTO"
              + " inv_receipt_line(id,tenant_id,receipt_id,line_no,material_id,bin_id,quantity,unit_price,allocated_fee,batch_code,supplier_batch,production_date,expiry_date,tax_rate,currency)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          target,
          ++lineNo,
          material.get("id"),
          bin.get("id"),
          qty,
          price,
          fee,
          text(line, "batchCode"),
          text(line, "supplierBatch"),
          production,
          expiry,
          taxRate,
          "CNY");
    }
    event(target, previous == null ? "创建草稿" : "修改草稿");
    return detailForMenu(target, menu);
  }

  @Transactional
  public Map<String, Object> confirm(long recordId, Map<String, Object> body) {
    return confirmForMenu(recordId, body, "inventoryReceipt");
  }

  private Map<String, Object> confirmForMenu(long recordId, Map<String, Object> body, String menu) {
    mutex.enter();
    var receipt = receipt(recordId, true, "入库", menu);
    if (text(receipt, "status").equals("已入库")) return detailForMenu(recordId, menu);
    require(text(receipt, "status").equals("草稿"), "入库单状态不可确认");
    VersionGuard.checkPayload((Number) receipt.get("row_version"), body);
    var warehouse = masters.row("warehouses", id(receipt.get("warehouse_id")), true);
    require(flag(warehouse.get("enabled")), "仓库已停用");
    var lines =
        db.queryForList(
            "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=? ORDER BY"
                + " material_id,bin_id,id FOR UPDATE",
            auth.tenantId(),
            recordId);
    require(!lines.isEmpty(), "入库明细不能为空");
    for (var line : lines) {
      var material = masters.row("materials", id(line.get("material_id")), true);
      var bin = masters.row("bins", id(line.get("bin_id")), true);
      require(flag(material.get("enabled")) && flag(bin.get("enabled")), "物料或库位已停用");
      require(line.get("unit_price") != null, "请先确定所有明细的不含税单价");
      long batchId = 0;
      String batchCode = text(line, "batch_code");
      if (flag(material.get("batch_enabled"))) {
        LocalDate expiry =
            line.get("expiry_date") == null
                ? null
                : LocalDate.parse(line.get("expiry_date").toString());
        require(!flag(material.get("expiry_enabled")) || expiry != null, "有效期管理物料必须填写有效期");
        require(
            expiry == null || !expiry.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
            "过期物料不能确认入库");
        if (batchCode.isBlank()) batchCode = number("inv_batch", "PC");
        var batches =
            db.queryForList(
                "SELECT * FROM inv_batch WHERE tenant_id=? AND code=? FOR UPDATE",
                auth.tenantId(),
                batchCode);
        if (batches.isEmpty()) {
          batchId = IdWorker.getId();
          db.update(
              "INSERT INTO"
                  + " inv_batch(id,tenant_id,material_id,code,supplier_batch,production_date,expiry_date)"
                  + " VALUES(?,?,?,?,?,?,?)",
              batchId,
              auth.tenantId(),
              material.get("id"),
              batchCode,
              line.get("supplier_batch"),
              line.get("production_date"),
              line.get("expiry_date"));
        } else {
          var batch = batches.getFirst();
          require(id(batch.get("material_id")) == id(material.get("id")), "批次号已被其他物料使用");
          for (String key : List.of("supplier_batch", "production_date", "expiry_date"))
            require(
                Objects.equals(
                    Objects.toString(batch.get(key), ""), Objects.toString(line.get(key), "")),
                "同一批次的供应商批次及日期必须一致");
          batchId = id(batch.get("id"));
        }
      } else
        require(
            batchCode.isEmpty()
                && text(line, "supplier_batch").isEmpty()
                && line.get("production_date") == null
                && line.get("expiry_date") == null,
            "物料追踪规则已变更，请重新编辑入库单");
      BigDecimal qty = (BigDecimal) line.get("quantity"),
          price = (BigDecimal) line.get("unit_price"),
          fee = (BigDecimal) line.get("allocated_fee");
      BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP).add(fee),
          unitCost = amount.divide(qty, 6, RoundingMode.HALF_UP);
      require(
          amount.precision() - amount.scale() <= 14
              && unitCost.precision() - unitCost.scale() <= 14,
          "成本金额超出范围");
      long candidate = IdWorker.getId();
      db.update(
          "INSERT INTO"
              + " inv_balance(id,tenant_id,company_id,warehouse_id,bin_id,material_id,batch_id)"
              + " VALUES(?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
          candidate,
          auth.tenantId(),
          receipt.get("company_id"),
          receipt.get("warehouse_id"),
          bin.get("id"),
          material.get("id"),
          batchId);
      var balance =
          db.queryForMap(
              "SELECT * FROM inv_balance WHERE tenant_id=? AND warehouse_id=? AND bin_id=? AND"
                  + " material_id=? AND batch_id=? AND stock_status='可用' FOR UPDATE",
              auth.tenantId(),
              receipt.get("warehouse_id"),
              bin.get("id"),
              material.get("id"),
              batchId);
      db.update(
          "UPDATE inv_balance SET quantity=quantity+?,row_version=row_version+1 WHERE tenant_id=?"
              + " AND id=?",
          qty,
          auth.tenantId(),
          balance.get("id"));
      db.update(
          "INSERT INTO"
              + " inv_cost_layer(id,tenant_id,balance_id,source_line_id,origin_received_at,quantity,remaining_quantity,unit_cost,remaining_cost)"
              + " VALUES(?,?,?,?,CURRENT_TIMESTAMP(3),?,?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          balance.get("id"),
          line.get("id"),
          qty,
          qty,
          unitCost,
          amount);
      db.update(
          "INSERT INTO"
              + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
              + " VALUES(?,?,?,?,?,?,?,'入库',?,?,?)",
          IdWorker.getId(),
          auth.tenantId(),
          receipt.get("company_id"),
          receipt.get("warehouse_id"),
          balance.get("id"),
          recordId,
          line.get("id"),
          qty,
          amount,
          auth.userId());
      db.update(
          "UPDATE inv_receipt_line SET"
              + " batch_id=?,batch_code=?,total_cost=?,tax_amount=?,gross_amount=? WHERE"
              + " tenant_id=? AND id=?",
          batchId,
          batchCode,
          amount,
          qty.multiply(price)
              .multiply((BigDecimal) line.get("tax_rate"))
              .setScale(2, RoundingMode.HALF_UP),
          qty.multiply(price)
              .setScale(2, RoundingMode.HALF_UP)
              .add(
                  qty.multiply(price)
                      .multiply((BigDecimal) line.get("tax_rate"))
                      .setScale(2, RoundingMode.HALF_UP)),
          auth.tenantId(),
          line.get("id"));
    }
    db.update(
        "UPDATE inv_receipt SET"
            + " status='已入库',confirmed_at=CURRENT_TIMESTAMP(3),row_version=row_version+1 WHERE"
            + " tenant_id=? AND id=?",
        auth.tenantId(),
        recordId);
    event(recordId, "确认入库");
    return detailForMenu(recordId, menu);
  }

  @Transactional
  public void delete(long recordId, Map<String, Object> body) {
    mutex.enter();
    var raw = receipt(recordId, true, "删除");
    require(text(raw, "status").equals("草稿"), "只能删除草稿");
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    db.update(
        "UPDATE inv_receipt SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        recordId);
    event(recordId, "删除草稿");
  }

  @Transactional
  public Map<String, Object> opening(Map<String, Object> body) {
    var draft = saveForMenu(null, body, "inventoryOpening");
    return confirmForMenu(
        InventoryService.id(draft.get("id")),
        Map.of("rowVersion", draft.get("rowVersion")),
        "inventoryOpening");
  }

  @Transactional
  public Map<String, Object> allocateFees(long recordId, Map<String, Object> body) {
    mutex.enter();
    var raw = receipt(recordId, true, "编辑");
    require(text(raw, "status").equals("草稿"), "只能修改草稿费用");
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var fee = decimal(body, "totalFee", false, 2);
    var lines =
        db.queryForList(
            "SELECT * FROM inv_receipt_line WHERE tenant_id=? AND receipt_id=? ORDER BY line_no FOR"
                + " UPDATE",
            auth.tenantId(),
            recordId);
    require(!lines.isEmpty(), "明细不能为空");
    BigDecimal sum = BigDecimal.ZERO;
    for (var l : lines) {
      require(l.get("unit_price") != null, "先填写所有不含税单价");
      sum = sum.add(((BigDecimal) l.get("quantity")).multiply((BigDecimal) l.get("unit_price")));
    }
    require(sum.signum() > 0, "金额合计须大于零才能按金额分摊");
    BigDecimal remaining = fee;
    for (int i = 0; i < lines.size(); i++) {
      var l = lines.get(i);
      BigDecimal allocated =
          i == lines.size() - 1
              ? remaining
              : fee.multiply(
                      ((BigDecimal) l.get("quantity")).multiply((BigDecimal) l.get("unit_price")))
                  .divide(sum, 2, RoundingMode.HALF_UP)
                  .min(remaining);
      remaining = remaining.subtract(allocated);
      db.update(
          "UPDATE inv_receipt_line SET allocated_fee=? WHERE tenant_id=? AND id=?",
          allocated,
          auth.tenantId(),
          l.get("id"));
    }
    db.update(
        "UPDATE inv_receipt SET row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        recordId);
    event(recordId, "按金额分摊费用");
    return detail(recordId);
  }
}
