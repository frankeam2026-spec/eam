package com.windasoft.eam.service.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.LocalizedStatusException;
import com.windasoft.eam.dto.common.DeletionResult;
import com.windasoft.eam.dto.system.BrandingAssetKind;
import com.windasoft.eam.dto.system.BrandingAssetView;
import com.windasoft.eam.dto.system.BrandingImage;
import com.windasoft.eam.dto.system.BrandingUpload;
import com.windasoft.eam.dto.system.PersonalizationForm;
import com.windasoft.eam.dto.system.PersonalizationView;
import com.windasoft.eam.dto.system.PublicBrandingView;
import com.windasoft.eam.entity.system.BrandingAsset;
import com.windasoft.eam.entity.system.BrandingEvent;
import com.windasoft.eam.entity.system.TenantBranding;
import com.windasoft.eam.repository.system.BrandingAssetRepository;
import com.windasoft.eam.repository.system.BrandingEventRepository;
import com.windasoft.eam.repository.system.TenantBrandingRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PersonalizationService {
  private static final String DEFAULT_NAME = "设备运维平台";
  private static final long MAX_IMAGE_PIXELS = 20_000_000L;
  private static final Set<String> BACKGROUND_POSITIONS =
      Set.of("center", "top", "bottom", "left", "right");
  private final TenantBrandingRepository configuration;
  private final BrandingAssetRepository assets;
  private final BrandingEventRepository events;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public PersonalizationService(
      TenantBrandingRepository configuration,
      BrandingAssetRepository assets,
      BrandingEventRepository events,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.configuration = configuration;
    this.assets = assets;
    this.events = events;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  /** 沿用现有菜单的查看授权，不另造编辑权限而改变已配置角色的行为。 */
  private void access() {
    permissions.require("personalization", "查看");
  }

  private TenantBranding defaults(long tenant) {
    var value = new TenantBranding();
    value.setTenantId(tenant);
    value.setSystemName(DEFAULT_NAME);
    value.setBackgroundPosition("center");
    value.setRowVersion(0);
    return value;
  }

  private TenantBranding row(long tenant) {
    var value = configuration.find(tenant);
    return value == null ? defaults(tenant) : value;
  }

  private String assetUrl(long tenant, String id, String defaultUrl) {
    return id == null || id.isBlank()
        ? defaultUrl
        : "/api/v1/branding/public/assets/" + tenant + "/" + id;
  }

  private PublicBrandingView publicView(long tenant, TenantBranding value) {
    return new PublicBrandingView(
        value.getSystemName(),
        assetUrl(tenant, value.getLogoFileId(), "/branding/default-logo.png"),
        assetUrl(tenant, value.getBackgroundFileId(), "/branding/default-login-background.png"),
        value.getBackgroundPosition());
  }

  private PersonalizationView view(long tenant, TenantBranding value) {
    var display = publicView(tenant, value);
    return new PersonalizationView(
        display.systemName(),
        display.logoUrl(),
        display.backgroundUrl(),
        display.backgroundPosition(),
        Objects.toString(value.getLogoFileId(), ""),
        Objects.toString(value.getBackgroundFileId(), ""),
        value.getRowVersion(),
        Objects.toString(value.getUpdatedAt(), ""));
  }

  public PublicBrandingView publicConfig(long tenantId) {
    if (tenantId <= 0) throw new LocalizedArgumentException("server.system.message0007");
    return publicView(tenantId, row(tenantId));
  }

  public PersonalizationView get() {
    access();
    long tenant = auth.tenantId();
    return view(tenant, row(tenant));
  }

  /** 当前配置已被锁定；只能选择本人上传的临时图片或保留当前发布图片，不能跨租户借用。 */
  private String selectedAsset(String id, BrandingAssetKind kind, TenantBranding current) {
    if (id == null || id.isBlank()) return null;
    var asset = assets.lockMetadata(auth.tenantId(), id, kind.code());
    if (asset == null)
      throw new IllegalArgumentException(
          kind == BrandingAssetKind.LOGO ? "请选择有效的Logo" : "请选择有效的登录背景");
    String currentId =
        kind == BrandingAssetKind.LOGO ? current.getLogoFileId() : current.getBackgroundFileId();
    if (asset.getCreatedBy() != auth.userId() && !id.equals(currentId))
      throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.system.message0008");
    return id;
  }

  /** 配置、图片引用与审计原子提交；统一先锁租户配置再锁图片，避免取消上传与保存交错产生悬空引用。 审计序列化属于本事务，受检异常也必须回滚；首次初始化失败不能留下配置或审计残片。 */
  @Transactional(rollbackFor = Exception.class)
  public PersonalizationView save(PersonalizationForm form) throws JsonProcessingException {
    access();
    String name = form.systemName().trim();
    if (name.isEmpty()) throw new LocalizedArgumentException("ui.equipment.text0927");
    if (!BACKGROUND_POSITIONS.contains(form.backgroundPosition()))
      throw new LocalizedArgumentException("server.system.message0009");
    long tenant = auth.tenantId();
    boolean created = configuration.ensureConfiguration(tenant);
    var current = configuration.lock(tenant);
    if (!Objects.equals(current.getRowVersion(), form.rowVersion()))
      throw new LocalizedStatusException(HttpStatus.CONFLICT, "server.system.message0010");
    // 初次保存前仍是未持久化的默认展示，历史快照的updatedAt保留原空字符串语义。
    var before = view(tenant, created ? defaults(tenant) : current);
    String logo = selectedAsset(form.logoFileId(), BrandingAssetKind.LOGO, current);
    String background =
        selectedAsset(form.backgroundFileId(), BrandingAssetKind.BACKGROUND, current);
    current.setSystemName(name);
    current.setLogoFileId(logo);
    current.setBackgroundFileId(background);
    current.setBackgroundPosition(form.backgroundPosition());
    current.setUpdatedBy(auth.userId());
    if (configuration.update(current, form.rowVersion()) != 1)
      throw new LocalizedStatusException(HttpStatus.CONFLICT, "server.system.message0010");
    var after = view(tenant, configuration.find(tenant));
    var event = new BrandingEvent();
    event.setTenantId(tenant);
    event.setUserId(auth.userId());
    event.setBeforeJson(json.writeValueAsString(before));
    event.setAfterJson(json.writeValueAsString(after));
    events.insert(event);
    return after;
  }

  /** 在写入数据库前验证真实图片编码、体积和像素数，拒绝仅凭上传扩展名判断图片格式。 */
  public BrandingAssetView upload(String code, BrandingUpload file) {
    access();
    var kind = BrandingAssetKind.from(code);
    int limit = kind.limitMiB();
    if (file.empty() || file.size() > limit * 1024L * 1024)
      throw new LocalizedArgumentException("server.system.message0012", limit);
    byte[] bytes = file.bytes();
    String type;
    try (var stream = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(stream);
      if (!readers.hasNext()) throw new LocalizedArgumentException("ui.equipment.text0925");
      var reader = readers.next();
      try {
        reader.setInput(stream);
        String format = reader.getFormatName().toLowerCase(Locale.ROOT);
        if (!Set.of("png", "jpeg", "jpg").contains(format))
          throw new LocalizedArgumentException("ui.equipment.text0925");
        if ((long) reader.getWidth(0) * reader.getHeight(0) > MAX_IMAGE_PIXELS)
          throw new LocalizedArgumentException("server.system.message0013");
        if (reader.read(0) == null)
          throw new LocalizedArgumentException("server.printing.message0015");
        type = format.equals("png") ? "image/png" : "image/jpeg";
      } finally {
        reader.dispose();
      }
    } catch (IOException exception) {
      throw new LocalizedArgumentException("server.system.message0014");
    }
    String id = UUID.randomUUID().toString();
    var asset = new BrandingAsset();
    asset.setId(id);
    asset.setTenantId(auth.tenantId());
    asset.setKind(kind.code());
    asset.setContentType(type);
    asset.setImageData(bytes);
    asset.setCreatedBy(auth.userId());
    assets.insert(asset);
    return new BrandingAssetView(id, "/api/v1/personalization/assets/" + id);
  }

  /** 先锁配置再删除图片，与保存保持相同锁顺序；当前发布及历史审计中的图片均不得当作临时文件取消。 */
  @Transactional
  public DeletionResult discardUpload(String id) {
    access();
    long tenant = auth.tenantId();
    var current = configuration.lock(tenant);
    if (referenced(current, id)) return new DeletionResult(false);
    return new DeletionResult(assets.discardUnreferenced(tenant, auth.userId(), id) == 1);
  }

  private boolean referenced(TenantBranding config, String id) {
    return config != null
        && (id.equals(config.getLogoFileId()) || id.equals(config.getBackgroundFileId()));
  }

  /** 匿名访问仅限当前发布图片；管理预览只能读取本人上传或本租户当前使用的图片。 */
  private BrandingImage image(long tenant, String id, boolean published) {
    if (!id.matches("[0-9a-f-]{36}"))
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.system.message0015");
    if (published && !referenced(row(tenant), id))
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.system.message0015");
    var asset = assets.find(tenant, id);
    if (asset == null)
      throw new LocalizedStatusException(HttpStatus.NOT_FOUND, "server.system.message0015");
    if (!published && asset.getCreatedBy() != auth.userId() && !referenced(row(tenant), id))
      throw new LocalizedStatusException(HttpStatus.FORBIDDEN, "server.system.message0016");
    return new BrandingImage(asset.getImageData(), asset.getContentType());
  }

  public BrandingImage privateAsset(String id) {
    access();
    return image(auth.tenantId(), id, false);
  }

  public BrandingImage publicAsset(long tenantId, String id) {
    return image(tenantId, id, true);
  }
}
