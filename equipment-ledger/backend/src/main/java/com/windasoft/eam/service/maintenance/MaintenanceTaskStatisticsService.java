package com.windasoft.eam.service.maintenance;

import static com.windasoft.eam.common.maintenance.MaintenanceSupport.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import org.springframework.stereotype.Service;

/** Read-only item statistics. Prefilled answers do not establish execution. */
@Service
public class MaintenanceTaskStatisticsService {
  public record Statistics(
      int total,
      Integer completed,
      Integer abnormal,
      BigDecimal completionRate,
      List<String> completedItemIds,
      List<String> abnormalItemIds,
      boolean submitted,
      boolean reliable) {}

  public Statistics summarize(Map<String, Object> task, List<Map<String, Object>> events) {
    var items = rows(task.get("items"));
    var result = object(task.get("result"));
    boolean submitted =
        events.stream().anyMatch(e -> "提交保养".equals(e.get("action")))
            || (!text(result, "startAt").isBlank() && !text(result, "endAt").isBlank());
    boolean reliable = submitted || !Set.of("已完成", "待验收").contains(text(task, "status"));
    if (!reliable)
      return new Statistics(items.size(), null, null, null, List.of(), List.of(), false, false);
    var completed = new ArrayList<String>();
    var abnormal = new ArrayList<String>();
    if (submitted) {
      var answers = new HashMap<String, Map<String, Object>>();
      for (var answer : rows(result.get("items"))) answers.put(text(answer, "id"), answer);
      for (var item : items) {
        var answer = answers.getOrDefault(text(item, "id"), Map.of());
        boolean exception = abnormal(item, answer);
        if (exception) abnormal.add(text(item, "id"));
        if (complete(item, answer, exception)) completed.add(text(item, "id"));
      }
    }
    BigDecimal rate =
        items.isEmpty()
            ? null
            : BigDecimal.valueOf(completed.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    return new Statistics(
        items.size(),
        completed.size(),
        abnormal.size(),
        rate,
        List.copyOf(completed),
        List.copyOf(abnormal),
        submitted,
        true);
  }

  private boolean complete(Map<String, Object> item, Map<String, Object> answer, boolean abnormal) {
    String value = text(answer, "value");
    if (value.isBlank() || yes(answer.get("skipped"))) return false;
    if (text(item, "valueType").equals("选项") && !strings(item.get("options")).contains(value))
      return false;
    if (text(item, "valueType").equals("数值")) {
      try {
        new BigDecimal(value);
      } catch (NumberFormatException e) {
        return false;
      }
    }
    if (yes(item.get("photoRequired")) && rows(answer.get("images")).isEmpty()) return false;
    return !abnormal || !yes(answer.get("resolved")) || !text(answer, "measure").isBlank();
  }

  private boolean abnormal(Map<String, Object> item, Map<String, Object> answer) {
    String value = text(answer, "value");
    if (value.isBlank() || yes(answer.get("skipped"))) return false;
    if (answer.get("abnormal") instanceof Boolean flag) return flag;
    if (text(item, "valueType").equals("选项"))
      return !strings(item.get("normalOptions")).contains(value);
    if (text(item, "valueType").equals("数值")) {
      try {
        var number = new BigDecimal(value);
        return (!text(item, "lower").isBlank()
                && number.compareTo(new BigDecimal(text(item, "lower"))) < 0)
            || (!text(item, "upper").isBlank()
                && number.compareTo(new BigDecimal(text(item, "upper"))) > 0);
      } catch (NumberFormatException e) {
        return false;
      }
    }
    return false;
  }

  @SuppressWarnings("unchecked")
  private Map<String, Object> object(Object value) {
    return value instanceof Map<?, ?> ? (Map<String, Object>) value : Map.of();
  }
}
