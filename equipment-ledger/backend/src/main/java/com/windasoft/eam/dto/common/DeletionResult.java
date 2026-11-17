package com.windasoft.eam.dto.common;

/** 现有删除接口的业务结果字段，由HTTP入口在删除成功后返回。 */
public record DeletionResult(boolean deleted) {}
