package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowDefinitionValidatorTest {
  final WorkflowDefinitionValidator validator =
      new WorkflowDefinitionValidator(new WorkflowVariables());

  WorkflowDefinition definition(boolean commonApproval) {
    return new WorkflowDefinition(
        null,
        0,
        "审批路径校验",
        "requisition",
        "71002",
        "",
        null,
        "DRAFT",
        List.of(
            new Node("start", "START", "开始", "branch", "ANY", List.of(), List.of(), List.of()),
            new Node(
                "branch",
                "BRANCH",
                "分支",
                null,
                "ANY",
                List.of(),
                List.of(),
                List.of(
                    new Branch("match", "条件", "approval", false, "SUM(lines.quantity) > 10"),
                    new Branch("other", "其他", commonApproval ? "approval" : "end", true, ""))),
            new Node(
                "approval", "APPROVAL", "审批", "end", "ANY", List.of("73001"), List.of(), List.of()),
            new Node("end", "END", "结束", null, "ANY", List.of(), List.of(), List.of())));
  }

  @Test
  void publishingRejectsAPathThatSkipsApprovalButDraftCanBeSaved() {
    assertDoesNotThrow(() -> validator.validate(definition(false), false));
    assertTrue(
        assertThrows(IllegalArgumentException.class, () -> validator.validate(definition(false)))
            .getMessage()
            .contains("每条完整路径"));
  }

  @Test
  void convergingBranchesMayUseTheSameApproval() {
    assertDoesNotThrow(() -> validator.validate(definition(true)));
  }

  @Test
  void unfinishedCanvasCanBeSavedAsDraftButCannotBePublished() {
    var d =
        new WorkflowDefinition(
            null,
            0,
            "draft",
            "requisition",
            "71002",
            "",
            null,
            "DRAFT",
            List.of(
                new Node("start", "START", "开始", null, "ANY", List.of(), List.of(), List.of())));
    assertDoesNotThrow(() -> validator.validate(d, false));
    assertThrows(IllegalArgumentException.class, () -> validator.validate(d, true));
  }

  @Test
  void connectionsCannotJumpOutsideASubprocess() {
    var nodes =
        List.of(
            new Node("start", "START", "开始", "sub", "ANY", List.of(), List.of(), List.of()),
            new Node("sub", "SUBPROCESS", "子流程", "end", "ANY", List.of(), List.of(), List.of()),
            new Node(
                "inner", "START", "子开始", "end", "ANY", List.of(), List.of(), List.of(), "sub",
                null),
            new Node("end", "END", "结束", null, "ANY", List.of(), List.of(), List.of()));
    var error =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                validator.validate(
                    new WorkflowDefinition(
                        null, 0, "boundary", "requisition", "71002", "", null, "DRAFT", nodes),
                    false));
    assertTrue(error.getMessage().contains("跨子流程"));
  }
}
