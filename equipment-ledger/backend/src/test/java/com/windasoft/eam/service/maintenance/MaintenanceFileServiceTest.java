package com.windasoft.eam.service.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.maintenance.MaintenanceUpload;
import com.windasoft.eam.entity.maintenance.MaintenanceFile;
import com.windasoft.eam.repository.maintenance.MaintenanceFileMapper;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MaintenanceFileServiceTest {
  @TempDir Path directory;

  @Test
  void registrationFailureMustRemoveTheAlreadyStoredFile() throws Exception {
    var mapper = mock(MaintenanceFileMapper.class);
    var failure = new IllegalStateException("登记失败");
    doThrow(failure).when(mapper).insert(any(MaintenanceFile.class));
    var permissions = mock(PermissionService.class);
    when(permissions.anyMaintenance("查看")).thenReturn(true);
    var repository = new MaintenanceFileRepository(mapper);
    var service =
        new MaintenanceFileService(
            mock(AuthContext.class),
            permissions,
            repository,
            new MaintenanceFileStorage(directory.toString()),
            mock(MaintenanceListService.class));
    assertSame(
        failure,
        assertThrows(
            IllegalStateException.class,
            () ->
                service.upload(
                    new MaintenanceUpload(
                        "保养手册.pdf",
                        "application/pdf",
                        1,
                        false,
                        new ByteArrayInputStream(new byte[] {1})))));
    try (var files = Files.walk(directory)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
  }

  @Test
  void deniedUploadMustNotCreateTheStorageDirectory() {
    var service =
        new MaintenanceFileService(
            mock(AuthContext.class),
            mock(PermissionService.class),
            new MaintenanceFileRepository(mock(MaintenanceFileMapper.class)),
            new MaintenanceFileStorage(directory.toString()),
            mock(MaintenanceListService.class));
    assertThrows(
        IllegalArgumentException.class,
        () ->
            service.upload(
                new MaintenanceUpload(
                    "x.pdf",
                    "application/pdf",
                    1,
                    false,
                    new ByteArrayInputStream(new byte[] {1}))));
    assertFalse(Files.exists(directory.resolve("maintenance")));
  }
}
