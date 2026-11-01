package com.windasoft.eam.dto.system;

public record OrganizationView(
    String id, String name, String code, String type, String parentId,
    String manager, String status) {}
