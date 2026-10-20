<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import {
  ElAlert,
  ElButton,
  ElSelect,
  ElOption,
  ElTableColumn,
  ElTooltip,
  ElSkeleton,
} from 'element-plus';
import AppIcon from './AppIcon.vue';
import { analyticsSeries, categoryColor } from './analyticsPalette';
import AnalyticsChart from './AnalyticsChart.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import {
  qualityLabels,
  type AnalyticsMetric,
  type AnalyticsResult,
  type AnalysisRow,
} from '../../analytics';
const props = defineProps<{
  interactive?: boolean;
  topN?: number;
  metric?: AnalyticsMetric;
  result?: AnalyticsResult;
  loading?: boolean;
  error?: string;
}>();
const emit = defineEmits<{
  drill: [{ key?: string; series?: string }];
  retry: [];
  currency: [string];
}>();
const rows = computed(() => props.result?.rows || []),
  summary = computed(() => props.result?.summary || {});
const quality = computed(() =>
  Object.entries(props.result?.quality || {})
    .filter(([k, v]) => qualityLabels[k] && Number(v) > 0)
    .map(([k, v]) => `${qualityLabels[k]} ${v} 条`),
);
const money = (value: unknown) =>
  Number(value || 0).toLocaleString('zh-CN', {
    minimumFractionDigits: 2,
    maximumFractionDigits: 2,
  });
const rate = (value: unknown) =>
  value == null ? '暂无可评价任务' : `${Number(value).toFixed(2)}%`;
