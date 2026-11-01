package com.windasoft.eam.service.basedata;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import java.util.Map;
import org.junit.jupiter.api.Test;

class BaseDataTypeTest {
  @Test
  void allExistingResourcesKeepDatabaseTypeAndPermissionMenu() {
    var expected = Map.of(
        "categories", "CATEGORY:category",
        "material-categories", "MATERIAL_CATEGORY:base",
        "units", "UNIT:base",
        "locations", "LOCATION:location",
        "use-statuses", "USE_STATUS:base",
        "sources", "SOURCE:base",
        "fault-types", "FAULT_TYPE:base",
        "processing-results", "PROCESS_RESULT:base",
        "fault-causes", "FAULT_CAUSE:base");
    assertEquals(expected.size(), BaseDataType.values().length);
    expected.forEach((resource, value) -> {
      var type = BaseDataType.fromResource(resource);
      assertEquals(value, type.code() + ":" + type.menu());
    });
  }

  @Test
  void preservesHierarchicalAndEquipmentReferenceRules() {
    assertEquals(
        java.util.Set.of(BaseDataType.CATEGORY, BaseDataType.MATERIAL_CATEGORY,
            BaseDataType.LOCATION, BaseDataType.FAULT_TYPE),
        java.util.Arrays.stream(BaseDataType.values())
            .filter(BaseDataType::hierarchical).collect(java.util.stream.Collectors.toSet()));
    assertEquals("category_id", BaseDataType.CATEGORY.equipmentReferenceColumn());
    assertEquals("unit_id", BaseDataType.UNIT.equipmentReferenceColumn());
    assertEquals("function_location_id", BaseDataType.LOCATION.equipmentReferenceColumn());
    assertNull(BaseDataType.FAULT_CAUSE.equipmentReferenceColumn());
  }

  @Test
  void rejectsUnknownResource() {
    assertThrows(LocalizedArgumentException.class, () -> BaseDataType.fromResource("unknown"));
    assertThrows(LocalizedArgumentException.class, () -> BaseDataType.fromResource(null));
  }
}
