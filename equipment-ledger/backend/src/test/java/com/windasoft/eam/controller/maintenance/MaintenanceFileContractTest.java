package com.windasoft.eam.controller.maintenance;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.maintenance.MaintenanceUpload;
import com.windasoft.eam.entity.maintenance.MaintenanceFile;
import com.windasoft.eam.repository.maintenance.MaintenanceFileRepository;
import com.windasoft.eam.service.maintenance.MaintenanceFileService;
import com.windasoft.eam.service.maintenance.MaintenanceFileStorage;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import com.windasoft.eam.support.ContractTypes;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

/** 验证 maintenanceUpload 的实际五字段响应、磁盘补偿与原有下载授权分支。 */
class MaintenanceFileContractTest {
  @TempDir Path directory;
  private final AuthContext auth = mock(AuthContext.class);
  private final PermissionService permissions = mock(PermissionService.class);
  private final MaintenanceFileRepository repository = mock(MaintenanceFileRepository.class);
  private final MaintenanceListService lists = mock(MaintenanceListService.class);
  private MaintenanceFileStorage storage;
  private MaintenanceFileService service;

  @BeforeEach
  void setup() {
    when(auth.tenantId()).thenReturn(71L);
    when(auth.userId()).thenReturn(73L);
    when(permissions.anyMaintenance("查看")).thenReturn(true);
    storage = new MaintenanceFileStorage(directory.toString());
    service = new MaintenanceFileService(auth, permissions, repository, storage, lists);
  }

  @Test
  void uploadKeepsHttpShapeBytesAndServerOwnedMetadata() throws Exception {
    var saved = new AtomicReference<MaintenanceFile>();
    doAnswer(
            call -> {
              saved.set(call.getArgument(0));
              return null;
            })
        .when(repository)
        .insert(any(MaintenanceFile.class));
    byte[] bytes = {1, 2, 3};
    var mvc =
        MockMvcBuilders.standaloneSetup(new MaintenanceFileController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
    var response =
        mvc.perform(
                multipart("/api/v1/maintenance/files")
                    .file(new MockMultipartFile("file", "保养手册.pdf", "application/pdf", bytes)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var root = new ObjectMapper().readTree(response.getContentAsByteArray());
    assertEquals(1, root.size());
    var data = root.path("data");
    assertEquals(5, data.size());
    String id = data.path("id").asText();
    assertEquals(id, UUID.fromString(id).toString());
    assertEquals("保养手册.pdf", data.path("name").asText());
    assertEquals("application/pdf", data.path("contentType").asText());
    assertTrue(data.path("size").isIntegralNumber());
    assertEquals(3, data.path("size").asLong());
    assertEquals("/api/v1/maintenance/files/" + id, data.path("url").asText());
    assertEquals(71L, saved.get().getTenantId());
    assertEquals(73L, saved.get().getUserId());
    assertEquals(id, saved.get().getId());
    assertArrayEquals(bytes, Files.readAllBytes(storage.path(id, ".pdf")));
    when(repository.find(71L, id)).thenReturn(saved.get());
    var download =
        mvc.perform(get("/api/v1/maintenance/files/" + id))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    assertEquals("nosniff", download.getHeader("X-Content-Type-Options"));
    assertEquals("application/pdf", download.getContentType());
    assertTrue(download.getHeader("Content-Disposition").startsWith("inline; filename*=UTF-8''"));
    assertArrayEquals(bytes, download.getContentAsByteArray());
  }

  @Test
  void uploaderCanDownloadWithoutConsultingOtherBusinessPermissions() throws Exception {
    var file = registered(73L);
    var downloaded = service.download(file.getId());
    assertEquals("保养.pdf", downloaded.name());
    assertEquals("application/pdf", downloaded.contentType());
    assertEquals(storage.path(file.getId(), ".pdf"), downloaded.path());
    verifyNoInteractions(lists);
  }

  @Test
  void otherUploaderRequiresAVisibleBusinessReference() throws Exception {
    var file = registered(99L);
    var denied = assertThrows(ResponseStatusException.class, () -> service.download(file.getId()));
    assertEquals(403, denied.getStatusCode().value());
    when(lists.hasFile(file.getId())).thenReturn(true);
    assertEquals("application/pdf", service.download(file.getId()).contentType());
  }

  @Test
  void missingTenantRecordCannotUseAnotherObjectsReference() {
    assertThrows(
        IllegalArgumentException.class, () -> service.download(UUID.randomUUID().toString()));
    verifyNoInteractions(lists);
  }

  @Test
  void invalidIdentifierDoesNotQueryMetadata() {
    assertThrows(IllegalArgumentException.class, () -> service.download("../foreign.pdf"));
    verifyNoInteractions(repository, lists);
  }

  @Test
  void partialCopyFailureRemovesPartialContentAndNeverRegisters() throws Exception {
    var broken =
        new InputStream() {
          private boolean started;

          @Override
          public int read() throws IOException {
            if (!started) {
              started = true;
              return 1;
            }
            throw new IOException("读取中断");
          }
        };
    assertThrows(
        IOException.class,
        () -> service.upload(new MaintenanceUpload("x.pdf", null, 2, false, broken)));
    try (var files = Files.walk(directory)) {
      assertEquals(0, files.filter(Files::isRegularFile).count());
    }
    verify(repository, never()).insert(any());
  }

  @Test
  void fileEntryAndItsServiceContractCannotRegressToDynamicTypes() throws Exception {
    for (var method : MaintenanceFileController.class.getDeclaredMethods()) {
      ContractTypes.assertExplicit(method.getGenericReturnType());
    }
    ContractTypes.assertExplicit(
        MaintenanceFileService.class
            .getMethod("upload", MaintenanceUpload.class)
            .getGenericReturnType());
    ContractTypes.assertExplicit(
        MaintenanceFileService.class.getMethod("download", String.class).getGenericReturnType());
    ContractTypes.assertExplicit(MaintenanceUpload.class);
    assertEquals(
        MaintenanceFile.class,
        MaintenanceFileRepository.class
            .getMethod("find", long.class, String.class)
            .getReturnType());
  }

  private MaintenanceFile registered(long user) throws IOException {
    var file = new MaintenanceFile();
    file.setId(UUID.randomUUID().toString());
    file.setUserId(user);
    file.setName("保养.pdf");
    file.setExtension(".pdf");
    file.setContentType("text/html");
    when(repository.find(71L, file.getId())).thenReturn(file);
    storage.store(file.getId(), ".pdf", new ByteArrayInputStream(new byte[] {1}));
    return file;
  }
}
