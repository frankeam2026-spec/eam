<script setup lang="ts">
import { formatNumber } from './utils/number';
import RowMoreActions from './components/common/RowMoreActions.vue';
import { inventoryMenu } from './inventory';
import { maintenanceMenu, inspectionMenu } from './rolePermissions';
import { useUnsavedChanges } from './composables/useUnsavedChanges';
import RepairNotificationBell from './components/common/RepairNotificationBell.vue';
import ThemePicker from './components/common/ThemePicker.vue';
import HelpMenu from './components/common/HelpMenu.vue';
import UserAccountMenu from './components/common/UserAccountMenu.vue';
import AdvancedFilterDrawer from './components/common/AdvancedFilterDrawer.vue';
import AppliedFilterChips from './components/common/AppliedFilterChips.vue';
import { matchesAdvanced } from './query/advancedFilters';
import type { FilterCondition, FilterField } from './query/advancedFilters';
import ListSearchInput from './components/common/ListSearchInput.vue';
import AppPagination from './components/common/AppPagination.vue';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
import AppDialog from './components/common/AppDialog.vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from './stores/auth';
import { useUiStore } from './stores/ui';
import { useBaseDataStore, BASE_TABS } from './stores/baseData';
import type { AppPage } from './router';
import * as api from './api';
import TreeSelect, { type TreeSelectNode } from './components/TreeSelect.vue';
import EquipmentEditor from './components/EquipmentEditor.vue';
import { confirmAction } from './services/dialog';
import ListToolbarIcons from './components/ListToolbarIcons.vue';
import LoginScreen from './components/LoginScreen.vue';
import EquipmentHistory from './components/EquipmentHistory.vue';
import EquipmentImage from './components/common/EquipmentImage.vue';
import { EQUIPMENT_STATES, EQUIPMENT_TABS, normalizeEquipmentState } from './config/equipment';

type Equipment = api.EquipmentRecord;
type Column = { key: keyof Equipment; label: string; width: number; required?: boolean };
type BaseNode = { id: string; code: string; name: string; parentId: string; status: string };
type BaseValue = { id: string; code: string; name: string; precision?: number; status: string };

const rows = ref<Equipment[]>([]);
const apiMode = ref(false);
const auth = useAuthStore();
const ui = useUiStore();
const baseData = useBaseDataStore();
const { palette, collapsed: desktopCollapsed } = storeToRefs(ui);
const compactQuery = window.matchMedia('(max-width:1060px)');
const compactViewport = ref(compactQuery.matches);
const compactExpanded = ref(false);
const collapsed = computed({
  get: () => (compactViewport.value ? !compactExpanded.value : desktopCollapsed.value),
  set: (value: boolean) => {
    if (compactViewport.value) compactExpanded.value = !value;
    else desktopCollapsed.value = value;
  },
});
function navigationViewportChanged(event: MediaQueryListEvent) {
  compactViewport.value = event.matches;
  compactExpanded.value = false;
  flyoutGroup.value = null;
}
const helpSection = ref('');
const {
  currentUser,
  currentCompany,
  currentDepartment,
  developmentMode,
  authState,
  authMessage,
  grantedActions,
} = storeToRefs(auth);
const {
  categoryData,
  materialCategoryData,
  locationData,
  faultTypeData,
  faultCauseData,
  unitData,
  useStateData,
  sourceData,
  processingResultData,
} = storeToRefs(baseData);
const router = useRouter(),
  route = useRoute();
const historyId = computed(() =>
  route.name === 'equipment-history' ? String(route.params.id || '') : '',
);
function openHistory(row: Equipment) {
  router.push({ name: 'equipment-history', params: { id: row.id } });
}
function closeHistory() {
  router.push({ name: 'equipment' });
}
const batchOpen = ref(false),
  batchMenu = ref(false),
  batchBusy = ref(false);
const batchForm = reactive({ operation: 'category', value: '' });
const batchOptions = ref<{ departments: api.OrganizationRecord[]; users: api.SystemUserRecord[] }>({
  departments: [],
  users: [],
});
const batchLabels: Record<string, string> = {
  location: '修改位置',
  category: '修改分类',
  department: '修改部门',
  owner: '修改负责人',
  state: '修改状态',
  delete: '删除',
};
const batchDepartmentNodes = computed(() =>
  batchOptions.value.departments.map((x) => ({ ...x, value: x.id })),
);
async function openBatch(operation: string) {
  batchMenu.value = false;
  batchForm.operation = operation;
  batchForm.value = '';
  if (operation === 'delete') {
    await removeSelected();
    return;
  }
  try {
    if (operation === 'department' || operation === 'owner')
      batchOptions.value = await api.equipmentBatchOptions();
    batchOpen.value = true;
  } catch (e) {
    notify(e instanceof Error ? e.message : '加载失败');
  }
}
async function submitBatch() {
  if (!batchForm.value || batchBusy.value) return;
  batchBusy.value = true;
  try {
    const result = await api.batchEquipment(
      [...selected.value],
      batchForm.operation,
      batchForm.value,
    );
    batchOpen.value = false;
    selected.value = [];
    await refreshEquipment();
    notify(`已修改 ${result.data.count} 台设备`);
  } catch (e) {
    notify(e instanceof Error ? e.message : '批量修改失败');
  } finally {
    batchBusy.value = false;
  }
}
function can(menu: string, action = '查看') {
  return grantedActions.value[menu]?.includes(action) === true;
}
function canMenu(item: MenuItem) {
  if (item.maintenanceKind === 'pending-issues' && !can('inventoryOutbound', '确认出库'))
    return false;
  const key =
    item.baseTab === '设备分类'
      ? 'category'
      : item.page === 'inventory'
        ? inventoryMenu(item.maintenanceKind || 'balances')
        : item.page === 'inspection'
          ? inspectionMenu(item.maintenanceKind || 'daily-tasks')
          : item.page === 'maintenance'
            ? maintenanceMenu(item.maintenanceKind || 'tasks')
            : item.page === 'labels'
              ? 'equipmentTag'
              : item.page === 'equipment'
                ? 'equipment'
                : item.page === 'repairs'
                  ? 'repair'
                  : item.page === 'organization'
                    ? 'org'
                    : item.page === 'users'
                      ? 'user'
                      : item.page === 'roles'
                        ? 'role'
                        : item.page === 'locations'
                          ? 'location'
                          : item.page === 'base'
                            ? 'base'
                            : item.label === '工作台'
                              ? 'workspace'
                              : null;
  return key ? can(key) : false;
}
const currentPage = computed<AppPage>({
  get: () => route.meta.page as AppPage,
  set: (page) => {
    router.push(
      page === 'maintenance'
        ? { name: page, params: { kind: 'tasks' } }
        : page === 'inspection'
          ? { name: page, params: { kind: 'daily-tasks' } }
          : { name: page },
    );
  },
});
type MenuItem = {
  label: string;
  icon: string;
  page?: AppPage;
  baseTab?: string;
  maintenanceKind?: string;
};
type MenuGroup = { id: string; label: string; icon: string; items: MenuItem[] };
const workspaceMenu: MenuGroup = {
  id: 'workspace',
  label: '工作台',
  icon: '▦',
  items: [{ label: '工作台', icon: '▦', page: 'workspace' }],
};
const navGroups: MenuGroup[] = [
  {
    id: 'assets',
    label: '设备管理',
    icon: '▣',
    items: [
      { label: '设备台账', icon: '▣', page: 'equipment' },
      { label: '设备标签', icon: '▤', page: 'labels' },
    ],
  },
  {
    id: 'operations',
    label: '运维管理',
    icon: '⚒',
    items: [{ label: '维修工单', icon: '⚒', page: 'repairs' }],
  },
  {
    id: 'maintenance',
    label: '保养管理',
    icon: '◷',
    items: [
      { label: '保养类型', icon: '◷', page: 'maintenance', maintenanceKind: 'types' },
      { label: '保养标准', icon: '▤', page: 'maintenance', maintenanceKind: 'standards' },
      { label: '保养计划', icon: '▦', page: 'maintenance', maintenanceKind: 'plans' },
      { label: '保养任务', icon: '✓', page: 'maintenance', maintenanceKind: 'tasks' },
    ],
  },
  {
    id: 'inspection',
    label: '点巡检管理',
    icon: '✓',
    items: [
      { label: '点巡检标准', icon: '▤', page: 'inspection', maintenanceKind: 'standards' },
      { label: '日常点检计划', icon: '▦', page: 'inspection', maintenanceKind: 'daily-plans' },
      { label: '日常点检任务', icon: '✓', page: 'inspection', maintenanceKind: 'daily-tasks' },
      { label: '周期巡检计划', icon: '◷', page: 'inspection', maintenanceKind: 'periodic-plans' },
      { label: '周期巡检任务', icon: '✓', page: 'inspection', maintenanceKind: 'periodic-tasks' },
    ],
  },
  {
    id: 'spares',
    label: '库存管理',
    icon: '▧',
    items: [
      { label: '物料档案', icon: '▤', page: 'inventory', maintenanceKind: 'materials' },
      { label: '仓库管理', icon: '▧', page: 'inventory', maintenanceKind: 'warehouses' },
      { label: '入库管理', icon: '⇣', page: 'inventory', maintenanceKind: 'receipts' },
      { label: '领料申请', icon: '⇢', page: 'inventory', maintenanceKind: 'requisitions' },
      { label: '出库管理', icon: '⇢', page: 'inventory', maintenanceKind: 'outbounds' },
      { label: '库存查询', icon: '▦', page: 'inventory', maintenanceKind: 'balances' },
      { label: '退料管理', icon: '↩', page: 'inventory', maintenanceKind: 'returns' },
      { label: '调拨管理', icon: '⇄', page: 'inventory', maintenanceKind: 'transfers' },
      { label: '盘点管理', icon: '▤', page: 'inventory', maintenanceKind: 'counts' },
      { label: '期初与初始化', icon: '▧', page: 'inventory', maintenanceKind: 'openings' },
      { label: '单据纠正', icon: '↶', page: 'inventory', maintenanceKind: 'reversals' },
    ],
  },
  {
    id: 'system',
    label: '系统管理',
    icon: '⚙',
    items: [
      { label: '组织架构', icon: '⌘', page: 'organization' },
      { label: '用户管理', icon: '♙', page: 'users' },
      { label: '角色权限', icon: '⚿', page: 'roles' },
      { label: '存放地点', icon: '⌖', page: 'locations' },
      { label: '基础数据', icon: '▦', page: 'base' },
    ],
  },
];
const menuSearch = ref('');
const matchingNavGroups = computed(() =>
  navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) =>
          canMenu(item) &&
          (collapsed.value ||
            !menuSearch.value.trim() ||
            (group.label + item.label).includes(menuSearch.value.trim())),
      ),
    }))
    .filter((group) => group.items.length),
);
const navExpanded = ref<Record<string, boolean>>({
  assets: true,
  operations: true,
  inspection: true,
  maintenance: true,
  spares: true,
  system: true,
});
const flyoutGroup = ref<string | null>(null),
  flyoutTop = ref(72);
