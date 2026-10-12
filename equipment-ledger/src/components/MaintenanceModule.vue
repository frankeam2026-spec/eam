<script setup lang="ts">
import AppDecimalInput from './common/AppDecimalInput.vue';
import TaskOrganizationFilter from './common/TaskOrganizationFilter.vue';
import { maintenanceMenu, inspectionMenu } from '../rolePermissions';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import AppBackButton from './common/AppBackButton.vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElInputNumber,
  ElSelect,
  ElOption,
  ElDatePicker,
  ElSwitch,
  ElCheckbox,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElMessage,
  ElTag,
} from 'element-plus';
import {
  maintenanceApi,
  emptyDefinition,
  emptyItem,
  type MaintenanceKind,
  type MaintenanceDefinition,
  type MaintenanceTask,
  type PlanDetail,
  type Choice,
  type MaintenanceItem,
} from '../maintenance';
import { useAuthStore } from '../stores/auth';
import { confirmAction } from '../services/dialog';
import AppPagination from './common/AppPagination.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import AppDialog from './common/AppDialog.vue';
import MaintenanceAcceptanceDialog from './common/MaintenanceAcceptanceDialog.vue';
import type { TaskResult } from '../maintenance';
import ListToolbarIcons from './ListToolbarIcons.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import TreeSelect from './TreeSelect.vue';
import AdvancedFilterDrawer from './common/AdvancedFilterDrawer.vue';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { matchesAdvanced, type FilterCondition, type FilterField } from '../query/advancedFilters';
import MaintenanceAttachments from './common/MaintenanceAttachments.vue';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const tabs = [
  { key: 'tasks', label: '保养任务' },
  { key: 'plans', label: '保养计划' },
  { key: 'standards', label: '保养标准' },
  { key: 'types', label: '保养类型' },
] as const;
const kind = computed(() => String(route.params.kind || 'tasks') as MaintenanceKind);
const editing = computed(
  () => route.name === 'maintenance-new' || route.name === 'maintenance-edit',
);
const definitions = ref<MaintenanceDefinition[]>([]),
  tasks = ref<MaintenanceTask[]>([]),
  types = ref<MaintenanceDefinition[]>([]),
  standards = ref<MaintenanceDefinition[]>([]);
const choices = ref<{
  equipment: Choice[];
  organizations: Choice[];
  users: Choice[];
  categories: Choice[];
  stocks: import('../api').StockRow[];
}>({ equipment: [], organizations: [], users: [], categories: [], stocks: [] });
const form = ref(emptyDefinition()),
  busy = ref(false),
  loading = ref(false),
  error = ref(''),
  keyword = ref(''),
  page = ref(1),
  size = ref(10),
  queue = ref('全部'),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  columnFilters = ref<Record<string, string>>({}),
  visibleColumns = ref<string[]>([]),
  selected = ref<string[]>([]),
  sortKey = ref(''),
  sortAsc = ref(true);
const advancedOpen = ref(false),
  conditions = ref<FilterCondition[]>([]);
const advancedFields = computed<FilterField[]>(() =>
  fields.value.map(([key, label]) => ({
    key: key!,
    label: label!,
    type: key === 'status' ? 'choice' : key === 'dueDate' || key === 'createdAt' ? 'date' : 'text',
    dateOnly: key === 'dueDate',
    ...(key === 'status'
      ? { options: ['待执行', '执行中', '挂起', '待验收', '已完成', '已作废', '过期作废'] }
      : {}),
  })),
);
const widths = ref<Record<string, number>>({});
function resize(event: MouseEvent, key: string) {
  event.stopPropagation();
  event.preventDefault();
  const start = event.clientX;
  const width =
    (event.currentTarget as HTMLElement).parentElement?.getBoundingClientRect().width || 160;
  const move = (e: MouseEvent) => {
    widths.value[key] = Math.max(90, width + e.clientX - start);
  };
  const stop = () => {
    window.removeEventListener('mousemove', move);
    window.removeEventListener('mouseup', stop);
    localStorage.setItem('eam-maintenance-widths-' + kind.value, JSON.stringify(widths.value));
  };
  window.addEventListener('mousemove', move);
  window.addEventListener('mouseup', stop);
}
watch(
  visibleColumns,
  () => {
    localStorage.setItem(
      'eam-maintenance-columns-' + kind.value,
      JSON.stringify(visibleColumns.value),
    );
  },
  { deep: true },
);
const typeDialog = ref(false),
  equipmentDialog = ref(false),
  pickSearch = ref(''),
  picked = ref<string[]>([]),
  category = ref('');
const selectedItems = ref<string[]>([]);
type ItemBatchAction = 'type' | 'options' | 'normalOptions';
const itemBatchOpen = ref(false),
  itemBatchAction = ref<ItemBatchAction>('type'),
  itemBatchType = ref(''),
  itemBatchValues = ref<string[]>([]);
