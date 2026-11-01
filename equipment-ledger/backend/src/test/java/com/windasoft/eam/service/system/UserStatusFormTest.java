package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.UserStatusForm;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

class UserStatusFormTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void missingNullAndOutOfRangeStatusAreRejected() throws Exception {
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      var validator = factory.getValidator();
      for (String body : new String[]{"{}", "{\"status\":null}", "{\"status\":-1}", "{\"status\":2}"}) {
        var form = json.readValue(body, UserStatusForm.class);
        assertFalse(validator.validate(form).isEmpty(), body);
      }
      for (int status : new int[]{0, 1}) {
        var form = json.readValue("{\"status\":" + status + "}", UserStatusForm.class);
        assertTrue(validator.validate(form).isEmpty());
        assertEquals(status, form.status());
      }
    }
  }
}
