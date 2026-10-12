<script setup lang="ts">
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { request } from '../api';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTag,
  ElDatePicker,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  operationApi,
  inventoryMenu,
  type InventoryOperation,
  type OperationKind,
  type OperationChoices,
  type OperationLine,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const kind = computed(() => String(route.params.kind) as OperationKind);
const names: Record<OperationKind, string> = {
  returns: '退料',
  transfers: '调拨',
  counts: '盘点',
  openings: '期初与初始化',
  reversals: '单据纠正',
};
const menu = computed(() => inventoryMenu(kind.value));
const editing = computed(() => !!route.params.id);
const empty = (): InventoryOperation => ({
  id: '',
  code: '',
  kind: kind.value,
  warehouseId: '',
  targetWarehouseId: '',
  status: '草稿',
  form: { remark: '', lines: [], receiptId: '' },
});
const form = ref(empty()),
  rows = ref<InventoryOperation[]>([]),
  choices = ref<OperationChoices>({
    companies: [],
    users: [],
    warehouses: [],
    bins: [],
    materials: [],
    balances: [],
    allocations: [],
    receipts: [],
    legacy: [],
  });
const busy = ref(false),
  loading = ref(false),
  error = ref(''),
  q = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(10),
  total = ref(0),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  filters = ref<Record<string, string>>({}),
  exportBusy = ref(false);
