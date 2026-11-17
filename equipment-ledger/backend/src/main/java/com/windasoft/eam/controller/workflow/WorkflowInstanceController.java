package com.windasoft.eam.controller.workflow;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.service.workflow.WorkflowRuntimeService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/workflow/instances")
public class WorkflowInstanceController {
  private final WorkflowRuntimeService service;

  public WorkflowInstanceController(WorkflowRuntimeService service) {
    this.service = service;
  }

  /** 决定与版本必须显式提交；基本类型缺省值会把漏传误当驳回或初始版本。 */
  public record Action(
      @NotNull @Positive @JsonDeserialize(using = ExactLongDeserializer.class) Long approvalId,
      @NotNull(message = "{server.common.message0018}")
          @PositiveOrZero
          @JsonDeserialize(using = ExactLongDeserializer.class)
          Long rowVersion,
      @NotNull(message = "{ui.equipment.text1086}") Boolean approve,
      String opinion) {}

  public record Withdrawal(
      @NotNull(message = "{server.common.message0018}")
          @PositiveOrZero
          @JsonDeserialize(using = ExactLongDeserializer.class)
          Long rowVersion) {}

  @PostMapping("/{id}/act")
  public DataResponse<List<WorkflowHistoryRound>> act(
      @PathVariable long id, @Valid @RequestBody Action body) {
    return new DataResponse<>(
        service.act(id, body.approvalId(), body.rowVersion(), body.approve(), body.opinion()));
  }

  @PostMapping("/{id}/withdraw")
  public DataResponse<List<WorkflowHistoryRound>> withdraw(
      @PathVariable long id, @Valid @RequestBody Withdrawal body) {
    return new DataResponse<>(service.withdraw(id, body.rowVersion()));
  }
}
