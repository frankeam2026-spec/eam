package com.windasoft.eam.repository.common;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class AttachmentRelationsRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;

  public AttachmentRelationsRepository(JdbcTemplate db, ObjectMapper json) {
    this.db = db;
    this.json = json;
  }

  private record Field(String key, String column, String type) {}

  public enum Kind {
    EQUIPMENT_IMAGE,
    EQUIPMENT_DOCUMENT,
    REPAIR_REPORT,
    REPAIR_PROCESS,
    ANOMALY,
    REPAIR_PLAN
  }

  private record Target(String table, String parent, List<Field> fields) {}

  private Target target(Kind kind) {
    return switch (kind) {
      case EQUIPMENT_IMAGE ->
          new Target(
              "eam_equipment_image",
              "equipment_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)")));
      case EQUIPMENT_DOCUMENT ->
          new Target(
              "eam_equipment_document",
              "equipment_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)"),
                  new Field("contentType", "content_type", "VARCHAR(150)"),
                  new Field("size", "size_bytes", "BIGINT"),
                  new Field("url", "url_snapshot", "VARCHAR(1000)"),
                  new Field("originalName", "original_name", "VARCHAR(255)"),
                  new Field("code", "document_code", "VARCHAR(64)"),
                  new Field("type", "document_type", "VARCHAR(100)"),
                  new Field("security", "security_level", "VARCHAR(24)"),
                  new Field("quantity", "quantity", "DECIMAL(18,2)"),
                  new Field("remark", "remark", "TEXT")));
      case REPAIR_REPORT ->
          new Target(
              "eam_repair_report_file",
              "order_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)"),
                  new Field("contentType", "content_type", "VARCHAR(150)"),
                  new Field("size", "size_bytes", "BIGINT"),
                  new Field("url", "url_snapshot", "VARCHAR(1000)")));
      case REPAIR_PROCESS ->
          new Target(
              "eam_repair_process_file",
              "order_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)"),
                  new Field("contentType", "content_type", "VARCHAR(150)"),
                  new Field("size", "size_bytes", "BIGINT"),
                  new Field("url", "url_snapshot", "VARCHAR(1000)")));
      case ANOMALY ->
          new Target(
              "eam_anomaly_attachment",
              "anomaly_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)"),
                  new Field("contentType", "content_type", "VARCHAR(150)"),
                  new Field("size", "size_bytes", "BIGINT"),
                  new Field("url", "url_snapshot", "VARCHAR(1000)")));
      case REPAIR_PLAN ->
          new Target(
              "eam_repair_plan_file",
              "plan_id",
              List.of(
                  new Field("id", "file_id", "VARCHAR(64)"),
                  new Field("name", "name_snapshot", "VARCHAR(255)"),
                  new Field("contentType", "content_type", "VARCHAR(150)"),
                  new Field("size", "size_bytes", "BIGINT"),
                  new Field("url", "url_snapshot", "VARCHAR(1000)")));
    };
  }

  public Map<Long, String> load(Kind kind, long tenant, Collection<Long> ids) {
    if (ids.isEmpty()) return Map.of();
    var t = target(kind);
    var args = new ArrayList<Object>();
    args.add(tenant);
    args.addAll(ids);
    var rows =
        db.queryForList(
            "SELECT * FROM "
                + t.table()
                + " WHERE tenant_id=? AND "
                + t.parent()
                + " IN ("
                + String.join(",", Collections.nCopies(ids.size(), "?"))
                + ") ORDER BY sort_no",
            args.toArray());
    var grouped = new HashMap<Long, List<Map<String, Object>>>();
    for (var row : rows) {
      var value = new LinkedHashMap<String, Object>();
      for (var field : t.fields())
        if (row.get(field.column()) != null) value.put(field.key(), row.get(field.column()));
      grouped
          .computeIfAbsent(((Number) row.get(t.parent())).longValue(), k -> new ArrayList<>())
          .add(value);
    }
    var result = new HashMap<Long, String>();
    for (Long id : ids)
      try {
        result.put(id, json.writeValueAsString(grouped.getOrDefault(id, List.of())));
      } catch (Exception e) {
        throw new IllegalStateException("附件关联读取失败", e);
      }
    return result;
  }

  public void save(Kind kind, long tenant, long id, String content) {
    if (content == null) return;
    List<Map<String, Object>> rows;
    try {
      rows = json.readValue(content, new TypeReference<List<Map<String, Object>>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.common.message0007"), e);
    }
    var t = target(kind);
    db.update(
        "DELETE FROM " + t.table() + " WHERE tenant_id=? AND " + t.parent() + "=?", tenant, id);
    int at = 0;
    for (var row : rows) {
      var args = new ArrayList<Object>();
      args.add(tenant);
      args.add(id);
      args.add(at++);
      for (var field : t.fields()) {
        Object value = row.get(field.key());
        if (value != null && (field.type().equals("BIGINT") || field.type().startsWith("DECIMAL")))
          value = value.toString().isBlank() ? null : new BigDecimal(value.toString());
        args.add(value);
      }
      db.update(
          "INSERT INTO "
              + t.table()
              + " (tenant_id,"
              + t.parent()
              + ",sort_no,"
              + String.join(",", t.fields().stream().map(f -> f.column()).toList())
              + ") VALUES("
              + String.join(",", Collections.nCopies(args.size(), "?"))
              + ")",
          args.toArray());
    }
  }
}
