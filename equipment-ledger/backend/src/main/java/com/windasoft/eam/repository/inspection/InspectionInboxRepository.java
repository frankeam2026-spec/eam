package com.windasoft.eam.repository.inspection;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.entity.inspection.InspectionInbox;
import org.springframework.stereotype.Repository;

@Repository
public class InspectionInboxRepository {
  private final InspectionInboxMapper mapper;

  public InspectionInboxRepository(InspectionInboxMapper mapper) {
    this.mapper = mapper;
  }

  /** 工作中心只标记当前用户尚未读的消息，不修改待办完成状态。 */
  public void markWorkRead(long tenant, long user, String id) {
    mapper.update(
        null,
        new LambdaUpdateWrapper<InspectionInbox>()
            .eq(InspectionInbox::getTenantId, tenant)
            .eq(InspectionInbox::getUserId, user)
            .eq(!id.isBlank(), InspectionInbox::getId, id)
            .isNull(InspectionInbox::getReadAt)
            .setSql("read_at=NOW()"));
  }
}
