package com.windasoft.eam.dto.equipment;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.dto.equipment.EquipmentListQuery.Direction;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 设备模块接收的有界查询计划；不包含 SQL、表名或任意表达式。 */
public record EquipmentAssistantQuery(
    Operation operation,
    Filter where,
    List<Field> groupBy,
    Aggregate aggregate,
    Field measure,
    SortBy sortBy,
    Direction direction,
    Field timeField,
    Period period,
    DateRange customRange,
    Bucket bucket,
    int topN,
    Long equipmentId,
    int page,
    int size) {
  public EquipmentAssistantQuery {
    groupBy = copy(groupBy);
  }

  public enum Operation {
    LIST,
    COUNT,
    GROUP,
    TREND
  }

  public enum Aggregate {
    COUNT,
    DISTINCT
  }

  public enum Kind {
    TEXT,
    ID,
    DATE,
    DATETIME
  }

  public enum Comparison {
    EQ,
    NE,
    CONTAINS,
    IN,
    GT,
    GTE,
    LT,
    LTE,
    EMPTY,
    NOT_EMPTY
  }

  public enum Logical {
    AND,
    OR,
    NOT
  }

  public enum Period {
    ALL,
    TODAY,
    YESTERDAY,
    WEEK,
    LAST_WEEK,
    MONTH,
    LAST_MONTH,
    YEAR,
    LAST_YEAR,
    LAST7,
    LAST30,
    LAST90,
    CUSTOM
  }

  public enum Bucket {
    DAY,
    MONTH,
    YEAR
  }

  /** 字段能力由设备模块登记，助手目录据此生成；SQL映射只存在于Repository。 */
  public enum Field {
    ID("id", "ID", Kind.ID),
    CODE("code", "设备编号", Kind.TEXT),
    NAME("name", "设备名称", Kind.TEXT),
    MODEL("model", "规格型号", Kind.TEXT),
    BRAND("brand", "品牌", Kind.TEXT),
    CATEGORY("category", "设备分类", Kind.TEXT),
    CATEGORY_ID("categoryId", "分类ID", Kind.ID),
    STATUS("status", "设备状态", Kind.TEXT),
    USE_STATE("useState", "使用状态", Kind.TEXT),
    COMPANY("company", "公司", Kind.TEXT),
    COMPANY_ID("companyId", "公司ID", Kind.ID),
    DEPARTMENT("department", "部门", Kind.TEXT),
    DEPARTMENT_ID("departmentId", "部门ID", Kind.ID),
    OWNER("owner", "负责人", Kind.TEXT),
    OWNER_ID("ownerId", "负责人ID", Kind.ID),
    ACQUIRED_AT("acquiredAt", "购置日期", Kind.DATE),
    WARRANTY_UNTIL("warrantyUntil", "保修到期", Kind.DATE),
    CREATED_AT("createdAt", "创建时间", Kind.DATETIME),
    LOCATION("location", "位置", Kind.TEXT);
    private final String key;
    private final String label;
    private final Kind kind;

    Field(String key, String label, Kind kind) {
      this.key = key;
      this.label = label;
      this.kind = kind;
    }

    public String key() {
      return key;
    }

    public String label() {
      return label;
    }

    public Kind kind() {
      return kind;
    }

    public Field identity() {
      return switch (this) {
        case NAME -> ID;
        case CATEGORY -> CATEGORY_ID;
        case COMPANY -> COMPANY_ID;
        case DEPARTMENT -> DEPARTMENT_ID;
        case OWNER -> OWNER_ID;
        default -> null;
      };
    }

    public static Field fromKey(String key) {
      for (var field : values()) {
        if (field.key.equals(key)) {
          return field;
        }
      }
      throw new LocalizedArgumentException("server.common.message0015", key);
    }
  }

  public static final List<Field> LIST_FIELDS =
      List.of(
          Field.CODE,
          Field.NAME,
          Field.CATEGORY,
          Field.STATUS,
          Field.USE_STATE,
          Field.DEPARTMENT,
          Field.MODEL);

  public sealed interface Filter permits All, Junction, Condition {}

  public record All() implements Filter {}

  public record Junction(Logical operator, List<Filter> children) implements Filter {
    public Junction {
      children = copy(children);
    }
  }

  public record Condition(Field field, Comparison operator, List<Value> values) implements Filter {
    public Condition {
      values = copy(values);
    }
  }

  public sealed interface Value permits Text, Identifier, DateValue, DateTimeValue, Reference {}

  public record Text(String value) implements Value {}

  public record Identifier(Long value) implements Value {}

  public record DateValue(LocalDate value) implements Value {}

  public record DateTimeValue(LocalDateTime value) implements Value {}

  public enum Reference implements Value {
    ME,
    TODAY,
    NOW
  }

  public sealed interface SortBy permits FieldSort, ResultSort {}

  public record FieldSort(Field field) implements SortBy {}

  public enum ResultSort implements SortBy {
    COUNT,
    VALUE,
    PERIOD
  }

  public record DateRange(LocalDate start, LocalDate end) {}

  private static <T> List<T> copy(List<T> values) {
    return values == null ? null : Collections.unmodifiableList(new ArrayList<>(values));
  }
}
