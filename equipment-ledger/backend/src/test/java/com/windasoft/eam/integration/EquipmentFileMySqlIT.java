package com.windasoft.eam.integration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.windasoft.eam.repository.equipment.EquipmentFileRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** 在隔离 MySQL 验证设备文件元数据的 MyBatis-Plus CRUD 和租户边界。 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@EnabledIfEnvironmentVariable(named = "EAM_ENGINEERING_DB_URL", matches = ".+")
@Transactional
class EquipmentFileMySqlIT {
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

  @Autowired EquipmentFileRepository files;

  @Test
  void fileMetadataUsesMapperAndDoesNotLeakAcrossTenants() {
    String id = UUID.randomUUID().toString();
    files.insert(id, 994600001L, 994600101L, "测试文档.pdf",
        ".pdf", "application/pdf", 1024L);
    var row = files.find(994600001L, id);
    assertNotNull(row);
    assertEquals(id, row.getId());
    assertEquals("测试文档.pdf", row.getName());
    assertEquals("application/pdf", row.getContentType());
    assertEquals(994600101L, row.getUserId());
    assertEquals(1024L, row.getSize());
    assertNull(files.find(994600002L, id));
  }
}
