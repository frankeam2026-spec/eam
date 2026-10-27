package com.windasoft.eam.service.workflow;

import com.windasoft.eam.repository.workflow.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.HexFormat;
import org.flowable.engine.delegate.*;
import org.springframework.stereotype.Component;

@Component("eamWorkflowScript")
public class WorkflowScriptDelegate implements JavaDelegate {
  private final WorkflowRepository templates;
  private final WorkflowInstanceRepository instances;
  private final WorkflowScriptRunner runner;

  public WorkflowScriptDelegate(
      WorkflowRepository templates,
      WorkflowInstanceRepository instances,
      WorkflowScriptRunner runner) {
    this.templates = templates;
    this.instances = instances;
    this.runner = runner;
  }

  @Override
  public void execute(DelegateExecution execution) {
    var command=org.flowable.common.engine.impl.context.Context.getCommandContext();
    var deadline=(Long)command.getAttribute("eam.scriptDeadline");
    if(deadline==null){deadline=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(15);command.addAttribute("eam.scriptDeadline",deadline);}
    var trial = WorkflowSimulation.current();
    if(trial!=null)deadline=Math.min(deadline,trial.deadline);
    long timeout=java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(deadline-System.nanoTime());
    if (trial != null) {
      var node =
          trial.definition.nodes().stream()
              .filter(n -> n.key().equals(execution.getCurrentActivityId()))
              .findFirst()
              .orElseThrow();
      var value = runner.execute(node.script(), trial.values,timeout);
      trial.values.put(node.script().outputKey(), value);
      trial.trace.add(
          java.util.Map.of(
              "node",
              node.key(),
              "name",
              node.name(),
              "type",
              "SCRIPT_RESULT",
              "value",
              value.toString()));
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
    var value = runner.execute(node.script(), instances.values(tenant, instance),timeout);
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
    instances.scriptResult(
        tenant,
        instance,
        node.key(),
        execution.getId(),
        hash,
        started,
        node.script().outputKey(),
        node.script().outputType(),
        value);
    instances.event(
        tenant,
        instance,
        node.key(),
        "SCRIPT",
        Long.parseLong(execution.getVariable("eamActor").toString()),
        "脚本计算完成：" + node.name());
  }
}
