package com.windasoft.eam.controller.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound.Approval;
import com.windasoft.eam.support.ContractTypes;
import java.util.List;
import org.junit.jupiter.api.Test;

/** 固定历史契约穿透响应包装；精确ID和旧下划线字段不能因移除Map转换而变化。 */
class WorkflowHistoryContractTest {
  @Test
  void instanceEndpointsDeclareCompleteContracts() {
    for (var controller :
        List.of(WorkflowInstanceController.class, WorkflowTemplateController.class)) {
      for (var method : controller.getDeclaredMethods()) {
        if (java.lang.reflect.Modifier.isPublic(method.getModifiers())) {
          ContractTypes.assertExplicit(method.getGenericReturnType());
          for (var parameter : method.getGenericParameterTypes()) {
            ContractTypes.assertExplicit(parameter);
          }
        }
      }
    }
    assertThrows(AssertionError.class, () -> ContractTypes.assertExplicit(Object.class));
    assertThrows(AssertionError.class, () -> ContractTypes.assertExplicit(java.util.Map.class));
  }

  @Test
  void identifiersRemainStringsAndVersionRemainsFullBigint() throws Exception {
    long id = 9007199254740993L;
    var approval =
        new Approval(id, 1L, id, "approve", "engine", 2L, "PENDING", "", null, "张三", "主管审批");
    var round =
        new WorkflowHistoryRound(
            id,
            1,
            "RUNNING",
            id,
            5000000000L,
            null,
            null,
            "流程",
            List.of(approval),
            List.of(),
            List.of());
    var json =
        new ObjectMapper().findAndRegisterModules().valueToTree(new DataResponse<>(List.of(round)));
    var result = json.path("data").get(0);
    assertEquals(Long.toString(id), result.path("id").asText());
    assertTrue(result.path("id").isTextual());
    assertEquals(5000000000L, result.path("rowVersion").longValue());
    assertEquals(Long.toString(id), result.path("approvals").get(0).path("instance_id").asText());
    assertEquals("张三", result.path("approvals").get(0).path("user_name").asText());
    assertFalse(result.path("approvals").get(0).has("instanceId"));
  }
}
