<script setup lang="ts">
import { ElFormItem } from 'element-plus';
import 'element-plus/es/components/form/style/css';
import 'element-plus/es/components/form-item/style/css';
withDefaults(
  defineProps<{
    label: string;
    prop?: string;
    error?: string;
    required?: boolean;
    labelWidth?: string | number;
  }>(),
  { required: undefined },
);
</script>
<template>
  <ElFormItem
    class="app-form-field"
    :label="label"
    :prop="prop"
    :error="error || ''"
    :required="required"
    :label-width="labelWidth"
    ><slot
  /></ElFormItem>
</template>
