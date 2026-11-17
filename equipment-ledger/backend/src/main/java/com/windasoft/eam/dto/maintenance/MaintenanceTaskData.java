package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 数据库关系投影；历史文档仅在snapshot边界保留，主体、人员及执行事实具有明确类型。 */
public record MaintenanceTaskData(
    long id,
    long tenantId,
    String code,
    Long planId,
    Long scheduleId,
    Long equipmentId,
    Long typeId,
    Long companyId,
    Long departmentId,
    Long ownerId,
    String status,
    int rowVersion,
    LocalDate dueDate,
    LocalDate expiresDate,
    LocalDateTime createdAt,
    ObjectNode snapshot,
    MaintenanceExecutionResult result,
    String department,
    String owner,
    boolean departmentSelected,
    boolean ownerSelected) {}
