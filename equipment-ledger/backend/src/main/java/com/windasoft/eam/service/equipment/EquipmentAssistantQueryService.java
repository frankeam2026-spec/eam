package com.windasoft.eam.service.equipment;

import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.*;
import com.windasoft.eam.dto.equipment.EquipmentAssistantResult;
import com.windasoft.eam.repository.equipment.EquipmentAssistantRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDate;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 设备自然语言助手查询的参数约束、时间范围解析和只读查询编排。 */
@Service
public class EquipmentAssistantQueryService {
  private final EquipmentAssistantRepository assistantRepository;
  private final EquipmentQueryRepository equipmentRepository;
  private final AuthContext auth;
  private final PermissionService permissions;

  public EquipmentAssistantQueryService(
      EquipmentAssistantRepository assistantRepository,
      EquipmentQueryRepository equipmentRepository,
      AuthContext auth,
      PermissionService permissions) {
    this.assistantRepository = assistantRepository;
    this.equipmentRepository = equipmentRepository;
    this.auth = auth;
    this.permissions = permissions;
  }

  private void requireVisibleEquipment(long id) {
    var equipment = equipmentRepository.find(auth.tenantId(), id);
    if (equipment == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0064");
    permissions.checkEquipment(equipment, "查看");
  }

  public EquipmentAssistantResult query(EquipmentAssistantQuery query) {
    permissions.require("equipment", "查看");
    validateAssistant(query);
    if (query.equipmentId() != null) {
      requireVisibleEquipment(query.equipmentId());
    }
    var now = java.time.LocalDateTime.now(java.time.ZoneId.of("Asia/Shanghai")).withNano(0);
    var range = assistantRange(query, now.toLocalDate());
    var scope = Objects.requireNonNull(permissions.equipment("查看"));
    var ambiguous =
        assistantRepository.ambiguousName(auth.tenantId(), auth.userId(), scope, query, now, range);
    if (ambiguous.isPresent()) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.equipment.assistant.ambiguous", ambiguous.get().label());
    }
    return new EquipmentAssistantResult(
        assistantRepository.execute(auth.tenantId(), auth.userId(), scope, query, now, range),
        now,
        range);
  }

