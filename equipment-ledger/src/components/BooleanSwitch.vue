<script setup lang="ts">
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
      aria-label="是否"
      @update:model-value="$emit('update:modelValue', String($event))"
    /><span class="boolean-value" :class="{ enabled: modelValue === '是' }">{{
      modelValue === '是' ? '是' : '否'
    }}</span></span
  >
</template>
