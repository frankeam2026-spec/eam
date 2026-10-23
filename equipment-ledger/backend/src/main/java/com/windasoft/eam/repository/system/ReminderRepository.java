package com.windasoft.eam.repository.system;

import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ReminderRepository {
  private final JdbcTemplate db;
  public ReminderRepository(JdbcTemplate db){this.db=db;}
  static String table(String module){return switch(module){case "repair"->"eam_repair_order";case "inspection"->"eam_inspection_task";case "maintenance"->"eam_maintenance_task";default->throw new IllegalArgumentException();};}
  public List<Map<String,Object>> ids(String module,long after,int size) {
    String kind=module.equals("inspection")?"t.kind":"'"+module+"'";
    String states=module.equals("repair")?"'待派工','待接单','处理中','待验收'":"'未开始','待执行','执行中','已过期','待审核','待验收','过期作废'";
    return db.queryForList("SELECT t.tenant_id,t.id FROM "+table(module)+" t WHERE t.id>? AND t.deleted=0 AND t.status IN ("+states+") AND EXISTS(SELECT 1 FROM eam_notify_policy p WHERE p.tenant_id=t.tenant_id AND p.business_kind="+kind+" AND p.enabled=1) ORDER BY t.id LIMIT ?",after,size);
  }
  public Map<String,Object> source(String module,long tenant,long id,boolean lock) {
    var rows=db.queryForList("SELECT * FROM "+table(module)+" WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),tenant,id);
    if(rows.isEmpty())return Map.of();
    var row=rows.getFirst();
    String event=module.equals("repair")?"eam_repair_event":module.equals("inspection")?"eam_inspection_event":"eam_maintenance_event";
    String foreign=module.equals("repair")?"order_id":"task_id";
    row.put("reminder_excluded",db.queryForObject("SELECT EXISTS(SELECT 1 FROM "+event+" WHERE tenant_id=? AND "+foreign+"=? AND action IN ('挂起','转派','验收打回','审核打回'))",Integer.class,tenant,id));
    if(module.equals("inspection")) {
      var execution=db.queryForList("SELECT finished_at FROM eam_inspection_execution WHERE tenant_id=? AND task_id=?",tenant,id);
      if(!execution.isEmpty())row.put("submitted_at",execution.getFirst().get("finished_at"));
    }
    return row;
  }
  public Map<String,Object> plan(long tenant,String kind,long id) {
    var rows=db.queryForList("SELECT name,reminder_mode,reminder_effective_at FROM "+NotificationRepository.planTable(kind)+" WHERE tenant_id=? AND id=?",tenant,id);
    return rows.isEmpty()?Map.of():rows.getFirst();
  }
  public void message(long tenant,String key,String module,long object,long rule,int version,String scene,String subject,String content,long user,String role,LocalDateTime trigger) {
    String delivered="EXISTS(SELECT 1 FROM eam_notify_delivery d WHERE d.message_id=eam_notify_message.id AND d.status='SENT') OR EXISTS(SELECT 1 FROM eam_notify_receipt r WHERE r.message_id=eam_notify_message.id AND r.terminal_status='SENT')";
    db.update("INSERT INTO eam_notify_message(tenant_id,source_key,module,object_id,rule_id,policy_version,scene,subject,content,recipient_id,recipient_kind,trigger_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE subject=IF("+delivered+",subject,VALUES(subject)),content=IF("+delivered+",content,VALUES(content)),policy_version=IF("+delivered+",policy_version,VALUES(policy_version)),trigger_at=IF("+delivered+",trigger_at,VALUES(trigger_at)),recipient_kind=IF("+delivered+",recipient_kind,VALUES(recipient_kind)),cancelled_at=IF("+delivered+",cancelled_at,NULL)",tenant,key,module,object,rule,version,scene,subject,content,user,role,trigger);
  }
  public List<Map<String,Object>> ready(int limit) {
    db.update("UPDATE eam_notify_delivery SET status='UNKNOWN',last_error='发送进程中断，邮件结果待确认',lease_until=NULL WHERE channel='EMAIL' AND status='SENDING' AND lease_until<NOW(6)");
    return db.queryForList("SELECT d.id,m.tenant_id FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id WHERE ((d.status='PENDING' OR (d.status='FAILED' AND d.attempts<5)) AND d.next_attempt_at<=NOW(6)) OR (d.channel<>'EMAIL' AND d.status='SENDING' AND d.lease_until<NOW(6)) ORDER BY d.next_attempt_at,d.id LIMIT ?",limit);
  }
  @org.springframework.transaction.annotation.Transactional
  public Map<String,Object> claim(long tenant,long id) {
    var owner=db.queryForList("SELECT message_id FROM eam_notify_delivery WHERE id=?",Long.class,id);
    if(owner.isEmpty())return Map.of();
    if(db.queryForList("SELECT id FROM eam_notify_message WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,tenant,owner.getFirst()).isEmpty())return Map.of();
    int changed=db.update("UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET d.status='SENDING',d.attempts=d.attempts+1,d.lease_until=DATE_ADD(NOW(6),INTERVAL 2 MINUTE) WHERE m.tenant_id=? AND d.id=? AND (((d.status='PENDING' OR (d.status='FAILED' AND d.attempts<5)) AND d.next_attempt_at<=NOW(6)) OR (d.channel<>'EMAIL' AND d.status='SENDING' AND d.lease_until<NOW(6)))",tenant,id);
    if(changed==0)return Map.of();
    return db.queryForMap("SELECT d.id delivery_id,d.channel,d.attempts,m.*,u.email,u.status user_status,u.deleted user_deleted FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id JOIN eam_sys_user u ON u.id=m.recipient_id AND u.tenant_id=m.tenant_id WHERE m.tenant_id=? AND d.id=?",tenant,id);
  }
  public void finish(long id,int attempt,String status,String error) {
    db.update("UPDATE eam_notify_delivery SET status=?,last_error=?,sent_at=IF(?='SENT',NOW(6),sent_at),lease_until=NULL,next_attempt_at=DATE_ADD(NOW(6),INTERVAL LEAST(3600,POW(2,attempts)*30) SECOND) WHERE id=? AND attempts=? AND status='SENDING'",status,error,status,id,attempt);
  }
  public boolean ruleCurrent(long tenant,long rule) {return db.queryForObject("SELECT COUNT(*) FROM eam_notify_rule r JOIN eam_notify_policy p ON p.id=r.policy_id AND p.tenant_id=r.tenant_id WHERE r.tenant_id=? AND r.id=? AND r.enabled=1 AND r.archived=0 AND p.enabled=1",Integer.class,tenant,rule)==1;}
  public void cancel(long tenant,long message) {db.update("UPDATE eam_notify_message SET cancelled_at=COALESCE(cancelled_at,NOW(6)) WHERE tenant_id=? AND id=?",tenant,message);}
}
