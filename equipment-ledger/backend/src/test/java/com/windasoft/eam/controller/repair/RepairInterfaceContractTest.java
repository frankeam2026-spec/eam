package com.windasoft.eam.controller.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import com.fasterxml.jackson.core.type.TypeReference;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.repair.RepairOrderView;
import com.windasoft.eam.service.repair.RepairApprovalService;
import java.lang.reflect.*;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class RepairInterfaceContractTest {
  @Test
  void approvalVersionMustBePresentAndNonnegative() throws Exception {
    var approvals = mock(RepairApprovalService.class);
    var controller = new RepairOrderController(null, null, null, null, null, null);
    ReflectionTestUtils.setField(controller, "approvals", approvals);
    var mvc =
        MockMvcBuilders.standaloneSetup(controller)
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    for (String body : new String[] {"{}", "{\"rowVersion\":null}", "{\"rowVersion\":-1}"}) {
      mvc.perform(
              post("/api/v1/repairs/1/approval-submit")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(body))
          .andExpect(status().isBadRequest());
    }
    verifyNoInteractions(approvals);
    mvc.perform(
            post("/api/v1/repairs/1/approval-submit")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":0}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data").value(true));
    verify(approvals).submit(1, 0);
  }

  static boolean dynamicWrapper(Type t) {
    if (t == Object.class) {
      return true;
    }
    if (t instanceof ParameterizedType p) {
      if (p.getRawType() == Map.class) {
        return true;
      }
      for (var a : p.getActualTypeArguments()) {
        if (dynamicWrapper(a)) {
          return true;
        }
      }
    }
    return false;
  }

  @Test
  void fixedControllerResponsesCannotHideObjectOrMapInWrappers() {
    for (var m : RepairOrderController.class.getDeclaredMethods()) {
      assertFalse(dynamicWrapper(m.getGenericReturnType()), m.toString());
    }
    assertTrue(dynamicWrapper(new TypeReference<List<Map<String, Object>>>() {}.getType()));
    assertFalse(dynamicWrapper(new TypeReference<DataResponse<RepairOrderView>>() {}.getType()));
  }

  @Test
  void typedCommandDoesNotTruncateFractionalIds() throws Exception {
    var json = new com.fasterxml.jackson.databind.ObjectMapper();
    assertThrows(
        com.fasterxml.jackson.core.JsonProcessingException.class,
        () ->
            json.readValue(
                "{\"action\":\"process\",\"faultTypeId\":1.5}",
                com.windasoft.eam.dto.repair.RepairWorkflowCommand.class));
    assertThrows(
        com.fasterxml.jackson.core.JsonProcessingException.class,
        () ->
            json.readValue(
                "{\"action\":\"dispatch\",\"userIds\":[1.5]}",
                com.windasoft.eam.dto.repair.RepairWorkflowCommand.class));
    assertEquals(
        9007199254740993L,
        json.readValue(
                "{\"action\":\"process\",\"faultTypeId\":\"9007199254740993\"}",
                com.windasoft.eam.dto.repair.RepairWorkflowCommand.class)
            .faultTypeId());
  }
}
