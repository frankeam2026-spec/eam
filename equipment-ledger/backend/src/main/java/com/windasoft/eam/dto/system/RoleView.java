package com.windasoft.eam.dto.system;

import java.util.Map;

public record RoleView(
    String id, String code, String name, String description, String status,
    Map<String, Object> menuPermissions) {}
