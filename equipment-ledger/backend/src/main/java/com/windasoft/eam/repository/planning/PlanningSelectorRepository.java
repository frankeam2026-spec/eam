package com.windasoft.eam.repository.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.stereotype.Repository;

@Repository
public class PlanningSelectorRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;

  public PlanningSelectorRepository(JdbcListRepository lists, ObjectMapper json) {
    this.lists = lists;
    this.json = json;
  }

  public PageResult<Map<String, Object>> equipment(
      long tenant, ListQuery q, SqlPredicate access, Set<Long> ids, String category) {
    String from = "FROM eam_equipment t";
    var where = SqlPredicate.of("t.tenant_id=? AND t.deleted=0", tenant).and(access);
    if (ids != null) where = where.and(SqlScopes.in("t.id", ids));
    if (!category.isBlank())
      where =
          where.and(
              SqlPredicate.of(
                  "(CAST(t.category_id AS CHAR)=? OR t.category=?)", category, category));
    if (!q.keyword().isBlank())
      where =
          where.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.equipment_code,t.name,t.model,t.category)", q.keyword()));
    var rows =
        lists.rows(
            "CAST(t.id AS CHAR) id,t.equipment_code code,t.name,COALESCE(t.model,'')"
                + " model,COALESCE(t.function_location,'') functionLocation,COALESCE(t.category,'')"
                + " category,COALESCE(CAST(t.category_id AS CHAR),'')"
                + " categoryId,COALESCE(t.department_path,t.department,'')"
                + " department,COALESCE(CAST(t.company_org_id AS CHAR),'') companyId",
            from,
            where,
            "t.equipment_code,t.id",
            q);
    return new PageResult<>(rows, q.page(), q.size(), lists.count(from, where));
  }

  public PageResult<Map<String, Object>> users(long tenant, ListQuery q, Set<Long> ids) {
    String from =
        "FROM eam_sys_user t LEFT JOIN eam_org_unit o ON o.id=t.org_id AND o.tenant_id=t.tenant_id";
    var where = SqlPredicate.of("t.tenant_id=? AND t.deleted=0 AND t.status=1", tenant);
    if (ids != null) where = where.and(SqlScopes.in("t.id", ids));
    if (!q.keyword().isBlank())
      where =
          where.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.display_name,t.account,o.name)", q.keyword()));
    return new PageResult<>(
        lists.rows(
            "CAST(t.id AS CHAR) id,t.display_name name,t.account,COALESCE(o.name,'') department",
            from,
            where,
            "t.display_name,t.id",
            q),
        q.page(),
        q.size(),
        lists.count(from, where));
  }

  public List<Map<String, Object>> equipmentContext(long tenant, long equipment) {
    return lists.rows(
        "t.id,t.category_id",
        "FROM eam_equipment t",
        SqlPredicate.of("t.tenant_id=? AND t.id=? AND t.deleted=0", tenant, equipment),
        "t.id",
        ListQuery.from(Map.of("size", "1")));
  }

  public SqlPredicate applicable(long equipment, Set<Long> categoryAncestors) {
    String p = "t.payload";
    var unrestricted =
        SqlPredicate.of(
            "(COALESCE(JSON_LENGTH("
                + p
                + ",'$.equipmentIds'),0)=0 AND COALESCE(JSON_LENGTH("
                + p
                + ",'$.categoryIds'),0)=0 AND COALESCE(JSON_LENGTH("
                + p
                + ",'$.legacyCategoryNames'),0)=0 AND (JSON_CONTAINS_PATH("
                + p
                + ",'one','$.categoryIds') OR COALESCE(JSON_LENGTH("
                + p
                + ",'$.categoryNames'),0)=0))");
    var match =
        unrestricted.or(
            SqlScopes.jsonMember("JSON_EXTRACT(" + p + ",'$.equipmentIds')", equipment));
    for (long id : categoryAncestors)
      match = match.or(SqlScopes.jsonMember("JSON_EXTRACT(" + p + ",'$.categoryIds')", id));
    return match;
  }
}
