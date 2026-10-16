package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.equipment.NumberCounterMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RepairConcurrencyTest {
  private RepairOrderMapper mapper;
  private com.windasoft.eam.service.repair.RepairOrderService controller;
  private RepairOrder row;

  @BeforeEach
  void setUp() {
    mapper = mock(RepairOrderMapper.class);
    AuthContext auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    var json = new ObjectMapper();
    controller =
        new com.windasoft.eam.service.repair.RepairOrderService(
            new com.windasoft.eam.repository.repair.RepairOrderRepository(
                mapper,
                mock(EquipmentMapper.class),
                mock(com.windasoft.eam.repository.basedata.BaseItemMapper.class),
                mock(NumberCounterMapper.class),
                "./data/uploads"),
            null,
            json,
            auth,
            mock(PermissionService.class),
            new com.windasoft.eam.service.repair.RepairViewAssembler(json));
    row = new RepairOrder();
    row.setId(1L);
    row.setTenantId(10001L);
    row.setRowVersion(4);
    row.setStatus("处理中");
    row.setDeleted(0);
    when(mapper.selectOne(any())).thenReturn(row);
  }

  @Test
  void staleEditDoesNotWrite() {
    assertEquals(
        409,
        assertThrows(ResponseStatusException.class, () -> controller.update(1L, null, 3))
            .getStatusCode()
            .value());
    verify(mapper, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void staleTransitionDoesNotWrite() {
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.action(
                        1L,
                        new com.windasoft.eam.dto.repair.RepairAction("complete", null, "已修复", 10),
                        3))
            .getStatusCode()
            .value());
    assertEquals("处理中", row.getStatus());
    verify(mapper, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void concurrentWriteAfterReadAlsoConflicts() {
    when(mapper.updateById(any(RepairOrder.class))).thenReturn(0);
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    controller.action(
                        1L,
                        new com.windasoft.eam.dto.repair.RepairAction("complete", null, "已修复", 10),
                        4))
            .getStatusCode()
            .value());
  }
}
