package com.windasoft.eam.common;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

class ApiExceptionHandlerTest {
  private MockMvc mvc;

  @BeforeEach
  void setUp() {
    mvc =
        MockMvcBuilders.standaloneSetup(new ProbeController())
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void malformedJsonReturns400() throws Exception {
    mvc.perform(post("/probe").contentType(MediaType.APPLICATION_JSON).content("{"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void wrongPathTypeReturns400() throws Exception {
    mvc.perform(get("/probe/id/not-a-number"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void missingRequiredVersionReturns400() throws Exception {
    mvc.perform(get("/probe/version"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_ARGUMENT"));
  }

  @Test
  void staleVersionReturnsConflictContract() throws Exception {
    mvc.perform(get("/probe/stale"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"));
  }

  @Test
  void databaseConflictDoesNotExposeSql() throws Exception {
    mvc.perform(get("/probe/conflict"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("DATA_CONFLICT"))
        .andExpect(jsonPath("$.message").value("数据存在重复或关联冲突，请检查后重试"));
  }

  @Test
  void nullArgumentMessageStillReturns400() throws Exception {
    mvc.perform(get("/probe/argument"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("请求参数不正确"));
  }

  @Test
  void uploadLimitReturns413() throws Exception {
    mvc.perform(get("/probe/upload"))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.code").value("UPLOAD_TOO_LARGE"));
  }

  @Test
  void workflowCalculationErrorKeepsItsActionableMessage() throws Exception {
    mvc.perform(get("/probe/workflow"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message").value("脚本执行超时，已终止"));
  }

  @Test
  void methodParameterValidationIsAFieldErrorInsteadOf500() throws Exception {
    mvc.perform(get("/probe/positive").param("quantity", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors.quantity").isString());
  }

  @Test
  void invalidServerReturnValueRemainsAnInternalError() throws Exception {
    mvc.perform(get("/probe/invalid-return"))
        .andExpect(status().isInternalServerError())
        .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
        .andExpect(jsonPath("$.fieldErrors").doesNotExist());
  }

  @RestController
  static class ProbeController {
    record Payload(String name) {}

    @GetMapping("/probe/positive")
    int positive(@RequestParam @jakarta.validation.constraints.Min(1) int quantity) {
      return quantity;
    }

    @GetMapping("/probe/invalid-return")
    @jakarta.validation.constraints.Min(1)
    int invalidReturn() {
      return 0;
    }

    @PostMapping("/probe")
    Payload body(@RequestBody Payload payload) {
      return payload;
    }

    @GetMapping("/probe/id/{id}")
    Long id(@PathVariable Long id) {
      return id;
    }

    @GetMapping("/probe/version")
    int version(@RequestParam int rowVersion) {
      return rowVersion;
    }

    @GetMapping("/probe/stale")
    void stale() {
      VersionGuard.check(2, 1);
    }

    @GetMapping("/probe/conflict")
    void conflict() {
      throw new DataIntegrityViolationException("internal SQL detail");
    }

    @GetMapping("/probe/argument")
    void argument() {
      throw new IllegalArgumentException();
    }

    @GetMapping("/probe/upload")
    void upload() {
      throw new MaxUploadSizeExceededException(20 * 1024 * 1024);
    }

    @GetMapping("/probe/workflow")
    void workflow() {
      throw new org.flowable.common.engine.api.FlowableException(
          "delegate failed", new IllegalArgumentException("脚本执行超时，已终止"));
    }
  }
}
