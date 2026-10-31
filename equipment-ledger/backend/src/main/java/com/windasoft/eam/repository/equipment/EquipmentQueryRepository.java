package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.repository.repair.RepairWorkflowMapper;
import com.windasoft.eam.repository.system.OrgUnitMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
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
  private final OrgUnitMapper organizations;
  private final SystemUserMapper users;
  private final RepairOrderMapper repairs;
  private final RepairWorkflowMapper repairWorkflow;
  private final JdbcTemplate db;
  private final ObjectMapper json;


  public EquipmentQueryRepository(
      EquipmentMapper equipment,
      EquipmentMediaRepository media,
      BaseItemMapper baseItems,
      OrgUnitMapper organizations,
      SystemUserMapper users,
      RepairOrderMapper repairs,
      RepairWorkflowMapper repairWorkflow,
      JdbcTemplate db,
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
    this.organizations = organizations;
    this.users = users;
    this.repairs = repairs;
    this.repairWorkflow = repairWorkflow;
    this.db = db;
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

  public List<OrgUnit> organizations(long tenant, boolean activeOnly) {
    return organizations.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(activeOnly, OrgUnit::getStatus, 1));
  }

  public Page<SystemUser> ownerCandidates(
      long tenant, com.windasoft.eam.dto.common.ListQuery query) {
    var where =
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1);
    if (!query.keyword().isBlank())
      where.and(
          w ->
              w.like(SystemUser::getDisplayName, query.keyword())
                  .or()
                  .like(SystemUser::getAccount, query.keyword()));
    where.orderByAsc(SystemUser::getDisplayName).orderByAsc(SystemUser::getId);
    return users.selectPage(new Page<>(query.page(), query.size()), where);
  }

  public List<SystemUser> employees(long tenant) {
    return users.selectList(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getStatus, 1));
  }

  public BaseItem baseItem(Long id) {
    return baseItems.selectById(id);
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


  public List<RepairOrder> repairs(long tenant, String code) {
    return repairs.selectList(
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getEquipmentCode, code)
            .orderByDesc(RepairOrder::getReportedAt));
  }

  public List<Map<String, Object>> history(long tenant, long id) {
    return db.queryForList(
        "SELECT record_type AS type,record_no AS number,occurred_at AS"
            + " time,description,operator_name AS person,status,result FROM eam_equipment_history"
            + " WHERE tenant_id=? AND equipment_id=? ORDER BY occurred_at DESC,id DESC",
        tenant,
        id);
  }

  public List<Map<String, Object>> installedSpares(long tenant, long id) {
    var result = new ArrayList<Map<String, Object>>(repairWorkflow.installed(tenant, id));
    result.addAll(
        db.queryForList(
            "SELECT CAST(u.id AS CHAR)"
                + " id,m.code,m.name,m.model,u.quantity,u.status,DATE_FORMAT(u.created_at,'%Y-%m-%d"
                + " %H:%i') installedAt FROM inv_usage u JOIN inv_material m ON"
                + " m.tenant_id=u.tenant_id AND m.id=u.material_id WHERE u.tenant_id=? AND"
                + " u.equipment_id=? AND u.status='在用' ORDER BY u.created_at DESC,u.id DESC",
            tenant, id));
    return result;
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
