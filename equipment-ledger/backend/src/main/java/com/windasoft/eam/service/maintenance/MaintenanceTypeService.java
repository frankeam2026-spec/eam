package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceTypeView;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardItemRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTaskRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 类型生命周期及周期引用规则；稳定主体使用明确类型，不经动态字段模板写入。 */
@Service
public class MaintenanceTypeService {
  private static final String KIND = "types";
  private final MaintenanceTypeRepository types;
  private final MaintenanceRepository references;
  private final MaintenanceTaskRepository tasks;
  private final MaintenanceStandardItemRepository items;
  private final MaintenanceValidationService validation;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final BusinessQueryService filters;

  public MaintenanceTypeService(
      MaintenanceTypeRepository types,
      MaintenanceRepository references,
      MaintenanceTaskRepository tasks,
      MaintenanceStandardItemRepository items,
      MaintenanceValidationService validation,
      AuthContext auth,
      PermissionService permissions,
      BusinessQueryService filters) {
    this.types = types;
    this.references = references;
    this.tasks = tasks;
    this.items = items;
    this.validation = validation;
    this.auth = auth;
    this.permissions = permissions;
    this.filters = filters;
  }

  @Transactional(readOnly = true)
  public List<MaintenanceTypeView> list(String conditions, boolean export) {
    if (export) permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.EXPORT);
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.VIEW);
    // 类型没有自定义字段；沿用原校验：普通条件不参与过滤，自定义字段条件拒绝。
    filters.predicate(null, conditions, "t");
    return types.list(auth.tenantId()).stream().map(this::view).toList();
  }

  @Transactional(readOnly = true)
  public MaintenanceTypeView get(long id) {
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.VIEW);
    return view(required(id, false));
  }

  private MaintenanceType required(long id, boolean lock) {
    var row = types.find(auth.tenantId(), id, lock);
    if (row == null)
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    return row;
  }

  /** 锁定原类型后复核版本；周期被计划引用时不能修改。系统标志只继承数据库值。 */
  @Transactional
  public MaintenanceTypeView save(Long id, MaintenanceTypeRequest request) {
    permissions.require(PermissionService.maintenanceMenu(KIND), id == null ? PermissionAction.CREATE : PermissionAction.EDIT);
    var old = id == null ? null : required(id, true);
    if (old != null) {
      require(request.rowVersion() != null, MessageContext.text("server.common.message0018"));
      VersionGuard.check(old.getRowVersion(), request.rowVersion());
    }
    validation.validateName(request);
    validation.validate(request);
    String name = request.name();
    long period = request.period();
    String unit = text(request.unit());
    require(
        Set.of("日", "周", "月", "年").contains(unit),
        MessageContext.text("server.maintenance.message0008"));
    if (old != null
        && (!Objects.equals(old.getPeriodCount(), (int) period)
            || !Objects.equals(text(old.getPeriodUnit()), unit)))
      require(
          references.cyclePlanTypeReferences(auth.tenantId(), id) == 0,
          MessageContext.text("server.inspection.message0054"));
    String code = text(request.code());
    if (code.isEmpty()) code = "MT-" + IdWorker.getId();

    var row = new MaintenanceType();
    row.setId(id == null ? IdWorker.getId() : id);
    row.setTenantId(auth.tenantId());
    row.setCode(code);
    row.setName(name);
    String department = Objects.toString(request.departmentId(), "");
    long departmentId = department.isBlank() ? 0 : Long.parseLong(department);
    row.setDepartmentId(departmentId == 0 ? null : departmentId);
    row.setPeriodCount((int) period);
    row.setPeriodUnit(request.unit());
    row.setSymbol(emptyIfBlank(request.symbol()));
    row.setSystemFlag(old != null && Boolean.TRUE.equals(old.getSystemFlag()));
    row.setEnabled(request.enabled() == null || request.enabled());
    row.setRemark(emptyIfBlank(request.remark()));
    if (old == null) {
      row.setCreatorId(auth.userId());
      types.insert(row);
    } else {
      types.update(row);
    }
    return get(row.getId());
  }

  /** 保留原禁止删除系统类型和所有引用的边界；关联读取不引入另一套类型写入权威。 */
  @Transactional
  public void delete(long id) {
    var row = required(id, true);
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.DELETE);
    require(
        !Boolean.TRUE.equals(row.getSystemFlag()),
        MessageContext.text("server.maintenance.message0041"));
    require(
        references.countScheduleReferences(auth.tenantId(), id, "type_id") == 0,
        MessageContext.text("server.maintenance.message0042"));
    require(
        references.cyclePlanTypeReferences(auth.tenantId(), id) == 0,
        MessageContext.text("server.maintenance.message0044"));
    require(
        tasks.taskTypeReferences(auth.tenantId(), id) == 0,
        MessageContext.text("server.maintenance.message0045"));
    require(
        items.countByType(auth.tenantId(), id) == 0,
        MessageContext.text("server.maintenance.message0046"));
    types.delete(auth.tenantId(), id);
  }

  public MaintenanceTypeView view(MaintenanceType row) {
    return new MaintenanceTypeView(
        Long.toString(row.getId()),
        row.getCode(),
        row.getName(),
        Objects.toString(row.getDepartmentId(), ""),
        Objects.toString(row.getCreatorId()),
        row.getRowVersion(),
        Objects.toString(row.getCreatedAt()).replace('T', ' '),
        row.getPeriodCount(),
        row.getPeriodUnit(),
        row.getSymbol(),
        Boolean.TRUE.equals(row.getSystemFlag()),
        Boolean.TRUE.equals(row.getEnabled()),
        row.getRemark(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }

  private static String emptyIfBlank(String value) {
    return value == null || value.isBlank() ? "" : value;
  }

  private static String text(String value) {
    return Objects.toString(value, "").trim();
  }
}
