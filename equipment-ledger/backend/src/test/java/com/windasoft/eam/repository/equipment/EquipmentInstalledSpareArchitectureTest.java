package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/** 设备查询仓储不得直接访问库存表或协调维修、库存两个领域的数据源。 */
class EquipmentInstalledSpareArchitectureTest {
  @Test
  void equipmentQueryRepositoryDoesNotOwnInstalledSpareSqlOrCrossDomainComposition() {
    var fieldTypes =
        Arrays.stream(EquipmentQueryRepository.class.getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(java.lang.reflect.Field::getType)
            .toList();

    assertFalse(fieldTypes.contains(JdbcTemplate.class));
    assertFalse(fieldTypes.contains(com.windasoft.eam.repository.repair.RepairWorkflowMapper.class));
    assertFalse(
        Arrays.stream(EquipmentQueryRepository.class.getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("installedSpares")));
  }

  @Test
  void experienceRepositoryDoesNotDelegateBackToEquipmentQueryRepository() {
    var fieldTypes =
        Arrays.stream(com.windasoft.eam.repository.experience.EquipmentExperienceRepository.class
                .getDeclaredFields())
            .filter(field -> !Modifier.isStatic(field.getModifiers()))
            .map(java.lang.reflect.Field::getType)
            .toList();
    assertFalse(fieldTypes.contains(EquipmentQueryRepository.class));
    assertFalse(
        Arrays.stream(
                com.windasoft.eam.repository.experience.EquipmentExperienceRepository.class
                    .getDeclaredMethods())
            .anyMatch(method -> method.getName().equals("listInstalled")));
  }

  @Test
  void installedSpareResponseIsTypedRatherThanMapBased() throws Exception {
    var field =
        com.windasoft.eam.dto.equipment.EquipmentHistoryView.class
            .getDeclaredField("installedSpares");
    assertTrue(
        field.getGenericType()
            .getTypeName()
            .contains("EquipmentInstalledSpareView"));
    assertFalse(field.getGenericType().getTypeName().contains("java.util.Map"));
  }
}
