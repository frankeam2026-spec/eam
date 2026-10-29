package com.windasoft.eam.repository.repair;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.support.SFunction;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.AdvancedFilters;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.util.*;
import java.util.function.*;
import org.springframework.stereotype.Repository;

@Repository
public class RepairQueryRepository {
  private final RepairRelationsRepository collaborators;
  private final RepairOrderMapper mapper;
  private final ObjectMapper json;

  public RepairQueryRepository(
      RepairOrderMapper mapper, ObjectMapper json, RepairRelationsRepository collaborators) {
    this.collaborators = collaborators;
    this.mapper = mapper;
    this.json = json;
  }

  public static final class Query {
    private final LambdaQueryWrapper<RepairOrder> rows;

    public LambdaQueryWrapper<RepairOrder> rows() {
      return rows;
    }

    private Query(LambdaQueryWrapper<RepairOrder> rows) {
      this.rows = rows;
    }
  }

  public Set<String> fields() {
    return FIELDS.keySet();
  }

  public com.windasoft.eam.repository.common.AssistantReadSource assistantSource(Query query){
    return com.windasoft.eam.repository.common.AssistantReadSource.wrapped("CAST(id AS CHAR) id,order_no code,equipment_code equipmentCode,equipment_name equipmentName,CAST(equipment_id AS CHAR) equipmentId,status,severity,department,CAST(department_org_id AS CHAR) departmentId,(SELECT o.name FROM eam_org_unit o WHERE o.tenant_id=eam_repair_order.tenant_id AND o.id=company_org_id) company,CAST(company_org_id AS CHAR) companyId,assignee owner,CAST(assignee_user_id AS CHAR) ownerId,reporter,CAST(reporter_user_id AS CHAR) reporterId,fault_type faultType,CAST(fault_type_id AS CHAR) faultTypeId,fault_cause faultCause,CAST(fault_cause_id AS CHAR) faultCauseId,reported_at reportedAt,claimed_at claimedAt,completed_at submittedAt,accepted_at acceptedAt,downtime_minutes downtimeMinutes", "eam_repair_order",query.rows());
  }

  public Object value(RepairOrder row, String field) {
    return FIELDS.get(field).apply(row);
  }

  public Query ordered(
      Map<String, String> params,
      Map<String, String> filters,
      boolean export,
      long tenant,
      long user,
      BiConsumer<LambdaQueryWrapper<RepairOrder>, Boolean> scope,
      Consumer<LambdaQueryWrapper<RepairOrder>> acceptance) {
    return new Query(orderedRows(params, filters, export, tenant, user, scope, acceptance));
  }

  public Query query(
      Map<String, String> params,
      Map<String, String> filters,
      String queue,
      boolean export,
      long tenant,
      long user,
      BiConsumer<LambdaQueryWrapper<RepairOrder>, Boolean> scope,
      Consumer<LambdaQueryWrapper<RepairOrder>> acceptance) {
    return new Query(queryRows(params, filters, queue, export, tenant, user, scope, acceptance));
  }

  public Page<RepairOrder> page(Query query, long page, long size, boolean count) {
    var result = mapper.selectPage(new Page<RepairOrder>(page, size, count), query.rows);
    collaborators.hydrate(result.getRecords());
    return result;
  }

  public long count(Query query) {
    return mapper.selectCount(query.rows);
  }

  private static final List<String> QUEUES =
      List.of("pending", "owned", "mine", "accept", "created", "dispatch", "all");
  private static final Map<String, SFunction<RepairOrder, ?>> FIELDS =
      Map.ofEntries(
          Map.entry("orderNo", RepairOrder::getOrderNo),
          Map.entry("equipmentCode", RepairOrder::getEquipmentCode),
          Map.entry("equipmentName", RepairOrder::getEquipmentName),
          Map.entry("description", RepairOrder::getDescription),
          Map.entry("department", RepairOrder::getDepartment),
          Map.entry("reporter", RepairOrder::getReporter),
          Map.entry("assignee", RepairOrder::getAssignee),
          Map.entry("severity", RepairOrder::getSeverity),
          Map.entry("status", RepairOrder::getStatus),
          Map.entry("reportedAt", RepairOrder::getReportedAt));
  private static final Map<String, AdvancedFilters.Field<RepairOrder>> ADVANCED_FIELDS =
      advancedFields();

