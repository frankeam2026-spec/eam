<script setup lang="ts" generic="Row extends object">import { tr } from '../../i18n';

import { ref, normalizeClass, watch, onMounted } from 'vue';
import { ElTable, ElTableColumn, ElButton, ElLoading } from 'element-plus';
import type { TableInstance } from 'element-plus';
import 'element-plus/es/components/table/style/css';
import 'element-plus/es/components/loading/style/css';
import AppPagination from './AppPagination.vue';
import type { BusinessColumn, BusinessSort } from './BusinessTable.types';

const props = withDefaults(
  defineProps<{
    rows?: Row[];
    sortable?: boolean;
    unsortableFields?: string[];
    fill?: boolean;
    framed?: boolean;
    borderMode?: 'all' | 'header' | 'none';
    showHeader?: boolean;
    maxHeight?: number;
    highlightCurrentRow?: boolean;
    currentRowKey?: string | number;
    rowClass?: (row: Row, index: number) => unknown;
    columns?: BusinessColumn<Row>[];
    rowKey?: Extract<keyof Row, string>;
    loading?: boolean;
    error?: string;
    filtered?: boolean;
    selectable?: boolean;
    selectionWidth?: number;
    selectionFixed?: boolean | 'left' | 'right';
    selectionAlign?: 'left' | 'center' | 'right';
    canSelectRow?: (row: Row, index: number) => boolean;
    actionWidth?: number;
    pagination?: boolean;
    total?: number;
    defaultSort?: { prop: string; order: 'ascending' | 'descending' };
  }>(),
  {
    rows: () => [],
    sortable: false,
    unsortableFields: () => [],
    columns: () => [],
    fill: true,
    framed: true,
    borderMode: 'header',
    showHeader: true,
    loading: false,
    error: '',
    filtered: false,
    selectable: false,
    actionWidth: 156,
    pagination: true,
    total: 0,
  },
);
const selection = defineModel<Row[]>('selection');
const selectionIds = defineModel<string[]>('selectionIds');
const page = defineModel<number>('page', { default: 1 });
const pageSize = defineModel<number>('pageSize', { default: 20 });
const emit = defineEmits<{
  'selection-change': [rows: Row[]];
  'sort-change': [sort: BusinessSort];
  'column-resize': [prop: string, width: number];
  'row-double-click': [row: Row];
  'row-click': [row: Row];
  retry: [];
}>();
const vLoading = ElLoading.directive;
const table = ref<TableInstance>();
const anonymousKeys = new WeakMap<object, string>();
let sequence = 0;
function stableRowKey(row: Row) {
  const value = props.rowKey ? row[props.rowKey] : (row as Record<string, unknown>).id;
  if (value !== undefined && value !== null && value !== '') return String(value);
  if (!anonymousKeys.has(row)) anonymousKeys.set(row, `draft-${++sequence}`);
  return anonymousKeys.get(row)!;
}
// Delegate checkbox rendering and selection storage to ElTable; synchronize a controlled model when supplied.
let syncingSelection = false;
let nativeSelectionKeys = new Set<string>();
function selectionChanged(rows: Row[]) {
  if (syncingSelection) return;
  selection.value = rows;
  const keys = rows.map(stableRowKey);
  if (selectionIds.value !== undefined) {
    const unresolved = selectionIds.value.filter(
      (id) => !nativeSelectionKeys.has(id) && !keys.includes(id),
    );
    selectionIds.value = [...unresolved, ...keys];
  }
  nativeSelectionKeys = new Set(keys);
  emit('selection-change', rows);
}
function syncSelection(desired: Row[] | undefined) {
  const instance = table.value;
  if (!instance || desired === undefined || !props.selectable) return;
  const current = instance.getSelectionRows() as Row[];
  const desiredKeys = new Set(desired.map(stableRowKey));
  const currentKeys = new Set(current.map(stableRowKey));
  if (
    desiredKeys.size === currentKeys.size &&
    [...desiredKeys].every((key) => currentKeys.has(key))
  )
    return;
  syncingSelection = true;
  try {
    if (!desired.length) instance.clearSelection();
    else {
      current
        .filter((row) => !desiredKeys.has(stableRowKey(row)))
        .forEach((row) => instance.toggleRowSelection(row, false));
      desired
        .filter((row) => !currentKeys.has(stableRowKey(row)))
        .forEach((row) => instance.toggleRowSelection(row, true));
    }
  } finally {
    syncingSelection = false;
  }
  selectionChanged(instance.getSelectionRows() as Row[]);
}
function syncSelectionIds(ids: string[] | undefined) {
  if (!table.value || ids === undefined) return;
  const known = new Map<string, Row>();
  const collect = (rows: Row[]) => {
    rows.forEach((row) => {
      known.set(stableRowKey(row), row);
      const children = (row as Record<string, unknown>).children;
      if (Array.isArray(children)) collect(children as Row[]);
    });
  };
  collect(table.value.getSelectionRows() as Row[]);
  collect(props.rows);
  syncSelection(ids.flatMap((id) => (known.has(id) ? [known.get(id)!] : [])));
}
watch(selection, syncSelection, { flush: 'post' });
watch([selectionIds, () => props.rows], ([ids]) => syncSelectionIds(ids), { flush: 'post' });
onMounted(() => {
  syncSelection(selection.value);
  syncSelectionIds(selectionIds.value);
});

