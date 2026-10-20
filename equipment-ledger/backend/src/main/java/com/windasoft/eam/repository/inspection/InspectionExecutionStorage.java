package com.windasoft.eam.repository.inspection;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Relational task facts; the JSON-shaped input/output only adapts existing API contracts. */
@Repository
public class InspectionExecutionStorage {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public InspectionExecutionStorage(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String path, String column, boolean timestamp) {}

  private static final List<Field> FIELDS =
      List.of(
          new Field("review.reviewerId", "reviewer_id", false),
          new Field("description", "description", false),
          new Field("startedAt", "started_at", true),
          new Field("finishedAt", "finished_at", true),
          new Field("skipReason", "skip_reason", false),
          new Field("review.decision", "review_decision", false),
          new Field("review.description", "review_description", false),
          new Field("review.reviewer", "reviewer_name", false),
          new Field("review.reviewedAt", "reviewed_at", true));

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
    data.remove("personMode");
    return encode(data);
  }

  public void participants(long tenant, long id, String content) {
    var data = decode(content);
    db.update(
        "DELETE FROM eam_inspection_task_person WHERE tenant_id=? AND task_id=? AND role_code IN"
            + " ('owner','reviewer')",
        tenant,
        id);
    for (var role : Map.of("owners", "owner", "reviewers", "reviewer").entrySet()) {
      int at = 0;
      for (Object user : new LinkedHashSet<>(values(data.get(role.getKey()))))
        db.update(
            "INSERT INTO eam_inspection_task_person VALUES(?,?,?,?,?)",
            tenant,
            id,
            role.getValue(),
            Long.parseLong(user.toString()),
            at++);
    }
    db.update(
        "UPDATE eam_inspection_task SET person_mode=? WHERE tenant_id=? AND id=?",
        text(data, "personMode"),
        tenant,
        id);
  }

  public void save(long tenant, long id, String content) {
    db.update("DELETE FROM eam_inspection_execution WHERE tenant_id=? AND task_id=?", tenant, id);
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
        "INSERT INTO eam_inspection_execution (tenant_id,task_id,"
            + String.join(",", FIELDS.stream().map(f -> f.column()).toList())
            + ") VALUES("
            + String.join(",", Collections.nCopies(args.size(), "?"))
            + ")",
        args.toArray());
    int at = 0;
    for (var item : rows(data.get("items"))) {
      String key = text(item, "id");
      db.update(
          "INSERT INTO eam_inspection_execution_item VALUES(?,?,?,?,?,?,?,?,?,?,?)",
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
            "INSERT INTO eam_inspection_execution_image VALUES(?,?,?,?,?,?,?,?,?)",
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
    Object review = data.get("review");
    if (review instanceof Map<?, ?> map) files(tenant, id, "review", map.get("attachments"));
    for (var action :
        Map.of("scannedEquipmentIds", "scan", "skippedEquipmentIds", "skip").entrySet()) {
      int position = 0;
      for (Object equipment : new LinkedHashSet<>(values(data.get(action.getKey()))))
        db.update(
            "INSERT INTO eam_inspection_execution_equipment VALUES(?,?,?,?,?)",
            tenant,
            id,
            action.getValue(),
            Long.parseLong(equipment.toString()),
            position++);
    }
    int position = 0;
    for (var item : rows(data.get("reviewItems")))
      db.update(
          "INSERT INTO eam_inspection_execution_review_item VALUES(?,?,?,?,?,?)",
          tenant,
          id,
          text(item, "id"),
          position++,
          text(item, "decision"),
          text(item, "description"));
  }

  private void files(long tenant, long id, String stage, Object files) {
    int at = 0;
    for (var file : rows(files))
      db.update(
          "INSERT INTO eam_inspection_execution_file VALUES(?,?,?,?,?,?,?,?,?)",
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
