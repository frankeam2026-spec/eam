package com.windasoft.eam.entity.maintenance;

import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.Data;

/** 保养任务主体；snapshot仅保存生成时的历史内容，人员与执行结果分别以关系表为权威。 */
@Data
@TableName("eam_maintenance_task")
public class MaintenanceTask {
  @TableId private Long id;
  private Long tenantId;
  private String code;
  private Long planId;
  private Long scheduleId;
  private Long equipmentId;
  private Long typeId;
  private Long companyId;
  private Long departmentId;
  private LocalDate dueDate;
  private LocalDate expiresDate;
  private Long ownerId;
  private String status;
  private String snapshot;
  private Integer rowVersion;
  private LocalDateTime createdAt;
  private LocalDateTime updatedAt;

  /** 普通CRUD沿用全局逻辑删除；包含已删除记录的历史锁范围由专门Mapper入口保留。 */
  private Boolean deleted;

  private LocalDateTime deletedAt;
  private Long deletedBy;
  private LocalDateTime submittedAt;
}
