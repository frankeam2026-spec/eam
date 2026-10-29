package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.*;

class KnowledgeConversationServiceTest {
  final KnowledgeConversationRepository repo=mock(KnowledgeConversationRepository.class);
  final KnowledgeSearchRepository sources=mock(KnowledgeSearchRepository.class);
  final KnowledgeAnswerService answers=mock(KnowledgeAnswerService.class);
  final AuthContext auth=mock(AuthContext.class);
  final PermissionService permissions=mock(PermissionService.class);
  final TransactionTemplate tx=mock(TransactionTemplate.class);
  final String key="00000000-0000-0000-0000-000000000001";
  KnowledgeConversationService service(){
    when(repo.equipment(anyLong(),anyLong(),anyLong())).thenReturn(null);
    when(repo.existing(anyLong(),anyLong(),anyString())).thenReturn(null);
    when(auth.tenantId()).thenReturn(10L);when(auth.userId()).thenReturn(20L);
    when(permissions.scope("knowledge","查看")).thenReturn(new PermissionScope(true,null,Set.of(),false));
    when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    return new KnowledgeConversationService(repo,sources,answers,auth,permissions,tx);
  }
  @Test void foreignUserCannotReadHistory(){
    var service=service();assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.history(30));
    verify(repo,never()).exchanges(anyLong(),anyLong());
  }
  @Test void duplicateSuccessfulRequestDoesNotCallModelAgain() throws Exception {
    var service=service();when(repo.owner(10,20,30,true)).thenReturn(true);
    when(repo.existing(10,30,key)).thenReturn(Map.of("id","40","question","question","answer","answer","status","SUCCEEDED"));
    assertEquals("answer",((Map<?,?>)service.ask(30,key,"question")).get("answer"));verifyNoInteractions(answers);
  }
  @Test void duplicateKeyWithDifferentQuestionIsRejected(){
    var service=service();when(repo.owner(10,20,30,true)).thenReturn(true);
    when(repo.existing(10,30,key)).thenReturn(Map.of("question","original","status","SUCCEEDED"));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.ask(30,key,"other"));verifyNoInteractions(answers);
  }
  @Test void historyHidesDerivedAnswerAfterSourceRevocation(){
    var service=service();when(repo.owner(10,20,30,false)).thenReturn(true);
    when(repo.exchanges(10,30,1,50)).thenReturn(List.of(Map.of("id","40","question","question","answer","sensitive answer","status","SUCCEEDED")));
    when(repo.citations(10,40)).thenReturn(List.of(99L));
    var result=(Map<?,?>)service.history(30);var turn=(Map<?,?>)((List<?>)result.get("data")).getFirst();
    assertEquals(true,turn.get("restricted"));assertFalse(turn.get("answer").toString().contains("sensitive"));
  }
  @Test void failedTurnIsMarkedAndNewRequestCanRetry() throws Exception {
    var service=service();when(repo.owner(10,20,30,true)).thenReturn(true);
    when(answers.ask(eq("question"),anyList(),isNull())).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"小卓尚未启用"));
    var failure=assertThrows(KnowledgeConversationService.TurnFailed.class,()->service.ask(30,key,"question"));
    verify(repo).fail(eq(10L),anyLong());
    var event=KnowledgeAnswerExecution.failure(failure);assertEquals("AI_TURN_FAILED",event.get("code"));assertEquals(503,event.get("status"));assertEquals("小卓尚未启用",event.get("message"));
    when(answers.ask(eq("question"),anyList(),isNull())).thenReturn(Map.of("answer","answer","citations",List.of()));
    assertEquals("answer",((Map<?,?>)service.ask(30,"00000000-0000-0000-0000-000000000002","question")).get("answer"));
  }
  @Test void transportFailureDoesNotAuthorizeANewBillableRequest(){
    var event=KnowledgeAnswerExecution.failure(new java.io.IOException("network unavailable"));
    assertEquals("REQUEST_ERROR",event.get("code"));
  }
  @Test void businessHistoryDoesNotPersistFiguresOutsideTheirOriginalDataScope() throws Exception {
    var service=service();when(repo.owner(10,20,30,true)).thenReturn(true);
    doAnswer(i->{((java.util.function.Consumer<TransactionStatus>)i.getArgument(0)).accept(mock(TransactionStatus.class));return null;}).when(tx).executeWithoutResult(any());
    when(answers.ask(eq("question"),anyList(),isNull())).thenReturn(Map.of("answer","设备数量987654","citations",List.of(),"businessQueries",List.of()));
    var result=(Map<?,?>)service.ask(30,key,"question");assertTrue(result.get("answer").toString().contains("987654"));
    verify(repo).finish(eq(10L),anyLong(),argThat(text->!text.contains("987654")&&text.contains("当前权限")),eq(List.of()));
  }

}
