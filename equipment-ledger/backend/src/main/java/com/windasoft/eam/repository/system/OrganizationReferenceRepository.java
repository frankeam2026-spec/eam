package com.windasoft.eam.repository.system;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 组织删除前的跨领域只读引用检查，不负责业务删除或授权决策。 */
@Repository
public class OrganizationReferenceRepository {
  private final JdbcTemplate db;

  public OrganizationReferenceRepository(JdbcTemplate db) {
    this.db = db;
  }

  public boolean businessReferences(long tenant, Long id) {

    record ReferenceQuery(String sql, boolean hasDepartmentColumn) {}
    for (ReferenceQuery query :
        List.of(
            new ReferenceQuery(
                "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=? AND (company_org_id=? OR"
                    + " department_org_id=?)", true),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM eam_repair_order WHERE tenant_id=? AND department_org_id=?", false),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM v_maintenance_task WHERE tenant_id=? AND (company_id=? OR"
                    + " department_id=?)", true),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM v_inspection_task WHERE tenant_id=? AND (company_id=? OR"
                    + " department_id=?)", true),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM v_maintenance_definition WHERE tenant_id=? AND department_id=?", false),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM v_inspection_definition WHERE tenant_id=? AND department_id=?", false),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND company_id=?", false),
            new ReferenceQuery(
                "SELECT COUNT(*) FROM eam_role_menu_target WHERE tenant_id=? AND target_kind IN"
                    + " ('company','department') AND target_id=?", false))) {
      int count =
          query.hasDepartmentColumn()
              ? db.queryForObject(query.sql(), Integer.class, tenant, id, id)
              : db.queryForObject(query, Integer.class, tenant, id);
      if (count > 0) return true;
    }
    return false;
  }
}
