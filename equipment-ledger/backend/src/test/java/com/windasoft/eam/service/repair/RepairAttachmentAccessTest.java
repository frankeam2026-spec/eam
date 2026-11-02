package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.entity.repair.RepairUploadRecord;
import com.windasoft.eam.repository.repair.RepairOrderRepository;
import com.windasoft.eam.repository.repair.RepairUploadRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

class RepairAttachmentAccessTest {
  RepairUploadRepository uploads;
  RepairOrderRepository orders;
  RepairAccessService access;
  AuthContext auth;
  RepairAttachmentAccessService service;
  String file = "00000000-0000-0000-0000-000000000001";

  @BeforeEach
  void setup() {
    uploads = mock(RepairUploadRepository.class);
    orders = mock(RepairOrderRepository.class);
    access = mock(RepairAccessService.class);
    auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    service = new RepairAttachmentAccessService(uploads, orders, access, auth);
  }

  RepairUploadRecord upload(long tenant, long user) {
    var row = new RepairUploadRecord();
    row.setId(file);
    row.setTenantId(tenant);
    row.setUploadedBy(user);
    return row;
  }

  @Test
  void rejectsForeignTenantEvenWhenForgedLocalAssociationExists() {
    when(uploads.ownership(file)).thenReturn(upload(10002, 73001));
    assertThrows(IllegalArgumentException.class, () -> service.requireAttachable(file));
    verifyNoInteractions(orders, access);
  }

  @Test
  void uploaderCanBindOwnPendingUpload() {
    when(uploads.ownership(file)).thenReturn(upload(10001, 73001));
    assertDoesNotThrow(() -> service.requireAttachable(file));
    verifyNoInteractions(orders, access);
  }

  @Test
  void anotherUserCannotBindUnrelatedPendingUpload() {
    when(uploads.ownership(file)).thenReturn(upload(10001, 73002));
    when(orders.attachmentOrders(10001, file)).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> service.requireAttachable(file));
  }

  @Test
  void historicalFileRequiresVisibleOriginalOrder() {
    var order = new RepairOrder();
    when(orders.attachmentOrders(10001, file)).thenReturn(List.of(order));
    assertDoesNotThrow(() -> service.requireAttachable(file));
    verify(access).requireView(order);
  }

  @Test
  void deniedOriginalOrderCannotBeLaunderedByNewRelation() {
    var order = new RepairOrder();
    when(orders.attachmentOrders(10001, file)).thenReturn(List.of(order));
    doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireView(order);
    assertThrows(IllegalArgumentException.class, () -> service.requireAttachable(file));
  }

  @Test
  void orphanedHistoricalIdIsNotAProofOfOwnership() {
    when(orders.attachmentOrders(10001, file)).thenReturn(List.of());
    assertThrows(IllegalArgumentException.class, () -> service.requireAttachable(file));
  }
}
