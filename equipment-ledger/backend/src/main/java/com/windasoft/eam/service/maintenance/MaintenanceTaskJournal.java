package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.maintenance.MaintenanceRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceTaskJournal {
  private final MaintenanceRepository repository;
  private final ObjectMapper json;

  public MaintenanceTaskJournal(MaintenanceRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  void event(long tenant, long task, String action, Long actor, String name, Object data) {
    repository.insertTaskEvent(IdWorker.getId(), tenant, task, actor, name, action, encode(data));
  }

  void notify(long tenant, long task, List<Long> users, String kind, String title, boolean todo) {
    for (var user : users)
      repository.insertInbox(IdWorker.getId(), tenant, task, user, kind, title, todo);
  }

  void closeTodos(long tenant, long task) {
    repository.completeTodos(tenant, task);
  }

  public String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
