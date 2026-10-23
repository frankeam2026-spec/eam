<script setup lang="ts">
import ReminderPolicyField from './common/ReminderPolicyField.vue';
import { isDepartmentCustomColumn } from '../businessConfig';
import DepartmentValuesText from './common/DepartmentValuesText.vue';

import DepartmentText from './common/DepartmentText.vue';
import { tr, enumLabel } from '../i18n';

import DialogChoiceInput from './common/DialogChoiceInput.vue';
import { useStandardSelectorPage } from '../composables/useStandardSelectorPage';
import PagedChoiceField from './common/PagedChoiceField.vue';
import AppPagination from './common/AppPagination.vue';
import { planningSelectors, resolvePlanning, mergeOptions, type SelectorRow } from '../selectors';
const selector = planningSelectors('maintenance');
function rememberPeople(rows: SelectorRow[]) {
  choices.value.users = mergeOptions(choices.value.users, rows);
}
function rememberEquipment(rows: SelectorRow[]) {
  choices.value.equipment = mergeOptions(choices.value.equipment, rows);
}

import { useColumnPreferences } from '../composables/useColumnPreferences';
import { useMaintenanceList } from '../composables/maintenance/useMaintenanceList';
import { useBusinessColumns } from '../composables/useBusinessColumns';
import { customCell, type BusinessObject } from '../businessConfig';
import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
const extensionInitializationClean = ref(true);
import { standardApplies } from '../utils/baseReferences';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckboxGroup } from 'element-plus';

function onTableSort(sort: BusinessSort) {
  sortKey.value = sort.order ? sort.prop || '' : '';
  sortAsc.value = sort.order !== 'descending';
  page.value = 1;
}
import type { BusinessSort } from './common/BusinessTable.types';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import TaskOrganizationFilter from './common/TaskOrganizationFilter.vue';
import { maintenanceMenu, inspectionMenu } from '../rolePermissions';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import { ApiError } from '../api';
import AppBackButton from './common/AppBackButton.vue';
import AppFormField from './common/AppFormField.vue';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
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

