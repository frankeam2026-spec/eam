package com.windasoft.eam.repository.imports;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.dto.imports.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class ImportBatchRepository {
  private final JdbcTemplate db;

  public ImportBatchRepository(JdbcTemplate db) {
    this.db = db;
  }

  public void create(String id, long tenant, long actor, String name, long size, String sha) {
    db.update(
        "INSERT INTO"
            + " eam_import_batch(id,tenant_id,actor_id,object_key,file_name,file_size,file_sha256,state,expires_at)"
            + " VALUES(?,?,?,'equipment',?,?,?,'VALIDATING',DATE_ADD(NOW(),INTERVAL 7 DAY))",
        id,
        tenant,
        actor,
        name,
        size,
        sha);
  }

  public Map<String, Object> find(String id, long tenant, long actor, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM eam_import_batch WHERE id=? AND tenant_id=? AND actor_id=?"
                + (lock ? " FOR UPDATE" : ""),
            id,
            tenant,
            actor);
    if (rows.isEmpty())
      throw new com.windasoft.eam.common.LocalizedArgumentException("server.imports.message0001");
    return rows.getFirst();
  }

  public Map<String, Object> internal(String id) {
    return db.queryForMap("SELECT * FROM eam_import_batch WHERE id=?", id);
  }

  public void columns(String id, List<ImportColumn> columns) {
    db.update("DELETE FROM eam_import_column WHERE batch_id=?", id);
    var args = new ArrayList<Object[]>();
    for (int i = 0; i < columns.size(); i++) {
      var c = columns.get(i);
      args.add(new Object[] {id, c.key(), i, c.label()});
    }
    db.batchUpdate(
        "INSERT INTO eam_import_column(batch_id,field_key,column_no,label) VALUES(?,?,?,?)", args);
  }

  public List<Map<String, Object>> columns(String id) {
    return db.queryForList(
        "SELECT field_key `key`,label FROM eam_import_column WHERE batch_id=? ORDER BY column_no",
        id);
  }

  public void saveCells(String id, List<ImportWorkbook.InputRow> rows) {
    var args = new ArrayList<Object[]>();
    for (var r : rows)
      for (var e : r.values().entrySet())
        if (!e.getValue().isBlank() || r.formulas().contains(e.getKey())) {
          args.add(
              new Object[] {
                id, r.row(), e.getKey(), e.getValue(), r.formulas().contains(e.getKey())
              });
          if (args.size() == 1000) {
            db.batchUpdate("INSERT INTO eam_import_cell VALUES(?,?,?,?,?)", args);
            args.clear();
          }
        }
    if (!args.isEmpty()) db.batchUpdate("INSERT INTO eam_import_cell VALUES(?,?,?,?,?)", args);
  }

  public void clearValidation(String id) {
    db.update("DELETE FROM eam_import_issue WHERE batch_id=?", id);
    db.update("DELETE FROM eam_equipment_import_row WHERE batch_id=?", id);
  }

  public void saveIssues(String id, List<ImportIssue> issues) {
    for (int i = 0; i < issues.size(); i += 500) {
      var args =
          issues.subList(i, Math.min(i + 500, issues.size())).stream()
              .map(
                  e ->
                      new Object[] {
                        IdWorker.getId(),
                        id,
                        e.row(),
                        e.field(),
                        e.label(),
                        e.value(),
                        e.reason(),
                        e.suggestion()
                      })
              .toList();
      db.batchUpdate("INSERT INTO eam_import_issue VALUES(?,?,?,?,?,?,?,?)", args);
    }
  }

  public void validated(String id, int version, int total, int auto, List<ImportIssue> issues) {
    long errors = issues.stream().map(ImportIssue::row).distinct().count();
    db.update(
        "UPDATE eam_import_batch SET"
            + " schema_version=?,total_rows=?,error_rows=?,error_count=?,auto_number_rows=?,state=?,message=?,row_version=row_version+1,updated_at=NOW()"
            + " WHERE id=?",
        version,
        total,
        errors,
        issues.size(),
        auto,
        issues.isEmpty() ? "READY" : "INVALID",
        issues.isEmpty() ? "全部校验通过，尚未导入设备" : "请修改后重新上传，本次尚未导入任何设备",
        id);
  }

  public boolean claim(String id, long tenant, long actor, int version, String key) {
    return db.update(
            "UPDATE eam_import_batch SET"
                + " state='IMPORTING',confirm_key=?,confirm_version=?,message='正在重新校验并导入',row_version=row_version+1,updated_at=NOW()"
                + " WHERE id=? AND tenant_id=? AND actor_id=? AND state='READY' AND row_version=?"
                + " AND expires_at>NOW() AND purged_at IS NULL",
            key,
            version,
            id,
            tenant,
            actor,
            version)
        == 1;
  }

  public void failed(String id, String message) {
    db.update(
        "UPDATE eam_import_batch SET"
            + " state='FAILED',message=?,success_rows=0,row_version=row_version+1,updated_at=NOW()"
            + " WHERE id=? AND state<>'SUCCEEDED'",
        message,
        id);
  }

  public void succeed(String id, int count) {
    db.update(
        "UPDATE eam_import_batch SET"
            + " state='SUCCEEDED',success_rows=?,message='整批导入成功',row_version=row_version+1,updated_at=NOW()"
            + " WHERE id=?",
        count,
        id);
  }

  public List<Map<String, Object>> issues(String id, List<Integer> rows) {
    if (rows.isEmpty()) return List.of();
    var args = new ArrayList<Object>();
    args.add(id);
    args.addAll(rows);
    return db.queryForList(
        "SELECT row_no `row`,field_key field,field_label label,raw_value value,reason,suggestion"
            + " FROM eam_import_issue WHERE batch_id=? AND row_no IN ("
            + String.join(",", Collections.nCopies(rows.size(), "?"))
            + ") ORDER BY row_no,id",
        args.toArray());
  }

  public List<Map<String, Object>> allIssues(String id) {
    return db.queryForList(
        "SELECT row_no `row`,field_key field,field_label label,raw_value value,reason,suggestion"
            + " FROM eam_import_issue WHERE batch_id=? ORDER BY row_no,id",
        id);
  }

  public List<Integer> rowNumbers(String id, boolean errors, int page, int size) {
    return db.queryForList(
        "SELECT r.row_no FROM eam_equipment_import_row r WHERE r.batch_id=?"
            + (errors
                ? " AND EXISTS(SELECT 1 FROM eam_import_issue e WHERE e.batch_id=r.batch_id AND"
                    + " e.row_no=r.row_no)"
                : "")
            + " ORDER BY r.row_no LIMIT ? OFFSET ?",
        Integer.class,
        id,
        size,
        (page - 1) * size);
  }

  public List<Map<String, Object>> cells(String id, List<Integer> rows) {
    if (rows.isEmpty()) return List.of();
    var args = new ArrayList<Object>();
    args.add(id);
    args.addAll(rows);
    return db.queryForList(
        "SELECT row_no,field_key,raw_value FROM eam_import_cell WHERE batch_id=? AND row_no IN ("
            + String.join(",", Collections.nCopies(rows.size(), "?"))
            + ")",
        args.toArray());
  }

  public List<Map<String, Object>> cells(String id) {
    return db.queryForList(
        "SELECT row_no,field_key,raw_value,formula_flag FROM eam_import_cell WHERE batch_id=? ORDER"
            + " BY row_no",
        id);
  }

  public List<String> recover() {
    var ids =
        db.queryForList(
            "SELECT id FROM eam_import_batch WHERE state IN ('VALIDATING','IMPORTING')",
            String.class);
    db.update(
        "UPDATE eam_import_batch SET"
            + " state='FAILED',message='服务重启中断处理，本次新增0条。请重新上传校验。',success_rows=0,row_version=row_version+1"
            + " WHERE state IN ('VALIDATING','IMPORTING')");
    return ids;
  }

  public List<String> expired() {
    return db.queryForList(
        "SELECT id FROM eam_import_batch WHERE purged_at IS NULL AND expires_at<NOW() AND state NOT"
            + " IN ('VALIDATING','IMPORTING','EXPIRED')",
        String.class);
  }

  public boolean expire(String id) {
    var rows =
        db.queryForList(
            "SELECT state FROM eam_import_batch WHERE id=? AND expires_at<NOW() AND purged_at IS"
                + " NULL FOR UPDATE",
            id);
    if (rows.isEmpty() || Set.of("VALIDATING", "IMPORTING").contains(rows.getFirst().get("state")))
      return false;
    db.update("DELETE FROM eam_import_cell WHERE batch_id=?", id);
    db.update("DELETE FROM eam_import_issue WHERE batch_id=?", id);
    db.update("DELETE FROM eam_equipment_import_row WHERE batch_id=?", id);
    db.update(
        "UPDATE eam_import_batch SET"
            + " state=IF(state='SUCCEEDED','SUCCEEDED','EXPIRED'),message=IF(state='SUCCEEDED',message,'暂存文件已过期，请重新上传'),purged_at=NOW(),updated_at=NOW()"
            + " WHERE id=?",
        id);
    return true;
  }
}
