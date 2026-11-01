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
    var roleMethods = Arrays.stream(RoleManagementRepository.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName).toList();

    assertTrue(orgMethods.contains("lockOrganizations"));
    assertTrue(orgMethods.contains("orgEvent"));
    assertTrue(orgMethods.contains("insertOrganization"));
    assertTrue(orgMethods.contains("updateOrganization"));
    assertTrue(orgMethods.contains("deleteOrganization"));
    assertTrue(orgMethods.contains("orgCodeCount"));
    assertTrue(roleMethods.contains("insertRole"));
    assertTrue(roleMethods.contains("updateRole"));
    assertTrue(roleMethods.contains("deleteRole"));
    assertTrue(roleMethods.contains("menuPermissions"));
    var userMethods = Arrays.stream(SystemUserRepository.class.getDeclaredMethods())
        .map(java.lang.reflect.Method::getName).toList();
    assertTrue(userMethods.contains("insertUser"));
    assertTrue(userMethods.contains("updateUser"));
    assertTrue(userMethods.contains("deleteUser"));
    assertTrue(userMethods.contains("accountCount"));
    assertTrue(userMethods.contains("emailProfile"));
    assertTrue(userMethods.contains("updateEmail"));
    assertTrue(Arrays.stream(OrganizationReferenceRepository.class.getDeclaredMethods())
        .anyMatch(m -> m.getName().equals("businessReferences")));
  }
}
