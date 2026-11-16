package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.windasoft.eam.dto.system.PlanReminderBinding;

/** 详情组合固定计划、受Schema约束的扩展字段及公共提醒绑定；不新增嵌套HTTP层级。 */
public record MaintenancePlanDetail(
    @JsonUnwrapped MaintenancePlanView definition,
    @JsonUnwrapped MaintenanceCustomFields custom,
    @JsonUnwrapped PlanReminderBinding reminder) {}
