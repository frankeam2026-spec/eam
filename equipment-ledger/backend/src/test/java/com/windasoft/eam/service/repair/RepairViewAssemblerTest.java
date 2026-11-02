package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.time.LocalDateTime;
import java.util.Map;
import org.junit.jupiter.api.Test;

class RepairViewAssemblerTest {
  RepairOrder row() {
    var r = new RepairOrder();
    r.setId(9007199254740993L);
    r.setCustomFields(Map.of());
    r.setCustomFieldLabels(Map.of());
    r.setCustomSchemaVersion(0);
    r.setReportedAt(LocalDateTime.of(2026, 11, 2, 8, 30));
    return r;
  }

  @Test
  void viewNeedsNoDatabaseAndPreservesIdDateAndEmptyShapes() {
    var view = new RepairViewAssembler(new ObjectMapper()).view(row());
    assertEquals("9007199254740993", view.id());
    assertEquals("2026-11-02T08:30", view.reportedAt());
    assertEquals("", view.equipmentCategoryId());
    assertTrue(view.attachments().isArray());
    assertEquals(0, view.rowVersion());
    for (var f : RepairViewAssembler.class.getDeclaredFields()) {
      assertFalse(f.getType().getPackageName().startsWith("com.windasoft.eam.service.business"));
    }
  }

  @Test
  void corruptedJsonCannotPretendToBeEmptyAttachmentList() {
    var r = row();
    r.setAttachmentsJson("not json");
    assertThrows(
        IllegalStateException.class, () -> new RepairViewAssembler(new ObjectMapper()).view(r));
  }
}
