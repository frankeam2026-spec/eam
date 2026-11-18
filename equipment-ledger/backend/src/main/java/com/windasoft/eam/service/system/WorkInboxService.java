package com.windasoft.eam.service.system;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.NotificationMessages;
import com.windasoft.eam.dto.common.SuccessResponse;
import com.windasoft.eam.dto.system.WorkInboxViews;
import com.windasoft.eam.repository.experience.ExperienceInboxRepository;
import com.windasoft.eam.repository.inspection.InspectionInboxRepository;
import com.windasoft.eam.repository.inventory.InventoryInboxRepository;
import com.windasoft.eam.repository.maintenance.MaintenanceInboxRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.repository.system.NotifyMessageRepository;
import com.windasoft.eam.repository.system.WorkInboxRepository;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 工作中心读取已授权给当前用户的消息投影，不把已读当作业务办理完成。 */
@Service
public class WorkInboxService {
  private final WorkInboxRepository repository;
  private final AuthContext auth;
  private final NotifyMessageRepository messages;
  private final ExperienceInboxRepository experience;
  private final RepairInboxRepository repair;
  private final MaintenanceInboxRepository maintenance;
  private final InventoryInboxRepository inventory;
  private final InspectionInboxRepository inspection;

  public WorkInboxService(
      WorkInboxRepository repository,
      AuthContext auth,
      NotifyMessageRepository messages,
      ExperienceInboxRepository experience,
      RepairInboxRepository repair,
      MaintenanceInboxRepository maintenance,
      InventoryInboxRepository inventory,
      InspectionInboxRepository inspection) {
    this.repository = repository;
    this.auth = auth;
    this.messages = messages;
    this.experience = experience;
    this.repair = repair;
    this.maintenance = maintenance;
    this.inventory = inventory;
    this.inspection = inspection;
  }

  public WorkInboxViews.Summary summary() {
    return repository.summary(auth.tenantId(), auth.userId());
  }

  public List<WorkInboxViews.Kind> assistantKinds() {
    return repository.assistantKinds(auth.tenantId(), auth.userId());
  }

  public WorkInboxViews.Page list(
      String channel, String state, String module, String kind, String search, int page, int size) {
    if (!Set.of("todo", "notification").contains(channel)
        || !Set.of("pending", "completed", "unread", "all").contains(state))
      throw new LocalizedArgumentException("server.system.message0032");
    var rows =
        repository.list(
            auth.tenantId(),
            auth.userId(),
            channel,
            state,
            module,
            kind,
            search,
            Math.max(1, page),
            Math.min(100, Math.max(1, size)));
    return new WorkInboxViews.Page(
        rows.data().stream()
            .map(
                r ->
                    r.translated(
                        NotificationMessages.renderTitle(
                            r.title(), r.messageKey(), r.messageArgs())))
            .toList(),
        rows.total());
  }

  /** 公共消息优先更新，保留各表数据库取时及原事务边界；空ID只标记本人的消息，不关闭待办。 */
  public SuccessResponse read(WorkInboxViews.Read body) {
    String module = Objects.toString(body.module(), ""), id = Objects.toString(body.id(), "");
    if (Set.of("inventoryTransfer", "inventoryOutbound", "inventoryReturn").contains(module))
      module = "inventory";
    if (!module.isBlank()
        && !Set.of("repair", "maintenance", "inspection", "inventory", "anomaly", "repairPlan")
            .contains(module)) throw new LocalizedArgumentException("server.system.message0033");
    if (!id.isBlank() && !id.matches("(?:n:)?[0-9]{1,19}"))
      throw new LocalizedArgumentException("server.system.message0034");
    long tenant = auth.tenantId(), user = auth.userId();
    messages.markRead(tenant, user, module, id);
    if (id.startsWith("n:")) return new SuccessResponse(true);
    if (module.isBlank() || module.equals("anomaly") || module.equals("repairPlan"))
      experience.markWorkRead(tenant, user, id, module);
    if (module.isBlank() || module.equals("inventory")) inventory.markWorkRead(tenant, user, id);
    if (module.isBlank() || module.equals("repair")) repair.markWorkRead(tenant, user, id);
    if (module.isBlank() || module.equals("maintenance"))
      maintenance.markWorkRead(tenant, user, id);
    if (module.isBlank() || module.equals("inspection")) inspection.markWorkRead(tenant, user, id);
    return new SuccessResponse(true);
  }
}
