package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.repository.workflow.*;
import com.windasoft.eam.service.system.AuthContext;
import java.util.*;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowRuntimeService {
  private final WorkflowInstanceRepository repository;
  private final WorkflowRepository templates;
  private final WorkflowTemplateService templateService;
  private final WorkflowDefinitionValidator validator;
  private final WorkflowVariables variables;
  private final AuthContext auth;
  private final ProcessEngine engine;
  private final ObjectProvider<WorkflowBusinessAdapter> adapters;

  public WorkflowRuntimeService(
      WorkflowInstanceRepository repository,
      WorkflowRepository templates,
      WorkflowTemplateService templateService,
      WorkflowDefinitionValidator validator,
      WorkflowVariables variables,
      AuthContext auth,
      ProcessEngine engine,
      ObjectProvider<WorkflowBusinessAdapter> adapters) {
    this.repository = repository;
    this.templates = templates;
    this.templateService = templateService;
    this.validator = validator;
    this.variables = variables;
    this.auth = auth;
    this.engine = engine;
    this.adapters = adapters;
  }

  private WorkflowBusinessAdapter adapter(String business) {
    return adapters.stream()
        .filter(a -> a.businessType().equals(business))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("未接入审批业务"));
  }

  public boolean participant(String business, long id) {
    return repository.participant(auth.tenantId(), business, id, auth.userId());
  }

  public boolean approved(String business, long id) {
    var i = repository.latest(auth.tenantId(), business, id);
    return i != null && "APPROVED".equals(i.get("state"));
  }

  public boolean configured(long company, String business) {
    return repository.binding(auth.tenantId(), company, business) != null;
  }

  @Transactional
  public long start(String business, long businessId, long company, long applicant) {
    var adapter = adapter(business);
    adapter.lock(businessId);
    adapter.requireActive(businessId);
    var previous = repository.latest(auth.tenantId(), business, businessId);
    if (previous != null && Set.of("RUNNING", "APPROVED").contains(previous.get("state")))
      throw new IllegalArgumentException("此申请已启动审批");
    var binding = repository.binding(auth.tenantId(), company, business);
    if (binding == null
        || ((Number) binding.get("enabled")).intValue() != 1
        || binding.get("version_id") == null)
      throw new IllegalArgumentException("本公司尚未配置有效审批流程，请联系管理员");
    long version = ((Number) binding.get("version_id")).longValue();
    var definition =
        templates.definition(
            auth.tenantId(), ((Number) binding.get("template_id")).longValue(), version);
    validator.validate(definition);
    var expressions = templates.expressions(auth.tenantId(), version);
    var values = adapter.values(businessId);
    long instance =
        repository.start(auth.tenantId(), version, business, businessId, company, applicant);
    repository.snapshot(auth.tenantId(), instance, variables.catalog(business), values);
    Map<String, Object> routes = new LinkedHashMap<>();
    routes.put("eamTenant", Long.toString(auth.tenantId()));
    routes.put("eamInstance", Long.toString(instance));
    routes.put("eamVersion", Long.toString(version));
    routes.put("eamActor", Long.toString(auth.userId()));
    if (((Number) binding.get("execution_version")).intValue() == 1) {
      Map<String, WorkflowDefinition.Node> nodes = new LinkedHashMap<>();
      definition.nodes().forEach(n -> nodes.put(n.key(), n));
      var node =
          definition.nodes().stream()
              .filter(n -> n.type().equals("START"))
              .findFirst()
              .orElseThrow();
      while (!node.type().equals("END")) {
        String next = node.next();
        if (node.type().equals("BRANCH"))
          for (var branch : node.branches())
            if (branch.fallback()
                || WorkflowExpression.test(expressions.get(node.key() + ":" + branch.key()), values)
                    .matched()) {
              routes.put("route_" + node.key(), branch.key());
              next = branch.target();
              repository.event(
                  auth.tenantId(),
                  instance,
                  node.key(),
                  "BRANCH",
                  auth.userId(),
                  "命中：" + branch.name());
              break;
            }
        node = nodes.get(next);
      }
    }
    var process =
        engine
            .getRuntimeService()
            .startProcessInstanceById(
                binding.get("engine_definition_id").toString(), "" + instance, routes);
    repository.engine(auth.tenantId(), instance, process.getId());
    repository.event(auth.tenantId(), instance, null, "SUBMITTED", auth.userId(), "提交审批");
    enter(repository.instance(auth.tenantId(), instance, false), definition);
    return instance;
  }

  private void enter(Map<String, Object> instance, WorkflowDefinition definition) {
    long id = number(instance, "id");
    String process = instance.get("engine_instance_id").toString();
    var tasks = engine.getTaskService().createTaskQuery().processInstanceId(process).list();
    if (tasks.isEmpty()) {
      if (engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(process).count()
          > 0) return;
      adapter(instance.get("business_type").toString())
          .requireActive(number(instance, "business_id"));
      repository.finish(auth.tenantId(), id, "APPROVED");
      adapter(instance.get("business_type").toString()).approved(number(instance, "business_id"));
      repository.event(auth.tenantId(), id, null, "APPROVED", auth.userId(), "审批通过");
      return;
    }
    for (var task : tasks) {
      if (repository.registered(auth.tenantId(), id, task.getId())) continue;
      var node =
          definition.nodes().stream()
              .filter(n -> n.key().equals(task.getTaskDefinitionKey()))
              .findFirst()
              .orElseThrow();
      var users = templateService.candidates(definition, node);
      if (users.isEmpty()) throw new IllegalArgumentException("下一节点没有可用审批人：" + node.name());
      for (long user : users)
        repository.approval(
            auth.tenantId(),
            id,
            node.key(),
            task.getId(),
            user,
            number(instance, "business_id"),
            "领料申请待审批 · " + node.name());
    }
  }

  @Transactional
  public Object act(
      long instanceId, long approvalId, long rowVersion, boolean approve, String opinion) {
    if (opinion == null) opinion = "";
    if (opinion.length() > 1000) throw new IllegalArgumentException("审批意见不能超过1000字");
    var raw = repository.instance(auth.tenantId(), instanceId, false);
    var adapter = adapter(raw.get("business_type").toString());
    adapter.lock(number(raw, "business_id"));
    var instance = repository.instance(auth.tenantId(), instanceId, true);
    running(instance, rowVersion);
    var current =
        repository.approvals(auth.tenantId(), instanceId).stream()
            .filter(
                a ->
                    number(a, "id") == approvalId
                        && number(a, "user_id") == auth.userId()
                        && "PENDING".equals(a.get("state")))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无此待办的审批权限"));
    if (approve) adapter.requireActive(number(instance, "business_id"));
    repository.act(auth.tenantId(), approvalId, approve ? "APPROVED" : "REJECTED", opinion);
    repository.touch(auth.tenantId(), instanceId);
    repository.event(
        auth.tenantId(),
        instanceId,
        current.get("node_key").toString(),
        approve ? "APPROVE" : "REJECT",
        auth.userId(),
        opinion);
    if (!approve) {
      engine
          .getRuntimeService()
          .deleteProcessInstance(instance.get("engine_instance_id").toString(), "驳回");
      repository.finish(auth.tenantId(), instanceId, "REJECTED");
      adapter.returned(number(instance, "business_id"), "审批驳回");
    } else {
      var definition = definition(instance);
      var node =
          definition.nodes().stream()
              .filter(n -> n.key().equals(current.get("node_key")))
              .findFirst()
              .orElseThrow();
      boolean complete =
          node.approvalMode().equals("ANY")
              || repository.approvals(auth.tenantId(), instanceId).stream()
                  .noneMatch(
                      a ->
                          Objects.equals(a.get("engine_task_id"), current.get("engine_task_id"))
                              && "PENDING".equals(a.get("state")));
      repository.closeTodos(auth.tenantId(), instanceId);
      if (complete) {
        repository.cancelNode(
            auth.tenantId(), instanceId, current.get("engine_task_id").toString());
        engine
            .getRuntimeService()
            .setVariable(
                instance.get("engine_instance_id").toString(),
                "eamActor",
                Long.toString(auth.userId()));
        engine.getTaskService().complete(current.get("engine_task_id").toString());
        enter(instance, definition);
      }
    }
    return history(instance.get("business_type").toString(), number(instance, "business_id"));
  }

  @Transactional
  public Object withdraw(long instanceId, long rowVersion) {
    var raw = repository.instance(auth.tenantId(), instanceId, false);
    var adapter = adapter(raw.get("business_type").toString());
    adapter.lock(number(raw, "business_id"));
    var instance = repository.instance(auth.tenantId(), instanceId, true);
    running(instance, rowVersion);
    if (number(instance, "applicant_id") != auth.userId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅申请人可以撤回");
    engine
        .getRuntimeService()
        .deleteProcessInstance(instance.get("engine_instance_id").toString(), "申请人撤回");
    repository.finish(auth.tenantId(), instanceId, "WITHDRAWN");
    adapter.returned(number(instance, "business_id"), "撤回审批");
    repository.event(auth.tenantId(), instanceId, null, "WITHDRAW", auth.userId(), "申请人撤回");
    return history(instance.get("business_type").toString(), number(instance, "business_id"));
  }

  private WorkflowDefinition definition(Map<String, Object> instance) {
    long version = number(instance, "version_id");
    return templates.definition(
        auth.tenantId(), templates.templateForVersion(auth.tenantId(), version), version);
  }

  /** Caller must authorize access to the business document before invoking history. */
  public Object history(String business, long id) {
    var rounds = repository.history(auth.tenantId(), business, id);
    for (var round : rounds) {
      long instance = number(round, "id");
      round.put("approvals", repository.approvals(auth.tenantId(), instance));
      round.put("events", repository.events(auth.tenantId(), instance));
      long version = number(round, "versionId");
      var pinned =
          templates.definition(
              auth.tenantId(), templates.templateForVersion(auth.tenantId(), version), version);
      round.put("nodes", com.windasoft.eam.dto.workflow.WorkflowProgress.nodes(pinned));
    }
    return com.windasoft.eam.dto.workflow.WorkflowResponse.exact(rounds);
  }

  private static long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  private static void running(Map<String, Object> row, long version) {
    if (!"RUNNING".equals(row.get("state")) || number(row, "row_version") != version)
      throw new IllegalArgumentException("审批状态已变化，请刷新后重试");
  }
}
