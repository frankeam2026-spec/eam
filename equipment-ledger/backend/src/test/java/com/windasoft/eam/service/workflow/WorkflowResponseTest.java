package com.windasoft.eam.service.workflow;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews;
import com.windasoft.eam.dto.workflow.WorkflowTrialStep;
import java.util.List;
import org.junit.jupiter.api.Test;

class WorkflowResponseTest {
  @Test
  void browserIdsAndExpressionNumbersKeepPrecision() {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var round =
        new WorkflowHistoryRound(
            9007199254740993L,
            1,
            "RUNNING",
            12L,
            5000000000L,
            null,
            null,
            "流程",
            List.of(),
            List.of(),
            List.of());
    var tree = mapper.valueToTree(new DataResponse<>(List.of(round)));
    assertEquals("9007199254740993", tree.at("/data/0/id").textValue());
    assertEquals(5000000000L, tree.at("/data/0/rowVersion").longValue());
    var expression =
        WorkflowExpression.parse(
            "SUM(lines.quantity) > 12345678901234567890.123",
            new WorkflowVariables().catalog("requisition"));
    var output = mapper.valueToTree(WorkflowTemplateViews.Expression.from(expression));
    assertEquals("12345678901234567890.123", output.at("/children/1/literal").textValue());
  }

  @Test
  void selectorAndTemplatePagesKeepTheirDifferentExistingShapes() {
    var mapper = new ObjectMapper().findAndRegisterModules();
    var choice =
        mapper.valueToTree(
            new WorkflowTemplateViews.ChoicePage(
                List.of(new WorkflowTemplateViews.Choice("9007199254740993", "张三", null)),
                new WorkflowTemplateViews.Count(1)));
    assertEquals(1, choice.at("/page/total").asInt());
    assertFalse(choice.has("total"));
    assertFalse(choice.at("/data/0").has("code"));
    var page = mapper.valueToTree(new WorkflowTemplateViews.TemplatePage(List.of(), 0, 1, 20));
    assertEquals(1, page.path("page").asInt());
    assertEquals(20, page.path("size").asInt());
    assertTrue(page.path("total").isNumber());
    // publishedBy原接口是数值，不属于旧转换器推断的Id字段，保持该历史协议。
    var version =
        mapper.valueToTree(
            new WorkflowTemplateViews.Version("12", 1, "PUBLISHED", 9007199254740993L, null));
    assertTrue(version.path("publishedBy").isNumber());
    assertEquals(9007199254740993L, version.path("publishedBy").longValue());
  }

  @Test
  void optionalTraceFieldsAndLegacyFlagScalarsRemainCompatible() {
    var mapper = new ObjectMapper();
    var trace = mapper.valueToTree(WorkflowTrialStep.branch("high", "高金额", true));
    assertTrue(trace.path("matched").asBoolean());
    assertFalse(trace.has("node"));
    assertFalse(trace.has("type"));
    assertTrue(WorkflowTemplateViews.flag(false).isBoolean());
    assertTrue(WorkflowTemplateViews.flag(0).isNumber());
    assertThrows(IllegalStateException.class, () -> WorkflowTemplateViews.flag("false"));
  }
}
