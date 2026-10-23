package com.windasoft.eam.service.business;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.repository.business.BusinessConfigRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;

class BusinessReferencePathTest {
  @Test
  void departmentChoicesUseArrowsAndKeepTheOriginalIdentityAndSourceNames() {
    var repo = mock(BusinessConfigRepository.class);
    var auth = mock(AuthContext.class);
    var permissions = mock(PermissionService.class);
    when(auth.tenantId()).thenReturn(1L);
    when(permissions.scope(anyString(), eq("查看")))
        .thenReturn(new PermissionScope(true, null, Set.of(), false));
    List<Map<String, Object>> organizations = List.of(
        Map.of("id", "1", "name", "集团", "type", "集团"),
        Map.of("id", "2", "name", "公司", "type", "公司", "parentId", "1"),
        Map.of("id", "3", "name", "部门", "type", "部门", "parentId", "2"));
    when(repo.organizations(1L)).thenReturn(organizations);
    var service = new BusinessReferenceService(repo, auth, permissions, mock(BusinessConfigService.class));
    var result = service.all("equipment", "department", false);
    assertEquals(1, result.size());
    assertEquals("集团 > 公司 > 部门", result.getFirst().get("name"));
    assertEquals("3", result.getFirst().get("id"));
    assertEquals("部门", organizations.getLast().get("name"));
  }
}
