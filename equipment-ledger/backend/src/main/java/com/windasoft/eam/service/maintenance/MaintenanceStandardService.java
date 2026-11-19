package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.domain.maintenance.MaintenancePersonRole;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRequest;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardRow;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.entity.inventory.LegacySpareStock;
import com.windasoft.eam.entity.maintenance.MaintenanceItemFile;
import com.windasoft.eam.entity.maintenance.MaintenanceItemOption;
import com.windasoft.eam.entity.maintenance.MaintenanceItemSpare;
import com.windasoft.eam.entity.maintenance.MaintenanceStandard;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardCategory;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardCategoryLabel;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardEquipment;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardItem;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardPerson;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.inventory.LegacySpareStockRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceDeviceProjectRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceItemFileRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceItemOptionRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceItemSpareRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardCategoryLabelRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardCategoryRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardEquipmentRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardItemRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardPersonRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceStandardRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceTypeRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.business.BusinessQueryService;
import com.windasoft.eam.service.common.DefinitionNumberService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 标准的授权、项目规则及完整保存事务；各关系表分别交给所属仓储，读取复用原关系投影。 */
@Service
@RequiredArgsConstructor
public class MaintenanceStandardService {
  private static final String KIND = "standards";
  private final MaintenanceStandardRepository standards;
  private final MaintenanceStandardItemRepository items;
  private final MaintenanceStandardPersonRepository people;
  private final MaintenanceStandardEquipmentRepository equipmentLinks;
  private final MaintenanceStandardCategoryRepository categoryLinks;
  private final MaintenanceStandardCategoryLabelRepository categoryLabels;
  private final MaintenanceItemOptionRepository options;
  private final MaintenanceItemFileRepository files;
  private final MaintenanceItemSpareRepository spares;
  private final MaintenanceTypeRepository types;
  private final MaintenanceListRepository queries;
  private final MaintenanceRepository references;
  private final MaintenanceDeviceProjectRepository projectLinks;
  private final EquipmentQueryRepository equipment;
  private final LegacySpareStockRepository stocks;
  private final OrgUnitRepository organizations;
  private final MaintenanceValidationService validation;
  private final MaintenanceListService lists;
  private final BaseDataReferenceService baseData;
  private final BusinessQueryService customQueries;
  private final DefinitionNumberService numbers;
  private final AuthContext auth;
  private final PermissionService permissions;

  @Transactional(readOnly = true)
  public MaintenanceStandardView get(long id) {
    var result = queries.standard(auth.tenantId(), id);
    if (result == null) {
      throw missing();
    }
    if (!permissions
        .maintenance(KIND, PermissionAction.VIEW)
        .matches(
            null,
            nullableId(result.departmentId()),
            nullableId(result.creatorId()),
            auth.userId())) {
      deny();
    }
    return result;
  }

  @Transactional(readOnly = true)
  public PageResult<MaintenanceStandardRow> query(ListQuery query, boolean export) {
    var access = lists.access(KIND, query, export);
    var selected =
        queries.select(
            auth.tenantId(),
            auth.userId(),
            KIND,
            access.operation(),
            export,
            access.organizations(),
            query,
            customQueries.predicate(null, query.conditions(), "t"),
            null);
    if (export) {
      var read =
          queries.select(
              auth.tenantId(),
              auth.userId(),
              KIND,
              access.read(),
              false,
              access.organizations(),
              query,
              SqlPredicate.of("1=1"),
              null);
      selected = queries.restrict(selected, read.predicate());
    }
    return new PageResult<>(
        queries.standardRows(selected, query), query.page(), query.size(), queries.count(selected));
  }

