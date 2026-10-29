package com.windasoft.eam.service.knowledge;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.experience.HomeAnalyticsService;
import com.windasoft.eam.service.system.PermissionService;
import java.util.*;
import org.junit.jupiter.api.Test;
class AiAnalyticsServiceTest {
  @Test void faultRateNeverSilentlyBecomesReportCount() throws Exception {
    var metrics=mock(HomeAnalyticsService.class);var gateway=mock(AiGateway.class);var permissions=mock(PermissionService.class);when(permissions.allowed("workspace","查看")).thenReturn(true);
    var service=new AiAnalyticsService(metrics,gateway,permissions,new ObjectMapper());
    var result=(Map<?,?>)service.tryAnswer("上月故障率排行",List.of());assertTrue(result.get("answer").toString().contains("分母"));verifyNoInteractions(metrics,gateway);
  }
  @Test void routedAnalyticsStillChecksPermissionsBeforeReadingTheCatalog() throws Exception {
    var metrics=mock(HomeAnalyticsService.class);var gateway=mock(AiGateway.class);var permissions=mock(PermissionService.class);
    var result=(Map<?,?>)new AiAnalyticsService(metrics,gateway,permissions,new ObjectMapper()).tryAnswer("本月设备表现",List.of());assertTrue(result.get("answer").toString().contains("权限"));verifyNoInteractions(metrics,gateway);
  }
}
