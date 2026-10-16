package com.windasoft.eam.service.equipment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentDownload;
import com.windasoft.eam.dto.equipment.EquipmentUpload;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.*;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EquipmentFileService {
  private final AuthContext auth;
  private final PermissionService permissions;
  private final EquipmentFileRepository repository;
  private final ObjectMapper json;

  public EquipmentFileService(
      AuthContext auth,
      PermissionService permissions,
      EquipmentFileRepository repository,
      ObjectMapper json) {
    this.auth = auth;
    this.permissions = permissions;
    this.repository = repository;
    this.json = json;
  }

  private void require(boolean valid, String message) {
    if (!valid) throw new IllegalArgumentException(message);
  }

  private Map<String, Object> file(String id) {
    require(id.matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}"), "附件编号无效");
    var list = repository.file(auth.tenantId(), id);
    if (list.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件不存在");
    return list.getFirst();
  }

  private boolean mayRead(Map<String, Object> file) {
    String id = file.get("id").toString();
    boolean attached =
        repository.attachedDevices(auth.tenantId(), id, q -> {}).stream()
            .anyMatch(e -> references(e, id));
    if (!attached) return ((Number) file.get("user_id")).longValue() == auth.userId();
    return repository.attachedDevices(auth.tenantId(), id, permissions::filterEquipment).stream()
        .anyMatch(e -> references(e, id));
  }

  private boolean references(Equipment e, String id) {
    return id.equals(e.getImageFileId()) || references(e.getImagesJson(), id) || references(e.getDocumentsJson(), id);
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

  public Map<String, Object> upload(EquipmentUpload upload) throws Exception {
    if (!permissions.allowed("equipment", "新增") && !permissions.allowed("equipment", "编辑"))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "没有上传设备附件的权限");
    require(!upload.empty() && upload.size() <= 20L * 1024 * 1024, "请选择不超过20MB的附件");
    String name = Objects.toString(upload.originalName(), "附件").replace('\\', '/');
    name = name.substring(name.lastIndexOf('/') + 1);
    require(!name.isBlank() && name.length() <= 255, "附件名称需为1至255字");
    int dot = name.lastIndexOf('.');
    String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
    require(
        ext.matches("\\.(jpg|jpeg|png|gif|webp|pdf|doc|docx|xls|xlsx|txt|zip|csv)"), "不支持该附件格式");
    String id = UUID.randomUUID().toString();
    Path path = repository.store(upload.content(), id, ext);
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
      repository.insert(id, auth.tenantId(), auth.userId(), name, ext, contentType, upload.size());
    } catch (Exception failure) {
      repository.deleteContent(path);
      throw failure;
    }
    return Map.of(
        "id", id, "name", name, "contentType", contentType, "size", upload.size(), "url", url(id));
  }

  public JsonNode validate(JsonNode documents) {
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

  public JsonNode validateImages(JsonNode images, String legacyId, String legacyName) {
    var result = json.createArrayNode();
    if (images == null || images.isNull()) {
      if (legacyId != null && !legacyId.isBlank())
        result.addObject().put("id", validateImage(legacyId)).put("name", Objects.toString(legacyName, ""));
      return result;
    }
    require(images.isArray(), "设备图片格式无效");
    var seen = new HashSet<String>();
    for (var image : images) {
      var id = image.path("id").asText("");
      require(!id.isBlank(), "设备图片编号不能为空");
      if (!seen.add(id)) continue;
      validateImage(id);
      result.addObject().put("id", id).put("name", file(id).get("name").toString());
    }
    return result;
  }

  public String validateImage(String id) {
    if (id == null || id.isBlank()) return "";
    var stored = file(id);
    checkRead(stored);
    require(stored.get("content_type").toString().startsWith("image/"), "请选择有效图片");
    return id;
  }

  public EquipmentDownload preview(String id) {
    var stored = file(id);
    checkRead(stored);
    require(stored.get("content_type").toString().startsWith("image/"), "附件不是可预览图片");
    Path path = repository.contentPath(id, stored.get("extension").toString());
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片内容不存在");
    return new EquipmentDownload(
        path, stored.get("content_type").toString(), stored.get("name").toString());
  }

  public EquipmentDownload download(String id) {
    var stored = file(id);
    checkRead(stored);
    Path path = repository.contentPath(id, stored.get("extension").toString());
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "附件内容不存在");
    return new EquipmentDownload(
        path, stored.get("content_type").toString(), stored.get("name").toString());
  }
}
