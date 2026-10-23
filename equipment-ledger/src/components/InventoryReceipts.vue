<script setup lang="ts">
import { isDepartmentCustomColumn } from '../businessConfig';
import DepartmentValuesText from './common/DepartmentValuesText.vue';
import { enumLabel } from '../i18n';
import { tr } from '../i18n';

import DialogChoiceInput from './common/DialogChoiceInput.vue';
import { onUnmounted } from 'vue';
import { useReceiptList } from '../composables/inventory/useInventoryLists';
const { rows, total, refresh: refreshList, invalidate: invalidateList } = useReceiptList();
import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
const extensionInitializationClean = ref(true);
import { useBusinessColumns } from '../composables/useBusinessColumns';
import { customCell } from '../businessConfig';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import MaterialPicker from './common/MaterialPicker.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
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
  receiptApi,
  type Receipt,
  type ReceiptChoices,
  type ReceiptLine,
  type InventoryMaster,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const editing = computed(() => !!route.params.id);
const empty = (): Receipt => ({
  id: '',
  code: '',
  warehouseId: '',
  supplier: '',
  remark: '',
  status: '草稿',
  lines: [],
});
const form = ref(empty()),
  choices = ref<ReceiptChoices>({
    companies: [],
    users: [],
    warehouses: [],
    materials: [],
    bins: [],
  });
const query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  loading = ref(false),
  error = ref('');
const hasBatch = computed(() =>
  form.value.lines.some((line) => line.batchEnabled || material(line)?.batchEnabled),
);
const readOnly = computed(
  () =>
    form.value.status !== '草稿' || !auth.can('inventoryReceipt', form.value.id ? '编辑' : '新增'),
);
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && !readOnly.value,
  () => busy.value,
);
const baseFields: Record<string, string> = {
  code: '入库单号',
  warehouseName: '仓库',
  supplier: '供应商',
  status: '状态',
  actorName: '操作人',
  createdAt: '创建时间',
  confirmedAt: '入库时间',
};
const { definitions: customDepartmentDefinitions, extensionColumns, extensionFilters, extensionExportColumns, configurationError } =
  useBusinessColumns('inventory_receipt');
const fields = computed<Record<string, string>>(() => ({
  ...baseFields,
  ...Object.fromEntries(extensionColumns.value.map((c) => [c.key, c.label])),
}));
const conditions = ref<FilterCondition[]>([]),
  conditionOpen = ref(false);
const conditionFields = computed<FilterField[]>(() => extensionFilters.value);
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    conditionOpen,
    () => conditions.value,
    (value) => {
      conditions.value = value;
      page.value = 1;
      void load();
    },
  );
const columns = ref(Object.keys(fields.value)),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  filters = ref<Record<string, string>>({}),
  exportBusy = ref(false);
