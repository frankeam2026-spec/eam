package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.common.DefinitionNumberService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class InspectionService {
  private final NotificationPolicyService reminderPolicies;
  private final DefinitionNumberService definitionNumbers;

  private final com.windasoft.eam.service.business.BusinessValueService custom;

  private final com.windasoft.eam.service.basedata.BaseDataReferenceService references;

  private final DailyArrangementService dailyArrangements;
  private final DailyArrangementRepository dailyStorage;

  private final InspectionViewAssembler views;

  private final InspectionProjectService projectSource;

  private final InspectionPlanScheduleService planSchedules;

  private final InspectionValidationService validation;

  private final InspectionNumbers numbers;
  private final InspectionListService listQueries;
  private final InspectionRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private static final Set<String> KINDS =
      Set.of("types", "standards", "daily-plans", "periodic-plans");

  public InspectionService(
      InspectionRepository repository,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      com.windasoft.eam.service.basedata.BaseDataReferenceService references,
      com.windasoft.eam.service.business.BusinessValueService custom,
      InspectionViewAssembler views,
      InspectionProjectService projectSource,
      InspectionPlanScheduleService planSchedules,
      InspectionValidationService validation,
      InspectionNumbers numbers,
      InspectionListService listQueries,
      NotificationPolicyService reminderPolicies,
      DefinitionNumberService definitionNumbers,
      DailyArrangementService dailyArrangements,
      DailyArrangementRepository dailyStorage) {
    this.custom = custom;
    this.views = views;
    this.projectSource = projectSource;
    this.planSchedules = planSchedules;
    this.validation = validation;
    this.numbers = numbers;
    this.listQueries = listQueries;
    this.reminderPolicies = reminderPolicies;
    this.definitionNumbers = definitionNumbers;
    this.dailyArrangements = dailyArrangements;
    this.dailyStorage = dailyStorage;

    this.references = references;
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  @SuppressWarnings("unchecked")
  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }

  Map<String, Object> raw(String kind, long tenant, long id, boolean lock) {
    return projectSource.raw(kind, tenant, id, lock);
  }

  boolean scoped(Map<String, Object> row, String action) {
    return projectSource.scoped(row, action);
  }

  Map<String, Object> view(Map<String, Object> row) {
    return views.definition(row);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> definitions(String kind) {
    require(
        KINDS.contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.system.message0046"));
    permissions.require(PermissionService.inspectionMenu(kind), "查看");
    if (kind.equals("types"))
      return repository.listDefinitions(auth.tenantId(), kind).stream()
          .map(this::view)
          .sorted(
              Comparator.<Map<String, Object>>comparingLong(row -> number(row.get("sortOrder")))
                  .thenComparing(row -> text(row, "name"))
                  .thenComparingLong(row -> number(row.get("id"))))
          .toList();
    return definitionBatches(kind);
  }

  /**
   * Legacy selectors still request all options; SQL scope and extension hydration run per batch.
   */
  private List<Map<String, Object>> definitionBatches(String kind) {
    var result = new ArrayList<Map<String, Object>>();
    var query = com.windasoft.eam.dto.common.ListQuery.from(Map.of());
    for (int page = 1; ; page++) {
      var batch = listQueries.query(kind, query.at(page, 200));
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) return result;
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportRows(String kind, Object selectedIds) {
    permissions.require(PermissionService.inspectionMenu(kind), "导出");
    var selected = new HashSet<>(ids(selectedIds));
    require(
        selected.size() <= 10000,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0043"));
    if (selected.isEmpty()) return List.of();
    if (kind.equals("types"))
      return definitions(kind).stream()
          .filter(row -> selected.contains(number(row.get("id"))))
          .toList();
    var result = new ArrayList<Map<String, Object>>();
    var query = com.windasoft.eam.dto.common.ListQuery.from(Map.of("size", "200"));
    for (int page = 1; ; page++) {
      var batch = listQueries.query(kind, query.at(page, 200), true, selected);
      if (batch.page().total() != selected.size()) deny();
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) return result;
    }
  }

  public Map<String, Object> definition(String kind, long id) {
    var r = raw(kind, auth.tenantId(), id, false);
    if (kind.equals("types")) permissions.require(PermissionService.inspectionMenu(kind), "查看");
    else if (!scoped(r, "查看")) deny();
    var result = view(r);
    if (kind.equals("daily-plans")) {
      result.put("arrangements", dailyStorage.load(auth.tenantId(), id));
      result.put("workdays", dailyStorage.workdays(auth.tenantId(), id));
    }
    if (kind.endsWith("plans"))
      custom.attach(com.windasoft.eam.service.business.BusinessObjects.inspection(kind), result);
    if (kind.endsWith("plans")) reminderPolicies.attachBinding(auth.tenantId(), kind.equals("daily-plans") ? "daily" : "periodic", id, result);
    return result;
  }

  @Transactional
  public Map<String, Object> save(String kind, Long id, Map<String, Object> body) {
    require(
        KINDS.contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.system.message0046"));
    String action = id == null ? "新增" : "编辑";
    permissions.require(PermissionService.inspectionMenu(kind), action);
    long tenant = auth.tenantId();
    var existing = id == null ? null : raw(kind, tenant, id, true);
    if (existing != null) {
      if (!kind.equals("types") && !scoped(existing, action)) deny();
      VersionGuard.checkPayload((Number) existing.get("row_version"), body);
    }
    var data = decode(encode(body));
    require(
        !text(data, "name").isBlank() && text(data, "name").length() <= 150,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0050"));
    long department = number(data.get("departmentId"));
    if (!kind.equals("types")) {
      var org = repository.findDepartmentParent(tenant, department);
      require(
          !org.isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0051"));
      var s = permissions.inspection(kind, action);
      if (!s.matches(
          permissions.companyOfOrganization(department), department, auth.userId(), auth.userId()))
        deny();
    }
    if (kind.equals("types")) {
      Object sort = data.getOrDefault("sortOrder", 0);
      require(
          sort instanceof Integer || sort instanceof Long,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0052"));
      long sortOrder = ((Number) sort).longValue();
      require(
          sortOrder >= 0 && sortOrder <= 999999,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0053"));
      data.put("sortOrder", sortOrder);
      range(data, "period", 1, 10000);
      require(
          Set.of("日", "周", "月", "年").contains(text(data, "unit")),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0008"));
      if (existing != null) {
        var before = decode(existing.get("payload"));
        if (number(before.get("period")) != number(data.get("period"))
            || !text(before, "unit").equals(text(data, "unit")))
          for (var plan : repository.listOtherDefinitionPayloads(tenant, id)) {
            var payload = decode(plan.get("payload"));
            if (text(plan, "kind").equals("periodic-plans"))
              require(
                  !text(payload, "typeId").equals(id.toString()),
                  com.windasoft.eam.common.MessageContext.text("server.inspection.message0054"));
          }
      }
    }
    if (kind.equals("standards")) {
      references.categories(tenant, data);
      validation.validateItems(tenant, rows(data.get("items")));
      for (var eq : ids(data.get("equipmentIds")))
        validation.checkEquipment(projectSource.equipment(tenant, eq), kind, action);
    }
    if (kind.endsWith("plans")) {
      require(
          Set.of("无需验收", "整机验收", "逐项审核").contains(text(data, "reviewMode")),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0055"));
      validation.users(tenant, data.get("owners"), !kind.equals("daily-plans"));
      validation.users(tenant, data.get("reviewers"), !text(data, "reviewMode").equals("无需验收"));
      if (kind.equals("periodic-plans")) duration(data, "validHours", "validUnit", 1, 10);
      duration(data, "advanceHours", "advanceUnit", 0, 1);
      duration(data, "remindHours", "remindUnit", 0, 1);
      duration(data, "expireRemindHours", "expireRemindUnit", 0, 1);
      range(data, "randomPhotoCount", 0, 1000);
      require(
          Set.of("无需扫码", "逐台校验", "校验一台即可提交").contains(text(data, "scanMode")),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0056"));
      boolean periodic = kind.equals("periodic-plans");
      LocalDateTime start = periodic
          ? periodicBoundary(data.get("startDate"), false)
          : LocalDate.parse(text(data, "startDate")).atStartOfDay();
      LocalDateTime end = periodic
          ? periodicBoundary(data.get("endDate"), true)
          : LocalDate.parse(text(data, "endDate")).atStartOfDay();
      if (periodic) {
        var seconds = java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss");
        data.put("startDate", start.format(seconds));
        data.put("endDate", end.format(seconds));
      }
      require(
          !end.isBefore(start),
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0057"));
      require(
          !rows(data.get("details")).isEmpty() && rows(data.get("details")).size() <= 500,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0058"));
      var equipmentIds = new HashSet<Long>();
      for (var detail : rows(data.get("details"))) {
        long eq = number(detail.get("equipmentId"));
        require(
            equipmentIds.add(eq),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0059"));
        validation.checkEquipment(projectSource.equipment(tenant, eq), kind, action);
        projectSource.projects(tenant, detail, true);
        validation.users(tenant, detail.get("owners"), false);
        validation.users(tenant, detail.get("reviewers"), false);
      }
      if (kind.equals("periodic-plans")) {
        var selectedType = validation.cycleType(tenant, data.get("typeId"));
        data.put("typeId", selectedType.get("id"));
        data.put("typeName", selectedType.get("name"));
        if (!data.containsKey("period")) data.put("period", selectedType.get("period"));
        if (!data.containsKey("unit")) data.put("unit", selectedType.get("unit"));
        require(
            data.get("period") instanceof Number value
                && value.doubleValue() == Math.rint(value.doubleValue()),
            com.windasoft.eam.common.MessageContext.text("server.inspection.message0017", "period"));
        range(data, "period", 1, 10000);
        require(
            Set.of("日", "周", "月", "年").contains(text(data, "unit")),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0008"));
        // Initial inspection follows plan validity; legacy clients cannot choose a separate anchor.
        data.put("nextAt", start.toString());
      } else {
        // Daily frequency belongs to each arrangement, not to a cycle dictionary.
        data.put("typeId", null);
        data.remove("typeName");
        dailyArrangements.validate(tenant, id, data);
        data.put(
            "shifts",
            rows(data.get("arrangements")).stream()
                .filter(a -> text(a, "frequency").equals("shift"))
                .flatMap(a -> rows(a.get("slots")).stream())
                .toList());
        require(
            Set.of("按周", "按月").contains(text(data, "restCycle")),
            com.windasoft.eam.common.MessageContext.text("daily.error.restRule"));
        for (long day : data.containsKey("weeklyRestDays") || data.containsKey("monthlyRestDays")
            ? List.<Long>of() : ids(data.get("restDays")))
          require(
              day >= 1 && day <= (text(data, "restCycle").equals("按月") ? 31 : 7),
              com.windasoft.eam.common.MessageContext.text("daily.error.restDay"));
        for (String day : strings(data.get("holidays"))) LocalDate.parse(day);
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
      code =
          kind.equals("standards")
              ? definitionNumbers.next(tenant, "IS")
              : numbers.nextCode(tenant, prefix);
    }
    require(
        code.length() <= 64,
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0066"));
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
    if (kind.equals("daily-plans"))
      dailyStorage.save(tenant, key, rows(data.get("arrangements")), data.get("workdays"));
    if (kind.endsWith("plans")) {
      planSchedules.maintain(kind, tenant, key, data, existing, auth.userId());
    }
    if (kind.endsWith("plans"))
      custom.save(
          com.windasoft.eam.service.business.BusinessObjects.inspection(kind),
          key,
          body,
          id == null);
    if (kind.endsWith("plans")) reminderPolicies.saveBinding(tenant, kind.equals("daily-plans") ? "daily" : "periodic", key, body);
    return definition(kind, key);
  }

  @Transactional
  public void delete(String kind, long id) {
    var row = raw(kind, auth.tenantId(), id, true);
    permissions.require(PermissionService.inspectionMenu(kind), "删除");
    if (!kind.equals("types") && !scoped(row, "删除")) deny();
    if (kind.equals("types"))
      require(
          repository.taskTypeReferences(auth.tenantId(), id) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0068"));
    if (kind.endsWith("plans"))
      custom.remove(com.windasoft.eam.service.business.BusinessObjects.inspection(kind), id);
    if (kind.endsWith("plans"))
      require(
          (kind.equals("daily-plans")
                  ? repository.countUndeletedPlanTasks(auth.tenantId(), id)
                  : repository.countPlanTasks(auth.tenantId(), id)) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0069"));
    else
      for (var plan : repository.listOtherDefinitionPayloads(auth.tenantId(), id)) {
        var p = decode(plan.get("payload"));
        if (kind.equals("standards"))
          for (var d : rows(p.get("details")))
            require(
                rows(d.get("projectRefs")).stream()
                    .noneMatch(ref -> number(ref.get("standardId")) == id),
                com.windasoft.eam.common.MessageContext.text("server.inspection.message0070"));
        else {
          require(
              text(plan, "kind").equals("daily-plans") || number(p.get("typeId")) != id,
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0071"));
          for (var i : rows(p.get("items")))
            require(
                number(i.get("typeId")) != id,
                com.windasoft.eam.common.MessageContext.text("server.inspection.message0072"));
        }
      }
    if (kind.equals("daily-plans")) dailyStorage.save(auth.tenantId(), id, List.of(), List.of());
    require(
        !kind.equals("daily-plans") || dailyStorage.exemptions(auth.tenantId(), id).isEmpty(),
        com.windasoft.eam.common.MessageContext.text("daily.error.exemptPlanDelete"));
    repository.deletePlanSchedules(auth.tenantId(), id);
    repository.deleteDefinition(auth.tenantId(), id);
  }

  public Map<String, Object> choices() {
    return choices(false);
  }

  public Map<String, Object> choices(boolean paged) {
    require(
        permissions.anyInspection("查看"),
        com.windasoft.eam.common.MessageContext.text("server.inspection.message0073"));
    var equipment =
        paged
            ? List.of()
            : repository.listEquipment(auth.tenantId()).stream()
                .filter(
                    e -> {
                      try {
                        return permissions.inspectionEquipmentVisible(
                          nullable(e.get("company_org_id")),
                          nullable(e.get("department_org_id")),
                          nullable(e.get("owner_user_id")));
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
                            "categoryId",
                            Objects.toString(e.get("category_id"), ""),
                            "department",
                            Objects.toString(e.get("department"), ""),
                            "functionLocation",
                            Objects.toString(e.get("function_location"), "")))
                .toList();
    var orgs = repository.listOrganizations(auth.tenantId());
    var people = paged ? List.of() : repository.listUsers(auth.tenantId());
    return Map.of(
        "equipment",
        equipment,
        "organizations",
        orgs,
        "users",
        people,
        "types",
        repository.listDefinitions(auth.tenantId(), "types").stream()
            .map(this::view)
            .sorted(Comparator.comparingLong(t -> number(t.get("sortOrder"))))
            .toList(),
        "categories",
        repository.listCategories(auth.tenantId()));
  }

}
