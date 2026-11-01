package com.windasoft.eam.dto.system;

import java.util.List;

public record UserView(
    String id, String account, String name, String employeeNo, String phone,
    String orgId, List<String> roleIds, String status, String email, Object emailVersion) {}
