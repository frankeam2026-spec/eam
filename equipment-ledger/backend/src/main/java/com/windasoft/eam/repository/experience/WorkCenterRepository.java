package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.common.SqlScopes;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class WorkCenterRepository {
  private static final int ID_BATCH_SIZE = 1000;
  private final JdbcTemplate db;
  private final RepairOrderMapper repairs;

  public List<RepairOrder> findRepairs(
      com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<RepairOrder> query) {
    return repairs.selectList(query);
  }

  public WorkCenterRepository(JdbcTemplate db, RepairOrderMapper repairs) {
    this.db = db;
    this.repairs = repairs;
  }

  /** 历史任务仍显示原用户或组织名，保持停用和已删除主数据的既有读取口径。 */
  public Map<Long, String> userNames(long tenant, Collection<Long> ids) {
    return values(
        "SELECT id,display_name FROM eam_sys_user WHERE tenant_id=? AND ",
        "id",
        tenant,
        ids,
        (row, ignored) -> row.getString(2));
  }

  public Map<Long, String> organizationNames(long tenant, Collection<Long> ids) {
    return values(
        "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND ",
        "id",
        tenant,
        ids,
        (row, ignored) -> row.getString(2));
  }

  public Map<Long, Long> taskCreators(long tenant, Collection<Long> ids, boolean maintenance) {
    String source =
        maintenance
            ? "v_maintenance_task t JOIN v_maintenance_definition d"
            : "v_inspection_task t JOIN v_inspection_definition d";
    return values(
        "SELECT t.id,d.creator_id FROM "
            + source
            + " ON d.tenant_id=t.tenant_id AND d.id=t.plan_id WHERE t.tenant_id=? AND ",
        "t.id",
        tenant,
        ids,
        (row, ignored) -> row.getObject(2, Long.class));
  }

  private <T> Map<Long, T> values(
      String sql, String column, long tenant, Collection<Long> ids, RowMapper<T> value) {
    if (ids.isEmpty()) {
      return Map.of();
    }
    var result = new HashMap<Long, T>();
    var keys = List.copyOf(ids);
    for (int offset = 0; offset < keys.size(); offset += ID_BATCH_SIZE) {
      var scope =
          SqlScopes.in(column, keys.subList(offset, Math.min(offset + ID_BATCH_SIZE, keys.size())));
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.addAll(scope.arguments());
      db.query(
          sql + scope.sql(),
          row -> {
            result.put(row.getLong(1), value.mapRow(row, 0));
          },
          args.toArray());
    }
    return result;
  }
}
