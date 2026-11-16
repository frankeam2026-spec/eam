package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandardItem;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 标准项目自身数据访问；引用是否合法由保养业务服务决定。 */
@Repository
public class MaintenanceStandardItemRepository {
  private final MaintenanceStandardItemMapper mapper;

  public MaintenanceStandardItemRepository(MaintenanceStandardItemMapper mapper) {
    this.mapper = mapper;
  }

  public long countByType(long tenant, long type) {
    return mapper.selectCount(
        new LambdaQueryWrapper<MaintenanceStandardItem>()
            .eq(MaintenanceStandardItem::getTenantId, tenant)
            .eq(MaintenanceStandardItem::getTypeId, type));
  }

  public List<MaintenanceStandardItem> list(long tenant, Collection<Long> standards) {
    if (standards.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceStandardItem>()
            .eq(MaintenanceStandardItem::getTenantId, tenant)
            .in(MaintenanceStandardItem::getStandardId, standards)
            .orderByAsc(
                MaintenanceStandardItem::getStandardId,
                MaintenanceStandardItem::getSortNo,
                MaintenanceStandardItem::getItemKey));
  }

  public void insertAll(List<MaintenanceStandardItem> rows) {
    if (!rows.isEmpty()) {
      mapper.insert(rows);
    }
  }

  /** 复合主键完整限定，不能使用updateById；空上下限由实体ALWAYS策略明确清除。 */
  public int update(MaintenanceStandardItem row) {
    return mapper.update(
        row,
        new LambdaUpdateWrapper<MaintenanceStandardItem>()
            .eq(MaintenanceStandardItem::getTenantId, row.getTenantId())
            .eq(MaintenanceStandardItem::getStandardId, row.getStandardId())
            .eq(MaintenanceStandardItem::getItemKey, row.getItemKey()));
  }

  public int delete(long tenant, long standard, Collection<String> keys) {
    if (keys.isEmpty()) {
      return 0;
    }
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceStandardItem>()
            .eq(MaintenanceStandardItem::getTenantId, tenant)
            .eq(MaintenanceStandardItem::getStandardId, standard)
            .in(MaintenanceStandardItem::getItemKey, keys));
  }
}
