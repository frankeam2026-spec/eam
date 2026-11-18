package com.windasoft.eam.repository.system;

import com.windasoft.eam.dto.system.NotificationContracts;
import com.windasoft.eam.dto.system.NotificationViews;
import com.windasoft.eam.dto.system.PlanReminderBinding;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.springframework.jdbc.core.DataClassRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** 公共通知读模型和既有消息桥接；策略、规则、渠道等配置由各自主仓储负责。 */
@Repository
public class NotificationRepository {
  private final JdbcTemplate db;

  public NotificationRepository(JdbcTemplate db) {
    this.db = db;
  }

  public NotificationViews.Rows<NotificationViews.Delivery> deliveryPage(
      long tenant, String status, String channel, String q, int page, int size) {
    return deliveryPage(
        tenant,
        new NotificationContracts.DeliveryFilter(
            status,
            channel,
            q,
            LocalDateTime.now().minusDays(7),
            LocalDateTime.now().plusDays(1),
            "",
            null,
            true),
        page,
        size);
  }

  public NotificationViews.Rows<NotificationViews.Delivery> deliveryPage(
      long tenant, NotificationContracts.DeliveryFilter f, int page, int size) {
    String business = "CASE WHEN m.module='inspection' THEN t.kind ELSE m.module END";
    String from =
        " FROM eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id JOIN"
            + " eam_sys_user u ON u.id=m.recipient_id AND u.tenant_id=m.tenant_id LEFT JOIN"
            + " eam_inspection_task t ON m.module='inspection' AND t.id=m.object_id AND"
            + " t.tenant_id=m.tenant_id WHERE m.tenant_id=? AND m.created_at>=? AND m.created_at<?";
    var filters = new ArrayList<Object>(List.of(tenant, f.from(), f.until()));
    if (!f.status().isBlank()) {
      from += " AND d.status=?";
      filters.add(f.status());
    } else if (!f.showInactive()) from += " AND d.status NOT IN ('DISABLED','NOT_INTEGRATED')";
    if (!f.channel().isBlank()) {
      from += " AND d.channel=?";
      filters.add(f.channel());
    }
    if (!f.businessKind().isBlank()) {
      from += " AND " + business + "=?";
      filters.add(f.businessKind());
    }
    if (f.recipientId() != null) {
      from += " AND m.recipient_id=?";
      filters.add(f.recipientId());
    }
    if (!f.query().isBlank()) {
      from += " AND (LOCATE(?,m.subject)>0 OR LOCATE(?,u.display_name)>0)";
      filters.add(f.query());
      filters.add(f.query());
    }
    Object[] filter = filters.toArray();
    long total = db.queryForObject("SELECT COUNT(*)" + from, Long.class, filter);
    var args = new ArrayList<>(Arrays.asList(filter));
    args.add(size);
    args.add((long) (page - 1) * size);
    return new NotificationViews.Rows<>(
        db.query(
            "SELECT CAST(d.id AS CHAR) id,d.channel,d.status,d.attempts,d.last_error"
                + " lastError,d.sent_at sentAt,m.subject,m.module,"
                + business
                + " businessKind,CAST(m.object_id AS CHAR) objectId,m.recipient_kind"
                + " recipientKind,u.display_name recipient,m.created_at"
                + " createdAt,COALESCE(m.read_at,CASE m.legacy_source WHEN 'eam_repair_inbox' THEN"
                + " (SELECT old.read_at FROM eam_repair_inbox old WHERE old.tenant_id=m.tenant_id"
                + " AND old.id=m.legacy_id) WHEN 'eam_inspection_inbox' THEN (SELECT old.read_at"
                + " FROM eam_inspection_inbox old WHERE old.tenant_id=m.tenant_id AND"
                + " old.id=m.legacy_id) WHEN 'eam_maintenance_inbox' THEN (SELECT old.read_at FROM"
                + " eam_maintenance_inbox old WHERE old.tenant_id=m.tenant_id AND"
                + " old.id=m.legacy_id) WHEN 'eam_experience_inbox' THEN (SELECT old.read_at FROM"
                + " eam_experience_inbox old WHERE old.tenant_id=m.tenant_id AND"
                + " old.id=m.legacy_id) WHEN 'inv_inbox' THEN (SELECT old.read_at FROM inv_inbox"
                + " old WHERE old.tenant_id=m.tenant_id AND old.id=m.legacy_id) END) readAt"
                + from
                + " ORDER BY m.created_at DESC,d.id DESC LIMIT ? OFFSET ?",
            DataClassRowMapper.newInstance(NotificationViews.Delivery.class),
            args.toArray()),
        total);
  }

  public NotificationViews.Rows<NotificationViews.Recipient> deliveryRecipients(
      long tenant, String q, int page, int size) {
    String from =
        " FROM eam_sys_user u WHERE u.tenant_id=? AND LOCATE(?,u.display_name)>0 AND EXISTS(SELECT"
            + " 1 FROM eam_notify_message m WHERE m.tenant_id=u.tenant_id AND m.recipient_id=u.id)";
    return new NotificationViews.Rows<>(
        db.query(
            "SELECT CAST(u.id AS CHAR) id,u.display_name name"
                + from
                + " ORDER BY u.display_name,u.id LIMIT ? OFFSET ?",
            DataClassRowMapper.newInstance(NotificationViews.Recipient.class),
            tenant,
            q,
            size,
            (long) (page - 1) * size),
        db.queryForObject("SELECT COUNT(*)" + from, Long.class, tenant, q));
  }

