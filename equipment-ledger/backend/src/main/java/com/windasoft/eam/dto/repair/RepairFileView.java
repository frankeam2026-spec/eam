package com.windasoft.eam.dto.repair;

/** 上传成功的维修文件元数据，ID不代表附件访问授权。 */
public record RepairFileView(String id, String name, String contentType, long size, String url) {}
