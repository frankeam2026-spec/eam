package com.windasoft.eam.controller.printing;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.printing.PrintViews;
import com.windasoft.eam.service.printing.DocumentPrintService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;

class PrintContractTest {
  private static final String LABELS = "java.util.Map<java.lang.String, java.lang.String>";
  private static final ContractTypes.DynamicField HEADERS =
      new ContractTypes.DynamicField(PrintViews.Catalog.class, "headers", LABELS);
  private static final ContractTypes.DynamicField COLUMNS =
      new ContractTypes.DynamicField(PrintViews.Catalog.class, "columns", LABELS);

  @Test
  void endpointsHaveExplicitNestedContracts() {
    for (var method : DocumentPrintController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertEndpoint(method, HEADERS, COLUMNS);
  }

  @Test
  void nestedFixedMapAndRequestBodyAreRejected() throws Exception {
    assertThrows(
        AssertionError.class,
        () ->
            ContractTypes.assertEndpoint(
                InvalidSignatures.class.getDeclaredMethod("nested"), HEADERS, COLUMNS));
    assertThrows(
        AssertionError.class,
        () ->
            ContractTypes.assertEndpoint(
                InvalidSignatures.class.getDeclaredMethod("request", Map.class), HEADERS, COLUMNS));
  }

  interface InvalidSignatures {
    DataResponse<List<Map<String, String>>> nested();

    String request(@RequestBody Map<String, String> input);
  }

  @Test
  void fractionalCompanyNeverReachesTemplateWriter() throws Exception {
    var service = mock(DocumentPrintService.class);
    MockMvcBuilders.standaloneSetup(new DocumentPrintController(service))
        .build()
        .perform(
            post("/api/v1/printing/templates")
                .contentType("application/json")
                .content("{\"name\":\"模板\",\"companyId\":7.5,\"config\":{}}"))
        .andExpect(status().isBadRequest());
    verifyNoInteractions(service);
  }
}
