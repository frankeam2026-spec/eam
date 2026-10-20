<script setup lang="ts">import { tr } from '../../i18n';

import { computed, ref, onMounted } from 'vue';
import { ElForm, ElSelect, ElOption, ElButton, ElAlert, type FormInstance } from 'element-plus';
import OverviewSection from './OverviewSection.vue';
import AnalyticsPeriodSelect from './AnalyticsPeriodSelect.vue';
import { analyticsApi, isAnalytics, periodError, type AnalyticsCatalog } from '../../analytics';
import { overviewLabels, type OverviewLayout, type OverviewBlock } from '../../homeOverview';
const dragId = ref('');
const model = defineModel<OverviewLayout>({ required: true });
const props = defineProps<{
  preview?: boolean;
  busy?: boolean;
  teamAllowed: boolean;
  legacyCatalog: string[];
}>();
const catalog = ref<AnalyticsCatalog>(),
  presets = ref<{ name: string; layout: OverviewLayout }[]>([]),
  error = ref(''),
  selected = ref(''),
  preset = ref<number>(),
  form = ref<FormInstance>();
const available = computed(() =>
  [
    ...new Set([
      ...props.legacyCatalog
        .map((t) => (t === 'todo' ? 'work' : t))
        .filter((t) => t in overviewLabels),
      ...(catalog.value?.data || []).map((m) => 'analytics:' + m.id),
    ]),
  ].filter((t) => isAnalytics({ type: t }) || !model.value.blocks.some((b) => b.type === t)),
);
const rules = computed(() =>
  Object.fromEntries(
    model.value.blocks.map((b, i) => [
      `blocks.${i}.title`,
      [
        { required: true, whitespace: true, get message() { return tr('ui.equipment.text1555'); }, trigger: 'blur' },
        { max: 80, get message() { return tr('ui.equipment.text1556'); }, trigger: 'blur' },
      ],
    ]),
  ),
);
function add() {
  const type = selected.value;
  if (!available.value.includes(type) || model.value.blocks.length >= 12) return;
  const metric = catalog.value?.data.find((m) => m.id === type.slice(10));
  model.value.blocks.push({
    id: crypto.randomUUID(),
    type,
    title: metric?.name || overviewLabels[type] || type,
    width: 12,
    column: 'main',
    limit: 5,
    scope: type === 'load' ? 'team' : 'personal',
    ...(metric ? { topN: 10, period: { preset: metric.period } } : {}),
  });
  selected.value = '';
}
function update(b: OverviewBlock) {
  model.value.blocks = model.value.blocks.map((v) => (v.id === b.id ? b : v));
}
function move(id: string, offset: number) {
  const i = model.value.blocks.findIndex((b) => b.id === id),
    j = i + offset;
  if (j >= 0 && j < model.value.blocks.length) {
    const b = model.value.blocks[i]!;
    model.value.blocks.splice(i, 1);
    model.value.blocks.splice(j, 0, b);
  }
}
function drop(column: 'main' | 'side', target?: string) {
  const b = model.value.blocks.find((v) => v.id === dragId.value);
  if (
    !b ||
    props.preview ||
    props.busy ||
    (b.type === 'work' && column === 'side') ||
    b.id === target
  )
    return;
  const next = model.value.blocks.filter((v) => v.id !== b.id);
  const index = target ? next.findIndex((v) => v.id === target) : -1;
  next.splice(index < 0 ? next.length : index, 0, { ...b, column });
  model.value.blocks = next;
  dragId.value = '';
}
function apply() {
  const p = presets.value[preset.value ?? -1];
  if (p) model.value = JSON.parse(JSON.stringify(p.layout));
  preset.value = undefined;
}
const validate = async () =>
  !periodError(model.value.period) &&
  !model.value.blocks.some((b) => periodError(b.period)) &&
  !!(await form.value?.validate().catch(() => false));
