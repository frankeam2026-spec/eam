package com.windasoft.eam.service.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import com.windasoft.eam.repository.inventory.InventoryMasterRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InventoryMaterialNumberTest {
  @Test
  void materialUsesLifetimeFiveDigits() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    AuthContext auth = mock(AuthContext.class);
    NumberCounterMapper counter = mock(NumberCounterMapper.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(counter.currentValue()).thenReturn(1L);
    when(db.queryForObject(anyString(), eq(Integer.class), eq(10001L), anyString())).thenReturn(0);
    InventoryService service =
        new InventoryService(
            new InventoryMasterRepository(db, counter),
            auth,
            mock(PermissionService.class),
            new ObjectMapper());
    assertEquals("WL00001", service.number("materials"));
    verify(counter).nextMonthlyNumber("inv-materials-lifetime-10001", "all");
  }

  @Test
  void collisionSkipsExistingMaterialAndSequenceNeverTruncates() {
    JdbcTemplate db = mock(JdbcTemplate.class);
    AuthContext auth = mock(AuthContext.class);
    NumberCounterMapper counter = mock(NumberCounterMapper.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(counter.currentValue()).thenReturn(99999L, 100000L);
    when(db.queryForObject(anyString(), eq(Integer.class), eq(10001L), anyString()))
        .thenReturn(1, 0);
    InventoryService service =
        new InventoryService(
            new InventoryMasterRepository(db, counter),
            auth,
            mock(PermissionService.class),
            new ObjectMapper());
    assertEquals("WL100000", service.number("materials"));
    verify(counter, times(2)).nextMonthlyNumber("inv-materials-lifetime-10001", "all");
  }
}
