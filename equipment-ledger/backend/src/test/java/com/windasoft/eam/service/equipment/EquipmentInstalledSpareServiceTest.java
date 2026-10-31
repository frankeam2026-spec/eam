package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.repository.inventory.InventoryUsageRepository;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class EquipmentInstalledSpareServiceTest {
  @Test
  void mergesLegacyAndInventoryRecordsIntoTypedRowsInExistingSourceOrder() {
    var repair = mock(RepairWorkflowRepository.class);
    var inventory = mock(InventoryUsageRepository.class);
    var service = new EquipmentInstalledSpareService(repair, inventory);
    var legacyRow =
        Map.<String, Object>of(
            "id", "legacy-1",
            "code", "OLD-01",
            "name", "旧系统备件",
            "model", "M-OLD",
            "quantity", new BigDecimal("1.250000"),
            "status", "在用",
            "installedAt", "2026-10-30 09:10");
    var current = new EquipmentInstalledSpareView();
    current.setId("usage-1");
    current.setCode("MAT-01");
    current.setName("库存备件");
    current.setModel("M-NEW");
    current.setQuantity(new BigDecimal("2.500000"));
    current.setStatus("在用");
    current.setInstalledAt("2026-10-31 10:20");
    when(repair.installed(10001L, 3L)).thenReturn(List.of(legacyRow));
    when(inventory.installedForEquipment(10001L, 3L)).thenReturn(List.of(current));

    var rows = service.list(10001L, 3L);

    assertEquals(2, rows.size());
    assertEquals("legacy-1", rows.get(0).getId());
    assertEquals("OLD-01", rows.get(0).getCode());
    assertEquals("旧系统备件", rows.get(0).getName());
    assertEquals(new BigDecimal("1.250000"), rows.get(0).getQuantity());
    assertEquals("usage-1", rows.get(1).getId());
    assertEquals(new BigDecimal("2.500000"), rows.get(1).getQuantity());

    InOrder order = inOrder(repair, inventory);
    order.verify(repair).installed(10001L, 3L);
    order.verify(inventory).installedForEquipment(10001L, 3L);
    verifyNoMoreInteractions(repair, inventory);
  }
}
