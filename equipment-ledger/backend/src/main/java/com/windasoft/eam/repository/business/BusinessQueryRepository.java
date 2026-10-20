package com.windasoft.eam.repository.business;

import com.windasoft.eam.dto.business.BusinessFilterTerm;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class BusinessQueryRepository {
  private final JdbcTemplate db;

  public BusinessQueryRepository(JdbcTemplate db) {
    this.db = db;
  }

  private static final Set<String> COLUMNS =
      Set.of(
          "text_value",
          "number_value",
          "date_value",
          "datetime_value",
          "bool_value",
          "reference_id",
          "reference_name");

  private void alias(String value) {
    if (!Set.of("t", "target", "eam_equipment", "eam_repair_order").contains(value))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0003");
  }

  private void column(String value) {
    if (!COLUMNS.contains(value))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0004");
  }

  public SqlPredicate predicate(
      long tenant, String object, String alias, List<BusinessFilterTerm> terms) {
    alias(alias);
    var result = SqlPredicate.of("1=1");
    for (var term : terms) {
      UUID.fromString(term.fieldId());
      column(term.column());
      var clause =
          SqlPredicate.of(
              "cv.tenant_id=? AND cv.object_key=? AND cv.field_id=? AND cv.record_id="
                  + alias
                  + ".id",
              tenant,
              object,
              term.fieldId());
      String expression = "cv." + term.column();
      switch (term.operator()) {
        case CONTAINS ->
            clause =
                clause.and(
                    SqlListFilters.contains(expression, term.values().getFirst().toString()));
        case IN -> clause = clause.and(SqlScopes.in(expression, term.values()));
        case RANGE -> {
          if (term.values().get(0) != null)
            clause = clause.and(SqlPredicate.of(expression + ">=?", term.values().get(0)));
          if (term.values().get(1) != null)
            clause =
                clause.and(
                    SqlPredicate.of(
                        expression + (term.exclusiveUpper() ? "<?" : "<=?"), term.values().get(1)));
        }
      }
      result =
          result.and(
              new SqlPredicate(
                  "EXISTS(SELECT 1 FROM eam_business_value cv WHERE " + clause.sql() + ")",
                  clause.arguments()));
    }
    return result;
  }

  public String orderExpression(
      long tenant, String object, String fieldId, String column, String alias) {
    alias(alias);
    UUID.fromString(fieldId);
    column(column);
    if (!object.matches("[a-z_]+"))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0005");
    return "(SELECT cv."
        + column
        + " FROM eam_business_value cv WHERE cv.tenant_id="
        + tenant
        + " AND cv.object_key='"
        + object
        + "' AND cv.record_id="
        + alias
        + ".id AND cv.field_id='"
        + fieldId
        + "' LIMIT 1)";
  }

  /**
   * Compatibility callers intersect their authorized IDs against real records, never a UNION
   * candidate table.
   */
  public Set<String> matchAuthorized(
      long tenant, String object, Collection<String> ids, List<BusinessFilterTerm> terms) {
    String table =
        switch (object) {
          case "equipment" -> "eam_equipment";
          case "repair_report", "repair_process" -> "eam_repair_order";
          case "maintenance_plan" -> "v_maintenance_definition";
          case "maintenance_task" -> "v_maintenance_task";
          case "inspection_daily_plan", "inspection_periodic_plan" -> "v_inspection_definition";
          case "inspection_daily_task", "inspection_periodic_task" -> "v_inspection_task";
          case "inventory_receipt" -> "inv_receipt";
          case "inventory_outbound" -> "inv_issue";
          default ->
              throw new com.windasoft.eam.common.LocalizedArgumentException(
                  "server.business.message0006");
        };
    var scope =
        SqlPredicate.of("t.tenant_id=?", tenant)
            .and(SqlScopes.in("t.id", ids))
            .and(predicate(tenant, object, "t", terms));
    return new HashSet<>(
        db.queryForList(
            "SELECT CAST(t.id AS CHAR) FROM " + table + " t WHERE " + scope.sql(),
            String.class,
            scope.arguments().toArray()));
  }
}
