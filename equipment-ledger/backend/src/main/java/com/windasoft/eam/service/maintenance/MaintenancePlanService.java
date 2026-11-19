package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.advance;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.FieldValidationException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.maintenance.MaintenanceCalculationMode;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import com.windasoft.eam.domain.maintenance.MaintenancePersonRole;
import com.windasoft.eam.domain.maintenance.MaintenanceReviewMode;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceCustomFields;
import com.windasoft.eam.dto.maintenance.MaintenancePlanDetail;
import com.windasoft.eam.dto.maintenance.MaintenancePlanRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceProjectSelection;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.maintenance.MaintenanceDevicePerson;
import com.windasoft.eam.entity.maintenance.MaintenanceDeviceProject;
import com.windasoft.eam.entity.maintenance.MaintenancePlan;
import com.windasoft.eam.entity.maintenance.MaintenancePlanDevice;
import com.windasoft.eam.entity.maintenance.MaintenancePlanPerson;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDevicePersonRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDeviceProjectRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanDeviceRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanPersonRepository;
import com.windasoft.eam.repository.maintenance.MaintenancePlanRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardItemRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.common.DefinitionNumberService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.PermissionService;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 计划的授权、周期和验收规则及完整保存事务；主体/人员/安排/项目通过各自仓储协调，不承接任务状态机。 */
@Service
@RequiredArgsConstructor
public class MaintenancePlanService {
  private static final String KIND = "plans";
  private final MaintenancePlanRepository plans;
  private final MaintenancePlanDeviceRepository schedules;
  private final MaintenancePlanPersonRepository planPeople;
  private final MaintenanceDevicePersonRepository devicePeople;
  private final MaintenanceDeviceProjectRepository projectLinks;
  private final MaintenanceStandardItemRepository standardItems;
  private final MaintenanceTypeRepository types;
  private final MaintenanceListRepository views;
  private final MaintenanceTaskRepository taskReferences;
  private final OrgUnitRepository organizations;
  private final EquipmentQueryRepository equipment;
  private final MaintenanceValidationService validation;
  private final MaintenanceProjectService projects;
  private final MaintenanceGenerationService generation;
  private final DefinitionNumberService numbers;
  private final BusinessValueService custom;
  private final NotificationPolicyService reminders;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  @Transactional(readOnly = true)
  public MaintenancePlanDetail get(long id) {
    var view = views.planView(auth.tenantId(), id);
    if (view == null) {
      throw missing();
    }
    if (!permissions
        .maintenance(KIND, PermissionAction.VIEW)
        .matches(
            null,
            nullableId(view.getDepartmentId()),
            nullableId(view.getCreatorId()),
            auth.userId())) {
      deny();
    }
    var values = custom.read("maintenance_plan", id);
    var extra =
        values == null
            ? null
            : new MaintenanceCustomFields(
                json.valueToTree(values.customFields()),
                json.valueToTree(values.customFieldLabels()),
                values.customSchemaVersion());
    return new MaintenancePlanDetail(
        view, extra, reminders.planBinding(auth.tenantId(), "maintenance", id));
  }