const itemBatchLabels = { type: '修改类型', options: '修改可选项', normalOptions: '修改正常选项' };
const selectedOptionItems = computed(() =>
  form.value.items.filter(
    (item) => selectedItems.value.includes(item.id) && item.valueType === '选项',
  ),
);
const sharedNormalOptions = computed(() => {
  const rows = selectedOptionItems.value;
  return rows.length
    ? rows[0]!.options.filter((value) => rows.every((item) => item.options.includes(value)))
    : [];
});
function openItemBatch(action: ItemBatchAction | 'delete') {
  if (!selectedItems.value.length) return;
  if (action === 'delete') {
    void removeSelectedItems();
    return;
  }
  if (action !== 'type' && !selectedOptionItems.value.length) {
    ElMessage.warning('请选择结果类型为选项的项目');
    return;
  }
  itemBatchAction.value = action;
  itemBatchType.value = '';
  itemBatchValues.value = [];
  itemBatchOpen.value = true;
}
function applyItemBatch() {
  const action = itemBatchAction.value;
  const values = [...new Set(itemBatchValues.value.map((value) => value.trim()).filter(Boolean))];
  if (action === 'type' && !itemBatchType.value) {
    ElMessage.warning('请选择保养类型');
    return;
  }
  if (action === 'options' && values.length < 2) {
    ElMessage.warning('至少填写两个可选项');
    return;
  }
  if (
    action === 'normalOptions' &&
    (!values.length || values.some((value) => !sharedNormalOptions.value.includes(value)))
  ) {
    ElMessage.warning('请选择各选中项目共有的正常选项');
    return;
  }
  let count = 0;
  for (const item of form.value.items.filter((item) => selectedItems.value.includes(item.id))) {
    if (action === 'type') item.typeId = itemBatchType.value;
    else if (item.valueType === '选项') {
      if (action === 'options') {
        item.options = [...values];
        changeItemOptions(item);
      } else item.normalOptions = [...values];
    } else continue;
    delete itemErrors.value[item.id];
    count++;
  }
  itemBatchOpen.value = false;
  ElMessage.success(`已修改 ${count} 个项目，提交后保存`);
}
const itemErrors = ref<Record<string, string>>({});
const acceptance = ref(false);
const title = computed(() => tabs.find((x) => x.key === kind.value)?.label || '保养');
const can = (action: string) => auth.can(maintenanceMenu(kind.value), action);
const orgNodes = computed(() =>
  choices.value.organizations.map((x) => ({
    id: x.id,
    name: x.name,
    parentId: x.parentId || '',
    value: x.id,
  })),
);
const categoryNodes = computed(() =>
  choices.value.categories.map((x) => ({
    id: x.id,
    name: x.name,
    parentId: x.parentId === '0' ? '' : x.parentId || '',
  })),
);
const fields = computed(() =>
  kind.value === 'tasks'
    ? [
        ['code', '任务编号'],
        ['equipmentName', '设备名称'],
        ['equipmentCode', '设备编号'],
        ['typeName', '保养类型'],
        ['status', '状态'],
        ['department', '所属部门'],
        ['owner', '负责人'],
        ['dueDate', '保养日期'],
        ['createdAt', '生成时间'],
      ]
    : kind.value === 'types'
      ? [
          ['code', '编码'],
          ['name', '名称'],
          ['symbol', '符号'],
          ['cycle', '周期'],
          ['systemLabel', '系统数据'],
        ]
      : kind.value === 'standards'
        ? [
            ['code', '标准编号'],
            ['name', '标准名称'],
            ['department', '所属部门'],
            ['itemCount', '项目数'],
            ['createdAt', '创建时间'],
            ['remark', '说明'],
          ]
        : [
            ['code', '计划编号'],
            ['name', '计划名称'],
            ['department', '所属部门'],
            ['equipmentCount', '设备数'],
            ['enabledLabel', '状态'],
            ['calculation', '下次时间计算'],
            ['createdAt', '创建时间'],
          ],
);
function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}
function names(ids: string[] = []) {
  return ids.map((id) => choices.value.users.find((x) => x.id === id)?.name || id).join('、');
}
function cell(row: MaintenanceDefinition | MaintenanceTask, key: string): string {
  if (key === 'owner') {
    const t = row as MaintenanceTask;
    return t.ownerId ? names([t.ownerId]) : names(t.owners);
  }
  if (key === 'department' && 'departmentId' in row)
    return choices.value.organizations.find((x) => x.id === row.departmentId)?.name || '';
  if (key === 'cycle' && 'period' in row) return row.period + row.unit;
  if (key === 'systemLabel' && 'system' in row) return row.system ? '是' : '否';
  if (key === 'enabledLabel' && 'enabled' in row) return row.enabled ? '启用' : '停用';
  if (key === 'itemCount' && 'items' in row) return String(row.items?.length || 0);
  if (key === 'equipmentCount' && 'details' in row)
    return String(new Set(row.details?.map((x) => x.equipmentId)).size);
  const v = Reflect.get(row, key);
  return key === 'createdAt' ? formatDateTime(v) : String(v ?? '');
}
const organizationFilter = ref('');
function matchesOrganization(row: { departmentId?: string; companyId?: string }) {
  if (!organizationFilter.value) return true;
  const byId = new Map(choices.value.organizations.map((x) => [x.id, x]));
  for (const id of [row.departmentId, row.companyId]) {
    let current = id;
    const seen = new Set<string>();
    while (current && !seen.has(current)) {
      if (current === organizationFilter.value) return true;
      seen.add(current);
      current = byId.get(current)?.parentId;
    }
  }
  return false;
}
watch(organizationFilter, () => {
  page.value = 1;
});
watch(kind, () => {
  organizationFilter.value = '';
});
const filtered = computed(() => {
  const rows: Array<MaintenanceDefinition | MaintenanceTask> =
    kind.value === 'tasks' ? tasks.value : definitions.value;
  const result = rows.filter(
    (r) =>
      (kind.value !== 'tasks' || matchesOrganization(r)) &&
      (kind.value !== 'tasks' ||
        queue.value === '全部' ||
        (queue.value === '待我执行'
          ? (r as MaintenanceTask).canExecute &&
            ['待执行', '执行中', '挂起'].includes((r as MaintenanceTask).status)
          : queue.value === '待我验收'
            ? (r as MaintenanceTask).canAccept && (r as MaintenanceTask).status === '待验收'
            : (r as MaintenanceTask).status === queue.value)) &&
      fields.value.some(([k]) => cell(r, k!).includes(keyword.value.trim())) &&
      matchesAdvanced(conditions.value, (k) => cell(r, k)) &&
      Object.entries(columnFilters.value).every(([k, v]) => !v || cell(r, k).includes(v)),
  );
  if (sortKey.value)
    result.sort(
      (a, b) =>
        cell(a, sortKey.value).localeCompare(cell(b, sortKey.value), 'zh-CN', { numeric: true }) *
        (sortAsc.value ? 1 : -1),
    );
  return result;
});
const pageRows = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value),
);
const activeFields = computed(() =>
  fields.value.filter(([k]) => visibleColumns.value.includes(k!)),
);
const eqRows = computed(() =>
  choices.value.equipment.filter((e) =>
    [e.code, e.name, e.model, e.category].some((v) => v?.includes(pickSearch.value)),
  ),
);
const unsaved = useUnsavedChanges(
  () => form.value,
  () => editing.value,
  () => busy.value,
);
async function load() {
  loading.value = true;
  error.value = '';
  try {
    const [c, t, s] = await Promise.all([
      maintenanceApi.choices(),
      kind.value === 'tasks' || !auth.can('maintenanceType')
        ? Promise.resolve({ data: [] as MaintenanceDefinition[] })
        : maintenanceApi.list('types'),
      kind.value === 'tasks' || !auth.can('maintenanceStandard')
        ? Promise.resolve({ data: [] as MaintenanceDefinition[] })
        : maintenanceApi.list('standards'),
    ]);
    choices.value = c;
    types.value = t.data.sort(
      (a, b) =>
        a.period * ({ 日: 1, 周: 7, 月: 30, 年: 365 }[a.unit] || 1) -
        b.period * ({ 日: 1, 周: 7, 月: 30, 年: 365 }[b.unit] || 1),
    );
    standards.value = s.data;
    if (editing.value) {
      form.value = route.params.id
        ? (await maintenanceApi.get(kind.value, String(route.params.id))).data
        : emptyDefinition();
      if (!form.value.departmentId) form.value.departmentId = String(auth.user?.orgId || '');
      selectedItems.value = [];
      selectedPlanRows.value = [];
      for (const detail of form.value.details) {
        detail.clientKey = crypto.randomUUID();
        if (!detail.projectRefs)
          detail.projectRefs = (
            standards.value.find((st) => st.id === detail.standardId)?.items || []
          )
            .filter((item) => item.typeId === detail.typeId)
            .map((item) => ({ standardId: detail.standardId, itemId: item.id }));
      }
      itemErrors.value = {};
      unsaved.markSaved();
    } else if (kind.value === 'tasks') tasks.value = (await maintenanceApi.tasks()).data;
    else
      definitions.value =
        kind.value === 'types' ? types.value : (await maintenanceApi.list(kind.value)).data;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    loading.value = false;
  }
}
watch(
  () => [kind.value, route.name, route.params.id],
  () => {
    keyword.value = '';
    columnFilters.value = {};
    try {
      visibleColumns.value =
        JSON.parse(localStorage.getItem('eam-maintenance-columns-' + kind.value) || 'null') ||
        fields.value.map(([k]) => k!);
      widths.value = JSON.parse(
        localStorage.getItem('eam-maintenance-widths-' + kind.value) || '{}',
      );
    } catch {
      visibleColumns.value = fields.value.map(([k]) => k!);
      widths.value = {};
    }
    conditions.value = [];
    selected.value = [];
    page.value = 1;
    load();
  },
  { immediate: true },
);
function back() {
  router.push({ name: 'maintenance', params: { kind: kind.value } });
}
async function open(row?: MaintenanceDefinition) {
  if (kind.value === 'types') {
    form.value = row ? clone(row) : emptyDefinition();
    typeDialog.value = true;
  } else
    router.push({
      name: row ? 'maintenance-edit' : 'maintenance-new',
      params: { kind: kind.value, ...(row ? { id: row.id } : {}) },
    });
}
async function save() {
  if (busy.value) return;
  if (kind.value === 'standards') {
    itemErrors.value = {};
    for (const [index, item] of form.value.items.entries()) {
      const missing = !item.name.trim()
        ? '请填写项目名称'
        : !item.typeId
          ? '请选择保养类型'
          : !item.content.trim()
            ? '请填写操作要求'
            : item.valueType === '选项' && item.options.length < 2
              ? '至少填写两个可选项'
              : item.valueType === '选项' &&
                  (!item.normalOptions.length ||
                    item.normalOptions.some((value) => !item.options.includes(value)))
                ? '请选择正常选项'
                : item.valueType === '数值' &&
                    ((item.lower !== '' && !Number.isFinite(Number(item.lower))) ||
                      (item.upper !== '' && !Number.isFinite(Number(item.upper))))
                  ? '上下限必须为数值'
                  : item.valueType === '数值' &&
                      item.lower !== '' &&
                      item.upper !== '' &&
                      Number(item.lower) > Number(item.upper)
                    ? '下限不能大于上限'
                    : '';
      if (missing) itemErrors.value[item.id] = `第 ${index + 1} 行：${missing}`;
    }
    if (Object.keys(itemErrors.value).length) {
      ElMessage.error(Object.values(itemErrors.value)[0]);
      return;
    }
  }
  if (kind.value === 'plans') {
    const invalid = form.value.details.findIndex(
      (detail) => !detail.typeId || !detail.projectRefs?.length || !detail.nextDate,
    );
    if (invalid >= 0) {
      ElMessage.error(`计划设备第 ${invalid + 1} 行：请选择保养类型、保养项目及下次保养时间`);
      return;
    }
  }
  busy.value = true;
  try {
    await maintenanceApi.save(kind.value, form.value);
    unsaved.markSaved();
    ElMessage.success('保存成功');
    typeDialog.value = false;
    if (editing.value) {
      busy.value = false;
      back();
    } else await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    busy.value = false;
  }
}
async function remove(row: MaintenanceDefinition) {
  if (!(await confirmAction('确认删除“' + row.name + '”？'))) return;
  try {
    await maintenanceApi.remove(kind.value, row.id);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败');
  }
}
function addItem() {
  const i = emptyItem(types.value[0]?.id || '');
  form.value.items.push(i);
}
function copyItem(item: MaintenanceItem, index: number) {
  const copy = clone(item);
  copy.id = emptyItem().id;
  copy.code = '';
  form.value.items.splice(index + 1, 0, copy);
}
function moveItem(index: number, offset: number) {
  const next = index + offset;
  if (next < 0 || next >= form.value.items.length) return;
  [form.value.items[index], form.value.items[next]] = [
    form.value.items[next]!,
    form.value.items[index]!,
  ];
}
function removeItem(id: string) {
  form.value.items = form.value.items.filter((item) => item.id !== id);
  selectedItems.value = selectedItems.value.filter((x) => x !== id);
  delete itemErrors.value[id];
}
async function removeSelectedItems() {
  if (
    !selectedItems.value.length ||
    !(await confirmAction(`删除选中的 ${selectedItems.value.length} 个保养项目？提交后生效。`))
  )
    return;
  for (const id of [...selectedItems.value]) removeItem(id);
}
function changeItemOptions(item: MaintenanceItem) {
  item.normalOptions = item.normalOptions.filter((value) => item.options.includes(value));
}
function chooseEquipment() {
  picked.value =
    kind.value === 'standards'
      ? [...form.value.equipmentIds]
      : form.value.details.map((d) => d.equipmentId);
  pickSearch.value = '';
  equipmentDialog.value = true;
}
function applicableStandards(equipmentId: string) {
  const equipment = eq(equipmentId);
  const categories = new Set<string>();
  let category = choices.value.categories.find((node) => node.name === equipment?.category);
  while (category && !categories.has(category.name)) {
    categories.add(category.name);
    category = choices.value.categories.find((node) => node.id === category!.parentId);
  }
  return standards.value.filter(
    (standard) =>
      (!standard.equipmentIds?.length && !standard.categoryNames?.length) ||
      standard.equipmentIds?.includes(equipmentId) ||
      standard.categoryNames?.some((name) => categories.has(name)),
  );
}
function defaultProjects(equipmentId: string, typeId: string) {
  return applicableStandards(equipmentId).flatMap((standard) =>
    standard.items
      .filter((item) => item.typeId === typeId)
      .map((item) => ({ standardId: standard.id, itemId: item.id })),
  );
}
function defaultTaskDuration(period: number, unit: string, nextDate: string) {
  if (unit === '日' || unit === '周') return Math.min(36500, period * (unit === '周' ? 7 : 1));
  const parts = (nextDate || localDate()).split('-').map(Number);
  const year = parts[0]!,
    month = parts[1]! - 1,
    day = parts[2]!;
  const start = Date.UTC(year, month, day);
  const targetMonth = month + period * (unit === '年' ? 12 : 1);
  const lastDay = new Date(Date.UTC(year, targetMonth + 1, 0)).getUTCDate();
  const end = Date.UTC(year, targetMonth, Math.min(day, lastDay));
  return Math.min(36500, Math.round((end - start) / 86400000));
}
function updateTaskDuration(detail: PlanDetail) {
  detail.validDays = defaultTaskDuration(detail.period, detail.unit, detail.nextDate);
}
function newPlanDetail(equipmentId: string, typeId: string): PlanDetail {
  const type = types.value.find((item) => item.id === typeId);
  const projectRefs = defaultProjects(equipmentId, typeId);
  return {
    clientKey: crypto.randomUUID(),
    equipmentId,
    typeId,
    standardId: projectRefs[0]?.standardId || '',
    projectRefs,
    period: type?.period || 1,
    unit: type?.unit || '月',
    nextDate: localDate(),
    validDays: defaultTaskDuration(type?.period || 1, type?.unit || '月', localDate()),
    owners: [],
    reviewers: [],
    remark: '',
  };
}
function confirmEquipment() {
  if (kind.value === 'standards') form.value.equipmentIds = [...picked.value];
  else {
    form.value.details = form.value.details.filter((detail) =>
      picked.value.includes(detail.equipmentId),
    );
    for (const id of [...new Set(picked.value)]) {
      if (form.value.details.some((detail) => detail.equipmentId === id)) continue;
      const typeIds = [
        ...new Set(
          applicableStandards(id).flatMap((standard) => standard.items.map((item) => item.typeId)),
        ),
      ];
      for (const typeId of typeIds.length ? typeIds : [types.value[0]?.id || ''])
        form.value.details.push(newPlanDetail(id, typeId));
    }
    selectedPlanRows.value = selectedPlanRows.value.filter((key) =>
      form.value.details.some((detail) => detail.clientKey === key),
    );
  }
  equipmentDialog.value = false;
}
function projectValues(detail: PlanDetail) {
  return (detail.projectRefs || []).map((ref) => `${ref.standardId}:${ref.itemId}`);
}
function setProjects(detail: PlanDetail, values: string[]) {
  detail.projectRefs = values.map((value) => {
    const [standardId, itemId] = value.split(':');
    return { standardId: standardId!, itemId: itemId! };
  });
  detail.standardId = detail.projectRefs[0]?.standardId || '';
}
const projectPickerOpen = ref(false),
  projectPickerRowKey = ref(''),
  projectPickerAll = ref(false),
  projectPickerStandard = ref(''),
  projectPickerSearch = ref(''),
  projectPickerValues = ref<string[]>([]);
