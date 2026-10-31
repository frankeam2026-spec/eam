package com.windasoft.eam.repository.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class InventoryRepositoryTest {
  @Test
  void materialIdReferencesSurviveTheWriteWhitelist() {
    var db = mock(JdbcTemplate.class);
    var repository = new InventoryMasterRepository(db, mock(NumberCounterMapper.class));
    var fields = new LinkedHashMap<String,Object>(); fields.put("name", "合规物料"); fields.put("unit_id", 11L); fields.put("category_id", 12L);
    assertDoesNotThrow(() -> repository.insertMaster("materials",fields,List.of("合规物料",11L,12L,21L,10001L)));
    assertDoesNotThrow(() -> repository.updateMaster("materials",fields,List.of("合规物料",11L,12L,10001L,21L)));
    verify(db,times(2)).update(anyString(), any(Object[].class));
  }

  @Test
  void identifiersCannotBecomeArbitrarySql() {
    var db = mock(JdbcTemplate.class);
    var masters = new InventoryMasterRepository(db, mock(NumberCounterMapper.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> masters.lookupParent(10001L, 1L, "inv_warehouse; DROP TABLE inv_balance"));
    assertThrows(
        IllegalArgumentException.class,
        () -> masters.insertMaster("materials", Map.of("name=? WHERE 1=1 --", "bad"), List.of()));
    var receipts = new ReceiptRepository(db, mock(NumberCounterMapper.class));
    assertThrows(
        IllegalArgumentException.class, () -> receipts.countCode(10001L, "bad", "eam_sys_user"));
    verifyNoInteractions(db);
  }

  @Test
  void nullableTaskDepartmentStaysNullable() {
    var db = mock(JdbcTemplate.class);
    when(db.queryForObject(anyString(), eq(Long.class), eq(10001L), eq(1L))).thenReturn(null);
    var repository =
        new RequisitionRepository(
            db, mock(NumberCounterMapper.class), mock(RepairOrderMapper.class));
    assertNull(repository.findTaskDepartment(10001L, 1L, "department_id", "v_maintenance_task"));
  }

  @Test
  void queryWhitelistRejectsInjectedFields() {
    var db = mock(JdbcTemplate.class);
    var repository = new InventoryQueryRepository(db);
    assertThrows(
        IllegalArgumentException.class,
        () -> repository.query(10001L, "balances", "", Map.of("m.name OR 1=1", "x"), List.of(1L)));
    verifyNoInteractions(db);
  }

  @Test
  void emptyBatchLookupDoesNotBuildInvalidInClause() {
    var db = mock(JdbcTemplate.class);
    var repository = new InventoryUsageRepository(db, mock(RepairOrderMapper.class), mock(InventoryUsageMapper.class));
    assertEquals(List.of(), repository.listEquipmentUsage(10001L, List.of()));
    verifyNoInteractions(db);
  }
}
