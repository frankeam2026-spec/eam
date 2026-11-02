package com.windasoft.eam.repository.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.common.AttachmentRelationsRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/** 维修工单关联数据的持久化装配：批量读写附件和协同人员关系，不负责审批或状态流转。 */
@Repository
public class RepairRelationsRepository {
  private final RepairCollaboratorRepository collaborators;
  private final ObjectMapper json;
  private final AttachmentRelationsRepository files;

  public RepairRelationsRepository(
      RepairCollaboratorRepository collaborators,
      ObjectMapper json,
      AttachmentRelationsRepository files) {
    this.collaborators = collaborators;
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
      var ids = group.getValue().stream().map(RepairOrder::getId).toList();
      var reports =
          files.load(AttachmentRelationsRepository.Kind.REPAIR_REPORT, group.getKey(), ids);
      var processes =
          files.load(AttachmentRelationsRepository.Kind.REPAIR_PROCESS, group.getKey(), ids);
      group
          .getValue()
          .forEach(
              v -> {
                v.setAttachmentsJson(reports.get(v.getId()));
                v.setProcessAttachmentsJson(processes.get(v.getId()));
              });
      var data = collaborators.list(group.getKey(), ids);
      for (var row : group.getValue())
        try {
          row.setCollaboratorsJson(
              json.writeValueAsString(
                  data.stream()
                      .filter(v -> Objects.equals(v.getOrderId(), row.getId()))
                      .map(v -> Map.of("id", v.getUserId().toString(), "name", v.getNameSnapshot()))
                      .toList()));
        } catch (Exception e) {
          throw new IllegalStateException("协同人员读取失败", e);
        }
    }
    return rows;
  }
}
