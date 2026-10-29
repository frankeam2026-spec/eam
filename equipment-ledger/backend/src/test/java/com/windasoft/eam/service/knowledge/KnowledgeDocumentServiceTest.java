package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.transaction.support.TransactionTemplate;

class KnowledgeDocumentServiceTest {
  @TempDir Path directory;
  final KnowledgeDocumentRepository repository=mock(KnowledgeDocumentRepository.class);
  final KnowledgePdfParser parser=mock(KnowledgePdfParser.class);
  final KnowledgeContentWriter writer=mock(KnowledgeContentWriter.class);
  final AuthContext auth=mock(AuthContext.class);
  final PermissionService permissions=mock(PermissionService.class);
  final TransactionTemplate tx=mock(TransactionTemplate.class);
  KnowledgeDocumentService service(){return new KnowledgeDocumentService(repository,parser,writer,auth,permissions,tx,directory.toString());}
  void grant(){
    when(auth.tenantId()).thenReturn(10L);
    when(permissions.scope("knowledge","编辑")).thenReturn(new PermissionScope(false,20L,Set.of(),false,Set.of(20L)));
    when(repository.companyExists(10,20)).thenReturn(true);
  }
  @Test void unauthorizedCompanyCannotParseOrStore() {
    grant();
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service().upload(21,"manual",new byte[]{1}));
    verifyNoInteractions(parser,writer,tx);
  }
  @Test void foreignTenantDocumentCannotExposePages(){
    grant();
    when(repository.document(10,99)).thenReturn(null);
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service().pages(99));
    verify(repository,never()).pages(anyLong(),anyLong());
  }
  @Test void draftPreviewRequiresEditorGrant(){
    grant();
    when(repository.document(10,99)).thenReturn(Map.of("company_id",20L));
    when(permissions.scope("knowledge","查看")).thenReturn(new PermissionScope(false,20L,Set.of(),false,Set.of(20L)));
    when(permissions.scope("knowledge","编辑")).thenThrow(new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertThrows(org.springframework.web.server.ResponseStatusException.class,()->service().pages(99));
    verify(repository,never()).pages(anyLong(),anyLong());
  }
  @Test void transactionFailureCleansNewFile() throws Exception {
    grant();
    when(parser.parse(any())).thenReturn(List.of(new KnowledgePdfParser.Page(1,"source",false,List.of("source"))));
    doThrow(new IllegalStateException("database unavailable")).when(tx).executeWithoutResult(any());
    assertThrows(IllegalStateException.class,()->service().upload(20,"manual","%PDF-test".getBytes(java.nio.charset.StandardCharsets.US_ASCII)));
    try(var files=Files.walk(directory)){assertEquals(0,files.filter(Files::isRegularFile).count());}
  }
}
