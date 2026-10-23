package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import com.windasoft.eam.common.notification.ReminderTiming;
import java.time.*;
import org.junit.jupiter.api.Test;

class ReminderTimingTest {
  final LocalDateTime report=LocalDateTime.of(2026,10,23,8,0);
  @Test void normalStagesUseTheirOwnAnchors() {
    var waiting=ReminderTiming.repair(report,null,null,null,"待派工",false);
    assertEquals(report,waiting.get("WAIT_DISPATCH").anchor());
    assertEquals(report,waiting.get("REPORT_UNCLAIMED").anchor());
    var dispatched=ReminderTiming.repair(report,report.plusMinutes(10),null,null,"待接单",false);
    assertFalse(dispatched.containsKey("WAIT_DISPATCH"));
    assertEquals(report,dispatched.get("REPORT_UNCLAIMED").anchor());
    assertEquals(report.plusMinutes(10),dispatched.get("WAIT_CLAIM").anchor());
    var processing=ReminderTiming.repair(report,report.plusMinutes(10),report.plusMinutes(20),null,"处理中",false);
    assertEquals(1,processing.size());assertEquals(report.plusMinutes(20),processing.get("PROCESS").anchor());
    var review=ReminderTiming.repair(report,null,report.plusMinutes(20),report.plusHours(1),"待验收",false);
    assertEquals(1,review.size());assertEquals(report.plusHours(1),review.get("WAIT_REVIEW").anchor());
  }
  @Test void specialFlowsAndTerminalStatesAreExcludedWithoutClockGuessing() {
    for(String state:new String[]{"待派工","待接单","处理中","挂起","待验收","已完成","已撤回","已作废"})
      assertTrue(ReminderTiming.repair(report,report,report,report,state,true).isEmpty());
    assertTrue(ReminderTiming.repair(report,null,null,null,"处理中",false).isEmpty());
    assertTrue(ReminderTiming.repair(report,null,null,null,"挂起",false).isEmpty());
    assertTrue(ReminderTiming.repair(report,report,report,report,"已完成",false).isEmpty());
  }
  @Test void expiryOffsetsCrossMidnightAndDaysAreContinuous24Hours() {
    var expiry=LocalDateTime.of(2026,10,24,0,0);
    assertEquals(LocalDateTime.of(2026,10,23,23,30),new ReminderTiming.Stage("BEFORE_DUE",expiry).trigger(ReminderTiming.seconds(30,"MINUTE")));
    assertEquals(LocalDateTime.of(2026,10,24,1,0),new ReminderTiming.Stage("AFTER_DUE",expiry).trigger(ReminderTiming.seconds(1,"HOUR")));
    assertEquals(86400,ReminderTiming.seconds(1,"DAY"));
  }
  @Test void smtpPasswordsAreAuthenticatedEncryptedAndNeedExplicitKey() {
    String key=java.util.Base64.getEncoder().encodeToString(new byte[32]);var crypto=new NotificationSecret(key);
    String encoded=crypto.encrypt("smtp-test-password");assertFalse(encoded.contains("smtp-test-password"));assertEquals("smtp-test-password",crypto.decrypt(encoded));
    assertNotEquals(encoded,crypto.encrypt("smtp-test-password"));
    assertThrows(IllegalArgumentException.class,()->new NotificationSecret("").encrypt("password"));
    assertThrows(IllegalStateException.class,()->crypto.decrypt(encoded.substring(0,encoded.length()-3)+"AAA"));
  }
}
