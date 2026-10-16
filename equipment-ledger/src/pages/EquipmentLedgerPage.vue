<script setup lang="ts">
import AppListTabs from '../components/common/AppListTabs.vue';
import AppListToolbar from '../components/common/AppListToolbar.vue';
import 'element-plus/es/components/tabs/style/css';
import 'element-plus/es/components/checkbox/style/css';
import type { BusinessSort } from '../components/common/BusinessTable.types';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppIcon from '../components/common/AppIcon.vue';
import { formatNumber } from '../utils/number';
import { normalizeLocationPath } from '../utils/locationPath';
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
import { EQUIPMENT_STATES, EQUIPMENT_TABS } from '../config/equipment';

type Equipment = api.EquipmentRecord;

type Column = {
  key: keyof Equipment;
  label: string;
  width: number;
  required?: boolean;
  align?: 'left' | 'center' | 'right';
  resizable?: boolean;
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
    notify(e instanceof Error ? e.message : '加载失败', 'error');
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
    notify(e instanceof Error ? e.message : '批量修改失败', 'error');
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
const sortKey = ref<keyof Equipment | null>(null);

const sortDirection = ref<'asc' | 'desc' | null>(null);

const storedWidths = (() => {
  try {
    return JSON.parse(localStorage.getItem('eam-column-widths') || '{}') as Partial<
      Record<keyof Equipment, number>
    >;
  } catch {
    return {};
  }
})();

const columnWidths = ref(storedWidths);

const resizeOrigin = ref<{ key: keyof Equipment; x: number; width: number } | null>(null);

const page = ref(1);

const pageSize = ref(10);

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

const { markSaved: markEquipmentSaved } = useUnsavedChanges(
  () => editing.value,
  () => drawer.value && !!editing.value,
  () => equipmentSaving.value || equipmentUploading.value,
);

function closeEquipment() {
  if (equipmentSaving.value || equipmentUploading.value) return;
  router.push({ name: 'equipment' });
}

const columns: Column[] = [
  // { key: 'imageFileId', label: '图片', width: 72, resizable: false },
  { key: 'code', label: '设备编号', width: 190, required: true },
  { key: 'name', label: '设备名称', width: 150, required: true },
  { key: 'state', label: '设备状态', width: 100, align: 'center' },
  { key: 'useState', label: '使用状态', width: 100, align: 'center' },
  { key: 'assetNo', label: '资产编号', width: 132 },
  { key: 'category', label: '设备分类', width: 124 },
  { key: 'department', label: '所属部门', width: 142 },
  { key: 'functionLocation', label: '位置', width: 150 },
  { key: 'model', label: '规格型号', width: 138 },
  { key: 'serial', label: '出厂编号', width: 140 },
  { key: 'owner', label: '责任人', width: 92 },
  { key: 'brand', label: '品牌', width: 116 },
  { key: 'source', label: '设备来源', width: 100 },
  { key: 'unit', label: '单位', width: 80 },
  { key: 'supplier', label: '供应商', width: 130 },
  { key: 'acquiredAt', label: '购置日期', width: 116 },
  { key: 'amount', label: '采购金额（元）', width: 122 },
  { key: 'warranty', label: '保修期至', width: 116 },
];

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
    ...columns
      .filter((c) => c.key !== 'imageFileId')
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
    .sort((a, b) => equipmentFilterOrder.indexOf(a.key) - equipmentFilterOrder.indexOf(b.key)),
);

const defaultEquipmentColumns = columns.slice(0, 12).map((c) => c.key);

