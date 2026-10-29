<script setup lang="ts">import { enumLabel } from '../i18n';
import { tr } from '../i18n';

import DialogChoiceInput from './common/DialogChoiceInput.vue';
import PagedChoiceField from './common/PagedChoiceField.vue';
import { mergeOptions, type SelectorRow, type SelectorPage } from '../selectors';

import { onUnmounted } from 'vue';
import { useRequisitionList } from '../composables/inventory/useInventoryLists';
const { rows, total, refresh: refreshList, invalidate: invalidateList } = useRequisitionList();
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import MaterialPicker from './common/MaterialPicker.vue';
import type { InventoryMaster } from '../inventory';
import AppListTabs from './common/AppListTabs.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { request } from '../api';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTag,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import RequisitionApproval from './workflow/RequisitionApproval.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  requisitionApi,
  type Requisition,
  type RequisitionChoices,
  type RequisitionLine,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const editing = computed(() => !!route.params.id);
const empty = (): Requisition => ({
  id: '',
  code: '',
  warehouseId: '',
  taskKind: '',
  taskId: '',
  remark: '',
  status: '草稿',
  lines: [],
});
const form = ref(empty()),
  choices = ref<RequisitionChoices>({
    companies: [],
    users: [],
    warehouses: [],
    materials: [],
    bins: [],
    tasks: [],
  });
const busy = ref(false),
  loading = ref(false),
  error = ref(''),
  query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(20),
  filters = ref<Record<string, string>>({}),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  exportBusy = ref(false);
const fields: Record<string, string> = {
  code: '领料单号',
  warehouseName: '仓库',
  taskKind: '任务类型',
  taskCode: '任务编号',
  applicantName: '申请人',
  status: '状态',
  createdAt: '申请时间',
};
const columns = ref(Object.keys(fields));
const pendingOnly = computed(() => route.query.queue === 'pending');
const selectedRequests = ref<string[]>([]),
  mergedBusy = ref(false);
