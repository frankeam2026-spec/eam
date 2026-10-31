package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.windasoft.eam.repository.equipment.EquipmentNumberRepository;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class EquipmentCodeServiceTest {
  final EquipmentWriteRepository equipment = mock(EquipmentWriteRepository.class);
  final EquipmentNumberRepository counter = mock(EquipmentNumberRepository.class);
  final AuthContext auth = mock(AuthContext.class);
  final EquipmentCodeService service = new EquipmentCodeService(equipment, counter, auth);

  @Test
  void refusesDuplicateCodeWithinTenant() {
    when(auth.tenantId()).thenReturn(10001L);
    when(equipment.countCode(10001L, "EQ-TEST", null)).thenReturn(1L);
    assertThrows(IllegalArgumentException.class, () -> service.assertAvailable("EQ-TEST", null));
  }

  @Test
  void repairsCounterWhenGeneratedCodeAlreadyExists() {
    when(auth.tenantId()).thenReturn(10001L);
    String year = String.valueOf(LocalDate.now().getYear());
    when(counter.currentValue()).thenReturn(9L, 10L);
    when(equipment.countCode(10001L, "EQ-" + year + "-0009", null)).thenReturn(1L);
    when(equipment.maxEquipmentNumber(year)).thenReturn(9L);

    assertEquals("EQ-" + year + "-0010", service.nextCode());
    verify(counter).nextEquipmentNumber(year, 0L);
    verify(counter).nextEquipmentNumber(year, 9L);
  }
}
