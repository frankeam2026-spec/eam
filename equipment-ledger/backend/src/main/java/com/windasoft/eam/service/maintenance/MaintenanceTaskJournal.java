package com.windasoft.eam.service.maintenance;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskEventView;
import com.windasoft.eam.entity.maintenance.MaintenanceEvent;
import com.windasoft.eam.entity.maintenance.MaintenanceInbox;
import com.windasoft.eam.repository.maintenance.MaintenanceEventRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceInboxRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/** 保养事件、待办及公共通知的业务协调；加入调用方的任务事务，不单独提交消息。 */
@Service
public class MaintenanceTaskJournal {
  static final String EXECUTION_NOTICE = "保养待执行";
  private static final String NOTIFICATION_SOURCE = "eam_maintenance_inbox";
  private static final DateTimeFormatter EVENT_TIME =
      DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
  private final MaintenanceEventRepository events;
  private final MaintenanceInboxRepository inbox;
  private final NotificationRepository notifications;
  private final ObjectMapper json;

  public MaintenanceTaskJournal(
      MaintenanceEventRepository events,
      MaintenanceInboxRepository inbox,
      NotificationRepository notifications,
      ObjectMapper json) {
    this.events = events;
    this.inbox = inbox;
    this.notifications = notifications;
    this.json = json;
  }

  void event(long tenant, long task, String action, Long actor, String name, Object data) {
    var event = new MaintenanceEvent();
    event.setId(IdWorker.getId());
    event.setTenantId(tenant);
    event.setTaskId(task);
    event.setActorId(actor);
    event.setActorName(name);
    event.setAction(action);
    event.setDetails(encode(data));
    events.insert(event);
  }

  /** 翻译元数据和消息一次登记后再捕获公共通知；捕获失败由外层任务事务回滚两者。 */
  void notify(long tenant, long task, List<Long> users, String kind, String title, boolean todo) {
    for (var user : users) {
      var message = new MaintenanceInbox();
      message.setId(IdWorker.getId());
      message.setTenantId(tenant);
      message.setTaskId(task);
      message.setUserId(user);
      message.setKind(kind);
      message.setTitle(title);
      message.setTodo(todo);
      message.setMessageKey(NotificationMessages.key(kind));
      message.setMessageArgs(NotificationMessages.arguments(kind, title));
      inbox.insert(message);
      notifications.captureSource(NOTIFICATION_SOURCE, tenant, message.getId());
    }
  }

  void closeTodos(long tenant, long task) {
    inbox.completeTodos(tenant, task);
  }

  void completeForDeletion(long tenant, long task) {
    inbox.completeForDeletion(tenant, task);
  }

  boolean hasExecutionNotice(long tenant, long task) {
    return inbox.hasNotice(tenant, task, EXECUTION_NOTICE);
  }

  /** 保留事件顺序和秒级时间文本；仅审计详情按动作保留动态对象，不暴露持久化实体。 */
  List<MaintenanceTaskEventView> events(long tenant, long task) {
    return events.list(tenant, task).stream().map(this::view).toList();
  }

  private MaintenanceTaskEventView view(MaintenanceEvent event) {
    Map<String, Object> details;
    try {
      details =
          json.readValue(
              Objects.toString(event.getDetails(), "{}"),
              new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception failure) {
      throw new LocalizedArgumentException("server.maintenance.message0009");
    }
    return new MaintenanceTaskEventView(
        event.getAction(), event.getActorName(), event.getCreatedAt().format(EVENT_TIME), details);
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception failure) {
      throw new LocalizedArgumentException("server.inspection.message0012");
    }
  }
}
