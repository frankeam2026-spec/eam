package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.maintenance.MaintenanceCategoryOption;
import com.windasoft.eam.dto.maintenance.MaintenanceChoices;
import com.windasoft.eam.dto.maintenance.MaintenanceEquipmentOption;
import com.windasoft.eam.dto.maintenance.MaintenanceLegacyStockOption;
import com.windasoft.eam.dto.maintenance.MaintenancePlanDetail;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeView;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.inventory.LegacySpareStockRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceInboxRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.basedata.BaseDataType;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 兼容聚合与选项入口；类型、标准、计划及任务各自委托业务Service，不保留另一套保存规则。 */
@Service
public class MaintenanceService {

  private final MaintenanceViewAssembler assembler;

  private final MaintenanceGenerationService generation;

  private final MaintenanceTaskService taskCommands;

  private final MaintenanceListService listQueries;

  private final MaintenanceRepository repository;
  private final MaintenanceTypeService types;
  private final MaintenanceTypeRepository typeFacts;
  private final MaintenanceStandardService standards;
  private final MaintenancePlanService plans;
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
      MaintenanceTypeService types,
      MaintenanceTypeRepository typeFacts,
      MaintenanceStandardService standards,
      MaintenancePlanService plans,
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
      MaintenanceGenerationService generation,
      MaintenanceTaskService taskCommands,
      MaintenanceListService listQueries) {
    this.assembler = assembler;
    this.generation = generation;
    this.taskCommands = taskCommands;
    this.listQueries = listQueries;

    this.repository = repository;
    this.types = types;
    this.typeFacts = typeFacts;
    this.standards = standards;
    this.plans = plans;
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
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    return switch (kind) {
      case "types" -> legacyType(types.get(id));
      case "standards" -> legacyStandard(standards.get(id));
      default -> legacyPlan(plans.get(id));
    };
  }

  /** 仅旧聚合入口保留Map协议，校验和持久化完全委托对应的类型化业务能力。 */
  @Transactional
  public Map<String, Object> save(String kind, Long id, Map<String, Object> body) {
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    return switch (kind) {
      case "types" ->
          legacyType(types.save(id, json.convertValue(body, MaintenanceTypeRequest.class)));
      case "standards" ->
          legacyStandard(
              standards.save(id, json.convertValue(body, MaintenanceStandardRequest.class)));
      default -> legacyPlan(plans.save(id, json.convertValue(body, MaintenancePlanRequest.class)));
    };
  }

  @Transactional
  public void delete(String kind, long id) {
    require(KINDS.contains(kind), MessageContext.text("server.system.message0045"));
    switch (kind) {
      case "types" -> types.delete(id);
      case "standards" -> standards.delete(id);
      default -> plans.delete(id);
    }
  }

  private Map<String, Object> legacyPlan(MaintenancePlanDetail value) {
    return json.convertValue(value, new TypeReference<LinkedHashMap<String, Object>>() {});
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
