package com.windasoft.eam.dto.inspection;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;

/** 点检执行入口的设备候选，只提供编号与名称；BIGINT主键保持原JSON字符串契约。 */
public record InspectionDeviceOption(
    @JsonSerialize(using = ToStringSerializer.class) Long id, String name, String code) {}
