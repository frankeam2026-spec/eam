package com.windasoft.eam.controller.knowledge;

import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.knowledge.AiGateway;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import org.junit.jupiter.api.Test;

class AiSettingsContractTest {
  @Test
  void settingsAndCallsHaveFixedTypes() {
    for (var method : AiSettingsController.class.getDeclaredMethods())
      if (Modifier.isPublic(method.getModifiers()))
        ContractTypes.assertExplicit(method.getGenericReturnType());
  }

  @Test
  void fractionalVersionIsRejected() {
    assertThrows(
        JsonProcessingException.class,
        () -> new ObjectMapper().readValue("{\"rowVersion\":0.5}", AiGateway.Settings.class));
  }
}
