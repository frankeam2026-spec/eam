package com.windasoft.eam.dto.imports;

import com.windasoft.eam.dto.common.PageResult.Page;
import java.util.List;
import java.util.Map;

public record ImportPreview(List<Row> data, List<Column> columns, Page page) {

  public record Column(String key, String label) {}

  /** 模板支持可配置列，原始单元格按稳定字段键输出。 */
  public record Row(int row, Map<String, String> values, List<ImportIssue> issues) {}
}
