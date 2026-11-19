package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.domain.system.PermissionAction.VIEW;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.entity.inventory.InventoryBin;
import com.windasoft.eam.entity.inventory.Material;
import com.windasoft.eam.entity.inventory.Warehouse;
import com.windasoft.eam.repository.inventory.InventoryBinRepository;
import com.windasoft.eam.repository.inventory.InventoryMasterRepository;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import com.windasoft.eam.repository.inventory.WarehouseRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InventoryService {
  private final BaseDataReferenceService references;
  private final InventoryMutex mutex;
  private final InventoryBinRepository bins;
  private final MaterialRepository materials;
  private final WarehouseRepository warehouseRepository;

  static String menu(String kind) {
    return kind.equals("materials") ? "inventoryMaterial" : "inventoryWarehouse";
  }

  private final InventoryMasterRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;
  private final InventoryViewAssembler views;
  private final InventoryListService listQueries;

  public InventoryService(
      InventoryMasterRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json,
      BaseDataReferenceService references,
      InventoryMutex mutex,
      InventoryBinRepository bins,
      MaterialRepository materials,
      WarehouseRepository warehouseRepository,
      InventoryViewAssembler views,
      InventoryListService listQueries) {
    this.references = references;
    this.mutex = mutex;
    this.bins = bins;
    this.materials = materials;
    this.warehouseRepository = warehouseRepository;
    this.views = views;
    this.listQueries = listQueries;
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
      throw new LocalizedArgumentException("server.inventory.message0024");
    }
  }

  static boolean flag(Object value) {
    return Boolean.TRUE.equals(value) || "1".equals(Objects.toString(value));
  }

  Map<String, Object> row(String kind, long recordId, boolean lock) {
    var rows = repository.loadMaster(auth.tenantId(), recordId, kind, lock);
    if (rows.isEmpty())
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    return rows.getFirst();
  }

  void company(long companyId, String action) {
    require(
        repository.countCompany(auth.tenantId(), companyId) == 1,
        MessageContext.text("server.inventory.message0188"));
    var scope = permissions.scope("inventoryWarehouse", action);
    if (!scope.all() && !scope.companyIds().contains(companyId))
      throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.inventory.message0025");
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
    return views.view(raw);
  }

  public Map<String, Object> list(String kind, String q, String filters, int page, int size) {
    return listQueries.masters(kind, q, filters, page, size, false);
  }

  public List<Map<String, Object>> export(String kind, String q, String filters) {
    return listQueries.exportMasters(kind, q, filters);
  }

  private static final List<String> WAREHOUSE_CHOICE_MENUS =
      List.of(
          "inventoryWarehouse",
          "inventoryReceipt",
          "inventoryRequisition",
          "inventoryReturn",
          "inventoryTransfer",
          "inventoryCount",
          "inventoryOpening",
          "inventoryReversal",
          "inventoryQuery");

  private Set<Long> visibleWarehouses() {
    var candidates = warehouseRepository.scopeRows(auth.tenantId());
    var result = new HashSet<Long>();
    for (String menu : WAREHOUSE_CHOICE_MENUS)
      result.addAll(permissions.inventoryWarehouses(menu, VIEW, candidates));
    return result;
  }

  long ensureDefaultBin(long warehouseId) {
    Long existing = bins.firstId(auth.tenantId(), warehouseId);
    if (existing != null) return existing;
    long binId = IdWorker.getId();
    bins.createDefault(auth.tenantId(), warehouseId, binId);
    return binId;
  }

  public Map<String, Object> choices() {
    return choices(visibleWarehouses());
  }

  private Map<String, Object> choices(Set<Long> visibleWarehouses) {
    var actions = permissions.menuActions();
    require(
        actions.entrySet().stream()
            .anyMatch(e -> e.getKey().startsWith("inventory") && e.getValue().contains("查看")),
        MessageContext.text("server.inventory.message0189"));
    var companies = new ArrayList<Map<String, Object>>();
    for (var record : repository.listCompanies(auth.tenantId())) {
      try {
        // Company choices are only metadata; warehouse choices below enforce each menu scope.
        long company = id(record.get("id"));
        boolean visible =
            actions.entrySet().stream()
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
                          actions.entrySet().stream()
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
    var companyByOrg = new HashMap<Long, Optional<Long>>();
    var visibleCompanies = new HashSet<Long>();
    for (var company : companies) visibleCompanies.add(id(company.get("id")));
    for (var record : repository.listUsers(auth.tenantId())) {
      var company =
          companyByOrg.computeIfAbsent(
              id(record.get("org_id")),
              org -> Optional.ofNullable(permissions.companyOfOrganization(org)));
      if (company.isPresent() && visibleCompanies.contains(company.get())) {
        var v = view(record);
        v.put("companyId", company.get().toString());
        users.add(v);
      }
    }
    var warehouses = new ArrayList<Map<String, Object>>();
    for (var record : repository.listWarehouses(auth.tenantId()))
      if (visibleWarehouses.contains(id(record.get("id")))) warehouses.add(view(record));
    return Map.of("companies", companies, "users", users, "warehouses", warehouses);
  }

  public Map<String, Object> stockChoices() {
    return stockChoices(true);
  }

  public Map<String, Object> stockChoices(boolean includeMaterials) {
    var visibleWarehouses = visibleWarehouses();
    var result = new LinkedHashMap<String, Object>(choices(visibleWarehouses));
    boolean showPrices = includeMaterials && permissions.allowed("inventoryMaterial", "查看金额");
    result.put(
        "materials",
        includeMaterials
            ? repository.listMaterials(auth.tenantId()).stream()
                .map(
                    row -> {
                      var v = view(row);
                      if (!showPrices) InventoryMoney.hide(v);
                      return v;
                    })
                .toList()
            : List.of());
    var bins = new ArrayList<Map<String, Object>>();
    for (var bin : repository.listBins(auth.tenantId()))
      if (visibleWarehouses.contains(id(bin.get("warehouse_id")))) bins.add(view(bin));
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
    String name = text(body, "name"), code = text(body, "code");
    require(
        !name.isBlank() && name.length() <= 120,
        MessageContext.text("server.inventory.message0190"));
    if (code.isBlank()) code = previous == null ? number(kind) : text(previous, "code");
    require(code.length() <= 64, MessageContext.text("server.inventory.message0191"));
    require(
        text(body, "remark").length() <= 1000, MessageContext.text("server.inventory.message0192"));
    String remark = text(body, "remark");
    boolean enabled = flag(body.get("enabled"));
    boolean create = previous == null;
    long target = create ? IdWorker.getId() : recordId;
    long tenant = auth.tenantId();
    ObjectNode after;
    if (kind.equals("materials")) {
      String priceInput = text(body, "referencePrice");
      if (!permissions.allowed("inventoryMaterial", "编辑参考价")) {
        require(
            priceInput.isBlank()
                || (previous != null
                    && previous.get("reference_price") != null
                    && new BigDecimal(priceInput)
                            .compareTo((BigDecimal) previous.get("reference_price"))
                        == 0),
            MessageContext.text("server.inventory.message0193"));
        if (previous != null) priceInput = text(previous, "reference_price");
      }
      require(
          text(body, "model").length() <= 200 && text(body, "category").length() <= 120,
          MessageContext.text("server.inventory.message0194"));
      require(
          Set.of("消耗型", "可安装").contains(text(body, "usageType")),
          MessageContext.text("server.inventory.message0195"));
      boolean batch = flag(body.get("batchEnabled")), expiry = flag(body.get("expiryEnabled"));
      require(!expiry || batch, MessageContext.text("server.inventory.message0196"));
      // 批次、有效期和用途决定现有库存与未结单据的解释，发生变化时统一检查依赖。
      if (previous != null
          && (flag(previous.get("batch_enabled")) != batch
              || flag(previous.get("expiry_enabled")) != expiry
              || !text(previous, "usage_type").equals(text(body, "usageType")))) {
        require(
            repository.countMaterialStock(auth.tenantId(), recordId) == 0,
            MessageContext.text("server.inventory.message0197"));
        require(
            repository.countLegacyStock(auth.tenantId(), recordId) == 0,
            MessageContext.text("server.inventory.message0198"));
        require(
            repository.countDraftReceipts(auth.tenantId(), recordId) == 0,
            MessageContext.text("server.inventory.message0199"));
        require(
            repository.countOpenRequisitions(auth.tenantId(), recordId) == 0,
            MessageContext.text("server.inventory.message0200"));
        require(
            repository.countOpenOperations(auth.tenantId(), Long.toString(recordId), recordId) == 0,
            MessageContext.text("server.inventory.message0201"));
        require(
            repository.countInstalledUsage(auth.tenantId(), recordId) == 0,
            MessageContext.text("server.inventory.message0202"));
      }
      BigDecimal price = text(body, "referencePrice").isBlank() ? null : new BigDecimal(priceInput);
      require(
          price == null
              || (price.signum() >= 0
                  && price.scale() <= 6
                  && price.precision() - price.scale() <= 14),
          MessageContext.text("server.inventory.message0203"));
      String rule = text(body, "allocationRule");
      require(
          Set.of("", "FIFO", "FEFO").contains(rule),
          MessageContext.text("server.inventory.message0204"));
      var unit = references.required(auth.tenantId(), body.get("unitId"), "UNIT", "unitId", "单位");
      require(unit.getName().length() <= 40, MessageContext.text("server.inventory.message0205"));
      var category =
          references.optional(
              auth.tenantId(), body.get("categoryId"), "MATERIAL_CATEGORY", "categoryId", "物料分类");
      var row = new Material();
      row.setCode(code);
      row.setName(name);
      row.setRemark(remark);
      row.setEnabled(enabled);
      row.setModel(text(body, "model"));
      row.setUnitId(unit.getId());
      row.setUnit(unit.getName());
      row.setCategoryId(category == null ? null : category.getId());
      row.setCategory(category == null ? "" : category.getName());
      row.setUsageType(text(body, "usageType"));
      row.setReferencePrice(price);
      row.setBatchEnabled(batch);
      row.setExpiryEnabled(expiry);
      row.setAllocationRule(rule);
      after =
          auditChange(
              row,
              List.of(
                  "model",
                  "unitId",
                  "unit",
                  "categoryId",
                  "category",
                  "usageType",
                  "referencePrice",
                  "batchEnabled",
                  "expiryEnabled",
                  "allocationRule"));
      auditId(after, "unitId", row.getUnitId());
      auditId(after, "categoryId", row.getCategoryId());
      after.put("referencePrice", price == null ? null : price.toPlainString());
      materials.saveMaster(tenant, target, row, create);
    } else if (kind.equals("warehouses")) {
      long companyId = id(body.get("companyId"));
      if (previous == null || id(previous.get("company_id")) != companyId)
        company(companyId, action);
      if (previous != null)
        require(
            id(previous.get("company_id")) == companyId,
            MessageContext.text("server.inventory.message0206"));
      String rule = text(body, "allocationRule");
      require(
          Set.of("FIFO", "FEFO").contains(rule),
          MessageContext.text("server.inventory.message0207"));
      Long manager = text(body, "managerId").isBlank() ? null : id(body.get("managerId"));
      if (manager != null) {
        var users = repository.findManager(auth.tenantId(), manager);
        require(
            users.size() == 1
                && Objects.equals(
                    permissions.companyOfOrganization(id(users.getFirst().get("org_id"))),
                    companyId),
            MessageContext.text("server.inventory.message0208"));
      }
      var row = new Warehouse();
      row.setCode(code);
      row.setName(name);
      row.setRemark(remark);
      row.setEnabled(enabled);
      row.setCompanyId(companyId);
      row.setManagerId(manager);
      row.setAllocationRule(rule);
      after = auditChange(row, List.of("companyId", "managerId", "allocationRule"));
      auditId(after, "companyId", companyId);
      auditId(after, "managerId", manager);
      warehouseRepository.saveMaster(tenant, target, row, create);
    } else {
      long warehouseId = id(body.get("warehouseId"));
      var warehouse = row("warehouses", warehouseId, true);
      access("warehouses", warehouse, action);
      require(flag(warehouse.get("enabled")), MessageContext.text("server.inventory.message0035"));
      if (previous != null)
        require(
            id(previous.get("warehouse_id")) == warehouseId,
            MessageContext.text("server.inventory.message0209"));
      var row = new InventoryBin();
      row.setCode(code);
      row.setName(name);
      row.setRemark(remark);
      row.setEnabled(enabled);
      row.setWarehouseId(warehouseId);
      after = auditChange(row, List.of("warehouseId"));
      auditId(after, "warehouseId", warehouseId);
      bins.saveMaster(tenant, target, row, create);
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
              Map.of("before", previous == null ? Map.of() : view(previous), "after", after)));
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("无法保存操作记录", e);
    }
    if (kind.equals("warehouses")) ensureDefaultBin(target);
    var output = view(row(kind, target, false));
    if (kind.equals("materials") && !permissions.allowed("inventoryMaterial", "查看金额"))
      InventoryMoney.hide(output);
    return output;
  }

  /** 审计沿用既有 JSON 字段和空值形式，事务及持久化通过各实体仓储处理。 */
  private ObjectNode auditChange(Object row, List<String> fields) {
    ObjectNode output = json.valueToTree(row);
    var keys = new HashSet<>(fields);
    keys.addAll(List.of("code", "name", "remark", "enabled"));
    return output.retain(keys);
  }

  private void auditId(ObjectNode change, String field, Long id) {
    change.put(field, Objects.toString(id, ""));
  }
}
