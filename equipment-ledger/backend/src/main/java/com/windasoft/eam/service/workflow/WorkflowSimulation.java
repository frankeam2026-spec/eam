package com.windasoft.eam.service.workflow;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import java.util.*;

/**
 * Synchronous trial context. It is never persisted as a business instance or inherited by threads.
 */
final class WorkflowSimulation implements AutoCloseable {
  final long deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(20);
  private static final ThreadLocal<WorkflowSimulation> CURRENT = new ThreadLocal<>();
  final WorkflowDefinition definition;
  final Map<String, Object> values;
  final List<Map<String, Object>> trace = new ArrayList<>();

  WorkflowSimulation(WorkflowDefinition definition, Map<String, Object> values) {
    if (CURRENT.get() != null) throw new IllegalStateException("不支持嵌套试算");
    this.definition = definition;
    this.values = new LinkedHashMap<>(values);
    CURRENT.set(this);
  }

  static WorkflowSimulation current() {
    return CURRENT.get();
  }

  public void close() {
    CURRENT.remove();
  }
}
