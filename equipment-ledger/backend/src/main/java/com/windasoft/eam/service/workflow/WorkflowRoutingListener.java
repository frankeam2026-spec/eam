package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.workflow.WorkflowTrialStep;
import com.windasoft.eam.repository.workflow.WorkflowEventRepository;
import com.windasoft.eam.repository.workflow.WorkflowRepository;
import java.util.ArrayList;
import java.util.Set;
import org.flowable.bpmn.model.ExclusiveGateway;
import org.flowable.bpmn.model.InclusiveGateway;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.ExecutionListener;
import org.springframework.stereotype.Component;

/** 仅由生成的BPMN在到达网关时调用；分支使用此前脚本计算后已落库的本轮变量。 */
@Component("eamWorkflowRouting")
public class WorkflowRoutingListener implements ExecutionListener {
  private final WorkflowRepository templates;
  private final WorkflowValueService valueStore;
  private final WorkflowEventRepository events;

  public WorkflowRoutingListener(
      WorkflowRepository templates,
      WorkflowValueService valueStore,
      WorkflowEventRepository events) {
    this.templates = templates;
    this.valueStore = valueStore;
    this.events = events;
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
      trial.trace.add(WorkflowTrialStep.node(node.key(), node.name(), node.type()));
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
        trial.trace.add(WorkflowTrialStep.branch(branch.key(), branch.name(), result));
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
    if (!(type instanceof ExclusiveGateway) && !(type instanceof InclusiveGateway)) return;
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
    var values = valueStore.values(tenant, instance);
    var matched = new ArrayList<String>();
    for (var branch : node.branches()) {
      if (node.branches().size() == 1
          || branch.fallback() && matched.isEmpty()
          || !branch.fallback()
              && WorkflowExpression.test(expressions.get(node.key() + ":" + branch.key()), values)
                  .matched()) {
        matched.add(branch.key());
        events.event(
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
