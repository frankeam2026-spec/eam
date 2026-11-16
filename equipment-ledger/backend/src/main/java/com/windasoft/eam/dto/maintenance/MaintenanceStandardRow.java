package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** 行的公共详情字段平铺到原JSON，计数和显示标签是列表专用投影。 */
public record MaintenanceStandardRow(
    @JsonUnwrapped MaintenanceStandardView detail,
    String department,
    @JsonInclude(JsonInclude.Include.NON_NULL) String owner,
    long equipmentCount,
    long itemCount,
    String enabledLabel) {}
