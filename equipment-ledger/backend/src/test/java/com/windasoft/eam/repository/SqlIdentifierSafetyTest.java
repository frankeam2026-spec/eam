package com.windasoft.eam.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.windasoft.eam.repository.inventory.InventoryOperationRepository;
import com.windasoft.eam.repository.inventory.RequisitionRepository;
import com.windasoft.eam.repository.system.ReminderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

class SqlIdentifierSafetyTest {
  @Test
  void unsupportedDynamicTablesCannotReachJdbc() {
    var db = mock(JdbcTemplate.class);
    var reminders = new ReminderRepository(db);
    var operations = new InventoryOperationRepository(db, null, null);
    String unsafe = "inv_receipt WHERE 1=1 --";
    assertThrows(IllegalArgumentException.class, () -> reminders.ids(unsafe, 0, 20));
    assertThrows(IllegalArgumentException.class, () -> operations.loadSource(10001, 1, unsafe));
    verifyNoInteractions(db);
  }

  @Test
  void cleanupTaskKindIsNeverInterpolatedAsATable() {
    var db = mock(JdbcTemplate.class);
    var requests = new RequisitionRepository(db, null, null);
    requests.cleanupTaskStatus(10001, 1, "eam_repair_order WHERE 1=1 --");
    verify(db)
        .query(
            eq("SELECT status FROM v_inspection_task WHERE tenant_id=? AND id=? AND deleted=0"),
            org.mockito.ArgumentMatchers.<RowMapper<String>>any(),
            eq(10001L),
            eq(1L));
  }

  @Test
  void whitelistedTableKeepsUserValuesBound() {
    var db = mock(JdbcTemplate.class);
    var operations = new InventoryOperationRepository(db, null, null);
    String unsafe = "1 OR 1=1 --";
    operations.loadSource(10001L, unsafe, "inv_receipt");
    verify(db).queryForList("SELECT * FROM inv_receipt WHERE tenant_id=? AND id=?", 10001L, unsafe);
  }

}
