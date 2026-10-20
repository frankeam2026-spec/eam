package com.windasoft.eam.repository.common;

import com.windasoft.eam.dto.system.PermissionScope;
import java.util.*;

public final class SqlScopes {
  private SqlScopes() {}

  public static SqlPredicate scope(
      PermissionScope scope, String company, String department, String owner, long user) {
    if (scope.all()) return SqlPredicate.of("1=1");
    var result = SqlPredicate.of("1=0");
    if (company != null && !scope.companyIds().isEmpty())
      result = result.or(in(company, scope.companyIds()));
    if (department != null && !scope.departmentIds().isEmpty())
      result = result.or(in(department, scope.departmentIds()));
    if (owner != null && scope.owner()) result = result.or(SqlPredicate.of(owner + "=?", user));
    return result;
  }

  public static SqlPredicate in(String expression, Collection<?> values) {
    return values.isEmpty()
        ? SqlPredicate.of("1=0")
        : new SqlPredicate(
            expression + " IN (" + String.join(",", Collections.nCopies(values.size(), "?")) + ")",
            new ArrayList<>(values));
  }

  public static SqlPredicate jsonMember(String expression, long user) {
    // IDs in historical snapshots may be JSON numbers or strings. Avoid a correlated
    // JSON_TABLE semijoin: count/projection plans must use the same scalar predicate.
    return SqlPredicate.of(
        "(JSON_TYPE("
            + expression
            + ")='ARRAY' AND (JSON_CONTAINS("
            + expression
            + ",JSON_QUOTE(?)) OR JSON_CONTAINS("
            + expression
            + ",CAST(? AS JSON))))",
        Long.toString(user),
        Long.toString(user));
  }
}
