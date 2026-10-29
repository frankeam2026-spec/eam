<script setup lang="ts">
import { isDepartmentCustomColumn } from '../businessConfig';
import DepartmentValuesText from './common/DepartmentValuesText.vue';
function departmentCustomIds(fields: unknown, key: string) {
  return fields && typeof fields === 'object' ? Reflect.get(fields, key.slice(7)) : undefined;
}

import DepartmentText from './common/DepartmentText.vue';
import { enumLabel } from '../i18n';
import { tr } from '../i18n';
import { normalizePageSize } from '../utils/pagination';

import DialogChoiceInput from './common/DialogChoiceInput.vue';
import PagedChoiceField from './common/PagedChoiceField.vue';
import { mergeOptions, type SelectorRow, type SelectorPage } from '../selectors';

import { onUnmounted } from 'vue';
import { useOutboundList } from '../composables/inventory/useInventoryLists';
import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
const extensionInitializationClean = ref(true);
import { useBusinessColumns } from '../composables/useBusinessColumns';
import { customCell } from '../businessConfig';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
const vLoading = ElLoading.directive;
import AppIcon from './common/AppIcon.vue';
import MaterialPicker from './common/MaterialPicker.vue';
import type { InventoryMaster } from '../inventory';
import AppDecimalInput from './common/AppDecimalInput.vue';
import AppListTabs from './common/AppListTabs.vue';
import { formatNumber } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ElLoading,
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTag,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import { request } from '../api';
import { useAuthStore } from '../stores/auth';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppPagination from './common/AppPagination.vue';
import AppDialog from './common/AppDialog.vue';
import OutboundPrintDialog from './common/OutboundPrintDialog.vue';
import RowMoreActions from './common/RowMoreActions.vue';
import TreeSelect from './TreeSelect.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import { formatDateTime } from '../utils/dateTime';

type Choice = {
  id: string;
  name: string;
  code?: string;
  companyId?: string;
  companyName?: string;
  parentId?: string;
  orgId?: string;
  kind?: string;
  orgType?: string;
  unit?: string;
  model?: string;
};
type Selection = { balanceId: string; quantity: string };
type Line = {
  id?: string;
  materialId: string;
  materialName?: string;
  materialCode?: string;
  unit?: string;
  quantity: string;
  requestedQuantity?: string;
  issuedQuantity?: string;
  reservedQuantity?: string;
  remainingQuantity?: string;
  requisitionId?: string;
  requisitionLineId?: string;
  requisitionCode?: string;
  taskKind?: string;
  taskId?: string;
  taskCode?: string;
  recipientName?: string;
  departmentName?: string;
  selections: Selection[];
};
type Allocation = {
  id?: string;
  materialName: string;
  binName: string;
  batchCode?: string;
  expiryDate?: string;
  quantity: string;
  unitCost?: string;
  amount?: string;
  returnedQuantity?: string;
  usedQuantity?: string;
  costLayerId?: string;
  receiptId?: string;
  requisitionCode?: string;
  taskKind?: string;
  taskId?: string;
  taskCode?: string;
  recipientName?: string;
  issueId?: string;
};
type Outbound = import('../businessConfig').CustomRecord & {
  id: string;
  code: string;
  status: string;
  sourceKind: string;
  warehouseId: string;
  warehouseName?: string;
  departmentId: string;
  recipientId: string;
  purpose: string;
  remark: string;
  taskKind: string;
  taskId: string;
  rowVersion?: number;
  totalAmount?: string;
  totalQuantity?: string;
  actorName?: string;
  createdAt?: string;
  confirmedAt?: string;
  recipientName?: string;
  taskCode?: string;
  lines: Line[];
  allocations?: Allocation[];
  events?: { action: string; actorName: string; createdAt: string }[];
  [key: string]: unknown;
};
type Preview = {
  fingerprint: string;
  rowVersion: number;
  totalAmount?: string;
  allocations: Allocation[];
  pickingSummary?: Allocation[];
};
type Pending = {
  id: string;
  code: string;
  warehouseId: string;
  warehouseName: string;
  taskCode: string;
  taskKind: string;
  applicantName: string;
  status: string;
  reservationStatus: string;
  remainingQuantity: string;
  createdAt: string;
  rowVersion: number;
  [key: string]: unknown;
};
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const empty = (): Outbound => ({
  id: '',
  code: '',
  status: '草稿',
  sourceKind: '直接出库',
  warehouseId: '',
  departmentId: '',
  recipientId: '',
  purpose: '',
  remark: '',
  taskKind: '',
  taskId: '',
  lines: [],
});
const {
  rows,
  total,
  refresh: refreshList,
  invalidate: invalidateList,
} = useOutboundList<Outbound | Pending>();
const form = ref(empty()),
  choices = ref<{
    warehouses: Choice[];
    materials: Choice[];
    departments: Choice[];
    users: Choice[];
    tasks: Choice[];
  }>({ warehouses: [], materials: [], departments: [], users: [], tasks: [] });
const picking = ref(false);
const busy = ref(false),
  loading = ref(false),
  error = ref(''),
  preview = ref<Preview>(),
  previewOpen = ref(false),
  selected = ref<string[]>([]);
const editing = computed(() => !!route.params.id),
  pending = computed(() => route.query.tab === 'pending');
const can = (action: string) => auth.can('inventoryOutbound', action);
const printOpen = ref(false),
  printTarget = ref({ id: '', code: '' });
function openPrint(item: { id: string; code?: string }) {
  printTarget.value = { id: item.id, code: item.code || '' };
  printOpen.value = true;
}
const direct = computed(() => form.value.sourceKind === '直接出库');
const writable = computed(
  () =>
    form.value.status === '草稿' &&
    (direct.value ? can(form.value.id ? '编辑草稿' : '新增直接出库') : can('确认出库')),
);
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && writable.value,
  () => busy.value,
);
const q = ref(String(route.query.q || '')),
  conditions = ref<FilterCondition[]>([]),
  page = ref(1),
  size = ref(20),
  columnsOpen = ref(false),
  drawerOpen = ref(false);
