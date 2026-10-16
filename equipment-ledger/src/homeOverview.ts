import { request } from './api';
import type { AnalyticsPeriod } from './analytics';
import type { HomeBlock } from './home';
export type OverviewColumn = 'main' | 'side';
export type OverviewMetric = 'pending' | 'active' | 'today' | 'notification';
export type OverviewBlock = HomeBlock & {
  column: OverviewColumn;
  period?: AnalyticsPeriod;
  topN?: number;
  currency?: string;
  companyIds?: string[];
  departmentIds?: string[];
};
export type OverviewLayout = {
  schemaVersion?: number;
  layoutMode?: 'work' | 'analysis';
  period?: AnalyticsPeriod;
  companyIds?: string[];
  departmentIds?: string[];
  metrics: OverviewMetric[];
  blocks: OverviewBlock[];
};
export type OverviewState = {
  templates: { id: string; name: string; version: number }[];
  selected: { id: string; name: string; version: number };
  layout: OverviewLayout;
  defaultLayout: OverviewLayout;
  catalog: string[];
  rowVersion: number;
  templateVersion: number;
  updateAvailable: boolean;
};
export const overviewLabels: Record<string, string> = {
  work: '我的工作',
  anomalies: '关注事项',
  shortcuts: '常用入口',
  recent: '最近访问',
  surplus: '领用余料',
  stats: '工作概况',
  load: '团队任务负荷',
  inventory: '库存待办',
  created: '我发起的事项',
};
export const overviewApi = {
  load: (templateId?: string) =>
    request<OverviewState>(
      '/home/overview' +
        (templateId === undefined ? '' : '?templateId=' + encodeURIComponent(templateId)),
    ),
  save: (state: OverviewState, layout: OverviewLayout) =>
    request<OverviewState>('/home/overview', {
      method: 'PUT',
      body: JSON.stringify({
        templateId: state.selected.id,
        templateVersion: state.templateVersion,
        rowVersion: state.rowVersion,
        layout,
      }),
    }),
};
