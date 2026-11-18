package com.windasoft.eam.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.NotificationContracts.Policy;
import com.windasoft.eam.service.system.NotificationChannelService;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.NotificationRetentionService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class NotificationContractTest {
  @Test
  void allManagementResponsesHaveExplicitNestedTypes() {
    for (var method : NotificationController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertExplicit(method.getGenericReturnType());
  }

  @Test
  void invalidRecipientNeverReachesPolicyWriter() throws Exception {
    var service = mock(NotificationPolicyService.class);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new NotificationController(
                    service,
                    mock(NotificationChannelService.class),
                    mock(NotificationRetentionService.class)))
            .build();
    String body =
        """
        {"name":"规则","businessKind":"repair","rules":[{"scene":"PROCESS","value":1,
         "unit":"MINUTE","enabled":true,"recipients":%s,"cc":%s}]}
        """;
    for (var invalid : List.of(new String[] {"[7.5]", "[]"}, new String[] {"[7]", "[null]"})) {
      mvc.perform(
              post("/api/v1/notifications/policies")
                  .contentType("application/json")
                  .content(body.formatted(invalid[0], invalid[1])))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(service);
    var policy =
        new ObjectMapper().readValue(body.formatted("[\"9007199254740993\"]", "[]"), Policy.class);
    assertEquals(List.of(9007199254740993L), policy.rules().getFirst().recipients());
  }
}
