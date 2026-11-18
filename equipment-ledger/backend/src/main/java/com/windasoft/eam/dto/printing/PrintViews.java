package com.windasoft.eam.dto.printing;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public final class PrintViews {
  private PrintViews() {}

  public record Company(String id, String name) {}

  /** headers与columns为已声明字段目录，画布由打印校验器限制，固定元数据独立建模。 */
  public record Catalog(
      JsonNode defaults,
      Map<String, String> headers,
      Map<String, String> columns,
      List<Company> companies,
      boolean canManageCommon) {}

  public record Template(
      String id,
      String name,
      String companyId,
      String companyName,
      boolean enabled,
      boolean isDefault,
      int rowVersion,
      JsonNode config,
      String updatedAt,
      boolean canEdit,
      boolean canDelete) {}

  public record Choice(String id, String name, int rowVersion, boolean containsMoney) {}

  public record Options(List<Choice> templates, String defaultId) {}

  public record Preview(String pdf, String fingerprint, String code) {}

  public record Job(
      String id,
      String documentId,
      String documentCode,
      String documentStatus,
      String templateId,
      String templateName,
      String templateVersion,
      String actorName,
      LocalDateTime createdAt,
      String pageCount,
      String pdfSha256) {}

  public record Source(Long id, Long warehouseId, Long companyId, String companyName) {}

  public record Allocation(
      Long id,
      BigDecimal quantity,
      BigDecimal unitCost,
      BigDecimal amount,
      Long outboundLineId,
      String code,
      String name,
      String model,
      String unit,
      String batch,
      String bin,
      String recipient,
      String department,
      String task,
      String requisition,
      String currency) {}

  /** 仅在库存旧接口边界读取打印所需字段，不改变库存业务接口或金额权威。 */
  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Outbound(
      String code,
      String status,
      String warehouseName,
      String sourceKind,
      String confirmedAt,
      String createdAt,
      String recipientName,
      String departmentName,
      String taskCode,
      String purpose,
      String remark,
      String actorName,
      Integer rowVersion,
      List<Line> lines) {}

  @JsonIgnoreProperties(ignoreUnknown = true)
  public record Line(
      String materialCode,
      String materialName,
      String model,
      String unit,
      BigDecimal quantity,
      String recipientName,
      String departmentName,
      String taskCode,
      String requisitionCode) {}
}
