package com.windasoft.eam.dto.basedata;

import jakarta.validation.constraints.*;

public record BaseItemForm(
    @Size(max = 40, message = "{server.common.message0001}") String code,
    @NotBlank(message = "{server.common.message0002}") String name,
    Long parentId,
    Integer precision,
    Integer status,
    Integer sortNo,
    Long companyId) {}
