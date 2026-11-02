package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.windasoft.eam.repository.basedata.BaseItemMapper;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.system.OrgUnitMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 避免设备仓储重新承担字典、用户、组织和跨关系转发等职责。 */
class EquipmentDataOwnershipArchitectureTest {
  @Test
  void writeRepositoryOnlyOwnsEquipmentAccessAndDoesNotCoordinateOtherTables() {
    var fields =
        Arrays.stream(EquipmentWriteRepository.class.getDeclaredFields())
            .map(Field::getType)
            .toList();
    assertTrue(fields.contains(EquipmentMapper.class));
    assertFalse(fields.contains(OrgUnitMapper.class));
    assertFalse(fields.contains(SystemUserMapper.class));
    assertFalse(fields.contains(BaseItemMapper.class));
    assertFalse(fields.contains(NumberCounterMapper.class));
    noMethods(
        EquipmentWriteRepository.class,
        "baseItem",
        "activeBase",
        "organization",
        "activeDepartment",
        "user",
        "nextEquipmentNumber",
        "currentValue",
        "importCodeKeys");
  }

  @Test
  void relationRepositoryDoesNotDelegateWritesAcrossObjectOwnership() {
    var fields =
        Arrays.stream(EquipmentRelationsRepository.class.getDeclaredFields())
            .map(Field::getType)
            .toList();
    assertTrue(fields.contains(EquipmentMapper.class));
    assertFalse(fields.contains(EquipmentChildRepository.class));
    assertFalse(fields.contains(EquipmentBomRepository.class));
    assertFalse(fields.contains(EquipmentQueryRepository.class));
    noMethods(
        EquipmentRelationsRepository.class,
        "parents",
        "addChild",
        "clearChildren",
        "relationshipCount",
        "children",
        "bom");
  }

  @Test
  void queryRepositoryDoesNotOwnUserOrOrganizationSelection() {
    var fields =
        Arrays.stream(EquipmentQueryRepository.class.getDeclaredFields())
            .map(Field::getType)
            .toList();
    assertFalse(fields.contains(OrgUnitMapper.class));
    assertFalse(fields.contains(SystemUserMapper.class));
    noBaseItemMapper(EquipmentQueryRepository.class);
    noRelationAssembly(EquipmentQueryRepository.class);
    noMethods(
        EquipmentQueryRepository.class,
        "ownerCandidates",
        "employees",
        "organizations",
        "baseItem",
        "pageRelations",
        "children",
        "bom");
  }

  @Test
  void dictionaryOwnershipCheckRejectsDirectMapperDependency() {
    assertThrows(AssertionError.class, () -> noBaseItemMapper(DirectDictionaryAccess.class));
  }

  @Test
  void relationAssemblyCheckRejectsDependenciesAndAssemblyEntryPoints() {
    assertThrows(AssertionError.class, () -> noRelationAssembly(DirectRelationAccess.class));
    assertThrows(AssertionError.class, () -> noRelationAssembly(RelationAssemblyEntry.class));
  }

  private static void noRelationAssembly(Class<?> owner) {
    var fields = Arrays.stream(owner.getDeclaredFields()).map(Field::getType).toList();
    assertFalse(fields.contains(EquipmentChildRepository.class));
    assertFalse(fields.contains(EquipmentBomRepository.class));
    assertFalse(fields.contains(EquipmentRelationLegacyRepository.class));
    assertFalse(fields.contains(BaseItemRepository.class));
    noMethods(owner, "pageRelations", "children", "bom");
  }

  private static class DirectRelationAccess {
    private EquipmentChildRepository children;
  }

  private static class RelationAssemblyEntry {
    void pageRelations() {}
  }

  private static void noBaseItemMapper(Class<?> owner) {
    assertFalse(
        Arrays.stream(owner.getDeclaredFields())
            .anyMatch(field -> field.getType().equals(BaseItemMapper.class)),
        "设备查询必须通过基础资料Repository读取字典，不能直接持有其Mapper");
  }

  private static class DirectDictionaryAccess {
    private BaseItemMapper mapper;
  }

  private static void noMethods(Class<?> owner, String... names) {
    var methodNames = Arrays.stream(owner.getDeclaredMethods()).map(Method::getName).toList();
    for (var name : names) {
      assertFalse(methodNames.contains(name), owner.getSimpleName() + "不应承担：" + name);
    }
  }
}
