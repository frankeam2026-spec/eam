package com.windasoft.eam.repository.basedata;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.repository.system.OrgUnitMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BaseDataRepository {
  private final BaseItemMapper mapper;
  private final EquipmentMapper equipment;
  private final RepairOrderMapper repairs;
  private final OrgUnitMapper orgs;
  private final JdbcTemplate db;

  public BaseDataRepository(
      BaseItemMapper mapper,
      EquipmentMapper equipment,
      RepairOrderMapper repairs,
      OrgUnitMapper orgs,
      JdbcTemplate db) {
    this.mapper = mapper;
    this.equipment = equipment;
    this.repairs = repairs;
    this.orgs = orgs;
    this.db = db;
  }

  public List<BaseItem> list(long tenant, String type, Long company) {
    return mapper.selectList(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, type)
            .eq("LOCATION".equals(type) && company != null, BaseItem::getCompanyOrgId, company)
            .orderByAsc(BaseItem::getSortNo)
            .orderByAsc(BaseItem::getId));
  }

  public BaseItem find(long tenant, String type, Long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getId, id)
            .eq(BaseItem::getItemType, type));
  }

  public BaseItem reference(long tenant, String type, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getId, id)
            .last("FOR SHARE"));
  }

  public BaseItem locked(long tenant, String type, long id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getId, id)
            .last("FOR UPDATE"));
  }

  public long codeCount(long tenant, String type, String code, Long current) {
    var q =
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getCode, code)
            .eq(BaseItem::getDeleted, 0);
    if (current != null) q.ne(BaseItem::getId, current);
    return mapper.selectCount(q);
  }

  public long childCount(long tenant, String type, Long id) {
    return mapper.selectCount(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getParentId, id)
            .eq(BaseItem::getDeleted, 0));
  }

  public void insert(BaseItem x) {
    mapper.insert(x);
  }

  public void update(BaseItem x) {
    mapper.updateById(x);
  }

  public void delete(Long id) {
    mapper.deleteById(id);
  }

  public void renameMaterialField(String field, long itemId, String name, long tenant) {
    if (!Set.of("unit", "category").contains(field))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0005");
    db.update(
        "UPDATE inv_material SET "
            + field
            + "=?,row_version=row_version+1 WHERE tenant_id=? AND "
            + field
            + "_id=? AND "
            + field
            + "<>?",
        name,
        tenant,
        itemId,
        name);
  }

  public long dictionaryReferenceCount(long tenant, String type, long id, String name) {
    record Reference(String table, String idColumn, String nameColumn, String extra) {}
    var refs = new ArrayList<Reference>();
    switch (type) {
      case "UNIT" -> refs.add(new Reference("inv_material", "unit_id", "unit", ""));
      case "MATERIAL_CATEGORY" ->
          refs.add(new Reference("inv_material", "category_id", "category", ""));
      case "FAULT_TYPE" -> {
        refs.add(
            new Reference("eam_repair_order", "fault_type_id", "fault_type", " AND deleted=0"));
        refs.add(new Reference("v_single_repair_plan", "fault_type_id", "fault_type", ""));
      }
      case "FAULT_CAUSE" ->
          refs.add(
              new Reference("eam_repair_order", "fault_cause_id", "fault_cause", " AND deleted=0"));
      case "PROCESS_RESULT" ->
          refs.add(
              new Reference(
                  "eam_repair_order",
                  "processing_result_id",
                  "processing_result",
                  " AND deleted=0"));
      default -> {}
    }
    long count =
        db.queryForObject(
            "SELECT COUNT(*) FROM eam_business_value v JOIN eam_business_field f ON f.id=v.field_id"
                + " AND f.tenant_id=v.tenant_id WHERE v.tenant_id=? AND f.source_key=? AND"
                + " v.reference_id=?",
            Long.class,
            tenant,
            "base:" + type,
            Long.toString(id));
    for (var ref : refs)
      count +=
          db.queryForObject(
              "SELECT COUNT(*) FROM "
                  + ref.table()
                  + " WHERE tenant_id=? AND ("
                  + ref.idColumn()
                  + "=? OR ("
                  + ref.idColumn()
                  + " IS NULL AND "
                  + ref.nameColumn()
                  + "=?))"
                  + ref.extra(),
              Long.class,
              tenant,
              id,
              name);
    if (type.equals("CATEGORY"))
      for (String table : List.of("v_maintenance_definition", "v_inspection_definition"))
        count +=
            db.queryForObject(
                "SELECT COUNT(*) FROM "
                    + table
                    + " WHERE tenant_id=? AND kind='standards' AND"
                    + " (JSON_CONTAINS(COALESCE(JSON_EXTRACT(payload,'$.categoryIds'),JSON_ARRAY()),JSON_QUOTE(?))"
                    + " OR JSON_CONTAINS(COALESCE(JSON_EXTRACT(payload,'$.legacyCategoryNames'),JSON_ARRAY()),JSON_QUOTE(?)))",
                Long.class,
                tenant,
                Long.toString(id),
                name);
    return count;
  }

  public long equipmentReferenceCount(long tenant, String field, Long id) {
    if (!Set.of("category_id", "unit_id", "use_status_id", "source_id", "function_location_id")
        .contains(field))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0006");
    return equipment.selectCount(
        new QueryWrapper<Equipment>().eq("tenant_id", tenant).eq(field, id));
  }

  public long categoryEquipmentCount(long tenant, String name) {
    return equipment.selectCount(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .isNull(Equipment::getCategoryId)
            .eq(Equipment::getCategory, name));
  }

  public long locationEquipmentCount(long tenant, BaseItem item) {
    return equipment.selectCount(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getCompanyOrgId, item.getCompanyOrgId())
            .and(
                q ->
                    q.eq(Equipment::getFunctionLocationId, item.getId())
                        .or(
                            legacy ->
                                legacy
                                    .isNull(Equipment::getFunctionLocationId)
                                    .like(Equipment::getFunctionLocation, item.getName()))));
  }

  public List<OrgUnit> companies(long tenant) {
    return orgs.selectList(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .in(OrgUnit::getOrgType, "集团", "公司")
            .orderByAsc(OrgUnit::getId));
  }

  public OrgUnit company(long tenant, Long id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "公司"));
  }
}
