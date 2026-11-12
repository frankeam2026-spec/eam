package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;

import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.AuthContext;
import org.springframework.stereotype.Service;

/** 保养人员与附件输入的业务校验；用户及附件事实分别从其所属仓储读取。 */
@Service
public class MaintenanceValidationService {
  private final SystemUserRepository users;
  private final MaintenanceFileRepository files;
  private final AuthContext auth;

  public MaintenanceValidationService(
      SystemUserRepository users, MaintenanceFileRepository files, AuthContext auth) {
    this.users = users;
    this.files = files;
    this.auth = auth;
  }

  /** 新选人员必须仍可用；历史姓名查询不能替代此处的启用、未删除约束。 */
  void validUsers(Object value, boolean required) {
    var ids = ids(value);
    require(!required || !ids.isEmpty(), MessageContext.text("ui.equipment.text0336"));
    for (var id : ids) {
      require(
          users.activeUser(auth.tenantId(), id) != null,
          MessageContext.text("server.maintenance.message0013"));
    }
  }

  /** 先区分附件不存在和格式不符，沿用旧错误顺序；不接受外部 URL 代替本租户上传记录。 */
  void validAttachments(Object value, boolean imagesOnly) {
    for (var a : rows(value)) {
      var url = text(a, "url");
      require(
          url.matches("/api/v1/maintenance/files/[0-9a-f-]{36}"),
          MessageContext.text("server.inspection.message0075"));
      String id = url.substring(url.lastIndexOf('/') + 1);
      require(
          files.exists(auth.tenantId(), id, false),
          MessageContext.text("server.repair.message0011"));
      if (imagesOnly) {
        require(
            files.exists(auth.tenantId(), id, true),
            MessageContext.text("server.maintenance.message0014"));
      }
    }
  }
}
