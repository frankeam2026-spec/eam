package com.windasoft.eam.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.equipment.EquipmentMapper;
import com.windasoft.eam.equipment.NumberCounterMapper;
import com.windasoft.eam.system.AuthContext;
import com.windasoft.eam.system.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RepairConcurrencyTest {
    private RepairOrderMapper mapper;
    private RepairOrderController controller;
    private RepairOrder row;

    @BeforeEach void setUp() {
        mapper = mock(RepairOrderMapper.class);
        AuthContext auth = mock(AuthContext.class);
        when(auth.tenantId()).thenReturn(10001L);
        controller = new RepairOrderController(mapper, mock(NumberCounterMapper.class),
            new ObjectMapper(), mock(EquipmentMapper.class), auth, mock(PermissionService.class), mock(RepairQueryService.class));
        row = new RepairOrder(); row.setId(1L); row.setTenantId(10001L);
        row.setRowVersion(4); row.setStatus("处理中"); row.setDeleted(0);
        when(mapper.selectOne(any())).thenReturn(row);
    }

    @Test void staleEditDoesNotWrite() {
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> controller.update(1L, null, 3)).getStatusCode().value());
        verify(mapper, never()).updateById(any(RepairOrder.class));
    }

    @Test void staleTransitionDoesNotWrite() {
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> controller.action(1L, new RepairOrderController.Action("complete", null, "已修复", 10), 3))
            .getStatusCode().value());
        assertEquals("处理中", row.getStatus());
        verify(mapper, never()).updateById(any(RepairOrder.class));
    }

    @Test void concurrentWriteAfterReadAlsoConflicts() {
        when(mapper.updateById(any(RepairOrder.class))).thenReturn(0);
        assertEquals(409, assertThrows(ResponseStatusException.class,
            () -> controller.action(1L, new RepairOrderController.Action("complete", null, "已修复", 10), 4))
            .getStatusCode().value());
    }
}
