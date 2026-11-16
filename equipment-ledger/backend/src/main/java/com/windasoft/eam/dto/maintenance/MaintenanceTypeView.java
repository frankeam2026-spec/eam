package com.windasoft.eam.dto.maintenance;

import java.util.List;

/** 类型不包含项目/设备/排程；空数组保留原统一编辑器协议，不承载隐藏的动态业务内容。 */
public record MaintenanceTypeView(
    String id,
    String code,
    String name,
    String departmentId,
    String creatorId,
    Integer rowVersion,
    String createdAt,
    Integer period,
    String unit,
    String symbol,
    boolean system,
    boolean enabled,
    String remark,
    List<String> items,
    List<String> equipmentIds,
    List<String> categoryIds,
    List<String> categoryNames,
    List<String> legacyCategoryNames,
    List<String> owners,
    List<String> reviewers,
    List<String> details) {}
