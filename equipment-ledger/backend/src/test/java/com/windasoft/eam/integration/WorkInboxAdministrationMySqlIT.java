package com.windasoft.eam.integration;

import com.windasoft.eam.service.system.WorkInboxService;

import org.junit.jupiter.api.Assertions;

import com.windasoft.eam.dto.system.WorkInboxViews;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.scheduler.experience.RepairPlanScheduler;
import com.windasoft.eam.scheduler.inspection.InspectionScheduler;
import com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup;
import com.windasoft.eam.scheduler.knowledge.KnowledgeScheduler;
import com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler;
import com.windasoft.eam.scheduler.system.NotificationScheduler;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

/** 仅在本专项隔离库验证通知配置、批量人员校验、生效下界及回滚。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_SYSTEM_TEST_URL", matches = ".+")
@Transactional
class WorkInboxAdministrationMySqlIT {
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
  @Autowired WorkInboxService work;
  private long tenant, user, other, order, inbox, notice, legacy;

  @BeforeEach
  void identity() {
    tenant = IdWorker.getId();
    user = IdWorker.getId();
    other = IdWorker.getId();
    order = IdWorker.getId();
    inbox = IdWorker.getId();
    notice = IdWorker.getId();
    legacy = IdWorker.getId();
    long org = IdWorker.getId();
    when(auth.tenantId()).thenReturn(tenant);
    when(auth.userId()).thenReturn(user);
    db.update(
        "INSERT INTO eam_org_unit(id,tenant_id,name,code,org_type,status) VALUES(?,?,?,?,?,1)",
        org,
        tenant,
        "待办公司",
        "INBOX",
        "公司");
    for (long u : List.of(user, other))
      db.update(
          "INSERT INTO eam_sys_user(id,tenant_id,account,display_name,org_id,status)"
              + " VALUES(?,?,?,?,?,1)",
          u,
          tenant,
          "inbox-" + u,
          "待办测试",
          org);
    db.update(
        "INSERT INTO"
            + " eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,fault_type,description,reporter,status,reported_at)"
            + " VALUES(?,?,'INBOX-TEST','EQ-TEST','待办设备','test','test','test','待派工',NOW())",
        order,
        tenant);
    db.update(
        "INSERT INTO eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,created_at)"
            + " VALUES(?,?,?,?,'待派工','待办标题',1,NOW())",
        inbox,
        tenant,
        user,
        order);
    db.update(
        "INSERT INTO"
            + " eam_notify_message(id,tenant_id,source_key,module,object_id,subject,recipient_id)"
            + " VALUES(?,?,?,'repair',?,'公共通知',?)",
        notice,
        tenant,
        "public:" + notice,
        order,
        user);
    db.update(
        "INSERT INTO eam_notify_delivery(message_id,channel,status,sent_at)"
            + " VALUES(?,'SYSTEM','SENT',NOW(6))",
        notice);
    db.update(
        "INSERT INTO"
            + " eam_notify_message(id,tenant_id,source_key,module,object_id,subject,recipient_id,legacy_source,legacy_id)"
            + " VALUES(?,?,?,'repair',?,'旧消息映射',?,'eam_repair_inbox',?)",
        legacy,
        tenant,
        "legacy:" + legacy,
        order,
        user,
        inbox);
  }

  @Test
  void rowsSummaryAndOriginalJsonRemainCompatible() throws Exception {
    var page = work.list("todo", "pending", "repair", "", "", 1, 20);
    assertEquals(1, page.total());
    var old =
        db.queryForMap(
            "SELECT CAST(i.id AS CHAR) id,'repair' module,CAST(r.id AS CHAR) orderId,r.order_no"
                + " orderNo,r.equipment_name equipmentName,r.status,i.kind,i.title,i.message_key"
                + " messageKey,i.message_args messageArgs,i.is_todo todo,i.done,(i.read_at IS NOT"
                + " NULL) isRead,'' planName,'' shiftName,DATE_FORMAT(i.created_at,'%Y-%m-%d"
                + " %H:%i:%s') time FROM eam_repair_inbox i JOIN eam_repair_order r ON"
                + " r.id=i.order_id AND r.tenant_id=i.tenant_id WHERE i.tenant_id=? AND"
                + " i.user_id=?",
            tenant, user);
    // 比较真实JSON协议；JDBC整型节点的Int/Long实现类型不属于HTTP契约。
    assertEquals(
        json.readTree(json.writeValueAsString(old)),
        json.readTree(json.writeValueAsString(page.data().getFirst())));
    var counts = work.summary().counts();
    assertEquals(1, counts.pending());
    assertEquals(0, counts.completed());
    assertEquals(2, counts.unread());
    assertEquals(2, work.list("notification", "all", "repair", "", "", 1, 20).total());
    assertEquals(
        List.of(new WorkInboxViews.Kind("repair", "待派工")),
        work.assistantKinds());
    when(auth.userId()).thenReturn(other);
    assertEquals(0, work.list("notification", "all", "repair", "", "", 1, 20).total());
    when(auth.userId()).thenReturn(user);
    when(auth.tenantId()).thenReturn(tenant + 1);
    assertEquals(0, work.summary().counts().unread());
  }

  @Test
  void readDistinguishesLegacyAndPublicWithoutClosingTodo() {
    work.read(new WorkInboxViews.Read("repair", Long.toString(inbox)));
    assertEquals(
        0, db.queryForObject("SELECT done FROM eam_repair_inbox WHERE id=?", Integer.class, inbox));
    var first =
        db.queryForObject(
            "SELECT read_at FROM eam_repair_inbox WHERE id=?", LocalDateTime.class, inbox);
    assertTrue(first != null);
    work.read(new WorkInboxViews.Read("repair", Long.toString(inbox)));
    assertEquals(
        first,
        db.queryForObject(
            "SELECT read_at FROM eam_repair_inbox WHERE id=?", LocalDateTime.class, inbox));
    assertTrue(
        db.queryForObject(
                "SELECT read_at FROM eam_notify_message WHERE id=?", LocalDateTime.class, legacy)
            != null);
    assertEquals(
        null,
        db.queryForObject(
            "SELECT read_at FROM eam_notify_message WHERE id=?", LocalDateTime.class, notice));
    when(auth.userId()).thenReturn(other);
    work.read(new WorkInboxViews.Read("repair", "n:" + notice));
    assertEquals(
        null,
        db.queryForObject(
            "SELECT read_at FROM eam_notify_message WHERE id=?", LocalDateTime.class, notice));
    when(auth.userId()).thenReturn(user);
    work.read(new WorkInboxViews.Read("maintenance", "n:" + notice));
    assertTrue(
        db.queryForObject(
                "SELECT read_at FROM eam_notify_message WHERE id=?", LocalDateTime.class, notice)
            != null);
    assertEquals(0, work.summary().counts().unread());
    assertEquals(1, work.summary().counts().pending());
  }

  @Test
  void bulkReadUsesEachOwnedInboxAndPreservesOtherUsers() {
    for (String table : List.of("eam_maintenance_inbox", "eam_inspection_inbox"))
      for (long u : List.of(user, other))
        db.update(
            "INSERT INTO "
                + table
                + "(id,tenant_id,task_id,user_id,kind,title,is_todo) VALUES(?,?,?,?,'提醒','标题',1)",
            IdWorker.getId(),
            tenant,
            order,
            u);
    for (long u : List.of(user, other)) {
      db.update(
          "INSERT INTO inv_inbox(id,tenant_id,user_id,document_id,route_kind,kind,title,is_todo)"
              + " VALUES(?,?,?,?,'transfers','提醒','标题',1)",
          IdWorker.getId(),
          tenant,
          u,
          order);
      db.update(
          "INSERT INTO"
              + " eam_experience_inbox(id,tenant_id,user_id,object_id,object_kind,kind,title,is_todo)"
              + " VALUES(?,?,?,?,'anomaly','提醒','标题',1)",
          IdWorker.getId(),
          tenant,
          u,
          order);
    }
    work.read(new WorkInboxViews.Read(null, null));
    for (String table :
        List.of(
            "eam_repair_inbox",
            "eam_maintenance_inbox",
            "eam_inspection_inbox",
            "inv_inbox",
            "eam_experience_inbox")) {
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM "
                  + table
                  + " WHERE tenant_id=? AND user_id=? AND read_at IS NULL",
              Integer.class,
              tenant,
              user));
      assertEquals(
          0,
          db.queryForObject(
              "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND done<>0",
              Integer.class,
              tenant));
    }
    for (String table :
        List.of(
            "eam_maintenance_inbox", "eam_inspection_inbox", "inv_inbox", "eam_experience_inbox"))
      assertEquals(
          1,
          db.queryForObject(
              "SELECT COUNT(*) FROM "
                  + table
                  + " WHERE tenant_id=? AND user_id=? AND read_at IS NULL",
              Integer.class,
              tenant,
              other));
  }
}
