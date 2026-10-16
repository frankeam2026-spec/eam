package com.windasoft.eam.service.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RepairQueryService {
  private static final List<String> QUEUES =
      List.of("pending", "owned", "mine", "accept", "created", "dispatch", "all");
  private static final List<String> EXPORT_FIELDS =
      List.of(
          "orderNo",
          "equipmentCode",
          "equipmentName",
          "description",
          "department",
          "reporter",
          "assignee",
          "severity",
          "status",
          "reportedAt");
  private static final List<String> EXPORT_LABELS =
      List.of("工单编号", "设备编号", "设备名称", "故障描述", "所属部门", "报修人", "维修人", "紧急程度", "工单状态", "报修时间");
  private final com.windasoft.eam.repository.repair.RepairQueryRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final ObjectMapper json;

  public RepairQueryService(
      com.windasoft.eam.repository.repair.RepairQueryRepository repository,
      AuthContext auth,
      PermissionService permissions,
      ObjectMapper json) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.json = json;
  }

  public void requireExport() {
    permissions.require("repair", "导出");
  }

  public record Result(Page<RepairOrder> page, Map<String, Long> counts) {}

  @Transactional(readOnly = true)
  public Result list(Map<String, String> params) {
    var filters = filters(params);
    long page = number(params.getOrDefault("page", "1"), 1, 10_000_000);
    long size = number(params.getOrDefault("size", "10"), 1, 200);
    var result = repository.page(ordered(params, filters, false), page, size, true);
    Map<String, Long> counts = new LinkedHashMap<>();
    for (String queue : QUEUES)
      counts.put(queue, repository.count(query(params, filters, queue, false)));
    return new Result(result, counts);
  }

  @Transactional(readOnly = true)
  public void export(Map<String, String> params, PrintWriter writer) {
    permissions.require("repair", "导出");
    var filters = filters(params);
    var query = ordered(params, filters, true);
    List<String> columns = exportColumns(params);
    writer.print('\ufeff');
    writer.println(
        String.join(
            ",",
            columns.stream()
                .map(key -> csv(EXPORT_LABELS.get(EXPORT_FIELDS.indexOf(key))))
                .toList()));
    long page = 1;
    while (true) {
      var result = repository.page(query, page++, 500, false);
      for (var row : result.getRecords())
        writer.println(
            String.join(
                ",",
                columns.stream()
                    .map(
                        key -> {
                          Object value = repository.value(row, key);
                          return csv(
                              value instanceof LocalDateTime d
                                  ? date(d)
                                  : value == null ? "" : String.valueOf(value));
                        })
                    .toList()));
      if (writer.checkError()) throw new IllegalStateException("导出连接已断开");
      if (result.getRecords().size() < 500) break;
    }
  }

  public void validateExport(Map<String, String> params) {
    permissions.require("repair", "导出");
    ordered(params, filters(params), true);
    exportColumns(params);
  }

  private List<String> exportColumns(Map<String, String> params) {
    List<String> columns =
        params.containsKey("columns")
            ? Arrays.asList(params.get("columns").split(",", -1))
            : EXPORT_FIELDS;
    if (columns.isEmpty()
        || columns.size() > repository.fields().size()
        || new HashSet<>(columns).size() != columns.size()
        || !repository.fields().containsAll(columns)) throw new IllegalArgumentException("导出字段无效");
    return columns;
  }

  private Map<String, String> filters(Map<String, String> params) {
    String raw = params.getOrDefault("filters", "{}");
    if (raw.length() > 5000) throw new IllegalArgumentException("筛选条件过长");
    try {
      Map<String, String> result = json.readValue(raw, new TypeReference<Map<String, String>>() {});
      if (result == null || !repository.fields().containsAll(result.keySet()))
        throw new IllegalArgumentException("筛选字段无效");
      result.values().forEach(RepairQueryService::text);
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalArgumentException("筛选条件格式无效");
    }
  }

  private static long number(String raw, long min, long max) {
    try {
      long value = Long.parseLong(raw);
      if (value < min || value > max) throw new IllegalArgumentException("分页参数无效");
      return value;
    } catch (NumberFormatException e) {
      throw new IllegalArgumentException("分页参数无效");
    }
  }

  private static String text(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > 200) throw new IllegalArgumentException("查询条件不能超过200个字符");
    return result;
  }

  static String csv(String value) {
    String text = value == null ? "" : value;
    String trimmed = text.stripLeading();
    if (!trimmed.isEmpty() && "=+-@".indexOf(trimmed.charAt(0)) >= 0
        || text.startsWith("\t")
        || text.startsWith("\r")) text = "'" + text;
    return "\"" + text.replace("\"", "\"\"") + "\"";
  }

  private static String date(LocalDateTime value) {
    return value == null ? "" : value.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
  }

  private com.windasoft.eam.repository.repair.RepairQueryRepository.Query ordered(
      Map<String, String> params, Map<String, String> filters, boolean export) {
    return repository.ordered(
        params,
        filters,
        export,
        auth.tenantId(),
        auth.userId(),
        this::scope,
        permissions::filterRepairAcceptance);
  }

  private com.windasoft.eam.repository.repair.RepairQueryRepository.Query query(
      Map<String, String> params, Map<String, String> filters, String queue, boolean export) {
    return repository.query(
        params,
        filters,
        queue,
        export,
        auth.tenantId(),
        auth.userId(),
        this::scope,
        permissions::filterRepairAcceptance);
  }

  private void scope(LambdaQueryWrapper<RepairOrder> query, Boolean export) {
    permissions.filterRepair(query);
    if (export) permissions.filterRepair(query, "导出");
  }
}