  @Transactional(readOnly = true)
  public List<MaintenanceStandardRow> all(String conditions) {
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.VIEW);
    var request = ListQuery.from(Map.of("conditions", conditions));
    var result = new ArrayList<MaintenanceStandardRow>();
    for (int page = 1; ; page++) {
      var batch = query(request.at(page, 200), false);
      result.addAll(batch.data());
      if ((long) page * 200 >= batch.page().total()) {
        return result;
      }
    }
  }

  /** 先锁定原标准并复核原/目标范围，分类共享锁与项目类型锁沿用原顺序；所有替换在同一事务中完成。 */
  @Transactional
  public MaintenanceStandardView save(Long id, MaintenanceStandardRequest request) {
    long tenant = auth.tenantId();
    var action = id == null ? PermissionAction.CREATE : PermissionAction.EDIT;
    permissions.require(PermissionService.maintenanceMenu(KIND), action);
    var old = id == null ? null : standards.find(tenant, id, true);
    if (id != null && old == null) {
      throw missing();
    }
    var scope = permissions.maintenance(KIND, action);
    if (old != null) {
      if (!scope.matches(null, old.getDepartmentId(), old.getCreatorId(), auth.userId())) {
        deny();
      }
      require(request.rowVersion() != null, MessageContext.text("server.common.message0018"));
      VersionGuard.check(old.getRowVersion(), request.rowVersion());
    }
    validation.validateName(request);
    String name = request.name();
    require(
        request.departmentId() != null && request.departmentId() > 0,
        MessageContext.text("ui.equipment.text0099"));
    require(
        organizations.organization(tenant, request.departmentId()) != null,
        MessageContext.text("server.maintenance.message0021"));
    if (!scope.matches(null, request.departmentId(), auth.userId(), auth.userId())) {
      deny();
    }
    var categories =
        baseData.categories(
            tenant,
            request.categoryIds(),
            !values(request.categoryNames()).isEmpty(),
            !values(request.legacyCategoryNames()).isEmpty());
    validation.validate(request);
    var requested = values(request.items());
    require(!requested.isEmpty(), MessageContext.text("server.maintenance.message0023"));
    long standard = id == null ? IdWorker.getId() : id;
    var typeCache = new HashMap<Long, MaintenanceType>();
    var stockCache = new HashMap<Long, LegacySpareStock>();
    var keys = new HashSet<String>();
    var canonicalKeys = new HashSet<String>();
    var prepared = new ArrayList<MaintenanceStandardItem>();
    var optionRows = new ArrayList<MaintenanceItemOption>();
    var fileRows = new ArrayList<MaintenanceItemFile>();
    var spareRows = new ArrayList<MaintenanceItemSpare>();
    for (var input : requested) {
      require(input.typeId() != null, MessageContext.text("server.maintenance.message0018"));
      var type = typeCache.computeIfAbsent(input.typeId(), key -> types.find(tenant, key, true));
      if (type == null) {
        throw missing();
      }
      require(
          Boolean.TRUE.equals(type.getEnabled()),
          MessageContext.text("server.maintenance.message0018"));
      String valueType = text(input.valueType());
      require(
          Set.of("选项", "数值", "文本").contains(valueType),
          MessageContext.text("server.maintenance.message0025"));
      String key = text(input.id()).isEmpty() ? Long.toString(IdWorker.getId()) : input.id();
      require(canonicalKeys.add(text(key)), MessageContext.text("server.maintenance.message0026"));
      keys.add(key);
      var lower = decimal(input.lower());
      var upper = decimal(input.upper());
      if (valueType.equals("数值") && lower != null && upper != null) {
        require(lower.compareTo(upper) <= 0, MessageContext.text("server.inspection.message0049"));
      }
      var choices = values(input.options());
      var normal = values(input.normalOptions());
      require(
          choices.stream().noneMatch(Objects::isNull) && normal.stream().noneMatch(Objects::isNull),
          MessageContext.text("server.maintenance.message0027"));
      if (valueType.equals("选项")) {
        require(choices.size() >= 2, MessageContext.text("server.maintenance.message0027"));
        require(
            !normal.isEmpty() && choices.containsAll(normal),
            MessageContext.text("server.maintenance.message0028"));
      }
      validation.validFileReferences(input.references(), false);
      var summary = new ArrayList<String>();
      int order = 0;
      for (var spare : values(input.spareRequirements())) {
        require(spare.stockId() != null, MessageContext.text("server.maintenance.message0029"));
        var stock =
            stockCache.computeIfAbsent(spare.stockId(), stockId -> stocks.find(tenant, stockId));
        require(stock != null, MessageContext.text("server.maintenance.message0029"));
        if (!scope.all() && !scope.companyIds().contains(stock.getCompanyId())) {
          deny();
        }
        var quantity = spare.quantity();
        require(
            quantity != null && quantity.signum() > 0,
            MessageContext.text("server.maintenance.message0030"));
        summary.add(
            text(stock.getName())
                + " · "
                + text(stock.getModel())
                + " × "
                + quantity.stripTrailingZeros().toPlainString());
        var row = new MaintenanceItemSpare();
        row.setTenantId(tenant);
        row.setStandardId(standard);
        row.setItemKey(key);
        row.setSortNo(order++);
        row.setStockId(spare.stockId());
        row.setQuantity(quantity);
        spareRows.add(row);
      }
      var row = new MaintenanceStandardItem();
      row.setTenantId(tenant);
      row.setStandardId(standard);
      row.setItemKey(key);
      row.setSortNo(prepared.size());
      row.setCode(text(input.code()).isEmpty() ? "MI-" + IdWorker.getId() : input.code());
      row.setModuleName(empty(input.module()));
      row.setName(input.name());
      row.setContent(input.content());
      row.setTypeId(input.typeId());
      row.setValueType(input.valueType());
      row.setLowerBound(lower);
      row.setUpperBound(upper);
      row.setRequired(Boolean.TRUE.equals(input.required()));
      row.setPhotoRequired(Boolean.TRUE.equals(input.photoRequired()));
      row.setRemark(empty(input.remark()));
      row.setSpareDescription(String.join("；", summary));
      prepared.add(row);
      order = 0;
      for (String value : choices) {
        var option = new MaintenanceItemOption();
        option.setTenantId(tenant);
        option.setStandardId(standard);
        option.setItemKey(key);
        option.setSortNo(order++);
        option.setOptionValue(value);
        option.setNormalValue(normal.contains(value));
        optionRows.add(option);
      }
      order = 0;
      for (var file : values(input.references())) {
        var reference = new MaintenanceItemFile();
        reference.setTenantId(tenant);
        reference.setStandardId(standard);
        reference.setItemKey(key);
        reference.setSortNo(order++);
        reference.setFileId(
            text(file.id()).isEmpty()
                ? text(file.url()).substring(text(file.url()).lastIndexOf('/') + 1)
                : file.id());
        reference.setNameSnapshot(Objects.toString(file.name(), ""));
        reference.setContentType(Objects.toString(file.contentType(), ""));
        reference.setSizeBytes(file.size() == null ? 0 : file.size());
        reference.setUrlSnapshot(Objects.toString(file.url(), ""));
        fileRows.add(reference);
      }
    }
    var selectedEquipment = new LinkedHashSet<>(values(request.equipmentIds()));
    for (Long key : selectedEquipment) {
      var device = key == null ? null : equipment.findFacts(tenant, key);
      require(device != null, MessageContext.text("server.common.message0064"));
      if (!scope.matches(
          device.getCompanyOrgId(),
          device.getDepartmentOrgId(),
          device.getOwnerUserId(),
          auth.userId())) {
        deny();
      }
    }
    String code = text(request.code());
    if (code.isEmpty()) {
      code = numbers.next(tenant, "MS");
    }
    var header = new MaintenanceStandard();
    header.setId(standard);
    header.setTenantId(tenant);
    header.setCode(code);
    header.setName(name);
    header.setDepartmentId(request.departmentId());
    header.setEnabled(request.enabled() == null || request.enabled());
    header.setRemark(empty(request.remark()));
    if (old == null) {
      header.setCreatorId(auth.userId());
      standards.insert(header);
    } else {
      standards.update(header);
    }
    replacePeople(tenant, standard, request.owners(), request.reviewers());
    equipmentLinks.clear(tenant, standard);
    var links = new ArrayList<MaintenanceStandardEquipment>();
    int position = 0;
    for (Long key : selectedEquipment) {
      var link = new MaintenanceStandardEquipment();
      link.setTenantId(tenant);
      link.setStandardId(standard);
      link.setEquipmentId(key);
      link.setSortNo(position++);
      links.add(link);
    }
    equipmentLinks.insertAll(links);
    categoryLinks.clear(tenant, standard);
    categoryLabels.clear(tenant, standard);
    var categoryRows = new ArrayList<MaintenanceStandardCategory>();
    var labelRows = new ArrayList<MaintenanceStandardCategoryLabel>();
    position = 0;
    for (var category : categories) {
      var link = new MaintenanceStandardCategory();
      link.setTenantId(tenant);
      link.setStandardId(standard);
      link.setCategoryId(category.getId());
      link.setSortNo(position);
      categoryRows.add(link);
      var label = new MaintenanceStandardCategoryLabel();
      label.setTenantId(tenant);
      label.setStandardId(standard);
      label.setLabelKind("display");
      label.setSortNo(position++);
      label.setNameSnapshot(category.getName());
      labelRows.add(label);
    }
    categoryLinks.insertAll(categoryRows);
    categoryLabels.insertAll(labelRows);
    var previous = items.list(tenant, List.of(standard));
    var oldKeys =
        previous.stream().map(MaintenanceStandardItem::getItemKey).collect(Collectors.toSet());
    var removed = oldKeys.stream().filter(key -> !keys.contains(key)).toList();
    for (String key : removed) {
      require(
          projectLinks.activeReferences(tenant, standard, key) == 0,
          MessageContext.text("server.maintenance.message0006"));
    }
    items.delete(tenant, standard, removed);
    var additions = new ArrayList<MaintenanceStandardItem>();
    for (var row : prepared) {
      if (oldKeys.contains(row.getItemKey())) {
        items.update(row);
      } else {
        additions.add(row);
      }
    }
    // 新项目按表合并到一个框架批次，避免每个项目建立一次批次及驱动只读状态探测。
    items.insertAll(additions);
    // 标准编辑是完整明细替换；项目主键保留，子表只清理当前标准，不影响历史任务快照。
    options.clear(tenant, standard);
    files.clear(tenant, standard);
    spares.clear(tenant, standard);
    options.insertAll(optionRows);
    files.insertAll(fileRows);
    spares.insertAll(spareRows);
    return get(standard);
  }

  private void replacePeople(long tenant, long standard, List<Long> owners, List<Long> reviewers) {
    people.clear(tenant, standard);
    var rows = new ArrayList<MaintenanceStandardPerson>();
    for (var role : List.of(MaintenancePersonRole.OWNER, MaintenancePersonRole.REVIEWER)) {
      int position = 0;
      for (Long user :
          new LinkedHashSet<>(values(role == MaintenancePersonRole.OWNER ? owners : reviewers))) {
        require(user != null && user > 0, MessageContext.text("server.maintenance.message0013"));
        var row = new MaintenanceStandardPerson();
        row.setTenantId(tenant);
        row.setStandardId(standard);
        row.setRoleCode(role.code());
        row.setUserId(user);
        row.setSortNo(position++);
        rows.add(row);
      }
    }
    people.insertAll(rows);
  }

  @Transactional
  public void delete(long id) {
    var row = standards.find(auth.tenantId(), id, true);
    if (row == null) {
      throw missing();
    }
    permissions.require(PermissionService.maintenanceMenu(KIND), PermissionAction.DELETE);
    if (!permissions
        .maintenance(KIND, PermissionAction.DELETE)
        .matches(null, row.getDepartmentId(), row.getCreatorId(), auth.userId())) {
      deny();
    }
    require(
        references.countScheduleReferences(auth.tenantId(), id, "standard_id") == 0,
        MessageContext.text("server.maintenance.message0042"));
    require(
        references.countProjectReferences(auth.tenantId(), Long.toString(id)) == 0,
        MessageContext.text("server.maintenance.message0043"));
    standards.delete(auth.tenantId(), id);
  }

  private static LocalizedStatusException missing() {
    return new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
  }

  private static Long nullableId(String value) {
    return value == null || value.isBlank() ? null : Long.parseLong(value);
  }

  private static String text(String value) {
    return Objects.toString(value, "").trim();
  }

  private static String empty(String value) {
    return value == null || value.isBlank() ? "" : value;
  }

  private static BigDecimal decimal(String value) {
    return text(value).isEmpty() ? null : new BigDecimal(text(value));
  }

  private static <T> List<T> values(List<T> value) {
    return value == null ? List.of() : value;
  }
}
