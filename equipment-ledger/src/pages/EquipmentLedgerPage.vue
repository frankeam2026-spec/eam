<script setup lang="ts">
import { isDepartmentCustomColumn } from '../businessConfig';
import DepartmentValuesText from '../components/common/DepartmentValuesText.vue';
import { tr, enumLabel } from '../i18n';

import {
  businessApi,
  customColumns,
  customFilters,
  customCell,
  type BusinessField,
} from '../businessConfig';
const customDefinitions = ref<BusinessField[]>([]);
const serverTotal = ref(0);
const customInitializationClean = ref(true);

import EquipmentImportDialog from '../components/EquipmentImportDialog.vue';
import AppListTabs from '../components/common/AppListTabs.vue';
import AppListToolbar from '../components/common/AppListToolbar.vue';
import 'element-plus/es/components/tabs/style/css';
import 'element-plus/es/components/checkbox/style/css';
import type { BusinessSort } from '../components/common/BusinessTable.types';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppIcon from '../components/common/AppIcon.vue';
import { formatNumber } from '../utils/number';
import { normalizeLocationPath } from '../utils/locationPath';
import DepartmentText from '../components/common/DepartmentText.vue';
import { EQUIPMENT_PAGE_SIZES, useEquipmentPageSize } from '../composables/equipment/useEquipmentPageSize';
import 'element-plus/es/components/tooltip/style/css';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import FilterConditionPopover from '../components/common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from '../components/common/AppliedFilterChips.vue';
import { matchesAdvanced } from '../query/advancedFilters';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import ListSearchInput from '../components/common/ListSearchInput.vue';
import AppPagination from '../components/common/AppPagination.vue';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import {
  ElButton,
  ElTag,
  ElCheckbox,
  ElMessage,
  ElTableColumn,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElInput,
  ElSelect,
  ElOption,
} from 'element-plus';
import AppDialog from '../components/common/AppDialog.vue';
import ExportFieldsDialog from '../components/common/ExportFieldsDialog.vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useBaseDataStore } from '../stores/baseData';
import * as api from '../api';
import TreeSelect, { type TreeSelectNode } from '../components/TreeSelect.vue';
import EquipmentEditor from '../components/EquipmentEditor.vue';
import { confirmAction } from '../services/dialog';
import ListToolbarIcons from '../components/ListToolbarIcons.vue';
import EquipmentHistory from '../components/EquipmentHistory.vue';
import EquipmentImage from '../components/common/EquipmentImage.vue';
import EquipmentNavigationTree, {
  type EquipmentTreeSelection,
} from '../components/common/EquipmentNavigationTree.vue';
import { EQUIPMENT_STATES, EQUIPMENT_TABS, equipmentUseStatusStyle } from '../config/equipment';

type Equipment = api.EquipmentRecord;

type Column = {
  key: string;
  label: string;
  width: number;
  required?: boolean;
  align?: 'left' | 'center' | 'right';
  resizable?: boolean;
  sortable?: boolean;
};

const rows = ref<Equipment[]>([]);

const equipmentTreeSelection = ref<EquipmentTreeSelection | null>(null);

async function selectEquipmentTree(value: EquipmentTreeSelection | null) {
  equipmentTreeSelection.value = value;
  page.value = 1;
  selected.value = [];
  await refreshEquipment();
}

const apiMode = computed(() => auth.authState === 'ready');

const auth = useAuthStore();

const baseData = useBaseDataStore();

const { currentCompany, currentDepartment, grantedActions } = storeToRefs(auth);

const { categoryData, locationData, unitData, useStateData, sourceData } = storeToRefs(baseData);

const router = useRouter();

const route = useRoute();
const importOpen = computed(
  () => route.query.import === '1' && can('equipment', '导入') && can('equipment', '新增'),
);
function openImport() {
  void router.replace({ query: { ...route.query, import: '1' } });
}
function importBatchChanged(id: string) {
  void router.replace({ query: { ...route.query, import: '1', batch: id } });
}
async function closeImport(open: boolean) {
  if (open) return;
  const query = { ...route.query };
  delete query.import;
  delete query.batch;
  await router.replace({ query });
  await refreshEquipment();
}

const historyId = computed(() =>
  route.name === 'equipment-history' ? String(route.params.id || '') : '',
);

function openHistory(row: Equipment) {
  router.push({ name: 'equipment-history', params: { id: row.id } });
}

function closeHistory() {
  router.push({ name: 'equipment' });
}

const batchOpen = ref(false);

const batchBusy = ref(false);

const batchForm = reactive({ operation: 'category', value: '', ownerIds: [] as string[] });

const equipmentEditor = ref<InstanceType<typeof EquipmentEditor>>();

const batchOptions = ref<{ departments: api.OrganizationRecord[]; users: api.SystemUserRecord[] }>({
  departments: [],
  users: [],
});

const batchLabels: Record<string, string> = {
  location: '修改位置',
  category: '修改分类',
  department: '修改部门',
  owner: '设置负责人',
  state: '修改状态',
  delete: '删除',
};

const batchDepartmentNodes = computed(() =>
  batchOptions.value.departments.map((x) => ({
    ...x,
    value: x.id,
    disabled: x.disabled || x.type !== '部门',
  })),
);

async function openBatch(operation: string) {
  batchForm.operation = operation;
  batchForm.value = '';
  batchForm.ownerIds = [];
  if (operation === 'delete') {
    await removeSelected();
    return;
  }
  try {
    if (operation === 'department' || operation === 'owner')
      batchOptions.value = await api.equipmentBatchOptions();
    batchOpen.value = true;
  } catch (e) {
    notify(e instanceof Error ? e.message : tr('ui.equipment.text0160'), 'error');
  }
}

