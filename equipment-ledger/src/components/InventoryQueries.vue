<script setup lang="ts">
import { formatNumber } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton, ElInput, ElTag, ElMessage, ElMessageBox } from 'element-plus';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useAuthStore } from '../stores/auth';
import {
  inventoryQueryApi,
  operationApi,
  type InventoryQueryKind,
  type InventoryQueryRow,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const kind = computed(() => String(route.params.kind || 'balances') as InventoryQueryKind);
const titles: Record<InventoryQueryKind, string> = {
  balances: '库存余额',
  layers: '成本明细',
  transactions: '库存流水',
  transit: '在途库存',
};
const common = {
  materialCode: '料号',
  materialName: '物料名称',
  model: '规格型号',
  companyName: '所属公司',
  warehouseName: '仓库',
  binName: '库位',
  batchCode: '批次号',
  supplierBatch: '供应商批次',
  productionDate: '生产日期',
  expiryDate: '有效期至',
};
const fields: Record<InventoryQueryKind, Record<string, string>> = {
  transit: {},
  balances: {
    ...common,
    unit: '单位',
    quantity: '在库数量',
    reservedQuantity: '预留数量',
    availableQuantity: '可用数量',
    stockStatus: '库存状态',
    expiryStatus: '有效期状态',
    expiryDate: '有效期至',
    remainingCost: '库存金额（元）',
  },
  layers: {
    ...common,
    sourceCode: '来源单据',
    receivedAt: '来源入库时间',
    quantity: '原入库数量',
    remainingQuantity: '剩余数量',
    unitCost: '成本单价（元）',
    remainingCost: '剩余成本（元）',
    costStatus: '成本状态',
  },
  transactions: {
    ...common,
    kind: '业务类型',
    sourceCode: '来源单据',
    quantity: '变动数量',
    amount: '变动金额（元）',
    actorName: '操作人',
    createdAt: '操作时间',
  },
};
fields.transit = {
  ...common,
  sourceCode: '调拨单号',
  targetWarehouseName: '目标仓库',
  quantity: '在途数量',
  amount: '在途金额（元）',
  createdAt: '发出时间',
};
const defaults: Record<InventoryQueryKind, string[]> = {
  transit: [
    'materialCode',
    'materialName',
    'warehouseName',
    'targetWarehouseName',
    'batchCode',
    'sourceCode',
    'quantity',
    'amount',
    'createdAt',
  ],
  balances: [
    'materialCode',
    'materialName',
    'warehouseName',
    'binName',
    'batchCode',
    'quantity',
    'reservedQuantity',
    'availableQuantity',
    'stockStatus',
    'expiryStatus',
    'remainingCost',
  ],
  layers: [
    'materialCode',
    'materialName',
    'warehouseName',
    'batchCode',
    'sourceCode',
    'receivedAt',
    'remainingQuantity',
    'unitCost',
    'remainingCost',
  ],
  transactions: [
    'materialCode',
    'materialName',
    'warehouseName',
    'batchCode',
    'kind',
    'sourceCode',
    'quantity',
    'amount',
    'actorName',
    'createdAt',
  ],
};
const filterable = computed(
  () =>
    new Set([
      ...Object.keys(common),
      'unit',
      ...(kind.value === 'balances'
        ? ['stockStatus', 'quantity', 'reservedQuantity', 'expiryDate']
        : kind.value === 'layers'
          ? ['sourceCode', 'costStatus', 'receivedAt']
          : ['sourceCode', 'kind', 'createdAt', 'actorName']),
    ]),
);
const columns = ref<string[]>([]),
  rows = ref<InventoryQueryRow[]>([]),
  filters = ref<Record<string, string>>({}),
  query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(10),
  total = ref(0),
  loading = ref(false),
  error = ref(''),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  exportBusy = ref(false);
let revision = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : '加载失败，请重试';
}
function display(row: InventoryQueryRow, key: string) {
  const value = String(row[key] ?? '');
  if (key.endsWith('At')) return value.replace('T', ' ').slice(0, 19);
  if (
    [
      'quantity',
      'reservedQuantity',
      'availableQuantity',
      'remainingQuantity',
      'unitCost',
      'remainingCost',
      'amount',
    ].includes(key) &&
    /^-?\d+(\.\d+)?$/.test(value)
  ) {
    return formatNumber(value);
  }
  return value;
}
function tone(value: string) {
  return value === '可用' || value === '已确定'
    ? 'success'
    : value === '冻结' || value === '已过期'
      ? 'danger'
      : 'warning';
}
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await inventoryQueryApi.list(
      kind.value,
      applied.value,
      filters.value,
      page.value,
      size.value,
    );
    if (current !== revision) return;
    rows.value = result.data;
    total.value = result.page.total;
    const last = Math.max(1, Math.ceil(total.value / size.value));
    if (page.value > last) page.value = last;
  } catch (e) {
    if (current === revision) {
      error.value = message(e);
      rows.value = [];
      total.value = 0;
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(
  kind,
  () => {
    columns.value = [...defaults[kind.value]];
    filters.value = {};
    page.value = 1;
    query.value = '';
    applied.value = '';
    rows.value = [];
    filterOpen.value = false;
    columnsOpen.value = false;
    load();
  },
  { immediate: true },
);
watch([page, size], load);
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
function search() {
  applied.value = query.value;
  page.value = 1;
  load();
}
function reset() {
  query.value = '';
  applied.value = '';
  filters.value = {};
  page.value = 1;
  load();
}
function toggle(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((x) => x !== key);
  } else
    columns.value = Object.keys(fields[kind.value]).filter(
      (x) => x === key || columns.value.includes(x),
    );
}
async function exportRows() {
  if (exportBusy.value) return;
  exportBusy.value = true;
  try {
    const result = await inventoryQueryApi.export(kind.value, applied.value, filters.value);
    const quote = (s: string) =>
      '"' + (/^[=+@\-\t\r]/.test(s) ? "'" : '') + s.replaceAll('"', '""') + '"';
    const content = [
      columns.value.map((x) => quote(fields[kind.value][x]!)).join(','),
      ...result.data.map((row) => columns.value.map((key) => quote(display(row, key))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = titles[kind.value] + '.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
function source(row: InventoryQueryRow) {
  const k = String(row.sourceKind || 'receipt');
  router.push(
    ['returns', 'transfers', 'counts', 'openings', 'reversals'].includes(k)
      ? { name: 'inventory-operation-detail', params: { kind: k, id: String(row.sourceId) } }
      : {
          name:
            k === 'issue'
              ? 'inventory-outbound-detail'
              : k === 'requisition'
                ? 'inventory-requisition-detail'
                : 'inventory-receipt-detail',
          params: { id: String(row.sourceId) },
        },
  );
}
async function freeze(row: InventoryQueryRow) {
  try {
    const result = await ElMessageBox.prompt(
      '请输入' + (row.stockStatus === '冻结' ? '解冻' : '冻结') + '原因',
      '库存状态',
      {
        inputValidator: (s: string) => !!s.trim() || '请填写原因',
        confirmButtonText: '确定',
        cancelButtonText: '取消',
      },
    );
    await operationApi.freeze(row, row.stockStatus === '冻结' ? '可用' : '冻结', result.value);
    ElMessage.success('库存状态已更新');
    await load();
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  }
}
</script>
<template>
  <section class="inventory-queries">
    <div class="toolbar">
      <div class="tabs" aria-label="库存查询">
        <button
          v-for="(label, key) in titles"
          :key="key"
          :class="{ active: key === kind }"
          type="button"
          @click="router.push({ name: 'inventory-queries', params: { kind: key } })"
        >
          {{ label }}
        </button>
      </div>
      <form @submit.prevent="search">
        <ListToolbarIcons
          mode="filter"
          :filter-open="filterOpen"
          :columns-open="columnsOpen"
          @toggle-filter="filterOpen = !filterOpen"
        /><ListSearchInput v-model="query" placeholder="搜索料号、名称、型号、批次号" /><ElButton
          text
          @click="reset"
          >重置</ElButton
        >
      </form>
    </div>
    <p v-if="error" class="error" role="alert">
      {{ error }}<ElButton text @click="load">重试</ElButton>
    </p>
    <div class="card" :aria-busy="loading">
      <div class="scroll">
        <table :style="{ minWidth: Math.max(850, columns.length * 125) + 'px' }">
          <thead>
            <tr>
              <th
                v-for="key in columns"
                :key="key"
                :class="{
                  numeric: [
                    'quantity',
                    'reservedQuantity',
                    'availableQuantity',
                    'remainingQuantity',
                    'unitCost',
                    'remainingCost',
                    'amount',
                  ].includes(key),
                }"
              >
                {{ fields[kind][key] }}
              </th>
              <th
                v-if="kind === 'balances' && auth.can('inventoryQuery', '冻结')"
                class="operation"
              >
                操作
              </th>
            </tr>
            <tr v-if="filterOpen">
              <th v-for="key in columns" :key="key">
                <ElInput
                  v-if="filterable.has(key)"
                  v-model="filters[key]"
                  clearable
                  :placeholder="'筛选' + fields[kind][key]"
                />
              </th>
              <th
                v-if="kind === 'balances' && auth.can('inventoryQuery', '冻结')"
                class="operation"
              ></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in rows" :key="row.id">
              <td
                v-for="key in columns"
                :key="key"
                :class="{
                  numeric: [
                    'quantity',
                    'reservedQuantity',
                    'availableQuantity',
                    'remainingQuantity',
                    'unitCost',
                    'remainingCost',
                    'amount',
                  ].includes(key),
                }"
              >
                <ElTag
                  v-if="['stockStatus', 'expiryStatus', 'costStatus'].includes(key) && row[key]"
                  :type="tone(display(row, key))"
                  >{{ display(row, key) }}</ElTag
                ><ElButton
                  v-else-if="key === 'sourceCode' && row.sourceId"
                  type="primary"
                  link
                  @click="source(row)"
                  >{{ display(row, key) }}</ElButton
                ><span v-else>{{ display(row, key) }}</span>
              </td>
              <td
                v-if="kind === 'balances' && auth.can('inventoryQuery', '冻结')"
                class="operation"
              >
                <ElButton
                  type="primary"
                  link
                  :disabled="Number(row.quantity) <= 0"
                  @click="freeze(row)"
                  >{{ row.stockStatus === '冻结' ? '解冻' : '冻结' }}</ElButton
                >
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!rows.length" class="empty-state">
          {{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无数据' }}
        </div>
      </div>
      <footer>
        <span>共 {{ total }} 条</span
        ><AppPagination
          v-model:page="page"
          v-model:page-size="size"
          :total="total"
        /><ListToolbarIcons
          class="footer-tools"
          mode="footer"
          :filter-open="filterOpen"
          :columns-open="columnsOpen"
          :can-export="auth.can('inventoryQuery', '导出')"
          :export-busy="exportBusy"
          @toggle-columns="columnsOpen = !columnsOpen"
          @export="exportRows"
          ><template #columns
            ><label v-for="(label, key) in fields[kind]" :key="key"
              ><input
                type="checkbox"
                :checked="columns.includes(key)"
                :disabled="columns.length === 1 && columns.includes(key)"
                @change="toggle(key)"
              />{{ label }}</label
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </div>
  </section>
</template>
<style scoped>
.operation {
  position: sticky;
  right: 0;
  min-width: 92px;
  background: white;
  box-shadow: -3px 0 7px #20375608;
}
.inventory-queries {
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  min-height: 0;
  color: var(--text);
  font-size: 13px;
}
.toolbar,
.toolbar form,
footer {
  display: flex;
  gap: 12px;
  align-items: center;
}
.toolbar {
  margin-bottom: 0;
}
.toolbar form {
  margin-left: auto;
}
.tabs {
  display: flex;
  background: #fff;
  border: 1px solid #dfe5ee;
  border-radius: 5px;
  overflow: hidden;
  flex: none;
}
.tabs button {
  border: 0;
  border-right: 1px solid #e4e9f0;
  background: #fff;
  padding: 9px 14px;
  font: inherit;
  color: var(--text);
  cursor: pointer;
}
.tabs button:last-child {
  border-right: 0;
}
.tabs button.active {
  background: #eaf0ff;
  color: var(--brand);
  font-weight: 600;
}
.tabs button:hover {
  background: #f1f5ff;
}
.card {
  flex: 1;
  min-height: 0;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  background: white;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
table {
  width: 100%;
  border-spacing: 0;
  border-collapse: separate;
  table-layout: fixed;
}
td,
th {
  font-size: 13px;
  height: 44px;
  padding: 10px 12px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-bottom: 1px solid #e4e9f0;
}
th {
  font-weight: 700;
  background: #f5f7fb;
}
tbody tr:hover {
  background: #f8faff;
}
th.numeric,
td.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.empty {
  text-align: center;
  height: 80px;
}
footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}
.empty-state {
  position: sticky;
  left: 0;
  height: 100px;
  display: flex;
  align-items: center;
  justify-content: center;
}
footer {
  height: 46px;
  flex: none;
  padding: 0 12px;
  border-top: 1px solid #e4e9f0;
}
.footer-tools {
  margin-left: auto;
}
.error {
  margin: 0;
  color: #c13449;
}
:deep(.el-button),
:deep(.el-input) {
  font-size: 13px;
}
@media (max-width: 1000px) {
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar form {
    margin-left: 0;
  }
}
</style>
