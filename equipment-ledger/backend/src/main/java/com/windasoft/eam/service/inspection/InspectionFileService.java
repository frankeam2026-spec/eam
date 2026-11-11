package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.deny;
import static com.windasoft.eam.common.inspection.InspectionSupport.number;
import static com.windasoft.eam.common.inspection.InspectionSupport.require;

import com.windasoft.eam.dto.inspection.InspectionDownload;
import com.windasoft.eam.dto.inspection.InspectionFileUploadResult;
import com.windasoft.eam.dto.inspection.InspectionUpload;
import com.windasoft.eam.repository.inspection.InspectionFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class InspectionFileService {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InspectionFileRepository repository;
  private final InspectionListService lists;

  public InspectionFileService(
      AuthContext auth,
      PermissionService permissions,
      InspectionFileRepository repository,
      InspectionListService lists) {
    this.auth = auth;
    this.permissions = permissions;
    this.repository = repository;
    this.lists = lists;
  }

  /** 文件与元数据登记成功后返回业务结果；登记失败仍清理文件，data响应包装由Controller负责。 */
  public InspectionFileUploadResult upload(InspectionUpload file) throws Exception {
    require(permissions.anyInspection("查看"), "没有点巡检访问权限");
    require(!file.empty() && file.size() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(file.name(), "附件");
    name = Path.of(name).getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    String type = Objects.toString(file.contentType(), "application/octet-stream");
    try {
      repository.store(id, ext, file.content());
      repository.insert(id, auth.tenantId(), auth.userId(), name, ext, type, file.size());
    } catch (Exception failure) {
      try {
        repository.delete(id, ext);
      } catch (Exception cleanupFailure) {
        failure.addSuppressed(cleanupFailure);
      }
      throw failure;
    }
    return new InspectionFileUploadResult(
        id, name, type, file.size(), "/api/v1/inspection/files/" + id);
  }

  public InspectionDownload download(String id) {
    require(id.matches("[0-9a-f-]{36}"), "附件不存在");
    var files = repository.find(auth.tenantId(), id);
    require(!files.isEmpty(), "附件不存在");
    var f = files.getFirst();
    boolean allowed = number(f.get("user_id")) == auth.userId();
    if (!allowed) allowed = lists.hasFile(id);
    if (!allowed) deny();
    var file = repository.path(id, Objects.toString(f.get("extension")));
    require(Files.isRegularFile(file), "附件不存在");
    String ext = Objects.toString(f.get("extension"));
    String type =
        switch (ext) {
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".png" -> "image/png";
          case ".gif" -> "image/gif";
          case ".webp" -> "image/webp";
          case ".pdf" -> "application/pdf";
          default -> "application/octet-stream";
        };
    return new InspectionDownload(file, type, Objects.toString(f.get("name")));
  }
}
