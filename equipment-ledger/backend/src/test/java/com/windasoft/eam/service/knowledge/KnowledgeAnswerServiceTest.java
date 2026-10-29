package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.junit.jupiter.api.Test;

class KnowledgeAnswerServiceTest {
  final KnowledgeRetrievalService retrieval=mock(KnowledgeRetrievalService.class);
  final KnowledgeAnswerService service=new KnowledgeAnswerService(retrieval,new ObjectMapper(),"","","test",true);
  final List<Map<String,Object>> sources=List.of(Map.of("chunkId","100","title","Manual","pageNo",2));
  @Test void equipmentAndUnknownIntentsNeverFallThroughToVectorRetrieval() throws Exception {
    var router=mock(AiQuestionRouter.class);var equipment=mock(AiEquipmentQueryService.class);
    org.springframework.test.util.ReflectionTestUtils.setField(service,"router",router);
    org.springframework.test.util.ReflectionTestUtils.setField(service,"equipmentQueries",equipment);
    var intent=new AiQuestionRouter.Intent("EQUIPMENT_COUNT",List.of(),"");
    when(router.interpret(anyString(),anyList())).thenReturn(intent);
    when(equipment.answer(intent,null)).thenReturn(Map.of("answer","共153台","citations",List.of()));
    assertEquals("共153台",((Map<?,?>)service.ask("我们有多少个设备")).get("answer"));verifyNoInteractions(retrieval);
    when(router.interpret(anyString(),anyList())).thenReturn(AiQuestionRouter.clarify());
    service.ask("不明确的数据请求");verifyNoInteractions(retrieval);
  }
  @Test void unknownCitationCannotBecomeAValidSource(){
    assertThrows(IllegalStateException.class,()->service.validate("{\"answer\":\"check\",\"citationIds\":[\"999\"]}",sources));
  }
  @Test void uncitedAnswerFallsBackInsteadOfPresentingModelGuesses() throws Exception {
    var answer=(Map<?,?>)service.validate("{\"answer\":\"invented location\",\"citationIds\":[]}",sources);
    assertFalse(answer.get("answer").toString().contains("invented"));assertEquals(List.of(),answer.get("citations"));
  }
  @Test void citationsAreServerMetadataAndDeduplicated() throws Exception {
    var answer=(Map<?,?>)service.validate("{\"answer\":\"check\",\"citationIds\":[\"100\",\"100\"]}",sources);
    assertEquals(sources,answer.get("citations"));
  }
  @Test void noPublishedSourcesNeedsNoModelConnection() throws Exception {
    when(retrieval.retrieve("question")).thenReturn(List.of());
    assertTrue(((Map<?,?>)service.ask("question")).get("citations") instanceof List<?>);
  }
}
