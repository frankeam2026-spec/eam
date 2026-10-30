package com.windasoft.eam.dto.common;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Existing error protocol, including the deliberately dynamic field-path dictionary. */
public record ApiError(
    String code,
    String message,
    @JsonInclude(JsonInclude.Include.NON_NULL) Map<String, String> fieldErrors) {
  public ApiError(String code, String message) {
    this(code, message, null);
  }

  public ApiError {
    fieldErrors =
        fieldErrors == null ? null : Collections.unmodifiableMap(new LinkedHashMap<>(fieldErrors));
  }
}