async function submitBatch() {
  if ((batchForm.operation !== 'owner' && !batchForm.value) || batchBusy.value) return;
  batchBusy.value = true;
  try {
    const result = await api.batchEquipment(
      [...selected.value],
      batchForm.operation,
      batchForm.value,
      batchForm.operation === 'owner' ? batchForm.ownerIds : undefined,
    );
    batchOpen.value = false;
    selected.value = [];
    await refreshEquipment();
    notify(`已修改 ${result.data.count} 台设备`);
  } catch (e) {
    notify(e instanceof Error ? e.message : tr('ui.equipment.text1665'), 'error');
  } finally {
    batchBusy.value = false;
  }
}

function can(menu: string, action = '查看') {
  return grantedActions.value[menu]?.includes(action) === true;
}

const quickTab = computed(() => {
  const state = equipmentAdvanced.value.find((c) => c.field === 'state');
  return state?.values.length === 1 ? state.values[0]! : '全部设备';
});

const query = reactive({ keyword: '' });
const appliedQuery = ref({ ...query });
const filterOpen = ref(false);
const equipmentAdvanced = ref<FilterCondition[]>([]);
const navigationTree = ref<InstanceType<typeof EquipmentNavigationTree>>();
function commitConditions(conditions: FilterCondition[]) {
  filterOpen.value = false;
  equipmentAdvanced.value = conditions;
  page.value = 1;
  selectedRows.value = [];
}
const sortKey = ref<string | null>(null);

const sortDirection = ref<'asc' | 'desc' | null>(null);

const storedWidths = (() => {
  try {
    return JSON.parse(localStorage.getItem('eam-column-widths') || '{}') as Record<string, number>;
  } catch {
    return {};
  }
})();

const columnWidths = ref(storedWidths);

const resizeOrigin = ref<{ key: keyof Equipment; x: number; width: number } | null>(null);

const page = ref(1);

const pageSize = useEquipmentPageSize(() =>
  auth.user ? `${auth.user.tenantId}-${auth.user.id}` : '',
);
const listLoading = ref(false);
const listError = ref('');
const hasAppliedFilters = computed(() =>
  !!appliedQuery.value.keyword.trim() || equipmentAdvanced.value.length > 0 || !!equipmentTreeSelection.value,
);

const selectedRows = ref<Equipment[]>([]);
const selected = computed<string[]>({
  get: () => selectedRows.value.map((row) => row.id),
  set: (ids) => {
    selectedRows.value = selectedRows.value.filter((row) => ids.includes(row.id));
  },
});

const showColumns = ref(false);

const editing = ref<Equipment | null>(null);

const drawer = ref(false);

const isNew = ref(false);

const equipmentSaving = ref(false);

const equipmentUploading = ref(false);
const equipmentRelationSaving = ref(false);
const equipmentRelationDirty = ref(false);

const { markSaved: markEquipmentDetailsSaved, dirty: equipmentDetailsDirty } = useUnsavedChanges(
  () => {
    if (!editing.value) return null;
    const { rowVersion, children, relatedSpares, ...details } = editing.value;
    return details;
  },
  () => drawer.value && !!editing.value,
  () => equipmentSaving.value || equipmentUploading.value || equipmentRelationSaving.value,
  { localOnly: true },
);
const { markSaved: markEquipmentNavigationSaved, dirty: equipmentDirty } = useUnsavedChanges(
  () => ({ detailsDirty: equipmentDetailsDirty.value, relationDirty: equipmentRelationDirty.value }),
  () => drawer.value && !!editing.value,
  () => equipmentSaving.value || equipmentUploading.value || equipmentRelationSaving.value,
);
function markEquipmentSaved() {
  markEquipmentDetailsSaved();
  markEquipmentNavigationSaved();
}

function closeEquipment() {
  if (equipmentSaving.value || equipmentUploading.value || equipmentRelationSaving.value) return;
  router.push({ name: 'equipment' });
}

const baseColumns: Column[] = [
  // { key: 'imageFileId', label: '图片', width: 72, resizable: false },
  { key: 'code', get label() { return tr('ui.equipment.text0078'); }, width: 176, required: true },
  { key: 'name', get label() { return tr('ui.equipment.text0081'); }, width: 220, required: true },
  { key: 'state', get label() { return tr('ui.equipment.text0111'); }, width: 100, align: 'center' },
  { key: 'assetNo', get label() { return tr('ui.equipment.text0085'); }, width: 132 },
  { key: 'category', get label() { return tr('ui.equipment.text1082'); }, width: 124 },
  { key: 'department', get label() { return tr('ui.equipment.text0098'); }, width: 200 },
  { key: 'functionLocation', get label() { return tr('ui.equipment.text0108'); }, width: 220 },
   { key: 'useState', get label() { return tr('ui.equipment.text0112'); }, width: 100, align: 'center' },
  { key: 'model', get label() { return tr('ui.equipment.text0091'); }, width: 180 },
  { key: 'serial', get label() { return tr('ui.equipment.text1666'); }, width: 140 },
  { key: 'owner', get label() { return tr('ui.equipment.text1667'); }, width: 156 },
  { key: 'brand', get label() { return tr('ui.equipment.text0093'); }, width: 116 },
  { key: 'source', get label() { return tr('ui.equipment.text0117'); }, width: 100 },
  { key: 'unit', get label() { return tr('ui.equipment.text0095'); }, width: 80 },
  { key: 'supplier', get label() { return tr('ui.equipment.text0118'); }, width: 130 },
  { key: 'acquiredAt', get label() { return tr('ui.equipment.text0122'); }, width: 116 },
  { key: 'amount', get label() { return tr('ui.equipment.text1668'); }, width: 122 },
  { key: 'warranty', get label() { return tr('ui.equipment.text0125'); }, width: 116 },
];

const columns = computed<Column[]>(() => [
  ...baseColumns,
  ...customColumns(customDefinitions.value),
]);

