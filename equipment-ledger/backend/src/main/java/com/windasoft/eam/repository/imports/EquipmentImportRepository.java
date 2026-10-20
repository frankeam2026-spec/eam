package com.windasoft.eam.repository.imports;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.dto.imports.EquipmentImportFields;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentImportRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final SystemUserMapper users;
  private final com.windasoft.eam.repository.equipment.EquipmentMapper equipmentMapper;

  public EquipmentImportRepository(
      JdbcTemplate db,
      ObjectMapper json,
      SystemUserMapper users,
      com.windasoft.eam.repository.equipment.EquipmentMapper equipmentMapper) {
    this.db = db;
    this.json = json;
    this.users = users;
    this.equipmentMapper = equipmentMapper;
  }

  public List<Map<String, Object>> organizations(long tenant) {
    return db.queryForList(
        "SELECT id,name,parent_id,org_type,status FROM eam_org_unit WHERE tenant_id=? AND"
            + " deleted=0",
        tenant);
  }

  public List<Map<String, Object>> bases(long tenant) {
    return db.queryForList(
        "SELECT id,name,code,item_type,parent_id,company_org_id,status FROM eam_base_item WHERE"
            + " tenant_id=? AND deleted=0",
        tenant);
  }

  public List<Map<String, Object>> users(long tenant) {
    return db.queryForList(
        "SELECT id,account,display_name,org_id,status FROM eam_sys_user WHERE tenant_id=? AND"
            + " deleted=0",
        tenant);
  }

  public Map<String, String> codeKeys(Collection<String> codes) {
    return ImportCodeIdentity.keys(db, codes);
  }

  public SystemUser user(long id) {
    return users.selectById(id);
  }

  public Map<String, Equipment> equipment(long tenant, Collection<String> codes) {
    var result = new LinkedHashMap<String, Equipment>();
    var list = new ArrayList<>(codes);
    for (int i = 0; i < list.size(); i += 500) {
      var part = list.subList(i, Math.min(i + 500, list.size()));
      var args = new ArrayList<Object>();
      args.addAll(part);
      args.add(tenant);
      String requested =
          String.join(
              " UNION ALL ",
              Collections.nCopies(
                  part.size(), "SELECT CAST(? AS CHAR CHARACTER SET utf8mb4) request_code"));
      for (var row :
          db.queryForList(
              "SELECT"
                  + " r.request_code,e.id,e.equipment_code,e.company_org_id,e.department_org_id,e.owner_user_id,e.deleted"
                  + " FROM ("
                  + requested
                  + ") r JOIN eam_equipment e ON e.equipment_code=r.request_code AND e.tenant_id=?",
              args.toArray())) {
        var e = new Equipment();
        e.setId(((Number) row.get("id")).longValue());
        e.setTenantId(tenant);
        e.setEquipmentCode(row.get("equipment_code").toString());
        e.setCompanyOrgId(number(row.get("company_org_id")));
        e.setDepartmentOrgId(number(row.get("department_org_id")));
        e.setOwnerUserId(number(row.get("owner_user_id")));
        e.setDeleted(((Number) row.get("deleted")).intValue());
        result.put(row.get("request_code").toString(), e);
      }
    }
    return result;
  }

  public Map<Long, List<Long>> owners(long tenant, Collection<Equipment> equipment) {
    var result = new HashMap<Long, List<Long>>();
    var list = equipment.stream().map(Equipment::getId).toList();
    for (int i = 0; i < list.size(); i += 500) {
      var part = list.subList(i, Math.min(i + 500, list.size()));
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.addAll(part);
      for (var r :
          db.queryForList(
              "SELECT equipment_id,user_id FROM eam_equipment_owner WHERE tenant_id=? AND"
                  + " equipment_id IN ("
                  + String.join(",", Collections.nCopies(part.size(), "?"))
                  + ")",
              args.toArray()))
        result
            .computeIfAbsent(number(r.get("equipment_id")), x -> new ArrayList<>())
            .add(number(r.get("user_id")));
    }
    return result;
  }

  public void stage(String batch, int row, EquipmentForm form, String parent) {
    Map<String, Object> values =
        form == null
            ? new HashMap<>()
            : json.convertValue(
                form, new com.fasterxml.jackson.core.type.TypeReference<Map<String, Object>>() {});
    values.put("parentCode", parent);
    var cols = new ArrayList<>(EquipmentImportFields.STAGING.keySet());
    var args = new ArrayList<Object>();
    args.add(batch);
    args.add(row);
    for (var key : cols) {
      Object v = values.get(key);
      args.add(v);
    }
    db.update(
        "INSERT INTO eam_equipment_import_row(batch_id,row_no,"
            + String.join(
                ",",
                cols.stream().map(k -> "`" + EquipmentImportFields.STAGING.get(k) + "`").toList())
            + ") VALUES("
            + String.join(",", Collections.nCopies(cols.size() + 2, "?"))
            + ")",
        args.toArray());
    if (form != null && form.ownerIds() != null)
      for (int i = 0; i < form.ownerIds().size(); i++)
        db.update(
            "INSERT INTO eam_equipment_import_owner VALUES(?,?,?,?)",
            batch,
            row,
            form.ownerIds().get(i),
            i);
  }

  public void result(String batch, int row, long equipment) {
    db.update("INSERT INTO eam_equipment_import_result VALUES(?,?,?)", batch, row, equipment);
  }

  public com.baomidou.mybatisplus.extension.plugins.pagination.Page<Equipment> results(
      String batch,
      long tenant,
      int page,
      int size,
      java.util.function.Consumer<
              com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Equipment>>
          scope) {
    var q =
        new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .apply(
                "id IN (SELECT equipment_id FROM eam_equipment_import_result WHERE batch_id={0})",
                batch);
    scope.accept(q);
    q.orderByAsc(Equipment::getEquipmentCode).orderByAsc(Equipment::getId);
    return equipmentMapper.selectPage(
        new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(page, size), q);
  }

  public void lockReferences(long tenant) {
    // Lock referenced catalogs once per batch; the original company/mutex order is acquired first.
    db.queryForList("SELECT id FROM eam_org_unit WHERE tenant_id=? ORDER BY id FOR SHARE", tenant);
    db.queryForList("SELECT id FROM eam_base_item WHERE tenant_id=? ORDER BY id FOR SHARE", tenant);
    db.queryForList("SELECT id FROM eam_sys_user WHERE tenant_id=? ORDER BY id FOR SHARE", tenant);
  }

  private static Long number(Object v) {
    return v == null ? null : ((Number) v).longValue();
  }
}
