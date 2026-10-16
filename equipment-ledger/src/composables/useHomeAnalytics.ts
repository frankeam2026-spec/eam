import { ref, computed, watch, onBeforeUnmount, type Ref } from 'vue';
import { useRouter } from 'vue-router';
import {
  analyticsApi,
  isAnalytics,
  analyticsWidget,
  analysisContext,
  periodError,
  type AnalyticsCatalog,
  type AnalyticsResult,
  type AnalysisDrill,
} from '../analytics';
import { overviewLabels, type OverviewLayout, type OverviewBlock } from '../homeOverview';
export function useHomeAnalytics(
  layout: Ref<OverviewLayout>,
  getTemplateId?: () => string | undefined,
) {
  const router = useRouter(),
    catalog = ref<AnalyticsCatalog>(),
    results = ref<Record<string, AnalyticsResult>>({}),
    loading = ref(false),
    error = ref(''),
    asOf = ref('');
  const presets = ref<{ name: string; layout: OverviewLayout }[]>([]),
    presetIndex = ref<number>();
  const analytical = computed(() => layout.value.blocks.filter(isAnalytics));
  let version = 0,
    timer: ReturnType<typeof setTimeout> | undefined;
  async function initialize() {
    try {
      catalog.value = await analyticsApi.catalog();
      for (const m of catalog.value.data) overviewLabels['analytics:' + m.id] = m.name;
      presets.value = (await analyticsApi.presets()).data;
      error.value = '';
    } catch (e) {
      error.value = e instanceof Error ? e.message : '指标目录加载失败';
    }
  }
  async function refresh() {
    const v = ++version;
    if (!analytical.value.length) {
      results.value = {};
      loading.value = false;
      return;
    }
    if (periodError(layout.value.period) || analytical.value.some((b) => periodError(b.period))) {
      loading.value = false;
      return;
    }
    loading.value = true;
    error.value = '';
    try {
      const r = await analyticsApi.query(
        analytical.value.map(analyticsWidget),
        analysisContext(layout.value),
      );
      if (v !== version) return;
      results.value = Object.fromEntries(r.data.map((x) => [x.id, x]));
      asOf.value = r.asOf;
    } catch (e) {
      if (v === version) error.value = e instanceof Error ? e.message : '分析加载失败';
    } finally {
      if (v === version) loading.value = false;
    }
  }
  const signature = computed(() =>
    JSON.stringify({
      widgets: analytical.value.map(analyticsWidget),
      context: analysisContext(layout.value),
    }),
  );
  watch(signature, () => {
    clearTimeout(timer);
    timer = setTimeout(() => void refresh(), 180);
  });
  function drill(block: OverviewBlock, filter: { key?: string; series?: string }) {
    const result = results.value[block.id];
    if (!result?.asOf) return;
    const request: AnalysisDrill = {
      widget: { ...analyticsWidget(block), currency: result.currency || block.currency },
      context: { ...analysisContext(layout.value), asOf: result.asOf },
      filter,
      title: [
        block.title,
        result.rows?.find((r) => String(r.key) === filter.key)?.name,
        (
          {
            reported: '新增报修',
            completed: '验收完成',
            unfinished: '当前未完成',
            score: '最终评分样本',
            collaboration: '协同参与',
            onTime: '按时提交任务',
            overdue: '到期未完成',
            pending: '待验收 / 待审核',
            cancelled: '人工作废',
            upcoming: '尚未到期',
          } as Record<string, string>
        )[filter.series || ''],
      ]
        .filter(Boolean)
        .join(' · '),
      subtitle: result.period?.label,
      returnLayout: JSON.parse(JSON.stringify(layout.value)),
      templateId: getTemplateId?.(),
    };
    const key = crypto.randomUUID();
    sessionStorage.setItem('eam-analysis:' + key, JSON.stringify(request));
    void router.push({ name: 'home-analytics-details', params: { key } });
  }
  function currency(block: OverviewBlock, currency: string) {
    block.currency = currency;
  }
  function applyPreset() {
    const p = presets.value[presetIndex.value ?? -1];
    if (p)
      layout.value = { companyIds: [], departmentIds: [], ...JSON.parse(JSON.stringify(p.layout)) };
    presetIndex.value = undefined;
  }
  onBeforeUnmount(() => {
    version++;
    clearTimeout(timer);
  });
  return {
    catalog,
    results,
    loading,
    error,
    asOf,
    presets,
    presetIndex,
    analytical,
    initialize,
    refresh,
    drill,
    currency,
    applyPreset,
  };
}
