package com.windasoft.eam.dto.maintenance;

import java.util.List;

/** 标准项目的固定展示协议；上下限沿用十进制字符串，备件数量沿用数值。 */
public record MaintenanceStandardItemView(
    String id,
    String code,
    String module,
    String name,
    String content,
    String typeId,
    String valueType,
    String lower,
    String upper,
    boolean required,
    boolean photoRequired,
    String remark,
    String spares,
    List<String> options,
    List<String> normalOptions,
    List<MaintenanceFileReference> references,
    List<MaintenanceSpareRequirement> spareRequirements) {}
