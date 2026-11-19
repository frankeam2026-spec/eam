package com.windasoft.eam.service.basedata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windasoft.eam.domain.system.PermissionAction;
import com.windasoft.eam.repository.basedata.BaseDataRepository;
import com.windasoft.eam.repository.basedata.BaseItemRepository;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class BaseDataPermissionTest {
  @Test
  void typedViewKeepsCategoryInventoryAndRepairFallbacksLimitedToTheirResources() {
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    var permissions = mock(PermissionService.class);
    var items = mock(BaseItemRepository.class);
    var service = new BaseDataService(mock(BaseDataRepository.class), items,
        mock(OrgUnitRepository.class), mock(MaterialRepository.class), auth, permissions);

    when(permissions.allowed("base", PermissionAction.VIEW)).thenReturn(true);
    service.list("categories", null);
    verify(items).list(10001L, "CATEGORY", null);

    when(permissions.allowed("base", PermissionAction.VIEW)).thenReturn(false);
    when(permissions.menuActions()).thenReturn(Map.of("inventoryQuery", Set.of("查看")));
    service.list("units", null);
    verify(items).list(10001L, "UNIT", null);

    when(permissions.allowed("repair", PermissionAction.VIEW)).thenReturn(true);
    service.list("fault-types", null);
    verify(items).list(10001L, "FAULT_TYPE", null);

    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions).require("base", PermissionAction.VIEW);
    assertEquals(HttpStatus.FORBIDDEN,
        assertThrows(ResponseStatusException.class, () -> service.list("sources", null)).getStatusCode());

    when(permissions.allowed("equipment", PermissionAction.VIEW)).thenReturn(true);
    service.list("sources", null);
    verify(items).list(10001L, "SOURCE", null);
  }
}
