package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import com.windasoft.eam.service.system.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InspectionTaskJournal {
  private final InspectionRepository repository;
  private final ObjectMapper json;

  public InspectionTaskJournal(InspectionRepository repository, ObjectMapper json) {
    this.repository = repository;
    this.json = json;
  }

  void event(long tenant, long task, String action, Long actor, String name, Object details) {
    repository.insertTaskEvent(
        IdWorker.getId(), tenant, task, actor, name, action, encode(details));
  }

  void closeTodos(long tenant, long task) {
    repository.completeTodos(tenant, task);
  }

  void notify(
      long tenant,
      long task,
      List<Long> users,
      String kind,
      String title,
      boolean todo,
      boolean once) {
    for (var user : users)
      if (repository.countNotice(tenant, task, user, kind, once) == 0)
        repository.insertInbox(IdWorker.getId(), tenant, task, user, kind, title, todo);
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
