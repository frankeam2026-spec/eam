<script setup lang="ts">import { tr } from '../../i18n';

import { computed, ref, watch, nextTick } from 'vue';
import {
  ElButton,
  ElSelect,
  ElOption,
  ElInput,
  ElInputNumber,
  ElSwitch,
  ElTag,
  ElForm,
  ElFormItem,
  ElTooltip,
} from 'element-plus';
import AppIcon from '../common/AppIcon.vue';
import BusinessOptionsEditor from './BusinessOptionsEditor.vue';
import {
  FIELD_TYPES,
  EXTENSION_GROUP,
  newField,
  fieldSpan,
  formLayout,
  inlineOptions,
  optionError,
  type Configuration,
  type BusinessField,
  type FieldType,
} from '../../businessConfig';
const configuration = defineModel<Configuration>({ required: true });
const props = defineProps<{
  publishedFields: BusinessField[];
  publishedIds: Set<string>;
  readonly?: boolean;
}>();
const emit = defineEmits<{ edit: [field: BusinessField]; remove: [field: BusinessField] }>();
const canvas = ref<HTMLElement>();
const selectedId = ref(''),
  hovering = ref('');
const drag = ref<{ type?: FieldType; id?: string } | null>(null);
const selected = computed(() => configuration.value.fields.find((f) => f.id === selectedId.value));
const showOptionErrors = ref(false);
watch(selectedId, () => {
  showOptionErrors.value = false;
});
function validate() {
  for (const f of configuration.value.fields) {
    const message = optionError(f);
    if (message) {
      selectedId.value = f.id;
      void nextTick(() => (showOptionErrors.value = true));
      return `${f.name}：${message}`;
    }
  }
  return '';
}
defineExpose({ validate });
const layout = computed(() => formLayout(configuration.value));
const icons: Record<FieldType, string> = {
  TEXT: 'Document',
  LONG_TEXT: 'Columns',
  NUMBER: 'Grid',
  DATE: 'Clock',
  DATETIME: 'Clock',
  CHOICE: 'Check',
  MULTI_CHOICE: 'Todo',
  BOOLEAN: 'Transfer',
  USER: 'User',
  DEPARTMENT: 'Organization',
};
const ordered = computed(() =>
  [...configuration.value.fields].sort((a, b) => a.sortOrder - b.sortOrder),
);
watch(
  () => configuration.value,
  () => {
    if (!selected.value) selectedId.value = '';
    drag.value = null;
    hovering.value = '';
  },
);
function setLayout(columns: number, maxWidth = layout.value.maxWidth) {
  const old = layout.value.columns;
  for (const f of configuration.value.fields) {
    const span = fieldSpan(f, old);
    f.span = f.width === 'full' ? columns : Math.min(span, columns);
    f.width = f.span === columns ? 'full' : 'half';
  }
  configuration.value.layout = { columns, maxWidth };
}
function setSpan(f: BusinessField, span: number) {
  f.span = span;
  f.width = span === layout.value.columns ? 'full' : 'half';
}
function reorder(f: BusinessField, target?: BusinessField, after = false) {
  const remaining = ordered.value.filter((x) => x.id !== f.id);
  const index = target ? remaining.findIndex((x) => x.id === target.id) : -1;
  remaining.splice(index < 0 ? remaining.length : index + Number(after), 0, f);
  f.group = EXTENSION_GROUP;
  remaining.forEach((x, i) => {
    x.sortOrder = i;
  });
  if (!configuration.value.fields.some((x) => x.id === f.id)) configuration.value.fields.push(f);
  selectedId.value = f.id;
  void nextTick(() =>
    canvas.value
      ?.querySelector(`[data-designer-id="${f.id}"]`)
      ?.scrollIntoView({ block: 'nearest', inline: 'nearest' }),
  );
}
function create(type: FieldType) {
  const f = newField();
  f.type = type;
  const label = FIELD_TYPES[type];
  let name = label,
    index = 2;
  while (configuration.value.fields.some((x) => x.name === name)) name = `${label}${index++}`;
  f.name = name;
  f.source = type === 'USER' ? 'user' : type === 'DEPARTMENT' ? 'department' : '';
  if (['CHOICE', 'MULTI_CHOICE'].includes(type)) f.options = [];
  if (type === 'LONG_TEXT') {
    f.maxLength = 4000;
    f.width = 'full';
  }
  f.span = f.width === 'full' ? layout.value.columns : 1;
  return f;
}
function add(type: FieldType) {
  if (!props.readonly) reorder(create(type));
}
function start(event: DragEvent, payload: { type?: FieldType; id?: string }) {
  if (props.readonly || !event.dataTransfer) return;
  drag.value = payload;
  event.dataTransfer.effectAllowed = 'copyMove';
  event.dataTransfer.setData('application/x-eam-business-field', JSON.stringify(payload));
}
function end() {
  drag.value = null;
  hovering.value = '';
}
function over(event: DragEvent, target?: BusinessField) {
  if (props.readonly || !drag.value) return;
  event.preventDefault();
  event.stopPropagation();
  hovering.value = target?.id || 'canvas';
  if (event.dataTransfer) event.dataTransfer.dropEffect = drag.value.id ? 'move' : 'copy';
}
function drop(event: DragEvent, target?: BusinessField) {
  if (props.readonly || !drag.value) return;
  event.preventDefault();
  event.stopPropagation();
  const payload = drag.value;
  if (payload.id && payload.id === target?.id) {
    end();
    return;
  }
  const f = payload.id
    ? configuration.value.fields.find((x) => x.id === payload.id)
    : payload.type
      ? create(payload.type)
      : undefined;
  if (f) {
    const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
    const after =
      !!target &&
      (event.clientY > rect.top + rect.height * 0.65 ||
        (layout.value.columns > 1 && event.clientX > rect.left + rect.width * 0.65));
    reorder(f, target, after);
  }
  end();
}
function move(f: BusinessField, direction: number) {
  const list = ordered.value,
    index = list.findIndex((x) => x.id === f.id),
    other = list[index + direction];
  if (other) reorder(f, other, direction > 0);
}
</script>
<template>
  <div class="form-designer">
    <aside class="designer-palette">
      <h3>{{ tr('ui.equipment.text1250') }}</h3>
      <p>{{ tr('ui.equipment.text1251') }}</p>
      <div class="designer-palette-grid">
        <ElButton
          v-for="(label, type) in FIELD_TYPES"
          :key="type"
          :disabled="readonly"
          :draggable="!readonly"
          @dragstart="start($event, { type })"
          @dragend="end"
          @click="add(type)"
        >
          <AppIcon :name="icons[type]" /><span>{{ label }}</span>
        </ElButton>
      </div>
      <div class="designer-note">{{ tr('ui.equipment.text1252') }}</div>
    </aside>
    <main class="designer-workspace">
      <div class="designer-layout-tools">
        <strong>{{ EXTENSION_GROUP }}</strong>
        <label>{{ tr('ui.equipment.text1253') }}</label
        ><ElSelect
          :model-value="layout.columns"
          :disabled="readonly"
          :aria-label="tr('ui.equipment.text1254')"
          @update:model-value="setLayout(Number($event))"
        >
          <ElOption v-for="n in 4" :key="n" :value="n" :label="`${n} 列`" />
        </ElSelect>
        <label>{{ tr('ui.equipment.text1255') }}</label
        ><ElSelect
          :model-value="layout.maxWidth === 0 ? 'auto' : 'fixed'"
          :disabled="readonly"
          :aria-label="tr('ui.equipment.text1256')"
          @update:model-value="setLayout(layout.columns, $event === 'auto' ? 0 : 960)"
        >
          <ElOption value="auto" :label="tr('ui.equipment.text1257')" /><ElOption value="fixed" :label="tr('ui.equipment.text1258')" />
        </ElSelect>
        <ElInputNumber
          v-if="layout.maxWidth"
          :model-value="layout.maxWidth"
          :min="480"
          :max="1920"
          :step="40"
          :precision="0"
          :disabled="readonly"
          controls-position="right"
          :aria-label="tr('ui.equipment.text1259')"
          @update:model-value="setLayout(layout.columns, Number($event || 960))"
        />
        <span v-if="layout.maxWidth">px</span>
      </div>
      <div ref="canvas" class="designer-canvas-scroll">
        <div
          class="designer-canvas"
          :style="{ maxWidth: layout.maxWidth ? `${layout.maxWidth}px` : undefined }"
        >
          <section
            class="designer-area"
            :class="{ 'is-target': hovering === 'canvas' }"
            @dragenter="over($event)"
            @dragover="over($event)"
            @drop="drop($event)"
          >
            <div class="designer-grid" :style="{ '--designer-columns': layout.columns }">
              <article
                v-for="f in ordered"
                :key="f.id"
                :data-designer-id="f.id"
                :draggable="!readonly"
                @dragstart.stop="start($event, { id: f.id })"
                @dragend="end"
                tabindex="0"
                class="designer-field"
                :class="{
                  'is-selected': selectedId === f.id,
                  'is-target': hovering === f.id,
                  'is-disabled': !f.enabled,
                }"
                :style="{ gridColumn: `span ${fieldSpan(f, layout.columns)}` }"
                @click.stop="selectedId = f.id"
                @keydown.enter.prevent="selectedId = f.id"
                @dragenter="over($event, f)"
                @dragover="over($event, f)"
                @drop="drop($event, f)"
              >
                <div class="designer-field-heading">
                  <ElTooltip :content="tr('ui.equipment.text1260')"
                    ><ElButton
                      v-if="!readonly"
                      class="designer-drag"
                      text
                      :draggable="true"
                      :aria-label="`拖动${f.name}`"
                      @dragstart.stop="start($event, { id: f.id })"
                      @dragend="end"
                      @click.stop
                      ><AppIcon name="HamburgerButton" /></ElButton
                  ></ElTooltip>
                  <span class="designer-field-label" :title="f.name"
                    ><b v-if="f.required">*</b>{{ f.name }}</span
                  >
                  <ElTag v-if="!f.enabled" size="small" type="info">{{ tr('ui.equipment.text0260') }}</ElTag>
                  <ElButton text :aria-label="`设置${f.name}`" @click.stop="emit('edit', f)"
                    ><AppIcon name="Settings"
                  /></ElButton>
                </div>
                <div
                  class="designer-control-preview"
                  :class="{ 'is-textarea': f.type === 'LONG_TEXT' }"
                >
                  <span>{{
                    f.placeholder ||
                    (f.type === 'NUMBER'
                      ? '0.00'
                      : ['CHOICE', 'MULTI_CHOICE', 'USER', 'DEPARTMENT', 'BOOLEAN'].includes(f.type)
                        ? tr('ui.equipment.text0096')
                        : ['DATE', 'DATETIME'].includes(f.type)
                          ? tr('ui.equipment.text0822')
                          : tr('ui.equipment.text1261'))
                  }}</span>
                  <AppIcon v-if="['DATE', 'DATETIME'].includes(f.type)" name="Clock" />
                  <AppIcon
                    v-else-if="
                      ['CHOICE', 'MULTI_CHOICE', 'USER', 'DEPARTMENT', 'BOOLEAN'].includes(f.type)
                    "
                    name="Down"
                  />
                </div>
                <div class="designer-field-meta">
                  <span>{{ FIELD_TYPES[f.type] }}</span
                  ><span>{{ tr('ui.equipment.text1262') }} {{ fieldSpan(f, layout.columns) }}/{{ layout.columns }} {{ tr('ui.equipment.text1263') }}</span>
                </div>
              </article>
            </div>
            <div v-if="!ordered.length" class="designer-drop-empty">
              <AppIcon name="Plus" /><span>{{
                readonly ? tr('ui.equipment.text1264') : tr('ui.equipment.text1265')
              }}</span>
            </div>
          </section>
        </div>
      </div>
    </main>
    <aside class="designer-properties">
      <template v-if="selected">
        <div class="designer-properties-heading">
          <h3>{{ tr('ui.equipment.text1266') }}</h3>
          <ElTag size="small">{{ FIELD_TYPES[selected.type] }}</ElTag>
        </div>
        <ElForm label-position="top" :disabled="readonly" @submit.prevent>
          <ElFormItem :label="tr('ui.equipment.text1267')"
            ><ElInput v-model="selected.name" maxlength="100"
          /></ElFormItem>
          <ElFormItem :label="tr('ui.equipment.text1268')"
            ><ElSelect
              :model-value="fieldSpan(selected, layout.columns)"
              @update:model-value="setSpan(selected!, Number($event))"
              ><ElOption
                v-for="n in layout.columns"
                :key="n"
                :value="n"
                :label="`${n} 列（${Math.round((n / layout.columns) * 100)}%）`" /></ElSelect
          ></ElFormItem>
          <BusinessOptionsEditor
            v-if="inlineOptions(selected)"
            :model-value="selected.options || []"
            :published-ids="
              publishedFields.find((f) => f.id === selected!.id)?.options?.map((o) => o.id) || []
            "
            :disabled="readonly"
            :show-errors="showOptionErrors"
            @update:model-value="selected!.options = $event"
          />
          <p v-else-if="['CHOICE', 'MULTI_CHOICE'].includes(selected.type)">
            {{ tr('ui.equipment.text1269') }}
          </p>
          <ElFormItem :label="tr('ui.equipment.text1270')"
            ><ElInput v-model="selected.placeholder" maxlength="200"
          /></ElFormItem>
          <div class="designer-property-switch">
            <span>{{ tr('ui.equipment.text0794') }}</span><ElSwitch v-model="selected.required" />
          </div>
          <div class="designer-property-switch">
            <span>{{ tr('ui.equipment.text0216') }}</span><ElSwitch v-model="selected.enabled" />
          </div>
        </ElForm>
        <ElButton class="designer-settings-button" @click="emit('edit', selected)">{{
          readonly ? tr('ui.equipment.text1271') : tr('ui.equipment.text1272')
        }}</ElButton>
        <div v-if="!readonly" class="designer-move-tools">
          <ElButton :disabled="ordered[0]?.id === selected.id" @click="move(selected!, -1)"
            ><AppIcon name="ArrowUp" />{{ tr('ui.equipment.text0382') }}</ElButton
          ><ElButton :disabled="ordered.at(-1)?.id === selected.id" @click="move(selected!, 1)"
            ><AppIcon name="ArrowDown" />{{ tr('ui.equipment.text0383') }}</ElButton
          >
        </div>
        <ElButton
          v-if="!readonly"
          class="designer-settings-button"
          :type="publishedIds.has(selected.id) ? 'primary' : 'danger'"
          plain
          @click="emit('remove', selected)"
          >{{
            publishedIds.has(selected.id)
              ? selected.enabled
                ? tr('ui.equipment.text1273')
                : tr('ui.equipment.text1274')
              : tr('ui.equipment.text1275')
          }}</ElButton
        >
      </template>
      <div v-else class="designer-no-selection">
        <AppIcon name="Settings" /><span>{{ tr('ui.equipment.text1276') }}</span>
      </div>
    </aside>
  </div>
