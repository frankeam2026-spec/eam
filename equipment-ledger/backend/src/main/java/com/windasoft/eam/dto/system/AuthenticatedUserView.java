package com.windasoft.eam.dto.system;

/** 登录与当前用户复用相同投影，绝不包含密码哈希或会话令牌。 */
public record AuthenticatedUserView(
    String id,
    String tenantId,
    String account,
    String name,
    String orgId,
    String company,
    String department) {}
