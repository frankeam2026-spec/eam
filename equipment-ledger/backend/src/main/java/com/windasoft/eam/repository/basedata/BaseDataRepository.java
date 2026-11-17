package com.windasoft.eam.repository.basedata;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.windasoft.eam.entity.basedata.BaseItem;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 基础资料的跨表引用只读查询；主体CRUD由BaseItemRepository维护，不写入物料或组织。 */
@Repository
public class BaseDataRepository {
  private final EquipmentMapper equipment;
  private final JdbcTemplate db;

  public BaseDataRepository(EquipmentMapper equipment, JdbcTemplate db) {
    this.equipment = equipment;
    this.db = db;
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
}
