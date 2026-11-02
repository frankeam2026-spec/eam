package com.windasoft.eam.service.repair;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.windasoft.eam.dto.repair.RepairUpload;
import com.windasoft.eam.entity.repair.RepairUploadRecord;
import com.windasoft.eam.repository.repair.*;
import com.windasoft.eam.service.system.*;
import java.io.ByteArrayInputStream;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataAccessResourceFailureException;

class RepairFileAuthorizationTest {
  @Test
  void downloadRejectsForeignOriginalOwnerBeforeAssociationsOrFilesystem() throws Exception {
    var orders = mock(RepairOrderRepository.class);
    var storage = mock(RepairFileStorage.class);
    var uploads = mock(RepairUploadRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    String id = "00000000-0000-0000-0000-000000000001";
    var original = new RepairUploadRecord();
    original.setTenantId(10002L);
    when(uploads.ownership(id)).thenReturn(original);
    var service =
        new RepairFileService(
            orders,
            storage,
            mock(RepairAccessService.class),
            uploads,
            auth,
            mock(PermissionService.class));
    assertThrows(IllegalArgumentException.class, () -> service.download(id));
    verifyNoInteractions(orders, storage);
  }

  @Test
  void failedRegistrationCleansOnlyNewlyStoredUpload() throws Exception {
    var storage = mock(RepairFileStorage.class);
    var uploads = mock(RepairUploadRepository.class);
    var auth = mock(AuthContext.class);
    when(auth.tenantId()).thenReturn(10001L);
    when(auth.userId()).thenReturn(73001L);
    var permissions = mock(PermissionService.class);
    when(permissions.allowed("repair", "报修")).thenReturn(true);
    doThrow(new DataAccessResourceFailureException("test failure")).when(uploads).register(any());
    var service =
        new RepairFileService(
            mock(RepairOrderRepository.class),
            storage,
            mock(RepairAccessService.class),
            uploads,
            auth,
            permissions);
    assertThrows(
        DataAccessResourceFailureException.class,
        () ->
            service.upload(
                new RepairUpload(
                    "proof.txt",
                    "text/plain",
                    1,
                    false,
                    new ByteArrayInputStream(new byte[] {1}))));
    var file = ArgumentCaptor.forClass(String.class);
    verify(storage).store(any(), file.capture(), eq(".txt"));
    verify(storage).remove(file.getValue(), ".txt");
  }
}
