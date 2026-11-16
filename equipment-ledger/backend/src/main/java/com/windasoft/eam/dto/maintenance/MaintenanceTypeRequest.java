package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;

/** 旧编辑器同时回传展示字段，按既有契约忽略；system和审计字段不能由客户端决定。 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record MaintenanceTypeRequest(
    String code,
    String name,
    String departmentId,
    @JsonDeserialize(using = ExactLongDeserializer.class) Long period,
    String unit,
    String symbol,
    Boolean enabled,
    String remark,
    @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion) {}
