<script setup lang="ts">import { tr } from '../../i18n';

import { ref, onMounted, onBeforeUnmount, watch, nextTick } from 'vue';
import { init, use, type EChartsType, type EChartsCoreOption } from 'echarts/core';
import { BarChart, LineChart } from 'echarts/charts';
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components';
import { analyticsColors } from './analyticsPalette';
import { CanvasRenderer } from 'echarts/renderers';
use([BarChart, LineChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer]);
const props = defineProps<{ option: EChartsCoreOption; height?: number }>();
const emit = defineEmits<{ select: [{ dataIndex: number; seriesIndex: number; name: string }] }>();
const root = ref<HTMLDivElement>();
let chart: EChartsType | undefined,
  resize: ResizeObserver | undefined,
  theme: MutationObserver | undefined;
function render() {
  if (!root.value || !chart) return;
  const css = getComputedStyle(root.value);
  chart.setOption(
    {
      color: analyticsColors,
      textStyle: {
        fontFamily: 'Microsoft YaHei, sans-serif',
        fontSize: 13,
        color: css.getPropertyValue('--text').trim() || '#273347',
      },
      ...props.option,
    },
    true,
  );
}
onMounted(() => {
  if (!root.value) return;
  chart = init(root.value, undefined, { renderer: 'canvas' });
  chart.on('click', (p: any) =>
    emit('select', { dataIndex: p.dataIndex, seriesIndex: p.seriesIndex, name: p.name }),
  );
  resize = new ResizeObserver(() => chart?.resize());
  resize.observe(root.value);
  theme = new MutationObserver(render);
  theme.observe(document.documentElement, {
    attributes: true,
    attributeFilter: ['class', 'style', 'data-theme'],
  });
  render();
});
watch(
  () => props.option,
  async () => {
    await nextTick();
    render();
  },
  { deep: true },
);
onBeforeUnmount(() => {
  resize?.disconnect();
  theme?.disconnect();
  chart?.dispose();
});
</script>
<template>
  <div
    ref="root"
    class="analytics-chart"
    :style="{ height: `${height || 280}px` }"
    role="img"
    :aria-label="tr('ui.equipment.text1297')"
  />
</template>
<style scoped>
.analytics-chart {
  width: 100%;
  min-width: 0;
}
</style>
