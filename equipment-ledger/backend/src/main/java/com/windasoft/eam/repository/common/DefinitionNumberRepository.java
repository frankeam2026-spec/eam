package com.windasoft.eam.repository.common;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DefinitionNumberRepository {
  private static final Map<String, String> KINDS =
      Map.of("MS", "standards", "MP", "plans", "IS", "standards");
  private final NumberCounterMapper counter;
  private final JdbcTemplate db;

  public DefinitionNumberRepository(NumberCounterMapper counter, JdbcTemplate db) {
    this.counter = counter;
    this.db = db;
  }

  public long next(long tenant, String prefix) {
    if (!KINDS.containsKey(prefix))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0016");
    counter.nextMonthlyNumber("def-" + prefix + "-" + tenant, "all");
    return counter.currentValue();
  }

  public boolean exists(long tenant, String prefix, String code) {
    String kind = KINDS.get(prefix);
    if (kind == null)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0016");
    String table = prefix.equals("IS") ? "v_inspection_definition" : "v_maintenance_definition";
    return db.queryForObject(
            "SELECT COUNT(*) FROM " + table + " WHERE tenant_id=? AND kind=? AND code=?",
            Long.class,
            tenant,
            kind,
            code)
        > 0;
  }
}
