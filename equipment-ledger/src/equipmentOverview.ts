import { request, type InstalledSpare } from './api';
import { normalizePageSize } from './utils/pagination';

export type AttentionKind = 'all' | 'repair' | 'maintenance' | 'inspection' | 'anomaly';
export type EventKind = '' | 'repair' | 'maintenance' | 'inspection' | 'anomaly' | 'spare';
export type ArchiveTab = '概览' | '基本信息' | '维修' | '保养' | '巡检' | '备件' | '动态';
export type AttentionState = { kind: AttentionKind; expanded: boolean; page: number; size: number };
export type EventState = { kind: EventKind; sort: 'asc' | 'desc'; page: number; size: number };
export type ArchiveState = {
  tab: ArchiveTab;
  attention: AttentionState;
  events: EventState;
  scroll: Partial<Record<ArchiveTab, number>>;
};
export type AttentionItem = {
  id: string;
  kind: Exclude<AttentionKind, 'all'>;
  code: string;
  summary: string;
  status: string;
  owner: string;
  createdAt: string;
  deadline: string;
  overdue: boolean;
  source: string;
  url: string;
};
export type EquipmentEvent = {
  id: string;
  kind: Exclude<EventKind, ''>;
  code: string;
  action: string;
  actor: string;
  time: string;
  url?: string;
};
export type EquipmentOverviewData = {
  tasks: AttentionItem[];
  anomalies: AttentionItem[];
  installed: InstalledSpare[];
  installedTotal: number;
  counts: Record<Exclude<AttentionKind, 'all'>, number>;
  access: Record<Exclude<AttentionKind, 'all'>, boolean>;
};
export const equipmentOverviewApi = {
  overview: (id: string) => request<EquipmentOverviewData>(`/equipment/${id}/overview`),
  attention: (id: string, state: AttentionState) =>
    request<{ data: AttentionItem[]; total: number; accessible: boolean }>(
      `/equipment/${id}/attention?kind=${state.kind}&page=${state.page}&size=${state.size}`,
    ),
  events: (id: string, state: EventState) =>
    request<{ data: EquipmentEvent[]; total: number }>(
      `/equipment/${id}/events?kind=${state.kind}&sort=${state.sort}&page=${state.page}&size=${state.size}`,
    ),
};
export const eventLabels: Record<Exclude<EventKind, ''>, string> = {
  repair: '维修',
  maintenance: '保养',
  inspection: '点巡检',
  anomaly: '异常',
  spare: '备件更换',
};
export function archiveState(input?: Partial<ArchiveState>): ArchiveState {
  const tab = input?.tab;
  const attention = input?.attention;
  const events = input?.events;
  const positive = (n: unknown, fallback: number) =>
    typeof n === 'number' && Number.isSafeInteger(n) && n > 0 ? n : fallback;
  return {
    tab:
      tab && ['概览', '基本信息', '维修', '保养', '巡检', '备件', '动态'].includes(tab)
        ? tab
        : '概览',
    attention: {
      kind:
        attention &&
        ['all', 'repair', 'maintenance', 'inspection', 'anomaly'].includes(attention.kind)
          ? attention.kind
          : 'all',
      expanded: attention?.expanded === true,
      page: positive(attention?.page, 1),
      size: normalizePageSize(attention?.size),
    },
    events: {
      kind: events && ['', ...Object.keys(eventLabels)].includes(events.kind) ? events.kind : '',
      sort: events?.sort === 'asc' ? 'asc' : 'desc',
      page: positive(events?.page, 1),
      size: normalizePageSize(events?.size),
    },
    scroll: Object.fromEntries(
      Object.entries(input?.scroll || {}).filter(
        ([key, n]) =>
          ['概览', '基本信息', '维修', '保养', '巡检', '备件', '动态'].includes(key) &&
          typeof n === 'number' &&
          Number.isFinite(n) &&
          n >= 0,
      ),
    ),
  };
}
export function statusType(value: string): 'primary' | 'success' | 'warning' | 'danger' | 'info' {
  if (['故障', '已超期', '过期作废'].includes(value)) return 'danger';
  if (['正常', '已完成', '已消除'].includes(value)) return 'success';
  if (['带病运行', '挂起', '已挂起', '待验收', '待审核', '待处理'].includes(value))
    return 'warning';
  if (['已取消', '已撤销', '已作废', '停用'].includes(value)) return 'info';
  return 'primary';
}
