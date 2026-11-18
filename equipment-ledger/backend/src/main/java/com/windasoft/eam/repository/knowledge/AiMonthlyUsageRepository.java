package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.knowledge.AiMonthlyUsage;
import java.math.BigDecimal;
import org.springframework.stereotype.Repository;

@Repository
public class AiMonthlyUsageRepository {
  private final AiMonthlyUsageMapper mapper;

  public AiMonthlyUsageRepository(AiMonthlyUsageMapper mapper) {
    this.mapper = mapper;
  }

  public AiMonthlyUsage usage(long tenant, String month, boolean lock) {
    mapper.ensure(tenant, month);
    var where =
        new LambdaQueryWrapper<AiMonthlyUsage>()
            .eq(AiMonthlyUsage::getTenantId, tenant)
            .eq(AiMonthlyUsage::getBillingMonth, month);
    if (lock) where.last("FOR UPDATE");
    return mapper.selectOne(where);
  }

  public void reserve(long tenant, String month, BigDecimal amount) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<AiMonthlyUsage>()
            .eq(AiMonthlyUsage::getTenantId, tenant)
            .eq(AiMonthlyUsage::getBillingMonth, month)
            .setSql("reserved_amount=reserved_amount+{0}", amount));
  }

  /** 调用状态更新成功后才结算，防重由服务检查调用更新影响行数。 */
  public void settle(long tenant, String month, BigDecimal reserved, BigDecimal cost) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<AiMonthlyUsage>()
            .eq(AiMonthlyUsage::getTenantId, tenant)
            .eq(AiMonthlyUsage::getBillingMonth, month)
            .setSql(
                "reserved_amount=reserved_amount-{0},estimated_spent=estimated_spent+{1}",
                reserved, cost));
  }
}
