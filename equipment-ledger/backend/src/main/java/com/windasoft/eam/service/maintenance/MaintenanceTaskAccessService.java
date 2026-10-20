package com.windasoft.eam.service.maintenance;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class MaintenanceTaskAccessService {
  private final ObjectMapper json;
  private final AuthContext auth;
  private final PermissionService permissions;

  public MaintenanceTaskAccessService(
      ObjectMapper json, AuthContext auth, PermissionService permissions) {
    this.json = json;
    this.auth = auth;
    this.permissions = permissions;
  }

  public boolean allowed(Map<String, Object> row, String action) {
    var snapshot = decode(row.get("snapshot"));
    if (action.equals("查看")
        && (ids(snapshot.get("owners")).contains(auth.userId())
            || ids(snapshot.get("reviewers")).contains(auth.userId()))) return true;
    try {
      var scope = permissions.maintenance(action);
      if (action.equals("验收")
          && scope.owner()
          && ids(snapshot.get("reviewers")).contains(auth.userId())) return true;
      return scope.matches(
          row.get("company_id") == null ? null : number(row.get("company_id")),
          row.get("department_id") == null ? null : number(row.get("department_id")),
          row.get("owner_id") == null ? null : number(row.get("owner_id")),
          auth.userId());
    } catch (ResponseStatusException e) {
      return false;
    }
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
            .map(MaintenanceTaskAccessService::number)
            .filter(id -> id > 0)
            .distinct()
            .toList()
        : List.of();
  }
}
