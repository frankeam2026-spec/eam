package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.entity.equipment.EquipmentRelationLegacy;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Repository;

@Repository
public class EquipmentRelationLegacyRepository {
  private final ObjectMapper json;
  private final EquipmentRelationLegacyMapper mapper;

  public EquipmentRelationLegacyRepository(
      EquipmentRelationLegacyMapper mapper, ObjectMapper json) {
    this.mapper = mapper;
    this.json = json;
  }

  public List<EquipmentRelationLegacy> list(long tenant, long equipment, String kind) {
    return mapper.selectList(
        scope(tenant, equipment)
            .eq(EquipmentRelationLegacy::getKind, kind)
            .orderByAsc(EquipmentRelationLegacy::getSourceIndex));
  }

  public void clear(long tenant, long equipment) {
    mapper.delete(scope(tenant, equipment));
  }

  public void remove(long tenant, long equipment, String kind, int sourceIndex) {
    mapper.delete(
        scope(tenant, equipment)
            .eq(EquipmentRelationLegacy::getKind, kind)
            .eq(EquipmentRelationLegacy::getSourceIndex, sourceIndex));
  }

  private LambdaQueryWrapper<EquipmentRelationLegacy> scope(long tenant, long equipment) {
    return new LambdaQueryWrapper<EquipmentRelationLegacy>()
        .eq(EquipmentRelationLegacy::getTenantId, tenant)
        .eq(EquipmentRelationLegacy::getEquipmentId, equipment);
  }

  public List<EquipmentRelationLegacy> list(long tenant, List<Long> ids) {
    if (ids.isEmpty()) return List.of();
    return mapper.selectList(
        new LambdaQueryWrapper<EquipmentRelationLegacy>()
            .eq(EquipmentRelationLegacy::getTenantId, tenant)
            .in(EquipmentRelationLegacy::getEquipmentId, ids)
            .in(EquipmentRelationLegacy::getKind, List.of("child", "bom"))
            .orderByAsc(
                EquipmentRelationLegacy::getEquipmentId, EquipmentRelationLegacy::getSourceIndex));
  }

  /** 兼容边界保留历史快照的未知字段，固定标记由后台覆盖。 */
  public Map<String, Object> view(EquipmentRelationLegacy row) {
    try {
      Map<String, Object> snapshot =
          json.readValue(
              row.getSnapshotJson(), new TypeReference<LinkedHashMap<String, Object>>() {});
      snapshot.put("legacy", true);
      snapshot.put("legacyIndex", row.getSourceIndex());
      snapshot.put("legacyReason", row.getReason());
      return snapshot;
    } catch (IOException invalid) {
      throw new LocalizedArgumentException("server.equipment.message0009");
    }
  }
}
