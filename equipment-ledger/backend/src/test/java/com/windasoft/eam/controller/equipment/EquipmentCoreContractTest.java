package com.windasoft.eam.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.ApiExceptionHandler;
import com.windasoft.eam.dto.common.DataResponse;
import com.windasoft.eam.dto.equipment.EquipmentForm;
import com.windasoft.eam.entity.equipment.EquipmentOwner;
import com.windasoft.eam.repository.equipment.EquipmentOwnerRepository;
import com.windasoft.eam.service.equipment.EquipmentCommandService;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.Jackson2ObjectMapperBuilder;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class EquipmentCoreContractTest {
  MockMvc mvc;
  EquipmentCommandService service;
  final String body =
      """
      {"name":"设备","state":"正常","departmentOrgId":"9007199254740993",
       "locationId":"2","categoryId":"3","unitId":"4","useStateId":"5","ownerIds":["6"]}
      """;

  @BeforeEach
  void setup() {
    service = mock(EquipmentCommandService.class);
    ObjectMapper json = Jackson2ObjectMapperBuilder.json().build();
    mvc =
        MockMvcBuilders.standaloneSetup(new EquipmentController(service))
            .setMessageConverters(new MappingJackson2HttpMessageConverter(json))
            .setControllerAdvice(new ApiExceptionHandler())
            .build();
  }

  @Test
  void identifiersRemainExactAndOperationResponseShapesRemainCompatible() throws Exception {
    mvc.perform(post("/api/v1/equipment").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk());
    var captured = org.mockito.ArgumentCaptor.forClass(EquipmentForm.class);
    verify(service).create(captured.capture());
    assertEquals(9007199254740993L, captured.getValue().departmentOrgId());
    when(service.delete(1L)).thenReturn(true);
    mvc.perform(delete("/api/v1/equipment/1"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.deleted").value(true));
    when(service.batch(any())).thenReturn(2);
    mvc.perform(
            post("/api/v1/equipment/batch")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"ids\":[1,2],\"operation\":\"state\",\"value\":\"正常\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.count").value(2));
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.5", "true", "9223372036854775808", "\"1.0\"", "[]", "-1", "0", "null"})
  void invalidReferenceIdsAre400WithFieldLocation(String value) throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("\"9007199254740993\"", value)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors.departmentOrgId").isString());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(strings = {"1.9", "true", "9223372036854775808", "\"1.0\"", "0", "null"})
  void ownerIdsHaveElementValidationAndCannotBeTruncated(String value) throws Exception {
    mvc.perform(
            post("/api/v1/equipment")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body.replace("[\"6\"]", "[" + value + "]")))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.fieldErrors['ownerIds[0]']").isString());
    verifyNoInteractions(service);
  }

  @Test
  void ownerPersistenceAndControllerEnvelopesCannotRegressToMaps() throws Exception {
    Type owners =
        EquipmentOwnerRepository.class
            .getMethod("list", long.class, long.class)
            .getGenericReturnType();
    assertEquals(
        EquipmentOwner.class, ((ParameterizedType) owners).getActualTypeArguments()[0]);
    for (var method : EquipmentController.class.getDeclaredMethods()) {
      assertTrue(typedEnvelope(method.getGenericReturnType()), method.getName());
    }
    assertFalse(typedEnvelope(Object.class));
    assertFalse(typedEnvelope(Poison.class.getDeclaredMethod("map").getGenericReturnType()));
    assertFalse(typedEnvelope(Poison.class.getDeclaredMethod("object").getGenericReturnType()));
    assertFalse(typedEnvelope(Poison.class.getDeclaredMethod("unknown").getGenericReturnType()));
  }

  static boolean typedEnvelope(Type type) {
    if (!(type instanceof ParameterizedType p) || p.getRawType() != DataResponse.class) {
      return false;
    }
    Type value = p.getActualTypeArguments()[0];
    return value instanceof Class<?> c && c != Object.class && !Map.class.isAssignableFrom(c);
  }

  static class Poison {
    Map<String, Object> map() {
      return null;
    }

    DataResponse<Object> object() {
      return null;
    }

    DataResponse<?> unknown() {
      return null;
    }
  }
}
