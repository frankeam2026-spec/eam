package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.NotificationContracts.*;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.*;
import com.windasoft.eam.service.system.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest(webEnvironment=SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named="EAM_STAFFING_TEST_URL",matches=".+")
@Transactional
class NotificationMySqlIT {
  @DynamicPropertySource static void database(DynamicPropertyRegistry p) {
    String url=System.getenv("EAM_STAFFING_TEST_URL");
    if(url==null || !url.matches("jdbc:mysql://127\\.0\\.0\\.1:3307/eam_staffing_test_20261023(\\?.*)?"))throw new IllegalStateException("Only isolated MySQL is allowed");
    p.add("spring.datasource.url",()->url);p.add("spring.datasource.username",()->"root");p.add("spring.datasource.password",()->"");
    p.add("eam.notification.secret-key",()->Base64.getEncoder().encodeToString(new byte[32]));
  }
  @MockitoBean AuthContext auth;
  @MockitoBean PermissionService permissions;
  @MockitoBean com.windasoft.eam.scheduler.system.NotificationScheduler notificationsScheduler;
  @MockitoBean com.windasoft.eam.scheduler.maintenance.MaintenanceScheduler maintenanceScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inspection.InspectionScheduler inspectionScheduler;
  @MockitoBean com.windasoft.eam.scheduler.inventory.InventoryReservationCleanup cleanup;
  @MockitoBean com.windasoft.eam.scheduler.experience.RepairPlanScheduler repairScheduler;
  @Autowired JdbcTemplate db;
  @Autowired NotificationPolicyService policies;
  @Autowired NotificationChannelService channels;
  @Autowired NotificationDeliveryService delivery;
  @Autowired NotificationRepository notifications;
  @Autowired ReminderRepository repository;
  @Autowired ReminderService reminders;
  @Autowired WorkInboxRepository inbox;
  @Autowired NotificationRetentionService retention;
  @Autowired NotificationRetentionRepository retentionRepository;
  @Autowired UserManagementService usersService;
  long user,other;
  @BeforeEach void identity() {
    var users=db.queryForList("SELECT id FROM eam_sys_user WHERE tenant_id=10001 AND status=1 AND deleted=0 ORDER BY id LIMIT 2",Long.class);
    user=users.get(0);other=users.get(1);when(auth.tenantId()).thenReturn(10001L);when(auth.userId()).thenReturn(user);
    var u=new SystemUser();u.setId(user);u.setTenantId(10001L);u.setDisplayName("notification-test");when(auth.user()).thenReturn(u);
  }
  Rule rule(String scene,int value){return new Rule(null,scene,value,"MINUTE",true,List.of(user),List.of(user,other));}
  long policy(String kind,List<Rule> rules,boolean past) {
    var p=policies.save(new Policy(null,null,"通知测试-"+IdWorker.getId(),kind,true,rules));long id=Long.parseLong(p.get("id").toString());
    int v=notifications.defaults(10001).stream().filter(x->x.get("businessKind").equals(kind)).map(x->((Number)x.get("rowVersion")).intValue()).findFirst().orElse(0);
    policies.setDefault(kind,new DefaultPolicy(id,v));
    if(past) {db.update("UPDATE eam_notify_policy SET effective_at=DATE_SUB(NOW(6),INTERVAL 1 DAY) WHERE id=?",id);db.update("UPDATE eam_notify_rule SET effective_at=DATE_SUB(NOW(6),INTERVAL 1 DAY) WHERE policy_id=?",id);db.update("UPDATE eam_notify_default SET effective_at=DATE_SUB(NOW(6),INTERVAL 1 DAY) WHERE tenant_id=10001 AND business_kind=?",kind);}
    return id;
  }
  long repair(String status) {
    long id=IdWorker.getId();
    db.update("INSERT INTO eam_repair_order(id,tenant_id,order_no,equipment_code,equipment_name,fault_type,description,reporter,status,reported_at,dispatched_at,claimed_at,completed_at) VALUES(?,10001,?,'EQ-TEST','notification-test-device','test','test','test',?,DATE_SUB(NOW(),INTERVAL 90 SECOND),DATE_SUB(NOW(),INTERVAL 80 SECOND),?,?)",id,"NOTIFY-"+id,status,status.equals("处理中")?LocalDateTime.now().minusMinutes(2):null,status.equals("待验收")?LocalDateTime.now().minusMinutes(2):null);
    return id;
  }
  int count(long id){return db.queryForObject("SELECT COUNT(*) FROM eam_notify_message WHERE tenant_id=10001 AND object_id=? AND rule_id IS NOT NULL",Integer.class,id);}
  void send(long object,String channel) {for(long id:db.queryForList("SELECT d.id FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.tenant_id=10001 AND m.object_id=? AND d.channel=?",Long.class,object,channel))delivery.deliver(10001,id);}