const execution = computed(() => ['execution', 'departments'].includes(props.metric?.chart || ''));
const stats = computed(() => [
  { get label() { return tr('ui.equipment.text1299'); }, key: 'total' },
  { get label() { return tr('ui.equipment.text0760'); }, key: 'completed' },
  { get label() { return tr('ui.equipment.text1300'); }, key: 'pending' },
  { get label() { return tr('ui.equipment.text1301'); }, key: 'overdue' },
  { get label() { return tr('ui.equipment.text1302'); }, key: 'upcoming' },
  { get label() { return tr('ui.equipment.text1303'); }, key: 'cancelled' },
]);
const value = (key: string) => summary.value[key] ?? 0;
const option = computed(() => {
  const data = rows.value;
  if (props.metric?.chart === 'line') {
    const repair = props.metric.id === 'repair_trend';
    return {
      tooltip: { trigger: 'axis', renderMode: 'richText' },
      legend: { bottom: 0 },
      grid: { left: 55, right: 22, top: 20, bottom: 58 },
      xAxis: { type: 'category', data: data.map((r) => r.name), axisLabel: { fontSize: 11 } },
      yAxis: {
        type: 'value',
        minInterval: repair ? 1 : undefined,
        name: repair ? '单' : props.result?.currency || '金额',
        splitLine: { lineStyle: { color: '#edf0f5' } },
      },
      series: (repair
        ? [
            ['reported', '新增报修'],
            ['completed', '验收完成'],
          ]
        : [['value', '实际消耗']]
      ).map(([key, name]) => ({
        name,
        type: 'line',
        smooth: false,
        symbolSize: 7,
        lineStyle: { width: 3 },
        itemStyle: {
          color: repair
            ? key === 'reported'
              ? analyticsSeries.reported
              : analyticsSeries.completed
            : analyticsSeries.material,
        },
        areaStyle: repair ? undefined : { color: 'rgba(56,180,220,0.08)' },
        data: data.map((r) => Number(r[key!] || 0)),
      })),
    };
  }
  return {
    tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' }, renderMode: 'richText' },
    grid: { left: 130, right: 100, top: 12, bottom: 25 },
    xAxis: {
      type: 'value',
      minInterval: props.metric?.money ? undefined : 1,
      splitLine: { lineStyle: { color: '#edf0f5' } },
    },
    yAxis: {
      type: 'category',
      inverse: true,
      data: data.map((r) => r.name || r.code || '未确认设备'),
      axisTick: { show: false },
      axisLine: { show: false },
      axisLabel: { width: 115, overflow: 'truncate', fontSize: 12 },
    },
    series: [
      {
        type: 'bar',
        barMaxWidth: 16,
        itemStyle: { borderRadius: [0, 3, 3, 0] },
        label: {
          show: true,
          position: 'right',
          fontSize: 12,
          formatter: (p: any) =>
            props.metric?.money
              ? money(p.value)
              : rows.value[p.dataIndex]?.percentage != null
                ? `${p.value}（${Number(rows.value[p.dataIndex]?.percentage).toFixed(2)}%）`
                : String(p.value),
        },
        data: data.map((r) => ({
          value: Number(r.value || 0),
          itemStyle: { color: categoryColor(String(r.key)) },
        })),
      },
    ],
  };
});
function chartClick(p: { dataIndex: number; seriesIndex: number }) {
  if (props.interactive === false) return;
  const row = rows.value[p.dataIndex];
  if (row)
    emit('drill', {
      key: String(row.key),
      ...(props.metric?.id === 'repair_trend'
        ? { series: p.seriesIndex === 1 ? 'completed' : 'reported' }
        : {}),
    });
}
</script>
<template>
  <div class="analysis-widget">
    <div class="analysis-caption">
      <ElTooltip :content="metric?.definition || result?.definition || ''" placement="top"
        ><span
          >{{ result?.period?.label || tr('ui.equipment.text1298')
          }}<template v-if="metric?.chart === 'bar'"> {{ tr('ui.equipment.text1304') }}{{ topN || 10 }}{{ tr('ui.equipment.text0394') }}</template> ·
          {{ execution ? tr('ui.equipment.text1305') : metric?.unit }}
          <AppIcon name="Help" class="definition-help" /></span></ElTooltip
      ><ElSelect
        v-if="result?.currencies?.length"
        :model-value="result.currency"
        :aria-label="tr('ui.equipment.text1306')"
        @update:model-value="emit('currency', String($event))"
        ><ElOption v-for="c in result.currencies" :key="c" :label="c" :value="c" /></ElSelect
      ><ElButton
        v-if="result && result.status === 'ok'"
        type="primary"
        link
        :disabled="interactive === false"
        @click="emit('drill', {})"
        >{{ tr('ui.equipment.text1307') }}</ElButton
      >
    </div>
    <ElSkeleton v-if="loading && !result" :rows="4" animated />
    <ElAlert
      v-else-if="error || result?.status === 'error'"
      :title="error || result?.message || tr('ui.equipment.text0160')"
      type="error"
      :closable="false"
      ><ElButton link type="primary" @click="emit('retry')">{{ tr('ui.equipment.text0299') }}</ElButton></ElAlert
    >
    <p v-else-if="result?.status === 'forbidden'" class="analysis-empty">
      {{ tr('ui.equipment.text1308') }}
    </p>
    <p
      v-else-if="result?.status === 'empty' || result?.status === 'data_missing'"
      class="analysis-empty"
    >
      {{
        result.status === 'data_missing'
          ? tr('ui.equipment.text1309')
          : tr('ui.equipment.text1310')
      }}
    </p>
    <template v-else-if="result">
      <template v-if="execution">
        <div class="execution-counts">
          <ElButton
            v-for="s in stats"
            :key="s.key"
            text
            :disabled="interactive === false"
            @click="emit('drill', { series: s.key })"
            ><span>{{ s.label }}</span
            ><strong :class="'fact-' + s.key">{{ value(s.key) }}</strong></ElButton
          >
        </div>
        <div class="execution-rates">
          <ElButton
            text
            :disabled="interactive === false"
            @click="emit('drill', { series: 'completed' })"
            ><span>{{ tr('ui.equipment.text1311') }}</span><b>{{ rate(summary.completionRate) }}</b></ElButton
          ><ElButton
            text
            :disabled="interactive === false"
            @click="emit('drill', { series: 'onTime' })"
            ><span>{{ tr('ui.equipment.text1312') }}</span><b>{{ rate(summary.onTimeRate) }}</b
            ><small
              >{{ summary.onTime || 0 }} / {{ summary.evaluable || 0 }} {{ tr('ui.equipment.text1313') }}</small
            ></ElButton
          >
        </div>
        <div v-if="summary.unknownDeadlineUnfinished" class="execution-note">
          {{ tr('ui.equipment.text1314') }} {{ summary.unknownDeadlineUnfinished }} {{ tr('ui.equipment.text1315') }}
        </div>
        <div class="execution-note">
          {{ tr('ui.equipment.text1316') }} {{ summary.completedOnTime || 0 }}{{ tr('ui.equipment.text1317') }}
          {{ summary.completedLate || 0 }}{{ tr('ui.equipment.text1318') }}
        </div>
        <AppBusinessTable
          v-if="metric?.id !== 'maintenance_execution'"
          :rows="rows"
          :fill="false"
          :pagination="false"
          :framed="false"
          row-key="key"
          :max-height="310"
          ><ElTableColumn
            :label="metric?.id === 'inspection_execution' ? tr('ui.equipment.text1319') : tr('ui.equipment.text1320')"
            prop="name"
            min-width="130"
            show-overflow-tooltip
          /><ElTableColumn :label="tr('ui.equipment.text1299')" prop="total" width="95" align="right"
            ><template #default="{ row }"
              ><ElButton
                link
                type="primary"
                :disabled="interactive === false"
                @click="emit('drill', { key: String(row.key) })"
                >{{ row.total }}</ElButton
              ></template
            ></ElTableColumn
          ><ElTableColumn :label="tr('ui.equipment.text0760')" prop="completed" width="95" align="right"
            ><template #default="{ row }"
              ><ElButton
                link
                type="primary"
                :disabled="interactive === false"
                @click="emit('drill', { key: String(row.key), series: 'completed' })"
                >{{ row.completed }}</ElButton
              ></template
            ></ElTableColumn
          ><ElTableColumn :label="tr('ui.equipment.text1321')" width="105" align="right"
            ><template #default="{ row }">{{ rate(row.completionRate) }}</template></ElTableColumn
          ><ElTableColumn :label="tr('ui.equipment.text1312')" width="130" align="right"
            ><template #default="{ row }">{{ rate(row.onTimeRate) }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </template>
      <AppBusinessTable
        v-else-if="metric?.chart === 'table'"
        :rows="rows"
        :fill="false"
        :pagination="false"
        :framed="false"
        row-key="key"
        :max-height="350"
        ><ElTableColumn
          :label="tr('ui.equipment.text1322')"
          prop="name"
          min-width="100"
          show-overflow-tooltip
        /><ElTableColumn
          v-for="c in [
            { key: 'completed', label: '验收完成' },
            { key: 'unfinished', label: '当前未完成' },
            { key: 'score', label: '平均评分' },
            { key: 'collaboration', label: '协同参与' },
          ]"
          :key="c.key"
          :prop="c.key"
          :label="c.label"
          sortable
          min-width="105"
          align="right"
          ><template #default="{ row }"
            ><ElButton
              v-if="row[c.key] != null"
              link
              type="primary"
              :disabled="interactive === false"
              @click="emit('drill', { key: String(row.key), series: c.key })"
              >{{
                c.key === 'score'
                  ? `${Number(row.score).toFixed(2)}（${row.scoreCount}份）`
                  : row[c.key]
              }}</ElButton
            ><span v-else>{{ tr('ui.equipment.text1323') }}</span></template
          ></ElTableColumn
        ></AppBusinessTable
      >
      <template v-else
        ><div v-if="metric?.money" class="money-total">
          {{ tr('ui.equipment.text1324') }} <b>{{ money(summary.totalAmount) }}</b> {{ result.currency }}
        </div>
        <AnalyticsChart
          :option="option"
          :height="
            metric?.chart === 'bar' ? Math.min(420, Math.max(220, rows.length * 30 + 30)) : 280
          "
          @select="chartClick"
        />
        <div v-if="metric?.id === 'equipment_material_ranking'" class="attribution-summary">
          <ElButton
            link
            type="primary"
            :disabled="interactive === false"
            @click="emit('drill', { key: '__shared__' })"
            >{{ tr('ui.equipment.text1325') }} {{ money(summary.shared?.amount) }} {{ result.currency }}</ElButton
          ><ElButton
            link
            type="primary"
            :disabled="interactive === false"
            @click="emit('drill', { key: '__unassigned__' })"
            >{{ tr('ui.equipment.text1326') }} {{ money(summary.unassigned?.amount) }} {{ result.currency }}</ElButton
          >
        </div></template
      >
    </template>
    <div v-if="quality.length" class="analysis-quality">
      {{ tr('ui.equipment.text1327') }}{{ quality.join('；') }}{{ tr('ui.equipment.text1328') }}
    </div>
    <footer v-if="result?.asOf">
      {{ tr('ui.equipment.text1329') }}{{ result.asOf.replace('T', ' ').slice(0, 19)
      }}<span v-if="metric?.id === 'personnel'"> {{ tr('ui.equipment.text1330') }}</span
      ><span v-if="loading"> {{ tr('ui.equipment.text1331') }}</span>
    </footer>
  </div>
