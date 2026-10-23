package com.windasoft.eam.service.system;

import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts.Retention;
import com.windasoft.eam.repository.system.NotificationRetentionRepository;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationRetentionService {
  private final NotificationRetentionRepository repo;
  private final PermissionService permissions;
  private final AuthContext auth;
  public NotificationRetentionService(NotificationRetentionRepository repo, PermissionService permissions, AuthContext auth) {
    this.repo=repo;this.permissions=permissions;this.auth=auth;
  }
  public Map<String,Object> settings() {
    permissions.require("notificationDelivery","查看");return repo.settings(auth.tenantId(),false);
  }
  @Transactional public Map<String,Object> save(Retention input) {
    permissions.require("notificationChannel","编辑");
    NotificationPolicyService.require(input.successDays()>=1 && input.successDays()<=3650 && input.exceptionDays()>=1 && input.exceptionDays()<=3650,"日志保留天数须为1至3650天");
    var old=repo.settings(auth.tenantId(),true);
    VersionGuard.checkPayload((Number)old.get("rowVersion"),Map.of("rowVersion",input.rowVersion()));
    repo.save(auth.tenantId(),input);return repo.settings(auth.tenantId(),false);
  }
  // Same parent-message-before-delivery lock order as reminder delivery creation.
  @Transactional public boolean compact(long tenant,long message,long delivery) {
    return repo.compact(tenant,message,delivery);
  }
}
