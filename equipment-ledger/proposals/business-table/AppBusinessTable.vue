<script setup lang="ts" generic="Row extends object">
import { ref } from 'vue';
import { ElTable, ElTableColumn, ElButton } from 'element-plus';
import type { TableInstance } from 'element-plus';
import 'element-plus/es/components/table/style/css';
import 'element-plus/es/components/loading/style/css';
import AppPagination from '../../src/components/common/AppPagination.vue';
import type { BusinessColumn, BusinessSort } from './BusinessTable.types';

const props = withDefaults(
  defineProps<{
    rows: Row[];
    columns: BusinessColumn<Row>[];
    rowKey: Extract<keyof Row, string>;
    loading?: boolean;
    error?: string;
    filtered?: boolean;
    selectable?: boolean;
    canSelectRow?: (row: Row, index: number) => boolean;
    actionWidth?: number;
    pagination?: boolean;
    total?: number;
    defaultSort?: { prop: string; order: 'ascending' | 'descending' };
  }>(),
  {
    loading: false,
    error: '',
    filtered: false,
    selectable: false,
    actionWidth: 156,
    pagination: true,
    total: 0,
  },
);
const page = defineModel<number>('page', { default: 1 });
const pageSize = defineModel<number>('pageSize', { default: 10 });
const emit = defineEmits<{
  'selection-change': [rows: Row[]];
  'sort-change': [sort: BusinessSort];
  'column-resize': [prop: string, width: number];
  'row-double-click': [row: Row];
  retry: [];
}>();
const table = ref<TableInstance>();

function cellText(row: Row, column: BusinessColumn<Row>): string {
  const value = row[column.prop];
  if (column.formatter) return column.formatter(value, row);
  return value == null ? '' : String(value); // Keep 0 and false; missing values stay blank.
}
function resized(width: number, _oldWidth: number, column: { property?: string }) {
  if (column.property) emit('column-resize', column.property, width);
}
defineExpose({
  clearSelection: () => table.value?.clearSelection(),
  doLayout: () => table.value?.doLayout(),
});
</script>

<template>
  <section class="app-business-table">
    <div class="table-body" :aria-busy="loading">
      <ElTable
        ref="table"
        border
        v-loading="loading"
        :data="error ? [] : rows"
        :row-key="rowKey"
        :default-sort="defaultSort"
        height="100%"
        table-layout="fixed"
        @selection-change="emit('selection-change', $event)"
        @sort-change="emit('sort-change', { prop: $event.prop, order: $event.order })"
        @header-dragend="resized"
        @row-dblclick="emit('row-double-click', $event)"
      >
        <ElTableColumn
          v-if="selectable"
          type="selection"
          fixed="left"
          :width="44"
          :reserve-selection="true"
          :resizable="false"
          :selectable="canSelectRow"
        />
        <ElTableColumn
          v-for="column in columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth || 120"
          :fixed="column.fixed"
          :align="column.align || 'left'"
          :sortable="column.sortable ? 'custom' : false"
          :show-overflow-tooltip="column.overflowTooltip ?? false"
        >
          <template #header="scope">
            <slot :name="`header-${column.prop}`" :column="column" :scope="scope">
              {{ column.label }}
            </slot>
          </template>
          <template #default="scope">
            <slot :name="column.prop" :row="scope.row" :column="column" :index="scope.$index">
              {{ cellText(scope.row, column) }}
            </slot>
          </template>
        </ElTableColumn>
        <ElTableColumn
          :resizable="false"
          v-if="$slots.actions"
          label="操作"
          fixed="right"
          :width="Math.max(156, actionWidth)"
        >
          <template #default="scope">
            <div class="row-actions">
              <slot name="actions" :row="scope.row" :index="scope.$index" />
            </div>
          </template>
        </ElTableColumn>
        <template #empty>
          <div class="empty-state" :role="error ? 'alert' : 'status'">
            <template v-if="loading">正在加载…</template>
            <template v-else-if="error">
              <span>{{ error }}</span
              ><ElButton link type="primary" @click="emit('retry')">重试</ElButton>
            </template>
            <template v-else>{{ filtered ? '没有符合条件的数据' : '暂无数据' }}</template>
          </div>
        </template>
      </ElTable>
    </div>
    <footer v-if="pagination || $slots['footer-actions']" class="table-footer">
      <div v-if="pagination" class="pagination">
        <span>共 {{ total }} 条</span>
        <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </div>
      <div v-if="$slots['footer-actions']" class="footer-actions">
        <slot name="footer-actions" />
      </div>
    </footer>
  </section>
</template>

<style scoped>
.app-business-table {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  overflow: hidden;
  border: 1px solid var(--border-color, #e4e9f0);
  border-radius: 6px;
  background: var(--surface, #fff);
  color: var(--text, #263246);
  font-size: 13px;
}
.table-body {
  flex: 1;
  min-height: 0;
}
.app-business-table :deep(.el-table) {
  --el-table-text-color: var(--text, #263246);
  --el-table-header-text-color: var(--text, #263246);
  --el-table-header-bg-color: var(--table-header-bg, #f8f9fb);
  --el-table-tr-bg-color: var(--surface, #fff);
  --el-table-border-color: var(--border-color, #e4e9f0);
  font-size: 13px;
}
.app-business-table :deep(th.el-table__cell) {
  height: 40px;
  font-weight: 700;
}
.app-business-table :deep(td.el-table__cell) {
  height: 44px;
  padding: 8px 0;
}
.row-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.row-actions :deep(.el-button) {
  margin-left: 0;
  font-size: 13px;
}
.table-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex-wrap: wrap;
  flex-shrink: 0;
  min-height: 42px;
  padding: 6px 12px;
  border-top: 1px solid var(--border-color, #e4e9f0);
}
.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
.empty-state {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 24px;
}

/* Enable native column resizing while keeping the horizontal-divider visual style. */
.app-business-table :deep(.el-table--border .el-table__cell) {
  border-right: 0;
}
.app-business-table :deep(.el-table--border::after),
.app-business-table :deep(.el-table__border-left-patch) {
  display: none;
}
</style>
