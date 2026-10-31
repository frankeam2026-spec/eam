package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

/** 台账只读数据访问，不授予权限，也不写入业务状态。 */
@Repository
public class EquipmentQueryRepository {
  private final EquipmentRelationLegacyRepository legacyRelations;
  private final EquipmentChildRepository childRelations;
  private final EquipmentBomRepository bomRelations;
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final BaseItemMapper baseItems;
  private final ObjectMapper json;


  public EquipmentQueryRepository(
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      BaseItemMapper baseItems,
      ObjectMapper json,
      EquipmentChildRepository childRelations,
      EquipmentBomRepository bomRelations,
      EquipmentRelationLegacyRepository legacyRelations) {
    this.legacyRelations = legacyRelations;
    this.childRelations = childRelations;
    this.bomRelations = bomRelations;
    this.equipment = equipment;
    this.media = media;
    this.baseItems = baseItems;
    this.json = json;
  }

  public Page<Equipment> list(
      long tenant,
      EquipmentListQuery request,
      Consumer<LambdaQueryWrapper<Equipment>> authorizedScope,
      com.windasoft.eam.repository.common.SqlPredicate extensions,
      String extensionOrder) {
    var query =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getDeleted, 0);
    if (!request.keyword().isBlank()) apply(query, EquipmentQueryFields.keyword(request.keyword()));
    apply(query, EquipmentQueryFields.filters(json, request.conditions()));
    applyScope(query, request.scope());
    authorizedScope.accept(query);
    apply(query, extensions);
    String order =
        request.sort().field().startsWith("custom:")
            ? extensionOrder
            : EquipmentQueryFields.order(request.sort());
    if (order == null || order.isBlank())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0009");
    query.last(
        "ORDER BY " + order + " " + request.sort().direction().name() + ",eam_equipment.id ASC");
    return media.hydratePage(
        equipment.selectPage(new Page<>(request.page(), request.size()), query));
  }

  private static void apply(
      LambdaQueryWrapper<Equipment> query,
      com.windasoft.eam.repository.common.SqlPredicate predicate) {
    var sql = new StringBuilder();
    int index = 0;
    for (char c : predicate.sql().toCharArray()) {
      sql.append(c == '?' ? "{" + (index++) + "}" : String.valueOf(c));
    }
    // 关键词可能包含OR，必须作为完整条件与租户和授权范围相交。
    query.apply("(" + sql + ")", predicate.arguments().toArray());
  }

  private static void applyScope(
      LambdaQueryWrapper<Equipment> query, EquipmentListQuery.Scope scope) {
    switch (scope.type()) {
      case NONE -> {}
      case ORGANIZATION ->
          query.and(
              w ->
                  w.in(Equipment::getDepartmentOrgId, scope.ids())
                      .or()
                      .in(Equipment::getCompanyOrgId, scope.ids()));
      case CATEGORY -> query.in(Equipment::getCategoryId, scope.ids());
      case LOCATION -> {
        query.in(Equipment::getFunctionLocationId, scope.ids());
        if (scope.companyId() != null) query.eq(Equipment::getCompanyOrgId, scope.companyId());
      }
    }
  }

  public Equipment find(long tenant, long id) {
    return media.hydrate(
        equipment.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, tenant)
                .eq(Equipment::getId, id)));
  }

  /** 批量读取设备关系所需的设备实体，避免逐个设备编号回表。 */
  public Map<Long, Equipment> findByIds(long tenant, List<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var rows =
        equipment.selectList(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, tenant)
                .eq(Equipment::getDeleted, 0)
                .in(Equipment::getId, ids));
    media.hydrate(rows);
    var result = new HashMap<Long, Equipment>();
    for (var row : rows) result.put(row.getId(), row);
    return Map.copyOf(result);
  }

  public List<Equipment> visibleOrganizations(
      long tenant, Consumer<LambdaQueryWrapper<Equipment>> authorizedScope) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getDeleted, 0)
            .select(Equipment::getCompanyOrgId, Equipment::getDepartmentOrgId);
    authorizedScope.accept(q);
    return equipment.selectList(q);
  }

  public record PageRelations(
      Map<Long, BaseItem> dictionary,
      Map<Long, List<Map<String, Object>>> children,
      Map<Long, List<Map<String, Object>>> bom) {}

  public PageRelations pageRelations(long tenant, List<Equipment> rows) {
    var dictionary = new HashMap<Long, BaseItem>();
    var children = new HashMap<Long, List<Map<String, Object>>>();
    var bom = new HashMap<Long, List<Map<String, Object>>>();
    if (rows.isEmpty()) return new PageRelations(dictionary, children, bom);
    var ids = rows.stream().map(Equipment::getId).toList();
    var requested = new HashSet<Long>();
    var pending = new HashSet<Long>();
    for (var row : rows)
      for (Long id :
          Arrays.asList(
              row.getCategoryId(),
              row.getUseStatusId(),
              row.getUnitId(),
              row.getSourceId(),
              row.getFunctionLocationId())) if (id != null) pending.add(id);
    while (!pending.isEmpty()) {
      var next = new ArrayList<>(pending);
      requested.addAll(next);
      pending.clear();
      for (int start = 0; start < next.size(); start += 500)
        for (var item :
            baseItems.selectList(
                new LambdaQueryWrapper<BaseItem>()
                    .eq(BaseItem::getTenantId, tenant)
                    .in(
                        BaseItem::getId,
                        next.subList(start, Math.min(start + 500, next.size()))))) {
          dictionary.put(item.getId(), item);
          if (item.getParentId() != null && !requested.contains(item.getParentId()))
            pending.add(item.getParentId());
        }
    }
    for (int start = 0; start < ids.size(); start += 500) {
      var batch = ids.subList(start, Math.min(start + 500, ids.size()));
      group(children, childRelations.viewRows(tenant, batch));
      group(bom, bomRelations.viewRows(tenant, batch));
      for (var row : legacyRelations.list(tenant, batch)) {
        var target = row.getKind().equals("child") ? children : bom;
        target
            .computeIfAbsent(row.getEquipmentId(), key -> new ArrayList<>())
            .add(legacyRelations.view(row));
      }
    }
    return new PageRelations(dictionary, children, bom);
  }

  private static void group(
      Map<Long, List<Map<String, Object>>> target, List<Map<String, Object>> rows) {
    for (var row : rows) {
      var value = new LinkedHashMap<>(row);
      long id = ((Number) value.remove("__equipment_id")).longValue();
      target.computeIfAbsent(id, key -> new ArrayList<>()).add(value);
    }
  }


  public List<Map<String, Object>> children(long tenant, long id) {
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    group(grouped, childRelations.viewRows(tenant, List.of(id)));
    var result = new ArrayList<>(grouped.getOrDefault(id, List.of()));
    appendLegacy(result, tenant, id, "child");
    return result;
  }

  public List<Map<String, Object>> bom(long tenant, long id) {
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    group(grouped, bomRelations.viewRows(tenant, List.of(id)));
    var result = new ArrayList<>(grouped.getOrDefault(id, List.of()));
    appendLegacy(result, tenant, id, "bom");
    return result;
  }

  private void appendLegacy(List<Map<String, Object>> result, long tenant, long id, String kind) {
    for (var row : legacyRelations.list(tenant, id, kind)) result.add(legacyRelations.view(row));
  }
}
