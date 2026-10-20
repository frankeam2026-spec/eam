import type { AcceptanceDecisionValue } from './config/acceptance';
import { request, requestFile } from './api';
import type { CustomRecord } from './businessConfig';
import type { RepairAttachment } from './api';
import type { Choice, MaintenanceItem, ItemResult } from './maintenance';
export type InspectionKind =
  'standards' | 'daily-plans' | 'daily-tasks' | 'periodic-plans' | 'periodic-tasks';
export type InspectionEquipment = Choice & { functionLocation?: string };
export type InspectionDetail = {
  key: string;
  equipmentId: string;
  owners: string[];
  reviewers: string[];
  projectRefs: { standardId: string; itemId: string }[];
};
export type DailyArrangement = {
  id: string;
  name: string;
  enabled: boolean;
  frequency: 'daily' | 'weekly' | 'monthly' | 'shift' | 'slots' | 'interval' | 'event';
  intervalCount: number;
  anchorDate: string;
  startTime: string;
  endTime: string;
  windowMinutes: number;
  days: number[];
  slots: { id: string; name: string; start: string; end: string }[];
  personMode: 'specified' | 'team' | 'roster';
  owners: string[];
  teamId: string;
  followUpUserId: string;
  eventType: string;
  allowLate: boolean;
  equipmentIds: string[];
  projectRefs: { standardId: string; itemId: string }[];
};
export type OperationTeam = { id: string; name: string; members: string[]; rowVersion?: number };
export type DailyPreview = {
  rows: {
    arrangementName: string;
    equipmentId: string;
    businessDate: string;
    start: string;
    end: string;
    candidateCount: number;
  }[];
  total: number;
  unstaffed: number;
  limit: number;
  from: string;
  to: string;
};
export function emptyArrangement(date: string): DailyArrangement {
  return {
    id: '',
    name: '',
    enabled: true,
    frequency: 'daily',
    intervalCount: 1,
    anchorDate: date,
    startTime: '08:00:00',
    endTime: '10:00:00',
    windowMinutes: 30,
    days: [],
    slots: [],
    personMode: 'specified',
    owners: [],
    teamId: '',
    followUpUserId: '',
    eventType: 'startup',
    allowLate: true,
    equipmentIds: [],
    projectRefs: [],
  };
}
export type InspectionDefinition = CustomRecord & {
  id: string;
  code: string;
  name: string;
  departmentId: string;
  rowVersion?: number;
  createdAt?: string;
  enabled: boolean;
  remark: string;
  requirement: string;
  equipmentIds: string[];
  categoryNames: string[];
  categoryIds: string[];
  legacyCategoryNames?: string[];
  items: MaintenanceItem[];
  period: number;
  sortOrder: number;
  unit: string;
  typeId: string;
  owners: string[];
  reviewers: string[];
  reviewMode: string;
  startDate: string;
  endDate: string;
  nextAt: string;
  validHours: number;
  advanceHours: number;
  remindHours: number;
  expireRemindHours: number;
  advanceUnit: string;
  validUnit: string;
  remindUnit: string;
  expireRemindUnit: string;
  scanMode: string;
  onlyAbnormal: boolean;
  randomPhotoCount: number;
  allowLate: boolean;
  personMode: string;
  skipUnstaffed: boolean;
  restCycle: string;
  restDays: number[];
  holidays: string[];
  useShiftEnd: boolean;
  shifts: { id: string; name: string; start: string; end: string }[];
  roster: { key: string; date: string; shiftId: string; users: string[] }[];
  details: InspectionDetail[];
  arrangements: DailyArrangement[];
  workdays: string[];
};
export type InspectionResult = {
  items: ItemResult[];
  description?: string;
  attachments?: RepairAttachment[];
  startedAt?: string;
  finishedAt?: string;
  scannedEquipmentIds?: string[];
  skippedEquipmentIds?: string[];
  skipReason?: string;
  review?: {
    decision: AcceptanceDecisionValue;
    description: string;
    reviewer: string;
    reviewedAt: string;
    attachments: RepairAttachment[];
  };
  reviewItems?: { id: string; decision: AcceptanceDecisionValue; description: string }[];
};
export type InspectionTask = CustomRecord & {
  typeId: string;
  typeName: string;
  companyId?: string;
  departmentId?: string;
  id: string;
  code: string;
  kind: 'daily' | 'periodic';
  status: string;
  rowVersion: number;
  planName: string;
  planCode: string;
  equipmentName: string;
  equipmentCode: string;
  department: string;
  equipment: InspectionEquipment[];
  items: (MaintenanceItem & { equipmentId: string })[];
  shiftName: string;
  shiftDate: string;
  dueAt: string;
  expiresAt: string;
  createdAt: string;
  ownerId: string;
  ownerName: string;
  owners: string[];
  reviewers: string[];
  reviewMode: string;
  scanMode: string;
  onlyAbnormal: boolean;
  remark: string;
  canExecute: boolean;
  canReview: boolean;
  canTransfer: boolean;
  canVoid: boolean;
  canExempt: boolean;
  arrangementName: string;
  businessDate: string;
  assignmentMissing: boolean;
  followUpUserId: string;
  exemptionReason: string;
  submissions?: {
    id: string;
    submittedBy: string;
    submittedAt: string;
    description: string;
    decision?: string;
    reviewDescription?: string;
    reviewedAt?: string;
  }[];
  overdue: boolean;
  expected: number;
  checked: number;
  missing: number;
  abnormal: number;
  skipped: number;
  equipmentCount: number;
  workSeconds: number;
  result: InspectionResult;
  events: { action: string; actor: string; time: string; details: Record<string, unknown> }[];
};
export type InspectionChoices = {
  types?: InspectionDefinition[];
  equipment: InspectionEquipment[];
  organizations: Choice[];
  users: Choice[];
  categories: Choice[];
};
async function call<T>(path: string, body?: unknown, method = 'GET'): Promise<T> {
  return request<T>('/inspection' + path, {
    method,
    ...(body === undefined ? {} : { body: JSON.stringify(body) }),
  });
}
export const inspectionApi = {
  dailyEventOptions: (q: string, page: number, size: number) =>
    call<{
      data: {
        id: string;
        name: string;
        planName: string;
        code: string;
        planId: string;
        arrangementId: string;
        equipmentId: string;
        rowVersion: number;
      }[];
      page: { total: number };
    }>(
      '/daily-event-options?' + new URLSearchParams({ q, page: String(page), size: String(size) }),
    ),
  dailyPreview: (body: InspectionDefinition & { previewDays: number }) =>
    call<{ data: DailyPreview }>('/daily-plans/preview', body, 'POST'),
  dailyExemption: (
    id: string,
    body: {
      equipmentIds: string[];
      startAt: string;
      endAt: string;
      reason: string;
      rowVersion?: number;
    },
  ) => call<{ data: { count: number } }>('/daily-plans/' + id + '/exemptions', body, 'POST'),
  operationTeams: () => call<{ data: OperationTeam[] }>('/daily-operation-teams'),
  updateOperationTeam: (
    id: string,
    body: { name: string; members: string[]; rowVersion?: number },
  ) => call<{ data: OperationTeam }>('/daily-operation-teams/' + id, body, 'PUT'),
  createOperationTeam: (body: { name: string; departmentId: string; members: string[] }) =>
    call<{ data: OperationTeam }>('/daily-operation-teams', body, 'POST'),
  dailyEvent: (
    id: string,
    body: {
      arrangementId: string;
      equipmentIds: string[];
      eventAt: string;
      idempotencyKey: string;
      rowVersion?: number;
    },
  ) =>
    call<{ data: { count: number; taskIds: string[] } }>(
      '/daily-plans/' + id + '/events',
      body,
      'POST',
    ),
  query: (kind: InspectionKind, params: Record<string, string | number>) =>
    call<{
      data: (InspectionDefinition | InspectionTask)[];
      page: { number: number; size: number; total: number };
    }>(
      '/' +
        (kind.endsWith('tasks') ? 'tasks' : kind) +
        '?' +
        new URLSearchParams(
          Object.entries({
            ...params,
            ...(kind.endsWith('tasks')
              ? { kind: kind.startsWith('daily') ? 'daily' : 'periodic' }
              : {}),
          }).map(([key, value]) => [key, String(value)]),
        ),
    ),
  listContext: (kind: InspectionKind) =>
    call<{ organizations: InspectionChoices['organizations'] }>('/list-context?kind=' + kind),
  exportFile: (kind: InspectionKind, params: Record<string, string | number>) =>
    requestFile(
      '/inspection/' +
        kind +
        '/export?' +
        new URLSearchParams(Object.entries(params).map(([key, value]) => [key, String(value)])),
    ),
  choices: (paged = false) => call<InspectionChoices>('/choices?paged=' + paged),
  export: (kind: string, ids: string[]) =>
    call<{ data: (InspectionDefinition | InspectionTask)[] }>('/export', { kind, ids }, 'POST'),
  list: (kind: string, conditions = '[]') =>
    call<{ data: InspectionDefinition[] }>(
      '/' + kind + '?conditions=' + encodeURIComponent(conditions),
    ),
  get: (kind: string, id: string) => call<{ data: InspectionDefinition }>('/' + kind + '/' + id),
  save: (kind: string, body: InspectionDefinition) =>
    call<{ data: InspectionDefinition }>(
      '/' + kind + (body.id ? '/' + body.id : ''),
      body,
      body.id ? 'PUT' : 'POST',
    ),
  remove: (kind: string, id: string) => call('/' + kind + '/' + id, undefined, 'DELETE'),
  tasks: (kind: 'daily' | 'periodic', conditions = '[]') =>
    call<{ data: InspectionTask[]; page: { number: number; size: number; total: number } }>(
      '/tasks?kind=' + kind + '&conditions=' + encodeURIComponent(conditions),
    ),
  task: (id: string) => call<{ data: InspectionTask }>('/tasks/' + id),
  action: (id: string, action: string, body: unknown) =>
    call<{ data: InspectionTask }>('/tasks/' + id + '/' + action, body, 'POST'),
  batchReview: (body: unknown) => call('/batch-review', body, 'POST'),
};
export async function inspectionUpload(file: File): Promise<RepairAttachment> {
  const body = new FormData();
  body.append('file', file);
  return (
    await request<{ data: RepairAttachment }>('/inspection/files', { method: 'POST', body: body })
  ).data;
}
export const inspectionLabels: Record<InspectionKind, string> = {
  standards: '点巡检标准',
  'daily-plans': '日常点检计划',
  'daily-tasks': '日常点检任务',
  'periodic-plans': '周期巡检计划',
  'periodic-tasks': '周期巡检任务',
};
export const inspectionStates = [
  '未开始',
  '待执行',
  '执行中',
  '挂起',
  '待审核',
  '已完成',
  '已审核',
  '审核不通过',
  '已过期',
  '过期作废',
  '已作废',
  '已免检',
];
export function localTime(date = new Date()) {
  const pad = (v: number) => String(v).padStart(2, '0');
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`;
}
export function emptyInspection(): InspectionDefinition {
  const date = new Date(),
    end = new Date(date);
  end.setFullYear(end.getFullYear() + 1);
  return {
    id: '',
    code: '',
    name: '',
    departmentId: '',
    enabled: true,
    remark: '',
    requirement: '',
    equipmentIds: [],
    categoryNames: [],
    categoryIds: [],
    legacyCategoryNames: [],
    items: [],
    period: 1,
    sortOrder: 0,
    unit: '周',
    typeId: '',
    owners: [],
    reviewers: [],
    reviewMode: '无需验收',
    startDate: localTime(date).slice(0, 10),
    endDate: localTime(end).slice(0, 10),
    nextAt: localTime(date),
    validHours: 24,
    advanceHours: 0,
    remindHours: 0,
    expireRemindHours: 0,
    advanceUnit: '时',
    validUnit: '时',
    remindUnit: '时',
    expireRemindUnit: '时',
    scanMode: '无需扫码',
    onlyAbnormal: false,
    randomPhotoCount: 0,
    allowLate: false,
    personMode: '指定负责人',
    skipUnstaffed: true,
    restCycle: '按周',
    restDays: [],
    holidays: [],
    useShiftEnd: false,
    shifts: [{ id: crypto.randomUUID(), name: '白班', start: '08:00:00', end: '20:00:00' }],
    roster: [],
    details: [],
    arrangements: [],
    workdays: [],
  };
}
export function csvRows(text: string): string[][] {
  const result: string[][] = [];
  let row: string[] = [],
    cell = '',
    quoted = false;
  for (let i = 0; i < text.length; i++) {
    const c = text[i]!;
    if (c === '"') {
      if (quoted && text[i + 1] === '"') {
        cell += '"';
        i++;
      } else quoted = !quoted;
    } else if (c === ',' && !quoted) {
      row.push(cell.trim());
      cell = '';
    } else if ((c === '\n' || c === '\r') && !quoted) {
      if (c === '\r' && text[i + 1] === '\n') i++;
      row.push(cell.trim());
      if (row.some(Boolean)) result.push(row);
      row = [];
      cell = '';
    } else cell += c;
  }
  row.push(cell.trim());
  if (row.some(Boolean)) result.push(row);
  return result;
}
export function downloadCsv(name: string, rows: unknown[][]) {
  const csv =
    '\uFEFF' +
    rows
      .map((r) =>
        r
          .map((v) => {
            let s = String(v ?? '');
            if (/^[\s]*[=+@-]/.test(s)) s = "'" + s;
            return '"' + s.replaceAll('"', '""') + '"';
          })
          .join(','),
      )
      .join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = name + '.csv';
  a.click();
  URL.revokeObjectURL(url);
}
