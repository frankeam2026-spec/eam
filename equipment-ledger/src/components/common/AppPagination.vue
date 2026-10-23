<script setup lang="ts">
import { computed } from 'vue';
import { ElPagination } from 'element-plus';
import { PAGE_SIZES } from '../../utils/pagination';
const props = withDefaults(
  defineProps<{
    page: number;
    pageSize: number;
    total: number;
    pageSizes?: number[];
    showSizes?: boolean;
  }>(),
  { pageSizes: () => [...PAGE_SIZES], showSizes: true },
);
const emit = defineEmits<{ 'update:page': [value: number]; 'update:pageSize': [value: number] }>();
const current = computed(() =>
  Math.min(Math.max(1, props.page), Math.max(1, Math.ceil(props.total / props.pageSize))),
);
function size(value: number) {
  emit('update:pageSize', value);
  emit('update:page', 1);
}
</script>
<template>
  <div class="app-pagination">
    <ElPagination
      :current-page="current"
      :page-size="pageSize"
      :total="total"
      :page-sizes="pageSizes"
      :layout="showSizes ? 'sizes, prev, pager, next, jumper' : 'prev, pager, next'"
      :pager-count="5"
      size="small"
      background
      @update:current-page="$emit('update:page', $event)"
      @update:page-size="size"
    />
  </div>
</template>
<style scoped>
.app-pagination {
  margin-left: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}
.app-pagination :deep(.el-pagination) {
  --el-pagination-button-height: 28px;
  --el-pagination-button-width: 28px;
  font-size: 13px;
  gap: 0;
  margin: 0;
}
.app-pagination :deep(.el-pagination button),
.app-pagination :deep(.el-pager li) {
  height: 28px;
  min-width: 28px;
  padding: 0;
  margin: 0 3px;
  font-size: 13px;
}
.app-pagination :deep(.el-pagination__jump) {
  margin-left: 10px;
  font-size: 13px;
}
.app-pagination :deep(.el-pagination__sizes) {
  margin-right: 8px;
}
.app-pagination :deep(.el-pagination__sizes .el-select) {
  width: 104px;
}
.app-pagination :deep(.el-input__inner) {
  height: 24px;
  border: 0;
  padding: 0;
  background: transparent;
  text-align: center;
  font-size: 13px;
  box-shadow: none;
}
.app-pagination :deep(.el-select__wrapper),
.app-pagination :deep(.el-input__wrapper) {
  min-height: 28px;
}
@media (max-width: 600px) {
  .app-pagination :deep(.el-pagination__jump),
.app-pagination :deep(.el-pagination__sizes) {
    display: none;
  }
}
</style>
