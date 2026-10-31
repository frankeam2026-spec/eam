package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 避免设备仓储重新承担字典、用户、组织和跨关系转发等职责。 */
class EquipmentDataOwnershipArchitectureTest {
  @Test
  void writeRepositoryOnlyOwnsEquipmentAccessAndDoesNotCoordinateOtherTables() {
    var fields = Arrays.stream(EquipmentWriteRepository.class.getDeclaredFields())
        .map(java.lang.reflect.Field::getType).toList();
    assertTrue(fields.contains(EquipmentMapper.class));
    assertFalse(fields.contains(com.windasoft.eam.repository.system.OrgUnitMapper.class));
    assertFalse(fields.contains(com.windasoft.eam.repository.system.SystemUserMapper.class));
    assertFalse(fields.contains(com.windasoft.eam.repository.basedata.BaseItemMapper.class));
    assertFalse(fields.contains(NumberCounterMapper.class));
    noMethods(EquipmentWriteRepository.class, "baseItem", "activeBase", "organization",
        "activeDepartment", "user", "nextEquipmentNumber", "currentValue", "importCodeKeys");
  }

  @Test
  void relationRepositoryDoesNotDelegateWritesAcrossObjectOwnership() {
    var fields = Arrays.stream(EquipmentRelationsRepository.class.getDeclaredFields())
        .map(java.lang.reflect.Field::getType).toList();
    assertTrue(fields.contains(EquipmentMapper.class));
    assertFalse(fields.contains(EquipmentChildRepository.class));
    assertFalse(fields.contains(EquipmentBomRepository.class));
    assertFalse(fields.contains(EquipmentQueryRepository.class));
    noMethods(EquipmentRelationsRepository.class, "parents", "addChild",
        "clearChildren", "relationshipCount", "children", "bom");
  }

  @Test
  void queryRepositoryDoesNotOwnUserOrOrganizationSelection() {
    var fields = Arrays.stream(EquipmentQueryRepository.class.getDeclaredFields())
        .map(java.lang.reflect.Field::getType).toList();
    assertFalse(fields.contains(com.windasoft.eam.repository.system.OrgUnitMapper.class));
    assertFalse(fields.contains(com.windasoft.eam.repository.system.SystemUserMapper.class));
    noMethods(EquipmentQueryRepository.class, "ownerCandidates", "employees",
        "organizations", "baseItem");
  }

  private static void noMethods(Class<?> owner, String... names) {
    var methodNames = Arrays.stream(owner.getDeclaredMethods()).map(Method::getName).toList();
    for (var name : names) {
      assertFalse(methodNames.contains(name), owner.getSimpleName() + "不应承担：" + name);
    }
  }
}
