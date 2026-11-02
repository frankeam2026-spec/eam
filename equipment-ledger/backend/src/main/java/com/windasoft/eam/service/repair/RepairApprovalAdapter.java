package com.windasoft.eam.service.repair;

import com.windasoft.eam.service.workflow.WorkflowBusinessAdapter;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepairApprovalAdapter implements WorkflowBusinessAdapter {
  private final RepairApprovalService service;

  public RepairApprovalAdapter(RepairApprovalService service) {
    this.service = service;
  }

  public String businessType() {
    return "repair_report";
  }

  public void lock(long id) {
    service.lock(id);
  }

  public Map<String, Object> values(long id) {
    // 工作流变量注册接口按稳定变量名读取，仅在此边界适配为Map。
    return service.values(id).variables();
  }

  public void requireActive(long id) {
    service.requireActive(id);
  }

  public void approved(long id) {
    service.approved(id);
  }

  public void returned(long id, String reason) {
    service.returned(id, reason);
  }
}
