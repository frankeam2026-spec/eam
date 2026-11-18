package com.windasoft.eam.service.system;

import com.windasoft.eam.common.VersionGuard;
import com.windasoft.eam.dto.system.NotificationContracts.Retention;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.entity.system.NotifyRetention;
import com.windasoft.eam.repository.system.NotificationRetentionRepository;
import com.windasoft.eam.repository.system.NotifyRetentionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 通知保留服务：负责过期通知清理，保留期限与投递策略分离。 */
@Service
public class NotificationRetentionService {
  private final NotificationRetentionRepository repo;
  private final NotifyRetentionRepository settings;
  private final PermissionService permissions;
  private final AuthContext auth;

  public NotificationRetentionService(
      NotificationRetentionRepository repo,
      NotifyRetentionRepository settings,
      PermissionService permissions,
      AuthContext auth) {
    this.repo = repo;
    this.settings = settings;
    this.permissions = permissions;
    this.auth = auth;
  }

  public NotificationViews.Retention settings() {
    permissions.require("notificationDelivery", "查看");
    return view(settings.find(auth.tenantId(), false));
  }

  @Transactional
  public NotificationViews.Retention save(Retention input) {
    permissions.require("notificationChannel", "编辑");
    NotificationPolicyService.require(
        input.successDays() >= 1
            && input.successDays() <= 3650
            && input.exceptionDays() >= 1
            && input.exceptionDays() <= 3650,
        "日志保留天数须为1至3650天");
    var old = settings.find(auth.tenantId(), true);
    VersionGuard.check(old.getRowVersion(), input.rowVersion());
    settings.save(auth.tenantId(), input);
    return view(settings.find(auth.tenantId(), false));
  }

  private NotificationViews.Retention view(NotifyRetention row) {
    return new NotificationViews.Retention(
        row.getRowVersion(), row.getSuccessDays(), row.getExceptionDays());
  }

  // 与提醒创建保持相同的父消息→投递记录加锁顺序。
  @Transactional
  public boolean compact(long tenant, long message, long delivery) {
    return repo.compact(tenant, message, delivery);
  }
}
