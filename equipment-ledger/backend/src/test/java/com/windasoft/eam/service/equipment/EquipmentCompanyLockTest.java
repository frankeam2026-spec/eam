package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.repository.system.OrgUnitRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EquipmentCompanyLockTest {
  private final EquipmentRelationsRepository equipment = mock(EquipmentRelationsRepository.class);
  private final OrgUnitRepository organizations = mock(OrgUnitRepository.class);
  private final InventoryMutex mutex = mock(InventoryMutex.class);
  private final AuthContext auth = mock(AuthContext.class);
  private final EquipmentRelationsService service =
      new EquipmentRelationsService(
          equipment, auth, mock(PermissionService.class), new ObjectMapper(), mutex, organizations);

  @BeforeEach
  void identity() {
    when(auth.tenantId()).thenReturn(10001L);
    when(organizations.lockExisting(anyLong(), anyLong())).thenReturn(true);
  }

  @Test
  void mutexComesFirstThenDistinctNonNullCompaniesInAscendingOrder() {
    service.enter(Arrays.asList(3L, null, 1L, 3L, 2L));
    var order = inOrder(mutex, organizations);
    order.verify(mutex).enter();
    order.verify(organizations).lockExisting(10001L, 1L);
    order.verify(organizations).lockExisting(10001L, 2L);
    order.verify(organizations).lockExisting(10001L, 3L);
    verifyNoMoreInteractions(organizations);
    verifyNoInteractions(equipment);
  }

  @Test
  void emptyCompaniesStillAcquireTheExistingInventoryMutex() {
    service.enter(List.of());
    verify(mutex).enter();
    verifyNoInteractions(organizations, equipment);
  }

  @Test
  void missingCompanyStopsBeforeLaterLocks() {
    when(organizations.lockExisting(10001L, 2L)).thenReturn(false);
    assertThrows(IllegalArgumentException.class, () -> service.enter(List.of(3L, 2L, 1L)));
    var order = inOrder(mutex, organizations);
    order.verify(mutex).enter();
    order.verify(organizations).lockExisting(10001L, 1L);
    order.verify(organizations).lockExisting(10001L, 2L);
    verify(organizations, never()).lockExisting(10001L, 3L);
  }

  @Test
  void databaseFailureIsNotConvertedToMissingCompany() {
    var failure = new IllegalStateException("模拟数据库锁失败");
    when(organizations.lockExisting(10001L, 1L)).thenThrow(failure);
    assertSame(
        failure, assertThrows(IllegalStateException.class, () -> service.enter(List.of(1L, 2L))));
    verify(organizations, never()).lockExisting(10001L, 2L);
  }

  @Test
  void inventoryMutexFailureDoesNotAcquireOrganizationLocks() {
    doThrow(new IllegalStateException("模拟互斥锁失败")).when(mutex).enter();
    assertThrows(IllegalStateException.class, () -> service.enter(List.of(1L)));
    verifyNoInteractions(organizations, equipment);
  }
}