const equipmentFilterOrder = [
  'code',
  'name',
  'state',
  'useState',
  'category',
  'model',
  'brand',
  'assetNo',
  'serial',
  'tagCode',
  'unit',
  'level',
  'company',
  'department',
  'functionLocation',
  'owner',
  'source',
  'supplier',
  'acquiredAt',
  'warranty',
  'amount',
  'inServiceDate',
  'retirementDate',
];

const equipmentFilterFields = computed<FilterField[]>(() =>
  [
    ...columns.value
      .filter((c) => c.key !== 'imageFileId' && !c.key.startsWith('custom:'))
      .map((c) => ({ key: c.key, label: c.label, type: 'text' as const })),
    ...[
      ['company', '所属公司'],
      ['tagCode', '电子标签码'],
      ['inServiceDate', '启用日期'],
      ['retirementDate', '预计报废日期'],
      ['level', '设备等级'],
    ].map(([key, label]) => ({ key: key!, label: label!, type: 'text' as const })),
  ]
    .map<FilterField>((f) => {
      if (f.key === 'category') return { ...f, type: 'tree', nodes: categoryData.value };
      if (f.key === 'functionLocation')
        return { ...f, type: 'tree', nodes: locationData.value, pathValue: true };
      if (['acquiredAt', 'warranty', 'inServiceDate', 'retirementDate'].includes(f.key))
        return { ...f, type: 'date', dateOnly: true };
      if (f.key === 'amount') return { ...f, type: 'number' };
      if (f.key === 'state') return { ...f, type: 'choice', options: [...EQUIPMENT_STATES] };
      const options =
        f.key === 'unit'
          ? unitData.value
          : f.key === 'useState'
            ? useStateData.value
            : f.key === 'source'
              ? sourceData.value
              : null;
      return options ? { ...f, type: 'choice', options: options.map((v) => v.name) } : f;
    })
    .sort((a, b) => equipmentFilterOrder.indexOf(a.key) - equipmentFilterOrder.indexOf(b.key))
    .concat(customFilters(customDefinitions.value, 'equipment')),
);

const defaultEquipmentColumns = ['code', 'name', 'state', 'useState', 'model', 'department', 'functionLocation', 'owner'];
function restoreEquipmentColumns() {
  visibleKeys.value = [...defaultEquipmentColumns];
  columnWidths.value = {};
}

function loadEquipmentColumns(): string[] {
  try {
    const saved = JSON.parse(localStorage.getItem('eam-columns') || 'null');
    if (!Array.isArray(saved)) return [...defaultEquipmentColumns];
    const keys = saved.filter(
      (key) => key.startsWith('custom:') || columns.value.some((c) => c.key === key),
    );
    if (localStorage.getItem('eam-equipment-columns-version') !== '2') {
      if (!keys.includes('imageFileId')) keys.push('imageFileId');
      localStorage.setItem('eam-equipment-columns-version', '2');
      localStorage.setItem('eam-columns', JSON.stringify(keys));
    }
    return [...new Set(['code', 'name', ...keys])];
  } catch {
    return [...defaultEquipmentColumns];
  }
}

const visibleKeys = ref<string[]>(loadEquipmentColumns());

const activeCategories = computed(() => categoryData.value.filter((x) => x.status === '启用'));

const categoryTreeOptions = computed<TreeSelectNode[]>(() => activeCategories.value);

const locationTreeOptions = computed<TreeSelectNode[]>(() =>
  locationData.value.filter((x) => x.status === '启用'),
);

const filtered = computed(() => rows.value);
const pageCount = computed(() => Math.max(1, Math.ceil(serverTotal.value / pageSize.value)));
const pageRows = computed(() => rows.value);
function cell(row: Equipment, key: string) {
  if (key === 'functionLocation') return normalizeLocationPath(row.functionLocation);
  return key.startsWith('custom:') ? customCell(row, key) : (row[key as keyof Equipment] ?? '');
}
function equipmentParams() {
  const scope = equipmentTreeSelection.value;
  const sortMap: Record<string, string> = {
    code: 'equipmentCode',
    state: 'equipmentStatus',
    useState: 'useStatus',
    serial: 'serialNo',
    owner: 'ownerName',
    amount: 'acquiredAmount',
    warranty: 'warrantyUntil',
  };
  return {
    keyword: appliedQuery.value.keyword,
    advanced: JSON.stringify(equipmentAdvanced.value),
    sortBy: sortKey.value ? sortMap[sortKey.value] || sortKey.value : 'createdAt',
    direction: sortDirection.value || 'desc',
    scopeType: scope?.type,
    scopeIds: scope?.ids.join(','),
    scopeCompanyId: scope?.companyId,
  };
}

const selectedColumns = computed(() =>
  visibleKeys.value.flatMap(key => {
    const column = columns.value.find(c => c.key === key);
    return column ? [column] : [];
  }),
);

let equipmentLoadRevision = 0;

function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (e.defaultPrevented || document.querySelector('.eam-unsaved-confirmation')) return;
    if (drawer.value || filterOpen.value) return;
    showColumns.value = false;
  }
}

function notify(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'success') {
  ElMessage({ message, type });
}

async function refreshEquipment() {
  const revision = ++equipmentLoadRevision;
  if (!apiMode.value || !can('equipment')) {
    rows.value = [];
    serverTotal.value = 0;
    listLoading.value = false;
    listError.value = '';
    return;
  }
  listLoading.value = true;
  listError.value = '';
  try {
    const result = await api.listEquipment({
      ...equipmentParams(),
      page: page.value,
      size: pageSize.value,
    });
    if (revision === equipmentLoadRevision) {
      rows.value = result.data;
      serverTotal.value = result.page.total;
    }
  } catch (error) {
    if (revision !== equipmentLoadRevision) return;
    rows.value = [];
    serverTotal.value = 0;
    selected.value = [];
    listError.value = error instanceof Error ? error.message : tr('ui.equipment.text1669');
  } finally {
    if (revision === equipmentLoadRevision) listLoading.value = false;
  }
}

