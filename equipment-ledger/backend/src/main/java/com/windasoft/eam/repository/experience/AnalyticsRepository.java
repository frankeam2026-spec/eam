package com.windasoft.eam.repository.experience;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcUtils;
import org.springframework.stereotype.Repository;

@Repository
public class AnalyticsRepository {
  private final JdbcTemplate db;

  public AnalyticsRepository(JdbcTemplate db) {
    this.db = db;
  }

  public record Source(String sql, List<Object> args) {}

  public List<Map<String, Object>> rows(
      Source source, String select, String where, List<Object> extra, String tail) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    return db.queryForList(
        "SELECT " + select + " FROM (" + source.sql() + ") s WHERE " + where + " " + tail,
        args.toArray());
  }

  public long count(Source source, String where, List<Object> extra) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    return db.queryForObject(
        "SELECT COUNT(*) FROM (" + source.sql() + ") s WHERE " + where, Long.class, args.toArray());
  }

  /** CSV 只读选定列，并沿用钻取明细的排序和 JDBC 单元格取值规则。 */
  public void exportRows(
      Source source,
      String where,
      List<Object> extra,
      List<String> columns,
      int limit,
      Consumer<List<String>> consume) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    args.add(limit);
    // 列名由服务内部的两套 CSV 表头决定，请求参数只绑定到过滤条件。
    String projection = String.join(",", columns.stream().map(column -> "s." + column).toList());
    db.query(
        "SELECT "
            + projection
            + " FROM ("
            + source.sql()
            + ") s WHERE "
            + where
            + " ORDER BY s.occurred_at DESC,s.source_id LIMIT ?",
        row -> {
          var values = new ArrayList<String>(columns.size());
          for (int i = 1; i <= columns.size(); i++) {
            values.add(Objects.toString(JdbcUtils.getResultSetValue(row, i), ""));
          }
          consume.accept(values);
        },
        args.toArray());
  }

  public List<Map<String, Object>> cte(Source source, String query, List<Object> extra) {
    var args = new ArrayList<>(source.args());
    args.addAll(extra);
    return db.queryForList("WITH base AS (" + source.sql() + ") " + query, args.toArray());
  }
}
