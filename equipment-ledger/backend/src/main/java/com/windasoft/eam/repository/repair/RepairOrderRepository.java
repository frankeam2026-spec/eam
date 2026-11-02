package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 维修工单主表访问：读写工单并批量读取展示关系，不代写附件或协同人员。 附件磁盘存储由 RepairFileStorage 负责，本类不管理文件路径或文件内容。 */
@Repository
public class RepairOrderRepository {
  private final RepairRelationsRepository collaborators;
  private final RepairOrderMapper orders;

  public RepairOrderRepository(RepairOrderMapper orders, RepairRelationsRepository collaborators) {
    this.collaborators = collaborators;
    this.orders = orders;
  }

  public RepairOrder find(long tenant, Long id) {
    return collaborators.hydrate(
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .eq(RepairOrder::getId, id)));
  }

  public void insert(RepairOrder row) {
    orders.insert(row);
  }

  public int update(RepairOrder row) {
    int changed = orders.updateById(row);
    return changed;
  }

  /** 只接受关系表或维修事件 attachments 数组中的精确附件 ID。 不使用全文 LIKE，避免将备注等无关文本中的相同 ID 误判为附件关联。 */
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

  /** 状态流转在服务事务内先锁工单，再访问候选、待办及历史备件，顺序不变。 */
  public RepairOrder lock(long tenant, long id) {
    return collaborators.hydrate(
        orders.selectOne(
            new LambdaQueryWrapper<RepairOrder>()
                .eq(RepairOrder::getTenantId, tenant)
                .eq(RepairOrder::getId, id)
                .eq(RepairOrder::getDeleted, 0)
                .last("FOR UPDATE")));
  }

  public long assignedOrderCount(long tenant, long user) {
    return orders.selectCount(
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getAssigneeUserId, user)
            .eq(RepairOrder::getDeleted, 0));
  }

  /** 明确清空负责人ID，不能依赖忽略null的实体更新策略。 */
  public void clearOwner(long tenant, long id) {
    orders.update(
        null,
        new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getId, id)
            .set(RepairOrder::getAssigneeUserId, null));
  }
}
