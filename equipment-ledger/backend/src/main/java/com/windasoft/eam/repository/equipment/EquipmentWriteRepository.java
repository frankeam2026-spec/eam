package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import com.windasoft.eam.repository.system.OrgUnitMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentWriteRepository {
  private final EquipmentMapper equipment;
  private final BaseItemMapper base;
  private final OrgUnitMapper orgs;
  private final SystemUserMapper users;
  private final NumberCounterMapper counter;
  private final JdbcTemplate db;

  public EquipmentWriteRepository(
      EquipmentMapper equipment,
      BaseItemMapper base,
      OrgUnitMapper orgs,
      SystemUserMapper users,
      NumberCounterMapper counter,
      JdbcTemplate db) {
    this.equipment = equipment;
    this.base = base;
    this.orgs = orgs;
    this.users = users;
    this.counter = counter;
    this.db = db;
  }

  public int insert(Equipment e) {
    return equipment.insert(e);
  }

  public int update(Equipment e) {
    return equipment.updateById(e);
  }

  public int delete(Long id) {
    return equipment.deleteById(id);
  }

  public Equipment find(long tenant, Long id) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getId, id));
  }

  public long countCode(long tenant, String code, Long currentId) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code);
    if (currentId != null) q.ne(Equipment::getId, currentId);
    return equipment.selectCount(q);
  }

  public void nextEquipmentNumber(String year, long minimum) {
    counter.nextEquipmentNumber(year, minimum);
  }

  public long maxEquipmentNumber(String year) {
    String prefix = "EQ-" + year + "-";
    // The counter is shared by this database; include historical rows when advancing it.
    Long maximum = db.queryForObject(
        "SELECT COALESCE(MAX(CAST(SUBSTRING(equipment_code, ?) AS UNSIGNED)),0)"
            + " FROM eam_equipment WHERE equipment_code LIKE ? AND equipment_code REGEXP ?",
        Long.class,
        prefix.length() + 1,
        prefix + "%",
        "^" + prefix + "[0-9]+$");
    return maximum == null ? 0L : maximum;
  }

  public long currentValue() {
    return counter.currentValue();
  }

  public BaseItem baseItem(Long id) {
    return base.selectById(id);
  }

  public BaseItem baseItemInTenant(long tenant, Long id) {
    return base.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getId, id));
  }

  public BaseItem activeBase(long tenant, String id, String type) {
    return base.selectOne(
        new LambdaQueryWrapper<BaseItem>()
            .eq(BaseItem::getTenantId, tenant)
            .eq(BaseItem::getId, id)
            .eq(BaseItem::getItemType, type)
            .eq(BaseItem::getStatus, 1));
  }

  public OrgUnit activeDepartment(long tenant, String id) {
    return orgs.selectOne(
        new LambdaQueryWrapper<OrgUnit>()
            .eq(OrgUnit::getTenantId, tenant)
            .eq(OrgUnit::getId, id)
            .eq(OrgUnit::getOrgType, "部门")
            .eq(OrgUnit::getStatus, 1));
  }

  public OrgUnit organization(Long id) {
    return orgs.selectById(id);
  }

  public SystemUser user(long tenant, Long id) {
    return users.selectOne(
        new LambdaQueryWrapper<SystemUser>()
            .eq(SystemUser::getTenantId, tenant)
            .eq(SystemUser::getId, id));
  }

  public List<Map<String, Object>> owners(long tenant, Long id) {
    return db.query(
        "SELECT user_id,owner_name FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?"
            + " ORDER BY sort_no,user_id",
        (rs, row) ->
            Map.<String, Object>of(
                "id", rs.getString("user_id"), "name", rs.getString("owner_name")),
        tenant,
        id);
  }

  public void deleteOwners(long tenant, Long id) {
    db.update("DELETE FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?", tenant, id);
  }

  public void addOwner(long tenant, Long id, Long userId, String name, int sort) {
    db.update(
        "INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name,sort_no)"
            + " VALUES(?,?,?,?,?)",
        tenant,
        id,
        userId,
        name,
        sort);
  }
}
