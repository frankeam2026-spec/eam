package com.windasoft.eam.service.inspection;

import com.windasoft.eam.dto.inspection.*;
import com.windasoft.eam.repository.inspection.InspectionFileRepository;
import com.windasoft.eam.service.system.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InspectionFileService {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final InspectionFileRepository repository;
  private final InspectionService service;

  public InspectionFileService(
      AuthContext auth,
      PermissionService permissions,
      InspectionFileRepository repository,
      InspectionService service) {
    this.auth = auth;
    this.permissions = permissions;
    this.repository = repository;
    this.service = service;
  }

  public Object upload(InspectionUpload file) throws Exception {
    InspectionService.require(permissions.anyInspection("查看"), "没有点巡检访问权限");
    InspectionService.require(!file.empty() && file.size() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(file.name(), "附件");
    name = Path.of(name).getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    InspectionService.require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    repository.store(id, ext, file.content());
    String type = Objects.toString(file.contentType(), "application/octet-stream");
    repository.insert(id, auth.tenantId(), auth.userId(), name, ext, type, file.size());
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
            "url",
            "/api/v1/inspection/files/" + id));
  }

  public InspectionDownload download(String id) {
    InspectionService.require(id.matches("[0-9a-f-]{36}"), "附件不存在");
    var files = repository.find(auth.tenantId(), id);
    InspectionService.require(!files.isEmpty(), "附件不存在");
    var f = files.getFirst();
    boolean allowed = InspectionService.number(f.get("user_id")) == auth.userId();
    if (!allowed) {
      for (var t : service.tasks())
        if (service.encode(t).contains(id)) {
          allowed = true;
          break;
        }
    }
    if (!allowed) {
      for (String kind : List.of("standards", "daily-plans", "periodic-plans"))
        if (permissions.allowed(PermissionService.inspectionMenu(kind), "查看")
            && service.definitions(kind).stream().anyMatch(d -> service.encode(d).contains(id))) {
          allowed = true;
          break;
        }
    }
    if (!allowed) InspectionService.deny();
    var file = repository.path(id, Objects.toString(f.get("extension")));
    InspectionService.require(Files.isRegularFile(file), "附件不存在");
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
