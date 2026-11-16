package com.windasoft.eam.dto.maintenance;

/** 保养组织树的既有四字段协议；根节点 parentId 由入口保留原来的空串或0约定。 */
public record MaintenanceOrganizationOption(String id, String name, String parentId, String type) {}