  private static void assistantValid(boolean condition) {
    if (!condition) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.equipment.assistant.invalid");
    }
  }

  private static void validateAssistant(EquipmentAssistantQuery q) {
    assistantValid(q != null);
    assistantValid(
        q.operation() != null
            && q.aggregate() != null
            && q.direction() != null
            && q.period() != null
            && q.bucket() != null
            && q.groupBy() != null
            && q.where() != null);
    assistantValid(
        q.page() >= 1
            && q.page() <= 1_000_000
            && Set.of(20, 50, 100).contains(q.size())
            && q.topN() >= 0
            && q.topN() <= 100
            && (q.equipmentId() == null || q.equipmentId() > 0));
    assistantValid(
        q.groupBy().size() <= 2 && new HashSet<>(q.groupBy()).size() == q.groupBy().size());
    for (var field : q.groupBy()) {
      assistantValid(field != null && field.kind() == Kind.TEXT);
    }
    assistantValid(
        q.aggregate() == Aggregate.DISTINCT
            ? q.measure() != null && Set.of(Kind.TEXT, Kind.ID).contains(q.measure().kind())
            : q.measure() == null);
    assistantValid(
        q.timeField() == null || Set.of(Kind.DATE, Kind.DATETIME).contains(q.timeField().kind()));
    assistantValid(q.period() == Period.ALL || q.timeField() != null);
    assistantValid((q.period() == Period.CUSTOM) == (q.customRange() != null));
    if (q.operation() == Operation.COUNT) {
      assistantValid(q.groupBy().isEmpty() && q.topN() == 0 && q.sortBy() == null);
    }
    if (q.operation() == Operation.LIST) {
      assistantValid(q.groupBy().isEmpty() && q.aggregate() == Aggregate.COUNT);
    }
    if (q.operation() == Operation.GROUP) {
      assistantValid(!q.groupBy().isEmpty());
    }
    if (q.operation() == Operation.TREND) {
      assistantValid(q.timeField() != null);
    }
    if (q.sortBy() instanceof FieldSort(Field field)) {
      assistantValid(
          field != null
              && (q.operation() == Operation.LIST || q.groupBy().contains(field)));
    } else if (q.sortBy() instanceof ResultSort sort) {
      assistantValid(
          q.operation() != Operation.LIST
              && (sort != ResultSort.PERIOD || q.operation() == Operation.TREND));
    }
    validateAssistantFilter(q.where(), 0, new int[] {0});
  }

  private static void validateAssistantFilter(Filter filter, int depth, int[] count) {
    assistantValid(filter != null && depth <= 4 && ++count[0] <= 24);
    if (filter instanceof All) {
      assistantValid(depth == 0);
      return;
    }
    if (filter instanceof Junction(Logical operator, List<Filter> children)) {
      assistantValid(
          operator != null
              && children != null
              && !children.isEmpty()
              && children.size() <= 12
              && (operator != Logical.NOT || children.size() == 1));
      for (var child : children) {
        validateAssistantFilter(child, depth + 1, count);
      }
      return;
    }
    var condition = (Condition) filter;
    assistantValid(
        condition.field() != null
            && condition.operator() != null
            && condition.values() != null
            && condition.values().size() <= 20);
    if (Set.of(Comparison.EMPTY, Comparison.NOT_EMPTY).contains(condition.operator())) {
      assistantValid(condition.values().isEmpty());
      return;
    }
    assistantValid(
        !condition.values().isEmpty()
            && (condition.operator() == Comparison.IN || condition.values().size() == 1));
    if (condition.operator() == Comparison.CONTAINS) {
      assistantValid(condition.field().kind() == Kind.TEXT);
    }
    if (Set.of(Comparison.GT, Comparison.GTE, Comparison.LT, Comparison.LTE)
        .contains(condition.operator())) {
      assistantValid(Set.of(Kind.DATE, Kind.DATETIME).contains(condition.field().kind()));
    }
    for (var value : condition.values()) {
      boolean valid =
          switch (condition.field().kind()) {
            case TEXT -> value instanceof Text(String value1) && value1 != null && value1.length() <= 200;
            case ID ->
                value instanceof Identifier(Long value1) && value1 != null && value1 > 0
                    || value == Reference.ME && condition.field() == Field.OWNER_ID;
            case DATE ->
                value instanceof DateValue(LocalDate value1)
                        && value1 != null
                        && value1.getYear() >= 1000
                        && value1.getYear() <= 9999
                    || value == Reference.TODAY;
            case DATETIME ->
                value instanceof DateTimeValue(java.time.LocalDateTime value1)
                        && value1 != null
                        && value1.getYear() >= 1000
                        && value1.getYear() <= 9999
                    || value == Reference.TODAY
                    || value == Reference.NOW;
          };
      assistantValid(valid);
    }
  }

  private static DateRange assistantRange(EquipmentAssistantQuery q, LocalDate today) {
    if (q.period() == Period.ALL) {
      return null;
    }
    LocalDate start = today, end = today;
    switch (q.period()) {
      case TODAY -> {}
      case YESTERDAY -> {
        start = today.minusDays(1);
        end = start;
      }
      case WEEK, LAST_WEEK -> {
        start = today.minusDays(today.getDayOfWeek().getValue() - 1);
        if (q.period() == Period.LAST_WEEK) {
          start = start.minusWeeks(1);
        }
        end = start.plusDays(6);
      }
      case MONTH -> {
        start = today.withDayOfMonth(1);
        end = today.withDayOfMonth(today.lengthOfMonth());
      }
      case LAST_MONTH -> {
        end = today.withDayOfMonth(1).minusDays(1);
        start = end.withDayOfMonth(1);
      }
      case YEAR, LAST_YEAR -> {
        start = today.withDayOfYear(1);
        if (q.period() == Period.LAST_YEAR) {
          start = start.minusYears(1);
        }
        end = start.plusYears(1).minusDays(1);
      }
      case LAST7 -> start = today.minusDays(6);
      case LAST30 -> start = today.minusDays(29);
      case LAST90 -> start = today.minusDays(89);
      case CUSTOM -> {
        start = q.customRange().start();
        end = q.customRange().end();
      }
      default -> throw new IllegalStateException("未处理的设备查询周期");
    }
    assistantValid(
        start != null
            && end != null
            && !start.isAfter(end)
            && !start.isBefore(LocalDate.of(1900, 1, 1))
            && !end.isAfter(today.plusYears(10)));
    return new DateRange(start, end);
  }

}
