package com.windasoft.eam.service.workflow;

import com.windasoft.eam.dto.workflow.WorkflowDefinition.Branch;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Node;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound.Approval;
import com.windasoft.eam.entity.workflow.WorkflowBranch;
import com.windasoft.eam.entity.workflow.WorkflowNode;
import com.windasoft.eam.repository.workflow.WorkflowBranchRepository;
import com.windasoft.eam.repository.workflow.WorkflowHistoryRepository;
import com.windasoft.eam.repository.workflow.WorkflowNodeRepository;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** 组合业务历史的轮次、办理记录和只读拓扑，不重新加载可执行脚本及条件数据。 */
@Service
@RequiredArgsConstructor
public class WorkflowHistoryService {
  private final WorkflowHistoryRepository history;
  private final WorkflowNodeRepository nodes;
  private final WorkflowBranchRepository branches;

  /** 调用方先完成业务单据查看授权；所有查询仍按可信租户与该单据限定。 */
  public List<WorkflowHistoryRound> history(long tenant, String business, long id) {
    var rounds = history.rounds(tenant, business, id);
    if (rounds.isEmpty()) {
      return List.of();
    }
    var instances = rounds.stream().map(r -> r.id()).toList();
    var approvals =
        history.approvals(tenant, instances).stream()
            .collect(Collectors.groupingBy(Approval::instanceId));
    var events =
        history.events(tenant, instances).stream()
            .collect(
                Collectors.groupingBy(
                    WorkflowHistoryRepository.InstanceEvent::instanceId,
                    Collectors.mapping(
                        WorkflowHistoryRepository.InstanceEvent::event, Collectors.toList())));
    var nodeRows =
        nodes.versions(tenant, rounds.stream().map(r -> r.versionId()).distinct().toList());
    var branchRows =
        branches.list(tenant, nodeRows.stream().map(WorkflowNode::getId).toList()).stream()
            .collect(Collectors.groupingBy(WorkflowBranch::getNodeId));
    // 保留原进度拓扑协议；不读取也不暴露固定审批人、角色、表达式或脚本源码。
    var topology =
        nodeRows.stream()
            .collect(
                Collectors.groupingBy(
                    WorkflowNode::getVersionId,
                    Collectors.mapping(
                        n ->
                            new Node(
                                n.getNodeKey(),
                                n.getNodeType(),
                                n.getName(),
                                n.getNextKey(),
                                n.getApprovalMode(),
                                List.of(),
                                List.of(),
                                branchRows.getOrDefault(n.getId(), List.of()).stream()
                                    .map(
                                        b ->
                                            new Branch(
                                                b.getBranchKey(),
                                                b.getName(),
                                                b.getTargetKey(),
                                                Boolean.TRUE.equals(b.getFallback()),
                                                ""))
                                    .toList(),
                                n.getParentKey(),
                                null),
                        Collectors.toList())));
    return rounds.stream()
        .map(
            r ->
                new WorkflowHistoryRound(
                    r.id(),
                    r.roundNo(),
                    r.state(),
                    r.versionId(),
                    r.rowVersion(),
                    r.startedAt(),
                    r.endedAt(),
                    r.templateName(),
                    approvals.getOrDefault(r.id(), List.of()),
                    events.getOrDefault(r.id(), List.of()),
                    topology.getOrDefault(r.versionId(), List.of())))
        .toList();
  }
}
