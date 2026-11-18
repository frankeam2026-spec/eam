package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.knowledge.AiCallView;
import com.windasoft.eam.repository.knowledge.AiCallRepository;
import com.windasoft.eam.repository.knowledge.AiMonthlyUsageRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.knowledge.AiGateway;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅在专项隔离库验证AI配置与预算；HTTP传输始终替换为mock，不发送业务数据。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class AiSettingsMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
  }

  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean NotificationScheduler notificationScheduler;
  @MockitoBean MaintenanceScheduler maintenanceScheduler;
  @MockitoBean InspectionScheduler inspectionScheduler;
  @MockitoBean InventoryReservationCleanup cleanup;
  @MockitoBean RepairPlanScheduler repairScheduler;
  @MockitoBean KnowledgeScheduler knowledgeScheduler;
  @Autowired ObjectMapper json;
  @Autowired JdbcTemplate db;

  @Autowired AiGateway gateway;
  @Autowired AiMonthlyUsageRepository usage;

  @MockitoSpyBean AiCallRepository calls;

  private long tenant;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(73001L);
  }

  private AiGateway.Settings config(long version) {
    return new AiGateway.Settings(
        true,
        "https://test.aliyuncs.com/v1",
        "test-not-a-real-key",
        "test",
        "test",
        new BigDecimal("100"),
        80,
        BigDecimal.ONE,
        BigDecimal.ONE,
        BigDecimal.ONE,
        version,
        "",
        "",
        BigDecimal.ZERO,
        BigDecimal.ZERO,
        BigDecimal.ZERO);
  }

  @Test
  void settingsStayRedactedAndUseVersions() {
    gateway.save(config(0));
    var view = json.valueToTree(gateway.settings());
    Assertions.assertTrue(view.path("hasApiKey").asBoolean());
    Assertions.assertFalse(view.has("apiKey"));
    Assertions.assertFalse(view.has("apiSecret"));
    assertEquals(1, view.path("rowVersion").asLong());
    assertThrows(IllegalStateException.class, () -> gateway.save(config(0)));
    when(auth.tenantId()).thenReturn(tenant + 1);
    Assertions.assertFalse(gateway.settings().hasApiKey());
  }

  @Test
  void realBudgetSettlementAndCallRecordUseMockTransport() throws Exception {
    gateway.save(config(0));
    var http = Mockito.mock(HttpClient.class);
    HttpResponse<byte[]> response = Mockito.mock(HttpResponse.class);
    when(response.statusCode()).thenReturn(200);
    when(response.body())
        .thenReturn(
            "{\"usage\":{\"prompt_tokens\":10,\"completion_tokens\":2}}"
                .getBytes(StandardCharsets.UTF_8));
    when(http.sendAsync(
            ArgumentMatchers.any(HttpRequest.class),
            ArgumentMatchers.<HttpResponse.BodyHandler<byte[]>>any()))
        .thenReturn(CompletableFuture.completedFuture(response));
    var original = ReflectionTestUtils.getField(gateway, "http");
    ReflectionTestUtils.setField(gateway, "http", http);
    try {
      gateway.post("/chat/completions", Map.of("max_tokens", 2, "messages", List.of()), false);
      var used = usage.usage(tenant, YearMonth.now(ZoneId.of("Asia/Shanghai")).toString(), false);
      assertEquals(0, used.getReservedAmount().signum());
      Assertions.assertTrue(used.getEstimatedSpent().signum() > 0);
      var page = gateway.calls(1, 20);
      assertEquals(1, page.total());
      assertEquals("SUCCEEDED", page.data().getFirst().status());
      assertEquals(10L, page.data().getFirst().inputTokens());
      Assertions.assertFalse(
          calls.finish(
              Long.parseLong(page.data().getFirst().id()),
              tenant,
              BigDecimal.ONE,
              null,
              null,
              1,
              true));
      assertEquals(
          0,
          usage
              .usage(tenant, YearMonth.now(ZoneId.of("Asia/Shanghai")).toString(), false)
              .getEstimatedSpent()
              .compareTo(used.getEstimatedSpent()));
    } finally {
      ReflectionTestUtils.setField(gateway, "http", original);
    }
  }

  @Test
  void callPaginationKeepsOriginalWireShapeAndStableOrder() {
    var ids = new java.util.ArrayList<Long>();
    for (int i = 0; i < 21; i++) {
      long id = IdWorker.getId();
      ids.add(id);
      db.update(
          "INSERT INTO"
              + " eam_ai_call(id,tenant_id,user_id,billing_month,purpose,model_name,status,reserved_amount,created_at)"
              + " VALUES(?,?,?,?,'CHAT','test','UNKNOWN',0,'2026-11-18 08:00:00')",
          id,
          tenant,
          73001L,
          "2026-11");
    }
    ids.sort(java.util.Comparator.reverseOrder());
    var first = gateway.calls(1, 20);
    var second = gateway.calls(2, 20);
    assertEquals(21, first.total());
    assertEquals(
        ids.subList(0, 20).stream().map(String::valueOf).toList(),
        first.data().stream().map(AiCallView::id).toList());
    assertEquals(
        java.util.List.of(String.valueOf(ids.get(20))),
        second.data().stream().map(AiCallView::id).toList());
    var old =
        db.queryForList(
            "SELECT CAST(id AS CHAR) id,purpose,model_name modelName,status,input_tokens"
                + " inputTokens,output_tokens outputTokens,estimated_cost estimatedCost,elapsed_ms"
                + " elapsedMs,created_at createdAt FROM eam_ai_call WHERE tenant_id=? ORDER BY"
                + " created_at DESC,id DESC LIMIT 20",
            tenant);
    assertEquals(
        json.valueToTree(java.util.Map.of("total", 21L, "data", old)), json.valueToTree(first));
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedCallInsertRollsBackReservation() {
    try {
      gateway.save(config(0));
      doThrow(new IllegalStateException("调用记录保存失败")).when(calls).insert(ArgumentMatchers.any());
      assertThrows(
          IllegalStateException.class,
          () ->
              gateway.post(
                  "/chat/completions", Map.of("max_tokens", 1, "messages", List.of()), false));
      assertEquals(
          0,
          db.queryForObject(
                  "SELECT COALESCE(SUM(reserved_amount),0) FROM eam_ai_monthly_usage WHERE"
                      + " tenant_id=?",
                  BigDecimal.class,
                  tenant)
              .signum());
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_ai_call WHERE tenant_id=?", Integer.class, tenant));
    } finally {
      db.update("DELETE FROM eam_ai_call WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_ai_monthly_usage WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_ai_setting WHERE tenant_id=?", tenant);
    }
  }
}
