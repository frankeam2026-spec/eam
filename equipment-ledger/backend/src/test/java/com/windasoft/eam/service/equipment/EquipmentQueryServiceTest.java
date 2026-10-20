package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentOwnerView;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.service.equipment.EquipmentQueryService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class EquipmentQueryServiceTest {
  EquipmentQueryRepository repository;
  PermissionService permissions;
  EquipmentQueryService service;

  @BeforeEach
  void setup() {
    repository = mock(EquipmentQueryRepository.class);
    permissions = mock(PermissionService.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    service = new EquipmentQueryService(repository, auth, permissions, new ObjectMapper());
    var custom=mock(com.windasoft.eam.service.business.BusinessValueService.class);
    when(custom.read(anyString(),anyLong())).thenReturn(new com.windasoft.eam.service.business.BusinessValueService.Values(Map.of(),Map.of(),0));
    org.springframework.test.util.ReflectionTestUtils.setField(service,"custom",custom);
  }

  @Test
  void unavailableDeviceKeepsExistingError() {
    var error = assertThrows(IllegalArgumentException.class, () -> service.get(3L));
    assertEquals("设备不存在", error.getMessage());
    verifyNoInteractions(permissions);
  }

  @Test
  void permissionCheckedBeforeBuildingDetail() {
    var equipment = new Equipment();
    equipment.setId(3L);
    when(repository.find(10001L, 3L)).thenReturn(equipment);
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN, "denied"))
        .when(permissions)
        .checkEquipment(equipment, "查看");
    assertThrows(ResponseStatusException.class, () -> service.get(3L));
    verify(repository, never()).owners(anyLong(), any());
  }

  @Test
  void responsePreservesStringIdsMoneyScaleEmptyValuesAndAssociations() {
    var equipment = new Equipment();
    equipment.setId(3L);
    equipment.setEquipmentCode("EQ-QA");
    equipment.setOwnerUserId(73001L);
    equipment.setAcquiredAmount(new BigDecimal("12.30"));
    equipment.setRowVersion(2);
    when(repository.find(10001L, 3L)).thenReturn(equipment);
    when(repository.owners(10001L, 3L)).thenReturn(List.of(new EquipmentOwnerView("73001", "负责人")));
    when(repository.children(10001L, 3L))
        .thenReturn(List.of(Map.of("equipmentId", "4", "code", "EQ-C")));
    when(repository.bom(10001L, 3L))
        .thenReturn(List.of(Map.of("materialId", "5", "quantity", "1.00")));
    var view = service.get(3L);
    assertEquals("3", view.id());
    assertEquals("12.30", view.amount());
    assertEquals("", view.model());
    assertEquals("", view.categoryId());
    assertEquals(2, view.rowVersion());
    assertTrue(view.documents().isArray());
    assertEquals(List.of("73001"), view.ownerIds());
    assertEquals("4", view.children().getFirst().get("equipmentId"));
    assertEquals("1.00", view.relatedSpares().getFirst().get("quantity"));
    InOrder order = inOrder(repository, permissions);
    order.verify(repository).find(10001L, 3L);
    order.verify(permissions).checkEquipment(equipment, "查看");
    order.verify(repository).owners(10001L, 3L);
  }

  @Test
  void invalidOptionsActionDoesNotReadDatabase() {
    assertThrows(IllegalArgumentException.class, () -> service.batchOptions("删除"));
    verifyNoInteractions(repository, permissions);
  }

  @Test
  void deniedNavigationDoesNotReadDatabase() {
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN))
        .when(permissions)
        .require("equipment", "查看");
    assertThrows(ResponseStatusException.class, () -> service.navigationTree());
    verifyNoInteractions(repository);
  }

  @Test
  void qrAccessRequiresViewPermission() throws Exception {
    var equipment = new Equipment();
    equipment.setId(3L);
    equipment.setEquipmentCode("EQ-QA");
    when(repository.find(10001L, 3L)).thenReturn(equipment);
    byte[] png = service.qrCode(3L);
    assertArrayEquals(new byte[] {(byte) 137, 80, 78, 71}, Arrays.copyOf(png, 4));
    verify(permissions).checkEquipment(equipment, "查看");
  }
}