</template>
<style scoped>
.analysis-widget {
  padding: 0 16px 12px;
  min-width: 0;
}
.analysis-caption {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  font-size: 12px;
  color: var(--text);
}
.analysis-caption > .el-button {
  margin-left: auto;
}
.analysis-caption > .el-select {
  width: 85px;
  flex: 0 0 85px;
  margin-left: auto;
}
.definition-help {
  color: var(--brand);
  cursor: help;
}
.analysis-empty {
  padding: 50px 8px;
  text-align: center;
  line-height: 1.7;
}
.analysis-quality {
  font-size: 12px;
  line-height: 1.7;
  background: var(--canvas);
  padding: 8px;
  border-radius: 4px;
  margin-top: 10px;
}
.analysis-widget footer {
  font-size: 11px;
  margin-top: 10px;
  color: var(--text-secondary);
}
.execution-counts {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8px;
}
.execution-counts .el-button {
  margin: 0;
  height: auto;
  padding: 10px 4px;
  background: var(--canvas);
}
.execution-counts :deep(.el-button > span) {
  display: flex;
  flex-direction: column;
  gap: 6px;
  font-size: 12px;
}
.execution-counts strong {
  font-size: 22px;
  font-weight: 650;
  color: var(--brand);
}
.execution-counts .fact-completed {
  color: #0fa38f;
}
.execution-counts .fact-overdue {
  color: #ed8050;
}
.execution-counts .fact-pending {
  color: #9170e3;
}
.execution-counts .fact-cancelled {
  color: #b16c90;
}
.execution-rates {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  margin-top: 12px;
}
.execution-rates > .el-button {
  height: auto;
  margin: 0;
  padding: 10px 4px;
}
.execution-rates :deep(.el-button > span) {
  display: flex;
  flex-direction: column;
  gap: 8px;
}
.execution-rates b {
  font-size: 18px;
}
.execution-rates small {
  font-size: 11px;
}
.execution-note {
  font-size: 12px;
  line-height: 1.7;
  margin: 8px 0 12px;
}
.money-total {
  font-size: 13px;
  display: flex;
  align-items: baseline;
  gap: 8px;
}
.money-total b {
  font-size: 24px;
  color: var(--brand);
  font-weight: 650;
}
.attribution-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}
.attribution-summary > .el-button {
  margin: 0;
}
</style>
