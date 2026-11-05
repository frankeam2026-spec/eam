package com.windasoft.eam.service.experience;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.experience.AnomalyRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.inspection.InspectionTaskService;
import com.windasoft.eam.service.inspection.InspectionListService;
import com.windasoft.eam.service.business.BusinessConfigService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.repair.RepairOrderService;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AnomalyInspectionCaptureTest {
  @Test
  void skippedInspectionAnswersDoNotCreateAnomalies() {
    var repository = mock(AnomalyRepository.class);
    var auth = mock(AuthContext.class);
    var inspectionTasks = mock(InspectionTaskService.class);
    var inspectionLists = mock(InspectionListService.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(repository.findSourceActor(10001L, 88001L, "提交检查结果", "点巡检"))
        .thenReturn(
            List.of(
                Map.of(
                    "actor_id",
                    73001L,
                    "actor_name",
                    "巡检员",
                    "created_at",
                    LocalDateTime.of(2026, 11, 2, 8, 30))));

    var items =
        List.of(
            Map.<String, Object>of(
                "id", "501/temperature", "equipmentId", "501", "name", "温度"),
            Map.<String, Object>of(
                "id", "502/temperature", "equipmentId", "502", "name", "温度"));
    var answers =
        List.of(
            Map.<String, Object>of(
                "id", "501/temperature", "value", "99", "abnormal", true, "skipped", true),
            // Older result snapshots may only contain the equipment-level skip list.
            Map.<String, Object>of(
                "id", "502/temperature", "value", "99", "abnormal", true));
    var taskView = new java.util.LinkedHashMap<String, Object>();
    taskView.put("items", items);
    taskView.put("result", Map.of("items", answers, "skippedEquipmentIds", List.of("502")));
    when(inspectionTasks.task(88001L)).thenReturn(taskView);

    var service =
        new AnomalyService(
            repository,
            auth,
            mock(PermissionService.class),
            new ObjectMapper(),
            mock(RepairOrderService.class),
            mock(RepairWorkflowService.class),
            mock(MaintenanceService.class),
            inspectionTasks,
            inspectionLists,
            mock(BusinessConfigService.class),
            mock(BaseDataReferenceService.class));

    service.capture("点巡检", 88001L);

    verify(inspectionTasks).task(88001L);
    verify(repository).findSourceActor(10001L, 88001L, "提交检查结果", "点巡检");
    verifyNoMoreInteractions(repository);
  }
}
