package com.windasoft.eam.repository.inventory;

import com.windasoft.eam.dto.common.*;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.common.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Authorized task candidates are unioned before counting and paging. */
@Repository
public class InventorySelectorRepository {
  private final JdbcTemplate db;

  public InventorySelectorRepository(JdbcTemplate db) {
    this.db = db;
  }

  private static final String ORGS =
      "WITH RECURSIVE org_company AS (SELECT id,parent_id,CASE WHEN org_type IN ('公司','集团') THEN id"
          + " END company_id FROM eam_org_unit WHERE tenant_id=? AND deleted=0 AND parent_id IS"
          + " NULL UNION ALL SELECT o.id,o.parent_id,CASE WHEN o.org_type IN ('公司','集团') THEN o.id"
          + " ELSE p.company_id END FROM eam_org_unit o JOIN org_company p ON o.parent_id=p.id"
          + " WHERE o.tenant_id=? AND o.deleted=0) ";
  private static final String ACTIVE =
      "t.status NOT IN ('待验收','待审核','已完成','已验收','已审核','已作废','过期作废','已取消','作废','完成','已关闭')";

  private static SqlPredicate participant(long user) {
    return SqlScopes.jsonMember("JSON_EXTRACT(t.snapshot,'$.owners')", user)
        .or(SqlScopes.jsonMember("JSON_EXTRACT(t.snapshot,'$.reviewers')", user));
  }

  public PageResult<Map<String, Object>> tasks(
      long tenant,
      long user,
      ListQuery q,
      PermissionScope repair,
      PermissionScope maintenance,
      PermissionScope daily,
      PermissionScope periodic,
      boolean personalOnly,
      Long company,
      String kind,
      Long id) {
    var sqls = new ArrayList<String>();
    var args = new ArrayList<Object>(List.of(tenant, tenant));
    for (String source : List.of("维修", "保养", "点巡检")) {
      if (!kind.isBlank() && !source.equals(kind)) continue;
      String from, code, name, companySql;
      var where = SqlPredicate.of("t.tenant_id=? AND " + ACTIVE, tenant);
      if (id != null) where = where.and(SqlPredicate.of("t.id=?", id));
      if (source.equals("维修")) {
        companySql =
            "COALESCE(o.company_id,(SELECT e.company_org_id FROM eam_equipment e WHERE"
                + " e.tenant_id=t.tenant_id AND e.equipment_code=t.equipment_code AND"
                + " e.deleted=0))";
        from = "eam_repair_order t LEFT JOIN org_company o ON o.id=t.department_org_id";
        code = "t.order_no";
        name = "t.equipment_name";
        var access = SqlPredicate.of("t.assignee_user_id=?", user);
        access = access.or(SqlScopes.scope(repair, null, "t.department_org_id", null, user));
        if (repair.owner()) access = access.or(SqlPredicate.of("t.reporter_user_id=?", user));
        where = where.and(SqlPredicate.of("t.deleted=0")).and(access);
        if (personalOnly)
          where =
              where.and(SqlPredicate.of("t.assignee_user_id=? AND t.status IN ('处理中','挂起')", user));
      } else {
        boolean maint = source.equals("保养");
        from = (maint ? "v_maintenance_task" : "v_inspection_task") + " t";
        code = "t.code";
        name =
            maint
                ? "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.equipmentName')),'')"
                : "COALESCE(JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.planName')),'')";
        companySql = "t.company_id";
        var access = participant(user);
        if (maint)
          access =
              access.or(
                  SqlScopes.scope(
                      maintenance, "t.company_id", "t.department_id", "t.owner_id", user));
        else {
          where = where.and(SqlPredicate.of("t.deleted=0"));
          access =
              access
                  .or(
                      SqlPredicate.of(
                          "t.owner_id=? OR"
                              + " JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.personMode'))='任何人可点检'",
                          user))
                  .or(
                      SqlPredicate.of("t.kind='daily'")
                          .and(
                              SqlScopes.scope(
                                  daily, "t.company_id", "t.department_id", "t.owner_id", user)))
                  .or(
                      SqlPredicate.of("t.kind='periodic'")
                          .and(
                              SqlScopes.scope(
                                  periodic,
                                  "t.company_id",
                                  "t.department_id",
                                  "t.owner_id",
                                  user)));
        }
        where = where.and(access);
        if (personalOnly) {
          var execute = SqlScopes.jsonMember("JSON_EXTRACT(t.snapshot,'$.owners')", user);
          if (!maint)
            execute =
                execute.or(
                    SqlPredicate.of(
                        "JSON_UNQUOTE(JSON_EXTRACT(t.snapshot,'$.personMode'))='任何人可点检'"));
          where =
              where
                  .and(execute)
                  .and(
                      SqlPredicate.of(
                          "(t.owner_id IS NULL OR t.owner_id=?) AND t.status IN ('执行中','挂起')",
                          user));
        }
      }
      where = where.and(SqlPredicate.of(companySql + " IS NOT NULL"));
      if (company != null) where = where.and(SqlPredicate.of(companySql + "=?", company));
      if (!q.keyword().isBlank())
        where =
            where.and(
                SqlListFilters.contains("CONCAT_WS(' '," + code + "," + name + ")", q.keyword()));
      sqls.add(
          "SELECT CAST(t.id AS CHAR) id,'"
              + source
              + "' kind,"
              + code
              + " code,"
              + name
              + " name,CAST("
              + companySql
              + " AS CHAR) companyId FROM "
              + from
              + " WHERE "
              + where.sql());
      args.addAll(where.arguments());
    }
    if (sqls.isEmpty()) return new PageResult<>(List.of(), q.page(), q.size(), 0);
    String union = String.join(" UNION ALL ", sqls);
    long total =
        db.queryForObject(
            ORGS + "SELECT COUNT(*) FROM (" + union + ") candidates", Long.class, args.toArray());
    var paged = new ArrayList<>(args);
    paged.add(q.size());
    paged.add((long) (q.page() - 1) * q.size());
    var rows =
        db.queryForList(
            ORGS
                + "SELECT * FROM ("
                + union
                + ") candidates ORDER BY kind,code,id LIMIT ? OFFSET ?",
            paged.toArray());
    return new PageResult<>(rows, q.page(), q.size(), total);
  }

