package com.windasoft.eam.service.equipment;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.equipment.EquipmentBatchOperation;
import org.junit.jupiter.api.Test;

/** 设备批量操作使用固定接口动作，但设备状态取值仍由可配置字典校验。 */
class EquipmentBatchOperationTest {
  @Test
  void preservesExistingBatchActionCodes() {
    assertEquals("location", EquipmentBatchOperation.LOCATION.code());
    assertEquals("category", EquipmentBatchOperation.CATEGORY.code());
    assertEquals("department", EquipmentBatchOperation.DEPARTMENT.code());
    assertEquals("owner", EquipmentBatchOperation.OWNER.code());
    assertEquals("state", EquipmentBatchOperation.STATE.code());
    assertEquals("delete", EquipmentBatchOperation.DELETE.code());
    for (var operation : EquipmentBatchOperation.values())
      assertEquals(operation, EquipmentBatchOperation.parse(operation.code()));
  }

  @Test
  void rejectsUnknownOperationsWithoutImplicitFallback() {
    assertThrows(LocalizedArgumentException.class, () -> EquipmentBatchOperation.parse(null));
    assertThrows(LocalizedArgumentException.class, () -> EquipmentBatchOperation.parse("unknown"));
  }
}