  /** 原计划先加锁并核对版本与原/目标范围，再按原顺序校验周期、设备及项目。 计划人员、设备安排、项目关系及即时任务生成在同一事务内，生成或通知失败不能留下已保存计划。 */
  @Transactional
  public MaintenancePlanDetail save(Long id, MaintenancePlanRequest request) {
    long tenant = auth.tenantId();
    var action = id == null ? PermissionAction.CREATE : PermissionAction.EDIT;
    permissions.require(PermissionService.maintenanceMenu(KIND), action);
    var old = id == null ? null : plans.find(tenant, id, true);
    if (id != null && old == null) {
      throw missing();
    }
    var scope = permissions.maintenance(KIND, action);
    if (old != null) {
      if (!scope.matches(null, old.getDepartmentId(), old.getCreatorId(), auth.userId())) {
        deny();
      }
      require(request.getRowVersion() != null, MessageContext.text("server.common.message0018"));
      VersionGuard.check(old.getRowVersion(), request.getRowVersion());
    }
    validation.validateName(request);
    require(
        request.getDepartmentId() != null && request.getDepartmentId() > 0,
        MessageContext.text("ui.equipment.text0099"));
    require(
        organizations.organization(tenant, request.getDepartmentId()) != null,
        MessageContext.text("server.maintenance.message0021"));
    if (!scope.matches(null, request.getDepartmentId(), auth.userId(), auth.userId())) {
      deny();
    }
    validation.validate(request);
    validation.validUsers(request.getOwners(), true);
    boolean acceptance = !text(request.getReviewMode()).equals(MaintenanceReviewMode.NONE.code());
    request.setReviewMode(
        acceptance ? MaintenanceReviewMode.EQUIPMENT.code() : MaintenanceReviewMode.NONE.code());
    if (!acceptance) {
      request.setReviewers(List.of());
    }
    if (acceptance && values(request.getReviewers()).isEmpty()) {
      throw new FieldValidationException("reviewers", "请选择验收人");
    }
    validation.validUsers(request.getReviewers(), acceptance);
    var details = values(request.getDetails());
    require(!details.isEmpty(), MessageContext.text("server.maintenance.message0031"));
    require(
        Set.of(
                MaintenanceCalculationMode.FIXED_INTERVAL.code(),
                MaintenanceCalculationMode.COMPLETION.code())
            .contains(text(request.getCalculation())),
        MessageContext.text("server.maintenance.message0032"));
    int advanceDays =
        request.getAdvanceDays() == null ? 0 : Math.toIntExact(request.getAdvanceDays());
    int remindDays = request.getRemindDays() == null ? 0 : Math.toIntExact(request.getRemindDays());
    require(advanceDays >= remindDays, MessageContext.text("server.maintenance.message0035"));
    var equipmentFacts = new HashMap<Long, Equipment>();
    var typeFacts = new HashMap<Long, MaintenanceType>();
    for (int index = 0; index < details.size(); index++) {
      var detail = details.get(index);
      var device =
          equipmentFacts.computeIfAbsent(
              detail.getEquipmentId(), key -> equipment.findFacts(tenant, key));
      require(device != null, MessageContext.text("server.common.message0064"));
      if (!scope.matches(
          device.getCompanyOrgId(),
          device.getDepartmentOrgId(),
          device.getOwnerUserId(),
          auth.userId())) {
        deny();
      }
      var type =
          typeFacts.computeIfAbsent(detail.getTypeId(), key -> types.find(tenant, key, true));
      if (type == null) {
        throw missing();
      }
      require(
          Boolean.TRUE.equals(type.getEnabled()),
          MessageContext.text("server.maintenance.message0018"));
      if (!detail.isPeriodPresent()) {
        detail.setPeriod(type.getPeriodCount().longValue());
      }
      if (!detail.isUnitPresent()) {
        detail.setUnit(type.getPeriodUnit());
      }
      var selection =
          new MaintenanceProjectSelection(
              detail.getTypeId(),
              detail.getStandardId() == null ? 0 : detail.getStandardId(),
              detail.isProjectRefsPresent(),
              values(detail.getProjectRefs()));
      require(
          projects.validateSelection(selection, tenant, device) > 0,
          MessageContext.text("ui.equipment.text0753"));
      // 明确为空的周期不能当作“未传”，只有未传才沿用保养类型默认周期。
      require(
          detail.getPeriod() != null
              && detail.getPeriod() > 0
              && detail.getPeriod() <= MaintenanceDefinitionLimits.PERIOD_MAX_COUNT,
          MessageContext.text("server.maintenance.message0036"));
      require(
          Set.of("日", "周", "月", "年").contains(text(detail.getUnit())),
          MessageContext.text("server.maintenance.message0008"));
      var date = detail.getNextDate();
      var duration =
          detail.isValidDurationPresent() ? detail.getValidDuration() : detail.getValidDays();
      String unit = detail.isValidUnitPresent() ? text(detail.getValidUnit()) : "日";
      String field = "details." + index + ".validDuration";
      if (duration == null
          || duration < 0
          || duration > MaintenanceDefinitionLimits.MAX_VALIDITY_DAYS
          || !Set.of("日", "周", "月").contains(unit)) {
        throw new FieldValidationException(field, "请填写有效时长整数并选择天、周或月");
      }
      long days = ChronoUnit.DAYS.between(date, advance(date, duration, unit));
      if (days > MaintenanceDefinitionLimits.MAX_VALIDITY_DAYS) {
        throw new FieldValidationException(field, "有效时长不能超过36500天");
      }
      detail.setValidDuration(duration);
      detail.setValidUnit(unit);
      detail.setValidDays(days);
      validation.validUsers(detail.getOwners(), false);
      if (!acceptance) {
        detail.setReviewers(List.of());
      }
      validation.validUsers(detail.getReviewers(), false);
      var reviewers =
          values(detail.getReviewers()).isEmpty()
              ? values(request.getReviewers())
              : detail.getReviewers();
      for (long reviewer : reviewers) {
        require(
            permissions.canMaintenanceAccept(reviewer, device.getDepartmentOrgId()),
            MessageContext.text("server.maintenance.message0037"));
      }
    }
    long plan = id == null ? IdWorker.getId() : id;
    String code = request.getCode();
    if (code.isEmpty()) {
      code = numbers.next(tenant, "MP");
    }
    var header = new MaintenancePlan();
    header.setId(plan);
    header.setTenantId(tenant);
    header.setCode(code);
    header.setName(request.getName());
    header.setDepartmentId(request.getDepartmentId());
    header.setEnabled(request.getEnabled() == null || request.getEnabled());
    header.setRemark(stored(request.getRemark(), ""));
    header.setCalculationMode(
        stored(request.getCalculation(), MaintenanceCalculationMode.FIXED_INTERVAL.code()));
    header.setAdvanceDays(advanceDays);
    header.setRemindDays(remindDays);
    header.setAutoFill(stored(request.getAutoFill(), "无"));
    header.setScanStart(Boolean.TRUE.equals(request.getScanStart()));
    header.setReviewMode(request.getReviewMode());
    header.setReviewRule("");
    if (old == null) {
      header.setCreatorId(auth.userId());
      plans.insert(header);
    } else {
      plans.update(header);
    }
    replacePlanPeople(tenant, plan, request.getOwners(), request.getReviewers());
    var keep = new HashSet<Long>();
    for (var detail : details) {
      long key = detail.getId() == null ? 0 : detail.getId();
      var date = detail.getNextDate();
      var previous = key > 0 ? schedules.find(tenant, plan, key, true) : null;
      if (key > 0) {
        require(previous != null, MessageContext.text("server.maintenance.message0038"));
        require(
            Objects.equals(previous.getEquipmentId(), detail.getEquipmentId()),
            MessageContext.text("server.maintenance.message0039"));
        var last = taskReferences.latestDueDate(tenant, key);
        require(
            last == null || date.equals(previous.getNextDate()) || date.isAfter(last),
            MessageContext.text("server.maintenance.message0040"));
      } else {
        key = IdWorker.getId();
      }
      boolean keepAnchor =
          previous != null
              && date.equals(previous.getNextDate())
              && Math.toIntExact(detail.getPeriod()) == previous.getPeriodCount()
              && text(detail.getUnit()).equals(text(previous.getPeriodUnit()));
      var row = new MaintenancePlanDevice();
      row.setId(key);
      row.setTenantId(tenant);
      row.setPlanId(plan);
      row.setEquipmentId(detail.getEquipmentId());
      row.setTypeId(detail.getTypeId());
      row.setLegacyStandardId(
          detail.isProjectRefsPresent()
              ? null
              : detail.getStandardId() == null ? 0 : detail.getStandardId());
      row.setNextDate(date);
      row.setAnchorDate(keepAnchor ? previous.getAnchorDate() : date);
      row.setCycleIndex(keepAnchor ? previous.getCycleIndex() : 0);
      row.setEnabled(true);
      row.setPeriodCount(Math.toIntExact(detail.getPeriod()));
      row.setPeriodUnit(stored(detail.getUnit(), "月"));
      row.setValidDays(Math.toIntExact(detail.getValidDays()));
      row.setValidDuration(Math.toIntExact(detail.getValidDuration()));
      row.setValidUnit(detail.getValidUnit());
      row.setRemark(stored(detail.getRemark(), ""));
      if (previous == null) {
        schedules.insert(row);
      } else {
        schedules.update(row);
      }
      replaceDevicePeople(tenant, key, detail.getOwners(), detail.getReviewers());
      projectLinks.clear(tenant, key);
      var selected = new ArrayList<MaintenanceDeviceProject>();
      if (detail.isProjectRefsPresent()) {
        for (var reference : values(detail.getProjectRefs())) {
          var project = new MaintenanceDeviceProject();
          project.setTenantId(tenant);
          project.setDeviceId(key);
          project.setStandardId(reference.standardId());
          project.setItemKey(reference.itemId());
          project.setSortNo(selected.size());
          selected.add(project);
        }
      } else {
        for (var item : standardItems.list(tenant, List.of(row.getLegacyStandardId()))) {
          if (!Objects.equals(item.getTypeId(), row.getTypeId())) {
            continue;
          }
          var project = new MaintenanceDeviceProject();
          project.setTenantId(tenant);
          project.setDeviceId(key);
          project.setStandardId(item.getStandardId());
          project.setItemKey(item.getItemKey());
          project.setSortNo(selected.size());
          selected.add(project);
        }
      }
      projectLinks.insertAll(selected);
      keep.add(key);
    }
    for (var row : schedules.list(tenant, plan)) {
      if (!keep.contains(row.getId())) {
        schedules.disable(tenant, plan, row.getId());
      }
    }
    generation.generate(tenant, plan);
    custom.save(
        "maintenance_plan",
        plan,
        request.getCustomFields() == null ? json.nullNode() : request.getCustomFields(),
        request.getCustomSchemaVersion(),
        "",
        id == null);
    if (request.isReminderModePresent()) {
      reminders.saveBinding(
          tenant, "maintenance", plan, request.getReminderMode(), request.getReminderPolicyId());
    }
    return get(plan);
  }

