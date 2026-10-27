package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.repository.workflow.*;
import java.util.*;
import org.flowable.engine.delegate.*;
import org.springframework.stereotype.Component;

/** Called only by generated BPMN at the actual gateway, after preceding script results exist. */
@Component("eamWorkflowRouting")
public class WorkflowRoutingListener implements ExecutionListener {
  private final WorkflowRepository templates;
  private final WorkflowInstanceRepository instances;

  public WorkflowRoutingListener(
      WorkflowRepository templates, WorkflowInstanceRepository instances) {
    this.templates = templates;
    this.instances = instances;
  }

  @Override
  public void notify(DelegateExecution execution) {
    var trial = WorkflowSimulation.current();
    if (trial != null) {
      var node =
          trial.definition.nodes().stream()
              .filter(n -> n.key().equals(execution.getCurrentActivityId()))
              .findFirst()
              .orElseThrow();
      trial.trace.add(Map.of("node", node.key(), "name", node.name(), "type", node.type()));
      if (!Set.of("BRANCH", "INCLUSIVE").contains(node.type())) return;
      var expressions =
          new WorkflowDefinitionValidator(new WorkflowVariables()).validate(trial.definition);
      var matched = new ArrayList<String>();
      for (var branch : node.branches()) {
        boolean result =
            node.branches().size() == 1
                || branch.fallback() && matched.isEmpty()
                || !branch.fallback()
                    && WorkflowExpression.test(
                            expressions.get(node.key() + ":" + branch.key()), trial.values)
                        .matched();
        trial.trace.add(Map.of("branch", branch.key(), "name", branch.name(), "matched", result));
        if (result) {
          matched.add(branch.key());
          if (node.type().equals("BRANCH")) break;
        }
      }
      for (var branch : node.branches())
        execution.setVariable(
            "route_" + node.key() + "_" + branch.key(), matched.contains(branch.key()));
      return;
    }
    var type = execution.getCurrentFlowElement();
    if (!(type instanceof org.flowable.bpmn.model.ExclusiveGateway)
        && !(type instanceof org.flowable.bpmn.model.InclusiveGateway)) return;
    long tenant = Long.parseLong(execution.getVariable("eamTenant").toString()),
        instance = Long.parseLong(execution.getVariable("eamInstance").toString()),
        version = Long.parseLong(execution.getVariable("eamVersion").toString());
    var definition =
        templates.definition(tenant, templates.templateForVersion(tenant, version), version);
    var node =
        definition.nodes().stream()
            .filter(n -> n.key().equals(execution.getCurrentActivityId()))
            .findFirst()
            .orElseThrow();
    var expressions = templates.expressions(tenant, version);
    var values = instances.values(tenant, instance);
    var matched = new ArrayList<String>();
    for (var branch : node.branches()) {
      if (node.branches().size() == 1
          || branch.fallback() && matched.isEmpty()
          || !branch.fallback()
              && WorkflowExpression.test(expressions.get(node.key() + ":" + branch.key()), values)
                  .matched()) {
        matched.add(branch.key());
        instances.event(
            tenant,
            instance,
            node.key(),
            "BRANCH",
            Long.parseLong(execution.getVariable("eamActor").toString()),
            "命中：" + branch.name());
        if (node.type().equals("BRANCH")) break;
      }
    }
    for (var branch : node.branches())
      execution.setVariable(
          "route_" + node.key() + "_" + branch.key(), matched.contains(branch.key()));
  }
}
