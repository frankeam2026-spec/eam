package com.windasoft.eam.repository.business;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.windasoft.eam.dto.business.BusinessChoiceQuery;
import com.windasoft.eam.dto.business.BusinessChoices.LockedOption;
import com.windasoft.eam.dto.business.BusinessChoices.StoredChoice;
import com.windasoft.eam.entity.business.BusinessOption;
import java.util.List;
import org.springframework.stereotype.Repository;

/** 选项自身查询复用BaseMapper；字典状态与共享锁需父子JOIN，由Mapper明确执行。 */
@Repository
public class BusinessOptionRepository {
  private final BusinessOptionMapper mapper;

  public BusinessOptionRepository(BusinessOptionMapper mapper) {
    this.mapper = mapper;
  }

  public List<BusinessOption> options(long tenant, String dictionary) {
    return mapper.selectList(
        new LambdaQueryWrapper<BusinessOption>()
            .eq(BusinessOption::getTenantId, tenant)
            .eq(BusinessOption::getDictionaryId, dictionary)
            .orderByAsc(BusinessOption::getSortNo, BusinessOption::getId));
  }

  public IPage<StoredChoice> choices(
      long tenant, String dictionary, BusinessChoiceQuery query, boolean inactive) {
    return mapper.choices(
        new Page<>(query.page(), query.size()), tenant, dictionary, query.likePattern(), inactive);
  }

  public LockedOption locked(long tenant, String dictionary, String id) {
    return mapper.locked(tenant, dictionary, id);
  }
}
