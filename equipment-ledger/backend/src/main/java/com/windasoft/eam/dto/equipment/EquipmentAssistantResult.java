package com.windasoft.eam.dto.equipment;

import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.DateRange;
import com.windasoft.eam.dto.equipment.EquipmentAssistantQuery.Field;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** 设备查询的明确结果类型；旧助手的动态表格由AI边界适配，不向外传递持久化对象。 */
public record EquipmentAssistantResult(Data data, LocalDateTime evaluatedAt, DateRange range) {
  public sealed interface Data permits Count, ListData, Grouped {}

  public record Count(long value, long total) implements Data {}

  public record ListData(List<Row> rows, long total, long records) implements Data {
    public ListData {
      rows = List.copyOf(rows);
    }
  }

  public record Row(
      Long id,
      String code,
      String name,
      String category,
      String status,
      String useState,
      String department,
      String model) {}

  public record Grouped(List<GroupRow> rows, long total, long records) implements Data {
    public Grouped {
      rows = List.copyOf(rows);
    }
  }

  public record GroupRow(
      String period, List<Dimension> dimensions, long count, long value, BigDecimal percent) {
    public GroupRow {
      dimensions = List.copyOf(dimensions);
    }
  }

  public record Dimension(Field field, String value, Long identity) {}
}
