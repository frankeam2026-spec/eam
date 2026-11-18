package com.windasoft.eam.service.printing;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.dto.printing.PrintForms;
import com.windasoft.eam.dto.printing.PrintViews;
import com.windasoft.eam.entity.printing.PrintJob;
import com.windasoft.eam.entity.printing.PrintTemplate;
import com.windasoft.eam.repository.printing.DocumentPrintRepository;
import com.windasoft.eam.repository.printing.PrintJobRepository;
import com.windasoft.eam.repository.printing.PrintTemplateRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.inventory.OutboundService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.apache.pdfbox.Loader;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPrintService {
  private static final String MENU = "printTemplate", OUTBOUND = "inventoryOutbound";
  private final DocumentPrintRepository repo;
  private final PrintTemplateRepository templates;
  private final PrintJobRepository jobs;
  private final OrgUnitRepository organizations;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final OutboundService outbounds;
  private final DocumentPrintLayout layout;
  private final ObjectMapper json;
  private final byte[] previewKey = new byte[32];

  public DocumentPrintService(
      DocumentPrintRepository repo,
      PrintTemplateRepository templates,
      PrintJobRepository jobs,
      OrgUnitRepository organizations,
      AuthContext auth,
      PermissionService permissions,
      OutboundService outbounds,
      DocumentPrintLayout layout,
      ObjectMapper json) {
    this.repo = repo;
    this.templates = templates;
    this.jobs = jobs;
    this.organizations = organizations;
    this.auth = auth;
    this.permissions = permissions;
    this.outbounds = outbounds;
    this.layout = layout;
    this.json = json;
    new SecureRandom().nextBytes(previewKey);
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
      throw new LocalizedArgumentException(MessageRef.of("server.printing.message0005"), e);
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
      throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.printing.message0006");
  }

  private boolean permitted(Long company, String action) {
    var s = permissions.scope(MENU, action);
    return s.all() || company != null && s.companyIds().contains(company);
  }

  private boolean visible(Long company) {
    var s = permissions.scope(MENU, "查看");
    return s.all() || company == null || s.companyIds().contains(company);
  }

  private PrintViews.Template templateView(PrintTemplate t) {
    return new PrintViews.Template(
        t.getId().toString(),
        t.getName(),
        t.getCompanyId() == null ? "" : t.getCompanyId().toString(),
        text(t.getCompanyName()),
        t.getEnabled() == 1,
        t.getIsDefault() == 1,
        t.getRowVersion(),
        decode(t.getConfigJson()),
        text(t.getUpdatedAt()),
        permitted(t.getCompanyId(), "编辑"),
        permitted(t.getCompanyId(), "删除"));
  }

  public PrintViews.Catalog catalog() {
    permissions.require(MENU, "查看");
    var companies =
        organizations.activeCompanies(auth.tenantId()).stream()
            .filter(c -> visible(c.getId()))
            .map(c -> new PrintViews.Company(c.getId().toString(), text(c.getName())))
            .toList();
    return new PrintViews.Catalog(
        layout.defaults(),
        layout.headerLabels(),
        layout.columnLabels(),
        companies,
        permissions.scope(MENU, "查看").all());
  }

  public List<PrintViews.Template> templates() {
    permissions.require(MENU, "查看");
    return templates.list(auth.tenantId()).stream()
        .filter(t -> visible(t.getCompanyId()))
        .map(this::templateView)
        .toList();
  }

  /** 校验原归属及目标公司，默认项清理和乐观锁写入仍在同一事务；冲突回滚全部变更。 */
  @Transactional
  public PrintViews.Template saveTemplate(Long templateId, PrintForms.Template f) {
    String action = templateId == null ? "新增" : "编辑";
    permissions.require(MENU, action);
    company(f.companyId(), action);
    layout.validate(f.config());
    if (f.companyId() != null)
      require(
          organizations.activeCompanies(auth.tenantId()).stream()
              .anyMatch(c -> c.getId().equals(f.companyId())),
          MessageContext.text("server.printing.message0018"));
    require(!f.isDefault() || f.enabled(), MessageContext.text("server.printing.message0019"));
    long key = templateId == null ? IdWorker.getId() : templateId;
    if (templateId != null) {
      var old = templates.find(auth.tenantId(), key);
      company(old.getCompanyId(), action);
      require(
          f.rowVersion() != null && old.getRowVersion().equals(f.rowVersion()),
          MessageContext.text("server.common.message0067"));
    }
    if (f.isDefault()) templates.clearDefaults(auth.tenantId(), f.companyId(), key);
    var row = new PrintTemplate();
    row.setId(key);
    row.setTenantId(auth.tenantId());
    row.setName(f.name().trim());
    row.setCompanyId(f.companyId());
    row.setEnabled(f.enabled() ? 1 : 0);
    row.setIsDefault(f.isDefault() ? 1 : 0);
    row.setConfigJson(encode(f.config()));
    if (templateId == null) templates.insert(row);
    else
      require(
          templates.update(row, f.rowVersion()) == 1,
          MessageContext.text("server.common.message0067"));
    return templateView(templates.find(auth.tenantId(), key));
  }

  @Transactional
  public void deleteTemplate(long key, int version) {
    permissions.require(MENU, "删除");
    var t = templates.find(auth.tenantId(), key);
    company(t.getCompanyId(), "删除");
    require(
        templates.delete(auth.tenantId(), key, version) == 1,
        MessageContext.text("server.printing.message0020"));
  }

  public byte[] designerPreview(PrintForms.Template f) throws IOException {
    permissions.require(MENU, "查看");
    require(visible(f.companyId()), MessageContext.text("server.printing.message0021"));
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

  private PrintViews.Source source(String document) {
    var s = repo.source(auth.tenantId(), document);
    checkWarehouse(s.warehouseId(), false);
    return s;
  }

  private void checkWarehouse(long warehouse, boolean money) {
    permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "查看");
    permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "打印");
    if (money) permissions.checkInventoryWarehouse(OUTBOUND, warehouse, "查看金额");
  }

  private record SelectedTemplate(String id, String name, int rowVersion, JsonNode config) {}

  private List<SelectedTemplate> available(long company, long warehouse) {
    boolean money = permissions.inventoryWarehouseAllowed(OUTBOUND, warehouse, "查看金额");
    var rows =
        templates.list(auth.tenantId()).stream()
            .filter(
                t ->
                    t.getEnabled() == 1
                        && (t.getCompanyId() == null || t.getCompanyId() == company))
            .filter(t -> !layout.money(decode(t.getConfigJson())) || money)
            .sorted(
                Comparator.comparingInt(
                    t -> t.getIsDefault() == 1 ? (t.getCompanyId() == null ? 1 : 0) : 2))
            .map(
                t ->
                    new SelectedTemplate(
                        t.getId().toString(),
                        t.getName(),
                        t.getRowVersion(),
                        decode(t.getConfigJson())))
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    rows.add(
        new SelectedTemplate(
            "system", MessageContext.text("output.print.builtinName"), 0, layout.defaults()));
    return rows;
  }

  public PrintViews.Options options(String document) {
    var s = source(document);
    var list = available(s.companyId(), s.warehouseId());
    return new PrintViews.Options(
        list.stream()
            .map(
                t ->
                    new PrintViews.Choice(
                        t.id(), t.name(), t.rowVersion(), layout.money(t.config())))
            .toList(),
        list.getFirst().id());
  }

  /** document/lines仅是打印器白名单字段字典，不用于持久化或跨层传递固定业务元数据。 */
  private record Prepared(
      PrintViews.Source source,
      SelectedTemplate template,
      JsonNode config,
      Map<String, Object> document,
      List<Map<String, String>> lines,
      String fingerprint) {}

  @SuppressWarnings("unchecked")
  /** 来源先校验仓库查看/打印权限，选定模板后复核金额权限；字段字典仅交给现有渲染器和规范化签名。 */
  private Prepared prepare(PrintForms.Output f) {
    var s = source(f.documentId());
    var t =
        available(s.companyId(), s.warehouseId()).stream()
            .filter(x -> x.id().equals(f.templateId()))
            .findFirst()
            .orElseThrow(() -> new LocalizedArgumentException("server.printing.message0007"));
    var config = t.config();
    ((ObjectNode) config).put("generatedLocale", MessageContext.locale());
    layout.validate(config);
    boolean money = layout.money(config);
    checkWarehouse(s.warehouseId(), money);
    var original = json.valueToTree(outbounds.detail(f.documentId()));
    var raw = json.convertValue(original, PrintViews.Outbound.class);
    // 仅在渲染器边界转换已类型化字段；精确保留旧签名使用的键、值和规范化排序。
    var header = json.convertValue(raw, new TypeReference<Map<String, Object>>() {});
    var d = new LinkedHashMap<String, Object>();
    for (String key : DocumentPrintLayout.HEADERS.keySet()) d.put(key, text(header.get(key)));
    d.put(
        "confirmedAt",
        text(original.has("confirmedAt") ? raw.confirmedAt() : raw.createdAt())
            .replaceFirst("T", " ")
            .replaceFirst("\\.\\d+$", ""));
    d.put("code", raw.code());
    d.put("status", raw.status());
    d.put("companyName", text(s.companyName()));
    d.put("rowVersion", original.has("rowVersion") ? raw.rowVersion() : Integer.valueOf(0));
    var lines = new ArrayList<Map<String, String>>();
    if (!text(raw.status()).equals("草稿")) {
      var allocations = repo.allocations(auth.tenantId(), f.documentId());
      require(allocations.size() <= 1000, MessageContext.text("server.printing.message0022"));
      for (var a : allocations) {
        var values = json.convertValue(a, new TypeReference<Map<String, Object>>() {});
        var row = new LinkedHashMap<String, String>();
        for (String key : DocumentPrintLayout.COLUMNS.keySet())
          if (!DocumentPrintLayout.MONEY.contains(key) || money)
            row.put(key, text(values.get(key)));
        row.put("sequence", String.valueOf(lines.size() + 1));
        row.put("quantity", decimal(a.quantity()));
        if (money) {
          row.put("unitCost", decimal(a.unitCost()));
          row.put("amount", decimal(a.amount()));
        }
        lines.add(row);
      }
    } else {
      require(!money, MessageContext.text("server.printing.message0023"));
      for (var a : raw.lines()) {
        var row = new LinkedHashMap<String, String>();
        row.put("sequence", String.valueOf(lines.size() + 1));
        row.put("code", text(a.materialCode()));
        row.put("name", text(a.materialName()));
        row.put("model", text(a.model()));
        row.put("unit", text(a.unit()));
        row.put("quantity", decimal(a.quantity()));
        row.put("recipient", text(a.recipientName()));
        row.put("department", text(a.departmentName()));
        row.put("task", text(a.taskCode()));
        row.put("requisition", text(a.requisitionCode()));
        lines.add(row);
      }
    }
    require(!lines.isEmpty(), MessageContext.text("server.printing.message0024"));
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
                    "document", d, "lines", lines, "template", config, "version", t.rowVersion())));
    return new Prepared(s, t, config, d, lines, fingerprint);
  }

  private String decimal(BigDecimal n) {
    return n == null ? "" : n.setScale(2, RoundingMode.HALF_UP).toPlainString();
  }

  private String sign(String value) {
    try {
      var mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(previewKey, "HmacSHA256"));
      return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  private String hash(String value) {
    return hashBytes(value.getBytes(StandardCharsets.UTF_8));
  }

  private String hashBytes(byte[] value) {
    try {
      return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  @Transactional(readOnly = true)
  public PrintViews.Preview preview(PrintForms.Output f) throws IOException {
    var p = prepare(f);
    byte[] pdf = layout.render(p.config, p.document, p.lines);
    return new PrintViews.Preview(
        Base64.getEncoder().encodeToString(pdf), p.fingerprint, text(p.document.get("code")));
  }

  /** 预览指纹绑定当前来源和模板；幂等键冲突只取原记录，保持请求摘要核对及原事务/锁顺序。 */
  @Transactional
  public PrintViews.Job generate(PrintForms.Output f) throws IOException {
    require(
        f.requestKey() != null && f.requestKey().matches("[A-Za-z0-9_-]{16,64}"),
        MessageContext.text("server.printing.message0025"));
    String requestHash =
        hash(encode(List.of(text(f.documentId()), text(f.templateId()), text(f.fingerprint()))));
    var retry = jobs.retry(auth.tenantId(), auth.userId(), f.requestKey(), false);
    if (retry != null) {
      require(
          requestHash.equals(retry.getRequestHash()),
          MessageContext.text("server.printing.message0026"));
      return job(retry.getId());
    }
    var p = prepare(f);
    require(
        p.fingerprint.equals(f.fingerprint()), MessageContext.text("server.printing.message0027"));
    byte[] pdf = layout.render(p.config, p.document, p.lines);
    int pages;
    try (var doc = Loader.loadPDF(pdf)) {
      pages = doc.getNumberOfPages();
    }
    require(pages <= 100, MessageContext.text("server.printing.message0028"));
    long key = IdWorker.getId();
    var row = new PrintJob();
    row.setId(key);
    row.setTenantId(auth.tenantId());
    row.setCompanyId(p.source.companyId());
    row.setWarehouseId(p.source.warehouseId());
    row.setDocumentId(f.documentId());
    row.setDocumentCode(text(p.document.get("code")));
    row.setDocumentStatus(text(p.document.get("status")));
    row.setTemplateId(p.template.id().equals("system") ? null : Long.valueOf(p.template.id()));
    row.setTemplateName(p.template.name());
    row.setTemplateVersion(p.template.rowVersion());
    row.setTemplateSnapshot(encode(p.config));
    row.setDocumentSnapshot(encode(Map.of("header", p.document, "lines", p.lines)));
    row.setContainsMoney(layout.money(p.config) ? 1 : 0);
    row.setActorId(auth.userId());
    row.setActorName(auth.user().getDisplayName());
    row.setRequestKey(f.requestKey());
    row.setRequestHash(requestHash);
    row.setPdfSha256(hashBytes(pdf));
    row.setPageCount(pages);
    row.setPdfContent(pdf);
    jobs.insertOnce(row);
    var stored = jobs.retry(auth.tenantId(), auth.userId(), f.requestKey(), true);
    require(
        requestHash.equals(stored.getRequestHash()),
        MessageContext.text("server.printing.message0026"));
    var result = jobs.locked(auth.tenantId(), stored.getId());
    checkJob(result);
    return jobView(result);
  }

  private void checkJob(PrintJob j) {
    checkWarehouse(j.getWarehouseId(), j.getContainsMoney() == 1);
  }

  private PrintViews.Job jobView(PrintJob j) {
    return new PrintViews.Job(
        j.getId().toString(),
        j.getDocumentId(),
        j.getDocumentCode(),
        j.getDocumentStatus(),
        j.getTemplateId() == null ? null : j.getTemplateId().toString(),
        j.getTemplateName(),
        j.getTemplateVersion().toString(),
        j.getActorName(),
        j.getCreatedAt(),
        j.getPageCount().toString(),
        j.getPdfSha256());
  }

  public PrintViews.Job job(long key) {
    var j = jobs.find(auth.tenantId(), key, false);
    checkJob(j);
    return jobView(j);
  }

  public List<PrintViews.Job> jobs(String document) {
    source(document);
    return jobs.list(auth.tenantId(), document).stream()
        .filter(
            j ->
                j.getContainsMoney() == 0
                    || permissions.inventoryWarehouseAllowed(OUTBOUND, j.getWarehouseId(), "查看金额"))
        .map(this::jobView)
        .toList();
  }

  public byte[] file(long key) {
    var j = jobs.find(auth.tenantId(), key, true);
    checkJob(j);
    return j.getPdfContent();
  }
}
