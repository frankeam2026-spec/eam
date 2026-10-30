package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.InventoryOperationRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryFlowService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryListService listQueries;
  private final InventoryOperationRepository repository;
  private final InventoryService masters;
  private final AuthContext auth;
  private final PermissionService permissions;
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
      InventoryOperationRepository repository,
      InventoryService masters,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      ReceiptService receipts) {
    this.repository = repository;
    this.masters = masters;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
    this.receipts = receipts;
  }

  public void lock() {
    repository.insertTenantLock(auth.tenantId());
    repository.lockTenant(auth.tenantId());
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inventory.message0019"), e);
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
    require(
        body.get("lines") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0086"));
    var rows = (List<?>) body.get("lines");
    require(
        !rows.isEmpty() && rows.size() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0087"));
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
          com.windasoft.eam.common.MessageContext.text(
              "server.inventory.message0088", (zero ? "非负" : "大于零")));
      return n;
    } catch (NumberFormatException e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0020");
    }
  }

  private Map<String, Object> one(List<Map<String, Object>> rows) {
    require(
        rows.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0089"));
    return rows.getFirst();
  }

  private Map<String, Object> operation(String kind, long doc, boolean locked, String action) {
    require(
        NAMES.containsKey(kind),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0022"));
    var row = one(repository.loadOperation(auth.tenantId(), kind, doc, locked));
    // Personal return requests expose only their own document; receipt and all other operations
    // keep warehouse permission checks.
    if (kind.equals("returns")
        && action.equals("查看")
        && id(payload(row).get("surplusRequestedBy")) == auth.userId()) return row;
    permissions.require(menu(kind), action);
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
    repository.insertEvent(
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
    repository.nextMonthlyNumber("inv-" + prefix + "-" + auth.tenantId(), month);
    return prefix + "-" + month + "-" + String.format("%04d", repository.currentNumber());
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
        repository.listAllocations(auth.tenantId(), doc).stream().map(masters::view).toList());
    value.put(
        "events", repository.listEvents(auth.tenantId(), doc).stream().map(masters::view).toList());
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
    return export
        ? listQueries.exportOperations(kind, q, filters)
        : listQueries.operations(kind, q, filters, page, size, false);
  }

  public Object choices(String kind) {
    permissions.require(menu(kind), "查看");
    require(
        NAMES.containsKey(kind),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0022"));
    @SuppressWarnings("unchecked")
    var result = new LinkedHashMap<String, Object>(masters.stockChoices());
    result.put(
        "warehouses",
        ((List<Map<String, Object>>) result.get("warehouses"))
            .stream()
                .filter(
                    w -> permissions.inventoryWarehouseAllowed(menu(kind), id(w.get("id")), "查看"))
                .toList());
    var balances = new ArrayList<Map<String, Object>>();
    for (var b : repository.listStock(auth.tenantId())) {
      try {
        permissions.checkInventoryWarehouse(menu(kind), id(b.get("warehouse_id")), "查看");
        balances.add(masters.view(b));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    result.put("balances", balances);
    var allocations = new ArrayList<Map<String, Object>>();
    for (var a : repository.listReturnableAllocations(auth.tenantId())) {
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
        repository.listReceipts(auth.tenantId()).stream()
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
        repository.listReversalSources(auth.tenantId(), auth.tenantId(), auth.tenantId()))
      if (permissions.inventoryWarehouseAllowed(menu(kind), id(source.get("warehouse_id")), "查看"))
        correctionSources.add(masters.view(source));
    result.put("correctionSources", correctionSources);
    result.put(
        "legacy",
        repository.listLegacyStock(auth.tenantId()).stream()
            .filter(
                r -> {
                  if (id(r.get("company_id")) == 0)
                    return permissions.scope("inventoryOpening", "查看").all();
                  return repository
                      .listCompanyWarehouses(auth.tenantId(), r.get("company_id"))
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
    require(
        NAMES.containsKey(kind),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0022"));
    permissions.require(menu(kind), doc == null ? "新增" : "编辑");
    var previous = doc == null ? null : operation(kind, doc, true, "编辑");
    if (previous != null) {
      require(
          text(previous, "status").equals("草稿"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0090"));
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
    }
    long warehouseId = id(body.get("warehouseId"));
    permissions.checkInventoryWarehouse(menu(kind), warehouseId, doc == null ? "新增" : "编辑");
    var w = checkWarehouse(warehouseId, "新增");
    require(
        flag(w.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    var data = new LinkedHashMap<String, Object>();
    data.put("remark", text(body, "remark"));
    require(
        text(body, "remark").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0091"));
    if (kind.equals("openings") && id(body.get("conversionMaterialId")) != 0) {
      long material = id(body.get("conversionMaterialId"));
      var m = masters.row("materials", material, true);
      require(
          !flag(m.get("batch_enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0092"));
      validateConversion(material, doc == null ? 0 : doc);
      var normalized = new ArrayList<Map<String, Object>>();
      Set<Long> seen = new HashSet<>();
      for (var line : lines(body)) {
        long bid = id(line.get("balanceId"));
        require(
            seen.add(bid),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0093"));
        var b = balance(bid, true);
        require(
            id(b.get("material_id")) == material
                && id(b.get("batch_id")) == 0
                && ((BigDecimal) b.get("quantity")).signum() > 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0094"));
        permissions.checkInventoryWarehouse(
            "inventoryOpening", id(b.get("warehouse_id")), doc == null ? "新增" : "编辑");
        require(
            text(line, "batchCode").length() <= 64,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0095"));
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
          repository.countMaterialStock(auth.tenantId(), material) == normalized.size(),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0096"));
      data.put("conversionMaterialId", Long.toString(material));
      data.put("lines", normalized);
    } else if (kind.equals("counts")) {
      if (previous == null) {
        var snapshots = new ArrayList<Map<String, Object>>();
        for (var b : repository.listCountStock(auth.tenantId(), warehouseId)) {
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
        require(
            !snapshots.isEmpty(),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0097"));
        data.put("lines", snapshots);
        data.put("baselineAt", LocalDateTime.now().toString());
      } else {
        require(
            id(previous.get("warehouse_id")) == warehouseId,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0098"));
        var original = payload(previous);
        var old = lines(original);
        var entered = lines(body);
        require(
            old.size() == entered.size(),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0099"));
        var byId = new HashMap<Long, Map<String, Object>>();
        for (var l : entered)
          require(
              byId.put(id(l.get("balanceId")), l) == null,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0100"));
        for (var l : old) {
          var input = byId.get(id(l.get("balanceId")));
          require(
              input != null,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0101"));
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
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0102"));
      String table =
          sourceKind.equals("receipt")
              ? "inv_receipt"
              : sourceKind.equals("issue") ? "inv_issue" : "v_inv_operation";
      var source = one(repository.loadSource(auth.tenantId(), sourceId, table));
      require(
          id(source.get("warehouse_id")) == warehouseId,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0103"));
      if (sourceKind.equals("issue"))
        require(
            !flag(source.get("reversed")) && text(source, "status").equals("已出库"),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0104"));
      else
        require(
            Set.of("已入库", "已完成", "在途").contains(text(source, "status")),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0105"));
      if (table.equals("v_inv_operation")) {
        require(
            sourceKind.equals(text(source, "kind")),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0106"));
        if (source.get("target_warehouse_id") != null)
          permissions.checkInventoryWarehouse(
              menu(kind), id(source.get("target_warehouse_id")), "查看");
      }
      require(
          !text(body, "remark").isBlank(),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0107"));
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
          require(
              duplicates.add(allocationId),
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0108"));
          var a = issueAllocation(allocationId, false);
          permissions.checkInventoryWarehouse(menu(kind), id(a.get("warehouse_id")), "查看");
          require(
              id(a.get("company_id")) == id(w.get("company_id")),
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0109"));
          var bin = validBin(id(line.get("binId")), warehouseId);
          l.put("allocationId", Long.toString(allocationId));
          l.put("binId", Objects.toString(bin.get("id")));
          l.put("materialName", a.get("material_name"));
          l.put("issueCode", a.get("issue_code"));
        }
        if (kind.equals("transfers")) {
          long balanceId = id(line.get("balanceId"));
          require(
              duplicates.add(balanceId),
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0110"));
          var b = balance(balanceId, false);
          require(
              id(b.get("warehouse_id")) == warehouseId,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0111"));
          l.put("balanceId", Long.toString(balanceId));
          l.put("targetBinId", Long.toString(id(line.get("targetBinId"))));
          l.put(
              "materialName",
              masters.row("materials", id(b.get("material_id")), false).get("name"));
        }
        if (kind.equals("openings")) {
          long materialId = id(line.get("materialId"));
          var m = masters.row("materials", materialId, false);
          require(
              flag(m.get("enabled")),
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0057"));
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
            require(
                duplicates.add(stock),
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0112"));
            var legacy = one(repository.loadLegacyStock(auth.tenantId(), stock));
            require(
                id(legacy.get("company_id")) == 0
                    || id(legacy.get("company_id")) == id(w.get("company_id")),
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0113"));
            require(
                ((BigDecimal) legacy.get("quantity")).compareTo(qty) == 0,
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0114"));
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
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0115"));
      for (var l : lines(data)) validBin(id(l.get("targetBinId")), target);
      data.put("targetWarehouseId", target.toString());
    }
    long record = doc == null ? IdWorker.getId() : doc;
    if (previous == null)
      repository.insertOperation(
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
      repository.updateOperation(
          warehouseId, w.get("company_id"), target, encode(data), auth.tenantId(), record);
    event(record, kind, previous == null ? "创建草稿" : "修改草稿");
    return detail(kind, record);
  }

  /**
   * A holder may request return of their own issued surplus; warehouse receipt remains separately
   * authorized.
   */
  @Transactional
  public Map<String, Object> requestSurplusReturn(long allocation, Map<String, Object> body) {
    lock();
    var available = repository.lockPersonalReturnAllocation(auth.tenantId(), allocation);
    require(
        available.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0116"));
    var row = available.getFirst();
    if (id(row.get("holderId")) != auth.userId())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          org.springframework.http.HttpStatus.FORBIDDEN, "server.inventory.message0021");
    var qty = decimal(body.get("quantity"), false);
    require(
        qty.compareTo((BigDecimal) row.get("remaining")) <= 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0117"));
    String reason = text(body, "remark");
    require(
        !reason.isBlank() && reason.length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0118"));
    var pending = repository.findPendingReturn(auth.tenantId(), Long.toString(allocation));
    require(
        pending.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0119"));
    long warehouse = id(row.get("warehouseId"));
    var bins = repository.listEnabledBins(auth.tenantId(), warehouse);
    require(
        !bins.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0120"));
    var payload = new LinkedHashMap<String, Object>();
    payload.put("remark", reason);
    payload.put("surplusRequestedBy", Long.toString(auth.userId()));
    payload.put(
        "lines",
        List.of(
            Map.of(
                "allocationId",
                Long.toString(allocation),
                "quantity",
                qty.toPlainString(),
                "binId",
                Objects.toString(bins.getFirst().get("id")),
                "materialName",
                row.get("materialName"),
                "issueCode",
                row.get("issueCode"))));
    long doc = IdWorker.getId();
    String code = number("returns");
    repository.insertPersonalReturn(
        doc,
        auth.tenantId(),
        code,
        row.get("companyId"),
        warehouse,
        encode(payload),
        auth.userId());
    event(doc, "returns", "申请退料");
    for (var u : repository.listActiveUsers(auth.tenantId())) {
      long recipient = id(u.get("id"));
      if (permissions.inventoryWarehouseUser(recipient, "inventoryReturn", warehouse, "查看")
          && permissions.inventoryWarehouseUser(recipient, "inventoryReturn", warehouse, "入库"))
        repository.insertReturnTodo(
            IdWorker.getId(), auth.tenantId(), recipient, doc, code + " · 余料退回");
    }
    return Map.of("id", Long.toString(doc), "code", code);
  }

  private Map<String, Object> balance(long balance, boolean locked) {
    return one(repository.loadBalance(auth.tenantId(), balance, locked));
  }

  private Map<String, Object> validBin(long bin, long warehouse) {
    var b = masters.row("bins", bin, false);
    require(
        id(b.get("warehouse_id")) == warehouse && flag(b.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0121"));
    return b;
  }

  private Map<String, Object> issueAllocation(long allocation, boolean locked) {
    return one(repository.loadIssueAllocation(auth.tenantId(), allocation, locked));
  }

  private long targetBalance(long warehouse, long bin, long material, long batch, String state) {
    var w = checkWarehouse(warehouse, "入库");
    require(
        flag(w.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0122"));
    validBin(bin, warehouse);
    repository.ensureBalance(
        IdWorker.getId(),
        auth.tenantId(),
        w.get("company_id"),
        warehouse,
        bin,
        material,
        batch,
        state);
    return id(
        one(repository.lockTargetBalance(auth.tenantId(), warehouse, bin, material, batch, state))
            .get("id"));
  }

  private void transaction(
      long doc,
      long allocation,
      Map<String, Object> balance,
      String kind,
      BigDecimal qty,
      BigDecimal amount) {
    repository.insertTransaction(
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
    repository.addBalance(qty, auth.tenantId(), balance);
    repository.insertCostLayer(
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
    repository.insertMovement(
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
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0123"));
    if (normal) {
      require(
          text(b, "stock_status").equals("可用"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0124"));
      var m = masters.row("materials", id(b.get("material_id")), false);
      require(
          flag(m.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0057"));
      validBin(id(b.get("bin_id")), id(b.get("warehouse_id")));
      if (id(b.get("batch_id")) != 0) {
        var pc = one(repository.loadBatch(auth.tenantId(), b.get("batch_id")));
        require(
            pc.get("expiry_date") == null
                || !LocalDate.parse(pc.get("expiry_date").toString())
                    .isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0125"));
        require(
            !flag(m.get("expiry_enabled")) || pc.get("expiry_date") != null,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0126"));
      }
    }
    var result = new ArrayList<Map<String, Object>>();
    var need = qty;
    for (var layer : repository.lockLayers(auth.tenantId(), b.get("id"))) {
      if (need.signum() == 0) break;
      require(
          layer.get("remaining_cost") != null && layer.get("unit_cost") != null,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0127"));
      var remain = (BigDecimal) layer.get("remaining_quantity");
      var taken = remain.min(need);
      var cost = (BigDecimal) layer.get("remaining_cost");
      var unit = (BigDecimal) layer.get("unit_cost");
      var amount =
          taken.compareTo(remain) == 0
              ? cost
              : taken.multiply(unit).setScale(2, RoundingMode.HALF_UP).min(cost);
      long allocation = IdWorker.getId();
      repository.consumeLayer(taken, amount, auth.tenantId(), layer.get("id"));
      repository.insertOutgoingMovement(
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
    require(
        need.signum() == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0128"));
    repository.takeBalance(qty, auth.tenantId(), b.get("id"));
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
    require(
        text(raw, "status").equals(receive ? "在途" : "草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0129"));
    var data = payload(raw);
    long warehouse = id(raw.get("warehouse_id"));
    if (!receive)
      require(
          flag(checkWarehouse(warehouse, "出库").get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    if (receive) {
      require(
          kind.equals("transfers"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0130"));
      long target = id(raw.get("target_warehouse_id"));
      for (var a : repository.lockOutgoingMovements(auth.tenantId(), doc)) {
        var source = balance(id(a.get("balance_id")), true);
        long destination =
            targetBalance(
                target,
                id(a.get("target_bin_id")),
                id(source.get("material_id")),
                id(source.get("batch_id")),
                text(source, "stock_status"));
        var original = one(repository.loadLayerSource(auth.tenantId(), a.get("cost_layer_id")));
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
        repository.setMovementTarget(targetLayer, auth.tenantId(), a.get("id"));
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
            require(
                qty.compareTo(available) <= 0,
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0131"));
            require(
                ((Number) a.getOrDefault("missing_used_cost", 0)).intValue() == 0,
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0132"));
            var issued = (BigDecimal) a.get("quantity");
            var remainingAmount =
                ((BigDecimal) a.get("amount"))
                    .subtract((BigDecimal) a.get("returned_amount"))
                    .subtract((BigDecimal) a.getOrDefault("used_cost", BigDecimal.ZERO));
            var amount =
                com.windasoft.eam.common.inventory.UsageCost.portion(
                    issued, (BigDecimal) a.get("amount"), qty, available, remainingAmount);
            l.put("returnedAmount", amount.toPlainString());
            var original = one(repository.loadOriginLayer(auth.tenantId(), a.get("cost_layer_id")));
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
            repository.addReturnedQuantity(qty, amount, auth.tenantId(), a.get("id"));
            event(id(a.get("issue_id")), "出库", "退料 · " + doc);
          }
        }
        case "transfers" -> {
          for (var l : lines(data)) {
            var b = balance(id(l.get("balanceId")), true);
            require(
                id(b.get("warehouse_id")) == warehouse,
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0133"));
            validBin(id(l.get("targetBinId")), id(raw.get("target_warehouse_id")));
            take(doc, b, decimal(l.get("quantity"), false), id(l.get("targetBinId")), "调拨调出", true);
          }
        }
        case "counts" -> {
          for (var l : lines(data)) {
            require(
                !text(l, "countedQuantity").isBlank(),
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0134"));
            var b = balance(id(l.get("balanceId")), true);
            require(
                id(b.get("warehouse_id")) == warehouse,
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0135"));
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
              var legacy = one(repository.lockLegacyStock(auth.tenantId(), stock));
              require(
                  ((BigDecimal) legacy.get("quantity")).compareTo(decimal(l.get("quantity"), false))
                      == 0,
                  com.windasoft.eam.common.MessageContext.text("server.inventory.message0136"));
              var mapped = repository.lockLegacyMapping(auth.tenantId(), stock);
              require(
                  mapped.isEmpty() || text(mapped.getFirst(), "migration_status").equals("待初始化"),
                  com.windasoft.eam.common.MessageContext.text("server.inventory.message0137"));
              require(
                  repository.countLegacyWaiting(auth.tenantId(), stock) == 0,
                  com.windasoft.eam.common.MessageContext.text("server.inventory.message0138"));
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
            repository.insertLegacyMapping(
                auth.tenantId(), stock, id(l.get("materialId")), warehouse, source.get("id"));
            repository.clearLegacyQuantity(auth.tenantId(), stock);
          }
        }
        case "reversals" -> reverse(doc, data);
        default ->
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.inventory.message0022");
      }
    if (kind.equals("transfers")) {
      if (receive) repository.closeTransferTodos(auth.tenantId(), doc);
      else {
        long target = id(raw.get("target_warehouse_id"));
        var recipients =
            repository.listNotifyUsers(auth.tenantId()).stream()
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
          repository.insertTransferTodo(
              IdWorker.getId(), auth.tenantId(), user, doc, text(raw, "code") + " · 调拨待收货");
      }
    }
    String status = kind.equals("transfers") && !receive ? "在途" : "已完成";
    repository.confirmOperation(status, encode(data), auth.tenantId(), doc);
    if (kind.equals("returns")) repository.completeReturnTodos(auth.tenantId(), doc);
    if (kind.equals("returns") && id(data.get("surplusRequestedBy")) > 0)
      repository.insertReturnNotice(
          IdWorker.getId(),
          auth.tenantId(),
          id(data.get("surplusRequestedBy")),
          doc,
          text(raw, "code") + " · 退料已由仓库确认");
    event(doc, kind, receive ? "接收确认" : kind.equals("transfers") ? "调出确认" : "确认单据");
    return detail(kind, doc);
  }

  private void validateConversion(long material, long except) {
    require(
        repository.countReservedMaterial(auth.tenantId(), material) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0139"));
    require(
        repository.countDraftReceipts(auth.tenantId(), material) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0140"));
    require(
        repository.countOpenRequisitions(auth.tenantId(), material) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0141"));
    require(
        repository.countOpenOperations(auth.tenantId(), except, Long.toString(material), material)
            == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0142"));
  }

  private void convert(long doc, Map<String, Object> data) {
    long material = id(data.get("conversionMaterialId"));
    var m = masters.row("materials", material, true);
    require(
        !flag(m.get("batch_enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0143"));
    validateConversion(material, doc);
    require(
        repository.countStockForConversion(auth.tenantId(), material) == lines(data).size(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0144"));
    for (var line : lines(data)) {
      var b = balance(id(line.get("balanceId")), true);
      VersionGuard.checkPayload(
          (Number) b.get("row_version"), Map.of("rowVersion", line.get("balanceVersion")));
      permissions.checkInventoryWarehouse("inventoryOpening", id(b.get("warehouse_id")), "入库");
      require(
          id(b.get("material_id")) == material && id(b.get("batch_id")) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0145"));
      String code = text(line, "batchCode");
      if (code.isBlank()) {
        String month =
            LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
        do {
          repository.nextMonthlyNumber("inv-PC-" + auth.tenantId(), month);
          code = "PC-" + month + "-" + String.format("%04d", repository.currentNumber());
        } while (repository.countBatchCode(auth.tenantId(), code) > 0);
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
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0146"));
      var found = repository.lockBatch(auth.tenantId(), code);
      long batch;
      if (found.isEmpty()) {
        batch = IdWorker.getId();
        repository.insertBatch(batch, auth.tenantId(), material, code, production, expiry);
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
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0147"));
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
        var origin = one(repository.loadReceiptLineSource(auth.tenantId(), a.get("layerId")));
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
    repository.enableMaterialBatch(auth.tenantId(), material);
  }

  private void undoLayer(long doc, long layerId, BigDecimal qty, BigDecimal amount) {
    var layer = one(repository.lockLayer(auth.tenantId(), layerId));
    require(
        ((BigDecimal) layer.get("remaining_quantity")).compareTo(qty) == 0
            && ((BigDecimal) layer.get("remaining_cost")).compareTo(amount) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0148"));
    var b = balance(id(layer.get("balance_id")), true);
    permissions.checkInventoryWarehouse("inventoryReversal", id(b.get("warehouse_id")), "冲销");
    require(
        ((BigDecimal) b.get("quantity"))
                .subtract((BigDecimal) b.get("reserved_quantity"))
                .compareTo(qty)
            >= 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0149"));
    long allocation = IdWorker.getId();
    repository.closeCostLayer(auth.tenantId(), layerId);
    repository.takeBalanceForReversal(qty, auth.tenantId(), b.get("id"));
    repository.insertReversalMovement(
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
    var layer = one(repository.loadMovementLayer(auth.tenantId(), a.get("cost_layer_id")));
    var b = balance(id(a.get("balance_id")), true);
    permissions.checkInventoryWarehouse("inventoryReversal", id(b.get("warehouse_id")), "冲销");
    require(
        flag(checkWarehouse(id(b.get("warehouse_id")), "冲销").get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0150"));
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
    var r = one(repository.lockReceipt(auth.tenantId(), receipt));
    require(
        text(r, "status").equals("已入库"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0151"));
    for (var layer : repository.listReceiptLayers(auth.tenantId(), receipt))
      undoLayer(
          doc,
          id(layer.get("id")),
          (BigDecimal) layer.get("quantity"),
          (BigDecimal) layer.get("remaining_cost"));
    repository.reverseReceipt(auth.tenantId(), receipt);
    repository.reverseOpening(auth.tenantId(), Long.toString(receipt));
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
      var issue = one(repository.lockIssue(auth.tenantId(), source));
      require(
          !flag(issue.get("reversed")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0152"));
      for (var a : repository.lockIssueAllocations(auth.tenantId(), source)) {
        require(
            ((BigDecimal) a.get("used_quantity")).signum() == 0
                && ((BigDecimal) a.get("returned_quantity")).signum() == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0153"));
        restore(doc, a);
        repository.restoreRequestedQuantity(
            a.get("quantity"), auth.tenantId(), a.get("requisition_line_id"));
      }
      repository.markIssueReversed(auth.tenantId(), source);
      for (var req : repository.listAffectedRequests(auth.tenantId(), source)) {
        if (!Set.of("已关闭", "已取消").contains(text(req, "status"))) {
          repository.restoreRequestStatus(auth.tenantId(), req.get("id"));
          repository.reopenRequestTodos(auth.tenantId(), req.get("id"));
        }
        event(id(req.get("id")), "领料", "关联冲销单 " + doc);
      }
      repository.reverseIssue(auth.tenantId(), source);
      event(source, "出库", "关联冲销单 " + doc);
      return;
    }
    var original = one(repository.lockSourceOperation(auth.tenantId(), source));
    require(
        text(original, "kind").equals(kind)
            && Set.of("已完成", "在途").contains(text(original, "status")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0154"));
    var sourceData = payload(original);
    require(
        id(sourceData.get("conversionMaterialId")) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0155"));
    if (kind.equals("openings") && id(sourceData.get("conversionMaterialId")) == 0)
      reverseReceipt(doc, id(sourceData.get("receiptId")));
    else {
      var movements = repository.lockMovements(auth.tenantId(), source);
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
          repository.restoreReturnedQuantity(q, amount, auth.tenantId(), a.get("id"));
        }
    }
    repository.reverseSource(auth.tenantId(), source);
    repository.completeSourceTodos(auth.tenantId(), source);
    event(source, kind, "关联冲销单 " + doc);
  }

  @Transactional
  public void delete(String kind, long doc, Map<String, Object> body) {
    lock();
    var r = operation(kind, doc, true, "删除");
    require(
        text(r, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0156"));
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    repository.deleteOperation(auth.tenantId(), doc);
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
    require(
        Set.of("冻结", "可用").contains(target),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0157"));
    require(
        !text(body, "reason").isBlank() && text(body, "reason").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0158"));
    if (text(b, "stock_status").equals(target)) return masters.view(b);
    require(
        ((BigDecimal) b.get("reserved_quantity")).signum() == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0159"));
    long destination =
        targetBalance(
            id(b.get("warehouse_id")),
            id(b.get("bin_id")),
            id(b.get("material_id")),
            id(b.get("batch_id")),
            target);
    var qty = (BigDecimal) b.get("quantity");
    repository.moveCostLayers(destination, auth.tenantId(), balanceId);
    repository.addMergedBalance(qty, auth.tenantId(), destination);
    repository.clearMergedBalance(auth.tenantId(), balanceId);
    repository.insertMasterEvent(
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
