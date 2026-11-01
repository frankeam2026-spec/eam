package com.windasoft.eam.service.experience;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.experience.AnomalyAction;
import com.windasoft.eam.domain.experience.AnomalyStatus;
import org.junit.jupiter.api.Test;

/** 异常事项类型化后继续兼容历史状态及动作请求编码。 */
class AnomalyDomainCodesTest {
  @Test
  void keepsAnomalyStatuses() {
    assertEquals("待处理", AnomalyStatus.PENDING.code());
    assertEquals("已安排", AnomalyStatus.ARRANGED.code());
    assertEquals("处理中", AnomalyStatus.IN_PROGRESS.code());
    assertEquals("已消除", AnomalyStatus.RESOLVED.code());
    assertEquals("已作废", AnomalyStatus.VOIDED.code());
  }

  @Test
  void keepsActionCodesAndRejectsUnknownValues() {
    for (var action : AnomalyAction.values())
      assertEquals(action, AnomalyAction.parse(action.code()));
    assertThrows(LocalizedArgumentException.class, () -> AnomalyAction.parse(null));
    assertThrows(LocalizedArgumentException.class, () -> AnomalyAction.parse("invalid"));
  }
}
