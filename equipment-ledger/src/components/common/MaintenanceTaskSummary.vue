<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import { ElButton, ElProgress } from 'element-plus';
import AppIcon from './AppIcon.vue';
import type { MaintenanceTaskStatistics, MaintenanceProjectFilter } from '../../maintenance';
const props = defineProps<{
  statistics: MaintenanceTaskStatistics;
  modelValue: MaintenanceProjectFilter;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: MaintenanceProjectFilter] }>();
const metrics = computed(() => [
  {
    key: 'all' as const,
    get title(){return tr('ui.common.dynamic57');},
    value: props.statistics.total,
    icon: 'Todo',
    tone: 'total',
    description: '本次任务生成时的保养项目，点击查看全部',
  },
  {
    key: 'completed' as const,
    get title(){return tr('ui.common.dynamic58');},
    value: props.statistics.completed,
    icon: 'Check',
    tone: 'completed',
    description: '已提交结果并满足录入要求的项目，异常项目也可计入完成',
  },
  {
    key: 'abnormal' as const,
    get title(){return tr('ui.common.dynamic59');},
    value: props.statistics.abnormal,
    icon: 'Warning',
    tone: props.statistics.abnormal ? 'abnormal' : 'total',
    description: '判定异常的保养项目，处理后仍保留异常事实；与关联异常记录数分开统计',
  },
]);
</script>
<template>
  <section
    class="project-statistics"
    :aria-label="tr('ui.equipment.text1502')"
    :title="tr('ui.equipment.text1503')"
  >
    <div class="project-statistics-grid">
      <ElButton
        v-for="metric in metrics"
        :key="metric.key"
        class="project-stat"
        :class="{ 'is-active': modelValue === metric.key }"
        :disabled="metric.value === null"
        :aria-pressed="modelValue === metric.key"
        :title="metric.description"
        @click="emit('update:modelValue', metric.key)"
      >
        <span class="stat-icon" :class="metric.tone"><AppIcon :name="metric.icon" /></span>
        <span class="stat-content"
          ><span class="stat-title">{{ metric.title }}</span
          ><span class="stat-value"
            >{{ metric.value ?? tr('ui.equipment.text1504')
            }}<span v-if="metric.value !== null" class="stat-unit">{{ tr('ui.equipment.text0394') }}</span></span
          ></span
        >
      </ElButton>
      <div
        class="project-stat stat-rate"
        :title="tr('ui.equipment.text1505')"
      >
        <span class="stat-icon total"><AppIcon name="Proportion" /></span>
        <div class="stat-content">
          <span class="stat-title">{{ tr('ui.equipment.text1506') }}</span
          ><span class="stat-value"
            >{{
              statistics.completionRate === null
                ? statistics.total
                  ? tr('ui.equipment.text1504')
                  : tr('ui.equipment.text1507')
                : statistics.completionRate.toFixed(2)
            }}<span v-if="statistics.completionRate !== null" class="stat-unit">%</span></span
          >
          <ElProgress
            v-if="statistics.completionRate !== null"
            :percentage="statistics.completionRate"
            :stroke-width="4"
            :show-text="false"
            color="var(--brand, #245dff)"
          />
        </div>
      </div>
    </div>
    <p v-if="!statistics.reliable" class="stat-note">
      {{ tr('ui.equipment.text1508') }}
    </p>
  </section>
</template>
<style scoped>
.project-statistics {
  container-type: inline-size;
  background: #fff;
  border: 1px solid var(--border-color, #e4e9f1);
  border-radius: 8px;
  overflow: hidden;
}
.project-statistics-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
}
.project-stat {
  display: flex;
  align-items: center;
  gap: 12px;
  height: auto;
  min-height: 86px;
  min-width: 0;
  margin: 0;
  padding: 14px 18px;
  border: 0;
  border-radius: 0;
  background: transparent;
  color: var(--text, #263246);
  text-align: left;
  font-weight: normal;
}
.project-stat + .project-stat {
  margin-left: 0;
  border-left: 1px solid #edf1f7;
}
.project-stat :deep(> span) {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.project-stat.is-active {
  background: var(--brand-soft, #eef3ff);
}
.project-stat:hover:not(:disabled),
.project-stat:focus-visible {
  background: var(--brand-soft, #eef3ff);
  color: var(--text, #263246);
}
.project-stat:focus-visible {
  outline: 2px solid var(--brand, #245dff);
  outline-offset: -2px;
}
.stat-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 36px;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  font-size: 21px;
}
.stat-icon.total {
  background: var(--brand-soft, #edf3ff);
  color: var(--brand, #245dff);
}
.stat-icon.completed {
  background: #e5f8f2;
  color: #09a781;
}
.stat-icon.abnormal {
  background: #fff2df;
  color: #ec8b16;
}
.stat-content {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 6px;
  min-width: 0;
  flex: 1;
}
.stat-title {
  font-size: 13px;
  line-height: 18px;
}
.stat-value {
  font-size: 24px;
  font-weight: 700;
  line-height: 28px;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.stat-unit {
  margin-left: 5px;
  font-size: 12px;
  font-weight: normal;
}
.stat-rate .el-progress {
  width: 100%;
}
.stat-note {
  margin: 0;
  padding: 8px 16px;
  border-top: 1px solid #edf1f7;
  font-size: 12px;
  color: var(--text, #263246);
  line-height: 18px;
}
@container (max-width: 650px) {
  .project-statistics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .project-stat:nth-child(3) {
    border-left: 0;
  }
  .project-stat:nth-child(n + 3) {
    border-top: 1px solid #edf1f7;
  }
  .project-stat {
    padding: 12px;
  }
}
</style>
