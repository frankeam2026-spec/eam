package com.windasoft.eam.dto.business;

import java.util.List;

/** Values and references have already passed business validation before SQL compilation. */
public record BusinessFilterTerm(
    String fieldId, String column, Operator operator, List<Object> values, boolean exclusiveUpper) {
  public enum Operator {
    CONTAINS,
    RANGE,
    IN
  }

  public BusinessFilterTerm {
    values = java.util.Collections.unmodifiableList(new java.util.ArrayList<>(values));
  }
}
