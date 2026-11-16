package com.windasoft.eam.dto.maintenance;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import com.windasoft.eam.common.ExactLongDeserializer;

/** 项目选择引用只保存所属标准ID及标准内项目键，不依靠显示名称定位。 */
public record MaintenanceProjectReference(
    @JsonDeserialize(using = ExactLongDeserializer.class)
        @JsonSerialize(using = ToStringSerializer.class)
        Long standardId,
    String itemId) {}
