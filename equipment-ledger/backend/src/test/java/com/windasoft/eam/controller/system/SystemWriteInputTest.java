package com.windasoft.eam.controller.system;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.RoleForm;
import com.windasoft.eam.dto.system.UserForm;
import com.windasoft.eam.dto.system.UserStatusForm;
import jakarta.validation.Validation;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 状态及关联标识不能在JSON绑定时被小数截断，也不能依赖前端限制状态。 */
class SystemWriteInputTest {
  private final ObjectMapper json = new ObjectMapper();

  @Test
  void editStatusUsesTheSameLegalRangeAsStatusAction() throws Exception {
    var form =
        json.readValue(
            "{\"account\":\"t\",\"name\":\"t\",\"orgId\":71003,\"status\":2}", UserForm.class);
    try (var factory = Validation.buildDefaultValidatorFactory()) {
      assertFalse(factory.getValidator().validate(form).isEmpty());
    }
  }

  @Test
  void fractionalStatusAndOrganizationAreRejected() {
    assertThrows(
        JsonProcessingException.class,
        () -> json.readValue("{\"status\":1.5}", UserStatusForm.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue("{\"account\":\"t\",\"name\":\"t\",\"orgId\":71003.8}", UserForm.class));
  }

  @Test
  void integerStringsRemainCompatible() throws Exception {
    var form =
        json.readValue(
            "{\"account\":\"t\",\"name\":\"t\",\"orgId\":\"71003\",\"roleIds\":[\"72001\"],\"status\":\"1\",\"emailVersion\":\"0\"}",
            UserForm.class);
    assertEquals(71003L, form.orgId());
    assertEquals(List.of(72001L), form.roleIds());
    assertEquals(1, form.status());
    assertEquals(0, form.emailVersion());
  }

  @Test
  void nestedPermissionTargetIdsMustStayExact() {
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                "{\"code\":\"R\",\"name\":\"角色\",\"menuPermissions\":{\"equipment\":{\"actions\":[\"查看\"],\"dataScopes\":[\"selected\"],\"orgIds\":[71003.9]}}}",
                RoleForm.class));
  }
}
