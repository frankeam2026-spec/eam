package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class EquipmentOrganizationLockArchitectureTest {
  @Test
  void equipmentRepositoryDoesNotOwnTheOrganizationLock() {
    check(EquipmentRelationsRepository.class);
  }

  @Test
  void guardRejectsTheOldMisplacedEntry() {
    assertThrows(AssertionError.class, () -> check(MisplacedLock.class));
  }

  private static void check(Class<?> owner) {
    assertThrows(
        NoSuchMethodException.class,
        () -> owner.getDeclaredMethod("lockCompany", long.class, long.class));
  }

  private static final class MisplacedLock {
    void lockCompany(long tenant, long company) {}
  }
}
