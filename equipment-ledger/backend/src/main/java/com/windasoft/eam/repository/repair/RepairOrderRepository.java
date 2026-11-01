package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class RepairOrderRepository {
  private final RepairRelationsRepository collaborators;
  private final RepairOrderMapper orders;
  private final EquipmentMapper equipment;
  private final com.windasoft.eam.repository.basedata.BaseItemMapper base;
  private final NumberCounterMapper counter;

  public RepairOrderRepository(
      RepairOrderMapper orders,
      EquipmentMapper equipment,
      com.windasoft.eam.repository.basedata.BaseItemMapper base,
      NumberCounterMapper counter,
      RepairRelationsRepository collaborators) {
    this.collaborators = collaborators;
    this.orders = orders;
    this.equipment = equipment;
    this.base = base;
    this.counter = counter;
  }

  public RepairOrder find(long tenant, Long id) {
    return collaborators.hydrate(
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .eq(RepairOrder::getId, id)));
  }

  public Equipment equipment(long tenant, String code) {
    return equipment.selectOne(
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, tenant)
            .eq(Equipment::getEquipmentCode, code));
  }

  public void insert(RepairOrder row) {
    orders.insert(row);
    collaborators.save(row);
  }

  public int update(RepairOrder row) {
    int changed = orders.updateById(row);
    if (changed > 0) collaborators.save(row);
    return changed;
  }

  public void nextMonthlyNumber(String prefix, String month) {
    counter.nextMonthlyNumber(prefix, month);
  }

  public long currentValue() {
    return counter.currentValue();
  }

  /**
   * 只接受关系表或维修事件 attachments 数组中的精确附件 ID。
   * 不使用全文 LIKE，避免将备注等无关文本中的相同 ID 误判为附件关联。
   */
  public List<RepairOrder> attachmentOrders(long tenant, String id) {
    return collaborators.hydrate(
        orders.selectList(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .and(
                    w ->
                        w.apply(
                                "(EXISTS(SELECT 1 FROM eam_repair_report_file f WHERE"
                                    + " f.tenant_id={0} AND f.order_id=eam_repair_order.id AND"
                                    + " f.file_id={1}) OR EXISTS(SELECT 1 FROM"
                                    + " eam_repair_process_file f WHERE f.tenant_id={0} AND"
                                    + " f.order_id=eam_repair_order.id AND f.file_id={1}))",
                                tenant, id)
                            .or()
                            .apply(
                                "id IN (SELECT order_id FROM eam_repair_event WHERE tenant_id={0}"
                                    + " AND JSON_SEARCH(details_json, 'one', {1}, NULL,"
                                    + " '$.attachments[*].id') IS NOT NULL)",
                                tenant, id))));
  }

}
