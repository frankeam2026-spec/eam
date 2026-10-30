package com.windasoft.eam.service.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import com.windasoft.eam.repository.inventory.RequisitionRepository;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.service.inspection.InspectionService;
import com.windasoft.eam.service.maintenance.MaintenanceService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RequisitionChoicesTest {
  JdbcTemplate db;
  InventoryService masters;
  PermissionService permissions;
  RequisitionService service;
  com.windasoft.eam.repository.inventory.RequisitionDraftRepository drafts;

  @BeforeEach
  void setup() {
    db = mock(JdbcTemplate.class);
    masters = mock(InventoryService.class);
    permissions = mock(PermissionService.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(10L);
    var repairs = mock(RepairOrderMapper.class);
    var order = new RepairOrder();
    order.setId(1L);
    order.setTenantId(10001L);
    order.setDepartmentOrgId(30L);
    order.setAssigneeUserId(10L);
    order.setDeleted(0);
    order.setStatus("处理中");
    order.setOrderNo("QA-REQUEST-1");
    order.setEquipmentName("测试设备");
    when(repairs.selectList(any())).thenReturn(List.of(order));
    when(repairs.selectById(1L)).thenReturn(order);
    when(permissions.companyOfOrganization(30L)).thenReturn(2L);
    when(permissions.menuActions()).thenReturn(Map.of("inventoryQuery", Set.of("查看")));
    when(masters.stockChoices())
        .thenReturn(Map.of("materials", List.of(), "warehouses", List.of()));
    when(db.queryForList(anyString(), eq(10001L)))
        .thenAnswer(
            c -> {
              String query = c.getArgument(0);
              return query.contains("inv_warehouse")
                  ? List.of(
                      Map.of("id", 100L, "company_id", 2L, "name", "申请仓库"),
                      Map.of("id", 200L, "company_id", 3L, "name", "其他公司仓库"))
                  : List.of();
            });
    when(db.queryForList(anyString(), eq(10001L), eq(1L)))
        .thenReturn(
            List.of(Map.of("department_org_id", 30L, "order_no", "QA-REQUEST-1", "status", "处理中")));
    when(masters.view(anyMap()))
        .thenAnswer(
            c -> {
              Map<String, Object> row = c.getArgument(0);
              return Map.of(
                  "id",
                  row.get("id").toString(),
                  "companyId",
                  row.get("company_id").toString(),
                  "name",
                  row.get("name"));
            });
    drafts = mock(com.windasoft.eam.repository.inventory.RequisitionDraftRepository.class);
    when(drafts.source(10001L, 1L, "维修", false))
        .thenReturn(
            new com.windasoft.eam.repository.inventory.RequisitionDraftRepository.SourceTask(
                1L, 30L, "QA-EQ", "QA-REQUEST-1", "处理中", "LEGACY", null));
    service =
        new RequisitionService(
            new RequisitionRepository(db, mock(NumberCounterMapper.class), repairs),
            masters,
            auth,
            permissions,
            mock(MaintenanceService.class),
            mock(InspectionService.class),
            new ObjectMapper(),
            mock(com.windasoft.eam.service.workflow.WorkflowRuntimeService.class),
            mock(InventoryListService.class),
            mock(InventoryMutex.class),
            mock(OutboundService.class),
            drafts,
            mock(InventorySelectorService.class));
  }

  @SuppressWarnings("unchecked")
  Map<String, Object> choices() {
    return (Map<String, Object>) service.choices();
  }

  @Test
  void requestWarehousesDoNotDependOnInventoryQueryView() {
    when(permissions.allowed("inventoryRequisition", "新增")).thenReturn(true);
    when(permissions.inventoryWarehouseAllowed("inventoryRequisition", 100L, "新增"))
        .thenReturn(true);
    var result = choices();
    assertEquals(
        List.of(Map.of("id", "100", "companyId", "2", "name", "申请仓库")), result.get("warehouses"));
    verify(permissions).inventoryWarehouseAllowed("inventoryRequisition", 100L, "新增");
  }

  @Test
  void noAuthorizedWarehouseKeepsTaskForClearEmptyFeedback() {
    when(permissions.allowed("inventoryRequisition", "新增")).thenReturn(true);
    var result = choices();
    assertEquals(List.of(), result.get("warehouses"));
    assertEquals(1, ((List<?>) result.get("tasks")).size());
  }

  @Test
  void personalTaskApplicantNeedsNoWarehouseManagementPermission() {
    when(permissions.menuActions()).thenReturn(Map.of("workspace", Set.of("查看")));
    var result = choices();
    assertEquals(
        List.of(Map.of("id", "100", "companyId", "2", "name", "申请仓库")), result.get("warehouses"));
    verify(permissions, never())
        .inventoryWarehouseAllowed(eq("inventoryWarehouse"), anyLong(), anyString());
  }

  @Test
  void legacyRepairWithoutDepartmentUsesExplicitEquipmentCompany() {
    when(permissions.allowed("inventoryRequisition", "新增")).thenReturn(true);
    when(permissions.inventoryWarehouseAllowed("inventoryRequisition", 100L, "新增"))
        .thenReturn(true);
    when(permissions.companyOfOrganization((Long) null)).thenReturn(null);
    when(drafts.source(10001L, 1L, "维修", false))
        .thenReturn(
            new com.windasoft.eam.repository.inventory.RequisitionDraftRepository.SourceTask(
                1L, null, "QA-EQ", "QA-REQUEST-1", "处理中", "LEGACY", null));
    when(drafts.equipmentCompany(10001L, "QA-EQ")).thenReturn(2L);
    when(db.queryForList(anyString(), eq(10001L), eq(1L)))
        .thenReturn(
            List.of(
                Map.of(
                    "department_org_id",
                    0L,
                    "equipment_code",
                    "QA-EQ",
                    "order_no",
                    "QA-REQUEST-1",
                    "status",
                    "处理中")));
    when(db.queryForList(anyString(), eq(10001L), eq("QA-EQ")))
        .thenReturn(List.of(Map.of("company_org_id", 2L)));
    assertEquals(1, ((List<?>) choices().get("warehouses")).size());
  }

  @Test
  void pagedMaterialsAllowTaskApplicantWithoutInventoryMenu() {
    when(masters.row("warehouses", 100L, false))
        .thenReturn(Map.of("id", 100L, "company_id", 2L, "enabled", true));
    when(db.queryForList(anyString(), eq(10001L), eq(1L)))
        .thenReturn(
            List.of(Map.of("department_org_id", 30L, "order_no", "QA-REQUEST-1", "status", "处理中")));
    when(db.queryForObject(
            contains("COUNT(*) FROM inv_material"), eq(Long.class), any(Object[].class)))
        .thenReturn(41L);
    when(db.queryForList(contains("FROM inv_material"), any(Object[].class))).thenReturn(List.of());
    var result = (Map<?, ?>) service.materials("motor", 2, 20, "维修", 1, 100);
    assertEquals(41L, ((Map<?, ?>) result.get("page")).get("total"));
    verify(permissions, never())
        .checkInventoryWarehouse(eq("inventoryWarehouse"), anyLong(), anyString());
    verify(db)
        .queryForList(
            contains("LIMIT ? OFFSET ?"),
            eq(10001L),
            eq("%motor%"),
            eq("%motor%"),
            eq("%motor%"),
            eq("%motor%"),
            eq(20),
            eq(20L));
  }

  @Test
  void materialPickerRejectsWarehouseFromAnotherCompany() {
    when(masters.row("warehouses", 200L, false))
        .thenReturn(Map.of("id", 200L, "company_id", 3L, "enabled", true));
    assertThrows(RuntimeException.class, () -> service.materials("", 1, 20, "维修", 1, 200));
  }

  @Test
  void materialPickerRejectsInvalidPage() {
    assertThrows(RuntimeException.class, () -> service.materials("", 0, 20, "维修", 1, 100));
  }
}
