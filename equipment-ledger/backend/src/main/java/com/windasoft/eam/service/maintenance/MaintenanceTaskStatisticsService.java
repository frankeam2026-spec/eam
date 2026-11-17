package com.windasoft.eam.service.maintenance;

import com.windasoft.eam.domain.maintenance.MaintenanceTaskStatus;
import com.windasoft.eam.dto.maintenance.MaintenanceExecutionResult;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskEventView;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskSnapshot;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskStatistics;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;

/** 只读计算保养项目统计；预填结果不等于已经提交，无法证明的历史记录不伪造完成数。 */
@Service
public class MaintenanceTaskStatisticsService {
  public MaintenanceTaskStatistics summarize(
      MaintenanceTaskSnapshot snapshot,
      MaintenanceExecutionResult result,
      String status,
      List<MaintenanceTaskEventView> events) {
    var items = list(snapshot.getItems());
    boolean submitted =
        events.stream().anyMatch(e -> "提交保养".equals(e.action()))
            || (result != null && result.getStartAt() != null && result.getEndAt() != null);
    boolean reliable =
        submitted
            || !Set.of(
                    MaintenanceTaskStatus.COMPLETED.code(),
                    MaintenanceTaskStatus.PENDING_ACCEPTANCE.code())
                .contains(status);
    if (!reliable) {
      return new MaintenanceTaskStatistics(
          items.size(), null, null, null, List.of(), List.of(), false, false);
    }
    var completed = new ArrayList<String>();
    var abnormal = new ArrayList<String>();
    if (submitted) {
      var answers = new HashMap<String, MaintenanceExecutionResult.Item>();
      if (result != null) {
        // 沿用历史统计的末次答案覆盖语义；办理入口仍按第一次提交的同ID答案校验。
        for (var answer : list(result.getItems())) {
          answers.put(text(answer.getId()), answer);
        }
      }
      for (var item : items) {
        var answer =
            answers.getOrDefault(text(item.getId()), new MaintenanceExecutionResult.Item());
        boolean exception =
            answer.isAbnormal() && !text(answer.getValue()).isBlank() && !answer.isSkipped();
        if (exception) {
          abnormal.add(text(item.getId()));
        }
        if (complete(item, answer, exception)) {
          completed.add(text(item.getId()));
        }
      }
    }
    BigDecimal rate =
        items.isEmpty()
            ? null
            : BigDecimal.valueOf(completed.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(items.size()), 2, RoundingMode.HALF_UP);
    return new MaintenanceTaskStatistics(
        items.size(),
        completed.size(),
        abnormal.size(),
        rate,
        List.copyOf(completed),
        List.copyOf(abnormal),
        submitted,
        true);
  }

  private boolean complete(
      MaintenanceTaskSnapshot.Item item, MaintenanceExecutionResult.Item answer, boolean abnormal) {
    String value = text(answer.getValue());
    if (value.isBlank() || answer.isSkipped()) {
      return false;
    }
    if ("选项".equals(text(item.getValueType())) && !list(item.getOptions()).contains(value)) {
      return false;
    }
    if ("数值".equals(text(item.getValueType()))) {
      try {
        new BigDecimal(value);
      } catch (NumberFormatException e) {
        return false;
      }
    }
    if (item.isPhotoRequired() && list(answer.getImages()).isEmpty()) {
      return false;
    }
    return !abnormal || !answer.isResolved() || !text(answer.getMeasure()).isBlank();
  }

  private static <T> List<T> list(List<T> values) {
    return values == null ? List.of() : values;
  }

  private static String text(String value) {
    return Objects.toString(value, "").trim();
  }
}
