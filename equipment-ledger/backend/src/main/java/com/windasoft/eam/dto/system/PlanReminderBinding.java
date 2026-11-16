package com.windasoft.eam.dto.system;

import java.time.LocalDateTime;

/** 已有计划提醒协议；生效时间沿用DATETIME的当地时间语义，不擅自推断或迁移时区。 */
public record PlanReminderBinding(
    String reminderMode,
    String reminderPolicyId,
    LocalDateTime reminderEffectiveAt,
    String reminderPolicyName) {}
