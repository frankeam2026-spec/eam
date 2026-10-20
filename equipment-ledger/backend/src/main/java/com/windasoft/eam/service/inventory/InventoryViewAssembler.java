package com.windasoft.eam.service.inventory;

import static com.windasoft.eam.service.inventory.InventoryService.flag;

import java.math.BigDecimal;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class InventoryViewAssembler {
  public Map<String, Object> view(Map<String, Object> raw) {
    var result = new LinkedHashMap<String, Object>();
    raw.forEach(
        (key, value) -> {
          StringBuilder name = new StringBuilder();
          boolean upper = false;
          for (char c : key.toCharArray()) {
            if (c == '_') upper = true;
            else {
              name.append(upper ? Character.toUpperCase(c) : c);
              upper = false;
            }
          }
          String k = name.toString();
          result.put(
              k,
              k.equals("id") || k.endsWith("Id")
                  ? (value == null ? "" : value.toString())
                  : value instanceof BigDecimal decimal ? decimal.toPlainString() : value);
        });
    // DATETIME filters use MySQL's business calendar text, independent of JVM timezone.
    for (String field : List.of("CreatedAt", "ConfirmedAt"))
      if (result.containsKey("fmt" + field))
        result.put(
            Character.toLowerCase(field.charAt(0)) + field.substring(1),
            result.remove("fmt" + field));
    for (String key : List.of("enabled", "batchEnabled", "expiryEnabled"))
      if (result.containsKey(key)) result.put(key, flag(result.get(key)));
    return result;
  }
}
