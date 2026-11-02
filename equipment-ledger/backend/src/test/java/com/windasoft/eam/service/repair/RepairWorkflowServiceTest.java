package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.system.SystemUser;
import com.windasoft.eam.repository.equipment.EquipmentHistoryRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentSpareRepository;
import com.windasoft.eam.repository.inventory.LegacySpareStockRepository;
import com.windasoft.eam.repository.repair.RepairCandidateRepository;
import com.windasoft.eam.repository.repair.RepairEventRepository;
import com.windasoft.eam.repository.repair.RepairInboxRepository;
import com.windasoft.eam.repository.repair.RepairMaterialRepository;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.repository.repair.RepairWorkflowRepository;
import com.windasoft.eam.repository.system.NotificationRepository;
import com.windasoft.eam.repository.system.SystemUserRepository;
import com.windasoft.eam.service.basedata.BaseDataReferenceService;
import com.windasoft.eam.service.experience.MaterialCustodyService;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class RepairWorkflowServiceTest {
  RepairOrderRepository orderRows;
  RepairCandidateRepository candidates;
  RepairInboxRepository inboxes;
  PermissionService permissions;
  RepairWorkflowService service;
  RepairOrder row;
  ObjectMapper json = new ObjectMapper();

  @BeforeEach
  void setUp() {
    orderRows = mock(RepairOrderRepository.class);
    candidates = mock(RepairCandidateRepository.class);
    inboxes = mock(RepairInboxRepository.class);
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
            mock(RepairWorkflowRepository.class),
            auth,
            permissions,
            json,
            mock(BaseDataReferenceService.class),
            orderRows,
            candidates,
            mock(RepairEventRepository.class),
            inboxes,
            mock(RepairMaterialRepository.class),
            mock(LegacySpareStockRepository.class),
            mock(EquipmentSpareRepository.class),
            mock(EquipmentQueryRepository.class),
            mock(SystemUserRepository.class),
            mock(EquipmentHistoryRepository.class),
            mock(NotificationRepository.class),
            mock(RepairRelationsService.class),
            mock(RepairAttachmentAccessService.class),
            mock(RepairAccessService.class),
            mock(RepairQueryService.class));
    ReflectionTestUtils.setField(service, "experienceInventoryMutex", mock(InventoryMutex.class));
    ReflectionTestUtils.setField(service, "approvals", mock(RepairApprovalService.class));
    ReflectionTestUtils.setField(
        service, "experiencePublisher", mock(ApplicationEventPublisher.class));
    ReflectionTestUtils.setField(service, "custody", mock(MaterialCustodyService.class));
    row = new RepairOrder();
    row.setId(1L);
    row.setTenantId(10001L);
    row.setStatus("待接单");
    row.setRowVersion(4);
    row.setDeleted(0);
    when(orderRows.lock(10001L, 1L)).thenReturn(row);
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
    verify(candidates, never()).claim(anyLong(), anyLong(), anyLong());
    verify(orderRows, never()).update(any(RepairOrder.class));
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
    verify(inboxes, never()).closeTodos(anyLong(), anyLong());
  }

  @Test
  void alreadyClaimedOrderCannotBeClaimedAgain() {
    row.setStatus("处理中");
    row.setAssigneeUserId(73002L);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.action(1L, 4, json.readTree("{\"action\":\"claim\"}")));
    assertEquals(73002L, row.getAssigneeUserId());
    verify(orderRows, never()).update(any(RepairOrder.class));
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
    verify(orderRows, never()).update(any(RepairOrder.class));
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
    verify(orderRows, never()).update(any(RepairOrder.class));
  }

  @Test
  void acceptanceRequiresExplanation() {
    row.setStatus("待验收");
    when(permissions.canAccept(any(), eq(row))).thenReturn(true);
    assertThrows(
        IllegalArgumentException.class,
        () -> service.action(1L, 4, json.readTree("{\"action\":\"return\",\"score\":2}")));
    verify(orderRows, never()).update(any(RepairOrder.class));
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
