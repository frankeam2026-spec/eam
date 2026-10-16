<script setup lang="ts">
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
  }),
  rows = ref<Requisition[]>([]);
const busy = ref(false),
  loading = ref(false),
  error = ref(''),
  query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(10),
  total = ref(0),
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
const selectedTask = computed(() =>
  choices.value.tasks.find((t) => t.id === form.value.taskId && t.kind === form.value.taskKind),
);
const warehouses = computed(() =>
  choices.value.warehouses.filter((w) => w.companyId === selectedTask.value?.companyId),
);
const warehouseHint = computed(() => {
  if (!taskValue.value) return '请先选择关联任务，仓库按任务所属公司提供';
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
        inputValidator: (v) => !!v?.trim() || '请填写原因',
        confirmButtonText: '关闭剩余',
        cancelButtonText: '取消',
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
    ElMessage.success('剩余需求已关闭');
  } catch (e) {
    if (e instanceof Error) ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
let revision = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : '操作失败，请重试';
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
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      const options = await requisitionApi.choices();
      if (current !== revision) return;
      choices.value = options.data;
      const result =
        route.params.id === 'new'
          ? empty()
          : (await requisitionApi.detail(String(route.params.id))).data;
      if (current !== revision) return;
      form.value = result;
      if (route.params.id === 'new') {
        form.value.taskKind = String(route.query.taskKind || '');
        form.value.taskId = String(route.query.taskId || '');
      }
      guard.markSaved();
    } else {
      const result = await requisitionApi.list(
        applied.value,
        { ...filters.value, ...(pendingOnly.value ? { pendingOut: '1' } : {}) },
        page.value,
        size.value,
      );
      if (current !== revision) return;
      rows.value = result.data;
      total.value = result.page.total;
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
    ElMessage.warning('所选物料已在明细中，请修改原行数量');
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
    ElMessage.success('草稿已保存');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function action(type: 'submit' | 'reserve' | 'release' | 'cancel') {
  if (busy.value) return;
  if (guard.dirty.value) {
    ElMessage.warning('请先保存修改');
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
          cancelButtonText: '取消',
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
    ElMessage.success('所选库存已预留');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除这张领料草稿？', '删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
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
  <section class="requisition-page">
    <p v-if="error" class="error" role="alert">
      {{ error }} <ElButton text @click="load">重试</ElButton>
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
          >{{ selectedRequests.length > 1 ? '合并出库' : '出库' }}</ElButton
        >
        <ElButton
          v-if="!pendingOnly && auth.can('inventoryRequisition', '新增')"
          type="primary"
          @click="router.push({ name: 'inventory-requisition-detail', params: { id: 'new' } })"
          ><AppIcon name="Plus" /> 新增</ElButton
        >
        <form @submit.prevent="search">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput
            v-model="query"
            placeholder="搜索领料单、任务、申请人、仓库"
          /><ElButton text @click="reset">重置</ElButton>
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
                    :placeholder="'筛选' + fields[key]"
                    clearable
                  /></div></template
              ><template #default="{ row: row, $index: tableRowIndex }">
                <ElTag v-if="key === 'status'" :type="tone(row.status)">{{ row.status }}</ElTag
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
              ><template #header><div class="table-header-label">操作</div></template
              ><template #default="{ row: row, $index: tableRowIndex }">
                <ElButton
                  v-if="pendingOnly && auth.can('inventoryOutbound', '确认出库')"
                  link
                  type="primary"
                  :disabled="mergedBusy"
                  @click="previewMerged([row.id])"
                  >出库</ElButton
                >
                <ElButton
                  link
                  type="primary"
                  @click="
                    router.push({ name: 'inventory-requisition-detail', params: { id: row.id } })
                  "
                  >{{ row.status === '草稿' ? '编辑' : '查看' }}</ElButton
                >
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!rows.length">
                {{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无领料申请' }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer class="list-footer">
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
          form.id ? form.code : '新增领料申请'
        }}</strong
        ><ElTag :type="tone(form.status)">{{ form.status }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >关联任务<span v-if="readOnly">{{ form.taskKind }} · {{ form.taskCode }}</span
            ><ElSelect
              v-else
              v-model="taskValue"
              filterable
              placeholder="选择维修、保养或点巡检任务"
              ><ElOption
                v-for="t in choices.tasks"
                :key="t.kind + ':' + t.id"
                :value="t.kind + ':' + t.id"
                :label="t.kind + ' · ' + t.code + ' ' + t.name" /></ElSelect></label
          ><label
            >仓库<span v-if="readOnly">{{
              choices.warehouses.find((w) => w.id === form.warehouseId)?.name || form.warehouseName
            }}</span
            ><ElSelect
              v-else
              v-model="form.warehouseId"
              filterable
              :disabled="!selectedTask || !warehouses.length"
              :placeholder="!selectedTask ? '请先选择关联任务' : '请选择任务所属公司仓库'"
              ><ElOption v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name"
            /></ElSelect>
            <span v-if="!readOnly && warehouseHint" class="warehouse-hint" role="status">{{
              warehouseHint
            }}</span></label
          ><label
            >申请人<span>{{ form.applicantName || auth.currentUser }}</span></label
          ><label
            >备注<ElInput v-model="form.remark" :readonly="readOnly" maxlength="1000"
          /></label>
        </div>
        <div class="detail-heading">
          <strong>领料明细</strong
          ><ElButton
            v-if="!readOnly"
            type="primary"
            plain
            :disabled="!form.warehouseId"
            @click="add"
            ><AppIcon name="Plus" /> 新增行</ElButton
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
              ><template #header><div class="table-header-label">料号</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ line.materialCode }}</span>
                <ElButton
                  v-else
                  class="material-code-picker"
                  :disabled="!form.warehouseId"
                  @click="chooseMaterial(line)"
                  >{{ line.materialCode || material(line)?.code || '选择料号' }}</ElButton
                >
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">物料名称</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.materialName || material(line)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">规格型号</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.materialModel || material(line)?.model
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">单位</div></template
              ><template #default="{ row: line, $index: index }">{{
                line.unit || material(line)?.unit
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">申请数量</div></template
              ><template #default="{ row: line, $index: index }">
                <span v-if="readOnly">{{ number(line.quantity) }}</span
                ><AppDecimalInput
                  v-else
                  v-model="line.quantity"
                  inputmode="decimal"
                /> </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="readOnly" :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">预留数量</div></template
              ><template #default="{ row: line, $index: index }">{{
                number(line.reservedQuantity)
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn v-if="readOnly" :min-width="150" class-name="numeric"
              ><template #header><div class="table-header-label">已发数量</div></template
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
              ><template #header><div class="table-header-label">操作</div></template
              ><template #default="{ row: line, $index: index }">
                <ElButton link type="danger" @click="form.lines.splice(index, 1)">删除</ElButton>
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!form.lines.length">添加本任务需要的物料及数量</div></template
            ></AppBusinessTable
          >
        </div>
        <template v-if="form.reservations?.length"
          ><div class="detail-heading">
            <strong>预留明细</strong><span>预留数量不扣减在库数量</span>
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
                ><template #header><div class="table-header-label">物料</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.materialName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">库位</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.binName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">批次号</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.batchCode
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow3} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">预留数量</div></template
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
              issue.reversed ? '已冲销' : '已出库'
            }}</ElTag
            ><span v-if="issue.groupCode && issue.groupCode !== issue.code"
              >合并单号：{{ issue.groupCode }}</span
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
                ><template #header><div class="table-header-label">物料</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.materialName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">库位</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.binName
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">批次号</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  a.batchCode
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">发料数量</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  number(a.quantity)
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150" class-name="numeric"
                ><template #header><div class="table-header-label">金额</div></template
                ><template #default="{ row: a, $index: tableRowIndex }">{{
                  formatNumber(a.amount)
                }}</template></ElTableColumn
              ></AppBusinessTable
            >
          </div>
          <p v-if="auth.can('inventoryRequisition', '查看金额')" class="issue-total">
            合计 ¥ {{ formatNumber(issue.totalAmount) }}
          </p>
        </div>
        <div v-if="form.events?.length" class="records">
          <strong>单据记录</strong>
          <p v-for="(event, index) in form.events" :key="index">
            <span class="dot" />{{ event.actorName }} · {{ event.action
            }}<span v-if="event.reason"> · {{ event.reason }}</span
            ><time>{{ event.createdAt.replace('T', ' ').slice(0, 19) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readOnly ? '返回' : '取消' }}</ElButton
        ><template v-if="!error && !loading"
          ><ElButton v-if="!readOnly" :loading="busy" @click="save">保存草稿</ElButton
          ><ElButton
            v-if="!readOnly"
            type="primary"
            :disabled="busy || !form.id || guard.dirty.value"
            @click="action('submit')"
            >提交</ElButton
          ><ElButton v-if="canCloseRemaining && !canCancel" :disabled="busy" @click="closeRemaining"
            >关闭剩余需求</ElButton
          ><ElButton v-if="canCancel" :disabled="busy" @click="action('cancel')">取消申请</ElButton
          ><ElButton v-if="canRelease" :disabled="busy" @click="action('release')"
            >释放预留</ElButton
          ><ElButton v-if="canIssue" type="primary" :disabled="busy" @click="openIssue"
            >出库</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can('inventoryRequisition', '删除')"
            type="danger"
            plain
            :disabled="busy"
            @click="removeDraft"
            >删除</ElButton
          ><ElButton v-if="canReserve" :disabled="busy" @click="openPool">选择批次</ElButton
          ><ElButton v-if="canReserve" type="primary" :loading="busy" @click="action('reserve')">{{
            form.status === '部分预留' ? '继续预留' : '预留库存'
          }}</ElButton></template
        ></AppFormFooter
      >
    </template>
    <AppDialog
      v-model="poolOpen"
      title="选择库位与批次"
      width="min(860px,calc(100vw - 32px))"
      :busy="busy"
    >
      <p class="issue-hint">填写本次预留数量；留为 0 的行不预留。也可关闭窗口后使用自动预留。</p>
      <div class="line-scroll">
        <AppBusinessTable
          :rows="poolRows"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="issue-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">物料</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.materialName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">库位</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.binName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">批次</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.batchCode
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">有效期</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              row.expiryDate
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">可用数量</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">{{
              number(row.availableQuantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">预留数量</div></template
            ><template #default="{ row: row, $index: tableRowIndex }"
              ><AppDecimalInput
                v-model="row.quantity"
                inputmode="decimal" /></template></ElTableColumn
          ><template #empty
            ><div v-if="!poolRows.length">暂无可用库存</div></template
          ></AppBusinessTable
        >
      </div>
      <template #footer
        ><ElButton :disabled="busy" @click="poolOpen = false">取消</ElButton
        ><ElButton
          type="primary"
          :loading="busy"
          :disabled="!poolRows.some((v) => Number(v.quantity) > 0)"
          @click="manualReserve"
          >预留</ElButton
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
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
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
  height: 46px;
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
.material-code-picker {
  width: 100%;
  justify-content: space-between;
}
</style>