async function previewMerged(ids = selectedRequests.value) {
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { requests: ids.join(','), tab: 'pending' },
  });
}
function selectRequest(id: string, checked: boolean) {
  selectedRequests.value = checked
    ? [...new Set([...selectedRequests.value, id])]
    : selectedRequests.value.filter((x) => x !== id);
}
const readOnly = computed(
  () =>
    form.value.status !== '草稿' ||
    (!auth.can('inventoryRequisition', form.value.id ? '编辑' : '新增') && !form.value.taskId) ||
    (!!form.value.id && form.value.applicantId !== auth.user?.id),
);
const taskValue = computed({
  get: () =>
    form.value.taskKind && form.value.taskId ? form.value.taskKind + ':' + form.value.taskId : '',
  set: (value: string) => {
    const [kind, id] = value.split(':');
    form.value.taskKind = kind || '';
    form.value.taskId = id || '';
    form.value.warehouseId = '';
  },
});
const taskPickerOptions = computed(() =>
  choices.value.tasks.map((t) => ({ ...t, recordId: t.id, id: t.kind + ':' + t.id })),
);
async function loadTaskRows(q: string, page: number, size: number) {
  const result = await request<SelectorPage<SelectorRow>>(
    `/inventory/selectors/tasks?${new URLSearchParams({ mode: 'requisition', q, page: String(page), size: String(size) })}`,
  );
  return {
    ...result,
    data: result.data.map((t) => ({ ...t, recordId: t.id, id: t.kind + ':' + t.id })),
  };
}
let taskChoiceRevision = 0;
function rememberTasks(rows: SelectorRow[]) {
  choices.value.tasks = mergeOptions<SelectorRow>(
    choices.value.tasks.map((t) => ({ ...t, id: t.kind + ':' + t.id })),
    rows,
  ).map((t) => ({ ...t, id: t.id.split(':')[1]! })) as typeof choices.value.tasks;
}
watch(taskValue, async () => {
  const current = ++taskChoiceRevision;
  if (loading.value || readOnly.value) return;
  const requestedTask = taskValue.value;
  try {
    const result = await requisitionApi.choices(true, form.value.taskKind, form.value.taskId);
    if (current !== taskChoiceRevision || requestedTask !== taskValue.value || !editing.value)
      return;
    choices.value.warehouses = result.data.warehouses;
  } catch (e) {
    if (current === taskChoiceRevision)
      ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0638'));
  }
});
const selectedTask = computed(() =>
  choices.value.tasks.find((t) => t.id === form.value.taskId && t.kind === form.value.taskKind),
);
const warehouses = computed(() =>
  choices.value.warehouses.filter((w) => w.companyId === selectedTask.value?.companyId),
);
const warehouseHint = computed(() => {
  if (!taskValue.value) return tr('ui.validation.message19');
  if (!selectedTask.value) return '关联任务已结束或不可申请领料，请重新选择';
  if (!warehouses.value.length) return '任务所属公司暂无可申请领料的仓库，请联系仓管';
  return '';
});
const canReserve = computed(
  () =>
    auth.can('inventoryOutbound', '预留') &&
    ['待预留', '部分预留', '部分发料'].includes(form.value.status),
);
const canRelease = computed(
  () =>
    auth.can('inventoryOutbound', '释放预留') &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status) &&
    !!form.value.reservations?.length,
);
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && !readOnly.value,
  () => busy.value,
);
const canCancel = computed(
  () =>
    !!form.value.id &&
    ['草稿', '待预留', '部分预留', '已预留'].includes(form.value.status) &&
    form.value.lines.every((line) => Number(line.issuedQuantity || 0) === 0) &&
    (form.value.applicantId === auth.user?.id || auth.can('inventoryOutbound', '关闭剩余需求')),
);
const canIssue = computed(
  () =>
    auth.can('inventoryOutbound', '确认出库') &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status),
);
function openIssue() {
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { requests: form.value.id, tab: 'pending' },
  });
}
const canCloseRemaining = computed(
  () =>
    !!form.value.id &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status) &&
    (form.value.applicantId === auth.user?.id || auth.can('inventoryOutbound', '关闭剩余需求')),
);
async function closeRemaining() {
  try {
    const { value } = await ElMessageBox.prompt(
      '关闭后释放未发数量的预留，已出库记录保留。',
      '关闭剩余需求',
      {
        inputPlaceholder: '填写关闭原因',
        inputValidator: (v) => !!v?.trim() || tr('ui.equipment.text0461'),
        get confirmButtonText() { return tr('ui.equipment.text0542'); },
        get cancelButtonText() { return tr('ui.equipment.text0157'); },
        closeOnClickModal: false,
      },
    );
    busy.value = true;
    form.value = (
      await request<{ data: Requisition }>(
        `/inventory/requisitions/${form.value.id}/close-remaining`,
        {
          method: 'POST',
          body: JSON.stringify({ rowVersion: form.value.rowVersion, reason: value }),
        },
      )
    ).data;
    guard.markSaved();
    ElMessage.success(tr('ui.equipment.text0639'));
  } catch (e) {
    if (e instanceof Error) ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
let revision = 0;
onUnmounted(() => {
  taskChoiceRevision++;
  revision++;
  invalidateList();
});
function message(e: unknown) {
  return e instanceof Error ? e.message : tr('ui.equipment.text0491');
}
function display(row: Requisition, key: string) {
  const v = String(row[key as keyof Requisition] ?? '');
  return key.endsWith('At')
    ? v.replace('T', ' ').slice(0, 19)
    : isDecimalField(key)
      ? formatNumber(v)
      : v;
}
function tone(status: string) {
  return status === '已完成'
    ? 'success'
    : status === '已预留'
      ? 'primary'
      : status === '已取消'
        ? 'info'
        : 'warning';
}
function number(value?: string) {
  return formatNumber(value);
}
function material(line: RequisitionLine) {
  return choices.value.materials.find((m) => m.id === line.materialId);
}
async function load() {
  taskChoiceRevision++;
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      invalidateList();
      const result =
        route.params.id === 'new'
          ? empty()
          : (await requisitionApi.detail(String(route.params.id))).data;
      if (route.params.id === 'new') {
        result.taskKind = String(route.query.taskKind || '');
        result.taskId = String(route.query.taskId || '');
      }
      const options = result.status === '草稿' && (!result.id || result.applicantId === auth.user?.id)
        ? await requisitionApi.choices(true, result.taskKind, result.taskId)
        : {data:{companies:[],users:[],warehouses:[],materials:[],bins:[],tasks:[]} as RequisitionChoices};
      if (current !== revision) return;
      choices.value = options.data;
      form.value = result;
      guard.markSaved();
    } else {
      const accepted = await refreshList(
        applied.value,
        { ...filters.value, ...(pendingOnly.value ? { pendingOut: '1' } : {}) },
        page.value,
        size.value,
      );
      if (current !== revision || !accepted) return;
    }
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
  () => route.fullPath,
  () => {
    selectedRequests.value = [];
    page.value = 1;
    load();
  },
  { immediate: true },
);
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
async function back() {
  if (await guard.permitLeave()) router.push({ name: 'inventory-requisitions' });
}
const materialPickerOpen = ref(false);
const materialTarget = ref<RequisitionLine>();
function add() {
  form.value.lines.push({ materialId: '', quantity: '1' });
}
function chooseMaterial(line: RequisitionLine) {
  materialTarget.value = line;
  materialPickerOpen.value = true;
}
async function loadMaterialRows(q: string, page: number, size: number) {
  return request<{ data: InventoryMaster[]; page: { total: number } }>(
    `/inventory/requisitions/materials?${new URLSearchParams({ q, page: String(page), size: String(size), warehouseId: form.value.warehouseId, taskKind: form.value.taskKind, taskId: form.value.taskId })}`,
  );
}
function selectMaterials(rows: InventoryMaster[]) {
  const target = materialTarget.value;
  if (!target || !form.value.lines.includes(target)) return;
  const otherIds = new Set(form.value.lines.filter((l) => l !== target).map((l) => l.materialId));
  if (rows.some((m) => otherIds.has(m.id))) {
    ElMessage.warning(tr('ui.equipment.text0640'));
    return;
  }
  rows.forEach((m, index) => {
    if (!choices.value.materials.some((x) => x.id === m.id)) choices.value.materials.push(m);
    const value = {
      materialId: m.id,
      materialCode: m.code,
      materialName: m.name,
      materialModel: m.model,
      unit: m.unit,
      quantity: index === 0 ? target.quantity : '1',
    };
    if (index === 0) Object.assign(target, value);
    else form.value.lines.push(value);
  });
  materialPickerOpen.value = false;
}

async function save() {
  if (busy.value || readOnly.value) return;
  busy.value = true;
  try {
    const result = await requisitionApi.save(form.value);
    form.value = result.data;
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({
        name: 'inventory-requisition-detail',
        params: { id: result.data.id },
      });
    ElMessage.success(tr('ui.equipment.text0540'));
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function action(type: 'submit' | 'reserve' | 'release' | 'cancel') {
  if (busy.value) return;
  if (guard.dirty.value) {
    ElMessage.warning(tr('ui.equipment.text0493'));
    return;
  }
  if (type === 'release' || type === 'cancel') {
    try {
      await ElMessageBox.confirm(
        type === 'cancel'
          ? '取消申请并释放已预留库存？'
          : '释放后，这些库存将重新可供其他任务使用。',
        type === 'cancel' ? '取消申请' : '释放预留',
        {
          confirmButtonText: type === 'cancel' ? '取消申请' : '释放预留',
          get cancelButtonText() { return tr('ui.equipment.text0157'); },
          closeOnClickModal: false,
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }
  if (busy.value) return;
  busy.value = true;
  try {
    form.value = (await requisitionApi.action(form.value, type)).data;
    guard.markSaved();
    if (type === 'reserve' && form.value.status !== '已预留')
      ElMessage.warning(
        form.value.reservations?.length
          ? '库存不足，已预留当前可用数量，剩余数量可稍后继续预留'
          : '暂无可预留库存',
      );
    else
      ElMessage.success(
        { submit: '申请已提交', reserve: '预留完成', release: '已释放预留', cancel: '申请已取消' }[
          type
        ],
      );
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
function toggle(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((x) => x !== key);
  } else columns.value = Object.keys(fields).filter((x) => x === key || columns.value.includes(x));
}
async function exportRows() {
  if (exportBusy.value) return;
  exportBusy.value = true;
  try {
    const result = await requisitionApi.export(applied.value, {
      ...filters.value,
      ...(pendingOnly.value ? { pendingOut: '1' } : {}),
    });
    const quote = (s: string) =>
      '"' + (/^[=+@\-\t\r]/.test(s) ? "'" : '') + s.replaceAll('"', '""') + '"';
    const content = [
      columns.value.map((x) => quote(fields[x]!)).join(','),
      ...result.data.map((row) => columns.value.map((key) => quote(display(row, key))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = '领料申请.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
const poolOpen = ref(false),
  poolRows = ref<Record<string, string>[]>([]);
async function openPool() {
  try {
    poolRows.value = (await requisitionApi.pool(form.value.id)).data.map((v) => ({
      ...v,
      quantity: '0',
    }));
    poolOpen.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  }
}
async function manualReserve() {
  busy.value = true;
  try {
    const selections = poolRows.value.filter((v) => Number(v.quantity) > 0);
    form.value = (await requisitionApi.manualReserve(form.value, selections)).data;
    guard.markSaved();
    poolOpen.value = false;
    ElMessage.success(tr('ui.equipment.text0641'));
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除这张领料草稿？', '删除', {
      get confirmButtonText() { return tr('ui.equipment.text0146'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      type: 'warning',
    });
    busy.value = true;
    await requisitionApi.delete(form.value);
    guard.markSaved();
    router.push({ name: 'inventory-requisitions' });
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

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => form.value.reservations;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows4 = () => form.value.issues?.[0]?.allocations;
type TableColumnRow4 = NonNullable<ReturnType<typeof tableColumnRows4>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows5 = () => poolRows.value;
type TableColumnRow5 = NonNullable<ReturnType<typeof tableColumnRows5>>[number];
</script>
<template>
  <section class="content requisition-page">
    <p v-if="error" class="error" role="alert">
      {{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </p>
    <template v-if="!editing">
      <AppListTabs
        :active="pendingOnly ? 'pending' : 'requests'"
        :items="[
          { key: 'requests', label: '领料申请' },
          ...(auth.can('inventoryOutbound', '确认出库')
            ? [{ key: 'pending', label: '待出库' }]
            : []),
        ]"
        @select="
          (key) =>
            router.push(
              key === 'pending'
                ? { name: 'inventory-outbounds', query: { tab: 'pending' } }
                : { name: 'inventory-requisitions' },
            )
        "
      />
      <AppListToolbar class="toolbar">
        <ElButton
          v-if="pendingOnly && auth.can('inventoryOutbound', '确认出库')"
          type="primary"
          :disabled="!selectedRequests.length"
          :loading="mergedBusy"
          @click="previewMerged()"
          >{{ selectedRequests.length > 1 ? tr('ui.equipment.text0546') : tr('ui.equipment.text0504') }}</ElButton
        >
        <ElButton
          v-if="!pendingOnly && auth.can('inventoryRequisition', '新增')"
          type="primary"
          @click="router.push({ name: 'inventory-requisition-detail', params: { id: 'new' } })"
          ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
        >
        <form @submit.prevent="search">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput
            v-model="query"
            :placeholder="tr('ui.equipment.text0642')"
          /><ElButton text @click="reset">{{ tr('ui.equipment.text0204') }}</ElButton>
        </form>
      </AppListToolbar>
      <div class="card" :aria-busy="loading">
        <div class="scroll">
          <AppBusinessTable
            v-model:selection-ids="selectedRequests"
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            :selectable="pendingOnly"
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
                <div v-if="filterOpen" class="table-column-filter" @click.stop>
                  <ElInput
                    v-model="filters[key]"
                    :placeholder="tr('ui.equipment.text0301') + fields[key]"
                    clearable
                  /></div></template
              ><template #default="{ row: row, $index: tableRowIndex }">
                <ElTag v-if="key === 'status'" :type="tone(row.status)">{{ enumLabel('status',row.status) }}</ElTag
                ><ElButton
                  v-else-if="key === 'code'"
                  link
                  type="primary"
                  @click="
                    router.push({ name: 'inventory-requisition-detail', params: { id: row.id } })
                  "
                  >{{ row.code }}</ElButton
                ><span v-else>{{ display(row, key) }}</span>
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
                  v-if="pendingOnly && auth.can('inventoryOutbound', '确认出库')"
                  link
                  type="primary"
                  :disabled="mergedBusy"
                  @click="previewMerged([row.id])"
                  >{{ tr('ui.equipment.text0504') }}</ElButton
                >
                <ElButton
                  link
                  type="primary"
                  @click="
                    router.push({ name: 'inventory-requisition-detail', params: { id: row.id } })
                  "
                  >{{ row.status === '草稿' ? tr('ui.equipment.text0207') : tr('ui.equipment.text0206') }}</ElButton
                >
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!rows.length">
                {{ loading ? tr('ui.equipment.text0162') : error ? tr('ui.equipment.text0468') : tr('ui.equipment.text0643') }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer class="list-footer">
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
            :can-export="auth.can('inventoryRequisition', '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><ElCheckbox
                v-for="(label, key) in fields"
                :key="key"
                :model-value="columns.includes(key)"
                :disabled="columns.length === 1 && columns.includes(key)"
                @change="toggle(key)"
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
          form.id ? form.code : tr('ui.equipment.text0644')
        }}</strong
        ><ElTag :type="tone(form.status)">{{ enumLabel('status',form.status) }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >{{ tr('ui.equipment.text0566') }}<span v-if="readOnly">{{ form.taskKind }} · {{ form.taskCode }}</span>
            <PagedChoiceField
              v-else
              v-model="taskValue"
              :options="taskPickerOptions"
              :load-rows="loadTaskRows"
              :columns="[
                { key: 'kind', label: '类型', minWidth: 90 },
                { key: 'code', label: '任务编号', minWidth: 190 },
                { key: 'name', label: '设备／任务名称', minWidth: 220 },
              ]"
              :title="tr('ui.equipment.text0567')"
              :placeholder="tr('ui.equipment.text0645')"
              @select="rememberTasks" /></label
          ><label
            >{{ tr('ui.equipment.text0499') }}<span v-if="readOnly">{{
              choices.warehouses.find((w) => w.id === form.warehouseId)?.name || form.warehouseName
            }}</span
            ><ElSelect
              v-else
              v-model="form.warehouseId"
              filterable
              :disabled="!selectedTask || !warehouses.length"
              :placeholder="!selectedTask ? tr('ui.equipment.text0647') : tr('ui.equipment.text0646')"
              ><ElOption v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name"
            /></ElSelect>
            <span v-if="!readOnly && warehouseHint" class="warehouse-hint" role="status">{{
              warehouseHint
            }}</span></label
          ><label
            >{{ tr('ui.equipment.text0648') }}<span>{{ form.applicantName || auth.currentUser }}</span></label
          ><label
            >{{ tr('ui.equipment.text0130') }}<ElInput v-model="form.remark" :readonly="readOnly" maxlength="1000"
          /></label>
        </div>
        <div class="detail-heading">
          <strong>{{ tr('ui.equipment.text0649') }}</strong
          ><ElButton
            v-if="!readOnly"
            type="primary"
            plain
            :disabled="!form.warehouseId"
            @click="add"
            ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0572') }}</ElButton
          >
        </div>
        <div class="line-scroll">
          <AppBusinessTable
            :rows="form.lines"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table"
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0154') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ line.materialCode }}</span>
                <DialogChoiceInput
                  v-else
                  :model-value="line.materialCode || material(line)?.code"
                  :placeholder="tr('ui.equipment.text0574')"
                  :title="tr('ui.equipment.text0575')"
                  :disabled="!form.warehouseId"
                  @open="chooseMaterial(line)"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0155') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.materialName || material(line)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.materialModel || material(line)?.model
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0095') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.unit || material(line)?.unit
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0650') }}</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ number(line.quantity) }}</span
                ><AppDecimalInput
                  v-else
                  v-model="line.quantity"
                  inputmode="decimal"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="readOnly" :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0651') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                number(line.reservedQuantity)
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="readOnly" :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0652') }}</div></template
              ><template #default="{ row: line, $index: index }">{{
                number(line.issuedQuantity)
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
              ><div v-if="!form.lines.length">{{ tr('ui.equipment.text0653') }}</div></template
            ></AppBusinessTable
          >
        </div>
        <template v-if="form.reservations?.length"
          ><div class="detail-heading">
            <strong>{{ tr('ui.equipment.text0654') }}</strong><span>{{ tr('ui.equipment.text0655') }}</span>
          </div>
          <div class="line-scroll">
            <AppBusinessTable
              :rows="form.reservations"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.materialName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.binName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0518') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.batchCode
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0651') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  number(r.remainingQuantity)
                }}</template></ElTableColumn
              ></AppBusinessTable
            >
          </div></template
        >
        <div v-for="issue in form.issues" :key="issue.id" class="issue-history">
          <div class="detail-heading">
            <strong>{{ issue.code }}</strong
            ><ElTag :type="issue.reversed ? 'info' : 'success'">{{
              issue.reversed ? tr('ui.equipment.text0588') : tr('ui.equipment.text0656')
            }}</ElTag
            ><span v-if="issue.groupCode && issue.groupCode !== issue.code"
              >{{ tr('ui.equipment.text0657') }}{{ issue.groupCode }}</span
            ><span
              >{{ issue.actorName }} · {{ issue.createdAt.replace('T', ' ').slice(0, 19) }}</span
            >
          </div>
          <div class="line-scroll">
            <AppBusinessTable
              :rows="issue.allocations"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.materialName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.binName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0518') }}</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.batchCode
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0658') }}</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  number(a.quantity)
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0591') }}</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  formatNumber(a.amount)
                }}</template></ElTableColumn
              ></AppBusinessTable
            >
          </div>
          <p v-if="auth.can('inventoryRequisition', '查看金额')" class="issue-total">
            {{ tr('ui.equipment.text0659') }} {{ formatNumber(issue.totalAmount) }}
          </p>
        </div>
        <RequisitionApproval :rounds="form.workflow||[]" :applicant-id="form.applicantId" @changed="load"/>
        <div v-if="form.events?.length" class="records">
          <strong>{{ tr('ui.equipment.text0637') }}</strong>
          <p v-for="(event, index) in form.events" :key="index">
            <span class="dot" />{{ event.actorName }} · {{ event.action
            }}<span v-if="event.reason"> · {{ event.reason }}</span
            ><time>{{ event.createdAt.replace('T', ' ').slice(0, 19) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">{{ tr('ui.equipment.text0162') }}</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readOnly ? tr('ui.equipment.text0208') : tr('ui.equipment.text0157') }}</ElButton
        ><template v-if="!error && !loading"
          ><ElButton v-if="!readOnly" :loading="busy" @click="save">{{ tr('ui.equipment.text0275') }}</ElButton
          ><ElButton
            v-if="!readOnly"
            type="primary"
            :disabled="busy || !form.id || guard.dirty.value"
            @click="action('submit')"
            >{{ tr('ui.equipment.text0253') }}</ElButton
          ><ElButton v-if="canCloseRemaining && !canCancel" :disabled="busy" @click="closeRemaining"
            >{{ tr('ui.equipment.text0539') }}</ElButton
          ><ElButton v-if="canCancel" :disabled="busy" @click="action('cancel')">{{ tr('ui.equipment.text0660') }}</ElButton
          ><ElButton v-if="canRelease" :disabled="busy" @click="action('release')"
            >{{ tr('ui.equipment.text0538') }}</ElButton
          ><ElButton v-if="canIssue" type="primary" :disabled="busy" @click="openIssue"
            >{{ tr('ui.equipment.text0504') }}</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can('inventoryRequisition', '删除')"
            type="danger"
            plain
            :disabled="busy"
            @click="removeDraft"
            >{{ tr('ui.equipment.text0146') }}</ElButton
          ><ElButton v-if="canReserve" :disabled="busy" @click="openPool">{{ tr('ui.equipment.text0661') }}</ElButton
          ><ElButton v-if="canReserve" type="primary" :loading="busy" @click="action('reserve')">{{
            form.status === '部分预留' ? tr('ui.equipment.text0662') : tr('ui.equipment.text0663')
          }}</ElButton></template
        ></AppFormFooter
      >
    </template>
    <AppDialog
      v-model="poolOpen"
      :title="tr('ui.equipment.text0664')"
      width="min(860px,calc(100vw - 32px))"
      :busy="busy"
    >
      <p class="issue-hint">{{ tr('ui.equipment.text0665') }}</p>
      <div class="line-scroll">
        <AppBusinessTable
          :rows="poolRows"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="issue-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.materialName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.binName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0601') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.batchCode
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0602') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.expiryDate
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0666') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              number(row.availableQuantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0651') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }"
              ><AppDecimalInput
                v-model="row.quantity"
                inputmode="decimal" /></template></ElTableColumn
          ><template #empty
            ><div v-if="!poolRows.length">{{ tr('ui.equipment.text0667') }}</div></template
          ></AppBusinessTable
        >
      </div>
      <template #footer
        ><ElButton :disabled="busy" @click="poolOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton
          type="primary"
          :loading="busy"
          :disabled="!poolRows.some((v) => Number(v.quantity) > 0)"
          @click="manualReserve"
          >{{ tr('ui.equipment.text0537') }}</ElButton
        ></template
      >
    </AppDialog>
    <MaterialPicker
      v-model="materialPickerOpen"
      :multiple="true"
      :load-rows="loadMaterialRows"
      @select="selectMaterials"
    />
  </section>
</template>
<style scoped>
.list-footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}
.issue-table {
  min-width: 660px;
}
.issue-hint {
  margin: 0 0 16px;
  color: var(--text);
  line-height: 1.6;
}
.issue-total {
  text-align: right;
  font-weight: 700;
  color: var(--brand);
}
.requisition-page {
  height: 100%;
  display: flex;
  flex-direction: column;
  /* gap: 8px; */
  min-height: 0;
  font-size: 13px;
  color: var(--text);
}
header,
.list-footer,
.warehouse-hint {
  grid-column: 2;
  font-size: 13px;
  line-height: 1.5;
}
.detail-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}
.card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  background: white;
  overflow: hidden;
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
table {
  width: 100%;
  min-width: 850px;
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
.list-footer {
  height: var(--list-footer-height);
  padding: 0 12px;
  flex: none;
  border-top: 1px solid #e4e9f0;
}
.footer-tools {
  margin-left: auto;
}
header {
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e9f0;
}
header strong {
  font-size: 15px;
}
.editor-scroll {
  flex: 1;
  min-height: 0;
  background: white;
  padding: 18px;
  overflow: auto;
}
.metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 24px;
}
.metadata label {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 10px;
  align-items: center;
}
.warehouse-hint {
  grid-column: 2;
  font-size: 13px;
  line-height: 1.5;
}
.detail-heading {
  margin: 24px 0 12px;
}
.detail-heading span {
  margin-left: auto;
}
.line-scroll {
  overflow: auto;
  border: 1px solid #e4e9f0;
  border-radius: 5px;
}
.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.empty {
  text-align: center;
  padding: 40px;
}
.records {
  margin-top: 24px;
}
.records p {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
}
.records time {
  margin-left: 12px;
}
.error {
  color: #c13449;
  margin: 0;
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
  .editor-scroll {
    padding: 14px;
  }
}
.line-scroll table {
  min-width: 1000px;
}
.material {
  width: 180px;
  min-width: 180px;
}
</style>
