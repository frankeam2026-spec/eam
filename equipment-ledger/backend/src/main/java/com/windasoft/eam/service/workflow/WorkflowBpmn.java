package com.windasoft.eam.service.workflow;

import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.flowable.bpmn.converter.BpmnXMLConverter;
import org.flowable.bpmn.model.*;

/**
 * Only generated, trusted expressions/delegates reach the engine; diagram XML is never deployed.
 */
public final class WorkflowBpmn {
  private WorkflowBpmn() {}

  public static String create(WorkflowDefinition definition) {
    var model = new BpmnModel();
    var process = new org.flowable.bpmn.model.Process();
    process.setId("eam_" + definition.versionId());
    process.setName(definition.name());
    process.setExecutable(true);
    model.addProcess(process);
    Map<String, FlowNode> elements = new LinkedHashMap<>();
    for (var n : definition.nodes()) {
      FlowNode e =
          switch (n.type()) {
            case "START" -> new StartEvent();
            case "END" -> new EndEvent();
            case "APPROVAL" -> new UserTask();
            case "BRANCH" -> new ExclusiveGateway();
            case "PARALLEL" -> new ParallelGateway();
            case "INCLUSIVE" -> new InclusiveGateway();
            case "SUBPROCESS" -> new SubProcess();
            case "SCRIPT" -> {
              var task = new ServiceTask();
              task.setImplementationType("delegateExpression");
              task.setImplementation("${eamWorkflowScript}");
              yield task;
            }
            default -> throw new IllegalArgumentException("不支持的节点");
          };
      e.setId(n.key());
      e.setName(n.name());
      elements.put(n.key(), e);
      {
        var listener = new FlowableListener();
        listener.setEvent("start");
        listener.setImplementationType("delegateExpression");
        listener.setImplementation("${eamWorkflowRouting}");
        e.setExecutionListeners(List.of(listener));
      }
    }
    int index = 0;
    for (var n : definition.nodes()) {
      FlowElementsContainer container =
          WorkflowDefinitionValidator.container(n).isEmpty()
              ? process
              : (SubProcess) elements.get(n.parentKey());
      var e = elements.get(n.key());
      container.addFlowElement(e);
      if (WorkflowDefinitionValidator.gateway(n))
        for (var b : n.branches()) {
          var flow = new SequenceFlow(n.key(), b.target());
          flow.setId("edge_" + (index++));
          flow.setName(b.name());
          if (!n.type().equals("PARALLEL") && n.branches().size() > 1) {
            if (b.fallback()) {
              if (e instanceof ExclusiveGateway gateway) gateway.setDefaultFlow(flow.getId());
              if (e instanceof InclusiveGateway gateway) gateway.setDefaultFlow(flow.getId());
            } else flow.setConditionExpression("${route_" + n.key() + "_" + b.key() + " == true}");
          }
          container.addFlowElement(flow);
        }
      else if (!n.type().equals("END")) {
        var flow = new SequenceFlow(n.key(), n.next());
        flow.setId("edge_" + (index++));
        container.addFlowElement(flow);
      }
    }
    return new String(new BpmnXMLConverter().convertToXML(model), StandardCharsets.UTF_8);
  }
}
