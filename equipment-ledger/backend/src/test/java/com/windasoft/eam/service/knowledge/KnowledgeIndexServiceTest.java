package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.knowledge.KnowledgeDocument;
import com.windasoft.eam.entity.knowledge.KnowledgeVersion;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.knowledge.KnowledgeChunkRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeEquipmentRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeIndexRepository;
import com.windasoft.eam.repository.knowledge.KnowledgeVersionRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

class KnowledgeIndexServiceTest {
  final KnowledgeDocumentRepository docs = mock(KnowledgeDocumentRepository.class);
  final KnowledgeVersionRepository versions = mock(KnowledgeVersionRepository.class);
  final KnowledgeIndexRepository jobs = mock(KnowledgeIndexRepository.class);
  final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
  final KnowledgeEmbeddingClient embeddings = mock(KnowledgeEmbeddingClient.class);
  final KnowledgeVectorClient vectors = mock(KnowledgeVectorClient.class);
  final AuthContext auth = mock(AuthContext.class);
  final PermissionService permissions = mock(PermissionService.class);
  final TransactionTemplate tx = mock(TransactionTemplate.class);

  KnowledgeIndexService service() {
    when(auth.tenantId()).thenReturn(10L);
    when(permissions.scope(eq("knowledge"), anyString()))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    var d = new KnowledgeDocument();
    d.setCompanyId(20L);
    d.setRowVersion(0L);
    d.setAppliesToAll(true);
    when(docs.document(10, 1)).thenReturn(d);
    var org = new OrgUnit();
    org.setStatus(1);
    when(organizations.company(10, 20L)).thenReturn(org);
    when(embeddings.model()).thenReturn("test");
    when(tx.execute(any()))
        .thenAnswer(
            i ->
                ((TransactionCallback<?>) i.getArgument(0))
                    .doInTransaction(mock(TransactionStatus.class)));
    return new KnowledgeIndexService(
        docs,
        versions,
        mock(KnowledgeEquipmentRepository.class),
        jobs,
        mock(KnowledgeChunkRepository.class),
        organizations,
        embeddings,
        vectors,
        auth,
        permissions,
        tx);
  }

  KnowledgeVersion version(String state) {
    var v = new KnowledgeVersion();
    v.setId(2L);
    v.setParseStatus(state);
    return v;
  }

  @Test
  void cannotPublishUnindexedContent() {
    var service = service();
    when(versions.latest(10, 1)).thenReturn(version("READY"));
    var failure = assertThrows(IllegalArgumentException.class, () -> service.publish(1, 0));
    assertEquals("当前模型的向量索引尚未完成", failure.getMessage());
    verify(docs, never()).publish(anyLong(), anyLong(), anyLong(), anyLong());
  }

  @Test
  void versionConflictStopsBeforeModelCalls() throws Exception {
    var service = service();
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class, () -> service.index(1, 9));
    verifyNoInteractions(vectors);
    verify(embeddings, never()).embed(any());
  }

  @Test
  void ocrRequiredStopsBeforeIndexClaim() {
    var service = service();
    when(versions.latest(10, 1)).thenReturn(version("NEEDS_OCR"));
    assertThrows(IllegalArgumentException.class, () -> service.index(1, 0));
    verify(jobs, never()).claim(anyLong(), anyLong(), anyLong(), anyString());
  }
}