function rowClassName({ row, rowIndex }: { row: Row; rowIndex: number }) {
  return normalizeClass(props.rowClass?.(row, rowIndex));
}

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
  toggleRowSelection: (row: Row, selected: boolean) =>
    table.value?.toggleRowSelection(row, selected),
  toggleRowExpansion: (row: Row, expanded: boolean) =>
    table.value?.toggleRowExpansion(row, expanded),
  doLayout: () => table.value?.doLayout(),
});
</script>

<template>
  <section
    class="app-business-table"
    :data-border-mode="borderMode"
    :class="{ 'is-inline': !fill, 'is-frameless': !framed, 'is-empty': !rows.length }"
  >
    <div class="table-body" :aria-busy="loading">
      <ElTable
        ref="table"
        border
        v-loading="loading"
        :data="error ? [] : rows"
        :row-key="stableRowKey"
        :row-class-name="rowClassName"
        :show-header="showHeader"
        :max-height="maxHeight"
        :highlight-current-row="highlightCurrentRow"
        :current-row-key="currentRowKey"
        :default-sort="defaultSort"
        :height="fill ? '100%' : undefined"
        table-layout="fixed"
        @selection-change="selectionChanged"
        @sort-change="emit('sort-change', { prop: $event.prop, order: $event.order })"
        @header-dragend="resized"
        @row-dblclick="emit('row-double-click', $event)"
        @row-click="emit('row-click', $event)"
      >
        <ElTableColumn
          v-if="selectable"
          type="selection"
          :width="selectionWidth"
          :fixed="selectionFixed"
          :align="selectionAlign"
          :header-align="selectionAlign"
          :resizable="false"
          :reserve-selection="true"
          :selectable="canSelectRow"
        />
        <slot />
        <ElTableColumn
          v-for="column in columns"
          :key="column.prop"
          :prop="column.prop"
          :label="column.label"
          :width="column.width"
          :min-width="column.minWidth || 120"
          :fixed="column.fixed"
          :resizable="column.resizable ?? true"
          :align="column.align || 'left'"
          :sortable="
            (column.sortable ?? sortable) && !unsortableFields.includes(column.prop)
              ? 'custom'
              : false
          "
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
          v-if="$slots.actions"
          :label="tr('ui.equipment.text0145')"
          fixed="right"
          :width="170"
          :min-width="156"
          :resizable="false"
        >
          <template #default="scope">
            <div class="row-actions">
              <slot name="actions" :row="scope.row" :index="scope.$index" />
            </div>
          </template>
        </ElTableColumn>
        <template #empty>
          <slot name="empty"
            ><div class="empty-state" :role="error ? 'alert' : 'status'">
              <template v-if="loading">{{ tr('ui.equipment.text0162') }}</template>
              <template v-else-if="error">
                <span>{{ error }}</span
                ><ElButton link type="primary" @click="emit('retry')">{{ tr('ui.equipment.text0299') }}</ElButton>
              </template>
              <template v-else>{{ filtered ? tr('ui.equipment.text1339') : tr('ui.equipment.text0308') }}</template>
            </div></slot
          >
        </template>
      </ElTable>
    </div>
    <slot v-if="$slots.footer" name="footer" />
    <footer v-else-if="pagination || $slots['footer-actions']" class="table-footer">
      <div v-if="pagination" class="pagination">
        <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span>
        <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
      </div>
      <div v-if="$slots['footer-actions']" class="footer-actions">
        <slot name="footer-actions" />
      </div>
    </footer>
  </section>
