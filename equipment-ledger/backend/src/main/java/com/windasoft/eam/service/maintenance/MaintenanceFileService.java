package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.deny;
import static com.windasoft.eam.common.maintenance.MaintenanceSupport.require;

import com.windasoft.eam.dto.maintenance.MaintenanceDownload;
import com.windasoft.eam.dto.maintenance.MaintenanceFileView;
import com.windasoft.eam.dto.maintenance.MaintenanceUpload;
import com.windasoft.eam.entity.maintenance.MaintenanceFile;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** 协调保养附件校验、元数据登记和磁盘补偿；下载关联授权仍复用保养列表的授权查询。 */
@Service
public class MaintenanceFileService {
  private static final long MAX_UPLOAD_BYTES = 20L * 1024 * 1024;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final MaintenanceFileRepository repository;
  private final MaintenanceFileStorage storage;
  private final MaintenanceListService lists;

  public MaintenanceFileService(
      AuthContext auth,
      PermissionService permissions,
      MaintenanceFileRepository repository,
      MaintenanceFileStorage storage,
      MaintenanceListService lists) {
    this.auth = auth;
    this.permissions = permissions;
    this.repository = repository;
    this.storage = storage;
    this.lists = lists;
  }

  /**
   * 上传只登记当前用户的附件，不建立计划或任务关联。文件复制不受数据库事务回滚保护， 复制或登记失败时删除本次 UUID 对应的文件，并保留原异常；HTTP 包装由 Controller 完成。
   */
  public MaintenanceFileView upload(MaintenanceUpload file) throws IOException {
    require(permissions.anyMaintenance("查看"), "没有保养访问权限");
    require(!file.empty() && file.size() <= MAX_UPLOAD_BYTES, "请选择不超过20MB的附件");
    String name = Path.of(Objects.toString(file.name(), "附件")).getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    String type = Objects.toString(file.contentType(), "application/octet-stream");
    var record = new MaintenanceFile();
    record.setId(id);
    record.setTenantId(auth.tenantId());
    record.setUserId(auth.userId());
    record.setName(name);
    record.setExtension(ext);
    record.setContentType(type);
    record.setSize(file.size());
    try {
      storage.store(id, ext, file.content());
      repository.insert(record);
    } catch (IOException | RuntimeException failure) {
      try {
        storage.delete(id, ext);
      } catch (IOException | RuntimeException cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return new MaintenanceFileView(id, name, type, file.size(), "/api/v1/maintenance/files/" + id);
  }

  /** 先按租户读取登记记录，再允许上传者本人或能查看附件所属保养单据的用户下载。 不把“有任意保养菜单权限”当作附件授权；响应 MIME 仍按已允许的扩展名决定。 */
  public MaintenanceDownload download(String id) {
    require(id.matches("[0-9a-f-]{36}"), "附件不存在");
    var file = repository.find(auth.tenantId(), id);
    require(file != null, "附件不存在");
    if (!Objects.equals(file.getUserId(), auth.userId()) && !lists.hasFile(id)) {
      deny();
    }
    var path = storage.path(id, file.getExtension());
    require(Files.isRegularFile(path), "附件不存在");
    String type =
        switch (file.getExtension()) {
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".png" -> "image/png";
          case ".gif" -> "image/gif";
          case ".webp" -> "image/webp";
          case ".pdf" -> "application/pdf";
          default -> "application/octet-stream";
        };
    return new MaintenanceDownload(path, type, file.getName());
  }
}
