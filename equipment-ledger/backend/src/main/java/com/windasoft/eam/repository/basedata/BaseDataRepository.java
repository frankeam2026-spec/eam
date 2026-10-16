package com.windasoft.eam.repository.basedata;

import com.baomidou.mybatisplus.core.conditions.query.*;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
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

  private void materialField(String field) {
    if (!Set.of("unit", "category").contains(field)) throw new IllegalArgumentException("无效物料字段");
  }

  public void renameMaterialField(String field, String name, long tenant, String old) {
    materialField(field);
    db.update(
        "UPDATE inv_material SET "
            + field
            + "=?,row_version=row_version+1 WHERE tenant_id=? AND "
            + field
            + "=?",
        name,
        tenant,
        old);
  }

  public int materialReferenceCount(long tenant, String field, String name) {
    materialField(field);
    return db.queryForObject(
        "SELECT COUNT(*) FROM inv_material WHERE tenant_id=? AND " + field + "=?",
        Integer.class,
        tenant,
        name);
  }

  public long equipmentReferenceCount(long tenant, String field, Long id) {
    if (!Set.of("category_id", "unit_id", "use_status_id", "source_id", "function_location_id")
        .contains(field)) throw new IllegalArgumentException("无效设备字段");
    return equipment.selectCount(
        new QueryWrapper<Equipment>().eq("tenant_id", tenant).eq(field, id));
  }

  public long categoryEquipmentCount(long tenant, String name) {
    return equipment.selectCount(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
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

  public long repairReferenceCount(long tenant, String name, boolean cause) {
    var q =
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getDeleted, 0);
    if (cause) q.eq(RepairOrder::getFaultCause, name);
    else q.eq(RepairOrder::getFaultType, name);
    return repairs.selectCount(q);
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
