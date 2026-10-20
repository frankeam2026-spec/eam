import { currentLocale } from './i18n';
import { tr } from './i18n';
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
  { value: 'yesterday', get label() { return tr('ui.equipment.text0054'); } },
  { value: 'last7', get label() { return tr('ui.equipment.text0055'); } },
  { value: 'month', get label() { return tr('ui.equipment.text0056'); } },
  { value: 'lastMonth', get label() { return tr('ui.equipment.text0057'); } },
  { value: 'quarter', get label() { return tr('ui.equipment.text0058'); } },
  { value: 'year', get label() { return tr('ui.equipment.text0059'); } },
  { value: 'custom', get label() { return tr('ui.equipment.text0060'); } },
];
export const widgetPeriodOptions = [
  { value: 'follow', get label() { return tr('ui.equipment.text0061'); } },
  ...periodOptions.slice(0, -1),
  { value: 'last90', get label() { return tr('ui.equipment.text0062'); } },
  { value: 'last12months', get label() { return tr('ui.equipment.text0063'); } },
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
      headers: { 'Accept-Language': currentLocale.value, 'Content-Type': 'application/json' },
      body: JSON.stringify(drill),
    });
    if (!r.ok) {
      const e = await r.json().catch(() => ({}));
      throw new Error(e.message || tr('ui.equipment.text0185'));
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
      ? tr('ui.equipment.text0064')
      : p.from > p.to
        ? '开始日期不能晚于结束日期'
        : ''
    : '';
}
