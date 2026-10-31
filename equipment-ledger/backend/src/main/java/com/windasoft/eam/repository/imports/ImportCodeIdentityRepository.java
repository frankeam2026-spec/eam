package com.windasoft.eam.repository.imports;

import java.util.Collection;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 设备导入的数据库排序规则一致性校验，不属于设备主表 CRUD。 */
@Repository
public class ImportCodeIdentityRepository {
  private final JdbcTemplate db;

  public ImportCodeIdentityRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, String> keys(Collection<String> codes) {
    return ImportCodeIdentity.keys(db, codes);
  }
}
