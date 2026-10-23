package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class InspectionOpenAccessTest {
  @Test
  void openIsExplicitAndRequiresExecutionScopeAndSingleCurrentOperator() throws Exception {
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(11L);
    var permissions = mock(PermissionService.class);
    when(permissions.inspection("daily", "执行"))
        .thenReturn(new PermissionScope(false, null, Set.of(21L), false));
    var access = new InspectionTaskAccessService(new ObjectMapper(), auth, permissions);
    var row =
        new HashMap<String, Object>(
            Map.of(
                "tenant_id",
                10001L,
                "kind",
                "daily",
                "department_id",
                21L,
                "snapshot",
                "{\"personMode\":\"现场直接点检\",\"owners\":[]}"));
    assertTrue(access.execute(row));
    assertTrue(access.participant(row));
    row.put("owner_id", 12L);
    assertFalse(access.execute(row));
    row.remove("owner_id");
    row.put("department_id", 22L);
    assertFalse(access.execute(row));
    assertFalse(access.participant(row));
    row.put("department_id", 21L);
    row.put("tenant_id", 10002L);
    assertFalse(access.execute(row));
    row.put("tenant_id", 10001L);
    row.put("snapshot", "{\"personMode\":\"指定负责人\",\"owners\":[]}");
    assertFalse(access.execute(row));
    row.put("snapshot", "{\"personMode\":\"现场直接点检\",\"owners\":[]}");
    when(permissions.inspection("daily", "执行"))
        .thenThrow(new ResponseStatusException(HttpStatus.FORBIDDEN));
    assertFalse(access.execute(row));
  }
}
