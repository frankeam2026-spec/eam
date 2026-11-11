package com.windasoft.eam.dto.inspection;

/** 对应现有inspectionUpload前端读取的五个固定字段，大小仍按字节数值输出。 */
public record InspectionFileUploadResult(
    String id, String name, String contentType, long size, String url) {}
