package com.windasoft.eam.repository.system;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.entity.system.NotifyRulePerson;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface NotifyRulePersonMapper extends BaseMapper<NotifyRulePerson> {

  @Select(
      """
SELECT CAST(p.rule_id AS CHAR) ruleId,CAST(p.user_id AS CHAR) userId,p.recipient_kind recipientKind,u.display_name name
FROM eam_notify_rule_person p JOIN eam_notify_rule r ON r.id=p.rule_id AND r.tenant_id=p.tenant_id
JOIN eam_sys_user u ON u.id=p.user_id AND u.tenant_id=p.tenant_id
WHERE p.tenant_id=#{tenant} AND r.policy_id=#{policy}
""")
  List<NotificationViews.Person> people(@Param("tenant") long tenant, @Param("policy") long policy);
}
