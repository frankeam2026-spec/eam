<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  ElDrawer,
  ElCollapse,
  ElCollapseItem,
  ElInput,
  ElSelect,
  ElOption,
  ElDatePicker,
} from 'element-plus';
import { dateRangeShortcuts } from '../../query/dateRangeShortcuts';
import TreeSelect from '../TreeSelect.vue';
import type { FilterCondition, FilterField } from '../../query/advancedFilters';
const props = defineProps<{
  modelValue: boolean;
  fields: FilterField[];
  conditions: FilterCondition[];
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  apply: [conditions: FilterCondition[]];
}>();
const search = ref('');
const error = ref('');
const expanded = ref<string[]>([]);
const drafts = ref<Record<string, FilterCondition>>({});
const visibleFields = computed(() =>
  props.fields.filter((f) => f.label.includes(search.value.trim())),
);
watch(
  () => props.modelValue,
  (open) => {
    if (!open) return;
    search.value = '';
    error.value = '';
    drafts.value = Object.fromEntries(
      props.fields.map((f) => {
        const existing = props.conditions.find((c) => c.field === f.key);
        return [
          f.key,
          existing
            ? { ...existing, values: [...existing.values] }
            : {
                field: f.key,
                operator:
                  f.type === 'choice'
                    ? 'in'
                    : ['date', 'number'].includes(f.type)
                      ? 'range'
                      : f.type === 'tree'
                        ? 'eq'
                        : 'contains',
                values: [],
              },
        ];
      }),
    );
    expanded.value = props.conditions.map((c) => c.field);
  },
  { immediate: true },
);
function active(c?: FilterCondition) {
  return (
    !!c &&
    (['empty', 'notEmpty'].includes(c.operator) || c.values.some((v) => String(v ?? '').trim()))
  );
}
function apply() {
  const result = Object.values(drafts.value)
    .filter(active)
    .map((c) => ({
      ...c,
      values: ['empty', 'notEmpty'].includes(c.operator)
        ? []
        : Array.from(c.values, (v) => String(v ?? '').trim()),
    }));
  error.value = '';
  for (const condition of result) {
    if (condition.operator !== 'range') continue;
    const field = props.fields.find((f) => f.key === condition.field);
    const [lower, upper] = condition.values;
    if (
      lower &&
      upper &&
      (field?.type === 'number' ? Number(lower) > Number(upper) : lower > upper)
    ) {
      error.value = `${field?.label}：起始值不能大于结束值`;
      return;
    }
  }
  emit('apply', result);
}
function clear() {
  error.value = '';
  Object.values(drafts.value).forEach((c) => {
    c.values = [];
    const type = props.fields.find((f) => f.key === c.field)?.type;
    c.operator =
      type === 'choice'
        ? 'in'
        : type === 'tree'
          ? 'eq'
          : ['date', 'number'].includes(type || '')
            ? 'range'
            : 'contains';
  });
}
</script>
<template>
  <ElDrawer
    :model-value="modelValue"
    title="筛选器"
    size="420px"
    :modal="false"
    :close-on-click-modal="false"
    :close-on-press-escape="false"
    :lock-scroll="false"
    append-to-body
    destroy-on-close
    class="advanced-filter-drawer"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <ElInput v-model="search" placeholder="查找筛选字段" clearable aria-label="查找筛选字段" />
    <p class="filter-hint">不同字段同时满足；同一字段的多个选项满足其一。</p>
    <ElCollapse v-model="expanded">
      <ElCollapseItem v-for="field in visibleFields" :key="field.key" :name="field.key">
        <template #title
          ><span>{{ field.label }}</span
          ><span v-if="active(drafts[field.key])" class="filter-active-dot">已设置</span></template
        >
        <div v-if="drafts[field.key]" class="filter-field-editor">
          <ElSelect v-model="drafts[field.key]!.operator" aria-label="匹配方式">
            <ElOption v-if="field.type === 'text'" label="包含" value="contains" />
            <ElOption
              v-if="field.type !== 'choice'"
              :label="['date', 'number'].includes(field.type) ? '范围' : '等于'"
              :value="['date', 'number'].includes(field.type) ? 'range' : 'eq'"
            />
            <ElOption v-if="field.type === 'choice'" label="任一选项" value="in" />
            <ElOption label="为空" value="empty" /><ElOption label="不为空" value="notEmpty" />
          </ElSelect>
          <template v-if="!['empty', 'notEmpty'].includes(drafts[field.key]!.operator)">
            <ElSelect
              v-if="field.type === 'choice'"
              v-model="drafts[field.key]!.values"
              multiple
              clearable
              placeholder="请选择，可多选"
              ><ElOption v-for="value in field.options" :key="value" :label="value" :value="value"
            /></ElSelect>
            <TreeSelect
              v-else-if="field.type === 'tree'"
              :model-value="drafts[field.key]!.values[0] || ''"
              :nodes="field.nodes || []"
              :path-value="field.pathValue"
              @update:model-value="drafts[field.key]!.values = [$event]"
            />
            <ElDatePicker
              v-else-if="field.type === 'date'"
              :model-value="
                drafts[field.key]!.values.length === 2 && drafts[field.key]!.values.every(Boolean)
                  ? drafts[field.key]!.values
                  : undefined
              "
              :type="field.dateOnly ? 'daterange' : 'datetimerange'"
              :value-format="field.dateOnly ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'"
              :format="field.dateOnly ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm'"
              :default-time="[new Date(2000, 0, 1, 0, 0, 0), new Date(2000, 0, 1, 23, 59, 59)]"
              :shortcuts="dateRangeShortcuts"
              range-separator="至"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              :aria-label="`${field.label}范围`"
              unlink-panels
              clearable
              @update:model-value="drafts[field.key]!.values = $event ? [...$event] : []"
            />
            <div v-else-if="field.type === 'number'" class="filter-range">
              <input
                :value="drafts[field.key]!.values[0]"
                @input="drafts[field.key]!.values[0] = ($event.target as HTMLInputElement).value"
                type="number"
                :aria-label="`${field.label}起始值`"
                placeholder="最小值"
              />
              <span>至</span>
              <input
                :value="drafts[field.key]!.values[1]"
                @input="drafts[field.key]!.values[1] = ($event.target as HTMLInputElement).value"
                type="number"
                :aria-label="`${field.label}结束值`"
                placeholder="最大值"
              />
            </div>
            <ElInput
              v-else
              v-model="drafts[field.key]!.values[0]"
              clearable
              :maxlength="200"
              :placeholder="`请输入${field.label}`"
            />
          </template>
        </div>
      </ElCollapseItem>
    </ElCollapse>
    <p v-if="!visibleFields.length" class="filter-hint">没有匹配的字段</p>
    <template #footer
      ><p v-if="error" role="alert" class="filter-error">{{ error }}</p>
      <div class="advanced-filter-footer">
        <button type="button" @click="clear">清空条件</button>
        <div>
          <button type="button" @click="emit('update:modelValue', false)">取消</button
          ><button class="primary" type="button" @click="apply">应用</button>
        </div>
      </div></template
    >
  </ElDrawer>