async function search() {
  appliedQuery.value = { ...query };
  page.value = 1;
  selected.value = [];
  await refreshEquipment();
}

function reset() {
  commitConditions([]);
  query.keyword = '';
  search();
}
function selectQuickTab(tab: string) {
  const rest = equipmentAdvanced.value.filter((c) => c.field !== 'state');
  commitConditions(
    tab === '全部设备' ? rest : [...rest, { field: 'state', operator: 'in', values: [tab] }],
  );
}

function onTableSort(sort: BusinessSort) {
  sortKey.value = sort.order ? (sort.prop as keyof Equipment) : null;
  sortDirection.value =
    sort.order === 'ascending' ? 'asc' : sort.order === 'descending' ? 'desc' : null;
  page.value = 1;
}

function columnWidth(key: string) {
  const column = columns.value.find((c) => c.key === key);
  return Math.max(
    (column?.label.length || 0) * 13 + 56,
    columnWidths.value[key] || column?.width || 100,
  );
}
function fixedColumnWidth(key: string) {
  if (key === 'imageFileId') return 72;
  return columnWidths.value[key] || ['code', 'state', 'useState'].includes(key)
    ? columnWidth(key)
    : undefined;
}

function resizeMove(event: PointerEvent) {
  if (!resizeOrigin.value) return;
  columnWidths.value = {
    ...columnWidths.value,
    [resizeOrigin.value.key]: Math.max(
      76,
      Math.min(520, resizeOrigin.value.width + event.clientX - resizeOrigin.value.x),
    ),
  };
}

function resizeEnd() {
  if (resizeOrigin.value) {
    resizeOrigin.value = null;
    document.body.classList.remove('resizing-column');
  }
}

function toggleColumn(key: string) {
  const c = columns.value.find((c) => c.key === key);
  if (c?.required) return;
  visibleKeys.value = visibleKeys.value.includes(key)
    ? visibleKeys.value.filter((k) => k !== key)
    : [...visibleKeys.value, key];
}

function create() {
  isNew.value = true;
  editing.value = {
    id: '',
    code: '',
    name: '',
    category: '',
    model: '',
    serial: '',
    company: currentCompany.value,
    department: currentDepartment.value,
    departmentOrgId: currentDepartment.value ? auth.user?.orgId || '' : '',
    owner: '',
    brand: '',
    state: '正常',
    useState: '正常',
    source: '购入',
    acquiredAt: '',
    amount: '',
    warranty: '',
    remark: '',
    assetNo: '',
    tagCode: '',
    unit: '',
    supplier: '',
    purchaseDate: '',
    inServiceDate: '',
    retirementDate: '',
    level: '',
    functionLocation: '',
    locationId: '',
    specialEquipment: '否',
    metrologyEquipment: '否',
    depreciationEnabled: '否',
    technicalParams: '',
    imageName: '',
    documents: [],
    children: [],
    relatedSpares: [],
  };
  markEquipmentSaved();
  drawer.value = true;
  if (route.name !== 'equipment-new') router.push({ name: 'equipment-new' });
}

function edit(row: Equipment) {
  const draft = JSON.parse(JSON.stringify(row)) as Equipment;
  isNew.value = false;
  editing.value = {
    ...draft,
    unit: draft.unit || '台',
    functionLocation: draft.functionLocation || '',
    locationId: draft.locationId || '',
    specialEquipment: draft.specialEquipment || '否',
    metrologyEquipment: draft.metrologyEquipment || '否',
    depreciationEnabled: draft.depreciationEnabled || '否',
    documents: draft.documents || [],
    children: draft.children || [],
    relatedSpares: draft.relatedSpares || [],
  };
  markEquipmentSaved();
  drawer.value = true;
  if (route.name !== 'equipment-edit' || route.params.id !== row.id)
    router.push({ name: 'equipment-edit', params: { id: row.id } });
}

function clone(row: Equipment) {
  isNew.value = true;
  editing.value = {
    ...row,
    id: '',
    code: '',
    name: `${row.name} - 副本`,
    assetNo: '',
    tagCode: '',
    documents: [],
    children: [],
    relatedSpares: [],
  };
  markEquipmentSaved();
  drawer.value = true;
  router.push({ name: 'equipment-new' });
}

function generatedEquipmentCode() {
  let sequence =
    Math.max(0, ...rows.value.map((row) => Number(row.code.match(/(\d+)$/)?.[1]) || 0)) + 1;
  let code = '';
  do {
    code = `EQ-${new Date().getFullYear()}-${String(sequence++).padStart(4, '0')}`;
  } while (rows.value.some((row) => row.code === code));
  return code;
}

async function save() {
  if (!editing.value || equipmentSaving.value || equipmentUploading.value) return;
  const f = editing.value;
  const errors = equipmentEditor.value?.validate() || [];
  if (errors.length) {
    return;
  }
  if (
    isNew.value &&
    f.code.trim() &&
    rows.value.some((row) => row.code.toLocaleLowerCase() === f.code.trim().toLocaleLowerCase())
  ) {
    void equipmentEditor.value?.showErrors({ code: '设备编号已存在' });
    return;
  }
  equipmentSaving.value = true;
  try {
    if (isNew.value && !f.code.trim() && !apiMode.value) f.code = generatedEquipmentCode();
    if (apiMode.value) {
      const result = await api.saveEquipment(
        { ...f, customFields: equipmentEditor.value?.prepareCustomFields() ?? f.customFields },
        isNew.value,
        true,
      );
      if (isNew.value) rows.value.unshift(result.data);
      else rows.value = rows.value.map((x) => (x.id === result.data.id ? result.data : x));
    } else if (isNew.value) {
      f.id = crypto.randomUUID();
      rows.value.unshift({ ...f });
    } else {
      const i = rows.value.findIndex((r) => r.id === f.id);
      if (i >= 0) rows.value[i] = { ...f };
    }
    page.value = 1;
    markEquipmentSaved();
    equipmentSaving.value = false;
    await router.push({ name: 'equipment' });
    drawer.value = false;
    notify(isNew.value ? '设备已新增' : '设备信息已保存');
  } catch (error) {
    if (error instanceof api.ApiError && error.status === 409 && error.message.includes('字段配置'))
      await equipmentEditor.value?.reloadCustomFields();
    if (error instanceof api.ApiError && Object.keys(error.fieldErrors).length)
      void equipmentEditor.value?.showErrors(error.fieldErrors);
    else notify(error instanceof Error ? error.message : tr('ui.equipment.text1670'), 'error');
  } finally {
    equipmentSaving.value = false;
  }
}

