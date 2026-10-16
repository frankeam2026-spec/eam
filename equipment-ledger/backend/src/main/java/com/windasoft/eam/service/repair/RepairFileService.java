package com.windasoft.eam.service.repair;

import com.windasoft.eam.dto.repair.RepairDownload;
import com.windasoft.eam.dto.repair.RepairUpload;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class RepairFileService {
  private final RepairOrderRepository repository;
  private final RepairWorkflowService workflow;
  private final AuthContext auth;
  private final PermissionService permissions;

  public RepairFileService(
      RepairOrderRepository repository,
      RepairWorkflowService workflow,
      AuthContext auth,
      PermissionService permissions) {
    this.repository = repository;
    this.workflow = workflow;
    this.auth = auth;
    this.permissions = permissions;
  }

  public Map<String, Object> upload(RepairUpload file) throws IOException {
    if (!permissions.allowed("repair", "报修")
        && !permissions.allowed("repair", "处理")
        && !permissions.allowed("repair", "验收")) permissions.require("repair", "查看");
    if (file.empty()) throw new IllegalArgumentException("请选择文件");
    if (file.size() > 20L * 1024 * 1024) throw new IllegalArgumentException("单个附件不能超过20MB");
    String original =
        file.originalName() == null ? "附件" : Path.of(file.originalName()).getFileName().toString();
    String extension = "";
    int dot = original.lastIndexOf('.');
    if (dot >= 0) extension = original.substring(dot).toLowerCase();
    if (!extension.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)?"))
      throw new IllegalArgumentException("不支持该文件类型");
    String id = UUID.randomUUID().toString();
    repository.store(file.content(), id, extension);
    return Map.of(
        "data",
        Map.of(
            "id",
            id,
            "name",
            original,
            "contentType",
            file.contentType() == null ? "application/octet-stream" : file.contentType(),
            "size",
            file.size(),
            "url",
            "/api/v1/repairs/attachments/" + id));
  }

  public RepairDownload download(String id) throws IOException {
    var matches = repository.attachmentOrders(auth.tenantId(), id);
    if (matches.stream()
        .noneMatch(
            row -> {
              try {
                workflow.viewAllowed(row);
                return true;
              } catch (org.springframework.web.server.ResponseStatusException ignored) {
                return false;
              }
            })) throw new IllegalArgumentException("附件不存在");
    if (!id.matches("[0-9a-fA-F-]{36}")) throw new IllegalArgumentException("附件不存在");
    Path file = repository.attachmentPath(id);
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
