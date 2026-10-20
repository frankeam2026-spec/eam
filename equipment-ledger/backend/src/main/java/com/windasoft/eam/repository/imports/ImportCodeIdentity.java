package com.windasoft.eam.repository.imports;

import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Use MySQL's actual identifier collation, including accents and case, rather than guessing in
 * Java.
 */
public final class ImportCodeIdentity {
  private ImportCodeIdentity() {}

  public static Map<String, String> keys(JdbcTemplate db, Collection<String> input) {
    var list = input.stream().distinct().toList();
    var result = new HashMap<String, String>();
    for (int i = 0; i < list.size(); i += 250) {
      var part = list.subList(i, Math.min(i + 250, list.size()));
      var args = new ArrayList<Object>();
      for (var code : part) {
        args.add(code);
        args.add(code);
      }
      String select =
          "SELECT CAST(? AS CHAR CHARACTER SET utf8mb4) original_code,HEX(WEIGHT_STRING(CONVERT(?"
              + " USING utf8mb4) COLLATE utf8mb4_0900_ai_ci)) identity_key";
      for (var row :
          db.queryForList(
              String.join(" UNION ALL ", Collections.nCopies(part.size(), select)), args.toArray()))
        result.put(row.get("original_code").toString(), row.get("identity_key").toString());
    }
    return result;
  }
}
