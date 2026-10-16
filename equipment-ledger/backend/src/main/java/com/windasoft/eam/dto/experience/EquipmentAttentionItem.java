package com.windasoft.eam.dto.experience;

/** Read-only, authorized task or anomaly shown on an equipment overview. */
public record EquipmentAttentionItem(
    String id,
    String kind,
    String code,
    String summary,
    String status,
    String owner,
    String createdAt,
    String deadline,
    boolean overdue,
    String source,
    String url) {}
