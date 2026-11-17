package com.windasoft.eam.repository.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression.Expr;
import com.windasoft.eam.common.workflow.WorkflowExpression.Type;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Branch;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Node;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Script;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Choice;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.ChoicePage;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.Company;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.TemplatePage;
import com.windasoft.eam.dto.workflow.WorkflowTemplateViews.TemplateRow;
import com.windasoft.eam.entity.workflow.WorkflowBranch;
import com.windasoft.eam.entity.workflow.WorkflowCondition;
import com.windasoft.eam.entity.workflow.WorkflowNodeRole;
import com.windasoft.eam.entity.workflow.WorkflowNodeScript;
import com.windasoft.eam.entity.workflow.WorkflowNodeUser;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRepository {
  private final JdbcTemplate db;
  private final WorkflowTemplateRepository templates;
  private final WorkflowVersionRepository versions;
  private final WorkflowNodeRepository nodes;
  private final WorkflowConditionRepository conditions;
  private final WorkflowNodeUserRepository users;
  private final WorkflowNodeRoleRepository roles;
  private final WorkflowBranchRepository branches;
  private final WorkflowNodeScriptRepository scripts;

  public WorkflowRepository(
      JdbcTemplate db,
      WorkflowTemplateRepository templates,
      WorkflowVersionRepository versions,
      WorkflowNodeRepository nodes,
      WorkflowNodeUserRepository users,
      WorkflowNodeRoleRepository roles,
      WorkflowBranchRepository branches,
      WorkflowNodeScriptRepository scripts,
      WorkflowConditionRepository conditions) {
    this.db = db;
    this.templates = templates;
    this.versions = versions;
    this.nodes = nodes;
    this.users = users;
    this.roles = roles;
    this.branches = branches;
    this.scripts = scripts;
    this.conditions = conditions;
  }

  private String scope(PermissionScope scope, List<Object> args, String field) {
    if (scope.all()) return "";
    if (scope.companyIds().isEmpty()) return " AND 1=0";
    args.addAll(scope.companyIds());
    return " AND "
        + field
        + " IN ("
        + String.join(",", Collections.nCopies(scope.companyIds().size(), "?"))
        + ")";
  }

  public TemplatePage list(
      long tenant, PermissionScope scope, String q, Long company, int page, int size) {
    List<Object> args = new ArrayList<>(List.of(tenant, "%" + q + "%"));
    String where = " WHERE t.tenant_id=? AND t.name LIKE ?" + scope(scope, args, "t.company_id");
    if (company != null) {
      where += " AND t.company_id=?";
      args.add(company);
    }
    long total =
        db.queryForObject("SELECT COUNT(*) FROM wf_template t" + where, Long.class, args.toArray());
    args.add(size);
    args.add(((long) page - 1) * size);
    var rows =
        db.query(
            "SELECT t.id,t.name,t.company_id AS companyId,o.name AS companyName,t.business_type AS"
                + " businessType,t.row_version AS rowVersion,t.updated_at AS"
                + " updatedAt,EXISTS(SELECT 1 FROM wf_binding b JOIN wf_version v ON"
                + " v.tenant_id=b.tenant_id AND v.id=b.version_id WHERE b.tenant_id=t.tenant_id AND"
                + " b.company_id=t.company_id AND b.business_type=t.business_type AND"
                + " v.template_id=t.id AND b.enabled=1) AS active FROM wf_template t JOIN"
                + " eam_org_unit o ON o.tenant_id=t.tenant_id AND o.id=t.company_id"
                + where
                + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",
            (r, n) ->
                new TemplateRow(
                    r.getString("id"),
                    r.getString("name"),
                    r.getString("companyId"),
                    r.getString("companyName"),
                    r.getString("businessType"),
                    r.getLong("rowVersion"),
                    r.getObject("updatedAt", LocalDateTime.class),
                    r.getInt("active")),
            args.toArray());
    return new TemplatePage(rows, total, page, size);
  }

  public List<Company> companies(long tenant, PermissionScope scope) {
    List<Object> args = new ArrayList<>(List.of(tenant));
    return db.query(
        "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND deleted=0 AND status=1 AND org_type"
            + " IN ('公司','集团')"
            + scope(scope, args, "id")
            + " ORDER BY name,id",
        (r, n) -> new Company(r.getString("id"), r.getString("name")),
        args.toArray());
  }

  public boolean companyExists(long tenant, long id) {
    return db.queryForObject(
            "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0 AND"
                + " status=1 AND org_type IN ('公司','集团')",
            Integer.class,
            tenant,
            id)
        == 1;
  }

  public Long userOrg(long tenant, long user) {
    var rows =
        db.queryForList(
            "SELECT org_id FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND status=1",
            tenant,
            user);
    if (rows.isEmpty()) throw new IllegalArgumentException("审批人员不存在或已停用");
    return rows.getFirst().get("org_id") == null
        ? null
        : ((Number) rows.getFirst().get("org_id")).longValue();
  }

  public WorkflowDefinition definition(long tenant, long template, Long selectedVersion) {
    var t = templates.find(tenant, template, false);
    var v =
        selectedVersion == null
            ? versions.latest(tenant, template)
            : versions.find(tenant, selectedVersion);
    if (v.getTemplateId() != template) {
      throw new IllegalArgumentException("流程版本不存在");
    }
    long version = v.getId();
    // 版本图一次批量加载各类关联；节点增加不再额外触发四次查询。
    var nodeRows = nodes.list(tenant, version);
    var ids = nodeRows.stream().map(n -> n.getId()).toList();
    var nodeUsers =
        users.list(tenant, ids).stream()
            .collect(
                Collectors.groupingBy(
                    WorkflowNodeUser::getNodeId,
                    Collectors.mapping(r -> r.getUserId().toString(), Collectors.toList())));
    var nodeRoles =
        roles.list(tenant, ids).stream()
            .collect(
                Collectors.groupingBy(
                    WorkflowNodeRole::getNodeId,
                    Collectors.mapping(r -> r.getRoleId().toString(), Collectors.toList())));
    var nodeBranches =
        branches.list(tenant, ids).stream()
            .collect(
                Collectors.groupingBy(
                    WorkflowBranch::getNodeId,
                    Collectors.mapping(
                        r ->
                            new Branch(
                                r.getBranchKey(),
                                r.getName(),
                                r.getTargetKey(),
                                Boolean.TRUE.equals(r.getFallback()),
                                r.getExpressionSource()),
                        Collectors.toList())));
    var nodeScripts =
        scripts.list(tenant, ids).stream()
            .collect(
                Collectors.toMap(
                    WorkflowNodeScript::getNodeId,
                    r ->
                        new Script(
                            r.getLanguage(),
                            r.getSourceCode(),
                            r.getOutputKey(),
                            r.getOutputType())));
    List<Node> graph =
        nodeRows.stream()
            .map(
                n ->
                    new Node(
                        n.getNodeKey(),
                        n.getNodeType(),
                        n.getName(),
                        n.getNextKey(),
                        n.getApprovalMode(),
                        nodeUsers.getOrDefault(n.getId(), List.of()),
                        nodeRoles.getOrDefault(n.getId(), List.of()),
                        nodeBranches.getOrDefault(n.getId(), List.of()),
                        n.getParentKey(),
                        nodeScripts.get(n.getId())))
            .toList();
    return new WorkflowDefinition(
        Long.toString(template),
        t.getRowVersion(),
        t.getName(),
        t.getBusinessType(),
        t.getCompanyId().toString(),
        t.getDescription(),
        Long.toString(version),
        v.getState(),
        graph,
        v.getDiagramXml());
  }

  public long templateForVersion(long tenant, long version) {
    return versions.find(tenant, version).getTemplateId();
  }

  /** 条件按版本一次读取；关联键使用真实ID，不再以字符串Map推断列类型。 */
  public Map<String, Expr> expressions(long tenant, long version) {
    var rows = conditions.list(tenant, version);
    var indexed = rows.stream().collect(Collectors.toMap(WorkflowCondition::getId, r -> r));
    var children =
        rows.stream()
            .filter(r -> r.getParentId() != null)
            .collect(Collectors.groupingBy(WorkflowCondition::getParentId));
    var nodeKeys =
        nodes.list(tenant, version).stream()
            .collect(Collectors.toMap(n -> n.getId(), n -> n.getNodeKey()));
    Map<String, Expr> result = new HashMap<>();
    for (var branch : branches.list(tenant, new ArrayList<>(nodeKeys.keySet()))) {
      if (!Boolean.TRUE.equals(branch.getFallback()) && branch.getConditionId() != null) {
        result.put(
            nodeKeys.get(branch.getNodeId()) + ":" + branch.getBranchKey(),
            expression(branch.getConditionId(), indexed, children, 0));
      }
    }
    return result;
  }

  private Expr expression(
      long id,
      Map<Long, WorkflowCondition> rows,
      Map<Long, List<WorkflowCondition>> children,
      int depth) {
    if (depth > 64 || !rows.containsKey(id)) {
      throw new IllegalStateException("流程条件关系损坏");
    }
    var row = rows.get(id);
    var type = Type.valueOf(row.getValueType());
    // literal 是表达式协议允许的标量联合类型；固定条件结构仍使用显式模型。
    Object literal =
        switch (type) {
          case NUMBER -> row.getNumberValue();
          case TEXT -> row.getTextValue();
          case REFERENCE -> row.getReferenceValue();
          case BOOLEAN -> row.getBooleanValue();
          default -> null;
        };
    return new Expr(
        row.getOperator(),
        row.getFieldKey(),
        literal,
        type,
        children.getOrDefault(id, List.of()).stream()
            .map(child -> expression(child.getId(), rows, children, depth + 1))
            .toList());
  }

  public boolean referenceExists(long tenant, long company, String field, long value) {
    String sql =
        switch (field) {
          case "applicantId" ->
              "SELECT COUNT(*) FROM eam_sys_user WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " status=1";
          case "applicantDepartmentId", "departmentId" ->
              "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " status=1 AND org_type='部门'";
          case "companyId" ->
              "SELECT COUNT(*) FROM eam_org_unit WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " status=1 AND org_type IN ('公司','集团')";
          case "warehouseId" ->
              "SELECT COUNT(*) FROM inv_warehouse WHERE tenant_id=? AND id=? AND enabled=1 AND"
                  + " company_id="
                  + company;
          case "lines.materialId" ->
              "SELECT COUNT(*) FROM inv_material WHERE tenant_id=? AND id=? AND enabled=1";
          case "equipmentId" ->
              "SELECT COUNT(*) FROM eam_equipment WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " company_org_id="
                  + company;
          case "categoryId", "faultTypeId" ->
              "SELECT COUNT(*) FROM eam_base_item WHERE tenant_id=? AND id=? AND deleted=0 AND"
                  + " status=1 AND item_type='"
                  + (field.equals("categoryId") ? "CATEGORY" : "FAULT_TYPE")
                  + "'";
          default -> throw new IllegalArgumentException("不支持的关联变量");
        };
    return db.queryForObject(sql, Integer.class, tenant, value) == 1;
  }

  public ChoicePage choices(
      long tenant, String kind, Set<Long> organizations, String q, int page, int size) {
    return choices(tenant, kind, organizations, q, page, size, List.of());
  }

  public ChoicePage choices(
      long tenant,
      String kind,
      Set<Long> organizations,
      String q,
      int page,
      int size,
      List<Long> ids) {
    List<Object> args = new ArrayList<>(List.of(tenant, "%" + q + "%"));
    String table, where, label;
    switch (kind) {
      case "users" -> {
        table = "eam_sys_user";
        label = "display_name";
        where = " AND deleted=0 AND status=1";
      }
      case "roles" -> {
        table = "eam_sys_role";
        label = "name";
        where = " AND deleted=0 AND status=1";
      }
      case "departments" -> {
        table = "eam_org_unit";
        label = "name";
        where = " AND deleted=0 AND status=1 AND org_type='部门'";
      }
      case "companies" -> {
        table = "eam_org_unit";
        label = "name";
        where = " AND deleted=0 AND status=1 AND org_type IN ('公司','集团')";
      }
      case "materials" -> {
        table = "inv_material";
        label = "name";
        where = " AND enabled=1";
      }
      case "warehouses" -> {
        table = "inv_warehouse";
        label = "name";
        where = " AND enabled=1";
      }
      case "equipment" -> {
        table = "eam_equipment";
        label = "name";
        where = " AND deleted=0";
      }
      case "categories", "faultTypes" -> {
        table = "eam_base_item";
        label = "name";
        where =
            " AND deleted=0 AND status=1 AND item_type='"
                + (kind.equals("categories") ? "CATEGORY" : "FAULT_TYPE")
                + "'";
      }
      default -> throw new IllegalArgumentException("不支持的关联对象类型");
    }
    where = " WHERE tenant_id=? AND " + label + " LIKE ?" + where;
    if (Set.of("users", "departments", "warehouses", "companies", "equipment").contains(kind)) {
      String field =
          kind.equals("users")
              ? "org_id"
              : kind.equals("equipment")
                  ? "department_org_id"
                  : kind.equals("warehouses") ? "company_id" : "id";
      if (organizations.isEmpty()) where += " AND 1=0";
      else {
        where +=
            " AND "
                + field
                + " IN ("
                + String.join(",", Collections.nCopies(organizations.size(), "?"))
                + ")";
        args.addAll(organizations);
      }
    }
    if (!ids.isEmpty()) {
      where += " AND id IN (" + String.join(",", Collections.nCopies(ids.size(), "?")) + ")";
      args.addAll(ids);
    }
    long total =
        db.queryForObject("SELECT COUNT(*) FROM " + table + where, Long.class, args.toArray());
    args.add(size);
    args.add(((long) page - 1) * size);
    return new ChoicePage(
        db.query(
            "SELECT id,"
                + (kind.equals("materials") ? "code," : "")
                + label
                + " AS name FROM "
                + table
                + where
                + " ORDER BY "
                + label
                + ",id LIMIT ? OFFSET ?",
            (r, n) ->
                new Choice(
                    r.getString("id"),
                    r.getString("name"),
                    kind.equals("materials") ? r.getString("code") : null),
            args.toArray()),
        new WorkflowTemplateViews.Count(total));
  }
}
