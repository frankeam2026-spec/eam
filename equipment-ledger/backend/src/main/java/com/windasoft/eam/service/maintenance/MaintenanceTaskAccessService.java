package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskData;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskSnapshot;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceTaskAccessService {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public MaintenanceTaskAccessService(
      ObjectMapper json, AuthContext auth, PermissionService permissions) {
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  /** 任务人员具有原查看例外；验收仍同时核对验收权限，不提升为设备或计划的查看权限。 */
  public boolean allowed(MaintenanceTaskData row, String action) {
    var snapshot = json.convertValue(row.snapshot(), MaintenanceTaskSnapshot.class);
    var owners = users(snapshot.getOwners());
    var reviewers = users(snapshot.getReviewers());
    if (action.equals("查看")
        && (owners.contains(auth.userId()) || reviewers.contains(auth.userId()))) {
      return true;
    }
    try {
      var scope = permissions.maintenance(action);
      if (action.equals("验收") && scope.owner() && reviewers.contains(auth.userId())) {
        return true;
      }
      return scope.matches(row.companyId(), row.departmentId(), row.ownerId(), auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
  }

  private List<Long> users(List<Long> values) {
    return values == null
        ? List.of()
        : values.stream().filter(id -> id != null && id > 0).distinct().toList();
  }
}
