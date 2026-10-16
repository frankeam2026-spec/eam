package com.windasoft.eam.common;

import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<?> status(ResponseStatusException e) {
    String code =
        switch (e.getStatusCode().value()) {
          case 401, 403 -> "ACCESS_ERROR";
          case 404 -> "NOT_FOUND";
          case 409 -> "DATA_CONFLICT";
          default -> "REQUEST_ERROR";
        };
    return ResponseEntity.status(e.getStatusCode())
        .body(Map.of("code", code, "message", e.getReason() == null ? "请求无法完成" : e.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_ARGUMENT",
                "message",
                e.getMessage() == null ? "请求参数不正确" : e.getMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<?> malformedRequest(Exception e) {
    return ResponseEntity.badRequest()
        .body(Map.of("code", "INVALID_ARGUMENT", "message", "请求格式或参数类型不正确"));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> dataConflict(DataIntegrityViolationException e) {
    log.warn("API database constraint conflict", e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(Map.of("code", "DATA_CONFLICT", "message", "数据存在重复或关联冲突，请检查后重试"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> uploadLimit(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(Map.of("code", "UPLOAD_TOO_LARGE", "message", "附件超过允许的大小，请缩小文件后重试"));
  }

  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<?> fieldValidation(FieldValidationException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "VALIDATION_ERROR",
                "message",
                e.getMessage(),
                "fieldErrors",
                Map.of(e.field(), e.getMessage())));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<?> validation(MethodArgumentNotValidException e) {
    var errors = new java.util.LinkedHashMap<String, String>();
    e.getBindingResult()
        .getFieldErrors()
        .forEach(
            x ->
                errors.putIfAbsent(
                    x.getField(),
                    x.getDefaultMessage() == null ? "该字段不正确" : x.getDefaultMessage()));
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "VALIDATION_ERROR",
                "message",
                String.join("；", errors.values()),
                "fieldErrors",
                errors));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("API request failed", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(Map.of("code", "INTERNAL_ERROR", "message", "系统暂时无法处理请求"));
  }
}
