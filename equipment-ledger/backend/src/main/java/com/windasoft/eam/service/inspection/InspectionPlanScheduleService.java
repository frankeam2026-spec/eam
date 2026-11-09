package com.windasoft.eam.service.inspection;

import static com.windasoft.eam.common.inspection.InspectionSupport.*;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.inspection.DailyArrangementRepository;
import com.windasoft.eam.repository.inspection.InspectionRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Owns the schedule cursor lifecycle associated with inspection plan persistence.
 *
 * <p>Daily staffing refresh and periodic cycle reset are deliberately kept distinct: they
 * represent different scheduling rules and must not be unified into a generic recurrence flow.
 */
@Service
public class InspectionPlanScheduleService {
  private final InspectionRepository repository;
  private final DailyArrangementRepository dailyStorage;
  private final InspectionGenerationService generation;
  private final ObjectMapper json;

  public InspectionPlanScheduleService(
      InspectionRepository repository,
      DailyArrangementRepository dailyStorage,
      InspectionGenerationService generation,
      ObjectMapper json) {
    this.repository = repository;
    this.dailyStorage = dailyStorage;
    this.generation = generation;
    this.json = json;
  }

  /**
   * Persists the plan's schedule cursor and performs the appropriate follow-up for that plan kind.
   * The caller owns the surrounding transaction.
   */
  public void maintain(
      String kind,
      long tenant,
      long planId,
      Map<String, Object> data,
      Map<String, Object> existing,
      long actor) {
    if (!kind.endsWith("plans")) return;

    repository.setPlanType(
        tenant, planId, kind.equals("daily-plans") ? null : number(data.get("typeId")));
    var cursors = repository.lockPlanSchedules(tenant, planId);
    LocalDateTime next =
        kind.equals("periodic-plans")
            ? periodicBoundary(data.get("startDate"), false)
            : LocalDate.parse(text(data, "startDate")).atStartOfDay();

    if (cursors.isEmpty()) {
      repository.insertSchedule(IdWorker.getId(), tenant, planId, next, next);
    } else if (kind.equals("periodic-plans")
        && ((repository.countPlanTasks(tenant, planId) == 0
                && !next.equals(dateTime(cursors.getFirst().get("anchor_at"))))
            || (existing != null && periodicScheduleChanged(existing, data, next)))) {
      require(
          repository.countTasksAtOrAfter(tenant, planId, next) == 0,
          com.windasoft.eam.common.MessageContext.text("server.inspection.message0067"));
      repository.resetScheduleDate(next, next, tenant, planId);
    } else if (kind.equals("daily-plans")) {
      LocalDateTime revisit = now().toLocalDate().atStartOfDay();
      if (next.isAfter(revisit)) revisit = next;
      if (dateTime(cursors.getFirst().get("next_at")).isAfter(revisit)) {
        repository.revisitSchedule(revisit, tenant, planId);
      }
    }

    if (kind.equals("daily-plans") && existing != null) {
      generation.refreshAssignments(tenant, planId, actor);
    }
    generation.generate(tenant, planId);
  }

  private boolean periodicScheduleChanged(
      Map<String, Object> existing, Map<String, Object> data, LocalDateTime next) {
    Map<String, Object> before = decode(existing.get("payload"));
    return !periodicBoundary(before.get("startDate"), false).equals(next)
        || !text(before, "typeId").equals(text(data, "typeId"))
        || number(before.get("period")) != number(data.get("period"))
        || !text(before, "unit").equals(text(data, "unit"));
  }

  private Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"), new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.inspection.message0012");
    }
  }
}
