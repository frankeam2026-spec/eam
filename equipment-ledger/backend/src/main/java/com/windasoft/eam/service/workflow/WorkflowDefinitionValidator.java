package com.windasoft.eam.service.workflow;

import com.windasoft.eam.common.workflow.WorkflowExpression;
import com.windasoft.eam.dto.workflow.WorkflowDefinition;
import com.windasoft.eam.dto.workflow.WorkflowDefinition.Node;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class WorkflowDefinitionValidator {
  private final WorkflowVariables variables;

  public WorkflowDefinitionValidator(WorkflowVariables variables) {
    this.variables = variables;
  }

  public Map<String, WorkflowExpression.Expr> validate(WorkflowDefinition d) {
    return validate(d, true);
  }

  public static boolean gateway(Node n) {
    return Set.of("BRANCH", "PARALLEL", "INCLUSIVE").contains(n.type());
  }

  public static List<String> targets(Node n) {
    return gateway(n)
        ? n.branches().stream().map(b -> b.target()).toList()
        : n.next() == null ? List.of() : List.of(n.next());
  }

  public Map<String, WorkflowExpression.Expr> validate(WorkflowDefinition d, boolean publishing) {
    if (d.diagramXml() != null
        && (d.diagramXml().length() > 500000
            || d.diagramXml().contains("<!DOCTYPE")
            || d.diagramXml().contains("<!ENTITY"))) fail("画布文件过大或含不支持的XML声明");
    if (d.name() == null || d.name().isBlank() || d.name().length() > 100) fail("模板名称必填且不超过100字");
    if (d.description() != null && d.description().length() > 1000) fail("说明不超过1000字");
    if (d.nodes() == null || d.nodes().size() < (publishing ? 3 : 1) || d.nodes().size() > 100)
      fail("流程节点数量无效，发布时至少3个节点，最多100个节点");
    var fields = variables.catalog(d);
    Map<String, Node> nodes = new LinkedHashMap<>();
    Map<String, WorkflowExpression.Expr> expressions = new LinkedHashMap<>();
    for (var n : d.nodes()) {
      if (n == null || n.type() == null) fail("节点不能为空");
      if (n.key() == null
          || !n.key().matches("[A-Za-z][A-Za-z0-9_]{0,49}")
          || nodes.put(n.key(), n) != null) fail("节点标识不合法或重复");
      if (n.name() == null || n.name().isBlank() || n.name().length() > 100) fail("节点名称必填且不超过100字");
      if (!Set.of(
              "START", "END", "APPROVAL", "BRANCH", "PARALLEL", "INCLUSIVE", "SUBPROCESS", "SCRIPT")
          .contains(n.type())) fail("不支持的节点类型");
      if (n.type().equals("APPROVAL")) {
        if (n.approvalMode() == null || !Set.of("ANY", "ALL").contains(n.approvalMode()))
          fail("请选择审批方式");
        if (publishing
            && (n.users() == null || n.users().isEmpty())
            && (n.roles() == null || n.roles().isEmpty())) fail("审批节点必须配置人员或角色");
        if (n.users() != null && n.users().size() > 100
            || n.roles() != null && n.roles().size() > 100) fail("节点人员或角色不能超过100项");
      }
      if (n.type().equals("SCRIPT")) {
        if (n.script() == null) fail("脚本节点须配置脚本和结果变量");
        if (n.script().source() == null
            || n.script().source().length() > 32000
            || publishing && n.script().source().isBlank()) fail("脚本必填且不超过32000字符");
        if (n.script().source().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 60000)
          fail("脚本源码不能超过60000字节");
      } else if (n.script() != null) fail("仅脚本节点可以配置脚本");
      if (gateway(n)) {
        if (n.branches() == null
            || publishing && n.branches().isEmpty()
            || n.branches().size() > 20) fail("网关需要1至20条出边");
        Set<String> keys = new HashSet<>();
        for (int i = 0; i < n.branches().size(); i++) {
          var b = n.branches().get(i);
          if (b == null || b.name() == null || b.name().isBlank() || b.name().length() > 100)
            fail("分支名称必填且不超过100字");
          if (b.key() == null
              || !b.key().matches("[A-Za-z][A-Za-z0-9_]{0,49}")
              || !keys.add(b.key())) fail("分支标识不合法或重复");
          if (n.type().equals("PARALLEL")) {
            if (b.fallback() || b.expression() != null && !b.expression().isBlank())
              fail("并行出边全部执行，不配置条件或其他情况");
          } else if (n.branches().size() > 1) {
            if (publishing && b.fallback() != (i == n.branches().size() - 1)
                || !publishing && b.fallback() && i != n.branches().size() - 1)
              fail("必须且仅允许最后一条为其他情况");
            if (!b.fallback()
                && (publishing || b.expression() != null && !b.expression().isBlank()))
              expressions.put(
                  n.key() + ":" + b.key(), WorkflowExpression.parse(b.expression(), fields));
          }
        }
      } else if (n.branches() != null && !n.branches().isEmpty()) fail("仅网关可以配置分支出边");
      if (n.type().equals("END") && n.next() != null) fail("结束节点不可配置后续节点");
    }
    for (var n : nodes.values()) {
      Set<String> ancestors = new HashSet<>();
      Node parent = n;
      while (parent.parentKey() != null && !parent.parentKey().isBlank()) {
        if (!ancestors.add(parent.parentKey()) || ancestors.size() > 8) fail("子流程嵌套循环或超过8层");
        parent = nodes.get(parent.parentKey());
        if (parent == null || !parent.type().equals("SUBPROCESS")) fail("子流程容器不存在");
      }
      for (String target : targets(n)) {
        var next = nodes.get(target);
        if (next == null) fail("节点连接无效：" + target);
        if (!Objects.equals(container(n), container(next))) fail("不能跨子流程边界连接，请连接子流程本身");
        if (next.type().equals("START")) fail("开始节点不能有入边");
      }
      if (publishing && !n.type().equals("END") && targets(n).isEmpty())
        fail("节点缺少后续连接：" + n.name());
    }
    if (!publishing) return expressions;
    Set<String> visited = new HashSet<>();
    Set<String> containers = new LinkedHashSet<>();
    containers.add("");
    nodes.values().stream()
        .filter(n -> n.type().equals("SUBPROCESS"))
        .forEach(n -> containers.add(n.key()));
    for (String container : containers) {
      var members = nodes.values().stream().filter(n -> container(n).equals(container)).toList();
      var starts = members.stream().filter(n -> n.type().equals("START")).toList();
      if (starts.size() != 1 || members.stream().noneMatch(n -> n.type().equals("END")))
        fail("流程及每个子流程须有一个开始和至少一个结束节点");
      visit(starts.getFirst().key(), nodes, new HashSet<>(), visited);
    }
    if (visited.size() != nodes.size()) fail("存在无法到达的节点");
    var start =
        nodes.values().stream()
            .filter(n -> n.type().equals("START") && container(n).isEmpty())
            .findFirst()
            .orElseThrow();
    if (publishing && !guaranteesApproval(start.key(), nodes, new HashMap<>()))
      fail("每条完整路径必须经过审批节点，请检查条件分支和后续节点");
    return expressions;
  }

  public static String container(Node n) {
    return n.parentKey() == null ? "" : n.parentKey();
  }

  private boolean guaranteesApproval(
      String key, Map<String, Node> nodes, Map<String, Boolean> memo) {
    if (memo.containsKey(key)) return memo.get(key);
    var n = nodes.get(key);
    boolean result;
    if (n.type().equals("APPROVAL")) result = true;
    else if (n.type().equals("END")) result = false;
    else if (n.type().equals("SUBPROCESS")) {
      var start =
          nodes.values().stream()
              .filter(x -> x.type().equals("START") && container(x).equals(key))
              .findFirst()
              .orElseThrow();
      result =
          guaranteesApproval(start.key(), nodes, memo) || guaranteesApproval(n.next(), nodes, memo);
    } else if (n.type().equals("PARALLEL"))
      result = n.branches().stream().anyMatch(b -> guaranteesApproval(b.target(), nodes, memo));
    else if (gateway(n))
      result = n.branches().stream().allMatch(b -> guaranteesApproval(b.target(), nodes, memo));
    else result = guaranteesApproval(n.next(), nodes, memo);
    memo.put(key, result);
    return result;
  }

  private void visit(String key, Map<String, Node> nodes, Set<String> path, Set<String> visited) {
    if (!path.add(key)) fail("流程中存在循环连接，请先使用有明确结束边界的流程");
    if (!visited.contains(key)) {
      for (String target : targets(nodes.get(key))) visit(target, nodes, path, visited);
      visited.add(key);
    }
    path.remove(key);
  }

  private static void fail(String message) {
    throw new IllegalArgumentException(message);
  }
}
