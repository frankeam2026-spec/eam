package com.windasoft.eam.service.repair;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.repair.RepairDownload;
import com.windasoft.eam.dto.repair.RepairFileView;
import com.windasoft.eam.dto.repair.RepairUpload;
import com.windasoft.eam.entity.repair.RepairUploadRecord;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.repository.repair.RepairUploadRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

/** 维修附件业务入口：校验上传文件及下载者对关联工单的可见权限。 文件读写交给 RepairFileStorage，工单与附件关系由 Repository 查询。 */
@Service
public class RepairFileService {
  private final RepairOrderRepository repository;
  private final RepairFileStorage storage;
  private final RepairAccessService access;
  private final RepairUploadRepository uploads;
  private final AuthContext auth;
  private final PermissionService permissions;

  public RepairFileService(
      RepairOrderRepository repository,
      RepairFileStorage storage,
      RepairAccessService access,
      RepairUploadRepository uploads,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.storage = storage;
    this.access = access;
    this.uploads = uploads;
    this.auth = auth;
    this.permissions = permissions;
  }

  /** 附件上传只生成文件标识；附件是否属于某张工单，在后续保存报修或维修记录时建立关联。 不允许仅凭上传返回的 URL 绕过工单权限下载文件。 */
  public RepairFileView upload(RepairUpload file) throws IOException {
    if (!permissions.allowed("repair", "报修")
        && !permissions.allowed("repair", "处理")
        && !permissions.allowed("repair", "验收")) permissions.require("repair", "查看");
    if (file.empty()) throw new LocalizedArgumentException("server.repair.message0047");
    if (file.size() > 20L * 1024 * 1024)
      throw new LocalizedArgumentException("server.repair.message0048");
    String original =
        file.originalName() == null ? "附件" : Path.of(file.originalName()).getFileName().toString();
    String extension = "";
    int dot = original.lastIndexOf('.');
    if (dot >= 0) extension = original.substring(dot).toLowerCase();
    if (!extension.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)?"))
      throw new LocalizedArgumentException("server.repair.message0049");
    String id = UUID.randomUUID().toString();
    storage.store(file.content(), id, extension);
    var registration = new RepairUploadRecord();
    registration.setId(id);
    registration.setTenantId(auth.tenantId());
    registration.setUploadedBy(auth.userId());
    try {
      uploads.register(registration);
    } catch (RuntimeException failedRegistration) {
      try {
        storage.remove(id, extension);
      } catch (IOException cleanupFailure) {
        failedRegistration.addSuppressed(cleanupFailure);
      }
      throw failedRegistration;
    }

    return new RepairFileView(
        id,
        original,
        file.contentType() == null ? "application/octet-stream" : file.contentType(),
        file.size(),
        "/api/v1/repairs/attachments/" + id);
  }

  /** 下载前同时核实附件关联和工单可见性；任一关联工单可见才允许读取文件。 无有效关联、无可见工单或文件不存在均拒绝返回文件，避免裸文件 ID 成为访问凭证。 */
  public RepairDownload download(String id) throws IOException {
    // 在关联查询和文件定位之前校验附件 ID，避免无效输入触发数据库访问。
    if (id == null || !id.matches("[0-9a-fA-F-]{36}"))
      throw new LocalizedArgumentException("server.repair.message0011");
    var original = uploads.ownership(id);
    if (original != null && !Objects.equals(original.getTenantId(), auth.tenantId())) {
      throw new LocalizedArgumentException("server.repair.message0011");
    }
    var matches = repository.attachmentOrders(auth.tenantId(), id);
    if (matches.stream()
        .noneMatch(
            row -> {
              try {
                access.requireView(row);
                return true;
              } catch (ResponseStatusException ignored) {
                return false;
              }
            })) throw new LocalizedArgumentException("server.repair.message0011");
    Path file = storage.attachmentPath(id);
    String ext = file.getFileName().toString().substring(id.length() + 1);
    String type =
        switch (ext) {
          case "jpg", "jpeg" -> "image/jpeg";
          case "png" -> "image/png";
          case "gif" -> "image/gif";
          case "webp" -> "image/webp";
          case "pdf" -> "application/pdf";
          default -> "application/octet-stream";
        };
    return new RepairDownload(file, type);
  }
}
