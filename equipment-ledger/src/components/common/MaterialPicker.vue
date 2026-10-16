<script setup lang="ts">
import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import { computed, ref, watch } from 'vue';
import { ElButton, ElCheckbox } from 'element-plus';
import AppDialog from './AppDialog.vue';
import AppPagination from './AppPagination.vue';
import ListSearchInput from './ListSearchInput.vue';
import { formatNumber } from '../../utils/number';
import type { InventoryMaster } from '../../inventory';

const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    multiple?: boolean;
    disabledIds?: string[];
    showStock?: boolean;
    stockContext?: string;
    loadRows: (
      q: string,
      page: number,
      size: number,
    ) => Promise<{ data: InventoryMaster[]; page: { total: number } }>;
  }>(),
  { multiple: true, disabledIds: () => [] },
);
const emit = defineEmits<{ 'update:modelValue': [boolean]; select: [InventoryMaster[]] }>();
const query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  total = ref(0);
const rows = ref<InventoryMaster[]>([]),
  selected = ref(new Map<string, InventoryMaster>());
const loading = ref(false),
  error = ref('');
let revision = 0;
const allChecked = computed(
  () =>
    rows.value.length > 0 &&
    rows.value.filter((r) => !props.disabledIds.includes(r.id)).length > 0 &&
    rows.value
      .filter((r) => !props.disabledIds.includes(r.id))
      .every((r) => selected.value.has(r.id)),
);
const someChecked = computed(
  () => rows.value.some((r) => selected.value.has(r.id)) && !allChecked.value,
);
async function load() {
  if (!props.modelValue) return;
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await props.loadRows(applied.value, page.value, size.value);
    if (current !== revision) return;
    rows.value = result.data;
    total.value = result.page.total;
  } catch (e) {
    if (current === revision) error.value = e instanceof Error ? e.message : '加载物料失败';
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(
  () => props.modelValue,
  (open) => {
    if (!open) {
      revision++;
      loading.value = false;
      return;
    }
    query.value = applied.value = '';
    page.value = 1;
    selected.value = new Map();
    rows.value = [];
    load();
  },
);
watch([page, size], load);
function search() {
  applied.value = query.value.trim();
  page.value = 1;
  load();
}
function toggle(row: InventoryMaster, checked: boolean) {
  if (props.disabledIds.includes(row.id)) return;
  const next = props.multiple ? new Map(selected.value) : new Map<string, InventoryMaster>();
  if (checked) next.set(row.id, row);
  else next.delete(row.id);
  selected.value = next;
}
function togglePage(checked: boolean) {
  const next = new Map(selected.value);
  rows.value
    .filter((r) => !props.disabledIds.includes(r.id))
    .forEach((r) => (checked ? next.set(r.id, r) : next.delete(r.id)));
  selected.value = next;
}
function confirm() {
  emit('select', [...selected.value.values()]);
  emit('update:modelValue', false);
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => rows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>

<template>
  <AppDialog
    :model-value="modelValue"
    title="选择物料"
    :width="showStock ? '1120px' : '960px'"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <form class="material-picker-toolbar" @submit.prevent="search">
      <span>已选 {{ selected.size }} 项</span>
      <span v-if="showStock && stockContext" class="stock-context">库存：{{ stockContext }}</span>
      <ElButton text :disabled="!selected.size" @click="selected = new Map()">清空</ElButton>
      <ListSearchInput v-model="query" placeholder="搜索料号、名称、规格型号、分类" />
    </form>
    <div class="material-picker-table" :aria-busy="loading">
      <p v-if="error" role="alert">{{ error }} <ElButton text @click="load">重试</ElButton></p>
      <AppBusinessTable
        :rows="rows"
        :pagination="false"
        :fill="false"
        :framed="false"
        v-else
        class="business-table"
        :row-class="(row, tableRowIndex) => ({ selected: selected.has(row.id) })"
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn
          fixed="left"
          :min-width="44"
          :width="44"
          :resizable="false"
          class-name="selection"
          ><template #header
            ><div class="table-header-label">
              <ElCheckbox
                v-if="multiple"
                :model-value="allChecked"
                :indeterminate="someChecked"
                aria-label="选择当前页物料"
                @change="togglePage(Boolean($event))"
              /></div></template
          ><template #default="{ row: row, $index: tableRowIndex }">
            <ElCheckbox
              :model-value="selected.has(row.id) || disabledIds.includes(row.id)"
              :disabled="disabledIds.includes(row.id)"
              :aria-label="`选择${row.code} ${row.name}`"
              @change="toggle(row, Boolean($event))"
            /> </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">料号</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            row.code
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="210"
          ><template #header><div class="table-header-label">物料名称</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            row.name
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">规格型号</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            row.model
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">单位</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            row.unit
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">分类</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            row.category
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn v-if="showStock" :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">在库数量</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            formatNumber(row.stockQuantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn v-if="showStock" :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">预留数量</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            formatNumber(row.reservedQuantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow1} -->
        <ElTableColumn v-if="showStock" :min-width="150" class-name="numeric"
          ><template #header><div class="table-header-label">可用数量</div></template
          ><template #default="{ row: row, $index: tableRowIndex }">{{
            formatNumber(row.availableQuantity)
          }}</template></ElTableColumn
        ><template #empty
          ><div v-if="!rows.length">
            {{ loading ? '正在加载…' : applied ? '没有符合条件的物料' : '暂无可选物料' }}
          </div></template
        ></AppBusinessTable
      >
    </div>
    <div class="material-picker-footer">
      <span>共 {{ total }} 条</span
      ><AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
    </div>
    <template #footer
      ><ElButton @click="emit('update:modelValue', false)">取消</ElButton
      ><ElButton type="primary" :disabled="!selected.size" @click="confirm"
        >添加{{ multiple ? `（${selected.size}）` : '' }}</ElButton
      ></template
    >
  </AppDialog>
</template>

<style scoped>
.material-picker-toolbar,
.material-picker-footer {
  display: flex;
  align-items: center;
  gap: 8px;
}
.material-picker-toolbar {
  margin-bottom: 8px;
}
.material-picker-toolbar > span {
  flex: none;
  white-space: nowrap;
}
.material-picker-toolbar :deep(.list-search-input) {
  margin-left: auto;
  width: 340px;
}
.material-picker-table {
  max-height: 48vh;
  overflow: auto;
  border: 1px solid var(--el-border-color-light);
  border-radius: 4px;
}
.material-picker-table table {
  width: 100%;
  min-width: 720px;
  table-layout: fixed;
}
.material-picker-table :deep(th) {
  position: sticky;
  top: 0;
  z-index: 1;
}
.material-picker-table :deep(th),
.material-picker-table :deep(td) {
  overflow-wrap: anywhere;
}
.material-picker-table .numeric {
  text-align: right;
  white-space: nowrap;
  font-variant-numeric: tabular-nums;
}
.stock-context {
  max-width: 220px;
  overflow: hidden;
  text-overflow: ellipsis;
}
.material-picker-table .selection {
  width: 42px;
}
.material-picker-table :deep(tr.selected) {
  background: var(--el-color-primary-light-9);
}
.material-picker-table .empty {
  padding: 40px 12px;
  text-align: center;
}
.material-picker-footer {
  padding-top: 12px;
}
</style>
