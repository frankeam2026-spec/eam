package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.repository.workflow.WorkflowRepository;
import com.windasoft.eam.service.system.*;
import java.util.*;
import org.flowable.engine.ProcessEngine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowTemplateService {
  private final WorkflowRepository repository;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final WorkflowVariables variables;
  private final WorkflowDefinitionValidator validator;
  private final ProcessEngine engine;
  private final WorkflowScriptRunner scriptRunner;
  private final org.springframework.transaction.PlatformTransactionManager transactions;

  public WorkflowTemplateService(
      WorkflowRepository repository,
      AuthContext auth,
      PermissionService permissions,
      WorkflowVariables variables,
      WorkflowDefinitionValidator validator,
      ProcessEngine engine,
      WorkflowScriptRunner scriptRunner,
      org.springframework.transaction.PlatformTransactionManager transactions) {
    this.repository = repository;
    this.auth = auth;
    this.permissions = permissions;
    this.variables = variables;
    this.validator = validator;
    this.engine = engine;
    this.scriptRunner = scriptRunner;
    this.transactions = transactions;
  }

  public Object list(String q, Long company, int page, int size) {
    return repository.list(
        auth.tenantId(),
        permissions.scope("workflow", "查看"),
        q,
        company,
        Math.max(1, page),
        Set.of(20, 50, 100).contains(size) ? size : 20);
  }

  public Object companies(String action) {
    if (!Set.of("查看", "编辑", "发布", "停用").contains(action))
      throw new IllegalArgumentException("操作无效");
    return repository.companies(auth.tenantId(), permissions.scope("workflow", action));
  }

  public Object variables(String business) {
    permissions.require("workflow", "查看");
    return variables.catalog(business).values();
  }

  public Object parse(String expression) {
    permissions.require("workflow", "查看");
    return WorkflowExpression.parse(expression, variables.catalog("requisition"));
  }

  public Object parse(String expression, WorkflowDefinition definition) {
    if (definition == null) return parse(expression);
    check(Long.parseLong(definition.companyId()), "查看");
    return WorkflowExpression.parse(expression, variables.catalog(definition));
  }

  public Object scriptTrial(
      long company, WorkflowDefinition.Script script, Map<String, Object> values) {
    check(company, "脚本试算");
    Object value = scriptRunner.execute(script, values);
    return Map.of(
        "value", value instanceof java.math.BigDecimal number ? number.toPlainString() : value);
  }

  public Object choices(long company, String kind, String q, int page, int size) {
    check(company, "查看");
    Set<Long> orgs = new HashSet<>(permissions.companyDepartments(company));
    orgs.add(company);
    orgs.removeIf(id -> !Objects.equals(permissions.companyOfOrganization(id), company));
    return repository.choices(
        auth.tenantId(),
        kind,
        orgs,
        q,
        Math.max(1, page),
        Set.of(20, 50, 100).contains(size) ? size : 20);
  }

  public Object resolve(long company, String kind, List<Long> ids) {
    check(company, "查看");
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new IllegalArgumentException("一次可解析1至100个关联对象");
    Set<Long> orgs = new HashSet<>(permissions.companyDepartments(company));
    orgs.add(company);
    orgs.removeIf(id -> !Objects.equals(permissions.companyOfOrganization(id), company));
    return repository.choices(auth.tenantId(), kind, orgs, "", 1, 100, ids);
  }

  public WorkflowDefinition detail(long id, Long version) {
    var t = repository.template(auth.tenantId(), id, false);
    check(((Number) t.get("company_id")).longValue(), "查看");
    return repository.definition(auth.tenantId(), id, version);
  }

  public Object versions(long id) {
    detail(id, null);
    return repository.versions(auth.tenantId(), id);
  }

  public void check(long company, String action) {
    if (!repository.companyExists(auth.tenantId(), company)
        || !permissions.companyAllowed("workflow", action, company))
      throw new ResponseStatusException(HttpStatus.FORBIDDEN, "无此公司流程管理权限");
  }

  @Transactional
  public WorkflowDefinition save(WorkflowDefinition d) {
    long company = Long.parseLong(d.companyId());
    check(company, "编辑");
    WorkflowDefinition previous = null;
    if (d.id() != null) {
      var t = repository.template(auth.tenantId(), Long.parseLong(d.id()), true);
      if (!Objects.equals(t.get("company_id").toString(), d.companyId())
          || !Objects.equals(t.get("business_type"), d.businessType()))
        throw new IllegalArgumentException("已有模板的适用公司和业务类型不可变更，请新建模板");
      previous = repository.definition(auth.tenantId(), Long.parseLong(d.id()), null);
    }
    var oldScripts = new LinkedHashMap<String, WorkflowDefinition.Script>();
    var newScripts = new LinkedHashMap<String, WorkflowDefinition.Script>();
    if (previous != null)
      previous.nodes().stream()
          .filter(n -> n.script() != null)
          .forEach(n -> oldScripts.put(n.key(), n.script()));
    if (d.nodes() != null)
      d.nodes().stream()
          .filter(Objects::nonNull)
          .filter(n -> n.script() != null)
          .forEach(n -> newScripts.put(n.key(), n.script()));
    if (!oldScripts.equals(newScripts)) check(company, "脚本配置");
    var expressions = validator.validate(d, false);
    validateActors(d);
    expressions.values().forEach(e -> validateReferences(company, e));
    long id = repository.save(auth.tenantId(), auth.userId(), d, expressions);
    return repository.definition(auth.tenantId(), id, null);
  }

  private void validateActors(WorkflowDefinition d) {
    var roles =
        repository.roles(auth.tenantId()).stream()
            .map(r -> r.get("id").toString())
            .collect(java.util.stream.Collectors.toSet());
    for (var node : d.nodes()) {
      if (node.users() != null)
        for (String user : node.users()) {
          Long org = repository.userOrg(auth.tenantId(), Long.parseLong(user));
          if (!Objects.equals(permissions.companyOfOrganization(org), Long.valueOf(d.companyId())))
            throw new IllegalArgumentException("固定审批人必须属于模板适用公司");
        }
      if (node.roles() != null && !roles.containsAll(node.roles()))
        throw new IllegalArgumentException("审批角色不存在或已停用");
    }
  }

  private void validateReferences(long company, WorkflowExpression.Expr expression) {
    var children = expression.children();
    if (children.size() > 1
        && "FIELD".equals(children.getFirst().op())
        && children.getFirst().type() == WorkflowExpression.Type.REFERENCE) {
      String field = children.getFirst().field();
      for (var child : children.subList(1, children.size()))
        if ("LITERAL".equals(child.op())) {
          long id = ((Number) child.literal()).longValue();
          if (!repository.referenceExists(auth.tenantId(), company, field, id))
            throw new IllegalArgumentException("条件引用的关联对象不存在或不可用：" + field);
          Long owner =
              switch (field) {
                case "applicantId" ->
                    permissions.companyOfOrganization(repository.userOrg(auth.tenantId(), id));
                case "applicantDepartmentId" -> permissions.companyOfOrganization(id);
                case "companyId" -> id;
                default -> company;
              };
          if (!Objects.equals(owner, company)) throw new IllegalArgumentException("条件关联对象不属于适用公司");
        }
    }
    children.forEach(c -> validateReferences(company, c));
  }

  @Transactional
  public WorkflowDefinition publish(long id, long rowVersion) {
    var t = repository.template(auth.tenantId(), id, true);
    check(((Number) t.get("company_id")).longValue(), "发布");
    if (((Number) t.get("row_version")).longValue() != rowVersion)
      throw new IllegalArgumentException("模板已变更，请刷新");
    var d = repository.definition(auth.tenantId(), id, null);
    validator
        .validate(d)
        .values()
        .forEach(e -> validateReferences(Long.parseLong(d.companyId()), e));
    validateActors(d);
    if (d.nodes().stream().anyMatch(n -> n.type().equals("SCRIPT"))) {
      check(Long.parseLong(d.companyId()), "脚本发布");
      for (var n : d.nodes()) if (n.script() != null) scriptRunner.validate(n.script());
    }
    // Resolve every configured node on publication; roles are resolved again when nodes are
    // entered.
    for (var n : d.nodes())
      if (n.type().equals("APPROVAL") && candidates(d, n).isEmpty())
        throw new IllegalArgumentException("节点没有可用审批人：" + n.name());
    String xml = WorkflowBpmn.create(d);
    var deployment =
        engine
            .getRepositoryService()
            .createDeployment()
            .tenantId("" + auth.tenantId())
            .name(d.name())
            .addString("eam.bpmn20.xml", xml)
            .deploy();
    var definition =
        engine
            .getRepositoryService()
            .createProcessDefinitionQuery()
            .deploymentId(deployment.getId())
            .singleResult();
    repository.publish(
        auth.tenantId(),
        Long.parseLong(d.companyId()),
        d.businessType(),
        Long.parseLong(d.versionId()),
        auth.userId(),
        xml,
        definition.getId());
    return repository.definition(auth.tenantId(), id, null);
  }

  public Set<Long> candidates(WorkflowDefinition d, WorkflowDefinition.Node n) {
    Set<Long> users = new TreeSet<>();
    if (n.users() != null)
      for (String id : n.users()) {
        long user = Long.parseLong(id);
        if (Objects.equals(
            permissions.companyOfOrganization(repository.userOrg(auth.tenantId(), user)),
            Long.valueOf(d.companyId()))) users.add(user);
        else throw new IllegalArgumentException("固定审批人已不属于适用公司，请调整流程");
      }
    if (n.roles() != null)
      for (String role : n.roles())
        for (long user : repository.roleUsers(auth.tenantId(), Long.parseLong(role)))
          if (Objects.equals(
              permissions.companyOfOrganization(repository.userOrg(auth.tenantId(), user)),
              Long.valueOf(d.companyId()))) users.add(user);
    return users;
  }

  @Transactional
  public void disable(long id, long rowVersion) {
    var t = repository.template(auth.tenantId(), id, true);
    check(((Number) t.get("company_id")).longValue(), "停用");
    if (((Number) t.get("row_version")).longValue() != rowVersion)
      throw new IllegalArgumentException("模板已变更，请刷新");
    repository.disable(auth.tenantId(), id);
  }

  public Object simulate(WorkflowDefinition definition, Map<String, Object> values) {
    check(Long.parseLong(definition.companyId()), "编辑");
    validator.validate(definition);
    if (definition.nodes().stream().anyMatch(n -> n.script() != null))
      check(Long.parseLong(definition.companyId()), "脚本试算");
    var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
    transaction.setPropagationBehavior(
        org.springframework.transaction.TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    return transaction.execute(
        status -> {
          status.setRollbackOnly();
          try (var trial = new WorkflowSimulation(definition, values)) {
            var deployment =
                engine
                    .getRepositoryService()
                    .createDeployment()
                    .tenantId(Long.toString(auth.tenantId()))
                    .addString("trial.bpmn20.xml", WorkflowBpmn.create(definition))
                    .deploy();
            var processDefinition =
                engine
                    .getRepositoryService()
                    .createProcessDefinitionQuery()
                    .deploymentId(deployment.getId())
                    .singleResult();
            var process =
                engine.getRuntimeService().startProcessInstanceById(processDefinition.getId());
            for (int step = 0; step < 1000; step++) {
              var tasks =
                  engine
                      .getTaskService()
                      .createTaskQuery()
                      .processInstanceId(process.getId())
                      .list();
              if (tasks.isEmpty()) {
                if (engine
                        .getRuntimeService()
                        .createProcessInstanceQuery()
                        .processInstanceId(process.getId())
                        .count()
                    > 0) throw new IllegalArgumentException("流程仍有等待或网关未汇合，请检查连接");
                return List.copyOf(trial.trace);
              }
              for (var task : tasks)
                if (engine.getTaskService().createTaskQuery().taskId(task.getId()).count() > 0)
                  engine.getTaskService().complete(task.getId());
            }
            throw new IllegalArgumentException("试算超过最大节点步数");
          }
        });
  }
}
