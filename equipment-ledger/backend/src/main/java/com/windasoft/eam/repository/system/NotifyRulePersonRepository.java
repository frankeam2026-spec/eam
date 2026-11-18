package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.entity.system.NotifyRulePerson;
import java.util.List;
import org.springframework.stereotype.Repository;

@Repository
public class NotifyRulePersonRepository {
  private final NotifyRulePersonMapper mapper;

  public NotifyRulePersonRepository(NotifyRulePersonMapper mapper) {
    this.mapper = mapper;
  }

  public List<NotificationViews.Person> list(long tenant, long policy) {
    return mapper.people(tenant, policy);
  }

  public void replace(long tenant, long rule, List<NotifyRulePerson> people) {
    mapper.delete(
        new LambdaQueryWrapper<NotifyRulePerson>()
            .eq(NotifyRulePerson::getTenantId, tenant)
            .eq(NotifyRulePerson::getRuleId, rule));
    if (!people.isEmpty()) mapper.insert(people, 200);
  }
}