function loadEquipmentColumns(): string[] {
  try {
    const saved = JSON.parse(localStorage.getItem('eam-columns') || 'null');
    if (!Array.isArray(saved)) return [...defaultEquipmentColumns];
    const keys = saved.filter((key) => columns.some((c) => c.key === key));
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

const filtered = computed(() => {
  const term = appliedQuery.value.keyword.trim().toLocaleLowerCase();
  return rows.value.filter((r) => {
    const tree = equipmentTreeSelection.value;
    if (tree?.type === 'location' && tree.companyId && String(r.companyOrgId) !== tree.companyId)
      return false;
    if (
      tree &&
      !(tree.type === 'organization'
        ? tree.ids.includes(String(r.departmentOrgId)) || tree.ids.includes(String(r.companyOrgId))
        : tree.type === 'location'
          ? r.locationId
            ? tree.ids.includes(r.locationId)
            : tree.names.some(
                (value) =>
                  normalizeLocationPath(value) === normalizeLocationPath(r.functionLocation),
              )
          : r.categoryId
            ? tree.ids.includes(r.categoryId)
            : tree.names.includes(String(r.category)))
    )
      return false;
    const searchable = [
      r.code,
      r.name,
      r.assetNo,
      r.category,
      r.model,
      r.serial,
      r.department,
      r.owner,
      r.brand,
      r.state,
      r.useState,
      r.source,
      r.functionLocation,
      r.supplier,
    ]
      .join(' ')
      .toLocaleLowerCase();
    const matchesKeyword = !term || searchable.includes(term);
    return (
      matchesKeyword && matchesAdvanced(equipmentAdvanced.value, (key) => r[key as keyof Equipment])
    );
  });
});

const sortedFiltered = computed(() => {
  if (!sortKey.value || !sortDirection.value) return filtered.value;
  const key = sortKey.value,
    direction = sortDirection.value === 'asc' ? 1 : -1;
  return [...filtered.value].sort((a, b) => {
    const av = String(a[key] ?? ''),
      bv = String(b[key] ?? '');
    const compared =
      key === 'amount'
        ? Number(av || 0) - Number(bv || 0)
        : av.localeCompare(bv, 'zh-CN', { numeric: true, sensitivity: 'base' });
    return compared * direction;
  });
});

const pageCount = computed(() => Math.max(1, Math.ceil(filtered.value.length / pageSize.value)));

const pageRows = computed(() =>
  sortedFiltered.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
);

const selectedColumns = computed(() => columns.filter((c) => visibleKeys.value.includes(c.key)));

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
    return;
  }
  try {
    const q = appliedQuery.value;
    const result = await api.listEquipment({
      page: 1,
      size: 10000,
      keyword: q.keyword,
    });
    if (revision === equipmentLoadRevision) rows.value = result.data;
  } catch (error) {
    if (revision !== equipmentLoadRevision) return;
    rows.value = [];
    selected.value = [];
    notify(error instanceof Error ? error.message : '设备列表加载失败', 'error');
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

function columnWidth(key: keyof Equipment) {
  const column = columns.find((c) => c.key === key);
  return Math.max(
    (column?.label.length || 0) * 13 + 56,
    columnWidths.value[key] || column?.width || 100,
  );
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
  const c = columns.find((c) => c.key === key);
  if (c?.required) return;
  visibleKeys.value = visibleKeys.value.includes(key)
    ? visibleKeys.value.filter((k) => k !== key)
    : columns.filter((x) => visibleKeys.value.includes(x.key) || x.key === key).map((x) => x.key);
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
      const result = await api.saveEquipment(f, isNew.value);
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
    if (error instanceof api.ApiError && Object.keys(error.fieldErrors).length)
      void equipmentEditor.value?.showErrors(error.fieldErrors);
    else notify(error instanceof Error ? error.message : '设备保存失败', 'error');
  } finally {
    equipmentSaving.value = false;
  }
}

async function remove(row: Equipment) {
  if (!(await confirmAction(`确认删除设备「${row.name}（${row.code}）」？`))) return;
  try {
    if (apiMode.value) await api.deleteEquipment(row.id);
    rows.value = rows.value.filter((r) => r.id !== row.id);
    selected.value = selected.value.filter((id) => id !== row.id);
    notify('设备已删除');
  } catch (error) {
    notify(error instanceof Error ? error.message : '删除失败', 'error');
  }
}

async function removeSelected() {
  if (!selected.value.length) return;
  if (!(await confirmAction(`确认删除选中的 ${selected.value.length} 台设备？`))) return;
  try {
    await api.batchEquipment([...selected.value], 'delete', '');
    selected.value = [];
    await refreshEquipment();
    notify('已删除所选设备');
  } catch (error) {
    notify(error instanceof Error ? error.message : '批量删除失败', 'error');
  }
}

function exportCsv() {
  const list = filtered.value;
  const exportColumns = selectedColumns.value.filter((c) => c.key !== 'imageFileId');
  const heads = exportColumns.map((c) => c.label);
  const lines = list.map((r) =>
    exportColumns.map((c) => `"${String(r[c.key] ?? '').replaceAll('"', '""')}"`).join(','),
  );
  const blob = new Blob(['\ufeff' + [heads.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = '设备台账.csv';
  a.click();
  URL.revokeObjectURL(a.href);
  notify(`已导出 ${list.length} 条记录`);
}

function formatAmount(value: string) {
  return formatNumber(value);
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
  if (page.value > pageCount.value) page.value = pageCount.value;
});
watch(
  [() => route.name, () => route.params.id, rows],
  () => {
    if (route.name === 'equipment-new') {
      if (!editing.value || !isNew.value) create();
      drawer.value = true;
    } else if (route.name === 'equipment-edit') {
      const row = rows.value.find((item) => item.id === String(route.params.id));
      if (row && (!drawer.value || editing.value?.id !== row.id)) edit(row);
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
onMounted(() => {
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
  <section class="content">
    <AppListTabs
      class="equipment-tabs"
      :active="quickTab"
      :items="EQUIPMENT_TABS.map((tab) => ({ key: tab, label: tab }))"
      @select="selectQuickTab"
      @reselect="selectQuickTab"
    />
    <AppListToolbar class="toolbar">
      <div class="toolbar-left">
        <ElButton type="primary" v-if="can('equipment', '新增')" @click="create">
          <span class="plus"> <AppIcon name="Plus" /> </span>新增</ElButton
        >
        <ElDropdown
          v-if="can('equipment', '编辑') || can('equipment', '删除')"
          trigger="click"
          :disabled="!selected.length"
          @command="openBatch"
        >
          <ElButton :disabled="!selected.length"
            >批量操作<span v-if="selected.length" class="count-pill">{{ selected.length }}</span
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
        <ListSearchInput v-model="query.keyword" placeholder="搜索名称、编码、型号、部门、责任人" />
        <ElButton
          class="advanced-filter-trigger"
          :class="{ active: equipmentAdvanced.length }"
          native-type="button"
          @click="openFilter($event.currentTarget as HTMLElement)"
        >
          添加筛选<span v-if="equipmentAdvanced.length"> ({{ equipmentAdvanced.length }})</span>
        </ElButton>
        <ElButton text class="reset-btn" native-type="button" @click="reset">重置</ElButton>
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
            >范围：{{ equipmentTreeSelection.names[0] }}（含下级）</ElButton
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
              :min-width="col.key === 'imageFileId' ? 72 : col.key === 'code' ? 190 : 150"
              :width="col.key === 'imageFileId' ? 72 : columnWidth(col.key)"
              :resizable="col.resizable ?? true"
              :sortable="col.key === 'imageFileId' ? false : 'custom'"
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
                  ><span :title="row.departmentPath">{{
                    row.departmentPath || row.department
                  }}</span></template
                ><template v-else-if="col.key === 'name'"
                  ><span class="equipment-name-text">{{ row.name }}</span></template
                ><template v-else-if="col.key === 'state' || col.key === 'useState'"
                  ><span
                    v-if="row[col.key]"
                    class="status"
                    :class="[
                      statusClass(String(row[col.key])),
                      { 'equipment-status': col.key === 'state' },
                    ]"
                    ><i></i>{{ row[col.key] }}</span
                  ></template
                ><template v-else-if="col.key === 'amount'"
                  ><span class="amount">{{ formatAmount(row.amount) }}</span></template
                ><template v-else>{{ row[col.key] ?? '' }}</template>
              </template></ElTableColumn
            >
            <!-- @vue-generic {Equipment} -->
            <ElTableColumn
              label="操作"
              class-name="row-actions list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #default="{ row }">
                <ElButton type="primary" link v-if="can('equipment', '编辑')" @click="edit(row)">
                  编辑
                </ElButton>
                <ElButton type="primary" link v-if="can('equipment', '新增')" @click="clone(row)">
                  复制</ElButton
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
                        >删除</ElDropdownItem
                      >
                    </ElDropdownMenu>
                  </template></ElDropdown
                >
              </template></ElTableColumn
            ><template #empty>
              <div v-if="pageRows.length === 0">
                <div class="empty-icon">
                  <AppIcon name="Search" />
                </div>
                <b>{{ rows.length ? '没有找到匹配的设备' : '暂无设备' }}</b
                ><span>{{
                  rows.length ? '调整筛选条件或清空搜索后重试' : '点击“新增”建立第一条设备档案'
                }}</span
                ><ElButton type="primary" link v-if="rows.length" @click="reset"
                  >清空筛选条件</ElButton
                >
              </div>
            </template>
          </AppBusinessTable>
        </div>
        <footer class="table-footer">
          <div class="result-count">
            共 <b>{{ filtered.length }}</b> 条<span v-if="selected.length" class="selected-label"
              >已选 {{ selected.length }} 条</span
            >
          </div>
          <AppPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :total="filtered.length"
          />
          <ListToolbarIcons
            mode="footer"
            :can-export="can('equipment', '导出')"
            :columns-open="showColumns"
            @toggle-columns="showColumns = !showColumns"
            @export="exportCsv"
            ><template #columns>
              <div class="popover-head">
                <b>表格列设置</b
                ><ElButton native-type="button" @click="visibleKeys = [...defaultEquipmentColumns]">
                  恢复默认
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
                {{ col.label }}<small v-if="col.required">固定</small>
              </ElCheckbox>
              <div class="popover-foot">显示 {{ visibleKeys.length }} 列</div>
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
      <p>已选择 {{ selected.length }} 台设备</p>
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
          placeholder="请选择"
        />
        <TreeSelect
          v-else-if="batchForm.operation === 'department'"
          v-model="batchForm.value"
          :nodes="batchDepartmentNodes"
          placeholder="请选择部门"
        />
        <ElSelect
          v-else-if="batchForm.operation === 'owner'"
          v-model="batchForm.ownerIds"
          multiple
          filterable
          clearable
          placeholder="留空则清除负责人"
        >
          <ElOption
            v-for="user in batchOptions.users"
            :key="user.id"
            :value="user.id"
            :label="user.name"
          /> </ElSelect
        ><ElSelect v-else v-model="batchForm.value" placeholder="请选择状态" clearable>
          <ElOption v-for="state in EQUIPMENT_STATES" :key="state" :label="state" :value="state" />
        </ElSelect>
      </label>
    </div>
    <template #footer
      ><ElButton :disabled="batchBusy" @click="batchOpen = false">取消</ElButton
      ><ElButton
        type="primary"
        :loading="batchBusy"
        :disabled="batchForm.operation !== 'owner' && !batchForm.value"
        @click="submitBatch"
      >
        {{ batchBusy ? '保存中…' : '确定' }}
      </ElButton></template
    >
  </AppDialog>
  <EquipmentEditor
    ref="equipmentEditor"
    v-model="editing"
    v-model:open="drawer"
    :is-new="isNew"
    :busy="equipmentSaving || equipmentUploading"
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

/* 设备状态采用轻量语义标签，不改变其他模块的状态样式。 */
.equipment-ledger-table .equipment-status {
  font-size: 13px;
  font-weight: 500;
  line-height: 18px;
  padding: 3px 9px;
  border: 1px solid transparent;
  border-radius: 6px;
}

.equipment-ledger-table .equipment-status.success {
  color: #11934a;
  background: #e8faf0;
  border-color: #d1f2de;
}

.equipment-ledger-table .equipment-status.warning {
  color: #c66a00;
  background: #fff1d6;
  border-color: #ffe4ae;
}

.equipment-ledger-table .equipment-status.danger {
  color: #e33535;
  background: #ffe8e8;
  border-color: #ffd1d1;
}
</style>
