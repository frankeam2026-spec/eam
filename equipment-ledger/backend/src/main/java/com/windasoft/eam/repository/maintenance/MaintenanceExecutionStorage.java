package com.windasoft.eam.repository.maintenance;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Relational task facts; the JSON-shaped input/output only adapts existing API contracts. */
@Repository
public class MaintenanceExecutionStorage {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public MaintenanceExecutionStorage(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String path, String column, boolean timestamp) {}

  private static final List<Field> FIELDS =
      List.of(
          new Field("acceptance.reviewerId", "reviewer_id", false),
          new Field("acceptance.reviewer", "reviewer_name", false),
          new Field("acceptance.reviewedAt", "reviewed_at", true),
          new Field("description", "description", false),
          new Field("startedAt", "started_at", true),
          new Field("startAt", "work_start_at", true),
          new Field("endAt", "work_end_at", true),
          new Field("acceptance.decision", "review_decision", false),
          new Field("acceptance.description", "review_description", false),
          new Field("acceptance.score", "acceptance_score", false));

  private Map<String, Object> decode(String content) {
    try {
      return content == null
          ? new LinkedHashMap<>()
          : json.readValue(content, new TypeReference<LinkedHashMap<String, Object>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inspection.message0004"), e);
    }
  }

  private String encode(Object data) {
    try {
      return json.writeValueAsString(data);
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.inspection.message0005"), e);
    }
  }

  @SuppressWarnings("unchecked")
  private List<Map<String, Object>> rows(Object value) {
    return value instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
  }

  private List<?> values(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }

  private String text(Map<String, Object> data, String key) {
    return Objects.toString(data.get(key), "");
  }

  private int yes(Object value) {
    return Boolean.TRUE.equals(value) || "true".equals(Objects.toString(value)) ? 1 : 0;
  }

  private Object path(Map<String, Object> data, String key) {
    Object current = data;
    for (String part : key.split("\\.")) {
      if (!(current instanceof Map<?, ?> map)) return null;
      current = map.get(part);
    }
    return current;
  }

  public String historicalSnapshot(String content) {
    var data = decode(content);
    data.remove("owners");
    data.remove("reviewers");
    return encode(data);
  }

  public void participants(long tenant, long id, String content) {
    var data = decode(content);
    db.update(
        "DELETE FROM eam_maintenance_task_person WHERE tenant_id=? AND task_id=? AND role_code IN"
            + " ('owner','reviewer')",
        tenant,
        id);
    for (var role : Map.of("owners", "owner", "reviewers", "reviewer").entrySet()) {
      int at = 0;
      for (Object user : new LinkedHashSet<>(values(data.get(role.getKey()))))
        db.update(
            "INSERT INTO eam_maintenance_task_person VALUES(?,?,?,?,?)",
            tenant,
            id,
            role.getValue(),
            Long.parseLong(user.toString()),
            at++);
    }
  }

  public void save(long tenant, long id, String content) {
    db.update("DELETE FROM eam_maintenance_execution WHERE tenant_id=? AND task_id=?", tenant, id);
    db.update(
        "DELETE FROM eam_maintenance_task_person WHERE tenant_id=? AND task_id=? AND"
            + " role_code='collaborator'",
        tenant,
        id);
    if (content == null) return;
    var data = decode(content);
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.add(id);
    for (var field : FIELDS) {
      Object value = path(data, field.path());
      if (value != null && value.toString().isBlank()) value = null;
      if (field.timestamp() && value != null)
        value = java.time.LocalDateTime.parse(value.toString());
      args.add(value);
    }
    db.update(
        "INSERT INTO eam_maintenance_execution (tenant_id,task_id,"
            + String.join(",", FIELDS.stream().map(f -> f.column()).toList())
            + ") VALUES("
            + String.join(",", Collections.nCopies(args.size(), "?"))
            + ")",
        args.toArray());
    int at = 0;
    for (var item : rows(data.get("items"))) {
      String key = text(item, "id");
      db.update(
          "INSERT INTO eam_maintenance_execution_item VALUES(?,?,?,?,?,?,?,?,?,?,?)",
          tenant,
          id,
          key,
          at++,
          text(item, "value"),
          yes(item.get("abnormal")),
          yes(item.get("resolved")),
          text(item, "measure"),
          text(item, "remark"),
          yes(item.get("implicitNormal")),
          yes(item.get("skipped")));
      int position = 0;
      for (var file : rows(item.get("images")))
        db.update(
            "INSERT INTO eam_maintenance_execution_image VALUES(?,?,?,?,?,?,?,?,?)",
            tenant,
            id,
            key,
            position++,
            text(file, "id"),
            text(file, "name"),
            text(file, "contentType"),
            file.getOrDefault("size", 0),
            text(file, "url"));
    }
    files(tenant, id, "execution", data.get("attachments"));
    Object review = data.get("acceptance");
    if (review instanceof Map<?, ?> map) files(tenant, id, "acceptance", map.get("attachments"));
    int userOrder = 0;
    for (Object user : new LinkedHashSet<>(values(data.get("collaborators"))))
      db.update(
          "INSERT INTO eam_maintenance_task_person VALUES(?,?,?,?,?)",
          tenant,
          id,
          "collaborator",
          Long.parseLong(user.toString()),
          userOrder++);
  }

  private void files(long tenant, long id, String stage, Object files) {
    int at = 0;
    for (var file : rows(files))
      db.update(
          "INSERT INTO eam_maintenance_execution_file VALUES(?,?,?,?,?,?,?,?,?)",
          tenant,
          id,
          stage,
          at++,
          text(file, "id"),
          text(file, "name"),
          text(file, "contentType"),
          file.getOrDefault("size", 0),
          text(file, "url"));
  }
}
