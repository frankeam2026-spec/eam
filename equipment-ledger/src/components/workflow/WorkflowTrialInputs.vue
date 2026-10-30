<script setup lang="ts">
import { ref } from 'vue';
import { ElForm, ElInput, ElButton, ElTableColumn } from 'element-plus';
import { tr } from '../../i18n';
import { workflowApi, type WorkflowVariable } from '../../workflow';
import type { SelectorRow } from '../../selectors';
import AppFormField from '../common/AppFormField.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
type Line = { code: string; materialId: string; quantity: string };
const props = defineProps<{
  modelValue: Record<string, string>;
  lines: Line[];
  variables: WorkflowVariable[];
  companyId: string;
  disabled?: boolean;
}>();
const emit = defineEmits<{
  'update:modelValue': [Record<string, string>];
  'update:lines': [Line[]];
}>();
const options = ref<SelectorRow[]>([]);
const kinds: Record<string, string> = {
  applicantId: 'users',
  applicantDepartmentId: 'departments',
  companyId: 'companies',
  warehouseId: 'warehouses',
  departmentId: 'departments',
  equipmentId: 'equipment',
  categoryId: 'categories',
  faultTypeId: 'faultTypes',
};
const choices = (kind: string) => (q: string, p: number, s: number) =>
  workflowApi.choices(props.companyId, kind, q, p, s);
function merge(rows: SelectorRow[]) {
  options.value = [...new Map([...options.value, ...rows].map((row) => [row.id, row])).values()];
}
function value(key: string, value: string) {
  emit('update:modelValue', { ...props.modelValue, [key]: value });
}
function line(index: number, patch: Partial<Line>) {
  emit(
    'update:lines',
    props.lines.map((row, i) => (i === index ? { ...row, ...patch } : row)),
  );
}
function material(index: number, value: string | string[]) {
  const id = String(value),
    code = options.value.find((row) => row.id === id)?.code;
  line(index, { materialId: id, ...(code ? { code } : {}) });
}
</script>
<template>
  <ElForm label-position="left" label-width="140px" :disabled="disabled">
    <AppFormField
      v-for="v in variables.filter((v) => v.available && !v.key.startsWith('lines.'))"
      :key="v.key"
      :label="v.label"
    >
      <PagedChoiceField
        v-if="kinds[v.key]"
        :model-value="modelValue[v.key] || ''"
        :options="options"
        :disabled="disabled || !companyId"
        :context="companyId + v.key"
        :load-rows="choices(kinds[v.key]!)"
        :columns="[{ key: 'name', label: tr('ui.equipment.text0404') }]"
        @select="merge"
        @update:model-value="value(v.key, String($event))"
      />
      <ElInput
        v-else
        :model-value="modelValue[v.key] || ''"
        @update:model-value="value(v.key, $event)"
      />
    </AppFormField>
  </ElForm>
  <template v-if="variables.some((v) => v.key.startsWith('lines.'))">
    <h3>{{ tr('workflow.ui.071') }}</h3>
    <AppBusinessTable :rows="lines" :fill="false" :pagination="false">
      <ElTableColumn :label="tr('workflow.ui.072')"
        ><template #default="s"
          ><ElInput
            :model-value="s.row.code"
            :disabled="disabled"
            @update:model-value="line(s.$index, { code: $event })" /></template
      ></ElTableColumn>
      <ElTableColumn :label="tr('ui.equipment.text0515')"
        ><template #default="s"
          ><PagedChoiceField
            :model-value="s.row.materialId"
            :options="options"
            :disabled="disabled || !companyId"
            :context="companyId + 'materials'"
            :load-rows="choices('materials')"
            :columns="[{ key: 'name', label: tr('ui.equipment.text0404') }]"
            @select="merge"
            @update:model-value="material(s.$index, $event)" /></template
      ></ElTableColumn>
      <ElTableColumn :label="tr('workflow.ui.074')"
        ><template #default="s"
          ><ElInput
            :model-value="s.row.quantity"
            :disabled="disabled"
            @update:model-value="line(s.$index, { quantity: $event })" /></template
      ></ElTableColumn>
      <ElTableColumn :label="tr('workflow.ui.034')" min-width="156"
        ><template #default="s"
          ><ElButton
            text
            type="danger"
            :disabled="disabled"
            @click="
              emit(
                'update:lines',
                lines.filter((_, i) => i !== s.$index),
              )
            "
            >{{ tr('ui.equipment.text0146') }}</ElButton
          ></template
        ></ElTableColumn
      >
    </AppBusinessTable>
    <ElButton
      :disabled="disabled || lines.length >= 500"
      @click="emit('update:lines', [...lines, { code: '', materialId: '', quantity: '1' }])"
      >{{ tr('workflow.ui.075') }}</ElButton
    >
  </template>
</template>
