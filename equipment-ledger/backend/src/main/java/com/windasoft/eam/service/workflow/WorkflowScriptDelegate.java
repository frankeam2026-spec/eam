package com.windasoft.eam.service.workflow;

import com.windasoft.eam.dto.workflow.WorkflowTrialStep;
import com.windasoft.eam.repository.workflow.WorkflowEventRepository;
import com.windasoft.eam.repository.workflow.WorkflowRepository;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;
import org.flowable.common.engine.impl.context.Context;
import org.flowable.engine.delegate.DelegateExecution;
import org.flowable.engine.delegate.JavaDelegate;
import org.springframework.stereotype.Component;

@Component("eamWorkflowScript")
public class WorkflowScriptDelegate implements JavaDelegate {
  private final WorkflowRepository templates;
  private final WorkflowValueService valueStore;
  private final WorkflowEventRepository events;
  private final WorkflowScriptRunner runner;

  public WorkflowScriptDelegate(
      WorkflowRepository templates,
      WorkflowValueService valueStore,
      WorkflowEventRepository events,
      WorkflowScriptRunner runner) {
    this.templates = templates;
    this.valueStore = valueStore;
    this.events = events;
    this.runner = runner;
  }

  @Override
  public void execute(DelegateExecution execution) {
    // 同一引擎命令共享总截止时间，防止多个脚本各自重置超时绕过执行上限。
    var command = Context.getCommandContext();
    var deadline = (Long) command.getAttribute("eam.scriptDeadline");
    if (deadline == null) {
      deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
      command.addAttribute("eam.scriptDeadline", deadline);
    }
    var trial = WorkflowSimulation.current();
    if (trial != null) deadline = Math.min(deadline, trial.deadline);
    long timeout = TimeUnit.NANOSECONDS.toMillis(deadline - System.nanoTime());
    if (trial != null) {
      var node =
          trial.definition.nodes().stream()
              .filter(n -> n.key().equals(execution.getCurrentActivityId()))
              .findFirst()
              .orElseThrow();
      var value = runner.execute(node.script(), trial.values, timeout);
      trial.values.put(node.script().outputKey(), value);
      trial.trace.add(WorkflowTrialStep.script(node.key(), node.name(), value.toString()));
      return;
    }
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
    LocalDateTime started = LocalDateTime.now();
    var value = runner.execute(node.script(), valueStore.values(tenant, instance), timeout);
    String hash;
    try {
      hash =
          HexFormat.of()
              .formatHex(
                  MessageDigest.getInstance("SHA-256")
                      .digest(node.script().source().getBytes(StandardCharsets.UTF_8)));
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
    valueStore.scriptResult(
        tenant,
        instance,
        node.key(),
        execution.getId(),
        hash,
        started,
        node.script().outputKey(),
        node.script().outputType(),
        value);
    events.event(
        tenant,
        instance,
        node.key(),
        "SCRIPT",
        Long.parseLong(execution.getVariable("eamActor").toString()),
        "脚本计算完成：" + node.name());
  }
}