let flyoutCloseTimer = 0;
const activeFlyout = computed(() =>
  flyoutGroup.value === 'workspace'
    ? workspaceMenu
    : navGroups.find((group) => group.id === flyoutGroup.value) || null,
);
function navItemActive(item: MenuItem) {
  return (
    item.page === currentPage.value &&
    (item.page !== 'inventory' || !!item.maintenanceKind || route.name === 'inventory') &&
    (!item.baseTab || baseTab.value === item.baseTab) &&
    (!item.maintenanceKind ||
      route.params.kind === item.maintenanceKind ||
      (item.maintenanceKind === 'pending-issues' &&
        route.name === 'inventory-requisitions' &&
        route.query.queue === 'pending') ||
      (['requisitions', 'pending-issues'].includes(item.maintenanceKind || '') &&
        String(route.name).startsWith('inventory-requisition') &&
        (item.maintenanceKind === 'pending-issues'
          ? route.query.queue === 'pending'
          : route.query.queue !== 'pending')) ||
      (item.maintenanceKind === 'outbounds' &&
        String(route.name).startsWith('inventory-outbound')) ||
      (item.maintenanceKind === 'balances' && route.name === 'inventory-queries') ||
      (item.maintenanceKind === 'receipts' && String(route.name).startsWith('inventory-receipt')) ||
      (item.maintenanceKind === 'tasks' && route.name === 'maintenance-task') ||
      (route.name === 'inspection-task' && route.query.kind === item.maintenanceKind))
  );
}
function navGroupActive(group: MenuGroup) {
  return group.items.some(navItemActive);
}
function showNavFlyout(event: MouseEvent, group: MenuGroup) {
  if (!collapsed.value) return;
  window.clearTimeout(flyoutCloseTimer);
  flyoutGroup.value = group.id;
  const rect = (event.currentTarget as HTMLElement).getBoundingClientRect();
  const panelHeight = 48 + group.items.length * 42 + 12;
  flyoutTop.value = Math.max(8, Math.min(rect.top, window.innerHeight - panelHeight - 8));
}
function openNavFlyout(group: MenuGroup) {
  if (!collapsed.value) {
    navExpanded.value[group.id] = !navExpanded.value[group.id];
    return;
  }
  window.clearTimeout(flyoutCloseTimer);
  flyoutGroup.value = flyoutGroup.value === group.id ? null : group.id;
  const element = document.querySelector(`[data-nav-group="${group.id}"]`);
  if (element) {
    const rect = element.getBoundingClientRect();
    flyoutTop.value = Math.max(
      8,
      Math.min(rect.top, window.innerHeight - (48 + group.items.length * 42 + 20)),
    );
  }
}
function delayHideNavFlyout() {
  window.clearTimeout(flyoutCloseTimer);
  flyoutCloseTimer = window.setTimeout(() => (flyoutGroup.value = null), 160);
}
function keepNavFlyout() {
  window.clearTimeout(flyoutCloseTimer);
}
function activateNav(item: MenuItem) {
  if (!canMenu(item)) return;
  if (item.page)
    router.push({
      name:
        item.page === 'inventory' && item.maintenanceKind
          ? ['returns', 'transfers', 'counts', 'openings', 'reversals'].includes(
              item.maintenanceKind,
            )
            ? 'inventory-operations'
            : ['outbounds', 'pending-issues'].includes(item.maintenanceKind || '')
              ? 'inventory-outbounds'
              : item.maintenanceKind === 'receipts'
                ? 'inventory-receipts'
                : ['requisitions', 'pending-issues'].includes(item.maintenanceKind || '')
                  ? 'inventory-requisitions'
                  : item.maintenanceKind === 'balances'
                    ? 'inventory-queries'
                    : 'inventory-masters'
          : item.page,
      params:
        item.page === 'maintenance' ||
        item.page === 'inspection' ||
        (item.page === 'inventory' && item.maintenanceKind)
          ? { kind: item.maintenanceKind || 'tasks' }
          : {},
      query:
        item.maintenanceKind === 'pending-issues'
          ? { queue: 'pending' }
          : item.page === 'base'
            ? { tab: item.baseTab || '单位' }
            : {},
    });
  else notify(`${item.label}模块正在开发中`);
  flyoutGroup.value = null;
}
const quickTab = ref('全部设备');
const baseTab = computed({
  get: () => String(route.query.tab || (can('base') ? '单位' : '设备分类')),
  set: (tab) => {
    router.replace({ name: 'base', query: { tab } });
  },
});
const baseDialog = ref(false),
  baseEditId = ref('');
const baseForm = reactive({ code: '', name: '', parentId: '', precision: 0 });
const baseSaving = ref(false);
const baseFormGuard = useUnsavedChanges(
  () => baseForm,
  () => baseDialog.value,
  () => baseSaving.value,
);
async function closeBaseDialog() {
  if (!(await baseFormGuard.permitLeave())) return;
  baseDialog.value = false;
}
const query = reactive({
  keyword: '',
  category: '',
  department: '',
  state: '',
  company: '',
  model: '',
  serial: '',
  owner: '',
  brand: '',
  useState: '',
  source: '',
  acquiredFrom: '',
  acquiredTo: '',
  warrantyBefore: '',
});
const appliedQuery = ref({ ...query });
const advancedOpen = ref(false);
const equipmentAdvanced = ref<FilterCondition[]>([]);

const columnFiltersOpen = ref(false);
const columnFilters = reactive<Record<string, string>>({});
const sortKey = ref<keyof Equipment | null>(null),
  sortDirection = ref<'asc' | 'desc' | null>(null);
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
const page = ref(1),
  pageSize = ref(10),
  selected = ref<string[]>([]);
