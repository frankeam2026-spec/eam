import type { OverviewLayout } from './homeOverview';
import { request } from './api';
export type HomeBlock = {
  id: string;
  type: string;
  title: string;
  width: number;
  limit: number;
  scope: 'personal' | 'team';
  module?: string;
  status?: string;
  companyId?: string;
  departmentId?: string;
};
export type HomeLayout = { blocks: HomeBlock[] };
export type HomeTemplate = {
  id: string;
  name: string;
  version?: number;
  row_version: number;
  published_version: number;
  enabled: number;
  draft: HomeLayout;
  overviewDraft?: OverviewLayout;
  roleIds: string[];
  priority: number;
};
export type HomeState = {
  templates: { id: string; name: string; version: number }[];
  selected: { id: string; name: string; version: number };
  layout: HomeLayout;
  rowVersion: number;
  templateVersion: number;
  updateAvailable: boolean;
  permissions: Record<string, string[]>;
};
export const blockLabels: Record<string, string> = {
  todo: '待我处理',
  today: '今日任务',
  active: '正在执行',
  created: '我发起的事项',
  anomalies: '设备异常',
  load: '人员任务负荷',
  inventory: '库存事项',
  surplus: '我的余料',
  shortcuts: '快捷入口',
  recent: '最近访问',
  stats: '工作概况',
};
export const homeApi = {
  load: (id?: string) =>
    request<HomeState>('/home' + (id !== undefined ? '?templateId=' + id : '')),
  save: (body: unknown) =>
    request<HomeState>('/home/layout', { method: 'PUT', body: JSON.stringify(body) }),
  reset: (id: string) =>
    request<HomeState>('/home/reset', { method: 'POST', body: JSON.stringify({ templateId: id }) }),
  preferred: (id: string) =>
    request('/home/default', { method: 'PUT', body: JSON.stringify({ templateId: id }) }),
  templates: () => request<{ data: HomeTemplate[] }>('/home-templates'),
  roles: () => request<{ data: { id: string; name: string }[] }>('/home-templates/role-choices'),
  store: (t: HomeTemplate) =>
    request<{ data: HomeTemplate }>('/home-templates' + (t.id ? '/' + t.id : ''), {
      method: t.id ? 'PUT' : 'POST',
      body: JSON.stringify({ ...t, rowVersion: t.row_version }),
    }),
  publish: (t: HomeTemplate) =>
    request<{ data: HomeTemplate }>('/home-templates/' + t.id + '/publish', {
      method: 'POST',
      body: JSON.stringify({ rowVersion: t.row_version }),
    }),
  disable: (t: HomeTemplate) =>
    request('/home-templates/' + t.id + '/disable', {
      method: 'POST',
      body: JSON.stringify({ rowVersion: t.row_version }),
    }),
};
