package com.windasoft.eam.dto.inventory;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.entity.inventory.RequisitionHeader;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;

/** Fixed detail contract. Financial fields are explicitly omitted when access is denied. */
public record RequisitionDetail(
    @JsonUnwrapped Header header,
    String warehouseName,
    List<Line> lines,
    List<Reservation> reservations,
    List<Issue> issues,
    List<Event> events,
    List<WorkflowHistoryRound> workflow) {
  public RequisitionDetail {
    lines = List.copyOf(lines);
    reservations = List.copyOf(reservations);
    issues = List.copyOf(issues);
    events = List.copyOf(events);
    workflow = List.copyOf(workflow);
  }

  @Data
  public static class Header {
    @InventoryId private Long id;
    @InventoryId private Long tenantId;
    private String code;
    @InventoryId private Long companyId;
    @InventoryId private Long warehouseId;
    private String taskKind;
    @InventoryId private Long taskId;
    private String taskCode;
    @InventoryId private Long applicantId;
    private String applicantName;
    private String remark;
    private RequisitionStatus status;
    private Integer rowVersion;
    private LocalDateTime createdAt;
    private Integer deleted;
    private String approvalGate;

    public static Header from(RequisitionHeader source) {
      var view = new Header();
      view.setId(source.getId());
      view.setTenantId(source.getTenantId());
      view.setCode(source.getCode());
      view.setCompanyId(source.getCompanyId());
      view.setWarehouseId(source.getWarehouseId());
      view.setTaskKind(source.getTaskKind());
      view.setTaskId(source.getTaskId());
      view.setTaskCode(source.getTaskCode());
      view.setApplicantId(source.getApplicantId());
      view.setApplicantName(source.getApplicantName());
      view.setRemark(source.getRemark());
      view.setStatus(source.getStatus());
      view.setRowVersion(source.getRowVersion());
      view.setCreatedAt(source.getCreatedAt());
      view.setDeleted(source.getDeleted());
      view.setApprovalGate(source.getApprovalGate());
      return view;
    }
  }

  @Data
  public static class Line {
    @InventoryId private Long id;
    @InventoryId private Long tenantId;
    @InventoryId private Long requisitionId;
    private Integer lineNo;
    @InventoryId private Long materialId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal quantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal issuedQuantity;

    private String materialCode;
    private String materialName;
    private String materialModel;
    private String unit;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal reservedQuantity;
  }

  @Data
  public static class Reservation {
    @InventoryId private Long id;
    @InventoryId private Long tenantId;
    @InventoryId private Long requisitionId;
    @InventoryId private Long requisitionLineId;
    @InventoryId private Long balanceId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal quantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal remainingQuantity;

    private LocalDateTime createdAt;
    private String materialName;
    private String binName;
    private String batchCode;
  }

  @Data
  public static class Issue {
    @InventoryId private Long id;
    @InventoryId private Long tenantId;
    private String code;
    @InventoryId private Long requisitionId;
    @InventoryId private Long companyId;
    @InventoryId private Long warehouseId;
    private String confirmationKey;
    private String previewFingerprint;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal totalAmount;

    @InventoryId private Long actorId;
    private String actorName;
    private LocalDateTime createdAt;
    private Integer reversed;
    @InventoryId private Long groupId;
    private String status;
    private String sourceKind;
    private Integer rowVersion;
    private Integer deleted;
    private LocalDateTime confirmedAt;
    private String purpose;
    private String remark;
    @InventoryId private Long departmentId;
    @InventoryId private Long recipientId;
    private String recipientName;
    private String taskKind;
    @InventoryId private Long taskId;
    private String legacyFingerprint;
    private String groupCode;
    private List<Allocation> allocations = List.of();
  }

  @Data
  public static class Allocation {
    @InventoryId private Long id;
    @InventoryId private Long tenantId;
    @InventoryId private Long issueId;
    @InventoryId private Long requisitionLineId;
    @InventoryId private Long reservationId;
    @InventoryId private Long balanceId;
    @InventoryId private Long costLayerId;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal quantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal amount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal unitCost;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal returnedQuantity;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private BigDecimal returnedAmount;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    private BigDecimal usedQuantity;

    @InventoryId private Long outboundLineId;
    private String materialName;
    private String binName;
    private String batchCode;
  }

  @Data
  public static class Event {
    private String action;
    private String actorName;
    private LocalDateTime createdAt;
    private String reason;
  }
}
