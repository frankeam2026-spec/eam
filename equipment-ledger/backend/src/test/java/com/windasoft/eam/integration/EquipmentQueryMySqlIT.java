package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.*;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.repair.RepairQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentOwnerRepository;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.*;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.context.bean.override.mockito.*;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_EQUIPMENT_QUERY_TEST_URL", matches = ".+")
@Transactional
class EquipmentQueryMySqlIT {
  static final long FIRST = 990300001L, CATEGORY = 990399001L;
  static final String FIELD = "e053f411-e811-45a8-bf7b-f31a5dc36399";

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry p) {
    String url = System.getenv("EAM_EQUIPMENT_QUERY_TEST_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://127\\.0\\.0\\.1:3308/eam_equipment_query_test_20261030(\\?.*)?"))
      throw new IllegalStateException("Requires isolated equipment query database");
    p.add("spring.datasource.url", () -> url);
    p.add("spring.datasource.username", () -> "root");
    p.add("spring.datasource.password", () -> "");
    p.add("eam.notification.secret-key", () -> Base64.getEncoder().encodeToString(new byte[32]));
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @MockitoBean com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler knowledgeScheduler;
  @MockitoSpyBean EquipmentQueryRepository repository;
  @MockitoSpyBean EquipmentOwnerRepository owners;
  @MockitoSpyBean com.windasoft.eam.repository.basedata.BaseItemRepository baseItems;
  @Autowired EquipmentQueryService service;
  @Autowired RepairQueryRepository repairQueries;
  @Autowired JdbcTemplate db;
  @Autowired ObjectMapper json;

  @BeforeEach
  void fixture() {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    doAnswer(
            call -> {
              LambdaQueryWrapper<Equipment> q = call.getArgument(0);
              q.ge(Equipment::getId, FIRST).lt(Equipment::getId, FIRST + 24);
              return null;
            })
        .when(permissions)
        .filterEquipment(any());
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name)"
            + " VALUES(?,10001,'CATEGORY','QA-QUERY-CAT','当前分类')",
        CATEGORY);
    for (int i = 0; i < 24; i++)
      db.update(
          "INSERT INTO"
              + " eam_equipment(id,tenant_id,equipment_code,name,category,category_id,company,department,company_org_id,department_org_id,purchase_date,acquired_at,created_at,acquired_amount)"
              + " VALUES(?,10001,?,?,'旧分类',?,'测试公司',?,71002,?,'2026-10-04','2025-01-01','2026-10-01"
              + " 00:00:00',?)",
          FIRST + i,
          String.format("QA-QUERY-%02d", i),
          i == 0 ? "含%_符号" : "设备" + i,
          CATEGORY,
          i < 12 ? "A" : "B",
          i < 12 ? 71003 : 71004,
          i);
  }

  EquipmentListQuery query(Map<String, ?> input) {
    return EquipmentQueryParameters.read(input, json);
  }

  List<String> ids(EquipmentPage p) {
    return p.data().stream().map(EquipmentView::id).toList();
  }

  String export(Map<String, ?> input) {
    var text = new StringWriter();
    service.export(
        EquipmentQueryParameters.export(input, json),
        List.of("code", "acquiredAt"),
        new PrintWriter(text));
    return text.toString();
  }

  @Test
  void stableOrderUsesSqlCountAndPagination() {
    var first = service.list(query(Map.of()));
    var second = service.list(query(Map.of("page", "2")));
    assertEquals(24, first.page().total());
    assertEquals(20, first.data().size());
    assertEquals(4, second.data().size());
    assertEquals(String.valueOf(FIRST), ids(first).getFirst());
    assertEquals(String.valueOf(FIRST + 20), ids(second).getFirst());
    assertTrue(Collections.disjoint(ids(first), ids(second)));
    assertEquals(ids(first), ids(service.list(query(Map.of()))));
    var desc = service.list(query(Map.of("sortBy", "equipmentCode", "direction", "desc")));
    assertEquals("QA-QUERY-23", desc.data().getFirst().code());
  }

  @Test
  void filtersFollowCurrentDictionaryAndDisplayedPurchaseDate() {
    var input =
        Map.of("category", "当前分类", "acquiredFrom", "2026-10-04", "acquiredTo", "2026-10-04");
    var page = service.list(query(input));
    assertEquals(24, page.page().total());
    assertEquals("当前分类", page.data().getFirst().category());
    assertEquals("2026-10-04", page.data().getFirst().acquiredAt());
    assertEquals(0, service.list(query(Map.of("category", "旧分类"))).page().total());
    assertEquals(25, export(input).lines().count());
    assertEquals(1, export(Map.of("acquiredTo", "2026-10-03")).lines().count());
  }

  @Test
  void ordinaryAndAdvancedFiltersIntersectAndKeywordEscapesWildcards() {
    assertEquals(
        0,
        service
            .list(
                query(
                    Map.of(
                        "department",
                        "A",
                        "advanced",
                        "[{\"field\":\"department\",\"operator\":\"in\",\"values\":[\"B\"]}]")))
            .page()
            .total());
    assertEquals(1, service.list(query(Map.of("keyword", "%_"))).page().total());
  }

  @Test
  void permissionsIntersectUserScopeAndExportRightsAndKeepTenantIsolation() {
    doAnswer(
            call -> {
              LambdaQueryWrapper<Equipment> q = call.getArgument(0);
              q.ge(Equipment::getId, FIRST).eq(Equipment::getDepartmentOrgId, 71003L);
              return null;
            })
        .when(permissions)
        .filterEquipment(any());
    assertEquals(
        0,
        service
            .list(query(Map.of("scopeType", "organization", "scopeIds", "71004")))
            .page()
            .total());
    assertEquals(12, service.list(query(Map.of())).page().total());
    doAnswer(
            call -> {
              LambdaQueryWrapper<Equipment> q = call.getArgument(0);
              q.lt(Equipment::getId, FIRST + 2);
              return null;
            })
        .when(permissions)
        .filterEquipment(any(), eq("导出"));
    assertEquals(3, export(Map.of()).lines().count());
    db.update(
        "INSERT INTO eam_business_schema(tenant_id,object_key,draft,published)"
            + " VALUES(990999,'equipment','{\"fields\":[]}','{\"fields\":[]}')");
    when(auth.tenantId()).thenReturn(990999L);
    assertEquals(0, service.list(query(Map.of())).page().total());
  }

  @Test
  void batchRelationsPreserveDetailAndAvoidPerRowLookups() {
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,company_org_id)"
            + " VALUES(990399002,10001,'LOCATION','QA-ROOT','厂区',71002)");
    db.update(
        "INSERT INTO eam_base_item(id,tenant_id,item_type,code,name,company_org_id,parent_id)"
            + " VALUES(990399003,10001,'LOCATION','QA-LEAF','车间',71002,990399002)");
    db.update(
        "UPDATE eam_equipment SET function_location_id=990399003,function_location='旧位置' WHERE"
            + " id=?",
        FIRST);
    db.update(
        "INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name)"
            + " VALUES(10001,?,73001,'负责人')",
        FIRST);
    db.update(
        "INSERT INTO eam_equipment_child(tenant_id,parent_id,child_id,sort_no) VALUES(10001,?,?,0)",
        FIRST,
        FIRST + 1);
    db.update(
        "INSERT INTO"
            + " eam_equipment_relation_legacy(tenant_id,equipment_id,kind,source_index,snapshot_json,reason)"
            + " VALUES(10001,?,'bom',0,'{\"code\":\"QA-OLD\"}','test')",
        FIRST);
    clearInvocations(repository, owners, baseItems);
    var row = service.list(query(Map.of())).data().getFirst();
    verify(owners, never()).list(anyLong(), anyLong());
    verify(owners).listByEquipmentIds(eq(10001L), argThat(ids -> ids.size() == 20));
    verify(baseItems, never()).findById(any());
    verify(repository, never()).children(anyLong(), anyLong());
    verify(repository, never()).bom(anyLong(), anyLong());
    verify(repository).pageRelations(eq(10001L), argThat(rows -> rows.size() == 20));
    var detail = service.get(FIRST);
    assertEquals(detail, row);
    assertEquals("厂区 · 车间", row.functionLocation());
  }

  @Test
  void repairHistoryUsesEquipmentIdAfterCodeChangesAndOnlyFallsBackForLegacyRows() {
    insertRepair(990310001L, "QA-REPAIR-OLD-CODE", "2026-10-30 10:00:00.000", FIRST);
    insertRepair(990310002L, "QA-REPAIR-NEW-CODE", "2026-10-31 10:00:00.000", null);
    insertRepair(990310003L, "QA-REPAIR-NEW-CODE", "2026-10-31 11:00:00.000", FIRST + 1);
    insertRepair(990310004L, "QA-REPAIR-NEW-CODE", "2026-10-31 10:00:00.000", FIRST);

    var rows = repairQueries.historyByEquipment(10001L, FIRST, "QA-REPAIR-NEW-CODE");

    assertEquals(List.of(990310004L, 990310002L, 990310001L),
        rows.stream().map(com.windasoft.eam.entity.repair.RepairOrder::getId).toList());
  }

  private void insertRepair(long id, String code, String reportedAt, Long equipmentId) {
    db.update(
        "INSERT INTO eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,"
            + "department,fault_type,severity,description,reporter,status,reported_at,equipment_id)"
            + " VALUES(?,10001,?,?,?,'测试部门','机械故障','一般','测试维修','测试人员','已完成',?,?)",
        id, "QA-REPAIR-" + id, code, "测试设备", java.sql.Timestamp.valueOf(reportedAt), equipmentId);
  }

  @Test
  void customNumericOrderComposesWithRealPaginationAndFiltering() {
    db.update(
        "INSERT INTO eam_business_field(id,tenant_id,object_key,data_type,ever_published)"
            + " VALUES(?,10001,'equipment','NUMBER',1)",
        FIELD);
    db.update(
        "UPDATE eam_business_schema SET"
            + " published_version=1,published=JSON_OBJECT('fields',JSON_ARRAY(JSON_OBJECT('id',?,'name','测试数值','type','NUMBER','enabled',true,'listable',true,'filterable',true)))"
            + " WHERE tenant_id=10001 AND object_key='equipment'",
        FIELD);
    for (int i = 0; i < 24; i++)
      db.update(
          "INSERT INTO eam_business_value(tenant_id,object_key,record_id,field_id,number_value)"
              + " VALUES(10001,'equipment',?,?,?)",
          FIRST + i,
          FIELD,
          24 - i);
    var input = Map.of("sortBy", "custom:" + FIELD, "direction", "asc");
    var first = service.list(query(input));
    var second =
        service.list(query(Map.of("sortBy", "custom:" + FIELD, "direction", "asc", "page", "2")));
    assertEquals(24, first.page().total());
    assertEquals(String.valueOf(FIRST + 23), ids(first).getFirst());
    assertEquals(String.valueOf(FIRST + 3), ids(second).getFirst());
    assertTrue(Collections.disjoint(ids(first), ids(second)));
    var filtered =
        service.list(
            query(
                Map.of(
                    "sortBy",
                    "custom:" + FIELD,
                    "direction",
                    "asc",
                    "advanced",
                    "[{\"field\":\"custom:"
                        + FIELD
                        + "\",\"operator\":\"range\",\"values\":[\"2\",\"10\"]}]")));
    assertEquals(9, filtered.page().total());
    assertEquals(String.valueOf(FIRST + 22), ids(filtered).getFirst());
  }
}
