package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.workflow.WorkflowResponse;
import java.util.*;
import org.junit.jupiter.api.Test;

class WorkflowResponseTest {
  @Test
  void browserIdsAndExpressionNumbersKeepPrecision() throws Exception {
    var mapper = new ObjectMapper();
    var tree =
        mapper.readTree(
            mapper.writeValueAsString(
                WorkflowResponse.exact(
                    Map.of(
                        "data",
                        List.of(
                            Map.of("id", 9007199254740993L, "user_id", 73001L, "rowVersion", 2L)),
                        "total",
                        1L))));
    assertEquals("9007199254740993", tree.at("/data/0/id").textValue());
    assertEquals("73001", tree.at("/data/0/user_id").textValue());
    assertTrue(tree.at("/data/0/rowVersion").isNumber());
    assertTrue(tree.get("total").isNumber());
    var expression =
        WorkflowExpression.parse(
            "SUM(lines.quantity) > 12345678901234567890.123",
            new WorkflowVariables().catalog("requisition"));
    var output = mapper.readTree(mapper.writeValueAsString(WorkflowResponse.exact(expression)));
    assertEquals("12345678901234567890.123", output.at("/children/1/literal").textValue());
  }
}
