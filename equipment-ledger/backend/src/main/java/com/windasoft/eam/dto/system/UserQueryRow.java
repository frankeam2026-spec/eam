package com.windasoft.eam.dto.system;

/** 用户查询的固定数据库投影，不包含密码等认证字段，也不直接暴露为HTTP响应。 */
public record UserQueryRow(
    long id,
    String account,
    String name,
    String employeeNo,
    String phone,
    String email,
    int emailVersion,
    Long orgId,
    int status,
    String department,
    String roles) {}
