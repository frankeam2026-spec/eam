package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.domain.repair.LegacyRepairMaterialStatus;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.entity.repair.RepairMaterial;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

/** RepairMaterial 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class RepairMaterialRepository {
  private final RepairMaterialMapper mapper;

  public RepairMaterialRepository(RepairMaterialMapper mapper) {
    this.mapper = mapper;
  }

  public List<RepairReadViews.Material> materials(long tenant, long order) {
    return mapper.materials(tenant, order);
  }

  public int cancelMaterial(long tenant, long id, long order) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<RepairMaterial>()
            .eq(RepairMaterial::getTenantId, tenant)
            .eq(RepairMaterial::getId, id)
            .eq(RepairMaterial::getOrderId, order)
            .eq(RepairMaterial::getStatus, LegacyRepairMaterialStatus.PENDING_ISSUE.code())
            .set(RepairMaterial::getStatus, LegacyRepairMaterialStatus.WITHDRAWN.code()));
  }

  public RepairMaterial lockMaterial(long tenant, long id, long order) {
    return mapper.selectOne(
        new LambdaQueryWrapper<RepairMaterial>()
            .eq(RepairMaterial::getTenantId, tenant)
            .eq(RepairMaterial::getId, id)
            .eq(RepairMaterial::getOrderId, order)
            .last("FOR UPDATE"));
  }

  /** 保留数据库原子余额条件；不能先在Java计算再无条件更新，避免超量安装。 */
  public int consume(long tenant, long id, BigDecimal quantity) {
    return mapper.update(
        null,
        new LambdaUpdateWrapper<RepairMaterial>()
            .eq(RepairMaterial::getTenantId, tenant)
            .eq(RepairMaterial::getId, id)
            .apply("issued_quantity-installed_quantity >= {0}", quantity)
            .setSql("installed_quantity=installed_quantity+{0}", quantity));
  }
}
