package com.windasoft.eam.dto.workflow;

/** 生效绑定及执行版本的只读联查结果；缺失发布版本保持null，不自动放行。 */
public record WorkflowEffectiveBinding(
    Long versionId,
    Long templateId,
    String engineDefinitionId,
    Integer executionVersion,
    int enabled) {}
