package com.windasoft.eam.dto.equipment;

import com.fasterxml.jackson.annotation.*;
import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.*;

/** Validated query shared by list, export and internal callers. No raw HTTP JSON. */
public record EquipmentListQuery(
    long page, long size, String keyword, List<Condition> conditions, Sort sort, Scope scope) {
  public EquipmentListQuery {
    if (page < 1 || page > 10_000_000 || size < 1 || size > 10000)
      throw new LocalizedArgumentException("server.common.message0004");
    keyword = keyword == null ? "" : keyword;
    if (keyword.length() > 200 || conditions == null || conditions.size() > 180)
      throw new LocalizedArgumentException("server.common.message0009");
    conditions = List.copyOf(conditions);
    Objects.requireNonNull(sort);
    Objects.requireNonNull(scope);
  }

  public EquipmentListQuery at(long page, long size) {
    return new EquipmentListQuery(page, size, keyword, conditions, sort, scope);
  }

  public enum Operator {
    CONTAINS("contains"),
    EQ("eq"),
    IN("in"),
    RANGE("range"),
    EMPTY("empty"),
    NOT_EMPTY("notEmpty");
    private final String wire;

    Operator(String wire) {
      this.wire = wire;
    }

    @JsonValue
    public String wire() {
      return wire;
    }

    @JsonCreator
    public static Operator parse(String value) {
      for (var op : values()) if (op.wire.equals(value)) return op;
      throw new LocalizedArgumentException("server.common.message0013");
    }
  }

  public record Condition(String field, Operator operator, List<String> values) {
    public Condition {
      if (field == null
          || field.isBlank()
          || field.length() > 200
          || operator == null
          || values == null
          || values.size() > 2000)
        throw new LocalizedArgumentException("server.common.message0009");
      for (String value : values)
        if (value == null || value.length() > 200)
          throw new LocalizedArgumentException("server.common.message0011");
      values = List.copyOf(values);
    }
  }

  public enum Direction {
    ASC,
    DESC;

    public static Direction parse(String value) {
      if (value == null || value.isBlank()) return DESC;
      try {
        return valueOf(value.toUpperCase(Locale.ROOT));
      } catch (IllegalArgumentException e) {
        throw new LocalizedArgumentException("server.business.message0019");
      }
    }
  }

  public record Sort(String field, Direction direction) {
    public Sort {
      field = field == null || field.isBlank() ? "createdAt" : field;
      if (field.length() > 200 || direction == null)
        throw new LocalizedArgumentException("server.common.message0009");
    }
  }

  public enum ScopeType {
    NONE,
    ORGANIZATION,
    CATEGORY,
    LOCATION
  }

  public record Scope(ScopeType type, List<Long> ids, Long companyId) {
    public Scope {
      if (type == null
          || ids == null
          || ids.size() > 5000
          || ids.stream().anyMatch(id -> id == null || id <= 0)
          || companyId != null && companyId <= 0
          || type != ScopeType.NONE && ids.isEmpty()
          || type == ScopeType.NONE && !ids.isEmpty()
          || companyId != null && type != ScopeType.LOCATION)
        throw new LocalizedArgumentException("server.equipment.message0008");
      ids = List.copyOf(new LinkedHashSet<>(ids));
    }

    public static Scope none() {
      return new Scope(ScopeType.NONE, List.of(), null);
    }
  }
}
