package com.windasoft.eam.repository.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * Immutable task membership, recorded once at generation; descriptions stay in the frozen template.
 */
@Repository
public class TaskSourceRelationsRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public TaskSourceRelationsRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object v) {
    return v instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private Long id(Object v) {
    return v == null || v.toString().isBlank() ? null : Long.valueOf(v.toString());
  }

  public void maintenance(long tenant, long task, Object equipment, String snapshot) {
    write(false, tenant, task, id(equipment), snapshot);
  }

  public void inspection(long tenant, long task, String snapshot) {
    write(true, tenant, task, null, snapshot);
  }

  private void write(boolean inspection, long tenant, long task, Long equipment, String snapshot) {
    Map<String, Object> data;
    try {
      data = json.readValue(snapshot, new TypeReference<Map<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.common.message0008"), e);
    }
    if (inspection) {
      int at = 0;
      var seen = new HashSet<Long>();
      for (var row : rows(data.get("equipment"))) {
        Long eq = id(row.get("id"));
        if (seen.add(eq))
          db.update(
              "INSERT INTO eam_inspection_task_equipment VALUES(?,?,?,?)", tenant, task, eq, at++);
      }
    }
    String table = inspection ? "eam_inspection_task_project" : "eam_maintenance_task_project";
    int at = 0;
    for (var item : rows(data.get("items"))) {
      String key = Objects.toString(item.get("id"), "");
      String bare = key.substring(key.lastIndexOf('/') + 1);
      Long standard = id(item.get("sourceStandardId"));
      String original = Objects.toString(item.get("sourceItemId"), null);
      if (bare.matches("[0-9]+:[^:]+")) {
        int colon = bare.indexOf(':');
        if (standard == null) standard = Long.valueOf(bare.substring(0, colon));
        if (original == null) original = bare.substring(colon + 1);
      } else if (standard != null && original == null) original = bare;
      db.update(
          "INSERT INTO " + table + " VALUES(?,?,?,?,?,?,?,?)",
          tenant,
          task,
          key,
          inspection ? id(item.get("equipmentId")) : equipment,
          standard,
          original,
          id(item.get("typeId")),
          at++);
    }
  }
}
