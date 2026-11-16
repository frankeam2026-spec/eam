package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.advance;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.number;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.yes;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.maintenance.MaintenancePersonRole;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.maintenance.MaintenanceCategoryOption;
import com.windasoft.eam.dto.maintenance.MaintenanceChoices;
import com.windasoft.eam.dto.maintenance.MaintenanceEquipmentOption;
import com.windasoft.eam.dto.maintenance.MaintenanceLegacyStockOption;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeView;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.maintenance.MaintenanceDevicePerson;
import com.windasoft.eam.entity.maintenance.MaintenanceDeviceProject;
import com.windasoft.eam.entity.maintenance.MaintenancePlan;
import com.windasoft.eam.entity.maintenance.MaintenancePlanDevice;
import com.windasoft.eam.entity.maintenance.MaintenancePlanPerson;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.inventory.LegacySpareStockRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDevicePersonRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDeviceProjectRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceInboxRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanDeviceRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanPersonRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardItemRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.basedata.BaseDataType;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.common.DefinitionNumberService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceService {
  @Autowired private NotificationPolicyService reminderPolicies;

  @Autowired private DefinitionNumberService definitionNumbers;

  private final MaintenanceViewAssembler assembler;

  private final BusinessValueService custom;

  private final MaintenanceProjectService projects;

  private final MaintenanceGenerationService generation;

  private final MaintenanceValidationService validation;

  private final MaintenanceTaskService taskCommands;

  private final MaintenanceListService listQueries;

  private final MaintenanceRepository repository;
  private final MaintenanceTypeRepository typeFacts;
  private final MaintenanceTypeService types;
  private final MaintenanceStandardService standards;
  private final MaintenancePlanRepository plans;
  private final MaintenancePlanDeviceRepository schedules;
  private final MaintenancePlanPersonRepository planPeople;
  private final MaintenanceDevicePersonRepository devicePeople;
  private final MaintenanceDeviceProjectRepository deviceProjects;
  private final MaintenanceStandardItemRepository standardItems;
  private final OrgUnitRepository organizations;
  private final EquipmentQueryRepository equipmentQueries;
  private final SystemUserRepository userQueries;
  private final BaseItemRepository baseItems;
  private final MaintenanceInboxRepository inbox;
  private final LegacySpareStockRepository legacyStocks;
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;
  private static final Set<String> KINDS = Set.of("types", "standards", "plans");

  public MaintenanceService(
      MaintenanceRepository repository,
      MaintenanceTypeRepository typeFacts,
      MaintenanceTypeService types,
      MaintenanceStandardService standards,
      MaintenancePlanRepository plans,
      MaintenancePlanDeviceRepository schedules,
      MaintenancePlanPersonRepository planPeople,
      MaintenanceDevicePersonRepository devicePeople,
      MaintenanceDeviceProjectRepository deviceProjects,
      MaintenanceStandardItemRepository standardItems,
      OrgUnitRepository organizations,
      EquipmentQueryRepository equipmentQueries,
      SystemUserRepository userQueries,
      BaseItemRepository baseItems,
      MaintenanceInboxRepository inbox,
      LegacySpareStockRepository legacyStocks,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions,
      MaintenanceViewAssembler assembler,
      BusinessValueService custom,
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

    this.repository = repository;
    this.typeFacts = typeFacts;
    this.types = types;
    this.standards = standards;
    this.plans = plans;
    this.schedules = schedules;
    this.planPeople = planPeople;
    this.devicePeople = devicePeople;
    this.deviceProjects = deviceProjects;
    this.standardItems = standardItems;
    this.organizations = organizations;
    this.equipmentQueries = equipmentQueries;
    this.userQueries = userQueries;
    this.baseItems = baseItems;
    this.inbox = inbox;
    this.legacyStocks = legacyStocks;
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
  }

  Map<String, Object> raw(String kind, long tenant, long id) {
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    var a = repository.loadDefinition(tenant, kind, id);
    if (a.isEmpty()) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    return a.getFirst();
  }

  MaintenanceTypeView cycleType(long tenant, Object id) {
    var row = typeFacts.find(tenant, number(id), true);
    if (row == null) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    require(
        Boolean.TRUE.equals(row.getEnabled()),
        MessageContext.text("server.maintenance.message0018"));
    return types.view(row);
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
    return assembler.definition(row);
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> definitions(String kind) {
    return readDefinitions(kind);
  }

  private List<Map<String, Object>> readDefinitions(String kind) {
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    if (kind.equals("types")) {
      return types.list("[]", false).stream().map(this::legacyType).toList();
    }
    permissions.require(PermissionService.maintenanceMenu(kind), "查看");
    return definitionBatches(kind);
  }

  /**
   * Legacy selectors still request all options; SQL scope and extension hydration run per batch.
   */
  private List<Map<String, Object>> definitionBatches(String kind) {
    var result = new ArrayList<Map<String, Object>>();
    var query = ListQuery.from(Map.of());
    for (int page = 1; ; page++) {
      var batch = listQueries.query(kind, query.at(page, 200));
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) {
        return result;
      }
    }
  }

  @Transactional(readOnly = true)
  public List<Map<String, Object>> exportRows(String kind) {
    require(kind.equals("types"), MessageContext.text("server.maintenance.message0019"));
    permissions.require(PermissionService.maintenanceMenu(kind), "导出");
    return readDefinitions(kind);
  }

  public Map<String, Object> definition(String kind, long id) {
    if (kind.equals("types")) {
      return legacyType(types.get(id));
    }
    if (kind.equals("standards")) {
      return legacyStandard(standards.get(id));
    }
    var row = raw(kind, auth.tenantId(), id);
    if (!visible(row, "查看")) {
      deny();
    }
    var v = view(row);
    if (kind.equals("plans")) {
      custom.attach("maintenance_plan", v);
    }
    // 定义关系视图已按安排ID聚合完整details；再次逐计划读取相同投影既重复查询又产生两份装配规则。
    if (kind.equals("plans") && reminderPolicies != null) {
      reminderPolicies.attachBinding(auth.tenantId(), "maintenance", id, v);
    }
    return v;
  }

  List<Map<String, Object>> planProjects(
      Map<String, Object> detail, long tenant, Equipment eq, boolean checkAccess) {
    return projects.planProjects(detail, tenant, eq, checkAccess);
  }

  void validUsers(Object value, boolean required) {
    validation.validUsers(value, required);
  }

  void validAttachments(Object value, boolean imagesOnly) {
    validation.validAttachments(value, imagesOnly);
  }

  Equipment equipment(long tenant, long id) {
    return projects.equipment(tenant, id);
  }

  void checkEquipment(Equipment e, String kind, String action) {
    var scope = permissions.maintenance(kind, action);
    if (!scope.matches(
        e.getCompanyOrgId(), e.getDepartmentOrgId(), e.getOwnerUserId(), auth.userId())) {
      deny();
    }
  }

  @Transactional
  public Map<String, Object> save(String kind, Long id, Map<String, Object> body) {
    if (kind.equals("types")) {
      return legacyType(types.save(id, json.convertValue(body, MaintenanceTypeRequest.class)));
    }
    if (kind.equals("standards")) {
      return legacyStandard(
          standards.save(id, json.convertValue(body, MaintenanceStandardRequest.class)));
    }
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    String action = id == null ? "新增" : "编辑";
    permissions.require(PermissionService.maintenanceMenu(kind), action);
    long tenant = auth.tenantId();
    var existing = id == null ? null : plans.find(tenant, id, true);
    if (id != null && existing == null) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    if (existing != null) {
      if (!permissions
          .maintenance(kind, action)
          .matches(null, existing.getDepartmentId(), existing.getCreatorId(), auth.userId())) {
        deny();
      }
      VersionGuard.checkPayload(existing.getRowVersion(), body);
    }
    require(
        !text(body, "name").isBlank() && text(body, "name").length() <= 150,
        MessageContext.text("server.maintenance.message0020"));
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
      require(department > 0, MessageContext.text("ui.equipment.text0099"));
      // 沿用原归属存在性校验：同租户且未删除，不额外限制组织类型或启用状态。
      require(
          organizations.organization(tenant, department) != null,
          MessageContext.text("server.maintenance.message0021"));
      var scope = permissions.maintenance(kind, action);
      if (!scope.matches(null, department, auth.userId(), auth.userId())) {
        deny();
      }
    }
    if (kind.equals("plans")) {
      validUsers(data.get("owners"), true);
      boolean needsAcceptance = !text(data, "reviewMode").equals("无需验收");
      data.put("reviewMode", needsAcceptance ? "整机验收" : "无需验收");
      data.put("reviewRule", "");
      if (!needsAcceptance) {
        data.put("reviewers", List.of());
      }
      if (needsAcceptance && ids(data.get("reviewers")).isEmpty()) {
        throw new FieldValidationException("reviewers", "请选择验收人");
      }
      validUsers(data.get("reviewers"), needsAcceptance);
      require(
          !rows(data.get("details")).isEmpty(),
          MessageContext.text("server.maintenance.message0031"));
      require(
          Set.of("固定间隔", "完成时间").contains(text(data, "calculation")),
          MessageContext.text("server.maintenance.message0032"));
      require(
          number(data.get("advanceDays")) >= 0 && number(data.get("advanceDays")) <= 365,
          MessageContext.text("server.maintenance.message0033"));
      require(
          number(data.get("remindDays")) >= 0 && number(data.get("remindDays")) <= 365,
          MessageContext.text("server.maintenance.message0034"));
      require(
          number(data.get("advanceDays")) >= number(data.get("remindDays")),
          MessageContext.text("server.maintenance.message0035"));
      // 每次保存内复用设备事实，仍对每条安排独立校验权限和项目，不跨请求缓存。
      var equipmentFacts = new HashMap<Long, Equipment>();
      for (var d : rows(data.get("details"))) {
        var eq =
            equipmentFacts.computeIfAbsent(
                number(d.get("equipmentId")), key -> equipment(tenant, key));
        checkEquipment(eq, kind, action);
        var type = cycleType(tenant, d.get("typeId"));
        d.put("typeId", type.id());
        d.put("typeName", type.name());
        if (!d.containsKey("period")) {
          d.put("period", type.period());
        }
        if (!d.containsKey("unit")) {
          d.put("unit", type.unit());
        }
        var selectedProjects = planProjects(d, tenant, eq, true);
        require(!selectedProjects.isEmpty(), MessageContext.text("ui.equipment.text0753"));
        if (d.containsKey("projectRefs")) {
          d.put("standardId", text(rows(d.get("projectRefs")).getFirst(), "standardId"));
        }
        LocalDate.parse(text(d, "nextDate"));
        require(
            new BigDecimal(Objects.toString(d.get("period"), "0")).stripTrailingZeros().scale() <= 0
                && number(d.get("period")) > 0
                && number(d.get("period")) <= 10000,
            MessageContext.text("server.maintenance.message0036"));
        require(
            Set.of("日", "周", "月", "年").contains(text(d, "unit")),
            MessageContext.text("server.maintenance.message0008"));
        Object duration =
            d.containsKey("validDuration") ? d.get("validDuration") : d.get("validDays");
        String durationUnit = d.containsKey("validUnit") ? text(d, "validUnit") : "日";
        String durationField = "details." + rows(data.get("details")).indexOf(d) + ".validDuration";
        if (duration == null
            || new BigDecimal(duration.toString()).stripTrailingZeros().scale() > 0
            || number(duration) < 0
            || number(duration) > 36500
            || !Set.of("日", "周", "月").contains(durationUnit)) {
          throw new FieldValidationException(durationField, "请填写有效时长整数并选择天、周或月");
        }
        LocalDate startDate = LocalDate.parse(text(d, "nextDate"));
        long days =
            ChronoUnit.DAYS.between(startDate, advance(startDate, number(duration), durationUnit));
        if (days > 36500) {
          throw new FieldValidationException(durationField, "有效时长不能超过36500天");
        }
        d.put("validDuration", number(duration));
        d.put("validUnit", durationUnit);
        d.put("validDays", days);
        validUsers(d.get("owners"), false);
        if (!needsAcceptance) {
          d.put("reviewers", List.of());
        }
        validUsers(d.get("reviewers"), false);
        var reviewers =
            ids(d.get("reviewers")).isEmpty()
                ? ids(data.get("reviewers"))
                : ids(d.get("reviewers"));
        for (var reviewer : reviewers) {
          require(
              permissions.canMaintenanceAccept(reviewer, eq.getDepartmentOrgId()),
              MessageContext.text("server.maintenance.message0037"));
        }
      }
    }
    long key = id == null ? IdWorker.getId() : id;
    String code = text(data, "code");
    if (code.isEmpty()) {
      code = definitionNumbers.next(tenant, "MP");
    }
    require(code.length() <= 64, MessageContext.text("server.inventory.message0191"));
    data.put("code", code);
    var header = new MaintenancePlan();
    header.setId(key);
    header.setTenantId(tenant);
    header.setCode(code);
    header.setName(text(data, "name"));
    header.setDepartmentId(department);
    header.setEnabled(yes(storedText(data.get("enabled"), "true")));
    header.setRemark(storedText(data.get("remark"), ""));
    header.setCalculationMode(storedText(data.get("calculation"), "固定间隔"));
    header.setAdvanceDays(Math.toIntExact(number(data.get("advanceDays"))));
    header.setRemindDays(Math.toIntExact(number(data.get("remindDays"))));
    header.setAutoFill(storedText(data.get("autoFill"), "无"));
    header.setScanStart(yes(data.get("scanStart")));
    header.setReviewMode(text(data, "reviewMode"));
    header.setReviewRule("");
    if (existing == null) {
      header.setCreatorId(auth.userId());
      plans.insert(header);
    } else {
      plans.update(header);
    }
    replacePlanPeople(tenant, key, data);
    if (kind.equals("plans")) {
      var keep = new HashSet<Long>();
      for (var d : rows(data.get("details"))) {
        long sid = number(d.get("id"));
        var date = LocalDate.parse(text(d, "nextDate"));
        var old = sid > 0 ? schedules.find(tenant, key, sid, true) : null;
        if (sid > 0) {
          require(old != null, MessageContext.text("server.maintenance.message0038"));
          require(
              old.getEquipmentId() == number(d.get("equipmentId")),
              MessageContext.text("server.maintenance.message0039"));
          var last = repository.findLatestDueDate(tenant, sid);
          require(
              last.isEmpty()
                  || date.equals(old.getNextDate())
                  || date.isAfter(((Date) last.getFirst().get("due_date")).toLocalDate()),
              MessageContext.text("server.maintenance.message0040"));
        } else {
          sid = IdWorker.getId();
        }
        boolean keepAnchor =
            old != null
                && date.equals(old.getNextDate())
                && number(d.get("period")) == old.getPeriodCount()
                && text(d, "unit").equals(old.getPeriodUnit());
        var row = new MaintenancePlanDevice();
        row.setId(sid);
        row.setTenantId(tenant);
        row.setPlanId(key);
        row.setEquipmentId(number(d.get("equipmentId")));
        row.setTypeId(number(d.get("typeId")));
        row.setLegacyStandardId(d.containsKey("projectRefs") ? null : number(d.get("standardId")));
        row.setNextDate(date);
        row.setAnchorDate(keepAnchor ? old.getAnchorDate() : date);
        row.setCycleIndex(keepAnchor ? old.getCycleIndex() : 0);
        row.setEnabled(true);
        row.setPeriodCount(Math.toIntExact(number(d.get("period"))));
        row.setPeriodUnit(storedText(d.get("unit"), "月"));
        row.setValidDays(Math.toIntExact(number(d.get("validDays"))));
        row.setValidDuration(Math.toIntExact(number(d.get("validDuration"))));
        row.setValidUnit(text(d, "validUnit"));
        row.setRemark(storedText(d.get("remark"), ""));
        if (old == null) {
          schedules.insert(row);
        } else {
          schedules.update(row);
        }
        replaceDevicePeople(tenant, sid, d);
        deviceProjects.clear(tenant, sid);
        var projectRows = new ArrayList<MaintenanceDeviceProject>();
        if (d.containsKey("projectRefs")) {
          for (var ref : rows(d.get("projectRefs"))) {
            var project = new MaintenanceDeviceProject();
            project.setTenantId(tenant);
            project.setDeviceId(sid);
            project.setStandardId(number(ref.get("standardId")));
            project.setItemKey(Objects.toString(ref.get("itemId")));
            project.setSortNo(projectRows.size());
            projectRows.add(project);
          }
        } else {
          for (var item : standardItems.list(tenant, List.of(number(d.get("standardId"))))) {
            if (!Objects.equals(item.getTypeId(), row.getTypeId())) {
              continue;
            }
            var project = new MaintenanceDeviceProject();
            project.setTenantId(tenant);
            project.setDeviceId(sid);
            project.setStandardId(item.getStandardId());
            project.setItemKey(item.getItemKey());
            project.setSortNo(projectRows.size());
            projectRows.add(project);
          }
        }
        deviceProjects.insertAll(projectRows);
        keep.add(sid);
      }
      for (var row : schedules.list(tenant, key)) {
        if (!keep.contains(row.getId())) {
          schedules.disable(tenant, key, row.getId());
        }
      }
      // 计划保存与即时生成共用事务，任务、通知或履历失败时不能留下已更新的计划/游标。
      generate(tenant, key);
    }
    if (kind.equals("plans")) {
      custom.save("maintenance_plan", key, body, id == null);
    }
    if (kind.equals("plans") && reminderPolicies != null) {
      reminderPolicies.saveBinding(tenant, "maintenance", key, body);
    }
    return definition(kind, key);
  }

  @Transactional
  public void delete(String kind, long id) {
    if (kind.equals("types")) {
      types.delete(id);
      return;
    }
    if (kind.equals("standards")) {
      standards.delete(id);
      return;
    }
    require(kind.equals("plans"), MessageContext.text("server.system.message0045"));
    var row = plans.find(auth.tenantId(), id, true);
    if (row == null) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    permissions.require(PermissionService.maintenanceMenu(kind), "删除");
    if (!permissions
        .maintenance(kind, "删除")
        .matches(null, row.getDepartmentId(), row.getCreatorId(), auth.userId())) {
      deny();
    }
    custom.remove("maintenance_plan", id);
    schedules.disable(auth.tenantId(), id, null);
    require(
        repository.countPlanTasks(auth.tenantId(), id) == 0,
        MessageContext.text("server.inspection.message0069"));
    schedules.deleteForPlan(auth.tenantId(), id);
    plans.delete(auth.tenantId(), id);
  }

  // 旧关系写入将null/空白视为默认值，非空展示文字保持原样，避免重构悄悄修剪历史协议。
  private static String storedText(Object value, String fallback) {
    return value == null || value.toString().isBlank() ? fallback : value.toString();
  }

  /** 计划人员完整替换先于设备安排写入，保持原事务和人员顺序。 */
  private void replacePlanPeople(long tenant, long plan, Map<String, Object> data) {
    planPeople.clear(tenant, plan);
    var result = new ArrayList<MaintenancePlanPerson>();
    for (var role : List.of(MaintenancePersonRole.OWNER, MaintenancePersonRole.REVIEWER)) {
      int position = 0;
      for (long user :
          new LinkedHashSet<>(
              ids(data.get(role == MaintenancePersonRole.OWNER ? "owners" : "reviewers")))) {
        var row = new MaintenancePlanPerson();
        row.setTenantId(tenant);
        row.setPlanId(plan);
        row.setRoleCode(role.code());
        row.setUserId(user);
        row.setSortNo(position++);
        result.add(row);
      }
    }
    planPeople.insertAll(result);
  }

  /** 安排人员独立保存；项目关系由同一业务事务在其后替换，不藏入主体仓储。 */
  private void replaceDevicePeople(long tenant, long device, Map<String, Object> data) {
    devicePeople.clear(tenant, device);
    var result = new ArrayList<MaintenanceDevicePerson>();
    for (var role : List.of(MaintenancePersonRole.OWNER, MaintenancePersonRole.REVIEWER)) {
      int position = 0;
      for (long user :
          new LinkedHashSet<>(
              ids(data.get(role == MaintenancePersonRole.OWNER ? "owners" : "reviewers")))) {
        var row = new MaintenanceDevicePerson();
        row.setTenantId(tenant);
        row.setDeviceId(device);
        row.setRoleCode(role.code());
        row.setUserId(user);
        row.setSortNo(position++);
        result.add(row);
      }
    }
    devicePeople.insertAll(result);
  }

  /**
   * 旧内部聚合调用仍使用统一定义协议；仅在此边界适配类型视图，校验和持久化完全由类型Service负责。
   * 专用HTTP类型入口直接使用MaintenanceTypeRequest/View，不经此Map适配。
   */
  private Map<String, Object> legacyType(MaintenanceTypeView value) {
    return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  /** 仅为旧内部聚合入口保留格式适配，标准业务规则与写入只由标准Service维护。 */
  private Map<String, Object> legacyStandard(MaintenanceStandardView value) {
    return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
  }

  public MaintenanceChoices choices() {
    return choices(false);
  }

  public MaintenanceChoices choices(boolean paged) {
    require(
        permissions.anyMaintenance("查看"), MessageContext.text("server.maintenance.message0047"));
    var equipmentScopes = new ArrayList<PermissionScope>();
    if (!paged) {
      for (String kind : List.of("standards", "plans", "tasks")) {
        if (permissions.allowed(PermissionService.maintenanceMenu(kind), "查看")) {
          equipmentScopes.add(permissions.maintenance(kind, "查看"));
        }
      }
    }
    List<MaintenanceEquipmentOption> eq =
        paged
            ? List.of()
            : equipmentQueries.scopedFacts(auth.tenantId(), auth.userId(), equipmentScopes).stream()
                .map(
                    e ->
                        new MaintenanceEquipmentOption(
                            Long.toString(e.getId()),
                            e.getEquipmentCode(),
                            e.getName(),
                            Objects.toString(e.getModel(), ""),
                            Objects.toString(e.getCategory(), ""),
                            Objects.toString(e.getCategoryId(), ""),
                            Objects.toString(e.getDepartment(), "")))
                .toList();
    var org =
        organizations.organizations(auth.tenantId(), true).stream()
            .map(row -> assembler.organization(row, true))
            .toList();
    // 查看范围取各保养菜单的公司并集；不能用这份并集代替保存标准时的写权限。
    boolean allStocks = false;
    var stockCompanies = new HashSet<Long>();
    for (String kind : List.of("standards", "plans", "tasks")) {
      if (!permissions.allowed(PermissionService.maintenanceMenu(kind), "查看")) {
        continue;
      }
      var scope = permissions.maintenance(kind, "查看");
      stockCompanies.addAll(scope.companyIds());
      if (scope.all()) {
        allStocks = true;
        break;
      }
    }
    var stocks =
        paged
            ? List.<MaintenanceLegacyStockOption>of()
            : legacyStocks.listVisible(auth.tenantId(), allStocks, stockCompanies).stream()
                .map(
                    stock ->
                        new MaintenanceLegacyStockOption(
                            stock.getId(),
                            stock.getCode(),
                            stock.getName(),
                            stock.getModel(),
                            stock.getQuantity()))
                .toList();
    return new MaintenanceChoices(
        typeFacts.list(auth.tenantId()).stream().map(types::view).toList(),
        stocks,
        eq,
        org,
        paged ? List.of() : userQueries.nameOptions(auth.tenantId()),
        baseItems.itemsByType(auth.tenantId(), BaseDataType.CATEGORY.code(), true).stream()
            .map(
                item ->
                    new MaintenanceCategoryOption(
                        Long.toString(item.getId()),
                        item.getName(),
                        Objects.toString(item.getParentId(), "0")))
            .toList());
  }

  /** Compatibility for aggregate consumers; every batch is authorized and filtered in SQL. */
  @Transactional(readOnly = true)
  public List<Map<String, Object>> tasks() {
    var result = new ArrayList<Map<String, Object>>();
    var query = ListQuery.from(Map.of());
    for (int page = 1; ; page++) {
      var batch = listQueries.query("tasks", query.at(page, 200));
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) {
        return result;
      }
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
    return (List<Map<String, Object>>)
        NotificationMessages.render(repository.listInbox(auth.tenantId(), auth.userId()));
  }

  public void read(long id) {
    inbox.markRead(auth.tenantId(), auth.userId(), id);
  }

  public List<Map<String, Object>> generationTargets() {
    return repository.generationTargets();
  }
}
