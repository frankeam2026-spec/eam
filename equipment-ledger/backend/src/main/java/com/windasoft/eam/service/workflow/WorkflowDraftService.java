package com.windasoft.eam.service.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.workflow.WorkflowExpression.Expr;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.entity.workflow.WorkflowBranch;
import com.windasoft.eam.entity.workflow.WorkflowCondition;
import com.windasoft.eam.entity.workflow.WorkflowNode;
import com.windasoft.eam.entity.workflow.WorkflowNodeRole;
import com.windasoft.eam.entity.workflow.WorkflowNodeScript;
import com.windasoft.eam.entity.workflow.WorkflowNodeUser;
import com.windasoft.eam.entity.workflow.WorkflowTemplate;
import com.windasoft.eam.entity.workflow.WorkflowVersion;
import com.windasoft.eam.repository.workflow.WorkflowBranchRepository;
import com.windasoft.eam.repository.workflow.WorkflowConditionRepository;
import com.windasoft.eam.repository.workflow.WorkflowNodeRepository;
import com.windasoft.eam.repository.workflow.WorkflowNodeRoleRepository;
import com.windasoft.eam.repository.workflow.WorkflowNodeScriptRepository;
import com.windasoft.eam.repository.workflow.WorkflowNodeUserRepository;
import com.windasoft.eam.repository.workflow.WorkflowTemplateRepository;
import com.windasoft.eam.repository.workflow.WorkflowVersionRepository;
import java.math.BigDecimal;
import java.util.LinkedHashSet;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 保存完整草稿图；权限、结构校验和模板锁由模板用例取得，本服务只编排多表落库。 */
@Service
@RequiredArgsConstructor
public class WorkflowDraftService {
  private final WorkflowTemplateRepository templates;
  private final WorkflowVersionRepository versions;
  private final WorkflowNodeRepository nodes;
  private final WorkflowConditionRepository conditions;
  private final WorkflowBranchRepository branches;
  private final WorkflowNodeUserRepository users;
  private final WorkflowNodeRoleRepository roles;
  private final WorkflowNodeScriptRepository scripts;

  /** 必须加入模板保存事务；已发布图不覆盖，每次保存创建完整的新草稿版本。 */
  @Transactional(propagation = Propagation.MANDATORY)
  public long save(
      long tenant, long actor, WorkflowDefinition definition, Map<String, Expr> expressions) {
    long id = definition.id() == null ? IdWorker.getId() : Long.parseLong(definition.id());
    String description = definition.description() == null ? "" : definition.description();
    if (definition.id() == null) {
      var row = new WorkflowTemplate();
      row.setId(id);
      row.setTenantId(tenant);
      row.setCompanyId(Long.valueOf(definition.companyId()));
      row.setBusinessType(definition.businessType());
      row.setName(definition.name());
      row.setDescription(description);
      row.setCreatedBy(actor);
      row.setUpdatedBy(actor);
      templates.insert(row);
    } else if (!templates.update(
        tenant, id, definition.rowVersion(), definition.name(), description, actor)) {
      throw new IllegalArgumentException("模板已被修改，请刷新后重试");
    }
    long version = IdWorker.getId();
    var header = new WorkflowVersion();
    header.setId(version);
    header.setTenantId(tenant);
    header.setTemplateId(id);
    header.setVersionNo(versions.nextNumber(tenant, id));
    header.setDiagramXml(definition.diagramXml());
    versions.insert(header);
    int order = 0;
    for (var node : definition.nodes()) {
      long nodeId = IdWorker.getId();
      var row = new WorkflowNode();
      row.setId(nodeId);
      row.setTenantId(tenant);
      row.setVersionId(version);
      row.setNodeKey(node.key());
      row.setNodeType(node.type());
      row.setName(node.name());
      row.setNextKey(node.next());
      row.setApprovalMode(node.approvalMode());
      row.setSortNo(order++);
      row.setParentKey(node.parentKey());
      nodes.insert(row);
      if (node.script() != null) {
        var script = new WorkflowNodeScript();
        script.setTenantId(tenant);
        script.setNodeId(nodeId);
        script.setLanguage(node.script().language());
        script.setSourceCode(node.script().source());
        script.setOutputKey(node.script().outputKey());
        script.setOutputType(node.script().outputType());
        scripts.insert(script);
      }
      if (node.users() != null) {
        for (String user : new LinkedHashSet<>(node.users())) {
          var link = new WorkflowNodeUser();
          link.setTenantId(tenant);
          link.setNodeId(nodeId);
          link.setUserId(Long.valueOf(user));
          users.insert(link);
        }
      }
      if (node.roles() != null) {
        for (String role : new LinkedHashSet<>(node.roles())) {
          var link = new WorkflowNodeRole();
          link.setTenantId(tenant);
          link.setNodeId(nodeId);
          link.setRoleId(Long.valueOf(role));
          roles.insert(link);
        }
      }
      if (node.branches() != null) {
        int branchOrder = 0;
        for (var branch : node.branches()) {
          Expr expression = expressions.get(node.key() + ":" + branch.key());
          Long condition =
              branch.fallback() || expression == null
                  ? null
                  : saveExpression(tenant, version, null, 0, expression);
          var branchRow = new WorkflowBranch();
          branchRow.setId(IdWorker.getId());
          branchRow.setTenantId(tenant);
          branchRow.setNodeId(nodeId);
          branchRow.setBranchKey(branch.key());
          branchRow.setName(branch.name());
          branchRow.setSortNo(branchOrder++);
          branchRow.setFallback(branch.fallback());
          branchRow.setTargetKey(branch.target());
          branchRow.setConditionId(condition);
          branchRow.setExpressionSource(branch.expression());
          branches.insert(branchRow);
        }
      }
    }
    return id;
  }

  /** 父条件先写入，满足真实外键；字面值按已校验类型落入对应列，空值不补零。 */
  private long saveExpression(long tenant, long version, Long parent, int order, Expr expression) {
    long id = IdWorker.getId();
    var row = new WorkflowCondition();
    row.setId(id);
    row.setTenantId(tenant);
    row.setVersionId(version);
    row.setParentId(parent);
    row.setSortNo(order);
    row.setOperator(expression.op());
    row.setFieldKey(expression.field());
    row.setValueType(expression.type().name());
    switch (expression.type()) {
      case NUMBER -> row.setNumberValue((BigDecimal) expression.literal());
      case TEXT -> row.setTextValue((String) expression.literal());
      case REFERENCE -> row.setReferenceValue((Long) expression.literal());
      case BOOLEAN -> row.setBooleanValue((Boolean) expression.literal());
      default -> {}
    }
    conditions.insert(row);
    for (int i = 0; i < expression.children().size(); i++) {
      saveExpression(tenant, version, id, i, expression.children().get(i));
    }
    return id;
  }
}
