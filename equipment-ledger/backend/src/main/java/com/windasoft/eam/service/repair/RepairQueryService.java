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
  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessValueService custom;

  @org.springframework.beans.factory.annotation.Autowired
  private com.windasoft.eam.service.business.BusinessQueryService customQueries;

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

  private void hydrate(List<RepairOrder> rows) {
    var categories = custom.repairCategories(rows.stream().map(RepairOrder::getId).toList());
    var values = custom.batch("repair_report", rows.stream().map(RepairOrder::getId).toList());
    var processing = custom.batch("repair_process", rows.stream().map(RepairOrder::getId).toList());
    for (var row : rows) {
      row.setEquipmentCategoryId(categories.get(row.getId()));
      var v = values.get(row.getId());
      var fieldValues = new LinkedHashMap<>(v.customFields());
      fieldValues.putAll(processing.get(row.getId()).customFields());
      var labels = new LinkedHashMap<>(v.customFieldLabels());
      labels.putAll(processing.get(row.getId()).customFieldLabels());
      row.setCustomFields(fieldValues);
      row.setCustomFieldLabels(labels);
      row.setCustomSchemaVersion(v.customSchemaVersion());
    }
  }

  private String label(String key) {
    if (key.startsWith("custom:"))
      return customQueries
          .combinedFields("repair_report", "repair_process")
          .get(key.substring(7))
          .path("name")
          .asText();
    return com.windasoft.eam.common.MessageContext.text("output.repair.field." + key);
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
    hydrate(result.getRecords());
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
    writer.println(String.join(",", columns.stream().map(key -> csv(label(key))).toList()));
    long page = 1;
    while (true) {
      var result = repository.page(query, page++, 500, false);
      hydrate(result.getRecords());
      for (var row : result.getRecords())
        writer.println(
            String.join(
                ",",
                columns.stream()
                    .map(
                        key -> {
                          Object value =
                              key.startsWith("custom:")
                                  ? row.getCustomFieldLabels().get(key.substring(7))
                                  : repository.value(row, key);
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
    var allowed = new HashSet<>(repository.fields());
    customQueries
        .combinedFields("repair_report", "repair_process")
        .forEach(
            (id, f) -> {
              if (f.path("exportable").asBoolean()) allowed.add("custom:" + id);
            });
    if (columns.isEmpty()
        || columns.size() > allowed.size()
        || new HashSet<>(columns).size() != columns.size()
        || !allowed.containsAll(columns))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.equipment.message0028");
    return columns;
  }

  private Map<String, String> filters(Map<String, String> params) {
    String raw = params.getOrDefault("filters", "{}");
    if (raw.length() > 5000)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.business.message0007");
    try {
      Map<String, String> result = json.readValue(raw, new TypeReference<Map<String, String>>() {});
      if (result == null || !repository.fields().containsAll(result.keySet()))
        throw new com.windasoft.eam.common.LocalizedArgumentException(
            "server.inventory.message0005");
      result.values().forEach(RepairQueryService::text);
      return result;
    } catch (IllegalArgumentException e) {
      throw e;
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0014");
    }
  }

  private static long number(String raw, long min, long max) {
    try {
      long value = Long.parseLong(raw);
      if (value < min || value > max)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0004");
      return value;
    } catch (NumberFormatException e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.common.message0004");
    }
  }

  private static String text(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > 200)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0009");
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
    var prepared = new HashMap<>(params);
    prepared.put("advanced", customQueries.standard(params.get("advanced")));
    var result =
        repository.ordered(
            prepared,
            filters,
            export,
            auth.tenantId(),
            auth.userId(),
            this::scope,
            permissions::filterRepairAcceptance);
    applyRelation(result.rows(), params.getOrDefault("relation", ""));
    customQueries.applyAcross(
        result.rows(), params.get("advanced"), "repair_report", "repair_process");
    customQueries.sortAcross(
        result.rows(),
        params.getOrDefault("sortBy", "reportedAt"),
        params.getOrDefault("direction", "desc"),
        "repair_report",
        "repair_process");
    return result;
  }

  private com.windasoft.eam.repository.repair.RepairQueryRepository.Query query(
      Map<String, String> params, Map<String, String> filters, String queue, boolean export) {
    var prepared = new HashMap<>(params);
    prepared.put("advanced", customQueries.standard(params.get("advanced")));
    var result =
        repository.query(
            prepared,
            filters,
            queue,
            export,
            auth.tenantId(),
            auth.userId(),
            this::scope,
            permissions::filterRepairAcceptance);
    applyRelation(result.rows(), params.getOrDefault("relation", ""));
    customQueries.applyAcross(
        result.rows(), params.get("advanced"), "repair_report", "repair_process");
    return result;
  }

  private void applyRelation(LambdaQueryWrapper<RepairOrder> query, String raw) {
    if (raw.isBlank()) return;
    var values = new HashSet<>(Arrays.asList(raw.split(",", -1)));
    if (!Set.of("owned", "created").containsAll(values))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0040");
    query.and(
        group -> {
          if (values.contains("owned")) group.eq(RepairOrder::getAssigneeUserId, auth.userId());
          if (values.contains("owned") && values.contains("created")) group.or();
          if (values.contains("created")) group.eq(RepairOrder::getReporterUserId, auth.userId());
        });
  }

  private void scope(LambdaQueryWrapper<RepairOrder> query, Boolean export) {
    permissions.filterRepair(query);
    if (export) permissions.filterRepair(query, "导出");
  }
}
