import { request } from './api';
import type { OverviewBlock, OverviewLayout } from './homeOverview';
export type AnalyticsPeriod = { preset: string; from?: string; to?: string };
export type AnalyticsMetric = {
  id: string;
  name: string;
  domain: string;
  chart: 'bar' | 'line' | 'table' | 'execution' | 'departments';
  period: string;
  unit: string;
  definition: string;
  money: boolean;
};
export type AnalyticsOrganization = {
  id: string;
  name: string;
  org_type: string;
  parentId: string;
};
export type AnalyticsCatalog = {
  data: AnalyticsMetric[];
  organizations: AnalyticsOrganization[];
  teamAllowed: boolean;
  timezone: string;
};
export type AnalyticsWidget = {
  id: string;
  metric: string;
  period?: AnalyticsPeriod;
  scope: string;
  topN?: number;
  currency?: string;
  companyIds?: string[];
  departmentIds?: string[];
};
export type AnalyticsContext = {
  period: AnalyticsPeriod;
  companyIds: string[];
  departmentIds: string[];
  asOf?: string;
};
export type AnalysisRow = Record<string, any>;
export type AnalyticsResult = {
  id: string;
  metric: string;
  status: 'ok' | 'empty' | 'data_missing' | 'forbidden' | 'error';
  message?: string;
  rows?: AnalysisRow[];
  summary?: AnalysisRow;
  quality?: Record<string, number | null>;
  definition?: string;
  unit?: string;
  asOf?: string;
  period?: { from: string; to: string; label: string; preset: string };
  currency?: string;
  currencies?: string[];
  exportable?: boolean;
};
export type AnalysisDrill = {
  widget: AnalyticsWidget;
  context: AnalyticsContext;
  filter: { key?: string; series?: string };
  title: string;
  subtitle?: string;
  returnLayout?: OverviewLayout;
  templateId?: string;
};
export const periodOptions = [
  { value: 'yesterday', label: '昨日' },
  { value: 'last7', label: '最近7天' },
  { value: 'month', label: '本月' },
  { value: 'lastMonth', label: '上月' },
  { value: 'quarter', label: '本季度' },
  { value: 'year', label: '本年' },
  { value: 'custom', label: '自定义' },
];
export const widgetPeriodOptions = [
  { value: 'follow', label: '跟随页面周期' },
  ...periodOptions.slice(0, -1),
  { value: 'last90', label: '最近90天' },
  { value: 'last12months', label: '最近12个自然月' },
  periodOptions.at(-1)!,
];
export const isAnalytics = (block: { type: string }) => block.type.startsWith('analytics:');
export function analyticsWidget(block: OverviewBlock): AnalyticsWidget {
  return {
    id: block.id,
    metric: block.type.slice(10),
    period: block.period,
    scope: block.scope,
    topN: block.topN || 10,
    currency: block.currency || '',
    companyIds: block.companyIds,
    departmentIds: block.departmentIds,
  };
}
export function analysisContext(layout: OverviewLayout): AnalyticsContext {
  return {
    period: layout.period || { preset: 'month' },
    companyIds: layout.companyIds || [],
    departmentIds: layout.departmentIds || [],
  };
}
export const analyticsApi = {
  catalog: () => request<AnalyticsCatalog>('/home/analytics/catalog'),
  presets: () =>
    request<{ data: { name: string; layout: OverviewLayout }[] }>('/home/analytics/presets'),
  query: (widgets: AnalyticsWidget[], context: AnalyticsContext) =>
    request<{ data: AnalyticsResult[]; asOf: string }>('/home/analytics/query', {
      method: 'POST',
      body: JSON.stringify({ widgets, context }),
    }),
  details: (drill: AnalysisDrill, page: number, size: number) =>
    request<{
      data: AnalysisRow[];
      total: number;
      asOf: string;
      definition: string;
      exportable: boolean;
    }>('/home/analytics/details', {
      method: 'POST',
      body: JSON.stringify({ ...drill, page, size }),
    }),
  async export(drill: AnalysisDrill) {
    const r = await fetch('/api/v1/home/analytics/export', {
      method: 'POST',
      credentials: 'same-origin',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(drill),
    });
    if (!r.ok) {
      const e = await r.json().catch(() => ({}));
      throw new Error(e.message || '导出失败');
    }
    const url = URL.createObjectURL(await r.blob());
    const a = document.createElement('a');
    a.href = url;
    a.download = '管理分析明细.csv';
    a.click();
    URL.revokeObjectURL(url);
  },
};
export const qualityLabels: Record<string, string> = {
  missingEquipment: '缺少设备关联',
  unclassified: '未填写或历史未归类',
  missingAcceptanceTime: '历史验收时间缺失',
  missingSubmission: '提交或截止时间缺失',
  missingDeadline: '未记录截止时间',
  missingCost: '使用成本缺失',
  missingCurrency: '币种来源缺失',
};

export function periodError(p?: AnalyticsPeriod) {
  return p?.preset === 'custom'
    ? !p.from || !p.to
      ? '请选择完整日期区间'
      : p.from > p.to
        ? '开始日期不能晚于结束日期'
        : ''
    : '';
}
