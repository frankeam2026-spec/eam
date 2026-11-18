package com.windasoft.eam.service.business;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.entity.system.OrgUnit;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.business.BusinessDictionaryRepository;
import com.windasoft.eam.repository.business.BusinessOptionRepository;
import com.windasoft.eam.repository.system.AuthorizationRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.OrganizationScopeService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BusinessReferencePathTest {
  private OrgUnit org(long id, String name, String type, Long parent) {
    var o = new OrgUnit();
    o.setId(id);
    o.setName(name);
    o.setOrgType(type);
    o.setParentId(parent);
    o.setStatus(1);
    o.setTenantId(1L);
    return o;
  }

  @Test
  void departmentChoicesUseArrowsAndKeepTheOriginalIdentityAndSourceNames() {
    var repo = mock(OrgUnitRepository.class);
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    when(auth.tenantId()).thenReturn(1L);
    when(permissions.scope(anyString(), eq("查看")))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    var organizations =
        List.of(org(1, "集团", "集团", null), org(2, "公司", "公司", 1L), org(3, "部门", "部门", 2L));
    when(repo.organizations(1L)).thenReturn(organizations);
    var service =
        new BusinessReferenceService(
            mock(BusinessDictionaryRepository.class),
            mock(BusinessOptionRepository.class),
            mock(BaseItemRepository.class),
            repo,
            mock(SystemUserRepository.class),
            new OrganizationScopeService(auth, mock(AuthorizationRepository.class)),
            auth,
            permissions,
            mock(BusinessConfigService.class));
    var result = service.all("equipment", "department", false);
    assertEquals(1, result.size());
    assertEquals("集团 > 公司 > 部门", result.getFirst().name());
    assertEquals("3", result.getFirst().id());
    assertEquals("部门", organizations.getLast().getName());
  }
}
