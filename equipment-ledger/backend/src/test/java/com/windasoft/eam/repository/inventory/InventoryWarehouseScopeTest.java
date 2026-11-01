package com.windasoft.eam.repository.inventory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InventoryWarehouseScopeTest {
  @Test
  void warehouseCountUsesTenantAndBoundIdsAndHandlesEmptyInput() {
    var db = mock(JdbcTemplate.class);
    var repository = new InventoryQueryRepository(db);
    assertEquals(0L, repository.warehouseCount(7L, List.of()));
    verifyNoInteractions(db);

    when(db.queryForObject(anyString(), eq(Long.class), any(Object[].class))).thenReturn(2L);
    assertEquals(2L, repository.warehouseCount(7L, List.of(101L, 102L)));
    var sql = org.mockito.ArgumentCaptor.forClass(String.class);
    var args = org.mockito.ArgumentCaptor.forClass(Object[].class);
    verify(db).queryForObject(sql.capture(), eq(Long.class), args.capture());
    assertTrue(sql.getValue().contains("tenant_id=?"));
    assertTrue(sql.getValue().contains("id IN (?,?)"));
    assertEquals(List.of(7L, 101L, 102L), List.of(args.getValue()));
  }

  @Test
  void warehouseChoicesRetainsTenantBoundJoinAndNameShape() {
    var db = mock(JdbcTemplate.class);
    var repository = new InventoryQueryRepository(db);
    when(db.queryForList(anyString(), eq(7L))).thenReturn(
        List.of(Map.of("id", "101", "name", "一厂 / 原料库")));
    assertEquals(
        List.of(Map.of("id", "101", "name", "一厂 / 原料库")),
        repository.warehouseChoices(7L));
    var sql = org.mockito.ArgumentCaptor.forClass(String.class);
    verify(db).queryForList(sql.capture(), eq(7L));
    assertTrue(sql.getValue().contains("o.tenant_id=w.tenant_id"));
    assertTrue(sql.getValue().contains("w.tenant_id=?"));
    assertTrue(sql.getValue().contains("ORDER BY o.name,w.name"));
  }
}
