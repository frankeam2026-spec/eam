package com.windasoft.eam.repository.system;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Notification SQL only. Business documents remain in their own repositories/tables. */
@Repository
public class NotificationRepository {
  private final JdbcTemplate db;
  public NotificationRepository(JdbcTemplate db) { this.db = db; }
  public void savePolicy(long tenant,long id,String name,String kind,boolean enabled,boolean insert) {
    if(insert) db.update("INSERT INTO eam_notify_policy(id,tenant_id,name,business_kind,enabled) VALUES(?,?,?,?,?)",id,tenant,name,kind,enabled);
    else db.update("UPDATE eam_notify_policy SET name=?,effective_at=IF(enabled<>?,NOW(6),effective_at),enabled=?,row_version=row_version+1,updated_at=NOW(6) WHERE tenant_id=? AND id=?",name,enabled,enabled,tenant,id);
  }
  public void saveRule(long tenant,long policy,long id,String scene,int value,String unit,boolean enabled,int sort,boolean changed) {
    db.update("INSERT INTO eam_notify_rule(id,tenant_id,policy_id,scene,offset_value,offset_unit,enabled,sort_no) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE scene=VALUES(scene),offset_value=VALUES(offset_value),offset_unit=VALUES(offset_unit),enabled=VALUES(enabled),sort_no=VALUES(sort_no),effective_at=IF(?,NOW(6),effective_at)",id,tenant,policy,scene,value,unit,enabled,sort,changed);
    db.update("DELETE FROM eam_notify_rule_person WHERE tenant_id=? AND rule_id=?",tenant,id);
  }
  public void person(long tenant,long rule,long user,String kind) { db.update("INSERT INTO eam_notify_rule_person(tenant_id,rule_id,user_id,recipient_kind) VALUES(?,?,?,?)",tenant,rule,user,kind); }
  public void disableRule(long tenant,long id) { db.update("UPDATE eam_notify_rule SET enabled=0,archived=1,effective_at=NOW(6) WHERE tenant_id=? AND id=?",tenant,id); }
  public List<Map<String,Object>> defaults(long tenant) { return db.queryForList("SELECT business_kind businessKind,CAST(policy_id AS CHAR) policyId,row_version rowVersion FROM eam_notify_default WHERE tenant_id=?",tenant); }
  public Map<String,Object> lockDefault(long tenant,String kind) {
    db.update("INSERT IGNORE INTO eam_notify_default(tenant_id,business_kind) VALUES(?,?)",tenant,kind);
    return db.queryForMap("SELECT * FROM eam_notify_default WHERE tenant_id=? AND business_kind=? FOR UPDATE",tenant,kind);
  }
  public void setDefault(long tenant,String kind,Long policy) { db.update("UPDATE eam_notify_default SET policy_id=?,row_version=row_version+1,effective_at=NOW(6) WHERE tenant_id=? AND business_kind=?",policy,tenant,kind); }
  public void saveChannel(long tenant,com.windasoft.eam.dto.system.NotificationContracts.Channel c,String secret) {
    db.update("UPDATE eam_notify_channel SET system_enabled=?,email_enabled=?,smtp_host=?,smtp_port=?,smtp_security=?,smtp_username=?,smtp_secret=?,sender_address=?,public_url=?,row_version=row_version+1,updated_at=NOW(6) WHERE tenant_id=?",c.systemEnabled(),c.emailEnabled(),c.smtpHost(),c.smtpPort(),c.smtpSecurity(),c.smtpUsername(),secret,c.senderAddress(),c.publicUrl(),tenant);
  }
  public Map<String,Object> deliveryPage(long tenant,String status,String channel,String q,int page,int size) {
    return deliveryPage(tenant,new com.windasoft.eam.dto.system.NotificationContracts.DeliveryFilter(status,channel,q,java.time.LocalDateTime.now().minusDays(7),java.time.LocalDateTime.now().plusDays(1),"",null,true),page,size);
  }
  public Map<String,Object> deliveryPage(long tenant,com.windasoft.eam.dto.system.NotificationContracts.DeliveryFilter f,int page,int size) {
    String business="CASE WHEN m.module='inspection' THEN t.kind ELSE m.module END";
    String from=" FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id JOIN eam_sys_user u ON u.id=m.recipient_id AND u.tenant_id=m.tenant_id LEFT JOIN eam_inspection_task t ON m.module='inspection' AND t.id=m.object_id AND t.tenant_id=m.tenant_id WHERE m.tenant_id=? AND m.created_at>=? AND m.created_at<?";
    var filters=new ArrayList<Object>(List.of(tenant,f.from(),f.until()));
    if(!f.status().isBlank()){from+=" AND d.status=?";filters.add(f.status());}
    else if(!f.showInactive())from+=" AND d.status NOT IN ('DISABLED','NOT_INTEGRATED')";
    if(!f.channel().isBlank()){from+=" AND d.channel=?";filters.add(f.channel());}
    if(!f.businessKind().isBlank()){from+=" AND "+business+"=?";filters.add(f.businessKind());}
    if(f.recipientId()!=null){from+=" AND m.recipient_id=?";filters.add(f.recipientId());}
    if(!f.query().isBlank()){from+=" AND (LOCATE(?,m.subject)>0 OR LOCATE(?,u.display_name)>0)";filters.add(f.query());filters.add(f.query());}
    Object[] filter=filters.toArray();
    long total=db.queryForObject("SELECT COUNT(*)"+from,Long.class,filter);
    var args=new ArrayList<>(Arrays.asList(filter)); args.add(size);args.add((long)(page-1)*size);
    return Map.of("total",total,"data",db.queryForList("SELECT CAST(d.id AS CHAR) id,d.channel,d.status,d.attempts,d.last_error lastError,d.sent_at sentAt,m.subject,m.module,"+business+" businessKind,CAST(m.object_id AS CHAR) objectId,m.recipient_kind recipientKind,u.display_name recipient,m.created_at createdAt,COALESCE(m.read_at,CASE m.legacy_source WHEN 'eam_repair_inbox' THEN (SELECT old.read_at FROM eam_repair_inbox old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) WHEN 'eam_inspection_inbox' THEN (SELECT old.read_at FROM eam_inspection_inbox old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) WHEN 'eam_maintenance_inbox' THEN (SELECT old.read_at FROM eam_maintenance_inbox old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) WHEN 'eam_experience_inbox' THEN (SELECT old.read_at FROM eam_experience_inbox old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) WHEN 'inv_inbox' THEN (SELECT old.read_at FROM inv_inbox old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) END) readAt"+from+" ORDER BY m.created_at DESC,d.id DESC LIMIT ? OFFSET ?",args.toArray()));
  }
  public Map<String,Object> deliveryRecipients(long tenant,String q,int page,int size) {
    String from=" FROM eam_sys_user u WHERE u.tenant_id=? AND LOCATE(?,u.display_name)>0 AND EXISTS(SELECT 1 FROM eam_notify_message m WHERE m.tenant_id=u.tenant_id AND m.recipient_id=u.id)";
    return Map.of("data",db.queryForList("SELECT CAST(u.id AS CHAR) id,u.display_name name"+from+" ORDER BY u.display_name,u.id LIMIT ? OFFSET ?",tenant,q,size,(long)(page-1)*size),"total",db.queryForObject("SELECT COUNT(*)"+from,Long.class,tenant,q));
  }
  @org.springframework.transaction.annotation.Transactional
  public int retry(long tenant,long id,int attempts) {
    var owner=db.queryForList("SELECT message_id FROM eam_notify_delivery WHERE id=?",Long.class,id);
    if(owner.isEmpty() || db.queryForList("SELECT id FROM eam_notify_message WHERE tenant_id=? AND id=? FOR UPDATE",Long.class,tenant,owner.getFirst()).isEmpty())return 0;
    return db.update("UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET d.status='PENDING',d.next_attempt_at=NOW(6),d.lease_until=NULL WHERE m.tenant_id=? AND d.id=? AND d.attempts=? AND d.status IN ('FAILED','MISSING_ADDRESS','DISABLED') AND m.cancelled_at IS NULL AND d.channel<>'APP'",tenant,id,attempts); }