</template>

<style scoped>
/* Shared layout and border appearance; ElTable owns sorting, selection and resizing. */
.app-business-table {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  height: 100%;
  overflow: hidden;
}
/* Native column resizing requires border=true; border modes only change presentation. */
.app-business-table[data-border-mode='header'] :deep(.el-table.el-table--border td.el-table__cell),
.app-business-table[data-border-mode='none'] :deep(.el-table.el-table--border .el-table__cell) {
  border-right-color: transparent;
}
.app-business-table:not([data-border-mode='all']) :deep(.el-table::before),
.app-business-table:not([data-border-mode='all']) :deep(.el-table::after),
.app-business-table:not([data-border-mode='all']) :deep(.el-table__border-left-patch),
.app-business-table:not([data-border-mode='all']) :deep(.el-table__border-right-patch) {
  display: none;
}
.app-business-table[data-border-mode='none'] :deep(.el-table__inner-wrapper::after) {
  display: none;
}
/* Legacy action-column shadows must not reintroduce a body grid line. */
.app-business-table:not([data-border-mode='all']) :deep(td.list-action-column) {
  box-shadow: none;
}
.table-body {
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.is-inline {
  height: auto;
  overflow: visible;
}
.app-business-table :deep(.el-table) {
  font-size: 13px;
}
.app-business-table :deep(th.el-table__cell) {
  font-weight: 700;
}
.app-business-table > .table-footer {
  position: static;
  inset: auto;
  flex: none;
  display: flex;
  align-items: center;
  gap: 12px;
  width: auto;
  margin: 0;
  padding: 6px 12px;
  min-height: var(--list-footer-height, 42px);
  box-shadow: none;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}
.pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.footer-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-left: auto;
}
/* Header filter layout is shared; native sorting carets stay untouched. */
.app-business-table :deep(th .cell) {
  white-space: nowrap;
}
.app-business-table :deep(th .cell:has(.table-column-filter)) {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}
.app-business-table :deep(th .cell:has(.table-column-filter) > .business-column-label),
.app-business-table :deep(th .cell:has(.table-column-filter) > .table-header-label) {
  flex: 1;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.app-business-table :deep(.table-column-filter) {
  flex: 0 0 100%;
  order: 1;
  width: 100%;
  max-width: 100%;
  min-width: 0;
  margin-top: 6px;
}
.app-business-table :deep(.table-column-filter .el-input),
.app-business-table :deep(.table-column-filter .el-select) {
  width: 100%;
  min-width: 0;
}
.app-business-table :deep(.row-actions) {
  display: flex;
  align-items: center;
  gap: 12px;
  white-space: nowrap;
}
.app-business-table :deep(.row-actions .el-button + .el-button) {
  margin-left: 0;
}
</style>