const projectPickerRow = computed(() =>
  form.value.details.find((d) => d.clientKey === projectPickerRowKey.value),
);
const projectPickerStandards = computed(() => {
  const row = projectPickerRow.value;
  if (!row) return [];
  return projectPickerAll.value ? standards.value : applicableStandards(row.equipmentId);
});
const projectPickerItems = computed(() => {
  const row = projectPickerRow.value;
  const standard = standards.value.find((s) => s.id === projectPickerStandard.value);
  const query = projectPickerSearch.value.trim();
  return row && standard
    ? standard.items.filter(
        (i) =>
          i.typeId === row.typeId && [i.name, i.module, i.content].some((v) => v.includes(query)),
      )
    : [];
});
const pickerItemKey = (item: MaintenanceItem) => `${projectPickerStandard.value}:${item.id}`;
const projectPickerAllChecked = computed(
  () =>
    projectPickerItems.value.length > 0 &&
    projectPickerItems.value.every((i) => projectPickerValues.value.includes(pickerItemKey(i))),
);
const projectPickerSomeChecked = computed(() =>
  projectPickerItems.value.some((i) => projectPickerValues.value.includes(pickerItemKey(i))),
);
watch(projectPickerStandards, (rows) => {
  if (!rows.some((s) => s.id === projectPickerStandard.value))
    projectPickerStandard.value = rows[0]?.id || '';
});
function projectSummary(detail: PlanDetail) {
  return (detail.projectRefs || [])
    .map((ref) => {
      const standard = standards.value.find((s) => s.id === ref.standardId);
      return `${standard?.name || ''} · ${standard?.items.find((i) => i.id === ref.itemId)?.name || ''}`;
    })
    .join('；');
}
function openProjectPicker(detail: PlanDetail) {
  projectPickerRowKey.value = detail.clientKey!;
  projectPickerAll.value = false;
  projectPickerSearch.value = '';
  projectPickerValues.value = projectValues(detail);
  const associated = applicableStandards(detail.equipmentId);
  projectPickerStandard.value =
    associated.find((s) => detail.projectRefs?.some((r) => r.standardId === s.id))?.id ||
    associated[0]?.id ||
    '';
  projectPickerOpen.value = true;
}
function togglePickerItem(item: MaintenanceItem, checked: boolean) {
  const key = pickerItemKey(item);
  projectPickerValues.value = checked
    ? [...new Set([...projectPickerValues.value, key])]
    : projectPickerValues.value.filter((v) => v !== key);
}
function togglePickerAll(checked: boolean) {
  const keys = projectPickerItems.value.map(pickerItemKey);
  projectPickerValues.value = checked
    ? [...new Set([...projectPickerValues.value, ...keys])]
    : projectPickerValues.value.filter((v) => !keys.includes(v));
}
function confirmProjectPicker() {
  const detail = projectPickerRow.value;
  if (!detail) return;
  if (!projectPickerValues.value.length) {
    ElMessage.warning('请选择保养项目');
    return;
  }
  setProjects(detail, projectPickerValues.value);
  projectPickerOpen.value = false;
}
function copyPlanRow(detail: PlanDetail) {
  form.value.details.push({ ...clone(detail), id: undefined, clientKey: crypto.randomUUID() });
}
function removePlanRow(key: string) {
  form.value.details = form.value.details.filter((detail) => detail.clientKey !== key);
  selectedPlanRows.value = selectedPlanRows.value.filter((value) => value !== key);
}
const selectedPlanRows = ref<string[]>([]);
type PlanBatchAction = 'type' | 'date' | 'duration' | 'owners' | 'reviewers';
const planBatchOpen = ref(false),
  planBatchAction = ref<PlanBatchAction>('type'),
  planBatchType = ref(''),
  planBatchDate = ref(''),
  planBatchDuration = ref(0),
  planBatchUsers = ref<string[]>([]);
