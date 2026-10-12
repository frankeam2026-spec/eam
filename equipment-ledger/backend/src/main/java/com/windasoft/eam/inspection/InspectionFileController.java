package com.windasoft.eam.inspection;

import com.windasoft.eam.system.*;
import java.nio.file.*;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1/inspection/files")
public class InspectionFileController {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final JdbcTemplate db;
  private final InspectionService service;

  @Value("${eam.upload.directory:./data/uploads}")
  private String directory;

  public InspectionFileController(
      AuthContext auth, PermissionService permissions, JdbcTemplate db, InspectionService service) {
    this.auth = auth;
    this.permissions = permissions;
    this.db = db;
    this.service = service;
  }

  @PostMapping
  public Object upload(@RequestPart("file") MultipartFile file) throws Exception {
    InspectionService.require(permissions.anyInspection("查看"), "没有点巡检访问权限");
    InspectionService.require(
        !file.isEmpty() && file.getSize() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(file.getOriginalFilename(), "附件");
    name = Path.of(name).getFileName().toString();
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase();
    InspectionService.require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    Path path = Path.of(directory, "inspection").toAbsolutePath().normalize();
    Files.createDirectories(path);
    file.transferTo(path.resolve(id + ext));
    String type = Objects.toString(file.getContentType(), "application/octet-stream");
    db.update(
        "INSERT INTO eam_inspection_file(id,tenant_id,user_id,name,extension,content_type,size)"
            + " VALUES(?,?,?,?,?,?,?)",
        id,
        auth.tenantId(),
        auth.userId(),
        name,
        ext,
        type,
        file.getSize());
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
            file.getSize(),
            "url",
            "/api/v1/inspection/files/" + id));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
    InspectionService.require(id.matches("[0-9a-f-]{36}"), "附件不存在");
    var files =
        db.queryForList(
            "SELECT * FROM eam_inspection_file WHERE tenant_id=? AND id=?", auth.tenantId(), id);
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
    var file =
        Path.of(directory, "inspection", id + f.get("extension")).toAbsolutePath().normalize();
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
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "inline; filename*=UTF-8''"
                + java.net.URLEncoder.encode(
                        Objects.toString(f.get("name")), java.nio.charset.StandardCharsets.UTF_8)
                    .replace("+", "%20"))
        .contentType(MediaType.parseMediaType(type))
        .body(new FileSystemResource(file));
  }
}
