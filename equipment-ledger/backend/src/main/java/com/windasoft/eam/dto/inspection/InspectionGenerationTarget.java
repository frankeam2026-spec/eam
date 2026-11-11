package com.windasoft.eam.dto.inspection;

/** 调度器使用的内部只读投影，不作为HTTP响应；租户和计划ID来自数据库而非请求参数。 */
public record InspectionGenerationTarget(long tenantId, long planId) {}
