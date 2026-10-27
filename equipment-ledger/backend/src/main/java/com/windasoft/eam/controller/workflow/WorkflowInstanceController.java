package com.windasoft.eam.controller.workflow;

import com.windasoft.eam.service.workflow.WorkflowRuntimeService;
import java.util.Map;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/workflow/instances")
public class WorkflowInstanceController {
  private final WorkflowRuntimeService service;

  public WorkflowInstanceController(WorkflowRuntimeService service) {
    this.service = service;
  }

  public record Action(long approvalId, long rowVersion, boolean approve, String opinion) {}

  public record Withdrawal(long rowVersion) {}

  @PostMapping("/{id}/act")
  public Object act(@PathVariable long id, @RequestBody Action body) {
    return Map.of(
        "data",
        service.act(id, body.approvalId(), body.rowVersion(), body.approve(), body.opinion()));
  }

  @PostMapping("/{id}/withdraw")
  public Object withdraw(@PathVariable long id, @RequestBody Withdrawal body) {
    return Map.of("data", service.withdraw(id, body.rowVersion()));
  }
}
