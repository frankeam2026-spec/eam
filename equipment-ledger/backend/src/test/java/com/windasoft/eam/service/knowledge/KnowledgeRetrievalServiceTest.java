package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.KnowledgeSearchRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class KnowledgeRetrievalServiceTest {
  final KnowledgeSearchRepository repository=mock(KnowledgeSearchRepository.class);
  final KnowledgeEmbeddingClient embeddings=mock(KnowledgeEmbeddingClient.class);
  final KnowledgeVectorClient vectors=mock(KnowledgeVectorClient.class);
  final AuthContext auth=mock(AuthContext.class);
  final PermissionService permissions=mock(PermissionService.class);
  KnowledgeRetrievalService service(){
    when(auth.tenantId()).thenReturn(10L);when(embeddings.model()).thenReturn("model");
    when(permissions.scope("knowledge","查看")).thenReturn(new PermissionScope(false,20L,Set.of(),false,Set.of(20L)));
    return new KnowledgeRetrievalService(repository,embeddings,vectors,auth,permissions);
  }
  @Test void noAuthorizedVersionsNeverSendsQuestionToCloud() throws Exception {
    var s=service();assertTrue(s.retrieve("question").isEmpty());verify(embeddings,never()).embed(any());verifyNoInteractions(vectors);
  }
  @Test void vectorHitsRemovedBySqlCannotReachAnswer() throws Exception {
    var s=service();when(repository.versions(eq(10L),any(),eq("model"))).thenReturn(List.of(30L));
    when(embeddings.embed(any())).thenReturn(List.of(List.of(1.0)));
    when(vectors.search("model",List.of(1.0),10L,List.of(30L))).thenReturn(List.of(100L));
    when(repository.chunks(eq(10L),any(),eq(List.of(100L)))).thenReturn(List.of());
    assertTrue(s.retrieve("question").isEmpty());
  }
  @Test void revokedSourceFailsFinalRecheck(){
    var s=service();assertThrows(IllegalStateException.class,()->s.recheck(List.of(Map.of("chunkId","100"))));
  }
}
