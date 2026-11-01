package com.windasoft.eam.repository.system;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.Test;

class SystemManagementOwnershipTest {
  @Test
  void organizationLockAndEventsBelongToOrganizationRepository() {
    var orgMethods = Arrays.stream(OrgUnitRepository.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName).toList();
    var managementMethods = Arrays.stream(SystemManagementRepository.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName).toList();

    assertTrue(orgMethods.contains("lockOrganizations"));
    assertTrue(orgMethods.contains("orgEvent"));
    assertTrue(orgMethods.contains("insertOrganization"));
    assertTrue(orgMethods.contains("updateOrganization"));
    assertTrue(orgMethods.contains("deleteOrganization"));
    assertTrue(orgMethods.contains("orgCodeCount"));
    assertFalse(managementMethods.contains("lockOrganizations"));
    assertFalse(managementMethods.contains("orgEvent"));
    assertFalse(managementMethods.contains("insertOrganization"));
    assertFalse(managementMethods.contains("updateOrganization"));
    assertFalse(managementMethods.contains("deleteOrganization"));
    assertFalse(managementMethods.contains("orgCodeCount"));
    assertFalse(managementMethods.contains("warehouseCount"));
    assertFalse(managementMethods.contains("warehouseChoices"));
  }
}
