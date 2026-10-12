<script setup lang="ts">
import { computed, ref } from 'vue';
import { ElInput } from 'element-plus';
import { formatNumber } from '../../utils/number';

// Preserve the original decimal value for API submission and calculations.
// Show two decimals outside editing without rounding the stored model.
const props = defineProps<{ modelValue?: string | number | null }>();
const emit = defineEmits<{ 'update:modelValue': [value: string] }>();
const focused = ref(false);
const displayed = computed(() =>
  focused.value ? String(props.modelValue ?? '') : formatNumber(props.modelValue),
);
</script>

<template>
  <ElInput
    :model-value="displayed"
    inputmode="decimal"
    @focus="focused = true"
    @blur="focused = false"
    @update:model-value="emit('update:modelValue', $event)"
  />
</template>
