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
  void identifiersCannotBecomeArbitrarySql() {
    var db = mock(JdbcTemplate.class);
    var masters = new InventoryMasterRepository(db, mock(NumberCounterMapper.class));
    assertThrows(
        IllegalArgumentException.class,
        () -> masters.lookupParent(10001L, 1L, "inv_warehouse; DROP TABLE inv_balance"));
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
  void installedEquipmentUsageReadDelegatesToItsMapper() {
    var db = mock(JdbcTemplate.class);
    var mapper = mock(InventoryUsageMapper.class);
    var expected = List.of(new com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView());
    when(mapper.installedForEquipment(10001L, 3L)).thenReturn(expected);
    var repository =
        new InventoryUsageRepository(db, mock(RepairOrderMapper.class), mapper);

    assertSame(expected, repository.installedForEquipment(10001L, 3L));

    verify(mapper).installedForEquipment(10001L, 3L);
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
