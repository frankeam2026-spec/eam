package com.windasoft.eam.service.knowledge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.*;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;

class KnowledgeIndexServiceTest {
  final KnowledgeDocumentRepository docs=mock(KnowledgeDocumentRepository.class);
  final KnowledgeIndexRepository jobs=mock(KnowledgeIndexRepository.class);
  final KnowledgeEmbeddingClient embeddings=mock(KnowledgeEmbeddingClient.class);
  final KnowledgeVectorClient vectors=mock(KnowledgeVectorClient.class);
  final AuthContext auth=mock(AuthContext.class);
  final PermissionService permissions=mock(PermissionService.class);
  final TransactionTemplate tx=mock(TransactionTemplate.class);
  KnowledgeIndexService service(){
    when(auth.tenantId()).thenReturn(10L);
    when(permissions.scope(eq("knowledge"),anyString())).thenReturn(new PermissionScope(true,null,Set.of(),false));
    when(docs.document(10,1)).thenReturn(Map.of("company_id",20L,"row_version",0L));
    when(docs.companyExists(10,20)).thenReturn(true);
    when(embeddings.model()).thenReturn("test");
    when(tx.execute(any())).thenAnswer(i->((TransactionCallback<?>)i.getArgument(0)).doInTransaction(mock(TransactionStatus.class)));
    return new KnowledgeIndexService(docs,jobs,embeddings,vectors,auth,permissions,tx);
  }
  @Test void cannotPublishUnindexedContent(){
    var service=service();when(jobs.latest(10,1)).thenReturn(Map.of("id",2L));
    assertThrows(IllegalArgumentException.class,()->service.publish(1,0));
    verify(jobs,never()).publish(anyLong(),anyLong(),anyLong(),anyLong());
  }
  @Test void versionConflictStopsBeforeModelCalls() throws Exception {
    var service=service();
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service.index(1,9));
    verifyNoInteractions(vectors);verify(embeddings,never()).embed(any());
  }
  @Test void ocrRequiredStopsBeforeIndexClaim(){
    var service=service();when(jobs.latest(10,1)).thenReturn(Map.of("id",2L,"parse_status","NEEDS_OCR"));
    assertThrows(IllegalArgumentException.class,()->service.index(1,0));
    verify(jobs,never()).claim(anyLong(),anyLong(),anyLong(),anyString());
  }
}
