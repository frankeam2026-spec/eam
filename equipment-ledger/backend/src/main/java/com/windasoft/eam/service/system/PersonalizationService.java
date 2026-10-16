package com.windasoft.eam.service.system;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.BrandingImage;
import com.windasoft.eam.dto.system.BrandingUpload;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.repository.system.PersonalizationRepository;
import jakarta.validation.constraints.*;
import java.io.ByteArrayInputStream;
import java.util.*;
import javax.imageio.ImageIO;
import org.springframework.http.*;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@org.springframework.stereotype.Service
public class PersonalizationService {
  private static final String DEFAULT_NAME = "设备运维平台";
  private final PersonalizationRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public PersonalizationService(
      PersonalizationRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  private void access() {
    permissions.require("personalization", "查看");
  }

  private Map<String, Object> row(long tenant) {
    var rows = repository.configuration(tenant);
    return rows.isEmpty()
        ? new HashMap<>(
            Map.of(
                "tenant_id",
                tenant,
                "system_name",
                DEFAULT_NAME,
                "background_position",
                "center",
                "row_version",
                0))
        : rows.getFirst();
  }

  private String text(Object value) {
    return value == null ? "" : value.toString();
  }

  private String assetUrl(long tenant, Object value, String defaultUrl) {
    String id = text(value);
    return id.isBlank() ? defaultUrl : "/api/v1/branding/public/assets/" + tenant + "/" + id;
  }

  private Map<String, Object> publicView(long tenant, Map<String, Object> r) {
    return Map.of(
        "systemName",
        text(r.get("system_name")),
        "logoUrl",
        assetUrl(tenant, r.get("logo_file_id"), "/branding/default-logo.png"),
        "backgroundUrl",
        assetUrl(tenant, r.get("background_file_id"), "/branding/default-login-background.png"),
        "backgroundPosition",
        text(r.get("background_position")));
  }

  private Map<String, Object> view(long tenant, Map<String, Object> r) {
    var v = new HashMap<>(publicView(tenant, r));
    v.put("logoFileId", text(r.get("logo_file_id")));
    v.put("backgroundFileId", text(r.get("background_file_id")));
    v.put("rowVersion", r.get("row_version"));
    v.put("updatedAt", text(r.get("updated_at")));
    return v;
  }

  public Object publicConfig(long tenantId) {
    if (tenantId <= 0) throw new IllegalArgumentException("租户编号无效");
    return Map.of("data", publicView(tenantId, row(tenantId)));
  }

  public Object get() {
    access();
    return Map.of("data", view(auth.tenantId(), row(auth.tenantId())));
  }

  private String selectedAsset(String id, String kind, Map<String, Object> current) {
    if (id == null || id.isBlank()) return null;
    var rows = repository.assetOwner(auth.tenantId(), id, kind);
    if (rows.isEmpty())
      throw new IllegalArgumentException(kind.equals("logo") ? "请选择有效的Logo" : "请选择有效的登录背景");
    var currentId = current.get(kind.equals("logo") ? "logo_file_id" : "background_file_id");
    if (((Number) rows.getFirst().get("created_by")).longValue() != auth.userId()
        && !id.equals(currentId))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法使用其他人的未保存图片");
    return id;
  }

  @Transactional
  public Object save(PersonalizationForm f) throws Exception {
    access();
    String name = f.systemName().trim();
    if (name.isEmpty()) throw new IllegalArgumentException("请输入系统名称");
    if (!List.of("center", "top", "bottom", "left", "right").contains(f.backgroundPosition()))
      throw new IllegalArgumentException("背景位置无效");
    long tenant = auth.tenantId();
    var before = row(tenant);
    var logo = selectedAsset(f.logoFileId(), "logo", before);
    var bg = selectedAsset(f.backgroundFileId(), "background", before);
    repository.ensureConfiguration(tenant);
    if (repository.update(
            name, logo, bg, f.backgroundPosition(), auth.userId(), tenant, f.rowVersion())
        != 1) throw new ResponseStatusException(HttpStatus.CONFLICT, "配置已被他人修改，请刷新后重新保存");
    var after = row(tenant);
    repository.event(
        tenant,
        auth.userId(),
        json.writeValueAsString(view(tenant, before)),
        json.writeValueAsString(view(tenant, after)));
    return Map.of("data", view(tenant, after));
  }

  public Object upload(String kind, BrandingUpload file) throws Exception {
    access();
    if (!List.of("logo", "background").contains(kind)) throw new IllegalArgumentException("图片用途无效");
    int limit = kind.equals("logo") ? 2 : 10;
    if (file.empty() || file.size() > limit * 1024L * 1024)
      throw new IllegalArgumentException("图片不能超过" + limit + "MB");
    byte[] bytes = file.bytes();
    String type;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new IllegalArgumentException("请选择PNG或JPG图片");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("png", "jpeg", "jpg").contains(format))
          throw new IllegalArgumentException("请选择PNG或JPG图片");
        if ((long) reader.getWidth(0) * reader.getHeight(0) > 20_000_000)
          throw new IllegalArgumentException("图片分辨率过大，请压缩后上传");
        if (reader.read(0) == null) throw new IllegalArgumentException("图片无法读取");
        type = format.equals("png") ? "image/png" : "image/jpeg";
      } finally {
        reader.dispose();
      }
    } catch (java.io.IOException e) {
      throw new IllegalArgumentException("图片损坏，请重新选择");
    }
    String id = UUID.randomUUID().toString();
    repository.insertAsset(id, auth.tenantId(), kind, type, bytes, auth.userId());
    return Map.of("data", Map.of("id", id, "url", "/api/v1/personalization/assets/" + id));
  }

  public Object discardUpload(String id) {
    access();
    int count = repository.discardAsset(auth.tenantId(), auth.userId(), id);
    return Map.of("data", Map.of("deleted", count == 1));
  }

  private BrandingImage image(long tenant, String id, boolean published) {
    if (!id.matches("[0-9a-f-]{36}"))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
    if (published) {
      var config = row(tenant);
      if (!id.equals(config.get("logo_file_id")) && !id.equals(config.get("background_file_id")))
        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
    }
    var rows = repository.asset(tenant, id);
    if (rows.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "图片不存在");
    var r = rows.getFirst();
    if (!published && ((Number) r.get("created_by")).longValue() != auth.userId()) {
      var config = row(tenant);
      if (!id.equals(config.get("logo_file_id")) && !id.equals(config.get("background_file_id")))
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无法查看其他人的未保存图片");
    }
    return new BrandingImage((byte[]) r.get("image_data"), text(r.get("content_type")));
  }

  public BrandingImage privateAsset(String id) {
    access();
    return image(auth.tenantId(), id, false);
  }

  public BrandingImage publicAsset(long tenantId, String id) {
    return image(tenantId, id, true);
  }
}
