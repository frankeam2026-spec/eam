package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.repair.RepairReadViews;
import com.windasoft.eam.entity.repair.RepairCandidate;
import java.util.List;
import org.springframework.stereotype.Repository;

/** RepairCandidate 主仓储：集中维护租户条件及持久化操作，不决定业务授权和事务。 */
@Repository
public class RepairCandidateRepository {
  private final RepairCandidateMapper mapper;

  public RepairCandidateRepository(RepairCandidateMapper mapper) {
    this.mapper = mapper;
  }

  public int candidate(long tenant, long order, long user) {
    return Math.toIntExact(
        mapper.selectCount(
            new LambdaQueryWrapper<RepairCandidate>()
                .eq(RepairCandidate::getTenantId, tenant)
                .eq(RepairCandidate::getOrderId, order)
                .eq(RepairCandidate::getUserId, user)
                .eq(RepairCandidate::getStatus, "待接单")));
  }

  public List<RepairReadViews.Candidate> candidates(long tenant, long order) {
    return mapper.candidates(tenant, order);
  }

  public void clearCandidates(long tenant, long order) {
    mapper.delete(
        new LambdaQueryWrapper<RepairCandidate>()
            .eq(RepairCandidate::getTenantId, tenant)
            .eq(RepairCandidate::getOrderId, order));
  }

  public void addCandidate(long id, long tenant, long order, long user) {
    var row = new RepairCandidate();
    row.setId(id);
    row.setTenantId(tenant);
    row.setOrderId(order);
    row.setUserId(user);
    row.setStatus("待接单");
    mapper.insertAtDatabaseTime(row);
  }

  /** 在原工单行锁及同一事务内批量标记接单结果，不生成新候选轮次。 */
  public void claim(long tenant, long order, long user) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<RepairCandidate>()
            .eq(RepairCandidate::getTenantId, tenant)
            .eq(RepairCandidate::getOrderId, order)
            .setSql("status=IF(user_id={0},'已接单','已由他人接单')", user));
  }
}