const planBatchLabels = {
  type: '设置保养类型',
  date: '设置下次保养时间',
  duration: '设置有效时长',
  owners: '设置负责人',
  reviewers: '设置审核人',
};
function openPlanBatch(action: PlanBatchAction) {
  planBatchAction.value = action;
  planBatchType.value = '';
  planBatchDate.value = '';
  planBatchDuration.value = 0;
  planBatchUsers.value = [];
  planBatchOpen.value = true;
}
function applyPlanBatch() {
  const action = planBatchAction.value;
  if (action === 'type' && !planBatchType.value) {
    ElMessage.warning('请选择保养类型');
    return;
  }
  if (action === 'date' && !planBatchDate.value) {
    ElMessage.warning('请选择下次保养时间');
    return;
  }
  for (const detail of form.value.details.filter((row) =>
    selectedPlanRows.value.includes(row.clientKey!),
  )) {
    if (action === 'type') {
      detail.typeId = planBatchType.value;
      typeChanged(detail);
    } else if (action === 'date') detail.nextDate = planBatchDate.value;
    else if (action === 'duration') detail.validDays = planBatchDuration.value;
    else if (action === 'owners') detail.owners = [...planBatchUsers.value];
    else detail.reviewers = [...planBatchUsers.value];
  }
  planBatchOpen.value = false;
  ElMessage.success('已更新选中行，提交后保存');
}
function localDate() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
function typeChanged(d: PlanDetail) {
  const t = types.value.find((x) => x.id === d.typeId);
  if (t) {
    d.period = t.period;
    d.unit = t.unit;
    updateTaskDuration(d);
    d.projectRefs = defaultProjects(d.equipmentId, d.typeId);
    d.standardId = d.projectRefs[0]?.standardId || '';
  }
}
function eq(id: string) {
  return choices.value.equipment.find((x) => x.id === id);
}
function reset() {
  keyword.value = '';
  columnFilters.value = {};
  queue.value = '全部';
  conditions.value = [];
  page.value = 1;
}
function exportRows() {
  if (!can('导出')) return;
  const csv =
    '\uFEFF' +
    fields.value.map((x) => x[1]).join(',') +
    '\r\n' +
    filtered.value
      .map((r) =>
        fields.value
          .map(
            ([k]) =>
              '"' +
              cell(r, k!)
                .replace(/^[=+@-]/, "'$&")
                .replaceAll('"', '""') +
              '"',
          )
          .join(','),
      )
      .join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = title.value + '.csv';
  a.click();
  URL.revokeObjectURL(url);
}
async function submitAcceptance(value: NonNullable<TaskResult['acceptance']>) {
  if (busy.value) return;
  busy.value = true;
  try {
    const list = tasks.value.filter((t) => selected.value.includes(t.id));
    await maintenanceApi.acceptBatch(
      list.map((t) => ({
        id: t.id,
        rowVersion: t.rowVersion,
        ...value,
      })),
    );
    acceptance.value = false;
    selected.value = [];
    window.dispatchEvent(new Event('repair-inbox-changed'));
    await load();
    ElMessage.success('验收完成');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '验收失败');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="maintenance-page">
    <div
      v-if="!editing"
      class="task-list-layout"
      :class="{ 'with-organizations': kind === 'tasks' }"
    >
      <TaskOrganizationFilter
        v-if="kind === 'tasks'"
        v-model="organizationFilter"
        :organizations="choices.organizations"
        :records="tasks"
      />
      <div class="task-list-content">
        <div v-if="kind === 'tasks'" class="maintenance-queues">
          <button
            v-for="q in ['全部', '待我执行', '待我验收', '待执行', '执行中', '待验收', '已完成']"
            :key="q"
            :class="{ active: queue === q }"
            @click="
              queue = q;
              page = 1;
            "
          >
            {{ q }}
          </button>
        </div>
        <div class="maintenance-toolbar">
          <div>
            <ElButton v-if="kind !== 'tasks' && can('新增')" type="primary" @click="open()"
              >＋ 新增</ElButton
            ><ElButton
              v-if="kind === 'tasks' && can('验收')"
              :disabled="!selected.length"
              @click="acceptance = true"
              >批量验收<span v-if="selected.length">（{{ selected.length }}）</span></ElButton
            >
          </div>
          <form @submit.prevent="page = 1">
            <ListToolbarIcons
              mode="filter"
              :filter-open="filterOpen"
              :columns-open="columnsOpen"
              @toggle-filter="filterOpen = !filterOpen"
            /><ListSearchInput
              v-model="keyword"
              :placeholder="
                kind === 'tasks' ? '搜索任务、设备、部门、负责人' : '搜索编号、名称、部门'
              "
            /><ElButton
              v-if="kind === 'tasks'"
              :class="{ active: conditions.length }"
              @click="advancedOpen = true"
              >筛选器<span v-if="conditions.length">（{{ conditions.length }}）</span></ElButton
            ><button type="button" class="text-action" @click="reset">重置</button>
          </form>
        </div>
        <AppliedFilterChips
          v-if="kind === 'tasks'"
          :conditions="conditions"
          :fields="advancedFields"
          @change="
            conditions = $event;
            page = 1;
          "
        /><AdvancedFilterDrawer
          v-if="kind === 'tasks'"
          v-model="advancedOpen"
          :conditions="conditions"
          :fields="advancedFields"
          @apply="
            conditions = $event;
            page = 1;
          "
        />
        <p v-if="error" class="maintenance-error">{{ error }}</p>
        <div class="maintenance-list">
          <div class="maintenance-table-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th v-if="kind === 'tasks'" class="check-cell"></th>
                  <th
                    v-for="[key, label] in activeFields"
                    :key="key"
                    :style="
                      widths[key!]
                        ? {
                            width: widths[key!] + 'px',
                            minWidth: widths[key!] + 'px',
                            maxWidth: widths[key!] + 'px',
                          }
                        : undefined
                    "
                    @click="
                      sortAsc = sortKey === key ? !sortAsc : true;
                      sortKey = key!;
                    "
                  >
                    {{ label }}
                    <span class="sort-mark">{{
                      sortKey === key ? (sortAsc ? '↑' : '↓') : '↕'
                    }}</span>
                    <span
                      class="column-resize-grip"
                      @click.stop
                      @mousedown="resize($event, key!)"
                      aria-hidden="true"
                    ></span>
                  </th>
                  <th class="list-action-column">操作</th>
                </tr>
                <tr v-if="filterOpen">
                  <th v-if="kind === 'tasks'"></th>
                  <th v-for="[key] in activeFields" :key="key">
                    <ElInput
                      v-model="columnFilters[key!]"
                      size="small"
                      clearable
                      placeholder="筛选"
                      @input="page = 1"
                    />
                  </th>
                  <th class="list-action-column"></th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in pageRows" :key="row.id">
                  <td v-if="kind === 'tasks'">
                    <input
                      v-model="selected"
                      type="checkbox"
                      :value="row.id"
                      :disabled="
                        !(row as MaintenanceTask).canAccept ||
                        (row as MaintenanceTask).status !== '待验收'
                      "
                      :aria-label="'选择' + row.code"
                    />
                  </td>
                  <td v-for="[key] in activeFields" :key="key">
                    <button
                      v-if="key === 'code'"
                      class="text-action code-link"
                      @click="
                        kind === 'tasks'
                          ? router.push({ name: 'maintenance-task', params: { id: row.id } })
                          : open(row as MaintenanceDefinition)
                      "
                    >
                      {{ cell(row, key!) }}
                    </button>
                    <MaintenanceStatus v-else-if="key === 'status'" :status="cell(row, key!)" />
                    <span v-else :title="cell(row, key!)">{{ cell(row, key!) }}</span>
                  </td>
                  <td class="maintenance-actions list-action-column">
                    <template v-if="kind === 'tasks'"
                      ><button
                        class="text-action"
                        @click="router.push({ name: 'maintenance-task', params: { id: row.id } })"
                      >
                        {{
                          (row as MaintenanceTask).canExecute
                            ? '处理'
                            : (row as MaintenanceTask).canAccept &&
                                (row as MaintenanceTask).status === '待验收'
                              ? '验收'
                              : '查看'
                        }}
                      </button></template
                    ><template v-else
                      ><button
                        v-if="can('编辑')"
                        class="text-action"
                        @click="open(row as MaintenanceDefinition)"
                      >
                        编辑</button
                      ><button
                        v-if="can('删除') && !(row as MaintenanceDefinition).system"
                        class="text-action danger"
                        @click="remove(row as MaintenanceDefinition)"
                      >
                        删除
                      </button></template
                    >
                  </td>
                </tr>
                <tr v-if="!pageRows.length">
                  <td :colspan="activeFields.length + 2" class="maintenance-empty">
                    {{ loading ? '加载中…' : '暂无数据' }}
                  </td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer class="maintenance-list-footer">
            <span>共 {{ filtered.length }} 条</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="filtered.length"
            /><ListToolbarIcons
              mode="footer"
              :can-export="can('导出')"
              :filter-open="filterOpen"
              :columns-open="columnsOpen"
              @toggle-columns="columnsOpen = !columnsOpen"
              @export="exportRows"
              ><template #columns
                ><label v-for="[key, label] in fields" :key="key"
                  ><input
                    v-model="visibleColumns"
                    type="checkbox"
                    :value="key"
                    :disabled="key === 'code'"
                  />{{ label }}</label
                ></template
              ></ListToolbarIcons
            >
          </footer>
        </div>
      </div>
    </div>
    <template v-else>
      <header class="maintenance-edit-head">
        <AppBackButton :label="`返回${title}列表`" :disabled="busy" @click="back" /><span
          >{{ title }} / <b>{{ form.id ? '编辑' : '新增' }}</b></span
        >
      </header>
      <div class="maintenance-edit-body">
        <p v-if="error" class="maintenance-error">{{ error }}</p>
        <section class="maintenance-form-section">
          <div class="maintenance-form-grid">
            <label
              ><span>编号</span
              ><ElInput v-model="form.code" placeholder="留空自动生成编号" maxlength="64" /></label
            ><label
              ><span class="required">名称</span
              ><ElInput
                v-model="form.name"
                :placeholder="'输入' + title + '名称'"
                maxlength="150" /></label
            ><label
              ><span class="required">所属部门</span
              ><TreeSelect
                v-model="form.departmentId"
                :nodes="orgNodes"
                placeholder="选择所属部门" /></label
            ><label v-if="kind === 'plans'"
              ><span>启用</span><ElSwitch v-model="form.enabled" /></label
            ><label class="full-row"
              ><span>说明</span
              ><ElInput v-model="form.remark" type="textarea" :rows="2" placeholder="填写说明"
            /></label>
          </div>
        </section>
        <template v-if="kind === 'standards'"
          ><section class="maintenance-form-section">
            <h3>适用范围</h3>
            <div class="maintenance-form-grid">
              <label class="full-row"
                ><span>设备</span>
                <div class="inline-control">
                  <ElButton @click="chooseEquipment">选择设备</ElButton
                  ><ElTag
                    v-for="id in form.equipmentIds"
                    :key="id"
                    closable
                    @close="form.equipmentIds = form.equipmentIds.filter((x) => x !== id)"
                    >{{ eq(id)?.name }}</ElTag
                  >
                </div></label
              ><label class="full-row"
                ><span>设备类别</span>
                <div class="inline-control">
                  <TreeSelect
                    v-model="category"
                    :nodes="categoryNodes"
                    placeholder="选择设备类别"
                  /><ElButton
                    @click="
                      category &&
                        !form.categoryNames.includes(category) &&
                        form.categoryNames.push(category);
                      category = '';
                    "
                    >添加</ElButton
                  ><ElTag
                    v-for="c in form.categoryNames"
                    :key="c"
                    closable
                    @close="form.categoryNames = form.categoryNames.filter((x) => x !== c)"
                    >{{ c }}</ElTag
                  >
                </div></label
              >
            </div>
          </section>
          <section class="maintenance-form-section">
            <div class="section-title">
              <h3>
                保养项目 <small>{{ form.items.length }} 项</small>
              </h3>
              <div class="item-toolbar">
                <ElDropdown :disabled="!selectedItems.length" @command="openItemBatch">
                  <ElButton :disabled="!selectedItems.length"
                    >批量操作<span v-if="selectedItems.length">（{{ selectedItems.length }}）</span
                    >⌄</ElButton
                  >
                  <template #dropdown
                    ><ElDropdownMenu
                      ><ElDropdownItem command="type">修改类型</ElDropdownItem
                      ><ElDropdownItem command="options">修改可选项</ElDropdownItem
                      ><ElDropdownItem command="normalOptions">修改正常选项</ElDropdownItem
                      ><ElDropdownItem command="delete" divided
                        >删除</ElDropdownItem
                      ></ElDropdownMenu
                    ></template
                  >
                </ElDropdown>
                <ElButton type="primary" @click="addItem">＋ 添加项目</ElButton>
              </div>
            </div>
            <div class="item-table-scroll" :class="{ 'is-empty': !form.items.length }">
              <table class="business-table item-edit-table">
                <colgroup>
                  <col style="width: 36px" />
                  <col style="width: 44px" />
                  <col style="width: 120px" />
                  <col style="width: 180px" />
                  <col style="width: 110px" />
                  <col style="width: 240px" />
                  <col style="width: 96px" />
                  <col style="width: 200px" />
                  <col style="width: 180px" />
                  <col style="width: 100px" />
                  <col style="width: 100px" />
                  <col style="width: 52px" />
                  <col style="width: 64px" />
                  <col style="width: 180px" />
                  <col style="width: 180px" />
                  <col style="width: 180px" />
                </colgroup>
                <thead>
                  <tr>
                    <th class="item-check">
                      <ElCheckbox
                        aria-label="选择全部项目"
                        :model-value="
                          form.items.length > 0 && selectedItems.length === form.items.length
                        "
                        :indeterminate="
                          selectedItems.length > 0 && selectedItems.length < form.items.length
                        "
                        @change="selectedItems = $event ? form.items.map((item) => item.id) : []"
                      />
                    </th>
                    <th class="item-number">序号</th>
                    <th class="item-module">模块</th>
                    <th class="item-name"><span class="required">项目名称</span></th>
                    <th><span class="required">保养类型</span></th>
                    <th><span class="required">操作要求</span></th>
                    <th>结果类型</th>
                    <th>可选项</th>
                    <th>正常选项</th>
                    <th>数值下限</th>
                    <th>数值上限</th>
                    <th>必填</th>
                    <th>必拍照</th>
                    <th>备注</th>
                    <th>参考附件</th>
                    <th class="item-actions">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <template v-for="(item, index) in form.items" :key="item.id">
                    <tr :class="{ 'item-error-row': itemErrors[item.id] }">
                      <td class="item-check">
                        <ElCheckbox
                          :aria-label="'选择第' + (index + 1) + '行'"
                          :model-value="selectedItems.includes(item.id)"
                          @change="
                            selectedItems = $event
                              ? [...selectedItems, item.id]
                              : selectedItems.filter((id) => id !== item.id)
                          "
                        />
                      </td>
                      <td class="item-number">{{ index + 1 }}</td>
                      <td class="item-module">
                        <ElInput v-model="item.module" aria-label="模块" placeholder="模块" />
                      </td>
                      <td class="item-name">
                        <ElInput
                          v-model="item.name"
                          aria-label="项目名称"
                          placeholder="项目名称"
                          maxlength="150"
                        />
                      </td>
                      <td>
                        <ElSelect v-model="item.typeId" aria-label="保养类型" placeholder="选择类型"
                          ><ElOption v-for="t in types" :key="t.id" :label="t.name" :value="t.id"
                        /></ElSelect>
                      </td>
                      <td>
                        <ElInput
                          v-model="item.content"
                          aria-label="操作要求"
                          type="textarea"
                          :autosize="{ minRows: 1, maxRows: 3 }"
                          placeholder="填写操作要求"
                        />
                      </td>
                      <td>
                        <ElSelect v-model="item.valueType" aria-label="结果类型"
                          ><ElOption
                            v-for="v in ['选项', '数值', '文本']"
                            :key="v"
                            :label="v"
                            :value="v"
                        /></ElSelect>
                      </td>
                      <td>
                        <ElSelect
                          v-if="item.valueType === '选项'"
                          v-model="item.options"
                          aria-label="可选项"
                          placeholder="输入选项，回车添加"
                          multiple
                          filterable
                          allow-create
                          default-first-option
                          collapse-tags
                          collapse-tags-tooltip
                          @change="changeItemOptions(item)"
                          ><ElOption v-for="v in item.options" :key="v" :label="v" :value="v"
                        /></ElSelect>
                        <span v-else class="item-muted"></span>
                      </td>
                      <td>
                        <ElSelect
                          v-if="item.valueType === '选项'"
                          v-model="item.normalOptions"
                          aria-label="正常选项"
                          placeholder="选择正常选项"
                          multiple
                          collapse-tags
                          collapse-tags-tooltip
                          ><ElOption v-for="v in item.options" :key="v" :label="v" :value="v"
                        /></ElSelect>
                        <span v-else class="item-muted"></span>
                      </td>
                      <td>
                        <AppDecimalInput
                          v-if="item.valueType === '数值'"
                          v-model="item.lower"
                          aria-label="数值下限"
                          placeholder="不限"
                        /><span v-else class="item-muted"></span>
                      </td>
                      <td>
                        <AppDecimalInput
                          v-if="item.valueType === '数值'"
                          v-model="item.upper"
                          aria-label="数值上限"
                          placeholder="不限"
                        /><span v-else class="item-muted"></span>
                      </td>
                      <td class="item-checkbox">
                        <ElCheckbox v-model="item.required" aria-label="必填" />
                      </td>
                      <td class="item-checkbox">
                        <ElCheckbox v-model="item.photoRequired" aria-label="必拍照" />
                      </td>
                      <td>
                        <ElInput
                          v-model="item.remark"
                          aria-label="项目备注"
                          type="textarea"
                          :autosize="{ minRows: 1, maxRows: 3 }"
                          placeholder="项目补充说明"
                        />
                      </td>
                      <td><MaintenanceAttachments v-model="item.references" /></td>
                      <td class="item-actions">
                        <div class="item-row-actions">
                          <button type="button" class="text-action" @click="copyItem(item, index)">
                            复制
                          </button>
                          <button
                            type="button"
                            class="text-action danger"
                            @click="removeItem(item.id)"
                          >
                            删除
                          </button>
                          <button
                            type="button"
                            class="item-move"
                            :disabled="index === 0"
                            aria-label="上移项目"
                            title="上移"
                            @click="moveItem(index, -1)"
                          >
                            ↑
                          </button>
                          <button
                            type="button"
                            class="item-move"
                            :disabled="index === form.items.length - 1"
                            aria-label="下移项目"
                            title="下移"
                            @click="moveItem(index, 1)"
                          >
                            ↓
                          </button>
                        </div>
                      </td>
                    </tr>
                    <tr v-if="itemErrors[item.id]" class="item-error-message">
                      <td colspan="16">{{ itemErrors[item.id] }}</td>
                    </tr>
                  </template>
                  <tr v-if="!form.items.length">
                    <td colspan="16" class="maintenance-empty">
                      添加保养项目，直接在表格中填写操作要求和结果标准
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
        <template v-if="kind === 'plans'"
          ><section class="maintenance-form-section">
            <h3>周期与人员</h3>
            <div class="maintenance-form-grid">
              <label
                ><span>下次时间</span
                ><ElSelect v-model="form.calculation"
                  ><ElOption label="按固定间隔" value="固定间隔" /><ElOption
                    label="按完成时间"
                    value="完成时间" /></ElSelect></label
              ><label
                ><span>提前生成</span>
                <div class="inline-control">
                  <ElInputNumber
                    v-model="form.advanceDays"
                    :min="0"
                    :max="365"
                    controls-position="right"
                  /><span>天</span>
                </div></label
              ><label
                ><span>提前提醒</span>
                <div class="inline-control">
                  <ElInputNumber
                    v-model="form.remindDays"
                    :min="0"
                    :max="365"
                    controls-position="right"
                  /><span>天</span>
                </div></label
              ><label
                ><span>自动填写</span
                ><ElSelect v-model="form.autoFill"
                  ><ElOption
                    v-for="v in ['无', '正常值', '上次结果']"
                    :key="v"
                    :label="v"
                    :value="v" /></ElSelect></label
              ><label><span>扫码开始</span><ElSwitch v-model="form.scanStart" /></label
              ><label
                ><span>验收规则</span
                ><ElInput model-value="任一验收人通过即可完成" disabled /></label
              ><label class="full-row"
                ><span class="required">保养人</span
                ><ElSelect v-model="form.owners" multiple filterable placeholder="选择保养人"
                  ><ElOption
                    v-for="u in choices.users"
                    :key="u.id"
                    :label="u.name + ' · ' + u.department"
                    :value="u.id" /></ElSelect></label
              ><label class="full-row"
                ><span class="required">验收人</span
                ><ElSelect
                  v-model="form.reviewers"
                  multiple
                  filterable
                  placeholder="选择具有保养验收权限的人员"
                  ><ElOption
                    v-for="u in choices.users"
                    :key="u.id"
                    :label="u.name + ' · ' + u.department"
                    :value="u.id" /></ElSelect
              ></label>
            </div>
          </section>
          <section class="maintenance-form-section">
            <div class="section-title">
              <h3>
                计划设备 <small>{{ form.details.length }} 项</small>
              </h3>
              <div class="item-toolbar">
                <ElDropdown :disabled="!selectedPlanRows.length" @command="openPlanBatch"
                  ><ElButton :disabled="!selectedPlanRows.length"
                    >批量操作<span v-if="selectedPlanRows.length"
                      >（{{ selectedPlanRows.length }}）</span
                    >⌄</ElButton
                  ><template #dropdown
                    ><ElDropdownMenu
                      ><ElDropdownItem
                        v-for="(label, action) in planBatchLabels"
                        :key="action"
                        :command="action"
                        >{{ label }}</ElDropdownItem
                      ></ElDropdownMenu
                    ></template
                  ></ElDropdown
                ><ElButton type="primary" @click="chooseEquipment">＋ 选择设备</ElButton>
              </div>
            </div>
            <div class="item-table-scroll" :class="{ 'is-empty': !form.details.length }">
              <table class="business-table item-edit-table plan-edit-table">
                <colgroup>
                  <col style="width: 36px" />
                  <col style="width: 44px" />
                  <col style="width: 120px" />
                  <col style="width: 180px" />
                  <col style="width: 120px" />
                  <col style="width: 340px" />
                  <col style="width: 190px" />
                  <col style="width: 164px" />
                  <col style="width: 130px" />
                  <col style="width: 200px" />
                  <col style="width: 200px" />
                  <col style="width: 180px" />
                  <col style="width: 160px" />
                </colgroup>
                <thead>
                  <tr>
                    <th class="item-check">
                      <ElCheckbox
                        aria-label="选择全部计划设备"
                        :model-value="
                          form.details.length > 0 && selectedPlanRows.length === form.details.length
                        "
                        :indeterminate="
                          selectedPlanRows.length > 0 &&
                          selectedPlanRows.length < form.details.length
                        "
                        @change="
                          selectedPlanRows = $event
                            ? form.details.map((detail) => detail.clientKey!)
                            : []
                        "
                      />
                    </th>
                    <th class="item-number">序号</th>
                    <th class="item-module">设备编码</th>
                    <th class="item-name">设备名称</th>
                    <th>保养类型</th>
                    <th>保养项目</th>
                    <th>保养周期</th>
                    <th>下次保养时间</th>
                    <th>有效时长（天）</th>
                    <th>负责人</th>
                    <th>审核人</th>
                    <th>说明</th>
                    <th class="item-actions">操作</th>
                  </tr>
                </thead>
                <tbody>
                  <tr v-for="(detail, index) in form.details" :key="detail.clientKey">
                    <td class="item-check">
                      <ElCheckbox
                        :aria-label="'选择计划设备第' + (index + 1) + '行'"
                        :model-value="selectedPlanRows.includes(detail.clientKey!)"
                        @change="
                          selectedPlanRows = $event
                            ? [...selectedPlanRows, detail.clientKey!]
                            : selectedPlanRows.filter((key) => key !== detail.clientKey)
                        "
                      />
                    </td>
                    <td class="item-number">{{ index + 1 }}</td>
                    <td class="item-module">{{ eq(detail.equipmentId)?.code }}</td>
                    <td class="item-name">{{ eq(detail.equipmentId)?.name }}</td>
                    <td>
                      <ElSelect
                        v-model="detail.typeId"
                        placeholder="保养类型"
                        @change="typeChanged(detail)"
                        ><ElOption
                          v-for="type in types"
                          :key="type.id"
                          :label="type.name"
                          :value="type.id"
                      /></ElSelect>
                    </td>
                    <td>
                      <button
                        type="button"
                        class="plan-project-trigger"
                        @click="openProjectPicker(detail)"
                        :title="projectSummary(detail)"
                      >
                        <span>{{
                          detail.projectRefs?.length
                            ? `已选 ${detail.projectRefs.length} 项`
                            : '选择保养项目'
                        }}</span
                        ><span class="project-trigger-arrow">›</span>
                      </button>
                    </td>
                    <td>
                      <div class="plan-cycle-control">
                        <ElInputNumber
                          v-model="detail.period"
                          @change="updateTaskDuration(detail)"
                          :min="1"
                          :max="10000"
                          controls-position="right"
                        /><ElSelect v-model="detail.unit" @change="updateTaskDuration(detail)"
                          ><ElOption
                            v-for="unit in ['日', '周', '月', '年']"
                            :key="unit"
                            :label="unit"
                            :value="unit"
                        /></ElSelect>
                      </div>
                    </td>
                    <td>
                      <ElDatePicker
                        v-model="detail.nextDate"
                        type="date"
                        value-format="YYYY-MM-DD"
                        placeholder="选择日期"
                      />
                    </td>
                    <td>
                      <ElInputNumber
                        v-model="detail.validDays"
                        :min="0"
                        :max="36500"
                        controls-position="right"
                      />
                    </td>
                    <td>
                      <ElSelect
                        v-model="detail.owners"
                        multiple
                        filterable
                        collapse-tags
                        collapse-tags-tooltip
                        placeholder="沿用计划负责人"
                        ><ElOption
                          v-for="user in choices.users"
                          :key="user.id"
                          :label="user.name"
                          :value="user.id"
                      /></ElSelect>
                    </td>
                    <td>
                      <ElSelect
                        v-model="detail.reviewers"
                        multiple
                        filterable
                        collapse-tags
                        collapse-tags-tooltip
                        placeholder="沿用计划审核人"
                        ><ElOption
                          v-for="user in choices.users"
                          :key="user.id"
                          :label="user.name"
                          :value="user.id"
                      /></ElSelect>
                    </td>
                    <td><ElInput v-model="detail.remark" placeholder="0天表示不过期" /></td>
                    <td class="item-actions">
                      <div class="item-row-actions">
                        <button class="text-action" @click="copyPlanRow(detail)">复制</button
                        ><button
                          class="text-action danger"
                          @click="removePlanRow(detail.clientKey!)"
                        >
                          删除
                        </button>
                      </div>
                    </td>
                  </tr>
                  <tr v-if="!form.details.length">
                    <td colspan="13" class="maintenance-empty">
                      选择设备后，按保养类型自动生成计划行
                    </td>
                  </tr>
                </tbody>
              </table>
            </div>
          </section></template
        >
      </div>
      <AppFormFooter
        ><template #hint><span class="required-note">* 为必填项</span></template
        ><ElButton @click="back">取消</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">提交</ElButton></AppFormFooter
      >
    </template>
    <AppDialog v-model="planBatchOpen" :title="planBatchLabels[planBatchAction]" width="540px">
      <p class="item-batch-note">修改选中的 {{ selectedPlanRows.length }} 行，提交后统一保存。</p>
      <div class="small-form">
        <label v-if="planBatchAction === 'type'"
          ><span>保养类型</span
          ><ElSelect v-model="planBatchType" placeholder="选择类型"
            ><ElOption
              v-for="type in types"
              :key="type.id"
              :label="type.name"
              :value="type.id" /></ElSelect></label
        ><label v-else-if="planBatchAction === 'date'"
          ><span>下次保养</span
          ><ElDatePicker
            v-model="planBatchDate"
            type="date"
            value-format="YYYY-MM-DD"
            placeholder="选择日期" /></label
        ><label v-else-if="planBatchAction === 'duration'"
          ><span>有效时长</span
          ><ElInputNumber
            v-model="planBatchDuration"
            :min="0"
            :max="36500"
            controls-position="right"
          />天</label
        ><label v-else
          ><span>{{ planBatchAction === 'owners' ? '负责人' : '审核人' }}</span
          ><ElSelect v-model="planBatchUsers" multiple filterable placeholder="留空沿用计划人员"
            ><ElOption
              v-for="user in choices.users"
              :key="user.id"
              :label="user.name + ' · ' + user.department"
              :value="user.id" /></ElSelect
        ></label>
      </div>
      <p v-if="planBatchAction === 'type'" class="item-batch-note">
        周期同步更新，并重新关联适用于该设备、属于新保养类型的项目。
      </p>
      <template #footer
        ><ElButton @click="planBatchOpen = false">取消</ElButton
        ><ElButton type="primary" @click="applyPlanBatch">确定</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="itemBatchOpen" :title="itemBatchLabels[itemBatchAction]" width="540px">
      <p class="item-batch-note">
        {{
          itemBatchAction === 'type'
            ? `修改选中的 ${selectedItems.length} 个项目的保养类型。`
            : `仅修改选中的 ${selectedOptionItems.length} 个选项型项目。`
        }}
      </p>
      <div class="small-form">
        <label v-if="itemBatchAction === 'type'"
          ><span>保养类型</span
          ><ElSelect v-model="itemBatchType" placeholder="选择保养类型"
            ><ElOption v-for="t in types" :key="t.id" :label="t.name" :value="t.id" /></ElSelect
        ></label>
        <label v-else-if="itemBatchAction === 'options'"
          ><span>可选项</span
          ><ElSelect
            v-model="itemBatchValues"
            multiple
            filterable
            allow-create
            default-first-option
            placeholder="输入选项，回车添加"
            ><ElOption
              v-for="value in itemBatchValues"
              :key="value"
              :label="value"
              :value="value" /></ElSelect
        ></label>
        <label v-else
          ><span>正常选项</span
          ><ElSelect v-model="itemBatchValues" multiple placeholder="选择共有选项"
            ><ElOption
              v-for="value in sharedNormalOptions"
              :key="value"
              :label="value"
              :value="value" /></ElSelect
        ></label>
      </div>
      <p v-if="itemBatchAction === 'options'" class="item-batch-note">
        替换原可选项；已移除的正常选项会同步清除，请重新选择正常选项。
      </p>
      <p
        v-if="itemBatchAction === 'normalOptions' && !sharedNormalOptions.length"
        class="maintenance-error"
      >
        所选项目没有共有选项，请先统一可选项。
      </p>
      <template #footer
        ><ElButton @click="itemBatchOpen = false">取消</ElButton
        ><ElButton type="primary" @click="applyItemBatch">确定</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="typeDialog" :title="form.id ? '编辑保养类型' : '新增保养类型'" :busy="busy"
      ><div class="small-form">
        <label><span>编码</span><ElInput v-model="form.code" placeholder="留空自动生成" /></label
        ><label
          ><span class="required">名称</span
          ><ElInput v-model="form.name" placeholder="输入保养类型名称" /></label
        ><label><span>符号</span><ElInput v-model="form.symbol" placeholder="例如：△" /></label
        ><label
          ><span>周期</span>
          <div class="inline-control">
            <ElInputNumber v-model="form.period" :min="1" :max="10000" /><ElSelect
              v-model="form.unit"
              ><ElOption v-for="u in ['日', '周', '月', '年']" :key="u" :value="u" :label="u"
            /></ElSelect></div></label
        ><label><span>启用</span><ElSwitch v-model="form.enabled" /></label>
      </div>
      <template #footer
        ><ElButton @click="typeDialog = false">取消</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">提交</ElButton></template
      ></AppDialog
    >
    <AppDialog
      v-model="projectPickerOpen"
      title="选择保养项目"
      width="min(1100px, calc(100vw - 48px))"
    >
      <div class="project-picker-toolbar">
        <span
          >{{ eq(projectPickerRow?.equipmentId || '')?.name }} ·
          {{ types.find((t) => t.id === projectPickerRow?.typeId)?.name }}</span
        >
        <ElSwitch v-model="projectPickerAll" active-text="查看所有标准" />
      </div>
      <div class="project-picker-layout">
        <aside class="project-picker-standards">
          <h4>保养标准</h4>
          <button
            v-for="standard in projectPickerStandards"
            :key="standard.id"
            type="button"
            :class="{ active: projectPickerStandard === standard.id }"
            @click="projectPickerStandard = standard.id"
          >
            <span>{{ standard.name }}</span
            ><small
              >{{ projectPickerValues.filter((v) => v.startsWith(standard.id + ':')).length }} /
              {{
                standard.items.filter((i) => i.typeId === projectPickerRow?.typeId).length
              }}</small
            >
          </button>
          <p v-if="!projectPickerStandards.length" class="picker-empty">
            没有关联标准，可切换查看所有标准
          </p>
        </aside>
        <section class="project-picker-projects">
          <div class="project-picker-search">
            <span>已选 {{ projectPickerValues.length }} 项</span
            ><ElInput
              v-model="projectPickerSearch"
              clearable
              placeholder="搜索模块、项目名称、内容"
            />
          </div>
          <div class="project-picker-table-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th>
                    <ElCheckbox
                      :model-value="projectPickerAllChecked"
                      :indeterminate="projectPickerSomeChecked && !projectPickerAllChecked"
                      aria-label="选择当前标准全部项目"
                      @change="togglePickerAll(Boolean($event))"
                    />
                  </th>
                  <th>模块</th>
                  <th>项目名称</th>
                  <th>保养内容</th>
                  <th>结果类型</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in projectPickerItems"
                  :key="item.id"
                  @click="
                    togglePickerItem(item, !projectPickerValues.includes(pickerItemKey(item)))
                  "
                >
                  <td @click.stop>
                    <ElCheckbox
                      :model-value="projectPickerValues.includes(pickerItemKey(item))"
                      :aria-label="'选择' + item.name"
                      @change="togglePickerItem(item, Boolean($event))"
                    />
                  </td>
                  <td>{{ item.module || '' }}</td>
                  <td>{{ item.name }}</td>
                  <td>{{ item.content }}</td>
                  <td>{{ item.valueType }}</td>
                </tr>
                <tr v-if="!projectPickerItems.length">
                  <td colspan="5" class="picker-empty">暂无符合本行保养类型的项目</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p class="project-picker-hint">
            可跨标准选择同一保养类型的项目，切换标准会保留已选项目。
          </p>
        </section>
      </div>
      <template #footer
        ><ElButton @click="projectPickerOpen = false">取消</ElButton
        ><ElButton type="primary" @click="confirmProjectPicker">确定</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="equipmentDialog" title="选择设备" width="820px"
      ><div class="section-title">
        <span>已选 {{ picked.length }} 台</span
        ><ListSearchInput v-model="pickSearch" placeholder="搜索设备名称、编号、型号、类别" />
      </div>
      <div class="equipment-picker-table">
        <table class="business-table">
          <thead>
            <tr>
              <th></th>
              <th>设备编号</th>
              <th>设备名称</th>
              <th>类别</th>
              <th>规格型号</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="e in eqRows" :key="e.id">
              <td>
                <ElCheckbox
                  :model-value="picked.includes(e.id)"
                  @change="
                    (v) => (picked = v ? [...picked, e.id] : picked.filter((id) => id !== e.id))
                  "
                />
              </td>
              <td>{{ e.code }}</td>
              <td>{{ e.name }}</td>
              <td>{{ e.category }}</td>
              <td>{{ e.model }}</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer
        ><ElButton @click="equipmentDialog = false">取消</ElButton
        ><ElButton type="primary" @click="confirmEquipment">确定</ElButton></template
      ></AppDialog
    >
    <MaintenanceAcceptanceDialog
      v-model="acceptance"
      :busy="busy"
      :count="selected.length"
      @submit="submitAcceptance"
    />
  </section>
</template>
<style scoped>
.task-list-layout {
  display: flex;
  gap: 12px;
  min-height: 0;
  height: 100%;
}
.task-list-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}

