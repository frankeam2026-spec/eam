<script setup lang="ts">import { tr } from '../../i18n';

import { computed } from 'vue';
import { ElInput, ElDatePicker, ElSelect, ElOption } from 'element-plus';
import AppDecimalInput from '../common/AppDecimalInput.vue';
import BusinessReferencePicker from './BusinessReferencePicker.vue';
import {
  isReference,
  inlineOptions,
  isMultiple,
  type BusinessField,
  type BusinessObject,
  type FieldValue,
} from '../../businessConfig';
const props = defineProps<{
  field: BusinessField;
  object: BusinessObject;
  disabled?: boolean;
  labels?: Record<string, string>;
}>();
const model = defineModel<FieldValue>();
const emit = defineEmits<{ labels: [Record<string, string>] }>();
const selectedIds = computed(() =>
  Array.isArray(model.value) ? model.value : model.value ? [String(model.value)] : [],
);
function choose(value: string | string[] | undefined) {
  model.value = value || null;
  const ids = Array.isArray(value) ? value : value ? [value] : [];
  emit(
    'labels',
    Object.fromEntries(
      ids.map((id) => [
        id,
        props.field.options?.find((o) => o.id === id)?.name || props.labels?.[id] || id,
      ]),
    ),
  );
}
</script>
<template>
  <ElSelect
    v-if="inlineOptions(field)"
    :model-value="model"
    :multiple="isMultiple(field)"
    filterable
    clearable
    collapse-tags
    collapse-tags-tooltip
    :disabled="disabled"
    :placeholder="field.placeholder || tr('ui.equipment.text0096')"
    @update:model-value="choose($event)"
  >
    <ElOption
      v-for="option in field.options?.filter((o) => o.enabled || selectedIds.includes(o.id))"
      :key="option.id"
      :value="option.id"
      :label="selectedIds.includes(option.id) ? labels?.[option.id] || option.name : option.name"
      :disabled="!option.enabled"
    />
  </ElSelect>
  <BusinessReferencePicker
    v-else-if="isReference(field)"
    :object="object"
    :source="field.source"
    :model-value="typeof model === 'boolean' ? null : (model ?? null)"
    :multiple="isMultiple(field)"
    :labels="labels"
    :disabled="disabled"
    :placeholder="field.placeholder || `选择${field.name}`"
    @update:model-value="model = $event"
    @labels="emit('labels', $event)"
  />
  <ElSelect
    v-else-if="field.type === 'BOOLEAN'"
    :model-value="model"
    clearable
    :disabled="disabled"
    :placeholder="field.placeholder || tr('ui.equipment.text0096')"
    @update:model-value="model = $event === '' ? null : $event"
    ><ElOption :value="true" :label="tr('ui.equipment.text0067')" /><ElOption :value="false" :label="tr('ui.equipment.text0068')"
  /></ElSelect>
  <AppDecimalInput
    v-else-if="field.type === 'NUMBER'"
    :model-value="typeof model === 'string' ? model : ''"
    :disabled="disabled"
    :placeholder="field.placeholder"
    @update:model-value="model = $event"
  />
  <ElDatePicker
    v-else-if="field.type === 'DATE' || field.type === 'DATETIME'"
    :model-value="typeof model === 'string' ? model : null"
    :type="field.type === 'DATE' ? 'date' : 'datetime'"
    :value-format="field.type === 'DATE' ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'"
    :disabled="disabled"
    :placeholder="field.placeholder || tr('ui.equipment.text0096')"
    @update:model-value="model = $event || null"
  />
  <ElInput
    v-else
    :model-value="typeof model === 'string' ? model : ''"
    :type="field.type === 'LONG_TEXT' ? 'textarea' : 'text'"
    :rows="3"
    :maxlength="field.maxLength"
    :disabled="disabled"
    :placeholder="field.placeholder"
    @update:model-value="model = $event"
  />
</template>
