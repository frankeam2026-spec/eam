package com.windasoft.eam.service.knowledge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;
class AiRepairServiceTest {
  @Test void recordingRequiresCurrentWorkOrderProcessingAccess(){
    var gateway=mock(AiGateway.class);var workflow=mock(RepairWorkflowService.class);var permission=mock(PermissionService.class);var retrieval=mock(KnowledgeRetrievalService.class);
    when(workflow.detail(1)).thenReturn(Map.of("canProcess",false,"order",new RepairOrder()));
    var service=new AiRepairService(gateway,workflow,permission,retrieval,new ObjectMapper());
    assertThrows(IllegalArgumentException.class,()->service.assist("RECORD",1L,"fixed it",null));verifyNoInteractions(gateway,retrieval);
  }
  @Test void invalidPhotoCannotBeForwardedToModel(){
    var gateway=mock(AiGateway.class);var service=new AiRepairService(gateway,mock(RepairWorkflowService.class),mock(PermissionService.class),mock(KnowledgeRetrievalService.class),new ObjectMapper());
    assertThrows(IllegalArgumentException.class,()->service.assist("REPORT",null,"noise",new byte[]{1,2,3}));verifyNoInteractions(gateway);
  }
  @Test void unknownAudioFormatCannotBeForwardedToCloud(){
    var gateway=mock(AiGateway.class);var service=new AiRepairService(gateway,mock(RepairWorkflowService.class),mock(PermissionService.class),mock(KnowledgeRetrievalService.class),new ObjectMapper());
    assertThrows(IllegalArgumentException.class,()->service.transcribe(new byte[20]));verifyNoInteractions(gateway);
  }
  @Test void diagnosisUsesNewSymptomsAndRechecksTheSameEquipment() throws Exception {
    var gateway=mock(AiGateway.class);var workflow=mock(RepairWorkflowService.class);var retrieval=mock(KnowledgeRetrievalService.class);var json=new ObjectMapper();
    var order=new RepairOrder();order.setEquipmentId(42L);order.setDescription("old symptom");
    when(workflow.detail(1)).thenReturn(Map.of("order",order,"model","P-103"));
    var sources=List.<Map<String,Object>>of(Map.of("chunkId","7"));
    when(retrieval.retrieve(anyString(),eq(42L))).thenReturn(sources);
    when(gateway.post(anyString(),anyMap(),eq("CHAT"),anyDouble())).thenReturn(json.readTree("{\"choices\":[{\"message\":{\"content\":\"{\\\"text\\\":\\\"Verify the port\\\",\\\"missingInfo\\\":[]}\"}}]}"));
    var service=new AiRepairService(gateway,workflow,mock(PermissionService.class),retrieval,json);
    service.assist("DIAGNOSE",1L,"new vibration",null);
    verify(retrieval).retrieve(contains("new vibration"),eq(42L));verify(retrieval).recheck(sources,42L);verify(workflow,times(2)).detail(1);
  }

}
