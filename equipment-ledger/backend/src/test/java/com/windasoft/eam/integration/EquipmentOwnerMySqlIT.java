package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.windasoft.eam.entity.equipment.EquipmentOwner;
import com.windasoft.eam.repository.equipment.EquipmentOwnerRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 使用隔离 MySQL 验证 MyBatis-Plus 对无单列主键的负责人关系表进行读写。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class EquipmentOwnerMySqlIT {
  private static final long TENANT = 994500001L;
  private static final long EQUIPMENT = 994500101L;

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

  @Autowired EquipmentOwnerRepository owners;

  @Test
  void insertReadAndDeleteUseRealCompositeKeyTable() {
    owners.add(new EquipmentOwner(TENANT, EQUIPMENT, 7201L, "甲", 2));
    owners.add(new EquipmentOwner(TENANT, EQUIPMENT, 7202L, "乙", 1));
    owners.add(new EquipmentOwner(TENANT + 1, EQUIPMENT, 7203L, "其他租户", 0));

    assertEquals(
        List.of(7202L, 7201L),
        owners.list(TENANT, EQUIPMENT).stream().map(EquipmentOwner::getUserId).toList());
    assertEquals("乙", owners.list(TENANT, EQUIPMENT).getFirst().getOwnerName());
    owners.clear(TENANT, EQUIPMENT);
    assertEquals(List.of(), owners.list(TENANT, EQUIPMENT));
    assertEquals(1, owners.list(TENANT + 1, EQUIPMENT).size());
  }

  @Test
  void batchReadKeepsTenantAndEquipmentScopes() {
    owners.add(new EquipmentOwner(TENANT, EQUIPMENT, 7201L, "甲", 2));
    owners.add(new EquipmentOwner(TENANT, EQUIPMENT, 7202L, "乙", 1));
    owners.add(new EquipmentOwner(TENANT, EQUIPMENT + 1, 7203L, "丙", 0));
    owners.add(new EquipmentOwner(TENANT + 1, EQUIPMENT, 7204L, "其他租户", 0));

    var rows = owners.listByEquipmentIds(TENANT, List.of(EQUIPMENT, EQUIPMENT + 1));
    assertEquals(List.of(7202L, 7201L, 7203L), rows.stream().map(EquipmentOwner::getUserId).toList());
    assertEquals(
        List.of(7202L, 7201L),
        owners.listByEquipmentIds(TENANT, List.of(EQUIPMENT)).stream()
            .map(EquipmentOwner::getUserId).toList());
    assertEquals(List.of(), owners.listByEquipmentIds(TENANT, List.of(EQUIPMENT + 2)));
  }
}
