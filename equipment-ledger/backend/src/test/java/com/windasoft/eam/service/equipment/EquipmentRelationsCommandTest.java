package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyInt;
import static org.mockito.Mockito.anyLong;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.equipment.Equipment;
import com.windasoft.eam.repository.equipment.EquipmentBomRepository;
import com.windasoft.eam.repository.equipment.EquipmentOwnerRepository;
import com.windasoft.eam.repository.equipment.EquipmentRelationLegacyRepository;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.repository.equipment.EquipmentChildRepository;
import com.windasoft.eam.repository.equipment.EquipmentQueryRepository;
import com.windasoft.eam.repository.equipment.EquipmentWriteRepository;
import com.windasoft.eam.repository.inventory.MaterialRepository;
import com.windasoft.eam.service.inventory.InventoryMutex;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
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
    return new EquipmentCommandService(
        relations,
        mock(EquipmentFileService.class),
        mock(EquipmentQueryService.class),
        writes,
        json,
        auth,
        permissions,
        mock(com.windasoft.eam.service.business.BusinessValueService.class),
        mock(EquipmentInputValidation.class),
        mock(EquipmentOwnerRepository.class),
        mock(com.windasoft.eam.repository.basedata.BaseItemRepository.class),
        mock(com.windasoft.eam.repository.system.OrgUnitRepository.class),
        mock(com.windasoft.eam.repository.system.SystemUserRepository.class),
        mock(com.windasoft.eam.repository.equipment.EquipmentNumberRepository.class));
  }

  @Test
  void staleVersionCannotReplaceRelations() {
    var service = commands(4);
    assertThrows(
        org.springframework.web.server.ResponseStatusException.class,
        () -> service.updateRelations(901L, json.createArrayNode(), 3, false));
    verify(writes, never()).advanceVersion(anyLong(), anyLong(), anyInt(), anyLong());
    verify(relations, never()).saveBom(any(), any());
  }

  @Test
  void forbiddenWriteDoesNotAdvanceVersionOrChangeRelations() {
    var service = commands(4);
    doThrow(new IllegalArgumentException("denied"))
        .when(permissions)
        .checkEquipment(any(), eq("编辑"));
    assertThrows(
        IllegalArgumentException.class,
        () -> service.updateRelations(901L, json.createArrayNode(), 4, true));
    verify(writes, never()).advanceVersion(anyLong(), anyLong(), anyInt(), anyLong());
    verify(relations, never()).saveChildren(any(), any());
  }

  @Test
  void bomWriteUsesExistingLockOrderAndDoesNotSubmitEquipmentFields() {
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

  @Test
  void emptyBomCannotClearChildrenOrTheirLegacyRows() {
    var repo = mock(EquipmentRelationsRepository.class);
    var childr = mock(EquipmentChildRepository.class);
    var queryr = mock(EquipmentQueryRepository.class);
    var bomr = mock(EquipmentBomRepository.class);
    var reR  = mock(EquipmentRelationLegacyRepository.class);
    var mR =   mock(MaterialRepository.class);
    when(reR.list(anyLong(), anyLong(), anyString())).thenReturn(java.util.List.of());
    var service =
        new EquipmentRelationsService(repo, childr, queryr, auth, permissions, json, mock(InventoryMutex.class),
            mock(com.windasoft.eam.repository.system.OrgUnitRepository.class), bomr,reR,mR);
    var parent = new Equipment();
    parent.setId(901L);
    service.saveBom(parent, json.createArrayNode());
    verify(bomr).clear(anyLong(), eq(901L));
    verify(childr, never()).clear(anyLong(), anyLong());
    verify(reR, never()).list(anyLong(), anyLong(), eq("child"));
    verify(reR, never()).clear(anyLong(), anyLong());
  }

  @Test
  void emptyChildrenCannotClearBomOrItsLegacyRows() {
    var repo = mock(EquipmentRelationsRepository.class);
    var childr = mock(EquipmentChildRepository.class);
    var queryr = mock(EquipmentQueryRepository.class);
    var bomR = mock(EquipmentBomRepository.class);
    var reR =  mock(EquipmentRelationLegacyRepository.class);
    var mR =   mock(MaterialRepository.class);
    when(reR.list(anyLong(), anyLong(), anyString())).thenReturn(java.util.List.of());
    var service =
        new EquipmentRelationsService(repo, childr, queryr, auth, permissions, json, mock(InventoryMutex.class),
            mock(com.windasoft.eam.repository.system.OrgUnitRepository.class), bomR, reR,mR);
    var parent = new Equipment();
    parent.setId(901L);
    service.saveChildren(parent, json.createArrayNode());
    verify(childr).clear(anyLong(), eq(901L));
    verify(bomR, never()).clear(anyLong(), anyLong());
    verify(reR, never()).list(anyLong(), anyLong(), eq("bom"));
  }
}
