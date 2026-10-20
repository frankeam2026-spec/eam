package com.windasoft.eam.repository.repair;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RepairRelationsRepository {
  private final JdbcTemplate db;
  private final ObjectMapper json;
  private final com.windasoft.eam.repository.common.AttachmentRelationsRepository files;

  public RepairRelationsRepository(
      JdbcTemplate db,
      ObjectMapper json,
      com.windasoft.eam.repository.common.AttachmentRelationsRepository files) {
    this.db = db;
    this.json = json;
    this.files = files;
  }

  public RepairOrder hydrate(RepairOrder row) {
    if (row != null) hydrate(List.of(row));
    return row;
  }

  public <T extends List<RepairOrder>> T hydrate(T rows) {
    for (var group :
        rows.stream()
            .collect(java.util.stream.Collectors.groupingBy(RepairOrder::getTenantId))
            .entrySet()) {
      var args = new ArrayList<Object>();
      args.add(group.getKey());
      group.getValue().forEach(r -> args.add(r.getId()));
      var ids = group.getValue().stream().map(RepairOrder::getId).toList();
      var reports =
          files.load(
              com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.REPAIR_REPORT,
              group.getKey(),
              ids);
      var processes =
          files.load(
              com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.REPAIR_PROCESS,
              group.getKey(),
              ids);
      group
          .getValue()
          .forEach(
              v -> {
                v.setAttachmentsJson(reports.get(v.getId()));
                v.setProcessAttachmentsJson(processes.get(v.getId()));
              });
      var data =
          db.queryForList(
              "SELECT order_id,user_id,name_snapshot FROM eam_repair_collaborator WHERE tenant_id=?"
                  + " AND order_id IN ("
                  + String.join(",", Collections.nCopies(group.getValue().size(), "?"))
                  + ") ORDER BY sort_no",
              args.toArray());
      for (var row : group.getValue())
        try {
          row.setCollaboratorsJson(
              json.writeValueAsString(
                  data.stream()
                      .filter(
                          v ->
                              Objects.equals(((Number) v.get("order_id")).longValue(), row.getId()))
                      .map(
                          v ->
                              Map.of(
                                  "id",
                                  v.get("user_id").toString(),
                                  "name",
                                  v.get("name_snapshot")))
                      .toList()));
        } catch (Exception e) {
          throw new IllegalStateException("协同人员读取失败", e);
        }
    }
    return rows;
  }

  public void save(RepairOrder row) {
    files.save(
        com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.REPAIR_REPORT,
        row.getTenantId(),
        row.getId(),
        row.getAttachmentsJson());
    files.save(
        com.windasoft.eam.repository.common.AttachmentRelationsRepository.Kind.REPAIR_PROCESS,
        row.getTenantId(),
        row.getId(),
        row.getProcessAttachmentsJson());
    if (row.getCollaboratorsJson() == null) return;
    List<Map<String, String>> people;
    try {
      people =
          json.readValue(
              row.getCollaboratorsJson(), new TypeReference<List<Map<String, String>>>() {});
    } catch (Exception e) {
      throw new com.windasoft.eam.common.LocalizedArgumentException(
          com.windasoft.eam.common.MessageRef.of("server.repair.message0006"), e);
    }
    db.update(
        "DELETE FROM eam_repair_collaborator WHERE tenant_id=? AND order_id=?",
        row.getTenantId(),
        row.getId());
    int sort = 0;
    for (var person : people)
      db.update(
          "INSERT INTO eam_repair_collaborator VALUES(?,?,?,?,?)",
          row.getTenantId(),
          row.getId(),
          Long.parseLong(person.get("id")),
          person.getOrDefault("name", ""),
          sort++);
  }
}
