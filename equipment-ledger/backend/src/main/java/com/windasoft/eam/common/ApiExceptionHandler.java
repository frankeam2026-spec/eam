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
  private String validationMessage(String value) {
    if (value != null && value.matches("\\{[a-zA-Z0-9_.]+}"))
      return MessageContext.text(value.substring(1, value.length() - 1));
    return value;
  }

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
        .body(
            Map.of(
                "code",
                code,
                "message",
                e instanceof LocalizedStatusException localized
                    ? MessageContext.render(localized.messageRef())
                    : e.getReason() == null
                        ? com.windasoft.eam.common.MessageContext.text("server.common.message0070")
                        : e.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<?> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_ARGUMENT",
                "message",
                e instanceof LocalizedArgumentException localized
                    ? MessageContext.render(localized.messageRef())
                    : e.getMessage() == null
                        ? com.windasoft.eam.common.MessageContext.text("server.common.message0071")
                        : e.getMessage()));
  }

  @ExceptionHandler({
    HttpMessageNotReadableException.class,
    MethodArgumentTypeMismatchException.class,
    MissingServletRequestParameterException.class
  })
  ResponseEntity<?> malformedRequest(Exception e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "INVALID_ARGUMENT",
                "message",
                com.windasoft.eam.common.MessageContext.text("server.common.message0072")));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<?> dataConflict(DataIntegrityViolationException e) {
    log.warn("API database constraint conflict", e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            Map.of(
                "code",
                "DATA_CONFLICT",
                "message",
                com.windasoft.eam.common.MessageContext.text("server.common.message0073")));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<?> uploadLimit(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(
            Map.of(
                "code",
                "UPLOAD_TOO_LARGE",
                "message",
                com.windasoft.eam.common.MessageContext.text("server.common.message0074")));
  }

  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<?> fieldValidation(FieldValidationException e) {
    return ResponseEntity.badRequest()
        .body(
            Map.of(
                "code",
                "VALIDATION_ERROR",
                "message",
                e.messageRef() == null
                    ? validationMessage(e.getMessage())
                    : MessageContext.render(e.messageRef()),
                "fieldErrors",
                Map.of(
                    e.field(),
                    e.messageRef() == null
                        ? validationMessage(e.getMessage())
                        : MessageContext.render(e.messageRef()))));
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
                    x.getDefaultMessage() == null
                        ? com.windasoft.eam.common.MessageContext.text("server.common.message0075")
                        : validationMessage(x.getDefaultMessage())));
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

  @ExceptionHandler(org.flowable.common.engine.api.FlowableException.class)
  ResponseEntity<?> workflowFailure(org.flowable.common.engine.api.FlowableException e) {
    Throwable cause = e;
    for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause())
      if (cause instanceof IllegalArgumentException invalid) return badRequest(invalid);
    return unexpected(e);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception e) {
    log.error("API request failed", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            Map.of(
                "code",
                "INTERNAL_ERROR",
                "message",
                com.windasoft.eam.common.MessageContext.text("server.common.message0076")));
  }
}
