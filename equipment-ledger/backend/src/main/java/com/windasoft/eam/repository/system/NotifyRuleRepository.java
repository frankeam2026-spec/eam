package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.system.NotifyRule;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyRuleRepository {
  private final NotifyRuleMapper mapper;

  public NotifyRuleRepository(NotifyRuleMapper mapper) {
    this.mapper = mapper;
  }

  public List<NotifyRule> list(long tenant, long policy) {
    return mapper.selectList(
        new LambdaQueryWrapper<NotifyRule>()
            .eq(NotifyRule::getTenantId, tenant)
            .eq(NotifyRule::getPolicyId, policy)
            .eq(NotifyRule::getArchived, 0)
            .last(
                "ORDER BY CASE scene WHEN 'BEFORE_DUE' THEN 0 WHEN 'AFTER_DUE' THEN 0 WHEN"
                    + " 'WAIT_DISPATCH' THEN 1 WHEN 'REPORT_UNCLAIMED' THEN 2 WHEN 'WAIT_CLAIM'"
                    + " THEN 3 WHEN 'PROCESS' THEN 4 ELSE 5"
                    + " END,IF(scene='BEFORE_DUE',-1,1)*offset_value*CASE offset_unit WHEN 'MINUTE'"
                    + " THEN 60 WHEN 'HOUR' THEN 3600 ELSE 86400 END,sort_no,id"));
  }

  public void insert(NotifyRule row) {
    mapper.insert(row);
  }

  public void update(NotifyRule row, boolean changed) {
    var q =
        new LambdaUpdateWrapper<NotifyRule>()
            .eq(NotifyRule::getTenantId, row.getTenantId())
            .eq(NotifyRule::getId, row.getId())
            .eq(NotifyRule::getPolicyId, row.getPolicyId())
            .set(NotifyRule::getScene, row.getScene())
            .set(NotifyRule::getOffsetValue, row.getOffsetValue())
            .set(NotifyRule::getOffsetUnit, row.getOffsetUnit())
            .set(NotifyRule::getEnabled, row.getEnabled())
            .set(NotifyRule::getSortNo, row.getSortNo());
    if (changed) q.setSql("effective_at=NOW(6)");
    mapper.update(null, q);
  }

  /** 删除规则只归档，保留发送记录的真实外键，不能物理删除历史引用。 */
  public void disable(long tenant, long id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<NotifyRule>()
            .eq(NotifyRule::getTenantId, tenant)
            .eq(NotifyRule::getId, id)
            .set(NotifyRule::getEnabled, 0)
            .set(NotifyRule::getArchived, 1)
            .setSql("effective_at=NOW(6)"));
  }
}
