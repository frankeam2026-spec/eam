package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgeDocument;
import com.windasoft.eam.entity.knowledge.KnowledgeVersion;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeEquipmentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgePageRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

/** 文档管理编排：文件在事务外写入，元数据及关联在事务内保存，失败只清理本次新文件。 */
@Service
public class KnowledgeDocumentService {
  public static final int MAX_PDF_BYTES = 50 * 1024 * 1024;
  private final KnowledgeDocumentRepository documents;
  private final KnowledgeVersionRepository versions;
  private final KnowledgeEquipmentRepository equipment;
  private final KnowledgePageRepository pages;
  private final OrgUnitRepository organizations;
  private final EquipmentQueryService equipmentQueries;
  private final KnowledgePdfParser parser;
  private final KnowledgeContentWriter writer;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate transaction;
  private final Path root;

  public KnowledgeDocumentService(
      KnowledgeDocumentRepository documents,
      KnowledgeVersionRepository versions,
      KnowledgeEquipmentRepository equipment,
      KnowledgePageRepository pages,
      OrgUnitRepository organizations,
      EquipmentQueryService equipmentQueries,
      KnowledgePdfParser parser,
      KnowledgeContentWriter writer,
      AuthContext auth,
      PermissionService permissions,
      TransactionTemplate transaction,
      @Value("${eam.upload.directory:./data/uploads}") String directory) {
    this.documents = documents;
    this.versions = versions;
    this.equipment = equipment;
    this.pages = pages;
    this.organizations = organizations;
    this.equipmentQueries = equipmentQueries;
    this.parser = parser;
    this.writer = writer;
    this.auth = auth;
    this.permissions = permissions;
    this.transaction = transaction;
    this.root = Path.of(directory, "knowledge").toAbsolutePath().normalize();
  }

  public KnowledgeViews.Documents list(String q, int page, int size) {
    return documents.list(auth.tenantId(), permissions.scope("knowledge", "查看"), q, page, size);
  }

  private KnowledgeDocument document(long id, String action, String missing) {
    var d = documents.document(auth.tenantId(), id);
    if (d == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, missing);
    company(d.getCompanyId(), action);
    return d;
  }

  private void company(long id, String action) {
    var scope = permissions.scope("knowledge", action);
    if (!scope.all() && !scope.companyIds().contains(id))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无权访问该公司知识资料");
    var org = organizations.company(auth.tenantId(), id);
    if (org == null || org.getStatus() != 1) throw new IllegalArgumentException("所属公司不存在或已停用");
  }

  public KnowledgeViews.Versions versions(long id, int page, int size) {
    document(id, "编辑", "资料不存在");
    return versions.list(auth.tenantId(), id, page, size);
  }

