package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.windasoft.eam.common.ExactLongDeserializer;
import com.windasoft.eam.common.RowVersionDeserializer;

/** 批量操作必须逐任务校验版本，不能只接受ID集合。 */
public record MaintenanceTaskTarget(
    @JsonDeserialize(using = ExactLongDeserializer.class) Long id,
    @JsonDeserialize(using = RowVersionDeserializer.class) Integer rowVersion) {}
