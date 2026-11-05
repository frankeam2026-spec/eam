package com.windasoft.eam.service.inspection;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.windasoft.eam.dto.inspection.InspectionUpload;
import com.windasoft.eam.repository.inspection.InspectionFileRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;

class InspectionFileServiceTest {
  @TempDir Path temp;

  @Test
  void removesStoredFileWhenMetadataInsertFails() throws Exception {
    var db = mock(JdbcTemplate.class);
    var failure = new IllegalStateException("模拟附件元数据保存失败");
    doThrow(failure).when(db).update(anyString(), any(Object[].class));

    var repository = new InspectionFileRepository(db, temp.toString());
    var permissions = mock(PermissionService.class);
    when(permissions.anyInspection("查看")).thenReturn(true);
    var service =
        new InspectionFileService(
            mock(AuthContext.class), permissions, repository, mock(InspectionListService.class));
    var upload =
        new InspectionUpload(
            "inspection-photo.png",
            "image/png",
            4,
            false,
            new ByteArrayInputStream(new byte[] {1, 2, 3, 4}));

    assertThrows(IllegalStateException.class, () -> service.upload(upload));

    Path directory = temp.resolve("inspection");
    assertTrue(Files.isDirectory(directory));
    try (Stream<Path> files = Files.list(directory)) {
      assertEquals(0L, files.count());
    }
  }
}