function display(row: Receipt, key: string) {
  if (key.startsWith('custom:')) return customCell(row, key);
  return key.endsWith('At')
    ? time(row[key as keyof Receipt] as string)
    : isDecimalField(key)
      ? formatNumber(row[key as keyof Receipt])
      : String(row[key as keyof Receipt] ?? '');
}
function toggleColumn(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((x) => x !== key);
  } else
    columns.value = Object.keys(fields.value).filter((x) => x === key || columns.value.includes(x));
}
async function exportRows() {
  if (exportBusy.value) return;
  exportBusy.value = true;
  try {
    const result = await receiptApi.export(
      applied.value,
      filters.value,
      JSON.stringify(conditions.value),
    );
    const exportFields = {
      ...Object.fromEntries(
        columns.value.filter((k) => !k.startsWith('custom:')).map((k) => [k, fields.value[k]]),
      ),
      ...Object.fromEntries(extensionExportColumns.value.map((c) => [c.key, c.label])),
    };
    const exportKeys = Object.keys(exportFields);
    const quote = (value: string) =>
      '"' + (/^[=+@\-]/.test(value) ? "'" : '') + value.replaceAll('"', '""') + '"';
    const content = [
      exportKeys.map((x) => quote(exportFields[x]!)).join(','),
      ...result.data.map((row) => exportKeys.map((key) => quote(display(row, key))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = '入库单.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
let revision = 0;
onUnmounted(() => {
  revision++;
  invalidateList();
});
function message(e: unknown) {
  return e instanceof Error ? e.message : tr('ui.equipment.text0491');
}
function time(s?: string) {
  return s?.replace('T', ' ').slice(0, 19) || '';
}
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      invalidateList();
      const options = await receiptApi.choices();
      if (current !== revision) return;
      choices.value = options.data;
      const loaded =
        route.params.id === 'new'
          ? empty()
          : (await receiptApi.detail(String(route.params.id))).data;
      if (current !== revision) return;
      form.value = loaded;
      guard.markSaved();
    } else {
      const accepted = await refreshList(
        applied.value,
        page.value,
        size.value,
        filters.value,
        JSON.stringify(conditions.value),
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
async function back() {
  if (await guard.permitLeave()) router.push({ name: 'inventory-receipts' });
}
const materialPickerOpen = ref(false);
const replacingLine = ref<ReceiptLine>();
function add() {
  replacingLine.value = undefined;
  materialPickerOpen.value = true;
}
function replaceMaterial(line: ReceiptLine) {
  replacingLine.value = line;
  materialPickerOpen.value = true;
}
function selectMaterials(selected: InventoryMaster[]) {
  const byId = new Map(choices.value.materials.map((m) => [m.id, m]));
  selected.forEach((m) => byId.set(m.id, m));
  choices.value.materials = [...byId.values()];
  const bins = choices.value.bins.filter(
    (x) => x.warehouseId === form.value.warehouseId && x.enabled,
  );
  for (const m of selected) {
    const line = replacingLine.value || {
      materialId: m.id,
      binId: bins.length === 1 ? bins[0]!.id : '',
      quantity: '1',
      unitPrice: '',
      allocatedFee: '0',
      batchCode: '',
      supplierBatch: '',
      productionDate: '',
      expiryDate: '',
    };
    line.materialId = m.id;
    changeMaterial(line);
    line.materialCode = m.code;
    line.materialName = m.name;
    if (!replacingLine.value) form.value.lines.push(line);
  }
  replacingLine.value = undefined;
}
function material(line: ReceiptLine) {
  return choices.value.materials.find((x) => x.id === line.materialId);
}
function changeMaterial(line: ReceiptLine) {
  line.batchCode = '';
  line.supplierBatch = '';
  line.productionDate = '';
  line.expiryDate = '';
  line.unitPrice = '';
  line.unit = material(line)?.unit;
  line.batchEnabled = material(line)?.batchEnabled;
  line.expiryEnabled = material(line)?.expiryEnabled;
}
function warehouseChanged() {
  const bins = choices.value.bins.filter(
    (x) => x.warehouseId === form.value.warehouseId && x.enabled,
  );
  form.value.lines.forEach((x) => (x.binId = bins.length === 1 ? bins[0]!.id : ''));
}
async function save() {
  if (busy.value || readOnly.value) return;
  if (!form.value.warehouseId) {
    ElMessage.warning(tr('ui.equipment.text0614'));
    return;
  }
  if (!form.value.lines.length) {
    ElMessage.warning(tr('ui.equipment.text0615'));
    return;
  }
  if (form.value.lines.some((x) => !x.materialId)) {
    ElMessage.warning(tr('ui.equipment.text0616'));
    return;
  }
  if (!prepareExtension(form.value)) return;
  busy.value = true;
  try {
    const result = await receiptApi.save(form.value);
    form.value = result.data;
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({ name: 'inventory-receipt-detail', params: { id: result.data.id } });
    ElMessage.success(tr('ui.equipment.text0540'));
  } catch (e) {
    if (extensionError(e)) return;
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (busy.value || readOnly.value || !form.value.id) return;
  if (guard.dirty.value) {
    ElMessage.warning(tr('ui.equipment.text0617'));
    return;
  }
  try {
    await ElMessageBox.confirm('确认后将增加库存并记录成本，单据不能直接修改。', '确认入库', {
      get confirmButtonText() { return tr('ui.equipment.text0618'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      closeOnClickModal: false,
      type: 'warning',
    });
  } catch {
    return;
  }
  if (busy.value) return;
  busy.value = true;
  try {
    form.value = (await receiptApi.confirm(form.value)).data;
    guard.markSaved();
    ElMessage.success(tr('ui.equipment.text0619'));
  } catch (e) {
    if (extensionError(e)) return;
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除这张草稿？已入库单据不能删除。', '删除', {
      get confirmButtonText() { return tr('ui.equipment.text0146'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      type: 'warning',
    });
    busy.value = true;
    await receiptApi.delete(form.value);
    guard.markSaved();
    router.push({ name: 'inventory-receipts' });
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function allocateFees() {
  try {
    const { value } = await ElMessageBox.prompt(
      '按各行不含税采购金额比例分摊，尾差计入最后一行。请先保存草稿。',
      '费用分摊（元）',
      {
        inputPattern: /^\d+(\.\d{1,2})?$/,
        inputErrorMessage: tr('ui.validation.message18'),
        get confirmButtonText() { return tr('ui.equipment.text0620'); },
        get cancelButtonText() { return tr('ui.equipment.text0157'); },
      },
    );
    busy.value = true;
    form.value = (await receiptApi.allocate(form.value, value)).data;
    guard.markSaved();
    ElMessage.success(tr('ui.equipment.text0621'));
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => rows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => form.value.lines;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
</script>
<template>
  <section class="receipts">
    <p v-if="error" class="error" role="alert">
      {{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </p>
    <p v-if="configurationError" class="error" role="alert">{{ configurationError }}</p>
    <template v-if="!editing">
      <AppListToolbar class="toolbar">
        <ElButton
          v-if="auth.can('inventoryReceipt', '入库')"
          type="primary"
          @click="router.push({ name: 'inventory-receipt-detail', params: { id: 'new' } })"
          ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
        >
        <form
          @submit.prevent="
            applied = query;
            page = 1;
            load();
          "
        >
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput v-model="query" :placeholder="tr('ui.equipment.text0622')" /><ElButton
            v-if="conditionFields.length"
            @click="showFilter($event.currentTarget as HTMLElement)"
            >{{ tr('ui.equipment.text0203') }}</ElButton
          ><ElButton
            text
            @click="
              query = '';
              applied = '';
              filters = {};
              conditions = [];
              page = 1;
              load();
            "
            >{{ tr('ui.equipment.text0204') }}</ElButton
          >
        </form>
      </AppListToolbar>
      <AppliedFilterChips
        :conditions="conditions"
        :fields="conditionFields"
        @change="replaceFilters"
        @edit="(condition, anchor) => showFilter(anchor, condition.field)"
      />
      <FilterConditionPopover
        v-model="conditionOpen"
        :anchor="filterAnchor"
        :field-key="editingFilter"
        :conditions="conditions"
        :fields="conditionFields"
        @confirm="confirmFilter"
      />
      <div class="card">
        <div class="scroll">
          <AppBusinessTable
            :rows="rows"
            :pagination="false"
            :fill="true"
            :framed="false"
            class="business-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="key in columns"
              :min-width="150"
              :prop="String(key)"
              :key="String(key)"
              ><template #header
                ><div class="table-header-label">{{ fields[key] }}</div>
                <div
                  v-if="filterOpen && !key.startsWith('custom:')"
                  class="table-column-filter"
                  @click.stop
                >
                  <ElInput
                    v-model="filters[key]"
                    clearable
                    :placeholder="tr('ui.equipment.text0301') + fields[key]"
                  /></div></template
              ><template #default="{ row: row, $index: tableRowIndex }">
                <ElButton
                  v-if="key === 'code'"
                  type="primary"
                  link
                  @click="router.push({ name: 'inventory-receipt-detail', params: { id: row.id } })"
                  >{{ row.code }}</ElButton
                ><ElTag
                  v-else-if="key === 'status'"
                  :type="row.status === '已入库' ? 'success' : 'warning'"
                  >{{ enumLabel('status',row.status) }}</ElTag
                ><DepartmentValuesText v-else-if="isDepartmentCustomColumn(customDepartmentDefinitions, key)" :value="String(display(row, key))" :ids="row.customFields?.[key.slice(7)]" /><span v-else>{{ display(row, key) }}</span>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="action"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
              ><template #default="{ row: row, $index: tableRowIndex }">
                <ElButton
                  type="primary"
                  link
                  @click="router.push({ name: 'inventory-receipt-detail', params: { id: row.id } })"
                  >{{ row.status === '草稿' ? tr('ui.equipment.text0207') : tr('ui.equipment.text0206') }}</ElButton
                >
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!rows.length">
                {{ loading ? tr('ui.equipment.text0162') : error ? tr('ui.equipment.text0468') : tr('ui.equipment.text0623') }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer class="list-footer">
          <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
          ><AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
          <ListToolbarIcons
            class="footer-tools"
            mode="footer"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            :can-export="auth.can('inventoryReceipt', '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><ElCheckbox
                v-for="(label, key) in fields"
                :key="key"
                :model-value="columns.includes(key)"
                :disabled="columns.length === 1 && columns.includes(key)"
                @change="toggleColumn(key)"
                >{{ label }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </template>
    <template v-else>
      <header>
        <AppBackButton :disabled="busy" @click="back" /><strong>{{
          form.id ? form.code : tr('ui.equipment.text0624')
        }}</strong
        ><ElTag :type="form.status === '已入库' ? 'success' : 'warning'">{{ enumLabel('status',form.status) }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >{{ tr('ui.equipment.text0499') }}<ElSelect
              v-model="form.warehouseId"
              :disabled="readOnly"
              :placeholder="tr('ui.equipment.text0625')"
              @change="warehouseChanged"
              ><ElOption
                v-for="w in choices.warehouses"
                :key="w.id"
                :label="w.name"
                :value="w.id" /></ElSelect></label
          ><label
            >{{ tr('ui.equipment.text0118') }}<ElInput v-model="form.supplier" :disabled="readOnly" maxlength="120" /></label
          ><label class="wide"
            >{{ tr('ui.equipment.text0130') }}<ElInput v-model="form.remark" :disabled="readOnly" maxlength="1000"
          /></label>
        </div>
        <div class="detail-heading">
          <strong>{{ tr('ui.equipment.text0626') }}</strong
          ><ElButton
            v-if="!readOnly"
            type="primary"
            plain
            :disabled="!form.warehouseId"
            @click="add"
            ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0527') }}</ElButton
          ><ElButton
            v-if="!readOnly"
            :disabled="!form.id || guard.dirty.value || busy"
            @click="allocateFees"
            >{{ tr('ui.equipment.text0627') }}</ElButton
          ><span>{{ tr('ui.equipment.text0628') }}</span>
        </div>
        <div class="line-scroll">
          <AppBusinessTable
            :rows="form.lines"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="lines"
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150" class-name="fixed-material"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ line.materialCode }} {{ line.materialName }}</span
                ><DialogChoiceInput
                  v-else
                  :model-value="
                    [
                      line.materialCode || material(line)?.code,
                      line.materialName || material(line)?.name,
                    ]
                      .filter(Boolean)
                      .join(' · ')
                  "
                  :placeholder="tr('ui.equipment.text0575')"
                  :title="tr('ui.equipment.text0575')"
                  @open="replaceMaterial(line)"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0095') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.unit || material(line)?.unit
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ line.binName }}</span
                ><ElSelect v-else v-model="line.binId" filterable :placeholder="tr('ui.equipment.text0629')"
                  ><ElOption
                    v-for="b in choices.bins.filter((x) => x.warehouseId === form.warehouseId)"
                    :key="b.id"
                    :value="b.id"
                    :label="b.name"
                /></ElSelect> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <AppDecimalInput
                  v-model="line.quantity"
                  :readonly="readOnly"
                  inputmode="decimal"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0630') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <AppDecimalInput
                  v-model="line.unitPrice"
                  :readonly="readOnly"
                  inputmode="decimal"
                  :placeholder="tr('ui.equipment.text0631')"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0627') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <AppDecimalInput
                  v-model="line.allocatedFee"
                  :readonly="readOnly"
                  inputmode="decimal"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0632') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <AppDecimalInput
                  v-model="line.taxRate"
                  :readonly="readOnly"
                  inputmode="decimal"
                  :placeholder="tr('ui.equipment.text0633')"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="hasBatch" :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0518') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <ElInput
                  v-if="line.batchEnabled || material(line)?.batchEnabled"
                  v-model="line.batchCode"
                  :readonly="readOnly"
                  :placeholder="tr('ui.equipment.text0312')"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="hasBatch" :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0634') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <ElInput
                  v-if="line.batchEnabled || material(line)?.batchEnabled"
                  v-model="line.supplierBatch"
                  :readonly="readOnly"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="hasBatch" :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0519') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <ElDatePicker
                  v-if="!readOnly && material(line)?.batchEnabled"
                  v-model="line.productionDate"
                  value-format="YYYY-MM-DD"
                  type="date"
                /><span v-else>{{ line.productionDate }}</span>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="hasBatch" :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0520') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <ElDatePicker
                  v-if="!readOnly && material(line)?.batchEnabled"
                  v-model="line.expiryDate"
                  value-format="YYYY-MM-DD"
                  type="date"
                /><span v-else>{{ line.expiryDate }}</span>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="readOnly" :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0635') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                formatNumber(line.totalCost)
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn
              v-if="!readOnly"
              class-name="action"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <ElButton link type="danger" @click="form.lines.splice(index, 1)">{{ tr('ui.equipment.text0146') }}</ElButton>
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!form.lines.length">{{ tr('ui.equipment.text0636') }}</div></template
            ></AppBusinessTable
          >
        </div>
        <BusinessFields
          ref="extension"
          v-model="form"
          @initialize="extensionInitializationClean = !guard.dirty.value"
          @ready="extensionInitializationClean && guard.markSaved()"
          object="inventory_receipt"
          :readonly="readOnly"
          :is-new="!form.id"
          :errors="extensionErrors"
        />
        <div v-if="form.events?.length" class="records">
          <strong>{{ tr('ui.equipment.text0637') }}</strong>
          <p v-for="(event, index) in form.events" :key="index">
            <span class="dot" />{{ event.actorName }} · {{ event.action }}
            <time>{{ time(event.createdAt) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">{{ tr('ui.equipment.text0162') }}</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readOnly ? tr('ui.equipment.text0208') : tr('ui.equipment.text0157') }}</ElButton
        ><ElButton v-if="!readOnly && !error && !loading" :loading="busy" @click="save"
          >{{ tr('ui.equipment.text0275') }}</ElButton
        ><ElButton
          v-if="
            !error && !loading && form.status === '草稿' && auth.can('inventoryReceipt', '入库')
          "
          type="primary"
          :disabled="busy || !form.id || guard.dirty.value"
          @click="confirm"
          >{{ tr('ui.equipment.text0618') }}</ElButton
        ><ElButton
          v-if="form.id && form.status === '草稿' && auth.can('inventoryReceipt', '删除')"
          type="danger"
          plain
          :disabled="busy"
          @click="removeDraft"
          >{{ tr('ui.equipment.text0146') }}</ElButton
        ></AppFormFooter
      >
    </template>
    <MaterialPicker
      v-model="materialPickerOpen"
      :multiple="!replacingLine"
      :load-rows="receiptApi.materials"
      @select="selectMaterials"
    />
  </section>
</template>
<style scoped>
.list-footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}

.receipts {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 54px);
  min-height: 0;
  padding: 12px;
  font-size: 13px;
  color: var(--text);
  gap: 8px;
}
header,
.detail-heading,
.list-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}
header {
  background: #fff;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e9f0;
}
header strong {
  font-size: 15px;
}
.card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  overflow: hidden;
}
.scroll {
  flex: 1;
  overflow: auto;
}
.list-footer {
  height: 46px;
  flex: none;
  padding: 0 12px;
  border-top: 1px solid #e4e9f0;
}
.footer-tools {
  margin-left: auto;
}
.editor-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
  padding: 18px;
}
.metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 24px;
  max-width: 1000px;
}
.metadata label {
  display: grid;
  grid-template-columns: 70px 1fr;
  align-items: center;
  gap: 10px;
}
.wide {
  grid-column: 1/-1;
}
.detail-heading {
  margin: 24px 0 12px;
}
.detail-heading span {
  margin-left: auto;
  color: var(--text-secondary);
}
table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  min-width: 950px;
}
.action {
  position: sticky;
  right: 0;
  width: 100px;
  min-width: 100px;
  z-index: 2;
  box-shadow: -1px 0 #e4e9f0;
}
.line-scroll {
  overflow: auto;
  border: 1px solid #e4e9f0;
  border-radius: 5px;
}
.lines {
  min-width: 850px;
}
.lines :deep(td:first-child) {
  min-width: 270px;
}
.lines :deep(td:nth-child(2)) {
  min-width: 60px;
}
.lines :deep(td) {
  min-width: 130px;
}
.lines :deep(.el-date-editor) {
  width: 150px;
}
.fixed-material {
  position: sticky;
  left: 0;
  z-index: 2;
  box-shadow: 1px 0 #e4e9f0;
}
.empty {
  text-align: center;
  padding: 48px;
}
.records {
  margin-top: 24px;
}
.records p {
  display: flex;
  gap: 10px;
  align-items: center;
}
.records time {
  margin-left: 12px;
}
.dot {
  height: 7px;
  width: 7px;
  border-radius: 50%;
  background: var(--brand);
}
.error {
  color: #c13449;
}
:deep(.el-input),
:deep(.el-select) {
  font-size: 13px;
}
:deep(.el-button) {
  font-size: 13px;
}
</style>
