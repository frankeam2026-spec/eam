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

/** One issue header for direct, single-request and consolidated issues. Preview is read-only. */
@Service
public class OutboundService {
  static final String MENU = "inventoryOutbound";
  private final JdbcTemplate db;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InventoryService masters;
  private final InventoryMutex mutex;
  private final NumberCounterMapper counter;
  private final ObjectMapper json;
  private final RequisitionService requests;
  private final byte[] previewKey = new byte[32];

  {
    new java.security.SecureRandom().nextBytes(previewKey);
  }

  public OutboundService(
      JdbcTemplate db,
      AuthContext auth,
      PermissionService permissions,
      InventoryService masters,
      InventoryMutex mutex,
      NumberCounterMapper counter,
      ObjectMapper json,
      RequisitionService requests) {
    this.db = db;
    this.auth = auth;
    this.permissions = permissions;
    this.masters = masters;
    this.mutex = mutex;
    this.counter = counter;
    this.json = json;
    this.requests = requests;
  }

  @org.springframework.beans.factory.annotation.Value("${eam.inventory.preview-secret:}")
  void configurePreviewSecret(String secret) {
    if (secret.isBlank()) return;
    require(secret.length() >= 32, "库存预览密钥至少32个字符");
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
      throw new IllegalArgumentException("单据内容无效", e);
    }
  }

  private Map<String, Object> payload(Map<String, Object> raw) {
    try {
      return raw.get("payload") == null
          ? new LinkedHashMap<>()
          : json.readValue(
              raw.get("payload").toString(), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("单据内容无法读取", e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> inputLines(Map<String, Object> body) {
    require(body.get("lines") instanceof List<?>, "请添加出库物料");
    var list = (List<?>) body.get("lines");
    require(!list.isEmpty() && list.size() <= 500, "请选择1至500行物料");
    var result = new ArrayList<Map<String, Object>>();
    for (Object value : list) {
      require(value instanceof Map<?, ?>, "出库明细无效");
      result.add(new LinkedHashMap<>((Map<String, Object>) value));
    }
    return result;
  }

  private Map<String, Object> row(long id, boolean lock, String action) {
    var rows =
        db.queryForList(
            "SELECT * FROM inv_issue WHERE tenant_id=? AND id=? AND deleted=0"
                + (lock ? " FOR UPDATE" : ""),
            auth.tenantId(),
            id);
    require(rows.size() == 1, "出库单不存在");
    var r = rows.getFirst();
    boolean ownRead =
        action.equals("查看")
            && text(r, "source_kind").equals("直接出库")
            && !text(r, "status").equals("草稿")
            && db.queryForObject(
                    "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
                        + " recipient_id<>?",
                    Integer.class,
                    auth.tenantId(),
                    id,
                    auth.userId())
                == 0
            && db.queryForObject(
                    "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
                        + " recipient_id=?",
                    Integer.class,
                    auth.tenantId(),
                    id,
                    auth.userId())
                > 0;
    if (!ownRead) permissions.checkInventoryWarehouse(MENU, id(r.get("warehouse_id")), action);
    return r;
  }

  private List<Map<String, Object>> lines(long id) {
    return db.queryForList(
        "SELECT l.*,m.code material_code,m.name material_name,m.unit,rl.quantity"
            + " requested_quantity,rl.issued_quantity,(rl.quantity-rl.issued_quantity)"
            + " remaining_quantity,COALESCE((SELECT SUM(remaining_quantity) FROM inv_reservation h"
            + " WHERE h.tenant_id=l.tenant_id AND h.requisition_line_id=l.requisition_line_id),0)"
            + " reserved_quantity,r.code requisition_code,d.name department_name FROM"
            + " inv_outbound_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
            + " m.id=l.material_id LEFT JOIN inv_requisition_line rl ON rl.tenant_id=l.tenant_id"
            + " AND rl.id=l.requisition_line_id LEFT JOIN inv_requisition r ON"
            + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id LEFT JOIN eam_org_unit d ON"
            + " d.tenant_id=l.tenant_id AND d.id=l.department_id WHERE l.tenant_id=? AND"
            + " l.issue_id=? ORDER BY l.line_no",
        auth.tenantId(),
        id);
  }

  private void event(long id, String action) {
    db.update(
        "INSERT INTO"
            + " inv_document_event(id,tenant_id,document_kind,document_id,action,actor_id,actor_name)"
            + " VALUES(?,?,'出库',?,?,?,?)",
        IdWorker.getId(),
        auth.tenantId(),
        id,
        action,
        auth.userId(),
        auth.user().getDisplayName());
  }

  private String number() {
    String month =
        LocalDate.now(ZoneId.of("Asia/Shanghai")).format(DateTimeFormatter.ofPattern("yyyyMM"));
    String code;
    do {
      counter.nextMonthlyNumber("inv-CKD-" + auth.tenantId(), month);
      code = "CK-" + month + "-" + String.format("%04d", counter.currentValue());
    } while (db.queryForObject(
            "SELECT (SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND code=?)+(SELECT COUNT(*)"
                + " FROM inv_issue_group WHERE tenant_id=? AND code=?)",
            Integer.class,
            auth.tenantId(),
            code,
            auth.tenantId(),
            code)
        > 0);
    return code;
  }

  public Object materials(String q, int page, int size, long warehouseId) {
    permissions.require(MENU, "查看");
    require(q.length() <= 200 && page >= 1 && size >= 1 && size <= 100, "查询参数无效");
    permissions.checkInventoryWarehouse(MENU, warehouseId, "查看");
    String match = "%" + q.trim() + "%";
    String where = " WHERE tenant_id=? AND enabled=1 AND (code LIKE ? OR name LIKE ? OR model LIKE ? OR category LIKE ?)";
    Long total = db.queryForObject("SELECT COUNT(*) FROM inv_material" + where, Long.class,
        auth.tenantId(), match, match, match, match);
    var data = db.queryForList(
        "SELECT id,code,name,model,unit,category,enabled,batch_enabled,expiry_enabled FROM inv_material"
            + where + " ORDER BY code,id LIMIT ? OFFSET ?", auth.tenantId(), match, match, match,
        match, size, ((long) page - 1) * size).stream().map(masters::view).toList();
    if (!data.isEmpty()) {
      var args = new ArrayList<Object>();
      args.add(LocalDate.now(ZoneId.of("Asia/Shanghai")));
      args.add(auth.tenantId()); args.add(warehouseId);
      data.forEach(m -> args.add(id(m.get("id"))));
      var stock = db.queryForList(
          "SELECT s.material_id,COALESCE(SUM(s.quantity),0) stock_quantity,"
              + " COALESCE(SUM(s.reserved_quantity),0) reserved_quantity,"
              + " COALESCE(SUM(CASE WHEN s.stock_status='可用' AND b.enabled=1"
              + " AND (m.batch_enabled=0 OR s.batch_id<>0)"
              + " AND (m.expiry_enabled=0 OR pc.expiry_date IS NOT NULL)"
              + " AND (pc.expiry_date IS NULL OR pc.expiry_date>=?)"
              + " THEN GREATEST(s.quantity-s.reserved_quantity,0) ELSE 0 END),0) available_quantity"
              + " FROM inv_balance s JOIN inv_material m ON m.tenant_id=s.tenant_id AND m.id=s.material_id"
              + " JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id"
              + " LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id"
              + " WHERE s.tenant_id=? AND s.warehouse_id=? AND s.material_id IN ("
              + String.join(",", Collections.nCopies(data.size(), "?")) + ") GROUP BY s.material_id", args.toArray());
      var quantities = new HashMap<Long, Map<String, Object>>();
      stock.forEach(row -> quantities.put(id(row.get("material_id")), row));
      data.forEach(material -> {
        var row = quantities.get(id(material.get("id")));
        material.put("stockQuantity", row == null ? "0" : row.get("stock_quantity").toString());
        material.put("reservedQuantity", row == null ? "0" : row.get("reserved_quantity").toString());
        material.put("availableQuantity", row == null ? "0" : row.get("available_quantity").toString());
      });
    }
    return Map.of("data", data, "page", Map.of("total", total));
  }

  public Object choices() {
    permissions.require(MENU, "查看");
    var warehouses =
        db
            .queryForList(
                "SELECT w.*,o.name company_name FROM inv_warehouse w JOIN eam_org_unit o ON"
                    + " o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE w.tenant_id=? AND"
                    + " w.enabled=1 ORDER BY o.name,w.name",
                auth.tenantId())
            .stream()
            .filter(w -> permissions.inventoryWarehouseAllowed(MENU, id(w.get("id")), "查看"))
            .map(masters::view)
            .toList();
    var companies = new HashSet<Long>();
    warehouses.forEach(w -> companies.add(id(w.get("companyId"))));
    var departments =
        db
            .queryForList(
                "SELECT id,name,parent_id,org_type FROM eam_org_unit WHERE tenant_id=? AND"
                    + " deleted=0 AND status=1 ORDER BY id",
                auth.tenantId())
            .stream()
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
        db
            .queryForList(
                "SELECT id,display_name name,org_id FROM eam_sys_user WHERE tenant_id=? AND"
                    + " deleted=0 AND status=1 ORDER BY display_name",
                auth.tenantId())
            .stream()
            .filter(u -> companies.contains(permissions.companyOfOrganization(id(u.get("org_id")))))
            .map(
                u -> {
                  var v = masters.view(u);
                  v.put(
                      "companyId",
                      Objects.toString(permissions.companyOfOrganization(id(u.get("org_id"))), ""));
                  return v;
                })
            .toList();
    var materials = List.of();
    var taskChoices = requests.outboundTaskChoices();
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
    require(Set.of("直接出库", "领料出库").contains(text(body, "sourceKind")), "出库来源无效");
    require(text(body, "remark").length() <= 1000, "备注最多1000字");
    boolean direct = !"领料出库".equals(text(body, "sourceKind"));
    long warehouse = id(body.get("warehouseId"));
    var w = masters.row("warehouses", warehouse, true);
    permissions.checkInventoryWarehouse(
        MENU, warehouse, direct ? (issueId == null ? "新增直接出库" : "编辑草稿") : "确认出库");
    require(flag(w.get("enabled")), "仓库已停用");
    Map<String, Object> previous =
        issueId == null ? null : row(issueId, true, direct ? "编辑草稿" : "确认出库");
    if (previous != null) {
      require(text(previous, "status").equals("草稿"), "已出库单不能修改");
      VersionGuard.checkPayload((Number) previous.get("row_version"), body);
      require(text(previous, "source_kind").equals(direct ? "直接出库" : "领料出库"), "不能切换出库来源");
    }
    long company = id(w.get("company_id"));
    long department = id(body.get("departmentId")), recipient = id(body.get("recipientId"));
    String recipientName = "";
    if (direct) {
      var departments =
          db.queryForList(
              "SELECT id,org_type FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " status=1",
              auth.tenantId(),
              department);
      require(
          departments.size() == 1
              && Set.of("部门", "department").contains(text(departments.getFirst(), "org_type"))
              && Objects.equals(permissions.companyOfOrganization(department), company),
          "请选择本公司领用部门");
      var recipients =
          db.queryForList(
              "SELECT display_name,org_id FROM eam_sys_user WHERE tenant_id=? AND id=? AND"
                  + " deleted=0 AND status=1",
              auth.tenantId(),
              recipient);
      require(
          recipients.size() == 1
              && Objects.equals(
                  permissions.companyOfOrganization(id(recipients.getFirst().get("org_id"))),
                  company),
          "请选择本公司领用人");
      recipientName = text(recipients.getFirst(), "display_name");
      require(
          !text(body, "purpose").isBlank() && text(body, "purpose").length() <= 1000,
          "请填写用途（最多1000字）");
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
        require(seen.add(reqLine), "申请明细不能重复");
        var source =
            db.queryForList(
                "SELECT"
                    + " l.*,r.company_id,r.warehouse_id,r.task_kind,r.task_id,r.task_code,r.applicant_id,r.applicant_name,r.id"
                    + " request_id FROM inv_requisition_line l JOIN inv_requisition r ON"
                    + " r.tenant_id=l.tenant_id AND r.id=l.requisition_id WHERE l.tenant_id=? AND"
                    + " l.id=? AND r.deleted=0 FOR UPDATE",
                auth.tenantId(),
                reqLine);
        require(source.size() == 1, "申请明细不存在");
        var r = source.getFirst();
        requests.outboundRequest(id(r.get("request_id")), true, "确认出库");
        require(
            id(r.get("company_id")) == company && id(r.get("warehouse_id")) == warehouse,
            "只能合并同公司、同仓库申请");
        require(id(r.get("material_id")) == material, "申请物料不可修改");
        require(
            qty.compareTo(
                    ((BigDecimal) r.get("quantity"))
                        .subtract((BigDecimal) r.get("issued_quantity")))
                <= 0,
            "本次出库数量超过剩余待发数量");
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
      require(rawSelections == null || rawSelections instanceof List<?>, "批次分配格式无效");
      if (rawSelections instanceof List<?> chosen) {
        require(chosen.size() <= 500, "批次分配数量过多");
        for (Object value : chosen) {
          require(value instanceof Map<?, ?>, "批次分配明细无效");
          var selection = (Map<?, ?>) value;
          require(id(selection.get("balanceId")) > 0, "请选择有效库位批次");
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
    require(!normalized.isEmpty(), "本次出库数量必须大于零");
    var stored = new LinkedHashMap<String, Object>();
    stored.put("purpose", text(body, "purpose"));
    stored.put("remark", text(body, "remark"));
    stored.put("departmentId", Long.toString(department));
    stored.put("recipientId", Long.toString(recipient));
    stored.put("taskKind", text(body, "taskKind"));
    stored.put("taskId", text(body, "taskId"));
    stored.put("lineSelections", normalized.stream().map(l -> l.get("selections")).toList());
    long target = issueId == null ? IdWorker.getId() : issueId;
    if (previous == null)
      db.update(
          "INSERT INTO"
              + " inv_issue(id,tenant_id,code,requisition_id,company_id,warehouse_id,confirmation_key,preview_fingerprint,total_amount,actor_id,actor_name,status,source_kind,payload)"
              + " VALUES(?,?,?,NULL,?,?,?,'',0,?,?,'草稿',?,CAST(? AS JSON))",
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
      db.update(
          "UPDATE inv_issue SET company_id=?,warehouse_id=?,payload=CAST(? AS"
              + " JSON),row_version=row_version+1 WHERE tenant_id=? AND id=?",
          company,
          warehouse,
          encode(stored),
          auth.tenantId(),
          target);
      db.update(
          "DELETE FROM inv_outbound_line WHERE tenant_id=? AND issue_id=?",
          auth.tenantId(),
          target);
    }
    int n = 0;
    for (var l : normalized)
      db.update(
          "INSERT INTO"
              + " inv_outbound_line(id,tenant_id,issue_id,line_no,material_id,quantity,requisition_id,requisition_line_id,task_kind,task_id,task_code,department_id,recipient_id,recipient_name)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
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
    event(target, previous == null ? "创建出库草稿" : "修改出库草稿");
    return detail(Long.toString(target));
  }

  public Object fromRequests(Map<String, Object> body) {
    require(body.get("ids") instanceof List<?>, "请选择待出库申请");
    var ids = (List<?>) body.get("ids");
    require(!ids.isEmpty() && ids.size() <= 50, "请选择1至50张申请");
    var result = new LinkedHashMap<String, Object>();
    var output = new ArrayList<Map<String, Object>>();
    long warehouse = 0;
    var seen = new HashSet<Long>();
    for (Object value : ids) {
      long reqId = id(value);
      require(seen.add(reqId), "申请不能重复");
      var r = requests.outboundRequest(reqId, false, "确认出库");
      if (warehouse == 0) warehouse = id(r.get("warehouse_id"));
      require(warehouse == id(r.get("warehouse_id")), "只能合并同一仓库申请");
      for (var l :
          db.queryForList(
              "SELECT l.*,m.code material_code,m.name material_name,m.unit,COALESCE((SELECT"
                  + " SUM(remaining_quantity) FROM inv_reservation s WHERE s.tenant_id=l.tenant_id"
                  + " AND s.requisition_line_id=l.id),0) reserved_quantity FROM"
                  + " inv_requisition_line l JOIN inv_material m ON m.tenant_id=l.tenant_id AND"
                  + " m.id=l.material_id WHERE l.tenant_id=? AND l.requisition_id=? AND"
                  + " l.quantity>l.issued_quantity ORDER BY l.line_no",
              auth.tenantId(),
              reqId)) {
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
      var owner =
          db.queryForList(
              "SELECT requisition_id FROM inv_requisition_line WHERE tenant_id=? AND id=?",
              auth.tenantId(),
              reqLine);
      require(owner.size() == 1, "申请明细不存在");
      var req = requests.outboundRequest(id(owner.getFirst().get("requisition_id")), false, "确认出库");
      require(id(req.get("warehouse_id")) == warehouse, "申请不属于该仓库");
    }
    return db
        .queryForList(
            "SELECT s.id,s.material_id,s.bin_id,b.name bin_name,pc.code"
                + " batch_code,pc.expiry_date,s.stock_status,b.enabled"
                + " bin_enabled,s.quantity,s.reserved_quantity,(SELECT"
                + " COALESCE(SUM(remaining_quantity),0) FROM inv_reservation h WHERE"
                + " h.tenant_id=s.tenant_id AND h.balance_id=s.id AND h.requisition_line_id="
                + reqLine
                + ") own_reserved_quantity,s.quantity-s.reserved_quantity available_quantity FROM"
                + " inv_balance s JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT"
                + " JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
                + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? AND s.stock_status='可用'"
                + " AND b.enabled=1 AND (pc.expiry_date IS NULL OR pc.expiry_date>=CURRENT_DATE())"
                + " ORDER BY b.code,pc.code,s.id",
            auth.tenantId(),
            warehouse,
            material)
        .stream()
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
    require(text(r, "status").equals("草稿"), "出库单已经生效");
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    long warehouse = id(r.get("warehouse_id"));
    var w = masters.row("warehouses", warehouse, lock);
    require(flag(w.get("enabled")), "仓库已停用");
    return planPrepared(r, lines(issueId), lock);
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> planPrepared(
      Map<String, Object> r, List<Map<String, Object>> draftLines, boolean lock) {
    long warehouse = id(r.get("warehouse_id"));
    var w = masters.row("warehouses", warehouse, lock);
    require(flag(w.get("enabled")), "仓库已停用");
    var draft = payload(r);
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
        var original =
            db.queryForMap(
                "SELECT quantity,issued_quantity FROM inv_requisition_line WHERE tenant_id=? AND"
                    + " id=?"
                    + (lock ? " FOR UPDATE" : ""),
                auth.tenantId(),
                reqLine);
        require(
            decimal(l, "quantity")
                    .compareTo(
                        decimal(original, "quantity")
                            .subtract(decimal(original, "issued_quantity")))
                <= 0,
            "申请待发数量已变化");
      } else if (id(l.get("task_id")) != 0)
        requests.validateOutboundTask(
            text(l, "task_kind"), id(l.get("task_id")), id(r.get("company_id")), true);
      var m = masters.row("materials", material, lock);
      require(flag(m.get("enabled")), "物料已停用");
      var candidates =
          db.queryForList(
              "SELECT s.*,b.name bin_name,b.enabled bin_enabled,pc.code"
                  + " batch_code,pc.expiry_date,COALESCE((SELECT MIN(c.origin_received_at) FROM"
                  + " inv_cost_layer c WHERE c.tenant_id=s.tenant_id AND c.balance_id=s.id AND"
                  + " c.remaining_quantity>0),'9999-12-31') first_received_at FROM inv_balance s"
                  + " JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id LEFT JOIN"
                  + " inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id WHERE"
                  + " s.tenant_id=? AND s.warehouse_id=? AND s.material_id=? ORDER BY s.id"
                  + (lock ? " FOR UPDATE" : ""),
              auth.tenantId(),
              warehouse,
              material);
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
                  reqLine,
                  key ->
                      db.queryForList(
                          "SELECT * FROM inv_reservation WHERE tenant_id=? AND"
                              + " requisition_line_id=? AND remaining_quantity>0 ORDER BY id"
                              + (lock ? " FOR UPDATE" : ""),
                          auth.tenantId(),
                          key));
      List<Map<String, Object>> manual =
          (int) id(l.get("line_no")) <= selections.size()
              ? (List<Map<String, Object>>) selections.get((int) id(l.get("line_no")) - 1)
              : List.of();
      var chosen = new LinkedHashMap<Long, BigDecimal>();
      for (var selection : manual) {
        long bid = id(selection.get("balanceId"));
        require(!chosen.containsKey(bid), "批次库位不能重复");
        chosen.put(bid, InventoryFlowService.decimal(selection.get("quantity"), false));
      }
      require(
          chosen.isEmpty()
              || chosen.values().stream()
                      .reduce(BigDecimal.ZERO, BigDecimal::add)
                      .compareTo(decimal(l, "quantity"))
                  == 0,
          "手动分配总数量必须等于本次出库数量");
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
        require(take.compareTo(available) <= 0, "所选批次可用库存不足：" + text(m, "name"));
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
                bid,
                key ->
                    db.queryForList(
                        "SELECT * FROM inv_cost_layer WHERE tenant_id=? AND balance_id=? AND"
                            + " remaining_quantity>0 ORDER BY origin_received_at,id"
                            + (lock ? " FOR UPDATE" : ""),
                        auth.tenantId(),
                        key));
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
                "库存成本未确定，不能出库");
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
          require(part.signum() == 0, "成本层数量不足，请核对库存");
        }
        balance.put("quantity", decimal(balance, "quantity").subtract(take));
        balance.put(
            "reserved_quantity", decimal(balance, "reserved_quantity").subtract(take.min(own)));
        need = need.subtract(take);
        if (need.signum() == 0) break;
      }
      require(valid.containsAll(chosen.keySet()), "所选批次或库位不可用");
      require(
          need.signum() == 0,
          "可用库存不足："
              + text(m, "name")
              + "，缺少 "
              + need.stripTrailingZeros().toPlainString()
              + " "
              + text(m, "unit"));
    }
    require(!allocations.isEmpty(), "没有可出库明细");
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
    require(key.matches("[a-zA-Z0-9-]{16,64}"), "确认标识无效");
    if (!text(r, "status").equals("草稿")) {
      require(
          key.equals(text(r, "confirmation_key"))
              && text(body, "fingerprint").equals(text(r, "preview_fingerprint")),
          "单据已经出库，请刷新");
      return detail(Long.toString(issueId));
    }
    require(
        db.queryForObject(
                "SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND id<>?",
                Integer.class,
                auth.tenantId(),
                key,
                issueId)
            == 0,
        "确认标识已经用于其他单据");
    var p = plan(issueId, body, true);
    require(text(p, "fingerprint").equals(text(body, "fingerprint")), "库存、成本或申请已变化，请重新预览");
    var reqs = new TreeSet<Long>();
    for (var a : (List<Map<String, Object>>) p.get("allocations")) {
      var qty = (BigDecimal) a.get("quantity");
      var amount = (BigDecimal) a.get("amount");
      long allocation = IdWorker.getId();
      boolean held = id(a.get("reservationId")) != 0;
      int changed =
          db.update(
              "UPDATE inv_balance SET"
                  + " quantity=quantity-?,reserved_quantity=reserved_quantity-?,row_version=row_version+1"
                  + " WHERE tenant_id=? AND id=? AND quantity>=? AND reserved_quantity>=?",
              qty,
              held ? qty : BigDecimal.ZERO,
              auth.tenantId(),
              id(a.get("balanceId")),
              qty,
              held ? qty : BigDecimal.ZERO);
      require(changed == 1, "库存已变化");
      if (held)
        require(
            db.update(
                    "UPDATE inv_reservation SET remaining_quantity=remaining_quantity-? WHERE"
                        + " tenant_id=? AND id=? AND remaining_quantity>=?",
                    qty,
                    auth.tenantId(),
                    id(a.get("reservationId")),
                    qty)
                == 1,
            "预留已变化");
      require(
          db.update(
                  "UPDATE inv_cost_layer SET"
                      + " remaining_quantity=remaining_quantity-?,remaining_cost=remaining_cost-?"
                      + " WHERE tenant_id=? AND id=? AND remaining_quantity>=? AND"
                      + " remaining_cost>=?",
                  qty,
                  amount,
                  auth.tenantId(),
                  id(a.get("costLayerId")),
                  qty,
                  amount)
              == 1,
          "成本层已变化");
      if (id(a.get("requisitionId")) != 0) {
        reqs.add(id(a.get("requisitionId")));
        require(
            db.update(
                    "UPDATE inv_requisition_line SET issued_quantity=issued_quantity+? WHERE"
                        + " tenant_id=? AND id=? AND quantity-issued_quantity>=?",
                    qty,
                    auth.tenantId(),
                    id(a.get("requisitionLineId")),
                    qty)
                == 1,
            "待发数量已变化");
      }
      db.update(
          "INSERT INTO"
              + " inv_issue_allocation(id,tenant_id,issue_id,outbound_line_id,requisition_line_id,reservation_id,balance_id,cost_layer_id,quantity,amount,unit_cost)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
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
      db.update(
          "INSERT INTO"
              + " inv_transaction(id,tenant_id,company_id,warehouse_id,balance_id,document_id,source_line_id,kind,quantity,amount,actor_id)"
              + " VALUES(?,?,?,?,?,?,?,'出库',?,?,?)",
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
    db.update(
        "UPDATE inv_issue SET"
            + " status='已出库',confirmation_key=?,preview_fingerprint=?,total_amount=?,actor_id=?,actor_name=?,confirmed_at=NOW(3),row_version=row_version+1"
            + " WHERE tenant_id=? AND id=?",
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
          db.update(
              "INSERT INTO"
                  + " inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
                  + " VALUES(?,?,?,?,'outbounds','出库完成',?,0)",
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
    require(text(r, "status").equals("草稿"), "已出库单不能删除");
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    db.update(
        "UPDATE inv_issue SET deleted=1,row_version=row_version+1 WHERE tenant_id=? AND id=?",
        auth.tenantId(),
        id);
    event(id, "删除草稿");
  }

  private List<Map<String, Object>> allocations(long issueId) {
    return db
        .queryForList(
            "SELECT a.*,m.code material_code,m.name material_name,b.name bin_name,pc.code"
                + " batch_code,pc.expiry_date,(SELECT receipt_id FROM inv_receipt_line rl WHERE"
                + " rl.tenant_id=c.tenant_id AND rl.id=c.source_line_id)"
                + " receipt_id,c.origin_received_at,r.code"
                + " requisition_code,COALESCE(l.task_kind,r.task_kind)"
                + " task_kind,COALESCE(l.task_id,r.task_id)"
                + " task_id,COALESCE(l.task_code,r.task_code)"
                + " task_code,COALESCE(l.recipient_name,r.applicant_name) recipient_name FROM"
                + " inv_issue_allocation a JOIN inv_balance s ON s.tenant_id=a.tenant_id AND"
                + " s.id=a.balance_id JOIN inv_material m ON m.tenant_id=s.tenant_id AND"
                + " m.id=s.material_id JOIN inv_bin b ON b.tenant_id=s.tenant_id AND b.id=s.bin_id"
                + " LEFT JOIN inv_batch pc ON pc.tenant_id=s.tenant_id AND pc.id=s.batch_id JOIN"
                + " inv_cost_layer c ON c.tenant_id=a.tenant_id AND c.id=a.cost_layer_id LEFT JOIN"
                + " inv_outbound_line l ON l.tenant_id=a.tenant_id AND l.id=a.outbound_line_id LEFT"
                + " JOIN inv_requisition_line rl ON rl.tenant_id=a.tenant_id AND"
                + " rl.id=a.requisition_line_id LEFT JOIN inv_requisition r ON"
                + " r.tenant_id=rl.tenant_id AND r.id=rl.requisition_id WHERE a.tenant_id=? AND"
                + " a.issue_id=? ORDER BY a.id",
            auth.tenantId(),
            issueId)
        .stream()
        .map(masters::view)
        .toList();
  }

  public Object detail(String reference) {
    if (reference.startsWith("g")) {
      var groups =
          db.queryForList(
              "SELECT * FROM inv_issue_group WHERE tenant_id=? AND id=?",
              auth.tenantId(),
              id(reference.substring(1)));
      require(groups.size() == 1, "历史合并单不存在");
      var g = groups.getFirst();
      permissions.checkInventoryWarehouse(MENU, id(g.get("warehouse_id")), "查看");
      var result = masters.view(g);
      result.put("id", reference);
      result.put("sourceKind", "历史合并");
      var groupWarehouse = masters.row("warehouses", id(g.get("warehouse_id")), false);
      result.put("warehouseName", groupWarehouse.get("name"));
      result.put(
          "companyName",
          db.queryForObject(
              "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?",
              String.class,
              auth.tenantId(),
              groupWarehouse.get("company_id")));

      var children =
          db.queryForList(
              "SELECT id,reversed FROM inv_issue WHERE tenant_id=? AND group_id=? ORDER BY id",
              auth.tenantId(),
              g.get("id"));
      var merged = new ArrayList<Map<String, Object>>();
      BigDecimal sum = BigDecimal.ZERO;
      for (var child : children) {
        merged.addAll(allocations(id(child.get("id"))));
        sum =
            sum.add(
                db.queryForObject(
                    "SELECT total_amount FROM inv_issue WHERE tenant_id=? AND id=?",
                    BigDecimal.class,
                    auth.tenantId(),
                    child.get("id")));
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
    v.putAll(payload(r));
    v.put("warehouseName", masters.row("warehouses", id(r.get("warehouse_id")), false).get("name"));
    v.put(
        "companyName",
        db.queryForObject(
            "SELECT name FROM eam_org_unit WHERE tenant_id=? AND id=?",
            String.class,
            auth.tenantId(),
            r.get("company_id")));
    var ls = lines(issueId).stream().map(masters::view).toList();
    var selections = (List<?>) payload(r).getOrDefault("lineSelections", List.of());
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
        db
            .queryForList(
                "SELECT action,actor_name,created_at FROM inv_document_event WHERE tenant_id=? AND"
                    + " document_kind IN ('出库','issue') AND document_id=? ORDER BY created_at,id",
                auth.tenantId(),
                issueId)
            .stream()
            .map(masters::view)
            .toList());
    if (!permissions.inventoryWarehouseAllowed(MENU, id(r.get("warehouse_id")), "查看金额"))
      InventoryMoney.hide(v);
    return v;
  }

  private List<Map<String, Object>> matching(
      String q, String filters, String conditions, String action) {
    require(
        q.length() <= 200 && filters.length() <= 8000 && conditions.length() <= 16000, "查询条件过长");
    Map<String, String> f;
    List<Map<String, Object>> cs;
    try {
      f = json.readValue(filters, new TypeReference<Map<String, String>>() {});
      cs = json.readValue(conditions, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    var allowed =
        Set.of(
            "code",
            "sourceKind",
            "warehouseName",
            "status",
            "actorName",
            "createdAt",
            "confirmedAt",
            "recipientName",
            "taskCode",
            "totalAmount",
            "totalQuantity");
    require(allowed.containsAll(f.keySet()), "筛选字段无效");
    var rows = new ArrayList<Map<String, Object>>();
    for (var r :
        db.queryForList(
            "SELECT i.*,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_created_at,DATE_FORMAT(i.confirmed_at,'%Y-%m-%d %H:%i:%s')"
                + " fmt_confirmed_at,w.name warehouse_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT"
                + " recipient_name SEPARATOR '、') FROM inv_outbound_line l WHERE"
                + " l.tenant_id=i.tenant_id AND l.issue_id=i.id),(SELECT applicant_name FROM"
                + " inv_requisition r WHERE r.tenant_id=i.tenant_id AND r.id=i.requisition_id))"
                + " recipient_name,COALESCE((SELECT GROUP_CONCAT(DISTINCT task_code SEPARATOR '、')"
                + " FROM inv_outbound_line l WHERE l.tenant_id=i.tenant_id AND l.issue_id=i.id),'')"
                + " task_code,COALESCE((SELECT SUM(a.quantity) FROM inv_issue_allocation a WHERE"
                + " a.tenant_id=i.tenant_id AND a.issue_id=i.id),(SELECT SUM(l.quantity) FROM"
                + " inv_outbound_line l WHERE l.tenant_id=i.tenant_id AND l.issue_id=i.id))"
                + " total_quantity FROM inv_issue i JOIN inv_warehouse w ON w.tenant_id=i.tenant_id"
                + " AND w.id=i.warehouse_id WHERE i.tenant_id=? AND i.deleted=0 AND i.group_id IS"
                + " NULL ORDER BY i.created_at DESC,i.id DESC",
            auth.tenantId())) {
      if (!permissions.inventoryWarehouseAllowed(MENU, id(r.get("warehouse_id")), action)) continue;
      var v = masters.view(r);
      v.remove("payload");
      v.remove("confirmationKey");
      v.remove("previewFingerprint");
      if (!permissions.inventoryWarehouseAllowed(MENU, id(r.get("warehouse_id")), "查看金额"))
        InventoryMoney.hide(v);
      rows.add(v);
    }
    for (var g :
        db.queryForList(
            "SELECT g.*,DATE_FORMAT(g.created_at,'%Y-%m-%d %H:%i:%s') fmt_created_at,w.name"
                + " warehouse_name,(SELECT SUM(total_amount) FROM inv_issue i WHERE"
                + " i.tenant_id=g.tenant_id AND i.group_id=g.id) total_amount,(SELECT"
                + " SUM(a.quantity) FROM inv_issue_allocation a JOIN inv_issue i ON"
                + " i.tenant_id=a.tenant_id AND i.id=a.issue_id WHERE i.tenant_id=g.tenant_id AND"
                + " i.group_id=g.id) total_quantity FROM inv_issue_group g JOIN inv_warehouse w ON"
                + " w.tenant_id=g.tenant_id AND w.id=g.warehouse_id WHERE g.tenant_id=? ORDER BY"
                + " g.created_at DESC",
            auth.tenantId())) {
      if (!permissions.inventoryWarehouseAllowed(MENU, id(g.get("warehouse_id")), action)) continue;
      var v = masters.view(g);
      v.put("id", "g" + v.get("id"));
      v.put("sourceKind", "历史合并");
      v.put(
          "status",
          db.queryForObject(
                      "SELECT COUNT(*) FROM inv_issue WHERE tenant_id=? AND group_id=? AND"
                          + " reversed=0",
                      Integer.class,
                      auth.tenantId(),
                      g.get("id"))
                  == 0
              ? "已冲销"
              : "已出库");
      v.remove("confirmationKey");
      v.remove("requestIds");
      if (!permissions.inventoryWarehouseAllowed(MENU, id(g.get("warehouse_id")), "查看金额"))
        InventoryMoney.hide(v);
      rows.add(v);
    }
    rows.removeIf(
        v ->
            !(text(v, "code")
                        + " "
                        + text(v, "recipientName")
                        + " "
                        + text(v, "warehouseName")
                        + " "
                        + text(v, "taskCode"))
                    .toLowerCase(Locale.ROOT)
                    .contains(q.trim().toLowerCase(Locale.ROOT))
                || f.entrySet().stream()
                    .anyMatch(
                        e -> !text(v, e.getKey()).replace('T', ' ').contains(e.getValue().trim()))
                || !matches(cs, v, allowed));
    rows.sort(
        Comparator.<Map<String, Object>, String>comparing(v -> text(v, "createdAt")).reversed());
    return rows;
  }

  private boolean matches(
      List<Map<String, Object>> conditions, Map<String, Object> row, Set<String> allowed) {
    for (var c : conditions) {
      String field = text(c, "field"), op = text(c, "operator");
      require(allowed.contains(field), "筛选字段无效");
      require(!field.equals("totalAmount") || permissions.allowed(MENU, "查看金额"), "无金额筛选权限");
      require(c.get("values") instanceof List<?>, "筛选值无效");
      var values = (List<?>) c.get("values");
      String value = text(row, field).replace('T', ' '),
          first = values.isEmpty() ? "" : Objects.toString(values.getFirst(), "").replace('T', ' ');
      boolean ok =
          switch (op) {
            case "contains" -> value.contains(first);
            case "eq" -> value.equals(first);
            case "in" -> values.contains(value);
            case "empty" -> value.isEmpty();
            case "notEmpty" -> !value.isEmpty();
            case "range" -> {
              String upper =
                  values.size() > 1 ? Objects.toString(values.get(1), "").replace('T', ' ') : "";
              if (value.isEmpty()) yield false;
              if (Set.of("totalAmount", "remainingQuantity", "totalQuantity").contains(field))
                yield (first.isEmpty()
                        || new BigDecimal(value).compareTo(new BigDecimal(first)) >= 0)
                    && (upper.isEmpty()
                        || new BigDecimal(value).compareTo(new BigDecimal(upper)) <= 0);
              yield (first.isEmpty() || value.compareTo(first) >= 0)
                  && (upper.isEmpty() || value.compareTo(upper) <= 0);
            }
            default -> throw new IllegalArgumentException("筛选方式无效");
          };
      if (!ok) return false;
    }
    return true;
  }

  public Object list(String q, String filters, String conditions, int page, int size) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    permissions.require(MENU, "查看");
    var rows = matching(q, filters, conditions, "查看");
    int start = (int) Math.min((long) (page - 1) * size, rows.size());
    return Map.of(
        "data",
        rows.subList(start, Math.min(start + size, rows.size())),
        "page",
        Map.of("total", rows.size()));
  }

  public Object export(String q, String filters, String conditions) {
    permissions.require(MENU, "导出");
    return matching(q, filters, conditions, "导出");
  }

  public Object pending(
      String q, String filters, String conditions, int page, int size, boolean export) {
    require(page >= 1 && size >= 1 && size <= 100, "分页参数无效");
    permissions.require(MENU, export ? "导出" : "查看");
    List<Map<String, Object>> cs;
    try {
      cs = json.readValue(conditions, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件无效");
    }
    var rows =
        requests.pendingRows(q, filters, export ? "导出" : "查看").stream()
            .filter(
                r ->
                    matches(
                        cs,
                        r,
                        Set.of(
                            "code",
                            "taskCode",
                            "taskKind",
                            "applicantName",
                            "warehouseName",
                            "remainingQuantity",
                            "status",
                            "reservationStatus",
                            "createdAt")))
            .toList();
    if (export) return rows;
    int start = (int) Math.min((long) (page - 1) * size, rows.size());
    return Map.of(
        "data",
        rows.subList(start, Math.min(start + size, rows.size())),
        "page",
        Map.of("total", rows.size()));
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> legacyForm(long requestId, Map<String, Object> body) {
    var req = requests.outboundRequest(requestId, false, "确认出库");
    VersionGuard.checkPayload((Number) req.get("row_version"), body);
    var f = (Map<String, Object>) fromRequests(Map.of("ids", List.of(Long.toString(requestId))));
    var sums = new TreeMap<Long, BigDecimal>();
    var bins = new HashMap<Long, List<Map<String, Object>>>();
    require(body.get("selections") instanceof List<?>, "请选择本次出库数量");
    var seen = new HashSet<Long>();
    for (Object input : (List<?>) body.get("selections")) {
      var v = (Map<String, Object>) input;
      long res = id(v.get("reservationId"));
      require(seen.add(res), "预留明细不能重复");
      var found =
          db.queryForList(
              "SELECT * FROM inv_reservation WHERE tenant_id=? AND id=? AND requisition_id=?",
              auth.tenantId(),
              res,
              requestId);
      require(found.size() == 1, "预留明细不存在");
      var held = found.getFirst();
      var qty = InventoryFlowService.decimal(v.get("quantity"), true);
      require(qty.compareTo((BigDecimal) held.get("remaining_quantity")) <= 0, "数量超过预留");
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
    require(!ls.isEmpty(), "本次出库数量必须大于零");
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
    raw.put("payload", encode(Map.of("lineSelections", selections)));
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
    var found =
        db.queryForList(
            "SELECT * FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND deleted=0",
            auth.tenantId(),
            key);
    if (!found.isEmpty()) {
      row(id(found.getFirst().get("id")), false, "确认出库");
      require(
          id(found.getFirst().get("requisition_id")) == requestId
              || db.queryForObject(
                      "SELECT COUNT(*) FROM inv_outbound_line WHERE tenant_id=? AND issue_id=? AND"
                          + " requisition_id=?",
                      Integer.class,
                      auth.tenantId(),
                      found.getFirst().get("id"),
                      requestId)
                  > 0,
          "确认标识不属于当前申请");

      require(
          Objects.toString(
                  payload(found.getFirst())
                      .getOrDefault(
                          "legacyFingerprint", found.getFirst().get("preview_fingerprint")),
                  "")
              .equals(text(body, "fingerprint")),
          "确认标识不能重复用于不同出库内容");
      return requests.detail(requestId);
    }
    var f = legacyForm(requestId, body);
    var p = transientPlan(f);
    require(text(p, "fingerprint").equals(text(body, "fingerprint")), "库存或申请已变化，请重新预览");
    var created = (Map<String, Object>) save(null, f);
    long issue = id(created.get("id"));
    var raw = row(issue, true, "确认出库");
    var data = payload(raw);
    data.put("legacyFingerprint", p.get("fingerprint"));
    db.update(
        "UPDATE inv_issue SET payload=CAST(? AS JSON) WHERE tenant_id=? AND id=?",
        encode(data),
        auth.tenantId(),
        issue);
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
    var prior =
        db.queryForList(
            "SELECT * FROM inv_issue WHERE tenant_id=? AND confirmation_key=? AND deleted=0",
            auth.tenantId(),
            key);
    if (!prior.isEmpty()) {
      var old = prior.getFirst();
      row(id(old.get("id")), false, "确认出库");
      require(
          text(payload(old), "legacyFingerprint").equals(text(body, "fingerprint")),
          "确认标识不能重复用于不同内容");
      return Map.of("groupCode", old.get("code"), "outboundId", Objects.toString(old.get("id")));
    }
    require(body.get("ids") instanceof List<?>, "请在出库管理中重新预览合并出库");
    var f = (Map<String, Object>) fromRequests(body);
    var p = transientPlan(f);
    require(text(p, "fingerprint").equals(text(body, "fingerprint")), "库存已变化，请重新预览");
    var created = (Map<String, Object>) save(null, f);
    long issue = id(created.get("id"));
    var stored = payload(row(issue, true, "确认出库"));
    stored.put("legacyFingerprint", p.get("fingerprint"));
    db.update(
        "UPDATE inv_issue SET payload=CAST(? AS JSON) WHERE tenant_id=? AND id=?",
        encode(stored),
        auth.tenantId(),
        issue);
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
