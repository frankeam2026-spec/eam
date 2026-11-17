package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.business.BusinessField;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessFieldRepository {
  private final BusinessFieldMapper mapper;

  public BusinessFieldRepository(BusinessFieldMapper mapper) {
    this.mapper = mapper;
  }

  /** UUID全局唯一，需先锁真实归属，再由Service拒绝其他租户/对象复用；不能先加租户条件把冲突当成不存在。 */
  public BusinessField identity(String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BusinessField>().eq(BusinessField::getId, id).last("FOR UPDATE"));
  }

  public void definition(String id, long tenant, String object, String type, String source) {
    mapper.upsertDefinition(id, tenant, object, type, source);
  }

  public void markPublished(long tenant, String object) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<BusinessField>()
            .eq(BusinessField::getTenantId, tenant)
            .eq(BusinessField::getObjectKey, object)
            .set(BusinessField::getEverPublished, 1));
  }
}
