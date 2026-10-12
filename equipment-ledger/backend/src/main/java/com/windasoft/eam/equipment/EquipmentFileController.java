package com.windasoft.eam.equipment;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/equipment/files")
public class EquipmentFileController {
  private final EquipmentFiles files;

  public EquipmentFileController(EquipmentFiles files) {
    this.files = files;
  }

  @PostMapping
  public Object upload(@RequestPart("file") MultipartFile file) throws Exception {
    return Map.of("data", files.upload(file));
  }

  @GetMapping("/{id}")
  public ResponseEntity<FileSystemResource> download(@PathVariable String id) {
    return files.download(id);
  }

  @GetMapping("/{id}/preview")
  public ResponseEntity<FileSystemResource> preview(@PathVariable String id) {
    return files.preview(id);
  }
}

@Service
class EquipmentFiles {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final JdbcTemplate db;
  private final EquipmentMapper equipment;
  private final ObjectMapper json;

  @Value("${eam.upload.directory:./data/uploads}")
  private String directory;

  EquipmentFiles(
      AuthContext auth,
      PermissionService permissions,
      JdbcTemplate db,
      EquipmentMapper equipment,
      ObjectMapper json) {
    this.auth = auth;
    this.permissions = permissions;
    this.db = db;
    this.equipment = equipment;
    this.json = json;
  }

  private void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }

  private Map<String, Object> file(String id) {
    require(id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), "附件编号无效");
    var list =
        db.queryForList(
            "SELECT * FROM eam_equipment_file WHERE tenant_id=? AND id=?", auth.tenantId(), id);
    if (list.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
    return list.getFirst();
  }

  private boolean mayRead(Map<String, Object> file) {
    var query =
        new LambdaQueryWrapper<Equipment>()
            .eq(Equipment::getTenantId, auth.tenantId())
            .and(
                w ->
                    w.like(Equipment::getDocumentsJson, file.get("id"))
                        .or()
                        .eq(Equipment::getImageFileId, file.get("id")));
    boolean attached =
        equipment.selectList(query).stream()
            .anyMatch(e -> references(e, file.get("id").toString()));
    if (!attached) return ((Number) file.get("user_id")).longValue() == auth.userId();
    permissions.filterEquipment(query);
    return equipment.selectList(query).stream()
        .anyMatch(e -> references(e, file.get("id").toString()));
  }

  private boolean references(Equipment e, String id) {
    return id.equals(e.getImageFileId()) || references(e.getDocumentsJson(), id);
  }

  private boolean references(String value, String id) {
    try {
      for (JsonNode doc : json.readTree(value)) if (id.equals(doc.path("id").asText())) return true;
    } catch (Exception ignored) {
    }
    return false;
  }

  private void checkRead(Map<String, Object> file) {
    if (!mayRead(file)) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有此设备附件的访问权限");
  }

  private String url(String id) {
    return "/api/v1/equipment/files/" + id;
  }

  Map<String, Object> upload(MultipartFile upload) throws Exception {
    if (!permissions.allowed("equipment", "新增") && !permissions.allowed("equipment", "编辑"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有上传设备附件的权限");
    require(!upload.isEmpty() && upload.getSize() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(upload.getOriginalFilename(), "附件").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    require(!name.isBlank() && name.length() <= 255, "附件名称需为1至255字");
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    Path root = Path.of(directory, "equipment").toAbsolutePath().normalize();
    Files.createDirectories(root);
    Path path = root.resolve(id + ext);
    upload.transferTo(path);
    String contentType = "application/octet-stream";
    try {
      if (Set.of(".png", ".jpg", ".jpeg", ".gif").contains(ext)) {
        try (var stream = ImageIO.createImageInputStream(path.toFile())) {
          var readers = ImageIO.getImageReaders(stream);
          require(readers.hasNext(), "图片内容无效");
          var reader = readers.next();
          try {
            reader.setInput(stream);
            require((long) reader.getWidth(0) * reader.getHeight(0) <= 40000000, "图片像素过大");
            String format = reader.getFormatName().toLowerCase(Locale.ROOT);
            require(Set.of("png", "jpeg", "gif").contains(format), "不支持该图片格式");
            require(reader.read(0) != null, "图片内容无效");
            contentType = "image/" + format;
          } finally {
            reader.dispose();
          }
        }
      }
      db.update(
          "INSERT INTO eam_equipment_file(id,tenant_id,user_id,name,extension,content_type,size)"
              + " VALUES(?,?,?,?,?,?,?)",
          id,
          auth.tenantId(),
          auth.userId(),
          name,
          ext,
          contentType,
          upload.getSize());
    } catch (Exception failure) {
      Files.deleteIfExists(path);
      throw failure;
    }
    return Map.of(
        "id",
        id,
        "name",
        name,
        "contentType",
        contentType,
        "size",
        upload.getSize(),
        "url",
        url(id));
  }

  JsonNode validate(JsonNode documents) {
    if (documents == null || documents.isNull()) return json.createArrayNode();
    require(documents.isArray() && documents.size() <= 100, "设备附件最多100个");
    JsonNode copy = documents.deepCopy();
    for (JsonNode doc : copy) {
      require(doc.isObject(), "附件格式无效");
      String id = doc.path("id").asText();
      if (id.isBlank()) {
        ((com.fasterxml.jackson.databind.node.ObjectNode) doc).remove("url");
        continue;
      }
      var stored = file(id);
      checkRead(stored);
      var object = (com.fasterxml.jackson.databind.node.ObjectNode) doc;
      object.put("url", url(id));
      object.put("size", ((Number) stored.get("size")).longValue());
      object.put("originalName", stored.get("name").toString());
    }
    return copy;
  }

  String validateImage(String id) {
    if (id == null || id.isBlank()) return "";
    var stored = file(id);
    checkRead(stored);
    require(stored.get("content_type").toString().startsWith("image/"), "请选择有效图片");
    return id;
  }

  ResponseEntity<FileSystemResource> preview(String id) {
    var stored = file(id);
    checkRead(stored);
    require(stored.get("content_type").toString().startsWith("image/"), "附件不是可预览图片");
    Path path =
        Path.of(directory, "equipment", id + stored.get("extension")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片内容不存在");
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header("Cache-Control", "private, no-store")
        .contentType(MediaType.parseMediaType(stored.get("content_type").toString()))
        .body(new FileSystemResource(path));
  }

  ResponseEntity<FileSystemResource> download(String id) {
    var stored = file(id);
    checkRead(stored);
    Path path =
        Path.of(directory, "equipment", id + stored.get("extension")).toAbsolutePath().normalize();
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件内容不存在");
    return ResponseEntity.ok()
        .header("X-Content-Type-Options", "nosniff")
        .header(
            "Content-Disposition",
            "attachment; filename*=UTF-8''"
                + URLEncoder.encode(stored.get("name").toString(), StandardCharsets.UTF_8)
                    .replace("+", "%20"))
        .contentType(MediaType.APPLICATION_OCTET_STREAM)
        .body(new FileSystemResource(path));
  }
}
