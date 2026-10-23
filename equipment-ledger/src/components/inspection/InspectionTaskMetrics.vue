<script setup lang="ts">
import { computed } from 'vue';
import { ElProgress, ElTag } from 'element-plus';
import AppIcon from '../common/AppIcon.vue';
import { tr } from '../../i18n';
import type { InspectionTask } from '../../inspection';
import type { ItemResult } from '../../maintenance';
const props = defineProps<{
  task: Pick<
    InspectionTask,
    | 'kind'
    | 'expected'
    | 'checked'
    | 'missing'
    | 'abnormal'
    | 'skipped'
    | 'equipmentCount'
    | 'workSeconds'
    | 'overdue'
    | 'items'
  >;
  results?: ItemResult[];
  skippedEquipmentIds?: string[];
}>();
const view = computed(() => {
  if (!props.results) return props.task;
  const answered = props.results.filter(
    (row) => !row.skipped && (String(row.value ?? '').trim() !== '' || row.implicitNormal),
  );
  const ids = new Set(answered.map((row) => row.id));
  const skipped = new Set(props.skippedEquipmentIds || []);
  return {
    ...props.task,
    checked: answered.length,
    abnormal: props.results.filter((row) => !row.skipped && row.abnormal).length,
    missing: props.task.items.filter((item) => !skipped.has(item.equipmentId) && !ids.has(item.id))
      .length,
    skipped: skipped.size,
  };
});
const progress = computed(() =>
  view.value.expected > 0
    ? Math.min(100, Math.max(0, Math.round((view.value.checked / view.value.expected) * 100)))
    : 0,
);
const metrics = computed(() => [
  ...(props.task.kind !== 'daily'
    ? [
        {
          key: 'equipment',
          label: tr('ui.equipment.text0433'),
          value: props.task.equipmentCount,
          tone: '',
        },
      ]
    : []),
  { key: 'expected', label: tr('ui.equipment.text0434'), value: props.task.expected, tone: '' },
  {
    key: 'checked',
    label: tr('ui.equipment.text0435'),
    value: view.value.checked,
    tone: view.value.checked > 0 ? 'success' : '',
  },
  {
    key: 'missing',
    label: tr('ui.equipment.text0436'),
    value: view.value.missing,
    tone: view.value.missing > 0 ? 'warning' : '',
  },
  {
    key: 'abnormal',
    label: tr('ui.equipment.text0210'),
    value: view.value.abnormal,
    tone: view.value.abnormal > 0 ? 'danger' : '',
  },
  { key: 'skipped', label: tr('ui.equipment.text0437'), value: view.value.skipped, tone: '' },
]);
</script>
<template>
  <section class="inspection-metrics" :aria-label="tr('inspection.metrics.title')">
    <dl class="inspection-metrics-values">
      <div
        v-for="metric in metrics"
        :key="metric.key"
        class="inspection-metric"
        :class="metric.tone"
        :data-metric="metric.key"
      >
        <dt>
          <AppIcon v-if="metric.key === 'abnormal'" name="Warning" aria-hidden="true" />{{
            metric.label
          }}
        </dt>
        <dd>{{ metric.value }}</dd>
      </div>
      <div class="inspection-metric inspection-metric-duration">
        <dt><AppIcon name="Clock" aria-hidden="true" />{{ tr('inspection.metrics.duration') }}</dt>
        <dd>
          <span>{{ Math.floor(task.workSeconds / 60) }}</span
          ><small>{{ tr('ui.equipment.text0439') }}</small
          ><span>{{ task.workSeconds % 60 }}</span
          ><small>{{ tr('ui.equipment.text0440') }}</small>
        </dd>
      </div>
    </dl>
    <div class="inspection-metrics-progress">
      <div class="inspection-progress-label">
        <span>{{ tr('inspection.metrics.progress') }}</span
        ><span>{{ view.checked }} / {{ task.expected }}</span
        ><ElTag v-if="task.overdue" type="warning" size="small">{{
          tr('ui.equipment.text0441')
        }}</ElTag>
      </div>
      <ElProgress
        :percentage="progress"
        :stroke-width="4"
        :show-text="false"
        :color="progress === 100 ? 'var(--el-color-success)' : 'var(--el-color-primary)'"
      />
    </div>
  </section>
</template>
<style scoped>
.inspection-metrics {
  margin-top: 16px;
  padding: 16px;
  border-radius: 6px;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
}
.inspection-metrics-values {
  display: flex;
  flex-wrap: wrap;
  gap: 16px 24px;
  margin: 0;
}
.inspection-metric {
  flex: 1 0 72px;
  min-width: 0;
}
.inspection-metric dt {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 13px;
  line-height: 20px;
  color: var(--el-text-color-regular);
}
.inspection-metric dt :deep(.app-icon) {
  width: 16px;
  height: 16px;
}
.inspection-metric dd {
  margin: 6px 0 0;
  font-size: 20px;
  font-weight: 600;
  line-height: 26px;
  font-variant-numeric: tabular-nums;
}
.success dd {
  color: var(--el-color-success-dark-2);
}
.warning dd {
  color: var(--el-color-warning-dark-2);
}
.danger dd,
.danger dt :deep(.app-icon) {
  color: var(--el-color-danger-dark-2);
}
.inspection-metric-duration {
  flex: 0 0 auto;
  min-width: 132px;
  margin-left: auto;
  padding-left: 24px;
  border-left: 1px solid var(--el-border-color);
}
.inspection-metric-duration dd {
  display: flex;
  align-items: baseline;
  gap: 4px;
  font-size: 18px;
  white-space: nowrap;
}
.inspection-metric-duration small {
  margin-right: 4px;
  font-size: 12px;
  font-weight: 400;
  color: var(--el-text-color-regular);
}
.inspection-metrics-progress {
  margin-top: 14px;
}
.inspection-progress-label {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 6px;
  font-size: 12px;
  color: var(--el-text-color-regular);
  font-variant-numeric: tabular-nums;
}
.inspection-progress-label > :last-child {
  margin-left: auto;
}
.inspection-metrics-progress :deep(.el-progress-bar__inner) {
  transition: width 180ms ease;
}
@media (max-width: 700px) {
  .inspection-metric {
    flex-basis: calc((100% - 48px) / 3);
  }
  .inspection-metric-duration {
    margin-left: 0;
    padding-left: 0;
    border-left: 0;
  }
}
@media (prefers-reduced-motion: reduce) {
  .inspection-metrics-progress :deep(.el-progress-bar__inner) {
    transition: none;
  }
}
</style>
