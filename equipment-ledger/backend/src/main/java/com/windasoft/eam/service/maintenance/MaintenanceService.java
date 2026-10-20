package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceService {
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.common.DefinitionNumberService definitionNumbers;

  private final MaintenanceViewAssembler assembler;

  private final com.windasoft.eam.service.business.BusinessValueService custom;

  private final com.windasoft.eam.service.basedata.BaseDataReferenceService references;

  private final MaintenanceProjectService projects;

  private final MaintenanceGenerationService generation;

  private final MaintenanceValidationService validation;

  private final MaintenanceTaskService taskCommands;

  private final MaintenanceListService listQueries;

  private final MaintenanceRepository repository;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private static final Set<String> KINDS = Set.of("types", "standards", "plans");

  public MaintenanceService(
      MaintenanceRepository repository,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      com.windasoft.eam.service.basedata.BaseDataReferenceService references,
      MaintenanceViewAssembler assembler,
      com.windasoft.eam.service.business.BusinessValueService custom,
      MaintenanceProjectService projects,
      MaintenanceGenerationService generation,
      MaintenanceValidationService validation,
      MaintenanceTaskService taskCommands,
      MaintenanceListService listQueries) {
    this.assembler = assembler;
    this.custom = custom;
    this.projects = projects;
    this.generation = generation;
    this.validation = validation;
    this.taskCommands = taskCommands;
    this.listQueries = listQueries;

    this.references = references;
    this.repository = repository;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

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
          "server.maintenance.message0009");
    }
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> raw(String kind, long tenant, long id, boolean lock) {
    return projects.raw(kind, tenant, id, lock);
  }

  Map<String, Object> cycleType(long tenant, Object id) {
    var type = view(raw("types", tenant, number(id), true));
    require(
        yes(type.get("enabled")),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0018"));
    return type;
  }

  boolean visible(Map<String, Object> row, String action) {
    return projects.visible(row, action);
  }

  Map<String, Object> view(Map<String, Object> row) {
    return assembler.definition(row);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> definitions(String kind) {
    require(
        KINDS.contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.system.message0045"));
    permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    if (kind.equals("types"))
      return repository.listDefinitions(auth.tenantId(), kind).stream().map(this::view).toList();
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

  public List<Map<String, Object>> exportRows(String kind) {
    require(
        kind.equals("types"),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0019"));
    permissions.require(PermissionService.maintenanceMenu(kind), "导出");
    return definitions(kind);
  }

  public Map<String, Object> definition(String kind, long id) {
    var row = raw(kind, auth.tenantId(), id, false);
    if (kind.equals("types")) permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    else if (!visible(row, "查看")) deny();
    var v = view(row);
    if (kind.equals("plans")) custom.attach("maintenance_plan", v);
    if (kind.equals("plans"))
      v.put(
          "details",
          repository.listPlanSchedules(auth.tenantId(), id).stream()
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
    return projects.planProjects(detail, tenant, eq, checkAccess);
  }

  void validUsers(Object value, boolean required) {
    validation.validUsers(value, required);
  }

  void validAttachments(Object value, boolean imagesOnly) {
    validation.validAttachments(value, imagesOnly);
  }

  Map<String, Object> equipment(long tenant, long id) {
    return projects.equipment(tenant, id);
  }

  boolean applicable(Map<String, Object> standard, Map<String, Object> equipment, long tenant) {
    return projects.applicable(standard, equipment, tenant);
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
    require(
        KINDS.contains(kind),
        com.windasoft.eam.common.MessageContext.text("server.system.message0045"));
    String action = id == null ? "新增" : "编辑";
    permissions.require(PermissionService.maintenanceMenu(kind), action);
    long tenant = auth.tenantId();
    Map<String, Object> existing = id == null ? null : raw(kind, tenant, id, true);
    if (existing != null) {
      if (!kind.equals("types") && !visible(existing, action)) deny();
      VersionGuard.checkPayload((Number) existing.get("row_version"), body);
    }
    require(
        !text(body, "name").isBlank() && text(body, "name").length() <= 150,
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0020"));
    var data = new LinkedHashMap<>(body);
    data.remove("customFields");
    data.remove("customSchemaVersion");
    data.remove("customFieldLabels");
    data.remove("id");
    data.remove("rowVersion");
    data.remove("createdAt");
    data.remove("creatorId");
    long department = number(data.get("departmentId"));
    if (!kind.equals("types")) {
      require(
          department > 0, com.windasoft.eam.common.MessageContext.text("ui.equipment.text0099"));
      require(
          repository.countDepartment(tenant, department) > 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0021"));
      var scope = permissions.maintenance(kind, action);
      if (!scope.matches(null, department, auth.userId(), auth.userId())) deny();
    }
    if (kind.equals("types")) {
      require(
          number(data.get("period")) > 0 && number(data.get("period")) <= 10000,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0022"));
      require(
          Set.of("日", "周", "月", "年").contains(text(data, "unit")),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0008"));
      if (existing != null) {
        var before = decode(existing.get("payload"));
        if (number(before.get("period")) != number(data.get("period"))
            || !text(before, "unit").equals(text(data, "unit")))
          require(
              repository.cyclePlanTypeReferences(tenant, id) == 0,
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0054"));
      }
      data.put("system", existing != null && yes(decode(existing.get("payload")).get("system")));
    }
    if (kind.equals("standards")) {
      references.categories(tenant, data);
      require(
          !rows(data.get("items")).isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0023"));
      var itemIds = new HashSet<String>();
      for (var item : rows(data.get("items"))) {
        require(
            !text(item, "name").isBlank() && !text(item, "content").isBlank(),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0024"));
        var itemType = cycleType(tenant, item.get("typeId"));
        item.put("typeId", itemType.get("id"));
        item.put("typeName", itemType.get("name"));
        require(
            Set.of("选项", "数值", "文本").contains(text(item, "valueType")),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0025"));
        if (text(item, "id").isEmpty()) item.put("id", Long.toString(IdWorker.getId()));
        if (text(item, "code").isEmpty()) item.put("code", "MI-" + IdWorker.getId());
        require(
            itemIds.add(text(item, "id")),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0026"));
        if (text(item, "valueType").equals("数值")) {
          if (!text(item, "lower").isEmpty()) new BigDecimal(text(item, "lower"));
          if (!text(item, "upper").isEmpty()) new BigDecimal(text(item, "upper"));
        }
        if (text(item, "valueType").equals("选项")) {
          var choices = strings(item.get("options"));
          require(
              choices.size() >= 2,
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0027"));
          require(
              choices.containsAll(strings(item.get("normalOptions")))
                  && !strings(item.get("normalOptions")).isEmpty(),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0028"));
        }
        if (text(item, "valueType").equals("数值")
            && !text(item, "lower").isEmpty()
            && !text(item, "upper").isEmpty())
          require(
              new BigDecimal(text(item, "lower")).compareTo(new BigDecimal(text(item, "upper")))
                  <= 0,
              com.windasoft.eam.common.MessageContext.text("server.inspection.message0049"));
        validAttachments(item.get("references"), false);
        var spareSummary = new ArrayList<String>();
        for (var spare : rows(item.get("spareRequirements"))) {
          var stock = repository.loadLegacyStock(tenant, number(spare.get("stockId")));
          require(
              !stock.isEmpty(),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0029"));
          var scope = permissions.maintenance(kind, action);
          if (!scope.all()
              && !scope.companyIds().contains(number(stock.getFirst().get("company_id")))) deny();
          var quantity = new BigDecimal(text(spare, "quantity"));
          require(
              quantity.signum() > 0,
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0030"));
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
      boolean needsAcceptance = !text(data, "reviewMode").equals("无需验收");
      data.put("reviewMode", needsAcceptance ? "整机验收" : "无需验收");
      data.put("reviewRule", "");
      if (!needsAcceptance) data.put("reviewers", List.of());
      if (needsAcceptance && ids(data.get("reviewers")).isEmpty())
        throw new com.windasoft.eam.common.FieldValidationException("reviewers", "请选择验收人");
      validUsers(data.get("reviewers"), needsAcceptance);
      require(
          !rows(data.get("details")).isEmpty(),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0031"));
      require(
          Set.of("固定间隔", "完成时间").contains(text(data, "calculation")),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0032"));
      require(
          number(data.get("advanceDays")) >= 0 && number(data.get("advanceDays")) <= 365,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0033"));
      require(
          number(data.get("remindDays")) >= 0 && number(data.get("remindDays")) <= 365,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0034"));
      require(
          number(data.get("advanceDays")) >= number(data.get("remindDays")),
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0035"));
      for (var d : rows(data.get("details"))) {
        checkEquipment(equipment(tenant, number(d.get("equipmentId"))), kind, action);
        var type = cycleType(tenant, d.get("typeId"));
        d.put("typeId", text(type, "id"));
        d.put("typeName", text(type, "name"));
        if (!d.containsKey("period")) d.put("period", type.get("period"));
        if (!d.containsKey("unit")) d.put("unit", type.get("unit"));
        var selectedProjects =
            planProjects(d, tenant, equipment(tenant, number(d.get("equipmentId"))), true);
        require(
            !selectedProjects.isEmpty(),
            com.windasoft.eam.common.MessageContext.text("ui.equipment.text0753"));
        if (d.containsKey("projectRefs"))
          d.put("standardId", text(rows(d.get("projectRefs")).getFirst(), "standardId"));
        LocalDate.parse(text(d, "nextDate"));
        require(
            new BigDecimal(Objects.toString(d.get("period"), "0")).stripTrailingZeros().scale() <= 0
                && number(d.get("period")) > 0
                && number(d.get("period")) <= 10000,
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0036"));
        require(
            Set.of("日", "周", "月", "年").contains(text(d, "unit")),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0008"));
        Object duration =
            d.containsKey("validDuration") ? d.get("validDuration") : d.get("validDays");
        String durationUnit = d.containsKey("validUnit") ? text(d, "validUnit") : "日";
        String durationField = "details." + rows(data.get("details")).indexOf(d) + ".validDuration";
        if (duration == null
            || new BigDecimal(duration.toString()).stripTrailingZeros().scale() > 0
            || number(duration) < 0
            || number(duration) > 36500
            || !Set.of("日", "周", "月").contains(durationUnit))
          throw new com.windasoft.eam.common.FieldValidationException(
              durationField, "请填写有效时长整数并选择天、周或月");
        LocalDate startDate = LocalDate.parse(text(d, "nextDate"));
        long days =
            java.time.temporal.ChronoUnit.DAYS.between(
                startDate, advance(startDate, number(duration), durationUnit));
        if (days > 36500)
          throw new com.windasoft.eam.common.FieldValidationException(
              durationField, "有效时长不能超过36500天");
        d.put("validDuration", number(duration));
        d.put("validUnit", durationUnit);
        d.put("validDays", days);
        validUsers(d.get("owners"), false);
        if (!needsAcceptance) d.put("reviewers", List.of());
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
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0037"));
      }
    }
    long key = id == null ? IdWorker.getId() : id;
    String code = text(data, "code");
    if (code.isEmpty()) {
      code =
          kind.equals("types")
              ? "MT-" + IdWorker.getId()
              : definitionNumbers.next(tenant, kind.equals("standards") ? "MS" : "MP");
    }
    require(
        code.length() <= 64,
        com.windasoft.eam.common.MessageContext.text("server.inventory.message0191"));
    data.put("code", code);
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
          code, text(data, "name"), department == 0 ? null : department, encode(data), key, tenant);
    if (kind.equals("plans")) {
      var keep = new ArrayList<Long>();
      for (var d : rows(data.get("details"))) {
        long sid = number(d.get("id"));
        var date = LocalDate.parse(text(d, "nextDate"));
        if (sid > 0) {
          var old = repository.lockSchedule(sid, tenant, key);
          require(
              !old.isEmpty(),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0038"));
          require(
              number(old.getFirst().get("equipment_id")) == number(d.get("equipmentId")),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0039"));
          var last = repository.findLatestDueDate(tenant, sid);
          require(
              last.isEmpty()
                  || date.equals(((java.sql.Date) old.getFirst().get("next_date")).toLocalDate())
                  || date.isAfter(((java.sql.Date) last.getFirst().get("due_date")).toLocalDate()),
              com.windasoft.eam.common.MessageContext.text("server.maintenance.message0040"));
          var oldCycle = decode(old.getFirst().get("payload"));
          boolean keepAnchor =
              date.equals(((java.sql.Date) old.getFirst().get("next_date")).toLocalDate())
                  && number(d.get("period")) == number(oldCycle.get("period"))
                  && text(d, "unit").equals(text(oldCycle, "unit"));
          repository.updateSchedule(
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
          repository.insertSchedule(
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
      for (var r : repository.listScheduleIds(tenant, key))
        if (!keep.contains(number(r.get("id")))) repository.disableSchedule(tenant, r.get("id"));
      // Save and generation share this transaction, so due tasks exist when saving succeeds.
      generate(tenant, key);
    }
    if (kind.equals("plans")) custom.save("maintenance_plan", key, body, id == null);
    return definition(kind, key);
  }

  @Transactional
  public void delete(String kind, long id) {
    var r = raw(kind, auth.tenantId(), id, true);
    permissions.require(PermissionService.maintenanceMenu(kind), "删除");
    if (!kind.equals("types") && !visible(r, "删除")) deny();
    if (kind.equals("plans")) custom.remove("maintenance_plan", id);
    var payload = decode(r.get("payload"));
    require(
        !yes(payload.get("system")),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0041"));
    if (kind.equals("plans")) {
      repository.disablePlanSchedules(auth.tenantId(), id);
      require(
          repository.countPlanTasks(auth.tenantId(), id) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0069"));
    }
    if (!kind.equals("plans")) {
      String column = kind.equals("types") ? "type_id" : "standard_id";
      require(
          repository.countScheduleReferences(auth.tenantId(), id, column) == 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0042"));
    }
    if (kind.equals("standards"))
      require(
          repository.countProjectReferences(auth.tenantId(), Long.toString(id)) == 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0043"));
    if (kind.equals("types")) {
      require(
          repository.cyclePlanTypeReferences(auth.tenantId(), id) == 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0044"));
      require(
          repository.taskTypeReferences(auth.tenantId(), id) == 0,
          com.windasoft.eam.common.MessageContext.text("server.maintenance.message0045"));
    }
    if (kind.equals("types"))
      for (var st : repository.listStandardPayloads(auth.tenantId()))
        require(
            rows(decode(st.get("payload")).get("items")).stream()
                .noneMatch(i -> text(i, "typeId").equals(Long.toString(id))),
            com.windasoft.eam.common.MessageContext.text("server.maintenance.message0046"));
    repository.deleteDefinition(auth.tenantId(), id);
  }

  public Map<String, Object> choices() {
    return choices(false);
  }

  public Map<String, Object> choices(boolean paged) {
    require(
        permissions.anyMaintenance("查看"),
        com.windasoft.eam.common.MessageContext.text("server.maintenance.message0047"));
    var eq =
        paged
            ? List.of()
            : repository.listEquipment(auth.tenantId()).stream()
                .filter(
                    e -> {
                      try {
                        if (!permissions.maintenanceEquipmentVisible(
                            e.get("company_org_id") == null
                                ? null
                                : number(e.get("company_org_id")),
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
                            "categoryId",
                            Objects.toString(e.get("category_id"), ""),
                            "department",
                            Objects.toString(e.get("department"), "")))
                .toList();
    var org = repository.listOrganizations(auth.tenantId());
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
        (paged ? List.<Map<String, Object>>of() : repository.listLegacyStock(auth.tenantId()))
            .stream()
                .filter(
                    r ->
                        allStocks
                            || List.of("standards", "plans", "tasks").stream()
                                .anyMatch(
                                    k ->
                                        permissions.allowed(permissions.maintenanceMenu(k), "查看")
                                            && permissions.companyAllowed(
                                                permissions.maintenanceMenu(k),
                                                "查看",
                                                number(r.get("company_id")))))
                .map(
                    r -> {
                      r.remove("company_id");
                      return r;
                    })
                .toList();
    return Map.of(
        "types",
        repository.listDefinitions(auth.tenantId(), "types").stream().map(this::view).toList(),
        "stocks",
        stocks,
        "equipment",
        eq,
        "organizations",
        org,
        "users",
        paged ? List.of() : repository.listUsers(auth.tenantId()),
        "categories",
        repository.listCategories(auth.tenantId()));
  }

  /** Compatibility for aggregate consumers; every batch is authorized and filtered in SQL. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> tasks() {
    var result = new ArrayList<Map<String, Object>>();
    var query = com.windasoft.eam.dto.common.ListQuery.from(Map.of());
    for (int page = 1; ; page++) {
      var batch = listQueries.query("tasks", query.at(page, 200));
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) return result;
    }
  }

  public Map<String, Object> task(long id) {
    return taskCommands.task(id);
  }

  @Transactional
  public Map<String, Object> action(long id, String action, Map<String, Object> body) {
    return taskCommands.action(id, action, body);
  }

  public void batchDelete(List<Map<String, Object>> values) {
    taskCommands.batchDelete(values);
  }

  public void batchPostpone(List<Map<String, Object>> values, String endDate, String reason) {
    taskCommands.batchPostpone(values, endDate, reason);
  }

  @Transactional
  public void batchAccept(List<Map<String, Object>> values) {
    taskCommands.batchAccept(values);
  }

  // Every occurrence has a unique schedule/date key. A locked schedule prevents duplicate
  // generation across workers.
  @Transactional
  public int generate(long tenant, Long planId) {
    return generation.generate(tenant, planId);
  }

  @Transactional
  public int generatePlan(long id) {
    return generation.generatePlan(id);
  }

  public List<Map<String, Object>> inbox() {
    return (java.util.List<java.util.Map<String, Object>>)
        com.windasoft.eam.common.NotificationMessages.render(
            repository.listInbox(auth.tenantId(), auth.userId()));
  }

  public void read(long id) {
    repository.markInboxRead(auth.tenantId(), auth.userId(), id);
  }

  public List<Map<String, Object>> generationTargets() {
    return repository.generationTargets();
  }
}
