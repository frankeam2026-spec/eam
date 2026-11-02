package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.common.SqlPredicate;
import java.util.List;
import java.util.function.Consumer;
import org.springframework.stereotype.Repository;

/** 设备台账只读仓储：查询设备主体及媒体投影，权限判断由业务服务承担。 */
@Repository
public class EquipmentQueryRepository {
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final ObjectMapper json;

  public EquipmentQueryRepository(
      EquipmentMapper equipment, EquipmentMediaRepository media, ObjectMapper json) {
    this.equipment = equipment;
    this.media = media;
    this.json = json;
  }

  public Page<Equipment> list(
      long tenant,
      EquipmentListQuery request,
      Consumer<LambdaQueryWrapper<Equipment>> authorizedScope,
      SqlPredicate extensions,
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
      throw new LocalizedArgumentException("server.common.message0009");
    query.last(
        "ORDER BY " + order + " " + request.sort().direction().name() + ",eam_equipment.id ASC");
    return media.hydratePage(
        equipment.selectPage(new Page<>(request.page(), request.size()), query));
  }

  private static void apply(LambdaQueryWrapper<Equipment> query, SqlPredicate predicate) {
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

  /** 维修按旧设备编号关联的只读入口，保持原租户限定及删除语义。 */
  public Equipment findByCode(long tenant, String code) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code));
  }
}
