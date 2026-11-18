package com.windasoft.eam.repository.experience;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.experience.ExperienceInbox;
import org.springframework.stereotype.Repository;

@Repository
public class ExperienceInboxRepository {
  private final ExperienceInboxMapper mapper;

  public ExperienceInboxRepository(ExperienceInboxMapper mapper) {
    this.mapper = mapper;
  }

  /** 工作中心只标记当前用户尚未读的消息，不修改待办完成状态。 */
  public void markWorkRead(long tenant, long user, String id, String module) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<ExperienceInbox>()
            .eq(ExperienceInbox::getTenantId, tenant)
            .eq(ExperienceInbox::getUserId, user)
            .eq(!id.isBlank(), ExperienceInbox::getId, id)
            .isNull(ExperienceInbox::getReadAt)
            .eq(!module.isBlank(), ExperienceInbox::getObjectKind, module)
            .setSql("read_at=NOW(3)"));
  }
}
