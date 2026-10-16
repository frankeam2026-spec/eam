package com.windasoft.eam.service.printing;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.dto.printing.PrintForms;
import com.windasoft.eam.repository.printing.DocumentPrintRepository;
import com.windasoft.eam.service.inventory.OutboundService;
import com.windasoft.eam.service.system.*;
import java.math.*;
import java.util.*;
import org.apache.pdfbox.Loader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class DocumentPrintService {
  private static final String MENU = "printTemplate", OUTBOUND = "inventoryOutbound";
  private final DocumentPrintRepository repo;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final OutboundService outbounds;
  private final DocumentPrintLayout layout;
  private final ObjectMapper json;
  private final byte[] previewKey = new byte[32];

  public DocumentPrintService(
      DocumentPrintRepository repo,
      AuthContext auth,
      PermissionService permissions,
      OutboundService outbounds,
      DocumentPrintLayout layout,
      ObjectMapper json) {
    this.repo = repo;
    this.auth = auth;
    this.permissions = permissions;
    this.outbounds = outbounds;
    this.layout = layout;
    this.json = json;
    new java.security.SecureRandom().nextBytes(previewKey);
  }

  private long id(Object value) {
    return value instanceof Boolean b ? (b ? 1 : 0) : Long.parseLong(String.valueOf(value));
  }

  private String text(Object value) {
    return value == null ? "" : String.valueOf(value);
  }

  private void require(boolean value, String message) {
    if (!value) throw new IllegalArgumentException(message);
  }

  private String encode(Object value) {
    try {
      return json.writer()
          .with(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
          .writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("打印内容无法序列化", e);
    }
  }

  private JsonNode decode(Object value) {
    try {
      return json.readTree(String.valueOf(value));
    } catch (Exception e) {
      throw new IllegalStateException("打印配置无法读取", e);
    }
  }

  private void company(Long company, String action) {
    var scope = permissions.scope(MENU, action);
    if (!(scope.all() || company != null && scope.companyIds().contains(company)))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权维护或查看该公司模板");
  }

  private boolean permitted(Long company, String action) {
    var s = permissions.scope(MENU, action);
    return s.all() || company != null && s.companyIds().contains(company);
  }

  private boolean visible(Long company) {
    var s = permissions.scope(MENU, "查看");
    return s.all() || company == null || s.companyIds().contains(company);
  }

  private Long owner(Map<String, Object> t) {
    return t.get("company_id") == null ? null : id(t.get("company_id"));
  }

  private Map<String, Object> templateView(Map<String, Object> t) {
    var v = new LinkedHashMap<String, Object>();
    v.put("id", text(t.get("id")));
    v.put("name", t.get("name"));
    v.put("companyId", t.get("company_id") == null ? "" : text(t.get("company_id")));
    v.put("companyName", text(t.get("company_name")));
    v.put("enabled", id(t.get("enabled")) == 1);
    v.put("isDefault", id(t.get("is_default")) == 1);
    v.put("rowVersion", t.get("row_version"));
    v.put("config", decode(t.get("config_json")));
    v.put("updatedAt", text(t.get("updated_at")));
    v.put("canEdit", permitted(owner(t), "编辑"));
    v.put("canDelete", permitted(owner(t), "删除"));
    return v;
  }

  public Map<String, Object> catalog() {
    permissions.require(MENU, "查看");
    var companies =
        repo.companies(auth.tenantId()).stream()
            .filter(c -> visible(id(c.get("id"))))
            .map(c -> Map.of("id", text(c.get("id")), "name", text(c.get("name"))))
            .toList();
    return Map.of(
        "defaults",
        layout.defaults(),
        "headers",
        DocumentPrintLayout.HEADERS,
        "columns",
        DocumentPrintLayout.COLUMNS,
        "companies",
        companies,
        "canManageCommon",
        permissions.scope(MENU, "查看").all());
  }

  public List<Map<String, Object>> templates() {
    permissions.require(MENU, "查看");
    return repo.templates(auth.tenantId()).stream()
        .filter(t -> visible(owner(t)))
        .map(this::templateView)
        .toList();
  }

  @Transactional
  public Map<String, Object> saveTemplate(Long templateId, PrintForms.Template f) {
    String action = templateId == null ? "新增" : "编辑";
    permissions.require(MENU, action);
    company(f.companyId(), action);
    layout.validate(f.config());
    if (f.companyId() != null)
      require(
          repo.companies(auth.tenantId()).stream().anyMatch(c -> id(c.get("id")) == f.companyId()),
          "请选择有效的公司");
    require(!f.isDefault() || f.enabled(), "停用模板不能设置为默认");
    long key = templateId == null ? IdWorker.getId() : templateId;
    if (templateId != null) {
      var old = repo.template(auth.tenantId(), key);
      company(owner(old), action);
      require(
          f.rowVersion() != null && id(old.get("row_version")) == f.rowVersion(), "模板已被修改，请重新打开");
    }
    if (f.isDefault()) repo.clearDefaults(auth.tenantId(), f.companyId(), key);
    if (templateId == null)
      repo.insertTemplate(
          key,
          auth.tenantId(),
          f.name().trim(),
          f.companyId(),
          f.enabled(),
          f.isDefault(),
          encode(f.config()));
    else
      require(
          repo.updateTemplate(
                  auth.tenantId(),
                  key,
                  f.name().trim(),
                  f.companyId(),
                  f.enabled(),
                  f.isDefault(),
                  encode(f.config()),
                  f.rowVersion())
              == 1,
          "模板已被修改，请重新打开");
    return templateView(repo.template(auth.tenantId(), key));
  }

  @Transactional
  public void deleteTemplate(long key, int version) {
    permissions.require(MENU, "删除");
    var t = repo.template(auth.tenantId(), key);
    company(owner(t), "删除");
    require(repo.deleteTemplate(auth.tenantId(), key, version) == 1, "模板已被修改，请刷新后再删除");
  }

  public byte[] designerPreview(PrintForms.Template f) throws java.io.IOException {
    permissions.require(MENU, "查看");
    require(visible(f.companyId()), "无权查看该公司模板");
    layout.validate(f.config());
    var d = new LinkedHashMap<String, Object>();
    d.put("code", "CK-202610-0001");
    d.put("status", "演示预览");
    d.put("companyName", "示例制造有限公司");
    d.put("warehouseName", "中心仓库");
    d.put("sourceKind", "领料出库");
    d.put("confirmedAt", "2026-10-16 09:00:00");
    d.put("actorName", "演示人员");
    d.put("purpose", "设备日常维修领用");
    d.put("recipientName", "演示领用人");
    d.put("departmentName", "设备维修部");
    d.put("taskCode", "wx-202610-0001");
    var line = new LinkedHashMap<String, String>();
    for (String key : DocumentPrintLayout.COLUMNS.keySet()) line.put(key, "");
    line.putAll(
        Map.of(
            "sequence",
            "1",
            "code",
            "WL00001",
            "name",
            "深沟球轴承",
            "model",
            "6205-2RS",
            "unit",
            "个",
            "quantity",
            "2.00",
            "recipient",
            "演示领用人",
            "department",
            "设备维修部",
            "task",
            "wx-202610-0001"));
    line.putAll(
        Map.of(
            "batch",
            "DEMO-001",
            "bin",
            "A-01",
            "unitCost",
            "25.00",
            "amount",
            "50.00",
            "currency",
            "CNY",
            "requisition",
            "LL-202610-0001"));
    return layout.render(f.config(), d, List.of(line));
  }

  private Map<String, Object> source(String document) {
    var s = repo.source(auth.tenantId(), document);
    checkWarehouse(id(s.get("warehouse_id")), false);
    return s;
  }

  private void checkWarehouse(long warehouse, boolean money) {
    permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "查看");
    permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "打印");
    if (money) permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "查看金额");
  }

  private Map<String, Object> builtin() {
    return new LinkedHashMap<>(
        Map.of(
            "id",
            "system",
            "name",
            "标准出库单（系统）",
            "enabled",
            1,
            "is_default",
            1,
            "row_version",
            0,
            "config_json",
            encode(layout.defaults())));
  }

  private List<Map<String, Object>> available(long company, long warehouse) {
    boolean money = permissions.inventoryWarehouseAllowed(OUTBOUND, warehouse, "查看金额");
    var rows = new ArrayList<Map<String, Object>>();
    for (var t : repo.templates(auth.tenantId()))
      if (id(t.get("enabled")) == 1
          && (owner(t) == null || owner(t) == company)
          && (!layout.money(decode(t.get("config_json"))) || money)) rows.add(t);
    rows.sort(
        Comparator.comparingInt(
            t -> id(t.get("is_default")) == 1 ? (owner(t) == null ? 1 : 0) : 2));
    rows.add(builtin());
    return rows;
  }

  public Map<String, Object> options(String document) {
    var s = source(document);
    var list = available(id(s.get("company_id")), id(s.get("warehouse_id")));
    return Map.of(
        "templates",
        list.stream()
            .map(
                t ->
                    Map.of(
                        "id",
                        text(t.get("id")),
                        "name",
                        text(t.get("name")),
                        "rowVersion",
                        t.get("row_version"),
                        "containsMoney",
                        layout.money(decode(t.get("config_json")))))
            .toList(),
        "defaultId",
        text(list.getFirst().get("id")));
  }

  private record Prepared(
      Map<String, Object> source,
      Map<String, Object> template,
      JsonNode config,
      Map<String, Object> document,
      List<Map<String, String>> lines,
      String fingerprint) {}

  @SuppressWarnings("unchecked")
  private Prepared prepare(PrintForms.Output f) {
    var s = source(f.documentId());
    var t =
        available(id(s.get("company_id")), id(s.get("warehouse_id"))).stream()
            .filter(x -> text(x.get("id")).equals(f.templateId()))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("模板已停用、不适用或无金额权限"));
    var config = decode(t.get("config_json"));
    layout.validate(config);
    boolean money = layout.money(config);
    checkWarehouse(id(s.get("warehouse_id")), money);
    var raw = (Map<String, Object>) outbounds.detail(f.documentId());
    var d = new LinkedHashMap<String, Object>();
    for (String key : DocumentPrintLayout.HEADERS.keySet()) d.put(key, text(raw.get(key)));
    d.put(
        "confirmedAt",
        text(raw.getOrDefault("confirmedAt", raw.get("createdAt")))
            .replaceFirst("T", " ")
            .replaceFirst("\\.\\d+$", ""));
    d.put("code", raw.get("code"));
    d.put("status", raw.get("status"));
    d.put("companyName", text(s.get("company_name")));
    d.put("rowVersion", raw.getOrDefault("rowVersion", 0));
    var lines = new ArrayList<Map<String, String>>();
    if (!text(raw.get("status")).equals("草稿")) {
      var allocations = repo.allocations(auth.tenantId(), f.documentId());
      require(allocations.size() <= 1000, "出库分配过多，请分批打印");
      for (var a : allocations) {
        var row = new LinkedHashMap<String, String>();
        for (String key : DocumentPrintLayout.COLUMNS.keySet())
          if (!DocumentPrintLayout.MONEY.contains(key) || money) row.put(key, text(a.get(key)));
        row.put("sequence", String.valueOf(lines.size() + 1));
        row.put("quantity", decimal(a.get("quantity")));
        if (money) {
          row.put("unitCost", decimal(a.get("unit_cost")));
          row.put("amount", decimal(a.get("amount")));
        }
        lines.add(row);
      }
    } else {
      require(!money, "草稿尚无实际出库成本，请选择无金额模板");
      for (var a : (List<Map<String, Object>>) raw.get("lines")) {
        var row = new LinkedHashMap<String, String>();
        row.put("sequence", String.valueOf(lines.size() + 1));
        row.put("code", text(a.get("materialCode")));
        row.put("name", text(a.get("materialName")));
        row.put("model", text(a.get("model")));
        row.put("unit", text(a.get("unit")));
        row.put("quantity", decimal(a.get("quantity")));
        row.put("recipient", text(a.get("recipientName")));
        row.put("department", text(a.get("departmentName")));
        row.put("task", text(a.get("taskCode")));
        row.put("requisition", text(a.get("requisitionCode")));
        lines.add(row);
      }
    }
    require(!lines.isEmpty(), "出库单没有可打印的明细");
    for (var pair :
        Map.of("recipientName", "recipient", "departmentName", "department", "taskCode", "task")
            .entrySet()) {
      var distinct =
          lines.stream()
              .map(l -> l.getOrDefault(pair.getValue(), ""))
              .filter(v -> !v.isBlank())
              .distinct()
              .toList();
      d.put(
          pair.getKey(),
          distinct.size() > 1 ? "详见明细" : distinct.isEmpty() ? "" : distinct.getFirst());
    }
    String fingerprint =
        sign(
            encode(
                Map.of(
                    "document",
                    d,
                    "lines",
                    lines,
                    "template",
                    config,
                    "version",
                    t.get("row_version"))));
    return new Prepared(s, t, config, d, lines, fingerprint);
  }

  private String decimal(Object n) {
    return n == null
        ? ""
        : new BigDecimal(n.toString()).setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private String sign(String value) {
    try {
      var mac = javax.crypto.Mac.getInstance("HmacSHA256");
      mac.init(new javax.crypto.spec.SecretKeySpec(previewKey, "HmacSHA256"));
      return HexFormat.of()
          .formatHex(mac.doFinal(value.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String hash(String value) {
    return hashBytes(value.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  private String hashBytes(byte[] value) {
    try {
      return HexFormat.of()
          .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional(readOnly = true)
  public Map<String, Object> preview(PrintForms.Output f) throws java.io.IOException {
    var p = prepare(f);
    byte[] pdf = layout.render(p.config, p.document, p.lines);
    return Map.of(
        "pdf",
        Base64.getEncoder().encodeToString(pdf),
        "fingerprint",
        p.fingerprint,
        "code",
        p.document.get("code"));
  }

  @Transactional
  public Map<String, Object> generate(PrintForms.Output f) throws java.io.IOException {
    require(
        f.requestKey() != null && f.requestKey().matches("[A-Za-z0-9_-]{16,64}"), "缺少有效的生成请求标识");
    String requestHash =
        hash(encode(List.of(text(f.documentId()), text(f.templateId()), text(f.fingerprint()))));
    var retry = repo.retry(auth.tenantId(), auth.userId(), f.requestKey(), false);
    if (retry != null) {
      require(requestHash.equals(retry.get("request_hash")), "相同请求标识不能用于不同内容");
      return job(id(retry.get("id")));
    }
    var p = prepare(f);
    require(p.fingerprint.equals(f.fingerprint()), "单据或模板已变化，请重新预览");
    byte[] pdf = layout.render(p.config, p.document, p.lines);
    int pages;
    try (var doc = Loader.loadPDF(pdf)) {
      pages = doc.getNumberOfPages();
    }
    require(pages <= 100, "单据超过100页，请调整纸张或明细列");
    long key = IdWorker.getId();
    Long template = p.template.get("id").equals("system") ? null : id(p.template.get("id"));
    repo.insertJob(
        key,
        auth.tenantId(),
        id(p.source.get("company_id")),
        id(p.source.get("warehouse_id")),
        f.documentId(),
        text(p.document.get("code")),
        text(p.document.get("status")),
        template,
        text(p.template.get("name")),
        (int) id(p.template.get("row_version")),
        encode(p.config),
        encode(Map.of("header", p.document, "lines", p.lines)),
        layout.money(p.config),
        auth.userId(),
        auth.user().getDisplayName(),
        f.requestKey(),
        requestHash,
        hashBytes(pdf),
        pages,
        pdf);
    var stored = repo.retry(auth.tenantId(), auth.userId(), f.requestKey(), true);
    require(requestHash.equals(stored.get("request_hash")), "相同请求标识不能用于不同内容");
    var result = repo.lockedJob(auth.tenantId(), id(stored.get("id")));
    checkJob(result);
    return jobView(result);
  }

  private void checkJob(Map<String, Object> j) {
    checkWarehouse(id(j.get("warehouse_id")), id(j.get("contains_money")) == 1);
  }

  private Map<String, Object> jobView(Map<String, Object> j) {
    var v = new LinkedHashMap<String, Object>();
    for (var e : j.entrySet())
      if (!Set.of("pdf_content", "company_id", "warehouse_id", "contains_money")
          .contains(e.getKey()))
        v.put(
            camel(e.getKey()), e.getValue() instanceof Number ? text(e.getValue()) : e.getValue());
    return v;
  }

  private String camel(String key) {
    var b = new StringBuilder();
    boolean upper = false;
    for (char c : key.toCharArray()) {
      if (c == '_') upper = true;
      else {
        b.append(upper ? Character.toUpperCase(c) : c);
        upper = false;
      }
    }
    return b.toString();
  }

  public Map<String, Object> job(long key) {
    var j = repo.job(auth.tenantId(), key, false);
    checkJob(j);
    return jobView(j);
  }

  public List<Map<String, Object>> jobs(String document) {
    source(document);
    return repo.jobs(auth.tenantId(), document).stream()
        .filter(
            j ->
                id(j.get("contains_money")) == 0
                    || permissions.inventoryWarehouseAllowed(
                        OUTBOUND, id(j.get("warehouse_id")), "查看金额"))
        .map(this::jobView)
        .toList();
  }

  public byte[] file(long key) {
    var j = repo.job(auth.tenantId(), key, true);
    checkJob(j);
    return (byte[]) j.get("pdf_content");
  }
}
