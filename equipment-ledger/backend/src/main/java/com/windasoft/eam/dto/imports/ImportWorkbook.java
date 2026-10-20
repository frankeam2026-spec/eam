package com.windasoft.eam.dto.imports;

import java.util.*;

public record ImportWorkbook(int schemaVersion, List<ImportColumn> columns, List<InputRow> rows) {
  public record InputRow(int row, Map<String, String> values, Set<String> formulas) {}
}
