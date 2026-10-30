package com.windasoft.eam.repository;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.windasoft.eam.repository.inventory.InventoryMasterRepository;
import com.windasoft.eam.repository.inventory.InventoryOperationRepository;
import com.windasoft.eam.repository.inventory.InventoryReservationRepository;
import com.windasoft.eam.repository.system.ReminderRepository;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class SqlIdentifierSafetyTest {
  @Test
  void unsupportedDynamicTablesCannotReachJdbc() {
    var db = mock(JdbcTemplate.class);
    var reminders = new ReminderRepository(db);
    var operations = new InventoryOperationRepository(db, null, null);
    var reservations = new InventoryReservationRepository(db);
    String unsafe = "inv_receipt WHERE 1=1 --";
    assertThrows(IllegalArgumentException.class, () -> reminders.ids(unsafe, 0, 20));
    assertThrows(IllegalArgumentException.class, () -> operations.loadSource(10001, 1, unsafe));
    assertThrows(
        IllegalArgumentException.class, () -> reservations.loadTask(10001, 1, "维修", unsafe));
    verifyNoInteractions(db);
  }

  @Test
  void whitelistedTableKeepsUserValuesBound() {
    var db = mock(JdbcTemplate.class);
    var operations = new InventoryOperationRepository(db, null, null);
    String unsafe = "1 OR 1=1 --";
    operations.loadSource(10001L, unsafe, "inv_receipt");
    verify(db).queryForList("SELECT * FROM inv_receipt WHERE tenant_id=? AND id=?", 10001L, unsafe);
  }

  @Test
  void unsupportedMasterColumnsCannotReachJdbc() {
    var db = mock(JdbcTemplate.class);
    var masters = new InventoryMasterRepository(db, null);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            masters.insertMaster(
                "materials",
                java.util.Map.of("name) VALUES ('x'); --", "value"),
                java.util.List.of("value")));
    verifyNoInteractions(db);
  }
}
