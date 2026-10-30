package com.windasoft.eam.controller.equipment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterialQuery;
import com.windasoft.eam.dto.equipment.EquipmentBomMaterials;
import com.windasoft.eam.entity.inventory.Material;
import com.windasoft.eam.repository.equipment.EquipmentRelationsRepository;
import com.windasoft.eam.service.equipment.EquipmentRelationsService;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EquipmentBomMaterialsContractTest {
  @Test
  void queryKeepsClamping() {
    assertEquals(
        new EquipmentBomMaterialQuery(" %_ ", 1, 1), new EquipmentBomMaterialQuery(" %_ ", -1, -1));
    assertEquals(100, new EquipmentBomMaterialQuery("", 1, 200).size());
  }

  @Test
  void serializationPreservesExactIdNullsAndOldPageShape() throws Exception {
    var response =
        new EquipmentBomMaterials(
            List.of(
                new EquipmentBomMaterials.Material(
                    "9007199254740993", "M1", "物料", null, "个", null, true)),
            new EquipmentBomMaterials.Page(1));
    var json = new ObjectMapper().readTree(new ObjectMapper().writeValueAsBytes(response));
    assertEquals("9007199254740993", json.path("data").get(0).path("id").asText());
    assertEquals(true, json.path("data").get(0).path("model").isNull());
    assertEquals(7, json.path("data").get(0).size());
    assertEquals(1, json.path("page").size());
  }

  @Test
  void bomControllerAndServiceHaveExplicitDeepContracts() throws Exception {
    var endpoint =
        EquipmentSelectionController.class.getMethod(
            "bomMaterials", String.class, int.class, int.class);
    var service =
        EquipmentRelationsService.class.getMethod("materials", EquipmentBomMaterialQuery.class);
    assertEquals(EquipmentBomMaterials.class, endpoint.getReturnType());
    assertEquals(EquipmentBomMaterials.class, service.getReturnType());
    explicit(endpoint.getGenericReturnType());
    explicit(EquipmentBomMaterialQuery.class);
    var internalRows =
        (ParameterizedType)
            EquipmentRelationsRepository.class
                .getMethod("materials", long.class, EquipmentBomMaterialQuery.class)
                .getGenericReturnType();
    assertEquals(Material.class, internalRows.getActualTypeArguments()[0]);
    assertThrows(AssertionError.class, () -> explicit(DynamicResponse.class));
    assertThrows(AssertionError.class, () -> explicit(NestedDynamicResponse.class));
  }

  private static void explicit(Type type) {
    if (type instanceof ParameterizedType generic && generic.getRawType() == List.class) {
      for (Type argument : generic.getActualTypeArguments()) {
        explicit(argument);
      }
      return;
    }
    if (type instanceof Class<?> model) {
      if (model.isPrimitive() || model == String.class) {
        return;
      }
      if (model.isRecord()) {
        for (var field : model.getRecordComponents()) {
          explicit(field.getGenericType());
        }
        return;
      }
    }
    throw new AssertionError("固定候选查询契约包含动态或未登记类型：" + type);
  }

  private record DynamicResponse(List<Map<String, Object>> data) {}

  private record NestedDynamicResponse(List<DynamicRow> data) {}

  private record DynamicRow(Object payload) {}
}