  @Transactional
  public int retry(long tenant, long id, int attempts) {
    var owner =
        db.queryForList("SELECT message_id FROM eam_notify_delivery WHERE id=?", Long.class, id);
    if (owner.isEmpty()
        || db.queryForList(
                "SELECT id FROM eam_notify_message WHERE tenant_id=? AND id=? FOR UPDATE",
                Long.class,
                tenant,
                owner.getFirst())
            .isEmpty()) return 0;
    return db.update(
        "UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET"
            + " d.status='PENDING',d.next_attempt_at=NOW(6),d.lease_until=NULL WHERE m.tenant_id=?"
            + " AND d.id=? AND d.attempts=? AND d.status IN ('FAILED','MISSING_ADDRESS','DISABLED')"
            + " AND m.cancelled_at IS NULL AND d.channel<>'APP'",
        tenant,
        id,
        attempts);
  }

  @Transactional
  public void captureSource(String source, long tenant, long id) {
    String object, module;
    switch (source) {
      case "eam_repair_inbox" -> {
        object = "i.order_id";
        module = "'repair'";
      }
      case "eam_inspection_inbox" -> {
        object = "i.task_id";
        module = "'inspection'";
      }
      case "eam_maintenance_inbox" -> {
        object = "i.task_id";
        module = "'maintenance'";
      }
      case "eam_experience_inbox" -> {
        object = "i.object_id";
        module = "i.object_kind";
      }
      case "inv_inbox" -> {
        object = "i.document_id";
        module =
            "CASE i.route_kind WHEN 'returns' THEN 'inventoryReturn' WHEN 'transfers' THEN"
                + " 'inventoryTransfer' WHEN 'outbounds' THEN 'inventoryOutbound' ELSE 'inventory'"
                + " END";
      }
      default -> throw new IllegalArgumentException("Unsupported notification source");
    }
    db.update(
        "INSERT IGNORE INTO"
            + " eam_notify_message(tenant_id,source_key,module,object_id,legacy_source,legacy_id,subject,content,message_key,message_args,recipient_id)"
            + " SELECT i.tenant_id,CONCAT(?,':',i.id),"
            + module
            + ","
            + object
            + ",?,i.id,CONCAT(i.kind,'：',i.title),i.title,i.message_key,i.message_args,i.user_id"
            + " FROM "
            + source
            + " i WHERE i.tenant_id=? AND i.id=?",
        source,
        source,
        tenant,
        id);
    db.queryForList(
        "SELECT id FROM eam_notify_message WHERE tenant_id=? AND source_key=? FOR UPDATE",
        Long.class,
        tenant,
        source + ":" + id);
    db.update(
        "INSERT IGNORE INTO eam_notify_delivery(message_id,channel,status,sent_at) SELECT"
            + " id,'SYSTEM','SENT',NOW(6) FROM eam_notify_message WHERE tenant_id=? AND"
            + " source_key=? AND NOT EXISTS(SELECT 1 FROM eam_notify_receipt r WHERE"
            + " r.message_id=eam_notify_message.id AND r.channel='SYSTEM')",
        tenant,
        source + ":" + id);
    deliveries(tenant, source + ":" + id);
  }

  @Transactional
  public void deliveries(long tenant, String key) {
    db.queryForList(
        "SELECT id FROM eam_notify_message WHERE tenant_id=? AND source_key=? FOR UPDATE",
        Long.class,
        tenant,
        key);
    for (String channel : List.of("SYSTEM", "EMAIL", "APP"))
      db.update(
          "INSERT IGNORE INTO eam_notify_delivery(message_id,channel,status,last_error) SELECT"
              + " id,?,IF(?='APP','NOT_INTEGRATED','PENDING'),IF(?='APP','App推送未接入',NULL) FROM"
              + " eam_notify_message WHERE tenant_id=? AND source_key=? AND NOT EXISTS(SELECT 1"
              + " FROM eam_notify_receipt r WHERE r.message_id=eam_notify_message.id AND"
              + " r.channel=?)",
          channel,
          channel,
          channel,
          tenant,
          key,
          channel);
    db.update(
        "UPDATE eam_notify_delivery d JOIN eam_notify_message m ON m.id=d.message_id SET"
            + " d.status='PENDING',d.next_attempt_at=NOW(6) WHERE m.tenant_id=? AND m.source_key=?"
            + " AND m.cancelled_at IS NULL AND d.status='CANCELLED' AND d.channel<>'APP'",
        tenant,
        key);
  }

  public static String planTable(String kind) {
    return switch (kind) {
      case "daily" -> "eam_inspection_daily_plan";
      case "periodic" -> "eam_inspection_periodic_plan";
      case "maintenance" -> "eam_maintenance_plan";
      default -> throw new IllegalArgumentException("Invalid plan kind");
    };
  }

  public PlanReminderBinding binding(long tenant, String kind, long id) {
    return db.queryForObject(
        "SELECT reminder_mode reminderMode,CAST(reminder_policy_id AS CHAR)"
            + " reminderPolicyId,reminder_effective_at reminderEffectiveAt,(SELECT name FROM"
            + " eam_notify_policy p WHERE p.id=reminder_policy_id AND p.tenant_id=?)"
            + " reminderPolicyName FROM "
            + planTable(kind)
            + " WHERE tenant_id=? AND id=?",
        DataClassRowMapper.newInstance(PlanReminderBinding.class),
        tenant,
        tenant,
        id);
  }

  public void binding(long tenant, String kind, long id, String mode, Long policy) {
    db.update(
        "UPDATE "
            + planTable(kind)
            + " SET reminder_effective_at=IF(reminder_mode<>? OR"
            + " NOT(reminder_policy_id<=>?),NOW(6),reminder_effective_at),reminder_mode=?,reminder_policy_id=?"
            + " WHERE tenant_id=? AND id=?",
        mode,
        policy,
        mode,
        policy,
        tenant,
        id);
  }

  public static boolean yes(Object v) {
    return Boolean.TRUE.equals(v) || "1".equals(String.valueOf(v));
  }
}