async function remove(row: Equipment) {
  if (!(await confirmAction(tr('ui.common.dynamic62',{p0:row.name,p1:row.code})))) return;
  try {
    if (apiMode.value) await api.deleteEquipment(row.id);
    rows.value = rows.value.filter((r) => r.id !== row.id);
    selected.value = selected.value.filter((id) => id !== row.id);
    notify('设备已删除');
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text0278'), 'error');
  }
}

async function removeSelected() {
  if (!selected.value.length) return;
  if (!(await confirmAction(tr('ui.common.dynamic63',{p0:selected.value.length})))) return;
  try {
    await api.batchEquipment([...selected.value], 'delete', '');
    selected.value = [];
    await refreshEquipment();
    notify('已删除所选设备');
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text1671'), 'error');
  }
}

const exportOpen = ref(false),
  exportBusy = ref(false);
const exportColumns = computed(() => [
  ...baseColumns.filter((c) => c.key !== 'imageFileId'),
  ...customDefinitions.value
    .filter((f) => f.exportable)
    .map((f) => ({ key: `custom:${f.id}`, label: f.name })),
]);
const exportDefaultKeys = computed(() =>
  exportColumns.value
    .filter((c) => (!c.key.startsWith('custom:') ? visibleKeys.value.includes(c.key) : true))
    .map((c) => c.key),
);
function exportCsv() {
  exportOpen.value = true;
}
async function confirmExport(keys: string[]) {
  exportBusy.value = true;
  try {
    const blob = await api.exportEquipment({ ...equipmentParams(), columns: keys.join(',') });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = '设备台账.csv';
    a.click();
    URL.revokeObjectURL(url);
    exportOpen.value = false;
    notify('导出成功');
  } catch (e) {
    notify((e as Error).message, 'error');
  } finally {
    exportBusy.value = false;
  }
}

function formatAmount(value: string) {
  return formatNumber(value);
}

const useStatusCodes = computed(
  () => new Map(useStateData.value.map((item) => [item.id, item.code])),
);
function useStatusStyle(row: Equipment) {
  return equipmentUseStatusStyle(useStatusCodes.value.get(row.useStateId || ''), row.useState);
}
function statusClass(value: string) {
  return value === '正常'
    ? 'success'
    : value === '故障'
      ? 'danger'
      : value === '带病运行'
        ? 'warning'
        : 'muted';
}

watch(visibleKeys, (v) => localStorage.setItem('eam-columns', JSON.stringify(v)), { deep: true });
watch(columnWidths, (v) => localStorage.setItem('eam-column-widths', JSON.stringify(v)), {
  deep: true,
});
watch(filtered, () => {
  if (!listError.value && page.value > pageCount.value) page.value = pageCount.value;
});
let editRouteRevision = 0;
watch(
  [() => route.name, () => route.params.id, rows],
  async () => {
    const revision = ++editRouteRevision;
    if (route.name === 'equipment-new') {
      if (!editing.value || !isNew.value) create();
      drawer.value = true;
    } else if (route.name === 'equipment-edit') {
      const id = String(route.params.id);
      if (drawer.value && editing.value?.id === id) return;
      try {
        const row = rows.value.find((item) => item.id === id) || (await api.getEquipment(id)).data;
        if (
          revision === editRouteRevision &&
          route.name === 'equipment-edit' &&
          String(route.params.id) === id
        )
          edit(row);
      } catch (e) {
        if (revision === editRouteRevision) notify((e as Error).message, 'error');
      }
    }
  },
  { immediate: true },
);
watch(drawer, (open) => {
  if (!open && ['equipment-new', 'equipment-edit'].includes(String(route.name)))
    router.push({ name: 'equipment' });
});
watch(
  () => route.fullPath,
  () => {
    showColumns.value = false;
    if (route.name === 'equipment' || route.name === 'equipment-history') drawer.value = false;
    if (route.name === 'equipment') {
      selected.value = [];
      void refreshEquipment();
    }
  },
);
async function reloadEquipmentPermissions() {
  equipmentLoadRevision++;
  rows.value = [];
  selected.value = [];
  await auth.load(true);
  await refreshEquipment();
}
watch([page, pageSize, sortKey, sortDirection], () => void refreshEquipment());
watch(
  [equipmentAdvanced, equipmentTreeSelection],
  () => {
    selectedRows.value = [];
    page.value = 1;
    void refreshEquipment();
  },
  { deep: true },
);
onMounted(() => {
  void businessApi
    .runtime('equipment')
    .then((r) => {
      customDefinitions.value = r.configuration.fields;
    })
    .catch((e) => notify(e.message, 'error'));
  window.addEventListener('keydown', onKey);
  window.addEventListener('pointermove', resizeMove);
  window.addEventListener('pointerup', resizeEnd);
  window.addEventListener('eam-permissions-changed', reloadEquipmentPermissions);
  void refreshEquipment();
});
onUnmounted(() => {
  equipmentLoadRevision++;
  window.removeEventListener('keydown', onKey);
  window.removeEventListener('pointermove', resizeMove);
  window.removeEventListener('pointerup', resizeEnd);
  window.removeEventListener('eam-permissions-changed', reloadEquipmentPermissions);
  resizeEnd();
});
const {
  filterAnchor,
  editingFilter,
  showFilter: openFilter,
  confirmFilter: confirmCondition,
} = useConditionPopover(filterOpen, () => equipmentAdvanced.value, commitConditions);
</script>

