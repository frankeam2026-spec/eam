package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.common.inventory.InventoryMoney;
import com.windasoft.eam.repository.inventory.OutboundRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** One issue header for direct, single-request and consolidated issues. Preview is read-only. */
@Service
public class OutboundService {
  @org.springframework.beans.factory.annotation.Autowired private InventoryListService listQueries;
  static final String MENU = "inventoryOutbound";

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  private final OutboundRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InventoryService masters;
  private final InventoryMutex mutex;
  private final ObjectMapper json;
  private final RequisitionService requests;
  private final byte[] previewKey = new byte[32];

  {
    new java.security.SecureRandom().nextBytes(previewKey);
  }

  public OutboundService(
      OutboundRepository repository,
      AuthContext auth,
      PermissionService permissions,
      InventoryService masters,
      InventoryMutex mutex,
      ObjectMapper json,
      RequisitionService requests) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.masters = masters;
    this.mutex = mutex;
    this.json = json;
    this.requests = requests;
  }

  @org.springframework.beans.factory.annotation.Value("${eam.inventory.preview-secret:}")
  void configurePreviewSecret(String secret) {
    if (secret.isBlank()) return;
    require(
        secret.length() >= 32,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0029"));
    try {
      System.arraycopy(
          java.security.MessageDigest.getInstance("SHA-256")
              .digest(secret.getBytes(java.nio.charset.StandardCharsets.UTF_8)),
          0,
          previewKey,
          0,
          32);
    } catch (java.security.NoSuchAlgorithmException e) {
      throw new IllegalStateException(e);
    }
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inventory.message0016"), e);
    }
  }

  private Map<String, Object> headerData(Map<String, Object> raw) {
    var data = new LinkedHashMap<String, Object>();
    for (var pair :
        Map.of(
                "purpose",
                "purpose",
                "remark",
                "remark",
                "departmentId",
                "department_id",
                "recipientId",
                "recipient_id",
                "recipientName",
                "recipient_name",
                "taskKind",
                "task_kind",
                "taskId",
                "task_id",
                "legacyFingerprint",
                "legacy_fingerprint")
            .entrySet()) {
      var value = raw.get(pair.getValue());
      if (value != null
          && !(pair.getKey().equals("legacyFingerprint") && value.toString().isBlank()))
        data.put(pair.getKey(), pair.getKey().endsWith("Id") ? value.toString() : value);
    }
    data.put("lineSelections", raw.getOrDefault("line_selections", List.of()));
    return data;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> inputLines(Map<String, Object> body) {
    require(
        body.get("lines") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0030"));
    var list = (List<?>) body.get("lines");
    require(
        !list.isEmpty() && list.size() <= 500,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0031"));
    var result = new ArrayList<Map<String, Object>>();
    for (Object value : list) {
      require(value instanceof Map<?, ?>, "出库明细无效");
      result.add(new LinkedHashMap<>((Map<String, Object>) value));
    }
    return result;
  }

  private Map<String, Object> row(long id, boolean lock, String action) {
    var rows = repository.loadIssue(auth.tenantId(), id, lock);
    require(
        rows.size() == 1,
        com.windasoft.eam.common.MessageContext.text("server.printing.message0003"));
    var r = rows.getFirst();
    boolean ownRead =
        action.equals("查看")
            && text(r, "source_kind").equals("直接出库")
            && !text(r, "status").equals("草稿")
            && repository.countOtherRecipients(auth.tenantId(), id, auth.userId()) == 0
            && repository.countOwnRecipient(auth.tenantId(), id, auth.userId()) > 0;
    if (!ownRead) permissions.checkInventoryWarehouse(MENU, id(r.get("warehouse_id")), action);
    return r;
  }

  private List<Map<String, Object>> lines(long id) {
    return repository.listLines(auth.tenantId(), id);
  }

  private void event(long id, String action) {
    repository.insertEvent(
        IdWorker.getId(), auth.tenantId(), id, action, auth.userId(), auth.user().getDisplayName());
  }

  private String number() {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      repository.nextMonthlyNumber("inv-CKD-" + auth.tenantId(), month);
      code = "CK-" + month + "-" + String.format("%04d", repository.currentNumber());
    } while (repository.countCode(auth.tenantId(), code, auth.tenantId(), code) > 0);
    return code;
  }

  public Object materials(String q, int page, int size, long warehouseId) {
    permissions.require(MENU, "查看");
    require(
        q.length() <= 200 && page >= 1 && size >= 1 && size <= 100,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0032"));
    permissions.checkInventoryWarehouse(MENU, warehouseId, "查看");
    String match = "%" + q.trim() + "%";
    Long total = repository.countMaterials(auth.tenantId(), match);
    var data =
        repository.listMaterials(auth.tenantId(), match, page, size).stream()
            .map(masters::view)
            .toList();
    if (!data.isEmpty()) {
      var stock =
          repository.aggregateMaterialStock(
              auth.tenantId(),
              warehouseId,
              LocalDate.now(ZoneId.of("Asia/Shanghai")),
              data.stream().map(m -> id(m.get("id"))).toList());
      var quantities = new HashMap<Long, Map<String, Object>>();
      stock.forEach(row -> quantities.put(id(row.get("material_id")), row));
      data.forEach(
          material -> {
            var row = quantities.get(id(material.get("id")));
            material.put("stockQuantity", row == null ? "0" : row.get("stock_quantity").toString());
            material.put(
                "reservedQuantity", row == null ? "0" : row.get("reserved_quantity").toString());
            material.put(
                "availableQuantity", row == null ? "0" : row.get("available_quantity").toString());
          });
    }
    return Map.of("data", data, "page", Map.of("total", total));
  }

  public Object choices() {
    return choices(false);
  }

  public Object choices(boolean paged) {
    permissions.require(MENU, "查看");
    var warehouses =
        repository.listWarehouses(auth.tenantId()).stream()
            .filter(w -> permissions.inventoryWarehouseAllowed(MENU, id(w.get("id")), "查看"))
            .map(masters::view)
            .toList();
    var companies = new HashSet<Long>();
    warehouses.forEach(w -> companies.add(id(w.get("companyId"))));
    var departments =
        repository.listOrganizations(auth.tenantId()).stream()
            .filter(d -> companies.contains(permissions.companyOfOrganization(id(d.get("id")))))
            .map(
                d -> {
                  var v = masters.view(d);
                  v.put(
                      "companyId",
                      Objects.toString(permissions.companyOfOrganization(id(d.get("id"))), ""));
                  return v;
                })
            .toList();
    var users =
        (paged ? List.<Map<String, Object>>of() : repository.listUsers(auth.tenantId()))
            .stream()
                .filter(
                    u -> companies.contains(permissions.companyOfOrganization(id(u.get("org_id")))))
                .map(
                    u -> {
                      var v = masters.view(u);
                      v.put(
                          "companyId",
                          Objects.toString(
                              permissions.companyOfOrganization(id(u.get("org_id"))), ""));
                      return v;
                    })
                .toList();
    var materials = List.of();
    var taskChoices = paged ? List.of() : requests.outboundTaskChoices();
    return Map.of(
        "warehouses",
        warehouses,
        "departments",
        departments,
        "users",
        users,
        "materials",
        materials,
        "tasks",
        taskChoices);
  }

  @Transactional
  public Object save(Long issueId, Map<String, Object> body) {
    mutex.enter();
    require(
        Set.of("直接出库", "领料出库").contains(text(body, "sourceKind")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0033"));
    require(
        text(body, "remark").length() <= 1000,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0034"));
    boolean direct = !"领料出库".equals(text(body, "sourceKind"));
    long warehouse = id(body.get("warehouseId"));
    var w = masters.row("warehouses", warehouse, true);
    permissions.checkInventoryWarehouse(
        MENU, warehouse, direct ? (issueId == null ? "新增直接出库" : "编辑草稿") : "确认出库");
    require(
        flag(w.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    Map<String, Object> previous =
        issueId == null ? null : row(issueId, true, direct ? "编辑草稿" : "确认出库");
    if (previous != null) {
      require(
          text(previous, "status").equals("草稿"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0036"));
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
      require(
          text(previous, "source_kind").equals(direct ? "直接出库" : "领料出库"),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0037"));
    }
    long company = id(w.get("company_id"));
    long department = id(body.get("departmentId")), recipient = id(body.get("recipientId"));
    String recipientName = "";
    if (direct) {
      var departments = repository.findDepartment(auth.tenantId(), department);
      require(
          departments.size() == 1
              && Set.of("部门", "department").contains(text(departments.getFirst(), "org_type"))
              && Objects.equals(permissions.companyOfOrganization(department), company),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0038"));
      var recipients = repository.findRecipient(auth.tenantId(), recipient);
      require(
          recipients.size() == 1
              && Objects.equals(
                  permissions.companyOfOrganization(id(recipients.getFirst().get("org_id"))),
                  company),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0039"));
      recipientName = text(recipients.getFirst(), "display_name");
      require(
          !text(body, "purpose").isBlank() && text(body, "purpose").length() <= 1000,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0040"));
      if (id(body.get("taskId")) != 0)
        requests.validateOutboundTask(
            text(body, "taskKind"), id(body.get("taskId")), company, true);
    }
    var normalized = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<Long>();
    for (var line : inputLines(body)) {
      var qty = InventoryFlowService.decimal(line.get("quantity"), true);
      if (qty.signum() == 0) continue;
      var item = new LinkedHashMap<String, Object>();
      long material = id(line.get("materialId"));
      masters.row("materials", material, true);
      item.put("materialId", material);
      item.put("quantity", qty);
      if (!direct) {
        long reqLine = id(line.get("requisitionLineId"));
        require(
            seen.add(reqLine),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0041"));
        var source = repository.findRequestLine(auth.tenantId(), reqLine);
        require(
            source.size() == 1,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0042"));
        var r = source.getFirst();
        requests.outboundRequest(id(r.get("request_id")), true, "确认出库");
        require(
            id(r.get("company_id")) == company && id(r.get("warehouse_id")) == warehouse,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0043"));
        require(
            id(r.get("material_id")) == material,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0044"));
        require(
            qty.compareTo(
                    ((BigDecimal) r.get("quantity"))
                        .subtract((BigDecimal) r.get("issued_quantity")))
                <= 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0045"));
        item.put("requisitionId", r.get("request_id"));
        item.put("requisitionLineId", reqLine);
        item.put("taskKind", r.get("task_kind"));
        item.put("taskId", r.get("task_id"));
        item.put("taskCode", r.get("task_code"));
        item.put("recipientId", r.get("applicant_id"));
        item.put("recipientName", r.get("applicant_name"));
        item.put(
            "departmentId", requests.taskDepartment(text(r, "task_kind"), id(r.get("task_id"))));
      } else {
        item.put("departmentId", department);
        item.put("recipientId", recipient);
        item.put("recipientName", recipientName);
        item.put("taskKind", text(body, "taskKind"));
        item.put("taskId", id(body.get("taskId")) == 0 ? null : id(body.get("taskId")));
        item.put(
            "taskCode",
            id(body.get("taskId")) == 0
                ? ""
                : requests.validateOutboundTask(
                    text(body, "taskKind"), id(body.get("taskId")), company, true));
      }
      var manual = new ArrayList<Map<String, Object>>();
      Object rawSelections = line.get("selections");
      require(
          rawSelections == null || rawSelections instanceof List<?>,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0046"));
      if (rawSelections instanceof List<?> chosen) {
        require(
            chosen.size() <= 500,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0047"));
        for (Object value : chosen) {
          require(value instanceof Map<?, ?>, "批次分配明细无效");
          var selection = (Map<?, ?>) value;
          require(
              id(selection.get("balanceId")) > 0,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0048"));
          manual.add(
              Map.of(
                  "balanceId",
                  id(selection.get("balanceId")),
                  "quantity",
                  InventoryFlowService.decimal(selection.get("quantity"), false)));
        }
      }
      item.put("selections", manual);
      normalized.add(item);
    }
    require(
        !normalized.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0049"));
    var stored = new LinkedHashMap<String, Object>();
    stored.put("purpose", text(body, "purpose"));
    stored.put("remark", text(body, "remark"));
    stored.put("departmentId", Long.toString(department));
    stored.put("recipientId", Long.toString(recipient));
    stored.put("recipientName", recipientName);
    stored.put("taskKind", text(body, "taskKind"));
    stored.put("taskId", text(body, "taskId"));
    stored.put("lineSelections", normalized.stream().map(l -> l.get("selections")).toList());
    long target = issueId == null ? IdWorker.getId() : issueId;
    if (previous == null)
      repository.insertIssue(
          target,
          auth.tenantId(),
          number(),
          company,
          warehouse,
          "draft-" + target,
          auth.userId(),
          auth.user().getDisplayName(),
          direct ? "直接出库" : "领料出库",
          encode(stored));
    else {
      repository.updateIssue(company, warehouse, encode(stored), auth.tenantId(), target);
      repository.deleteLines(auth.tenantId(), target);
    }
    int n = 0;
    for (var l : normalized)
      repository.insertLine(
          IdWorker.getId(),
          auth.tenantId(),
          target,
          ++n,
          l.get("materialId"),
          l.get("quantity"),
          l.get("requisitionId"),
          l.get("requisitionLineId"),
          l.get("taskKind"),
          l.get("taskId"),
          l.get("taskCode"),
          l.get("departmentId"),
          l.get("recipientId"),
          l.get("recipientName"));
    custom.saveDraft("inventory_outbound", target, body, issueId == null);
    event(target, previous == null ? "创建出库草稿" : "修改出库草稿");
    return detail(Long.toString(target));
  }

  public Object fromRequests(Map<String, Object> body) {
    require(
        body.get("ids") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0050"));
    var ids = (List<?>) body.get("ids");
    require(
        !ids.isEmpty() && ids.size() <= 50,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0051"));
    var result = new LinkedHashMap<String, Object>();
    var output = new ArrayList<Map<String, Object>>();
    long warehouse = 0;
    var seen = new HashSet<Long>();
    for (Object value : ids) {
      long reqId = id(value);
      require(
          seen.add(reqId),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0052"));
      var r = requests.outboundRequest(reqId, false, "确认出库");
      if (warehouse == 0) warehouse = id(r.get("warehouse_id"));
      require(
          warehouse == id(r.get("warehouse_id")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0053"));
      for (var l : repository.listRequestLines(auth.tenantId(), reqId)) {
        var v = masters.view(l);
        v.put("requisitionLineId", v.get("id"));
        v.put("requisitionCode", r.get("code"));
        v.put("requisitionId", Long.toString(reqId));
        v.put("taskCode", r.get("task_code"));
        v.put("taskKind", r.get("task_kind"));
        v.put("taskId", Objects.toString(r.get("task_id")));
        v.put("recipientName", r.get("applicant_name"));
        v.put("requestedQuantity", l.get("quantity"));
        v.put(
            "quantity",
            ((BigDecimal) l.get("quantity")).subtract((BigDecimal) l.get("issued_quantity")));
        v.put("selections", List.of());
        output.add(v);
      }
    }
    result.put("id", "");
    result.put("status", "草稿");
    result.put("sourceKind", "领料出库");
    result.put("warehouseId", Long.toString(warehouse));
    result.put("lines", output);
    return result;
  }

  public Object stock(long warehouse, long material, long reqLine) {
    permissions.checkInventoryWarehouse(MENU, warehouse, "确认出库");
    if (reqLine != 0) {
      var owner = repository.findLineRequest(auth.tenantId(), reqLine);
      require(
          owner.size() == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0042"));
      var req = requests.outboundRequest(id(owner.getFirst().get("requisition_id")), false, "确认出库");
      require(
          id(req.get("warehouse_id")) == warehouse,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0054"));
    }
    return repository.listStock(auth.tenantId(), warehouse, material, reqLine).stream()
        .map(masters::view)
        .toList();
  }

  private BigDecimal decimal(Map<String, Object> r, String key) {
    return (BigDecimal) r.get(key);
  }

  private String fingerprint(Object value) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(previewKey, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(
              mac.doFinal(
                  json.copy()
                      .configure(
                          com.fasterxml.jackson.databind.SerializationFeature
                              .ORDER_MAP_ENTRIES_BY_KEYS,
                          true)
                      .writeValueAsBytes(value)));
    } catch (Exception e) {
      throw new IllegalStateException("无法计算出库分配", e);
    }
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> plan(long issueId, Map<String, Object> body, boolean lock) {
    var r = row(issueId, lock, "确认出库");
    require(
        text(r, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0055"));
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    long warehouse = id(r.get("warehouse_id"));
    var w = masters.row("warehouses", warehouse, lock);
    require(
        flag(w.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    return planPrepared(r, lines(issueId), lock);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> planPrepared(
      Map<String, Object> r, List<Map<String, Object>> draftLines, boolean lock) {
    long warehouse = id(r.get("warehouse_id"));
    var w = masters.row("warehouses", warehouse, lock);
    require(
        flag(w.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0035"));
    var draft = headerData(r);
    var selections = (List<?>) draft.getOrDefault("lineSelections", List.of());
    var pool = new HashMap<Long, Map<String, Object>>();
    var layers = new HashMap<Long, List<Map<String, Object>>>();
    var reservations = new HashMap<Long, List<Map<String, Object>>>();
    var allocations = new ArrayList<Map<String, Object>>();
    var requestVersions = new TreeMap<Long, Object>();
    BigDecimal total = BigDecimal.ZERO;
    for (var l : draftLines) {
      long reqId = id(l.get("requisition_id")),
          lineId = id(l.get("id")),
          reqLine = id(l.get("requisition_line_id")),
          material = id(l.get("material_id"));
      if (reqId != 0) {
        var req = requests.outboundRequest(reqId, lock, "确认出库");
        requestVersions.put(reqId, req.get("row_version"));
        var original = repository.lockRequestQuantity(auth.tenantId(), reqLine, lock);
        require(
            decimal(l, "quantity")
                    .compareTo(
                        decimal(original, "quantity")
                            .subtract(decimal(original, "issued_quantity")))
                <= 0,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0056"));
      } else if (id(l.get("task_id")) != 0)
        requests.validateOutboundTask(
            text(l, "task_kind"), id(l.get("task_id")), id(r.get("company_id")), true);
      var m = masters.row("materials", material, lock);
      require(
          flag(m.get("enabled")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0057"));
      var candidates = repository.listMaterialStock(auth.tenantId(), warehouse, material, lock);
      String rule =
          text(m, "allocation_rule").isEmpty()
              ? text(w, "allocation_rule")
              : text(m, "allocation_rule");
      Comparator<Map<String, Object>> order =
          Comparator.comparing(x -> Objects.toString(x.get("first_received_at")));
      if (rule.equals("FEFO") && flag(m.get("expiry_enabled")))
        order =
            Comparator.<Map<String, Object>, String>comparing(
                    x -> Objects.toString(x.get("expiry_date"), "9999-12-31"))
                .thenComparing(order);
      candidates.sort(order.thenComparingLong(x -> id(x.get("id"))));
      var held =
          reqLine == 0
              ? List.<Map<String, Object>>of()
              : reservations.computeIfAbsent(
                  reqLine, key -> repository.listOwnReservations(auth.tenantId(), key, lock));
      List<Map<String, Object>> manual =
          (int) id(l.get("line_no")) <= selections.size()
              ? (List<Map<String, Object>>) selections.get((int) id(l.get("line_no")) - 1)
              : List.of();
      var chosen = new LinkedHashMap<Long, BigDecimal>();
      for (var selection : manual) {
        long bid = id(selection.get("balanceId"));
        require(
            !chosen.containsKey(bid),
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0058"));
        chosen.put(bid, InventoryFlowService.decimal(selection.get("quantity"), false));
      }
      require(
          chosen.isEmpty()
              || chosen.values().stream()
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                      .compareTo(decimal(l, "quantity"))
                  == 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0059"));
      BigDecimal need = decimal(l, "quantity");
      var valid = new HashSet<Long>();
      for (var candidate : candidates) {
        long bid = id(candidate.get("id"));
        var balance = pool.computeIfAbsent(bid, key -> candidate);
        boolean eligible =
            flag(balance.get("bin_enabled"))
                && text(balance, "stock_status").equals("可用")
                && (!flag(m.get("batch_enabled")) || id(balance.get("batch_id")) != 0)
                && (!flag(m.get("expiry_enabled")) || balance.get("expiry_date") != null)
                && (balance.get("expiry_date") == null
                    || !LocalDate.parse(balance.get("expiry_date").toString())
                        .isBefore(LocalDate.now(ZoneId.of("Asia/Shanghai"))));
        if (!eligible) continue;
        valid.add(bid);
        var own =
            held.stream()
                .filter(h -> id(h.get("balance_id")) == bid)
                .map(h -> decimal(h, "remaining_quantity"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        var free = decimal(balance, "quantity").subtract(decimal(balance, "reserved_quantity"));
        var available = free.add(own);
        if (!chosen.isEmpty() && !chosen.containsKey(bid)) continue;
        BigDecimal take = chosen.isEmpty() ? need.min(available) : chosen.get(bid);
        require(
            take.compareTo(available) <= 0,
            com.windasoft.eam.common.MessageContext.text(
                "server.inventory.message0060", text(m, "name")));
        if (take.signum() == 0) continue;
        var portions = new ArrayList<Map<String, Object>>();
        var remaining = take;
        for (var h : held)
          if (id(h.get("balance_id")) == bid && remaining.signum() > 0) {
            var used = remaining.min(decimal(h, "remaining_quantity"));
            if (used.signum() == 0) continue;
            portions.add(Map.of("quantity", used, "reservationId", h.get("id")));
            h.put("remaining_quantity", decimal(h, "remaining_quantity").subtract(used));
            remaining = remaining.subtract(used);
          }
        if (remaining.signum() > 0) portions.add(Map.of("quantity", remaining));
        var costLayers =
            layers.computeIfAbsent(
                bid, key -> repository.listCostLayers(auth.tenantId(), key, lock));
        for (var portion : portions) {
          var part = (BigDecimal) portion.get("quantity");
          for (var layer : costLayers) {
            if (part.signum() == 0) break;
            var layerQty = decimal(layer, "remaining_quantity");
            if (layerQty.signum() == 0) continue;
            require(
                layer.get("unit_cost") != null
                    && layer.get("remaining_cost") != null
                    && text(layer, "cost_status").equals("已确定"),
                com.windasoft.eam.common.MessageContext.text("server.inventory.message0061"));
            var qty = part.min(layerQty);
            var amount =
                qty.compareTo(layerQty) == 0
                    ? decimal(layer, "remaining_cost")
                    : qty.multiply(decimal(layer, "unit_cost"))
                        .setScale(2, RoundingMode.HALF_UP)
                        .min(decimal(layer, "remaining_cost"));
            var a = new LinkedHashMap<String, Object>();
            a.put("outboundLineId", Long.toString(lineId));
            a.put("requisitionLineId", reqLine == 0 ? null : Long.toString(reqLine));
            a.put("requisitionId", reqId == 0 ? null : Long.toString(reqId));
            a.put("reservationId", portion.get("reservationId"));
            a.put("balanceId", Long.toString(bid));
            a.put("costLayerId", Objects.toString(layer.get("id")));
            a.put("quantity", qty);
            a.put("amount", amount);
            a.put("unitCost", layer.get("unit_cost"));
            a.put("balanceVersion", balance.get("row_version"));
            a.put("layerQuantity", layerQty);
            a.put("layerCost", layer.get("remaining_cost"));
            a.put("materialName", m.get("name"));
            a.put("requisitionCode", l.get("requisition_code"));
            a.put("recipientName", l.get("recipient_name"));
            a.put("taskCode", l.get("task_code"));
            a.put("binName", balance.get("bin_name"));
            a.put("batchCode", balance.get("batch_code"));
            a.put("expiryDate", balance.get("expiry_date"));
            allocations.add(a);
            part = part.subtract(qty);
            layer.put("remaining_quantity", layerQty.subtract(qty));
            layer.put("remaining_cost", decimal(layer, "remaining_cost").subtract(amount));
            total = total.add(amount);
          }
          require(
              part.signum() == 0,
              com.windasoft.eam.common.MessageContext.text("server.inventory.message0062"));
        }
        balance.put("quantity", decimal(balance, "quantity").subtract(take));
        balance.put(
            "reserved_quantity", decimal(balance, "reserved_quantity").subtract(take.min(own)));
        need = need.subtract(take);
        if (need.signum() == 0) break;
      }
      require(
          valid.containsAll(chosen.keySet()),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0063"));
      require(
          need.signum() == 0,
          com.windasoft.eam.common.MessageContext.text(
              "server.inventory.message0064",
              text(m, "name"),
              need.stripTrailingZeros().toPlainString(),
              text(m, "unit")));
    }
    require(
        !allocations.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0065"));
    var picking = new LinkedHashMap<String, Map<String, Object>>();
    for (var a : allocations) {
      String key = text(a, "materialName") + ":" + text(a, "balanceId");
      var summary =
          picking.computeIfAbsent(
              key,
              k -> {
                var v = new LinkedHashMap<String, Object>();
                v.put("materialName", a.get("materialName"));
                v.put("binName", a.get("binName"));
                v.put("batchCode", a.get("batchCode"));
                v.put("expiryDate", a.get("expiryDate"));
                v.put("quantity", BigDecimal.ZERO);
                v.put("amount", BigDecimal.ZERO);
                return v;
              });
      summary.put(
          "quantity", ((BigDecimal) summary.get("quantity")).add((BigDecimal) a.get("quantity")));
      summary.put("amount", ((BigDecimal) summary.get("amount")).add((BigDecimal) a.get("amount")));
    }

    return new LinkedHashMap<>(
        Map.of(
            "pickingSummary",
            new ArrayList<>(picking.values()),
            "allocations",
            allocations,
            "totalAmount",
            total,
            "fingerprint",
            fingerprint(
                Map.of(
                    "allocations",
                    allocations,
                    "requestVersions",
                    requestVersions,
                    "rowVersion",
                    r.get("row_version"))),
            "rowVersion",
            r.get("row_version")));
  }

  public Object preview(long id, Map<String, Object> body) {
    var result = plan(id, body, false);
    var r = row(id, false, "确认出库");
    if (!permissions.inventoryWarehouseAllowed(MENU, id(r.get("warehouse_id")), "查看金额"))
      InventoryMoney.hide(result);
    return result;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Object confirm(long issueId, Map<String, Object> body) {
    mutex.enter();
    var r = row(issueId, true, "确认出库");
    String key = text(body, "confirmationKey");
    require(
        key.matches("[a-zA-Z0-9-]{16,64}"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0066"));
    if (!text(r, "status").equals("草稿")) {
      require(
          key.equals(text(r, "confirmation_key"))
              && text(body, "fingerprint").equals(text(r, "preview_fingerprint")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0067"));
      return detail(Long.toString(issueId));
    }
    custom.validateStored("inventory_outbound", issueId);
    require(
        repository.countConfirmationKey(auth.tenantId(), key, issueId) == 0,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0068"));
    var p = plan(issueId, body, true);
    require(
        text(p, "fingerprint").equals(text(body, "fingerprint")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0069"));
    var reqs = new TreeSet<Long>();
    for (var a : (List<Map<String, Object>>) p.get("allocations")) {
      var qty = (BigDecimal) a.get("quantity");
      var amount = (BigDecimal) a.get("amount");
      long allocation = IdWorker.getId();
      boolean held = id(a.get("reservationId")) != 0;
      int changed =
          repository.takeBalance(
              qty,
              held ? qty : BigDecimal.ZERO,
              auth.tenantId(),
              id(a.get("balanceId")),
              qty,
              held ? qty : BigDecimal.ZERO);
      require(
          changed == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0070"));
      if (held)
        require(
            repository.consumeReservation(qty, auth.tenantId(), id(a.get("reservationId")), qty)
                == 1,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0071"));
      require(
          repository.consumeCostLayer(
                  qty, amount, auth.tenantId(), id(a.get("costLayerId")), qty, amount)
              == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0072"));
      if (id(a.get("requisitionId")) != 0) {
        reqs.add(id(a.get("requisitionId")));
        require(
            repository.addIssuedQuantity(qty, auth.tenantId(), id(a.get("requisitionLineId")), qty)
                == 1,
            com.windasoft.eam.common.MessageContext.text("server.inventory.message0073"));
      }
      repository.insertAllocation(
          allocation,
          auth.tenantId(),
          issueId,
          id(a.get("outboundLineId")),
          a.get("requisitionLineId"),
          a.get("reservationId"),
          id(a.get("balanceId")),
          id(a.get("costLayerId")),
          qty,
          amount,
          a.get("unitCost"));
      repository.insertTransaction(
          IdWorker.getId(),
          auth.tenantId(),
          r.get("company_id"),
          r.get("warehouse_id"),
          id(a.get("balanceId")),
          issueId,
          allocation,
          qty.negate(),
          amount.negate(),
          auth.userId());
    }
    repository.confirmIssue(
        key,
        p.get("fingerprint"),
        p.get("totalAmount"),
        auth.userId(),
        auth.user().getDisplayName(),
        auth.tenantId(),
        issueId);
    for (long req : reqs) requests.outboundCompleted(req, text(r, "code"));
    var tasks = new HashSet<String>();
    for (var l : lines(issueId))
      if (id(l.get("requisition_id")) == 0
          && id(l.get("task_id")) != 0
          && tasks.add(text(l, "task_kind") + ":" + l.get("task_id")))
        requests.taskEvent(l, "出库完成 · " + text(r, "code"));
    var notified = new HashSet<Long>();
    if (reqs.isEmpty())
      for (var l : lines(issueId))
        if (id(l.get("recipient_id")) != 0 && notified.add(id(l.get("recipient_id"))))
          repository.insertRecipientNotice(
              IdWorker.getId(),
              auth.tenantId(),
              l.get("recipient_id"),
              issueId,
              text(r, "code") + " · " + text(l, "recipient_name"));
    event(issueId, "确认出库 · " + text(r, "code"));
    return detail(Long.toString(issueId));
  }

  @Transactional
  public void delete(long id, Map<String, Object> body) {
    mutex.enter();
    var r = row(id, true, "删除草稿");
    require(
        text(r, "status").equals("草稿"),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0074"));
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    repository.deleteIssue(auth.tenantId(), id);
    event(id, "删除草稿");
    custom.remove("inventory_outbound", id);
  }

  private List<Map<String, Object>> allocations(long issueId) {
    return repository.listAllocations(auth.tenantId(), issueId).stream()
        .map(masters::view)
        .toList();
  }

  public Object detail(String reference) {
    if (reference.startsWith("g")) {
      var groups = repository.findLegacyGroup(auth.tenantId(), id(reference.substring(1)));
      require(
          groups.size() == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0075"));
      var g = groups.getFirst();
      permissions.checkInventoryWarehouse(MENU, id(g.get("warehouse_id")), "查看");
      var result = masters.view(g);
      result.put("id", reference);
      result.put("sourceKind", "历史合并");
      var groupWarehouse = masters.row("warehouses", id(g.get("warehouse_id")), false);
      result.put("warehouseName", groupWarehouse.get("name"));
      result.put(
          "companyName",
          repository.findLegacyCompany(auth.tenantId(), groupWarehouse.get("company_id")));

      var children = repository.listLegacyGroupIssues(auth.tenantId(), g.get("id"));
      var merged = new ArrayList<Map<String, Object>>();
      BigDecimal sum = BigDecimal.ZERO;
      for (var child : children) {
        merged.addAll(allocations(id(child.get("id"))));
        sum = sum.add(repository.findLegacyAmount(auth.tenantId(), child.get("id")));
      }
      result.put("allocations", merged);
      result.put("children", children.stream().map(masters::view).toList());
      result.put("totalAmount", sum);
      result.put(
          "status", children.stream().allMatch(c -> flag(c.get("reversed"))) ? "已冲销" : "已出库");
      result.put("lines", List.of());
      result.put(
          "events",
          List.of(
              Map.of(
                  "action",
                  "确认合并出库 · " + text(g, "code"),
                  "actorName",
                  g.get("actor_name"),
                  "createdAt",
                  g.get("created_at"))));
      if (!permissions.inventoryWarehouseAllowed(MENU, id(g.get("warehouse_id")), "查看金额"))
        InventoryMoney.hide(result);
      return result;
    }
    long issueId = id(reference);
    var r = row(issueId, false, "查看");
    var v = masters.view(r);
    v.remove("payload");
    v.remove("confirmationKey");
    v.remove("previewFingerprint");
    v.putAll(headerData(r));
    v.put("warehouseName", masters.row("warehouses", id(r.get("warehouse_id")), false).get("name"));
    v.put("companyName", repository.findCompany(auth.tenantId(), r.get("company_id")));
    var ls = lines(issueId).stream().map(masters::view).toList();
    var selections = (List<?>) headerData(r).getOrDefault("lineSelections", List.of());
    for (int i = 0; i < ls.size(); i++)
      ls.get(i).put("selections", i < selections.size() ? selections.get(i) : List.of());
    if (!ls.isEmpty()) {
      v.put("departmentName", ls.getFirst().get("departmentName"));
      v.put("recipientName", ls.getFirst().get("recipientName"));
      if (text(r, "source_kind").equals("直接出库")) v.put("taskCode", ls.getFirst().get("taskCode"));
    }
    v.put("lines", ls);
    v.put("allocations", allocations(issueId));
    v.put(
        "events",
        repository.listEvents(auth.tenantId(), issueId).stream().map(masters::view).toList());
    if (!permissions.inventoryWarehouseAllowed(MENU, id(r.get("warehouse_id")), "查看金额"))
      InventoryMoney.hide(v);
    custom.attach("inventory_outbound", v);
    return v;
  }

  public Object list(String q, String filters, String conditions, int page, int size) {
    return listQueries.outbounds(q, filters, conditions, page, size, false);
  }

  public Object export(String q, String filters, String conditions) {
    return listQueries.exportOutbounds(q, filters, conditions);
  }

  public Object pending(
      String q, String filters, String conditions, int page, int size, boolean export) {
    return export
        ? listQueries.exportRequisitions(q, filters, conditions, true)
        : listQueries.requisitions(q, filters, conditions, page, size, false, true);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> legacyForm(long requestId, Map<String, Object> body) {
    var req = requests.outboundRequest(requestId, false, "确认出库");
    VersionGuard.checkPayload((Number) req.get("row_version"), body);
    var f = (Map<String, Object>) fromRequests(Map.of("ids", List.of(Long.toString(requestId))));
    var sums = new TreeMap<Long, BigDecimal>();
    var bins = new HashMap<Long, List<Map<String, Object>>>();
    require(
        body.get("selections") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0076"));
    var seen = new HashSet<Long>();
    for (Object input : (List<?>) body.get("selections")) {
      var v = (Map<String, Object>) input;
      long res = id(v.get("reservationId"));
      require(
          seen.add(res),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0077"));
      var found = repository.findReservation(auth.tenantId(), res, requestId);
      require(
          found.size() == 1,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0078"));
      var held = found.getFirst();
      var qty = InventoryFlowService.decimal(v.get("quantity"), true);
      require(
          qty.compareTo((BigDecimal) held.get("remaining_quantity")) <= 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0079"));
      if (qty.signum() == 0) continue;
      long rl = id(held.get("requisition_line_id"));
      sums.merge(rl, qty, BigDecimal::add);
      bins.computeIfAbsent(rl, k -> new ArrayList<>())
          .add(new LinkedHashMap<>(Map.of("balanceId", held.get("balance_id"), "quantity", qty)));
    }
    var ls = (List<Map<String, Object>>) f.get("lines");
    ls.removeIf(l -> !sums.containsKey(id(l.get("requisitionLineId"))));
    for (var l : ls) {
      long rl = id(l.get("requisitionLineId"));
      l.put("quantity", sums.get(rl));
      var byBalance = new TreeMap<Long, BigDecimal>();
      bins.get(rl)
          .forEach(
              b ->
                  byBalance.merge(
                      id(b.get("balanceId")), (BigDecimal) b.get("quantity"), BigDecimal::add));
      l.put(
          "selections",
          byBalance.entrySet().stream()
              .map(e -> Map.of("balanceId", e.getKey(), "quantity", e.getValue()))
              .toList());
    }
    require(
        !ls.isEmpty(),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0049"));
    return f;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> transientPlan(Map<String, Object> f) {
    long warehouse = id(f.get("warehouseId"));
    var w = masters.row("warehouses", warehouse, false);
    var raw = new LinkedHashMap<String, Object>();
    raw.put("warehouse_id", warehouse);
    raw.put("company_id", w.get("company_id"));
    raw.put("row_version", 0);
    var ls = new ArrayList<Map<String, Object>>();
    var selections = new ArrayList<Object>();
    int n = 0;
    for (var l : (List<Map<String, Object>>) f.get("lines")) {
      var row = new LinkedHashMap<String, Object>();
      row.put("id", l.get("requisitionLineId"));
      row.put("line_no", ++n);
      row.put("quantity", new BigDecimal(l.get("quantity").toString()));
      row.put("material_id", l.get("materialId"));
      row.put("requisition_id", l.get("requisitionId"));
      row.put("requisition_line_id", l.get("requisitionLineId"));
      row.put("task_id", l.get("taskId"));
      row.put("task_kind", l.get("taskKind"));
      row.put("requisition_code", l.get("requisitionCode"));
      row.put("recipient_name", l.get("recipientName"));
      row.put("task_code", l.get("taskCode"));
      ls.add(row);
      selections.add(l.getOrDefault("selections", List.of()));
    }
    raw.put("line_selections", selections);
    return planPrepared(raw, ls, false);
  }

  public Map<String, Object> legacyPreview(long requestId, Map<String, Object> body) {
    var p = transientPlan(legacyForm(requestId, body));
    p.put("rowVersion", requests.outboundRequest(requestId, false, "确认出库").get("row_version"));
    if (!permissions.inventoryWarehouseAllowed(
        MENU, id(requests.outboundRequest(requestId, false, "确认出库").get("warehouse_id")), "查看金额"))
      InventoryMoney.hide(p);
    return p;
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Object legacyConfirm(long requestId, Map<String, Object> body) {
    mutex.enter();
    String key = text(body, "confirmationKey");
    var found = repository.findConfirmedIssue(auth.tenantId(), key);
    if (!found.isEmpty()) {
      row(id(found.getFirst().get("id")), false, "确认出库");
      require(
          id(found.getFirst().get("requisition_id")) == requestId
              || repository.countIssueRequest(
                      auth.tenantId(), found.getFirst().get("id"), requestId)
                  > 0,
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0080"));

      require(
          Objects.toString(
                  headerData(found.getFirst())
                      .getOrDefault(
                          "legacyFingerprint", found.getFirst().get("preview_fingerprint")),
                  "")
              .equals(text(body, "fingerprint")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0081"));
      return requests.detail(requestId);
    }
    var f = legacyForm(requestId, body);
    var p = transientPlan(f);
    require(
        text(p, "fingerprint").equals(text(body, "fingerprint")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0082"));
    var created = (Map<String, Object>) save(null, f);
    long issue = id(created.get("id"));
    var raw = row(issue, true, "确认出库");
    var data = headerData(raw);
    data.put("legacyFingerprint", p.get("fingerprint"));
    repository.updatePayload(encode(data), auth.tenantId(), issue);
    var current = plan(issue, Map.of("rowVersion", created.get("rowVersion")), true);
    confirm(
        issue,
        Map.of(
            "rowVersion",
            current.get("rowVersion"),
            "fingerprint",
            current.get("fingerprint"),
            "confirmationKey",
            key));
    return requests.detail(requestId);
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Object legacyMergedPreview(Map<String, Object> body) {
    var f = (Map<String, Object>) fromRequests(body);
    var p = transientPlan(f);
    if (!permissions.inventoryWarehouseAllowed(MENU, id(f.get("warehouseId")), "查看金额"))
      InventoryMoney.hide(p);
    return Map.of("form", f, "preview", p, "ids", body.get("ids"));
  }

  @Transactional
  @SuppressWarnings("unchecked")
  public Object legacyMergedConfirm(Map<String, Object> body) {
    mutex.enter();
    String key = text(body, "confirmationKey");
    var prior = repository.findIssueByKey(auth.tenantId(), key);
    if (!prior.isEmpty()) {
      var old = prior.getFirst();
      row(id(old.get("id")), false, "确认出库");
      require(
          text(headerData(old), "legacyFingerprint").equals(text(body, "fingerprint")),
          com.windasoft.eam.common.MessageContext.text("server.inventory.message0083"));
      return Map.of("groupCode", old.get("code"), "outboundId", Objects.toString(old.get("id")));
    }
    require(
        body.get("ids") instanceof List<?>,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0084"));
    var f = (Map<String, Object>) fromRequests(body);
    var p = transientPlan(f);
    require(
        text(p, "fingerprint").equals(text(body, "fingerprint")),
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0085"));
    var created = (Map<String, Object>) save(null, f);
    long issue = id(created.get("id"));
    var stored = headerData(row(issue, true, "确认出库"));
    stored.put("legacyFingerprint", p.get("fingerprint"));
    repository.storePreviewPayload(encode(stored), auth.tenantId(), issue);
    var current = plan(issue, Map.of("rowVersion", created.get("rowVersion")), true);
    confirm(
        issue,
        Map.of(
            "rowVersion",
            current.get("rowVersion"),
            "fingerprint",
            current.get("fingerprint"),
            "confirmationKey",
            body.get("confirmationKey")));
    return Map.of("groupCode", created.get("code"), "outboundId", created.get("id"));
  }
}
