package com.windasoft.eam.common;

import com.fasterxml.jackson.databind.JsonMappingException;
import com.windasoft.eam.dto.common.ApiError;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class ApiExceptionHandler {
  private String validationMessage(String value) {
    if (value != null && value.matches("\\{[a-zA-Z0-9_.]+}")) {
      return MessageContext.text(value.substring(1, value.length() - 1));
    }
    return value;
  }

  private static final org.slf4j.Logger log =
      org.slf4j.LoggerFactory.getLogger(ApiExceptionHandler.class);

  @ExceptionHandler(ResponseStatusException.class)
  ResponseEntity<ApiError> status(ResponseStatusException e) {
    String code =
        switch (e.getStatusCode().value()) {
          case 401, 403 -> "ACCESS_ERROR";
          case 404 -> "NOT_FOUND";
          case 409 -> "DATA_CONFLICT";
          default -> "REQUEST_ERROR";
        };
    return ResponseEntity.status(e.getStatusCode())
        .body(
            new ApiError(
                code,
                e instanceof LocalizedStatusException localized
                    ? MessageContext.render(localized.messageRef())
                    : e.getReason() == null
                        ? com.windasoft.eam.common.MessageContext.text("server.common.message0070")
                        : e.getReason()));
  }

  @ExceptionHandler(IllegalArgumentException.class)
  ResponseEntity<ApiError> badRequest(IllegalArgumentException e) {
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "INVALID_ARGUMENT",
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
  ResponseEntity<ApiError> malformedRequest(Exception e) {
    String field = "";
    Throwable cause = e;
    FieldValidationException fieldFailure = null;
    for (int depth = 0; cause != null && depth < 16; depth++, cause = cause.getCause()) {
      if (cause instanceof JsonMappingException mapping && field.isEmpty()) {
        StringBuilder path = new StringBuilder();
        for (var reference : mapping.getPath()) {
          if (reference.getFieldName() != null) {
            if (!path.isEmpty()) {
              path.append('.');
            }
            path.append(reference.getFieldName());
          } else if (reference.getIndex() >= 0) {
            path.append('[').append(reference.getIndex()).append(']');
          }
        }
        field = path.toString();
      }
      if (cause instanceof FieldValidationException invalid) {
        fieldFailure = invalid;
      }
    }
    String message = MessageContext.text("server.common.message0072");
    if (fieldFailure != null) {
      if (field.isEmpty()) {
        field = fieldFailure.field();
      } else if (!field.equals(fieldFailure.field())
          && !field.endsWith("." + fieldFailure.field())) {
        // A record's @JsonAnySetter can fail after the enclosing list element was bound;
        // Jackson then supplies the element path, while the exception supplies its property.
        field += "." + fieldFailure.field();
      }
    }
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "INVALID_ARGUMENT", message, field.isEmpty() ? null : Map.of(field, message)));
  }

  @ExceptionHandler(DataIntegrityViolationException.class)
  ResponseEntity<ApiError> dataConflict(DataIntegrityViolationException e) {
    log.warn("API database constraint conflict", e);
    return ResponseEntity.status(HttpStatus.CONFLICT)
        .body(
            new ApiError(
                "DATA_CONFLICT",
                com.windasoft.eam.common.MessageContext.text("server.common.message0073")));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  ResponseEntity<ApiError> uploadLimit(MaxUploadSizeExceededException e) {
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(
            new ApiError(
                "UPLOAD_TOO_LARGE",
                com.windasoft.eam.common.MessageContext.text("server.common.message0074")));
  }

  @ExceptionHandler(FieldValidationException.class)
  ResponseEntity<ApiError> fieldValidation(FieldValidationException e) {
    return ResponseEntity.badRequest()
        .body(
            new ApiError(
                "VALIDATION_ERROR",
                e.messageRef() == null
                    ? validationMessage(e.getMessage())
                    : MessageContext.render(e.messageRef()),
                Map.of(
                    e.field(),
                    e.messageRef() == null
                        ? validationMessage(e.getMessage())
                        : MessageContext.render(e.messageRef()))));
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
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
        .body(new ApiError("VALIDATION_ERROR", String.join("；", errors.values()), errors));
  }

  @ExceptionHandler(HandlerMethodValidationException.class)
  ResponseEntity<ApiError> methodValidation(HandlerMethodValidationException e) {
    if (e.isForReturnValue()) {
      return unexpected(e);
    }
    var errors = new java.util.LinkedHashMap<String, String>();
    for (var result : e.getParameterValidationResults()) {
      var parameter = result.getMethodParameter();
      String field = parameter.getParameterName();
      if (field == null) {
        field = "parameters[" + parameter.getParameterIndex() + "]";
      }
      var query =
          parameter.getParameterAnnotation(
              org.springframework.web.bind.annotation.RequestParam.class);
      var path =
          parameter.getParameterAnnotation(
              org.springframework.web.bind.annotation.PathVariable.class);
      if (query != null && !query.name().isEmpty()) {
        field = query.name();
      } else if (path != null && !path.name().isEmpty()) {
        field = path.name();
      }
      for (var error : result.getResolvableErrors()) {
        String key =
            error instanceof org.springframework.validation.FieldError nested
                ? nested.getField()
                : field;
        errors.putIfAbsent(
            key,
            error.getDefaultMessage() == null
                ? MessageContext.text("server.common.message0075")
                : validationMessage(error.getDefaultMessage()));
      }
    }
    return ResponseEntity.badRequest()
        .body(new ApiError("VALIDATION_ERROR", String.join("；", errors.values()), errors));
  }

  @ExceptionHandler(org.flowable.common.engine.api.FlowableException.class)
  ResponseEntity<ApiError> workflowFailure(org.flowable.common.engine.api.FlowableException e) {
    Throwable cause = e;
    for (int i = 0; i < 12 && cause != null; i++, cause = cause.getCause()) {
      if (cause instanceof IllegalArgumentException invalid) {
        return badRequest(invalid);
      }
    }
    return unexpected(e);
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<ApiError> unexpected(Exception e) {
    log.error("API request failed", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(
            new ApiError(
                "INTERNAL_ERROR",
                com.windasoft.eam.common.MessageContext.text("server.common.message0076")));
  }
}
