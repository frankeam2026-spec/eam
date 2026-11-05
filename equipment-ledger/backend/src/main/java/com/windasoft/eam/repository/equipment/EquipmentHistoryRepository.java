package com.windasoft.eam.repository.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.equipment.EquipmentHistory;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 历史事件单对象数据访问，不处理维修和巡检业务规则。 */
/** 设备历史事件仓储：保存和读取设备事件，事件产生时机由业务用例决定。 */
@Repository
public class EquipmentHistoryRepository {
  private final EquipmentHistoryMapper mapper;

  public EquipmentHistoryRepository(EquipmentHistoryMapper mapper) {
    this.mapper = mapper;
  }

  public List<EquipmentHistory> list(long tenant, long equipmentId) {
    return mapper.selectList(
        new LambdaQueryWrapper<EquipmentHistory>()
            .eq(EquipmentHistory::getTenantId, tenant)
            .eq(EquipmentHistory::getEquipmentId, equipmentId)
            .orderByDesc(EquipmentHistory::getOccurredAt, EquipmentHistory::getId));
  }

  /** 判断指定设备是否已有对应的点巡检履历，避免重复写入。 */
  public boolean hasInspectionRecord(long tenant, long equipmentId, String recordNo) {
    return mapper.selectCount(
            new LambdaQueryWrapper<EquipmentHistory>()
                .eq(EquipmentHistory::getTenantId, tenant)
                .eq(EquipmentHistory::getEquipmentId, equipmentId)
                .eq(EquipmentHistory::getRecordType, "INSPECTION")
                .eq(EquipmentHistory::getRecordNo, recordNo))
        > 0;
  }

  /** 更新已存在的点巡检履历结果，不改变事件时间和其他历史字段。 */
  public int updateInspectionResult(
      long tenant, long equipmentId, String recordNo, String result) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<EquipmentHistory>()
            .eq(EquipmentHistory::getTenantId, tenant)
            .eq(EquipmentHistory::getEquipmentId, equipmentId)
            .eq(EquipmentHistory::getRecordType, "INSPECTION")
            .eq(EquipmentHistory::getRecordNo, recordNo)
            .set(EquipmentHistory::getResult, result));
  }

  /** 创建点巡检履历；事件时间由数据库生成，与既有写入语义保持一致。 */
  public void insertInspection(
      long id,
      long tenant,
      long equipmentId,
      String recordNo,
      String description,
      String operatorName,
      String status,
      String result) {
    var row = new EquipmentHistory();
    row.setId(id);
    row.setTenantId(tenant);
    row.setEquipmentId(equipmentId);
    row.setRecordType("INSPECTION");
    row.setRecordNo(recordNo);
    row.setDescription(description);
    row.setOperatorName(operatorName);
    row.setStatus(status);
    row.setResult(result);
    insert(row);
  }

  /** 事件内容由业务用例提供，仓储只保存设备履历记录。 */
  public void insert(EquipmentHistory row) {
    mapper.insertAtDatabaseTime(row);
  }
}