.maintenance-page {
  height: calc(100vh - 54px);
  padding: 0 12px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  font-size: 13px;
  color: #334155;
  overflow: hidden;
}
.maintenance-tabs {
  display: flex;
  border-bottom: 1px solid #e1e7ef;
  flex: none;
  height: 46px;
  gap: 22px;
}
.maintenance-tabs button,
.maintenance-queues button {
  border: 0;
  background: none;
  font: inherit;
  color: #728096;
  cursor: pointer;
}
.maintenance-tabs button {
  padding: 0 4px;
  border-bottom: 2px solid transparent;
  font-weight: 600;
}
.maintenance-tabs button.active {
  color: var(--brand);
  border-color: var(--brand);
}
.maintenance-queues {
  display: flex;
  gap: 4px;
  padding: 12px 0 0;
  flex: none;
}
.maintenance-queues button {
  padding: 6px 12px;
  border-radius: 4px;
}
.maintenance-queues button.active {
  background: var(--brand-soft);
  color: var(--brand);
}
.maintenance-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 12px 0;
  flex: none;
}
.maintenance-toolbar > div {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.maintenance-toolbar > div > .el-button {
  margin-left: 0;
}
.maintenance-toolbar form {
  display: flex;
  align-items: center;
  gap: 8px;
}
.maintenance-list {
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e1e7ef;
  border-radius: 6px 6px 0 0;
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.maintenance-table-scroll {
  flex: 1;
  overflow: auto;
  min-height: 0;
}
.maintenance-page table {
  width: 100%;
  border-collapse: collapse;
  white-space: nowrap;
  font-size: 13px;
}
.maintenance-page th {
  background: #f5f7fa;
  color: #6d7a8e;
  font-weight: 600;
  text-align: left;
  position: sticky;
  top: 0;
  z-index: 1;
  cursor: pointer;
}
.maintenance-page th,
.maintenance-page td {
  height: 44px;
  padding: 9px 14px;
  border-bottom: 1px solid #edf0f4;
  max-width: 320px;
}
.maintenance-page td > span:not(.el-tag):not(.maintenance-task-status) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.maintenance-page thead tr:nth-child(2) th {
  top: 44px;
}
.maintenance-page tbody tr:hover {
  background: #f7f9ff;
}
.column-resize-grip {
  position: absolute;
  right: 0;
  top: 0;
  width: 7px;
  height: 100%;
  cursor: col-resize;
}
.column-resize-grip:hover {
  background: var(--brand-soft);
}
.sort-mark {
  float: right;
  margin-left: 14px;
  color: #a9b3c1;
  font-size: 11px;
}
.maintenance-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.maintenance-list-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 42px;
  padding: 0 14px;
  border-top: 1px solid #e1e7ef;
  flex: none;
  color: #718097;
}
.maintenance-list-footer > span {
  white-space: nowrap;
}
.maintenance-error {
  color: #d34455;
  padding: 10px;
  background: #fff1f2;
}
.maintenance-empty {
  padding: 30px !important;
  text-align: center;
  color: #97a3b4;
}
.code-link {
  color: var(--brand);
  font-weight: 500;
}
.danger {
  color: #d94b60;
}
.check-cell {
  width: 40px;
}
.maintenance-edit-head {
  display: flex;
  align-items: center;
  gap: 12px;
  height: 48px;
  border-bottom: 1px solid #e3e8ef;
  flex: none;
  background: #fff;
  margin: 0 -12px;
  padding: 0 16px;
}
.maintenance-edit-body {
  flex: 1;
  overflow: auto;
  min-height: 0;
  background: #fff;
  margin: 0 -12px;
  padding: 16px 20px;
}
.maintenance-form-section {
  padding: 6px 0 14px;
  margin-bottom: 12px;
  border-bottom: 1px solid #edf0f4;
}
.maintenance-form-section h3 {
  font-size: 14px;
  margin: 0 0 14px;
  font-weight: 600;
  color: #26364c;
}
.maintenance-form-section h3:before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 13px;
  border-radius: 2px;
  background: var(--brand);
  margin-right: 8px;
}
.maintenance-form-grid,
.maintenance-item-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px 24px;
}
.item-table-scroll {
  width: 100%;
  min-width: 0;
  overflow-x: scroll;
  scrollbar-gutter: stable;
  height: clamp(160px, 48vh, 640px);
  overflow-y: auto;
  border: 1px solid #e3e8f0;
  border-radius: 5px;
}
.item-table-scroll.is-empty {
  height: 180px;
  overflow: hidden;
  scrollbar-gutter: auto;
}
.maintenance-page .item-table-scroll.is-empty .item-edit-table {
  width: 100%;
  min-width: 0;
}
.maintenance-page .item-table-scroll.is-empty thead {
  display: none;
}
.maintenance-page .item-table-scroll.is-empty .maintenance-empty {
  height: 178px;
  text-align: center;
  white-space: normal;
}
.maintenance-page .item-edit-table {
  width: 2062px;
  min-width: 2062px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}
