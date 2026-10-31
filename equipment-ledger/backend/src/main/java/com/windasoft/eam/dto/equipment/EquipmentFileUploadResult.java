package com.windasoft.eam.dto.equipment;

/** 上传设备文件成功后的稳定响应契约。 */
public record EquipmentFileUploadResult(
    String id, String name, String contentType, long size, String url) {}
