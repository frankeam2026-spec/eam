<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  ElPopover,
  ElInput,
  ElButton,
  ElForm,
  ElTag,
  ClickOutside as vClickOutside,
} from 'element-plus';
import FilterFieldInput from './FilterFieldInput.vue';
import AppFormField from './AppFormField.vue';
import {
  filterDraft,
  filterError,
  prepareFilter,
  type FilterField,
  type FilterCondition,
} from '../../query/advancedFilters';
const props = defineProps<{
  modelValue: boolean;
  anchor?: HTMLElement;
  fieldKey?: string;
  placement?: 'bottom-start' | 'bottom-end';
  fields: FilterField[];
  conditions: FilterCondition[];
}>();
const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  confirm: [condition: FilterCondition];
  close: [];
}>();
const search = ref('');
const currentKey = ref<string>();
const draft = ref<FilterCondition>();
const error = ref('');
const field = computed(() => props.fields.find((f) => f.key === currentKey.value));
const visibleFields = computed(() =>
  props.fields.filter((f) =>
    f.label.toLocaleLowerCase().includes(search.value.trim().toLocaleLowerCase()),
  ),
);
function choose(key?: string) {
  currentKey.value = key;
  const f = props.fields.find((f) => f.key === key);
  draft.value = f
    ? filterDraft(
        f,
        props.conditions.find((c) => c.field === key),
      )
    : undefined;
  error.value = '';
}
watch(
  () => [props.modelValue, props.fieldKey, props.anchor] as const,
  () => {
    if (props.modelValue) {
      search.value = '';
      choose(props.fieldKey);
    }
  },
  { immediate: true },
);
watch(
  draft,
  () => {
    if (error.value && field.value && draft.value)
      error.value = filterError(field.value, draft.value);
  },
  { deep: true },
);
function outside(event: MouseEvent) {
  if (props.modelValue && !props.anchor?.contains(event.target as Node)) close();
}
function close() {
  choose();
  emit('update:modelValue', false);
  emit('close');
}
function apply() {
  if (!field.value || !draft.value) return;
  const condition = prepareFilter(field.value, draft.value);
  error.value = filterError(field.value, condition);
  if (error.value) return;
  emit('confirm', condition);
  close();
}
</script>
<template>
  <ElPopover
    :visible="modelValue"
    :virtual-ref="anchor"
    virtual-triggering
    :trigger="[]"
    :placement="placement ?? 'bottom-end'"
    :width="field?.type === 'date' ? 420 : 320"
    :show-arrow="false"
    popper-class="condition-popover"
    :persistent="false"
    :hide-after="0"
    @keydown.esc.stop="close"
  >
    <div v-click-outside="outside">
      <template v-if="!field">
        <ElInput v-model="search" placeholder="搜索筛选字段" aria-label="搜索筛选字段" clearable />
        <div class="condition-fields">
          <ElButton
            v-for="item in visibleFields"
            :key="item.key"
            text
            class="condition-field"
            @click="choose(item.key)"
          >
            <span>{{ item.label }}</span
            ><ElTag v-if="conditions.some((c) => c.field === item.key)" size="small">已添加</ElTag>
          </ElButton>
          <p v-if="!visibleFields.length">没有匹配的字段</p>
        </div>
      </template>
      <template v-else-if="draft">
        <ElForm :model="draft" label-position="top" @submit.prevent="apply">
          <AppFormField :label="field.label" prop="values" :error="error">
            <FilterFieldInput v-model="draft" :field="field" :teleported="false" />
          </AppFormField>
        </ElForm>
        <div class="condition-actions">
          <ElButton text @click="choose()">返回</ElButton>
          <div>
            <ElButton @click="close">取消</ElButton
            ><ElButton type="primary" @click="apply">应用</ElButton>
          </div>
        </div>
      </template>
    </div>
  </ElPopover>
</template>
<style>
.condition-popover.el-popover {
  max-width: calc(100vw - 32px);
  box-sizing: border-box;
  padding: 16px;
  font-size: 13px;
}
</style>
<style scoped>
.condition-fields {
  margin-top: 8px;
  max-height: min(280px, calc(100dvh - 220px));
  overflow: auto;
}
.condition-field.el-button {
  display: flex;
  width: 100%;
  justify-content: space-between;
  margin: 0;
  height: 34px;
  padding: 0 8px;
}
.condition-field :deep(> span) {
  display: flex;
  justify-content: space-between;
  width: 100%;
}
.condition-actions,
.condition-actions > div {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.condition-actions .el-button + .el-button {
  margin-left: 0;
}
.condition-actions {
  border-top: 1px solid var(--el-border-color-lighter);
  padding-top: 12px;
}
</style>
