package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.service.inventory.InventoryMutex;
import org.junit.jupiter.api.Test;

class EquipmentRelationsCommandTest {
  final ObjectMapper json = new ObjectMapper();
  final AuthContext auth = mock(AuthContext.class);
  final PermissionService permissions = mock(PermissionService.class);
  final EquipmentWriteRepository writes = mock(EquipmentWriteRepository.class);
  final EquipmentRelationsService relations = mock(EquipmentRelationsService.class);

  EquipmentCommandService commands(int version) {
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    var parent = new Equipment();
    parent.setId(901L);
    parent.setCompanyOrgId(71001L);
    parent.setRowVersion(version);
    when(writes.find(10001L, 901L)).thenReturn(parent);
    when(writes.advanceVersion(10001L, 901L, version, 73001L)).thenReturn(1);
    return new EquipmentCommandService(relations, mock(EquipmentFileService.class),
        mock(EquipmentQueryService.class), writes, json, auth, permissions);
  }

  @Test void staleVersionCannotReplaceRelations() {
    var service = commands(4);
    assertThrows(org.springframework.web.server.ResponseStatusException.class,
        () -> service.updateRelations(901L, json.createArrayNode(), 3, false));
    verify(writes, never()).advanceVersion(anyLong(), anyLong(), anyInt(), anyLong());
    verify(relations, never()).saveBom(any(), any());
  }

  @Test void forbiddenWriteDoesNotAdvanceVersionOrChangeRelations() {
    var service = commands(4);
    doThrow(new IllegalArgumentException("denied")).when(permissions).checkEquipment(any(), eq("编辑"));
    assertThrows(IllegalArgumentException.class,
        () -> service.updateRelations(901L, json.createArrayNode(), 4, true));
    verify(writes, never()).advanceVersion(anyLong(), anyLong(), anyInt(), anyLong());
    verify(relations, never()).saveChildren(any(), any());
  }

  @Test void bomWriteUsesExistingLockOrderAndDoesNotSubmitEquipmentFields() {
    var service = commands(4);
    var rows = json.createArrayNode();
    service.updateRelations(901L, rows, 4, false);
    var ordered = inOrder(relations, permissions, writes);
    ordered.verify(writes).find(10001L, 901L);
    ordered.verify(relations).enter(java.util.List.of(71001L));
    ordered.verify(writes).find(10001L, 901L);
    ordered.verify(permissions).checkEquipment(any(), eq("编辑"));
    ordered.verify(writes).advanceVersion(10001L, 901L, 4, 73001L);
    ordered.verify(relations).saveBom(any(), same(rows));
    verify(writes, never()).update(any());
    verify(relations, never()).saveChildren(any(), any());
  }

  @Test void emptyBomCannotClearChildrenOrTheirLegacyRows() {
    var repo = mock(EquipmentRelationsRepository.class);
    when(repo.legacy(anyLong(), anyLong(), anyString())).thenReturn(java.util.List.of());
    var service = new EquipmentRelationsService(repo, auth, permissions, json, mock(InventoryMutex.class));
    var parent = new Equipment(); parent.setId(901L);
    service.saveBom(parent, json.createArrayNode());
    verify(repo).clearBom(anyLong(), eq(901L));
    verify(repo, never()).clearChildren(anyLong(), anyLong());
    verify(repo, never()).legacy(anyLong(), anyLong(), eq("child"));
    verify(repo, never()).clearLegacy(anyLong(), anyLong());
  }

  @Test void emptyChildrenCannotClearBomOrItsLegacyRows() {
    var repo = mock(EquipmentRelationsRepository.class);
    when(repo.legacy(anyLong(), anyLong(), anyString())).thenReturn(java.util.List.of());
    var service = new EquipmentRelationsService(repo, auth, permissions, json, mock(InventoryMutex.class));
    var parent = new Equipment(); parent.setId(901L);
    service.saveChildren(parent, json.createArrayNode());
    verify(repo).clearChildren(anyLong(), eq(901L));
    verify(repo, never()).clearBom(anyLong(), anyLong());
    verify(repo, never()).legacy(anyLong(), anyLong(), eq("bom"));
  }
}
