package com.windasoft.eam.repair;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import java.time.LocalDateTime;
import lombok.Data;

@Data
@TableName("eam_repair_order")
public class RepairOrder {
    @TableId(type = IdType.ASSIGN_ID) private Long id;
    private Long tenantId;
    private Long departmentOrgId;
    private Long reporterUserId;
    private Long assigneeUserId;
    private Long dispatcherUserId;
    private String faultCause;
    private String repairMethod;
    private String processingResult;
    private String preventiveMeasures;
    private String collaboratorsJson;
    private String processAttachmentsJson;
    private String outsourceVendor;
    private String orderNo;
    private String equipmentCode;
    private String equipmentName;
    private String department;
    private LocalDateTime faultAt;
    private String faultType;
    private String severity;
    private String description;
    private String reporter;
    private String assignee;
    private String status;
    private String repairAction;
    private Integer downtimeMinutes;
    private LocalDateTime reportedAt;
    private LocalDateTime completedAt;
    private String acceptanceComment;
    private String attachmentsJson;
    @Version private Integer rowVersion;
    private Integer deleted;
}
