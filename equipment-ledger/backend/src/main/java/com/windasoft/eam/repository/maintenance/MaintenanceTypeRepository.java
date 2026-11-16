package com.windasoft.eam.repository.maintenance;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.maintenance.MaintenanceType;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 类型主体持久化；不维护标准、计划或任务，修改合法性及事务在Service。 */
@Repository
public class MaintenanceTypeRepository {
  private final MaintenanceTypeMapper mapper;

  public MaintenanceTypeRepository(MaintenanceTypeMapper mapper) {
    this.mapper = mapper;
  }

  public MaintenanceType find(long tenant, long id, boolean lock) {
    return mapper.selectOne(
        new LambdaQueryWrapper<MaintenanceType>()
            .eq(MaintenanceType::getTenantId, tenant)
            .eq(MaintenanceType::getId, id)
            .last(lock ? "FOR UPDATE" : ""));
  }

  public List<MaintenanceType> list(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<MaintenanceType>()
            .eq(MaintenanceType::getTenantId, tenant)
            .orderByDesc(MaintenanceType::getCreatedAt, MaintenanceType::getId));
  }

  public int insert(MaintenanceType value) {
    return mapper.insert(value);
  }

  /** 已在用例内锁定主体；版本仍由数据库递增，数据库时间与旧写入口径一致。 */
  public int update(MaintenanceType value) {
    return mapper.update(
        value,
        new LambdaUpdateWrapper<MaintenanceType>()
            .eq(MaintenanceType::getTenantId, value.getTenantId())
            .eq(MaintenanceType::getId, value.getId())
            .setSql("row_version=row_version+1,updated_at=CURRENT_TIMESTAMP"));
  }

  public int delete(long tenant, long id) {
    return mapper.delete(
        new LambdaQueryWrapper<MaintenanceType>()
            .eq(MaintenanceType::getTenantId, tenant)
            .eq(MaintenanceType::getId, id));
  }
}