<template>
  <ExportFieldsDialog
    v-model="exportOpen"
    :columns="exportColumns"
    :default-keys="exportDefaultKeys"
    :busy="exportBusy"
    @confirm="confirmExport"
  />
  <section class="content">
    <AppListTabs
      class="equipment-tabs"
      :active="quickTab"
      :items="EQUIPMENT_TABS.map((tab) => ({ key: tab, label: enumLabel('tab',tab) }))"
      @select="selectQuickTab"
      @reselect="selectQuickTab"
    />
    <AppListToolbar class="toolbar">
      <div class="toolbar-left">
        <ElButton type="primary" v-if="can('equipment', '新增')" @click="create">
          <span class="plus"> <AppIcon name="Plus" /> </span>{{ tr('ui.equipment.text0149') }}</ElButton
        >
        <ElButton v-if="can('equipment', '导入') && can('equipment', '新增')" @click="openImport"
          ><AppIcon name="Upload" />{{ tr('ui.equipment.text1196') }}</ElButton
        >
        <ElDropdown
          v-if="can('equipment', '编辑') || can('equipment', '删除')"
          trigger="click"
          :disabled="!selected.length || listLoading || !!listError"
          @command="openBatch"
        >
          <ElButton :disabled="!selected.length || listLoading || !!listError"
            >{{ tr('ui.equipment.text0373') }}<span v-if="selected.length" class="count-pill">{{ selected.length }}</span
            ><AppIcon name="Down"
          /></ElButton>
          <template #dropdown
            ><ElDropdownMenu>
              <template v-for="(label, operation) in batchLabels" :key="operation">
                <ElDropdownItem
                  v-if="can('equipment', operation === 'delete' ? '删除' : '编辑')"
                  :command="operation"
                  :class="{ 'danger-text': operation === 'delete' }"
                  >{{ label }}</ElDropdownItem
                >
              </template>
            </ElDropdownMenu></template
          >
        </ElDropdown>
      </div>
      <form class="filters" @submit.prevent="search">
        <ListSearchInput v-model="query.keyword" :placeholder="tr('ui.equipment.text1672')" />
        <ElButton
          class="advanced-filter-trigger"
          :class="{ active: equipmentAdvanced.length }"
          native-type="button"
          @click="openFilter($event.currentTarget as HTMLElement)"
        >
          {{ tr('ui.equipment.text0203') }}<span v-if="equipmentAdvanced.length"> ({{ equipmentAdvanced.length }})</span>
        </ElButton>
        <ElButton text class="reset-btn" native-type="button" @click="reset">{{ tr('ui.equipment.text0204') }}</ElButton>
      </form>
    </AppListToolbar>
    <AppliedFilterChips
      :conditions="equipmentAdvanced"
      :fields="equipmentFilterFields"
      editable
      @change="commitConditions"
      @edit="(condition, anchor) => openFilter(anchor, condition.field)"
    >
      <template v-if="equipmentTreeSelection" #leading>
        <ElTag closable @close="selectEquipmentTree(null)">
          <ElButton link @click="navigationTree?.revealSelection()"
            >{{ tr('ui.equipment.text1673') }}{{ equipmentTreeSelection.names[0] }}{{ tr('ui.equipment.text1674') }}</ElButton
          >
        </ElTag>
      </template>
    </AppliedFilterChips>
    <FilterConditionPopover
      v-model="filterOpen"
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      :conditions="equipmentAdvanced"
      :fields="equipmentFilterFields"
      @confirm="confirmCondition"
    />
    <div class="equipment-list-layout">
      <EquipmentNavigationTree
        ref="navigationTree"
        :selection="equipmentTreeSelection"
        :categories="categoryData"
        :locations="locationData"
        @select="selectEquipmentTree"
      />
      <div class="table-card">
        <div class="table-scroll">
          <AppBusinessTable
            @sort-change="onTableSort"
            v-model:selection="selectedRows"
            selectable
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            :rows="pageRows"
            :loading="listLoading"
            :error="listError"
            :filtered="hasAppliedFilters"
            @retry="refreshEquipment"
            :pagination="false"
            :fill="true"
            :framed="false"
            border-mode="header"
            class="business-table equipment-ledger-table"
            @column-resize="
              (key, width) => {
                columnWidths[key as keyof Equipment] = width;
              }
            "
          >
            <!-- @vue-generic {Equipment} -->
            <ElTableColumn
              :label="col.label"
              :align="col.align ?? 'left'"
              v-for="col in selectedColumns"
              :min-width="col.key === 'imageFileId' ? 72 : columnWidth(col.key)"
              :width="fixedColumnWidth(col.key)"
              :resizable="col.resizable ?? true"
              :sortable="col.key === 'imageFileId' || col.sortable === false ? false : 'custom'"
              show-overflow-tooltip
              :prop="col.key"
              :key="col.key"
              ><template #header>
                <span class="equipment-column-label" :title="col.label">{{
                  col.label
                }}</span> </template
              ><template #default="{ row }">
                <template v-if="col.key === 'code'">
                  <ElButton type="primary" link class="code-link" @click="openHistory(row)">{{
                    row.code
                  }}</ElButton> </template
                ><template v-else-if="col.key === 'imageFileId'">
                  <EquipmentImage :file-id="row.imageFileId" :name="row.name" /> </template
                ><template v-else-if="col.key === 'department'"
                  ><DepartmentText :value="row.departmentPath || row.department" :department-id="row.departmentOrgId" /></template
                ><template v-else-if="col.key === 'name'"
                  ><span class="equipment-name-text">{{ row.name }}</span></template
                ><template v-else-if="col.key === 'state' || col.key === 'useState'"
                  ><span
                    v-if="row[col.key]"
                    class="status ledger-status"
                    :style="col.key === 'useState' ? useStatusStyle(row) : undefined"
                    :class="[
                      col.key === 'state' ? statusClass(String(row[col.key])) : '',
                      { 'equipment-status': col.key === 'state' },
                    ]"
                    ><i></i>{{ col.key === 'state' ? enumLabel('equipmentStatus',row.state) : row[col.key] }}</span
                  ></template
                ><template v-else-if="col.key === 'amount'"
                  ><span class="amount">{{ formatAmount(row.amount) }}</span></template
                ><template v-else-if="isDepartmentCustomColumn(customDefinitions, col.key)"><DepartmentValuesText :value="String(cell(row, col.key))" :ids="row.customFields?.[col.key.slice(7)]" /></template><template v-else>{{ cell(row, col.key) }}</template>
              </template></ElTableColumn
            >
            <!-- @vue-generic {Equipment} -->
            <ElTableColumn
              :label="tr('ui.equipment.text0145')"
              class-name="row-actions list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #default="{ row }">
                <ElButton type="primary" link v-if="can('equipment', '编辑')" @click="edit(row)">
                  {{ tr('ui.equipment.text0207') }}
                </ElButton>
                <ElButton type="primary" link v-if="can('equipment', '新增')" @click="clone(row)">
                  {{ tr('ui.equipment.text0270') }}</ElButton
                >

                <ElDropdown
                  v-if="can('equipment', '删除')"
                  trigger="click"
                  placement="bottom-end"
                  popper-class="eam-dropdown"
                  @command="(command) => (command === 'clone' ? clone(row) : remove(row))"
                  ><ElButton text class="more-action" :aria-label="`更多操作：${row.name}`">
                    <AppIcon name="More" /></ElButton
                  ><template #dropdown>
                    <ElDropdownMenu>
                      <!-- <ElDropdownItem v-if="can('equipment', '新增')" command="clone">复制</ElDropdownItem> -->
                      <ElDropdownItem v-if="can('equipment', '删除')" command="delete"
                        >{{ tr('ui.equipment.text0146') }}</ElDropdownItem
                      >
                    </ElDropdownMenu>
                  </template></ElDropdown
                >
              </template></ElTableColumn
            ><template #empty>
              <div v-if="pageRows.length === 0" class="ledger-empty" :role="listError ? 'alert' : 'status'">
                <template v-if="listLoading">{{ tr('ui.equipment.text0162') }}</template>
                <template v-else-if="listError">
                  <b>{{ tr('equipment.list.loadFailed') }}</b>
                  <span>{{ listError }}</span>
                  <ElButton type="primary" link @click="refreshEquipment">{{ tr('ui.equipment.text0299') }}</ElButton>
                </template>
                <template v-else>
                <div class="empty-icon">
                  <AppIcon name="Search" />
                </div>
                <b>{{ hasAppliedFilters ? tr('ui.equipment.text1675') : tr('ui.equipment.text0701') }}</b
                ><span>{{
                  hasAppliedFilters ? tr('ui.equipment.text1676') : can('equipment', '新增') ? tr('ui.equipment.text1677') : tr('equipment.list.emptyAuthorized')
                }}</span
                ><ElButton type="primary" link v-if="appliedQuery.keyword.trim() || equipmentAdvanced.length" @click="reset"
                  >{{ tr('equipment.list.clearQuery') }}</ElButton
                >
                <ElButton type="primary" link v-if="equipmentTreeSelection" @click="selectEquipmentTree(null)">{{ tr('equipment.list.allAuthorized') }}</ElButton>
                </template>
              </div>
            </template>
          </AppBusinessTable>
        </div>
        <footer class="table-footer">
          <div class="result-count">
            <template v-if="listLoading">{{ tr('ui.equipment.text0162') }}</template>
            <template v-else-if="listError">{{ tr('equipment.list.loadFailed') }}</template>
            <template v-else>
            {{ tr('ui.equipment.text0309') }} <b>{{ serverTotal }}</b> {{ tr('ui.equipment.text0296') }}<span v-if="selected.length" class="selected-label"
              >{{ tr('ui.equipment.text0295') }} {{ selected.length }} {{ tr('ui.equipment.text0296') }}</span
            >
            </template>
          </div>
          <AppPagination v-if="!listError" v-model:page="page" v-model:page-size="pageSize" :total="serverTotal" :page-sizes="EQUIPMENT_PAGE_SIZES" />
          <ListToolbarIcons
            mode="footer"
            :can-export="can('equipment', '导出')"
            :columns-open="showColumns"
            @toggle-columns="showColumns = !showColumns"
            @export="exportCsv"
            ><template #columns>
              <div class="popover-head">
                <b>{{ tr('ui.equipment.text1679') }}</b
                ><ElButton native-type="button" @click="restoreEquipmentColumns">
                  {{ tr('ui.equipment.text0553') }}
                </ElButton>
              </div>
              <ElCheckbox
                v-for="col in columns"
                :key="col.key"
                class="equipment-column-option"
                :model-value="visibleKeys.includes(col.key)"
                :disabled="col.required"
                @change="toggleColumn(col.key)"
              >
                {{ col.label }}<small v-if="col.required">{{ tr('ui.equipment.text1680') }}</small>
              </ElCheckbox>
              <div class="popover-foot">{{ tr('ui.equipment.text1681') }} {{ visibleKeys.length }} {{ tr('ui.equipment.text0975') }}</div>
            </template></ListToolbarIcons
          >
        </footer>
      </div>
    </div>
  </section>

  <EquipmentHistory v-if="historyId" :id="historyId" @close="closeHistory" />
  <AppDialog
    v-model="batchOpen"
    :title="batchLabels[batchForm.operation]"
    :busy="batchBusy"
    width="440px"
  >
    <div class="batch-dialog-content">
      <p>{{ tr('ui.equipment.text1218') }} {{ selected.length }} {{ tr('ui.equipment.text1682') }}</p>
      <label
        ><span>{{ batchLabels[batchForm.operation].replace('修改', '') }}</span>
        <TreeSelect
          v-if="batchForm.operation === 'category' || batchForm.operation === 'location'"
          v-model="batchForm.value"
          :nodes="
            (batchForm.operation === 'category' ? categoryTreeOptions : locationTreeOptions).map(
              (x) => ({ ...x, value: x.id }),
            )
          "
          :placeholder="tr('ui.equipment.text0096')"
        />
        <TreeSelect
          v-else-if="batchForm.operation === 'department'"
          v-model="batchForm.value"
          :nodes="batchDepartmentNodes"
          :placeholder="tr('ui.equipment.text0315')"
        />
        <ElSelect
          v-else-if="batchForm.operation === 'owner'"
          v-model="batchForm.ownerIds"
          multiple
          filterable
          clearable
          :placeholder="tr('ui.equipment.text1683')"
        >
          <ElOption
            v-for="user in batchOptions.users"
            :key="user.id"
            :value="user.id"
            :label="user.name"
          /> </ElSelect
        ><ElSelect v-else v-model="batchForm.value" :placeholder="tr('ui.equipment.text1684')" clearable>
          <ElOption v-for="state in EQUIPMENT_STATES" :key="state" :label="enumLabel('equipmentStatus',state)" :value="state" />
        </ElSelect>
      </label>
    </div>
    <template #footer
      ><ElButton :disabled="batchBusy" @click="batchOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton
        type="primary"
        :loading="batchBusy"
        :disabled="batchForm.operation !== 'owner' && !batchForm.value"
        @click="submitBatch"
      >
        {{ batchBusy ? tr('ui.equipment.text0158') : tr('ui.equipment.text0402') }}
      </ElButton></template
    >
  </AppDialog>
  <EquipmentImportDialog
    v-if="importOpen"
    :model-value="importOpen"
    :batch-id="String(route.query.batch || '')"
    @update:model-value="closeImport"
    @batch-change="importBatchChanged"
    @open-record="(row) => router.push({ name: 'equipment-history', params: { id: row.id } })"
  />
  <EquipmentEditor
    ref="equipmentEditor"
    v-model="editing"
    v-model:open="drawer"
    :is-new="isNew"
    :dirty="equipmentDetailsDirty"
    :can-view-archive="can('equipment', '查看')"
    :busy="equipmentSaving || equipmentUploading"
    @relation-busy="equipmentRelationSaving = $event"
    @relation-dirty="equipmentRelationDirty = $event"
    @relation-saved="(record) => { rows = rows.map(row => row.id === record.id ? record : row); }"
    @history="editing && router.push({ name: 'equipment-history', params: { id: editing.id } })"
    @custom-initialize="customInitializationClean = !equipmentDirty"
    @custom-ready="
      () => {
        if (customInitializationClean) markEquipmentSaved();
      }
    "
    @uploading="equipmentUploading = $event"
    @save="save"
    @close="closeEquipment"
  />
