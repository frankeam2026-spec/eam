package com.windasoft.eam.dto.system;

import java.util.List;

public record UserListView(
    String id,
    String account,
    String name,
    String employeeNo,
    String phone,
    String orgId,
    List<String> roleIds,
    String status,
    String department,
    String roles,
    String email,
    Integer emailVersion) {}
