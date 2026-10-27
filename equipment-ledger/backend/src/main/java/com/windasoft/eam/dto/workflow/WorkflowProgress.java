package com.windasoft.eam.dto.workflow;

import java.util.List;

/** Business history exposes diagram topology, never executable scripts or condition data. */
public final class WorkflowProgress {
  private WorkflowProgress() {}

  public static List<WorkflowDefinition.Node> nodes(WorkflowDefinition definition) {
    return definition.nodes().stream()
        .map(
            n ->
                new WorkflowDefinition.Node(
                    n.key(),
                    n.type(),
                    n.name(),
                    n.next(),
                    n.approvalMode(),
                    List.of(),
                    List.of(),
                    n.branches().stream()
                        .map(
                            b ->
                                new WorkflowDefinition.Branch(
                                    b.key(), b.name(), b.target(), b.fallback(), ""))
                        .toList(),
                    n.parentKey(),
                    null))
        .toList();
  }
}
