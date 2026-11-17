package com.windasoft.eam.controller.business;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.business.BusinessPublicationService;
import com.windasoft.eam.service.business.BusinessReferenceService;
import com.windasoft.eam.support.ContractTypes;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class BusinessConfigurationContractTest {
  @Test
  void configurationLifecycleHasExplicitResponses() {
    for (var method : BusinessConfigController.class.getDeclaredMethods()) {
      if (Set.of(
              "objects",
              "configuration",
              "save",
              "publish",
              "versions",
              "restore",
              "events",
              "runtime")
          .contains(method.getName())) {
        ContractTypes.assertExplicit(method.getGenericReturnType());
      }
    }
  }

  private record InvalidResponse(DataResponse<List<Object>> data) {}

  @Test
  void nestedDynamicBusinessResponseIsRejected() {
    assertThrows(AssertionError.class, () -> ContractTypes.assertExplicit(InvalidResponse.class));
  }

  @Test
  void publicationVersionCannotBeTruncated() throws Exception {
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new BusinessConfigController(
                    mock(BusinessConfigService.class),
                    mock(BusinessReferenceService.class),
                    mock(BusinessPublicationService.class)))
            .build();
    mvc.perform(
            post("/api/v1/business-config/objects/equipment/publish")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"version\":0.5}"))
        .andExpect(status().isBadRequest());
  }
}
