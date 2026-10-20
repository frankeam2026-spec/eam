package com.windasoft.eam.service.inventory;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.InventoryMasterRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
  private final com.windasoft.eam.service.basedata.BaseDataReferenceService references;
  @org.springframework.beans.factory.annotation.Autowired private InventoryMutex mutex;

  static String menu(String kind) {
    return kind.equals("materials") ? "inventoryMaterial" : "inventoryWarehouse";
  }

  private final InventoryMasterRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public InventoryService(
      InventoryMasterRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      com.windasoft.eam.service.basedata.BaseDataReferenceService references) {
    this.references = references;
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
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
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.inventory.message0024");
    }
  }

  static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "1".equals(Objects.toString(value));
  }

  Map<String, Object> row(String kind, long recordId, boolean lock) {
    var rows = repository.loadMaster(auth.tenantId(), recordId, kind, lock);
    if (rows.isEmpty())
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.NOT_FOUND, "server.inspection.message0013");
    return rows.getFirst();
  }

  void company(long companyId, String action) {
    require(
        repository.countCompany(auth.tenantId(), companyId) == 1,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0188"));
    var scope = permissions.scope("inventoryWarehouse", action);
    if (!scope.all() && !scope.companyIds().contains(companyId))
      throw new com.windasoft.eam.common.LocalizedStatusException(
          HttpStatus.FORBIDDEN, "server.inventory.message0025");
  }

  void access(String kind, Map<String, Object> row, String action) {
    permissions.require(menu(kind), action);
    if (kind.equals("warehouses"))
      permissions.checkInventoryWarehouse("inventoryWarehouse", id(row.get("id")), action);
    if (kind.equals("bins"))
      permissions.checkInventoryWarehouse(
          "inventoryWarehouse", id(row.get("warehouse_id")), action);
  }

  @org.springframework.beans.factory.annotation.Autowired private InventoryViewAssembler views;
  @org.springframework.beans.factory.annotation.Autowired private InventoryListService listQueries;

  Map<String, Object> view(Map<String, Object> raw) {
    return views.view(raw);
  }

  public Map<String, Object> list(String kind, String q, String filters, int page, int size) {
    return listQueries.masters(kind, q, filters, page, size, false);
  }

  public List<Map<String, Object>> export(String kind, String q, String filters) {
    return listQueries.exportMasters(kind, q, filters);
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
    var existing = repository.findFirstBin(auth.tenantId(), warehouseId);
    if (!existing.isEmpty()) return id(existing.getFirst().get("id"));
    long binId = IdWorker.getId();
    repository.insertDefaultBin(binId, auth.tenantId(), warehouseId);
    return binId;
  }

  public Map<String, Object> choices() {
    require(
        permissions.menuActions().entrySet().stream()
            .anyMatch(e -> e.getKey().startsWith("inventory") && e.getValue().contains("查看")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0189"));
    var companies = new ArrayList<Map<String, Object>>();
    for (var record : repository.listCompanies(auth.tenantId())) {
      try {
        // Company choices are only metadata; warehouse choices below enforce each menu scope.
        long company = id(record.get("id"));
        boolean visible =
            permissions.menuActions().entrySet().stream()
                .filter(
                    e ->
                        e.getKey().startsWith("inventory")
                            && !e.getKey().equals("inventoryMaterial"))
                .anyMatch(
                    e ->
                        e.getValue().contains("查看")
                            && permissions.companyAllowed(e.getKey(), "查看", company));
        if (!visible && permissions.allowed("inventoryWarehouse", "新增"))
          visible = permissions.companyAllowed("inventoryWarehouse", "新增", company);
        if (!visible) {
          visible =
              repository.listCompanyWarehouseIds(auth.tenantId(), company).stream()
                  .anyMatch(
                      w ->
                          permissions.menuActions().entrySet().stream()
                              .filter(
                                  e ->
                                      e.getKey().startsWith("inventory")
                                          && e.getValue().contains("查看"))
                              .anyMatch(
                                  e ->
                                      permissions.inventoryWarehouseAllowed(
                                          e.getKey(), id(w.get("id")), "查看")));
        }
        if (!visible) continue;
        companies.add(view(record));
      } catch (ResponseStatusException denied) {
        if (denied.getStatusCode().value() != 403) throw denied;
      }
    }
    var users = new ArrayList<Map<String, Object>>();
    for (var record : repository.listUsers(auth.tenantId())) {
      Long companyId = permissions.companyOfOrganization(id(record.get("org_id")));
      if (companyId != null && companies.stream().anyMatch(c -> id(c.get("id")) == companyId)) {
        var v = view(record);
        v.put("companyId", companyId.toString());
        users.add(v);
      }
    }
    var warehouses = new ArrayList<Map<String, Object>>();
    for (var record : repository.listWarehouses(auth.tenantId())) {
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
        includeMaterials
            ? repository.listMaterials(auth.tenantId()).stream()
                .map(
                    row -> {
                      var v = view(row);
                      if (!permissions.allowed("inventoryMaterial", "查看金额")) InventoryMoney.hide(v);
                      return v;
                    })
                .toList()
            : List.of());
    var bins = new ArrayList<Map<String, Object>>();
    for (var bin : repository.listBins(auth.tenantId())) {
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
        repository.nextMonthlyNumber("inv-materials-lifetime-" + auth.tenantId(), "all");
        code = "WL" + String.format("%05d", repository.currentNumber());
      } else {
        repository.nextMonthlyNumber("inv-" + kind + "-" + auth.tenantId(), month);
        code =
            (kind.equals("warehouses") ? "CK" : "KW")
                + "-"
                + month
                + "-"
                + String.format("%04d", repository.currentNumber());
      }
    } while (repository.countCode(auth.tenantId(), code, kind) > 0);
    return code;
  }

  @Transactional
  public Map<String, Object> save(String kind, Long recordId, Map<String, Object> body) {
    repository.validateKind(kind);
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
    require(
        !name.isBlank() && name.length() <= 120,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0190"));
    if (code.isBlank()) code = previous == null ? number(kind) : text(previous, "code");
    require(
        code.length() <= 64,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0191"));
    require(
        text(body, "remark").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0192"));
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
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0193"));
        if (previous != null) body.put("referencePrice", previous.get("reference_price"));
      }
      require(
          text(body, "model").length() <= 200 && text(body, "category").length() <= 120,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0194"));
      require(
          Set.of("消耗型", "可安装").contains(text(body, "usageType")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0195"));
      boolean batch = flag(body.get("batchEnabled")), expiry = flag(body.get("expiryEnabled"));
      require(
          !expiry || batch,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0196"));
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            repository.countMaterialStock(auth.tenantId(), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0197"));
        require(
            repository.countLegacyStock(auth.tenantId(), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0198"));
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            repository.countDraftReceipts(auth.tenantId(), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0199"));
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            repository.countOpenRequisitions(auth.tenantId(), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0200"));
      }
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            repository.countOpenOperations(auth.tenantId(), Long.toString(recordId), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0201"));
        require(
            repository.countInstalledUsage(auth.tenantId(), recordId) == 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0202"));
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
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0203"));
      String rule = text(body, "allocationRule");
      require(
          Set.of("", "FIFO", "FEFO").contains(rule),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0204"));
      fields.put("model", text(body, "model"));
      var unit = references.required(auth.tenantId(), body.get("unitId"), "UNIT", "unitId", "单位");
      require(
          unit.getName().length() <= 40,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0205"));
      var category =
          references.optional(
              auth.tenantId(), body.get("categoryId"), "MATERIAL_CATEGORY", "categoryId", "物料分类");
      fields.put("unit_id", unit.getId());
      fields.put("unit", unit.getName());
      fields.put("category_id", category == null ? null : category.getId());
      fields.put("category", category == null ? "" : category.getName());
      fields.put("usage_type", text(body, "usageType"));
      fields.put("reference_price", price);
      fields.put("batch_enabled", batch ? 1 : 0);
      fields.put("expiry_enabled", expiry ? 1 : 0);
      fields.put("allocation_rule", rule);
    } else if (kind.equals("warehouses")) {
      long companyId = id(body.get("companyId"));
      if (previous == null || id(previous.get("company_id")) != companyId)
        company(companyId, action);
      if (previous != null)
        require(
            id(previous.get("company_id")) == companyId,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0206"));
      String rule = text(body, "allocationRule");
      require(
          Set.of("FIFO", "FEFO").contains(rule),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0207"));
      Long manager = text(body, "managerId").isBlank() ? null : id(body.get("managerId"));
      if (manager != null) {
        var users = repository.findManager(auth.tenantId(), manager);
        require(
            users.size() == 1
                && Objects.equals(
                    permissions.companyOfOrganization(id(users.getFirst().get("org_id"))),
                    companyId),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0208"));
      }
      fields.put("company_id", companyId);
      fields.put("manager_id", manager);
      fields.put("allocation_rule", rule);
    } else {
      long warehouseId = id(body.get("warehouseId"));
      var warehouse = row("warehouses", warehouseId, true);
      access("warehouses", warehouse, action);
      require(
          flag(warehouse.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
      if (previous != null)
        require(
            id(previous.get("warehouse_id")) == warehouseId,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0209"));
      fields.put("warehouse_id", warehouseId);
    }
    long target = recordId == null ? IdWorker.getId() : recordId;
    var params = new ArrayList<Object>(fields.values());
    if (previous == null) {
      params.add(target);
      params.add(auth.tenantId());
      repository.insertMaster(kind, fields, params);
    } else {
      params.add(auth.tenantId());
      params.add(target);
      repository.updateMaster(kind, fields, params);
    }
    try {
      repository.insertMasterEvent(
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
