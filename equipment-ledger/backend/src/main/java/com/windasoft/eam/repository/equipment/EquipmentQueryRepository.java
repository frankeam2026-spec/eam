package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentListQuery;
import com.windasoft.eam.dto.equipment.EquipmentOwnerView;
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
import java.time.LocalDate;
import java.util.*;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Read-only data access for the ledger; never checks permissions or writes business state. */
@Repository
public class EquipmentQueryRepository {
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
      ObjectMapper json) {
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
      Consumer<LambdaQueryWrapper<Equipment>> authorizedScope) {
    long page = request.page();
    long size = request.size();
    String keyword = request.keyword();
    String category = request.category();
    String state = request.state();
    String department = request.department();
    String company = request.company();
    String model = request.model();
    String serial = request.serial();
    String owner = request.owner();
    String brand = request.brand();
    String useState = request.useState();
    String source = request.source();
    LocalDate acquiredFrom = request.acquiredFrom();
    LocalDate acquiredTo = request.acquiredTo();
    LocalDate warrantyBefore = request.warrantyBefore();
    String sortBy = request.sortBy();
    String direction = request.direction();

    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getDeleted, 0)
            .and(
                has(keyword),
                x ->
                    x.like(Equipment::getEquipmentCode, keyword)
                        .or()
                        .like(Equipment::getAssetNo, keyword)
                        .or()
                        .like(Equipment::getTagCode, keyword)
                        .or()
                        .like(Equipment::getName, keyword)
                        .or()
                        .like(Equipment::getModel, keyword)
                        .or()
                        .like(Equipment::getSerialNo, keyword)
                        .or()
                        .like(Equipment::getDepartment, keyword)
                        .or()
                        .like(Equipment::getOwnerName, keyword))
            .eq(has(category), Equipment::getCategory, category)
            .eq(has(state), Equipment::getEquipmentStatus, state)
            .eq(has(department), Equipment::getDepartment, department)
            .like(has(company), Equipment::getCompany, company)
            .like(has(model), Equipment::getModel, model)
            .like(has(serial), Equipment::getSerialNo, serial)
            .like(has(owner), Equipment::getOwnerName, owner)
            .like(has(brand), Equipment::getBrand, brand)
            .eq(has(useState), Equipment::getUseStatus, useState)
            .eq(has(source), Equipment::getSource, source)
            .ge(acquiredFrom != null, Equipment::getAcquiredAt, acquiredFrom)
            .le(acquiredTo != null, Equipment::getAcquiredAt, acquiredTo)
            .le(warrantyBefore != null, Equipment::getWarrantyUntil, warrantyBefore);
    var fields =
        new java.util.HashMap<String, com.windasoft.eam.common.AdvancedFilters.Field<Equipment>>();
    fields.put(
        "code",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getEquipmentCode, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "name",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getName, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "state",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getEquipmentStatus, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "useState",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getUseStatus, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "assetNo",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getAssetNo, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "category",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getCategory, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "department",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getDepartment, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "company",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getCompany, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "functionLocation",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getFunctionLocation, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "model",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getModel, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "serial",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getSerialNo, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "owner",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getOwnerName, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "brand",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getBrand, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "source",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getSource, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "unit",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getUnitName, com.windasoft.eam.common.AdvancedFilters.Kind.CHOICE));
    fields.put(
        "supplier",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getSupplier, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "tagCode",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getTagCode, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "level",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getEquipmentLevel, com.windasoft.eam.common.AdvancedFilters.Kind.TEXT));
    fields.put(
        "acquiredAt",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getAcquiredAt, com.windasoft.eam.common.AdvancedFilters.Kind.DATE));
    fields.put(
        "warranty",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getWarrantyUntil, com.windasoft.eam.common.AdvancedFilters.Kind.DATE));
    fields.put(
        "inServiceDate",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getInServiceDate, com.windasoft.eam.common.AdvancedFilters.Kind.DATE));
    fields.put(
        "retirementDate",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getExpectedRetirementDate,
            com.windasoft.eam.common.AdvancedFilters.Kind.DATE));
    fields.put(
        "amount",
        new com.windasoft.eam.common.AdvancedFilters.Field<>(
            Equipment::getAcquiredAmount, com.windasoft.eam.common.AdvancedFilters.Kind.NUMBER));
    com.windasoft.eam.common.AdvancedFilters.apply(q, request.advanced(), fields, json);
    if (request.scopeType() != null && !request.scopeType().isBlank()) {
      var ids =
          Arrays.stream(Objects.toString(request.scopeIds(), "").split(","))
              .filter(v -> !v.isBlank())
              .map(Long::valueOf)
              .toList();
      if (ids.isEmpty() || ids.size() > 5000)
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0008");
      switch (request.scopeType()) {
        case "organization" ->
            q.and(
                w ->
                    w.in(Equipment::getDepartmentOrgId, ids)
                        .or()
                        .in(Equipment::getCompanyOrgId, ids));
        case "category" -> q.in(Equipment::getCategoryId, ids);
        case "location" -> {
          q.in(Equipment::getFunctionLocationId, ids);
          if (request.scopeCompanyId() != null && !request.scopeCompanyId().isBlank())
            q.eq(Equipment::getCompanyOrgId, Long.valueOf(request.scopeCompanyId()));
        }
        default ->
            throw new com.windasoft.eam.common.LocalizedArgumentException(
                "server.equipment.message0008");
      }
    }
    authorizedScope.accept(q);
    if (!sortBy.startsWith("custom:"))
      switch (sortBy) {
        case "equipmentCode", "code" -> order(q, direction, Equipment::getEquipmentCode);
        case "name" -> order(q, direction, Equipment::getName);
        case "category" -> order(q, direction, Equipment::getCategory);
        case "model" -> order(q, direction, Equipment::getModel);
        case "serialNo" -> order(q, direction, Equipment::getSerialNo);
        case "department" -> order(q, direction, Equipment::getDepartment);
        case "ownerName" -> order(q, direction, Equipment::getOwnerName);
        case "brand" -> order(q, direction, Equipment::getBrand);
        case "equipmentStatus" -> order(q, direction, Equipment::getEquipmentStatus);
        case "useStatus" -> order(q, direction, Equipment::getUseStatus);
        case "source" -> order(q, direction, Equipment::getSource);
        case "acquiredAt" -> order(q, direction, Equipment::getAcquiredAt);
        case "acquiredAmount" -> order(q, direction, Equipment::getAcquiredAmount);
        case "warrantyUntil" -> order(q, direction, Equipment::getWarrantyUntil);
        default -> order(q, direction, Equipment::getCreatedAt);
      }
    if (!sortBy.startsWith("custom:")) q.orderByAsc(Equipment::getId);
    return media.hydratePage(
        equipment.selectPage(new Page<>(Math.max(1, page), Math.min(10000, Math.max(1, size))), q));
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

  public List<EquipmentOwnerView> owners(long tenant, Long id) {
    if (id == null) return List.of();
    return db.query(
        "SELECT user_id,owner_name FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?"
            + " ORDER BY sort_no,user_id",
        (rs, row) -> new EquipmentOwnerView(rs.getString("user_id"), rs.getString("owner_name")),
        tenant,
        id);
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
    var result =
        db.queryForList(
            "SELECT CAST(e.id AS CHAR) equipmentId,e.equipment_code code,e.name,e.model,e.brand"
                + " FROM eam_equipment_child c JOIN eam_equipment e ON e.tenant_id=c.tenant_id AND"
                + " e.id=c.child_id WHERE c.tenant_id=? AND c.parent_id=? AND e.deleted=0 ORDER BY"
                + " c.sort_no,e.id",
            tenant,
            id);
    appendLegacy(result, tenant, id, "child");
    return result;
  }

  public List<Map<String, Object>> bom(long tenant, long id) {
    var result =
        db.queryForList(
            "SELECT CAST(b.material_id AS CHAR) materialId,COALESCE(m.code,b.code)"
                + " code,COALESCE(m.name,b.name) name,COALESCE(m.model,b.model)"
                + " specification,COALESCE(m.unit,b.unit) unit,CAST(b.quantity AS CHAR) quantity"
                + " FROM eam_equipment_bom b LEFT JOIN inv_material m ON m.tenant_id=b.tenant_id"
                + " AND m.id=b.material_id WHERE b.tenant_id=? AND b.equipment_id=? ORDER BY"
                + " b.sort_no,b.material_id",
            tenant,
            id);
    appendLegacy(result, tenant, id, "bom");
    return result;
  }

  private void appendLegacy(List<Map<String, Object>> result, long tenant, long id, String kind) {
    for (var r :
        db.queryForList(
            "SELECT source_index,snapshot_json,reason FROM eam_equipment_relation_legacy WHERE"
                + " tenant_id=? AND equipment_id=? AND kind=? ORDER BY source_index",
            tenant,
            id,
            kind))
      try {
        Map<String, Object> snapshot =
            json.readValue(
                r.get("snapshot_json").toString(),
                new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
        snapshot.put("legacy", true);
        snapshot.put("legacyIndex", r.get("source_index"));
        snapshot.put("legacyReason", r.get("reason"));
        result.add(snapshot);
      } catch (Exception e) {
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.equipment.message0009");
      }
  }

  private static boolean has(String s) {
    return s != null && !s.isBlank();
  }

  private static <T> void order(
      LambdaQueryWrapper<Equipment> q,
      String direction,
      com.baomidou.mybatisplus.core.toolkit.support.SFunction<Equipment, T> col) {
    if ("asc".equalsIgnoreCase(direction)) q.orderByAsc(col);
    else q.orderByDesc(col);
  }
}