.maintenance-page .item-edit-table th {
  position: sticky;
  top: 0;
  z-index: 3;
  background: #f5f7fa;
  height: 40px;
  padding: 8px;
  color: #617086;
  text-align: left;
  font-weight: 600;
  border-bottom: 1px solid #e3e8f0;
}
.maintenance-page .item-edit-table td {
  padding: 10px 8px;
  background: #fff;
  border-bottom: 1px solid #edf0f4;
  vertical-align: top;
}
.item-edit-table tr:last-child td {
  border-bottom: 0;
}
/* Keep item identity visible while editing distant columns. */
.maintenance-page .item-edit-table .item-check,
.maintenance-page .item-edit-table .item-number,
.maintenance-page .item-edit-table .item-module,
.maintenance-page .item-edit-table .item-name,
.maintenance-page .item-edit-table .item-actions {
  position: sticky;
  z-index: 2;
  background: #fff;
}
.maintenance-page .item-edit-table .item-check {
  left: 0;
  text-align: center;
}
.maintenance-page .item-edit-table .item-number {
  left: 36px;
  text-align: center;
  color: #8995a7;
}
.maintenance-page .item-edit-table .item-module {
  left: 80px;
}
.maintenance-page .item-edit-table .item-name {
  left: 200px;
  border-right: 1px solid #e4e9f1;
  box-shadow: 4px 0 6px -5px #64748b40;
}
.maintenance-page .item-edit-table .item-actions {
  right: 0;
  border-left: 1px solid #e4e9f1;
  box-shadow: -4px 0 6px -5px #64748b40;
}
.maintenance-page .item-edit-table th.item-check,
.maintenance-page .item-edit-table th.item-number,
.maintenance-page .item-edit-table th.item-module,
.maintenance-page .item-edit-table th.item-name,
.maintenance-page .item-edit-table th.item-actions {
  z-index: 4;
  background: #f4f7fb;
}
.maintenance-page .item-edit-table th {
  height: 42px;
  padding: 9px 10px;
  background: #f4f7fb;
  font-size: 13px;
  color: #586981;
  cursor: default;
}
.maintenance-page .item-edit-table td {
  padding: 12px 10px;
  vertical-align: middle;
  white-space: normal;
}
.maintenance-page .item-edit-table tbody tr:hover > td {
  background: #f8faff;
}
.item-edit-table :deep(.el-input__wrapper),
.item-edit-table :deep(.el-select__wrapper) {
  font-size: 13px;
  min-height: 34px;
  border-radius: 4px;
  background: #fff;
  box-shadow: 0 0 0 1px #e7ecf3 inset;
  padding: 1px 9px;
}
.item-edit-table :deep(.el-input__wrapper:hover),
.item-edit-table :deep(.el-select__wrapper:hover) {
  box-shadow: 0 0 0 1px #b6c7df inset;
}
.item-edit-table :deep(.el-input__wrapper.is-focus),
.item-edit-table :deep(.el-select__wrapper.is-focused) {
  box-shadow: 0 0 0 1px var(--brand) inset;
}
.item-edit-table :deep(.el-textarea__inner) {
  font-size: 13px;
  min-height: 34px !important;
  line-height: 22px;
  padding: 6px 9px;
  border-radius: 4px;
  box-shadow: 0 0 0 1px #e7ecf3 inset;
  resize: none;
}
.item-edit-table :deep(.el-textarea__inner:focus) {
  box-shadow: 0 0 0 1px var(--brand) inset;
}
.maintenance-page .plan-edit-table {
  width: 2064px;
  min-width: 2064px;
}
.plan-cycle-control {
  display: flex;
  gap: 6px;
  align-items: center;
}
.plan-cycle-control :deep(.el-input-number) {
  width: 100px;
  flex: none;
}
.plan-cycle-control > .el-select {
  width: 64px;
  flex: none;
}
.plan-edit-table :deep(.el-date-editor) {
  width: 100%;
}
.item-checkbox {
  text-align: center;
}
.item-row-actions {
  display: flex;
  align-items: center;
  flex-wrap: nowrap;
  white-space: nowrap;
  gap: 8px;
}
.item-row-actions .text-action {
  padding: 4px 0;
  font-size: 13px;
}
.item-move {
  border: 0;
  background: transparent;
  color: #62738a;
  cursor: pointer;
  padding: 3px 5px;
  font-size: 15px;
}
.item-move:disabled {
  opacity: 0.3;
  cursor: default;
}
.item-muted {
  display: inline-block;
  padding-top: 7px;
  color: #9aa6b6;
}
.item-edit-table .item-error-row td {
  background: #fff7f7;
}
.item-edit-table .item-error-message td {
  color: #c43e4c;
  background: #fff7f7;
  padding: 5px 14px;
}
.item-toolbar {
  display: flex;
  gap: 8px;
}
.item-batch-note {
  font-size: 13px;
  color: #8290a3;
  margin: 0 0 18px;
  line-height: 1.7;
}
.item-table-scroll::-webkit-scrollbar {
  width: 10px;
  height: 10px;
}
.item-table-scroll::-webkit-scrollbar-thumb {
  background: #b5becb;
  border: 2px solid #f3f5f8;
  border-radius: 5px;
}
.item-table-scroll::-webkit-scrollbar-track {
  background: #f3f5f8;
}
.item-table-bottom {
  display: flex;
  align-items: center;
  gap: 14px;
  padding-top: 12px;
  color: #8692a3;
}
.maintenance-item-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  padding: 12px 14px;
}
.maintenance-form-grid label,
.maintenance-item-grid label,
.small-form label {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.maintenance-form-grid label > span,
.maintenance-item-grid label > span,
.small-form label > span {
  flex: none;
  width: 78px;
  text-align: right;
  color: #68788e;
}
.maintenance-form-grid label > :last-child,
.maintenance-item-grid label > :last-child {
  min-width: 0;
  flex: 1;
}
.full-row {
  grid-column: 1/-1;
}
.inline-control {
  display: flex;
  gap: 8px;
  align-items: center;
  flex-wrap: wrap;
  min-width: 0;
}
.inline-control > .el-select {
  min-width: 80px;
  flex: 1;
}
.inline-control > :deep(.tree-select) {
  flex: 1;
  min-width: 150px;
}
.required:before {
  content: '*';
  color: #e45265;
  margin-right: 4px;
}
.required-note {
  color: #94a3b8;
}
.section-title {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
  margin-bottom: 14px;
}
.section-title h3 {
  margin: 0;
}
.section-title small {
  color: #8b98aa;
  font-weight: 400;
  margin-left: 8px;
}
.item-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 22px;
  height: 22px;
  margin-right: 10px;
  border-radius: 4px;
  background: var(--brand-soft);
  color: var(--brand);
}
.item-kind {
  margin-left: 12px;
}
.item-buttons {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  padding: 0 14px 12px;
}
.plan-equipment {
  padding: 16px 0 22px;
  border-bottom: 1px solid #edf0f4;
}
.plan-equipment:last-child {
  border-bottom: 0;
}
.small-form {
  display: grid;
  gap: 20px;
}
.small-form label > :last-child {
  flex: 1;
  min-width: 0;
}
.equipment-picker-table {
  max-height: 420px;
  overflow: auto;
}
.equipment-picker-table table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.equipment-picker-table td,
.equipment-picker-table th {
  padding: 12px;
  border-bottom: 1px solid #edf0f4;
  text-align: left;
}
.maintenance-page :deep(.el-input__inner),
.maintenance-page :deep(.el-select),
.maintenance-page :deep(.el-textarea__inner) {
  font-size: 13px;
}
.maintenance-page :deep(.el-button + .el-button) {
  margin-left: 0;
}
.maintenance-page :deep(.el-date-editor) {
  width: 100%;
}
.maintenance-page :deep(.el-collapse-item__header) {
  font-size: 13px;
  padding: 0 10px;
  background: #f9fafc;
}
.maintenance-page :deep(.el-collapse) {
  border-top: 0;
}
.maintenance-page :deep(.el-switch) {
  flex: none;
}
.maintenance-page :deep(.el-input-number) {
  width: 120px;
}
@media (max-width: 1100px) {
  .maintenance-form-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 760px) {
  .maintenance-form-grid,
  .maintenance-item-grid {
    grid-template-columns: 1fr;
  }
  .maintenance-toolbar {
    flex-wrap: wrap;
  }
  .maintenance-toolbar form {
    width: 100%;
  }
  .maintenance-queues {
    overflow: auto;
  }
  .maintenance-edit-body {
    padding: 12px;
  }
  .maintenance-list-footer {
    overflow: auto;
  }
  .section-title {
    flex-wrap: wrap;
  }
}
</style>

