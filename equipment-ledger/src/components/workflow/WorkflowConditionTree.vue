<script setup lang="ts">
import { tr } from '../../i18n';
import { computed, ref, watch } from 'vue';
import { ElSelect, ElOption, ElInput, ElButton, ElCheckbox } from 'element-plus';
import type { Condition, WorkflowVariable } from '../../workflow';
import { workflowApi } from '../../workflow';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import type { SelectorRow } from '../../selectors';
const props = defineProps<{
  modelValue: Condition;
  variables: WorkflowVariable[];
  companyId: string;
  depth?: number;
}>();
const emit = defineEmits<{ 'update:modelValue': [value: Condition] }>();
const logical = computed(() => ['AND', 'OR', 'NOT', 'ANY', 'ALL'].includes(props.modelValue.op));
function update(patch: Partial<Condition>) {
  emit('update:modelValue', { ...props.modelValue, ...patch });
}
function child(i: number, value: Condition) {
  const children = [...props.modelValue.children];
  children[i] = value;
  update({ children });
}
function empty(): Condition {
  return {
    op: '==',
    field: null,
    literal: null,
    type: 'BOOLEAN',
    children: [
      { op: 'FIELD', field: 'taskKind', literal: null, type: 'TEXT', children: [] },
      { op: 'LITERAL', field: null, literal: '维修', type: 'TEXT', children: [] },
    ],
  };
}
function group(op: string) {
  emit('update:modelValue', {
    op,
    field: null,
    literal: null,
    type: 'BOOLEAN',
    children: op === 'AND' || op === 'OR' ? [props.modelValue, empty()] : [props.modelValue],
  });
}
function field(key: string) {
  const v = props.variables.find((v) => v.key === key)!;
  const left: Condition = { op: 'FIELD', field: key, literal: null, type: v.type, children: [] };
  update({
    op: '==',
    children: [
      left,
      {
        op: 'LITERAL',
        field: null,
        literal: v.type === 'TEXT' ? '' : '0',
        type: v.type,
        children: [],
      },
    ],
  });
}
const left = computed(() => props.modelValue.children[0]);
const options = ref<SelectorRow[]>([]);
const referenceKind = computed(
  () =>
    (
      ({
        companyId: 'companies',
        applicantId: 'users',
        applicantDepartmentId: 'departments',
        warehouseId: 'warehouses',
        'lines.materialId': 'materials',
      }) as Record<string, string>
    )[left.value?.field || ''],
);
const multi = computed(() => ['IN', 'NOT_IN'].includes(props.modelValue.op));
const referenceValue = computed(() =>
  multi.value
    ? props.modelValue.children.slice(1).map((c) => String(c.literal))
    : String(props.modelValue.children[1]?.literal || ''),
);
function references(value: string | string[]) {
  const ids = Array.isArray(value) ? value : [value];
  update({
    children: [
      left.value!,
      ...ids.map((id) => ({
        op: 'LITERAL',
        field: null,
        literal: id,
        type: 'REFERENCE',
        children: [],
      })),
    ],
  });
}
function literals(value: string[]) {
  update({
    children: [
      left.value!,
      ...value.map((v) => ({
        op: 'LITERAL',
        field: null,
        literal: v,
        type: left.value!.type,
        children: [],
      })),
    ],
  });
}
function logicalOperator(op: string) {
  update({
    op,
    children: ['NOT', 'ANY', 'ALL'].includes(op)
      ? [props.modelValue.children[0]!]
      : props.modelValue.children,
  });
}
watch(
  () => [props.companyId, referenceKind.value, JSON.stringify(referenceValue.value)],
  async () => {
    const value = referenceValue.value;
    const ids = (Array.isArray(value) ? value : [value]).filter((v) => /^[1-9][0-9]*$/.test(v));
    if (!referenceKind.value || !props.companyId || !ids.length) return;
    try {
      options.value = (await workflowApi.resolve(props.companyId, referenceKind.value, ids)).data;
    } catch {
      options.value = [];
    }
  },
  { immediate: true },
);
function loadRows(q: string, p: number, s: number) {
  return workflowApi.choices(props.companyId, referenceKind.value || 'users', q, p, s);
}
function operator(op: string) {
  const right = props.modelValue.children[1] || empty().children[1]!;
  update({ op, children: op.startsWith('IS_') ? [left.value!] : [left.value!, right] });
}
</script>
<template>
  <div class="condition-tree">
    <template v-if="logical">
      <div class="condition-heading">
        <ElSelect :model-value="modelValue.op" @update:model-value="logicalOperator"
          ><ElOption :label="tr('workflow.ui.012')" value="AND" /><ElOption
            :label="tr('workflow.ui.013')"
            value="OR" /><ElOption :label="tr('workflow.ui.014')" value="NOT" /><ElOption
            :label="tr('workflow.ui.015')"
            value="ANY" /><ElOption :label="tr('workflow.ui.016')" value="ALL" /></ElSelect
        ><ElButton
          v-if="['AND', 'OR'].includes(modelValue.op)"
          text
          @click="update({ children: [...modelValue.children, empty()] })"
          >{{ tr('workflow.ui.017') }}</ElButton
        >
      </div>
      <div v-for="(c, i) in modelValue.children" :key="i" class="condition-child">
        <WorkflowConditionTree
          :model-value="c"
          :variables="variables"
          :company-id="companyId"
          :depth="(depth || 0) + 1"
          @update:model-value="child(i, $event)"
        /><ElButton
          v-if="modelValue.children.length > 1"
          text
          type="danger"
          @click="update({ children: modelValue.children.filter((_, j) => i !== j) })"
          >{{ tr('workflow.ui.018') }}</ElButton
        >
      </div>
    </template>
    <template v-else>
      <div class="condition-leaf">
        <ElSelect
          :model-value="left?.field || left?.children[0]?.field"
          filterable
          @update:model-value="field"
          ><ElOption
            v-for="v in variables"
            :key="v.key"
            :label="v.label"
            :value="v.key"
            :disabled="!v.available"
        /></ElSelect>
        <ElSelect :model-value="modelValue.op" @update:model-value="operator"
          ><ElOption
            v-for="o in [
              '==',
              '!=',
              '>',
              '<',
              '>=',
              '<=',
              'IN',
              'NOT_IN',
              'CONTAINS',
              'STARTS_WITH',
              'IS_NULL',
              'IS_NOT_NULL',
            ]"
            :key="o"
            :value="o"
            :label="
              (
                {
                  CONTAINS: '包含',
                  STARTS_WITH: '开头匹配',
                  IS_NULL: '为空',
                  IS_NOT_NULL: '不为空',
                  IN: '属于',
                  NOT_IN: '不属于',
                } as Record<string, string>
              )[o] || o
            "
        /></ElSelect>
        <template v-if="!modelValue.op.startsWith('IS_')"
          ><PagedChoiceField
            v-if="referenceKind"
            :model-value="referenceValue"
            :multiple="multi"
            :options="options"
            :load-rows="loadRows"
            :columns="[{ key: 'name', label: '名称' }]"
            :context="companyId + referenceKind"
            @update:model-value="references"
            @select="
              options = [...new Map([...options, ...$event].map((r) => [r.id, r])).values()]
            " />
          <ElSelect
            v-else-if="multi"
            :model-value="modelValue.children.slice(1).map((c) => String(c.literal))"
            multiple
            filterable
            allow-create
            default-first-option
            @update:model-value="literals" />
          <ElInput
            v-else
            :model-value="String(modelValue.children[1]?.literal ?? '')"
            :placeholder="tr('workflow.ui.019')"
            @update:model-value="child(1, { ...modelValue.children[1]!, literal: $event })"
        /></template>
      </div>
      <ElCheckbox
        v-if="left?.type === 'NUMBER' && (left?.field?.startsWith('lines.') || left?.op === 'SUM')"
        :model-value="left?.op === 'SUM'"
        @update:model-value="
          child(
            0,
            $event
              ? { op: 'SUM', field: null, literal: null, type: 'NUMBER', children: [left!] }
              : left!.children[0]!,
          )
        "
        >{{ tr('workflow.ui.020') }}</ElCheckbox
      >
    </template>
    <div v-if="(depth || 0) < 8" class="condition-actions">
      <ElButton text @click="group('AND')">{{ tr('workflow.ui.021') }}</ElButton
      ><ElButton text @click="group('OR')">{{ tr('workflow.ui.022') }}</ElButton
      ><ElButton text @click="group('ANY')">{{ tr('workflow.ui.023') }}</ElButton>
    </div>
  </div>
</template>
<style scoped>
.condition-tree {
  min-width: 0;
}
.condition-heading,
.condition-leaf,
.condition-actions {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
}
.condition-heading > .el-select {
  width: 160px;
}
.condition-leaf > .el-select:first-child {
  width: 180px;
}
.condition-leaf > .el-select {
  width: 108px;
}
.condition-leaf > .el-input {
  flex: 1;
  min-width: 120px;
}
.condition-child {
  margin: 10px 0 10px 8px;
  padding-left: 12px;
  border-left: 2px solid var(--el-border-color);
  display: flex;
  gap: 4px;
}
.condition-child > .condition-tree {
  flex: 1;
}
.condition-actions {
  margin-top: 6px;
}
</style>