const showColumns = ref(false),
  editing = ref<Equipment | null>(null),
  drawer = ref(false),
  isNew = ref(false);
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

const toast = ref(''),
  toastTimer = ref<number>();
const columns: Column[] = [
  { key: 'imageFileId', label: '图片', width: 72 },
  { key: 'code', label: '设备编码', width: 154, required: true },
  { key: 'name', label: '设备名称', width: 150, required: true },
  { key: 'state', label: '设备状态', width: 100 },
  { key: 'useState', label: '使用状态', width: 100 },
  { key: 'assetNo', label: '资产编号', width: 132 },
  { key: 'category', label: '设备分类', width: 124 },
  { key: 'model', label: '规格型号', width: 138 },
  { key: 'serial', label: '出厂编号', width: 140 },
  { key: 'department', label: '所属部门', width: 142 },
  { key: 'owner', label: '责任人', width: 92 },
  { key: 'brand', label: '品牌', width: 116 },
  { key: 'source', label: '设备来源', width: 100 },
  { key: 'unit', label: '单位', width: 80 },
  { key: 'functionLocation', label: '功能位置', width: 150 },
  { key: 'supplier', label: '供应商', width: 130 },
  { key: 'acquiredAt', label: '购置日期', width: 116 },
  { key: 'amount', label: '采购金额（元）', width: 122 },
  { key: 'warranty', label: '保修期至', width: 116 },
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
      ['specialEquipment', '特种设备'],
      ['metrologyEquipment', '计量设备'],
      ['depreciationEnabled', '开启折旧'],
      ['technicalParams', '技术参数'],
      ['remark', '备注'],
    ].map(([key, label]) => ({ key: key!, label: label!, type: 'text' as const })),
  ].map((f) => {
    if (f.key === 'category') return { ...f, type: 'tree', nodes: categoryData.value };
    if (f.key === 'functionLocation')
      return { ...f, type: 'tree', nodes: locationData.value, pathValue: true };
    if (['acquiredAt', 'warranty', 'inServiceDate', 'retirementDate'].includes(f.key))
      return { ...f, type: 'date', dateOnly: true };
    if (f.key === 'amount') return { ...f, type: 'number' };
    if (f.key === 'state') return { ...f, type: 'choice', options: [...EQUIPMENT_STATES] };
    if (['specialEquipment', 'metrologyEquipment', 'depreciationEnabled'].includes(f.key))
      return { ...f, type: 'choice', options: ['是', '否'] };
    const options =
      f.key === 'unit'
        ? unitData.value
        : f.key === 'useState'
          ? useStateData.value
          : f.key === 'source'
            ? sourceData.value
            : null;
    return options ? { ...f, type: 'choice', options: options.map((v) => v.name) } : f;
  }),
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
const treeData = computed(() =>
  baseTab.value === '设备分类'
    ? categoryData.value
    : baseTab.value === '物料分类'
      ? materialCategoryData.value
      : baseTab.value === '故障类型'
        ? faultTypeData.value
        : locationData.value,
);
const currentBaseValues = computed(() =>
  baseTab.value === '单位'
    ? unitData.value
    : baseTab.value === '使用状态'
      ? useStateData.value
      : baseTab.value === '处理结果'
        ? processingResultData.value
        : baseTab.value === '故障原因'
          ? faultCauseData.value
          : sourceData.value,
);
const baseSearch = ref(''),
  appliedBaseSearch = ref(''),
  baseColumnFilterOpen = ref(false),
  baseColumnsOpen = ref(false),
  baseColumnFilters = reactive<Record<string, string>>({});
