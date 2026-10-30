package com.windasoft.eam.dto.common;

/** Single-resource response; preserves the existing {data: ...} wire contract. */
public record DataResponse<T>(T data) {}
