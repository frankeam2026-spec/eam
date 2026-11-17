package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.domain.workflow.WorkflowApprovalStatus;
import com.windasoft.eam.domain.workflow.WorkflowInstanceStatus;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowHistoryRound;
import com.windasoft.eam.entity.workflow.WorkflowInstance;
import com.windasoft.eam.repository.workflow.WorkflowApprovalRepository;
import com.windasoft.eam.repository.workflow.WorkflowBindingRepository;
import com.windasoft.eam.repository.workflow.WorkflowEventRepository;
import com.windasoft.eam.repository.workflow.WorkflowInstanceRepository;
import com.windasoft.eam.repository.workflow.WorkflowRepository;
import com.windasoft.eam.service.system.AuthContext;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.flowable.engine.ProcessEngine;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowRuntimeService {
  private final WorkflowInstanceRepository repository;
  private final WorkflowHistoryService histories;
  private final WorkflowApprovalRepository approvals;
  private final WorkflowJournalService journal;
  private final WorkflowBindingRepository bindings;
  private final WorkflowValueService valueStore;
  private final WorkflowEventRepository events;
  private final WorkflowRepository templates;
  private final WorkflowTemplateService templateService;
  private final WorkflowDefinitionValidator validator;
  private final WorkflowVariables variables;
  private final AuthContext auth;
  private final ProcessEngine engine;
  private final ObjectProvider<WorkflowBusinessAdapter> adapters;

  public WorkflowRuntimeService(
      WorkflowInstanceRepository repository,
      WorkflowHistoryService histories,
      WorkflowApprovalRepository approvals,
      WorkflowJournalService journal,
      WorkflowBindingRepository bindings,
      WorkflowValueService valueStore,
      WorkflowEventRepository events,
      WorkflowRepository templates,
      WorkflowTemplateService templateService,
      WorkflowDefinitionValidator validator,
      WorkflowVariables variables,
      AuthContext auth,
      ProcessEngine engine,
      ObjectProvider<WorkflowBusinessAdapter> adapters) {
    this.repository = repository;
    this.histories = histories;
    this.approvals = approvals;
    this.journal = journal;
    this.bindings = bindings;
    this.valueStore = valueStore;
    this.events = events;
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
    return i != null && WorkflowInstanceStatus.APPROVED.code().equals(i.getState());
  }

  public boolean configured(long company, String business) {
    return bindings.effective(auth.tenantId(), company, business) != null;
  }

  public boolean repairApprovalRequired(long company) {
    var binding = bindings.find(auth.tenantId(), company, "repair_report", true);
    return binding != null && binding.getRequiresApproval() == 1;
  }

  /** 先取得业务锁再创建轮次；业务状态、变量快照、引擎实例及待办共享事务，失败整体回滚。 */
  @Transactional
  public long start(String business, long businessId, long company, long applicant) {
    var adapter = adapter(business);
    adapter.lock(businessId);
    adapter.requireActive(businessId);
    var previous = repository.latest(auth.tenantId(), business, businessId);
    if (previous != null
        && Set.of(WorkflowInstanceStatus.RUNNING.code(), WorkflowInstanceStatus.APPROVED.code())
            .contains(previous.getState())) throw new IllegalArgumentException("此申请已启动审批");
    var binding = bindings.effective(auth.tenantId(), company, business);
    if (binding == null || binding.enabled() != 1 || binding.versionId() == null)
      throw new IllegalArgumentException("本公司尚未配置有效审批流程，请联系管理员");
    long version = binding.versionId();
    var definition = templates.definition(auth.tenantId(), binding.templateId(), version);
    validator.validate(definition);
    var expressions = templates.expressions(auth.tenantId(), version);
    var values = adapter.values(businessId);
    long instance =
        repository.start(auth.tenantId(), version, business, businessId, company, applicant);
    valueStore.snapshot(auth.tenantId(), instance, variables.catalog(business), values);
    // Flowable原生变量接口使用Map；此处只写服务端取得的固定执行标识，不作为业务DTO。
    Map<String, Object> routes = new LinkedHashMap<>();
    routes.put("eamTenant", Long.toString(auth.tenantId()));
    routes.put("eamInstance", Long.toString(instance));
    routes.put("eamVersion", Long.toString(version));
    routes.put("eamActor", Long.toString(auth.userId()));
    if (binding.executionVersion() == 1) {
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
              events.event(
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
            .startProcessInstanceById(binding.engineDefinitionId(), "" + instance, routes);
    repository.engine(auth.tenantId(), instance, process.getId());
    events.event(auth.tenantId(), instance, null, "SUBMITTED", auth.userId(), "提交审批");
    enter(repository.instance(auth.tenantId(), instance, false), definition);
    return instance;
  }

  private void enter(WorkflowInstance instance, WorkflowDefinition definition) {
    long id = instance.getId();
    String process = instance.getEngineInstanceId();
    var tasks = engine.getTaskService().createTaskQuery().processInstanceId(process).list();
    if (tasks.isEmpty()) {
      if (engine.getRuntimeService().createProcessInstanceQuery().processInstanceId(process).count()
          > 0) return;
      adapter(instance.getBusinessType()).requireActive(instance.getBusinessId());
      finish(id, WorkflowInstanceStatus.APPROVED.code());
      adapter(instance.getBusinessType()).approved(instance.getBusinessId());
      events.event(auth.tenantId(), id, null, "APPROVED", auth.userId(), "审批通过");
      return;
    }
    for (var task : tasks) {
      if (approvals.registered(auth.tenantId(), id, task.getId())) continue;
      var node =
          definition.nodes().stream()
              .filter(n -> n.key().equals(task.getTaskDefinitionKey()))
              .findFirst()
              .orElseThrow();
      var users = templateService.candidates(definition, node);
      if (users.isEmpty()) throw new IllegalArgumentException("下一节点没有可用审批人：" + node.name());
      for (long user : users)
        journal.approval(
            auth.tenantId(),
            id,
            node.key(),
            task.getId(),
            user,
            instance.getBusinessId(),
            (instance.getBusinessType().equals("repair_report") ? "报修待审批" : "领料申请待审批")
                + " · "
                + node.name(),
            instance.getBusinessType());
    }
  }

  /** 先锁业务单据再锁实例，检查办理人和版本后推进；会签按同一引擎任务判断，避免误关其他分支。 */
  @Transactional
  public List<WorkflowHistoryRound> act(
      long instanceId, long approvalId, long rowVersion, boolean approve, String opinion) {
    if (opinion == null) opinion = "";
    if (opinion.length() > 1000) throw new IllegalArgumentException("审批意见不能超过1000字");
    var raw = repository.instance(auth.tenantId(), instanceId, false);
    var adapter = adapter(raw.getBusinessType());
    adapter.lock(raw.getBusinessId());
    var instance = repository.instance(auth.tenantId(), instanceId, true);
    running(instance, rowVersion);
    var current =
        approvals.list(auth.tenantId(), instanceId).stream()
            .filter(
                a ->
                    a.getId() == approvalId
                        && a.getUserId() == auth.userId()
                        && WorkflowApprovalStatus.PENDING.code().equals(a.getState()))
            .findFirst()
            .orElseThrow(() -> new ResponseStatusException(HttpStatus.FORBIDDEN, "无此待办的审批权限"));
    if (approve) adapter.requireActive(instance.getBusinessId());
    if (!approvals.act(
        auth.tenantId(),
        approvalId,
        approve ? WorkflowApprovalStatus.APPROVED.code() : WorkflowApprovalStatus.REJECTED.code(),
        opinion)) {
      throw new IllegalArgumentException("审批任务已被处理");
    }
    repository.touch(auth.tenantId(), instanceId);
    events.event(
        auth.tenantId(),
        instanceId,
        current.getNodeKey(),
        approve ? "APPROVE" : "REJECT",
        auth.userId(),
        opinion);
    if (!approve) {
      engine.getRuntimeService().deleteProcessInstance(instance.getEngineInstanceId(), "驳回");
      finish(instanceId, WorkflowInstanceStatus.REJECTED.code());
      adapter.returned(instance.getBusinessId(), "审批驳回");
    } else {
      var definition = definition(instance);
      var node =
          definition.nodes().stream()
              .filter(n -> n.key().equals(current.getNodeKey()))
              .findFirst()
              .orElseThrow();
      boolean complete =
          node.approvalMode().equals("ANY")
              || approvals.list(auth.tenantId(), instanceId).stream()
                  .noneMatch(
                      a ->
                          Objects.equals(a.getEngineTaskId(), current.getEngineTaskId())
                              && WorkflowApprovalStatus.PENDING.code().equals(a.getState()));
      journal.closeTodos(auth.tenantId(), instanceId);
      if (complete) {
        approvals.cancel(auth.tenantId(), instanceId, current.getEngineTaskId());
        journal.closeTodos(auth.tenantId(), instanceId);
        engine
            .getRuntimeService()
            .setVariable(instance.getEngineInstanceId(), "eamActor", Long.toString(auth.userId()));
        engine.getTaskService().complete(current.getEngineTaskId());
        enter(instance, definition);
      }
    }
    return history(instance.getBusinessType(), instance.getBusinessId());
  }

  /** 仅申请人可撤回当前运行轮次；保持业务锁→实例锁顺序，并同步撤销引擎与精确待办。 */
  @Transactional
  public List<WorkflowHistoryRound> withdraw(long instanceId, long rowVersion) {
    var raw = repository.instance(auth.tenantId(), instanceId, false);
    var adapter = adapter(raw.getBusinessType());
    adapter.lock(raw.getBusinessId());
    var instance = repository.instance(auth.tenantId(), instanceId, true);
    running(instance, rowVersion);
    if (instance.getApplicantId() != auth.userId())
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "仅申请人可以撤回");
    engine.getRuntimeService().deleteProcessInstance(instance.getEngineInstanceId(), "申请人撤回");
    finish(instanceId, WorkflowInstanceStatus.WITHDRAWN.code());
    adapter.returned(instance.getBusinessId(), "撤回审批");
    events.event(auth.tenantId(), instanceId, null, "WITHDRAW", auth.userId(), "申请人撤回");
    return history(instance.getBusinessType(), instance.getBusinessId());
  }

  /** 终止实例后取消本轮未完成人员任务，再精确关闭关联消息；加入原调用方事务。 */
  private void finish(long instance, String state) {
    repository.finish(auth.tenantId(), instance, state);
    approvals.cancel(auth.tenantId(), instance, null);
    journal.closeTodos(auth.tenantId(), instance);
  }

  private WorkflowDefinition definition(WorkflowInstance instance) {
    long version = instance.getVersionId();
    return templates.definition(
        auth.tenantId(), templates.templateForVersion(auth.tenantId(), version), version);
  }

  /** 单据读取权限由业务调用方校验；返回固定历史类型，ID精度由DTO明确声明。 */
  public List<WorkflowHistoryRound> history(String business, long id) {
    return histories.history(auth.tenantId(), business, id);
  }

  private static void running(WorkflowInstance row, long version) {
    if (!WorkflowInstanceStatus.RUNNING.code().equals(row.getState())
        || row.getRowVersion() != version) throw new IllegalArgumentException("审批状态已变化，请刷新后重试");
  }
}
