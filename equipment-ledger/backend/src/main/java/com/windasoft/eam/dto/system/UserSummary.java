package com.windasoft.eam.dto.system;

import java.util.Map;

/** 汇总结构固定；roleCounts仅以动态角色ID为键、计数为值，保留前端既有字典协议。 */
public record UserSummary(Map<String, Long> roleCounts) {}