</template>
<style scoped>
.form-designer {
  display: grid;
  grid-template-columns: 168px minmax(0, 1fr) 218px;
  flex: 1;
  min-height: 0;
  gap: 12px;
  color: var(--text);
  font-size: 13px;
}
.designer-palette,
.designer-properties {
  background: var(--surface, #fff);
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  padding: 14px 12px;
  overflow: auto;
  min-height: 0;
}
h3 {
  font-size: 14px;
  margin: 0;
}
.designer-palette p {
  margin: 8px 0 16px;
  font-size: 12px;
}
.designer-palette-grid {
  display: grid;
  gap: 8px;
}
.designer-palette-grid .el-button {
  margin: 0;
  height: 38px;
  justify-content: flex-start;
  padding: 0 12px;
  background: var(--el-color-primary-light-9);
  border-color: var(--el-color-primary-light-8);
  color: var(--text);
  cursor: grab;
}
.designer-palette-grid .app-icon {
  color: var(--el-color-primary);
  margin-right: 10px;
}
.designer-note {
  margin-top: 20px;
  line-height: 1.8;
  font-size: 12px;
}
.designer-workspace {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  border: 1px solid var(--el-border-color-light);
  border-radius: 8px;
  overflow: hidden;
  background: var(--el-fill-color-light);
}
.designer-layout-tools {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--surface, #fff);
}
.designer-layout-tools strong {
  margin-right: auto;
}
.designer-layout-tools .el-select {
  width: 100px;
}
.designer-layout-tools .el-input-number {
  width: 110px;
}
.designer-canvas-scroll {
  padding: 12px;
  overflow: auto;
  flex: 1;
  min-height: 0;
}
.designer-canvas {
  display: flex;
  flex-direction: column;
  width: 100%;
  min-height: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  background: var(--surface, #fff);
  border-radius: 6px;
}
.designer-area {
  flex: 1;
}
.designer-grid {
  display: grid;
  grid-template-columns: repeat(var(--designer-columns, 2), minmax(0, 1fr));
  gap: 10px;
}
.designer-field {
  min-width: 0;
  padding: 8px 10px;
  border: 1px solid var(--el-border-color-light);
  background: var(--surface, #fff);
  border-radius: 6px;
  cursor: pointer;
  box-sizing: border-box;
  outline: none;
}
.designer-field.is-selected,
.designer-field:focus-visible {
  border-color: var(--el-color-primary);
  box-shadow: 0 0 0 1px var(--el-color-primary);
}
.designer-field.is-disabled {
  opacity: 0.65;
}
.designer-field.is-target {
  border-color: var(--el-color-primary);
  border-style: dashed;
  background: var(--el-color-primary-light-9);
}
.designer-area.is-target {
  outline: 2px dashed var(--el-color-primary);
  outline-offset: -2px;
  background: var(--el-color-primary-light-9);
}
.designer-field-heading {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 4px;
  height: 28px;
  margin-bottom: 6px;
}
.designer-field-label {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 600;
}
.designer-field-label b {
  color: var(--el-color-danger);
  margin-right: 4px;
}
.designer-field-heading .el-button {
  margin: 0;
  width: 24px;
  height: 24px;
  padding: 3px;
  color: var(--el-color-primary);
}
.designer-field-heading .designer-drag {
  cursor: grab;
  color: var(--text);
}
.designer-control-preview {
  display: flex;
  align-items: center;
  gap: 4px;
  justify-content: space-between;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  height: 32px;
  padding: 0 9px;
  font-size: 12px;
}
.designer-control-preview span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.designer-control-preview.is-textarea {
  height: 62px;
  align-items: flex-start;
  padding-top: 8px;
}
.designer-field-meta {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 4px;
  margin-top: 8px;
  font-size: 11px;
}
.designer-drop-empty {
  padding: 22px 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  border: 1px dashed var(--el-color-primary-light-5);
  border-radius: 5px;
  font-size: 12px;
  line-height: 1.6;
}
.designer-no-selection {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 16px;
  padding: 30px 8px;
  line-height: 1.7;
  text-align: center;
}
.designer-no-selection .app-icon {
  --app-icon-size: 32px;
  color: var(--el-color-primary);
}
.designer-properties-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.designer-properties .el-select {
  width: 100%;
}
.designer-properties :deep(.el-form-item) {
  margin-bottom: 16px;
}
.designer-property-switch {
  display: flex;
  align-items: center;
  justify-content: space-between;
  min-height: 38px;
}
.designer-settings-button {
  width: 100%;
  margin: 12px 0 0;
}
.designer-move-tools {
  display: flex;
  gap: 8px;
  margin-top: 12px;
}
.designer-move-tools .el-button {
  margin: 0;
  flex: 1;
  padding: 8px;
}
@media (max-width: 1350px) {
  .form-designer {
    grid-template-columns: 144px minmax(0, 1fr) 192px;
    gap: 8px;
  }
  .designer-field {
    padding: 6px;
  }
}
</style>