  @org.springframework.transaction.annotation.Transactional
  public void captureSource(String source, long tenant, long id) {
    String object, module;
    switch (source) {
      case "eam_repair_inbox" -> { object="i.order_id"; module="'repair'"; }
      case "eam_inspection_inbox" -> { object="i.task_id"; module="'inspection'"; }
      case "eam_maintenance_inbox" -> { object="i.task_id"; module="'maintenance'"; }
      case "eam_experience_inbox" -> { object="i.object_id"; module="i.object_kind"; }
      case "inv_inbox" -> { object="i.document_id"; module="CASE i.route_kind WHEN 'returns' THEN 'inventoryReturn' WHEN 'transfers' THEN 'inventoryTransfer' WHEN 'outbounds' THEN 'inventoryOutbound' ELSE 'inventory' END"; }
      default -> throw new IllegalArgumentException("Unsupported notification source");
    }
    db.update("INSERT IGNORE INTO eam_notify_message(tenant_id,source_key,module,object_id,legacy_source,legacy_id,subject,content,message_key,message_args,recipient_id) "
        + "SELECT i.tenant_id,CONCAT(?,':',i.id)," + module + "," + object
        + ",?,i.id,CONCAT(i.kind,'：',i.title),i.title,i.message_key,i.message_args,i.user_id FROM " + source
        + " i WHERE i.tenant_id=? AND i.id=?", source, source, tenant, id);
    db.queryForList("SELECT id FROM eam_notify_message WHERE tenant_id=? AND source_key=? FOR UPDATE",Long.class,tenant,source+":"+id);
    db.update("INSERT IGNORE INTO eam_notify_delivery(message_id,channel,status,sent_at) SELECT id,'SYSTEM','SENT',NOW(6) FROM eam_notify_message WHERE tenant_id=? AND source_key=? AND NOT EXISTS(SELECT 1 FROM eam_notify_receipt r WHERE r.message_id=eam_notify_message.id AND r.channel='SYSTEM')", tenant, source+":"+id);
    deliveries(tenant, source+":"+id);
  }