const fields: Record<string, string> = {
  code: '单据编号',
  warehouseName: '仓库',
  status: '状态',
  createdAt: '创建时间',
};
const columns = ref(Object.keys(fields));
const readonly = computed(
  () => form.value.status !== '草稿' || !auth.can(menu.value, form.value.id ? '编辑' : '新增'),
);
const canMoney = computed(() => auth.can(menu.value, '查看金额'));
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && !readonly.value,
  () => busy.value,
);
const destinations = computed(() =>
  choices.value.warehouses.filter(
    (w) =>
      w.id !== form.value.warehouseId &&
      w.companyId ===
        choices.value.warehouses.find((w) => w.id === form.value.warehouseId)?.companyId,
  ),
);
const bins = computed(() =>
  choices.value.bins.filter((b) => b.warehouseId === form.value.warehouseId),
);
const targetBins = computed(() =>
  choices.value.bins.filter((b) => b.warehouseId === form.value.targetWarehouseId),
);
const balances = computed(() =>
  choices.value.balances.filter((b) => b.warehouseId === form.value.warehouseId),
);
const allocations = computed(() =>
  choices.value.allocations.filter(
    (a) =>
      a.companyId ===
      choices.value.warehouses.find((w) => w.id === form.value.warehouseId)?.companyId,
  ),
);
const receipts = computed(() =>
  (choices.value.correctionSources || choices.value.receipts).filter(
    (r) => r.warehouseId === form.value.warehouseId,
  ),
);
const legacy = computed(() =>
  choices.value.legacy.filter(
    (l) =>
      !l.companyId ||
      l.companyId === '0' ||
      l.companyId ===
        choices.value.warehouses.find((w) => w.id === form.value.warehouseId)?.companyId,
  ),
);
const confirmAction = computed(() =>
  kind.value === 'reversals'
    ? '冲销'
    : ['returns', 'openings'].includes(kind.value)
      ? '入库'
      : '出库',
);
function message(e: unknown) {
  return e instanceof Error ? e.message : '操作失败，请重试';
}
function tone(status: string) {
  return status === '已完成' ? 'success' : status === '在途' ? 'warning' : 'info';
}
function display(v: unknown, key = '') {
  if (isDecimalField(key)) return formatNumber(v);
  return String(v ?? '')
    .replace('T', ' ')
    .slice(0, 30);
}
function add() {
  form.value.form.lines.push({
    quantity: '1',
    unitPrice: '',
    materialId: '',
    binId: '',
    balanceId: '',
    allocationId: '',
    targetBinId: '',
    batchCode: '',
    supplierBatch: '',
    productionDate: '',
    expiryDate: '',
    legacyStockId: '',
  });
}
function selectLegacy(line: OperationLine) {
  const old = legacy.value.find((l) => l.id === line.legacyStockId);
  if (old) line.quantity = old.quantity;
}
function tracking(line: OperationLine) {
  return choices.value.materials.find((m) => m.id === line.materialId)?.batchEnabled;
}
let revision = 0;
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      const c = await operationApi.choices(kind.value);
      if (current !== revision) return;
      choices.value = c.data;
      const d =
        route.params.id === 'new'
          ? empty()
          : (await operationApi.detail(kind.value, String(route.params.id))).data;
      if (current !== revision) return;
      form.value = d;
      if (route.params.id === 'new' && route.query.issueId) {
        const ref = String(route.query.issueId);
        if (kind.value === 'returns') {
          const ids = ref.startsWith('g')
            ? (
                await request<{ data: { children: { id: string; reversed: unknown }[] } }>(
                  `/inventory/outbounds/${ref}`,
                )
              ).data.children
                .filter((c) => !Number(c.reversed))
                .map((c) => c.id)
            : [ref];
          const matches = choices.value.allocations.filter((a) => ids.includes(a.issueId!));
          if (matches.length) {
            form.value.warehouseId = matches[0]!.warehouseId!;
            form.value.form.lines = matches.map((a) => ({
              allocationId: a.id!,
              quantity:
                a.availableQuantity ||
                (Number(a.quantity) - Number(a.usedQuantity) - Number(a.returnedQuantity)).toFixed(
                  6,
                ),
              binId: '',
            }));
          }
        } else if (kind.value === 'reversals') {
          const source = (choices.value.correctionSources || []).find(
            (s) => s.id === ref && s.sourceKind === 'issue',
          );
          if (source) {
            form.value.warehouseId = source.warehouseId!;
            form.value.form.sourceId = source.id;
            form.value.form.sourceKind = 'issue';
            form.value.form.sourceCode = source.code;
          }
        }
      }

      conversionMode.value = !!d.form.conversionMaterialId;
      guard.markSaved();
    } else {
      const d = await operationApi.list(
        kind.value,
        applied.value,
        filters.value,
        page.value,
        size.value,
      );
      if (current !== revision) return;
      rows.value = d.data;
      total.value = d.page.total;
    }
  } catch (e) {
    if (current === revision) error.value = message(e);
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(() => route.fullPath, load, { immediate: true });
watch([page, size], () => {
  if (!editing.value) load();
});
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
function search() {
  applied.value = q.value;
  page.value = 1;
  load();
}
function reset() {
  q.value = '';
  applied.value = '';
  filters.value = {};
  page.value = 1;
  load();
}
async function back() {
  if (await guard.permitLeave())
    router.push({ name: 'inventory-operations', params: { kind: kind.value } });
}
async function save() {
  if (busy.value || readonly.value) return;
  busy.value = true;
  try {
    form.value = (await operationApi.save(kind.value, form.value)).data;
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({
        name: 'inventory-operation-detail',
        params: { kind: kind.value, id: form.value.id },
      });
    ElMessage.success(kind.value === 'counts' ? '盘点基准已保存' : '草稿已保存');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function confirm(receive = false) {
  if (busy.value) return;
  if (guard.dirty.value) {
    ElMessage.warning('请先保存修改');
    return;
  }
  try {
    await ElMessageBox.confirm(
      receive
        ? '确认已收到调拨物料并入库？'
        : kind.value === 'counts'
          ? '按盘点基准和期间收发确认差异，更新库存与成本？'
          : '确认本单据并更新库存与成本？',
      '确认',
      {
        type: 'warning',
        confirmButtonText: '确认',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      },
    );
  } catch {
    return;
  }
  busy.value = true;
  try {
    form.value = (await operationApi.confirm(kind.value, form.value, receive)).data;
    guard.markSaved();
    ElMessage.success(receive ? '接收完成' : '确认完成');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function remove() {
  try {
    await ElMessageBox.confirm('删除此草稿？', '删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
      closeOnClickModal: false,
    });
  } catch {
    return;
  }
  busy.value = true;
  try {
    await operationApi.delete(kind.value, form.value);
    guard.markSaved();
    busy.value = false;
    await back();
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
function toggle(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((c) => c !== key);
  } else columns.value = Object.keys(fields).filter((c) => c === key || columns.value.includes(c));
}
async function exportRows() {
  exportBusy.value = true;
  try {
    const d = await operationApi.export(kind.value, applied.value, filters.value);
    const quote = (v: unknown) =>
      '"' +
      (/^[=+@\-\t\r]/.test(String(v ?? '')) ? "'" : '') +
      String(v ?? '').replaceAll('"', '""') +
      '"';
    const text = [
      columns.value.map((c) => quote(fields[c])).join(','),
      ...d.data.map((r) => columns.value.map((c) => quote(display(r[c], c))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + text], { type: 'text/csv;charset=utf-8' }),
    );
    const a = document.createElement('a');
    a.href = url;
    a.download = names[kind.value] + '.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
const conversionMode = ref(false);
watch(
  () => form.value.form.conversionMaterialId,
  (value) => {
    if (value) conversionMode.value = true;
  },
);
function captureConversion() {
  const id = form.value.form.conversionMaterialId || '';
  const source = choices.value.balances.filter(
    (b) => b.materialId === id && Number(b.quantity) > 0,
  );
  form.value.form.lines = source.map((b) => ({
    balanceId: b.id!,
    materialId: id,
    materialName: b.materialName || '',
    binId: b.binId || '',
    warehouseId: b.warehouseId || '',
    quantity: b.quantity || '',
    batchCode: '',
    productionDate: '',
    expiryDate: '',
  }));
  form.value.warehouseId = source[0]?.warehouseId || '';
}
function changeOpeningMode() {
  form.value.form.lines = [];
  form.value.form.conversionMaterialId = '';
}
</script>
<template>
  <section class="operation-page">
    <p v-if="error" role="alert" class="error">
      {{ error }} <ElButton text @click="load">重试</ElButton>
    </p>
    <template v-if="!editing">
      <div class="toolbar">
        <ElButton
          v-if="auth.can(menu, '新增')"
          type="primary"
          @click="router.push({ name: 'inventory-operation-detail', params: { kind, id: 'new' } })"
          >＋ 新增</ElButton
        >
        <form @submit.prevent="search">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput v-model="q" placeholder="搜索单据编号、仓库、状态" /><ElButton
            text
            @click="reset"
            >重置</ElButton
          >
        </form>
      </div>
      <div class="list-card">
        <div class="table-scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th v-for="c in columns" :key="c">{{ fields[c] }}</th>
                <th class="action">操作</th>
              </tr>
              <tr v-if="filterOpen">
                <th v-for="c in columns" :key="c">
                  <ElInput v-model="filters[c]" clearable :placeholder="'筛选' + fields[c]" />
                </th>
                <th class="action" />
              </tr>
            </thead>
            <tbody>
              <tr v-for="r in rows" :key="r.id">
                <td v-for="c in columns" :key="c">
                  <ElTag v-if="c === 'status'" :type="tone(r.status)">{{ r.status }}</ElTag
                  ><ElButton
                    v-else-if="c === 'code'"
                    link
                    type="primary"
                    @click="
                      router.push({
                        name: 'inventory-operation-detail',
                        params: { kind, id: r.id },
                      })
                    "
                    >{{ r.code }}</ElButton
                  ><span v-else>{{ display(r[c], c) }}</span>
                </td>
                <td class="action">
                  <ElButton
                    link
                    type="primary"
                    @click="
                      router.push({
                        name: 'inventory-operation-detail',
                        params: { kind, id: r.id },
                      })
                    "
                    >{{
                      r.status === '草稿' ? '编辑' : r.status === '在途' ? '接收' : '查看'
                    }}</ElButton
                  >
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td :colspan="columns.length + 1" class="empty">
                  {{ loading ? '正在加载…' : error ? '加载失败' : '暂无单据' }}
                </td>
              </tr>
            </tbody>
          </table>
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
            :can-export="auth.can(menu, '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><label v-for="(name, key) in fields" :key="key"
                ><input
                  type="checkbox"
                  :checked="columns.includes(key)"
                  :disabled="columns.length === 1 && columns.includes(key)"
                  @change="toggle(key)"
                />{{ name }}</label
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </template>
    <template v-else>
      <header>
        <AppBackButton :disabled="busy" @click="back" /><strong>{{
          form.code || '新增' + names[kind]
        }}</strong
        ><ElTag :type="tone(form.status)">{{ form.status }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >{{ kind === 'transfers' ? '源仓库' : '仓库'
            }}<span v-if="readonly || (kind === 'counts' && form.id)">{{ form.warehouseName }}</span
            ><ElSelect v-else v-model="form.warehouseId" filterable placeholder="选择仓库"
              ><ElOption
                v-for="w in choices.warehouses"
                :key="w.id"
                :value="w.id"
                :label="w.name" /></ElSelect></label
          ><label v-if="kind === 'transfers'"
            >目标仓库<span v-if="readonly">{{
              choices.warehouses.find((w) => w.id === form.targetWarehouseId)?.name
            }}</span
            ><ElSelect v-else v-model="form.targetWarehouseId" filterable
              ><ElOption
                v-for="w in destinations"
                :key="w.id"
                :value="w.id"
                :label="w.name" /></ElSelect></label
          ><label v-if="kind === 'reversals'"
            >来源单据<span v-if="readonly">{{ form.form.sourceCode || form.form.receiptCode }}</span
            ><ElSelect
              v-else
              v-model="form.form.sourceId"
              @change="
                (value: string) => {
                  form.form.sourceKind =
                    receipts.find((r) => r.id === value)?.sourceKind || 'receipt';
                }
              "
              filterable
              ><ElOption
                v-for="r in receipts"
                :key="r.id"
                :value="r.id"
                :label="
                  r.code +
                  ' · ' +
                  ((
                    {
                      receipt: '入库',
                      issue: '出库',
                      returns: '退料',
                      transfers: '调拨',
                      counts: '盘点',
                      openings: '期初',
                    } as Record<string, string>
                  )[r.sourceKind || 'receipt'] || '')
                " /></ElSelect></label
          ><label class="remark"
            >{{ kind === 'reversals' ? '冲销原因' : '备注'
            }}<ElInput v-model="form.form.remark" :readonly="readonly" maxlength="1000"
          /></label>
        </div>
        <div v-if="kind === 'counts'" class="notice">
          <strong>盘点基准 {{ display(form.form.baselineAt) }}</strong
          ><span
            >保存实盘数量时记录当时的账面数量；期间收发单独展示，确认时仅调整盘点差异。盘盈需填写成本单价。</span
          >
        </div>
        <div v-if="kind === 'openings' && !readonly" class="conversion-options">
          <label
            >初始化方式<ElSelect v-model="conversionMode" @change="changeOpeningMode"
              ><ElOption :value="false" label="期初入库 / 历史库存初始化" /><ElOption
                :value="true"
                label="已有库存启用批次" /></ElSelect
          ></label>
          <label v-if="conversionMode"
            >物料<ElSelect
              v-model="form.form.conversionMaterialId"
              filterable
              @change="captureConversion"
              ><ElOption
                v-for="m in choices.materials.filter((m) => !m.batchEnabled)"
                :key="m.id"
                :value="m.id"
                :label="m.code + ' ' + m.name" /></ElSelect
          ></label>
        </div>
        <div v-if="kind === 'openings'" class="notice">
          历史库存只在指定公司、物料、仓库、库位和成本后转换，原维修及设备履历关联继续保留。
        </div>
        <div v-if="kind === 'openings' && conversionMode" class="line-scroll">
          <table class="line-table conversion-table">
            <thead>
              <tr>
                <th>物料</th>
                <th>仓库</th>
                <th>库位</th>
                <th>全部在库数量</th>
                <th>批次号</th>
                <th>生产日期</th>
                <th>有效期至</th>
                <th>成本</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="l in form.form.lines" :key="l.balanceId">
                <td>{{ l.materialName }}</td>
                <td>{{ choices.warehouses.find((w) => w.id === l.warehouseId)?.name }}</td>
                <td>{{ choices.bins.find((b) => b.id === l.binId)?.name }}</td>
                <td>{{ formatNumber(l.quantity) }}</td>
                <td>
                  <ElInput v-model="l.batchCode" :readonly="readonly" placeholder="留空自动生成" />
                </td>
                <td>
                  <ElDatePicker
                    v-model="l.productionDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :disabled="readonly"
                  />
                </td>
                <td>
                  <ElDatePicker
                    v-model="l.expiryDate"
                    type="date"
                    value-format="YYYY-MM-DD"
                    :disabled="readonly"
                  />
                </td>
                <td>沿用原成本</td>
              </tr>
              <tr v-if="!form.form.lines.length">
                <td colspan="8" class="empty">选择物料后，加载全部仓库待初始化库存</td>
              </tr>
            </tbody>
          </table>
          <p class="notice">
            一次转换该物料全部在库数量，保留原成本及来源时间；存在预留、在途或未完成单据时禁止转换。
          </p>
        </div>
        <template v-if="kind !== 'reversals' && !conversionMode"
          ><div class="detail-heading">
            <strong>{{ kind === 'counts' ? '盘点明细' : '单据明细' }}</strong
            ><ElButton
              v-if="!readonly && kind !== 'counts'"
              :disabled="!form.warehouseId"
              type="primary"
              plain
              @click="add"
              >＋ 添加物料</ElButton
            >
          </div>
          <div class="line-scroll">
            <table :class="['line-table', kind]">
              <thead>
                <tr>
                  <template v-if="kind === 'returns'"
                    ><th class="wide">原发料物料</th>
                    <th>退回库位</th>
                    <th class="numeric">退料数量</th></template
                  >
                  <template v-if="kind === 'transfers'"
                    ><th class="wide">源库存 / 库位 / 批次</th>
                    <th>目标库位</th>
                    <th class="numeric">调拨数量</th></template
                  >
                  <template v-if="kind === 'counts'"
                    ><th>物料</th>
                    <th>库位 / 批次</th>
                    <th class="numeric">基准数量</th>
                    <th class="numeric">期间收发</th>
                    <th class="numeric">当前数量</th>
                    <th class="numeric">实盘数量</th>
                    <th v-if="canMoney" class="numeric">盘盈成本单价</th></template
                  >
                  <template v-if="kind === 'openings'"
                    ><th class="wide">物料</th>
                    <th>历史库存关联</th>
                    <th>库位</th>
                    <th class="numeric">数量</th>
                    <th v-if="canMoney" class="numeric">不含税单价</th>
                    <th>批次号</th>
                    <th>生产日期</th>
                    <th>有效期至</th></template
                  >
                  <th v-if="!readonly && kind !== 'counts'" class="action">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="(l, index) in form.form.lines" :key="index">
                  <template v-if="kind === 'returns'"
                    ><td>
                      <span v-if="readonly">{{ l.issueCode }} · {{ l.materialName }}</span
                      ><ElSelect
                        v-else
                        v-model="l.allocationId"
                        filterable
                        placeholder="选择原发料明细"
                        ><ElOption
                          v-for="a in allocations"
                          :key="a.id"
                          :value="a.id"
                          :label="a.issueCode + ' · ' + a.materialName + ' ' + (a.batchCode || '')"
                      /></ElSelect>
                    </td>
                    <td>
                      <span v-if="readonly">{{
                        choices.bins.find((b) => b.id === l.binId)?.name
                      }}</span
                      ><ElSelect v-else v-model="l.binId" filterable
                        ><ElOption v-for="b in bins" :key="b.id" :value="b.id" :label="b.name"
                      /></ElSelect>
                    </td>
                    <td class="numeric">
                      <span v-if="readonly">{{ formatNumber(l.quantity) }}</span
                      ><AppDecimalInput v-else v-model="l.quantity" inputmode="decimal" /></td
                  ></template>
                  <template v-if="kind === 'transfers'"
                    ><td>
                      <span v-if="readonly">{{ l.materialName }}</span
                      ><ElSelect v-else v-model="l.balanceId" filterable
                        ><ElOption
                          v-for="b in balances"
                          :key="b.id"
                          :value="b.id"
                          :label="
                            b.materialName +
                            ' / ' +
                            b.binName +
                            ' / ' +
                            (b.batchCode || '') +
                            ' · ' +
                            b.quantity
                          "
                      /></ElSelect>
                    </td>
                    <td>
                      <span v-if="readonly">{{
                        choices.bins.find((b) => b.id === l.targetBinId)?.name
                      }}</span
                      ><ElSelect v-else v-model="l.targetBinId" filterable
                        ><ElOption
                          v-for="b in targetBins"
                          :key="b.id"
                          :value="b.id"
                          :label="b.name"
                      /></ElSelect>
                    </td>
                    <td class="numeric">
                      <span v-if="readonly">{{ formatNumber(l.quantity) }}</span
                      ><AppDecimalInput v-else v-model="l.quantity" inputmode="decimal" /></td
                  ></template>
                  <template v-if="kind === 'counts'"
                    ><td>{{ l.materialName }}</td>
                    <td>{{ l.binName }} {{ l.batchCode }}</td>
                    <td class="numeric">{{ formatNumber(l.baselineQuantity) }}</td>
                    <td class="numeric">{{ l.periodMovement }}</td>
                    <td class="numeric">{{ formatNumber(l.currentQuantity) }}</td>
                    <td class="numeric">
                      <span v-if="readonly">{{ formatNumber(l.countedQuantity) }}</span
                      ><AppDecimalInput
                        v-else
                        v-model="l.countedQuantity"
                        inputmode="decimal"
                        placeholder="实盘数量"
                      />
                    </td>
                    <td v-if="canMoney">
                      <span v-if="readonly">{{ formatNumber(l.unitCost) }}</span
                      ><AppDecimalInput
                        v-else
                        v-model="l.unitCost"
                        inputmode="decimal"
                        placeholder="盘盈时填写"
                      /></td
                  ></template>
                  <template v-if="kind === 'openings'"
                    ><td>
                      <span v-if="readonly">{{ l.materialName }}</span
                      ><ElSelect v-else v-model="l.materialId" filterable
                        ><ElOption
                          v-for="m in choices.materials"
                          :key="m.id"
                          :value="m.id"
                          :label="m.code + ' ' + m.name"
                      /></ElSelect>
                    </td>
                    <td>
                      <span v-if="readonly">{{
                        choices.legacy.find((x) => x.id === l.legacyStockId)?.name
                      }}</span
                      ><ElSelect
                        v-else
                        v-model="l.legacyStockId"
                        clearable
                        filterable
                        placeholder="可不关联"
                        @change="selectLegacy(l)"
                        ><ElOption
                          v-for="x in legacy"
                          :key="x.id"
                          :value="x.id"
                          :label="x.code + ' ' + x.name"
                      /></ElSelect>
                    </td>
                    <td>
                      <span v-if="readonly">{{
                        choices.bins.find((b) => b.id === l.binId)?.name
                      }}</span
                      ><ElSelect v-else v-model="l.binId" filterable
                        ><ElOption v-for="b in bins" :key="b.id" :value="b.id" :label="b.name"
                      /></ElSelect>
                    </td>
                    <td class="numeric">
                      <span v-if="readonly">{{ formatNumber(l.quantity) }}</span
                      ><AppDecimalInput
                        v-else
                        v-model="l.quantity"
                        inputmode="decimal"
                        :readonly="!!l.legacyStockId"
                      />
                    </td>
                    <td v-if="canMoney">
                      <span v-if="readonly">{{ formatNumber(l.unitPrice) }}</span
                      ><AppDecimalInput v-else v-model="l.unitPrice" inputmode="decimal" />
                    </td>
                    <td>
                      <ElInput
                        v-if="tracking(l)"
                        v-model="l.batchCode"
                        :readonly="readonly"
                        placeholder="留空自动生成"
                      />
                    </td>
                    <td>
                      <input
                        v-if="tracking(l)"
                        v-model="l.productionDate"
                        type="date"
                        :disabled="readonly"
                      />
                    </td>
                    <td>
                      <input
                        v-if="tracking(l)"
                        v-model="l.expiryDate"
                        type="date"
                        :disabled="readonly"
                      /></td
                  ></template>
                  <td v-if="!readonly && kind !== 'counts'" class="action">
                    <ElButton link type="danger" @click="form.form.lines.splice(index, 1)"
                      >删除</ElButton
                    >
                  </td>
                </tr>
                <tr v-if="!form.form.lines.length">
                  <td colspan="10" class="empty">
                    {{
                      kind === 'counts'
                        ? '选择仓库后点击建立盘点，保存当前库存基准'
                        : '请添加本单据的物料明细'
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <template v-if="form.movements?.length"
          ><div class="detail-heading"><strong>库存与成本记录</strong></div>
          <div class="line-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th>库位</th>
                  <th>批次号</th>
                  <th class="numeric">变动数量</th>
                  <th v-if="canMoney" class="numeric">变动金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="m in form.movements" :key="m.id">
                  <td>{{ m.materialName }}</td>
                  <td>{{ m.binName }}</td>
                  <td>{{ m.batchCode }}</td>
                  <td class="numeric">{{ formatNumber(m.quantity) }}</td>
                  <td v-if="canMoney" class="numeric">{{ formatNumber(m.amount) }}</td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <div v-if="form.events?.length" class="records">
          <strong>单据时间轴</strong>
          <p v-for="(e, index) in form.events" :key="index">
            <i />{{ e.actorName }} · {{ e.action }}<time>{{ display(e.createdAt) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readonly ? '返回' : '取消' }}</ElButton
        ><template v-if="!error && !loading"
          ><ElButton v-if="!readonly" :loading="busy" @click="save">{{
            kind === 'counts' && !form.id ? '建立盘点' : '保存草稿'
          }}</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can(menu, '删除')"
            :disabled="busy"
            @click="remove"
            >删除</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can(menu, confirmAction)"
            type="primary"
            :disabled="busy || guard.dirty.value"
            @click="confirm()"
            >{{ kind === 'transfers' ? '确认调出' : '确认' }}</ElButton
          ><ElButton
            v-if="form.status === '在途' && auth.can(menu, '入库')"
            type="primary"
            :disabled="busy"
            @click="confirm(true)"
            >接收确认</ElButton
          ></template
        ></AppFormFooter
      >
    </template>
  </section>
</template>
<style scoped>
footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}

.conversion-options {
  display: flex;
  gap: 16px;
  align-items: center;
  padding: 8px 0 16px;
}
.conversion-options label {
  display: flex;
  gap: 10px;
  align-items: center;
}
.conversion-options :deep(.el-select) {
  width: 250px;
}
.conversion-table :deep(.el-date-editor) {
  width: 150px;
}
.operation-page {
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
header,
footer,
.detail-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar {
  margin-bottom: 0;
}
.toolbar form {
  margin-left: auto;
}
.list-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  overflow: hidden;
}
.table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
table {
  width: 100%;
  min-width: 700px;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
}
th,
td {
  padding: 12px;
  text-align: left;
  font-size: 13px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-bottom: 1px solid #e4e9f0;
  background: white;
}
th {
  background: #f5f7fb;
  font-weight: 700;
}
.action {
  position: sticky;
  right: 0;
  width: 100px;
  z-index: 1;
  box-shadow: -1px 0 #e4e9f0;
}
.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.wide {
  width: 36%;
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
header {
  padding: 8px 12px;
  background: white;
  border-bottom: 1px solid #e4e9f0;
}
header strong {
  font-size: 15px;
}
.editor-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: white;
  padding: 18px;
}
.metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 24px;
}
.metadata label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 10px;
}
.remark {
  grid-column: 1/-1;
}
.notice {
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 14px;
  margin-top: 18px;
  background: #edf4ff;
  border-left: 3px solid var(--brand);
  line-height: 1.6;
}
.detail-heading {
  margin: 22px 0 12px;
}
.detail-heading .el-button {
  margin-left: auto;
}
.line-scroll {
  overflow: auto;
  border: 1px solid #e4e9f0;
  border-radius: 5px;
}
.line-table.openings {
  min-width: 1450px;
}
.line-table.openings .wide {
  width: 220px;
}
.line-table.counts {
  min-width: 1100px;
}
.empty {
  padding: 40px;
  text-align: center;
}
.records {
  margin-top: 24px;
}
.records p {
  display: flex;
  gap: 10px;
  align-items: center;
}
.records i {
  width: 7px;
  height: 7px;
  background: var(--brand);
  border-radius: 50%;
}
.records time {
  margin-left: 12px;
}
.error {
  margin: 0;
  color: #c13449;
}
input[type='date'] {
  width: 100%;
  height: 32px;
  border: 1px solid #d8e0ec;
  border-radius: 4px;
  background: white;
  color: var(--text);
  font: inherit;
  padding: 0 8px;
}
:deep(.el-input),
:deep(.el-select),
:deep(.el-button) {
  font-size: 13px;
}
@media (max-width: 850px) {
  .metadata {
    grid-template-columns: 1fr;
  }
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar form {
    margin-left: 0;
    max-width: 100%;
  }
}
</style>
