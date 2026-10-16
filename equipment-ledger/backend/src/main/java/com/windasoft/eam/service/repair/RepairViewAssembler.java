package com.windasoft.eam.service.repair;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.windasoft.eam.entity.repair.RepairOrder;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RepairViewAssembler {
  private final ObjectMapper objectMapper;

  public RepairViewAssembler(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  private static final DateTimeFormatter MINUTE = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");

  public Map<String, Object> view(RepairOrder r) {
    return Map.ofEntries(
        Map.entry("id", String.valueOf(r.getId())),
        Map.entry("orderNo", safe(r.getOrderNo())),
        Map.entry("equipmentCode", safe(r.getEquipmentCode())),
        Map.entry("equipmentName", safe(r.getEquipmentName())),
        Map.entry("department", safe(r.getDepartment())),
        Map.entry("faultType", safe(r.getFaultType())),
        Map.entry("severity", safe(r.getSeverity())),
        Map.entry("description", safe(r.getDescription())),
        Map.entry("reporter", safe(r.getReporter())),
        Map.entry("assignee", safe(r.getAssignee())),
        Map.entry("status", safe(r.getStatus())),
        Map.entry("repairAction", safe(r.getRepairAction())),
        Map.entry("downtimeMinutes", r.getDowntimeMinutes() == null ? 0 : r.getDowntimeMinutes()),
        Map.entry("reportedAt", date(r.getReportedAt())),
        Map.entry("attachments", json(r.getAttachmentsJson())),
        Map.entry("faultCause", safe(r.getFaultCause())),
        Map.entry("repairMethod", safe(r.getRepairMethod())),
        Map.entry("processingResult", safe(r.getProcessingResult())),
        Map.entry("preventiveMeasures", safe(r.getPreventiveMeasures())),
        Map.entry("collaborators", json(r.getCollaboratorsJson())),
        Map.entry("processAttachments", json(r.getProcessAttachmentsJson())),
        Map.entry("outsourceVendor", safe(r.getOutsourceVendor())),
        Map.entry("rowVersion", r.getRowVersion() == null ? 0 : r.getRowVersion()));
  }

  public Object json(String value) {
    try {
      return value == null ? objectMapper.createArrayNode() : objectMapper.readTree(value);
    } catch (Exception e) {
      return objectMapper.createArrayNode();
    }
  }

  private static String date(LocalDateTime d) {
    return d == null ? "" : d.format(MINUTE);
  }

  private static String safe(String s) {
    return s == null ? "" : s;
  }
}
