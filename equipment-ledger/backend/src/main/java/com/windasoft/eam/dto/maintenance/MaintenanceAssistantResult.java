package com.windasoft.eam.dto.maintenance;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Collections;
import java.util.LinkedHashMap;
/** 固定汇总和表格元数据显式建模；仅data列由已校验目录和计划动态决定。 */
public sealed interface MaintenanceAssistantResult {
 record Count(BigDecimal value,long total) implements MaintenanceAssistantResult {}
 record Table(List<Map<String,Object>> data,long total,long records,List<String> columns) implements MaintenanceAssistantResult {
  public Table {
   data=data.stream().map(row->Collections.unmodifiableMap(new LinkedHashMap<>(row))).toList();
   columns=List.copyOf(columns);
  }
 }
}