  public PageResult<Map<String, Object>> users(
      long tenant, long company, ListQuery q, Set<Long> ids) {
    var args = new ArrayList<Object>(List.of(tenant, tenant));
    var where =
        SqlPredicate.of(
            "t.tenant_id=? AND t.deleted=0 AND t.status=1 AND o.company_id=?", tenant, company);
    if (ids != null) where = where.and(SqlScopes.in("t.id", ids));
    if (!q.keyword().isBlank())
      where =
          where.and(
              SqlListFilters.contains(
                  "CONCAT_WS(' ',t.display_name,t.account,d.name)", q.keyword()));
    args.addAll(where.arguments());
    String from =
        " FROM eam_sys_user t JOIN org_company o ON o.id=t.org_id LEFT JOIN eam_org_unit d ON"
            + " d.id=t.org_id AND d.tenant_id=t.tenant_id WHERE "
            + where.sql();
    long total = db.queryForObject(ORGS + "SELECT COUNT(*)" + from, Long.class, args.toArray());
    args.add(q.size());
    args.add((long) (q.page() - 1) * q.size());
    return new PageResult<>(
        db.queryForList(
            ORGS
                + "SELECT CAST(t.id AS CHAR) id,t.display_name name,t.account,COALESCE(d.name,'')"
                + " department,CAST(o.company_id AS CHAR) companyId"
                + from
                + " ORDER BY t.display_name,t.id LIMIT ? OFFSET ?",
            args.toArray()),
        q.page(),
        q.size(),
        total);
  }
}
