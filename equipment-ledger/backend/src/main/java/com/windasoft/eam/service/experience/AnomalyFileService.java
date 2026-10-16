package com.windasoft.eam.service.experience;

import static com.windasoft.eam.common.experience.ExperienceSupport.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.experience.AnomalyDownload;
import com.windasoft.eam.dto.experience.AnomalyUpload;
import com.windasoft.eam.repository.experience.AnomalyFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.*;
import java.util.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

@org.springframework.stereotype.Service
public class AnomalyFileService {
  final AnomalyService service;
  final PermissionService permissions;
  final AuthContext auth;
  final AnomalyFileRepository repository;
  final ObjectMapper json;

  public AnomalyFileService(
      AnomalyService service,
      PermissionService permissions,
      AuthContext auth,
      AnomalyFileRepository repository,
      ObjectMapper json) {
    this.service = service;
    this.permissions = permissions;
    this.auth = auth;
    this.repository = repository;
    this.json = json;
  }

  public Object upload(AnomalyUpload file) throws Exception {
    check(
        permissions.allowed("anomaly", "新增")
            || permissions.allowed("anomaly", "消除")
            || permissions.menuActions().containsKey("maintenanceTask")
            || permissions.menuActions().containsKey("inspectionDailyTask")
            || permissions.menuActions().containsKey("inspectionPeriodicTask"),
        "没有异常附件上传权限");
    check(!file.empty() && file.size() <= 20L * 1024 * 1024, "请选择不超过20MB的文件");
    String name = Path.of(Objects.toString(file.name(), "附件")).getFileName().toString();
    check(name.length() <= 255, "文件名过长");
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    check(ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "附件格式不支持");
    String id = UUID.randomUUID().toString();
    repository.store(id, ext, file.content());
    String type = Objects.toString(file.contentType(), "application/octet-stream");
    repository.insertFile(id, auth.tenantId(), auth.userId(), name, ext, type, file.size());
    return Map.of(
        "data",
        Map.of(
            "id",
            id,
            "name",
            name,
            "contentType",
            type,
            "size",
            file.size(),
            "source",
            "anomalies",
            "url",
            "/api/v1/anomalies/files/" + id));
  }

  public AnomalyDownload temporary(String fileId) {
    check(fileId.matches("[0-9a-f-]{36}"), "附件不存在");
    var rows = repository.findTemporary(auth.tenantId(), auth.userId(), fileId);
    check(rows.size() == 1, "附件不存在");
    var f = rows.getFirst();
    Path path = repository.path("anomalies", fileId, text(f, "extension"));
    check(Files.isRegularFile(path), "附件不存在");
    String type =
        switch (text(f, "extension")) {
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".png" -> "image/png";
          case ".gif" -> "image/gif";
          case ".webp" -> "image/webp";
          default -> "application/octet-stream";
        };
    return new AnomalyDownload(path, type, null);
  }

  public AnomalyDownload file(long id, String fileId) {
    var a = service.raw(id, false);
    if (!service.canView(a))
      throw new org.springframework.web.server.ResponseStatusException(
          HttpStatus.FORBIDDEN, "没有此异常附件的查看权限");
    var files = rows(decode(json, a.get("attachments")));
    var ref =
        files.stream()
            .filter(f -> fileId.equals(text(f, "id")))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("附件不存在"));
    String source = service.fileSource(ref, a);
    String table =
        source.equals("maintenance")
            ? "eam_maintenance_file"
            : source.equals("inspection") ? "eam_inspection_file" : "eam_anomaly_file";
    var rs = repository.findSource(auth.tenantId(), fileId, table);
    check(rs.size() == 1 && fileId.matches("[0-9a-f-]{36}"), "附件不存在");
    var r = rs.getFirst();
    Path path = repository.path(source, fileId, text(r, "extension"));
    check(Files.isRegularFile(path), "附件文件不存在");
    String content =
        switch (text(r, "extension")) {
          case ".jpg", ".jpeg" -> "image/jpeg";
          case ".png" -> "image/png";
          case ".gif" -> "image/gif";
          case ".webp" -> "image/webp";
          case ".pdf" -> "application/pdf";
          default -> "application/octet-stream";
        };
    return new AnomalyDownload(path, content, text(r, "name"));
  }
}
