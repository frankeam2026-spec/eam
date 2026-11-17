package com.windasoft.eam.service.workflow;

import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.windasoft.eam.common.LocalizedArgumentException;
import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.common.ListQuery;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.ChoicePage;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Company;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.ScriptResult;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Setting;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.TemplatePage;
import com.windasoft.eam.dto.workflow.WorkflowTrialStep;
import com.windasoft.eam.repository.workflow.WorkflowBindingRepository;
import com.windasoft.eam.repository.workflow.WorkflowRepository;
import com.windasoft.eam.repository.workflow.WorkflowTemplateRepository;
import com.windasoft.eam.repository.workflow.WorkflowVersionRepository;
import com.windasoft.eam.service.system.AuthContext;
import com.windasoft.eam.service.system.PermissionService;
import java.math.BigDecimal;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.flowable.engine.ProcessEngine;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class WorkflowTemplateService {
  private static final Set<Integer> PAGE_SIZES = Set.of(20, 50, 100);
  private final WorkflowRepository repository;
  private final WorkflowDraftService drafts;
  private final WorkflowActorService actors;
  private final WorkflowBindingRepository bindings;
  private final WorkflowTemplateRepository templates;
  private final WorkflowVersionRepository versions;
  private final AuthContext auth;
  private final PermissionService permissions;
  private final WorkflowVariables variables;
  private final WorkflowDefinitionValidator validator;
  private final ProcessEngine engine;
  private final WorkflowScriptRunner scriptRunner;
  private final PlatformTransactionManager transactions;

  public WorkflowTemplateService(
      WorkflowRepository repository,
      WorkflowDraftService drafts,
      WorkflowActorService actors,
      WorkflowBindingRepository bindings,
      WorkflowTemplateRepository templates,
      WorkflowVersionRepository versions,
      AuthContext auth,
      PermissionService permissions,
      WorkflowVariables variables,
      WorkflowDefinitionValidator validator,
      ProcessEngine engine,
      WorkflowScriptRunner scriptRunner,
      PlatformTransactionManager transactions) {
    this.repository = repository;
    this.drafts = drafts;
    this.actors = actors;
    this.bindings = bindings;
    this.templates = templates;
    this.versions = versions;
    this.auth = auth;
    this.permissions = permissions;
    this.variables = variables;
    this.validator = validator;
    this.engine = engine;
    this.scriptRunner = scriptRunner;
    this.transactions = transactions;
  }

  public TemplatePage list(String q, Long company, int page, int size) {
    validatePage(q, page, size);
    return repository.list(
        auth.tenantId(), permissions.scope("workflow", "查看"), q, company, page, size);
  }

  /** 复用现有分页/查询长度校验，保留工作流20/50/100边界；不能静默改变非法请求。 */
  private void validatePage(String keyword, int page, int size) {
    ListQuery.from(
        Map.of(
            "page",
            Integer.toString(page),
            "size",
            Integer.toString(size),
            "q",
            keyword == null ? "" : keyword));
    if (!PAGE_SIZES.contains(size)) {
      throw new LocalizedArgumentException("server.common.message0003");
    }
  }

  public List<Company> companies(String action) {
    if (!Set.of("查看", "编辑", "发布", "停用").contains(action))
      throw new IllegalArgumentException("操作无效");
    return repository.companies(auth.tenantId(), permissions.scope("workflow", action));
  }

  public Collection<WorkflowExpression.Variable> variables(String business) {
    permissions.require("workflow", "查看");
    return variables.catalog(business).values();
  }

  public Setting approvalSetting(long company) {
    check(company, "查看");
    return bindings.approvalSetting(auth.tenantId(), company);
  }

  @Transactional
  public Setting approvalSetting(long company, boolean required, long version) {
    check(company, "发布");
    bindings.approvalSetting(auth.tenantId(), company, required, version);
    return bindings.approvalSetting(auth.tenantId(), company);
  }

  public WorkflowExpression.Expr parse(String expression) {
    permissions.require("workflow", "查看");
    return WorkflowExpression.parse(expression, variables.catalog("requisition"));
  }

  public WorkflowExpression.Expr parse(String expression, WorkflowDefinition definition) {
    if (definition == null) return parse(expression);
    check(Long.parseLong(definition.companyId()), "查看");
    return WorkflowExpression.parse(expression, variables.catalog(definition));
  }

  public ScriptResult scriptTrial(
      long company, WorkflowDefinition.Script script, Map<String, Object> values) {
    check(company, "脚本试算");
    Object value = scriptRunner.execute(script, values);
    return new ScriptResult(
        value instanceof Boolean b
            ? JsonNodeFactory.instance.booleanNode(b)
            : JsonNodeFactory.instance.textNode(
                value instanceof BigDecimal n ? n.toPlainString() : value.toString()));
  }

  public ChoicePage choices(long company, String kind, String q, int page, int size) {
    validatePage(q, page, size);
    check(company, "查看");
    Set<Long> orgs = new HashSet<>(permissions.companyDepartments(company));
    orgs.add(company);
    orgs.removeIf(id -> !Objects.equals(permissions.companyOfOrganization(id), company));
    return repository.choices(auth.tenantId(), kind, orgs, q, page, size);
  }

  public ChoicePage resolve(long company, String kind, List<Long> ids) {
    check(company, "查看");
    if (ids == null || ids.isEmpty() || ids.size() > 100)
      throw new IllegalArgumentException("一次可解析1至100个关联对象");
    Set<Long> orgs = new HashSet<>(permissions.companyDepartments(company));
    orgs.add(company);
    orgs.removeIf(id -> !Objects.equals(permissions.companyOfOrganization(id), company));
    return repository.choices(auth.tenantId(), kind, orgs, "", 1, 100, ids);
  }

  public WorkflowDefinition detail(long id, Long version) {
    var t = templates.find(auth.tenantId(), id, false);
    check(t.getCompanyId(), "查看");
    return repository.definition(auth.tenantId(), id, version);
  }

  public List<WorkflowTemplateViews.Version> versions(long id) {
    detail(id, null);
    return versions.list(auth.tenantId(), id).stream()
        .map(
            v ->
                new WorkflowTemplateViews.Version(
                    v.getId().toString(),
                    v.getVersionNo(),
                    v.getState(),
                    v.getPublishedBy(),
                    v.getPublishedAt()))
        .toList();
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
      var t = templates.find(auth.tenantId(), Long.parseLong(d.id()), true);
      if (!Objects.equals(t.getCompanyId().toString(), d.companyId())
          || !Objects.equals(t.getBusinessType(), d.businessType()))
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
    actors.validate(auth.tenantId(), d);
    expressions.values().forEach(e -> validateReferences(company, e));
    long id = drafts.save(auth.tenantId(), auth.userId(), d, expressions);
    return repository.definition(auth.tenantId(), id, null);
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
                case "applicantDepartmentId", "departmentId" ->
                    permissions.companyOfOrganization(id);
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
    var t = templates.find(auth.tenantId(), id, true);
    check(t.getCompanyId(), "发布");
    if (t.getRowVersion() != rowVersion) throw new IllegalArgumentException("模板已变更，请刷新");
    var d = repository.definition(auth.tenantId(), id, null);
    validator
        .validate(d)
        .values()
        .forEach(e -> validateReferences(Long.parseLong(d.companyId()), e));
    actors.validate(auth.tenantId(), d);
    if (d.nodes().stream().anyMatch(n -> n.type().equals("SCRIPT"))) {
      check(Long.parseLong(d.companyId()), "脚本发布");
      for (var n : d.nodes()) if (n.script() != null) scriptRunner.validate(n.script());
    }
    // 发布时检查各审批节点有候选人；进入节点时再次解析并保存人员快照，避免沿用过期角色成员。
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
    // 先锁公司业务绑定，再原子发布草稿及切换绑定；与引擎部署处于同一事务。
    long company = Long.parseLong(d.companyId());
    long version = Long.parseLong(d.versionId());
    bindings.ensure(auth.tenantId(), company, d.businessType());
    bindings.find(auth.tenantId(), company, d.businessType(), true);
    if (!versions.publish(auth.tenantId(), version, auth.userId(), xml, definition.getId())) {
      throw new IllegalArgumentException("流程版本已发布或已变化，请刷新后重试");
    }
    bindings.activate(auth.tenantId(), company, d.businessType(), version);
    return repository.definition(auth.tenantId(), id, null);
  }

  public Set<Long> candidates(WorkflowDefinition definition, WorkflowDefinition.Node node) {
    return actors.candidates(auth.tenantId(), definition, node);
  }

  @Transactional
  public void disable(long id, long rowVersion) {
    var t = templates.find(auth.tenantId(), id, true);
    check(t.getCompanyId(), "停用");
    if (t.getRowVersion() != rowVersion) throw new IllegalArgumentException("模板已变更，请刷新");
    bindings.disableTemplate(auth.tenantId(), id);
  }

  public List<WorkflowTrialStep> simulate(
      WorkflowDefinition definition, Map<String, Object> values) {
    check(Long.parseLong(definition.companyId()), "编辑");
    validator.validate(definition);
    if (definition.nodes().stream().anyMatch(n -> n.script() != null))
      check(Long.parseLong(definition.companyId()), "脚本试算");
    var transaction = new TransactionTemplate(transactions);
    transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
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