  @Test void migrationAndPolicyRetainRelationalAssociationsAndStableRules() {
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM information_schema.tables WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME IN ('eam_repair_reminder_clock','eam_inspection_reminder_clock','eam_maintenance_reminder_clock')",Integer.class));
    long pid=policy("repair",List.of(rule("WAIT_DISPATCH",1)),true);
    var before=policies.detail(pid);var rules=(List<Map<String,Object>>)before.get("rules");long rid=Long.parseLong(rules.getFirst().get("id").toString());
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM eam_notify_rule_person WHERE rule_id=?",Integer.class,rid));
    assertEquals("TO",db.queryForObject("SELECT recipient_kind FROM eam_notify_rule_person WHERE rule_id=? AND user_id=?",String.class,rid,user));
    var at=db.queryForObject("SELECT effective_at FROM eam_notify_rule WHERE id=?",java.sql.Timestamp.class,rid);
    policies.save(new Policy(pid,0,before.get("name").toString(),"repair",true,List.of(new Rule(rid,"WAIT_DISPATCH",1,"MINUTE",true,List.of(user),List.of(other)))));
    assertEquals(at,db.queryForObject("SELECT effective_at FROM eam_notify_rule WHERE id=?",java.sql.Timestamp.class,rid));
    assertThrows(ResponseStatusException.class,()->policies.save(new Policy(pid,0,"stale","repair",true,List.of(rule("WAIT_DISPATCH",1)))));
    assertThrows(ResponseStatusException.class,()->policies.save(new Policy(null,null,"invalid-scene","repair",true,List.of(rule("REWORK",1)))));
    when(auth.tenantId()).thenReturn(10002L);assertThrows(ResponseStatusException.class,()->policies.detail(pid));
  }
  @Test void multipleRulesNotifyFixedUsersOnceAndExposeSystemMessages() {
    policy("repair",List.of(rule("WAIT_DISPATCH",1),rule("REPORT_UNCLAIMED",1)),true);long id=repair("待派工");
    reminders.scan("repair",10001,id);reminders.scan("repair",10001,id);assertEquals(4,count(id));
    send(id,"SYSTEM");
    var list=(Map<?,?>)inbox.list(10001,user,"notification","all","repair","","notification-test",1,20);
    assertTrue(((Number)list.get("total")).longValue()>=2);
    var rows=(List<Map<String,Object>>)list.get("data");var row=rows.stream().filter(r->id==Long.parseLong(r.get("orderId").toString()) && r.get("id").toString().startsWith("n:")).findFirst().orElseThrow();
    inbox.read(10001,user,"repair",row.get("id").toString());
    assertNotNull(db.queryForObject("SELECT read_at FROM eam_notify_message WHERE id=?",java.sql.Timestamp.class,Long.parseLong(row.get("id").toString().substring(2))));
  }
  @Test void newPolicyDoesNotReplayPastTriggersAndDisabledRulesNeverFire() {
    long pid=policy("repair",List.of(rule("WAIT_DISPATCH",1)),false);long id=repair("待派工");reminders.scan("repair",10001,id);assertEquals(0,count(id));
    db.update("UPDATE eam_notify_rule SET effective_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE policy_id=?",pid);
    // The default binding/policy cutoff still protects pre-enable triggers.
    reminders.scan("repair",10001,id);assertEquals(0,count(id));
    db.update("UPDATE eam_notify_policy SET enabled=0 WHERE id=?",pid);reminders.scan("repair",10001,id);assertEquals(0,count(id));
  }
  @Test void completionAndSpecialFlowsCancelPendingSends() {
    policy("repair",List.of(rule("PROCESS",1)),true);long id=repair("处理中");reminders.scan("repair",10001,id);assertEquals(2,count(id));
    db.update("UPDATE eam_repair_order SET status='已完成' WHERE id=?",id);send(id,"SYSTEM");
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.object_id=? AND d.channel='SYSTEM' AND d.status='CANCELLED'",Integer.class,id));
    long special=repair("处理中");db.update("INSERT INTO eam_repair_event(id,tenant_id,order_id,action,actor_id,actor_name,details_json,created_at) VALUES(?,10001,?,'挂起',?,'test','{}',NOW())",IdWorker.getId(),special,user);
    reminders.scan("repair",10001,special);assertEquals(0,count(special));
  }
  @Test void inspectionSubmissionStopsExpiryAndStartsReviewReminder() {
    long pid=policy("daily",List.of(rule("AFTER_DUE",0),rule("WAIT_REVIEW",1)),true);
    long plan=db.queryForObject("SELECT id FROM eam_inspection_daily_plan WHERE tenant_id=10001 LIMIT 1",Long.class);
    db.update("UPDATE eam_inspection_daily_plan SET reminder_mode='POLICY',reminder_policy_id=?,reminder_effective_at=DATE_SUB(NOW(),INTERVAL 1 DAY) WHERE id=?",pid,plan);
    long task=IdWorker.getId();db.update("INSERT INTO eam_inspection_task(id,tenant_id,kind,code,plan_id,equipment_id,due_at,expires_at,status,snapshot) VALUES(?,10001,'daily',?,?,0,DATE_SUB(NOW(),INTERVAL 10 MINUTE),DATE_SUB(NOW(),INTERVAL 1 MINUTE),'待审核','{}')",task,"NOTIFY-"+task,plan);
    db.update("INSERT INTO eam_inspection_execution(tenant_id,task_id,finished_at) VALUES(10001,?,DATE_SUB(NOW(),INTERVAL 2 MINUTE))",task);
    reminders.scan("inspection",10001,task);assertEquals(2,count(task));assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_notify_message WHERE object_id=? AND scene='AFTER_DUE'",Integer.class,task));
    db.update("UPDATE eam_inspection_daily_plan SET reminder_mode='OFF' WHERE id=?",plan);send(task,"SYSTEM");
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.object_id=? AND d.channel='SYSTEM' AND d.status='SENT'",Integer.class,task));
  }
  @Test void maintenanceExpiryUsesEndOfDateAndAutomaticExpiryIsStillNotified() {
    long pid=policy("maintenance",List.of(rule("AFTER_DUE",0)),true);long plan=IdWorker.getId();
    db.update("INSERT INTO eam_maintenance_plan(id,tenant_id,code,name,creator_id,enabled,remark,calculation_mode,advance_days,remind_days,auto_fill,scan_start,review_mode,review_rule,reminder_mode,reminder_policy_id,reminder_effective_at) VALUES(?,10001,?,'test',?,1,'','fixed',0,0,'',0,'无需验收','','POLICY',?,DATE_SUB(NOW(),INTERVAL 2 DAY))",plan,"NOTIFY-"+plan,user,pid);
    long task=IdWorker.getId();db.update("INSERT INTO eam_maintenance_task(id,tenant_id,code,plan_id,schedule_id,equipment_id,due_date,expires_date,status,snapshot) VALUES(?,10001,?,?,?,0,CURDATE(),CURDATE(),'待执行','{}')",task,"NOTIFY-"+task,plan,IdWorker.getId());
    reminders.scan("maintenance",10001,task);assertEquals(0,count(task));
    // Simulate a policy that existed before the previous midnight.
    db.update("UPDATE eam_notify_policy SET effective_at=DATE_SUB(NOW(),INTERVAL 2 DAY) WHERE id=?",pid);db.update("UPDATE eam_notify_rule SET effective_at=DATE_SUB(NOW(),INTERVAL 2 DAY) WHERE policy_id=?",pid);db.update("UPDATE eam_notify_default SET effective_at=DATE_SUB(NOW(),INTERVAL 2 DAY) WHERE business_kind='maintenance' AND tenant_id=10001");
    db.update("UPDATE eam_maintenance_task SET expires_date=DATE_SUB(CURDATE(),INTERVAL 1 DAY),status='过期作废' WHERE id=?",task);reminders.scan("maintenance",10001,task);assertEquals(2,count(task));
  }
  @Test void legacyCaptureIsAtomicAndDoesNotDuplicateStationMessages() {
    long order=repair("待派工"),id=IdWorker.getId();db.update("INSERT INTO eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,created_at) VALUES(?,10001,?,?,'待派工','test',1,NOW())",id,user,order);
    notifications.captureSource("eam_repair_inbox",10001,id);notifications.captureSource("eam_repair_inbox",10001,id);
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM eam_notify_message WHERE legacy_source='eam_repair_inbox' AND legacy_id=?",Integer.class,id));
    assertEquals(3,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.legacy_id=?",Integer.class,id));
    assertEquals("SENT",db.queryForObject("SELECT d.status FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.legacy_id=? AND d.channel='SYSTEM'",String.class,id));
    send(order,"EMAIL");assertEquals("DISABLED",db.queryForObject("SELECT d.status FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.legacy_id=? AND d.channel='EMAIL'",String.class,id));
    db.update("UPDATE eam_repair_inbox SET read_at=NOW(6) WHERE id=?",id);
    var history=(List<Map<String,Object>>)notifications.deliveryPage(10001,"","","test",1,100).get("data");
    assertTrue(history.stream().filter(x->x.get("objectId").toString().equals(Long.toString(order))).allMatch(x->x.get("readAt")!=null));

  }
  @Test void expiredEmailLeaseIsUnconfirmedAndCannotBeBlindlySentTwice() {
    long order=repair("待派工"),id=IdWorker.getId();
    db.update("INSERT INTO eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,created_at) VALUES(?,10001,?,?,'待派工','lease-test',1,NOW())",id,user,order);
    notifications.captureSource("eam_repair_inbox",10001,id);
    long email=db.queryForObject("SELECT d.id FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.legacy_id=? AND d.channel='EMAIL'",Long.class,id);
    assertFalse(repository.claim(10001,email).isEmpty());
    assertTrue(repository.claim(10001,email).isEmpty());
    db.update("UPDATE eam_notify_delivery SET lease_until=DATE_SUB(NOW(6),INTERVAL 1 MINUTE) WHERE id=?",email);
    repository.ready(100);
    assertEquals("UNKNOWN",db.queryForObject("SELECT status FROM eam_notify_delivery WHERE id=?",String.class,email));
    assertTrue(repository.claim(10001,email).isEmpty());
  }
  @Test
  @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void staleEmailVersionRollsBackTheEntireUserEdit() {
    long id=IdWorker.getId();String account="notify-email-"+id;
    long org=db.queryForObject("SELECT org_id FROM eam_sys_user WHERE id=?",Long.class,user);
    db.update("INSERT INTO eam_sys_user(id,tenant_id,account,display_name,password_hash,org_id,status) VALUES(?,10001,?,'email-before','not-a-login',?,1)",id,account,org);
    try {
      usersService.updateUser(id,new com.windasoft.eam.dto.system.UserForm(account,"email-first","","",org,List.of(),1,"","first@example.invalid",0));
      var stale=new com.windasoft.eam.dto.system.UserForm(account,"email-stale","","",org,List.of(),1,"","stale@example.invalid",0);
      assertEquals(409,assertThrows(ResponseStatusException.class,()->usersService.updateUser(id,stale)).getStatusCode().value());
      var saved=db.queryForMap("SELECT display_name,email,email_version FROM eam_sys_user WHERE id=?",id);
      assertEquals("email-first",saved.get("display_name"));assertEquals("first@example.invalid",saved.get("email"));assertEquals(1,((Number)saved.get("email_version")).intValue());
    } finally {
      db.update("DELETE FROM eam_sys_user_role WHERE tenant_id=10001 AND user_id=?",id);
      db.update("DELETE FROM eam_sys_user WHERE tenant_id=10001 AND id=?",id);
    }
  }
  @Test void emailUsesSavedConfigurationAndChannelFailureDoesNotAffectSystem() throws Exception {
    policy("repair",List.of(new Rule(null,"WAIT_DISPATCH",1,"MINUTE",true,List.of(user),List.of())),true);long order=repair("待派工");reminders.scan("repair",10001,order);
    db.update("UPDATE eam_sys_user SET email='' WHERE id=?",user);db.update("INSERT INTO eam_notify_channel(tenant_id,email_enabled) VALUES(10001,1) ON DUPLICATE KEY UPDATE email_enabled=1");
    send(order,"EMAIL");send(order,"SYSTEM");
    var failed=db.queryForMap("SELECT d.id,d.attempts FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.object_id=? AND d.channel='EMAIL'",order);
    assertEquals("MISSING_ADDRESS",db.queryForObject("SELECT status FROM eam_notify_delivery WHERE id=?",String.class,failed.get("id")));
    try(var smtp=new ServerSocket(0,1,InetAddress.getLoopbackAddress())) {
      smtp.setSoTimeout(10000);var executor=Executors.newSingleThreadExecutor();
      Future<String> received=executor.submit(()->{try(var socket=smtp.accept()){socket.setSoTimeout(10000);var input=new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));var output=new PrintWriter(socket.getOutputStream(),true,StandardCharsets.UTF_8);output.print("220 localhost\r\n");output.flush();boolean data=false;var message=new StringBuilder();String line;while((line=input.readLine())!=null){if(data){if(line.equals(".")){data=false;output.print("250 accepted\r\n");}else message.append(line).append('\n');}else if(line.startsWith("EHLO")||line.startsWith("HELO"))output.print("250 localhost\r\n");else if(line.startsWith("DATA")){data=true;output.print("354 continue\r\n");}else if(line.startsWith("QUIT")){output.print("221 bye\r\n");output.flush();break;}else output.print("250 ok\r\n");output.flush();}return message.toString();}});
      try {
        db.update("UPDATE eam_sys_user SET email='notification-test@example.invalid' WHERE id=?",user);
        int version=((Number)channels.settings().get("rowVersion")).intValue();channels.save(new Channel(version,true,true,"127.0.0.1",smtp.getLocalPort(),"NONE","","","sender@example.invalid","http://127.0.0.1:5177"));
        channels.retry(((Number)failed.get("id")).longValue(),((Number)failed.get("attempts")).intValue());send(order,"EMAIL");
        assertEquals("SENT",db.queryForObject("SELECT status FROM eam_notify_delivery WHERE id=?",String.class,failed.get("id")));
        String mail=received.get(10,TimeUnit.SECONDS);assertTrue(mail.contains("notification-test@example.invalid"));assertTrue(mail.contains("Subject:"));
      }finally{executor.shutdownNow();}
    }
  }
  @Test void cleaningSuccessfulLogsPreservesInboxAndPreventsRecreation() {
    policy("repair",List.of(rule("WAIT_DISPATCH",1)),true);long order=repair("待派工");
    reminders.scan("repair",10001,order);send(order,"SYSTEM");
    var logs=db.queryForList("SELECT d.id,d.message_id FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.object_id=? AND d.channel='SYSTEM'",order);
    db.update("UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET d.sent_at=DATE_SUB(NOW(),INTERVAL 91 DAY) WHERE m.object_id=? AND d.channel='SYSTEM'",order);
    var messageBefore=db.queryForMap("SELECT * FROM eam_notify_message WHERE id=?",logs.getFirst().get("message_id"));
    for(var log:logs)assertTrue(retention.compact(10001,((Number)log.get("message_id")).longValue(),((Number)log.get("id")).longValue()));
    reminders.scan("repair",10001,order);
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.object_id=? AND d.channel='SYSTEM'",Integer.class,order));
    assertEquals(2,db.queryForObject("SELECT COUNT(*) FROM eam_notify_receipt r JOIN eam_notify_message m ON m.id=r.message_id WHERE m.object_id=?",Integer.class,order));
    assertEquals(messageBefore.get("subject"),db.queryForMap("SELECT * FROM eam_notify_message WHERE id=?",logs.getFirst().get("message_id")).get("subject"));
    var page=(Map<?,?>)inbox.list(10001,user,"notification","all","repair","","notification-test",1,100);
    var list=(List<Map<String,Object>>)page.get("data");
    var row=list.stream().filter(r->Long.toString(order).equals(r.get("orderId"))).findFirst().orElseThrow();
    inbox.read(10001,user,"repair",row.get("id").toString());
    assertNotNull(db.queryForObject("SELECT read_at FROM eam_notify_message WHERE id=?",java.sql.Timestamp.class,Long.parseLong(row.get("id").toString().substring(2))));
  }
  @Test void cleanupKeepsActiveRetriesAndRecentLogsButCompactsOldTerminalExceptions() {
    long order=repair("待派工"),message=IdWorker.getId();
    db.update("INSERT INTO eam_notify_message(id,tenant_id,source_key,module,object_id,subject,recipient_id) VALUES(?,10001,?,'repair',?,'retention',?)",message,"retention-"+message,order,user);
    String[] states={"PENDING","SENDING","FAILED","FAILED","UNKNOWN","SENT","DISABLED","NOT_INTEGRATED","CANCELLED","MISSING_ADDRESS"};
    for(int i=0;i<states.length;i++) {
      db.update("DELETE FROM eam_notify_delivery WHERE message_id=?",message);
      db.update("DELETE FROM eam_notify_receipt WHERE message_id=?",message);
      db.update("INSERT INTO eam_notify_delivery(message_id,channel,status,attempts,sent_at,updated_at) VALUES(?,'EMAIL',?,?,DATE_SUB(NOW(),INTERVAL 89 DAY),DATE_SUB(NOW(),INTERVAL 181 DAY))",message,states[i],i==3?5:4);
      long id=db.queryForObject("SELECT id FROM eam_notify_delivery WHERE message_id=?",Long.class,message);
      assertEquals(i==3 || i>=6 || i==4,retention.compact(10001,message,id),states[i]+" / "+i);
    }
    db.update("DELETE FROM eam_notify_delivery WHERE message_id=?",message);db.update("DELETE FROM eam_notify_receipt WHERE message_id=?",message);
    db.update("INSERT INTO eam_notify_delivery(message_id,channel,status,updated_at) VALUES(?,'APP','NOT_INTEGRATED',DATE_SUB(NOW(),INTERVAL 181 DAY))",message);
    long log=db.queryForObject("SELECT id FROM eam_notify_delivery WHERE message_id=?",Long.class,message);
    assertFalse(retention.compact(10002,message,log));
    assertTrue(retentionRepository.candidates(500).stream().anyMatch(r->((Number)r.get("id")).longValue()==log));
  }
  @Test void defaultFiltersUseSevenDaysAndExplicitInactiveStatusOverridesHiding() {
    policy("repair",List.of(rule("WAIT_DISPATCH",1)),true);long order=repair("待派工");reminders.scan("repair",10001,order);send(order,"EMAIL");
    String q="NOTIFY-FILTER-"+order;db.update("UPDATE eam_notify_message SET subject=? WHERE object_id=?",q,order);
    assertEquals(2L,channels.deliveries("","",q,null,null,"repair",null,false,1,20).get("total"));
    assertEquals(6L,channels.deliveries("","",q,null,null,"repair",null,true,1,20).get("total"));
    assertEquals(2L,channels.deliveries("DISABLED","",q,null,null,"repair",null,false,1,20).get("total"));
    assertEquals(1L,channels.deliveries("","",q,null,null,"repair",user,false,1,20).get("total"));
    assertEquals(0L,channels.deliveries("","",q,null,null,"daily",null,true,1,20).get("total"));
    var page=channels.deliveries("","",q,null,null,"repair",null,true,2,1);assertEquals(6L,page.get("total"));assertEquals(1,((List<?>)page.get("data")).size());
    db.update("UPDATE eam_notify_message SET created_at=DATE_SUB(NOW(),INTERVAL 8 DAY) WHERE object_id=?",order);
    assertEquals(0L,channels.deliveries("","",q,null,null,"",null,true,1,20).get("total"));
    assertEquals(6L,channels.deliveries("","",q,LocalDate.now().minusDays(9),LocalDate.now().minusDays(7),"",null,true,1,20).get("total"));
    when(auth.tenantId()).thenReturn(10002L);assertEquals(0L,channels.deliveries("","",q,LocalDate.now().minusDays(9),LocalDate.now(),"",null,true,1,20).get("total"));
    assertThrows(ResponseStatusException.class,()->channels.deliveries("","",q,LocalDate.now(),LocalDate.now().minusDays(1),"",null,true,1,20));
  }
  @Test void cleanedLegacySystemDeliveryIsNotRecreatedWhenSourceIsCapturedAgain() {
    long order=repair("待派工"),source=IdWorker.getId();
    db.update("INSERT INTO eam_repair_inbox(id,tenant_id,user_id,order_id,kind,title,is_todo,created_at) VALUES(?,10001,?,?,'待派工','retention-legacy',1,NOW())",source,user,order);
    notifications.captureSource("eam_repair_inbox",10001,source);
    var log=db.queryForMap("SELECT d.id,d.message_id FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE m.legacy_id=? AND d.channel='SYSTEM'",source);
    db.update("UPDATE eam_notify_delivery SET sent_at=DATE_SUB(NOW(),INTERVAL 100 DAY) WHERE id=?",log.get("id"));
    assertTrue(retention.compact(10001,((Number)log.get("message_id")).longValue(),((Number)log.get("id")).longValue()));
    notifications.captureSource("eam_repair_inbox",10001,source);
    assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery WHERE message_id=? AND channel='SYSTEM'",Integer.class,log.get("message_id")));
    assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM eam_repair_inbox WHERE id=?",Integer.class,source));
  }
  @Test void retentionConfigurationChecksVersionAndPermissions() {
    var before=retention.settings();int version=((Number)before.get("rowVersion")).intValue();
    assertEquals(90,((Number)before.get("successDays")).intValue());
    retention.save(new Retention(version,45,120));
    assertThrows(ResponseStatusException.class,()->retention.save(new Retention(version,1,1)));
    assertThrows(ResponseStatusException.class,()->retention.save(new Retention(version+1,0,120)));
    doThrow(new ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN)).when(permissions).require("notificationChannel","编辑");
    assertThrows(ResponseStatusException.class,()->retention.save(new Retention(version+1,30,90)));
  }
  @Test @Transactional(propagation=org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
  void concurrentCleanupRetainsOneReceiptAndNeverResends() throws Exception {
    long message=IdWorker.getId();
    db.update("INSERT INTO eam_notify_message(id,tenant_id,source_key,module,object_id,subject,recipient_id) VALUES(?,10001,?,'repair',0,'retention-concurrency',?)",message,"retention-"+message,user);
    db.update("INSERT INTO eam_notify_delivery(message_id,channel,status,sent_at) VALUES(?,'SYSTEM','SENT',DATE_SUB(NOW(),INTERVAL 100 DAY))",message);
    long log=db.queryForObject("SELECT id FROM eam_notify_delivery WHERE message_id=?",Long.class,message);
    var workers=Executors.newFixedThreadPool(2);var start=new CountDownLatch(1);
    try {
      Callable<Boolean> compact=()->{start.await();return retention.compact(10001,message,log);};
      var first=workers.submit(compact);var second=workers.submit(compact);start.countDown();
      assertNotEquals(first.get(15,TimeUnit.SECONDS),second.get(15,TimeUnit.SECONDS));
      notifications.deliveries(10001,"retention-"+message);
      assertEquals(0,db.queryForObject("SELECT COUNT(*) FROM eam_notify_delivery WHERE message_id=? AND channel='SYSTEM'",Integer.class,message));
      assertEquals(1,db.queryForObject("SELECT COUNT(*) FROM eam_notify_receipt WHERE message_id=?",Integer.class,message));
    } finally {
      workers.shutdownNow();db.update("DELETE FROM eam_notify_delivery WHERE message_id=?",message);db.update("DELETE FROM eam_notify_receipt WHERE message_id=?",message);db.update("DELETE FROM eam_notify_message WHERE id=?",message);
    }
  }

}
