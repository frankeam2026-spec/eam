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

  public Object upload(InspectionUpload file) throws Exception {
    com.windasoft.eam.common.inspection.InspectionSupport.require(
        permissions.anyInspection("查看"), "没有点巡检访问权限");
    com.windasoft.eam.common.inspection.InspectionSupport.require(
        !file.empty() && file.size() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(file.name(), "附件");
    name = Path.of(name).getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    com.windasoft.eam.common.inspection.InspectionSupport.require(
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
    com.windasoft.eam.common.inspection.InspectionSupport.require(
        id.matches("[0-9a-f-]{36}"), "附件不存在");
    var files = repository.find(auth.tenantId(), id);
    com.windasoft.eam.common.inspection.InspectionSupport.require(!files.isEmpty(), "附件不存在");
    var f = files.getFirst();
    boolean allowed =
        com.windasoft.eam.common.inspection.InspectionSupport.number(f.get("user_id"))
            == auth.userId();
    if (!allowed) allowed = lists.hasFile(id);
    if (!allowed) com.windasoft.eam.common.inspection.InspectionSupport.deny();
    var file = repository.path(id, Objects.toString(f.get("extension")));
    com.windasoft.eam.common.inspection.InspectionSupport.require(
        Files.isRegularFile(file), "附件不存在");
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
