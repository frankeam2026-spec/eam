package com.windasoft.eam.dto.repair;

import com.windasoft.eam.entity.repair.RepairOrder;

/** 内部业务读取上下文，不作为HTTP响应，供知识案例与诊断使用真实工单字段。 */
public record RepairContext(RepairOrder order, String model, boolean canProcess) {}
