package com.windasoft.eam.dto.repair;

import com.fasterxml.jackson.databind.JsonNode;
import com.windasoft.eam.dto.equipment.EquipmentInstalledSpareView;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import java.util.List;
import java.util.Map;

/** 维修处理详情的固定结构。processingFields为现有客户扩展表单协议，字段受业务配置限制。 */
public record RepairWorkflowDetail(
    Map<String, Object> processingFields,
    RepairOrderView order,
    String categoryId,
    String model,
    List<RepairReadViews.Candidate> candidates,
    List<Event> events,
    List<RepairReadViews.Material> materials,
    List<EquipmentInstalledSpareView> installed,
    boolean canClaim,
    boolean canProcess,
    boolean canAccept,
    boolean canDispatch,
    boolean canIssue,
    List<ProcessResult> results,
    List<WorkflowHistoryRound> approvalRounds,
    boolean canResubmit) {
  public record Event(String id, String action, String actor, String time, JsonNode details) {}

  public record ProcessResult(String id, String name, boolean resolved) {}
}
