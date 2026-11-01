package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.windasoft.eam.entity.inventory.InventoryUsage;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 验证安装备件 Mapper XML 的关联映射、租户隔离、状态过滤与稳定排序。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class EquipmentInstalledSpareMySqlIT {
  private static final long TENANT = 994800001L;
  private static final long OTHER_TENANT = 994800002L;
  private static final long EQUIPMENT = 994800101L;

  @DynamicPropertySource
  static void database(DynamicPropertyRegistry properties) {
    String url = System.getenv("EAM_ENGINEERING_DB_URL");
    if (url == null
        || !url.matches(
            "jdbc:mysql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/eam_engineering_test(\\?.*)?")) {
      throw new IllegalStateException("只允许连接隔离的 eam_engineering_test 数据库");
    }
    properties.add("spring.datasource.url", () -> url);
    properties.add(
        "spring.datasource.username",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_USER", "eam"));
    properties.add(
        "spring.datasource.password",
        () -> System.getenv().getOrDefault("EAM_ENGINEERING_DB_PASSWORD", ""));
  }

  @Autowired JdbcTemplate db;
  @Autowired InventoryUsageRepository usages;

  private void material(long id, long tenant, String code, String name) {
    db.update(
        "INSERT INTO inv_material(id,tenant_id,code,name,unit,model,category,enabled)"
            + " VALUES(?,?,?,?,?,?,?,1)",
        id, tenant, code, name, "件", "MODEL", "测试");
  }

  private void usage(
      long id, long tenant, long equipment, long material, String status, String key, Timestamp createdAt,
      String quantity) {
    db.update(
        "INSERT INTO inv_usage(id,tenant_id,allocation_id,requisition_id,equipment_id,material_id,"
            + "quantity,kind,destination,confirmation_key,status,actor_name,created_at)"
            + " VALUES(?,?,?,?,?,?,?,'安装','',?,?,?,?)",
        id, tenant, id + 1000, id + 2000, equipment, material, new BigDecimal(quantity),
        key, status, "测试人员", createdAt);
  }

  @Test
  void inventoryUsageEntityMapsEveryCurrentTableColumn() {
    var table = TableInfoHelper.getTableInfo(InventoryUsage.class);
    var mapped =
        table.getFieldList().stream()
            .map(field -> field.getColumn())
            .collect(java.util.stream.Collectors.toSet());
    mapped.add(table.getKeyColumn());
    var actual =
        new java.util.HashSet<>(
            db.queryForList(
                "SELECT COLUMN_NAME FROM information_schema.COLUMNS WHERE TABLE_SCHEMA=DATABASE()"
                    + " AND TABLE_NAME=?",
                String.class,
                "inv_usage"));
    assertEquals(actual, mapped);
  }

  @Test
  void installedUsageProjectionPreservesFieldsTenantScopeAndOrdering() {
    material(994800201L, TENANT, "MAT-001", "主轴轴承");
    material(994800202L, TENANT, "MAT-002", "传动皮带");
    material(994800203L, OTHER_TENANT, "MAT-003", "其他租户物料");
    Timestamp sameTime = Timestamp.valueOf("2026-10-31 10:20:30");
    usage(994800301L, TENANT, EQUIPMENT, 994800201L, "在用", "usage-active-001", sameTime, "1.000000");
    usage(994800302L, TENANT, EQUIPMENT, 994800202L, "在用", "usage-active-002", sameTime, "2.500000");
    usage(994800303L, TENANT, EQUIPMENT, 994800201L, "已换下", "usage-removed", 
        Timestamp.valueOf("2026-10-31 11:20:30"), "3.000000");
    usage(994800304L, OTHER_TENANT, EQUIPMENT, 994800203L, "在用", "usage-other-tenant", sameTime, "4.000000");

    var rows = usages.installedForEquipment(TENANT, EQUIPMENT);

    assertEquals(2, rows.size());
    assertEquals("994800302", rows.get(0).getId());
    assertEquals("MAT-002", rows.get(0).getCode());
    assertEquals("传动皮带", rows.get(0).getName());
    assertEquals("MODEL", rows.get(0).getModel());
    assertEquals(0, rows.get(0).getQuantity().compareTo(new BigDecimal("2.500000")));
    assertEquals("在用", rows.get(0).getStatus());
    assertEquals("2026-10-31 10:20", rows.get(0).getInstalledAt());
    assertEquals("994800301", rows.get(1).getId());
    assertTrue(usages.installedForEquipment(OTHER_TENANT, EQUIPMENT).stream()
        .allMatch(row -> row.getId().equals("994800304")));
    assertTrue(usages.installedForEquipment(TENANT, EQUIPMENT + 1).isEmpty());
  }
}
