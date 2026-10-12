package com.windasoft.eam.inventory;

import java.util.*;

final class InventoryMoney {
  private static final Set<String> FIELDS =
      Set.of(
          "referencePrice",
          "unitPrice",
          "allocatedFee",
          "totalCost",
          "unitCost",
          "remainingCost",
          "amount",
          "totalAmount",
          "layerCost",
          "returnedAmount",
          "usedAmount",
          "taxAmount",
          "grossAmount");

  static void hide(Object value) {
    if (value instanceof Map<?, ?> raw) {
      var map = (Map<Object, Object>) raw;
      map.keySet().removeIf(k -> FIELDS.contains(k.toString()));
      map.values().forEach(InventoryMoney::hide);
    } else if (value instanceof List<?> rows) rows.forEach(InventoryMoney::hide);
  }
}
