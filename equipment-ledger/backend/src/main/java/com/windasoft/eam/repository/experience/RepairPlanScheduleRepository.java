package com.windasoft.eam.repository.experience;

import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepairPlanScheduleRepository {
  private final JdbcTemplate db;
  private final SystemUserMapper users;

  public RepairPlanScheduleRepository(JdbcTemplate db, SystemUserMapper users) {
    this.db = db;
    this.users = users;
  }

  public List<Map<String, Object>> generationTargets() {
    return db.queryForList(
        "SELECT id,tenant_id,creator_id FROM eam_single_repair_plan WHERE status='待生成' AND"
            + " planned_at<=NOW(3) ORDER BY planned_at,id LIMIT 100");
  }

  public SystemUser findCreator(long id) {
    return users.selectById(id);
  }
}
