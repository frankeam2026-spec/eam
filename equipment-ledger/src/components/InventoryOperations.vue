<script setup lang="ts">import { enumLabel } from '../i18n';
import { tr } from '../i18n';

import { onUnmounted } from 'vue';
import { useInventoryOperationList } from '../composables/inventory/useInventoryLists';
const {
  rows,
  total,
  refresh: refreshList,
  invalidate: invalidateList,
} = useInventoryOperationList();
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
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
  size = ref(20),
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
  return e instanceof Error ? e.message : tr('ui.equipment.text0491');
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
onUnmounted(() => {
  revision++;
  invalidateList();
});
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      invalidateList();
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

      if (route.params.id === 'new' && kind.value === 'returns' && route.query.allocationId) {
        const a = choices.value.allocations.find((x) => x.id === String(route.query.allocationId));
        if (a) {
          form.value.warehouseId = a.warehouseId!;
          form.value.form.lines = [
            {
              allocationId: a.id!,
              quantity:
                a.availableQuantity ||
                (Number(a.quantity) - Number(a.usedQuantity) - Number(a.returnedQuantity)).toFixed(
                  6,
                ),
              binId: '',
            },
          ];
        } else throw new Error(tr('ui.equipment.text0492'));
      }
      conversionMode.value = !!d.form.conversionMaterialId;
      guard.markSaved();
    } else {
      const accepted = await refreshList(
        kind.value,
        applied.value,
        filters.value,
        page.value,
        size.value,
      );
      if (current !== revision || !accepted) return;
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
    ElMessage.warning(tr('ui.equipment.text0493'));
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
        get confirmButtonText() { return tr('ui.equipment.text0494'); },
        get cancelButtonText() { return tr('ui.equipment.text0157'); },
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
      get confirmButtonText() { return tr('ui.equipment.text0146'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
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
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => rows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => form.value.form.lines;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => form.value.movements;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];
</script>
<template>
  <section class="content operation-page">
    <p v-if="error" role="alert" class="error">
      {{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </p>
    <template v-if="!editing">
      <AppListToolbar class="toolbar">
        <ElButton
          v-if="auth.can(menu, '新增')"
          type="primary"
          @click="router.push({ name: 'inventory-operation-detail', params: { kind, id: 'new' } })"
          ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
        >
        <form @submit.prevent="search">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput v-model="q" :placeholder="tr('ui.equipment.text0495')" /><ElButton
            text
            @click="reset"
            >{{ tr('ui.equipment.text0204') }}</ElButton
          >
        </form>
      </AppListToolbar>
      <div class="list-card">
        <div class="table-scroll">
          <AppBusinessTable
            :rows="rows"
            :pagination="false"
            :fill="true"
            :framed="false"
            class="business-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="c in columns"
              :min-width="150"
              :prop="String(c)"
              :key="String(c)"
              ><template #header
                ><div class="table-header-label">{{ fields[c] }}</div>
                <div v-if="filterOpen" class="table-column-filter" @click.stop>
                  <ElInput
                    v-model="filters[c]"
                    clearable
                    :placeholder="tr('ui.equipment.text0301') + fields[c]"
                  /></div></template
              ><template #default="{ row: r, $index: tableRowIndex }">
                <ElTag v-if="c === 'status'" :type="tone(r.status)">{{ enumLabel('status', r.status) }}</ElTag
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
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="action"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
              ><template #default="{ row: r, $index: tableRowIndex }">
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
                    r.status === '草稿' ? tr('ui.equipment.text0207') : r.status === '在途' ? tr('ui.equipment.text0496') : tr('ui.equipment.text0206')
                  }}</ElButton
                >
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!rows.length">
                {{ loading ? tr('ui.equipment.text0162') : error ? tr('ui.equipment.text0160') : tr('ui.equipment.text0497') }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer>
          <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
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
              ><ElCheckbox
                v-for="(name, key) in fields"
                :key="key"
                :model-value="columns.includes(key)"
                :disabled="columns.length === 1 && columns.includes(key)"
                @change="toggle(key)"
                >{{ name }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </template>
    <template v-else>
      <header>
        <AppBackButton :disabled="busy" @click="back" /><strong>{{
          form.code || tr('ui.equipment.text0149') + names[kind]
        }}</strong
        ><ElTag :type="tone(form.status)">{{ enumLabel('status',form.status) }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >{{ kind === 'transfers' ? tr('ui.equipment.text0498') : tr('ui.equipment.text0499')
            }}<span v-if="readonly || (kind === 'counts' && form.id)">{{ form.warehouseName }}</span
            ><ElSelect v-else v-model="form.warehouseId" filterable :placeholder="tr('ui.equipment.text0500')"
              ><ElOption
                v-for="w in choices.warehouses"
                :key="w.id"
                :value="w.id"
                :label="w.name" /></ElSelect></label
          ><label v-if="kind === 'transfers'"
            >{{ tr('ui.equipment.text0501') }}<span v-if="readonly">{{
              choices.warehouses.find((w) => w.id === form.targetWarehouseId)?.name
            }}</span
            ><ElSelect v-else v-model="form.targetWarehouseId" filterable
              ><ElOption
                v-for="w in destinations"
                :key="w.id"
                :value="w.id"
                :label="w.name" /></ElSelect></label
          ><label v-if="kind === 'reversals'"
            >{{ tr('ui.equipment.text0502') }}<span v-if="readonly">{{ form.form.sourceCode || form.form.receiptCode }}</span
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
                      receipt: tr('ui.equipment.text0503'),
                      issue: tr('ui.equipment.text0504'),
                      returns: tr('ui.equipment.text0505'),
                      transfers: tr('ui.equipment.text0506'),
                      counts: tr('ui.equipment.text0507'),
                      openings: tr('ui.equipment.text0508'),
                    } as Record<string, string>
                  )[r.sourceKind || 'receipt'] || '')
                " /></ElSelect></label
          ><label class="remark"
            >{{ kind === 'reversals' ? tr('ui.equipment.text0509') : tr('ui.equipment.text0130')
            }}<ElInput v-model="form.form.remark" :readonly="readonly" maxlength="1000"
          /></label>
        </div>
        <div v-if="kind === 'counts'" class="notice">
          <strong>{{ tr('ui.equipment.text0510') }} {{ display(form.form.baselineAt) }}</strong
          ><span
            >{{ tr('ui.equipment.text0511') }}</span
          >
        </div>
        <div v-if="kind === 'openings' && !readonly" class="conversion-options">
          <label
            >{{ tr('ui.equipment.text0512') }}<ElSelect v-model="conversionMode" @change="changeOpeningMode"
              ><ElOption :value="false" :label="tr('ui.equipment.text0513')" /><ElOption
                :value="true"
                :label="tr('ui.equipment.text0514')" /></ElSelect
          ></label>
          <label v-if="conversionMode"
            >{{ tr('ui.equipment.text0515') }}<ElSelect
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
          {{ tr('ui.equipment.text0516') }}
        </div>
        <div v-if="kind === 'openings' && conversionMode" class="line-scroll">
          <AppBusinessTable
            :rows="form.form.lines"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="line-table conversion-table"
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">{{
                l.materialName
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0499') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">{{
                choices.warehouses.find((w) => w.id === l.warehouseId)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">{{
                choices.bins.find((b) => b.id === l.binId)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0517') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">{{
                formatNumber(l.quantity)
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0518') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">
                <ElInput
                  v-model="l.batchCode"
                  :readonly="readonly"
                  :placeholder="tr('ui.equipment.text0312')"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0519') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">
                <ElDatePicker
                  v-model="l.productionDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :disabled="readonly"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0520') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }">
                <ElDatePicker
                  v-model="l.expiryDate"
                  type="date"
                  value-format="YYYY-MM-DD"
                  :disabled="readonly"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0521') }}</div></template
              ><template #default="{ row: l, $index: tableRowIndex }"
                >{{ tr('ui.equipment.text0522') }}</template
              ></ElTableColumn
            ><template #empty
              ><div v-if="!form.form.lines.length">
                {{ tr('ui.equipment.text0523') }}
              </div></template
            ></AppBusinessTable
          >
          <p class="notice">
            {{ tr('ui.equipment.text0524') }}
          </p>
        </div>
        <template v-if="kind !== 'reversals' && !conversionMode"
          ><div class="detail-heading">
            <strong>{{ kind === 'counts' ? tr('ui.equipment.text0525') : tr('ui.equipment.text0526') }}</strong
            ><ElButton
              v-if="!readonly && kind !== 'counts'"
              :disabled="!form.warehouseId"
              type="primary"
              plain
              @click="add"
              ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0527') }}</ElButton
            >
          </div>
          <div class="line-scroll">
            <AppBusinessTable
              :rows="form.form.lines"
              :pagination="false"
              :fill="false"
              :framed="false"
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn
                v-if="!readonly && kind !== 'counts'"
                fixed="right"
                :width="170"
                :min-width="156"
                :resizable="false"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: l, $index: index }">
                  <ElButton link type="danger" @click="form.form.lines.splice(index, 1)"
                    >{{ tr('ui.equipment.text0146') }}</ElButton
                  >
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!form.form.lines.length">
                  {{
                    kind === 'counts'
                      ? tr('ui.equipment.text0528')
                      : tr('ui.equipment.text0529')
                  }}
                </div></template
              ></AppBusinessTable
            >
          </div></template
        >
        <template v-if="form.movements?.length"
          ><div class="detail-heading"><strong>{{ tr('ui.equipment.text0530') }}</strong></div>
          <div class="line-scroll">
            <AppBusinessTable
              :rows="form.movements"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
                ><template #default="{ row: m, $index: tableRowIndex }">{{
                  m.materialName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
                ><template #default="{ row: m, $index: tableRowIndex }">{{
                  m.binName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0518') }}</div></template
                ><template #default="{ row: m, $index: tableRowIndex }">{{
                  m.batchCode
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0531') }}</div></template
                ><template #default="{ row: m, $index: tableRowIndex }">{{
                  formatNumber(m.quantity)
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn v-if="canMoney" :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0532') }}</div></template
                ><template #default="{ row: m, $index: tableRowIndex }">{{
                  formatNumber(m.amount)
                }}</template></ElTableColumn
              ></AppBusinessTable
            >
          </div></template
        >
        <div v-if="form.events?.length" class="records">
          <strong>{{ tr('ui.equipment.text0533') }}</strong>
          <p v-for="(e, index) in form.events" :key="index">
            <i />{{ e.actorName }} · {{ e.action }}<time>{{ display(e.createdAt) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">{{ tr('ui.equipment.text0162') }}</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readonly ? tr('ui.equipment.text0208') : tr('ui.equipment.text0157') }}</ElButton
        ><template v-if="!error && !loading"
          ><ElButton v-if="!readonly" :loading="busy" @click="save">{{
            kind === 'counts' && !form.id ? tr('ui.equipment.text0534') : tr('ui.equipment.text0275')
          }}</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can(menu, '删除')"
            :disabled="busy"
            @click="remove"
            >{{ tr('ui.equipment.text0146') }}</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can(menu, confirmAction)"
            type="primary"
            :disabled="busy || guard.dirty.value"
            @click="confirm()"
            >{{ kind === 'transfers' ? tr('ui.equipment.text0535') : tr('ui.equipment.text0494') }}</ElButton
          ><ElButton
            v-if="form.status === '在途' && auth.can(menu, '入库')"
            type="primary"
            :disabled="busy"
            @click="confirm(true)"
            >{{ tr('ui.equipment.text0536') }}</ElButton
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
  height: 100%;
  display: flex;
  flex-direction: column;
  /* gap: 8px; */
  min-height: 0;
  color: var(--text);
  font-size: 13px;
}
header,
footer,
.detail-heading {
  display: flex;
  align-items: center;
  gap: 12px;
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
  height: var(--list-footer-height);
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
:deep(.el-input),
:deep(.el-select),
:deep(.el-button) {
  font-size: 13px;
}
@media (max-width: 850px) {
  .metadata {
    grid-template-columns: 1fr;
  }
}
</style>
