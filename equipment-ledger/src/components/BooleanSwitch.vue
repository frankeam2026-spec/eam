<script setup lang="ts">import { tr } from '../i18n';

import { ElSwitch } from 'element-plus';
withDefaults(defineProps<{ modelValue?: string; disabled?: boolean }>(), {
  modelValue: '否',
  disabled: false,
});
defineEmits<{ 'update:modelValue': [value: string] }>();
</script>
<template>
  <span class="boolean-control"
    ><ElSwitch
      :model-value="modelValue"
      active-value="是"
      inactive-value="否"
      :disabled="disabled"
      :aria-label="tr('ui.equipment.text0066')"
      @update:model-value="$emit('update:modelValue', String($event))"
    /><span class="boolean-value" :class="{ enabled: modelValue === '是' }">{{
      modelValue === '是' ? tr('ui.equipment.text0067') : tr('ui.equipment.text0068')
    }}</span></span
  >
</template>
