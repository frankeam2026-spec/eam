package com.windasoft.eam.service.knowledge;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.knowledge.KnowledgeViews;
import com.windasoft.eam.entity.knowledge.KnowledgeCaseSource;
import com.windasoft.eam.entity.knowledge.KnowledgeDocument;
import com.windasoft.eam.entity.knowledge.KnowledgeVersion;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.knowledge.KnowledgeCaseSourceRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeEquipmentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.service.repair.RepairDetailService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class KnowledgeRepairCaseService {
  private final RepairDetailService repair;
  private final KnowledgeDocumentRepository docs;
  private final KnowledgeVersionRepository versions;
  private final KnowledgeEquipmentRepository equipment;
  private final KnowledgeCaseSourceRepository sources;
  private final KnowledgeContentWriter writer;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final TransactionTemplate tx;

  public KnowledgeRepairCaseService(
      RepairDetailService repair,
      KnowledgeDocumentRepository docs,
      KnowledgeVersionRepository versions,
      KnowledgeEquipmentRepository equipment,
      KnowledgeCaseSourceRepository sources,
      KnowledgeContentWriter writer,
      AuthContext auth,
      PermissionService permissions,
      TransactionTemplate tx) {
    this.repair = repair;
    this.docs = docs;
    this.versions = versions;
    this.equipment = equipment;
    this.sources = sources;
    this.writer = writer;
    this.auth = auth;
    this.permissions = permissions;
    this.tx = tx;
  }

  private RepairOrder order(long id) {
    var result = repair.context(id);
    var order = result.order();
    if (!"已完成".equals(order.getStatus())
        || order.getCompanyOrgId() == null
        || order.getEquipmentId() == null)
      throw new IllegalArgumentException("仅可从所属公司和设备明确的已验收工单建立案例");
    var scope = permissions.scope("knowledge", "编辑");
    if (!scope.all() && !scope.companyIds().contains(order.getCompanyOrgId()))
      throw new IllegalArgumentException("无权维护此公司知识库");
    return order;
  }

  public KnowledgeViews.CaseDraft draft(long id) {
    var order = order(id);
    String text =
        "设备："
            + order.getEquipmentCode()
            + " "
            + order.getEquipmentName()
            + "\n故障现象："
            + Objects.toString(order.getDescription(), "")
            + "\n确认原因："
            + Objects.toString(order.getFaultCause(), "未填写，请核对")
            + "\n实际处理："
            + Objects.toString(order.getRepairAction(), "未填写，请核对");
    return new KnowledgeViews.CaseDraft(
        text, order.getRowVersion(), order.getEquipmentName() + "维修案例");
  }

  /** 案例内容与工单来源、适用设备一起提交。没有原始 PDF，仍使用内容版本锁校验解析提交。 */
  public KnowledgeViews.CaseCreated create(
      long repairId, long repairVersion, String title, String text) {
    if (title == null
        || title.isBlank()
        || title.length() > 200
        || text == null
        || text.isBlank()
        || text.length() > 20000) throw new IllegalArgumentException("案例标题或内容无效");
    final String digest;
    try {
      digest =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(text.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException impossible) {
      throw new IllegalStateException(impossible);
    }
    return tx.execute(
        s -> {
          var order = order(repairId);
          if (order.getRowVersion() != repairVersion)
            throw new IllegalArgumentException("工单已修改，请重新生成案例草稿");
          long id = IdWorker.getId(), version = IdWorker.getId(), tenant = auth.tenantId();
          var document = new KnowledgeDocument();
          document.setId(id);
          document.setTenantId(tenant);
          document.setCompanyId(order.getCompanyOrgId());
          document.setTitle(title);
          document.setDocumentType("CASE");
          document.setCreatedBy(auth.userId());
          docs.insert(document);
          var content = new KnowledgeVersion();
          content.setId(version);
          content.setTenantId(tenant);
          content.setDocumentId(id);
          content.setVersionNo(1);
          content.setFileKey("case-" + UUID.randomUUID());
          content.setSha256(digest);
          content.setParserVersion("confirmed-case-v1");
          content.setParseStatus("RUNNING");
          content.setHasOriginal(false);
          versions.insert(content);
          var source = new KnowledgeCaseSource();
          source.setTenantId(tenant);
          source.setDocumentId(id);
          source.setRepairOrderId(repairId);
          source.setRepairRowVersion(repairVersion);
          sources.insert(source);
          writer.commit(
              tenant,
              version,
              List.of(new KnowledgePdfParser.Page(1, text, false, KnowledgePdfParser.split(text))));
          if (!docs.bind(tenant, id, 0, false)) throw new IllegalStateException("文档已变更，请刷新");
          equipment.replace(tenant, id, List.of(order.getEquipmentId()));
          return new KnowledgeViews.CaseCreated(Long.toString(id), 1);
        });
  }
}
