package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.domain.inspection.InspectionTaskStatus;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InspectionTaskStartPolicyTest {
  private static final LocalDateTime DUE = LocalDateTime.parse("2026-11-05T08:00:00");
  private static final LocalDateTime EXPIRES = LocalDateTime.parse("2026-11-05T10:00:00");

  @Test
  void startEligibilityUsesTheSameDueAndExpiryBoundariesForDailyAndPeriodicTasks() {
    var auth = mock(AuthContext.class);
    when(auth.userId()).thenReturn(13L);
    var service =
        new InspectionTaskAccessService(new ObjectMapper(), auth, mock(PermissionService.class));
    var row = new LinkedHashMap<String, Object>();
    row.put("status", InspectionTaskStatus.PENDING.code());
    row.put("snapshot", "{\"personMode\":\"指定负责人\",\"owners\":[13]}");
    row.put("owner_id", null);
    row.put("due_at", DUE.toString());
    row.put("expires_at", EXPIRES.toString());

    assertFalse(service.canStart(row, Map.of("allowLate", false), DUE.minusNanos(1)));
    assertTrue(service.canStart(row, Map.of("allowLate", false), DUE));
    assertTrue(service.canStart(row, Map.of("allowLate", false), EXPIRES));
    assertFalse(service.canStart(row, Map.of("allowLate", false), EXPIRES.plusNanos(1)));
    assertTrue(service.canStart(row, Map.of("allowLate", true), EXPIRES.plusNanos(1)));
  }

  @Test
  void terminalAndNonStartableStatusesNeverExposeStartAction() {
    var auth = mock(AuthContext.class);
    when(auth.userId()).thenReturn(13L);
    var service =
        new InspectionTaskAccessService(new ObjectMapper(), auth, mock(PermissionService.class));
    var row = new LinkedHashMap<String, Object>();
    row.put("snapshot", "{\"personMode\":\"指定负责人\",\"owners\":[13]}");
    row.put("owner_id", null);
    row.put("due_at", DUE.toString());
    row.put("expires_at", EXPIRES.toString());

    row.put("status", InspectionTaskStatus.IN_PROGRESS.code());
    assertFalse(service.canStart(row, Map.of("allowLate", true), DUE));

    row.put("status", InspectionTaskStatus.EXEMPT.code());
    assertFalse(service.canStart(row, Map.of("allowLate", true), DUE));
  }
}
