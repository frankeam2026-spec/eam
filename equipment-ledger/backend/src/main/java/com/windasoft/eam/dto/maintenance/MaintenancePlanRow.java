package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonUnwrapped;

/** 计划列表沿用扁平字段；扩展字段只来自已授权的Schema值批量读取。 */
public record MaintenancePlanRow(
    @JsonUnwrapped MaintenancePlanView definition,
    String department,
    @JsonInclude(JsonInclude.Include.NON_NULL) String owner,
    long equipmentCount,
    long itemCount,
    String enabledLabel,
    @JsonUnwrapped MaintenanceCustomFields custom) {
  public MaintenancePlanRow withCustom(MaintenanceCustomFields value) {
    return new MaintenancePlanRow(
        definition, department, owner, equipmentCount, itemCount, enabledLabel, value);
  }
}