  /** 文档版本推进与失败解析重置同一事务，状态不符时必须一并回滚，不能吞异常。 */
  public KnowledgeViews.RowVersion retryParse(long id, long expected) {
    return transaction.execute(
        s -> {
          document(id, "编辑", "资料不存在");
          if (!documents.advance(auth.tenantId(), id, expected))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "资料已变更，请刷新");
          var v = versions.latest(auth.tenantId(), id);
          if (v == null) throw new EmptyResultDataAccessException(1);
          if (!versions.retryParse(auth.tenantId(), v.getId(), auth.userId()))
            throw new IllegalArgumentException("仅失败的解析任务可以重试");
          return new KnowledgeViews.RowVersion(expected + 1);
        });
  }

  public DataResponse<List<KnowledgeViews.Company>> companies() {
    return new DataResponse<>(
        organizations
            .knowledgeCompanies(auth.tenantId(), permissions.scope("knowledge", "编辑"))
            .stream()
            .map(o -> new KnowledgeViews.Company(o.getId().toString(), o.getName()))
            .toList());
  }

  public KnowledgeViews.Applicability applicability(long id) {
    var d = document(id, "编辑", "资料不存在");
    return new KnowledgeViews.Applicability(
        Boolean.TRUE.equals(d.getAppliesToAll()),
        equipment.list(auth.tenantId(), id).stream()
            .map(e -> new KnowledgeViews.Equipment(e.getEquipmentId().toString()))
            .toList(),
        d.getRowVersion());
  }

  /** 先复核各设备原有台账授权及公司，主体条件更新成功后替换关联，保持通用/指定范围互斥。 */
  public KnowledgeViews.RowVersion bind(long id, long expected, boolean all, List<Long> selected) {
    if (selected == null
        || selected.size() > 200
        || new HashSet<>(selected).size() != selected.size()
        || (!all && selected.isEmpty())
        || (all && !selected.isEmpty())) throw new IllegalArgumentException("请选择通用资料或指定适用设备");
    return transaction.execute(
        s -> {
          var d = document(id, "编辑", "文档不存在");
          for (long eq : selected) {
            var device = equipmentQueries.get(eq);
            if (!Objects.equals(d.getCompanyId().toString(), device.companyOrgId()))
              throw new IllegalArgumentException("适用设备必须属于资料所属公司");
          }
          if (!documents.bind(auth.tenantId(), id, expected, all))
            throw new IllegalStateException("文档已变更，请刷新");
          equipment.replace(auth.tenantId(), id, selected);
          return new KnowledgeViews.RowVersion(expected + 1);
        });
  }

  public Path original(long id, long version) {
    document(id, "查看", "资料不存在");
    String key = versions.publishedFile(auth.tenantId(), id, version);
    if (key == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "资料版本已停用或替换");
    if (!key.matches("[0-9a-f-]{36}\\.pdf")) throw new IllegalStateException("资料文件标识无效");
    var path = root.resolve(Long.toString(auth.tenantId())).resolve(key);
    if (!Files.isRegularFile(path))
      throw new ResponseStatusException(HttpStatus.NOT_FOUND, "原文文件不存在");
    return path;
  }

  public byte[] pageImage(long id, long version, int page) throws Exception {
    try (var pdf = Loader.loadPDF(original(id, version).toFile())) {
      if (page < 1 || page > pdf.getNumberOfPages()) throw new IllegalArgumentException("原文页码无效");
      var box = pdf.getPage(page - 1).getCropBox();
      if (box.getWidth() <= 0
          || box.getHeight() <= 0
          || box.getWidth() * box.getHeight() * 4 > 20_000_000)
        throw new IllegalArgumentException("原文页面尺寸过大");
      var out = new ByteArrayOutputStream();
      ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(page - 1, 144), "png", out);
      return out.toByteArray();
    }
  }

  public DataResponse<List<KnowledgeViews.Page>> pages(long id) {
    var d = document(id, "查看", "知识文档不存在");
    company(d.getCompanyId(), "编辑");
    return new DataResponse<>(pages.pages(auth.tenantId(), id));
  }

  private void pdf(byte[] bytes) {
    if (bytes == null
        || bytes.length < 5
        || bytes.length > MAX_PDF_BYTES
        || !new String(bytes, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
      throw new IllegalArgumentException("请上传有效PDF，最大50MB");
  }

  public DataResponse<KnowledgeViews.Uploaded> upload(long company, String title, byte[] bytes)
      throws Exception {
    return upload(company, title, bytes, "MANUAL");
  }

  public DataResponse<KnowledgeViews.Uploaded> upload(
      long company, String title, byte[] bytes, String type) throws Exception {
    if (!Set.of("MANUAL", "SOP").contains(type)) throw new IllegalArgumentException("资料类型无效");
    company(company, "编辑");
    title = title == null ? "" : title.strip();
    if (title.isBlank() || title.length() > 200)
      throw new IllegalArgumentException("文档名称须为1至200个字符");
    pdf(bytes);
    long id = IdWorker.getId(), version = IdWorker.getId();
    String key = UUID.randomUUID() + ".pdf",
        digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var directory = root.resolve(Long.toString(auth.tenantId()));
    Files.createDirectories(directory);
    var target = directory.resolve(key);
    Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
    String name = title;
    try {
      transaction.executeWithoutResult(
          s -> {
            company(company, "编辑");
            var d = new KnowledgeDocument();
            d.setId(id);
            d.setTenantId(auth.tenantId());
            d.setCompanyId(company);
            d.setTitle(name);
            d.setDocumentType(type);
            d.setCreatedBy(auth.userId());
            documents.insert(d);
            var v = new KnowledgeVersion();
            v.setId(version);
            v.setTenantId(auth.tenantId());
            v.setDocumentId(id);
            v.setVersionNo(1);
            v.setFileKey(key);
            v.setSha256(digest);
            v.setParserVersion(KnowledgePdfParser.VERSION);
            v.setParseStatus("PENDING");
            v.setCreatedBy(auth.userId());
            versions.insert(v);
          });
    } catch (Exception error) {
      try {
        Files.deleteIfExists(target);
      } catch (Exception cleanup) {
        error.addSuppressed(cleanup);
      }
      throw error;
    }
    return new DataResponse<>(
        new KnowledgeViews.Uploaded(Long.toString(id), Long.toString(version), "PENDING"));
  }

  /** 持有文档锁再分配内容版本；新文件失败补偿不删除旧版本或已发布文件。 */
  public KnowledgeViews.Replaced replace(long id, long expected, byte[] bytes) throws Exception {
    var doc = document(id, "编辑", "文档不存在");
    pdf(bytes);
    long version = IdWorker.getId();
    String key = UUID.randomUUID() + ".pdf",
        digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    var directory = root.resolve(Long.toString(auth.tenantId()));
    Files.createDirectories(directory);
    var file = directory.resolve(key);
    Files.write(file, bytes, StandardOpenOption.CREATE_NEW);
    try {
      transaction.executeWithoutResult(
          s -> {
            company(doc.getCompanyId(), "编辑");
            var locked = documents.document(auth.tenantId(), id, true);
            if (locked == null || locked.getRowVersion() != expected)
              throw new IllegalStateException("文档已变更，请刷新");
            var v = new KnowledgeVersion();
            v.setId(version);
            v.setTenantId(auth.tenantId());
            v.setDocumentId(id);
            v.setVersionNo(versions.nextNumber(auth.tenantId(), id));
            v.setFileKey(key);
            v.setSha256(digest);
            v.setParserVersion(KnowledgePdfParser.VERSION);
            v.setParseStatus("PENDING");
            v.setCreatedBy(auth.userId());
            versions.insert(v);
            if (!documents.advance(auth.tenantId(), id, expected))
              throw new IllegalStateException("文档已变更，请刷新");
          });
    } catch (Exception error) {
      try {
        Files.deleteIfExists(file);
      } catch (Exception cleanup) {
        error.addSuppressed(cleanup);
      }
      throw error;
    }
    return new KnowledgeViews.Replaced(Long.toString(id), Long.toString(version), expected + 1);
  }

  public KnowledgeViews.StatusVersion disable(long id, long expected) {
    document(id, "停用", "文档不存在");
    if (!documents.disable(auth.tenantId(), id, expected))
      throw new ResponseStatusException(HttpStatus.CONFLICT, "文档已变更，请刷新");
    return new KnowledgeViews.StatusVersion("DISABLED", expected + 1);
  }

  /** 后台解析仅在发起人的租户/公司授权下执行；解析在事务外，提交仍由内容写入器校验租约。 */
  public void parseVersion(KnowledgeVersion row, String token) throws Exception {
    long tenant = row.getTenantId(), version = row.getId(), id = row.getDocumentId();
    if (auth.tenantId() != tenant) throw new IllegalStateException("解析任务租户不符");
    var d = document(id, "编辑", "文档不存在");
    String key = row.getFileKey();
    if (!key.matches("[0-9a-f-]{36}\\.pdf")) throw new IllegalArgumentException("文档文件标识无效");
    var file = root.resolve(Long.toString(tenant)).resolve(key);
    if (Files.size(file) > MAX_PDF_BYTES) throw new IllegalArgumentException("文档大小超限");
    var parsed =
        parser.parse(Files.readAllBytes(file), () -> versions.heartbeat(tenant, version, token));
    company(d.getCompanyId(), "编辑");
    writer.commitAttempt(tenant, version, parsed, token);
  }
}
