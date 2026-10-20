package com.windasoft.eam.repository.common;

import java.util.*;

public record SqlPredicate(String sql, List<Object> arguments) {
  public SqlPredicate {
    arguments = List.copyOf(arguments);
  }

  public static SqlPredicate of(String sql, Object... arguments) {
    return new SqlPredicate(sql, Arrays.asList(arguments));
  }

  public SqlPredicate and(SqlPredicate other) {
    return combine("AND", this, other);
  }

  public SqlPredicate or(SqlPredicate other) {
    return combine("OR", this, other);
  }

  private static SqlPredicate combine(String op, SqlPredicate a, SqlPredicate b) {
    if (op.equals("AND")) {
      if (a.constant(false) || b.constant(false)) return of("1=0");
      if (a.constant(true)) return b;
      if (b.constant(true)) return a;
    } else {
      if (a.constant(true) || b.constant(true)) return of("1=1");
      if (a.constant(false)) return b;
      if (b.constant(false)) return a;
    }
    var arguments = new ArrayList<Object>(a.arguments);
    arguments.addAll(b.arguments);
    return new SqlPredicate("(" + a.sql + ") " + op + " (" + b.sql + ")", arguments);
  }

  private boolean constant(boolean value) {
    return arguments.isEmpty() && sql.trim().equals(value ? "1=1" : "1=0");
  }
}
