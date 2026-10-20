package com.windasoft.eam.service.system;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import java.util.*;
import org.junit.jupiter.api.Test;

class OrganizationScopeServiceTest {
  @Test
  void querySelectionIncludesChildCompaniesWithoutChangingPermissionExpansion() {
    var repository = mock(AuthorizationRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(repository.organizations(10001L))
        .thenReturn(
            List.of(
                org(1L, null, "集团"),
                org(2L, 1L, "公司"),
                org(3L, 2L, "部门"),
                org(4L, 3L, "部门"),
                org(5L, 2L, "公司"),
                org(6L, 5L, "部门"),
                org(7L, null, "公司"),
                org(8L, 7L, "部门")));
    var scopes = new OrganizationScopeService(auth, repository);
    assertEquals(Set.of(1L, 2L, 3L, 4L, 5L, 6L), scopes.queryTree(1L));
    assertEquals(Set.of(2L, 3L, 4L, 5L, 6L), scopes.queryTree(2L));
    assertEquals(Set.of(3L, 4L), scopes.queryTree(3L));
    assertEquals(Set.of(), scopes.queryTree(999L));
    assertEquals(Set.of(3L, 4L), scopes.departmentTree(3L));
    assertEquals(Set.of(), scopes.departmentTree(2L));
    assertEquals(Set.of(2L, 5L), scopes.companyTree(2L));
    assertEquals(Set.of(3L, 4L), scopes.companyDepartments(2L));
  }

  private static OrgUnit org(long id, Long parent, String type) {
    var row = new OrgUnit();
    row.setId(id);
    row.setParentId(parent);
    row.setOrgType(type);
    return row;
  }
}
