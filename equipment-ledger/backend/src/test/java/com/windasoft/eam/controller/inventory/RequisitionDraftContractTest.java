package com.windasoft.eam.controller.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.inventory.RequisitionDraftForm;
import com.windasoft.eam.service.inventory.RequisitionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RequisitionDraftContractTest {
  private MockMvc mvc;
  private RequisitionService service;
  private ObjectMapper json;
  private static final String BODY =
      """
      {"taskKind":"维修","taskId":"9007199254740993","warehouseId":"12",
       "remark":"  备注  ","lines":[{"materialId":"23","quantity":"1.250000"}]}
      """;

  @BeforeEach
  void setup() {
    json = Jackson2ObjectMapperBuilder.json().build();
    service = mock(RequisitionService.class);
    when(service.create(any()))
        .thenAnswer(
            call -> {
              call.getArgument(0, RequisitionDraftForm.class).validate(false);
              return null;
            });
    when(service.update(anyLong(), any()))
        .thenAnswer(
            call -> {
              call.getArgument(1, RequisitionDraftForm.class).validate(true);
              return null;
            });
    mvc =
        MockMvcBuilders.standaloneSetup(new RequisitionController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void acceptsExactStringIdsNumericQuantityAndKnownReadonlyFields() throws Exception {
    String input =
        BODY.replace("\"1.250000\"", "1.250000")
            .replace("\"remark\":", "\"companyId\":\"999\",\"status\":\"已完成\",\"remark\":")
            .replace("\"materialId\":", "\"materialName\":\"展示值\",\"materialId\":");
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(input))
        .andExpect(status().isOk());
    var captured = org.mockito.ArgumentCaptor.forClass(RequisitionDraftForm.class);
    verify(service).create(captured.capture());
    assertEquals(9007199254740993L, captured.getValue().taskId());
    assertEquals("备注", captured.getValue().remark());
    assertEquals("1.250000", captured.getValue().lines().getFirst().quantity().toPlainString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.9", "true", "9223372036854775808", "\"1.0\"", "[]"})
  void rejectsIdCoercionAndOverflowWithFieldLocation(String id) throws Exception {
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"9007199254740993\"", id)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.taskId").isString());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"abc\"", "true", "{}", "[]"})
  void malformedQuantityReportsNestedField(String quantity) throws Exception {
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"1.250000\"", quantity)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['lines[0].quantity']").isString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"\"0\"", "\"-1\"", "\"1.0000001\"", "\"100000000000000\"", "null"})
  void quantityBoundsRemainFieldErrors(String quantity) throws Exception {
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"1.250000\"", quantity)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
        .andExpect(jsonPath("$.fieldErrors['lines[0].quantity']").isString());
  }

  @ParameterizedTest
  @ValueSource(strings = {"null", "-1", "1.5", "2147483648", "\"0\""})
  void updateRequiresExactNonnegativeIntegerVersion(String version) throws Exception {
    String input = BODY.replace("\"taskKind\":", "\"rowVersion\":" + version + ",\"taskKind\":");
    mvc.perform(
            put("/api/v1/inventory/requisitions/100")
                .contentType(MediaType.APPLICATION_JSON)
                .content(input))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.rowVersion").isString());
  }

  @Test
  void unknownEditableFieldIsRejectedWhileKnownDisplayFieldsRemainCompatible() throws Exception {
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"remark\":", "\"warehosueId\":\"12\",\"remark\":")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.warehosueId").isString());
    mvc.perform(
            post("/api/v1/inventory/requisitions")
                .contentType(MediaType.APPLICATION_JSON)
                .content(BODY.replace("\"materialId\":", "\"quanity\":1,\"materialId\":")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['lines[0].quanity']").isString());
  }

  @Test
  void collectionAndPrecisionLimitsAreEnforcedForInternalCallersToo() throws Exception {
    var base = json.readValue(BODY, RequisitionDraftForm.class);
    assertThrows(
        com.windasoft.eam.common.FieldValidationException.class,
        () ->
            new RequisitionDraftForm(
                    base.taskKind(),
                    base.taskId(),
                    base.warehouseId(),
                    "",
                    java.util.List.of(),
                    null)
                .validate(false));
    assertThrows(
        com.windasoft.eam.common.FieldValidationException.class,
        () ->
            new RequisitionDraftForm(
                    base.taskKind(),
                    base.taskId(),
                    base.warehouseId(),
                    "",
                    java.util.Collections.nCopies(501, base.lines().getFirst()),
                    null)
                .validate(false));
    var lines = new java.util.ArrayList<>(base.lines());
    var copy =
        new RequisitionDraftForm(
            base.taskKind(), base.taskId(), base.warehouseId(), "", lines, null);
    lines.clear();
    assertEquals(1, copy.lines().size());
    assertThrows(UnsupportedOperationException.class, () -> copy.lines().clear());
  }
}
