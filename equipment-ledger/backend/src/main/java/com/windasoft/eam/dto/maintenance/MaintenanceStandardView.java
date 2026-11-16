package com.windasoft.eam.dto.maintenance;

import java.util.List;

/** 标准详情/兼容列表的固定字段，不把模板数据塞进通用payload。 */
public record MaintenanceStandardView(
    String id,
    String code,
    String name,
    String departmentId,
    String creatorId,
    Integer rowVersion,
    String createdAt,
    boolean enabled,
    String remark,
    List<MaintenanceStandardItemView> items,
    List<String> equipmentIds,
    List<String> categoryIds,
    List<String> categoryNames,
    List<String> legacyCategoryNames,
    List<String> owners,
    List<String> reviewers,
    List<String> details) {}
