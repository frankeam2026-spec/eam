package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.ReceiptRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceiptService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  @org.springframework.beans.factory.annotation.Autowired private InventoryListService listQueries;
  private final ReceiptRepository repository;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;

  public ReceiptService(
      ReceiptRepository repository,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
  }

  private Map<String, Object> receipt(long recordId, boolean lock, String action) {
    return receipt(recordId, lock, action, "inventoryReceipt");
  }

  private Map<String, Object> receipt(long recordId, boolean lock, String action, String menu) {
    permissions.require(menu, action);
    var found = repository.loadReceipt(auth.tenantId(), recordId, lock);
    require(
        !found.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0160"));
    permissions.checkInventoryWarehouse(menu, id(found.getFirst().get("warehouse_id")), action);
    return found.getFirst();
  }

  private String number(String table, String prefix) {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      repository.nextMonthlyNumber("inv-" + prefix + "-" + auth.tenantId(), month);
      code = prefix + "-" + month + "-" + String.format("%04d", repository.currentNumber());
    } while (repository.countCode(auth.tenantId(), code, table) > 0);
    return code;
  }

  private BigDecimal decimal(Map<String, Object> body, String key, boolean optional, int scale) {
    if (optional && text(body, key).isBlank()) return null;
    BigDecimal value;
    try {
      value = new BigDecimal(text(body, key));
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0023");
    }
    require(
        value.signum() >= 0 && value.scale() <= scale && value.precision() - value.scale() <= 14,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0161"));
    return value;
  }

  private LocalDate date(Map<String, Object> body, String key) {
    return text(body, key).isBlank() ? null : LocalDate.parse(text(body, key));
  }

  private void event(long document, String action) {
    repository.insertEvent(
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
        repository.listLines(auth.tenantId(), recordId).stream().map(masters::view).toList());
    rendered.put(
        "events",
        repository.listEvents(auth.tenantId(), recordId).stream().map(masters::view).toList());
    if (!permissions.inventoryWarehouseAllowed(menu, id(rendered.get("warehouseId")), "查看金额"))
      InventoryMoney.hide(rendered);
    custom.attach("inventory_receipt", rendered);
    return rendered;
  }

  public Object list(String q, String filters, int page, int size, String customConditions) {
    return listQueries.receipts(q, filters, customConditions, page, size, false);
  }

  public Object export(String q, String filters, String conditions) {
    return listQueries.exportReceipts(q, filters, conditions);
  }

  public Object choices() {
    permissions.require("inventoryReceipt", "查看");
    var result = new LinkedHashMap<String, Object>(masters.stockChoices(false));
    var warehouseIds = new HashSet<Long>();
    var companyIds = new HashSet<Long>();
    var warehouses = new ArrayList<Object>();
    for (var value : (List<?>) result.get("warehouses")) {
      var warehouse = (Map<String, Object>) value;
      if (!permissions.inventoryWarehouseAllowed("inventoryReceipt", id(warehouse.get("id")), "查看"))
        continue;
      warehouseIds.add(id(warehouse.get("id")));
      companyIds.add(id(warehouse.get("companyId")));
      warehouses.add(warehouse);
    }
    result.put("warehouses", warehouses);
    result.put(
        "bins",
        ((List<?>) result.get("bins"))
            .stream()
                .filter(value -> warehouseIds.contains(id(((Map<?, ?>) value).get("warehouseId"))))
                .toList());
    result.put(
        "companies",
        ((List<?>) result.get("companies"))
            .stream()
                .filter(value -> companyIds.contains(id(((Map<?, ?>) value).get("id"))))
                .toList());
    return result;
  }

  public Object materials(String q, int page, int size) {
    permissions.require("inventoryReceipt", "查看");
    require(
        q.length() <= 200 && page >= 1 && size >= 1 && size <= 100,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0032"));
    String match = "%" + q.trim() + "%";
    Long total = repository.countMaterials(auth.tenantId(), match);
    var data =
        repository.listMaterials(auth.tenantId(), match, page, size).stream()
            .map(masters::view)
            .toList();
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
      require(
          text(previous, "status").equals("草稿"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0162"));
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    long warehouseId = id(body.get("warehouseId"));
    require(warehouseId > 0, com.windasoft.eam.common.MessageContext.text("ui.equipment.text0614"));
    var warehouse = masters.row("warehouses", warehouseId, true);
    permissions.checkInventoryWarehouse(menu, warehouseId, recordId == null ? "新增" : "编辑");
    require(
        flag(warehouse.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    require(
        text(body, "supplier").length() <= 120 && text(body, "remark").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0163"));
    require(
        body.get("lines") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("ui.equipment.text0615"));
    var input = (List<?>) body.get("lines");
    require(
        !input.isEmpty() && input.size() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0164"));
    var orderedMaterials = new TreeSet<Long>();
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      orderedMaterials.add(id(((Map<?, ?>) item).get("materialId")));
    }
    for (long materialId : orderedMaterials) masters.row("materials", materialId, true);
    var originalLines = new HashMap<Long, Map<String, Object>>();
    if (recordId != null)
      for (var l : repository.listRawLines(auth.tenantId(), recordId))
        originalLines.put(id(l.get("id")), l);
    long target = recordId == null ? IdWorker.getId() : recordId;
    if (previous == null)
      repository.insertReceipt(
          target,
          auth.tenantId(),
          number("inv_receipt", "RK"),
          warehouse.get("company_id"),
          warehouseId,
          text(body, "supplier"),
          text(body, "remark"),
          auth.userId());
    else {
      repository.updateReceipt(
          warehouse.get("company_id"),
          warehouseId,
          text(body, "supplier"),
          text(body, "remark"),
          auth.tenantId(),
          target);
      repository.deleteLines(auth.tenantId(), target);
    }
    int lineNo = 0;
    for (Object item : input) {
      require(item instanceof Map<?, ?>, "明细格式无效");
      @SuppressWarnings("unchecked")
      var line = (Map<String, Object>) item;
      var old = originalLines.get(id(line.get("id")));
      if (old != null && !permissions.inventoryWarehouseAllowed(menu, warehouseId, "查看金额")) {
        require(
            id(old.get("material_id")) == id(line.get("materialId")),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0165"));
        line.put("unitPrice", old.get("unit_price"));
        line.put("allocatedFee", old.get("allocated_fee"));
      }
      var material = masters.row("materials", id(line.get("materialId")), true);
      long binId = id(line.get("binId"));
      if (binId == 0) {
        var available = repository.listEnabledBins(auth.tenantId(), warehouseId);
        if (available.isEmpty()) binId = masters.ensureDefaultBin(warehouseId);
        else {
          require(
              available.size() == 1,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0166"));
          binId = id(available.getFirst().get("id"));
        }
      }
      var bin = masters.row("bins", binId, false);
      require(
          flag(material.get("enabled"))
              && flag(bin.get("enabled"))
              && id(bin.get("warehouse_id")) == warehouseId,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0167"));
      var qty = decimal(line, "quantity", false, 6);
      require(
          qty.signum() > 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0168"));
      var price = decimal(line, "unitPrice", true, 6);
      var taxRate = decimal(line, "taxRate", true, 6);
      if (taxRate == null) taxRate = BigDecimal.ZERO;
      require(
          taxRate.compareTo(BigDecimal.ONE) <= 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0169"));
      require(
          text(line, "currency").isBlank() || text(line, "currency").equals("CNY"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0170"));
      var fee = decimal(line, "allocatedFee", true, 2);
      if (fee == null) fee = BigDecimal.ZERO;
      require(
          text(line, "batchCode").length() <= 64 && text(line, "supplierBatch").length() <= 120,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0095"));
      var production = date(line, "productionDate");
      var expiry = date(line, "expiryDate");
      require(
          production == null || expiry == null || !expiry.isBefore(production),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0146"));
      require(
          flag(material.get("batch_enabled"))
              || (text(line, "batchCode").isEmpty()
                  && text(line, "supplierBatch").isEmpty()
                  && production == null
                  && expiry == null),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0171"));
      repository.insertLine(
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
    if (menu.equals("inventoryReceipt"))
      custom.saveDraft("inventory_receipt", target, body, recordId == null);
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
    require(
        text(receipt, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0172"));
    if (menu.equals("inventoryReceipt")) custom.validateStored("inventory_receipt", recordId);
    VersionGuard.checkPayload((Number) receipt.get("row_version"), body);
    var warehouse = masters.row("warehouses", id(receipt.get("warehouse_id")), true);
    require(
        flag(warehouse.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    var lines = repository.lockLines(auth.tenantId(), recordId);
    require(
        !lines.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0173"));
    for (var line : lines) {
      var material = masters.row("materials", id(line.get("material_id")), true);
      var bin = masters.row("bins", id(line.get("bin_id")), true);
      require(
          flag(material.get("enabled")) && flag(bin.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0174"));
      require(
          line.get("unit_price") != null,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0175"));
      long batchId = 0;
      String batchCode = text(line, "batch_code");
      if (flag(material.get("batch_enabled"))) {
        LocalDate expiry =
            line.get("expiry_date") == null
                ? null
                : LocalDate.parse(line.get("expiry_date").toString());
        require(
            !flag(material.get("expiry_enabled")) || expiry != null,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0176"));
        require(
            expiry == null || !expiry.isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0177"));
        if (batchCode.isBlank()) batchCode = number("inv_batch", "PC");
        var batches = repository.lockBatch(auth.tenantId(), batchCode);
        if (batches.isEmpty()) {
          batchId = IdWorker.getId();
          repository.insertBatch(
              batchId,
              auth.tenantId(),
              material.get("id"),
              batchCode,
              line.get("supplier_batch"),
              line.get("production_date"),
              line.get("expiry_date"));
        } else {
          var batch = batches.getFirst();
          require(
              id(batch.get("material_id")) == id(material.get("id")),
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0178"));
          for (String key : List.of("supplier_batch", "production_date", "expiry_date"))
            require(
                Objects.equals(
                    Objects.toString(batch.get(key), ""), Objects.toString(line.get(key), "")),
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0179"));
          batchId = id(batch.get("id"));
        }
      } else
        require(
            batchCode.isEmpty()
                && text(line, "supplier_batch").isEmpty()
                && line.get("production_date") == null
                && line.get("expiry_date") == null,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0180"));
      BigDecimal qty = (BigDecimal) line.get("quantity"),
          price = (BigDecimal) line.get("unit_price"),
          fee = (BigDecimal) line.get("allocated_fee");
      BigDecimal amount = qty.multiply(price).setScale(2, RoundingMode.HALF_UP).add(fee),
          unitCost = amount.divide(qty, 6, RoundingMode.HALF_UP);
      require(
          amount.precision() - amount.scale() <= 14
              && unitCost.precision() - unitCost.scale() <= 14,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0181"));
      long candidate = IdWorker.getId();
      repository.ensureBalance(
          candidate,
          auth.tenantId(),
          receipt.get("company_id"),
          receipt.get("warehouse_id"),
          bin.get("id"),
          material.get("id"),
          batchId);
      var balance =
          repository.lockBalance(
              auth.tenantId(),
              receipt.get("warehouse_id"),
              bin.get("id"),
              material.get("id"),
              batchId);
      repository.addBalance(qty, auth.tenantId(), balance.get("id"));
      repository.insertCostLayer(
          IdWorker.getId(),
          auth.tenantId(),
          balance.get("id"),
          line.get("id"),
          qty,
          qty,
          unitCost,
          amount);
      repository.insertTransaction(
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
      repository.updateLineCost(
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
    repository.confirmReceipt(auth.tenantId(), recordId);
    event(recordId, "确认入库");
    return detailForMenu(recordId, menu);
  }

  @Transactional
  public void delete(long recordId, Map<String, Object> body) {
    mutex.enter();
    var raw = receipt(recordId, true, "删除");
    require(
        text(raw, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0182"));
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    custom.remove("inventory_receipt", recordId);
    repository.deleteReceipt(auth.tenantId(), recordId);
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
    require(
        text(raw, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0183"));
    VersionGuard.checkPayload((Number) raw.get("row_version"), body);
    var fee = decimal(body, "totalFee", false, 2);
    var lines = repository.lockLinesForFees(auth.tenantId(), recordId);
    require(
        !lines.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0184"));
    BigDecimal sum = BigDecimal.ZERO;
    for (var l : lines) {
      require(
          l.get("unit_price") != null,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0185"));
      sum = sum.add(((BigDecimal) l.get("quantity")).multiply((BigDecimal) l.get("unit_price")));
    }
    require(
        sum.signum() > 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0186"));
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
      repository.updateLineFee(allocated, auth.tenantId(), l.get("id"));
    }
    repository.updateVersion(auth.tenantId(), recordId);
    event(recordId, "按金额分摊费用");
    return detail(recordId);
  }
}
