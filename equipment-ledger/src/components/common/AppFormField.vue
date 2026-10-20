<script setup lang="ts">
import { ElFormItem, type FormItemRule } from 'element-plus';
import 'element-plus/es/components/form/style/css';
import 'element-plus/es/components/form-item/style/css';
withDefaults(
  defineProps<{
    label: string;
    prop?: string;
    error?: string;
    required?: boolean;
    rules?: FormItemRule | FormItemRule[];
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
    :rules="rules"
    :label-width="labelWidth"
    ><slot
  /></ElFormItem>
</template>
