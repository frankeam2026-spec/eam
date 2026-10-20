package com.windasoft.eam.repository.common;

import com.windasoft.eam.dto.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcListRepository {
  private final JdbcTemplate db;

  public JdbcListRepository(JdbcTemplate db) {
    this.db = db;
  }

  public List<Map<String, Object>> distinct(String select, String from, SqlPredicate predicate) {
    return db.queryForList(
        "SELECT DISTINCT " + select + " " + from + " WHERE " + predicate.sql(),
        predicate.arguments().toArray());
  }

  public List<Map<String, Object>> grouped(
      String select, String from, SqlPredicate predicate, String groupBy) {
    return db.queryForList(
        "SELECT " + select + " " + from + " WHERE " + predicate.sql() + " GROUP BY " + groupBy,
        predicate.arguments().toArray());
  }

  public long count(String from, SqlPredicate predicate) {
    return db.queryForObject(
        "SELECT COUNT(*) " + from + " WHERE " + predicate.sql(),
        Long.class,
        predicate.arguments().toArray());
  }

  public List<Map<String, Object>> rows(
      String select, String from, SqlPredicate predicate, String order, ListQuery query) {
    var args = new ArrayList<>(predicate.arguments());
    args.add(query.size());
    args.add((long) (query.page() - 1) * query.size());
    return db.queryForList(
        "SELECT "
            + select
            + " "
            + from
            + " WHERE "
            + predicate.sql()
            + " ORDER BY "
            + order
            + " LIMIT ? OFFSET ?",
        args.toArray());
  }
}
