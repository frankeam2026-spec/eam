package com.windasoft.eam.service.knowledge;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.*;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AiEquipmentQueryServiceTest {
  final EquipmentQueryService equipment = mock(EquipmentQueryService.class);
  final PermissionService permissions = mock(PermissionService.class);
  final ObjectMapper json = new ObjectMapper();
  final AiEquipmentQueryService service = new AiEquipmentQueryService(equipment, permissions, json);

  @Test
  void countComesFromTheDatabaseTotalRatherThanPageLength() throws Exception {
    when(equipment.list(any()))
        .thenReturn(new EquipmentPage(List.of(), new EquipmentPage.PageInfo(1, 20, 153)));
    var result =
        (Map<?, ?>)
            service.answer(new AiQuestionRouter.Intent("EQUIPMENT_COUNT", List.of(), ""), null);
    assertTrue(result.get("answer").toString().contains("153 台"));
    assertFalse(result.containsKey("equipmentRecords"));
    verify(permissions).require("equipment", "查看");
    verify(permissions).require("knowledge", "查看");
  }

  @Test
  void translatesChoiceEqualityAndKeepsDatabasePagination() throws Exception {
    when(equipment.list(any()))
        .thenReturn(new EquipmentPage(List.of(), new EquipmentPage.PageInfo(2, 50, 100)));
    var filters =
        List.of(
            new AiQuestionRouter.Filter("useState", "eq", "闲置"),
            new AiQuestionRouter.Filter("name", "contains", "冲压机"));
    var result = service.query(filters, null, 2, 50);
    var captured = ArgumentCaptor.forClass(EquipmentListQuery.class);
    verify(equipment).list(captured.capture());
    assertEquals(2, captured.getValue().page());
    assertEquals(50, captured.getValue().size());
    var advanced = json.valueToTree(captured.getValue().conditions());
    assertEquals("in", advanced.get(0).path("operator").asText());
    assertEquals("contains", advanced.get(1).path("operator").asText());
    assertEquals(100L, result.get("total"));
  }

  @Test
  void permissionDenialStopsBeforeBusinessReads() {
    doThrow(new IllegalStateException("denied")).when(permissions).require("equipment", "查看");
    assertThrows(IllegalStateException.class, () -> service.query(List.of(), null, 1, 20));
    verifyNoInteractions(equipment);
  }

  @Test
  void rejectsUnsupportedFieldsAndPageSizes() {
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.query(List.of(new AiQuestionRouter.Filter("amount", "eq", "10")), null, 1, 20));
    assertThrows(IllegalArgumentException.class, () -> service.query(List.of(), null, 1, 500));
    verifyNoInteractions(equipment);
  }

  @Test
  void selectedEquipmentIntersectsRatherThanReplacesTheQuery() throws Exception {
    var device = json.convertValue(Map.of("id", "1", "code", "P-103"), EquipmentView.class);
    when(equipment.get(1L)).thenReturn(device);
    when(equipment.list(any()))
        .thenReturn(new EquipmentPage(List.of(), new EquipmentPage.PageInfo(1, 20, 0)));
    service.query(List.of(new AiQuestionRouter.Filter("state", "eq", "停用")), 1L, 1, 20);
    var captured = ArgumentCaptor.forClass(EquipmentListQuery.class);
    verify(equipment).list(captured.capture());
    var filters = json.valueToTree(captured.getValue().conditions());
    assertEquals(2, filters.size());
    assertEquals("P-103", filters.get(1).path("values").get(0).asText());
  }
}
