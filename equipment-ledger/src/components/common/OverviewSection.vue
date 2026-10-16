<script setup lang="ts">
import { ElButton, ElInput, ElSelect, ElOption } from 'element-plus';
import { computed } from 'vue';
import {
  isAnalytics,
  widgetPeriodOptions,
  type AnalyticsMetric,
  type AnalyticsOrganization,
} from '../../analytics';
import AnalyticsPeriodSelect from './AnalyticsPeriodSelect.vue';
import AppIcon from './AppIcon.vue';
import AppFormField from './AppFormField.vue';
import type { OverviewBlock } from '../../homeOverview';
const props = defineProps<{
  block: OverviewBlock;
  metric?: AnalyticsMetric;
  organizations?: AnalyticsOrganization[];
  index: number;
  editing: boolean;
  busy: boolean;
  first: boolean;
  last: boolean;
  removable: boolean;
  teamAllowed: boolean;
}>();
const analytic = computed(() => isAnalytics(props.block));
const emit = defineEmits<{
  update: [OverviewBlock];
  remove: [string];
  move: [string, number];
  start: [string];
  end: [];
  drop: [string];
}>();
const icons: Record<string, string> = {
  work: 'Todo',
  anomalies: 'Warning',
  recent: 'Clock',
  shortcuts: 'Grid',
  inventory: 'Warehouse',
  surplus: 'Warehouse',
  load: 'User',
  stats: 'Grid',
  created: 'Document',
};
function startDrag(event: DragEvent) {
  if (!props.editing || props.busy) {
    event.preventDefault();
    return;
  }
  if (event.dataTransfer) {
    event.dataTransfer.setData('text/plain', props.block.id);
    event.dataTransfer.effectAllowed = 'move';
  }
  emit('start', props.block.id);
}
function change(key: string, value: unknown) {
  emit('update', { ...props.block, [key]: value });
}
</script>
<template>
  <section
    class="overview-section"
    :class="{ 'editing-section': editing }"
    :data-overview-section="block.id"
    @dragover.prevent
    @drop.stop.prevent="emit('drop', block.id)"
  >
    <header
      class="section-heading"
      :draggable="editing && !busy"
      @dragstart="startDrag"
      @dragend="emit('end')"
    >
      <div class="section-title">
        <AppIcon :name="editing ? 'HamburgerButton' : icons[block.type] || 'Document'" />
        <h2>{{ block.title || '未命名区块' }}</h2>
        <span v-if="block.scope === 'team'" class="scope-label">团队</span
        ><span
          v-if="analytic && block.period?.preset && block.period.preset !== 'follow'"
          class="scope-label"
          >{{
            widgetPeriodOptions.find((p) => p.value === block.period?.preset)?.label || '独立周期'
          }}</span
        >
      </div>
      <div v-if="editing" class="section-actions">
        <ElButton
          aria-label="上移区块"
          title="上移"
          :disabled="first || busy"
          @click="emit('move', block.id, -1)"
          ><AppIcon name="Up" /></ElButton
        ><ElButton
          aria-label="下移区块"
          title="下移"
          :disabled="last || busy"
          @click="emit('move', block.id, 1)"
          ><AppIcon name="Down" /></ElButton
        ><ElButton
          aria-label="移除区块"
          title="移除"
          :disabled="!removable || busy"
          @click="emit('remove', block.id)"
          ><AppIcon name="Close"
        /></ElButton>
      </div>
      <slot v-else name="action" />
    </header>
    <div v-if="editing" class="section-settings">
      <AppFormField label="标题" :prop="`blocks.${index}.title`"
        ><ElInput
          :model-value="block.title"
          :disabled="busy"
          maxlength="80"
          @update:model-value="change('title', $event)"
      /></AppFormField>
      <AppFormField label="位置"
        ><ElSelect
          :model-value="block.column"
          :disabled="busy || block.type === 'work'"
          @update:model-value="change('column', $event)"
          ><ElOption value="main" label="主栏" /><ElOption value="side" label="侧栏" /></ElSelect
      ></AppFormField>
      <AppFormField
        v-if="!analytic || metric?.chart === 'bar'"
        :label="analytic ? '排名条数' : '显示条数'"
        ><ElSelect
          :model-value="analytic ? block.topN || 10 : block.limit"
          :disabled="busy"
          @update:model-value="change(analytic ? 'topN' : 'limit', $event)"
          ><ElOption v-for="n in [5, 10, 20]" :key="n" :value="n" :label="`${n}条`" /></ElSelect
      ></AppFormField>
      <AppFormField v-if="analytic" label="统计周期"
        ><AnalyticsPeriodSelect
          :model-value="block.period || { preset: 'follow' }"
          independent
          :disabled="busy"
          @update:model-value="change('period', $event)"
      /></AppFormField>
      <AppFormField
        v-if="['work', 'stats', 'load', 'created'].includes(block.type)"
        label="业务范围"
        ><ElSelect
          :model-value="block.module || '__all__'"
          :disabled="busy"
          @update:model-value="change('module', $event === '__all__' ? '' : $event)"
          ><ElOption value="__all__" label="全部业务" /><ElOption
            value="repair"
            label="维修" /><ElOption value="maintenance" label="保养" /><ElOption
            value="inspection"
            label="点巡检" /><ElOption
            v-if="block.type === 'work'"
            value="inventory"
            label="库存" /></ElSelect
      ></AppFormField>
      <AppFormField
        v-if="!['work', 'shortcuts', 'recent', 'inventory'].includes(block.type)"
        label="数据范围"
        ><ElSelect
          :model-value="block.scope"
          :disabled="busy || block.type === 'load'"
          @update:model-value="change('scope', $event)"
          ><ElOption v-if="block.type !== 'load'" value="personal" label="个人" /><ElOption
            v-if="teamAllowed"
            value="team"
            label="授权团队" /></ElSelect
      ></AppFormField>
      <AppFormField
        v-if="analytic && teamAllowed && block.scope === 'team'"
        label="公司（与页面范围取交集）"
        ><ElSelect
          :model-value="block.companyIds || []"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          :disabled="busy"
          @update:model-value="change('companyIds', $event)"
          ><ElOption
            v-for="o in (organizations || []).filter((o) => o.org_type === '公司')"
            :key="o.id"
            :value="o.id"
            :label="o.name" /></ElSelect
      ></AppFormField>
      <AppFormField v-if="analytic && teamAllowed && block.scope === 'team'" label="部门（含下级）"
        ><ElSelect
          :model-value="block.departmentIds || []"
          multiple
          filterable
          clearable
          collapse-tags
          collapse-tags-tooltip
          :disabled="busy"
          @update:model-value="change('departmentIds', $event)"
          ><ElOption
            v-for="o in (organizations || []).filter((o) => o.org_type !== '公司')"
            :key="o.id"
            :value="o.id"
            :label="o.name" /></ElSelect
      ></AppFormField>
    </div>
    <div class="section-content"><slot /></div>
  </section>
</template>
<style scoped>
.overview-section {
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}
.editing-section {
  border-style: dashed;
}
.section-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 14px 16px 12px;
}
.editing-section .section-heading {
  cursor: grab;
}
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.section-title h2 {
  font-size: 14px;
  margin: 0;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.section-title .app-icon {
  color: var(--brand);
  flex-shrink: 0;
}
.scope-label {
  color: var(--brand);
  background: var(--brand-soft);
  border-radius: 4px;
  padding: 2px 4px;
  font-size: 13px;
  white-space: nowrap;
}
.section-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.section-actions .el-button {
  margin: 0;
  width: 28px;
  height: 28px;
  padding: 0;
}
.section-settings {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid var(--line);
  margin-bottom: 12px;
}
.section-settings .app-form-field {
  min-width: 0;
  margin-bottom: 18px;
}
.section-settings .app-form-field:first-child {
  grid-column: 1/-1;
}
.section-settings :deep(.el-select) {
  width: 100%;
}
.section-content {
  min-width: 0;
}
</style>
