package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.entity.business.BusinessDictionary;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessDictionaryRepository {
  private final BusinessDictionaryMapper mapper;

  public BusinessDictionaryRepository(BusinessDictionaryMapper mapper) {
    this.mapper = mapper;
  }

  public BusinessDictionary find(long tenant, String id) {
    return mapper.selectOne(
        new LambdaQueryWrapper<BusinessDictionary>()
            .eq(BusinessDictionary::getTenantId, tenant)
            .eq(BusinessDictionary::getId, id));
  }
}
