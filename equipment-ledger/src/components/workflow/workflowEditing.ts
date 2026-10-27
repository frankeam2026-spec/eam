import type { WorkflowDefinition, WorkflowNode, WorkflowVariable } from '../../workflow';
import { gateway } from './workflowDiagram';

export type WorkflowIssue = { node: string; field: string; message: string; branch?: number };
export function inspectWorkflow(d: WorkflowDefinition): WorkflowIssue[] {
  const issues: WorkflowIssue[] = [],
    nodes = new Map(d.nodes.map((n) => [n.key, n]));
  const add = (node: WorkflowNode, field: string, message: string, branch?: number) =>
    issues.push({ node: node.key, field, message, branch });
  const outputs = new Set<string>();
  for (const n of d.nodes) {
    if (!n.name.trim()) add(n, 'name', '请输入节点名称');
    if (n.type === 'APPROVAL' && !n.users.length && !n.roles.length)
      add(n, 'actors', '请选择审批人员或审批角色');
    if (n.type === 'SCRIPT') {
      if (!n.script?.source.trim()) add(n, 'script', '请填写计算脚本');
      const key = n.script?.outputKey || '';
      if (!/^computed_[A-Za-z][A-Za-z0-9_]*$/.test(key))
        add(n, 'script', '结果变量须以computed_开头，后接字母、数字或下划线');
      else if (outputs.has(key)) add(n, 'script', '结果变量不能重复');
      outputs.add(key);
    }
    if (gateway(n)) {
      if (!n.branches.length) add(n, 'branches', '请添加执行路径');
      if (
        n.type !== 'PARALLEL' &&
        n.branches.length > 1 &&
        (n.branches.filter((b) => b.fallback).length !== 1 || !n.branches.at(-1)?.fallback)
      )
        add(n, 'branches', '请将唯一的“其他情况”放在最后');
      n.branches.forEach((b, i) => {
        if (
          !nodes.has(b.target) ||
          (nodes.get(b.target)?.parentKey !== n.parentKey &&
            (nodes.get(b.target)?.parentKey || '') !== (n.parentKey || ''))
        )
          add(n, 'branches', '请选择同一流程内的后续节点', i);
        if (n.type !== 'PARALLEL' && n.branches.length > 1 && !b.fallback && !b.expression.trim())
          add(n, 'condition', '请配置分支条件', i);
      });
    } else if (n.type !== 'END') {
      const next = nodes.get(n.next || '');
      if (!next || (next.parentKey || '') !== (n.parentKey || '') || next.type === 'START')
        add(n, 'next', '请选择同一流程内的后续节点');
    }
  }
  for (const scope of ['', ...d.nodes.filter((n) => n.type === 'SUBPROCESS').map((n) => n.key)]) {
    const members = d.nodes.filter((n) => (n.parentKey || '') === scope);
    const owner = nodes.get(scope) || d.nodes[0];
    if (!owner) continue;
    const starts = members.filter((n) => n.type === 'START');
    if (starts.length !== 1 || !members.some((n) => n.type === 'END')) {
      add(owner, 'connections', '每个流程须有一个开始节点和至少一个结束节点');
      continue;
    }
    const visited = new Set<string>(),
      active = new Set<string>();
    let cyclic = false;
    const walk = (key: string) => {
      if (active.has(key)) {
        cyclic = true;
        return;
      }
      if (visited.has(key)) return;
      const n = nodes.get(key);
      if (!n || (n.parentKey || '') !== scope) return;
      visited.add(key);
      active.add(key);
      for (const next of gateway(n) ? n.branches.map((b) => b.target) : n.next ? [n.next] : [])
        walk(next);
      active.delete(key);
    };
    walk(starts[0]!.key);
    for (const n of members) if (!visited.has(n.key)) add(n, 'connections', '节点未连接到开始节点');
    if (cyclic) add(owner, 'connections', '当前流程不支持循环连线');
  }
  return issues;
}

// Replace identifiers outside quoted literals; literal values must remain unchanged.
export function readableCondition(text: string, variables: WorkflowVariable[]): string {
  const names = Object.fromEntries(variables.map((v) => [v.key, v.label]));
  const ops: Record<string, string> = {
    AND: '且',
    OR: '或',
    NOT: '非',
    SUM: '合计',
    ANY: '任一明细满足',
    ALL: '全部明细满足',
    IN: '属于',
    CONTAINS: '包含',
    STARTS_WITH: '开头为',
    IS: '为',
    NULL: '空值',
    lines: '明细',
  };
  return text.replace(/"(?:\\.|[^"\\])*"|[A-Za-z_][\w.]*/g, (token) =>
    token.startsWith('"') ? token : names[token] || ops[token] || token,
  );
}

export function approvalSummary(node: WorkflowNode, names: Record<string, string>): string {
  const people = node.users.map((id) => names['users:' + id] || '已选人员');
  const roles = node.roles.map((id) => names['roles:' + id] || '已选角色');
  const actors = [...people, ...roles];
  return (
    (actors.length
      ? actors.slice(0, 2).join('、') + (actors.length > 2 ? ` 等${actors.length}项` : '')
      : '未设置审批人') +
    ' · ' +
    (node.approvalMode === 'ALL' ? '所有人通过' : '任一人通过')
  );
}
