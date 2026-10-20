package com.windasoft.eam.dto.imports;

import java.util.*;

public record ImportBatchView(
    String id,
    String state,
    int rowVersion,
    int schemaVersion,
    int totalRows,
    int validRows,
    int errorRows,
    int errorCount,
    int autoNumberRows,
    int successRows,
    String message,
    String fileName,
    String createdAt,
    String expiresAt,
    boolean reportAvailable) {
  public static ImportBatchView of(Map<String, Object> r) {
    int total = n(r, "total_rows"), bad = n(r, "error_rows");
    return new ImportBatchView(
        r.get("id").toString(),
        r.get("state").toString(),
        n(r, "row_version"),
        n(r, "schema_version"),
        total,
        Math.max(0, total - bad),
        bad,
        n(r, "error_count"),
        n(r, "auto_number_rows"),
        n(r, "success_rows"),
        Objects.toString(r.get("message"), ""),
        r.get("file_name").toString(),
        r.get("created_at").toString(),
        r.get("expires_at").toString(),
        total > 0 && r.get("purged_at") == null);
  }

  private static int n(Map<String, Object> r, String k) {
    return ((Number) r.get(k)).intValue();
  }
}
