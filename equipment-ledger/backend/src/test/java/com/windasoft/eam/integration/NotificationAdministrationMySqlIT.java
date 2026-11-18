package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.NotificationContracts;
import com.windasoft.eam.repository.system.NotifyChannelRepository;
import com.windasoft.eam.repository.system.NotifyRuleRepository;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.NotificationChannelService;
import com.windasoft.eam.service.system.NotificationPolicyService;
import com.windasoft.eam.service.system.NotificationRetentionService;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.support.SelectQueryLogger;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 仅在本专项隔离库验证通知配置、批量人员校验、生效下界及回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class NotificationAdministrationMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry p) {
    SystemUserQueryMySqlIT.database(p);
    p.add(
        "spring.datasource.url",
        () ->
            System.getenv("EAM_SYSTEM_TEST_URL")
                + "&profileSQL=true&logger=com.windasoft.eam.support.SelectQueryLogger");
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
  @Autowired NotificationPolicyService policies;
  @Autowired NotificationChannelService channels;
  @Autowired NotificationRetentionService retention;
  @Autowired NotifyChannelRepository channelConfiguration;
  @MockitoSpyBean NotifyRuleRepository rules;
  private long tenant, user;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    user = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(user);
    long company = IdWorker.getId();
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,status) VALUES(?,?,?,?,?,1)",
        company,
        tenant,
        "通知公司",
        "NOTIFY",
        "公司");
    db.update(
        "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status)"
            + " VALUES(?,?,?,?,?,1)",
        user,
        tenant,
        "notify-" + user,
        "通知测试人",
        company);
    SelectQueryLogger.start();
    db.queryForObject("SELECT 1", Integer.class);
    assertEquals(1, SelectQueryLogger.stop());
  }

  private NotificationContracts.Rule rule(Long id, int value, List<Long> to, List<Long> cc) {
    return new NotificationContracts.Rule(id, "WAIT_DISPATCH", value, "MINUTE", true, to, cc);
  }

  @Test
  void fixedRecipientsAreBatchedAndUnchangedRulesKeepTheirLowerBound() {
    var recipients = new ArrayList<Long>();
    recipients.add(user);
    long company =
        db.queryForObject("SELECT org_id FROM eam_sys_user WHERE id=?", Long.class, user);
    for (int i = 0; i < 39; i++) {
      long id = IdWorker.getId();
      recipients.add(id);
      db.update(
          "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status)"
              + " VALUES(?,?,?,?,?,1)",
          id,
          tenant,
          "notify-" + id,
          "提醒人" + i,
          company);
    }
    SelectQueryLogger.start();
    var saved =
        policies.save(
            new NotificationContracts.Policy(
                null,
                null,
                "维修提醒",
                "repair",
                true,
                List.of(rule(null, 1, recipients, List.of(user)))));
    long selects = SelectQueryLogger.stop();
    System.out.println("notification-policy SELECT recipients40=" + selects);
    assertTrue(selects <= 5, "固定接收人不应逐人查询");
    assertEquals(40, saved.rules().getFirst().recipients().size());
    assertEquals(List.of(), saved.rules().getFirst().cc());
    long id = Long.parseLong(saved.id()), rid = Long.parseLong(saved.rules().getFirst().id());
    var past = LocalDateTime.of(2020, 1, 1, 0, 0);
    db.update(
        "UPDATE eam_notify_rule SET effective_at=? WHERE tenant_id=? AND id=?", past, tenant, rid);
    db.update(
        "UPDATE eam_notify_policy SET effective_at=? WHERE tenant_id=? AND id=?", past, tenant, id);
    policies.save(
        new NotificationContracts.Policy(
            id, 0, "仅改名称", "repair", true, List.of(rule(rid, 1, recipients, List.of(user)))));
    assertEquals(
        past,
        db.queryForObject(
            "SELECT effective_at FROM eam_notify_rule WHERE id=?", LocalDateTime.class, rid));
    policies.setDefault("repair", new NotificationContracts.DefaultPolicy(id, 0));
    var binding =
        db.queryForObject(
            "SELECT effective_at FROM eam_notify_default WHERE tenant_id=? AND"
                + " business_kind='repair'",
            LocalDateTime.class,
            tenant);
    assertEquals(binding, policies.effectivePolicy(tenant, "repair", null).getEffectiveAt());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () ->
            policies.save(
                new NotificationContracts.Policy(
                    id, 0, "过期", "repair", true, List.of(rule(rid, 1, recipients, List.of())))));
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class, () -> policies.detail(id));
  }

  @Test
  void channelSecretsAndRetentionVersionsRemainIsolated() {
    var old = channels.settings();
    assertEquals(false, old.hasPassword());
    var input =
        new NotificationContracts.Channel(
            0,
            true,
            true,
            "smtp.example.invalid",
            587,
            "STARTTLS",
            "account",
            "isolated-fake-password",
            "sender@example.invalid",
            "http://localhost:5177");
    channels.save(input);
    var view = channels.settings();
    assertTrue(view.hasPassword());
    assertEquals(1, view.rowVersion());
    var stored = channelConfiguration.find(tenant, false);
    assertTrue(!stored.getSmtpSecret().contains("isolated-fake-password"));
    var wire = json.valueToTree(view);
    assertEquals(null, wire.get("smtpSecret"));
    assertEquals(null, wire.get("password"));
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class, () -> channels.save(input));
    var config = retention.settings();
    assertEquals(90, config.successDays());
    assertEquals(180, config.exceptionDays());
    retention.save(new NotificationContracts.Retention(0, 45, 120));
    assertEquals(45, retention.settings().successDays());
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> retention.save(new NotificationContracts.Retention(0, 1, 1)));
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertEquals(false, channels.settings().hasPassword());
    assertEquals(90, retention.settings().successDays());
  }

  @Test
  void deliveryFiltersAndMetadataStayCompatible() throws Exception {
    long message = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " eam_notify_message(id,tenant_id,source_key,module,object_id,subject,recipient_id)"
            + " VALUES(?,?,?,'repair',?,'通知回归',?)",
        message,
        tenant,
        "test:" + message,
        message,
        user);
    db.update(
        "INSERT INTO eam_notify_delivery(message_id,channel,status,sent_at)"
            + " VALUES(?,'SYSTEM','SENT',NOW(6)),(?,'EMAIL','DISABLED',NULL),(?,'APP','NOT_INTEGRATED',NULL)",
        message,
        message,
        message);
    var active = channels.deliveries("", "", "通知回归", null, null, "repair", null, false, 1, 20);
    assertEquals(1L, active.total());
    assertEquals("SYSTEM", active.data().getFirst().channel());
    var all = channels.deliveries("", "", "通知回归", null, null, "repair", user, true, 1, 20);
    assertEquals(3L, all.total());
    assertEquals(
        3L, channels.deliveries("", "", "通知回归", null, null, "repair", user, true, 2, 1).total());
    assertEquals(1, channels.deliveryRecipients("通知测试人", 1, 20).data().size());
    var row = active.data().getFirst();
    var old =
        db.queryForMap(
            "SELECT CAST(d.id AS CHAR) id,d.channel,d.status,d.attempts,d.last_error"
                + " lastError,d.sent_at sentAt,m.subject,m.module,m.module"
                + " businessKind,CAST(m.object_id AS CHAR) objectId,m.recipient_kind"
                + " recipientKind,u.display_name recipient,m.created_at createdAt,m.read_at readAt"
                + " FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id JOIN"
                + " eam_sys_user u ON u.tenant_id=m.tenant_id AND u.id=m.recipient_id WHERE"
                + " m.tenant_id=? AND d.channel='SYSTEM'",
            tenant);
    assertEquals(json.valueToTree(old), json.valueToTree(row));
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertEquals(
        0L, channels.deliveries("", "", "通知回归", null, null, "repair", null, true, 1, 20).total());
  }

  @Test
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  void failedRuleWriteRollsBackPolicy() {
    try {
      Mockito.doThrow(new IllegalStateException("模拟规则保存失败")).when(rules).insert(Mockito.any());
      assertThrows(
          IllegalStateException.class,
          () ->
              policies.save(
                  new NotificationContracts.Policy(
                      null,
                      null,
                      "失败策略",
                      "repair",
                      true,
                      List.of(rule(null, 1, List.of(user), List.of())))));
      assertEquals(
          0L,
          db.queryForObject(
              "SELECT COUNT(*) FROM eam_notify_policy WHERE tenant_id=?", Long.class, tenant));
    } finally {
      db.update("DELETE FROM eam_sys_user WHERE tenant_id=?", tenant);
      db.update("DELETE FROM eam_org_unit WHERE tenant_id=?", tenant);
    }
  }
}