import AppFormFooter from './common/AppFormFooter.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import AppDialog from './common/AppDialog.vue';
import MaintenanceAcceptanceDialog from './common/MaintenanceAcceptanceDialog.vue';
import type { TaskResult } from '../maintenance';
import ListToolbarIcons from './ListToolbarIcons.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import TreeSelect from './TreeSelect.vue';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { matchesAdvanced, type FilterCondition, type FilterField } from '../query/advancedFilters';
import MaintenanceAttachments from './common/MaintenanceAttachments.vue';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const tabs = [
  { key: 'tasks', get label() { return tr('ui.equipment.text0011'); } },
  { key: 'plans', get label() { return tr('ui.equipment.text0010'); } },
  { key: 'standards', get label() { return tr('ui.equipment.text0009'); } },
  { key: 'types', get label() { return tr('ui.equipment.text0008'); } },
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
  size = ref(20),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  columnFilters = ref<Record<string, string>>({}),
  selected = ref<string[]>([]),
  sortKey = ref(''),
  sortAsc = ref(true);
const advancedOpen = ref(false),
  conditions = ref<FilterCondition[]>([]);
const advancedFields = computed<FilterField[]>(() =>
  baseFields.value
    .map<FilterField>(([key, label]) => ({
      key: key!,
      label: label!,
      type:
        key === 'status'
          ? 'choice'
          : ['dueDate', 'expiresDate', 'actualStartAt', 'actualEndAt', 'createdAt'].includes(key!)
            ? 'date'
            : 'text',
      dateOnly: ['dueDate', 'expiresDate'].includes(key!),
      ...(key === 'status'
        ? { options: ['待执行', '执行中', '挂起', '待验收', '已完成', '已作废', '过期作废'] }
        : {}),
    }))
    .concat(extensionFilters.value),
);

const planReviewerError = ref('');
const planCycleErrors = ref<Record<string, string>>({});
const planDurationErrors = ref<Record<string, string>>({});
const needsAcceptance = computed({
  get: () => form.value.reviewMode !== '无需验收',
  set: (required: boolean) => {
    form.value.reviewMode = required ? '整机验收' : '无需验收';
    planReviewerError.value = '';
    if (!required) {
      form.value.reviewers = [];
      form.value.details.forEach((detail) => {
        detail.reviewers = [];
      });
    }
  },
});

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
    ElMessage.warning(tr('ui.equipment.text0746'));
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
    ElMessage.warning(tr('ui.equipment.text0747'));
    return;
  }
  if (action === 'options' && values.length < 2) {
    ElMessage.warning(tr('ui.equipment.text0748'));
    return;
  }
  if (
    action === 'normalOptions' &&
    (!values.length || values.some((value) => !sharedNormalOptions.value.includes(value)))
  ) {
    ElMessage.warning(tr('ui.equipment.text0749'));
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
  ElMessage.success(tr('ui.common.dynamic45',{p0:count}));
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
    value: x.id,
  })),
);
const baseFields = computed(() =>
  kind.value === 'tasks'
    ? [
        ['code', '任务编号'],
        ['planName', '计划名称'],
        ['equipmentName', '设备名称'],
        ['equipmentCode', '设备编号'],
        ['typeName', '保养类型'],
        ['status', '状态'],
        ['department', '所属部门'],
        ['owner', '负责人'],
        ['dueDate', '计划开始时间'],
        ['expiresDate', '计划结束时间'],
        ['actualStartAt', '实际开始时间'],
        ['actualEndAt', '实际结束时间'],
        ['createdAt', '生成时间'],
      ]
    : kind.value === 'types'
      ? [
          ['code', '编码'],
          ['name', '名称'],
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
const extensionObject = computed<BusinessObject | undefined>(() =>
  kind.value === 'plans'
    ? 'maintenance_plan'
    : kind.value === 'tasks'
      ? 'maintenance_task'
      : undefined,
);
const { definitions: customDepartmentDefinitions, extensionColumns, extensionFilters, extensionExportColumns, configurationError } =
  useBusinessColumns(extensionObject);
const fields = computed<[string, string][]>(() => [
  ...(baseFields.value as [string, string][]),
  ...extensionColumns.value.map((c) => [c.key, c.label] as [string, string]),
]);

const {
  visibleColumns,
  widths,
  restore: restoreColumnPreferences,
  persistWidths: persistTableWidths,
} = useColumnPreferences('eam-maintenance', kind, () => fields.value.map(([key]) => key));

function restoreTaskColumns() {
  restoreColumnPreferences();
  if (kind.value !== 'tasks') return;
  const marker = 'eam-maintenance-task-time-columns-v1';
  if (localStorage.getItem(marker)) return;
  const added = ['planName', 'expiresDate', 'actualStartAt', 'actualEndAt'];
  visibleColumns.value = [...new Set([...visibleColumns.value, ...added])];
  localStorage.setItem(marker, '1');
}
function openSourcePlan(row: MaintenanceTask) {
  if (!row.planId) return;
  router.push({ name: 'maintenance-plan-detail', params: { id: row.planId } });
}

function clone<T>(value: T): T {
  return JSON.parse(JSON.stringify(value));
}
function names(ids: string[] = []) {
  return ids.map((id) => choices.value.users.find((x) => x.id === id)?.name || id).join('、');
}
function cell(row: MaintenanceDefinition | MaintenanceTask, key: string): string {
  if (key.startsWith('custom:')) return customCell(row, key);
  if (
    (key === 'owner' || key === 'department' || key === 'equipmentCount' || key === 'itemCount') &&
    Reflect.get(row, key) != null
  )
    return String(Reflect.get(row, key));
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
  if (key === 'actualStartAt' || key === 'actualEndAt') return formatDateTime(v).slice(0, 16);
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
      baseFields.value.some(([k]) => cell(r, k!).includes(keyword.value.trim())) &&
      matchesAdvanced(
        conditions.value.filter((c) => !c.field.startsWith('custom:')),
        (k) => cell(r, k),
      ) &&
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
const remoteTotal = ref(0);
const pageRows = computed(() =>
  kind.value === 'types'
    ? filtered.value.slice((page.value - 1) * size.value, page.value * size.value)
    : kind.value === 'tasks'
      ? tasks.value
      : definitions.value,
);
const listTotal = computed(() =>
  kind.value === 'types' ? filtered.value.length : remoteTotal.value,
);
const activeFields = computed(() =>
  fields.value.filter(([k]) => visibleColumns.value.includes(k!)),
);
const eqRows = ref<Choice[]>([]),
  equipmentPage = ref(1),
  equipmentSize = ref(20),
  equipmentTotal = ref(0),
  equipmentLoading = ref(false),
  equipmentError = ref('');
const equipmentSelectedOnly = ref(false);
const equipmentVisible = computed(() =>
  equipmentSelectedOnly.value
    ? picked.value.map(
        (id) =>
          choices.value.equipment.find((row) => row.id === id) || {
            id,
            name: '已选设备不可用',
            code: id,
          },
      )
    : eqRows.value,
);
watch(equipmentSelectedOnly, (value) => {
  if (!value && equipmentDialog.value) void loadEquipmentPage();
});
let equipmentRevision = 0;
async function loadEquipmentPage() {
  if (!equipmentDialog.value || equipmentSelectedOnly.value) return;
  const seq = ++equipmentRevision;
  equipmentLoading.value = true;
  equipmentError.value = '';
  eqRows.value = [];
  try {
    const result = await selector.equipment(
      pickSearch.value,
      equipmentPage.value,
      equipmentSize.value,
    );
    if (seq !== equipmentRevision) return;
    eqRows.value = result.data;
    equipmentTotal.value = result.page.total;
    rememberEquipment(result.data);
  } catch (e) {
    if (seq === equipmentRevision)
      equipmentError.value = e instanceof Error ? e.message : tr('ui.equipment.text0279');
  } finally {
    if (seq === equipmentRevision) equipmentLoading.value = false;
  }
}
watch([equipmentPage, equipmentSize, pickSearch], () => {
  if (equipmentDialog.value) void loadEquipmentPage();
});
watch(pickSearch, () => {
  equipmentPage.value = 1;
});
watch(equipmentDialog, (open) => {
  if (!open) equipmentRevision++;
});
const unsaved = useUnsavedChanges(
  () => form.value,
  () => editing.value,
  () => busy.value,
);
const serverList = useMaintenanceList();
let loadRevision = 0;
async function load() {
  const current = ++loadRevision;
  loading.value = true;
  error.value = '';
  try {
    if (!editing.value && kind.value !== 'types') {
      const applied = await serverList.refresh(kind.value, {
        page: page.value,
        size: size.value,
        q: keyword.value.trim(),
        conditions: JSON.stringify(conditions.value),
        filters: JSON.stringify(columnFilters.value),
        sortBy: sortKey.value || '',
        direction: sortAsc.value ? 'asc' : 'desc',
        queue: '全部',
        organization: kind.value === 'tasks' ? organizationFilter.value : '',
      });
      if (!applied || current !== loadRevision) return;
      choices.value.organizations = serverList.organizations.value;
      remoteTotal.value = serverList.total.value;
      if (kind.value === 'tasks') tasks.value = serverList.rows.value as MaintenanceTask[];
      else definitions.value = serverList.rows.value as MaintenanceDefinition[];
      return;
    }
    const loaded = editing.value
      ? route.params.id
        ? (await maintenanceApi.get(kind.value, String(route.params.id))).data
        : emptyDefinition()
      : emptyDefinition();
    const [c, t, selected] = await Promise.all([
      maintenanceApi.choices(true),
      kind.value === 'tasks' || !auth.can('maintenanceType')
        ? Promise.resolve({ data: [] as MaintenanceDefinition[] })
        : maintenanceApi.list('types'),
      resolvePlanning<MaintenanceDefinition>('maintenance', loaded),
    ]);
    if (current !== loadRevision) return;
    choices.value = c;
    types.value = (t.data.length ? t.data : c.types || []).sort(
      (a, b) =>
        a.period * ({ 日: 1, 周: 7, 月: 30, 年: 365 }[a.unit] || 1) -
        b.period * ({ 日: 1, 周: 7, 月: 30, 年: 365 }[b.unit] || 1),
    );
    choices.value.equipment = selected.equipment;
    choices.value.users = mergeOptions(
      selected.users,
      auth.user
        ? [{ id: auth.user.id, name: auth.user.name, department: auth.user.department }]
        : [],
    );
    standards.value = selected.standards;
    if (editing.value) {
      form.value = loaded;
      if (!form.value.departmentId) form.value.departmentId = String(auth.user?.orgId || '');
      selectedItems.value = [];
      selectedPlanRows.value = [];
      for (const detail of form.value.details) {
        detail.clientKey = crypto.randomUUID();
        detail.validDuration ??= detail.validDays;
        detail.validUnit ||= '日';
        if (!detail.projectRefs)
          detail.projectRefs = (
            standards.value.find((st) => st.id === detail.standardId)?.items || []
          )
            .filter((item) => item.typeId === detail.typeId)
            .map((item) => ({ standardId: detail.standardId, itemId: item.id }));
      }
      itemErrors.value = {};
      unsaved.markSaved();
    } else if (kind.value === 'types') definitions.value = types.value;
  } catch (e) {
    if (current === loadRevision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (current === loadRevision) loading.value = false;
  }
}
watch(
  () => [kind.value, route.name, route.params.id],
  () => {
    keyword.value = '';
    columnFilters.value = {};
    planReviewerError.value = '';
    planCycleErrors.value = {};
    planDurationErrors.value = {};
    restoreTaskColumns();
    conditions.value = [];
    selected.value = [];
    page.value = 1;
    load();
  },
  { immediate: true },
);
watch(
  [page, size, keyword, columnFilters, conditions, sortKey, sortAsc, organizationFilter],
  () => {
    if (!editing.value && kind.value !== 'types') void load();
  },
  { deep: true, flush: 'post' },
);

onUnmounted(() => {
  equipmentRevision++;
  loadRevision++;
  serverList.invalidate();
});
watch(
  [keyword, columnFilters, conditions, size],
  () => {
    page.value = 1;
    selected.value = [];
  },
  { deep: true },
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
  if (kind.value === 'plans' && !prepareExtension(form.value)) return;
  if (kind.value === 'standards') {
    itemErrors.value = {};
    for (const [index, item] of form.value.items.entries()) {
      const missing = !item.name.trim()
        ? tr('ui.validation.message22')
        : !item.typeId
          ? tr('ui.equipment.text0747')
          : !item.content.trim()
            ? tr('ui.validation.message23')
            : item.valueType === '选项' && item.options.length < 2
              ? tr('ui.equipment.text0748')
              : item.valueType === '选项' &&
                  (!item.normalOptions.length ||
                    item.normalOptions.some((value) => !item.options.includes(value)))
                ? tr('ui.equipment.text0750')
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
    planReviewerError.value =
      needsAcceptance.value && !form.value.reviewers.length ? tr('ui.equipment.text0751') : '';
    planCycleErrors.value = {};
    planDurationErrors.value = {};
    for (const detail of form.value.details) {
      if (
        !Number.isInteger(detail.validDuration) ||
        detail.validDuration! < 0 ||
        detail.validDuration! > 36500 ||
        !['日', '周', '月'].includes(detail.validUnit || '')
      )
        planDurationErrors.value[detail.clientKey!] = tr('ui.validation.message24');
      if (
        !Number.isInteger(detail.period) ||
        detail.period < 1 ||
        detail.period > 10000 ||
        !['日', '周', '月', '年'].includes(detail.unit)
      )
        planCycleErrors.value[detail.clientKey!] = tr('ui.validation.message25');
    }
    if (
      planReviewerError.value ||
      Object.keys(planCycleErrors.value).length ||
      Object.keys(planDurationErrors.value).length
    )
      return;
    const invalid = form.value.details.findIndex(
      (detail) => !detail.typeId || !detail.projectRefs?.length || !detail.nextDate,
    );
    if (invalid >= 0) {
      ElMessage.error(tr('ui.common.dynamic46',{p0:invalid + 1}));
      return;
    }
  }
  busy.value = true;
  try {
    await maintenanceApi.save(kind.value, form.value);
    unsaved.markSaved();
    ElMessage.success(tr('ui.equipment.text0752'));
    typeDialog.value = false;
    if (editing.value) {
      busy.value = false;
      back();
    } else await load();
  } catch (e) {
    if (extensionError(e)) return;
    if (e instanceof ApiError && e.fieldErrors.reviewers) {
      planReviewerError.value = e.fieldErrors.reviewers;
      return;
    }
    if (e instanceof ApiError) {
      let durationError = false;
      for (const [key, message] of Object.entries(e.fieldErrors)) {
        const match = key.match(/^details\.(\d+)\.validDuration$/);
        const detail = match ? form.value.details[Number(match[1])] : undefined;
        if (detail?.clientKey) {
          planDurationErrors.value[detail.clientKey] = message;
          durationError = true;
        }
      }
      if (durationError) return;
    }
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
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
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0278'));
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
    !(await confirmAction(tr('ui.common.dynamic47',{p0:selectedItems.value.length})))
  )
    return;
  for (const id of [...selectedItems.value]) removeItem(id);
}
function changeItemOptions(item: MaintenanceItem) {
  item.normalOptions = item.normalOptions.filter((value) => item.options.includes(value));
}
function chooseEquipment() {
  equipmentSelectedOnly.value = false;
  picked.value =
    kind.value === 'standards'
      ? [...form.value.equipmentIds]
      : form.value.details.map((d) => d.equipmentId);
  pickSearch.value = '';
  equipmentPage.value = 1;
  equipmentDialog.value = true;
  void loadEquipmentPage();
}
function applicableStandards(equipmentId: string) {
  const equipment = eq(equipmentId);
  return equipment
    ? standards.value.filter((standard) =>
        standardApplies(standard, equipment, choices.value.categories),
      )
    : [];
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
  detail.validDuration = detail.unit === '年' ? detail.period * 12 : detail.period;
  detail.validUnit = detail.unit === '年' ? '月' : detail.unit;
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
    validDuration: (type?.period || 1) * (type?.unit === '年' ? 12 : 1),
    validUnit: type?.unit === '年' ? '月' : type?.unit || '月',
    owners: [],
    reviewers: [],
    remark: '',
  };
}
async function loadDefaults(ids: string[]) {
  if (!auth.can('maintenanceStandard')) return;
  for (let at = 0; at < ids.length; at += 200) {
    const result = await selector.defaults<MaintenanceDefinition>(ids.slice(at, at + 200));
    standards.value = mergeOptions(standards.value, result.data);
  }
}
async function confirmEquipment() {
  try {
    if (kind.value !== 'standards')
      await loadDefaults(
        picked.value.filter((id) => !form.value.details.some((row) => row.equipmentId === id)),
      );
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0280'));
    return;
  }
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
const {
  rows: projectPickerStandards,
  search: standardSearch,
  page: standardPage,
  total: standardTotal,
  size: standardSize,
  loading: standardLoading,
  error: standardError,
  load: reloadStandards,
} = useStandardSelectorPage<MaintenanceDefinition>(
  'maintenance',
  projectPickerOpen,
  computed(() => projectPickerRow.value?.equipmentId || ''),
  projectPickerAll,
  projectPickerStandard,
  (rows) => {
    standards.value = mergeOptions(standards.value, rows);
  },
);
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
  standardSearch.value = '';
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
    ElMessage.warning(tr('ui.equipment.text0753'));
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
  planBatchDurationUnit = ref('日'),
  planBatchUsers = ref<string[]>([]);
const planBatchLabels = computed(() => ({
  type: '设置保养类型',
  date: '设置下次保养时间',
  duration: '设置有效时长',
  owners: '设置负责人',
  ...(form.value.reviewMode !== '无需验收' ? { reviewers: '设置验收人' } : {}),
}));
function openPlanBatch(action: PlanBatchAction) {
  if (action === 'reviewers' && form.value.reviewMode === '无需验收') return;
  planBatchAction.value = action;
  planBatchType.value = '';
  planBatchDate.value = '';
  planBatchDuration.value = 0;
  planBatchDurationUnit.value = '日';
  planBatchUsers.value = [];
  planBatchOpen.value = true;
}
async function applyPlanBatch() {
  if (planBatchAction.value === 'type') {
    try {
      await loadDefaults(
        form.value.details
          .filter((row) => selectedPlanRows.value.includes(row.clientKey!))
          .map((row) => row.equipmentId),
      );
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0160'));
      return;
    }
  }
  const action = planBatchAction.value;
  if (action === 'type' && !planBatchType.value) {
    ElMessage.warning(tr('ui.equipment.text0747'));
    return;
  }
  if (action === 'date' && !planBatchDate.value) {
    ElMessage.warning(tr('ui.equipment.text0754'));
    return;
  }
  for (const detail of form.value.details.filter((row) =>
    selectedPlanRows.value.includes(row.clientKey!),
  )) {
    if (action === 'type') {
      detail.typeId = planBatchType.value;
      await typeChanged(detail, true);
    } else if (action === 'date') detail.nextDate = planBatchDate.value;
    else if (action === 'duration') {
      detail.validDuration = planBatchDuration.value;
      detail.validUnit = planBatchDurationUnit.value;
    } else if (action === 'owners') detail.owners = [...planBatchUsers.value];
    else detail.reviewers = [...planBatchUsers.value];
  }
  planBatchOpen.value = false;
  ElMessage.success(tr('ui.equipment.text0755'));
}
function localDate() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
async function typeChanged(d: PlanDetail, loaded = false) {
  if (!loaded) {
    try {
      await loadDefaults([d.equipmentId]);
    } catch (e) {
      ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0280'));
      return;
    }
  }
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
  conditions.value = [];
  page.value = 1;
}
async function exportRows() {
  if (!can('导出')) return;
  const exportFields: [string, string][] = [
    ...(baseFields.value as [string, string][]),
    ...extensionExportColumns.value.map((c) => [c.key, c.label] as [string, string]),
  ];
  if (kind.value !== 'types') {
    try {
      const blob = await maintenanceApi.export(kind.value, {
        q: keyword.value.trim(),
        conditions: JSON.stringify(conditions.value),
        filters: JSON.stringify(columnFilters.value),
        sortBy: sortKey.value || '',
        direction: sortAsc.value ? 'asc' : 'desc',
        queue: '全部',
        organization: kind.value === 'tasks' ? organizationFilter.value : '',
        columns: JSON.stringify(exportFields.map(([key]) => key)),
      });
      const url = URL.createObjectURL(blob),
        link = document.createElement('a');
      link.href = url;
      link.download = title.value + '.csv';
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      ElMessage.error(error instanceof Error ? error.message : tr('ui.equipment.text0185'));
    }
    return;
  }
  const csv =
    '\uFEFF' +
    exportFields.map((x) => x[1]).join(',') +
    '\r\n' +
    filtered.value
      .map((r) =>
        exportFields
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
const taskPostponeOpen = ref(false),
  taskPostponeDate = ref(''),
  taskPostponeReason = ref('');
const taskPostponeErrors = ref<{ endDate?: string; reason?: string }>({});
function selectedTaskRows() {
  const rows = tasks.value.filter((task) => selected.value.includes(task.id));
  if (!rows.length || rows.length !== selected.value.length || rows.length > 100) {
    ElMessage.error(tr('ui.equipment.text0756'));
    return;
  }
  return rows;
}
async function openTaskBatch(action: 'delete' | 'postpone' | 'accept') {
  if (busy.value) return;
  const rows = selectedTaskRows();
  if (!rows) return;
  const invalid = rows.find((task) =>
    action === 'accept'
      ? task.status !== '待验收' || !task.canAccept
      : action === 'postpone'
        ? !task.canPostpone
        : !task.canDelete,
  );
  if (invalid) {
    ElMessage.error(
      tr('ui.common.dynamic48',{p0:invalid.code,p1:action === 'accept' ? '不在待验收状态或你不是有权限的验收人' : action === 'postpone' ? '当前状态、截止时间或权限不支持延期' : '当前状态或权限不支持删除'}),
    );
    return;
  }
  if (action === 'accept') {
    acceptance.value = true;
    return;
  }
  if (action === 'postpone') {
    taskPostponeDate.value = '';
    taskPostponeReason.value = '';
    taskPostponeErrors.value = {};
    taskPostponeOpen.value = true;
    return;
  }
  if (
    !(await confirmAction(
      tr('ui.common.dynamic49',{p0:rows.length}),
    ))
  )
    return;
  if (busy.value) return;
  busy.value = true;
  try {
    await maintenanceApi.deleteTasks(
      rows.map((task) => ({ id: task.id, rowVersion: task.rowVersion })),
    );
    selected.value = [];
    window.dispatchEvent(new Event('repair-inbox-changed'));
    await load();
    ElMessage.success(tr('ui.equipment.text0757'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0278'));
  } finally {
    busy.value = false;
  }
}
async function submitTaskPostpone() {
  if (busy.value) return;
  const rows = selectedTaskRows();
  if (!rows) return;
  taskPostponeErrors.value = {};
  const invalid = rows.find(
    (task) =>
      !task.canPostpone ||
      !task.expiresDate ||
      taskPostponeDate.value <= task.expiresDate.slice(0, 10),
  );
  if (!taskPostponeDate.value) taskPostponeErrors.value.endDate = tr('ui.validation.message26');
  else if (taskPostponeDate.value < localDate())
    taskPostponeErrors.value.endDate = '计划结束时间不能早于今天';
  else if (invalid)
    taskPostponeErrors.value.endDate = `${invalid.code}：新结束时间必须晚于原结束时间，且任务允许延期`;
  if (!taskPostponeReason.value.trim()) taskPostponeErrors.value.reason = tr('ui.validation.message27');
  if (Object.keys(taskPostponeErrors.value).length) return;
  busy.value = true;
  try {
    await maintenanceApi.postponeTasks({
      tasks: rows.map((task) => ({ id: task.id, rowVersion: task.rowVersion })),
      endDate: taskPostponeDate.value,
      reason: taskPostponeReason.value.trim(),
    });
    taskPostponeOpen.value = false;
    selected.value = [];
    window.dispatchEvent(new Event('repair-inbox-changed'));
    await load();
    ElMessage.success(tr('ui.equipment.text0758'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0759'));
  } finally {
    busy.value = false;
  }
}
async function submitAcceptance(value: NonNullable<TaskResult['acceptance']>) {
  if (busy.value) return;
  busy.value = true;
  try {
    const list = selectedTaskRows();
    if (!list) return;
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
    ElMessage.success(tr('ui.equipment.text0760'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0761'));
  } finally {
    busy.value = false;
  }
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => form.value.items;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => form.value.details;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows4 = () => projectPickerItems.value;
type TableColumnRow4 = NonNullable<ReturnType<typeof tableColumnRows4>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows5 = () => eqRows.value;
type TableColumnRow5 = NonNullable<ReturnType<typeof tableColumnRows5>>[number];
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    advancedOpen,
    () => conditions.value,
    (value) => {
      conditions.value = value;
      page.value = 1;
      selected.value = [];
    },
  );
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
        :scope-by-records="false"
      />
      <div class="task-list-content">
        <AppListToolbar class="maintenance-toolbar">
          <div>
            <ElButton v-if="kind !== 'tasks' && can('新增')" type="primary" @click="open()"
              ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
            ><ElDropdown
              v-if="kind === 'tasks' && (can('删除') || can('编辑') || can('验收'))"
              :disabled="!selected.length || busy"
              @command="openTaskBatch"
            >
              <ElButton :disabled="!selected.length || busy"
                >{{ tr('ui.equipment.text0373') }}<span v-if="selected.length">（{{ selected.length }}）</span
                ><AppIcon name="Down"
              /></ElButton>
              <template #dropdown
                ><ElDropdownMenu>
                  <ElDropdownItem v-if="can('删除')" command="delete">{{ tr('ui.equipment.text0146') }}</ElDropdownItem>
                  <ElDropdownItem v-if="can('编辑')" command="postpone">{{ tr('ui.equipment.text0762') }}</ElDropdownItem>
                  <ElDropdownItem v-if="can('验收')" command="accept">{{ tr('ui.equipment.text0763') }}</ElDropdownItem>
                </ElDropdownMenu></template
              >
            </ElDropdown>
          </div>
          <form @submit.prevent="page = 1">
            <ListToolbarIcons
              v-if="kind !== 'tasks'"
              mode="filter"
              :filter-open="filterOpen"
              :columns-open="columnsOpen"
              @toggle-filter="filterOpen = !filterOpen"
            /><ListSearchInput
              v-model="keyword"
              :placeholder="
                kind === 'tasks' ? tr('ui.equipment.text0764') : tr('ui.equipment.text0765')
              "
            /><ElButton
              v-if="kind === 'tasks'"
              :class="{ active: conditions.length }"
              @click="showFilter($event.currentTarget as HTMLElement)"
              >{{ tr('ui.equipment.text0203') }}<span v-if="conditions.length">（{{ conditions.length }}）</span></ElButton
            ><ElButton class="text-action" @click="reset" native-type="button" type="primary" link
              >{{ tr('ui.equipment.text0204') }}</ElButton
            >
          </form>
        </AppListToolbar>
        <AppliedFilterChips
          editable
          @edit="(condition, anchor) => showFilter(anchor, condition.field)"
          v-if="kind === 'tasks'"
          :conditions="conditions"
          :fields="advancedFields"
          @change="replaceFilters"
        /><FilterConditionPopover
          :anchor="filterAnchor"
          :field-key="editingFilter"
          :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
          v-if="kind === 'tasks'"
          v-model="advancedOpen"
          :conditions="conditions"
          :fields="advancedFields"
          @confirm="confirmFilter"
        />
        <p v-if="error" class="maintenance-error">{{ error }}</p>
        <div class="maintenance-list">
          <div class="maintenance-table-scroll">
            <AppBusinessTable
              v-model:selection-ids="selected"
              :selection-width="48"
              selection-fixed="left"
              selection-align="center"
              :selectable="kind === 'tasks'"
              :can-select-row="
                (row) =>
                  !(
                    !(row as MaintenanceTask).canAccept ||
                    (row as MaintenanceTask).status !== '待验收'
                  )
              "
              :sortable="true"
              @sort-change="onTableSort"
              :rows="pageRows"
              :pagination="false"
              :fill="true"
              :framed="false"
              class="business-table"
              @column-resize="
                (key, width) => {
                  widths[key] = width;
                  persistTableWidths();
                }
              "
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn
                show-overflow-tooltip
                :label="label"
                v-for="[key, label] in activeFields"
                :prop="key!"
                :min-width="
                  key === 'planName'
                    ? 200
                    : ['dueDate', 'expiresDate', 'actualStartAt', 'actualEndAt'].includes(key!)
                      ? 170
                      : 150
                "
                :width="widths[key!]"
                :key="key"
                :sortable="
                  key.startsWith('custom:') &&
                  !extensionColumns.find((c) => c.key === key)?.sortable
                    ? false
                    : 'custom'
                "
                ><template #header
                  ><span class="business-column-label" :title="label">{{ label }}</span>
                  <div
                    v-if="filterOpen && kind !== 'tasks' && !key.startsWith('custom:')"
                    class="table-column-filter"
                    @click.stop
                  >
                    <ElInput
                      v-model="columnFilters[key!]"
                      size="small"
                      clearable
                      :placeholder="tr('ui.equipment.text0301')"
                      @input="page = 1"
                    /></div></template
                ><template #default="{ row: row, $index: tableRowIndex }">
                  <ElButton
                    v-if="key === 'code'"
                    class="text-action code-link"
                    @click="
                      kind === 'tasks'
                        ? router.push({ name: 'maintenance-task', params: { id: row.id } })
                        : open(row as MaintenanceDefinition)
                    "
                    type="primary"
                    link
                    >{{ cell(row, key!) }}</ElButton
                  >
                  <ElButton
                    v-else-if="
                      key === 'planName' &&
                      (row as MaintenanceTask).planId &&
                      auth.can('maintenancePlan', '查看')
                    "
                    type="primary"
                    link
                    @click="openSourcePlan(row as MaintenanceTask)"
                    >{{ cell(row, key!) }}</ElButton
                  >
                  <MaintenanceStatus v-else-if="key === 'status'" :status="cell(row, key!)" />
                  <DepartmentText v-else-if="key === 'department'" :value="cell(row, key!)" :department-id="row.departmentId" :nodes="choices.organizations" /><DepartmentValuesText v-else-if="isDepartmentCustomColumn(customDepartmentDefinitions, key!)" :value="cell(row, key!)" :ids="row.customFields?.[key!.slice(7)]" /><span v-else :title="cell(row, key!)">{{ cell(row, key!) }}</span>
                </template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn
                class-name="maintenance-actions list-action-column"
                fixed="right"
                :width="170"
                :min-width="156"
                :resizable="false"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: row, $index: tableRowIndex }">
                  <p v-if="configurationError" class="error" role="alert">
                    {{ configurationError }}
                  </p>
                  <template v-if="kind === 'tasks'"
                    ><ElButton
                      class="text-action"
                      @click="router.push({ name: 'maintenance-task', params: { id: row.id } })"
                      type="primary"
                      link
                      >{{
                        (row as MaintenanceTask).canExecute &&
                        [tr('ui.equipment.text0303'), tr('ui.equipment.text0304'), tr('ui.equipment.text0421')].includes((row as MaintenanceTask).status)
                          ? tr('ui.equipment.text0766')
                          : (row as MaintenanceTask).canAccept &&
                              (row as MaintenanceTask).status === '待验收'
                            ? tr('ui.equipment.text0763')
                            : tr('ui.equipment.text0206')
                      }}</ElButton
                    ></template
                  ><template v-else
                    ><ElButton
                      v-if="can('编辑')"
                      class="text-action"
                      @click="open(row as MaintenanceDefinition)"
                      type="primary"
                      link
                    >
                      {{ tr('ui.equipment.text0207') }}</ElButton
                    ><ElButton
                      v-if="can('删除') && !(row as MaintenanceDefinition).system"
                      class="text-action danger"
                      @click="remove(row as MaintenanceDefinition)"
                      type="danger"
                      link
                    >
                      {{ tr('ui.equipment.text0146') }}
                    </ElButton></template
                  >
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!pageRows.length">
                  {{ loading ? tr('ui.equipment.text0396') : tr('ui.equipment.text0308') }}
                </div></template
              ></AppBusinessTable
            >
          </div>
          <footer class="maintenance-list-footer">
            <span>{{ tr('ui.equipment.text0309') }} {{ listTotal }} {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="listTotal"
            /><ListToolbarIcons
              mode="footer"
              :can-export="can('导出')"
              :filter-open="filterOpen"
              :columns-open="columnsOpen"
              @toggle-columns="columnsOpen = !columnsOpen"
              @export="exportRows"
              ><template #columns
                ><ElCheckboxGroup v-model="visibleColumns"
                  ><ElCheckbox
                    v-for="[key, label] in fields"
                    :key="key"
                    :disabled="key === 'code'"
                    :value="key"
                    >{{ label }}</ElCheckbox
                  ></ElCheckboxGroup
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
          >{{ title }} / <b>{{ form.id ? tr('ui.equipment.text0207') : tr('ui.equipment.text0149') }}</b></span
        >
      </header>
      <div class="maintenance-edit-body" :class="{ 'maintenance-plan-editor': kind === 'plans' }">
        <p v-if="error" class="maintenance-error">{{ error }}</p>
        <section class="maintenance-form-section">
          <div class="maintenance-form-grid">
            <label
              ><span>{{ tr('ui.equipment.text0137') }}</span
              ><ElInput v-model="form.code" :placeholder="tr('ui.equipment.text0767')" maxlength="64" /></label
            ><label
              ><span class="required">{{ tr('ui.equipment.text0404') }}</span
              ><ElInput
                v-model="form.name"
                :placeholder="tr('ui.equipment.text0768') + title + tr('ui.equipment.text0404')"
                maxlength="150" /></label
            ><label
              ><span class="required">{{ tr('ui.equipment.text0098') }}</span
              ><TreeSelect
                v-model="form.departmentId"
                :nodes="orgNodes"
                :placeholder="tr('ui.equipment.text0769')" /></label
            ><label v-if="kind === 'plans'"
              ><span>{{ tr('ui.equipment.text0216') }}</span><ElSwitch v-model="form.enabled" /></label
            ><label
              ><span>{{ tr('ui.equipment.text0177') }}</span
              ><ElInput v-model="form.remark" type="textarea" :rows="2" :placeholder="tr('ui.equipment.text0770')"
            /></label>
          </div>
        </section>
        <template v-if="kind === 'standards'"
          ><section class="maintenance-form-section">
            <h3>{{ tr('ui.equipment.text0771') }}</h3>
            <div class="maintenance-form-grid">
              <label
                ><span>{{ tr('ui.equipment.text0772') }}</span>
                <div class="inline-control">
                  <ElButton @click="chooseEquipment">{{ tr('ui.equipment.text0220') }}</ElButton
                  ><ElTag
                    v-for="id in form.equipmentIds"
                    :key="id"
                    closable
                    @close="form.equipmentIds = form.equipmentIds.filter((x) => x !== id)"
                    >{{ eq(id)?.name }}</ElTag
                  >
                </div></label
              ><label class="full-row"
                ><span>{{ tr('ui.equipment.text0390') }}</span>
                <div class="inline-control">
                  <TreeSelect
                    v-model="category"
                    :nodes="categoryNodes"
                    :placeholder="tr('ui.equipment.text0773')"
                  /><ElButton
                    @click="
                      category &&
                        !form.categoryIds.includes(category) &&
                        form.categoryIds.push(category);
                      category = '';
                    "
                    >{{ tr('ui.equipment.text0774') }}</ElButton
                  ><ElTag
                    v-for="c in form.categoryIds"
                    :key="c"
                    closable
                    @close="form.categoryIds = form.categoryIds.filter((x) => x !== c)"
                    >{{ choices.categories.find((x) => x.id === c)?.name || c }}</ElTag
                  >
                  <div v-if="form.legacyCategoryNames?.length" class="legacy-reference-notice">
                    {{ tr('ui.equipment.text0323') }}<ElTag
                      v-for="name in form.legacyCategoryNames"
                      :key="name"
                      type="warning"
                      closable
                      @close="
                        form.legacyCategoryNames = form.legacyCategoryNames.filter(
                          (x) => x !== name,
                        )
                      "
                      >{{ name }}</ElTag
                    >
                    {{ tr('ui.equipment.text0324') }}
                  </div>
                </div></label
              >
            </div>
          </section>
          <section class="maintenance-form-section">
            <div class="section-title">
              <h3>
                {{ tr('ui.equipment.text0775') }} <small>{{ form.items.length }} {{ tr('ui.equipment.text0394') }}</small>
              </h3>
              <div class="item-toolbar">
                <ElDropdown :disabled="!selectedItems.length" @command="openItemBatch">
                  <ElButton :disabled="!selectedItems.length"
                    >{{ tr('ui.equipment.text0373') }}<span v-if="selectedItems.length">（{{ selectedItems.length }}）</span
                    ><AppIcon name="Down"
                  /></ElButton>
                  <template #dropdown
                    ><ElDropdownMenu
                      ><ElDropdownItem command="type">{{ tr('ui.equipment.text0776') }}</ElDropdownItem
                      ><ElDropdownItem command="options">{{ tr('ui.equipment.text0777') }}</ElDropdownItem
                      ><ElDropdownItem command="normalOptions">{{ tr('ui.equipment.text0778') }}</ElDropdownItem
                      ><ElDropdownItem command="delete" divided
                        >{{ tr('ui.equipment.text0146') }}</ElDropdownItem
                      ></ElDropdownMenu
                    ></template
                  >
                </ElDropdown>
                <ElButton type="primary" @click="addItem"
                  ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0779') }}</ElButton
                >
              </div>
            </div>
            <div class="item-table-scroll" :class="{ 'is-empty': !form.items.length }">
              <AppBusinessTable
                :rows="form.items"
                :pagination="false"
                :fill="false"
                :framed="false"
                class="business-table item-edit-table"
                :row-class="(item, index) => ({ 'item-error-row': itemErrors[item.id] })"
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  fixed="left"
                  :min-width="44"
                  :width="44"
                  :resizable="false"
                  class-name="item-check"
                  ><template #header
                    ><div class="table-header-label">
                      <ElCheckbox
                        :aria-label="tr('ui.equipment.text0780')"
                        :model-value="
                          form.items.length > 0 && selectedItems.length === form.items.length
                        "
                        :indeterminate="
                          selectedItems.length > 0 && selectedItems.length < form.items.length
                        "
                        @change="selectedItems = $event ? form.items.map((item) => item.id) : []"
                      /></div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElCheckbox
                      :aria-label="tr('ui.equipment.text0781') + (index + 1) + tr('ui.equipment.text0782')"
                      :model-value="selectedItems.includes(item.id)"
                      @change="
                        selectedItems = $event
                          ? [...selectedItems, item.id]
                          : selectedItems.filter((id) => id !== item.id)
                      "
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="55" :width="44" class-name="item-number" fixed="left"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0376') }}</div></template
                  ><template #default="{ row: item, $index: index }">{{
                    index + 1
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="120" class-name="item-module" fixed="left"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0783') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElInput v-model="item.module" :aria-label="tr('ui.equipment.text0783')" :placeholder="tr('ui.equipment.text0783')" />
                    <div v-if="itemErrors[item.id]" class="item-error-message">
                      {{ itemErrors[item.id] }}
                    </div></template
                  ></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="210" :width="180" class-name="item-name" fixed="left"
                  ><template #header
                    ><div class="table-header-label">
                      <span class="required">{{ tr('ui.equipment.text0398') }}</span>
                    </div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElInput
                      v-model="item.name"
                      :aria-label="tr('ui.equipment.text0398')"
                      :placeholder="tr('ui.equipment.text0398')"
                      maxlength="150"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="110" v-if="form.items.length > 0"
                  ><template #header
                    ><div class="table-header-label">
                      <span class="required">{{ tr('ui.equipment.text0008') }}</span>
                    </div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElSelect v-model="item.typeId" :aria-label="tr('ui.equipment.text0008')" :placeholder="tr('ui.equipment.text0784')"
                      ><ElOption
                        v-for="t in types"
                        :key="t.id"
                        :label="t.name"
                        :value="t.id"
                        :disabled="!t.enabled"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  :min-width="156"
                  :width="240"
                  :resizable="false"
                  v-if="form.items.length > 0"
                  ><template #header
                    ><div class="table-header-label">
                      <span class="required">{{ tr('ui.equipment.text0785') }}</span>
                    </div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElInput
                      v-model="item.content"
                      :aria-label="tr('ui.equipment.text0785')"
                      type="textarea"
                      :autosize="{ minRows: 1, maxRows: 3 }"
                      :placeholder="tr('ui.equipment.text0786')"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="96" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0400') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElSelect v-model="item.valueType" :aria-label="tr('ui.equipment.text0400')"
                      ><ElOption
                        v-for="v in ['选项', '数值', '文本']"
                        :key="v"
                        :label="enumLabel('systemOption', v)"
                        :value="v"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="200" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0787') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElSelect
                      v-if="item.valueType === '选项'"
                      v-model="item.options"
                      :aria-label="tr('ui.equipment.text0787')"
                      :placeholder="tr('ui.equipment.text0788')"
                      multiple
                      filterable
                      allow-create
                      default-first-option
                      collapse-tags
                      collapse-tags-tooltip
                      @change="changeItemOptions(item)"
                      ><ElOption v-for="v in item.options" :key="v" :label="v" :value="v"
                    /></ElSelect>
                    <span v-else class="item-muted"></span> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="180" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0789') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElSelect
                      v-if="item.valueType === '选项'"
                      v-model="item.normalOptions"
                      :aria-label="tr('ui.equipment.text0789')"
                      :placeholder="tr('ui.equipment.text0790')"
                      multiple
                      collapse-tags
                      collapse-tags-tooltip
                      ><ElOption v-for="v in item.options" :key="v" :label="v" :value="v"
                    /></ElSelect>
                    <span v-else class="item-muted"></span> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="100" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0791') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <AppDecimalInput
                      v-if="item.valueType === '数值'"
                      v-model="item.lower"
                      :aria-label="tr('ui.equipment.text0791')"
                      :placeholder="tr('ui.equipment.text0792')"
                    /><span v-else class="item-muted"></span> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="100" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0793') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <AppDecimalInput
                      v-if="item.valueType === '数值'"
                      v-model="item.upper"
                      :aria-label="tr('ui.equipment.text0793')"
                      :placeholder="tr('ui.equipment.text0792')"
                    /><span v-else class="item-muted"></span> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  :min-width="80"
                  :width="80"
                  :resizable="false"
                  class-name="item-checkbox"
                  v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0794') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElCheckbox
                      v-model="item.required"
                      :aria-label="tr('ui.equipment.text0794')"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  :min-width="80"
                  :width="80"
                  :resizable="false"
                  class-name="item-checkbox"
                  v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0795') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElCheckbox
                      v-model="item.photoRequired"
                      :aria-label="tr('ui.equipment.text0795')"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="180" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0130') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <ElInput
                      v-model="item.remark"
                      :aria-label="tr('ui.equipment.text0796')"
                      type="textarea"
                      :autosize="{ minRows: 1, maxRows: 3 }"
                      :placeholder="tr('ui.equipment.text0797')"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn :min-width="150" :width="180" v-if="form.items.length > 0"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0798') }}</div></template
                  ><template #default="{ row: item, $index: index }"
                    ><MaintenanceAttachments v-model="item.references" /></template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow2} -->
                <ElTableColumn
                  class-name="item-actions"
                  fixed="right"
                  :width="170"
                  :min-width="156"
                  :resizable="false"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                  ><template #default="{ row: item, $index: index }">
                    <div class="item-row-actions">
                      <ElButton
                        class="text-action"
                        @click="copyItem(item, index)"
                        native-type="button"
                        type="primary"
                        link
                      >
                        {{ tr('ui.equipment.text0270') }}
                      </ElButton>
                      <ElButton
                        class="text-action danger"
                        @click="removeItem(item.id)"
                        native-type="button"
                        type="danger"
                        link
                      >
                        {{ tr('ui.equipment.text0146') }}
                      </ElButton>
                      <ElButton
                        text
                        class="item-move"
                        :disabled="index === 0"
                        :aria-label="tr('ui.equipment.text0799')"
                        :title="tr('ui.equipment.text0382')"
                        @click="moveItem(index, -1)"
                        native-type="button"
                        ><AppIcon name="ArrowUp"
                      /></ElButton>
                      <ElButton
                        text
                        class="item-move"
                        :disabled="index === form.items.length - 1"
                        :aria-label="tr('ui.equipment.text0800')"
                        :title="tr('ui.equipment.text0383')"
                        @click="moveItem(index, 1)"
                        native-type="button"
                        ><AppIcon name="ArrowDown"
                      /></ElButton>
                    </div> </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.items.length">
                    {{ tr('ui.equipment.text0801') }}
                  </div></template
                ></AppBusinessTable
              >
            </div>
          </section></template
        >
        <template v-if="kind === 'plans'"
          ><section class="maintenance-form-section">
            <h3>{{ tr('ui.equipment.text0802') }}</h3>
            <div class="maintenance-form-grid">
              <label
                ><span>{{ tr('ui.equipment.text0803') }}</span
                ><ElSelect v-model="form.calculation"
                  ><ElOption :label="tr('ui.equipment.text0804')" value="固定间隔" /><ElOption
                    :label="tr('ui.equipment.text0805')"
                    value="完成时间" /></ElSelect></label
              ><label
                ><span>{{ tr('ui.equipment.text0328') }}</span>
                <div class="inline-control">
                  <ElInputNumber
                    v-model="form.advanceDays"
                    :min="0"
                    :max="365"
                    controls-position="right"
                  /><span>{{ tr('ui.equipment.text0806') }}</span>
                </div></label
              ><label
                ><span>{{ tr('ui.equipment.text0807') }}</span>
                <div class="inline-control">
                  <ElInputNumber
                    v-model="form.remindDays"
                    :min="0"
                    :max="365"
                    controls-position="right"
                  /><span>{{ tr('ui.equipment.text0806') }}</span>
                </div></label
              ><label
                ><span>{{ tr('ui.equipment.text0808') }}</span
                ><ElSelect v-model="form.autoFill"
                  ><ElOption
                    v-for="v in ['无', '正常值', '上次结果']"
                    :key="v"
                    :label="enumLabel('systemOption', v)"
                    :value="v" /></ElSelect></label
              ><label><span>{{ tr('ui.equipment.text0809') }}</span><ElSwitch v-model="form.scanStart" /></label
              ><label><span>{{ tr('ui.equipment.text0810') }}</span><ElSwitch v-model="needsAcceptance" /></label
              ><label
                ><span class="required">{{ tr('ui.equipment.text0811') }}</span
                ><PagedChoiceField
                  v-model="form.owners"
                  presentation="input"
                  selection-unit="人"
                  multiple
                  :options="choices.users"
                  :load-rows="selector.users"
                  :title="tr('ui.equipment.text0335')"
                  :placeholder="tr('ui.equipment.text0812')"
                  @select="rememberPeople"
              /></label>
              <ReminderPolicyField v-model:mode="form.reminderMode" v-model:id="form.reminderPolicyId" :name="form.reminderPolicyName" kind="maintenance" />
              <label
                ><span class="required">{{ tr('ui.equipment.text0813') }}</span>

                <PagedChoiceField
                  v-model="form.reviewers"
                  presentation="input"
                  selection-unit="人"
                  multiple
                  :options="choices.users"
                  :load-rows="selector.users"
                  :title="tr('ui.equipment.text0335')"
                  :placeholder="tr('ui.equipment.text0814')"
                  @select="rememberPeople"
                  @update:model-value="planReviewerError = ''"
              /></label>
            </div>
          </section>
          <section class="maintenance-form-section">
            <div class="section-title">
              <h3>
                {{ tr('ui.equipment.text0368') }} <small>{{ form.details.length }} {{ tr('ui.equipment.text0394') }}</small>
              </h3>
              <div class="item-toolbar">
                <ElDropdown :disabled="!selectedPlanRows.length" @command="openPlanBatch"
                  ><ElButton :disabled="!selectedPlanRows.length"
                    >{{ tr('ui.equipment.text0373') }}<span v-if="selectedPlanRows.length"
                      >（{{ selectedPlanRows.length }}）</span
                    ><AppIcon name="Down" /></ElButton
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
                ><ElButton type="primary" @click="chooseEquipment"
                  ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0220') }}</ElButton
                >
              </div>
            </div>
            <div
              class="item-table-scroll plan-device-scroll"
              :class="{ 'is-empty': !form.details.length }"
            >
              <AppBusinessTable
                :rows="form.details"
                :pagination="false"
                :fill="true"
                :framed="false"
                class="business-table item-edit-table plan-edit-table"
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn
                  fixed="left"
                  :min-width="44"
                  :width="44"
                  :resizable="false"
                  class-name="item-check"
                  ><template #header
                    ><div class="table-header-label">
                      <ElCheckbox
                        :aria-label="tr('ui.equipment.text0815')"
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
                      /></div></template
                  ><template #default="{ row: detail, $index: index }">
                    <ElCheckbox
                      :aria-label="tr('ui.equipment.text0816') + (index + 1) + tr('ui.equipment.text0782')"
                      :model-value="selectedPlanRows.includes(detail.clientKey!)"
                      @change="
                        selectedPlanRows = $event
                          ? [...selectedPlanRows, detail.clientKey!]
                          : selectedPlanRows.filter((key) => key !== detail.clientKey)
                      "
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="55" :width="44" class-name="item-number" fixed="left"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0376') }}</div></template
                  ><template #default="{ row: detail, $index: index }">{{
                    index + 1
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" :width="120" class-name="item-module" fixed="left"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0078') }}</div></template
                  ><template #default="{ row: detail, $index: index }">{{
                    eq(detail.equipmentId)?.code
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="210" :width="180" class-name="item-name" fixed="left"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0081') }}</div></template
                  ><template #default="{ row: detail, $index: index }">{{
                    eq(detail.equipmentId)?.name
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" :width="120"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0008') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <ElSelect
                      v-model="detail.typeId"
                      :placeholder="tr('ui.equipment.text0008')"
                      @change="typeChanged(detail)"
                      ><ElOption
                        v-for="type in types"
                        :key="type.id"
                        :label="type.name"
                        :value="type.id"
                        :disabled="!type.enabled"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="210" :width="340"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0775') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <DialogChoiceInput
                      :model-value="
                        detail.projectRefs?.length ? `已选 ${detail.projectRefs.length} 项` : ''
                      "
                      :placeholder="tr('ui.equipment.text0817')"
                      :title="projectSummary(detail) || tr('ui.equipment.text0817')"
                      @open="openProjectPicker(detail)"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="210" :width="220"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0818') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <AppFormField
                      label=""
                      label-width="0"
                      :error="planCycleErrors[detail.clientKey!]"
                    >
                      <div class="plan-cycle-control">
                        <ElInputNumber
                          v-model="detail.period"
                          :min="1"
                          :max="10000"
                          :precision="0"
                          controls-position="right"
                          :aria-label="tr('ui.equipment.text0819')"
                          @change="
                            updateTaskDuration(detail);
                            delete planCycleErrors[detail.clientKey!];
                          "
                        />
                        <ElSelect
                          v-model="detail.unit"
                          :aria-label="tr('ui.equipment.text0820')"
                          @change="
                            updateTaskDuration(detail);
                            delete planCycleErrors[detail.clientKey!];
                          "
                        >
                          <ElOption
                            v-for="unit in ['日', '周', '月', '年']"
                            :key="unit"
                            :label="unit"
                            :value="unit"
                          />
                        </ElSelect>
                      </div>
                    </AppFormField> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" :width="164"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0821') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <ElDatePicker
                      v-model="detail.nextDate"
                      type="date"
                      value-format="YYYY-MM-DD"
                      :placeholder="tr('ui.equipment.text0822')"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="210" :width="220"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0823') }}</div></template
                  ><template #default="{ row: detail }">
                    <AppFormField
                      label=""
                      label-width="0"
                      :error="planDurationErrors[detail.clientKey!]"
                    >
                      <div class="plan-duration-control">
                        <ElInputNumber
                          v-model="detail.validDuration"
                          :min="0"
                          :max="36500"
                          :precision="0"
                          controls-position="right"
                          :aria-label="tr('ui.equipment.text0824')"
                          @change="delete planDurationErrors[detail.clientKey!]"
                        />
                        <ElSelect
                          v-model="detail.validUnit"
                          :aria-label="tr('ui.equipment.text0825')"
                          @change="delete planDurationErrors[detail.clientKey!]"
                        >
                          <ElOption
                            v-for="unit in [
                              { value: '日', label: '天' },
                              { value: '周', label: '周' },
                              { value: '月', label: '月' },
                            ]"
                            :key="unit.value"
                            :value="unit.value"
                            :label="unit.label"
                          />
                        </ElSelect>
                      </div>
                    </AppFormField> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" :width="200"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0102') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <PagedChoiceField
                      v-model="detail.owners"
                      presentation="input"
                      selection-unit="人"
                      multiple
                      :options="choices.users"
                      :load-rows="selector.users"
                      :title="tr('ui.equipment.text0335')"
                      :placeholder="tr('ui.equipment.text0826')"
                      @select="rememberPeople"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn v-if="needsAcceptance" :min-width="150" :width="200"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0813') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <PagedChoiceField
                      v-model="detail.reviewers"
                      presentation="input"
                      selection-unit="人"
                      multiple
                      :options="choices.users"
                      :load-rows="selector.users"
                      :title="tr('ui.equipment.text0335')"
                      :placeholder="tr('ui.equipment.text0827')"
                      @select="rememberPeople"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150" :width="180"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0177') }}</div></template
                  ><template #default="{ row: detail, $index: index }"
                    ><ElInput v-model="detail.remark" placeholder="" /></template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn
                  class-name="item-actions"
                  fixed="right"
                  :width="170"
                  :min-width="156"
                  :resizable="false"
                  ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                  ><template #default="{ row: detail, $index: index }">
                    <div class="item-row-actions">
                      <ElButton class="text-action" @click="copyPlanRow(detail)" type="primary" link
                        >{{ tr('ui.equipment.text0270') }}</ElButton
                      ><ElButton
                        class="text-action danger"
                        @click="removePlanRow(detail.clientKey!)"
                        type="danger"
                        link
                      >
                        {{ tr('ui.equipment.text0146') }}
                      </ElButton>
                    </div>
                  </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.details.length">
                    {{ tr('ui.equipment.text0828') }}
                  </div></template
                ></AppBusinessTable
              >
            </div>
          </section></template
        >
        <BusinessFields
          v-if="kind === 'plans'"
          ref="extension"
          v-model="form"
          @initialize="extensionInitializationClean = !unsaved.dirty.value"
          @ready="extensionInitializationClean && unsaved.markSaved()"
          :object="'maintenance_plan'"
          :is-new="!form.id"
          :errors="extensionErrors"
        />
      </div>
      <AppFormFooter
        ><ElButton @click="back">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">{{ tr('ui.equipment.text0253') }}</ElButton></AppFormFooter
      >
    </template>
    <AppDialog
      v-model="planBatchOpen"
      :title="planBatchLabels[planBatchAction] || tr('ui.equipment.text0829')"
      width="540px"
    >
      <p class="item-batch-note">{{ tr('ui.equipment.text0830') }} {{ selectedPlanRows.length }} {{ tr('ui.equipment.text0831') }}</p>
      <div class="small-form">
        <label v-if="planBatchAction === 'type'"
          ><span>{{ tr('ui.equipment.text0008') }}</span
          ><ElSelect v-model="planBatchType" :placeholder="tr('ui.equipment.text0784')"
            ><ElOption
              v-for="type in types"
              :key="type.id"
              :label="type.name"
              :value="type.id"
              :disabled="!type.enabled" /></ElSelect></label
        ><label v-else-if="planBatchAction === 'date'"
          ><span>{{ tr('ui.equipment.text0832') }}</span
          ><ElDatePicker
            v-model="planBatchDate"
            type="date"
            value-format="YYYY-MM-DD"
            :placeholder="tr('ui.equipment.text0822')" /></label
        ><label v-else-if="planBatchAction === 'duration'"
          ><span>{{ tr('ui.equipment.text0823') }}</span
          ><ElInputNumber
            v-model="planBatchDuration"
            :min="0"
            :max="36500"
            controls-position="right" /><ElSelect
            v-model="planBatchDurationUnit"
            :aria-label="tr('ui.equipment.text0825')"
            class="duration-batch-unit"
            ><ElOption :label="tr('ui.equipment.text0806')" value="日" /><ElOption :label="tr('ui.equipment.text0833')" value="周" /><ElOption
              :label="tr('ui.equipment.text0834')"
              value="月" /></ElSelect></label
        ><label v-else
          ><span>{{ planBatchAction === 'owners' ? tr('ui.equipment.text0102') : tr('ui.equipment.text0813') }}</span
          ><PagedChoiceField
            v-model="planBatchUsers"
            presentation="input"
            selection-unit="人"
            multiple
            :options="choices.users"
            :load-rows="selector.users"
            :title="tr('ui.equipment.text0335')"
            :placeholder="tr('ui.equipment.text0835')"
            @select="rememberPeople"
        /></label>
      </div>
      <p v-if="planBatchAction === 'type'" class="item-batch-note">
        {{ tr('ui.equipment.text0836') }}
      </p>
      <template #footer
        ><ElButton @click="planBatchOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" @click="applyPlanBatch">{{ tr('ui.equipment.text0402') }}</ElButton></template
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
          ><span>{{ tr('ui.equipment.text0008') }}</span
          ><ElSelect v-model="itemBatchType" :placeholder="tr('ui.equipment.text0837')"
            ><ElOption
              v-for="t in types"
              :key="t.id"
              :label="t.name"
              :value="t.id"
              :disabled="!t.enabled" /></ElSelect
        ></label>
        <label v-else-if="itemBatchAction === 'options'"
          ><span>{{ tr('ui.equipment.text0787') }}</span
          ><ElSelect
            v-model="itemBatchValues"
            multiple
            filterable
            allow-create
            default-first-option
            :placeholder="tr('ui.equipment.text0788')"
            ><ElOption
              v-for="value in itemBatchValues"
              :key="value"
              :label="value"
              :value="value" /></ElSelect
        ></label>
        <label v-else
          ><span>{{ tr('ui.equipment.text0789') }}</span
          ><ElSelect v-model="itemBatchValues" multiple :placeholder="tr('ui.equipment.text0838')"
            ><ElOption
              v-for="value in sharedNormalOptions"
              :key="value"
              :label="value"
              :value="value" /></ElSelect
        ></label>
      </div>
      <p v-if="itemBatchAction === 'options'" class="item-batch-note">
        {{ tr('ui.equipment.text0839') }}
      </p>
      <p
        v-if="itemBatchAction === 'normalOptions' && !sharedNormalOptions.length"
        class="maintenance-error"
      >
        {{ tr('ui.equipment.text0840') }}
      </p>
      <template #footer
        ><ElButton @click="itemBatchOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" @click="applyItemBatch">{{ tr('ui.equipment.text0402') }}</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="typeDialog" :title="form.id ? tr('ui.equipment.text0841') : tr('ui.equipment.text0842')" :busy="busy"
      ><div class="small-form">
        <label><span>{{ tr('ui.equipment.text0474') }}</span><ElInput v-model="form.code" :placeholder="tr('ui.equipment.text0312')" /></label
        ><label
          ><span class="required">{{ tr('ui.equipment.text0404') }}</span
          ><ElInput v-model="form.name" :placeholder="tr('ui.equipment.text0843')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text0405') }}</span>
          <div class="inline-control">
            <ElInputNumber v-model="form.period" :min="1" :max="10000" /><ElSelect
              v-model="form.unit"
              ><ElOption v-for="u in ['日', '周', '月', '年']" :key="u" :value="u" :label="u"
            /></ElSelect></div></label
        ><label><span>{{ tr('ui.equipment.text0216') }}</span><ElSwitch v-model="form.enabled" /></label>
      </div>
      <template #footer
        ><ElButton @click="typeDialog = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">{{ tr('ui.equipment.text0253') }}</ElButton></template
      ></AppDialog
    >
    <AppDialog
      v-model="projectPickerOpen"
      :title="tr('ui.equipment.text0817')"
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
          <h4>{{ tr('ui.equipment.text0009') }}</h4>
          <ListSearchInput v-model="standardSearch" :placeholder="tr('ui.equipment.text0395')" />
          <AppBusinessTable
            class="standard-picker-table"
            :rows="projectPickerStandards"
            row-key="id"
            highlight-current-row
            :current-row-key="projectPickerStandard"
            :loading="standardLoading"
            :error="standardError"
            :pagination="false"
            :framed="false"
            border-mode="header"
            @row-click="(standard: MaintenanceDefinition) => (projectPickerStandard = standard.id)"
            @retry="reloadStandards"
          >
            <ElTableColumn prop="name" :label="tr('ui.equipment.text0313')" min-width="130" show-overflow-tooltip />
            <ElTableColumn prop="code" :label="tr('ui.equipment.text0137')" min-width="85" show-overflow-tooltip />
            <ElTableColumn :label="tr('ui.equipment.text0844')" width="86" align="center" :resizable="false">
              <template #default="{ row: standard }">
                <span :title="tr('ui.equipment.text0845')"
                  >{{
                    projectPickerValues.filter((value) => value.startsWith(standard.id + ':'))
                      .length
                  }}
                  /
                  {{
                    standard.items.filter(
                      (item: MaintenanceItem) => item.typeId === projectPickerRow?.typeId,
                    ).length
                  }}</span
                >
              </template>
            </ElTableColumn>
          </AppBusinessTable>
          <div class="standard-picker-page">
            <span>{{ tr('ui.equipment.text0309') }} {{ standardTotal }} {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="standardPage"
              v-model:page-size="standardSize"
              :total="standardTotal"
            />
          </div>
        </aside>
        <section class="project-picker-projects">
          <div class="project-picker-search">
            <span>{{ tr('ui.equipment.text0295') }} {{ projectPickerValues.length }} {{ tr('ui.equipment.text0394') }}</span
            ><ElInput
              v-model="projectPickerSearch"
              clearable
              :placeholder="tr('ui.equipment.text0846')"
            />
          </div>
          <div class="project-picker-table-scroll">
            <AppBusinessTable
              :rows="projectPickerItems"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              @row-click="
                (item) => {
                  togglePickerItem(item, !projectPickerValues.includes(pickerItemKey(item)));
                }
              "
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="44" fixed="left" :width="44"
                ><template #header
                  ><div class="table-header-label">
                    <ElCheckbox
                      :model-value="projectPickerAllChecked"
                      :indeterminate="projectPickerSomeChecked && !projectPickerAllChecked"
                      :aria-label="tr('ui.equipment.text0847')"
                      @change="togglePickerAll(Boolean($event))"
                    /></div></template
                ><template #default="{ row: item, $index: tableRowIndex }">
                  <ElCheckbox
                    :model-value="projectPickerValues.includes(pickerItemKey(item))"
                    :aria-label="tr('ui.equipment.text0848') + item.name"
                    @change="togglePickerItem(item, Boolean($event))"
                  /> </template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0783') }}</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.module || ''
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="210"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0398') }}</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.name
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="210"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0849') }}</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.content
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow4} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0400') }}</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.valueType
                }}</template></ElTableColumn
              ><template #empty
                ><div v-if="!projectPickerItems.length">{{ tr('ui.equipment.text0850') }}</div></template
              ></AppBusinessTable
            >
          </div>
          <p class="project-picker-hint">
            {{ tr('ui.equipment.text0851') }}
          </p>
        </section>
      </div>
      <template #footer
        ><ElButton @click="projectPickerOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" @click="confirmProjectPicker">{{ tr('ui.equipment.text0402') }}</ElButton></template
      >
    </AppDialog>
    <AppDialog v-model="equipmentDialog" :title="tr('ui.equipment.text0220')" width="820px"
      ><div class="section-title">
        <span>{{ tr('ui.equipment.text0295') }} {{ picked.length }} {{ tr('ui.equipment.text0370') }}</span
        ><ListSearchInput v-model="pickSearch" :placeholder="tr('ui.equipment.text0852')" />
      </div>
      <div class="equipment-picker-table">
        <AppBusinessTable
          v-model:selection-ids="picked"
          selectable
          row-key="id"
          :rows="equipmentVisible"
          :loading="equipmentLoading"
          :error="equipmentError"
          @retry="loadEquipmentPage"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0078') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.code
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="210"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0081') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0083') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.category
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.model
            }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </div>
      <div v-if="!equipmentSelectedOnly" class="equipment-selector-pagination">
        <span>{{ tr('ui.equipment.text0309') }} {{ equipmentTotal }} {{ tr('ui.equipment.text0296') }}</span
        ><AppPagination
          v-model:page="equipmentPage"
          v-model:page-size="equipmentSize"
          :total="equipmentTotal"
        />
      </div>
      <template #footer
        ><ElButton @click="equipmentDialog = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" @click="confirmEquipment">{{ tr('ui.equipment.text0402') }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="taskPostponeOpen" :title="tr('ui.equipment.text0853')" width="520px" :busy="busy">
      <p>{{ tr('ui.equipment.text0854') }} {{ selected.length }} {{ tr('ui.equipment.text0855') }}</p>
      <AppFormField
        :label="tr('ui.equipment.text0856')"
        label-width="110px"
        required
        :error="taskPostponeErrors.endDate"
      >
        <ElDatePicker
          v-model="taskPostponeDate"
          type="date"
          value-format="YYYY-MM-DD"
          :placeholder="tr('ui.equipment.text0857')"
          @change="delete taskPostponeErrors.endDate"
        />
      </AppFormField>
      <AppFormField
        :label="tr('ui.equipment.text0858')"
        label-width="110px"
        required
        :error="taskPostponeErrors.reason"
      >
        <ElInput
          v-model="taskPostponeReason"
          type="textarea"
          :rows="3"
          :maxlength="1000"
          show-word-limit
          :placeholder="tr('ui.equipment.text0859')"
          @input="delete taskPostponeErrors.reason"
        />
      </AppFormField>
      <template #footer
        ><ElButton :disabled="busy" @click="taskPostponeOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="submitTaskPostpone"
          >{{ tr('ui.equipment.text0860') }}</ElButton
        ></template
      >
    </AppDialog>
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

.plan-cycle-control,
.plan-duration-control {
  display: flex;
  gap: 8px;
  width: 100%;
}
.plan-cycle-control :deep(.el-select),
.plan-duration-control :deep(.el-select) {
  flex: 0 0 64px;
  width: 64px;
  min-width: 0;
}
.plan-cycle-control :deep(.el-input-number),
.plan-duration-control :deep(.el-input-number) {
  flex: 1;
  min-width: 0;
  width: 110px;
}
.duration-batch-unit {
  width: 80px;
  flex: none;
}
.maintenance-page {
  height: calc(100vh - 54px);
  padding: 0 8px;
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
  font-size: 13px;
  color: #334155;
  overflow: hidden;
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
.maintenance-page :deep(th) {
  position: sticky;
  top: 0;
  z-index: 1;
  cursor: pointer;
}
.maintenance-page :deep(th),
.maintenance-page :deep(td) {
  max-width: 320px;
}
.maintenance-page :deep(td > span:not(.el-tag):not(.maintenance-task-status)) {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 300px;
}

.maintenance-page :deep(thead tr:nth-child(2) th) {
  top: 44px;
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
.code-link {
  color: var(--brand);
  font-weight: 500;
}
.danger {
  color: #d94b60;
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
.maintenance-plan-editor .maintenance-form-grid {
  gap: 12px 24px;
}
.maintenance-plan-editor .maintenance-form-section {
  padding: 0;
  margin-bottom: 16px;
}
.maintenance-plan-editor .maintenance-form-section h3 {
  margin-bottom: 12px;
}
.maintenance-plan-editor :deep(.app-form-field) {
  margin-bottom: 0;
}
.maintenance-plan-editor :deep(.app-form-field.is-error) {
  padding-bottom: 18px;
}
.maintenance-form-grid {
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
.maintenance-page .item-edit-table {
  width: 2062px;
  min-width: 2062px;
  table-layout: fixed;
  border-collapse: separate;
  border-spacing: 0;
  font-size: 13px;
}
.maintenance-page .item-edit-table :deep(th) {
  position: sticky;
  top: 0;
  z-index: 3;
}
.item-edit-table :deep(tr:last-child td) {
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
.maintenance-page .item-edit-table :deep(th.item-check),
.maintenance-page .item-edit-table :deep(th.item-number),
.maintenance-page .item-edit-table :deep(th.item-module),
.maintenance-page .item-edit-table :deep(th.item-name),
.maintenance-page .item-edit-table :deep(th.item-actions) {
  z-index: 4;
  background: #f4f7fb;
}
.maintenance-page .item-edit-table :deep(th) {
  cursor: default;
}
.maintenance-page .item-edit-table :deep(td) {
  vertical-align: middle;
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
  width: 100%;
  min-width: 0;
  height: 100%;
}
.maintenance-page .plan-device-scroll {
  overflow: hidden;
  scrollbar-gutter: auto;
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
.maintenance-form-grid label,
.small-form label {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.maintenance-form-grid label > span,
.small-form label > span {
  flex: none;
  width: 78px;
  text-align: right;
  color: #68788e;
}
.maintenance-form-grid label > :last-child {
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
  .maintenance-form-grid {
    grid-template-columns: 1fr;
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
  grid-template-columns: 340px minmax(0, 1fr);
  height: 440px;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  overflow: hidden;
  font-size: 13px;
}
.project-picker-standards {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  min-height: 0;
  background: #fff;
  border-right: 1px solid #e3e8f0;
  padding: 12px;
}
.project-picker-standards h4 {
  margin: 0;
  color: var(--text);
  font-size: 14px;
}
.project-picker-standards .standard-picker-table {
  flex: 1;
  height: auto;
}
.standard-picker-table :deep(.el-table__row) {
  cursor: pointer;
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
.project-picker-table-scroll :deep(th) {
  position: sticky;
  top: 0;
  z-index: 1;
}
.project-picker-table-scroll :deep(td:first-child) {
  width: 36px;
}
.project-picker-table-scroll :deep(tbody tr:hover) {
  cursor: pointer;
}
.project-picker-hint {
  margin: 12px 0 0;
  color: #8995a6;
}
</style>

<style scoped>
.standard-picker-page {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  flex: none;
}
</style>

<style scoped>
.project-picker-standards :deep(.list-search-input),
.inspection-project-dialog aside :deep(.list-search-input) {
  width: 100%;
  min-width: 0;
}
.standard-picker-page :deep(.el-pagination) {
  flex-wrap: wrap;
}
</style>

<style scoped>
.equipment-selector-pagination {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-top: 12px;
}
</style>
