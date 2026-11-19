package com.windasoft.eam.repository.system;

import static com.windasoft.eam.repository.common.SqlListFilters.Kind.CHOICE;
import static com.windasoft.eam.repository.common.SqlListFilters.Kind.TEXT;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.common.ActivationStatus;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.system.UserQueryRow;
import com.windasoft.eam.repository.common.JdbcListRepository;
import com.windasoft.eam.repository.common.SqlListFilters;
import com.windasoft.eam.repository.common.SqlPredicate;
import com.windasoft.eam.repository.common.SqlScopes;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Repository;

/** 用户列表仓储：执行分页、筛选及角色关联查询，不改变用户状态。 */
@Repository
public class UserListRepository {
  private final JdbcListRepository lists;
  private final ObjectMapper json;

  public UserListRepository(JdbcListRepository lists, ObjectMapper json) {
    this.lists = lists;
    this.json = json;
  }

  public record Selection(String from, SqlPredicate predicate, String order, String roles) {}

  public Selection select(
      long tenant,
      ListQuery query,
      boolean all,
      Set<Long> visibleOrganizations,
      Set<Long> selectedOrganizations,
      boolean showRoles) {
    String roles =
        "(SELECT GROUP_CONCAT(r.name ORDER BY r.id SEPARATOR '、') FROM eam_sys_user_role l JOIN"
            + " eam_sys_role r ON r.tenant_id=l.tenant_id AND r.id=l.role_id AND r.deleted=0 WHERE"
            + " l.tenant_id=t.tenant_id AND l.user_id=t.id)";
    if (!showRoles) roles = "''";
    String from =
        "FROM eam_sys_user t LEFT JOIN eam_org_unit o ON o.tenant_id=t.tenant_id AND o.id=t.org_id";
    var fields = new LinkedHashMap<String, SqlListFilters.Field>();
    for (String key : List.of("account", "phone"))
      fields.put(key, new SqlListFilters.Field("t." + key, TEXT));
    fields.put("name", new SqlListFilters.Field("t.display_name", TEXT));
    fields.put("employeeNo", new SqlListFilters.Field("t.employee_no", TEXT));
    fields.put("department", new SqlListFilters.Field("o.name", TEXT));
    fields.put("roles", new SqlListFilters.Field(roles, TEXT));
    String status = SqlListFilters.activationLabel("t.status=" + ActivationStatus.ENABLED.code());
    fields.put("status", new SqlListFilters.Field(status, CHOICE));
    var predicate = SqlPredicate.of("t.tenant_id=? AND t.deleted=0", tenant);
    if (!all) predicate = predicate.and(SqlScopes.in("t.org_id", visibleOrganizations));
    if (selectedOrganizations != null)
      predicate = predicate.and(SqlScopes.in("t.org_id", selectedOrganizations));
    predicate = predicate.and(SqlListFilters.apply(json, query, fields));
    if (!query.keyword().isBlank())
      predicate =
          predicate.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.account,t.display_name,t.employee_no,t.phone,o.name,"
                      + roles
                      + ","
                      + status
                      + ")",
                  query.keyword()));
    return new Selection(from, predicate, SqlListFilters.order(query, fields, "t.id"), roles);
  }

  public long count(Selection selection) {
    return lists.count(selection.from(), selection.predicate());
  }

  public List<UserQueryRow> page(Selection selected, ListQuery query) {
    return lists
        .rows(
            "t.id,t.account,t.display_name,t.employee_no,t.phone,t.email,t.email_version,t.org_id,t.status,o.name"
                + " department,"
                + selected.roles()
                + " roles",
            selected.from(),
            selected.predicate(),
            selected.order(),
            query)
        .stream()
        .map(
            row ->
                new UserQueryRow(
                    ((Number) row.get("id")).longValue(),
                    text(row, "account"),
                    text(row, "display_name"),
                    text(row, "employee_no"),
                    text(row, "phone"),
                    text(row, "email"),
                    ((Number) row.get("email_version")).intValue(),
                    row.get("org_id") == null ? null : ((Number) row.get("org_id")).longValue(),
                    ((Number) row.get("status")).intValue(),
                    text(row, "department"),
                    text(row, "roles")))
        .toList();
  }

  /** 通用JDBC列表组件的动态映射在此结束，空文本保留旧接口的空串语义。 */
  private static String text(Map<String, Object> row, String key) {
    return Objects.toString(row.get(key), "");
  }

  public Map<String, Long> roleCounts(Selection selected) {
    var rows =
        lists.grouped(
            "l.role_id,COUNT(DISTINCT t.id) amount",
            selected.from()
                + " JOIN eam_sys_user_role l ON l.tenant_id=t.tenant_id AND l.user_id=t.id",
            selected.predicate(),
            "l.role_id");
    var result = new LinkedHashMap<String, Long>();
    for (var row : rows)
      result.put(row.get("role_id").toString(), ((Number) row.get("amount")).longValue());
    return result;
  }
}