<style scoped>
.maintenance-actions .text-action {
  color: var(--brand);
  font-size: 13px;
}
.maintenance-actions .text-action.danger {
  color: #c9535e;
}
.maintenance-actions button + button {
  margin-left: 12px;
}
</style>

<style scoped>
.plan-project-trigger {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  height: 32px;
  padding: 0 11px;
  border: 1px solid #d8e0ec;
  border-radius: 4px;
  background: #fff;
  color: var(--brand);
  font: inherit;
  cursor: pointer;
}
.plan-project-trigger:hover {
  border-color: var(--brand);
}
.project-trigger-arrow {
  font-size: 20px;
  color: #8995a6;
}
.project-picker-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 14px;
  color: #667387;
  font-size: 13px;
}
.project-picker-layout {
  display: grid;
  grid-template-columns: 240px minmax(0, 1fr);
  height: 440px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  overflow: hidden;
  font-size: 13px;
}
.project-picker-standards {
  overflow: auto;
  background: #f8faff;
  border-right: 1px solid #e3e8f0;
  padding: 10px;
}
.project-picker-standards h4 {
  margin: 4px 8px 12px;
  color: #344156;
}
.project-picker-standards button {
  display: flex;
  width: 100%;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
  text-align: left;
  padding: 12px 10px;
  margin-bottom: 4px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #4d5a6e;
  font: inherit;
  cursor: pointer;
}
.project-picker-standards button.active {
  background: var(--brand-soft);
  color: var(--brand);
}
.project-picker-standards button small {
  flex: none;
  color: #8995a6;
}
.project-picker-projects {
  display: flex;
  flex-direction: column;
  min-width: 0;
  padding: 14px;
}
.project-picker-search {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 12px;
  color: #718097;
}
.project-picker-search .el-input {
  width: 280px;
}
.project-picker-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.project-picker-table-scroll table {
  width: 100%;
  border-collapse: collapse;
}
.project-picker-table-scroll th {
  position: sticky;
  top: 0;
  background: #f5f7fa;
  color: #667387;
  text-align: left;
  z-index: 1;
}
.project-picker-table-scroll td,
.project-picker-table-scroll th {
  padding: 10px;
  border-bottom: 1px solid #edf0f4;
}
.project-picker-table-scroll td:first-child {
  width: 36px;
}
.project-picker-table-scroll tbody tr:hover {
  background: #f8faff;
  cursor: pointer;
}
.picker-empty {
  text-align: center;
  padding: 24px 12px !important;
  color: #94a3b8;
}
.project-picker-hint {
  margin: 12px 0 0;
  color: #8995a6;
}
</style>
