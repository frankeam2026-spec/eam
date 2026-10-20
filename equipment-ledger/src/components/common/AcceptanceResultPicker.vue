<script setup lang="ts">
import { ElRadioGroup, ElRadioButton } from 'element-plus';
import { acceptanceOptions, type AcceptanceDecision } from '../../config/acceptance';
withDefaults(defineProps<{ disabled?: boolean; ariaLabel?: string }>(), {
  disabled: false,
  ariaLabel: '验收结果',
});
const decision = defineModel<AcceptanceDecision>({ required: true });
</script>
<template>
  <ElRadioGroup v-model="decision" :disabled="disabled" :aria-label="ariaLabel">
    <ElRadioButton v-for="option in acceptanceOptions" :key="option.value" :value="option.value">{{
      option.label
    }}</ElRadioButton>
  </ElRadioGroup>
</template>
