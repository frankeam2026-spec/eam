package com.windasoft.eam.repository.inventory;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.windasoft.eam.dto.inventory.WarehouseChoice;
import com.windasoft.eam.entity.inventory.Warehouse;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WarehouseRepository {
  private final WarehouseMapper mapper;
  private final JdbcTemplate db;

  public WarehouseRepository(WarehouseMapper mapper, JdbcTemplate db) {
    this.mapper = mapper;
    this.db = db;
  }

  /** 主数据更新已在库存事务内校验旧版本并锁定原行，写入后递增版本。 */
  public void saveMaster(long tenant, long id, Warehouse row, boolean create) {
    if (create) {
      row.setTenantId(tenant);
      row.setId(id);
      mapper.insert(row);
      return;
    }
    var update =
        new LambdaUpdateWrapper<Warehouse>()
            .eq(Warehouse::getTenantId, tenant)
            .eq(Warehouse::getId, id)
            .setSql("row_version=row_version+1");
    if (row.getManagerId() == null) update.set(Warehouse::getManagerId, null);
    mapper.update(row, update);
  }

  /** 授权范围包括停用仓库，历史单据仍按其仓库归属授权。 */
  public List<Warehouse> scopeRows(long tenant) {
    return mapper.selectList(scopeQuery(tenant));
  }

  public Warehouse scopeRow(long tenant, long warehouse) {
    return mapper.selectOne(scopeQuery(tenant).eq(Warehouse::getId, warehouse));
  }

  public long countIds(long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return 0;
    return mapper.selectCount(
        new LambdaQueryWrapper<Warehouse>()
            .eq(Warehouse::getTenantId, tenant)
            .in(Warehouse::getId, ids));
  }

  private LambdaQueryWrapper<Warehouse> scopeQuery(long tenant) {
    return new LambdaQueryWrapper<Warehouse>()
        .select(Warehouse::getId, Warehouse::getCompanyId, Warehouse::getTenantId)
        .eq(Warehouse::getTenantId, tenant);
  }

  /** 角色权限配置的仓库候选项：保留现有 id/name JSON 字段和排序。 */
  public List<WarehouseChoice> warehouseChoices(long tenant) {
    return db.query(
        "SELECT CAST(w.id AS CHAR) id,CONCAT(o.name,' / ',w.name) name FROM inv_warehouse w JOIN"
            + " eam_org_unit o ON o.tenant_id=w.tenant_id AND o.id=w.company_id WHERE w.tenant_id=?"
            + " ORDER BY o.name,w.name",
        (row, index) -> new WarehouseChoice(row.getString("id"), row.getString("name")),
        tenant);
  }
}
