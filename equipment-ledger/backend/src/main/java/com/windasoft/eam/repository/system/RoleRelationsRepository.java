package com.windasoft.eam.repository.system;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.system.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** JSON strings below are legacy in-memory DTO adapters, never database storage. */
@Repository
public class RoleRelationsRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public RoleRelationsRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.system.message0005"), e);
    }
  }

  private List<String> strings(String value) {
    if (value == null) return List.of();
    try {
      return json.readValue(value, new TypeReference<List<String>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.system.message0005"), e);
    }
  }

  private List<Map<String, Object>> read(
      String table, String key, long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return List.of();
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.addAll(ids);
    return db.queryForList(
        "SELECT * FROM "
            + table
            + " WHERE tenant_id=? AND "
            + key
            + " IN ("
            + String.join(",", Collections.nCopies(ids.size(), "?"))
            + ") ORDER BY sort_no",
        args.toArray());
  }

  public List<SystemRole> roles(List<SystemRole> rows) {
    for (var group :
        rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(SystemRole::getTenantId))
            .entrySet()) {
      var byId = new HashMap<Long, SystemRole>();
      group
          .getValue()
          .forEach(
              r -> {
                byId.put(r.getId(), r);
                r.setOrgIdsJson("[]");
                r.setPermissionsJson("[]");
              });
      var orgs = new HashMap<Long, List<Long>>();
      for (var v : read("eam_role_organization", "role_id", group.getKey(), byId.keySet()))
        orgs.computeIfAbsent(((Number) v.get("role_id")).longValue(), k -> new ArrayList<>())
            .add(((Number) v.get("org_id")).longValue());
      var actions = new HashMap<Long, List<String>>();
      for (var v : read("eam_role_legacy_action", "role_id", group.getKey(), byId.keySet()))
        actions
            .computeIfAbsent(((Number) v.get("role_id")).longValue(), k -> new ArrayList<>())
            .add(v.get("menu_key") + "." + v.get("action_code"));
      orgs.forEach((id, v) -> byId.get(id).setOrgIdsJson(encode(v)));
      actions.forEach((id, v) -> byId.get(id).setPermissionsJson(encode(v)));
    }
    return rows;
  }

  public SystemRole role(SystemRole row) {
    if (row != null) roles(List.of(row));
    return row;
  }

  public List<RoleMenuPermission> grants(List<RoleMenuPermission> rows) {
    for (var group :
        rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(RoleMenuPermission::getTenantId))
            .entrySet()) {
      var ids = group.getValue().stream().map(RoleMenuPermission::getId).toList();
      var actions = read("eam_role_menu_action", "grant_id", group.getKey(), ids);
      var scopes = read("eam_role_menu_scope", "grant_id", group.getKey(), ids);
      var targets = read("eam_role_menu_target", "grant_id", group.getKey(), ids);
      for (var r : group.getValue()) {
        r.setActionsJson(
            encode(
                actions.stream()
                    .filter(
                        v -> Objects.equals(((Number) v.get("grant_id")).longValue(), r.getId()))
                    .map(v -> v.get("action_name"))
                    .toList()));
        r.setDataScopesJson(
            encode(
                scopes.stream()
                    .filter(
                        v -> Objects.equals(((Number) v.get("grant_id")).longValue(), r.getId()))
                    .map(v -> v.get("scope_code"))
                    .toList()));
        r.setOrgIdsJson(
            encode(
                targets.stream()
                    .filter(
                        v -> Objects.equals(((Number) v.get("grant_id")).longValue(), r.getId()))
                    .map(v -> v.get("target_id"))
                    .toList()));
      }
    }
    return rows;
  }

  public void saveRole(SystemRole row) {
    db.update(
        "DELETE FROM eam_role_organization WHERE tenant_id=? AND role_id=?",
        row.getTenantId(),
        row.getId());
    db.update(
        "DELETE FROM eam_role_legacy_action WHERE tenant_id=? AND role_id=?",
        row.getTenantId(),
        row.getId());
    int order = 0;
    for (var id : strings(row.getOrgIdsJson()))
      db.update(
          "INSERT INTO eam_role_organization VALUES(?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          Long.parseLong(id),
          order++);
    order = 0;
    for (var value : strings(row.getPermissionsJson())) {
      int dot = value.indexOf('.');
      if (dot <= 0)
        throw new com.windasoft.eam.common.LocalizedArgumentException("server.system.message0006");
      db.update(
          "INSERT INTO eam_role_legacy_action VALUES(?,?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          value.substring(0, dot),
          value.substring(dot + 1),
          order++);
    }
  }

  public void saveGrant(RoleMenuPermission row) {
    int at = 0;
    for (var action : strings(row.getActionsJson()))
      db.update(
          "INSERT INTO eam_role_menu_action VALUES(?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          action,
          at++);
    var scopes = strings(row.getDataScopesJson());
    at = 0;
    for (var scope : scopes)
      db.update(
          "INSERT INTO eam_role_menu_scope VALUES(?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          scope,
          at++);
    String kind =
        scopes.contains("warehouse")
            ? "warehouse"
            : scopes.contains("companies") ? "company" : "department";
    at = 0;
    for (var id : strings(row.getOrgIdsJson()))
      db.update(
          "INSERT INTO eam_role_menu_target VALUES(?,?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          kind,
          Long.parseLong(id),
          at++);
  }
}
