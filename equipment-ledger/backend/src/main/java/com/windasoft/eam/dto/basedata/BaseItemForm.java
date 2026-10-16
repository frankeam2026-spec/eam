package com.windasoft.eam.dto.basedata;

import jakarta.validation.constraints.*;

public record BaseItemForm(
    @Size(max = 40, message = "编码最多40个字符") String code,
    @NotBlank(message = "名称必填") String name,
    Long parentId,
    Integer precision,
    Integer status,
    Integer sortNo,
    Long companyId) {}
