package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.system.AuthContext;
import java.util.*;
import org.springframework.stereotype.Service;

@Service
public class MaintenanceViewAssembler {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final MaintenanceTaskAccessService access;

  public MaintenanceViewAssembler(
      ObjectMapper json, AuthContext auth, MaintenanceTaskAccessService access) {
    this.json = json;
    this.auth = auth;
    this.access = access;
  }

  public Map<String, Object> definition(Map<String, Object> row) {
    var v = decode(row.get("payload"));
    v.put("id", Objects.toString(row.get("id")));
    v.put("code", row.get("code"));
    v.put("name", row.get("name"));
    v.put("departmentId", Objects.toString(row.get("department_id"), ""));
    v.put("creatorId", Objects.toString(row.get("creator_id")));
    v.put("rowVersion", row.get("row_version"));
    v.put("createdAt", Objects.toString(row.get("created_at")).replace('T', ' '));
    for (String key : List.of("department", "owner", "equipmentCount", "itemCount", "enabledLabel"))
      if (row.containsKey(key)) v.put(key, row.get(key));
    return v;
  }

  public Map<String, Object> task(Map<String, Object> r) {
    var v = decode(r.get("snapshot"));
    v.put("typeId", Objects.toString(r.get("type_id"), ""));
    v.put("companyId", Objects.toString(r.get("company_id"), ""));
    v.put("departmentId", Objects.toString(r.get("department_id"), ""));
    v.put("id", Objects.toString(r.get("id")));
    v.put("code", r.get("code"));
    v.put("status", r.get("status"));
    v.put("planId", Objects.toString(r.get("plan_id"), ""));
    v.put("equipmentId", Objects.toString(r.get("equipment_id"), ""));
    v.put("dueDate", Objects.toString(r.get("due_date")));
    v.put("expiresDate", Objects.toString(r.get("expires_date"), ""));
    v.put("rowVersion", r.get("row_version"));
    v.put("ownerId", Objects.toString(r.get("owner_id"), ""));
    v.put("createdAt", Objects.toString(r.get("created_at")).replace('T', ' '));
    var result = r.get("result") == null ? Map.<String, Object>of() : decode(r.get("result"));
    v.put("result", result);
    String actualStart = Objects.toString(result.get("startAt"), "");
    v.put(
        "actualStartAt",
        actualStart.isBlank() ? Objects.toString(result.get("startedAt"), "") : actualStart);
    v.put("actualEndAt", Objects.toString(result.get("endAt"), ""));
    v.put(
        "canExecute",
        Set.of("待执行", "执行中", "挂起").contains(r.get("status"))
            && ids(v.get("owners")).contains(auth.userId())
            && (r.get("owner_id") == null || number(r.get("owner_id")) == auth.userId()));
    v.put(
        "canAccept",
        "待验收".equals(r.get("status"))
            && ids(v.get("reviewers")).contains(auth.userId())
            && access.allowed(r, "验收"));
    v.put(
        "canDelete",
        access.allowed(r, "删除") && Set.of("待执行", "已作废", "过期作废").contains(r.get("status")));
    v.put(
        "canPostpone",
        access.allowed(r, "编辑")
            && r.get("expires_date") != null
            && Set.of("待执行", "执行中", "挂起").contains(r.get("status")));
    for (String key : List.of("department", "owner"))
      if (r.containsKey(key)) v.put(key, r.get(key));
    return v;
  }

  Map<String, Object> decode(Object value) {
    try {
      return json.readValue(
          Objects.toString(value, "{}"),
          new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          "server.maintenance.message0009");
    }
  }

  static long number(Object value) {
    return value == null || value.toString().isBlank() ? 0 : Long.parseLong(value.toString());
  }

  static List<Long> ids(Object value) {
    return value instanceof List<?> rows
        ? rows.stream()
            .map(MaintenanceViewAssembler::number)
            .filter(id -> id > 0)
            .distinct()
            .toList()
        : List.of();
  }
}
