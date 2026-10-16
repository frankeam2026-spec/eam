package com.windasoft.eam.service.inspection;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.inventory.InventoryMutex experienceInventoryMutex;

  @org.springframework.beans.factory.annotation.Autowired
  private org.springframework.context.ApplicationEventPublisher experiencePublisher;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.experience.MaterialCustodyService materialCustody;

  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private static final Set<String> KINDS =
      Set.of("types", "standards", "daily-plans", "periodic-plans");
  private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

  public InspectionService(
      InspectionRepository repository,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public static String text(Map<String, Object> m, String key) {
    return Objects.toString(m.get(key), "").trim();
  }

  public static long number(Object value) {
    return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
  }

  public static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "true".equals(Objects.toString(v));
  }

  public static void require(boolean condition, String message) {
    if (!condition) throw new IllegalArgumentException(message);
  }

  public static void deny() {
    throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此点巡检记录的操作权限");
  }

  @SuppressWarnings("unchecked")
  public static List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> a ? (List<Map<String, Object>>) a : List.of();
  }

  public static List<String> strings(Object value) {
    return value instanceof List<?> a
        ? a.stream().map(Object::toString).filter(s -> !s.isBlank()).distinct().toList()
        : List.of();
  }

  static List<Long> ids(Object value) {
    return strings(value).stream().map(InspectionService::number).filter(v -> v > 0).toList();
  }

  public String encode(Object value) {
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
      throw new IllegalArgumentException("数据格式不正确");
    }
  }

  static LocalDateTime now() {
    return LocalDateTime.now(ZONE);
  }

  static LocalDateTime dateTime(Object value) {
    return value instanceof java.sql.Timestamp t
        ? t.toLocalDateTime()
        : LocalDateTime.parse(value.toString().replace(' ', 'T'));
  }

  static Long nullable(Object value) {
    return value == null ? null : number(value);
  }

  Map<String, Object> raw(String kind, long tenant, long id, boolean lock) {
    require(KINDS.contains(kind), "未知点巡检资源");
    var r = repository.loadDefinition(tenant, kind, id, lock);
    if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "记录不存在");
    return r.getFirst();
  }

  boolean scoped(Map<String, Object> row, String action) {
    try {
      return permissions
          .inspection(text(row, "kind"), action)
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("creator_id")),
              auth.userId());
    } catch (ResponseStatusException ex) {
      return false;
    }
  }

  Map<String, Object> view(Map<String, Object> row) {
    var v = decode(row.get("payload"));
    v.put("id", row.get("id").toString());
    v.put("code", row.get("code"));
    v.put("name", row.get("name"));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("rowVersion", row.get("row_version"));
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    if (text(row, "kind").equals("types")) v.putIfAbsent("sortOrder", 0);
    if (text(row, "kind").endsWith("plans")) {
      var cursor = repository.listScheduleDates(row.get("tenant_id"), row.get("id"));
      if (!cursor.isEmpty()) v.put("nextAt", dateTime(cursor.getFirst().get("next_at")).toString());
    }
    return v;
  }

  public List<Map<String, Object>> definitions(String kind) {
    require(KINDS.contains(kind), "未知点巡检资源");
    permissions.require(PermissionService.inspectionMenu(kind), "查看");
    return repository.listDefinitions(auth.tenantId(), kind).stream()
        .filter(r -> kind.equals("types") || scoped(r, "查看"))
        .map(this::view)
        .sorted(
            kind.equals("types")
                ? Comparator.<Map<String, Object>>comparingLong(v -> number(v.get("sortOrder")))
                    .thenComparing(v -> text(v, "name"))
                    .thenComparingLong(v -> number(v.get("id")))
                : (a, b) -> 0)
        .toList();
  }

  public List<Map<String, Object>> exportRows(String kind, Object selectedIds) {
    permissions.require(PermissionService.inspectionMenu(kind), "导出");
    var selected = new HashSet<>(ids(selectedIds));
    require(selected.size() <= 10000, "一次最多导出10000条记录");
    if (selected.isEmpty()) return List.of();
    boolean tasks = Set.of("daily-tasks", "periodic-tasks").contains(kind);
    require(tasks || KINDS.contains(kind), "未知点巡检资源");
    var candidates =
        tasks
            ? repository.listKindTasks(
                auth.tenantId(), kind.equals("daily-tasks") ? "daily" : "periodic")
            : repository.listKindDefinitions(auth.tenantId(), kind);
    var result = new ArrayList<Map<String, Object>>();
    for (var row : candidates) {
      if (!selected.contains(number(row.get("id")))) continue;
      if (tasks ? !access(row, "导出") : !scoped(row, "导出")) deny();
      result.add(tasks ? taskView(row) : view(row));
    }
    require(result.size() == selected.size(), "部分记录不存在或已删除，请刷新列表");
    return result;
  }

  public Map<String, Object> definition(String kind, long id) {
    var r = raw(kind, auth.tenantId(), id, false);
    if (kind.equals("types")) permissions.require(PermissionService.inspectionMenu(kind), "查看");
    else if (!scoped(r, "查看")) deny();
    return view(r);
  }

  Map<String, Object> equipment(long tenant, long id) {
    var r = repository.loadEquipment(tenant, id);
    require(!r.isEmpty(), "设备不存在");
    return r.getFirst();
  }

  void checkEquipment(Map<String, Object> row, String kind, String action) {
    var scope = permissions.inspection(kind, action);
    if (!scope.matches(
        nullable(row.get("company_org_id")),
        nullable(row.get("department_org_id")),
        nullable(row.get("owner_user_id")),
        auth.userId())) deny();
  }

  void users(long tenant, Object value, boolean required) {
    var selected = ids(value);
    require(!required || !selected.isEmpty(), "请选择人员");
    for (var id : selected) require(repository.countActiveUser(tenant, id) > 0, "人员不存在或已停用");
  }

  void attachments(long tenant, Object value, boolean images) {
    for (var a : rows(value)) {
      String url = text(a, "url");
      require(url.matches("/api/v1/inspection/files/[0-9a-f-]{36}"), "附件地址无效");
      String id = url.substring(url.lastIndexOf('/') + 1);
      require(repository.countAttachment(tenant, id, images) > 0, images ? "请上传图片" : "附件不存在");
    }
  }

  void validateItems(long tenant, List<Map<String, Object>> items) {
    require(!items.isEmpty() && items.size() <= 1000, "请添加1至1000个检查项目");
    var seen = new HashSet<String>();
    for (var item : items) {
      item.remove("module");
      if (text(item, "id").isBlank()) item.put("id", Long.toString(IdWorker.getId()));
      if (text(item, "code").isBlank()) item.put("code", "II-" + IdWorker.getId());
      require(seen.add(text(item, "id")), "项目不能重复");
      require(!text(item, "name").isBlank() && !text(item, "content").isBlank(), "项目名称和方法及基准必填");
      raw("types", tenant, number(item.get("typeId")), false);
      String type = text(item, "valueType");
      require(Set.of("选项", "数值", "文本").contains(type), "结果类型无效");
      if (type.equals("选项")) {
        var options = strings(item.get("options"));
        var normals = strings(item.get("normalOptions"));
        require(
            options.size() >= 2 && !normals.isEmpty() && options.containsAll(normals),
            "请填写至少两个可选项，并从中设置正常选项");
      }
      if (type.equals("数值")) {
        BigDecimal
            lower = text(item, "lower").isBlank() ? null : new BigDecimal(text(item, "lower")),
            upper = text(item, "upper").isBlank() ? null : new BigDecimal(text(item, "upper"));
        require(lower == null || upper == null || lower.compareTo(upper) <= 0, "下限不能大于上限");
      }
      attachments(tenant, item.get("references"), false);
    }
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> eq, long tenant) {
    if (ids(standard.get("equipmentIds")).isEmpty()
        && strings(standard.get("categoryNames")).isEmpty()) return true;
    if (ids(standard.get("equipmentIds")).contains(number(eq.get("id")))) return true;
    var names = strings(standard.get("categoryNames"));
    String category = text(eq, "category");
    var categories = repository.listCategoryParents(tenant);
    var node =
        categories.stream().filter(c -> text(c, "name").equals(category)).findFirst().orElse(null);
    var seen = new HashSet<Long>();
    while (node != null && seen.add(number(node.get("id")))) {
      if (names.contains(text(node, "name"))) return true;
      long parent = number(node.get("parent_id"));
      node =
          categories.stream().filter(c -> number(c.get("id")) == parent).findFirst().orElse(null);
    }
    return names.contains(category);
  }

  List<Map<String, Object>> projects(long tenant, Map<String, Object> detail, boolean checkAccess) {
    var result = new ArrayList<Map<String, Object>>();
    var seen = new HashSet<String>();
    for (var ref : rows(detail.get("projectRefs"))) {
      var st = raw("standards", tenant, number(ref.get("standardId")), false);
      if (checkAccess && !scoped(st, "查看")) deny();
      var item =
          rows(decode(st.get("payload")).get("items")).stream()
              .filter(i -> text(i, "id").equals(text(ref, "itemId")))
              .findFirst()
              .orElseThrow(() -> new IllegalArgumentException("检查项目已删除，请重新选择"));
      String id = text(ref, "standardId") + ":" + text(ref, "itemId");
      if (seen.add(id)) {
        var selected = new LinkedHashMap<>(item);
        selected.put("id", id);
        selected.put("sourceStandardName", st.get("name"));
        result.add(selected);
      }
    }
    require(!result.isEmpty(), "每台设备至少选择一个检查项目");
    return result;
  }

  static void range(Map<String, Object> data, String key, long min, long max) {
    long value = number(data.get(key));
    require(value >= min && value <= max, key + "超出允许范围");
  }

  static LocalDateTime shiftDuration(
      LocalDateTime base, Map<String, Object> data, String key, String unitKey, int direction) {
    long amount = number(data.get(key)) * direction;
    String unit = text(data, unitKey);
    return switch (unit.isBlank() ? "时" : unit) {
      case "时" -> base.plusHours(amount);
      case "日" -> base.plusDays(amount);
      case "周" -> base.plusWeeks(amount);
      case "月" -> base.plusMonths(amount);
      default -> throw new IllegalArgumentException("时间单位必须为时、日、周或月");
    };
  }

  static void duration(
      Map<String, Object> data, String key, String unitKey, long min, int maxYears) {
    if (text(data, unitKey).isBlank()) data.put(unitKey, "时");
    String unit = text(data, unitKey);
    long max =
        switch (unit) {
          case "时" -> 8760L * maxYears;
          case "日" -> 365L * maxYears;
          case "周" -> 52L * maxYears;
          case "月" -> 12L * maxYears;
          default -> throw new IllegalArgumentException("时间单位必须为时、日、周或月");
        };
    range(data, key, min, max);
  }

  static LocalDateTime generationDeadline(Map<String, Object> data) {
    LocalDateTime advance = shiftDuration(now(), data, "advanceHours", "advanceUnit", 1);
    LocalDateTime reminder = shiftDuration(now(), data, "remindHours", "remindUnit", 1);
    // The search horizon includes month-end clamping; eligibility is checked against each due date.
    if (text(data, "advanceUnit").equals("月") && number(data.get("advanceHours")) > 0)
      advance = advance.plusDays(3);
    if (text(data, "remindUnit").equals("月") && number(data.get("remindHours")) > 0)
      reminder = reminder.plusDays(3);
    return advance.isAfter(reminder) ? advance : reminder;
  }

  static boolean readyForGeneration(LocalDateTime due, Map<String, Object> data) {
    var now = now();
    return !now.isBefore(shiftDuration(due, data, "advanceHours", "advanceUnit", -1))
        || !now.isBefore(shiftDuration(due, data, "remindHours", "remindUnit", -1));
  }

  @Transactional
  public Map<String, Object> save(String kind, Long id, Map<String, Object> body) {
    require(KINDS.contains(kind), "未知点巡检资源");
    String action = id == null ? "新增" : "编辑";
    permissions.require(PermissionService.inspectionMenu(kind), action);
    long tenant = auth.tenantId();
    var existing = id == null ? null : raw(kind, tenant, id, true);
    if (existing != null) {
      if (!kind.equals("types") && !scoped(existing, action)) deny();
      VersionGuard.checkPayload((Number) existing.get("row_version"), body);
    }
    var data = decode(encode(body));
    require(!text(data, "name").isBlank() && text(data, "name").length() <= 150, "名称必填且不超过150字");
    long department = number(data.get("departmentId"));
    if (!kind.equals("types")) {
      var org = repository.findDepartmentParent(tenant, department);
      require(!org.isEmpty(), "请选择有效的所属部门");
      var s = permissions.inspection(kind, action);
      if (!s.matches(
          permissions.companyOfOrganization(department), department, auth.userId(), auth.userId()))
        deny();
    }
    if (kind.equals("types")) {
      Object sort = data.getOrDefault("sortOrder", 0);
      require(sort instanceof Integer || sort instanceof Long, "排序需为整数");
      long sortOrder = ((Number) sort).longValue();
      require(sortOrder >= 0 && sortOrder <= 999999, "排序需在0至999999之间");
      data.put("sortOrder", sortOrder);
      range(data, "period", 1, 10000);
      require(Set.of("日", "周", "月", "年").contains(text(data, "unit")), "周期单位无效");
    }
    if (kind.equals("standards")) {
      validateItems(tenant, rows(data.get("items")));
      for (var eq : ids(data.get("equipmentIds")))
        checkEquipment(equipment(tenant, eq), kind, action);
      for (var category : strings(data.get("categoryNames")))
        require(repository.countCategory(tenant, category) > 0, "设备类别不存在");
    }
    if (kind.endsWith("plans")) {
      require(Set.of("无需审核", "整机审核", "逐项审核").contains(text(data, "reviewMode")), "审核方式无效");
      users(
          tenant,
          data.get("owners"),
          !kind.equals("daily-plans") || text(data, "personMode").equals("指定负责人"));
      users(tenant, data.get("reviewers"), !text(data, "reviewMode").equals("无需审核"));
      duration(data, "validHours", "validUnit", 1, 10);
      duration(data, "advanceHours", "advanceUnit", 0, 1);
      duration(data, "remindHours", "remindUnit", 0, 1);
      duration(data, "expireRemindHours", "expireRemindUnit", 0, 1);
      range(data, "randomPhotoCount", 0, 1000);
      require(Set.of("无需扫码", "逐台校验", "校验一台即可提交").contains(text(data, "scanMode")), "设备校验方式无效");
      LocalDate start = LocalDate.parse(text(data, "startDate")),
          end = LocalDate.parse(text(data, "endDate"));
      require(!end.isBefore(start), "结束日期不能早于开始日期");
      require(
          !rows(data.get("details")).isEmpty() && rows(data.get("details")).size() <= 500,
          "请选择1至500台设备");
      var equipmentIds = new HashSet<Long>();
      for (var detail : rows(data.get("details"))) {
        long eq = number(detail.get("equipmentId"));
        require(equipmentIds.add(eq), "计划设备不能重复");
        checkEquipment(equipment(tenant, eq), kind, action);
        projects(tenant, detail, true);
        users(tenant, detail.get("owners"), false);
        users(tenant, detail.get("reviewers"), false);
      }
      if (kind.equals("periodic-plans")) {
        raw("types", tenant, number(data.get("typeId")), false);
        range(data, "period", 1, 10000);
        require(Set.of("日", "周", "月", "年").contains(text(data, "unit")), "周期单位无效");
        LocalDateTime.parse(text(data, "nextAt"));
      } else {
        require(Set.of("任何人可点检", "指定负责人", "按照排班表").contains(text(data, "personMode")), "请选择点检人员方式");
        require(Set.of("按周", "按月").contains(text(data, "restCycle")), "休息日周期无效");
        for (var d : ids(data.get("restDays")))
          require(d >= 1 && d <= (text(data, "restCycle").equals("按周") ? 7 : 31), "休息日无效");
        for (var d : strings(data.get("holidays"))) LocalDate.parse(d);
        require(
            !rows(data.get("shifts")).isEmpty() && rows(data.get("shifts")).size() <= 24,
            "请设置1至24个班次");
        var shiftIds = new HashSet<String>();
        for (var shift : rows(data.get("shifts"))) {
          if (text(shift, "id").isBlank()) shift.put("id", Long.toString(IdWorker.getId()));
          require(
              shiftIds.add(text(shift, "id")) && !text(shift, "name").isBlank(), "班次名称必填且班次不能重复");
          LocalTime.parse(text(shift, "start"));
          LocalTime.parse(text(shift, "end"));
        }
        for (var roster : rows(data.get("roster"))) {
          LocalDate.parse(text(roster, "date"));
          require(shiftIds.contains(text(roster, "shiftId")), "排班班次不存在");
          users(tenant, roster.get("users"), true);
        }
      }
    }
    long key = id == null ? IdWorker.getId() : id;
    String code = text(data, "code");
    if (code.isBlank()) {
      String prefix =
          switch (kind) {
            case "standards" -> "IS";
            case "types" -> "IT";
            case "daily-plans" -> "DP";
            default -> "IP";
          };
      code = nextCode(tenant, prefix);
    }
    require(code.length() <= 64, "编号不能超过64字");
    data.put("code", code);
    for (String k : List.of("id", "rowVersion", "createdAt", "creatorId")) data.remove(k);
    if (existing == null)
      repository.insertDefinition(
          key,
          tenant,
          kind,
          code,
          text(data, "name"),
          department == 0 ? null : department,
          auth.userId(),
          encode(data));
    else
      repository.updateDefinition(
          code, text(data, "name"), department == 0 ? null : department, encode(data), tenant, key);
    if (kind.endsWith("plans")) {
      var cursors = repository.lockPlanSchedules(tenant, key);
      LocalDateTime next =
          kind.equals("periodic-plans")
              ? LocalDateTime.parse(text(data, "nextAt"))
              : LocalDate.parse(text(data, "startDate")).atStartOfDay();
      if (cursors.isEmpty()) repository.insertSchedule(IdWorker.getId(), tenant, key, next, next);
      else if (kind.equals("periodic-plans")
          && !next.equals(dateTime(cursors.getFirst().get("next_at")))) {
        require(repository.countTasksAtOrAfter(tenant, key, next) == 0, "下次巡检时间必须晚于已生成的任务");
        repository.resetScheduleDate(next, next, tenant, key);
      } else if (kind.equals("daily-plans")) {
        LocalDateTime revisit = now().toLocalDate().atStartOfDay();
        if (next.isAfter(revisit)) revisit = next;
        if (dateTime(cursors.getFirst().get("next_at")).isAfter(revisit))
          repository.revisitSchedule(revisit, tenant, key);
      }
      generate(tenant, key);
    }
    return definition(kind, key);
  }

  String nextCode(long tenant, String prefix) {
    String month = now().format(DateTimeFormatter.ofPattern("yyyyMM"));
    repository.nextMonthlyNumber("inspection-" + prefix + "-" + tenant, month);
    return prefix + "-" + month + "-" + String.format("%04d", repository.currentNumber());
  }

  @Transactional
  public void delete(String kind, long id) {
    var row = raw(kind, auth.tenantId(), id, true);
    permissions.require(PermissionService.inspectionMenu(kind), "删除");
    if (!kind.equals("types") && !scoped(row, "删除")) deny();
    if (kind.endsWith("plans"))
      require(repository.countPlanTasks(auth.tenantId(), id) == 0, "计划已有任务，请停用计划");
    else
      for (var plan : repository.listOtherDefinitionPayloads(auth.tenantId(), id)) {
        var p = decode(plan.get("payload"));
        if (kind.equals("standards"))
          for (var d : rows(p.get("details")))
            require(
                rows(d.get("projectRefs")).stream()
                    .noneMatch(ref -> number(ref.get("standardId")) == id),
                "标准已被计划使用");
        else {
          require(number(p.get("typeId")) != id, "类型已被计划使用");
          for (var i : rows(p.get("items"))) require(number(i.get("typeId")) != id, "类型已被标准使用");
        }
      }
    repository.deletePlanSchedules(auth.tenantId(), id);
    repository.deleteDefinition(auth.tenantId(), id);
  }

  public Map<String, Object> choices() {
    require(permissions.anyInspection("查看"), "没有点巡检访问权限");
    var equipment =
        repository.listEquipment(auth.tenantId()).stream()
            .filter(
                e -> {
                  try {
                    if (!permissions.inspectionEquipmentVisible(
                        nullable(e.get("company_org_id")),
                        nullable(e.get("department_org_id")),
                        nullable(e.get("owner_user_id")))) return false;
                    return true;
                  } catch (ResponseStatusException ex) {
                    return false;
                  }
                })
            .map(
                e ->
                    Map.of(
                        "id",
                        e.get("id").toString(),
                        "code",
                        e.get("equipment_code"),
                        "name",
                        e.get("name"),
                        "model",
                        Objects.toString(e.get("model"), ""),
                        "category",
                        Objects.toString(e.get("category"), ""),
                        "department",
                        Objects.toString(e.get("department"), ""),
                        "functionLocation",
                        Objects.toString(e.get("function_location"), "")))
            .toList();
    var orgs = repository.listOrganizations(auth.tenantId());
    var people = repository.listUsers(auth.tenantId());
    return Map.of(
        "equipment",
        equipment,
        "organizations",
        orgs,
        "users",
        people,
        "categories",
        repository.listCategories(auth.tenantId()));
  }

  Map<String, Object> taskRaw(long tenant, long id, boolean lock) {
    var r = repository.loadTask(tenant, id, lock);
    if (r.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "点巡检任务不存在");
    return r.getFirst();
  }

  boolean participant(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    return ids(s.get("owners")).contains(auth.userId())
        || ids(s.get("reviewers")).contains(auth.userId())
        || Objects.equals(nullable(row.get("owner_id")), auth.userId())
        || text(s, "personMode").equals("任何人可点检");
  }

  boolean access(Map<String, Object> row, String action) {
    if (action.equals("查看") && participant(row)) return true;
    try {
      return permissions
          .inspection(text(row, "kind"), action)
          .matches(
              nullable(row.get("company_id")),
              nullable(row.get("department_id")),
              nullable(row.get("owner_id")),
              auth.userId());
    } catch (ResponseStatusException ex) {
      return false;
    }
  }

  boolean execute(Map<String, Object> row) {
    var s = decode(row.get("snapshot"));
    return (text(s, "personMode").equals("任何人可点检") || ids(s.get("owners")).contains(auth.userId()))
        && (row.get("owner_id") == null || number(row.get("owner_id")) == auth.userId());
  }

  boolean review(Map<String, Object> row) {
    return ids(decode(row.get("snapshot")).get("reviewers")).contains(auth.userId())
        || access(row, "审核");
  }

  Map<String, Object> taskView(Map<String, Object> row) {
    var v = decode(row.get("snapshot"));
    v.put("companyId", Objects.toString(row.get("company_id"), ""));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("id", row.get("id").toString());
    v.put("code", row.get("code"));
    v.put("kind", row.get("kind"));
    v.put("status", row.get("status"));
    v.put("dueAt", dateTime(row.get("due_at")).toString());
    v.put("expiresAt", dateTime(row.get("expires_at")).toString());
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    v.put("rowVersion", row.get("row_version"));
    v.put("ownerId", Objects.toString(row.get("owner_id"), ""));
    v.put("ownerName", userName(number(row.get("tenant_id")), nullable(row.get("owner_id"))));
    var result =
        row.get("result") == null ? new LinkedHashMap<String, Object>() : decode(row.get("result"));
    v.put("result", result);
    v.put("canExecute", execute(row));
    v.put("canReview", review(row));
    v.put("canTransfer", access(row, "转派"));
    v.put("canVoid", access(row, "删除"));
    var items = rows(v.get("items"));
    var answers = rows(result.get("items"));
    v.put("expected", items.size());
    v.put(
        "checked",
        answers.stream()
            .filter(
                a ->
                    !yes(a.get("skipped"))
                        && (!text(a, "value").isBlank() || yes(a.get("implicitNormal"))))
            .count());
    v.put(
        "abnormal",
        answers.stream().filter(a -> !yes(a.get("skipped")) && yes(a.get("abnormal"))).count());
    v.put("skipped", strings(result.get("skippedEquipmentIds")).size());
    v.put("equipmentCount", rows(v.get("equipment")).size());
    v.put(
        "missing",
        items.stream()
            .filter(
                i -> !strings(result.get("skippedEquipmentIds")).contains(text(i, "equipmentId")))
            .filter(
                i ->
                    answers.stream()
                        .noneMatch(
                            a ->
                                text(a, "id").equals(text(i, "id"))
                                    && (!text(a, "value").isBlank()
                                        || yes(a.get("implicitNormal")))))
            .count());
    var deadline = dateTime(row.get("expires_at"));
    String finishedAt = text(result, "finishedAt");
    LocalDateTime completion = finishedAt.isBlank() ? now() : LocalDateTime.parse(finishedAt);
    v.put("overdue", completion.isAfter(deadline));
    v.put(
        "delayDays",
        Math.max(0, (Duration.between(deadline, completion).getSeconds() + 86399) / 86400));
    String start = text(result, "startedAt"), end = text(result, "finishedAt");
    v.put(
        "workSeconds",
        start.isBlank()
            ? 0
            : Math.max(
                0,
                Duration.between(
                        LocalDateTime.parse(start),
                        end.isBlank() ? now() : LocalDateTime.parse(end))
                    .getSeconds()));
    return v;
  }

  /** Navigation includes authorized empty organizations, plus paths to visible participant tasks. */
  public List<Map<String, Object>> taskOrganizations(
      String kind, List<Map<String, Object>> visibleTasks) {
    require(Set.of("daily", "periodic").contains(kind), "任务类型无效");
    var organizations = repository.listOrganizations(auth.tenantId());
    var byId = new HashMap<Long, Map<String, Object>>();
    for (var organization : organizations) byId.put(number(organization.get("id")), organization);
    var keep = new HashSet<Long>();
    try {
      var scope = permissions.inspection(kind, "查看");
      for (var organization : organizations) {
        var id = number(organization.get("id"));
        if (scope.matches(permissions.companyOfOrganization(id), id, null, auth.userId()))
          keep.add(id);
      }
      // Own-task scope has a meaningful navigation path even before the first task exists.
      if (scope.owner() && auth.user().getOrgId() != null) keep.add(auth.user().getOrgId());
    } catch (ResponseStatusException ignored) {
      // Participation may grant visibility to particular tasks without a broad menu data scope.
    }
    for (var task : visibleTasks) {
      for (var key : List.of("companyId", "departmentId")) {
        var value = Objects.toString(task.get(key), "");
        if (!value.isBlank()) keep.add(number(value));
      }
    }
    for (var id : new ArrayList<>(keep)) {
      var seen = new HashSet<Long>();
      var current = byId.get(id);
      while (current != null && seen.add(number(current.get("id")))) {
        keep.add(number(current.get("id")));
        var parent = Objects.toString(current.get("parentId"), "");
        current = parent.isBlank() ? null : byId.get(number(parent));
      }
    }
    return organizations.stream().filter(o -> keep.contains(number(o.get("id")))).toList();
  }

  public List<Map<String, Object>> tasks(String kind) {
    require(Set.of("daily", "periodic").contains(kind), "任务类型无效");
    return repository.listKindTasks27(auth.tenantId(), kind).stream()
        .filter(r -> access(r, "查看"))
        .map(this::taskView)
        .toList();
  }

  public List<Map<String, Object>> tasks() {
    return repository.listAllTasks(auth.tenantId()).stream()
        .filter(r -> access(r, "查看"))
        .map(this::taskView)
        .toList();
  }

  public Map<String, Object> task(long id) {
    var r = taskRaw(auth.tenantId(), id, false);
    if (!access(r, "查看")) deny();
    var v = taskView(r);
    v.put(
        "events",
        repository.listTaskEvents(auth.tenantId(), id).stream()
            .map(
                e -> {
                  e.put("details", decode(e.get("details")));
                  return e;
                })
            .toList());
    return v;
  }

  String userName(long tenant, Long id) {
    if (id == null) return "";
    var r = repository.findUserName(tenant, id);
    return r.isEmpty() ? "" : text(r.getFirst(), "display_name");
  }

  void event(long tenant, long task, String action, Long actor, String name, Object details) {
    repository.insertTaskEvent(
        IdWorker.getId(), tenant, task, actor, name, action, encode(details));
  }

  void closeTodos(long tenant, long task) {
    repository.completeTodos(tenant, task);
  }

  void notify(
      long tenant,
      long task,
      List<Long> users,
      String kind,
      String title,
      boolean todo,
      boolean once) {
    for (var user : users)
      if (repository.countNotice(tenant, task, user, kind, once) == 0)
        repository.insertInbox(IdWorker.getId(), tenant, task, user, kind, title, todo);
  }

  List<Long> effectiveOwners(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> detail,
      LocalDate date,
      String shiftId) {
    if (text(plan, "personMode").equals("任何人可点检"))
      return repository.listActiveUsers(tenant).stream().map(r -> number(r.get("id"))).toList();
    if (text(plan, "personMode").equals("按照排班表"))
      return rows(plan.get("roster")).stream()
          .filter(
              r -> text(r, "date").equals(date.toString()) && text(r, "shiftId").equals(shiftId))
          .flatMap(r -> ids(r.get("users")).stream())
          .distinct()
          .toList();
    return ids(detail.get("owners")).isEmpty()
        ? ids(plan.get("owners"))
        : ids(detail.get("owners"));
  }

  LocalDateTime advance(LocalDateTime anchor, long index, long period, String unit) {
    long n = Math.multiplyExact(index, period);
    return switch (unit) {
      case "日" -> anchor.plusDays(n);
      case "周" -> anchor.plusWeeks(n);
      case "月" -> anchor.plusMonths(n);
      case "年" -> anchor.plusYears(n);
      default -> throw new IllegalArgumentException("周期单位无效");
    };
  }

  boolean rest(Map<String, Object> plan, LocalDate date) {
    return strings(plan.get("holidays")).contains(date.toString())
        || ids(plan.get("restDays"))
            .contains(
                (long)
                    (text(plan, "restCycle").equals("按月")
                        ? date.getDayOfMonth()
                        : date.getDayOfWeek().getValue()));
  }

  int createTask(
      long tenant,
      Map<String, Object> plan,
      Map<String, Object> p,
      List<Map<String, Object>> details,
      LocalDateTime due,
      String shiftId,
      String shiftName,
      LocalDateTime shiftEnd) {
    String kind = text(plan, "kind").equals("daily-plans") ? "daily" : "periodic";
    long equipmentId = kind.equals("daily") ? number(details.getFirst().get("equipmentId")) : 0;
    long planId = number(plan.get("id"));
    if (repository.countDailyTask(tenant, planId, equipmentId, shiftId, due) > 0) return 0;
    var snap = new LinkedHashMap<String, Object>(p);
    snap.remove("details");
    snap.remove("roster");
    var equipmentList = new ArrayList<Map<String, Object>>();
    var items = new ArrayList<Map<String, Object>>();
    var owners = new LinkedHashSet<Long>();
    var reviewers = new LinkedHashSet<Long>(ids(p.get("reviewers")));
    for (var detail : details) {
      var eq = equipment(tenant, number(detail.get("equipmentId")));
      if (kind.equals("periodic")) owners.addAll(ids(p.get("owners")));
      else owners.addAll(effectiveOwners(tenant, p, detail, due.toLocalDate(), shiftId));
      reviewers.addAll(ids(detail.get("reviewers")));
      equipmentList.add(
          Map.of(
              "id",
              eq.get("id").toString(),
              "code",
              eq.get("equipment_code"),
              "name",
              eq.get("name"),
              "model",
              Objects.toString(eq.get("model"), ""),
              "department",
              Objects.toString(eq.get("department"), ""),
              "functionLocation",
              Objects.toString(eq.get("function_location"), "")));
      for (var project : projects(tenant, detail, false)) {
        var item = new LinkedHashMap<>(project);
        item.put("id", eq.get("id") + "/" + text(item, "id"));
        item.put("equipmentId", eq.get("id").toString());
        items.add(item);
      }
    }
    if (kind.equals("daily") && text(p, "personMode").equals("按照排班表") && owners.isEmpty()) {
      if (yes(p.get("skipUnstaffed"))) return 0;
      owners.addAll(ids(p.get("owners")));
    }
    var shuffled = new ArrayList<>(items);
    Collections.shuffle(shuffled);
    for (int i = 0; i < Math.min(number(p.get("randomPhotoCount")), shuffled.size()); i++)
      shuffled.get(i).put("photoRequired", true);
    snap.put("equipment", equipmentList);
    snap.put("items", items);
    snap.put("owners", owners.stream().map(Object::toString).toList());
    snap.put("reviewers", reviewers.stream().map(Object::toString).toList());
    snap.put("planName", plan.get("name"));
    snap.put("planCode", plan.get("code"));
    if (kind.equals("periodic"))
      snap.put("typeName", raw("types", tenant, number(p.get("typeId")), false).get("name"));
    snap.put("shiftName", shiftName);
    snap.put("shiftDate", due.toLocalDate().toString());
    snap.put(
        "equipmentName",
        equipmentList.stream()
            .map(e -> text(e, "name"))
            .collect(java.util.stream.Collectors.joining("、")));
    snap.put(
        "equipmentCode",
        equipmentList.stream()
            .map(e -> text(e, "code"))
            .collect(java.util.stream.Collectors.joining("、")));
    snap.put(
        "department",
        Objects.toString(repository.findOrganizationName(tenant, plan.get("department_id")), ""));
    snap.put("personMode", kind.equals("periodic") ? "指定负责人" : text(p, "personMode"));
    long task = IdWorker.getId();
    String code = nextCode(tenant, kind.equals("daily") ? "DJ" : "XJ");
    LocalDateTime expires = shiftDuration(due, p, "validHours", "validUnit", 1);
    if (yes(p.get("useShiftEnd")) && shiftEnd != null) expires = shiftEnd;
    String status = due.isAfter(now()) ? "未开始" : "待执行";
    var org = repository.findOrganizationParent(tenant, plan.get("department_id"));
    Long company = permissions.companyOfOrganization(tenant, nullable(plan.get("department_id")));
    repository.insertTask(
        task,
        tenant,
        kind,
        code,
        planId,
        equipmentId,
        shiftId,
        company,
        plan.get("department_id"),
        due,
        expires,
        status,
        encode(snap));
    event(
        tenant,
        task,
        "生成任务",
        null,
        "系统",
        Map.of("计划", plan.get("name"), "开始时间", due.toString(), "班次", shiftName));
    if (!due.isAfter(now()))
      notify(
          tenant,
          task,
          new ArrayList<>(owners),
          "点巡检待执行",
          text(snap, "equipmentName"),
          true,
          false);
    return 1;
  }

  @Transactional
  public int generate(long tenant, long planId) {
    var plans = repository.lockPlan(tenant, planId);
    if (plans.isEmpty()) return 0;
    var plan = plans.getFirst();
    var p = decode(plan.get("payload"));
    int count = 0;
    if (yes(p.get("enabled"))) {
      var schedule = repository.lockSchedule(tenant, planId);
      LocalDateTime next = dateTime(schedule.get("next_at")),
          anchor = dateTime(schedule.get("anchor_at"));
      long index = number(schedule.get("cycle_index"));
      LocalDate start = LocalDate.parse(text(p, "startDate")),
          end = LocalDate.parse(text(p, "endDate"));
      var details = rows(p.get("details"));
      if (text(plan, "kind").equals("periodic-plans")) {
        for (int n = 0;
            n < 100 && readyForGeneration(next, p) && !next.toLocalDate().isAfter(end);
            n++) {
          if (!next.toLocalDate().isBefore(start))
            count += createTask(tenant, plan, p, details, next, "", "", null);
          index++;
          next = advance(anchor, index, number(p.get("period")), text(p, "unit"));
        }
      } else {
        LocalDateTime deadline = generationDeadline(p);
        for (int n = 0;
            n < 100
                && !next.toLocalDate().isAfter(deadline.toLocalDate())
                && !next.toLocalDate().isAfter(end);
            n++) {
          LocalDate date = next.toLocalDate();
          boolean allStarted = true;
          if (!date.isBefore(start) && !rest(p, date)) {
            for (var shift : rows(p.get("shifts"))) {
              LocalDateTime due = date.atTime(LocalTime.parse(text(shift, "start"))),
                  shiftEnd = date.atTime(LocalTime.parse(text(shift, "end")));
              if (!shiftEnd.isAfter(due)) shiftEnd = shiftEnd.plusDays(1);
              if (!readyForGeneration(due, p)) {
                allStarted = false;
                continue;
              }
              for (var detail : details)
                count +=
                    createTask(
                        tenant,
                        plan,
                        p,
                        List.of(detail),
                        due,
                        text(shift, "id"),
                        text(shift, "name"),
                        shiftEnd);
            }
          }
          if (!allStarted) break;
          next = date.plusDays(1).atStartOfDay();
        }
      }
      repository.advanceSchedule(next, index, tenant, planId);
    }
    maintainTasks(tenant, planId);
    return count;
  }

  void maintainTasks(long tenant, long planId) {
    for (var task : repository.lockOpenPlanTasks(tenant, planId)) {
      long id = number(task.get("id"));
      var snap = decode(task.get("snapshot"));
      String state = text(task, "status");
      LocalDateTime due = dateTime(task.get("due_at")), expires = dateTime(task.get("expires_at"));
      if (state.equals("未开始") && !due.isAfter(now())) {
        repository.activateTask(tenant, id);
        state = "待执行";
        event(tenant, id, "任务到期开始", null, "系统", Map.of());
      }
      if (now().isAfter(expires) && Set.of("未开始", "待执行").contains(state)) {
        String next = yes(snap.get("allowLate")) ? "已过期" : "过期作废";
        repository.changeTaskStatus(next, tenant, id);
        event(tenant, id, next, null, "系统", Map.of());
        if (next.equals("过期作废")) closeTodos(tenant, id);
        state = next;
      }
      if (Set.of("待执行", "已过期").contains(state))
        notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
      if (state.equals("未开始")
          && number(snap.get("remindHours")) > 0
          && !now().isBefore(shiftDuration(due, snap, "remindHours", "remindUnit", -1)))
        notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检开始提醒",
            text(snap, "equipmentName"),
            false,
            true);
      if (Set.of("待执行", "执行中", "挂起", "已过期").contains(state)
          && number(snap.get("expireRemindHours")) > 0
          && !now()
              .isBefore(shiftDuration(expires, snap, "expireRemindHours", "expireRemindUnit", -1)))
        notify(
            tenant,
            id,
            ids(snap.get("owners")),
            "点巡检即将过期",
            text(snap, "equipmentName"),
            false,
            true);
    }
  }

  List<Map<String, Object>> checkedResults(
      long tenant, Map<String, Object> snap, Map<String, Object> body, boolean complete) {
    var checked = new ArrayList<Map<String, Object>>();
    var submitted = rows(body.get("items"));
    require(
        submitted.stream().map(i -> text(i, "id")).distinct().count() == submitted.size(),
        "检查结果不能重复");
    var known = rows(snap.get("items")).stream().map(i -> text(i, "id")).toList();
    require(submitted.stream().allMatch(i -> known.contains(text(i, "id"))), "存在不属于此任务的项目");
    var skipped = strings(body.get("skippedEquipmentIds"));
    require(
        rows(snap.get("equipment")).stream().map(e -> text(e, "id")).toList().containsAll(skipped),
        "跳过的设备不属于当前任务");
    require(skipped.isEmpty() || !text(body, "skipReason").isBlank(), "跳过设备时请填写原因");
    for (var item : rows(snap.get("items"))) {
      var answer =
          submitted.stream()
              .filter(a -> text(a, "id").equals(text(item, "id")))
              .findFirst()
              .orElse(Map.of());
      String value = text(answer, "value");
      boolean skip = skipped.contains(text(item, "equipmentId"));
      boolean implicit = complete && yes(snap.get("onlyAbnormal")) && value.isBlank() && !skip;
      require(
          !complete || skip || implicit || !yes(item.get("required")) || !value.isBlank(),
          "必填检查项目未完成：" + text(item, "name"));
      attachments(tenant, answer.get("images"), true);
      require(
          !complete
              || skip
              || !yes(item.get("photoRequired"))
              || !rows(answer.get("images")).isEmpty(),
          "请上传项目图片：" + text(item, "name"));
      boolean abnormal = false;
      if (!value.isBlank()) {
        if (text(item, "valueType").equals("选项")) {
          require(strings(item.get("options")).contains(value), "检查结果选项无效");
          abnormal = !strings(item.get("normalOptions")).contains(value);
        } else if (text(item, "valueType").equals("数值")) {
          BigDecimal n = new BigDecimal(value);
          abnormal =
              (!text(item, "lower").isBlank()
                      && n.compareTo(new BigDecimal(text(item, "lower"))) < 0)
                  || (!text(item, "upper").isBlank()
                      && n.compareTo(new BigDecimal(text(item, "upper"))) > 0);
        }
      }
      require(
          !complete
              || !abnormal
              || !yes(answer.get("resolved"))
              || !text(answer, "measure").isBlank(),
          "异常处理后请填写处理措施");
      var result = new LinkedHashMap<String, Object>(answer);
      result.put("id", item.get("id"));
      result.put(
          "value",
          implicit && text(item, "valueType").equals("选项")
              ? strings(item.get("normalOptions")).getFirst()
              : value);
      result.put("implicitNormal", implicit);
      result.put("abnormal", abnormal);
      result.put("skipped", skip);
      checked.add(result);
    }
    return checked;
  }

  void history(long tenant, Map<String, Object> task, Map<String, Object> result, String status) {
    var snap = decode(task.get("snapshot"));
    for (var eq : rows(snap.get("equipment"))) {
      if (strings(result.get("skippedEquipmentIds")).contains(text(eq, "id"))) continue;
      String summary =
          rows(result.get("items")).stream()
              .filter(r -> text(r, "id").startsWith(text(eq, "id") + "/"))
              .map(r -> text(r, "value"))
              .collect(java.util.stream.Collectors.joining("、"));
      if (repository.countEquipmentHistory(tenant, number(eq.get("id")), task.get("code")) == 0)
        repository.insertEquipmentHistory(
            IdWorker.getId(),
            tenant,
            number(eq.get("id")),
            task.get("code"),
            text(snap, "planName"),
            userName(tenant, nullable(task.get("owner_id"))),
            status,
            summary);
    }
  }

  @Transactional
  public Map<String, Object> action(long id, String action, Map<String, Object> body) {
    experienceInventoryMutex.enter();
    long tenant = auth.tenantId(), user = auth.userId();
    var row = taskRaw(tenant, id, true);
    if (!access(row, "查看")) deny();
    VersionGuard.checkPayload((Number) row.get("row_version"), body);
    var snap = decode(row.get("snapshot"));
    var result =
        row.get("result") == null ? new LinkedHashMap<String, Object>() : decode(row.get("result"));
    String state = text(row, "status"), next = state, label = "";
    Long owner = nullable(row.get("owner_id"));
    if (Set.of("start", "draft", "submit", "suspend", "resume", "scan").contains(action)
        && !execute(row)) deny();
    switch (action) {
      case "start" -> {
        require(Set.of("待执行", "未开始", "已过期", "审核不通过").contains(state), "此任务不能开始");
        require(!now().isBefore(dateTime(row.get("due_at"))), "尚未到任务开始时间");
        require(
            !now().isAfter(dateTime(row.get("expires_at"))) || yes(snap.get("allowLate")), "任务已过期");
        owner = user;
        next = "执行中";
        if (text(result, "startedAt").isBlank()) result.put("startedAt", now().toString());
        closeTodos(tenant, id);
        notify(tenant, id, List.of(user), "点巡检待处理", text(snap, "equipmentName"), true, false);
        label = "开始点巡检";
      }
      case "scan" -> {
        require(state.equals("执行中") && Objects.equals(owner, user), "请先开始执行任务");
        var eq =
            rows(snap.get("equipment")).stream()
                .filter(e -> text(e, "code").equals(text(body, "equipmentCode")))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("设备编号不属于当前任务"));
        var scanned = new ArrayList<>(strings(result.get("scannedEquipmentIds")));
        if (!scanned.contains(text(eq, "id"))) scanned.add(text(eq, "id"));
        result.put("scannedEquipmentIds", scanned);
        label = "校验设备";
      }
      case "draft", "submit" -> {
        require(state.equals("执行中") && Objects.equals(owner, user), "只能处理自己执行中的任务");
        boolean complete = action.equals("submit");
        var values = checkedResults(tenant, snap, body, complete);
        attachments(tenant, body.get("attachments"), false);
        if (complete && !text(snap, "scanMode").equals("无需扫码")) {
          var scanned = strings(result.get("scannedEquipmentIds"));
          var expected =
              rows(snap.get("equipment")).stream()
                  .map(e -> text(e, "id"))
                  .filter(e -> !strings(body.get("skippedEquipmentIds")).contains(e))
                  .toList();
          require(
              text(snap, "scanMode").equals("校验一台即可提交")
                  ? !scanned.isEmpty()
                  : scanned.containsAll(expected),
              "请完成设备编号校验");
        }
        result.put("items", values);
        result.put("description", text(body, "description"));
        result.put("attachments", rows(body.get("attachments")));
        result.put("skippedEquipmentIds", strings(body.get("skippedEquipmentIds")));
        result.put("skipReason", text(body, "skipReason"));
        label = complete ? "提交检查结果" : "保存检查结果";
        if (complete) {
          materialCustody.finishTask("点巡检", id, body);
          result.put("finishedAt", now().toString());
          closeTodos(tenant, id);
          next = text(snap, "reviewMode").equals("无需审核") ? "已完成" : "待审核";
          if (next.equals("已完成")) history(tenant, row, result, next);
          else
            notify(
                tenant,
                id,
                ids(snap.get("reviewers")),
                "点巡检待审核",
                text(snap, "equipmentName"),
                true,
                false);
        }
      }
      case "review" -> {
        require(state.equals("待审核"), "任务不在待审核状态");
        if (!review(row)) deny();
        require(Set.of("通过", "打回").contains(text(body, "decision")), "请选择审核结果");
        attachments(tenant, body.get("attachments"), false);
        boolean pass = text(body, "decision").equals("通过");
        if (text(snap, "reviewMode").equals("逐项审核")) {
          var reviews = rows(body.get("reviewItems"));
          var expected =
              rows(snap.get("items")).stream()
                  .filter(
                      i ->
                          !strings(result.get("skippedEquipmentIds"))
                              .contains(text(i, "equipmentId")))
                  .map(i -> text(i, "id"))
                  .toList();
          require(
              reviews.stream().map(i -> text(i, "id")).distinct().count() == reviews.size(),
              "审核项目不能重复");
          require(reviews.stream().allMatch(i -> expected.contains(text(i, "id"))), "存在不属于本次审核的项目");
          var checkedReviews = new ArrayList<Map<String, Object>>();
          for (var item : rows(snap.get("items"))) {
            if (strings(result.get("skippedEquipmentIds")).contains(text(item, "equipmentId")))
              continue;
            var r =
                reviews.stream()
                    .filter(i -> text(i, "id").equals(text(item, "id")))
                    .findFirst()
                    .orElse(Map.of());
            require(Set.of("通过", "打回").contains(text(r, "decision")), "请审核所有检查项目");
            if (text(r, "decision").equals("打回")) pass = false;
            checkedReviews.add(
                Map.of(
                    "id", text(item, "id"),
                    "decision", text(r, "decision"),
                    "description", text(r, "description")));
          }
          result.put("reviewItems", checkedReviews);
        }
        require(pass || !text(body, "description").isBlank(), "打回时请填写说明");
        result.put(
            "review",
            Map.of(
                "decision",
                pass ? "通过" : "打回",
                "description",
                text(body, "description"),
                "attachments",
                rows(body.get("attachments")),
                "reviewer",
                auth.user().getDisplayName(),
                "reviewedAt",
                now().toString()));
        next = pass ? "已审核" : "审核不通过";
        closeTodos(tenant, id);
        if (pass) history(tenant, row, result, next);
        notify(
            tenant,
            id,
            owner == null ? ids(snap.get("owners")) : List.of(owner),
            pass ? "点巡检审核通过" : "点巡检待修改",
            text(snap, "equipmentName"),
            !pass,
            false);
        label = pass ? "审核通过" : "审核打回";
      }
      case "suspend" -> {
        require(state.equals("执行中") && Objects.equals(owner, user), "仅自己的执行中任务可挂起");
        require(!text(body, "description").isBlank(), "请填写挂起原因");
        next = "挂起";
        closeTodos(tenant, id);
        label = "挂起";
      }
      case "resume" -> {
        require(state.equals("挂起") && Objects.equals(owner, user), "仅自己的挂起任务可恢复");
        next = "执行中";
        notify(tenant, id, List.of(user), "点巡检待处理", text(snap, "equipmentName"), true, false);
        label = "恢复";
      }
      case "transfer" -> {
        if (!access(row, "转派")) deny();
        require(Set.of("未开始", "待执行", "执行中", "挂起", "已过期", "审核不通过").contains(state), "当前任务不能转派");
        users(tenant, body.get("owners"), true);
        snap.put("owners", strings(body.get("owners")));
        snap.put("personMode", "指定负责人");
        owner = null;
        next = now().isBefore(dateTime(row.get("due_at"))) ? "未开始" : "待执行";
        closeTodos(tenant, id);
        notify(
            tenant,
            id,
            ids(body.get("owners")),
            "点巡检待执行",
            text(snap, "equipmentName"),
            true,
            false);
        label = "转派";
      }
      case "void", "delete" -> {
        if (!access(row, "删除")) deny();
        require(
            !Set.of("已审核", "已完成", "已作废", "过期作废").contains(state) || action.equals("delete"),
            "当前任务不能作废");
        require(!text(body, "description").isBlank(), "请填写原因");
        next = "已作废";
        closeTodos(tenant, id);
        label = action.equals("delete") ? "删除任务" : "作废";
      }
      default -> throw new IllegalArgumentException("未知任务操作");
    }
    repository.updateTask(
        next, owner, encode(snap), encode(result), action.equals("delete") ? 1 : 0, tenant, id);
    var details = new LinkedHashMap<String, Object>();
    details.put("说明", text(body, "description"));
    if (action.equals("scan")) details.put("设备编号", text(body, "equipmentCode"));
    if (action.equals("transfer"))
      details.put("负责人", ids(body.get("owners")).stream().map(u -> userName(tenant, u)).toList());
    event(tenant, id, label, user, auth.user().getDisplayName(), details);
    if (action.equals("submit"))
      experiencePublisher.publishEvent(
          new com.windasoft.eam.event.experience.ExperienceEvents.TaskResult("点巡检", id));
    return action.equals("delete") ? Map.of("deleted", true) : task(id);
  }

  @Transactional
  public void batchReview(List<Map<String, Object>> tasks) {
    require(!tasks.isEmpty() && tasks.size() <= 100, "请选择1至100条任务");
    tasks.stream()
        .sorted(Comparator.comparingLong(t -> number(t.get("id"))))
        .forEach(t -> action(number(t.get("id")), "review", t));
  }

  public List<Map<String, Object>> generationTargets() {
    return repository.generationTargets();
  }
}
