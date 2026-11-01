package com.windasoft.eam.entity.repair;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_repair_order")
/** 维修工单持久化实体：包含审批、处理和验收状态；关联展示字段不应直接作为数据库更新依据。 */
public class RepairOrder {
  @TableId(type = IdType.ASSIGN_ID)
  private Long id;

  private Long tenantId;
  private String approvalGate;
  private LocalDateTime approvalReadyAt;
  private Long equipmentId;
  private Long companyOrgId;
  private Long faultTypeId;
  private Long faultCauseId;
  private Long processingResultId;
  private LocalDateTime acceptedAt;
  private Long acceptedOwnerId;
  private Integer acceptanceScore;
  private Long departmentOrgId;
  private Long reporterUserId;
  private Long assigneeUserId;
  private Long dispatcherUserId;
  private String faultCause;
  private String repairMethod;
  private String processingResult;
  private String preventiveMeasures;

  @TableField(exist = false)
  private String collaboratorsJson;

  @com.baomidou.mybatisplus.annotation.TableField(exist = false)
  private String processAttachmentsJson;

  private String outsourceVendor;
  private String orderNo;
  private String equipmentCode;
  private String equipmentName;
  private String department;
  private String faultType;
  private String severity;
  private String description;
  private String reporter;
  private String assignee;
  private String status;
  private String repairAction;
  private Integer downtimeMinutes;
  private LocalDateTime reportedAt;
  private LocalDateTime dispatchedAt;
  private LocalDateTime claimedAt;
  private LocalDateTime completedAt;
  private String acceptanceComment;

  @com.baomidou.mybatisplus.annotation.TableField(exist = false)
  private String attachmentsJson;

  @Version private Integer rowVersion;
  private Integer deleted;

  @TableField(exist = false)
  private java.util.Map<String, Object> customFields;

  @TableField(exist = false)
  private java.util.Map<String, String> customFieldLabels;

  @TableField(exist = false)
  private Integer customSchemaVersion;

  @TableField(exist = false)
  private String equipmentCategoryId;
}
