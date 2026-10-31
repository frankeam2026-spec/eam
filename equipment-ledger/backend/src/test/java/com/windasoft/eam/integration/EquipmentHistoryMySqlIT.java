package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.windasoft.eam.repository.equipment.EquipmentHistoryRepository;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 验证设备历史事件的 MyBatis-Plus 映射、租户隔离与稳定排序。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class EquipmentHistoryMySqlIT {
  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_ENGINEERING_DB_URL");
    if (url == null || !url.matches(
        "jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/eam_engineering_test(\\?.*)?")) {
      throw new IllegalStateException("只允许连接隔离的 eam_engineering_test 数据库");
    }
    properties.add("spring.datasource.url", () -> url);
    properties.add("spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "eam"));
    properties.add("spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
  }

  @Autowired JdbcTemplate db;
  @Autowired EquipmentHistoryRepository history;

  @Test
  void mapsExistingHistoryColumnsAndKeepsTenantScopeAndTieBreak() {
    long equipmentId = 994700001L;
    db.update("INSERT INTO eam_equipment(id,tenant_id,equipment_code,name,category,company,department,"
        + "company_org_id,department_org_id,purchase_date,acquired_at,created_at,acquired_amount)"
        + " VALUES(?,10001,'QA-HISTORY-001','测试历史设备','测试类别','测试公司','测试部门',"
        + "71002,71003,'2026-10-04','2025-01-01','2026-10-01 00:00:00',0)", equipmentId);
    var moment = Timestamp.valueOf("2026-10-31 10:30:00");
    db.update("INSERT INTO eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,"
        + "occurred_at,description,operator_name,status,result)"
        + " VALUES(?,10001,?,'MAINTENANCE','QA-1',?,'维护记录','张三','完成','已处理')",
        994710001L, equipmentId, moment);
    db.update("INSERT INTO eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,"
        + "occurred_at,description,operator_name,status,result)"
        + " VALUES(?,10001,?,'INSPECTION','QA-2',?,'巡检记录','李四','完成','正常')",
        994710002L, equipmentId, moment);
    db.update("INSERT INTO eam_equipment_history(id,tenant_id,equipment_id,record_type,record_no,"
        + "occurred_at) VALUES(?,10002,?,'MAINTENANCE','QA-OTHER',?)",
        994710003L, equipmentId, moment);

    var rows = history.list(10001L, equipmentId);
    assertEquals(2, rows.size());
    assertEquals("QA-2", rows.getFirst().getRecordNo());
    assertEquals("李四", rows.getFirst().getOperatorName());
    assertEquals("QA-1", rows.get(1).getRecordNo());
    assertEquals("已处理", rows.get(1).getResult());
    assertEquals(moment, rows.getFirst().getOccurredAt());
    assertTrue(history.list(10003L, equipmentId).isEmpty());
  }
}
