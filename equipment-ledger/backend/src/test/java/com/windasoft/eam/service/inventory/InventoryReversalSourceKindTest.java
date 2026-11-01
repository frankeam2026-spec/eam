package com.windasoft.eam.service.inventory;

import static org.junit.jupiter.api.Assertions.*;

import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.domain.inventory.InventoryReversalSourceKind;
import org.junit.jupiter.api.Test;

/** 冲销来源编码用于查找真实原始单据，不允许随枚举重构改变数据库和前端值。 */
class InventoryReversalSourceKindTest {
  @Test
  void retainsAllExistingSourceCodes() {
    assertEquals("receipt", InventoryReversalSourceKind.RECEIPT.code());
    assertEquals("issue", InventoryReversalSourceKind.ISSUE.code());
    assertEquals("returns", InventoryReversalSourceKind.RETURN.code());
    assertEquals("transfers", InventoryReversalSourceKind.TRANSFER.code());
    assertEquals("counts", InventoryReversalSourceKind.COUNT.code());
    assertEquals("openings", InventoryReversalSourceKind.OPENING.code());
    for (var source : InventoryReversalSourceKind.values())
      assertEquals(source, InventoryReversalSourceKind.parse(source.code()));
  }

  @Test
  void rejectsUnknownOrUnsupportedReversalSources() {
    assertThrows(LocalizedArgumentException.class, () -> InventoryReversalSourceKind.parse(null));
    assertThrows(LocalizedArgumentException.class, () -> InventoryReversalSourceKind.parse("reversals"));
    assertThrows(LocalizedArgumentException.class, () -> InventoryReversalSourceKind.parse("unknown"));
  }
}
