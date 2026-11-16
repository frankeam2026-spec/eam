package com.windasoft.eam.dto.system;

/** 活跃用户选择投影，不返回账号安全信息；部门名按历史选择器规则保留。 */
public record UserNameOption(String id, String name, String account, String department) {}