const warehouse = computed(() =>
  choices.value.warehouses.find((w) => w.id === form.value.warehouseId),
);
const departments = computed(() =>
  choices.value.departments.filter(
    (d) =>
      d.companyId === warehouse.value?.companyId &&
      ['部门', 'department'].includes(d.orgType || ''),
  ),
);
const users = computed(() =>
  choices.value.users.filter((u) => u.companyId === warehouse.value?.companyId),
);
const taskPickerOptions = computed(() =>
  choices.value.tasks
    .filter((t) => t.companyId === warehouse.value?.companyId)
    .map((t) => ({ ...t, recordId: t.id, id: t.kind + ':' + t.id })),
);
async function loadTaskRows(q: string, page: number, size: number) {
  const result = await request<SelectorPage<SelectorRow>>(
    `/inventory/selectors/tasks?${new URLSearchParams({ mode: 'outbound', warehouseId: form.value.warehouseId, q, page: String(page), size: String(size) })}`,
  );
  return {
    ...result,
    data: result.data.map((t) => ({ ...t, recordId: t.id, id: t.kind + ':' + t.id })),
  };
}
function rememberTasks(rows: SelectorRow[]) {
  choices.value.tasks = mergeOptions(
    choices.value.tasks.map((t) => ({ ...t, id: t.kind + ':' + t.id })),
    rows,
  ).map((t) => ({ ...t, id: t.id.split(':')[1]! }));
}
function rememberUsers(rows: SelectorRow[]) {
  choices.value.users = mergeOptions(choices.value.users, rows);
}
function loadUserRows(q: string, page: number, size: number) {
  return request<SelectorPage<SelectorRow>>(
    `/inventory/selectors/users?${new URLSearchParams({ warehouseId: form.value.warehouseId, q, page: String(page), size: String(size) })}`,
  );
}

const taskValue = computed({
  get: () => (form.value.taskId ? form.value.taskKind + ':' + form.value.taskId : ''),
  set: (v: string) => {
    [form.value.taskKind, form.value.taskId] = v ? v.split(':') : ['', ''];
  },
});
const pendingFields: Record<string, string> = {
  code: '申请单号',
  taskKind: '来源',
  taskCode: '任务编号',
  applicantName: '申请人',
  warehouseName: '仓库',
  remainingQuantity: '剩余待发',
  status: '状态',
  reservationStatus: '预留情况',
  createdAt: '申请时间',
};
const documentFields: Record<string, string> = {
  code: '出库单号',
  sourceKind: '类型',
  warehouseName: '仓库',
  recipientName: '领用人',
  totalQuantity: '数量',
  status: '状态',
  totalAmount: '金额',
  actorName: '操作人',
  confirmedAt: '出库时间',
  createdAt: '创建时间',
};
const { definitions: customDepartmentDefinitions, extensionColumns, extensionFilters, extensionExportColumns, configurationError } =
  useBusinessColumns(() => (pending.value ? undefined : 'inventory_outbound'));
const fields = computed(() =>
  pending.value
    ? pendingFields
    : Object.fromEntries(
        Object.entries({
          ...documentFields,
          ...Object.fromEntries(extensionColumns.value.map((c) => [c.key, c.label])),
        }).filter(([k]) => k !== 'totalAmount' || can('查看金额')),
      ),
);
const columns = ref<string[]>([]);
const columnDrag = ref('');
const orderedFields = computed(() =>
  Object.fromEntries(
    [...columns.value, ...Object.keys(fields.value).filter((k) => !columns.value.includes(k))].map(
      (k) => [k, fields.value[k]],
    ),
  ),
);
function dropColumn(target: string) {
  const source = columnDrag.value;
  if (source === target || !columns.value.includes(source) || !columns.value.includes(target))
    return;
  const copy = columns.value.filter((k) => k !== source);
  copy.splice(copy.indexOf(target), 0, source);
  columns.value = copy;
  localStorage.setItem(columnKey(), JSON.stringify(copy));
  columnDrag.value = '';
}

