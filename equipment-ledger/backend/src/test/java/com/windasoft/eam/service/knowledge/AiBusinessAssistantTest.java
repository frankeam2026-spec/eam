package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.*;
import com.windasoft.eam.service.system.WorkInboxService;
import java.util.*;
import org.junit.jupiter.api.*;
import org.mockito.ArgumentCaptor;

class AiBusinessAssistantTest {
  final ObjectMapper json = new ObjectMapper();
  final AiGateway gateway = mock(AiGateway.class);
  final AiBusinessQueryService queries = mock(AiBusinessQueryService.class);
  final AiAnalyticsService analytics = mock(AiAnalyticsService.class);
  final WorkInboxService inbox = mock(WorkInboxService.class);
  final AiBusinessAssistantService service =
      new AiBusinessAssistantService(gateway, queries, analytics, inbox, json);

  void response(String intent) throws Exception {
    when(gateway.post(anyString(), anyMap(), eq(false)))
        .thenReturn(
            json.readTree(
                "{\"choices\":[{\"message\":{\"content\":"
                    + json.writeValueAsString(intent)
                    + "}}]}"));
  }

  @BeforeEach
  void catalog() {
    when(queries.catalog()).thenReturn(Map.of("equipment", Map.of("label", "设备台账")));
    when(inbox.assistantKinds()).thenReturn(List.of(Map.of("module", "inspection", "kind", "待审核")));
  }

  @Test
  void validatesAllPlansBeforeExecutingAndDoesNotFallBackToKnowledge() throws Exception {
    response(
        "{\"operation\":\"BUSINESS\",\"queries\":[{\"domain\":\"equipment\",\"operation\":\"COUNT\"},{\"domain\":\"repair\",\"operation\":\"LIST\",\"where\":{\"field\":\"inventedColumn\",\"op\":\"eq\",\"values\":[\"x\"]}}]}");
    assertNotNull(service.tryAnswer("所有设备和故障记录", List.of(), null));
    verify(queries, never()).query(any(), any(), anyInt(), anyInt());
  }

  @Test
  void resultsAreFormattedLocallyAndNeverSentToModel() throws Exception {
    response(
        "{\"operation\":\"BUSINESS\",\"queries\":[{\"domain\":\"equipment\",\"operation\":\"COUNT\"}]}");
    when(queries.query(any(), isNull(), eq(1), eq(20)))
        .thenReturn(
            Map.of(
                "title",
                "设备台账",
                "valueLabel",
                "数量",
                "value",
                987654,
                "range",
                "全部",
                "timeField",
                ""));
    var result = (Map<?, ?>) service.tryAnswer("我们有多少设备", List.of(), null);
    assertTrue(result.get("answer").toString().contains("987654"));
    var body = ArgumentCaptor.forClass(Map.class);
    verify(gateway, times(1)).post(eq("/chat/completions"), body.capture(), eq(false));
    assertFalse(body.getValue().toString().contains("987654"));
  }

  @Test
  void previousValidatedPlansAreProvidedWithoutRows() throws Exception {
    response("{\"operation\":\"CLARIFY\",\"message\":\"test\"}");
    service.tryAnswer(
        "只看白班",
        List.of("本月日常点检任务"),
        null,
        json.readTree("[{\"domain\":\"dailyTask\",\"operation\":\"LIST\",\"period\":\"month\"}]"));
    var body = ArgumentCaptor.forClass(Map.class);
    verify(gateway).post(anyString(), body.capture(), eq(false));
    assertTrue(body.getValue().toString().contains("previousValidatedPlans"));
    assertTrue(body.getValue().toString().contains("dailyTask"));
  }

  @Test
  void approvalQueryIsRestrictedToCorrectKindAndUnknownFiltersAreRejected() throws Exception {
    response(
        "{\"operation\":\"TODO\",\"queries\":[{\"module\":\"inspection\",\"kind\":\"待审核\",\"state\":\"pending\",\"search\":\"\"}]}");
    when(inbox.list("todo", "pending", "inspection", "待审核", "", 1, 20))
        .thenReturn(Map.of("data", List.of(), "total", 0));
    assertTrue(
        ((Map<?, ?>) service.tryAnswer("待我审核的点检", List.of(), null)).containsKey("workInboxes"));
    verify(inbox).list("todo", "pending", "inspection", "待审核", "", 1, 20);
    clearInvocations(inbox);
    response(
        "{\"operation\":\"TODO\",\"queries\":[{\"module\":\"inspection\",\"kind\":\"待审核\",\"period\":\"lastMonth\"}]}");
    service.tryAnswer("上月待我审核的点检", List.of(), null);
    verify(inbox, never())
        .list(anyString(), anyString(), anyString(), anyString(), anyString(), anyInt(), anyInt());
  }

  @Test
  void onlyExplicitKnowledgeIntentMayUseRetrieval() throws Exception {
    response("{\"operation\":\"CLARIFY\",\"message\":\"没有运行小时，无法计算MTBF\"}");
    assertNotNull(service.tryAnswer("MTBF", List.of(), null));
    response("{\"operation\":\"KNOWLEDGE\",\"queries\":[]}");
    assertNull(service.tryAnswer("润滑油加注口在哪里", List.of(), null));
  }

  @Test
  void companyContextIsNotAnImplicitFilterForUnqualifiedQuestions() throws Exception {
    when(queries.companyContext()).thenReturn(Map.of("id", "71002", "name", "Test Company"));
    response("{\"operation\":\"CLARIFY\",\"message\":\"test\"}");
    service.tryAnswer("所有设备有哪些", List.of(), null);
    var body = ArgumentCaptor.forClass(Map.class);
    verify(gateway).post(anyString(), body.capture(), eq(false));
    assertFalse(body.getValue().toString().contains("71002"));
    clearInvocations(gateway);
    service.tryAnswer("我们公司的设备有哪些", List.of(), null);
    verify(gateway).post(anyString(), body.capture(), eq(false));
    assertTrue(body.getValue().toString().contains("71002"));
  }

  @Test
  void inventedAssociationIdsAreRejectedBeforeQuery() throws Exception {
    response(
        "{\"operation\":\"BUSINESS\",\"queries\":[{\"domain\":\"equipment\",\"operation\":\"COUNT\",\"where\":{\"field\":\"companyId\",\"op\":\"eq\",\"values\":[\"999999\"]}}]}");
    var result = (Map<?, ?>) service.tryAnswer("苏州公司的设备数量", List.of(), null);
    assertTrue(result.get("answer").toString().contains("不能猜测"));
    verify(queries, never()).query(any(), any(), anyInt(), anyInt());
  }
}
