package com.windasoft.eam.service.repair;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.repair.RepairCandidateRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 共用维修详情可见性规则，附件及详情复用同一参与资格，不借用更宽菜单授权。 */
@Service
public class RepairAccessService {
  private final RepairCandidateRepository candidates;
  private final RepairInboxRepository inboxes;
  private final AuthContext auth;
  private final PermissionService permissions;

  public RepairAccessService(
      RepairCandidateRepository candidates,
      RepairInboxRepository inboxes,
      AuthContext auth,
      PermissionService permissions) {
    this.candidates = candidates;
    this.inboxes = inboxes;
    this.auth = auth;
    this.permissions = permissions;
  }

  public void requireView(RepairOrder row) {
    if (!Objects.equals(row.getTenantId(), auth.tenantId())) {
      throw new ResponseStatusException(HttpStatus.FORBIDDEN);
    }
    if (candidates.candidate(auth.tenantId(), row.getId(), auth.userId()) > 0
        || inboxes.hasInbox(auth.tenantId(), auth.userId(), row.getId()) > 0) {
      return;
    }
    permissions.checkRepair(row, "查看");
  }
}
