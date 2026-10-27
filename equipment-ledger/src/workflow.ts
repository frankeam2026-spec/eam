import { request } from './api';
import type { SelectorPage, SelectorRow } from './selectors';
export type WorkflowBranch = {
  key: string;
  name: string;
  target: string;
  fallback: boolean;
  expression: string;
};
export type WorkflowNode = {
  key: string;
  type:
    'START' | 'APPROVAL' | 'BRANCH' | 'END' | 'PARALLEL' | 'INCLUSIVE' | 'SUBPROCESS' | 'SCRIPT';
  name: string;
  next: string | null;
  approvalMode: 'ANY' | 'ALL';
  users: string[];
  roles: string[];
  branches: WorkflowBranch[];
  parentKey?: string | null;
  script?: {
    language: 'javascript';
    source: string;
    outputKey: string;
    outputType: 'NUMBER' | 'TEXT' | 'BOOLEAN';
  } | null;
};
export type WorkflowDefinition = {
  id: string | null;
  rowVersion: number;
  name: string;
  businessType: string;
  companyId: string;
  description: string;
  versionId: string | null;
  state: string;
  nodes: WorkflowNode[];
  diagramXml?: string | null;
};
export type WorkflowVariable = {
  key: string;
  label: string;
  type: string;
  available: boolean;
  nullable: boolean;
};
export type WorkflowApproval = {
  id: string;
  user_id: string;
  user_name: string;
  node_key: string;
  node_name: string;
  state: string;
  opinion: string;
  acted_at: string | null;
};
export type WorkflowRound = {
  id: string;
  roundNo: number;
  rowVersion: number;
  state: string;
  templateName: string;
  startedAt: string;
  approvals: WorkflowApproval[];
  nodes?: WorkflowNode[];
  events: { type: string; nodeKey?:string; description: string; createdAt: string }[];
};
export type Condition = {
  op: string;
  field: string | null;
  literal: string | number | boolean | null;
  type: string;
  children: Condition[];
};
export function conditionText(c: Condition): string {
  if (c.op === 'FIELD') return c.field || '';
  if (c.op === 'LITERAL') return c.type === 'TEXT' ? JSON.stringify(c.literal) : String(c.literal);
  if (c.op === 'AND' || c.op === 'OR')
    return '(' + c.children.map(conditionText).join(' ' + c.op + ' ') + ')';
  if (c.op === 'NOT') return 'NOT (' + conditionText(c.children[0]!) + ')';
  if (c.op === 'ANY' || c.op === 'ALL')
    return c.op + '(lines, ' + conditionText(c.children[0]!) + ')';
  if (c.op === 'SUM') return 'SUM(' + conditionText(c.children[0]!) + ')';
  if (c.op === 'IS_NULL' || c.op === 'IS_NOT_NULL')
    return conditionText(c.children[0]!) + ' ' + c.op.replaceAll('_', ' ');
  if (c.op === 'IN' || c.op === 'NOT_IN')
    return (
      conditionText(c.children[0]!) +
      ' ' +
      c.op.replaceAll('_', ' ') +
      ' (' +
      c.children.slice(1).map(conditionText).join(', ') +
      ')'
    );
  return conditionText(c.children[0]!) + ' ' + c.op + ' ' + conditionText(c.children[1]!);
}
export const workflowApi = {
  scriptTrial:(companyId:string,script:NonNullable<WorkflowNode['script']>,values:Record<string,unknown>)=>request<{data:{value:string|boolean}}>('/workflow/templates/script-trial',{method:'POST',body:JSON.stringify({companyId,script,values})}),
  resolve: (companyId: string, kind: string, ids: string[]) =>
    request<SelectorPage<SelectorRow>>('/workflow/templates/resolve', {
      method: 'POST',
      body: JSON.stringify({ companyId, kind, ids }),
    }),
  list: (q: string, companyId: string, page: number, size: number) =>
    request<{
      data: {
        id: string;
        name: string;
        companyName: string;
        active: boolean;
        rowVersion: number;
      }[];
      total: number;
    }>(
      '/workflow/templates?' +
        new URLSearchParams({
          q,
          ...(companyId ? { companyId } : {}),
          page: String(page),
          size: String(size),
        }),
    ),
  companies: (action = '查看') =>
    request<{ data: SelectorRow[] }>(
      '/workflow/templates/companies?' + new URLSearchParams({ action }),
    ),
  variables: () => request<{ data: WorkflowVariable[] }>('/workflow/templates/variables'),
  detail: (id: string, versionId?: string) =>
    request<{ data: WorkflowDefinition }>(
      '/workflow/templates/' + id + (versionId ? '?versionId=' + versionId : ''),
    ),
  save: (d: WorkflowDefinition) =>
    request<{ data: WorkflowDefinition }>('/workflow/templates', {
      method: 'POST',
      body: JSON.stringify(d),
    }),
  publish: (d: WorkflowDefinition) =>
    request<{ data: WorkflowDefinition }>('/workflow/templates/' + d.id + '/publish', {
      method: 'POST',
      body: JSON.stringify({ rowVersion: d.rowVersion }),
    }),
  disable: (id: string, rowVersion: number) =>
    request('/workflow/templates/' + id + '/disable', {
      method: 'POST',
      body: JSON.stringify({ rowVersion }),
    }),
  versions: (id: string) =>
    request<{ data: { id: string; versionNo: number; state: string; publishedAt: string }[] }>(
      '/workflow/templates/' + id + '/versions',
    ),
  choices: (companyId: string, kind: string, q: string, page: number, size: number) =>
    request<SelectorPage<SelectorRow>>(
      '/workflow/templates/choices?' +
        new URLSearchParams({ companyId, kind, q, page: String(page), size: String(size) }),
    ),
  parse: (expression: string, definition?: WorkflowDefinition | null) =>
    request<{ data: Condition }>('/workflow/templates/expression', {
      method: 'POST',
      body: JSON.stringify({ expression, definition }),
    }),
  simulate: (definition: WorkflowDefinition, values: Record<string, unknown>) =>
    request<{ data: Record<string, unknown>[] }>('/workflow/templates/simulate', {
      method: 'POST',
      body: JSON.stringify({ definition, values }),
    }),
  act: (
    instance: string,
    approvalId: string,
    rowVersion: number,
    approve: boolean,
    opinion: string,
  ) =>
    request('/workflow/instances/' + instance + '/act', {
      method: 'POST',
      body: JSON.stringify({ approvalId, rowVersion, approve, opinion }),
    }),
  withdraw: (instance: string, rowVersion: number) =>
    request('/workflow/instances/' + instance + '/withdraw', {
      method: 'POST',
      body: JSON.stringify({ rowVersion }),
    }),
};
