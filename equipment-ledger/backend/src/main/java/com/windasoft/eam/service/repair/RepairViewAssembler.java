package com.windasoft.eam.service.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.dto.repair.RepairOrderView;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Component;

@Component
/** 维修工单对外视图装配器：只转换显示字段，不决定权限或改变业务状态。 */
public class RepairViewAssembler {
  private final ObjectMapper objectMapper;

  public RepairViewAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  public RepairOrderView view(RepairOrder r) {
    return new RepairOrderView(
        String.valueOf(r.getId()),
        safe(r.getEquipmentCategoryId()),
        r.getCustomFields(),
        r.getCustomFieldLabels(),
        r.getCustomSchemaVersion(),
        safe(r.getOrderNo()),
        safe(r.getEquipmentCode()),
        safe(r.getEquipmentName()),
        safe(r.getDepartment()),
        safe(r.getFaultType()),
        r.getFaultTypeId() == null ? "" : r.getFaultTypeId().toString(),
        r.getFaultCauseId() == null ? "" : r.getFaultCauseId().toString(),
        r.getProcessingResultId() == null ? "" : r.getProcessingResultId().toString(),
        safe(r.getSeverity()),
        safe(r.getDescription()),
        safe(r.getReporter()),
        safe(r.getAssignee()),
        safe(r.getStatus()),
        safe(r.getApprovalGate()),
        r.getReporterUserId() == null ? "" : r.getReporterUserId().toString(),
        safe(r.getRepairAction()),
        r.getDowntimeMinutes() == null ? 0 : r.getDowntimeMinutes(),
        date(r.getReportedAt()),
        json(r.getAttachmentsJson()),
        safe(r.getFaultCause()),
        safe(r.getRepairMethod()),
        safe(r.getProcessingResult()),
        safe(r.getPreventiveMeasures()),
        json(r.getCollaboratorsJson()),
        json(r.getProcessAttachmentsJson()),
        safe(r.getOutsourceVendor()),
        r.getRowVersion() == null ? 0 : r.getRowVersion());
  }

  public JsonNode json(String value) {
    try {
      return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value);
    } catch (Exception e) {
      throw new IllegalStateException("维修历史JSON损坏，不能作为空数据返回", e);
    }
  }

  private static String date(LocalDateTime d) {
    return d == null ? "" : d.format(MINUTE);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
