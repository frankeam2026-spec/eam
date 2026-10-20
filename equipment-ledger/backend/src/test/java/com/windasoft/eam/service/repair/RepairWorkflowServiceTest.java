package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.basedata.BaseItemMapper;
import com.windasoft.eam.repository.equipment.EquipmentMapper;
import com.windasoft.eam.repository.repair.RepairOrderMapper;
import com.windasoft.eam.repository.repair.RepairWorkflowMapper;
import com.windasoft.eam.repository.system.SystemUserMapper;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class RepairWorkflowServiceTest {
  RepairWorkflowMapper flow;
  RepairOrderMapper orders;
  PermissionService permissions;
  RepairWorkflowService service;
  RepairOrder row;
  ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() {
    flow = mock(RepairWorkflowMapper.class);
    orders = mock(RepairOrderMapper.class);
    permissions = mock(PermissionService.class);
    var auth = mock(AuthContext.class);
    var user = new SystemUser();
    user.setId(73001L);
    user.setTenantId(10001L);
    user.setDisplayName("测试负责人");
    when(auth.userId()).thenReturn(73001L);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.user()).thenReturn(user);
    service =
        new RepairWorkflowService(
            new com.windasoft.eam.repository.repair.RepairWorkflowRepository(
                flow,
                orders,
                mock(EquipmentMapper.class),
                mock(SystemUserMapper.class),
                mock(BaseItemMapper.class), com.windasoft.eam.support.RelationStubs.repair()),
            auth,
            permissions,
            json,
            mock(com.windasoft.eam.service.basedata.BaseDataReferenceService.class));
    org.springframework.test.util.ReflectionTestUtils.setField(
        service,
        "experienceInventoryMutex",
        mock(com.windasoft.eam.service.inventory.InventoryMutex.class));
    org.springframework.test.util.ReflectionTestUtils.setField(
        service,
        "experiencePublisher",
        mock(org.springframework.context.ApplicationEventPublisher.class));
    org.springframework.test.util.ReflectionTestUtils.setField(
        service,
        "custody",
        mock(com.windasoft.eam.service.experience.MaterialCustodyService.class));
    row = new RepairOrder();
    row.setId(1L);
    row.setTenantId(10001L);
    row.setStatus("待接单");
    row.setRowVersion(4);
    row.setDeleted(0);
    when(flow.lock(10001L, 1L)).thenReturn(row);
  }

  @Test
  void staleClaimDoesNotCreateTodoOrAudit() {
    assertEquals(
        409,
        assertThrows(
                ResponseStatusException.class,
                () -> service.action(1L, 3, json.readTree("{\"action\":\"claim\"}")))
            .getStatusCode()
            .value());
    verify(flow, never()).claim(anyLong(), anyLong(), anyLong());
    verify(orders, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void nonCandidateCannotClaim() {
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> service.action(1L, 4, json.readTree("{\"action\":\"claim\"}")))
            .getStatusCode()
            .value());
    verify(flow, never()).closeTodos(anyLong(), anyLong());
  }

  @Test
  void alreadyClaimedOrderCannotBeClaimedAgain() {
    row.setStatus("处理中");
    row.setAssigneeUserId(73002L);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.action(1L, 4, json.readTree("{\"action\":\"claim\"}")));
    assertEquals(73002L, row.getAssigneeUserId());
    verify(orders, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void onlyAcceptedOwnerCanProcess() {
    row.setStatus("处理中");
    row.setAssigneeUserId(73002L);
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () -> service.action(1L, 4, json.readTree("{\"action\":\"process\"}")))
            .getStatusCode()
            .value());
    verify(orders, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void acceptanceRequiresRecipientAuthorization() {
    row.setStatus("待验收");
    assertEquals(
        403,
        assertThrows(
                ResponseStatusException.class,
                () ->
                    service.action(
                        1L,
                        4,
                        json.readTree("{\"action\":\"accept\",\"score\":5,\"comment\":\"通过\"}")))
            .getStatusCode()
            .value());
  }

  @Test
  void acceptanceRejectsInvalidScore() {
    row.setStatus("待验收");
    when(permissions.canAccept(any(), eq(row))).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.action(
                1L, 4, json.readTree("{\"action\":\"accept\",\"score\":6,\"comment\":\"通过\"}")));
    verify(orders, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void acceptanceRequiresExplanation() {
    row.setStatus("待验收");
    when(permissions.canAccept(any(), eq(row))).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.action(1L, 4, json.readTree("{\"action\":\"return\",\"score\":2}")));
    verify(orders, never()).updateById(any(RepairOrder.class));
  }

  @Test
  void suspendRequiresAcceptedOwner() {
    row.setStatus("处理中");
    row.setAssigneeUserId(73002L);
    assertThrows(
        ResponseStatusException.class,
        () -> service.action(1L, 4, json.readTree("{\"action\":\"suspend\",\"reason\":\"等待配件\"}")));
  }
}