</template>
<style scoped>
.equipment-tabs {
  height: auto;
  flex: none;
  display: block;
  margin-bottom: 0;
}
.equipment-tabs :deep(.el-tabs__header) {
  margin: 0 0 8px;
}
.equipment-tabs :deep(.el-tabs__item) {
  height: 36px;
  font-size: 13px;
}
.toolbar-left,
.filters {
  gap: 12px;
}
.toolbar :deep(.el-button) {
  height: 34px;
  font-size: 13px;
}
.toolbar :deep(.el-button + .el-button) {
  margin-left: 0;
}
.equipment-column-option {
  display: flex;
  margin-right: 0;
  min-height: 30px;
}
.ledger-empty {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 24px;
  white-space: normal;
  overflow-wrap: anywhere;
}
.equipment-department {
  display: inline-block;
  max-width: 100%;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.equipment-department:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 2px;
}
.equipment-column-option small {
  margin-left: 6px;
  font-size: 12px;
}

.equipment-ledger-table :deep(th .cell) {
  white-space: nowrap;
}
.equipment-column-label {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.equipment-ledger-table .code-link,
.equipment-ledger-table .code-link:hover {
  text-decoration: none;
}
</style>
<style scoped>
.equipment-list-layout {
  display: flex;
  flex: 1;
  min-height: 0;
  min-width: 0;
  gap: 12px;
  padding-bottom: 0;
}

.equipment-list-layout:has(.equipment-navigation.collapsed) {
  gap: 0;
}

.equipment-list-layout > .table-card {
  min-width: 0;
  margin: 0;
}

/* 台账的设备状态和使用状态统一宽度与对齐。 */
.equipment-ledger-table .status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  width: 80px;
  white-space: nowrap;
}

/* 设备状态采用轻量语义标签，不改变其他模块的状态样式。 */
.equipment-ledger-table .ledger-status {
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  padding: 3px 9px;
  border: 1px solid transparent;
  border-radius: 6px;
}

.equipment-ledger-table .equipment-status.success {
  color: var(--equipment-state-success-color);
  background: var(--equipment-state-success-background);
  border-color: var(--equipment-state-success-border);
}

.equipment-ledger-table .equipment-status.warning {
  color: var(--equipment-state-warning-color);
  background: var(--equipment-state-warning-background);
  border-color: var(--equipment-state-warning-border);
}

.equipment-ledger-table .equipment-status.danger {
  color: var(--equipment-state-danger-color);
  background: var(--equipment-state-danger-background);
  border-color: var(--equipment-state-danger-border);
}
</style>
