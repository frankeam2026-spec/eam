package com.windasoft.eam.service.knowledge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.knowledge.KnowledgeDocumentRepository;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
class KnowledgeRepairCaseServiceTest {
  @Test void unfinishedWorkOrderCannotBecomeVerifiedKnowledge(){var workflow=mock(RepairWorkflowService.class);var docs=mock(KnowledgeDocumentRepository.class);var order=new RepairOrder();order.setStatus("处理中");when(workflow.detail(1)).thenReturn(Map.of("order",order));var service=new KnowledgeRepairCaseService(workflow,docs,mock(KnowledgeContentWriter.class),mock(AuthContext.class),mock(PermissionService.class),mock(TransactionTemplate.class));assertThrows(IllegalArgumentException.class,()->service.draft(1));verifyNoInteractions(docs);}
}
