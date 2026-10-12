<script setup lang="ts">
import { computed } from 'vue';
import { ElInputNumber, ElSelect, ElOption } from 'element-plus';
const props = withDefaults(
  defineProps<{ modelValue: number; unit?: string; min?: number; maxYears?: number }>(),
  { unit: '时', min: 0, maxYears: 1 },
);
const emit = defineEmits<{ 'update:modelValue': [number]; 'update:unit': [string] }>();
const max = computed(
  () => (({ 时: 8760, 日: 365, 周: 52, 月: 12 })[props.unit] || 8760) * props.maxYears,
);
function changeUnit(unit: string) {
  emit('update:unit', unit);
  const bound = ({ 时: 8760, 日: 365, 周: 52, 月: 12 }[unit] || 8760) * props.maxYears;
  if (props.modelValue > bound) emit('update:modelValue', bound);
}
</script>
<template>
  <div class="duration-input">
    <ElInputNumber
      :model-value="modelValue"
      :min="min"
      :max="max"
      :precision="0"
      controls-position="right"
      @update:model-value="emit('update:modelValue', $event ?? min)"
    />
    <ElSelect :model-value="unit" aria-label="时间单位" @update:model-value="changeUnit"
      ><ElOption v-for="u in ['时', '日', '周', '月']" :key="u" :label="u" :value="u"
    /></ElSelect>
  </div>
</template>
<style scoped>
.duration-input {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  width: 100%;
}
.duration-input > .el-input-number {
  flex: 1;
  min-width: 0;
  width: auto;
}
.duration-input > .el-select {
  width: 72px;
  flex: 0 0 72px;
}
</style>
