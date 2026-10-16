package com.windasoft.eam.dto.system;

import jakarta.validation.constraints.*;

public record PersonalizationForm(
    @NotBlank(message = "请输入系统名称") @Size(max = 30, message = "系统名称最多30字") String systemName,
    String logoFileId,
    String backgroundFileId,
    @NotBlank(message = "请选择背景显示位置") String backgroundPosition,
    @NotNull(message = "缺少配置版本，请刷新后重试") @Min(0) Integer rowVersion) {}
