<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import { ElSelect, ElOption, ElButton, ElInput } from 'element-plus';
import AppDecimalInput from '../common/AppDecimalInput.vue';
import BusinessReferencePicker from './BusinessReferencePicker.vue';
import {
  type BusinessField,
  type BusinessObject,
  type FieldRule,
  isReference,
  inlineOptions,
  newField,
} from '../../businessConfig';
const props = defineProps<{
  object: BusinessObject;
  fields: BusinessField[];
  fieldId: string;
  disabled?: boolean;
}>();
const model = defineModel<FieldRule>({ required: true });
const sources = computed(() => [
  ...(['equipment', 'repair_report', 'repair_process'].includes(props.object)
    ? [
        {
          ...newField(),
          id: 'categoryId',
          name: '设备类别',
          type: 'CHOICE' as const,
          source: 'base:CATEGORY',
        },
      ]
    : []),
  ...props.fields.filter((f) => f.enabled && f.id !== props.fieldId),
]);
function source(id: string) {
  return sources.value.find((f) => f.id === id);
}
function changed(c: FieldRule['items'][number]) {
  c.operator = 'eq';
  c.values = [];
}
</script>
<template>
  <div class="business-rules">
    <div class="business-rule-heading">
      <ElSelect v-model="model.mode" :disabled="disabled" style="width: 160px"
        ><ElOption value="all" :label="tr('ui.equipment.text1284')" /><ElOption
          value="any"
          :label="tr('ui.equipment.text1285')" /></ElSelect
      ><ElButton
        :disabled="disabled || !sources.length"
        @click="model.items.push({ field: sources[0]!.id, operator: 'eq', values: [] })"
        >{{ tr('ui.equipment.text1286') }}</ElButton
      >
    </div>
    <p v-if="!model.items.length">{{ tr('ui.equipment.text1287') }}</p>
    <div v-for="(c, index) in model.items" :key="index" class="business-rule-row">
      <ElSelect v-model="c.field" filterable :disabled="disabled" @change="changed(c)"
        ><ElOption v-for="f in sources" :key="f.id" :value="f.id" :label="f.name"
      /></ElSelect>
      <ElSelect v-model="c.operator" :disabled="disabled" @change="c.values = []"
        ><ElOption value="eq" :label="tr('ui.equipment.text1288')" /><ElOption value="ne" :label="tr('ui.equipment.text1289')" /><ElOption
          v-if="source(c.field) && isReference(source(c.field)!)"
          value="in"
          :label="tr('ui.equipment.text1290')" /><ElOption
          v-if="source(c.field)?.type === 'NUMBER'"
          value="range"
          :label="tr('ui.equipment.text1291')"
      /></ElSelect>
      <div v-if="c.operator === 'range'" class="business-rule-range">
        <AppDecimalInput v-model="c.values[0]" :placeholder="tr('ui.equipment.text1292')" /><span>{{ tr('ui.equipment.text0883') }}</span
        ><AppDecimalInput v-model="c.values[1]" :placeholder="tr('ui.equipment.text1293')" />
      </div>
      <ElSelect
        v-else-if="source(c.field) && inlineOptions(source(c.field)!)"
        v-model="c.values"
        multiple
        filterable
        collapse-tags
        collapse-tags-tooltip
        :disabled="disabled"
        ><ElOption
          v-for="option in source(c.field)?.options"
          :key="option.id"
          :value="option.id"
          :label="option.name + (option.enabled ? '' : tr('ui.equipment.text1294'))"
      /></ElSelect>
      <BusinessReferencePicker
        v-else-if="source(c.field) && isReference(source(c.field)!)"
        :object="object"
        :source="source(c.field)!.source"
        :model-value="c.values"
        :multiple="true"
        :disabled="disabled"
        @update:model-value="c.values = Array.isArray($event) ? $event : $event ? [$event] : []"
      />
      <ElSelect
        v-else-if="source(c.field)?.type === 'BOOLEAN'"
        v-model="c.values[0]"
        :disabled="disabled"
        ><ElOption value="true" :label="tr('ui.equipment.text0067')" /><ElOption value="false" :label="tr('ui.equipment.text0068')"
      /></ElSelect>
      <ElInput v-else v-model="c.values[0]" :disabled="disabled" :placeholder="tr('ui.equipment.text1295')" />
      <ElButton text type="danger" :disabled="disabled" @click="model.items.splice(index, 1)"
        >{{ tr('ui.equipment.text0151') }}</ElButton
      >
    </div>
  </div>
</template>
<style scoped>
.business-rule-heading {
  display: flex;
  gap: 12px;
  align-items: center;
  margin-bottom: 12px;
}
.business-rule-row {
  display: grid;
  grid-template-columns: 150px 130px minmax(0, 1fr) 55px;
  gap: 8px;
  margin-bottom: 10px;
  align-items: center;
}
.business-rule-range {
  display: flex;
  gap: 6px;
  align-items: center;
}
.business-rule-range :deep(.el-input) {
  min-width: 0;
}
.business-rules p {
  font-size: 13px;
}
</style>
