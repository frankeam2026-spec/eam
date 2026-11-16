package com.windasoft.eam.controller.maintenance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskCommand;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskResultView;
import com.windasoft.eam.dto.maintenance.MaintenanceTaskView;
import com.windasoft.eam.service.maintenance.MaintenanceListService;
import com.windasoft.eam.service.maintenance.MaintenanceTaskService;
import com.windasoft.eam.support.ContractTypes;
import java.lang.reflect.Modifier;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.RequestBody;

class MaintenanceTaskContractTest {
  private final ObjectMapper json = new ObjectMapper().findAndRegisterModules();

  @Test
  void taskRoutesHaveExplicitDeepRequestAndResponseContracts() {
    for (var method :
        Stream.of(MaintenanceTaskController.class, MaintenanceController.class)
            .flatMap(type -> Arrays.stream(type.getDeclaredMethods()))
            .filter(method -> Modifier.isPublic(method.getModifiers()))
            .toList()) {
      ContractTypes.assertExplicit(method.getGenericReturnType());
      for (var parameter : method.getParameters()) {
        if (parameter.isAnnotationPresent(RequestBody.class)) {
          ContractTypes.assertExplicit(parameter.getParameterizedType());
        }
      }
    }
  }

  @Test
  void processReadsExactIdsDatesAndOnlyClientEditableFields() throws Exception {
    var request =
        json.readValue(
            """
{"rowVersion":3,"startAt":"2026-11-01T08:00:00.123456","endAt":"2026-11-01T09:00:00",
 "collaborators":["9007199254740993"],"items":[{"id":"standard:item","value":" 12.50 "}],
 "acceptance":{"reviewerId":"1","decision":"accept"},"tenantId":"1","holderId":""}
""",
            MaintenanceTaskCommand.Process.class);
    assertEquals(9007199254740993L, request.collaborators().getFirst());
    assertEquals(LocalDateTime.of(2026, 11, 1, 8, 0, 0, 123456000), request.startAt());
    assertEquals("standard:item", request.items().getFirst().getId());
    var serialized = json.valueToTree(request);
    assertFalse(serialized.has("acceptance"));
    assertFalse(serialized.has("tenantId"));
    assertFalse(serialized.has("action"));
  }

  @Test
  void fractionalVersionsAndUserIdsCannotBeTruncated() {
    for (String content :
        List.of("{\"rowVersion\":1.5}", "{\"rowVersion\":\"1\"}", "{\"rowVersion\":2147483648}")) {
      assertThrows(
          JsonProcessingException.class,
          () -> json.readValue(content, MaintenanceTaskCommand.Start.class));
    }
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                "{\"rowVersion\":1,\"collaborators\":[1.5]}",
                MaintenanceTaskCommand.Process.class));
    assertThrows(
        JsonProcessingException.class,
        () ->
            json.readValue(
                "{\"rowVersion\":1,\"score\":3.5}", MaintenanceTaskCommand.Accept.class));
  }

  @Test
  void absentExecutionStillSerializesAsEmptyObject() {
    assertEquals(
        json.createObjectNode(),
        json.valueToTree(
            new MaintenanceTaskResultView(null, null, null, null, null, null, null, null)));
    var view = new MaintenanceTaskView();
    view.setHistoricalSnapshot(json.createObjectNode().putNull("oldOptionalField"));
    var body = json.valueToTree(view);
    assertFalse(body.has("historicalSnapshot"));
    assertEquals(json.nullNode(), body.path("oldOptionalField"));
  }

  @Test
  void existingActionPathsBindTheCorrectCommandType() throws Exception {
    var service = mock(MaintenanceTaskService.class);
    var mvc =
        MockMvcBuilders.standaloneSetup(
                new MaintenanceTaskController(service, mock(MaintenanceListService.class)))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .build();
    mvc.perform(
            post("/api/v1/maintenance/tasks/19/process")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
{"rowVersion":2,"startAt":"2026-11-01T08:00:00","endAt":"2026-11-01T09:00:00","items":[]}
"""))
        .andExpect(status().isOk());
    verify(service).action(eq(19L), any(MaintenanceTaskCommand.Process.class));
    mvc.perform(
            post("/api/v1/maintenance/tasks/19/start")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"rowVersion\":1.5}"))
        .andExpect(status().isBadRequest());
  }
}
