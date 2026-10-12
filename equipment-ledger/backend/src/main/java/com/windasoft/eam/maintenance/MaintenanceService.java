package com.windasoft.eam.maintenance;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.system.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceService {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final NumberCounterMapper counter;
  private static final Set<String> KINDS = Set.of("types", "standards", "plans");

  public MaintenanceService(
      JdbcTemplate db,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      NumberCounterMapper counter) {
    this.db = db;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
    this.counter = counter;
  }

  static String text(Map<String, Object> m, String key) {
    return Objects.toString(m.get(key), "").trim();
  }

  static long number(Object value) {
    return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
  }

  static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "true".equals(Objects.toString(v));
  }

  String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalArgumentException("数据格式不正确");
    }
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new IllegalArgumentException("保存的数据格式不正确");
    }
  }

  @SuppressWarnings("unchecked")
  static List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> a ? (List<Map<String, Object>>) a : List.of();
  }

  static List<Long> ids(Object value) {
    return value instanceof List<?> a
        ? a.stream().map(MaintenanceService::number).filter(x -> x > 0).distinct().toList()
        : List.of();
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  static void deny() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此任务的操作权限");
  }

  Map<String, Object> raw(String kind, long tenant, long id, boolean lock) {
    require(KINDS.contains(kind), "未知保养资源");
    var a =
        db.queryForList(
            "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            tenant,
            kind,
            id);
    if (a.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
    return a.getFirst();
  }

  boolean visible(Map<String, Object> row, String action) {
    try {
      var scope = permissions.maintenance(text(row, "kind"), action);
      return scope.matches(
          null,
          row.get("department_id") == null ? null : number(row.get("department_id")),
          number(row.get("creator_id")),
          auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  Map<String, Object> view(Map<String, Object> row) {
    var v = decode(row.get("payload"));
    v.put("id", Objects.toString(row.get("id")));
    v.put("code", row.get("code"));
    v.put("name", row.get("name"));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("creatorId", Objects.toString(row.get("creator_id")));
    v.put("rowVersion", row.get("row_version"));
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    return v;
  }

  public List<Map<String, Object>> definitions(String kind) {
    require(KINDS.contains(kind), "未知保养资源");
    permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    return db
        .queryForList(
            "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind=? ORDER BY"
                + " created_at DESC,id DESC",
            auth.tenantId(),
            kind)
        .stream()
        .filter(x -> kind.equals("types") || visible(x, "查看"))
        .map(this::view)
        .toList();
  }

  public List<Map<String, Object>> exportRows(String kind) {
    permissions.require(PermissionService.maintenanceMenu(kind), "导出");
    if (kind.equals("tasks"))
      return db
          .queryForList(
              "SELECT * FROM eam_maintenance_task WHERE tenant_id=? ORDER BY due_date DESC,id DESC",
              auth.tenantId())
          .stream()
          .filter(row -> taskAccess(row, "导出"))
          .map(this::taskView)
          .toList();
    require(KINDS.contains(kind), "未知保养资源");
    return db
        .queryForList(
            "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind=? ORDER BY"
                + " created_at DESC,id DESC",
            auth.tenantId(),
            kind)
        .stream()
        .filter(row -> kind.equals("types") || visible(row, "导出"))
        .map(this::view)
        .toList();
  }

  public Map<String, Object> definition(String kind, long id) {
    var row = raw(kind, auth.tenantId(), id, false);
    if (kind.equals("types")) permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    else if (!visible(row, "查看")) deny();
    var v = view(row);
    if (kind.equals("plans"))
      v.put(
          "details",
          db
              .queryForList(
                  "SELECT * FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND"
                      + " enabled=1 ORDER BY id",
                  auth.tenantId(),
                  id)
              .stream()
              .map(
                  r -> {
                    var d = decode(r.get("payload"));
                    d.put("id", Objects.toString(r.get("id")));
                    d.put("equipmentId", Objects.toString(r.get("equipment_id")));
                    d.put("typeId", Objects.toString(r.get("type_id")));
                    d.put("standardId", Objects.toString(r.get("standard_id")));
                    d.put("nextDate", Objects.toString(r.get("next_date")));
                    return d;
                  })
              .toList());
    return v;
  }

  List<Map<String, Object>> planProjects(
      Map<String, Object> detail, long tenant, Map<String, Object> eq, boolean checkAccess) {
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    var references = rows(detail.get("projectRefs"));
    if (!detail.containsKey("projectRefs")) {
      var legacy = raw("standards", tenant, number(detail.get("standardId")), false);
      references =
          rows(decode(legacy.get("payload")).get("items")).stream()
              .filter(item -> text(item, "typeId").equals(text(detail, "typeId")))
              .map(
                  item -> {
                    var ref = new LinkedHashMap<String, Object>();
                    ref.put("standardId", text(detail, "standardId"));
                    ref.put("itemId", text(item, "id"));
                    return (Map<String, Object>) ref;
                  })
              .toList();
    }
    require(references.size() <= 1000, "一行最多关联1000个保养项目");
    for (var ref : references) {
      var standard = raw("standards", tenant, number(ref.get("standardId")), false);
      if (checkAccess && !visible(standard, "查看")) deny();
      var payload = decode(standard.get("payload"));
      // Explicit selections may use any accessible standard; equipment associations are defaults.
      if (!detail.containsKey("projectRefs"))
        require(applicable(payload, eq, tenant), "保养项目所属标准不适用于该设备");
      var item =
          rows(payload.get("items")).stream()
              .filter(i -> text(i, "id").equals(text(ref, "itemId")))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("所选保养项目已删除，请重新选择"));
      require(text(item, "typeId").equals(text(detail, "typeId")), "保养项目周期与本行保养类型不一致");
      String key = text(ref, "standardId") + ":" + text(ref, "itemId");
      if (!seen.add(key)) continue;
      var selected = new LinkedHashMap<String, Object>(item);
      // Keep legacy item IDs for existing schedule result prefill; cross-standard IDs are
      // namespaced.
      if (detail.containsKey("projectRefs")) selected.put("id", key);
      selected.put("sourceStandardId", text(ref, "standardId"));
      selected.put("sourceStandardName", Objects.toString(standard.get("name"), ""));
      result.add(selected);
    }
    return result;
  }

  void validUsers(Object value, boolean required) {
    var ids = ids(value);
    require(!required || !ids.isEmpty(), "请选择人员");
    for (var id : ids)
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
                      + " status=1",
                  Integer.class,
                  auth.tenantId(),
                  id)
              > 0,
          "所选人员不存在或已停用");
  }

  void validAttachments(Object value, boolean imagesOnly) {
    for (var a : rows(value)) {
      var url = text(a, "url");
      require(url.matches("/api/v1/maintenance/files/[0-9a-f-]{36}"), "附件地址无效");
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_file WHERE tenant_id=? AND id=?",
                  Integer.class,
                  auth.tenantId(),
                  url.substring(url.lastIndexOf('/') + 1))
              > 0,
          "附件不存在");
      if (imagesOnly)
        require(
            db.queryForObject(
                    "SELECT COUNT(*) FROM eam_maintenance_file WHERE tenant_id=? AND id=? AND"
                        + " extension IN ('.jpg','.jpeg','.png','.gif','.webp')",
                    Integer.class,
                    auth.tenantId(),
                    url.substring(url.lastIndexOf('/') + 1))
                > 0,
            "该项目必须上传图片");
    }
  }

  Map<String, Object> equipment(long tenant, long id) {
    var a =
        db.queryForList(
            "SELECT * FROM eam_equipment WHERE tenant_id=? AND id=? AND deleted=0", tenant, id);
    require(!a.isEmpty(), "设备不存在");
    return a.getFirst();
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> equipment, long tenant) {
    if (ids(standard.get("equipmentIds")).isEmpty()
        && strings(standard.get("categoryNames")).isEmpty()) return true;
    if (ids(standard.get("equipmentIds")).contains(number(equipment.get("id")))) return true;
    var names = new HashSet<>(strings(standard.get("categoryNames")));
    if (names.contains(text(equipment, "category"))) return true;
    var categories =
        db.queryForList(
            "SELECT id,parent_id,name FROM eam_base_item WHERE tenant_id=? AND item_type='CATEGORY'"
                + " AND deleted=0",
            tenant);
    for (var category : categories)
      if (text(category, "name").equals(text(equipment, "category"))) {
        var current = category;
        var seen = new HashSet<Long>();
        while (current != null && seen.add(number(current.get("id")))) {
          if (names.contains(text(current, "name"))) return true;
          long parent = number(current.get("parent_id"));
          current =
              categories.stream()
                  .filter(c -> number(c.get("id")) == parent)
                  .findFirst()
                  .orElse(null);
        }
      }
    return false;
  }

  void checkEquipment(Map<String, Object> e, String kind, String action) {
    var scope = permissions.maintenance(kind, action);
    if (!scope.matches(
        e.get("company_org_id") == null ? null : number(e.get("company_org_id")),
        e.get("department_org_id") == null ? null : number(e.get("department_org_id")),
        e.get("owner_user_id") == null ? null : number(e.get("owner_user_id")),
        auth.userId())) deny();
  }

  @Transactional
  public Map<String, Object> save(String kind, Long id, Map<String, Object> body) {
    require(KINDS.contains(kind), "未知保养资源");
    String action = id == null ? "新增" : "编辑";
    permissions.require(PermissionService.maintenanceMenu(kind), action);
    long tenant = auth.tenantId();
    Map<String, Object> existing = id == null ? null : raw(kind, tenant, id, true);
    if (existing != null) {
      if (!kind.equals("types") && !visible(existing, action)) deny();
      VersionGuard.checkPayload((Number) existing.get("row_version"), body);
    }
    require(!text(body, "name").isBlank() && text(body, "name").length() <= 150, "名称必填，且不超过150字");
    var data = new LinkedHashMap<>(body);
    data.remove("id");
    data.remove("rowVersion");
    data.remove("createdAt");
    data.remove("creatorId");
    long department = number(data.get("departmentId"));
    if (!kind.equals("types")) {
      require(department > 0, "请选择所属部门");
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0",
                  Integer.class,
                  tenant,
                  department)
              > 0,
          "部门不存在");
      var scope = permissions.maintenance(kind, action);
      if (!scope.matches(null, department, auth.userId(), auth.userId())) deny();
    }
    if (kind.equals("types")) {
      require(
          number(data.get("period")) > 0 && number(data.get("period")) <= 10000, "周期必须在1至10000之间");
      require(Set.of("日", "周", "月", "年").contains(text(data, "unit")), "周期单位无效");
      data.put("system", existing != null && yes(decode(existing.get("payload")).get("system")));
    }
    if (kind.equals("standards")) {
      require(!rows(data.get("items")).isEmpty(), "请添加保养项目");
      var itemIds = new HashSet<String>();
      for (var item : rows(data.get("items"))) {
        require(!text(item, "name").isBlank() && !text(item, "content").isBlank(), "项目名称和内容必填");
        raw("types", tenant, number(item.get("typeId")), false);
        require(Set.of("选项", "数值", "文本").contains(text(item, "valueType")), "项目类型无效");
        if (text(item, "id").isEmpty()) item.put("id", Long.toString(IdWorker.getId()));
        if (text(item, "code").isEmpty()) item.put("code", "MI-" + IdWorker.getId());
        require(itemIds.add(text(item, "id")), "项目编号不能重复");
        if (text(item, "valueType").equals("数值")) {
          if (!text(item, "lower").isEmpty()) new BigDecimal(text(item, "lower"));
          if (!text(item, "upper").isEmpty()) new BigDecimal(text(item, "upper"));
        }
        if (text(item, "valueType").equals("选项")) {
          var choices = strings(item.get("options"));
          require(choices.size() >= 2, "选项项目至少填写两个选项");
          require(
              choices.containsAll(strings(item.get("normalOptions")))
                  && !strings(item.get("normalOptions")).isEmpty(),
              "正常选项必须来自可选项");
        }
        if (text(item, "valueType").equals("数值")
            && !text(item, "lower").isEmpty()
            && !text(item, "upper").isEmpty())
          require(
              new BigDecimal(text(item, "lower")).compareTo(new BigDecimal(text(item, "upper")))
                  <= 0,
              "下限不能大于上限");
        validAttachments(item.get("references"), false);
        var spareSummary = new ArrayList<String>();
        for (var spare : rows(item.get("spareRequirements"))) {
          var stock =
              db.queryForList(
                  "SELECT * FROM eam_spare_stock WHERE tenant_id=? AND id=?",
                  tenant,
                  number(spare.get("stockId")));
          require(!stock.isEmpty(), "所需备件不存在");
          var scope = permissions.maintenance(kind, action);
          if (!scope.all()
              && !Objects.equals(
                  permissions.companyOfUser(), number(stock.getFirst().get("company_id")))) deny();
          var quantity = new BigDecimal(text(spare, "quantity"));
          require(quantity.signum() > 0, "备件数量必须大于0");
          spareSummary.add(
              text(stock.getFirst(), "name")
                  + " · "
                  + text(stock.getFirst(), "model")
                  + " × "
                  + quantity.stripTrailingZeros().toPlainString());
        }
        item.put("spares", String.join("；", spareSummary));
      }
      for (var e : ids(data.get("equipmentIds")))
        checkEquipment(equipment(tenant, e), kind, action);
    }
    if (kind.equals("plans")) {
      validUsers(data.get("owners"), true);
      validUsers(data.get("reviewers"), true);
      require(!rows(data.get("details")).isEmpty(), "请添加计划设备及保养项目");
      require(Set.of("固定间隔", "完成时间").contains(text(data, "calculation")), "请选择下次保养时间计算方式");
      require(
          number(data.get("advanceDays")) >= 0 && number(data.get("advanceDays")) <= 365,
          "提前生成天数须在0至365之间");
      require(
          number(data.get("remindDays")) >= 0 && number(data.get("remindDays")) <= 365,
          "提前提醒天数须在0至365之间");
      require(
          number(data.get("advanceDays")) >= number(data.get("remindDays")), "提前生成天数不能小于提前提醒天数");
      for (var d : rows(data.get("details"))) {
        checkEquipment(equipment(tenant, number(d.get("equipmentId"))), kind, action);
        raw("types", tenant, number(d.get("typeId")), false);
        var selectedProjects =
            planProjects(d, tenant, equipment(tenant, number(d.get("equipmentId"))), true);
        require(!selectedProjects.isEmpty(), "请选择保养项目");
        if (d.containsKey("projectRefs"))
          d.put("standardId", text(rows(d.get("projectRefs")).getFirst(), "standardId"));
        LocalDate.parse(text(d, "nextDate"));
        require(number(d.get("period")) > 0 && number(d.get("period")) <= 10000, "周期必须在1至10000之间");
        require(Set.of("日", "周", "月", "年").contains(text(d, "unit")), "周期单位无效");
        require(number(d.get("validDays")) >= 0 && number(d.get("validDays")) <= 36500, "有效天数无效");
        validUsers(d.get("owners"), false);
        validUsers(d.get("reviewers"), false);
        var reviewers =
            ids(d.get("reviewers")).isEmpty()
                ? ids(data.get("reviewers"))
                : ids(d.get("reviewers"));
        var eq = equipment(tenant, number(d.get("equipmentId")));
        for (var reviewer : reviewers)
          require(
              permissions.canMaintenanceAccept(
                  reviewer,
                  eq.get("department_org_id") == null ? null : number(eq.get("department_org_id"))),
              "所选验收人没有该设备所属部门的保养验收权限");
      }
    }
    long key = id == null ? IdWorker.getId() : id;
    String code = text(data, "code");
    if (code.isEmpty()) {
      if (kind.equals("standards")) {
        String month =
            LocalDate.now(ZoneId.of("Asia/Shanghai"))
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
        counter.nextMonthlyNumber("standard-" + tenant, month);
        code = "MS-" + month + "-" + String.format("%04d", counter.currentValue());
      } else {
        code = (kind.equals("types") ? "MT-" : "MP-") + IdWorker.getId();
      }
    }
    require(code.length() <= 64, "编码不能超过64字");
    data.put("code", code);
    if (existing == null)
      db.update(
          "INSERT INTO"
              + " eam_maintenance_definition(id,tenant_id,kind,code,name,department_id,creator_id,payload)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          key,
          tenant,
          kind,
          code,
          text(data, "name"),
          department == 0 ? null : department,
          auth.userId(),
          encode(data));
    else
      db.update(
          "UPDATE eam_maintenance_definition SET"
              + " code=?,name=?,department_id=?,payload=?,row_version=row_version+1,updated_at=NOW()"
              + " WHERE id=? AND tenant_id=?",
          code,
          text(data, "name"),
          department == 0 ? null : department,
          encode(data),
          key,
          tenant);
    if (kind.equals("plans")) {
      var keep = new ArrayList<Long>();
      for (var d : rows(data.get("details"))) {
        long sid = number(d.get("id"));
        var date = LocalDate.parse(text(d, "nextDate"));
        if (sid > 0) {
          var old =
              db.queryForList(
                  "SELECT * FROM eam_maintenance_schedule WHERE id=? AND tenant_id=? AND plan_id=?"
                      + " FOR UPDATE",
                  sid,
                  tenant,
                  key);
          require(!old.isEmpty(), "计划明细已变更");
          require(
              number(old.getFirst().get("equipment_id")) == number(d.get("equipmentId")),
              "已有计划明细不能更换设备，请新增明细");
          var last =
              db.queryForList(
                  "SELECT due_date FROM eam_maintenance_task WHERE tenant_id=? AND schedule_id=?"
                      + " ORDER BY due_date DESC LIMIT 1",
                  tenant,
                  sid);
          require(
              last.isEmpty()
                  || date.equals(((java.sql.Date) old.getFirst().get("next_date")).toLocalDate())
                  || date.isAfter(((java.sql.Date) last.getFirst().get("due_date")).toLocalDate()),
              "下次保养时间必须晚于已生成任务的保养时间");
          var oldCycle = decode(old.getFirst().get("payload"));
          boolean keepAnchor =
              date.equals(((java.sql.Date) old.getFirst().get("next_date")).toLocalDate())
                  && number(d.get("period")) == number(oldCycle.get("period"))
                  && text(d, "unit").equals(text(oldCycle, "unit"));
          db.update(
              "UPDATE eam_maintenance_schedule SET"
                  + " type_id=?,standard_id=?,next_date=?,anchor_date=?,cycle_index=?,payload=?,enabled=1"
                  + " WHERE tenant_id=? AND id=?",
              number(d.get("typeId")),
              number(d.get("standardId")),
              date,
              keepAnchor ? old.getFirst().get("anchor_date") : date,
              keepAnchor ? old.getFirst().get("cycle_index") : 0,
              encode(d),
              tenant,
              sid);
        } else {
          sid = IdWorker.getId();
          db.update(
              "INSERT INTO"
                  + " eam_maintenance_schedule(id,tenant_id,plan_id,equipment_id,type_id,standard_id,next_date,anchor_date,payload)"
                  + " VALUES(?,?,?,?,?,?,?,?,?)",
              sid,
              tenant,
              key,
              number(d.get("equipmentId")),
              number(d.get("typeId")),
              number(d.get("standardId")),
              date,
              date,
              encode(d));
        }
        keep.add(sid);
      }
      for (var r :
          db.queryForList(
              "SELECT id FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=?",
              tenant,
              key))
        if (!keep.contains(number(r.get("id"))))
          db.update(
              "UPDATE eam_maintenance_schedule SET enabled=0 WHERE tenant_id=? AND id=?",
              tenant,
              r.get("id"));
      // Save and generation share this transaction, so due tasks exist when saving succeeds.
      generate(tenant, key);
    }
    return definition(kind, key);
  }

  @Transactional
  public void delete(String kind, long id) {
    var r = raw(kind, auth.tenantId(), id, true);
    permissions.require(PermissionService.maintenanceMenu(kind), "删除");
    if (!kind.equals("types") && !visible(r, "删除")) deny();
    var payload = decode(r.get("payload"));
    require(!yes(payload.get("system")), "系统预置保养类型不能删除");
    if (kind.equals("plans")) {
      db.update(
          "UPDATE eam_maintenance_schedule SET enabled=0 WHERE tenant_id=? AND plan_id=?",
          auth.tenantId(),
          id);
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND plan_id=?",
                  Integer.class,
                  auth.tenantId(),
                  id)
              == 0,
          "计划已有任务，请停用计划");
    }
    if (!kind.equals("plans")) {
      String column = kind.equals("types") ? "type_id" : "standard_id";
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_schedule WHERE tenant_id=? AND "
                      + column
                      + "=? AND enabled=1",
                  Integer.class,
                  auth.tenantId(),
                  id)
              == 0,
          "已被保养计划使用，不能删除");
    }
    if (kind.equals("standards"))
      require(
          db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_schedule WHERE tenant_id=? AND enabled=1"
                      + " AND JSON_CONTAINS(JSON_EXTRACT(payload,'$.projectRefs'),JSON_OBJECT('standardId',CAST(?"
                      + " AS CHAR)))",
                  Integer.class,
                  auth.tenantId(),
                  Long.toString(id))
              == 0,
          "已被保养计划项目引用，不能删除");
    if (kind.equals("types"))
      for (var st :
          db.queryForList(
              "SELECT payload FROM eam_maintenance_definition WHERE tenant_id=? AND"
                  + " kind='standards'",
              auth.tenantId()))
        require(
            rows(decode(st.get("payload")).get("items")).stream()
                .noneMatch(i -> text(i, "typeId").equals(Long.toString(id))),
            "已被保养标准使用，不能删除");
    db.update(
        "DELETE FROM eam_maintenance_definition WHERE tenant_id=? AND id=?", auth.tenantId(), id);
  }

  static List<String> strings(Object v) {
    return v instanceof List<?> a ? a.stream().map(Object::toString).toList() : List.of();
  }

  public Map<String, Object> choices() {
    require(permissions.anyMaintenance("查看"), "没有保养访问权限");
    var eq =
        db
            .queryForList(
                "SELECT * FROM eam_equipment WHERE tenant_id=? AND deleted=0 ORDER BY"
                    + " equipment_code",
                auth.tenantId())
            .stream()
            .filter(
                e -> {
                  try {
                    if (!permissions.maintenanceEquipmentVisible(
                        e.get("company_org_id") == null ? null : number(e.get("company_org_id")),
                        e.get("department_org_id") == null
                            ? null
                            : number(e.get("department_org_id")),
                        e.get("owner_user_id") == null ? null : number(e.get("owner_user_id"))))
                      return false;
                    return true;
                  } catch (ResponseStatusException ex) {
                    return false;
                  }
                })
            .map(
                e ->
                    Map.of(
                        "id",
                        Objects.toString(e.get("id")),
                        "code",
                        e.get("equipment_code"),
                        "name",
                        e.get("name"),
                        "model",
                        Objects.toString(e.get("model"), ""),
                        "category",
                        Objects.toString(e.get("category"), ""),
                        "department",
                        Objects.toString(e.get("department"), "")))
            .toList();
    var org =
        db.queryForList(
            "SELECT CAST(id AS CHAR) id,name,CAST(COALESCE(parent_id,0) AS CHAR) parentId,org_type"
                + " type FROM eam_org_unit WHERE tenant_id=? AND deleted=0 AND status=1",
            auth.tenantId());
    org.forEach(
        o -> {
          if ("0".equals(o.get("parentId"))) o.put("parentId", "");
        });
    var allStocks =
        List.of("standards", "plans", "tasks").stream()
            .anyMatch(
                k ->
                    permissions.allowed(PermissionService.maintenanceMenu(k), "查看")
                        && permissions.maintenance(k, "查看").all());
    var stocks =
        db
            .queryForList(
                "SELECT CAST(id AS CHAR) id,code,name,model,quantity,company_id FROM"
                    + " eam_spare_stock WHERE tenant_id=? ORDER BY name",
                auth.tenantId())
            .stream()
            .filter(
                r ->
                    allStocks
                        || Objects.equals(permissions.companyOfUser(), number(r.get("company_id"))))
            .map(
                r -> {
                  r.remove("company_id");
                  return r;
                })
            .toList();
    return Map.of(
        "stocks",
        stocks,
        "equipment",
        eq,
        "organizations",
        org,
        "users",
        db.queryForList(
            "SELECT CAST(u.id AS CHAR) id,u.display_name name,u.account,COALESCE(o.name,'')"
                + " department FROM eam_sys_user u LEFT JOIN eam_org_unit o ON o.id=u.org_id AND"
                + " o.tenant_id=u.tenant_id WHERE u.tenant_id=? AND u.deleted=0 AND u.status=1"
                + " ORDER BY u.display_name",
            auth.tenantId()),
        "categories",
        db.queryForList(
            "SELECT CAST(id AS CHAR) id,name,CAST(COALESCE(parent_id,0) AS CHAR) parentId FROM"
                + " eam_base_item WHERE tenant_id=? AND item_type='CATEGORY' AND deleted=0 AND"
                + " status=1",
            auth.tenantId()));
  }

  Map<String, Object> taskRaw(long tenant, long id, boolean lock) {
    var r =
        db.queryForList(
            "SELECT * FROM eam_maintenance_task WHERE tenant_id=? AND id=?"
                + (lock ? " FOR UPDATE" : ""),
            tenant,
            id);
    if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "保养任务不存在");
    return r.getFirst();
  }

  boolean taskAccess(Map<String, Object> row, String action) {
    var snapshot = decode(row.get("snapshot"));
    if (action.equals("查看")
        && (ids(snapshot.get("owners")).contains(auth.userId())
            || ids(snapshot.get("reviewers")).contains(auth.userId()))) return true;
    try {
      var scope = permissions.maintenance(action);
      if (action.equals("验收")
          && scope.owner()
          && ids(snapshot.get("reviewers")).contains(auth.userId())) return true;
      return scope.matches(
          row.get("company_id") == null ? null : number(row.get("company_id")),
          row.get("department_id") == null ? null : number(row.get("department_id")),
          row.get("owner_id") == null ? null : number(row.get("owner_id")),
          auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  Map<String, Object> taskView(Map<String, Object> r) {
    var v = decode(r.get("snapshot"));
    v.put("companyId", Objects.toString(r.get("company_id"), ""));
    v.put("departmentId", Objects.toString(r.get("department_id"), ""));
    v.put("id", Objects.toString(r.get("id")));
    v.put("code", r.get("code"));
    v.put("status", r.get("status"));
    v.put("dueDate", Objects.toString(r.get("due_date")));
    v.put("expiresDate", Objects.toString(r.get("expires_date"), ""));
    v.put("rowVersion", r.get("row_version"));
    v.put("ownerId", Objects.toString(r.get("owner_id"), ""));
    v.put("createdAt", Objects.toString(r.get("created_at")).replace('T', ' '));
    v.put("result", r.get("result") == null ? Map.of() : decode(r.get("result")));
    v.put(
        "canExecute",
        ids(v.get("owners")).contains(auth.userId())
            && (r.get("owner_id") == null || number(r.get("owner_id")) == auth.userId()));
    v.put("canAccept", ids(v.get("reviewers")).contains(auth.userId()) && taskAccess(r, "验收"));
    return v;
  }

  public List<Map<String, Object>> tasks() {
    return db
        .queryForList(
            "SELECT * FROM eam_maintenance_task WHERE tenant_id=? ORDER BY due_date DESC,id DESC",
            auth.tenantId())
        .stream()
        .filter(t -> taskAccess(t, "查看"))
        .map(this::taskView)
        .toList();
  }

  public Map<String, Object> task(long id) {
    var r = taskRaw(auth.tenantId(), id, false);
    if (!taskAccess(r, "查看")) deny();
    var v = taskView(r);
    v.put(
        "events",
        db
            .queryForList(
                "SELECT action,actor_name actor,DATE_FORMAT(created_at,'%Y-%m-%d %H:%i:%s')"
                    + " time,details FROM eam_maintenance_event WHERE tenant_id=? AND task_id=?"
                    + " ORDER BY created_at,id",
                auth.tenantId(), id)
            .stream()
            .map(
                e -> {
                  e.put("details", decode(e.get("details")));
                  return e;
                })
            .toList());
    return v;
  }

  void event(long tenant, long task, String action, Long actor, String name, Object data) {
    db.update(
        "INSERT INTO eam_maintenance_event(id,tenant_id,task_id,actor_id,actor_name,action,details)"
            + " VALUES(?,?,?,?,?,?,?)",
        IdWorker.getId(),
        tenant,
        task,
        actor,
        name,
        action,
        encode(data));
  }

  void notify(long tenant, long task, List<Long> users, String kind, String title, boolean todo) {
    for (var user : users)
      db.update(
          "INSERT INTO eam_maintenance_inbox(id,tenant_id,task_id,user_id,kind,title,is_todo)"
              + " VALUES(?,?,?,?,?,?,?)",
          IdWorker.getId(),
          tenant,
          task,
          user,
          kind,
          title,
          todo);
  }

  void closeTodos(long tenant, long task) {
    db.update(
        "UPDATE eam_maintenance_inbox SET done=1 WHERE tenant_id=? AND task_id=? AND is_todo=1",
        tenant,
        task);
  }

  @Transactional
  public Map<String, Object> action(long id, String action, Map<String, Object> body) {
    long tenant = auth.tenantId();
    var r = taskRaw(tenant, id, true);
    if (!taskAccess(r, "查看")) deny();
    VersionGuard.checkPayload((Number) r.get("row_version"), body);
    String state = Objects.toString(r.get("status")), next = state;
    var snap = decode(r.get("snapshot"));
    long user = auth.userId();
    var result =
        r.get("result") == null ? new LinkedHashMap<String, Object>() : decode(r.get("result"));
    var owners = ids(snap.get("owners"));
    var reviewers = ids(snap.get("reviewers"));
    Long owner = r.get("owner_id") == null ? null : number(r.get("owner_id"));
    if (Set.of("start", "process", "suspend", "resume").contains(action)) {
      if (!owners.contains(user) || (owner != null && owner != user)) deny();
    }
    if (action.equals("start")) {
      require(state.equals("待执行"), "任务不能开始");
      require(
          !((java.sql.Date) r.get("due_date"))
              .toLocalDate()
              .isAfter(LocalDate.now(ZoneId.of("Asia/Shanghai"))),
          "尚未到保养日期");
      if (yes(snap.get("scanStart")))
        require(text(snap, "equipmentCode").equals(text(body, "equipmentCode")), "设备编号不匹配");
      owner = user;
      next = "执行中";
      result.put("startedAt", LocalDateTime.now(ZoneId.of("Asia/Shanghai")).toString());
      closeTodos(tenant, id);
      notify(tenant, id, List.of(user), "保养待处理", text(snap, "equipmentName"), true);
    } else if (action.equals("process")) {
      require(state.equals("执行中") && Objects.equals(owner, user), "只能处理自己执行中的保养任务");
      validUsers(body.get("collaborators"), false);
      validAttachments(body.get("attachments"), false);
      var submitted = rows(body.get("items"));
      var checked = new ArrayList<Map<String, Object>>();
      for (var item : rows(snap.get("items"))) {
        var value =
            submitted.stream()
                .filter(x -> text(x, "id").equals(text(item, "id")))
                .findFirst()
                .orElse(Map.of());
        String answer = text(value, "value");
        require(!yes(item.get("required")) || !answer.isBlank(), "必填项目未完成：" + text(item, "name"));
        validAttachments(value.get("images"), true);
        require(
            !yes(item.get("photoRequired")) || !rows(value.get("images")).isEmpty(),
            "请上传保养图片：" + text(item, "name"));
        boolean abnormal = false;
        if (!answer.isBlank()) {
          if (text(item, "valueType").equals("选项")) {
            require(strings(item.get("options")).contains(answer), "项目选项无效");
            abnormal = !strings(item.get("normalOptions")).contains(answer);
          }
          if (text(item, "valueType").equals("数值")) {
            var n = new BigDecimal(answer);
            abnormal =
                (!text(item, "lower").isEmpty()
                        && n.compareTo(new BigDecimal(text(item, "lower"))) < 0)
                    || (!text(item, "upper").isEmpty()
                        && n.compareTo(new BigDecimal(text(item, "upper"))) > 0);
          }
        }
        var v = new LinkedHashMap<>(value);
        v.put("id", item.get("id"));
        v.put("abnormal", abnormal);
        require(
            !abnormal || !yes(value.get("resolved")) || !text(value, "measure").isBlank(),
            "异常已处理时请填写处理措施");
        checked.add(v);
      }
      String start = text(body, "startAt"), end = text(body, "endAt");
      require(!start.isEmpty() && !end.isEmpty(), "请填写保养时间段");
      require(!LocalDateTime.parse(start).isAfter(LocalDateTime.parse(end)), "开始时间不能晚于结束时间");
      result = new LinkedHashMap<>(body);
      result.remove("rowVersion");
      result.put("items", checked);
      next = "待验收";
      closeTodos(tenant, id);
      notify(tenant, id, reviewers, "保养待验收", text(snap, "equipmentName"), true);
    } else if (action.equals("accept")) {
      require(state.equals("待验收"), "任务不在待验收状态");
      if (!reviewers.contains(user) || !taskAccess(r, "验收")) deny();
      require(Set.of("通过", "打回").contains(text(body, "decision")), "请选择验收结果");
      require(number(body.get("score")) >= 1 && number(body.get("score")) <= 5, "评分须为1至5分");
      validAttachments(body.get("attachments"), false);
      next = text(body, "decision").equals("通过") ? "已完成" : "执行中";
      result.put("acceptance", body);
      closeTodos(tenant, id);
      notify(
          tenant,
          id,
          owner == null ? owners : List.of(owner),
          next.equals("已完成") ? "保养已验收" : "保养待处理",
          text(snap, "equipmentName"),
          !next.equals("已完成"));
      if (next.equals("已完成")) {
        db.update(
            "INSERT INTO"
                + " eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,occurred_at,description,operator_name,status,result)"
                + " VALUES(?,?,?,'MAINTENANCE',?,NOW(),?,?,?,?)",
            IdWorker.getId(),
            tenant,
            r.get("equipment_id"),
            r.get("code"),
            text(result, "description"),
            userName(tenant, owner),
            "已完成",
            text(body, "description"));
        if ("完成时间".equals(text(snap, "calculation"))) {
          var cycle = decode(snap.get("cycle"));
          LocalDate date =
              advance(
                  LocalDateTime.parse(text(result, "endAt")).toLocalDate(),
                  number(cycle.get("period")),
                  text(cycle, "unit"));
          db.update(
              "UPDATE eam_maintenance_schedule SET next_date=?,anchor_date=?,cycle_index=0 WHERE"
                  + " tenant_id=? AND id=?",
              date,
              date,
              tenant,
              r.get("schedule_id"));
        }
      }
    } else if (action.equals("suspend")) {
      require(state.equals("执行中"), "仅执行中的任务可以挂起");
      require(!text(body, "description").isEmpty(), "请填写挂起原因");
      next = "挂起";
      closeTodos(tenant, id);
    } else if (action.equals("resume")) {
      require(state.equals("挂起"), "仅挂起任务可以恢复");
      next = "执行中";
      notify(tenant, id, List.of(user), "保养待处理", text(snap, "equipmentName"), true);
    } else if (action.equals("transfer")) {
      if (!taskAccess(r, "编辑")) deny();
      require(Set.of("待执行", "执行中", "挂起").contains(state), "当前任务不能转派");
      validUsers(body.get("owners"), true);
      snap.put("owners", ids(body.get("owners")).stream().map(Object::toString).toList());
      owner = null;
      next = "待执行";
      closeTodos(tenant, id);
      notify(tenant, id, ids(body.get("owners")), "保养待执行", text(snap, "equipmentName"), true);
    } else if (action.equals("void")) {
      if (!taskAccess(r, "删除")) deny();
      require(!Set.of("已完成", "已作废").contains(state), "任务不能作废");
      require(!text(body, "description").isEmpty(), "请填写作废原因");
      next = "已作废";
      closeTodos(tenant, id);
    } else throw new IllegalArgumentException("未知任务操作");
    db.update(
        "UPDATE eam_maintenance_task SET"
            + " status=?,owner_id=?,snapshot=?,result=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE tenant_id=? AND id=?",
        next,
        owner,
        encode(snap),
        encode(result),
        tenant,
        id);
    String label =
        switch (action) {
          case "start" -> "开始保养";
          case "process" -> "提交保养";
          case "accept" -> text(body, "decision").equals("通过") ? "验收通过" : "验收打回";
          case "suspend" -> "挂起";
          case "resume" -> "恢复";
          case "transfer" -> "转派";
          default -> "作废";
        };
    event(tenant, id, label, user, auth.user().getDisplayName(), body);
    return task(id);
  }

  @Transactional
  public void batchAccept(List<Map<String, Object>> values) {
    require(!values.isEmpty() && values.size() <= 100, "请选择1至100条任务");
    var sorted = new ArrayList<>(values);
    sorted.sort(Comparator.comparingLong(v -> number(v.get("id"))));
    for (var v : sorted) action(number(v.get("id")), "accept", v);
  }

  String userName(long tenant, Long id) {
    if (id == null) return "";
    var users =
        db.queryForList(
            "SELECT display_name FROM eam_sys_user WHERE tenant_id=? AND id=?", tenant, id);
    return users.isEmpty() ? "" : Objects.toString(users.getFirst().get("display_name"));
  }

  static LocalDate advance(LocalDate date, long period, String unit) {
    return switch (unit) {
      case "日" -> date.plusDays(period);
      case "周" -> date.plusWeeks(period);
      case "月" -> date.plusMonths(period);
      case "年" -> date.plusYears(period);
      default -> throw new IllegalArgumentException("周期单位无效");
    };
  }

  // Every occurrence has a unique schedule/date key. A locked schedule prevents duplicate
  // generation across workers.
  @Transactional
  public int generate(long tenant, Long planId) {
    int count = 0;
    var plans =
        db.queryForList(
            "SELECT * FROM eam_maintenance_definition WHERE tenant_id=? AND kind='plans'"
                + (planId == null ? "" : " AND id=?")
                + " ORDER BY id FOR UPDATE",
            planId == null ? new Object[] {tenant} : new Object[] {tenant, planId});
    LocalDate today = LocalDate.now(ZoneId.of("Asia/Shanghai"));
    for (var plan : plans) {
      var p = decode(plan.get("payload"));
      if (!yes(p.get("enabled"))) continue;
      // Generate when today reaches next maintenance date minus advance days.
      LocalDate generationDeadline = today.plusDays(number(p.get("advanceDays")));
      var schedules =
          db.queryForList(
              "SELECT * FROM eam_maintenance_schedule WHERE tenant_id=? AND plan_id=? AND enabled=1"
                  + " ORDER BY id FOR UPDATE",
              tenant,
              plan.get("id"));
      for (var schedule : schedules) {
        var cycle = decode(schedule.get("payload"));
        if (!yes(
            decode(raw("types", tenant, number(schedule.get("type_id")), false).get("payload"))
                .get("enabled"))) continue;
        LocalDate due = ((java.sql.Date) schedule.get("next_date")).toLocalDate();
        LocalDate anchor = ((java.sql.Date) schedule.get("anchor_date")).toLocalDate();
        long index = number(schedule.get("cycle_index"));
        boolean completion = text(p, "calculation").equals("完成时间");
        if (completion
            && db.queryForObject(
                    "SELECT COUNT(*) FROM eam_maintenance_task WHERE tenant_id=? AND schedule_id=?"
                        + " AND status NOT IN ('已完成','已作废','过期作废')",
                    Integer.class,
                    tenant,
                    schedule.get("id"))
                > 0) continue;
        for (int n = 0; n < 100 && !due.isAfter(generationDeadline); n++) {
          var equipment = equipment(tenant, number(schedule.get("equipment_id")));
          var projects = planProjects(cycle, tenant, equipment, false);
          require(!projects.isEmpty(), "计划设备没有可执行的保养项目");
          var type = view(raw("types", tenant, number(schedule.get("type_id")), false));
          var snap = new LinkedHashMap<String, Object>();
          snap.put("planName", plan.get("name"));
          snap.put("equipmentCode", equipment.get("equipment_code"));
          snap.put("equipmentName", equipment.get("name"));
          snap.put("model", Objects.toString(equipment.get("model"), ""));
          snap.put("department", Objects.toString(equipment.get("department"), ""));
          snap.put("functionLocation", Objects.toString(equipment.get("function_location"), ""));
          snap.put("typeName", type.get("name"));
          snap.put("calculation", p.get("calculation"));
          snap.put("cycle", cycle);
          snap.put(
              "owners",
              (ids(cycle.get("owners")).isEmpty() ? ids(p.get("owners")) : ids(cycle.get("owners")))
                  .stream().map(Object::toString).toList());
          snap.put(
              "reviewers",
              (ids(cycle.get("reviewers")).isEmpty()
                      ? ids(p.get("reviewers"))
                      : ids(cycle.get("reviewers")))
                  .stream().map(Object::toString).toList());
          snap.put("items", projects);
          snap.put(
              "standardName",
              projects.stream()
                  .map(i -> text(i, "sourceStandardName"))
                  .distinct()
                  .collect(java.util.stream.Collectors.joining("、")));
          snap.put("remark", p.get("remark"));
          snap.put("autoFill", p.get("autoFill"));
          snap.put("scanStart", p.get("scanStart"));
          snap.put("remindDays", p.get("remindDays"));
          long task = IdWorker.getId();
          String month = today.format(java.time.format.DateTimeFormatter.ofPattern("yyyyMM"));
          counter.nextMonthlyNumber("maintenance-" + tenant, month);
          String code = "BY-" + month + "-" + String.format("%04d", counter.currentValue());
          var expires =
              number(cycle.get("validDays")) == 0
                  ? null
                  : due.plusDays(number(cycle.get("validDays")));
          int added =
              db.update(
                  "INSERT IGNORE INTO"
                      + " eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,company_id,department_id,due_date,expires_date,snapshot)"
                      + " VALUES(?,?,?,?,?,?,?,?,?,?,?)",
                  task,
                  tenant,
                  code,
                  plan.get("id"),
                  schedule.get("id"),
                  equipment.get("id"),
                  equipment.get("company_org_id"),
                  equipment.get("department_org_id"),
                  due,
                  expires,
                  encode(snap));
          if (added > 0) {
            if ("上次结果".equals(text(p, "autoFill"))) {
              var previous =
                  db.queryForList(
                      "SELECT result FROM eam_maintenance_task WHERE tenant_id=? AND equipment_id=?"
                          + " AND schedule_id=? AND status='已完成' ORDER BY updated_at DESC LIMIT 1",
                      tenant,
                      equipment.get("id"),
                      schedule.get("id"));
              if (!previous.isEmpty()) {
                var values = rows(decode(previous.getFirst().get("result")).get("items"));
                var draft = new ArrayList<Map<String, Object>>();
                for (var value : values) {
                  var v = new LinkedHashMap<>(value);
                  v.put("images", List.of());
                  v.put("measure", "");
                  v.put("resolved", false);
                  draft.add(v);
                }
                db.update(
                    "UPDATE eam_maintenance_task SET result=? WHERE tenant_id=? AND id=?",
                    encode(Map.of("items", draft)),
                    tenant,
                    task);
              }
            }
            count++;
            event(
                tenant,
                task,
                "生成任务",
                null,
                "系统",
                Map.of("计划", plan.get("name"), "保养日期", due.toString()));
            if (!due.isAfter(today.plusDays(number(p.get("remindDays")))))
              notify(
                  tenant,
                  task,
                  ids(snap.get("owners")),
                  "保养待执行",
                  text(snap, "equipmentName"),
                  true);
          }
          if (completion) {
            if (added == 0) {
              due = advance(due, number(cycle.get("period")), text(cycle, "unit"));
              db.update(
                  "UPDATE eam_maintenance_schedule SET next_date=?,anchor_date=?,cycle_index=0"
                      + " WHERE tenant_id=? AND id=?",
                  due,
                  due,
                  tenant,
                  schedule.get("id"));
              continue;
            }
            break;
          }
          index++;
          due = advance(anchor, number(cycle.get("period")) * index, text(cycle, "unit"));
        }
        if (!completion)
          db.update(
              "UPDATE eam_maintenance_schedule SET next_date=?,cycle_index=? WHERE tenant_id=? AND"
                  + " id=?",
              due,
              index,
              tenant,
              schedule.get("id"));
      }
    }
    // Expire only unopened tasks; keep in-progress work and its records intact.
    for (var task :
        db.queryForList(
            "SELECT id FROM eam_maintenance_task WHERE tenant_id=? AND expires_date<? AND"
                + " status='待执行' FOR UPDATE",
            tenant,
            today)) {
      long id = number(task.get("id"));
      db.update(
          "UPDATE eam_maintenance_task SET status='过期作废',row_version=row_version+1 WHERE"
              + " tenant_id=? AND id=?",
          tenant,
          id);
      closeTodos(tenant, id);
      event(tenant, id, "过期作废", null, "系统", Map.of());
    }
    for (var r :
        db.queryForList(
            "SELECT * FROM eam_maintenance_task WHERE tenant_id=? AND status='待执行'", tenant)) {
      var snap = decode(r.get("snapshot"));
      if (!((java.sql.Date) r.get("due_date"))
              .toLocalDate()
              .isAfter(today.plusDays(number(snap.get("remindDays"))))
          && db.queryForObject(
                  "SELECT COUNT(*) FROM eam_maintenance_inbox WHERE tenant_id=? AND task_id=? AND"
                      + " kind='保养待执行'",
                  Integer.class,
                  tenant,
                  r.get("id"))
              == 0)
        notify(
            tenant,
            number(r.get("id")),
            ids(snap.get("owners")),
            "保养待执行",
            text(snap, "equipmentName"),
            true);
    }
    return count;
  }

  @Transactional
  public int generatePlan(long id) {
    var p = raw("plans", auth.tenantId(), id, false);
    if (!visible(p, "编辑")) deny();
    return generate(auth.tenantId(), id);
  }

  public List<Map<String, Object>> inbox() {
    return db.queryForList(
        "SELECT CAST(i.id AS CHAR) id,CAST(t.id AS CHAR) orderId,'maintenance' module,t.code"
            + " orderNo,JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName'))"
            + " equipmentName,t.status,i.kind,i.title,i.is_todo todo,i.done,i.read_at IS NOT NULL"
            + " isRead,DATE_FORMAT(i.created_at,'%Y-%m-%d %H:%i:%s') time FROM"
            + " eam_maintenance_inbox i JOIN eam_maintenance_task t ON t.id=i.task_id AND"
            + " t.tenant_id=i.tenant_id WHERE i.tenant_id=? AND i.user_id=? ORDER BY i.created_at"
            + " DESC,i.id DESC LIMIT 300",
        auth.tenantId(), auth.userId());
  }

  public void read(long id) {
    db.update(
        "UPDATE eam_maintenance_inbox SET read_at=NOW() WHERE tenant_id=? AND user_id=? AND id=?",
        auth.tenantId(),
        auth.userId(),
        id);
  }
}
