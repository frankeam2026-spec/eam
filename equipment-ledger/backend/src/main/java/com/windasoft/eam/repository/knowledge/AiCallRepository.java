package com.windasoft.eam.repository.knowledge;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.entity.knowledge.AiCall;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class AiCallRepository {
  private final AiCallMapper mapper;

  public AiCallRepository(AiCallMapper mapper) {
    this.mapper = mapper;
  }

  public void insert(AiCall call) {
    mapper.insert(call);
  }

  /** 仅RUNNING可以首次结算；条件更新影响行数防止并发恢复与正常返回重复记账。 */
  public boolean finish(
      long id,
      long tenant,
      BigDecimal cost,
      Long input,
      Long output,
      long elapsed,
      boolean success) {
    return mapper.update(
            null,
            new LambdaUpdateWrapper<AiCall>()
                .eq(AiCall::getTenantId, tenant)
                .eq(AiCall::getId, id)
                .eq(AiCall::getStatus, "RUNNING")
                .set(AiCall::getStatus, success ? "SUCCEEDED" : "UNKNOWN")
                .set(AiCall::getEstimatedCost, cost)
                .set(AiCall::getInputTokens, input)
                .set(AiCall::getOutputTokens, output)
                .set(AiCall::getElapsedMs, elapsed)
                .setSql("completed_at=CURRENT_TIMESTAMP(6)"))
        == 1;
  }

  public Page<AiCall> page(long tenant, int number, int size) {
    return mapper.selectPage(
        new Page<>(number, size),
        new LambdaQueryWrapper<AiCall>()
            .eq(AiCall::getTenantId, tenant)
            .orderByDesc(AiCall::getCreatedAt)
            .orderByDesc(AiCall::getId));
  }

  /** 超过原十分钟窗口的调用按原金额保守结算，数据库时钟与单次100条上限保持。 */
  public List<AiCall> expired(long tenant) {
    return mapper.selectList(
        new LambdaQueryWrapper<AiCall>()
            .eq(AiCall::getTenantId, tenant)
            .eq(AiCall::getStatus, "RUNNING")
            .apply("created_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 10 MINUTE)")
            .orderByAsc(AiCall::getId)
            .last("LIMIT 100"));
  }
}
