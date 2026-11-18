package com.windasoft.eam.dto.system;

/** 管理视图沿用扁平字段及空字符串语义，updatedAt保留原LocalDateTime文本格式。 */
public record PersonalizationView(
    String systemName,
    String logoUrl,
    String backgroundUrl,
    String backgroundPosition,
    String logoFileId,
    String backgroundFileId,
    Integer rowVersion,
    String updatedAt) {}