const baseVisibleColumns = ref<string[]>([]);
const isBaseTree = computed(() =>
  ['设备分类', '物料分类', '存放地点', '故障类型'].includes(baseTab.value),
);
function defaultBaseColumns() {
  return isBaseTree.value
    ? ['name', 'code', 'parent']
    : baseTab.value === '单位'
      ? ['name', 'code', 'precision']
      : ['name', 'code', 'status'];
}
if (!baseVisibleColumns.value.length) baseVisibleColumns.value = defaultBaseColumns();
watch(baseTab, () => {
  baseSearch.value = '';
  appliedBaseSearch.value = '';
  baseVisibleColumns.value = defaultBaseColumns();
  Object.keys(baseColumnFilters).forEach((key) => delete baseColumnFilters[key]);
});
function baseLevel(item: BaseNode | BaseValue) {
  return 'level' in item ? item.level : 0;
}
function baseCell(item: BaseNode | BaseValue, key: string) {
  if (key === 'parent')
    return (
      treeData.value.find((x) => x.id === ('parentId' in item ? item.parentId : ''))?.name || ''
    );
  if (key === 'precision') return 'precision' in item ? `${item.precision ?? 0} 位` : '';
  return String((item as BaseNode & BaseValue)[key as keyof BaseNode] ?? '');
}
function baseMatches(item: BaseNode | BaseValue) {
  const term = appliedBaseSearch.value.trim().toLowerCase();
  const text = [
    item.name,
    item.code,
    'parentId' in item ? treeData.value.find((x) => x.id === item.parentId)?.name : '',
    'precision' in item ? String(item.precision) : '',
    item.status,
  ]
    .join(' ')
    .toLowerCase();
  return (
    (!term || text.includes(term)) &&
    Object.entries(baseColumnFilters).every(
      ([key, value]) =>
        !value || baseCell(item, key).toLowerCase().includes(value.trim().toLowerCase()),
    )
  );
}
const filteredTreeRows = computed(() => treeRows.value.filter(baseMatches));
const filteredBaseValues = computed(() => currentBaseValues.value.filter(baseMatches));
function searchBase() {
  appliedBaseSearch.value = baseSearch.value;
}
function resetBase() {
  baseSearch.value = '';
  appliedBaseSearch.value = '';
  Object.keys(baseColumnFilters).forEach((key) => delete baseColumnFilters[key]);
}
function exportBaseCsv() {
  const source = isBaseTree.value ? filteredTreeRows.value : filteredBaseValues.value;
  const keys = baseVisibleColumns.value;
  const headers = keys.map((key) =>
    key === 'name'
      ? baseTab.value === '设备分类'
        ? '分类名称'
        : baseTab.value === '存放地点'
          ? '地点名称'
          : baseTab.value === '故障类型'
            ? '故障类型'
            : '名称'
      : key === 'code'
        ? '编码'
        : key === 'parent'
          ? '上级节点'
          : key === 'precision'
            ? '小数精度'
            : '状态',
  );
  const lines = source.map((item) =>
    keys.map((key) => `\"${baseCell(item, key).replaceAll('\"', '\"\"')}\"`).join(','),
  );
  const blob = new Blob(['\ufeff' + [headers.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${baseTab.value}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}
function toggleBaseColumn(key: string) {
  baseVisibleColumns.value = baseVisibleColumns.value.includes(key)
    ? baseVisibleColumns.value.filter((x) => x !== key)
    : [...defaultBaseColumns().filter((x) => baseVisibleColumns.value.includes(x) || x === key)];
}
const treeRows = computed(() => {
  const result: Array<BaseNode & { level: number }> = [];
  const add = (parentId: string, level: number) => {
    treeData.value
      .filter((x) => x.parentId === parentId)
      .forEach((item) => {
        result.push({ ...item, level });
        if (expandedTreeIds.value[item.id] !== false) add(item.id, level + 1);
      });
  };
  add('', 0);
  return result;
});
const parentOptions = computed<TreeSelectNode[]>(() =>
  treeData.value
    .filter(
      (item) =>
        item.id !== baseEditId.value &&
        (!baseEditId.value || !isDescendant(item.id, baseEditId.value, treeData.value)),
    )
    .map((item) => ({
      ...item,
      value: item.id,
      status: undefined,
      disabled: item.status === '停用',
    })),
);
const activeCategories = computed(() => categoryData.value.filter((x) => x.status === '启用'));
const categoryTreeOptions = computed<TreeSelectNode[]>(() => activeCategories.value);
const locationTreeOptions = computed<TreeSelectNode[]>(() =>
  locationData.value.filter((x) => x.status === '启用'),
);
const baseTabs = BASE_TABS;
const expandedTreeIds = ref<Record<string, boolean>>({});
const filtered = computed(() => {
  const term = appliedQuery.value.keyword.trim().toLocaleLowerCase();
  return rows.value.filter((r) => {
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
    const matchesQuickState = !appliedQuery.value.state || r.state === appliedQuery.value.state;
    const matchesColumns = Object.entries(columnFilters).every(
      ([key, value]) =>
        !value ||
        String(r[key as keyof Equipment] ?? '')
          .toLocaleLowerCase()
          .includes(value.trim().toLocaleLowerCase()),
    );
    return (
      matchesKeyword &&
      matchesQuickState &&
      matchesColumns &&
      matchesAdvanced(equipmentAdvanced.value, (key) => r[key as keyof Equipment])
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
const allPageSelected = computed(
  () => pageRows.value.length > 0 && pageRows.value.every((r) => selected.value.includes(r.id)),
);
watch(visibleKeys, (v) => localStorage.setItem('eam-columns', JSON.stringify(v)), { deep: true });
watch(columnWidths, (v) => localStorage.setItem('eam-column-widths', JSON.stringify(v)), {
  deep: true,
});
watch(filtered, () => {
  if (page.value > pageCount.value) page.value = pageCount.value;
});
onMounted(() => {
  document.documentElement.dataset.theme = palette.value;
  compactQuery.addEventListener('change', navigationViewportChanged);
  window.addEventListener('keydown', onKey);
  window.addEventListener('pointermove', resizeMove);
  window.addEventListener('pointerup', resizeEnd);
  window.addEventListener('eam-session-expired', sessionExpired);
  initialize();
});
onUnmounted(() => {
  window.clearTimeout(flyoutCloseTimer);
  compactQuery.removeEventListener('change', navigationViewportChanged);
  window.removeEventListener('keydown', onKey);
  window.removeEventListener('pointermove', resizeMove);
  window.removeEventListener('pointerup', resizeEnd);
  window.removeEventListener('eam-session-expired', sessionExpired);
});
function sessionExpired() {
  auth.expire();
  baseData.clear();
  apiMode.value = false;
  rows.value = [];
}
async function initialize() {
  await auth.load();
  apiMode.value = authState.value === 'ready';
  if (apiMode.value) await connectBackend();
}
watch(
  () => route.fullPath,
  () => {
    drawer.value = false;
    showColumns.value = false;
    baseDialog.value = false;
  },
);
async function signIn(tenantId: string, account: string, password: string) {
  try {
    await api.login(tenantId, account, password);
    await initialize();
  } catch (error) {
    authState.value = 'login';
    authMessage.value = error instanceof Error ? error.message : '登录失败';
  }
}
async function signOut() {
  try {
    await api.logout();
  } finally {
    sessionExpired();
    authMessage.value = '';
  }
}
function onKey(e: KeyboardEvent) {
  if (e.key === 'Escape') {
    if (e.defaultPrevented || document.querySelector('.eam-unsaved-confirmation')) return;
    if (drawer.value || advancedOpen.value) return;
    showColumns.value = false;
  }
}
function notify(message: string) {
  toast.value = message;
  window.clearTimeout(toastTimer.value);
  toastTimer.value = window.setTimeout(() => (toast.value = ''), 2400);
}
async function connectBackend() {
  if (can('equipment')) {
    try {
      const result = await api.listEquipment({ page: 1, size: 10000 });
      rows.value = result.data;
    } catch (error) {
      rows.value = [];
      notify(error instanceof Error ? error.message : '设备加载失败');
    }
  }
  await Promise.all(
    [...baseTabs, '存放地点'].map(async (tab) => {
      if (
        !can(tab === '设备分类' ? 'category' : 'base') &&
        !can('equipment') &&
        !(['故障类型', '故障原因'].includes(tab) && can('repair'))
      )
        return;
      try {
        await baseData.load(tab);
      } catch (error) {
        notify(error instanceof Error ? error.message : '基础数据加载失败');
      }
    }),
  );
}
async function refreshEquipment() {
  if (!apiMode.value) return;
  try {
    const q = appliedQuery.value;
    const result = await api.listEquipment({
      page: 1,
      size: 10000,
      keyword: q.keyword,
      category: q.category,
      state: q.state,
      company: q.company,
      department: q.department,
      model: q.model,
      serial: q.serial,
      owner: q.owner,
      brand: q.brand,
      useState: q.useState,
      source: q.source,
      acquiredFrom: q.acquiredFrom,
      acquiredTo: q.acquiredTo,
      warrantyBefore: q.warrantyBefore,
    });
    rows.value = result.data;
  } catch (error) {
    notify(error instanceof Error ? error.message : '设备列表加载失败');
  }
}
async function refreshBase(tab = baseTab.value) {
  if (!apiMode.value) return;
  try {
    await baseData.load(tab);
  } catch (error) {
    notify(error instanceof Error ? error.message : '基础数据加载失败');
  }
}
async function search() {
  quickTab.value = query.state || '全部设备';
  appliedQuery.value = { ...query };
  page.value = 1;
  selected.value = [];
  await refreshEquipment();
}
function reset() {
  equipmentAdvanced.value = [];
  Object.assign(query, {
    keyword: '',
    category: '',
    department: '',
    state: '',
    company: '',
    model: '',
    serial: '',
    owner: '',
    brand: '',
    useState: '',
    source: '',
    acquiredFrom: '',
    acquiredTo: '',
    warrantyBefore: '',
  });
  quickTab.value = '全部设备';
  Object.keys(columnFilters).forEach((key) => delete columnFilters[key]);
  search();
}
function selectQuickTab(tab: string) {
  quickTab.value = tab;
  query.state = tab === '全部设备' ? '' : tab;
  search();
}
function sortBy(key: keyof Equipment) {
  if (key === 'imageFileId') return;
  if (sortKey.value !== key) {
    sortKey.value = key;
    sortDirection.value = 'asc';
  } else if (sortDirection.value === 'asc') sortDirection.value = 'desc';
  else if (sortDirection.value === 'desc') {
    sortKey.value = null;
    sortDirection.value = null;
  } else sortDirection.value = 'asc';
  page.value = 1;
}
function columnWidth(key: keyof Equipment) {
  return columnWidths.value[key] || columns.find((c) => c.key === key)?.width || 100;
}
function startResize(event: PointerEvent, key: keyof Equipment) {
  resizeOrigin.value = { key, x: event.clientX, width: columnWidth(key) };
  document.body.classList.add('resizing-column');
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
function openBaseCreate(parentId = '') {
  baseEditId.value = '';
  Object.assign(baseForm, { code: '', name: '', parentId, precision: 0 });
  baseFormGuard.markSaved();
  baseDialog.value = true;
}
function openBaseEdit(item: BaseNode | BaseValue) {
  baseEditId.value = item.id;
  Object.assign(baseForm, {
    code: item.code,
    name: item.name,
    parentId: 'parentId' in item ? item.parentId : '',
    precision: 'precision' in item ? item.precision || 0 : 0,
  });
  baseFormGuard.markSaved();
  baseDialog.value = true;
}
async function saveBase() {
  if (baseSaving.value) return;
  if (!baseForm.name.trim()) {
    notify('请填写名称');
    return;
  }
  if (
    baseTab.value === '设备分类' ||
    baseTab.value === '物料分类' ||
    baseTab.value === '存放地点' ||
    baseTab.value === '故障类型'
  ) {
    const target = treeData.value;
    if (
      baseForm.code.trim() &&
      target.some((x) => x.code === baseForm.code.trim() && x.id !== baseEditId.value)
    ) {
      notify('编码已存在');
      return;
    }
    if (
      baseEditId.value &&
      baseForm.parentId &&
      isDescendant(baseForm.parentId, baseEditId.value, target)
    ) {
      notify('不能将节点移动到自己的下级');
      return;
    }
  } else if (
    baseForm.code.trim() &&
    currentBaseValues.value.some(
      (x) => x.code === baseForm.code.trim() && x.id !== baseEditId.value,
    )
  ) {
    notify('编码已存在');
    return;
  }
  const payload = {
    id: baseEditId.value,
    code: baseForm.code.trim(),
    name: baseForm.name.trim(),
    parentId: baseForm.parentId,
    precision: baseForm.precision,
    status: '启用',
  };
  baseSaving.value = true;
  try {
    if (apiMode.value) {
      const saved = await api.saveBase(baseTab.value, payload, !baseEditId.value);
      const item = {
        ...saved.data,
        parentId: saved.data.parentId || '',
        status: saved.data.status,
      };
      if (baseTab.value === '设备分类')
        categoryData.value = baseEditId.value
          ? categoryData.value.map((x) => (x.id === item.id ? item : x))
          : [...categoryData.value, item];
      else if (baseTab.value === '物料分类')
        materialCategoryData.value = baseEditId.value
          ? materialCategoryData.value.map((x) => (x.id === item.id ? item : x))
          : [...materialCategoryData.value, item];
      else if (baseTab.value === '存放地点')
        locationData.value = baseEditId.value
          ? locationData.value.map((x) => (x.id === item.id ? item : x))
          : [...locationData.value, item];
      else if (baseTab.value === '故障类型')
        faultTypeData.value = baseEditId.value
          ? faultTypeData.value.map((x) => (x.id === item.id ? item : x))
          : [...faultTypeData.value, item];
      else if (baseTab.value === '故障原因')
        faultCauseData.value = baseEditId.value
          ? faultCauseData.value.map((x) => (x.id === item.id ? item : x))
          : [...faultCauseData.value, item];
      else if (baseTab.value === '单位')
        unitData.value = baseEditId.value
          ? unitData.value.map((x) => (x.id === item.id ? item : x))
          : [...unitData.value, item];
      else if (baseTab.value === '使用状态')
        useStateData.value = baseEditId.value
          ? useStateData.value.map((x) => (x.id === item.id ? item : x))
          : [...useStateData.value, item];
      else if (baseTab.value === '处理结果')
        processingResultData.value = baseEditId.value
          ? processingResultData.value.map((x) => (x.id === item.id ? item : x))
          : [...processingResultData.value, item];
      else
        sourceData.value = baseEditId.value
          ? sourceData.value.map((x) => (x.id === item.id ? item : x))
          : [...sourceData.value, item];
    } else if (
      baseTab.value === '设备分类' ||
      baseTab.value === '物料分类' ||
      baseTab.value === '存放地点' ||
      baseTab.value === '故障类型'
    ) {
      const target = treeData.value,
        item = { ...payload, id: baseEditId.value || crypto.randomUUID() };
      if (baseEditId.value) {
        const i = target.findIndex((x) => x.id === baseEditId.value);
        if (i >= 0) target[i] = item;
      } else target.push(item);
    } else {
      const target = currentBaseValues.value,
        item = { ...payload, id: baseEditId.value || crypto.randomUUID() };
      if (baseEditId.value) {
        const i = target.findIndex((x) => x.id === baseEditId.value);
        if (i >= 0) target[i] = item;
      } else target.push(item);
    }
    baseFormGuard.markSaved();
    baseDialog.value = false;
    notify('基础数据已保存');
  } catch (error) {
    notify(error instanceof Error ? error.message : '保存失败');
  } finally {
    baseSaving.value = false;
  }
}
function isDescendant(candidateId: string, parentId: string, items: BaseNode[]): boolean {
  const children = items.filter((x) => x.parentId === parentId);
  return children.some((x) => x.id === candidateId || isDescendant(candidateId, x.id, items));
}
async function deleteBase(item: BaseNode | BaseValue) {
  if ('parentId' in item) {
    const target = treeData.value;
    if (target.some((x) => x.parentId === item.id)) {
      notify('该节点下还有子节点，不能删除');
      return;
    }
    if (baseTab.value === '设备分类' && rows.value.some((x) => x.category === item.name)) {
      notify('该分类已有设备使用，不能删除');
      return;
    }
    if (baseTab.value === '故障类型') {
      try {
        const repairs = JSON.parse(localStorage.getItem('eam-repair-orders-v1') || '[]') as Array<{
          faultType?: string;
        }>;
        if (repairs.some((row) => row.faultType === item.name)) {
          notify('该故障类型已有维修工单使用，不能删除');
          return;
        }
      } catch {}
    }
  }
  if (!(await confirmAction(`确认删除“${item.name}”？`))) return;
  try {
    if (apiMode.value) await api.deleteBase(baseTab.value, item.id);
    if (baseTab.value === '设备分类')
      categoryData.value = categoryData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '物料分类')
      materialCategoryData.value = materialCategoryData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '存放地点')
      locationData.value = locationData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '故障类型')
      faultTypeData.value = faultTypeData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '故障原因')
      faultCauseData.value = faultCauseData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '单位')
      unitData.value = unitData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '使用状态')
      useStateData.value = useStateData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '处理结果')
      processingResultData.value = processingResultData.value.filter((x) => x.id !== item.id);
    else sourceData.value = sourceData.value.filter((x) => x.id !== item.id);
    notify('已删除');
  } catch (error) {
    notify(error instanceof Error ? error.message : '删除失败');
  }
}
async function toggleBaseStatus(item: BaseValue) {
  const status = item.status === '启用' ? '停用' : '启用';
  try {
    if (apiMode.value) await api.changeBaseStatus(baseTab.value, item, status);
    item.status = status;
  } catch (error) {
    notify(error instanceof Error ? error.message : '状态更新失败');
  }
}
function toggleSidebar() {
  collapsed.value = !collapsed.value;
  flyoutGroup.value = null;
}
function togglePage() {
  if (allPageSelected.value)
    selected.value = selected.value.filter((id) => !pageRows.value.some((r) => r.id === id));
  else selected.value = [...new Set([...selected.value, ...pageRows.value.map((r) => r.id)])];
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
async function save() {
  if (!editing.value || equipmentSaving.value || equipmentUploading.value) return;
  const f = editing.value;
  if (!f.name.trim() || !f.category || !f.unit || !f.functionLocation) {
    notify('请填写设备名称、单位、类别和功能位置');
    return;
  }
  if (!f.company || !f.department) {
    notify('请填写所属公司和所属部门');
    return;
  }
  if (
    isNew.value &&
    f.code.trim() &&
    rows.value.some((row) => row.code.toLocaleLowerCase() === f.code.trim().toLocaleLowerCase())
  ) {
    notify('设备编号已存在');
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
    drawer.value = false;
    notify(isNew.value ? '设备已新增' : '设备信息已保存');
  } catch (error) {
    notify(error instanceof Error ? error.message : '设备保存失败');
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
    notify(error instanceof Error ? error.message : '删除失败');
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
    notify(error instanceof Error ? error.message : '批量删除失败');
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
</script>

<template>
  <div v-if="authState === 'ready'" class="app-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <aside class="sidebar">
      <div class="sidebar-tools">
        <button
          class="collapse"
          :aria-label="collapsed ? '展开导航' : '收起导航'"
          :title="collapsed ? '展开导航' : '收起导航'"
          @click="toggleSidebar"
        >
          {{ collapsed ? '›' : '‹' }}
        </button>
      </div>
      <div v-if="!collapsed" class="navigation-search">
        <input v-model="menuSearch" placeholder="搜索菜单" aria-label="搜索菜单" /><button
          v-if="menuSearch"
          aria-label="清空菜单搜索"
          @click="menuSearch = ''"
        >
          ×
        </button>
      </div>
      <nav class="nav-list" aria-label="主导航">
        <div
          v-if="can('workspace') && (collapsed || !menuSearch || '工作台'.includes(menuSearch))"
          class="nav-group workspace-nav-group"
          data-nav-group="workspace"
          @mouseenter="showNavFlyout($event, workspaceMenu)"
          @mouseleave="delayHideNavFlyout"
        >
          <button
            class="nav-group-trigger workspace-trigger"
            title="工作台"
            :aria-expanded="collapsed ? flyoutGroup === 'workspace' : false"
            @click="router.push({ name: 'workspace' })"
          >
            <span class="nav-icon">▦</span><span class="nav-group-label">工作台</span>
          </button>
        </div>
        <div
          v-for="group in matchingNavGroups"
          :key="group.id"
          class="nav-group"
          :class="{ active: navGroupActive(group) }"
          :data-nav-group="group.id"
          @mouseenter="showNavFlyout($event, group)"
          @mouseleave="delayHideNavFlyout"
        >
          <button
            class="nav-group-trigger"
            :class="{ active: navGroupActive(group) }"
            :title="group.label"
            :aria-expanded="collapsed ? flyoutGroup === group.id : navExpanded[group.id]"
            @click="openNavFlyout(group)"
          >
            <span class="nav-icon">{{ group.icon }}</span
            ><span class="nav-group-label">{{ group.label }}</span
            ><span class="nav-group-caret">⌄</span>
          </button>
          <div v-if="!collapsed && (menuSearch || navExpanded[group.id])" class="nav-inline-menu">
            <button
              v-for="item in group.items.filter(canMenu)"
              :key="item.label"
              class="nav-subitem"
              :class="{
                active: navItemActive(item),
              }"
              @click="activateNav(item)"
            >
              <span>{{ item.icon }}</span
              >{{ item.label }}
            </button>
          </div>
        </div>
      </nav>
      <button
        class="sidebar-bottom"
        type="button"
        title="操作指南"
        aria-label="查看产品使用指南"
        @click="helpSection = 'guide'"
      >
        <div class="help-icon">?</div>
        <div><b>需要帮助？</b><small>操作指南</small></div>
        <span class="chevron" aria-hidden="true">›</span>
      </button>
    </aside>
    <Teleport to="body"
      ><div
        v-if="collapsed && activeFlyout"
        class="nav-hover-menu"
        :class="{ 'workspace-flyout': activeFlyout.id === 'workspace' }"
        :style="{ top: flyoutTop + 'px', left: '64px' }"
        @mouseenter="keepNavFlyout"
        @mouseleave="delayHideNavFlyout"
      >
        <header v-if="activeFlyout.id !== 'workspace'">
          <span>{{ activeFlyout.icon }}</span
          >{{ activeFlyout.label }}
        </header>
        <button
          v-for="item in activeFlyout.items.filter(canMenu)"
          :key="item.label"
          :class="{
            active: navItemActive(item),
          }"
          @click="activateNav(item)"
        >
          <span>{{ item.icon }}</span
          >{{ item.label }}
        </button>
      </div></Teleport
    >
    <main class="main-area">
      <header class="topbar">
        <div class="top-left">
          <button
            class="mobile-menu"
            :aria-label="collapsed ? '打开导航' : '收起导航'"
            @click="toggleSidebar"
          >
            ☰
          </button>
          <div class="product-brand">
            <span class="product-mark"
              ><svg viewBox="0 0 32 32" aria-hidden="true">
                <path
                  d="M16 3v26M16 8l10-5M16 8 6 4M16 17l12 2M16 17 5 22M16 25l9 5M16 25 7 30"
                /></svg></span
            ><strong>设备管理系统</strong>
          </div>
        </div>
        <div class="top-actions">
          <RepairNotificationBell />
          <HelpMenu v-model="helpSection" />
          <ThemePicker v-model="palette" />
          <span class="top-divider"></span>
          <UserAccountMenu @logout="signOut" />
        </div>
      </header>
      <section v-if="currentPage === 'equipment'" class="content">
        <div class="equipment-tabs" role="tablist">
          <button
            v-for="tab in EQUIPMENT_TABS"
            :key="tab"
            role="tab"
            :aria-selected="quickTab === tab"
            :class="{ active: quickTab === tab }"
            @click="selectQuickTab(tab)"
          >
            {{ tab }}
          </button>
        </div>
        <div class="toolbar">
          <div class="toolbar-left">
            <button v-if="can('equipment', '新增')" class="btn primary" @click="create">
              <span class="plus">＋</span>新增</button
            ><span v-if="can('equipment', '编辑') || can('equipment', '删除')" class="batch-wrap"
              ><button
                class="btn secondary"
                :disabled="!selected.length"
                @click="batchMenu = !batchMenu"
              >
                批量操作 <span v-if="selected.length" class="count-pill">{{ selected.length }}</span
                >⌄</button
              ><span v-if="batchMenu" class="batch-menu"
                ><template v-for="(label, operation) in batchLabels"
                  ><button
                    v-if="can('equipment', operation === 'delete' ? '删除' : '编辑')"
                    :class="{ 'danger-text': operation === 'delete' }"
                    @click="openBatch(operation)"
                  >
                    {{ label }}
                  </button></template
                ></span
              ></span
            >
          </div>
          <form class="filters" @submit.prevent="search">
            <ListToolbarIcons
              mode="filter"
              :can-export="can('equipment', '导出')"
              :filter-open="columnFiltersOpen"
              :columns-open="showColumns"
              @toggle-filter="columnFiltersOpen = !columnFiltersOpen"
              @toggle-columns="showColumns = !showColumns"
              @export="exportCsv"
            /><ListSearchInput
              v-model="query.keyword"
              placeholder="搜索名称、编码、型号、部门、责任人"
            />
            <button
              class="advanced-filter-trigger"
              :class="{ active: equipmentAdvanced.length }"
              type="button"
              @click="advancedOpen = true"
            >
              筛选器<span v-if="equipmentAdvanced.length"> ({{ equipmentAdvanced.length }})</span>
            </button>
            <button class="reset-btn" type="button" @click="reset">重置</button>
          </form>
        </div>
        <AppliedFilterChips
          :conditions="equipmentAdvanced"
          :fields="equipmentFilterFields"
          @change="equipmentAdvanced = $event"
        />
        <AdvancedFilterDrawer
          v-model="advancedOpen"
          :conditions="equipmentAdvanced"
          :fields="equipmentFilterFields"
          @apply="
            equipmentAdvanced = $event;
            page = 1;
          "
        />
        <div class="table-card">
          <div class="table-scroll">
            <table class="business-table equipment-ledger-table">
              <thead>
                <tr>
                  <th class="check-col">
                    <input
                      type="checkbox"
                      :checked="allPageSelected"
                      :indeterminate="selected.length > 0 && !allPageSelected"
                      aria-label="选择当前页"
                      @change="togglePage"
                    />
                  </th>
                  <th
                    v-for="col in selectedColumns"
                    :key="col.key"
                    class="sortable-header"
                    :style="{
                      width: columnWidth(col.key) + 'px',
                      minWidth: columnWidth(col.key) + 'px',
                    }"
                    @click="sortBy(col.key)"
                  >
                    <span class="header-content"
                      >{{ col.label
                      }}<span
                        v-if="col.key !== 'imageFileId'"
                        class="sort-mark"
                        :class="sortDirection && sortKey === col.key ? 'sort-active' : ''"
                        >{{
                          sortKey === col.key ? (sortDirection === 'asc' ? '↑' : '↓') : '↕'
                        }}</span
                      ></span
                    ><span
                      class="col-resizer"
                      title="拖动调整列宽"
                      @click.stop
                      @pointerdown.stop.prevent="startResize($event, col.key)"
                    ></span>
                  </th>
                  <th class="action-head list-action-column">操作</th>
                </tr>
                <tr v-if="columnFiltersOpen" class="equipment-filter-row">
                  <th class="list-action-column"></th>
                  <th v-for="col in selectedColumns" :key="col.key">
                    <span v-if="col.key === 'imageFileId'"></span>
                    <select v-else-if="col.key === 'state'" v-model="columnFilters[col.key]">
                      <option value="">全部状态</option>
                      <option v-for="state in EQUIPMENT_STATES" :key="state">
                        {{ state }}
                      </option></select
                    ><select v-else-if="col.key === 'useState'" v-model="columnFilters[col.key]">
                      <option value="">全部</option>
                      <option
                        v-for="item in useStateData.filter((x) => x.status === '启用')"
                        :key="item.id"
                      >
                        {{ item.name }}
                      </option></select
                    ><input
                      v-else
                      v-model="columnFilters[col.key]"
                      :placeholder="`筛选${col.label}`"
                      @click.stop
                    />
                  </th>
                  <th class="list-action-column"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pageRows" :key="row.id">
                  <td class="check-col">
                    <input
                      v-model="selected"
                      type="checkbox"
                      :value="row.id"
                      :aria-label="`选择${row.name}`"
                    />
                  </td>
                  <td
                    v-for="col in selectedColumns"
                    :key="col.key"
                    :style="{
                      width: columnWidth(col.key) + 'px',
                      minWidth: columnWidth(col.key) + 'px',
                      maxWidth: columnWidth(col.key) + 'px',
                    }"
                  >
                    <template v-if="col.key === 'code'"
                      ><button class="code-link" @click="openHistory(row)">
                        {{ row.code }}
                      </button></template
                    ><template v-else-if="col.key === 'imageFileId'"
                      ><EquipmentImage :file-id="row.imageFileId" :name="row.name" /></template
                    ><template v-else-if="col.key === 'name'"
                      ><span class="equipment-name-text">{{ row.name }}</span></template
                    ><template v-else-if="col.key === 'state' || col.key === 'useState'"
                      ><span class="status" :class="statusClass(String(row[col.key]))"
                        ><i></i>{{ row[col.key] }}</span
                      ></template
                    ><template v-else-if="col.key === 'amount'"
                      ><span class="amount">{{ formatAmount(row.amount) }}</span></template
                    ><template v-else>{{ row[col.key] ?? '' }}</template>
                  </td>
                  <td class="row-actions list-action-column">
                    <button v-if="can('equipment', '编辑')" class="text-action" @click="edit(row)">
                      编辑</button
                    ><ElDropdown
                      v-if="can('equipment', '删除') || can('equipment', '新增')"
                      trigger="click"
                      placement="bottom-end"
                      popper-class="eam-dropdown"
                      @command="(command) => (command === 'clone' ? clone(row) : remove(row))"
                      ><button class="more-action" :aria-label="`更多操作：${row.name}`">···</button
                      ><template #dropdown
                        ><ElDropdownMenu
                          ><ElDropdownItem v-if="can('equipment', '新增')" command="clone"
                            >复制新增</ElDropdownItem
                          ><ElDropdownItem v-if="can('equipment', '删除')" command="delete"
                            >删除</ElDropdownItem
                          ></ElDropdownMenu
                        ></template
                      ></ElDropdown
                    >
                  </td>
                </tr>
                <tr v-if="pageRows.length === 0">
                  <td class="empty-cell" :colspan="selectedColumns.length + 2">
                    <div class="empty-icon">⌕</div>
                    <b>{{ rows.length ? '没有找到匹配的设备' : '暂无设备' }}</b
                    ><span>{{
                      rows.length ? '调整筛选条件或清空搜索后重试' : '点击“新增”建立第一条设备档案'
                    }}</span
                    ><button v-if="rows.length" class="text-action" @click="reset">
                      清空筛选条件
                    </button>
                  </td>
                </tr>
              </tbody>
            </table>
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
              :filter-open="columnFiltersOpen"
              :columns-open="showColumns"
              @toggle-filter="columnFiltersOpen = !columnFiltersOpen"
              @toggle-columns="showColumns = !showColumns"
              @export="exportCsv"
              ><template #columns
                ><div class="popover-head">
                  <b>表格列设置</b
                  ><button type="button" @click="visibleKeys = [...defaultEquipmentColumns]">
                    恢复默认
                  </button>
                </div>
                <label v-for="col in columns" :key="col.key" class="column-option"
                  ><input
                    type="checkbox"
                    :checked="visibleKeys.includes(col.key)"
                    :disabled="col.required"
                    @change="toggleColumn(col.key)"
                  /><span>{{ col.label }}</span
                  ><small v-if="col.required">固定</small></label
                >
                <div class="popover-foot">显示 {{ visibleKeys.length }} 列</div></template
              ></ListToolbarIcons
            >
          </footer>
        </div>
      </section>
      <section v-else-if="currentPage === 'base'" class="content base-page">
        <div class="base-tabs" role="tablist">
          <button
            v-for="tab in baseTabs"
            :key="tab"
            role="tab"
            :aria-selected="baseTab === tab"
            :class="{ active: baseTab === tab }"
            @click="baseTab = tab"
          >
            {{ tab }}
          </button>
        </div>
        <div class="base-toolbar">
          <div class="base-actions">
            <button
              v-if="can(baseTab === '设备分类' ? 'category' : 'base', '新增')"
              class="btn primary"
              @click="openBaseCreate()"
            >
              <span class="plus">＋</span>新增{{
                baseTab === '设备分类'
                  ? '分类'
                  : baseTab === '存放地点'
                    ? '地点'
                    : baseTab === '故障类型'
                      ? '故障类型'
                      : baseTab === '单位'
                        ? '单位'
                        : baseTab === '使用状态'
                          ? '使用状态'
                          : baseTab
              }}
            </button>
          </div>
          <form class="base-list-filters" @submit.prevent="searchBase">
            <ListToolbarIcons
              mode="filter"
              :can-export="can(baseTab === '设备分类' ? 'category' : 'base', '导出')"
              :filter-open="baseColumnFilterOpen"
              :columns-open="baseColumnsOpen"
              @toggle-filter="baseColumnFilterOpen = !baseColumnFilterOpen"
              @toggle-columns="baseColumnsOpen = !baseColumnsOpen"
              @export="exportBaseCsv"
            /><ListSearchInput
              v-model="baseSearch"
              :placeholder="`搜索${baseTab}名称、编码${isBaseTree ? '、上级节点' : ''}`"
            /><button class="base-reset" type="button" @click="resetBase">重置</button>
          </form>
        </div>
        <div class="base-card">
          <div class="base-table-scroll">
            <table class="business-table base-table">
              <thead>
                <tr>
                  <th v-for="key in baseVisibleColumns" :key="key">
                    {{
                      key === 'name'
                        ? baseTab === '设备分类'
                          ? '分类名称'
                          : baseTab === '存放地点'
                            ? '地点名称'
                            : baseTab === '故障类型'
                              ? '故障类型'
                              : '名称'
                        : key === 'code'
                          ? '编码'
                          : key === 'parent'
                            ? '上级节点'
                            : key === 'precision'
                              ? '小数精度'
                              : '状态'
                    }}
                  </th>
                  <th class="base-actions-head list-action-column">操作</th>
                </tr>
                <tr v-if="baseColumnFilterOpen" class="base-filter-row">
                  <th v-for="key in baseVisibleColumns" :key="key">
                    <select v-if="key === 'status'" v-model="baseColumnFilters[key]">
                      <option value="">全部状态</option>
                      <option>启用</option>
                      <option>停用</option></select
                    ><input
                      v-else
                      v-model="baseColumnFilters[key]"
                      :placeholder="`筛选${key === 'name' ? '名称' : key === 'code' ? '编码' : key === 'parent' ? '上级节点' : '精度'}`"
                    />
                  </th>
                  <th class="list-action-column"></th>
                </tr>
              </thead>
              <tbody>
                <template v-if="isBaseTree"
                  ><tr v-for="item in filteredTreeRows" :key="item.id">
                    <td v-for="key in baseVisibleColumns" :key="key">
                      <div
                        v-if="key === 'name'"
                        class="tree-label"
                        :style="{ paddingLeft: item.level * 22 + 12 + 'px' }"
                      >
                        <button
                          v-if="treeData.some((node) => node.parentId === item.id)"
                          class="tree-toggle"
                          :aria-label="expandedTreeIds[item.id] === false ? '展开' : '收起'"
                          @click.stop="
                            expandedTreeIds[item.id] =
                              expandedTreeIds[item.id] === false ? true : false
                          "
                        >
                          {{ expandedTreeIds[item.id] === false ? '›' : '⌄' }}</button
                        ><span v-else class="tree-spacer"></span><span class="tree-node-dot"></span
                        ><b>{{ item.name }}</b>
                      </div>
                      <template v-else>{{ baseCell(item, key) }}</template>
                    </td>
                    <td class="base-row-actions list-action-column">
                      <button class="text-action" @click="openBaseCreate(item.id)">新增下级</button
                      ><button class="text-action" @click="openBaseEdit(item)">编辑</button
                      ><RowMoreActions
                        v-if="isBaseTree || baseTab !== '单位'"
                        :actions="[{ label: '删除', danger: true, run: () => deleteBase(item) }]"
                      /><button v-else class="text-action danger-action" @click="deleteBase(item)">
                        删除
                      </button>
                    </td>
                  </tr></template
                >
                <template v-else
                  ><tr v-for="item in filteredBaseValues" :key="item.id">
                    <td v-for="key in baseVisibleColumns" :key="key">
                      <b v-if="key === 'name'" class="base-value-name">{{ item.name }}</b
                      ><span
                        v-else-if="key === 'status'"
                        class="status"
                        :class="item.status === '启用' ? 'success' : 'muted'"
                        ><i></i>{{ item.status }}</span
                      ><template v-else>{{ baseCell(item, key) }}</template>
                    </td>
                    <td class="base-row-actions list-action-column">
                      <button class="text-action" @click="openBaseEdit(item)">编辑</button
                      ><button
                        v-if="baseTab !== '单位'"
                        class="text-action"
                        @click="toggleBaseStatus(item)"
                      >
                        {{ item.status === '启用' ? '停用' : '启用' }}</button
                      ><RowMoreActions
                        v-if="isBaseTree || baseTab !== '单位'"
                        :actions="[{ label: '删除', danger: true, run: () => deleteBase(item) }]"
                      /><button v-else class="text-action danger-action" @click="deleteBase(item)">
                        删除
                      </button>
                    </td>
                  </tr></template
                >
                <tr v-if="!(isBaseTree ? filteredTreeRows.length : filteredBaseValues.length)">
                  <td class="base-empty" :colspan="baseVisibleColumns.length + 1">
                    没有匹配的数据
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="base-footer">
            <span
              >共 {{ isBaseTree ? filteredTreeRows.length : filteredBaseValues.length }} 项</span
            >
            <ListToolbarIcons
              mode="footer"
              :can-export="can(baseTab === '设备分类' ? 'category' : 'base', '导出')"
              :filter-open="baseColumnFilterOpen"
              :columns-open="baseColumnsOpen"
              @toggle-filter="baseColumnFilterOpen = !baseColumnFilterOpen"
              @toggle-columns="baseColumnsOpen = !baseColumnsOpen"
              @export="exportBaseCsv"
              ><template #columns
                ><label v-for="key in defaultBaseColumns()" :key="key"
                  ><input
                    type="checkbox"
                    :checked="baseVisibleColumns.includes(key)"
                    @change="toggleBaseColumn(key)"
                  />{{
                    key === 'name'
                      ? '名称'
                      : key === 'code'
                        ? '编码'
                        : key === 'parent'
                          ? '上级节点'
                          : key === 'precision'
                            ? '小数精度'
                            : '状态'
                  }}</label
                ></template
              ></ListToolbarIcons
            >
          </footer>
        </div>
      </section>
      <RouterView v-else />
      <EquipmentHistory
        v-if="historyId && currentPage === 'equipment'"
        :id="historyId"
        @close="closeHistory"
      />
    </main>
    <AppDialog
      v-model="baseDialog"
      :busy="baseSaving"
      :before-close="baseFormGuard.permitLeave"
      :title="`${baseEditId ? '编辑' : '新增'}${baseTab}`"
      width="480px"
      ><div class="base-dialog-body" :inert="baseSaving || undefined">
        <label
          ><span>编码</span
          ><input v-model="baseForm.code" placeholder="留空自动生成编码" maxlength="40" /></label
        ><label
          ><span>名称 <i>*</i></span
          ><input v-model="baseForm.name" placeholder="请输入名称" maxlength="60" /></label
        ><label
          v-if="
            baseTab === '设备分类' ||
            baseTab === '物料分类' ||
            baseTab === '存放地点' ||
            baseTab === '故障类型'
          "
          ><span>上级节点</span
          ><TreeSelect
            v-model="baseForm.parentId"
            :nodes="parentOptions"
            placeholder="不选则作为一级节点" /></label
        ><label v-if="baseTab === '单位'"
          ><span>小数精度</span
          ><input v-model.number="baseForm.precision" type="number" min="0" max="6"
        /></label>
      </div>
      <template #footer
        ><button class="btn secondary" :disabled="baseSaving" @click="closeBaseDialog">取消</button
        ><button class="btn primary" :disabled="baseSaving" @click="saveBase">
          {{ baseSaving ? '保存中…' : '保存' }}
        </button></template
      ></AppDialog
    >

    <AppDialog
      v-model="batchOpen"
      :title="batchLabels[batchForm.operation]"
      :busy="batchBusy"
      width="440px"
      ><div class="batch-dialog-content">
        <p>已选择 {{ selected.length }} 台设备</p>
        <label
          ><span>{{ batchLabels[batchForm.operation].replace('修改', '') }}</span
          ><TreeSelect
            v-if="batchForm.operation === 'category' || batchForm.operation === 'location'"
            v-model="batchForm.value"
            :nodes="
              (batchForm.operation === 'category' ? categoryTreeOptions : locationTreeOptions).map(
                (x) => ({ ...x, value: x.id }),
              )
            "
            placeholder="请选择"
          /><TreeSelect
            v-else-if="batchForm.operation === 'department'"
            v-model="batchForm.value"
            :nodes="batchDepartmentNodes"
            placeholder="请选择部门"
          /><select v-else-if="batchForm.operation === 'owner'" v-model="batchForm.value">
            <option value="">请选择负责人</option>
            <option v-for="user in batchOptions.users" :value="user.id">
              {{ user.name }}（{{ user.account }}）
            </option></select
          ><select v-else v-model="batchForm.value">
            <option value="">请选择状态</option>
            <option v-for="state in EQUIPMENT_STATES">{{ state }}</option>
          </select></label
        >
      </div>
      <template #footer
        ><button class="btn secondary" @click="batchOpen = false">取消</button
        ><button class="btn primary" :disabled="!batchForm.value || batchBusy" @click="submitBatch">
          {{ batchBusy ? '保存中…' : '确定' }}
        </button></template
      ></AppDialog
    >
    <EquipmentEditor
      v-model="editing"
      v-model:open="drawer"
      :is-new="isNew"
      :busy="equipmentSaving || equipmentUploading"
      @uploading="equipmentUploading = $event"
      @save="save"
      @close="closeEquipment"
    />
    <Transition name="toast"
      ><div v-if="toast" class="toast-message"><span>✓</span>{{ toast }}</div></Transition
    >
  </div>
  <LoginScreen
    v-else
    :state="authState"
    :message="authMessage"
    @login="signIn"
    @retry="initialize"
  />
</template>

<style scoped>
.navigation-search {
  display: flex;
  align-items: center;
  margin: 6px 10px 10px;
  border: 1px solid var(--border-color, #dce3ee);
  border-radius: 5px;
  padding: 0 8px;
}
.navigation-search input {
  width: 100%;
  min-width: 0;
  height: 30px;
  border: 0;
  outline: 0;
  background: transparent;
  color: inherit;
  font: inherit;
}
.navigation-search button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.sidebar-bottom {
  border-right: 0;
  border-bottom: 0;
  border-left: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}
.sidebar-bottom b,
.sidebar-bottom small {
  color: inherit;
  font-size: 13px;
}
.sidebar-bottom:hover {
  background: var(--brand-soft);
  color: var(--brand);
}
.sidebar-bottom:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: -2px;
}
.equipment-row-menu {
  position: fixed;
  z-index: 1000;
  width: 144px;
  right: auto;
  padding: 5px;
}
.equipment-row-menu button {
  height: 34px;
  font-size: 13px;
  padding: 0 12px;
}

.main-area {
  position: relative;
}
.batch-wrap {
  position: relative;
}
.batch-menu {
  position: absolute;
  left: 0;
  top: 36px;
  z-index: 50;
  min-width: 140px;
  background: #fff;
  border: 1px solid #e3e8ef;
  border-radius: 5px;
  box-shadow: 0 5px 20px #22334b18;
  padding: 4px;
}
.batch-menu button {
  display: block;
  width: 100%;
  padding: 9px 12px;
  text-align: left;
  border: 0;
  background: none;
  font: inherit;
  color: #475569;
  cursor: pointer;
}
.batch-menu button:hover {
  background: #f1f5ff;
}
.batch-mask {
  position: fixed;
  inset: 0;
  background: #18243a55;
  z-index: 160;
  display: grid;
  place-items: center;
}
.batch-dialog {
  width: 440px;
  padding: 18px 22px;
  background: #fff;
  border-radius: 8px;
  font-size: 13px;
}
.batch-dialog header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.batch-dialog header button {
  border: 0;
  background: none;
  font-size: 22px;
  color: #94a3b8;
  cursor: pointer;
}
.batch-dialog p {
  color: #718096;
  margin: 16px 0;
}
.batch-dialog label {
  display: flex;
  gap: 12px;
  align-items: center;
}
.batch-dialog label > span {
  width: 68px;
  text-align: right;
}
.batch-dialog label > select,
.batch-dialog label > :deep(.tree-select) {
  flex: 1;
}
.batch-dialog select {
  height: 34px;
  padding: 0 10px;
  border: 1px solid #d9e1ec;
  border-radius: 4px;
  font: inherit;
}
.batch-dialog footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 24px;
}
</style>
