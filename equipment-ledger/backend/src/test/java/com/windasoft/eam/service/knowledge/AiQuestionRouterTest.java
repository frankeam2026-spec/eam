package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class AiQuestionRouterTest {
  final ObjectMapper json=new ObjectMapper();
  final AiGateway gateway=mock(AiGateway.class);
  final AiQuestionRouter router=new AiQuestionRouter(gateway,json);
  @Test void everydayCountQuestionsReachTheModelWithoutKeywordGating() throws Exception {
    when(gateway.post(eq("/chat/completions"),anyMap(),eq(false))).thenReturn(json.readTree("{\"choices\":[{\"message\":{\"content\":\"{\\\"operation\\\":\\\"EQUIPMENT_COUNT\\\",\\\"filters\\\":[],\\\"message\\\":\\\"\\\"}\"}}]}"));
    for(String q:List.of("我们有多少个设备","设备总数是多少","有几台设备"))assertEquals("EQUIPMENT_COUNT",router.interpret(q,List.of()).operation());
    verify(gateway,times(3)).post(eq("/chat/completions"),anyMap(),eq(false));
  }
  @Test void validatesFiltersAndRejectsInventedValuesOrUnknownFields() throws Exception {
    assertEquals("EQUIPMENT_LIST",router.parse("{\"operation\":\"EQUIPMENT_LIST\",\"filters\":[{\"field\":\"useState\",\"operator\":\"eq\",\"value\":\"闲置\"}],\"message\":\"\"}","查一下闲置设备").operation());
    for(String filter:List.of("{\"field\":\"amount\",\"operator\":\"eq\",\"value\":\"10\"}","{\"field\":\"name\",\"operator\":\"eq\",\"value\":\"虚构设备\"}","{\"field\":\"state\",\"operator\":\"contains\",\"value\":\"闲置\"}"))
      assertEquals("CLARIFY",router.parse("{\"operation\":\"EQUIPMENT_LIST\",\"filters\":["+filter+"],\"message\":\"\"}","查一下闲置设备10").operation());
    assertEquals("CLARIFY",router.parse("{\"operation\":\"SQL\",\"filters\":[],\"message\":\"\"}","任意请求").operation());
    assertEquals("CLARIFY",router.parse("{\"operation\":\"EQUIPMENT_COUNT\",\"filters\":[],\"message\":\"\",\"sql\":\"select *\"}","任意请求").operation());
  }
  @Test void invalidModelOutputClarifiesInsteadOfUsingTheVectorStore() throws Exception {
    when(gateway.post(eq("/chat/completions"),anyMap(),eq(false))).thenReturn(json.readTree("{\"choices\":[{\"message\":{\"content\":\"not json\"}}]}"));
    assertEquals("CLARIFY",router.interpret("查数据",List.of()).operation());
  }
}
