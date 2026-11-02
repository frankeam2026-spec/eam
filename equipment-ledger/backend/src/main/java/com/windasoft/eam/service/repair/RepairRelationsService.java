package com.windasoft.eam.service.repair;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.MessageRef;
import com.windasoft.eam.entity.repair.RepairCollaborator;
import com.windasoft.eam.entity.repair.RepairOrder;
import com.windasoft.eam.repository.common.AttachmentRelationsRepository;
import com.windasoft.eam.repository.repair.RepairCollaboratorRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

/** 将旧工单JSON契约转换为类型化协同关系及附件关联；由报修、审批、处理服务的现有事务统一提交。 */
@Service
public class RepairRelationsService {
  private final ObjectMapper json;
  private final RepairCollaboratorRepository collaborators;
  private final AttachmentRelationsRepository files;

  public RepairRelationsService(
      ObjectMapper json,
      RepairCollaboratorRepository collaborators,
      AttachmentRelationsRepository files) {
    this.json = json;
    this.collaborators = collaborators;
    this.files = files;
  }

  /** 报修资料只替换报修附件，不触及维修过程附件或协同人员。 */
  public void saveReport(RepairOrder row) {
    files.save(
        AttachmentRelationsRepository.Kind.REPAIR_REPORT,
        row.getTenantId(),
        row.getId(),
        row.getAttachmentsJson());
  }

  /** 处理资料只替换过程关系；null协同内容不替换，空数组清空。调用方提供完整业务事务。 */
  public void saveProcessing(RepairOrder row) {
    files.save(
        AttachmentRelationsRepository.Kind.REPAIR_PROCESS,
        row.getTenantId(),
        row.getId(),
        row.getProcessAttachmentsJson());
    if (row.getCollaboratorsJson() == null) {
      return;
    }
    List<Person> people;
    try {
      people = json.readValue(row.getCollaboratorsJson(), new TypeReference<List<Person>>() {});
    } catch (Exception e) {
      throw new LocalizedArgumentException(MessageRef.of("server.repair.message0006"), e);
    }
    var relations = new ArrayList<RepairCollaborator>();
    int sort = 0;
    for (var person : people) {
      var relation = new RepairCollaborator();
      relation.setTenantId(row.getTenantId());
      relation.setOrderId(row.getId());
      relation.setUserId(Long.parseLong(person.getId()));
      relation.setNameSnapshot(person.getName());
      relation.setSortNo(sort++);
      relations.add(relation);
    }
    collaborators.replace(row.getTenantId(), row.getId(), relations);
  }

  @JsonIgnoreProperties(ignoreUnknown = true)
  @lombok.Data
  private static class Person {
    private String id;
    private String name = "";
  }
}