const filterFields = computed<FilterField[]>(() =>
  Object.entries(fields.value)
    .filter(([key]) => !key.startsWith('custom:'))
    .map<FilterField>(([key, label]) => ({
      key,
      label,
      type: key.endsWith('At')
        ? 'date'
        : ['totalAmount', 'remainingQuantity', 'totalQuantity'].includes(key)
          ? 'number'
          : key === 'status'
            ? 'choice'
            : 'text',
      options:
        key === 'status'
          ? pending.value
            ? ['待出库', '部分出库']
            : ['草稿', '已出库', '已冲销']
          : undefined,
    }))
    .concat(extensionFilters.value),
);
const message = (e: unknown) => (e instanceof Error ? e.message : tr('ui.equipment.text0491'));
const number = formatNumber;
function display(r: Outbound | Pending, key: string) {
  if (key.startsWith('custom:')) return customCell(r as Outbound, key);
  return key.endsWith('At')
    ? formatDateTime(String(r[key] || ''))
    : ['totalQuantity', 'totalAmount', 'remainingQuantity'].includes(key)
      ? number(r[key])
      : String(r[key] ?? '');
}
function tone(status: string) {
  return status === '已出库' || status === '已完成'
    ? 'success'
    : status === '已冲销'
      ? 'danger'
      : status === '部分出库'
        ? 'warning'
        : 'info';
}
function columnKey() {
  return `outbound-columns:${auth.user?.id}:${pending.value ? 'pending' : 'documents'}`;
}
function restoreColumns() {
  try {
    const saved = JSON.parse(localStorage.getItem(columnKey()) || 'null');
    columns.value = Array.isArray(saved)
      ? saved.filter((k) => k in fields.value)
      : Object.keys(fields.value);
  } catch {
    columns.value = Object.keys(fields.value);
  }
  if (!columns.value.length) columns.value = Object.keys(fields.value);
}
function toggleColumn(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((k) => k !== key);
  } else columns.value.push(key);
  localStorage.setItem(columnKey(), JSON.stringify(columns.value));
}
function moveColumn(key: string, offset: number) {
  const index = columns.value.indexOf(key),
    target = index + offset;
  if (index < 0 || target < 0 || target >= columns.value.length) return;
  const copy = [...columns.value];
  [copy[index], copy[target]] = [copy[target]!, copy[index]!];
  columns.value = copy;
  localStorage.setItem(columnKey(), JSON.stringify(copy));
}
function resetColumns() {
  columns.value = Object.keys(fields.value);
  localStorage.removeItem(columnKey());
}
let revision = 0;
onUnmounted(() => {
  revision++;
  invalidateList();
});
async function load() {
  const version = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      invalidateList();
      if (can('查看')) {
        const options = await request<{ data: typeof choices.value }>(
          '/inventory/outbounds/choices?paged=true',
        );
        if (version !== revision) return;
        choices.value = options.data;
      }
      const loaded =
        route.params.id === 'new'
          ? route.query.requests
            ? (
                await request<{ data: Outbound }>('/inventory/outbounds/from-requests', {
                  method: 'POST',
                  body: JSON.stringify({ ids: String(route.query.requests).split(',') }),
                })
              ).data
            : empty()
          : (await request<{ data: Outbound }>(`/inventory/outbounds/${route.params.id}`)).data;
      if (loaded.warehouseId && can('查看')) {
        const [people, tasks] = await Promise.all([
          loaded.recipientId
            ? request<SelectorPage<SelectorRow>>(
                `/inventory/selectors/users/resolve?warehouseId=${loaded.warehouseId}`,
                { method: 'POST', body: JSON.stringify({ ids: [loaded.recipientId] }) },
              )
            : Promise.resolve({ data: [] }),
          loaded.taskId
            ? request<SelectorPage<SelectorRow>>(
                `/inventory/selectors/tasks?${new URLSearchParams({ mode: 'outbound', warehouseId: loaded.warehouseId, kind: loaded.taskKind, id: loaded.taskId, size: '1' })}`,
              )
            : Promise.resolve({ data: [] }),
        ]);
        if (version !== revision) return;
        choices.value.users = people.data;
        choices.value.tasks = tasks.data;
      }
      if (version !== revision) return;
      form.value = { ...empty(), ...loaded };
      form.value.lines.forEach((l) => (l.selections ||= []));
      guard.markSaved();
      preview.value = undefined;
    } else {
      const accepted = await refreshList(
        pending.value,
        q.value,
        JSON.stringify(conditions.value),
        page.value,
        size.value,
      );
      if (version !== revision || !accepted) return;
      selected.value = [];
    }
  } catch (e) {
    if (version === revision) error.value = message(e);
  } finally {
    if (version === revision) loading.value = false;
  }
}
async function search() {
  page.value = 1;
  await router.replace({
    name: 'inventory-outbounds',
    query: {
      tab: pending.value ? 'pending' : 'documents',
      q: q.value,
      conditions: JSON.stringify(conditions.value),
      page: String(page.value),
      size: String(size.value),
    },
  });
  await load();
}
function reset() {
  q.value = '';
  conditions.value = [];
  search();
}
function switchTab(tab: string) {
  conditions.value = [];
  page.value = 1;
  router.push({ name: 'inventory-outbounds', query: { tab } });
}
function newDirect() {
  sessionStorage.setItem('outbound-return', route.fullPath);
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { tab: 'documents' },
  });
}
function openDocument(id: string) {
  sessionStorage.setItem('outbound-return', route.fullPath);
  router.push({ name: 'inventory-outbound-detail', params: { id }, query: { tab: 'documents' } });
}
function issue(ids = selected.value) {
  sessionStorage.setItem('outbound-return', route.fullPath);
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { requests: ids.join(','), tab: 'pending' },
  });
}
function select(id: string, checked: boolean) {
  selected.value = checked
    ? [...new Set([...selected.value, id])]
    : selected.value.filter((x) => x !== id);
}
function pendingActions(r: Pending) {
  return [
    ...(can('预留') ? [{ get label() { return tr('ui.equipment.text0537'); }, run: () => requestAction(r, 'reserve') }] : []),
    ...(can('释放预留') ? [{ get label() { return tr('ui.equipment.text0538'); }, run: () => requestAction(r, 'release') }] : []),
    ...(can('关闭剩余需求')
      ? [{ get label() { return tr('ui.equipment.text0539'); }, danger: true, run: () => requestAction(r, 'close-remaining') }]
      : []),
  ];
}
function back() {
  if (
    typeof route.query.returnTo === 'string' &&
    (route.query.returnTo === '/workspace' || route.query.returnTo.startsWith('/workspace?'))
  ) {
    router.push(route.query.returnTo);
    return;
  }
  const saved = sessionStorage.getItem('outbound-return');
  router.push(
    saved || { name: 'inventory-outbounds', query: { tab: route.query.tab || 'documents' } },
  );
}
const formValidation = ref('');
watch(
  () => [
    form.value.warehouseId,
    form.value.departmentId,
    form.value.recipientId,
    form.value.lines.map((line) => line.materialId).join(','),
  ],
  () => {
    formValidation.value = '';
  },
);
const materialPickerOpen = ref(false);
const materialPickerLine = ref<Line>();
async function loadMaterialRows(q: string, page: number, size: number) {
  return request<{ data: InventoryMaster[]; page: { total: number } }>(
    `/inventory/outbounds/materials?${new URLSearchParams({ q, page: String(page), size: String(size), warehouseId: form.value.warehouseId })}`,
  );
}
function chooseMaterial(line: Line) {
  if (!form.value.warehouseId) {
    formValidation.value = tr('ui.validation.message14');
    document.getElementById('outbound-warehouse')?.focus();
    return;
  }
  materialPickerLine.value = line;
  materialPickerOpen.value = true;
}
function selectMaterials(selected: InventoryMaster[]) {
  const target = materialPickerLine.value;
  if (!target || !form.value.lines.includes(target) || !writable.value || !direct.value) return;
  const catalogue = new Map(choices.value.materials.map((m) => [m.id, m]));
  selected.forEach((m) => catalogue.set(m.id, m));
  choices.value.materials = [...catalogue.values()];
  selected.forEach((m, index) => {
    const line = index === 0 ? target : { materialId: '', quantity: '1', selections: [] };
    setMaterial(line, m.id);
    if (index > 0) form.value.lines.push(line);
  });
  materialPickerLine.value = undefined;
  formValidation.value = '';
}
function addLine() {
  form.value.lines.push({ materialId: '', quantity: '1', selections: [] });
}
function setMaterial(line: Line, id: string) {
  const m = choices.value.materials.find((x) => x.id === id);
  Object.assign(line, {
    materialId: id,
    materialName: m?.name,
    materialCode: m?.code,
    unit: m?.unit,
    selections: [],
  });
}
function warehouseChanged() {
  form.value.departmentId = '';
  form.value.recipientId = '';
  taskValue.value = '';
  form.value.lines.forEach((l) => (l.selections = []));
  preview.value = undefined;
}
async function save() {
  if (busy.value || !writable.value) return false;
  formValidation.value = '';
  if (direct.value) {
    const missing = !form.value.warehouseId
      ? [tr('ui.validation.message15'), 'outbound-warehouse']
      : !form.value.departmentId
        ? [tr('ui.validation.message16'), 'outbound-department']
        : !form.value.recipientId
          ? [tr('ui.validation.message17'), 'outbound-recipient']
          : null;
    if (missing) {
      formValidation.value = missing[0]!;
      document.getElementById(missing[1]!)?.focus();
      return false;
    }
    if (form.value.lines.some((line) => !line.materialId)) {
      formValidation.value = '请在料号列选择每行的物料';
      return false;
    }
  }
  if (!prepareExtension(form.value)) return;
  busy.value = true;
  try {
    const result = await request<{ data: Outbound }>(
      `/inventory/outbounds${form.value.id ? '/' + form.value.id : ''}`,
      { method: form.value.id ? 'PUT' : 'POST', body: JSON.stringify(form.value) },
    );
    form.value = { ...empty(), ...result.data };
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({
        name: 'inventory-outbound-detail',
        params: { id: form.value.id },
        query: route.query,
      });
    ElMessage.success(tr('ui.equipment.text0540'));
    return true;
  } catch (e) {
    if (extensionError(e)) return;
    ElMessage.error(message(e));
    return false;
  } finally {
    busy.value = false;
  }
}
async function showPreview() {
  if (busy.value || loading.value) return;
  if (guard.dirty.value || !form.value.id) {
    if (!(await save())) return;
  }
  busy.value = true;
  try {
    preview.value = (
      await request<{ data: Preview }>(`/inventory/outbounds/${form.value.id}/preview`, {
        method: 'POST',
        body: JSON.stringify({ rowVersion: form.value.rowVersion }),
      })
    ).data;
    previewOpen.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
let confirmationKey = '';
watch(preview, () => {
  confirmationKey = crypto.randomUUID();
  picking.value = !direct.value;
});
async function confirm() {
  if (!preview.value || busy.value) return;
  busy.value = true;
  try {
    form.value = (
      await request<{ data: Outbound }>(`/inventory/outbounds/${form.value.id}/confirm`, {
        method: 'POST',
        body: JSON.stringify({ ...preview.value, allocations: undefined, confirmationKey }),
      })
    ).data;
    guard.markSaved();
    previewOpen.value = false;
    ElMessage.success(tr('ui.equipment.text0541'));
  } catch (e) {
    if (extensionError(e)) return;
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除后草稿不可恢复，是否继续？', '删除草稿', {
      type: 'warning',
      get confirmButtonText() { return tr('ui.equipment.text0146'); },
      get cancelButtonText() { return tr('ui.equipment.text0157'); },
      closeOnClickModal: false,
    });
    busy.value = true;
    await request(`/inventory/outbounds/${form.value.id}`, {
      method: 'DELETE',
      body: JSON.stringify({ rowVersion: form.value.rowVersion }),
    });
    guard.markSaved();
    busy.value = false;
    back();
  } catch (e) {
    if (e instanceof Error) ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function requestAction(r: Pending, action: 'reserve' | 'release' | 'close-remaining') {
  let reason = '';
  try {
    if (action === 'close-remaining') {
      const result = await ElMessageBox.prompt(
        '关闭后释放预留，已出库记录仍保留。',
        '关闭剩余需求',
        {
          inputPlaceholder: '填写关闭原因',
          inputValidator: (v) => !!v?.trim() || tr('ui.equipment.text0461'),
          get confirmButtonText() { return tr('ui.equipment.text0542'); },
          get cancelButtonText() { return tr('ui.equipment.text0157'); },
          closeOnClickModal: false,
        },
      );
      reason = result.value;
    }
    busy.value = true;
    await request(`/inventory/requisitions/${r.id}/${action}`, {
      method: 'POST',
      body: JSON.stringify({ rowVersion: r.rowVersion, reason }),
    });
    await load();
    ElMessage.success(
      action === 'reserve' ? '预留完成' : action === 'release' ? '预留已释放' : '剩余需求已关闭',
    );
  } catch (e) {
    if (e instanceof Error) ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
const stockOpen = ref(false),
  stockLine = ref<Line>(),
  stockRows = ref<
    {
      id: string;
      binName: string;
      batchCode?: string;
      expiryDate?: string;
      availableQuantity: string;
      ownReservedQuantity?: string;
      stockStatus: string;
      binEnabled: boolean;
    }[]
  >([]),
  stockQuantities = ref<Record<string, number>>({});
async function chooseStock(line: Line) {
  if (!form.value.warehouseId || !line.materialId) {
    ElMessage.warning(tr('ui.equipment.text0543'));
    return;
  }
  try {
    stockRows.value = (
      await request<{ data: typeof stockRows.value }>(
        `/inventory/outbounds/stock?${new URLSearchParams({ warehouseId: form.value.warehouseId, materialId: line.materialId, requisitionLineId: line.requisitionLineId || '0' })}`,
      )
    ).data;
    stockLine.value = line;
    stockQuantities.value = Object.fromEntries(
      line.selections.map((s) => [s.balanceId, Number(s.quantity)]),
    );
    stockOpen.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  }
}
function applyStock() {
  const selections = Object.entries(stockQuantities.value)
    .filter(([, qty]) => qty > 0)
    .map(([balanceId, qty]) => ({ balanceId, quantity: String(qty) }));
  if (
    selections.length &&
    Math.abs(
      selections.reduce((sum, s) => sum + Number(s.quantity), 0) -
        Number(stockLine.value?.quantity),
    ) > 0.0000001
  ) {
    ElMessage.warning(tr('ui.equipment.text0544'));
    return;
  }
  if (stockLine.value) stockLine.value.selections = selections;
  stockOpen.value = false;
}
async function exportRows() {
  try {
    const params = new URLSearchParams({
      q: q.value,
      conditions: JSON.stringify(conditions.value),
    });
    const result = await request<{ data: (Outbound | Pending)[] }>(
      `/inventory/outbounds/${pending.value ? 'pending-export' : 'export'}?${params}`,
    );
    const exportFields = {
      ...Object.fromEntries(
        columns.value.filter((k) => !k.startsWith('custom:')).map((k) => [k, fields.value[k]]),
      ),
      ...Object.fromEntries(
        pending.value ? [] : extensionExportColumns.value.map((c) => [c.key, c.label]),
      ),
    };
    const exportKeys = Object.keys(exportFields);
    const quote = (v: string) =>
      '"' + (/^[=+@\-]/.test(v) ? "'" : '') + v.replaceAll('"', '""') + '"';
    const csv = [
      exportKeys.map((k) => quote(exportFields[k]!)).join(','),
      ...result.data.map((r) => exportKeys.map((k) => quote(display(r, k))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(new Blob(['\uFEFF' + csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = pending.value ? '待出库.csv' : '出库单.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  }
}
function taskLink(kind?: string, id?: string) {
  if (!id) return;
  router.push(
    kind === '维修'
      ? `/repairs/${id}`
      : kind === '保养'
        ? `/maintenance/task/${id}`
        : `/inspection/task/${id}`,
  );
}
function childReversed(id?: string) {
  return ((form.value.children || []) as { id: string; reversed: unknown }[]).some(
    (c) => c.id === id && Number(c.reversed) === 1,
  );
}
function correction(kind: 'returns' | 'reversals') {
  router.push({
    name: 'inventory-operation-detail',
    params: { kind, id: 'new' },
    query: { issueId: form.value.id },
  });
}
watch(
  () => [route.fullPath, route.params.id] as const,
  ([, recordId], previous) => {
    // Saving a new draft already returned its complete detail. Loading it again races
    // with the preview request and clears the newly returned allocations.
    if (previous?.[1] === 'new' && recordId !== 'new' && String(recordId) === form.value.id) return;
    if (!editing.value) {
      try {
        q.value = String(route.query.q || '');
        conditions.value = JSON.parse(String(route.query.conditions || '[]'));
        const legacy: Record<string, string> = JSON.parse(String(route.query.filters || '{}'));
        const appliedFields = new Set(conditions.value.map((c) => c.field));
        conditions.value.push(
          ...Object.entries(legacy)
            .filter(([field, value]) => value && !appliedFields.has(field))
            .map(([field, value]) => ({ field, operator: 'contains' as const, values: [value] })),
        );

        page.value = Number(route.query.page || 1);
        size.value = normalizePageSize(route.query.size);
      } catch {
        conditions.value = [];
      }
      restoreColumns();
    }
    load();
  },
  { immediate: true },
);
watch([page, size], () => {
  if (!editing.value)
    router.replace({
      query: { ...route.query, page: String(page.value), size: String(size.value) },
    });
});
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => rows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => form.value.lines;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => form.value.allocations;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows4 = () =>
  picking.value ? preview.value?.pickingSummary : preview.value?.allocations;
type TableColumnRow4 = NonNullable<ReturnType<typeof tableColumnRows4>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows5 = () => stockRows.value;
type TableColumnRow5 = NonNullable<ReturnType<typeof tableColumnRows5>>[number];
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    drawerOpen,
    () => conditions.value,
    (value) => {
      conditions.value = value;
      selected.value = [];
      search();
    },
  );
</script>

<template>
  <section class="content outbound-page" v-loading="loading">
    <div v-if="error" class="outbound-error">
      {{ error }} <ElButton @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </div>
    <p v-if="configurationError" class="outbound-error" role="alert">{{ configurationError }}</p>
    <template v-if="!editing">
      <AppListTabs
        :active="pending ? 'pending' : 'documents'"
        :items="[
          { key: 'documents', label: '出库单' },
          { key: 'pending', label: '待出库' },
        ]"
        @select="switchTab"
      />
      <AppListToolbar class="outbound-toolbar">
        <div class="outbound-actions">
          <ElButton type="primary" v-if="can('新增直接出库')" :title="tr('ui.equipment.text0545')" @click="newDirect"
            ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
          ><ElButton
            v-if="pending && can('确认出库')"
            :disabled="!selected.length"
            @click="issue()"
            >{{ selected.length > 1 ? tr('ui.equipment.text0546') : tr('ui.equipment.text0504') }}</ElButton
          >
        </div>
        <form class="outbound-query" @submit.prevent="search">
          <ListSearchInput
            v-model="q"
            :placeholder="
              pending ? tr('ui.equipment.text0547') : tr('ui.equipment.text0548')
            "
          /><ElButton @click="showFilter($event.currentTarget as HTMLElement)"
            >{{ tr('ui.equipment.text0203') }}<span v-if="conditions.length">（{{ conditions.length }}）</span></ElButton
          ><ElButton text @click="reset">{{ tr('ui.equipment.text0204') }}</ElButton>
        </form>
      </AppListToolbar>
      <AppliedFilterChips
        editable
        @edit="(condition, anchor) => showFilter(anchor, condition.field)"
        :conditions="conditions"
        :fields="filterFields"
        @change="replaceFilters"
      />
      <div class="outbound-list">
        <div class="outbound-table-scroll">
          <AppBusinessTable
            v-model:selection-ids="selected"
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            :selectable="pending"
            :rows="rows"
            :pagination="false"
            :fill="true"
            :framed="false"
            class="business-table outbound-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="key in columns"
              :min-width="150"
              :prop="String(key)"
              :key="String(key)"
              ><template #header
                ><div class="table-header-label">
                  {{ fields[key] }}
                </div> </template
              ><template #default="{ row: r, $index: tableRowIndex }">
                <ElButton
                  v-if="key === 'code'"
                  link
                  type="primary"
                  @click="
                    pending ? router.push(`/inventory/requisitions/${r.id}`) : openDocument(r.id)
                  "
                  >{{ r.code }}</ElButton
                ><ElTag v-else-if="key === 'status'" :type="tone(r.status)">{{ enumLabel('status', r.status) }}</ElTag
                ><DepartmentValuesText v-else-if="isDepartmentCustomColumn(customDepartmentDefinitions, key)" :value="String(display(r, key))" :ids="departmentCustomIds(r.customFields, key)" /><span v-else>{{ display(r, key) }}</span>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="outbound-operation"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
              ><template #default="{ row: r, $index: tableRowIndex }">
                <template v-if="pending"
                  ><ElButton v-if="can('确认出库')" link type="primary" @click="issue([r.id])"
                    >{{ tr('ui.equipment.text0504') }}</ElButton
                  ><ElButton
                    link
                    type="primary"
                    @click="router.push(`/inventory/requisitions/${r.id}`)"
                    >{{ tr('ui.equipment.text0206') }}</ElButton
                  ><RowMoreActions :actions="pendingActions(r as Pending)" /></template
                ><template v-else
                  ><ElButton link type="primary" @click="openDocument(r.id)">{{ tr('ui.equipment.text0206') }}</ElButton
                  ><ElButton
                    v-if="r.status === '草稿' && can('编辑草稿')"
                    link
                    type="primary"
                    @click="openDocument(r.id)"
                    >{{ tr('ui.equipment.text0207') }}</ElButton
                  >
                  <ElButton
                    v-if="can('打印') && (r.status !== '草稿' || !can('编辑草稿'))"
                    link
                    type="primary"
                    @click="openPrint(r)"
                    >{{ tr('ui.equipment.text0549') }}</ElButton
                  >
                  <RowMoreActions
                    v-if="can('打印') && r.status === '草稿' && can('编辑草稿')"
                    :actions="[{ label: '打印', run: () => openPrint(r) }]"
                  />
                </template> </template></ElTableColumn
            ><template #empty
              ><div v-if="!rows.length">
                {{ q || conditions.length ? tr('ui.equipment.text0550') : tr('ui.equipment.text0308') }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer class="outbound-list-footer">
          <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
          ><AppPagination
            v-model:page="page"
            v-model:page-size="size"
            :total="total"
          /><ListToolbarIcons
            mode="footer"
            :filter-open="false"
            :columns-open="columnsOpen"
            :can-export="can('导出')"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><ElCheckbox
                v-for="(label, key) in orderedFields"
                draggable="true"
                @dragstart="columnDrag = String(key)"
                @dragover.prevent
                @drop.prevent="dropColumn(String(key))"
                :key="key"
                :model-value="columns.includes(String(key))"
                @change="toggleColumn(String(key))"
                >{{ label
                }}<ElButton
                  v-if="columns.includes(String(key))"
                  text
                  :aria-label="tr('ui.equipment.text0551')"
                  @click.prevent="moveColumn(String(key), -1)"
                  ><AppIcon name="ArrowUp" /></ElButton
                ><ElButton
                  v-if="columns.includes(String(key))"
                  text
                  :aria-label="tr('ui.equipment.text0552')"
                  @click.prevent="moveColumn(String(key), 1)"
                  ><AppIcon name="ArrowDown" /></ElButton></ElCheckbox
              ><ElButton text @click="resetColumns">{{ tr('ui.equipment.text0553') }}</ElButton></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
      <FilterConditionPopover
        :anchor="filterAnchor"
        :field-key="editingFilter"
        :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
        v-model="drawerOpen"
        :fields="filterFields"
        :conditions="conditions"
        @confirm="confirmFilter"
      />
    </template>
    <template v-else>
      <header class="outbound-detail-header">
        <div>
          <AppBackButton :label="tr('ui.equipment.text0554')" :disabled="busy" @click="back" />
          <h2>{{ form.code || (direct ? tr('ui.equipment.text0555') : tr('ui.equipment.text0556')) }}</h2>
          <ElTag :type="tone(form.status)">{{ enumLabel('status',form.status) }}</ElTag
          ><span>{{ form.sourceKind }}</span>
        </div>
        <div class="outbound-actions">
          <ElButton
            v-if="form.id && can('打印')"
            :disabled="busy || guard.dirty.value"
            :title="guard.dirty.value ? tr('ui.equipment.text0557') : tr('ui.equipment.text0558')"
            @click="openPrint(form)"
            >{{ tr('ui.equipment.text0549') }}</ElButton
          >

          <ElButton
            v-if="form.id && writable && can('删除草稿')"
            type="danger"
            text
            @click="removeDraft"
            >{{ tr('ui.equipment.text0146') }}</ElButton
          ><ElButton
            v-if="form.status === '已出库' && auth.can('inventoryReturn', '新增')"
            @click="correction('returns')"
            >{{ tr('ui.equipment.text0505') }}</ElButton
          ><ElButton
            v-if="
              form.status === '已出库' &&
              !form.id.startsWith('g') &&
              auth.can('inventoryReversal', '新增')
            "
            @click="correction('reversals')"
            >{{ tr('ui.equipment.text0559') }}</ElButton
          >
        </div>
      </header>
      <div class="outbound-detail-body">
        <main class="outbound-main">
          <section class="outbound-panel">
            <h3>{{ tr('ui.equipment.text0560') }}</h3>
            <p v-if="formValidation" class="outbound-validation" role="alert">
              {{ formValidation }}
            </p>
            <div class="outbound-form-grid">
              <label
                >{{ tr('ui.equipment.text0499') }}<ElSelect
                  v-if="writable && direct"
                  id="outbound-warehouse"
                  v-model="form.warehouseId"
                  filterable
                  @change="warehouseChanged"
                  ><ElOption
                    v-for="w in choices.warehouses"
                    :key="w.id"
                    :label="`${w.companyName || ''} / ${w.name}`"
                    :value="w.id" /></ElSelect
                ><span v-else>{{ form.warehouseName || warehouse?.name }}</span></label
              ><label
                >{{ tr('ui.equipment.text0561') }}<span>{{ form.companyName || warehouse?.companyName }}</span></label
              ><template v-if="direct"
                ><label
                  ><span>{{ tr('ui.equipment.text0562') }} <i v-if="writable" class="outbound-required">*</i></span
                  ><TreeSelect
                    id="outbound-department"
                    v-if="writable"
                    :disabled="busy || !form.warehouseId"
                    v-model="form.departmentId"
                    :nodes="
                      departments.map((d) => ({
                        id: d.id,
                        name: d.name,
                        parentId: d.parentId || '',
                        value: d.id,
                      }))
                    "
                    :placeholder="tr('ui.equipment.text0563')"
                  /><DepartmentText v-else :value="String(form.departmentName || '')" :department-id="form.departmentId" :nodes="choices.departments.map((d) => ({ ...d, id: String(d.id), name: String(d.name), parentId: String(d.parentId || '') }))" /></label
                ><label
                  ><span>{{ tr('ui.equipment.text0564') }} <i v-if="writable" class="outbound-required">*</i></span>
                  <PagedChoiceField
                    id="outbound-recipient"
                    v-if="writable"
                    v-model="form.recipientId"
                    :options="users"
                    :load-rows="loadUserRows"
                    :context="form.warehouseId"
                    :disabled="busy || !form.warehouseId"
                    clearable
                    :title="tr('ui.equipment.text0565')"
                    :placeholder="tr('ui.equipment.text0565')"
                    @select="rememberUsers"
                  /><span v-else>{{
                    form.recipientName || choices.users.find((u) => u.id === form.recipientId)?.name
                  }}</span></label
                ><label
                  >{{ tr('ui.equipment.text0566') }}<PagedChoiceField
                    v-if="writable"
                    v-model="taskValue"
                    :options="taskPickerOptions"
                    :load-rows="loadTaskRows"
                    :columns="[
                      { key: 'kind', label: '类型', minWidth: 90 },
                      { key: 'code', label: '任务编号', minWidth: 190 },
                      { key: 'name', label: '设备／任务名称', minWidth: 220 },
                    ]"
                    :context="form.warehouseId"
                    :disabled="busy || !form.warehouseId"
                    clearable
                    :title="tr('ui.equipment.text0567')"
                    :placeholder="tr('ui.equipment.text0568')"
                    @select="rememberTasks"
                  /><ElButton
                    v-else
                    link
                    type="primary"
                    @click="taskLink(form.taskKind, form.taskId)"
                    >{{
                      form.taskCode || choices.tasks.find((t) => t.id === form.taskId)?.code
                    }}</ElButton
                  ></label
                ><label class="wide"
                  >{{ tr('ui.equipment.text0478') }}<ElInput
                    v-if="writable"
                    v-model="form.purpose"
                    maxlength="1000"
                    :placeholder="tr('ui.equipment.text0569')"
                  /><span v-else>{{ form.purpose }}</span></label
                ></template
              ><label class="wide"
                >{{ tr('ui.equipment.text0130') }}<ElInput
                  v-if="writable"
                  v-model="form.remark"
                  type="textarea"
                  :rows="2"
                  maxlength="1000"
                /><span v-else>{{ form.remark }}</span></label
              >
            </div>
          </section>
          <section v-if="form.lines.length || writable" class="outbound-panel">
            <div class="outbound-section-heading">
              <h3>{{ writable ? tr('ui.equipment.text0570') : tr('ui.equipment.text0571') }}</h3>
              <ElButton v-if="writable && direct" @click="addLine"
                ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0572') }}</ElButton
              >
            </div>
            <div class="outbound-line-scroll">
              <AppBusinessTable
                :rows="form.lines"
                :pagination="false"
                :fill="false"
                :framed="false"
                class="business-table outbound-line-table"
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn v-if="!direct" :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0573') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    <ElButton
                      link
                      type="primary"
                      @click="router.push(`/inventory/requisitions/${line.requisitionId}`)"
                      >{{ line.requisitionCode }}</ElButton
                    >
                    <div>
                      <ElButton link type="primary" @click="taskLink(line.taskKind, line.taskId)">{{
                        line.taskCode
                      }}</ElButton>
                    </div>
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" class-name="outbound-material-code"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0154') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    <DialogChoiceInput
                      v-if="writable && direct"
                      :model-value="line.materialCode"
                      :placeholder="tr('ui.equipment.text0574')"
                      :disabled="!form.warehouseId"
                      :title="form.warehouseId ? tr('ui.equipment.text0575') : tr('ui.equipment.text0576')"
                      @open="chooseMaterial(line)"
                    />
                    <span v-else>{{ line.materialCode }}</span>
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="210"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0155') }}</div></template
                  ><template #default="{ row: line, $index: i }">{{
                    line.materialName
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0095') }}</div></template
                  ><template #default="{ row: line, $index: i }">{{
                    line.unit
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn v-if="!direct" :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0577') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    {{ number(line.requestedQuantity) }} / {{ number(line.issuedQuantity) }}
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn v-if="!direct" :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0578') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    {{
                      number(
                        line.remainingQuantity ??
                          Number(line.requestedQuantity) - Number(line.issuedQuantity),
                      )
                    }}
                    / {{ number(line.reservedQuantity) }}
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0579') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    <AppDecimalInput
                      v-if="writable"
                      :model-value="line.quantity"
                      :min="0"
                      @update:model-value="
                        line.quantity = String($event ?? 0);
                        line.selections = [];
                      "
                    /><span v-else>{{ number(line.quantity) }}</span>
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn v-if="!direct" :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0564') }}</div></template
                  ><template #default="{ row: line, $index: i }">{{
                    line.recipientName
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  v-if="writable"
                  class-name="outbound-operation"
                  fixed="right"
                  :width="170"
                  :min-width="156"
                  :resizable="false"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                  ><template #default="{ row: line, $index: i }">
                    <ElButton link type="primary" @click="chooseStock(line)">{{
                      line.selections.length ? tr('ui.equipment.text0580') : tr('ui.equipment.text0581')
                    }}</ElButton
                    ><ElButton v-if="direct" link type="danger" @click="form.lines.splice(i, 1)"
                      >{{ tr('ui.equipment.text0151') }}</ElButton
                    >
                  </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.lines.length">
                    {{ writable && direct ? tr('ui.equipment.text0582') : tr('ui.equipment.text0583') }}
                  </div></template
                ></AppBusinessTable
              >
            </div>
          </section>
          <section v-if="form.allocations?.length" class="outbound-panel">
            <h3>{{ tr('ui.equipment.text0584') }}</h3>
            <div class="outbound-line-scroll">
              <AppBusinessTable
                :rows="form.allocations"
                :pagination="false"
                :fill="false"
                :framed="false"
                class="business-table outbound-allocation-table"
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
                  ><template #default="{ row: a, $index: i }">{{
                    a.materialName
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
                  ><template #default="{ row: a, $index: i }">{{
                    a.binName
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0585') }}</div></template
                  ><template #default="{ row: a, $index: i }">
                    {{ a.batchCode }}
                    <div>{{ a.expiryDate }}</div>
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0586') }}</div></template
                  ><template #default="{ row: a, $index: i }">
                    {{ a.requisitionCode }}
                    <div v-if="form.id.startsWith('g')">
                      <ElButton link type="primary" @click="openDocument(a.issueId || '')"
                        >{{ tr('ui.equipment.text0587') }}</ElButton
                      ><ElTag v-if="childReversed(a.issueId)" type="danger">{{ tr('ui.equipment.text0588') }}</ElTag>
                    </div>
                    <div>
                      {{ a.recipientName }}
                      <ElButton link type="primary" @click="taskLink(a.taskKind, a.taskId)">{{
                        a.taskCode
                      }}</ElButton>
                    </div>
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
                  ><template #default="{ row: a, $index: i }">{{
                    number(a.quantity)
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0589') }}</div></template
                  ><template #default="{ row: a, $index: i }">
                    {{ number(a.usedQuantity) }} / {{ number(a.returnedQuantity) }}
                  </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn v-if="can('查看金额')" :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0590') }}</div></template
                  ><template #default="{ row: a, $index: i }">{{
                    number(a.unitCost)
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn v-if="can('查看金额')" :min-width="150" class-name="numeric"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0591') }}</div></template
                  ><template #default="{ row: a, $index: i }">{{
                    number(a.amount)
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0592') }}</div></template
                  ><template #default="{ row: a, $index: i }">
                    <ElButton
                      v-if="a.receiptId"
                      link
                      type="primary"
                      @click="router.push(`/inventory/receipts/${a.receiptId}`)"
                      >{{ tr('ui.equipment.text0593') }}</ElButton
                    ><span v-else>{{ tr('ui.equipment.text0594') }} {{ a.costLayerId }}</span>
                  </template></ElTableColumn
                ></AppBusinessTable
              >
            </div>
            <div v-if="can('查看金额')" class="outbound-total">
              {{ tr('ui.equipment.text0595') }}{{ number(form.totalAmount) }}
            </div>
          </section>
          <BusinessFields
            ref="extension"
            v-model="form"
            @initialize="extensionInitializationClean = !guard.dirty.value"
            @ready="extensionInitializationClean && guard.markSaved()"
            object="inventory_outbound"
            :readonly="!writable"
            :is-new="!form.id"
            :errors="extensionErrors"
          />
        </main>
      </div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ writable ? tr('ui.equipment.text0157') : tr('ui.equipment.text0208') }}</ElButton
        ><ElButton v-if="writable" :loading="busy" @click="save">{{ tr('ui.equipment.text0159') }}</ElButton
        ><ElButton
          v-if="writable && can('确认出库')"
          type="primary"
          :loading="busy"
          @click="showPreview"
          >{{ tr('ui.equipment.text0596') }}</ElButton
        ></AppFormFooter
      >
    </template>
    <AppDialog v-model="previewOpen" :title="tr('ui.equipment.text0597')" width="960px" :busy="busy"
      ><p>{{ tr('ui.equipment.text0598') }}</p>
      <div class="outbound-actions">
        <ElButton :type="picking ? 'primary' : 'default'" @click="picking = true">{{ tr('ui.equipment.text0599') }}</ElButton
        ><ElButton :type="!picking ? 'primary' : 'default'" @click="picking = false"
          >{{ tr('ui.equipment.text0600') }}</ElButton
        >
      </div>
      <div class="outbound-line-scroll">
        <AppBusinessTable
          :rows="picking ? preview?.pickingSummary : preview?.allocations"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn v-if="!picking" :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0586') }}</div></template
            ><template #default="{ row: a, $index: i }">
              {{ a.requisitionCode }}
              <div>{{ a.recipientName }} · {{ a.taskCode }}</div>
            </template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0515') }}</div></template
            ><template #default="{ row: a, $index: i }">{{
              a.materialName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
            ><template #default="{ row: a, $index: i }">{{ a.binName }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0601') }}</div></template
            ><template #default="{ row: a, $index: i }">{{ a.batchCode }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0602') }}</div></template
            ><template #default="{ row: a, $index: i }">{{ a.expiryDate }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn :min-width="150" class-name="numeric"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
            ><template #default="{ row: a, $index: i }">{{
              number(a.quantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn v-if="can('查看金额')" :min-width="150" class-name="numeric"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0590') }}</div></template
            ><template #default="{ row: a, $index: i }">{{
              number(a.unitCost)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow4} -->
          <ElTableColumn v-if="can('查看金额')" :min-width="150" class-name="numeric"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0591') }}</div></template
            ><template #default="{ row: a, $index: i }">{{
              number(a.amount)
            }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </div>
      <div v-if="can('查看金额')" class="outbound-total">
        {{ tr('ui.equipment.text0595') }}{{ number(preview?.totalAmount) }}
      </div>
      <template #footer
        ><ElButton :disabled="busy" @click="previewOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="confirm">{{ tr('ui.equipment.text0597') }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="stockOpen" :title="tr('ui.equipment.text0603')" width="780px"
      ><p>{{ tr('ui.equipment.text0579') }} {{ number(stockLine?.quantity) }}{{ tr('ui.equipment.text0604') }}</p>
      <div class="outbound-line-scroll">
        <AppBusinessTable
          :rows="stockRows"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0467') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">{{
              s.binName
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0601') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">{{
              s.batchCode
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0602') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">{{
              s.expiryDate
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150" class-name="numeric"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0605') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">{{
              number(s.availableQuantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150" class-name="numeric"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0606') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">{{
              number(s.ownReservedQuantity)
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0607') }}</div></template
            ><template #default="{ row: s, $index: tableRowIndex }">
              <AppDecimalInput
                :model-value="stockQuantities[s.id]"
                @update:model-value="stockQuantities[s.id] = Number($event)"
                :min="0"
              /> </template></ElTableColumn
          ><template #empty
            ><div v-if="!stockRows.length">{{ tr('ui.equipment.text0608') }}</div></template
          ></AppBusinessTable
        >
      </div>
      <template #footer
        ><ElButton @click="stockOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" @click="applyStock">{{ tr('ui.equipment.text0609') }}</ElButton></template
      ></AppDialog
    >
    <MaterialPicker
      v-model="materialPickerOpen"
      :load-rows="loadMaterialRows"
      show-stock
      :stock-context="warehouse?.name || ''"
      multiple
      @select="selectMaterials"
    />
  </section>
  <OutboundPrintDialog
    v-model="printOpen"
    :document-id="printTarget.id"
    :document-code="printTarget.code"
  />
</template>

<style scoped>
.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.outbound-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  box-sizing: border-box;
  min-height: 0;
  font-size: 13px;
  color: var(--el-text-color-primary);
  /* gap: 8px; */
}
.outbound-query,
.outbound-actions,
.outbound-detail-header,
.outbound-section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}
.outbound-detail-header,
.outbound-section-heading {
  justify-content: space-between;
  flex: none;
  margin-bottom: 0;
}
.outbound-query {
  margin-left: auto;
}
.outbound-list {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  overflow: hidden;
}
.outbound-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.outbound-table {
  min-width: 1180px;
}
.outbound-list-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  min-height: var(--list-footer-height);
  padding: 0 12px;
  border-top: 1px solid var(--el-border-color-light);
}
.outbound-operation {
  position: sticky;
  right: 0;
  background: var(--el-bg-color);
  min-width: 180px;
  width: 180px;
  white-space: nowrap;
  z-index: 2;
}
.outbound-table :deep(thead .outbound-operation) {
  background: var(--el-fill-color-light);
  z-index: 4;
}
.outbound-operation :deep(.el-button + .el-button) {
  margin-left: 8px;
}
.outbound-operation details {
  display: inline-block;
  margin-left: 8px;
}
.outbound-operation summary {
  cursor: pointer;
  list-style: none;
  color: var(--el-color-primary);
}
.outbound-detail-header {
  padding: 4px 0 12px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.outbound-detail-header > div:first-child {
  display: flex;
  align-items: center;
  gap: 12px;
}
.outbound-detail-header h2 {
  font-size: 16px;
  margin: 0;
}
.outbound-detail-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: 16px;
  padding-bottom: 16px;
}
.outbound-main {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.outbound-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  padding: 16px;
}
.outbound-panel h3 {
  font-size: 13px;
  margin: 0 0 14px;
}
.outbound-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.outbound-form-grid label {
  display: grid;
  grid-template-columns: 80px minmax(0, 1fr);
  align-items: center;
  gap: 10px;
}
.outbound-form-grid .wide {
  grid-column: 1/-1;
}
.outbound-line-scroll {
  overflow: auto;
}
.outbound-line-table {
  min-width: 800px;
}
.outbound-allocation-table {
  min-width: 960px;
}
.outbound-material-code {
  min-width: 220px;
}
.outbound-required,
.outbound-validation {
  color: var(--el-color-danger);
}
.outbound-required {
  font-style: normal;
}
.outbound-validation {
  margin: 0 0 12px;
}
.outbound-total {
  text-align: right;
  margin-top: 12px;
  font-weight: 700;
}
.outbound-error {
  padding: 12px;
  background: var(--el-color-danger-light-9);
  color: var(--el-color-danger);
}
@media (max-width: 1280px) {
  .outbound-query {
    flex-wrap: wrap;
  }
}
@media (max-width: 1024px) {
  .outbound-form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
