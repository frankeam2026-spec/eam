<script setup lang="ts">
import { ElTag, ElButton, ElTooltip } from 'element-plus';
import { conditionLabel } from '../../query/advancedFilters';
import type { FilterCondition, FilterField } from '../../query/advancedFilters';
defineProps<{ conditions: FilterCondition[]; fields: FilterField[]; editable?: boolean }>();
const emit = defineEmits<{
  change: [conditions: FilterCondition[]];
  edit: [condition: FilterCondition, anchor: HTMLElement];
}>();
</script>
<template>
  <div
    v-if="conditions.length || $slots.leading"
    class="applied-filter-chips"
    aria-label="已应用筛选条件"
  >
    <slot name="leading" />
    <ElTag
      v-for="(condition, index) in conditions"
      :key="condition.field"
      closable
      @close="
        emit(
          'change',
          conditions.filter((_, i) => i !== index),
        )
      "
    >
      <ElTooltip v-if="editable" :content="conditionLabel(condition, fields)" placement="top">
        <ElButton
          link
          class="condition-chip-edit"
          @click="emit('edit', condition, $event.currentTarget as HTMLElement)"
          >{{ conditionLabel(condition, fields) }}</ElButton
        >
      </ElTooltip>
      <template v-else>{{ conditionLabel(condition, fields) }}</template>
    </ElTag>
    <ElButton v-if="conditions.length" link type="primary" @click="emit('change', [])"
      >清空筛选</ElButton
    >
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
.applied-filter-chips .el-tag {
  min-height: 28px;
  height: auto;
  max-width: 100%;
  font-size: 13px;
}
.applied-filter-chips :deep(.el-tag__content) {
  white-space: normal;
  overflow-wrap: anywhere;
  padding: 3px 0;
}
.applied-filter-chips :deep(.el-tag__close) {
  flex-shrink: 0;
}
.condition-chip-edit {
  max-width: min(280px, 65vw);
  font-size: 13px;
}
.condition-chip-edit :deep(> span) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
</style>
