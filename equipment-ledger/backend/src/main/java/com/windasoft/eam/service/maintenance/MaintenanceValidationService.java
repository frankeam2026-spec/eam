package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.ids;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.rows;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.text;

import com.windasoft.eam.common.MessageContext;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 保养人员与附件输入的业务校验；用户及附件事实分别从其所属仓储读取。 */
@Service
public class MaintenanceValidationService {
  private final SystemUserRepository users;
  private final MaintenanceFileRepository files;
  private final AuthContext auth;
  private final MaintenanceListService lists;

  public MaintenanceValidationService(
      SystemUserRepository users,
      MaintenanceFileRepository files,
      AuthContext auth,
      MaintenanceListService lists) {
    this.users = users;
    this.files = files;
    this.auth = auth;
    this.lists = lists;
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

  /**
   * 关联写入前，文件必须由本人上传或已在可查看的保养单据中。否则仅凭同租户 UUID 就能把私有文件挂到自己的单据并获得下载权。先校验存在性和图片格式，再检查引用权限； 兼容旧请求省略
   * ID，但显式 ID 必须与下载 URL 一致。
   */
  void validAttachments(Object value, boolean imagesOnly) {
    for (var a : rows(value)) {
      var url = text(a, "url");
      require(
          url.matches("/api/v1/maintenance/files/[0-9a-f-]{36}"),
          MessageContext.text("server.inspection.message0075"));
      String id = url.substring(url.lastIndexOf('/') + 1);
      var file = files.find(auth.tenantId(), id);
      require(file != null, MessageContext.text("server.repair.message0011"));
      if (imagesOnly) {
        require(
            files.exists(auth.tenantId(), id, true),
            MessageContext.text("server.maintenance.message0014"));
      }
      require(
          text(a, "id").isEmpty() || id.equals(text(a, "id")),
          MessageContext.text("server.inspection.message0075"));
      if (!Objects.equals(file.getUserId(), auth.userId()) && !lists.hasFile(id)) {
        deny();
      }
    }
  }
}
