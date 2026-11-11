package com.windasoft.eam.dto.maintenance;

/** 沿用 maintenanceUpload 的五字段契约；size 为字节数，保持 JSON 数值格式。 */
public record MaintenanceFileView(
    String id, String name, String contentType, long size, String url) {}
