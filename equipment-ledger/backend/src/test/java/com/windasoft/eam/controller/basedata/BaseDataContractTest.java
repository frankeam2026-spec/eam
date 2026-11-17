package com.windasoft.eam.controller.basedata;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.basedata.BaseItemForm;
import com.windasoft.eam.dto.basedata.BaseItemView;
import com.windasoft.eam.service.basedata.BaseDataService;
import com.windasoft.eam.support.ContractTypes;
import jakarta.validation.Validation;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BaseDataContractTest {
  @Test
  void allResponsesHaveExplicitBusinessTypes() {
    for (var method : BaseDataController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertExplicit(method.getGenericReturnType());
  }

  @Test
  void fractionalCompanyIdCannotBeTruncated() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            new ObjectMapper()
                .readValue("{\"name\":\"地点\",\"companyId\":71002.9}", BaseItemForm.class));
  }

  @Test
  void unknownStatusIsRejected() throws Exception {
    var form = new ObjectMapper().readValue("{\"name\":\"单位\",\"status\":2}", BaseItemForm.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertFalse(factory.getValidator().validate(form).isEmpty());
    }
  }

  @Test
  void httpKeepsOriginalDataShapeAndRejectsMissingStatus() throws Exception {
    var service = mock(BaseDataService.class);
    when(service.list("units", null))
        .thenReturn(
            List.of(new BaseItemView(false, "", "9007199254740993", "U", "单位", "", 0, "启用", 0)));
    var mvc = MockMvcBuilders.standaloneSetup(new BaseDataController(service)).build();
    mvc.perform(get("/api/v1/base-data/units"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].id").value("9007199254740993"))
        .andExpect(jsonPath("$.data[0].companyId").value(""))
        .andExpect(jsonPath("$.data[0].precision").value(0));
    mvc.perform(delete("/api/v1/base-data/units/9007199254740993"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true));
    mvc.perform(
            patch("/api/v1/base-data/units/1/status")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest());
  }
}
