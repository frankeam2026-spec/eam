import type { RepairAttachment, StockRow } from './api';
export type MaintenanceKind = 'types' | 'standards' | 'plans' | 'tasks';
export type Choice = {
  id: string;
  name: string;
  parentId?: string;
  type?: string;
  account?: string;
  department?: string;
  code?: string;
  model?: string;
  category?: string;
};
export type MaintenanceItem = {
  id: string;
  code: string;
  module: string;
  name: string;
  content: string;
  typeId: string;
  valueType: '选项' | '数值' | '文本';
  lower: string;
  upper: string;
  options: string[];
  normalOptions: string[];
  required: boolean;
  photoRequired: boolean;
  remark: string;
  references: RepairAttachment[];
  spares: string;
  spareRequirements: { stockId: string; quantity: number }[];
};
export type PlanDetail = {
  clientKey?: string;
  projectRefs?: { standardId: string; itemId: string }[];
  id?: string;
  equipmentId: string;
  typeId: string;
  standardId: string;
  period: number;
  unit: string;
  nextDate: string;
  validDays: number;
  owners: string[];
  reviewers: string[];
  remark: string;
};
export type MaintenanceDefinition = {
  id: string;
  code: string;
  name: string;
  departmentId: string;
  creatorId?: string;
  rowVersion?: number;
  createdAt?: string;
  period: number;
  unit: string;
  symbol: string;
  system: boolean;
  enabled: boolean;
  remark: string;
  equipmentIds: string[];
  categoryNames: string[];
  items: MaintenanceItem[];
  owners: string[];
  reviewers: string[];
  calculation: string;
  advanceDays: number;
  remindDays: number;
  autoFill: string;
  scanStart: boolean;
  reviewMode: string;
  reviewRule: string;
  details: PlanDetail[];
};
export type ItemResult = {
  implicitNormal?: boolean;
  skipped?: boolean;
  id: string;
  value: string;
  abnormal?: boolean;
  resolved: boolean;
  measure: string;
  remark: string;
  images: RepairAttachment[];
};
export type TaskResult = {
  description?: string;
  startAt?: string;
  endAt?: string;
  startedAt?: string;
  collaborators?: string[];
  attachments?: RepairAttachment[];
  items?: ItemResult[];
  acceptance?: {
    decision: string;
    score: number;
    description: string;
    attachments: RepairAttachment[];
  };
};
export type MaintenanceTask = {
  companyId?: string;
  departmentId?: string;
  id: string;
  code: string;
  status: string;
  rowVersion: number;
  dueDate: string;
  expiresDate: string;
  equipmentName: string;
  equipmentCode: string;
  model: string;
  department: string;
  functionLocation: string;
  typeName: string;
  planName: string;
  standardName: string;
  ownerId: string;
  owners: string[];
  reviewers: string[];
  items: MaintenanceItem[];
  result: TaskResult;
  scanStart?: boolean;
  canExecute: boolean;
  canAccept: boolean;
  createdAt: string;
  remark: string;
  autoFill: string;
  events: { action: string; actor: string; time: string; details: Record<string, unknown> }[];
};
const root = '/api/v1/maintenance';
async function call<T>(path: string, body?: unknown, method = 'GET'): Promise<T> {
  const r = await fetch(root + path, {
    method,
    credentials: 'same-origin',
    headers: { 'Content-Type': 'application/json' },
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
  const d = await r.json();
  if (!r.ok) throw new Error(d.message || '请求失败');
  return d;
}
export const maintenanceApi = {
  list: (kind: MaintenanceKind) => call<{ data: MaintenanceDefinition[] }>('/' + kind),
  get: (kind: MaintenanceKind, id: string) =>
    call<{ data: MaintenanceDefinition }>('/' + kind + '/' + id),
  save: (kind: MaintenanceKind, body: MaintenanceDefinition) =>
    call<{ data: MaintenanceDefinition }>(
      '/' + kind + (body.id ? '/' + body.id : ''),
      body,
      body.id ? 'PUT' : 'POST',
    ),
  remove: (kind: MaintenanceKind, id: string) => call('/' + kind + '/' + id, undefined, 'DELETE'),
  choices: () =>
    call<{
      equipment: Choice[];
      organizations: Choice[];
      users: Choice[];
      categories: Choice[];
      stocks: StockRow[];
    }>('/choices'),
  tasks: () => call<{ data: MaintenanceTask[] }>('/tasks'),
  task: (id: string) => call<{ data: MaintenanceTask }>('/tasks/' + id),
  action: (id: string, action: string, body: unknown) =>
    call<{ data: MaintenanceTask }>('/tasks/' + id + '/' + action, body, 'POST'),
  acceptBatch: (body: unknown) => call('/batch-accept', body, 'POST'),
  generate: (id: string) => call<{ count: number }>('/plans/' + id + '/generate', {}, 'POST'),
};
export async function maintenanceUpload(file: File): Promise<RepairAttachment> {
  const f = new FormData();
  f.append('file', file);
  const r = await fetch(root + '/files', { method: 'POST', body: f });
  const d = await r.json();
  if (!r.ok) throw new Error(d.message || '上传失败');
  return d.data;
}
export function emptyDefinition(): MaintenanceDefinition {
  return {
    id: '',
    code: '',
    name: '',
    departmentId: '',
    period: 1,
    unit: '月',
    symbol: '',
    system: false,
    enabled: true,
    remark: '',
    equipmentIds: [],
    categoryNames: [],
    items: [],
    owners: [],
    reviewers: [],
    calculation: '固定间隔',
    advanceDays: 0,
    remindDays: 0,
    autoFill: '无',
    scanStart: false,
    reviewMode: '整机验收',
    reviewRule: '任一人验收',
    details: [],
  };
}
export function emptyItem(typeId = ''): MaintenanceItem {
  return {
    id: crypto.randomUUID(),
    code: '',
    module: '',
    name: '',
    content: '',
    typeId,
    valueType: '选项',
    lower: '',
    upper: '',
    options: [],
    normalOptions: [],
    required: true,
    photoRequired: false,
    remark: '',
    references: [],
    spares: '',
    spareRequirements: [],
  };
}
