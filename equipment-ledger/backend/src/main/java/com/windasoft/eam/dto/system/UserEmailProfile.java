package com.windasoft.eam.dto.system;

/** 账号邮箱及其独立并发版本，不包含账号密码、会话或其他权限字段。 */
public record UserEmailProfile(long id, String email, int version) {}
