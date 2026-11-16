package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceStandard;
import java.util.Collection;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 标准主体数据访问；项目引用规则、关联替换和授权属于业务Service。 */
@Repository
public class MaintenanceStandardRepository {
  private final MaintenanceStandardMapper mapper;

  public MaintenanceStandardRepository(MaintenanceStandardMapper mapper) {
    this.mapper = mapper;
  }

  public MaintenanceStandard find(long tenant, long id, boolean lock) {
    return mapper.selectOne(
        new LambdaQueryWrapper<MaintenanceStandard>()
            .eq(MaintenanceStandard::getTenantId, tenant)
            .eq(MaintenanceStandard::getId, id)
            .last(lock ? "FOR UPDATE" : ""));
  }

  public List<MaintenanceStandard> list(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) {
      return List.of();
    }
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceStandard>()
            .eq(MaintenanceStandard::getTenantId, tenant)
            .in(MaintenanceStandard::getId, ids));
  }

  public int insert(MaintenanceStandard row) {
    return mapper.insert(row);
  }

  /** 主体已由保存用例加锁，版本与更新时间沿用原数据库递增语义。 */
  public int update(MaintenanceStandard row) {
    return mapper.update(
        row,
        new LambdaUpdateWrapper<MaintenanceStandard>()
            .eq(MaintenanceStandard::getTenantId, row.getTenantId())
            .eq(MaintenanceStandard::getId, row.getId())
            .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int delete(long tenant, long id) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceStandard>()
            .eq(MaintenanceStandard::getTenantId, tenant)
            .eq(MaintenanceStandard::getId, id));
  }
}
