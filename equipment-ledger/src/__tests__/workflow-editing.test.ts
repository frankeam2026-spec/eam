import { describe, it, expect } from 'vitest';
import {
  inspectWorkflow,
  readableCondition,
  approvalSummary,
} from '../components/workflow/workflowEditing';
import type { WorkflowDefinition, WorkflowNode } from '../workflow';
const node = (key: string, type: WorkflowNode['type'], next: string | null): WorkflowNode => ({
  key,
  type,
  next,
  name: key,
  users: [],
  roles: [],
  approvalMode: 'ANY',
  branches: [],
});
const definition = (nodes: WorkflowNode[]): WorkflowDefinition => ({
  id: null,
  rowVersion: 0,
  name: 'test',
  companyId: '1',
  businessType: 'requisition',
  description: '',
  state: 'DRAFT',
  versionId: null,
  nodes,
});
describe('workflow editing diagnostics', () => {
  it('locates missing approvers, invalid connections and detached steps without blocking draft persistence', () => {
    const a = node('a', 'APPROVAL', 'missing');
    const result = inspectWorkflow(
      definition([
        node('s', 'START', 'a'),
        a,
        node('end', 'END', null),
        node('lost', 'SCRIPT', 'end'),
      ]),
    );
    expect(result).toEqual(
      expect.arrayContaining([
        expect.objectContaining({ node: 'a', field: 'actors' }),
        expect.objectContaining({ node: 'a', field: 'next' }),
        expect.objectContaining({ node: 'lost', field: 'connections' }),
      ]),
    );
  });
  it('accepts parallel paths joining a common successor and detects actual cycles', () => {
    const fork = node('fork', 'PARALLEL', null),
      a = node('a', 'APPROVAL', 'join'),
      b = node('b', 'APPROVAL', 'join'),
      join = node('join', 'PARALLEL', null);
    a.users = ['1'];
    b.roles = ['2'];
    fork.branches = [
      { key: 'a', name: 'A', target: 'a', fallback: false, expression: '' },
      { key: 'b', name: 'B', target: 'b', fallback: false, expression: '' },
    ];
    join.branches = [
      { key: 'end', name: 'continue', target: 'end', fallback: false, expression: '' },
    ];
    const d = definition([node('s', 'START', 'fork'), fork, a, b, join, node('end', 'END', null)]);
    expect(inspectWorkflow(d)).toEqual([]);
    a.next = 'fork';
    expect(inspectWorkflow(d).some((i) => i.message.includes('循环'))).toBe(true);
  });
  it('does not translate literal strings while making variable names readable', () => {
    expect(
      readableCondition('taskKind == "AND SUM taskKind" AND SUM(lines.quantity) > 10', [
        { key: 'taskKind', label: '关联任务', type: 'TEXT', available: true, nullable: false },
        {
          key: 'lines.quantity',
          label: '申请数量',
          type: 'NUMBER',
          available: true,
          nullable: false,
        },
      ]),
    ).toBe('关联任务 == "AND SUM taskKind" 且 合计(申请数量) > 10');
  });
  it('uses resolved actor names without leaking raw IDs and shows the approval mode', () => {
    const n = node('a', 'APPROVAL', null);
    n.users = ['9007199254740999'];
    n.roles = ['2'];
    n.approvalMode = 'ALL';
    expect(approvalSummary(n, { 'users:9007199254740999': '张三', 'roles:2': '部门经理' })).toBe(
      '张三、部门经理 · 所有人通过',
    );
    expect(approvalSummary(n, {})).not.toContain('9007199254740999');
  });
});
