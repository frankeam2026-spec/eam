package com.windasoft.eam.service.inventory;

import com.windasoft.eam.repository.inventory.RequisitionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.workflow.WorkflowBusinessAdapter;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RequisitionWorkflowAdapter implements WorkflowBusinessAdapter {
  private final RequisitionService requisitions;
  private final RequisitionRepository repository;
  private final AuthContext auth;

  public RequisitionWorkflowAdapter(
      RequisitionService requisitions, RequisitionRepository repository, AuthContext auth) {
    this.requisitions = requisitions;
    this.repository = repository;
    this.auth = auth;
  }

  public String businessType() {
    return "requisition";
  }

  public void lock(long id) {
    requisitions.lockForWorkflow(id);
  }

  public Map<String, Object> values(long id) {
    return repository.workflowValues(auth.tenantId(), id);
  }

  public void requireActive(long id) {
    requisitions.requireWorkflowTaskActive(id);
  }

  public void approved(long id) {
    requisitions.workflowApproved(id);
  }

  public void returned(long id, String reason) {
    requisitions.workflowReturned(id, reason);
  }
}
