package com.windasoft.eam.service.system;

import com.windasoft.eam.repository.system.WorkInboxRepository;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class WorkInboxService {
  private final WorkInboxRepository repository;
  private final AuthContext auth;

  public WorkInboxService(WorkInboxRepository repository, AuthContext auth) {
    this.repository = repository;
    this.auth = auth;
  }

  public Object summary() {
    return repository.summary(auth.tenantId(), auth.userId());
  }

  public Object list(
      String channel, String state, String module, String kind, String search, int page, int size) {
    if (!Set.of("todo", "notification").contains(channel)
        || !Set.of("pending", "completed", "unread", "all").contains(state))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0032");
    return com.windasoft.eam.common.NotificationMessages.render(
        repository.list(
            auth.tenantId(),
            auth.userId(),
            channel,
            state,
            module,
            kind,
            search,
            Math.max(1, page),
            Math.min(100, Math.max(1, size))));
  }

  public Object read(Map<String, Object> body) {
    String module = Objects.toString(body.get("module"), "");
    String id = Objects.toString(body.get("id"), "");
    if (Set.of("inventoryTransfer", "inventoryOutbound", "inventoryReturn").contains(module))
      module = "inventory";
    if (!module.isBlank()
        && !Set.of("repair", "maintenance", "inspection", "inventory", "anomaly", "repairPlan")
            .contains(module))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0033");
    if (!id.isBlank() && !id.matches("(?:n:)?[0-9]{1,19}"))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0034");
    return repository.read(auth.tenantId(), auth.userId(), module, id);
  }
}
