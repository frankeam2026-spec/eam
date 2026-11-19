package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.number;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.domain.maintenance.MaintenanceDefinitionLimits;
import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.dto.maintenance.MaintenanceProjectReference;
import com.windasoft.eam.dto.maintenance.MaintenanceProjectSelection;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardItemView;
import com.windasoft.eam.dto.maintenance.MaintenanceStandardView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceListRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.basedata.BaseDataType;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 负责标准项目来源、适用性及历史快照采集，不承接计划CRUD或生成授权。 */
@Service
public class MaintenanceProjectService {
  private final MaintenanceListRepository standardViews;
  private final ObjectMapper json;
  private final EquipmentQueryRepository equipment;
  private final BaseItemRepository baseItems;
  private final AuthContext auth;
  private final PermissionService permissions;

  public MaintenanceProjectService(
      MaintenanceListRepository standardViews,
      EquipmentQueryRepository equipment,
      BaseItemRepository baseItems,
      ObjectMapper json,
      AuthContext auth,
      PermissionService permissions) {
    this.standardViews = standardViews;
    this.json = json;
    this.equipment = equipment;
    this.baseItems = baseItems;
    this.auth = auth;
    this.permissions = permissions;
  }

  /** 标准及项目事实使用类型化只读投影；每次调用按标准ID缓存，维持原选择顺序和重复键取首项语义。 仅在生成历史快照的明确边界转换项目Map，不能把可编辑标准的动态字段袋当作事实模型。 */
  List<Map<String, Object>> planProjects(
      Map<String, Object> detail, long tenant, Equipment eq, boolean checkAccess) {
    var refs =
        rows(detail.get("projectRefs")).stream()
            .map(
                ref ->
                    new MaintenanceProjectReference(
                        number(ref.get("standardId")), text(ref, "itemId")))
            .toList();
    return planProjects(
        new MaintenanceProjectSelection(
            number(detail.get("typeId")),
            number(detail.get("standardId")),
            detail.containsKey("projectRefs"),
            refs),
        tenant,
        eq,
        checkAccess);
  }

  /** 历史任务生成在此转换项目快照，正式计划验证不构建或传递任意Map。 */
  List<Map<String, Object>> planProjects(
      MaintenanceProjectSelection selection, long tenant, Equipment eq, boolean checkAccess) {
    return resolveProjects(selection, tenant, eq, checkAccess).stream()
        .map(this::snapshot)
        .toList();
  }

  /** 完整检查全部项目及其标准权限；返回数量供计划用例判断，不能短路为只检查首项。 */
  int validateSelection(MaintenanceProjectSelection selection, long tenant, Equipment eq) {
    return resolveProjects(selection, tenant, eq, true).size();
  }

  private record SelectedProject(
      long standardId,
      String itemId,
      String standardName,
      String snapshotKey,
      MaintenanceStandardItemView item) {}

  private Map<String, Object> snapshot(SelectedProject project) {
    var result =
        json.convertValue(project.item(), new TypeReference<LinkedHashMap<String, Object>>() {});
    result.put("id", project.snapshotKey());
    result.put("sourceStandardId", Long.toString(project.standardId()));
    result.put("sourceItemId", project.itemId());
    result.put("sourceStandardName", project.standardName());
    return result;
  }

  private List<SelectedProject> resolveProjects(
      MaintenanceProjectSelection selection, long tenant, Equipment eq, boolean checkAccess) {
    var result = new ArrayList<SelectedProject>();
    var seen = new HashSet<String>();
    var references = selection.references();
    MaintenanceStandardView legacy = null;
    if (!selection.explicit()) {
      legacy = requiredStandard(tenant, selection.standardId());
      references =
          legacy.items().stream()
              .filter(
                  item ->
                      Objects.toString(item.typeId(), "")
                          .trim()
                          .equals(Long.toString(selection.typeId())))
              .map(
                  item ->
                      new MaintenanceProjectReference(
                          selection.standardId(), Objects.toString(item.id(), "").trim()))
              .toList();
    }
    require(
        references.size() <= MaintenanceDefinitionLimits.MAX_PROJECTS_PER_ARRANGEMENT,
        MessageContext.text("server.maintenance.message0015"));
    if (references.isEmpty()) {
      return result;
    }
    var standards = new HashMap<Long, StandardProjects>();
    if (legacy != null) {
      var resolved = standardProjects(legacy, checkAccess);
      // 旧整标准安排检查设备适用范围；显式选项目沿用原规则，不追加限制。
      require(
          applicable(legacy, eq, tenant), MessageContext.text("server.maintenance.message0016"));
      standards.put(selection.standardId(), resolved);
    }
    for (var ref : references) {
      var standard =
          standards.computeIfAbsent(
              ref.standardId() == null ? 0 : ref.standardId(),
              id -> standardProjects(requiredStandard(tenant, id), checkAccess));
      var item = standard.items().get(Objects.toString(ref.itemId(), "").trim());
      if (item == null) {
        throw new LocalizedArgumentException("server.maintenance.message0010");
      }
      require(
          Objects.toString(item.typeId(), "").trim().equals(Long.toString(selection.typeId())),
          MessageContext.text("server.maintenance.message0017"));
      String key =
          Long.toString(ref.standardId() == null ? 0 : ref.standardId())
              + ":"
              + Objects.toString(ref.itemId(), "").trim();
      if (!seen.add(key)) {
        continue;
      }
      result.add(
          new SelectedProject(
              ref.standardId(),
              Objects.toString(item.id(), "").trim(),
              standard.name(),
              selection.explicit() ? key : item.id(),
              item));
    }
    return result;
  }

  private record StandardProjects(String name, Map<String, MaintenanceStandardItemView> items) {}

  private MaintenanceStandardView requiredStandard(long tenant, long id) {
    var view = standardViews.standard(tenant, id);
    if (view == null) {
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.inspection.message0013");
    }
    return view;
  }

  private StandardProjects standardProjects(MaintenanceStandardView standard, boolean checkAccess) {
    if (checkAccess) {
      boolean allowed;
      try {
        allowed =
            permissions
                .maintenance("standards", PermissionAction.VIEW)
                .matches(
                    null,
                    standard.departmentId() == null || standard.departmentId().isBlank()
                        ? null
                        : Long.valueOf(standard.departmentId()),
                    Long.valueOf(standard.creatorId()),
                    auth.userId());
      } catch (ResponseStatusException denied) {
        allowed = false;
      }
      if (!allowed) {
        deny();
      }
    }
    var items = new LinkedHashMap<String, MaintenanceStandardItemView>();
    for (var item : standard.items()) {
      items.putIfAbsent(Objects.toString(item.id(), "").trim(), item);
    }
    return new StandardProjects(standard.name(), items);
  }

  Equipment equipment(long tenant, long id) {
    var row = equipment.findFacts(tenant, id);
    require(row != null, MessageContext.text("server.common.message0064"));
    return row;
  }

  boolean applicable(MaintenanceStandardView standard, Equipment equipment, long tenant) {
    return BaseDataReferenceService.applicable(
        standard.equipmentIds(),
        standard.categoryIds(),
        !standard.legacyCategoryNames().isEmpty(),
        equipment.getId(),
        equipment.getCategoryId(),
        baseItems.itemsByType(tenant, BaseDataType.CATEGORY.code(), false));
  }
}
