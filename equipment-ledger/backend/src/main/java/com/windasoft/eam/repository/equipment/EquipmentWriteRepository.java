package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import java.util.Collection;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentWriteRepository {
  private final EquipmentMapper equipment;
  private final EquipmentMediaRepository media;
  private final JdbcTemplate db;

  public EquipmentWriteRepository(
      EquipmentMapper equipment, EquipmentMediaRepository media, JdbcTemplate db) {
    this.equipment = equipment;
    this.media = media;
    this.db = db;
  }

  public int insert(Equipment e) {
    int changed = equipment.insert(e);
    if (changed > 0) {
      media.save(e);
    }
    return changed;
  }

  public Map<String, String> importCodeKeys(Collection<String> codes) {
    return com.windasoft.eam.repository.imports.ImportCodeIdentity.keys(db, codes);
  }

  public int insertImported(Equipment e) {
    return equipment.insert(e);
  }

  public int update(Equipment e) {
    int changed = equipment.updateById(e);
    if (changed > 0) {
      media.save(e);
    }
    return changed;
  }

  public int delete(Long id) {
    return equipment.deleteById(id);
  }

  public int advanceVersion(long tenant, Long id, int version, long user) {
    return db.update(
        "UPDATE eam_equipment SET row_version=row_version+1, updated_by=?"
            + " WHERE tenant_id=? AND id=? AND deleted=0 AND row_version=?",
        user,
        tenant,
        id,
        version);
  }

  public Equipment find(long tenant, Long id) {
    return media.hydrate(
        equipment.selectOne(
            new LambdaQueryWrapper<Equipment>()
                .eq(Equipment::getTenantId, tenant)
                .eq(Equipment::getId, id)));
  }

  public long countCode(long tenant, String code, Long currentId) {
    var q =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code);
    if (currentId != null) {
      q.ne(Equipment::getId, currentId);
    }
    return equipment.selectCount(q);
  }

  public long maxEquipmentNumber(String year) {
    String prefix = "EQ-" + year + "-";
    // 编号计数器由当前数据库共享，推进时需计入历史设备编号。
    Long maximum =
        db.queryForObject(
            "SELECT COALESCE(MAX(CAST(SUBSTRING(equipment_code, ?) AS UNSIGNED)),0)"
                + " FROM eam_equipment WHERE equipment_code LIKE ? AND equipment_code REGEXP ?",
            Long.class,
            prefix.length() + 1,
            prefix + "%",
            "^" + prefix + "[0-9]+$");
    return maximum == null ? 0L : maximum;
  }

}
