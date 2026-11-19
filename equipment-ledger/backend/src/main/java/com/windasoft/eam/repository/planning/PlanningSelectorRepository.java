package com.windasoft.eam.repository.planning;

import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.common.PageResult;
import com.windasoft.eam.dto.planning.PlanningCandidate.Equipment;
import com.windasoft.eam.dto.planning.PlanningCandidate.User;
import com.windasoft.eam.repository.common.JdbcListRepository;
import com.windasoft.eam.repository.common.SqlListFilters;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.common.SqlScopes;
import java.util.Set;
import org.springframework.stereotype.Repository;

@Repository
public class PlanningSelectorRepository {
  private final JdbcListRepository lists;

  public PlanningSelectorRepository(JdbcListRepository lists) {
    this.lists = lists;
  }

  public PageResult<Equipment> equipment(
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
        lists
            .rows(
                "CAST(t.id AS CHAR) id,t.equipment_code code,t.name,COALESCE(t.model,'')"
                    + " model,COALESCE(t.function_location,'') functionLocation,COALESCE(t.category,'')"
                    + " category,COALESCE(CAST(t.category_id AS CHAR),'')"
                    + " categoryId,COALESCE(t.department_path,t.department,'')"
                    + " department,COALESCE(CAST(t.company_org_id AS CHAR),'') companyId",
                from,
                where,
                "t.equipment_code,t.id",
                q)
            .stream()
            .map(
                row ->
                    new Equipment(
                        (String) row.get("id"),
                        (String) row.get("code"),
                        (String) row.get("name"),
                        (String) row.get("model"),
                        (String) row.get("functionLocation"),
                        (String) row.get("category"),
                        (String) row.get("categoryId"),
                        (String) row.get("department"),
                        (String) row.get("companyId")))
            .toList();
    return new PageResult<>(rows, q.page(), q.size(), lists.count(from, where));
  }

  public PageResult<User> users(long tenant, ListQuery q, Set<Long> ids) {
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
        lists
            .rows(
                "CAST(t.id AS CHAR) id,t.display_name name,t.account,COALESCE(o.name,'') department",
                from,
                where,
                "t.display_name,t.id",
                q)
            .stream()
            .map(
                row ->
                    new User(
                        (String) row.get("id"),
                        (String) row.get("name"),
                        (String) row.get("account"),
                        (String) row.get("department")))
            .toList(),
        q.page(),
        q.size(),
        lists.count(from, where));
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
