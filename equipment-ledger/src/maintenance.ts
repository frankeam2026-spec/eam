import type { AcceptanceDecisionValue } from './config/acceptance';
import { request, requestFile } from './api';
import type { CustomRecord } from './businessConfig';
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
  categoryId?: string;
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
  validDuration?: number;
  validUnit?: string;
  owners: string[];
  reviewers: string[];
  remark: string;
};
export type MaintenanceDefinition = CustomRecord & {
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
  categoryIds: string[];
  legacyCategoryNames?: string[];
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
    decision: AcceptanceDecisionValue;
    score: number;
    description: string;
    attachments: RepairAttachment[];
  };
};
export type MaintenanceTaskStatistics = {
  total: number;
  completed: number | null;
  abnormal: number | null;
  completionRate: number | null;
  completedItemIds: string[];
  abnormalItemIds: string[];
  submitted: boolean;
  reliable: boolean;
};
export type MaintenanceProjectFilter = 'all' | 'completed' | 'abnormal';
export type MaintenanceTask = CustomRecord & {
  statistics?: MaintenanceTaskStatistics;
  reviewMode?: string;
  canDelete?: boolean;
  canPostpone?: boolean;
  typeId: string;
  companyId?: string;
  departmentId?: string;
  id: string;
  code: string;
  status: string;
  rowVersion: number;
  dueDate: string;
  expiresDate: string;
  equipmentId?: string;
  equipmentName: string;
  equipmentCode: string;
  model: string;
  department: string;
  functionLocation: string;
  typeName: string;
  planName: string;
  planId: string;
  actualStartAt: string;
  actualEndAt: string;
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
async function call<T>(path: string, body?: unknown, method = 'GET'): Promise<T> {
  return request<T>('/maintenance' + path, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}
export const maintenanceApi = {
  listContext: (kind: MaintenanceKind) =>
    call<{ organizations: Choice[] }>('/list-context?kind=' + kind),
  export: (kind: MaintenanceKind, parameters: Record<string, string | number>) =>
    requestFile(
      '/maintenance/' +
        kind +
        '/export?' +
        new URLSearchParams(
          Object.fromEntries(
            Object.entries(parameters).map(([key, value]) => [key, String(value)]),
          ),
        ),
    ),
  query: (kind: MaintenanceKind, parameters: Record<string, string | number>) =>
    call<{
      data: (MaintenanceDefinition | MaintenanceTask)[];
      page: { number: number; size: number; total: number };
    }>(
      '/' +
        kind +
        '?' +
        new URLSearchParams(
          Object.fromEntries(
            Object.entries(parameters).map(([key, value]) => [key, String(value)]),
          ),
        ),
    ),
  list: (kind: MaintenanceKind, conditions = '[]') =>
    call<{ data: MaintenanceDefinition[] }>(
      '/' + kind + '?conditions=' + encodeURIComponent(conditions),
    ),
  get: (kind: MaintenanceKind, id: string) =>
    call<{ data: MaintenanceDefinition }>('/' + kind + '/' + id),
  save: (kind: MaintenanceKind, body: MaintenanceDefinition) =>
    call<{ data: MaintenanceDefinition }>(
      '/' + kind + (body.id ? '/' + body.id : ''),
      body,
      body.id ? 'PUT' : 'POST',
    ),
  remove: (kind: MaintenanceKind, id: string) => call('/' + kind + '/' + id, undefined, 'DELETE'),
  choices: (paged = false) =>
    call<{
      types?: MaintenanceDefinition[];
      equipment: Choice[];
      organizations: Choice[];
      users: Choice[];
      categories: Choice[];
      stocks: StockRow[];
    }>('/choices?paged=' + paged),
  tasks: (conditions = '[]') =>
    call<{ data: MaintenanceTask[] }>('/tasks?conditions=' + encodeURIComponent(conditions)),
  task: (id: string) => call<{ data: MaintenanceTask }>('/tasks/' + id),
  action: (id: string, action: string, body: unknown) =>
    call<{ data: MaintenanceTask }>('/tasks/' + id + '/' + action, body, 'POST'),
  acceptBatch: (body: unknown) => call('/batch-accept', body, 'POST'),
  deleteTasks: (tasks: { id: string; rowVersion: number }[]) =>
    call('/batch-delete', tasks, 'POST'),
  postponeTasks: (body: {
    tasks: { id: string; rowVersion: number }[];
    endDate: string;
    reason: string;
  }) => call('/batch-postpone', body, 'POST'),
  generate: (id: string) => call<{ count: number }>('/plans/' + id + '/generate', {}, 'POST'),
};
export async function maintenanceUpload(file: File): Promise<RepairAttachment> {
  const f = new FormData();
  f.append('file', file);
  return (
    await request<{ data: RepairAttachment }>('/maintenance/files', { method: 'POST', body: f })
  ).data;
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
    categoryIds: [],
    legacyCategoryNames: [],
    items: [],
    owners: [],
    reviewers: [],
    calculation: '固定间隔',
    advanceDays: 0,
    remindDays: 0,
    autoFill: '无',
    scanStart: false,
    reviewMode: '无需验收',
    reviewRule: '',
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
