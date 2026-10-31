package com.windasoft.eam.repository.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.windasoft.eam.entity.equipment.EquipmentOwner;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class EquipmentOwnerRepositoryTest {
  @Test
  void writesUseTheCompleteTenantScopedRelationshipKey() {
    var db = mock(JdbcTemplate.class);
    var repository = new EquipmentOwnerRepository(db);
    repository.add(new EquipmentOwner(10001L, 501L, 701L, "负责人", 2));
    repository.clear(10001L, 501L);

    verify(db).update(
        contains("INSERT INTO eam_equipment_owner(tenant_id,equipment_id,user_id,owner_name,sort_no)"),
        eq(10001L), eq(501L), eq(701L), eq("负责人"), eq(2));
    verify(db).update(
        contains("DELETE FROM eam_equipment_owner WHERE tenant_id=? AND equipment_id=?"),
        eq(10001L), eq(501L));
  }

  @Test
  void anEmptyPageDoesNotQueryTheDatabase() {
    var db = mock(JdbcTemplate.class);
    var repository = new EquipmentOwnerRepository(db);
    assertEquals(List.of(), repository.listByEquipmentIds(10001L, List.of()));
    verifyNoInteractions(db);
  }
}
