package com.windasoft.eam.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.equipment.EquipmentFileUploadResult;
import org.junit.jupiter.api.Test;

class EquipmentFileUploadContractTest {
  @Test
  void typedResponsePreservesExistingUploadJsonShape() throws Exception {
    var json = new ObjectMapper().readTree(
        new ObjectMapper().writeValueAsString(
            new DataResponse<>(
                new EquipmentFileUploadResult("file-id", "附件.pdf", "application/pdf", 42L,
                    "/api/v1/equipment/files/file-id"))));
    var data = json.path("data");
    assertEquals("file-id", data.path("id").asText());
    assertEquals("附件.pdf", data.path("name").asText());
    assertEquals("application/pdf", data.path("contentType").asText());
    assertEquals(42L, data.path("size").asLong());
    assertEquals("/api/v1/equipment/files/file-id", data.path("url").asText());
    assertEquals(5, data.size());
  }
}
