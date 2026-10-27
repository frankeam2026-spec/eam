package com.windasoft.eam.service.workflow;

import java.util.Map;

public interface WorkflowBusinessAdapter {
  String businessType();

  /** Acquire existing business locks before workflow instance locks. */
  void lock(long businessId);

  Map<String, Object> values(long businessId);

  void requireActive(long businessId);

  void approved(long businessId);

  void returned(long businessId, String reason);
}
