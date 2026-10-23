package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.business.BusinessValueService;
import com.windasoft.eam.service.experience.MaterialCustodyService;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.server.ResponseStatusException;

class InspectionResultAmendmentTest {
  final ObjectMapper json = new ObjectMapper();
  final AuthContext auth = mock(AuthContext.class);
  final InspectionRepository repository = mock(InspectionRepository.class);
  final InspectionTaskAccessService access = mock(InspectionTaskAccessService.class);
  final InspectionTaskJournal journal = mock(InspectionTaskJournal.class);
  final MaterialCustodyService custody = mock(MaterialCustodyService.class);
  final BusinessValueService custom = mock(BusinessValueService.class);
  final ApplicationEventPublisher publisher = mock(ApplicationEventPublisher.class);
  final Map<String, Object> row = new HashMap<>();
  final InspectionTaskService service;

  InspectionResultAmendmentTest() {
    when(auth.tenantId()).thenReturn(1L);
    when(auth.userId()).thenReturn(20L);
    var user = new SystemUser();
    user.setDisplayName("修订人");
    when(auth.user()).thenReturn(user);
    row.putAll(Map.of("tenant_id", 1L, "id", 7L, "kind", "daily", "row_version", 3,
        "status", "已审核", "owner_id", 10L, "code", "DJ7"));
    row.put("snapshot", """
        {"equipment":[{"id":"1"}],"items":[{"id":"1/check","equipmentId":"1",
        "name":"温度","valueType":"数值","required":true,"lower":"0","upper":"10"}]}
        """);
    row.put("result", """
        {"items":[{"id":"1/check","value":"2"}],"startedAt":"2026-10-23T08:00:00",
        "finishedAt":"2026-10-23T08:10:00","scannedEquipmentIds":["1"],
        "review":{"decision":"accept","reviewerId":"30"},"attachments":[]}
        """);
    when(repository.loadTask(1L, 7L, true)).thenReturn(List.of(row));
    when(access.access(row, "查看")).thenReturn(true);
    when(access.canAmendResult(row)).thenReturn(true);
    service = new InspectionTaskService(repository, json, auth, access,
        mock(InspectionViewAssembler.class), mock(InspectionValidationService.class), journal,
        custom, mock(InventoryMutex.class), publisher, custody) {
      @Override public Map<String, Object> task(long id) { return Map.of("id", id); }
    };
  }

  Map<String, Object> body() {
    return new HashMap<>(Map.of("rowVersion", 3, "items",
        List.of(Map.of("id", "1/check", "value", "12")), "description", "校正读数",
        "attachments", List.of(), "finishedAt", "伪造时间", "review", Map.of("decision", "reject")));
  }

  @Test void preservesTerminalStatusOperatorTimingAndReviewWithoutResubmission() throws Exception {
    for (String status : List.of("已完成", "已审核")) {
      clearInvocations(repository, journal);
      row.put("status", status);
      service.action(7L, "amend-result", body());
      var saved = ArgumentCaptor.forClass(String.class);
      verify(repository).updateTask(eq(status), eq(10L), anyString(), saved.capture(), eq(0), eq(1L), eq(7L));
      var result = json.readTree(saved.getValue());
      assertEquals("12", result.at("/items/0/value").asText());
      assertTrue(result.at("/items/0/abnormal").asBoolean());
      assertEquals("2026-10-23T08:10:00", result.get("finishedAt").asText());
      assertEquals("2026-10-23T08:00:00", result.get("startedAt").asText());
      assertEquals("accept", result.at("/review/decision").asText());
      assertEquals("30", result.at("/review/reviewerId").asText());
      verify(repository).updateEquipmentHistoryResult(1L, 1L, "DJ7", "12");
      var audit = ArgumentCaptor.forClass(Object.class);
      verify(journal).event(eq(1L), eq(7L), eq("修改检查结果"), eq(20L), eq("修订人"), audit.capture());
      assertTrue(((Map<?, ?>) audit.getValue()).containsKey("修改前"));
      assertTrue(((Map<?, ?>) audit.getValue()).containsKey("修改后"));
      verify(journal, never()).closeTodos(anyLong(), anyLong());
    }
    verifyNoInteractions(custody, custom, publisher);
  }

  @Test void rejectsMissingPermissionStaleVersionAndIncompleteAnswers() {
    when(access.canAmendResult(row)).thenReturn(false);
    assertEquals(403, assertThrows(ResponseStatusException.class,
        () -> service.action(7L, "amend-result", body())).getStatusCode().value());
    when(access.canAmendResult(row)).thenReturn(true);
    var stale = body();
    stale.put("rowVersion", 2);
    assertEquals(409, assertThrows(ResponseStatusException.class,
        () -> service.action(7L, "amend-result", stale)).getStatusCode().value());
    var missing = body();
    missing.put("items", List.of());
    assertThrows(IllegalArgumentException.class, () -> service.action(7L, "amend-result", missing));
    verify(repository, never()).updateTask(any(), any(), anyString(), anyString(), any(), anyLong(), anyLong());
  }

  @Test void amendmentRequiresItsOwnPermissionAndDataScopeOnlyForDailyTerminalTasks() {
    var permissions = mock(PermissionService.class);
    when(permissions.inspection("daily", "修改结果"))
        .thenReturn(new PermissionScope(false, null, Set.of(21L), false));
    var policy = new InspectionTaskAccessService(json, auth, permissions);
    row.put("department_id", 21L);
    assertTrue(policy.canAmendResult(row));
    row.put("department_id", 22L);
    assertFalse(policy.canAmendResult(row));
    row.put("department_id", 21L);
    for (String state : List.of("执行中", "待审核", "审核不通过", "已作废", "已免检")) {
      row.put("status", state);
      assertFalse(policy.canAmendResult(row), state);
    }
    row.put("status", "已完成");
    row.put("kind", "periodic");
    assertFalse(policy.canAmendResult(row));
    row.put("kind", "daily");
    row.put("tenant_id", 2L);
    assertFalse(policy.canAmendResult(row));
    row.put("tenant_id", 1L);
    when(permissions.inspection("daily", "修改结果"))
        .thenThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN));
    assertFalse(policy.canAmendResult(row));
    verify(permissions, never()).inspection(anyString(), eq("执行"));
  }
}