defineExpose({ validate });
onMounted(async () => {
  try {
    catalog.value = await analyticsApi.catalog();
    presets.value = (await analyticsApi.presets()).data;
    for (const m of catalog.value.data) overviewLabels['analytics:' + m.id] = m.name;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text1557');
  }
});
</script>
<template>
  <ElForm
    ref="form"
    :model="model"
    :rules="rules"
    label-position="top"
    class="overview-template"
    scroll-to-error
    @submit.prevent
    ><ElAlert v-if="error" type="error" :closable="false" :title="error" />
    <div v-if="!preview" class="template-controls">
      <ElSelect v-model="model.layoutMode" :aria-label="tr('ui.equipment.text1558')"
        ><ElOption value="work" :label="tr('ui.equipment.text1559')" /><ElOption
          value="analysis"
          :label="tr('ui.equipment.text1560')" /></ElSelect
      ><AnalyticsPeriodSelect
        :model-value="model.period || { preset: 'month' }"
        @update:model-value="model.period = $event"
      /><ElSelect v-model="preset" :placeholder="tr('ui.equipment.text1561')"
        ><ElOption v-for="(p, i) in presets" :key="p.name" :value="i" :label="p.name" /></ElSelect
      ><ElButton :disabled="preset === undefined || busy" @click="apply">{{ tr('ui.equipment.text1562') }}</ElButton>
    </div>
    <div v-if="!preview" class="template-controls">
      <ElSelect v-model="selected" filterable :placeholder="tr('ui.equipment.text1563')"
        ><ElOption
          v-for="t in available"
          :key="t"
          :value="t"
          :label="overviewLabels[t] || t" /></ElSelect
      ><ElButton :disabled="!selected || model.blocks.length >= 12 || busy" @click="add"
        >{{ tr('ui.equipment.text1564') }}</ElButton
      ><span>{{ tr('ui.equipment.text1565') }}</span>
    </div>
    <div
      class="template-sections"
      :class="{ analysis: model.layoutMode === 'analysis', 'preview-sections': preview }"
    >
      <div
        v-for="col in ['main', 'side']"
        :key="col"
        class="template-column"
        @dragover.prevent
        @drop.self.prevent="drop(col as 'main' | 'side')"
      >
        <h3 v-if="!preview" class="template-column-heading">
          {{
            model.layoutMode === 'analysis'
              ? col === 'main'
                ? tr('ui.equipment.text1566')
                : tr('ui.equipment.text1567')
              : col === 'main'
                ? tr('ui.equipment.text1541')
                : tr('ui.equipment.text1542')
          }}
        </h3>
        <OverviewSection
          v-for="b in model.blocks.filter((b) => b.column === col)"
          :key="b.id"
          :block="b"
          :index="model.blocks.findIndex((v) => v.id === b.id)"
          :editing="!preview"
          :busy="!!busy"
          :first="model.blocks.indexOf(b) === 0"
          :last="model.blocks.indexOf(b) === model.blocks.length - 1"
          :removable="model.blocks.length > 1"
          :team-allowed="teamAllowed"
          :organizations="catalog?.organizations"
          :metric="catalog?.data.find((m) => m.id === b.type.slice(10))"
          @start="dragId = $event"
          @end="dragId = ''"
          @drop="drop(col as 'main' | 'side', $event)"
          @update="update"
          @remove="model.blocks = model.blocks.filter((v) => v.id !== $event)"
          @move="move"
          ><p class="template-description">
            {{
              catalog?.data.find((m) => m.id === b.type.slice(10))?.definition ||
              tr('ui.equipment.text1568')
            }}
          </p></OverviewSection
        >
        <div
          v-if="!preview"
          class="template-drop-zone"
          @dragover.prevent
          @drop.stop.prevent="drop(col as 'main' | 'side')"
        >
          {{ tr('ui.equipment.text1569') }}
        </div>
      </div>
    </div></ElForm
  >
</template>
<style scoped>
.overview-template {
  margin-top: 16px;
  min-width: 0;
  container: template-canvas / inline-size;
}
.template-controls {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 12px;
  background: var(--surface);
  padding: 12px;
  margin-bottom: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
}
.template-controls > .el-select {
  width: 190px;
}
.template-controls > .el-button {
  margin: 0;
}
.template-sections {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(0, 1fr);
  gap: 16px;
}
.template-sections.analysis {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.template-column {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 16px;
}
.template-drop-zone {
  border: 1px dashed var(--line);
  border-radius: 6px;
  padding: 16px;
  text-align: center;
  background: var(--surface);
}
.template-description {
  margin: 0;
  padding: 0 16px 16px;
  font-size: 12px;
  line-height: 1.7;
}
.template-column-heading {
  font-size: 13px;
  line-height: 20px;
  margin: 0;
  font-weight: 600;
}
.template-sections :deep(.section-title) {
  flex: 1;
  flex-wrap: wrap;
}
.template-sections :deep(.section-title h2) {
  min-width: 120px;
  flex: 1;
}
.template-sections :deep(.section-heading) {
  align-items: flex-start;
}
.template-sections :deep(.scope-label) {
  font-size: 12px;
}
.template-sections :deep(.section-settings .app-form-field) {
  margin-bottom: 14px;
}
@container template-canvas (max-width: 999px) {
  .template-sections:not(.preview-sections) {
    grid-template-columns: minmax(0, 1fr);
  }
}
@container template-canvas (max-width: 680px) {
  .template-sections.preview-sections,
  .template-sections.analysis.preview-sections {
    grid-template-columns: minmax(0, 1fr);
  }
  .template-controls > .el-select {
    flex: 1 1 180px;
    width: auto;
  }
}
</style>
