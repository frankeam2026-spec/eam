<script setup lang="ts">
import { conditionLabel } from '../../query/advancedFilters';
import type { FilterCondition, FilterField } from '../../query/advancedFilters';
defineProps<{ conditions: FilterCondition[]; fields: FilterField[] }>();
const emit = defineEmits<{ change: [conditions: FilterCondition[]] }>();
</script>
<template>
  <div v-if="conditions.length" class="applied-filter-chips" aria-label="已应用筛选条件">
    <button
      v-for="(condition, index) in conditions"
      :key="condition.field"
      type="button"
      :aria-label="`移除${conditionLabel(condition, fields)}`"
      @click="
        emit(
          'change',
          conditions.filter((_, i) => i !== index),
        )
      "
    >
      {{ conditionLabel(condition, fields) }} <span>×</span>
    </button>
    <button type="button" class="clear" @click="emit('change', [])">清空筛选</button>
  </div>
</template>
<style scoped>
.applied-filter-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  padding-bottom: 10px;
  flex-shrink: 0;
  font-size: 13px;
}
.applied-filter-chips button {
  border: 0;
  border-radius: 4px;
  padding: 5px 9px;
  background: var(--el-color-primary-light-9);
  color: var(--el-color-primary);
  font: inherit;
  cursor: pointer;
}
.applied-filter-chips span {
  margin-left: 8px;
}
.applied-filter-chips .clear {
  background: transparent;
  color: var(--el-text-color-secondary);
}
</style>
