package com.windasoft.eam.repository.workflow;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.windasoft.eam.common.workflow.WorkflowExpression.Expr;
import com.windasoft.eam.dto.system.PermissionScope;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class WorkflowRepository {
  private final JdbcTemplate db;

  public WorkflowRepository(JdbcTemplate db) {
    this.db = db;
  }

  public Map<String, Object> template(long tenant, long id, boolean lock) {
    var rows =
        db.queryForList(
            "SELECT * FROM wf_template WHERE tenant_id=? AND id=?" + (lock ? " FOR UPDATE" : ""),
            tenant,
            id);
    if (rows.isEmpty()) throw new IllegalArgumentException("流程模板不存在");
    return rows.getFirst();
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

  public Map<String, Object> list(
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
    args.add((page - 1) * size);
    var rows =
        db.queryForList(
            "SELECT t.id,t.name,t.company_id AS companyId,o.name AS companyName,t.business_type AS"
                + " businessType,t.row_version AS rowVersion,t.updated_at AS"
                + " updatedAt,EXISTS(SELECT 1 FROM wf_binding b JOIN wf_version v ON"
                + " v.tenant_id=b.tenant_id AND v.id=b.version_id WHERE b.tenant_id=t.tenant_id AND"
                + " b.company_id=t.company_id AND b.business_type=t.business_type AND"
                + " v.template_id=t.id AND b.enabled=1) AS active FROM wf_template t JOIN"
                + " eam_org_unit o ON o.tenant_id=t.tenant_id AND o.id=t.company_id"
                + where
                + " ORDER BY t.updated_at DESC,t.id DESC LIMIT ? OFFSET ?",
            args.toArray());
    return Map.of("data", rows, "total", total, "page", page, "size", size);
  }

  public List<Map<String, Object>> companies(long tenant, PermissionScope scope) {
    List<Object> args = new ArrayList<>(List.of(tenant));
    return db.queryForList(
        "SELECT id,name FROM eam_org_unit WHERE tenant_id=? AND deleted=0 AND status=1 AND org_type"
            + " IN ('公司','集团')"
            + scope(scope, args, "id")
            + " ORDER BY name,id",
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

  public Map<String, Object> approvalSetting(long tenant, long company) {
    var rows =
        db.queryForList(
            "SELECT requires_approval AS required,row_version AS rowVersion,(enabled=1 AND"
                + " version_id IS NOT NULL) AS templateAvailable FROM wf_binding WHERE tenant_id=?"
                + " AND company_id=? AND business_type='repair_report'",
            tenant,
            company);
    return rows.isEmpty()
        ? Map.of("required", false, "rowVersion", -1L, "templateAvailable", false)
        : rows.getFirst();
  }

  public void approvalSetting(long tenant, long company, boolean required, long version) {
    db.update(
        "INSERT IGNORE INTO wf_binding(tenant_id,company_id,business_type,requires_approval)"
            + " VALUES(?,?,'repair_report',0)",
        tenant,
        company);
    int changed =
        db.update(
            "UPDATE wf_binding SET requires_approval=?,row_version=row_version+1 WHERE tenant_id=?"
                + " AND company_id=? AND business_type='repair_report' AND row_version=?",
            required,
            tenant,
            company,
            version == -1 ? 0 : version);
    if (changed != 1) throw new IllegalArgumentException("审批设置已变化，请刷新后重试");
  }

  public List<Map<String, Object>> users(long tenant) {
    return db.queryForList(
        "SELECT id,display_name AS name,org_id FROM eam_sys_user WHERE tenant_id=? AND deleted=0"
            + " AND status=1 ORDER BY display_name,id",
        tenant);
  }

  public List<Map<String, Object>> roles(long tenant) {
    return db.queryForList(
        "SELECT id,name FROM eam_sys_role WHERE tenant_id=? AND deleted=0 AND status=1 ORDER BY"
            + " name,id",
        tenant);
  }

  public List<Long> roleUsers(long tenant, long role) {
    return db.queryForList(
        "SELECT u.id FROM eam_sys_user u JOIN eam_sys_user_role r ON r.tenant_id=u.tenant_id AND"
            + " r.user_id=u.id JOIN eam_sys_role s ON s.tenant_id=r.tenant_id AND s.id=r.role_id"
            + " WHERE u.tenant_id=? AND r.role_id=? AND u.deleted=0 AND u.status=1 AND s.deleted=0"
            + " AND s.status=1",
        Long.class,
        tenant,
        role);
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

  public long save(long tenant, long actor, WorkflowDefinition d, Map<String, Expr> expressions) {
    long id = d.id() == null ? IdWorker.getId() : Long.parseLong(d.id());
    if (d.id() == null)
      db.update(
          "INSERT INTO"
              + " wf_template(id,tenant_id,company_id,business_type,name,description,created_by,updated_by)"
              + " VALUES(?,?,?,?,?,?,?,?)",
          id,
          tenant,
          d.companyId(),
          d.businessType(),
          d.name(),
          d.description() == null ? "" : d.description(),
          actor,
          actor);
    else if (db.update(
            "UPDATE wf_template SET"
                + " name=?,description=?,row_version=row_version+1,updated_by=?,updated_at=NOW(3)"
                + " WHERE tenant_id=? AND id=? AND row_version=?",
            d.name(),
            d.description() == null ? "" : d.description(),
            actor,
            tenant,
            id,
            d.rowVersion())
        != 1) throw new IllegalArgumentException("模板已被修改，请刷新后重试");
    // Each save produces a complete relational draft version; published versions are never edited.
    int next =
        db.queryForObject(
            "SELECT COALESCE(MAX(version_no),0)+1 FROM wf_version WHERE tenant_id=? AND"
                + " template_id=?",
            Integer.class,
            tenant,
            id);
    long version = IdWorker.getId();
    db.update(
        "INSERT INTO wf_version(id,tenant_id,template_id,version_no,diagram_xml) VALUES(?,?,?,?,?)",
        version,
        tenant,
        id,
        next,
        d.diagramXml());
    int sort = 0;
    for (Node n : d.nodes()) {
      long node = IdWorker.getId();
      db.update(
          "INSERT INTO"
              + " wf_node(id,tenant_id,version_id,node_key,node_type,name,next_key,approval_mode,sort_no,parent_key)"
              + " VALUES(?,?,?,?,?,?,?,?,?,?)",
          node,
          tenant,
          version,
          n.key(),
          n.type(),
          n.name(),
          n.next(),
          n.approvalMode(),
          sort++,
          n.parentKey());
      if (n.script() != null)
        db.update(
            "INSERT INTO"
                + " wf_node_script(tenant_id,node_id,language,source_code,output_key,output_type)"
                + " VALUES(?,?,?,?,?,?)",
            tenant,
            node,
            n.script().language(),
            n.script().source(),
            n.script().outputKey(),
            n.script().outputType());
      if (n.users() != null)
        for (String user : new LinkedHashSet<>(n.users()))
          db.update("INSERT INTO wf_node_user VALUES(?,?,?)", tenant, node, Long.parseLong(user));
      if (n.roles() != null)
        for (String role : new LinkedHashSet<>(n.roles()))
          db.update("INSERT INTO wf_node_role VALUES(?,?,?)", tenant, node, Long.parseLong(role));
      if (n.branches() != null) {
        int order = 0;
        for (Branch b : n.branches()) {
          Long condition =
              b.fallback() || expressions.get(n.key() + ":" + b.key()) == null
                  ? null
                  : saveExpression(
                      tenant, version, null, 0, expressions.get(n.key() + ":" + b.key()));
          db.update(
              "INSERT INTO"
                  + " wf_branch(id,tenant_id,node_id,branch_key,name,sort_no,fallback,target_key,condition_id,expression_source)"
                  + " VALUES(?,?,?,?,?,?,?,?,?,?)",
              IdWorker.getId(),
              tenant,
              node,
              b.key(),
              b.name(),
              order++,
              b.fallback(),
              b.target(),
              condition,
              b.expression());
        }
      }
    }
    return id;
  }

  private long saveExpression(long tenant, long version, Long parent, int order, Expr e) {
    long id = IdWorker.getId();
    db.update(
        "INSERT INTO"
            + " wf_condition(id,tenant_id,version_id,parent_id,sort_no,operator,field_key,value_type,number_value,text_value,reference_value,boolean_value)"
            + " VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",
        id,
        tenant,
        version,
        parent,
        order,
        e.op(),
        e.field(),
        e.type().name(),
        e.type().name().equals("NUMBER") ? e.literal() : null,
        e.type().name().equals("TEXT") ? e.literal() : null,
        e.type().name().equals("REFERENCE") ? e.literal() : null,
        e.type().name().equals("BOOLEAN") ? e.literal() : null);
    for (int i = 0; i < e.children().size(); i++)
      saveExpression(tenant, version, id, i, e.children().get(i));
    return id;
  }

  public List<Map<String, Object>> versions(long tenant, long template) {
    return db.queryForList(
        "SELECT id,version_no AS versionNo,state,published_by AS publishedBy,published_at AS"
            + " publishedAt FROM wf_version WHERE tenant_id=? AND template_id=? ORDER BY version_no"
            + " DESC",
        tenant,
        template);
  }

  public WorkflowDefinition definition(long tenant, long template, Long selectedVersion) {
    var t = template(tenant, template, false);
    long version =
        selectedVersion == null
            ? db.queryForObject(
                "SELECT id FROM wf_version WHERE tenant_id=? AND template_id=? ORDER BY version_no"
                    + " DESC LIMIT 1",
                Long.class,
                tenant,
                template)
            : selectedVersion;
    var v =
        db.queryForList(
            "SELECT state,diagram_xml FROM wf_version WHERE tenant_id=? AND template_id=? AND id=?",
            tenant,
            template,
            version);
    if (v.isEmpty()) throw new IllegalArgumentException("流程版本不存在");
    List<Node> nodes = new ArrayList<>();
    for (var n :
        db.queryForList(
            "SELECT * FROM wf_node WHERE tenant_id=? AND version_id=? ORDER BY sort_no",
            tenant,
            version)) {
      Object node = n.get("id");
      var users =
          db
              .queryForList(
                  "SELECT user_id FROM wf_node_user WHERE tenant_id=? AND node_id=? ORDER BY"
                      + " user_id",
                  Long.class,
                  tenant,
                  node)
              .stream()
              .map(Object::toString)
              .toList();
      var roles =
          db
              .queryForList(
                  "SELECT role_id FROM wf_node_role WHERE tenant_id=? AND node_id=? ORDER BY"
                      + " role_id",
                  Long.class,
                  tenant,
                  node)
              .stream()
              .map(Object::toString)
              .toList();
      var branches =
          db
              .queryForList(
                  "SELECT * FROM wf_branch WHERE tenant_id=? AND node_id=? ORDER BY sort_no",
                  tenant,
                  node)
              .stream()
              .map(
                  b ->
                      new Branch(
                          s(b, "branch_key"),
                          s(b, "name"),
                          s(b, "target_key"),
                          ((Number) b.get("fallback")).intValue() == 1,
                          s(b, "expression_source")))
              .toList();
      nodes.add(
          new Node(
              s(n, "node_key"),
              s(n, "node_type"),
              s(n, "name"),
              s(n, "next_key"),
              s(n, "approval_mode"),
              users,
              roles,
              branches,
              s(n, "parent_key"),
              script(tenant, node)));
    }
    return new WorkflowDefinition(
        "" + template,
        ((Number) t.get("row_version")).longValue(),
        s(t, "name"),
        s(t, "business_type"),
        s(t, "company_id"),
        s(t, "description"),
        "" + version,
        s(v.getFirst(), "state"),
        nodes,
        s(v.getFirst(), "diagram_xml"));
  }

  private Script script(long tenant, Object node) {
    var rows =
        db.queryForList(
            "SELECT language,source_code,output_key,output_type FROM wf_node_script WHERE"
                + " tenant_id=? AND node_id=?",
            tenant,
            node);
    if (rows.isEmpty()) return null;
    var row = rows.getFirst();
    return new Script(
        s(row, "language"), s(row, "source_code"), s(row, "output_key"), s(row, "output_type"));
  }

  public void publish(
      long tenant,
      long company,
      String business,
      long version,
      long actor,
      String xml,
      String engineId) {
    db.update(
        "INSERT IGNORE INTO wf_binding(tenant_id,company_id,business_type,requires_approval)"
            + " VALUES(?,?,?,?)",
        tenant,
        company,
        business,
        business.equals("repair_report") ? 0 : 1);
    db.queryForList(
        "SELECT version_id FROM wf_binding WHERE tenant_id=? AND company_id=? AND business_type=?"
            + " FOR UPDATE",
        tenant,
        company,
        business);
    if (db.update(
            "UPDATE wf_version SET"
                + " state='PUBLISHED',execution_version=2,bpmn_xml=?,engine_definition_id=?,published_by=?,published_at=NOW(3)"
                + " WHERE tenant_id=? AND id=? AND state='DRAFT'",
            xml,
            engineId,
            actor,
            tenant,
            version)
        != 1) throw new IllegalArgumentException("此版本已经发布，请先保存新草稿");
    db.update(
        "UPDATE wf_binding SET version_id=?,enabled=1,row_version=row_version+1 WHERE tenant_id=?"
            + " AND company_id=? AND business_type=?",
        version,
        tenant,
        company,
        business);
  }

  public void disable(long tenant, long template) {
    db.update(
        "UPDATE wf_binding b JOIN wf_version v ON v.tenant_id=b.tenant_id AND v.id=b.version_id SET"
            + " b.enabled=0,b.row_version=b.row_version+1 WHERE b.tenant_id=? AND v.template_id=?",
        tenant,
        template);
  }

  private static String s(Map<String, Object> row, String key) {
    return row.get(key) == null ? null : row.get(key).toString();
  }

  public long templateForVersion(long tenant, long version) {
    return db.queryForObject(
        "SELECT template_id FROM wf_version WHERE tenant_id=? AND id=?",
        Long.class,
        tenant,
        version);
  }

  public Map<String, Expr> expressions(long tenant, long version) {
    Map<Long, Map<String, Object>> rows = new HashMap<>();
    Map<Long, List<Map<String, Object>>> children = new HashMap<>();
    for (var row :
        db.queryForList(
            "SELECT * FROM wf_condition WHERE tenant_id=? AND version_id=? ORDER BY sort_no,id",
            tenant,
            version)) {
      rows.put(((Number) row.get("id")).longValue(), row);
      if (row.get("parent_id") != null)
        children
            .computeIfAbsent(((Number) row.get("parent_id")).longValue(), k -> new ArrayList<>())
            .add(row);
    }
    Map<String, Expr> result = new HashMap<>();
    for (var branch :
        db.queryForList(
            "SELECT n.node_key,b.branch_key,b.condition_id FROM wf_branch b JOIN wf_node n ON"
                + " n.tenant_id=b.tenant_id AND n.id=b.node_id WHERE n.tenant_id=? AND"
                + " n.version_id=? AND b.fallback=0 AND b.condition_id IS NOT NULL",
            tenant,
            version))
      result.put(
          branch.get("node_key") + ":" + branch.get("branch_key"),
          expression(((Number) branch.get("condition_id")).longValue(), rows, children, 0));
    return result;
  }

  private Expr expression(
      long id,
      Map<Long, Map<String, Object>> rows,
      Map<Long, List<Map<String, Object>>> children,
      int depth) {
    if (depth > 64 || !rows.containsKey(id)) throw new IllegalStateException("流程条件关系损坏");
    var row = rows.get(id);
    var type =
        com.windasoft.eam.common.workflow.WorkflowExpression.Type.valueOf(s(row, "value_type"));
    Object literal =
        switch (type) {
          case NUMBER -> row.get("number_value");
          case TEXT -> row.get("text_value");
          case REFERENCE -> row.get("reference_value");
          case BOOLEAN ->
              row.get("boolean_value") == null
                  ? null
                  : ((Number) row.get("boolean_value")).intValue() != 0;
          default -> null;
        };
    return new Expr(
        s(row, "operator"),
        s(row, "field_key"),
        literal,
        type,
        children.getOrDefault(id, List.of()).stream()
            .map(c -> expression(((Number) c.get("id")).longValue(), rows, children, depth + 1))
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

  public Object choices(
      long tenant, String kind, Set<Long> organizations, String q, int page, int size) {
    return choices(tenant, kind, organizations, q, page, size, List.of());
  }

  public Object choices(
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
    args.add((page - 1) * size);
    return Map.of(
        "data",
        db.queryForList(
            "SELECT id,"
                + (kind.equals("materials") ? "code," : "")
                + label
                + " AS name FROM "
                + table
                + where
                + " ORDER BY "
                + label
                + ",id LIMIT ? OFFSET ?",
            args.toArray()),
        "page",
        Map.of("total", total));
  }
}