</template>
<style>
.advanced-filter-drawer {
  border-left: 1px solid var(--el-border-color-light);
  font-size: 13px;
}
.advanced-filter-drawer .el-drawer__header {
  margin-bottom: 0;
  padding: 18px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.advanced-filter-drawer .el-drawer__body {
  padding: 16px 20px;
}
.filter-hint {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  margin: 12px 0;
  line-height: 1.6;
}
.filter-active-dot {
  margin-left: auto;
  margin-right: 10px;
  color: var(--el-color-primary);
  font-size: 12px;
}
.filter-field-editor {
  display: grid;
  gap: 10px;
  padding: 4px 0 12px;
}
.filter-range {
  display: grid;
  gap: 8px;
}
.filter-range input {
  width: 100%;
  box-sizing: border-box;
  height: 34px;
  padding: 0 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font: inherit;
  color: var(--el-text-color-regular);
}
.filter-range span {
  text-align: center;
  color: var(--el-text-color-secondary);
}
.advanced-filter-footer,
.advanced-filter-footer > div {
  display: flex;
  justify-content: space-between;
  gap: 8px;
}
.advanced-filter-footer button {
  height: 34px;
  padding: 0 14px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  background: white;
  font: inherit;
  cursor: pointer;
}
.advanced-filter-footer button.primary {
  color: white;
  background: var(--el-color-primary);
  border-color: var(--el-color-primary);
}
</style>

<style>
.filter-error {
  color: var(--el-color-danger);
  text-align: left;
  font-size: 13px;
  margin: 0 0 10px;
}
</style>

<style>
.advanced-filter-drawer .filter-field-editor > .el-date-editor {
  width: 100%;
  box-sizing: border-box;
  min-width: 0;
}
.advanced-filter-drawer .el-date-editor .el-range-input {
  font-size: 13px;
  min-width: 0;
}
</style>
