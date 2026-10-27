package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowProgress;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowProgressTest {
  @Test
  void historyPreservesTopologyWithoutExposingScriptsOrConditions() {
    var node =
        new WorkflowDefinition.Node(
            "compute",
            "SCRIPT",
            "计算",
            "end",
            "ANY",
            List.of("1"),
            List.of("2"),
            List.of(new WorkflowDefinition.Branch("b", "条件", "end", false, "amount > 5000")),
            "sub",
            new WorkflowDefinition.Script(
                "javascript", "return input.amount;", "computed_amount", "NUMBER"));
    var d =
        new WorkflowDefinition(
            "1", 0, "test", "requisition", "2", "", "3", "PUBLISHED", List.of(node));
    var safe = WorkflowProgress.nodes(d).getFirst();
    assertEquals("compute", safe.key());
    assertEquals("sub", safe.parentKey());
    assertEquals("end", safe.next());
    assertNull(safe.script());
    assertTrue(safe.users().isEmpty());
    assertTrue(safe.roles().isEmpty());
    assertEquals("", safe.branches().getFirst().expression());
    assertNotNull(node.script());
    assertEquals("amount > 5000", node.branches().getFirst().expression());
  }
}