  private static Map<String, AdvancedFilters.Field<RepairOrder>> advancedFields() {
    Map<String, AdvancedFilters.Field<RepairOrder>> result = new HashMap<>();
    FIELDS.forEach(
        (key, column) ->
            result.put(
                key,
                new AdvancedFilters.Field<>(
                    column,
                    "reportedAt".equals(key)
                        ? AdvancedFilters.Kind.DATE
                        : List.of("status", "severity").contains(key)
                            ? AdvancedFilters.Kind.CHOICE
                            : AdvancedFilters.Kind.TEXT)));
    result.put(
        "faultType",
        new AdvancedFilters.Field<>(RepairOrder::getFaultType, AdvancedFilters.Kind.TEXT));
    result.put(
        "repairAction",
        new AdvancedFilters.Field<>(RepairOrder::getRepairAction, AdvancedFilters.Kind.TEXT));
    result.put(
        "acceptanceComment",
        new AdvancedFilters.Field<>(RepairOrder::getAcceptanceComment, AdvancedFilters.Kind.TEXT));
    result.put(
        "completedAt",
        new AdvancedFilters.Field<>(RepairOrder::getCompletedAt, AdvancedFilters.Kind.DATE));
    result.put(
        "downtimeMinutes",
        new AdvancedFilters.Field<>(RepairOrder::getDowntimeMinutes, AdvancedFilters.Kind.NUMBER));
    return Map.copyOf(result);
  }

  private LambdaQueryWrapper<RepairOrder> orderedRows(
      Map<String, String> params,
      Map<String, String> filters,
      boolean export,
      long tenant,
      long user,
      java.util.function.BiConsumer<LambdaQueryWrapper<RepairOrder>, Boolean> scope,
      java.util.function.Consumer<LambdaQueryWrapper<RepairOrder>> acceptance) {
    var query =
        queryRows(
            params,
            filters,
            params.getOrDefault("queue", "all"),
            export,
            tenant,
            user,
            scope,
            acceptance);
    String sort = params.getOrDefault("sortBy", "reportedAt"),
        direction = params.getOrDefault("direction", "desc");
    if (sort.startsWith("custom:")) return query;
    if (!FIELDS.containsKey(sort) || !List.of("asc", "desc").contains(direction))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0007");
    query.orderBy(true, "asc".equals(direction), FIELDS.get(sort)).orderByAsc(RepairOrder::getId);
    return query;
  }

  private LambdaQueryWrapper<RepairOrder> queryRows(
      Map<String, String> params,
      Map<String, String> filters,
      String queue,
      boolean export,
      long tenant,
      long user,
      java.util.function.BiConsumer<LambdaQueryWrapper<RepairOrder>, Boolean> scope,
      java.util.function.Consumer<LambdaQueryWrapper<RepairOrder>> acceptance) {
    if (!QUEUES.contains(queue))
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0008");
    String keyword = text(params.get("keyword"));
    var q =
        new LambdaQueryWrapper<RepairOrder>()
            .eq(RepairOrder::getTenantId, tenant)
            .eq(RepairOrder::getDeleted, 0);
    if (!keyword.isEmpty())
      q.and(
          w ->
              w.like(RepairOrder::getOrderNo, keyword)
                  .or()
                  .like(RepairOrder::getEquipmentCode, keyword)
                  .or()
                  .like(RepairOrder::getEquipmentName, keyword)
                  .or()
                  .like(RepairOrder::getDescription, keyword)
                  .or()
                  .like(RepairOrder::getDepartment, keyword)
                  .or()
                  .like(RepairOrder::getFaultType, keyword)
                  .or()
                  .like(RepairOrder::getReporter, keyword)
                  .or()
                  .like(RepairOrder::getAssignee, keyword));
    for (String field : List.of("status", "severity", "department")) {
      String value = text(params.get(field));
      if (!value.isEmpty()) q.eq(FIELDS.get(field), value);
    }
    for (var entry : filters.entrySet()) {
      String value = text(entry.getValue());
      if (value.isEmpty()) continue;
      switch (entry.getKey()) {
        case "reportedAt" ->
            q.apply(
                "DATE_FORMAT(reported_at, '%Y-%m-%d %H:%i') LIKE {0}",
                "%" + value.replace('T', ' ') + "%");
        case "status", "severity" -> q.eq(FIELDS.get(entry.getKey()), value);
        default -> q.like(FIELDS.get(entry.getKey()), value);
      }
    }
    AdvancedFilters.apply(q, params.get("advanced"), ADVANCED_FIELDS, json);
    scope.accept(q, export);
    switch (queue) {
      case "owned" -> q.eq(RepairOrder::getAssigneeUserId, user);
      case "mine" ->
          q.eq(RepairOrder::getAssigneeUserId, user)
              .in(RepairOrder::getStatus, List.of("处理中", "挂起"));
      case "pending" ->
          q.eq(RepairOrder::getStatus, "待接单")
              .apply(
                  "id IN (SELECT order_id FROM eam_repair_candidate WHERE tenant_id={0} AND"
                      + " user_id={1} AND status='待接单')",
                  tenant, user);
      case "accept" -> {
        q.eq(RepairOrder::getStatus, "待验收");
        acceptance.accept(q);
      }
      case "created" -> q.eq(RepairOrder::getReporterUserId, user);
      case "dispatch" -> q.eq(RepairOrder::getStatus, "待派工");
      default -> {}
    }
    return q;
  }

  private static String text(String value) {
    String result = value == null ? "" : value.trim();
    if (result.length() > 200)
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.repair.message0009");
    return result;
  }
}
