package com.windasoft.eam.scheduler.system;

import com.windasoft.eam.repository.system.ReminderRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class NotificationScheduler {
  private final ReminderRepository repo;private final ReminderService reminders;private final NotificationDeliveryService delivery;
  private final Map<String,Long> cursors=new HashMap<>();
  private final com.windasoft.eam.repository.system.NotificationRetentionRepository retentionRepository;
  private final NotificationRetentionService retention;
  public NotificationScheduler(ReminderRepository repo,ReminderService reminders,NotificationDeliveryService delivery,com.windasoft.eam.repository.system.NotificationRetentionRepository retentionRepository,NotificationRetentionService retention){this.repo=repo;this.reminders=reminders;this.delivery=delivery;this.retentionRepository=retentionRepository;this.retention=retention;}
  @Scheduled(cron="0 15 3 * * *",zone="Asia/Shanghai",scheduler="notificationTaskScheduler")
  public void cleanupDeliveryLogs() {
    for(int batch=0;batch<20;batch++) {
      var rows=retentionRepository.candidates(500);if(rows.isEmpty())return;
      int cleaned=0;
      for(var row:rows)try {
        if(retention.compact(((Number)row.get("tenant_id")).longValue(),((Number)row.get("message_id")).longValue(),((Number)row.get("id")).longValue()))cleaned++;
      } catch(Exception e){LoggerFactory.getLogger(getClass()).error("Notification log cleanup failed for delivery {}",row.get("id"),e);}
      if(cleaned==0 || rows.size()<500)return;
    }
  }
  @Scheduled(fixedDelay=60000,initialDelay=20000,scheduler="notificationTaskScheduler")
  public void scan() {
    for(String module:List.of("repair","inspection","maintenance")) {
      long cursor=cursors.getOrDefault(module,0L);
      for(int batch=0;batch<10;batch++) {
        var rows=repo.ids(module,cursor,200);
        for(var row:rows) {cursor=((Number)row.get("id")).longValue();try{reminders.scan(module,((Number)row.get("tenant_id")).longValue(),cursor);}catch(Exception e){LoggerFactory.getLogger(getClass()).error("Reminder scan failed for {} {}",module,cursor,e);}}
        if(rows.size()<200){cursor=0;break;}
      }
      cursors.put(module,cursor);
    }
  }
  @Scheduled(fixedDelay=5000,initialDelay=25000,scheduler="notificationTaskScheduler")
  public void deliver() {for(var row:repo.ready(100))try{delivery.deliver(((Number)row.get("tenant_id")).longValue(),((Number)row.get("id")).longValue());}catch(Exception e){LoggerFactory.getLogger(getClass()).error("Notification delivery failed",e);}}
}