  @org.springframework.transaction.annotation.Transactional
  public void deliveries(long tenant, String key) {
    db.queryForList("SELECT id FROM eam_notify_message WHERE tenant_id=? AND source_key=? FOR UPDATE",Long.class,tenant,key);
    for (String channel : List.of("SYSTEM", "EMAIL", "APP"))
      db.update("INSERT IGNORE INTO eam_notify_delivery(message_id,channel,status,last_error) SELECT id,?,IF(?='APP','NOT_INTEGRATED','PENDING'),IF(?='APP','App推送未接入',NULL) FROM eam_notify_message WHERE tenant_id=? AND source_key=? AND NOT EXISTS(SELECT 1 FROM eam_notify_receipt r WHERE r.message_id=eam_notify_message.id AND r.channel=?)",
          channel, channel, channel, tenant, key,channel);
    db.update("UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET d.status='PENDING',d.next_attempt_at=NOW(6) WHERE m.tenant_id=? AND m.source_key=? AND m.cancelled_at IS NULL AND d.status='CANCELLED' AND d.channel<>'APP'",tenant,key);
  }

  public Map<String,Object> channel(long tenant, boolean lock) {
    db.update("INSERT IGNORE INTO eam_notify_channel(tenant_id) VALUES(?)",tenant);
    return db.queryForMap("SELECT * FROM eam_notify_channel WHERE tenant_id=?"+(lock?" FOR UPDATE":""),tenant);
  }
  public List<Map<String,Object>> policyRows(long tenant, String kind, String q, int page, int size) {
    return db.queryForList("SELECT CAST(id AS CHAR) id,name,business_kind businessKind,enabled,row_version rowVersion,effective_at effectiveAt FROM eam_notify_policy WHERE tenant_id=? AND (?='' OR business_kind=?) AND LOCATE(?,name)>0 ORDER BY updated_at DESC,id DESC LIMIT ? OFFSET ?",tenant,kind,kind,q,size,(long)(page-1)*size);
  }
  public Map<String,Object> policyChoices(long tenant,String kind,String q,int page,int size) {
    return Map.of("data",db.queryForList("SELECT CAST(id AS CHAR) id,name,enabled FROM eam_notify_policy WHERE tenant_id=? AND business_kind=? AND enabled=1 AND LOCATE(?,name)>0 ORDER BY name,id LIMIT ? OFFSET ?",tenant,kind,q,size,(long)(page-1)*size),"page",Map.of("total",db.queryForObject("SELECT COUNT(*) FROM eam_notify_policy WHERE tenant_id=? AND business_kind=? AND enabled=1 AND LOCATE(?,name)>0",Long.class,tenant,kind,q)));
  }
  public long policyCount(long tenant, String kind, String q) {
    return db.queryForObject("SELECT COUNT(*) FROM eam_notify_policy WHERE tenant_id=? AND (?='' OR business_kind=?) AND LOCATE(?,name)>0",Long.class,tenant,kind,kind,q);
  }
  public Map<String,Object> policy(long tenant,long id,boolean lock) {
    var rows=db.queryForList("SELECT * FROM eam_notify_policy WHERE tenant_id=? AND id=?"+(lock?" FOR UPDATE":""),tenant,id);
    return rows.isEmpty()?null:rows.getFirst();
  }
  public List<Map<String,Object>> rules(long tenant,long policy) {
    return db.queryForList("SELECT CAST(id AS CHAR) id,scene,offset_value value,offset_unit unit,enabled,effective_at effectiveAt FROM eam_notify_rule WHERE tenant_id=? AND policy_id=? AND archived=0 ORDER BY CASE scene WHEN 'BEFORE_DUE' THEN 0 WHEN 'AFTER_DUE' THEN 0 WHEN 'WAIT_DISPATCH' THEN 1 WHEN 'REPORT_UNCLAIMED' THEN 2 WHEN 'WAIT_CLAIM' THEN 3 WHEN 'PROCESS' THEN 4 ELSE 5 END,IF(scene='BEFORE_DUE',-1,1)*offset_value*CASE offset_unit WHEN 'MINUTE' THEN 60 WHEN 'HOUR' THEN 3600 ELSE 86400 END,sort_no,id",tenant,policy);
  }
  public List<Map<String,Object>> people(long tenant,long policy) {
    return db.queryForList("SELECT CAST(p.rule_id AS CHAR) ruleId,CAST(p.user_id AS CHAR) userId,p.recipient_kind recipientKind,u.display_name name FROM eam_notify_rule_person p JOIN eam_notify_rule r ON r.id=p.rule_id AND r.tenant_id=p.tenant_id JOIN eam_sys_user u ON u.id=p.user_id AND u.tenant_id=p.tenant_id WHERE p.tenant_id=? AND r.policy_id=?",tenant,policy);
  }
  public List<Map<String,Object>> users(long tenant,String q,int page,int size) {
    return db.queryForList("SELECT CAST(id AS CHAR) id,display_name name,email FROM eam_sys_user WHERE tenant_id=? AND deleted=0 AND status=1 AND (LOCATE(?,display_name)>0 OR LOCATE(?,account)>0) ORDER BY display_name,id LIMIT ? OFFSET ?",tenant,q,q,size,(long)(page-1)*size);
  }
  public long userCount(long tenant,String q) {
    return db.queryForObject("SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND deleted=0 AND status=1 AND (LOCATE(?,display_name)>0 OR LOCATE(?,account)>0)",Long.class,tenant,q,q);
  }
  public boolean activeUser(long tenant,long user) {
    return db.queryForObject("SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND status=1",Integer.class,tenant,user)==1;
  }
  public static String planTable(String kind) {
    return switch(kind) { case "daily" -> "eam_inspection_daily_plan"; case "periodic" -> "eam_inspection_periodic_plan"; case "maintenance" -> "eam_maintenance_plan"; default -> throw new IllegalArgumentException("Invalid plan kind"); };
  }
  public Map<String,Object> binding(long tenant,String kind,long id) {
    return db.queryForMap("SELECT reminder_mode reminderMode,CAST(reminder_policy_id AS CHAR) reminderPolicyId,reminder_effective_at reminderEffectiveAt,(SELECT name FROM eam_notify_policy p WHERE p.id=reminder_policy_id AND p.tenant_id=?) reminderPolicyName FROM "+planTable(kind)+" WHERE tenant_id=? AND id=?",tenant,tenant,id);
  }
  public void binding(long tenant,String kind,long id,String mode,Long policy) {
    db.update("UPDATE "+planTable(kind)+" SET reminder_effective_at=IF(reminder_mode<>? OR NOT(reminder_policy_id<=>?),NOW(6),reminder_effective_at),reminder_mode=?,reminder_policy_id=? WHERE tenant_id=? AND id=?",mode,policy,mode,policy,tenant,id);
  }
  public Map<String,Object> effectivePolicy(long tenant,String kind,Long plan) {
    String mode="DEFAULT"; Long selected=null;
    if(plan!=null) { var b=binding(tenant,kind,plan); mode=Objects.toString(b.get("reminderMode"),"DEFAULT"); if(b.get("reminderPolicyId")!=null)selected=Long.valueOf(b.get("reminderPolicyId").toString()); }
    if(mode.equals("OFF")) return Map.of("off",true);
    if(!mode.equals("POLICY")) {
      var defaults=db.queryForList("SELECT policy_id,effective_at FROM eam_notify_default WHERE tenant_id=? AND business_kind=?",tenant,kind);
      if(defaults.isEmpty() || defaults.getFirst().get("policy_id")==null) return Map.of();
      selected=((Number)defaults.getFirst().get("policy_id")).longValue();
      var p=policy(tenant,selected,false);
      if(p!=null && p.get("effective_at") instanceof java.sql.Timestamp t && defaults.getFirst().get("effective_at") instanceof java.sql.Timestamp d && d.after(t))p.put("effective_at",d);
      return p==null || !yes(p.get("enabled")) ? Map.of():p;
    }
    var p=selected==null?null:policy(tenant,selected,false);
    return p==null || !yes(p.get("enabled")) ? Map.of():p;
  }
  public boolean replacesLegacy(long tenant,String kind,long plan) { return !effectivePolicy(tenant,kind,plan).isEmpty(); }
  public static boolean yes(Object v) { return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v)); }
}
