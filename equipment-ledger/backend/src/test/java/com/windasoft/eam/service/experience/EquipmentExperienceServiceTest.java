package com.windasoft.eam.service.experience;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.experience.EquipmentAttentionItem;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.service.equipment.EquipmentInstalledSpareService;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.experience.EquipmentExperienceRepository;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.repair.RepairWorkflowService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EquipmentExperienceServiceTest {
  EquipmentExperienceRepository repo = mock(EquipmentExperienceRepository.class);
  AuthContext auth = mock(AuthContext.class);
  PermissionService permissions = mock(PermissionService.class);
  RepairWorkflowService repair = mock(RepairWorkflowService.class);
  MaintenanceService maintenance = mock(MaintenanceService.class);
  InspectionService inspection = mock(InspectionService.class);
  AnomalyService anomalies = mock(AnomalyService.class);
  EquipmentInstalledSpareService installedSpares = mock(EquipmentInstalledSpareService.class);
  EquipmentExperienceService service;
  Equipment equipment;

  @BeforeEach
  void setup() {
    when(auth.tenantId()).thenReturn(1L);
    when(auth.userId()).thenReturn(11L);
    equipment = new Equipment();
    equipment.setId(1L);
    equipment.setTenantId(1L);
    equipment.setEquipmentCode("EQ-TEST");
    equipment.setDeleted(0);
    when(repo.findEquipment(1L)).thenReturn(equipment);
    when(repo.userNames(eq(1L), anySet())).thenReturn(Map.of("11", "张工", "12", "李工"));
    when(anomalies.list(eq(""), eq(1L), anyInt(), eq(100), eq(false)))
        .thenReturn(Map.of("data", List.of(), "total", 0));
    service =
        new EquipmentExperienceService(
            repo, auth, permissions, repair, maintenance, inspection, anomalies, installedSpares);
  }

  Map<String, Object> task(String id, String status, String deadline) {
    return new HashMap<>(
        Map.of(
            "id",
            id,
            "code",
            "BY-" + id,
            "status",
            status,
            "planName",
            "保养计划",
            "expiresDate",
            deadline,
            "createdAt",
            "2026-10-15 10:00:00",
            "owners",
            List.of("11", "12"),
            "ownerId",
            ""));
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> result(Object value) {
    return (Map<String, Object>) value;
  }

  @SuppressWarnings("unchecked")
  List<EquipmentAttentionItem> items(Object value) {
    return (List<EquipmentAttentionItem>) value;
  }

  @Test
  void inclusiveDateDeadlineAndMissingTime() {
    var now = LocalDateTime.of(2026, 10, 15, 23, 59, 59);
    assertFalse(EquipmentExperienceService.overdue("2026-10-15", now));
    assertTrue(EquipmentExperienceService.overdue("2026-10-14", now));
    assertTrue(EquipmentExperienceService.overdue("2026-10-15T12:00:00", now));
    assertFalse(EquipmentExperienceService.overdue("", now));
    assertFalse(EquipmentExperienceService.overdue("invalid", now));
  }

  @Test
  void countsAreFullButPreviewCappedAndPagesStable() {
    var tasks = new ArrayList<Map<String, Object>>();
    for (int i = 1; i <= 8; i++)
      tasks.add(task(String.valueOf(i), "待执行", i == 1 ? "2000-01-01" : ""));
    tasks.add(task("99", "已完成", "2000-01-01"));
    tasks.add(task("98", "已作废", "2000-01-01"));
    when(repo.listMaintenanceIds(1L, 1L)).thenReturn(tasks);
    when(maintenance.tasks()).thenReturn(tasks);
    var overview = result(service.overview(1L));
    assertEquals(8, result(overview.get("counts")).get("maintenance"));
    assertEquals(5, items(overview.get("tasks")).size());
    var preview = items(overview.get("tasks"));
    assertTrue(preview.getFirst().overdue());
    assertEquals("张工、李工", preview.getFirst().owner());
    var page = result(service.attention(1L, "maintenance", 2, 5));
    assertEquals(8, page.get("total"));
    assertEquals(3, items(page.get("data")).size());
    assertTrue((boolean) page.get("accessible"));
    assertEquals(
        8,
        new HashSet<>(
                java.util.stream.Stream.concat(preview.stream(), items(page.get("data")).stream())
                    .map(EquipmentAttentionItem::id)
                    .toList())
            .size());
  }

  @Test
  void participantAccessDoesNotPretendEmptyUnauthorisedModulesAreZero() {
    var r = new RepairOrder();
    r.setId(7L);
    r.setOrderNo("wx-0007");
    r.setStatus("处理中");
    when(repo.findRepairRows(1L, "EQ-TEST")).thenReturn(List.of(r));
    var result = result(service.overview(1L));
    var access = result(result.get("access"));
    assertEquals(true, access.get("repair"));
    assertEquals(false, access.get("maintenance"));
    assertEquals(false, result(service.attention(1L, "inspection", 1, 10)).get("accessible"));
    assertEquals(true, result(service.attention(1L, "all", 1, 10)).get("accessible"));
  }

  @Test
  void forbiddenRepairExcludedFromCountsAndEvents() {
    var r = new RepairOrder();
    r.setId(7L);
    r.setStatus("处理中");
    when(repo.findRepairRows(1L, "EQ-TEST")).thenReturn(List.of(r));
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(repair).viewAllowed(r);
    assertEquals(0, result(result(service.overview(1L)).get("counts")).get("repair"));
    assertEquals(0, result(service.events(1L, "", 1, 10, "desc")).get("total"));
  }

  @Test
  void eventsDefaultAscendingAndOverviewCanRequestNewest() {
    var r = new RepairOrder();
    r.setId(7L);
    r.setOrderNo("wx-0007");
    r.setStatus("处理中");
    when(repo.findRepairRows(1L, "EQ-TEST")).thenReturn(List.of(r));
    when(repo.listTaskEvents(1L, 7L, "eam_repair_event", "order_id"))
        .thenAnswer(
            call ->
                List.of(
                    new HashMap<String, Object>(
                        Map.of("id", "1", "time", "2026-10-01 10:00:00", "action", "报修")),
                    new HashMap<String, Object>(
                        Map.of("id", "2", "time", "2026-10-02 10:00:00", "action", "接单"))));
    var asc = (List<?>) result(service.events(1L, "", 1, 10)).get("data");
    var desc = (List<?>) result(service.events(1L, "", 1, 1, "desc")).get("data");
    assertEquals("报修", result(asc.getFirst()).get("action"));
    assertEquals("接单", result(desc.getFirst()).get("action"));
    assertEquals(2, result(service.events(1L, "repair", 2, 1, "desc")).get("total"));
    assertEquals(0, result(service.events(1L, "maintenance", 1, 10, "desc")).get("total"));
  }

  @Test
  void invalidSortKindAndOtherTenantRejected() {
    assertThrows(IllegalArgumentException.class, () -> service.events(1L, "", 1, 10, "invalid"));
    assertThrows(IllegalArgumentException.class, () -> service.events(1L, "sql", 1, 10, "desc"));
    assertThrows(IllegalArgumentException.class, () -> service.attention(1L, "sql", 1, 10));
    equipment.setTenantId(2L);
    assertThrows(IllegalArgumentException.class, () -> service.overview(1L));
  }

  @Test
  void installedPreviewHasSameSourceAndNoMixedQuantityTotal() {
    var newer = new EquipmentInstalledSpareView();
    newer.setId("2");
    newer.setInstalledAt("2026-10-02");
    newer.setQuantity(new java.math.BigDecimal("2.00"));
    var older = new EquipmentInstalledSpareView();
    older.setId("1");
    older.setInstalledAt("2026-10-01");
    older.setQuantity(new java.math.BigDecimal("5.00"));
    when(installedSpares.list(1L, 1L)).thenReturn(List.of(older, newer));
    var data = result(service.overview(1L));
    assertEquals(2, data.get("installedTotal"));
    assertEquals("2", result(((List<?>) data.get("installed")).getFirst()).get("id"));
    verify(installedSpares).list(1L, 1L);
    assertFalse(data.containsKey("totalQuantity"));
  }

  @Test
  void allTaskKindsAndOpenAnomaliesAreCountedSeparately() {
    var r = new RepairOrder();
    r.setId(7L);
    r.setOrderNo("wx-0007");
    r.setStatus("处理中");
    var closed = new RepairOrder();
    closed.setId(8L);
    closed.setStatus("已完成");
    when(repo.findRepairRows(1L, "EQ-TEST")).thenReturn(List.of(r, closed));
    var pm = task("3", "待执行", "");
    when(repo.listMaintenanceIds(1L, 1L)).thenReturn(List.of(pm));
    when(maintenance.tasks()).thenReturn(List.of(pm));
    var check =
        new HashMap<String, Object>(
            Map.of(
                "id",
                "4",
                "code",
                "XJ-0004",
                "status",
                "执行中",
                "kind",
                "daily",
                "ownerId",
                "11",
                "expiresAt",
                "2000-01-01T12:00:00",
                "equipment",
                List.of(Map.of("id", "1"))));
    var other = new HashMap<>(check);
    other.put("id", "99");
    other.put("equipment", List.of(Map.of("id", "2")));
    var approved = new HashMap<>(check);
    approved.put("id", "45");
    approved.put("status", "已审核");
    var expired = new HashMap<>(check);
    expired.put("id", "46");
    expired.put("status", "过期作废");
    when(inspection.tasks()).thenReturn(List.of(check, other, approved, expired));
    var open =
        Map.<String, Object>of(
            "id",
            "5",
            "code",
            "YC-0005",
            "status",
            "待处理",
            "description",
            "轴承温度异常",
            "url",
            "/anomalies/5",
            "source_kind",
            "点巡检");
    var resolved = new HashMap<>(open);
    resolved.put("id", "6");
    resolved.put("status", "已消除");
    when(anomalies.list(eq(""), eq(1L), anyInt(), eq(100), eq(false)))
        .thenReturn(Map.of("data", List.of(open, resolved), "total", 2));
    var data = result(service.overview(1L));
    assertEquals(
        Map.of("repair", 1, "maintenance", 1, "inspection", 1, "anomaly", 1), data.get("counts"));
    assertEquals("inspection", items(data.get("tasks")).getFirst().kind());
    assertEquals("日常点检", items(data.get("tasks")).getFirst().source());
    assertEquals(1, result(service.attention(1L, "anomaly", 1, 10)).get("total"));
    assertEquals(
        "YC-0005",
        items(result(service.attention(1L, "anomaly", 1, 10)).get("data")).getFirst().code());
  }
}
