package com.windasoft.eam.controller.inspection;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.repository.inspection.InspectionFileRepository;
import com.windasoft.eam.service.inspection.InspectionFileService;
import com.windasoft.eam.service.inspection.InspectionListService;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/** 复用真实Service和临时文件存储，核对现有前端inspectionUpload读取的data结构。 */
class InspectionFileUploadContractTest {
  @TempDir Path directory;
  private final PermissionService permissions = mock(PermissionService.class);
  private MockMvc mvc;

  @BeforeEach
  void setup() {
    var caller = mock(AuthContext.class);
    when(caller.tenantId()).thenReturn(7L);
    when(caller.userId()).thenReturn(13L);
    when(permissions.anyInspection("查看")).thenReturn(true);
    var repository = new InspectionFileRepository(mock(JdbcTemplate.class), directory.toString());
    var service =
        new InspectionFileService(
            caller, permissions, repository, mock(InspectionListService.class));
    mvc =
        MockMvcBuilders.standaloneSetup(new InspectionFileController(service))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void multipartUploadKeepsItsResponseAndStoredBytes() throws Exception {
    byte[] bytes = {1, 2, 3, 4};
    var file = new MockMultipartFile("file", "检查手册.pdf", "application/pdf", bytes);
    var response =
        mvc.perform(multipart("/api/v1/inspection/files").file(file))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse();
    var root = new ObjectMapper().readTree(response.getContentAsByteArray());
    assertEquals(1, root.size());
    var data = root.path("data");
    assertEquals(5, data.size());
    assertTrue(data.path("id").isTextual());
    assertEquals("检查手册.pdf", data.path("name").asText());
    assertEquals("application/pdf", data.path("contentType").asText());
    assertTrue(data.path("size").isIntegralNumber());
    assertEquals(4, data.path("size").asLong());
    assertEquals("/api/v1/inspection/files/" + data.path("id").asText(), data.path("url").asText());
    assertEquals(
        4, Files.size(directory.resolve("inspection").resolve(data.path("id").asText() + ".pdf")));
    assertArrayEquals(
        bytes,
        Files.readAllBytes(
            directory.resolve("inspection").resolve(data.path("id").asText() + ".pdf")));
  }

  @Test
  void deniedUploadDoesNotCreateAFile() throws Exception {
    when(permissions.anyInspection("查看")).thenReturn(false);
    mvc.perform(
            multipart("/api/v1/inspection/files")
                .file(new MockMultipartFile("file", "x.pdf", "application/pdf", new byte[] {1})))
        .andExpect(status().isBadRequest());
    assertFalse(Files.exists(directory.resolve("inspection")));
  }
}