  /** 先停用安排再核对未删除任务，仍在原事务内；删除被拒绝时扩展字段和安排状态一起回滚。 */
  @Transactional
  public void delete(long id) {
    var row = plans.find(auth.tenantId(), id, true);
    if (row == null) {
      throw missing();
    }
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.DELETE);
    if (!permissions
        .maintenance(KIND, PermissionAction.DELETE)
        .matches(null, row.getDepartmentId(), row.getCreatorId(), auth.userId())) {
      deny();
    }
    custom.remove("maintenance_plan", id);
    schedules.disable(auth.tenantId(), id, null);
    require(
        taskReferences.countPlanTasks(auth.tenantId(), id) == 0,
        MessageContext.text("server.inspection.message0069"));
    schedules.deleteForPlan(auth.tenantId(), id);
    plans.delete(auth.tenantId(), id);
  }

  private void replacePlanPeople(long tenant, long plan, List<Long> owners, List<Long> reviewers) {
    planPeople.clear(tenant, plan);
    var rows = new ArrayList<MaintenancePlanPerson>();
    for (var role : List.of(MaintenancePersonRole.OWNER, MaintenancePersonRole.REVIEWER)) {
      int order = 0;
      for (long user :
          new LinkedHashSet<>(values(role == MaintenancePersonRole.OWNER ? owners : reviewers))) {
        var row = new MaintenancePlanPerson();
        row.setTenantId(tenant);
        row.setPlanId(plan);
        row.setRoleCode(role.code());
        row.setUserId(user);
        row.setSortNo(order++);
        rows.add(row);
      }
    }
    planPeople.insertAll(rows);
  }

  private void replaceDevicePeople(
      long tenant, long device, List<Long> owners, List<Long> reviewers) {
    devicePeople.clear(tenant, device);
    var rows = new ArrayList<MaintenanceDevicePerson>();
    for (var role : List.of(MaintenancePersonRole.OWNER, MaintenancePersonRole.REVIEWER)) {
      int order = 0;
      for (long user :
          new LinkedHashSet<>(values(role == MaintenancePersonRole.OWNER ? owners : reviewers))) {
        var row = new MaintenanceDevicePerson();
        row.setTenantId(tenant);
        row.setDeviceId(device);
        row.setRoleCode(role.code());
        row.setUserId(user);
        row.setSortNo(order++);
        rows.add(row);
      }
    }
    devicePeople.insertAll(rows);
  }

  private static LocalizedStatusException missing() {
    return new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
  }

  private static Long nullableId(String value) {
    return value == null || value.isBlank() ? null : Long.valueOf(value);
  }

  private static String text(String value) {
    return Objects.toString(value, "").trim();
  }

  private static String stored(String value, String fallback) {
    return value == null || value.isBlank() ? fallback : value;
  }

  private static <T> List<T> values(List<T> value) {
    return value == null ? List.of() : value;
  }
}
