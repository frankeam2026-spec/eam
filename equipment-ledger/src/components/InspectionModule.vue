<script setup lang="ts">
import ReminderPolicyField from './common/ReminderPolicyField.vue';
import AppHelpTip from './common/AppHelpTip.vue';
import { isDepartmentCustomColumn } from '../businessConfig';
import DepartmentValuesText from './common/DepartmentValuesText.vue';

import DepartmentText from './common/DepartmentText.vue';

import { tr } from '../i18n';

import InspectionPlanPreviewDialog from './inspection/InspectionPlanPreviewDialog.vue';
const planPreviewOpen = ref(false),
  previewPlanId = ref('');
function previewTaskPlan(row: InspectionTask) {
  previewPlanId.value = row.planId;
  planPreviewOpen.value = true;
}
import InspectionTaskDeleteDialog from './inspection/InspectionTaskDeleteDialog.vue';
const taskDeleteOpen = ref(false),
  taskToDelete = ref<InspectionTask>();
function confirmTaskDelete(row: InspectionTask) {
  taskToDelete.value = row;
  taskDeleteOpen.value = true;
}
async function taskDeleted(id: string) {
  selected.value = selected.value.filter((value) => value !== id);
  await load();
}
import DailyArrangementEditor from './inspection/DailyArrangementEditor.vue';
import { ApiError } from '../api';
const dailyErrors = ref<Record<string, string>>({});
const dailyEditorTab = ref('basic');
const dailyDeviceOverrides = ref(false);
const reminderSettings = [
  { key: 'advanceHours', unit: 'advanceUnit', label: 'ui.equipment.text0328' },
  { key: 'remindHours', unit: 'remindUnit', label: 'ui.equipment.text0330' },
  { key: 'expireRemindHours', unit: 'expireRemindUnit', label: 'ui.equipment.text0331' },
] as const;
import DialogChoiceInput from './common/DialogChoiceInput.vue';
import { useStandardSelectorPage } from '../composables/useStandardSelectorPage';
import PagedChoiceField from './common/PagedChoiceField.vue';
import AppPagination from './common/AppPagination.vue';
import { planningSelectors, resolvePlanning, mergeOptions, type SelectorRow } from '../selectors';
const selector = planningSelectors('inspection');
function rememberPeople(rows: SelectorRow[]) {
  choices.value.users = mergeOptions(choices.value.users, rows);
}
function rememberEquipment(rows: SelectorRow[]) {
  choices.value.equipment = mergeOptions(choices.value.equipment, rows);
}

import { useColumnPreferences } from '../composables/useColumnPreferences';
import { useInspectionList } from '../composables/inspection/useInspectionList';
import { useBusinessColumns } from '../composables/useBusinessColumns';
import { customCell, type BusinessObject } from '../businessConfig';
import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
const extensionInitializationClean = ref(true);
import { standardApplies } from '../utils/baseReferences';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import AppFilePicker from './common/AppFilePicker.vue';
import { ElCheckboxGroup } from 'element-plus';

function onTableSort(sort: BusinessSort) {
  sortKey.value = sort.order ? sort.prop || '' : '';
  sortAsc.value = sort.order !== 'descending';
  page.value = 1;
}
import type { BusinessSort } from './common/BusinessTable.types';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import RowMoreActions from './common/RowMoreActions.vue';
import TaskOrganizationFilter from './common/TaskOrganizationFilter.vue';
import { maintenanceMenu, inspectionMenu } from '../rolePermissions';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import AppBackButton from './common/AppBackButton.vue';
import DurationInput from './common/DurationInput.vue';
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
  ElTag,
  ElCollapse,
  ElCollapseItem,
  ElForm,
  ElFormItem,
  ElTabs,
  ElTabPane,
  ElRadioGroup,
  ElRadioButton,
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElMessage,
  ElTreeSelect,
} from 'element-plus';
import {
  inspectionApi,
  inspectionLabels,
  inspectionStates,
  emptyInspection,
  emptyArrangement,
  csvRows,
  downloadCsv,
  type InspectionKind,
  type InspectionDefinition,
  type InspectionTask,
  type InspectionDetail,
  type InspectionChoices,
} from '../inspection';
import type { MaintenanceItem } from '../maintenance';
import { useAuthStore } from '../stores/auth';
import { confirmAction } from '../services/dialog';

import AppFormFooter from './common/AppFormFooter.vue';
import AppDialog from './common/AppDialog.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import InspectionStandardEditor from './inspection/InspectionStandardEditor.vue';
const standardEditor = ref<InstanceType<typeof InspectionStandardEditor>>();
import InspectionReviewDialog from './common/InspectionReviewDialog.vue';
import TreeSelect from './TreeSelect.vue';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { matchesAdvanced, type FilterCondition, type FilterField } from '../query/advancedFilters';
const uuid = () => crypto.randomUUID();
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const can = (action: string) => auth.can(inspectionMenu(kind.value), action);
const kind = computed(() => String(route.params.kind || 'daily-tasks') as InspectionKind);
const isTasks = computed(() => kind.value.endsWith('tasks')),
  daily = computed(() => kind.value.startsWith('daily')),
  standard = computed(() => kind.value === 'standards');
const editing = computed(() => route.name === 'inspection-new' || route.name === 'inspection-edit');
const choices = ref<InspectionChoices>({
  equipment: [],
  organizations: [],
  users: [],
  categories: [],
});
const records = ref<(InspectionDefinition | InspectionTask)[]>([]),
  standards = ref<InspectionDefinition[]>([]),
  types = ref<InspectionDefinition[]>([]),
  form = ref(emptyInspection());
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  keyword = ref(''),
  search = ref(''),
  queue = ref('全部'),
  page = ref(1),
  size = ref(20),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  advancedOpen = ref(false);
const columnFilters = ref<Record<string, string>>({}),
  conditions = ref<FilterCondition[]>([]),
  selected = ref<string[]>([]),
  sortKey = ref(kind.value === 'daily-tasks' ? 'dueAt' : ''),
  sortAsc = ref(kind.value !== 'daily-tasks');
const baseFields = computed<[string, string][]>(() =>
  isTasks.value
    ? [
        ['code', '任务编号'],
        ['planName', '计划名称'],
        ['equipmentName', '设备名称'],
        ...(!daily.value ? [['typeName', '点巡检类型'] as [string, string]] : []),
        ...(daily.value
          ? ([
              ['arrangementName', '检查安排'],
              ['shiftName', tr('daily.slots')],
              ['businessDate', '应检日期'],
            ] as [string, string][])
          : []),
        ['status', '状态'],
        ['dueAt', '计划开始时间'],
        ['expiresAt', '计划结束时间'],
        ['ownerNames', '负责人'],
        ['ownerName', daily.value ? tr('inspection.ui.inspector') : '实际点检人'],
        ['equipmentCount', '应检设备'],
        ['expected', '应检项目'],
        ['checked', '已检项目'],
        ['missing', '未检项目'],
        ['skipped', '跳过设备'],
        ['abnormal', '异常项目'],
        ['workSeconds', '工时（秒）'],
        ['startedAt', '实际开始时间'],
        ['finishedAt', '实际完成时间'],
        ['delayDays', '延误天数'],
        ['reviewMode', '审核方式'],
        ['reviewerNames', '审核人'],
        ['reviewer', '实际审核人'],
        ['reviewedAt', '审核时间'],
        ['createdAt', '生成时间'],
      ]
    : [
        ['code', standard.value ? '标准编号' : '计划编号'],
        ['name', standard.value ? '标准名称' : '计划名称'],
        ['departmentName', '所属部门'],
        ['enabled', '状态'],
        [standard.value ? 'itemCount' : 'equipmentCount', standard.value ? '项目数' : '设备数'],
        ...(standard.value
          ? []
          : ([
              ...(!daily.value ? [['typeName', '点巡检类型']] : []),
              ['startDate', tr(daily.value ? 'component.el.datepicker.startDate' : 'component.el.datepicker.startTime')],
              ['endDate', tr(daily.value ? 'component.el.datepicker.endDate' : 'component.el.datepicker.endTime')],
              ...(daily.value
                ? [['arrangementCount', tr('daily.arrangementCount')]]
                : [
                    ['periodLabel', '巡检周期'],
                    ['nextAt', '下次巡检时间'],
                  ]),
            ] as [string, string][])),
        ['remark', '说明'],
        ['createdAt', '创建时间'],
      ],
);
const extensionObject = computed<BusinessObject | undefined>(() =>
  standard.value
    ? undefined
    : daily.value
      ? isTasks.value
        ? 'inspection_daily_task'
        : 'inspection_daily_plan'
      : isTasks.value
        ? 'inspection_periodic_task'
        : 'inspection_periodic_plan',
);
const {
  definitions: customDepartmentDefinitions,
  extensionColumns,
  extensionFilters,
  extensionExportColumns,
  configurationError,
} = useBusinessColumns(extensionObject);
const fields = computed<[string, string][]>(() => [
  ...(baseFields.value as [string, string][]),
  ...extensionColumns.value.map((c) => [c.key, c.label] as [string, string]),
]);
const {
  visibleColumns,
  widths,
  restore: restoreColumnPreferences,
  persistWidths: persistTableWidths,
} = useColumnPreferences('eam-inspection', kind, () =>
  fields.value
    .slice(0, isTasks.value ? (daily.value ? 11 : 10) : fields.value.length)
    .map(([key]) => key),
);
// Add the new default column once without replacing a user's other column preferences.
watch(
  kind,
  () => {
    if (kind.value !== 'daily-tasks') return;
    const key = 'eam-inspection-daily-tasks-shift-column-v1';
    if (localStorage.getItem(key)) return;
    if (!visibleColumns.value.includes('shiftName')) {
      const index = visibleColumns.value.indexOf('arrangementName');
      visibleColumns.value.splice(index < 0 ? 2 : index + 1, 0, 'shiftName');
    }
    localStorage.setItem(key, '1');
  },
  { immediate: true, flush: 'post' },
);
const shownFields = computed(() =>
  fields.value.filter(([key]) => visibleColumns.value.includes(key)),
);
const filterFields = computed<FilterField[]>(() =>
  baseFields.value
    .map<FilterField>(([key, label]) => ({
      key,
      label,
      type:
        key === 'status' || key === 'enabled'
          ? 'choice'
          : key.endsWith('At') || key.endsWith('Date')
            ? 'date'
            : [
                  'expected',
                  'checked',
                  'missing',
                  'skipped',
                  'abnormal',
                  'workSeconds',
                  'delayDays',
                  'equipmentCount',
                  'itemCount',
                ].includes(key)
              ? 'number'
              : 'text',
      ...(key === 'status'
        ? { options: inspectionStates }
        : key === 'enabled'
          ? { options: ['启用', '停用'] }
          : {}),
      dateOnly: key.endsWith('Date') && kind.value !== 'periodic-plans',
    }))
    .concat(extensionFilters.value),
);
const people = (ids: string[]) =>
  ids.map((id) => choices.value.users.find((u) => u.id === id)?.name || id).join('、');
function value(row: InspectionDefinition | InspectionTask, key: string): string | number {
  if (key.startsWith('custom:')) return customCell(row, key);
  const r = row as any;
  if (
    key !== 'enabled' &&
    Object.hasOwn(r, key) &&
    [
      'departmentName',
      'ownerNames',
      'reviewerNames',
      'reviewer',
      'startedAt',
      'finishedAt',
      'reviewedAt',
      'equipmentCount',
      'itemCount',
      'shiftCount',
      'periodLabel',
    ].includes(key)
  )
    return key.endsWith('At') ? formatDateTime(r[key], '') : (r[key] ?? '');
  if (key === 'enabled') return r.enabled ? '启用' : '停用';
  if (key === 'departmentName')
    return choices.value.organizations.find((o) => o.id === r.departmentId)?.name || '';
  if (key === 'ownerNames') return people(r.owners || []);
  if (key === 'reviewerNames') return people(r.reviewers || []);
  if (key === 'reviewer') return r.result?.review?.reviewer || '';
  if (key === 'startedAt' || key === 'finishedAt') return formatDateTime(r.result?.[key], '');
  if (key === 'reviewedAt') return formatDateTime(r.result?.review?.reviewedAt, '');
  if (key === 'equipmentCount' && !isTasks.value) return r.details?.length || 0;
  if (key === 'itemCount') return r.items?.length || 0;
  if (key === 'shiftCount') return r.shifts?.length || 0;
  if (key === 'periodLabel') return `${r.period} ${r.unit}`;
  if (key.endsWith('At')) return formatDateTime(r[key], '');
  if (kind.value === 'periodic-plans' && (key === 'startDate' || key === 'endDate'))
    return formatDateTime(r[key], '').slice(0, 16);
  return r[key] ?? '';
}
const organizationFilter = ref('');
const taskOrganizations = ref<InspectionChoices['organizations']>([]);
watch(organizationFilter, () => {
  page.value = 1;
});
watch(kind, () => {
  organizationFilter.value = '';
});
const listTotal = ref(0);
const serverList = useInspectionList();
const pageRows = computed(() => records.value);
function listParameters() {
  return {
    page: page.value,
    size: size.value,
    q: search.value,
    conditions: JSON.stringify(conditions.value),
    filters: JSON.stringify(columnFilters.value),
    sortBy: sortKey.value || '',
    direction: sortAsc.value ? 'asc' : 'desc',
    queue: queue.value,
    organization: isTasks.value ? organizationFilter.value : '',
  };
}
watch(
  [search, queue, columnFilters, conditions, size],
  () => {
    page.value = 1;
    selected.value = [];
  },
  { deep: true },
);

let loadVersion = 0;
const unsaved = useUnsavedChanges(
  () => form.value,
  () => editing.value,
  () => busy.value,
);
async function load() {
  const version = ++loadVersion;
  loading.value = true;
  error.value = '';
  try {
    if (!editing.value) {
      const applied = await serverList.refresh(kind.value, listParameters());
      if (!applied || version !== loadVersion) return;
      records.value = serverList.rows.value;
      listTotal.value = serverList.total.value;
      choices.value.organizations = serverList.organizations.value;
      taskOrganizations.value = serverList.organizations.value;
      return;
    }
    const loadedId = String(route.params.id || route.query.copy || '');
    const loaded = loadedId
      ? { ...emptyInspection(), ...(await inspectionApi.get(kind.value, loadedId)).data }
      : emptyInspection();
    const [choiceResult, selected, typeResult] = await Promise.all([
      inspectionApi.choices(true),
      resolvePlanning<InspectionDefinition>('inspection', loaded),
      isTasks.value || !auth.can('inspectionType')
        ? Promise.resolve({ data: [] as InspectionDefinition[] })
        : inspectionApi.list('types'),
    ]);
    if (version !== loadVersion) return;
    choices.value = choiceResult;
    choices.value.equipment = selected.equipment;
    choices.value.users = mergeOptions(
      selected.users,
      auth.user
        ? [{ id: auth.user.id, name: auth.user.name, department: auth.user.department }]
        : [],
    );
    standards.value = selected.standards;
    types.value = typeResult.data.length ? typeResult.data : choiceResult.types || [];
    if (editing.value) {
      const id = loadedId;
      form.value = loaded;
      if (!daily.value && !standard.value) {
        form.value.startDate = form.value.startDate.length === 10
          ? `${form.value.startDate}T00:00:00` : form.value.startDate;
        form.value.endDate = form.value.endDate.length === 10
          ? `${form.value.endDate}T23:59:59` : form.value.endDate;
      }
      if (route.query.copy) {
        form.value.id = '';
        delete form.value.rowVersion;
        form.value.code = '';
        form.value.name += '（副本）';
      }
      if (!form.value.departmentId) form.value.departmentId = auth.user?.orgId || '';
      if (!daily.value && !form.value.typeId)
        form.value.typeId =
          types.value.find(
            (t) => t.enabled && t.unit === (daily.value ? '日' : '周') && t.period === 1,
          )?.id ||
          types.value[0]?.id ||
          '';
      if (daily.value) form.value.typeId = '';
      if (!id && !standard.value && !daily.value) typeChange();
      if (!form.value.owners.length && auth.user) form.value.owners = [auth.user.id];
      if (!form.value.items) form.value.items = [];
      if (daily.value && !form.value.arrangements.length) {
        const a = emptyArrangement(form.value.startDate);
        a.name = tr('daily.defaultName');
        a.owners = [...form.value.owners];
        a.followUpUserId = '';
        form.value.arrangements = [a];
      }
      selectedDetails.value = [];
      dailyErrors.value = {};
      unsaved.markSaved();
    }
  } catch (e) {
    if (version === loadVersion)
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    // Vue reuses this page across inspection resources. Clear the previous resource
    // before rendering the new columns, and reject any outstanding old response.
    loadVersion++;
    serverList.reset();
    records.value = [];
    listTotal.value = 0;
    taskOrganizations.value = [];
    choices.value.organizations = [];
    loading.value = true;
    restoreColumnPreferences();
    sortKey.value = kind.value === 'daily-tasks' ? 'dueAt' : '';
    sortAsc.value = kind.value !== 'daily-tasks';
    keyword.value = '';
    search.value = '';
    queue.value = '全部';
    conditions.value = [];
    columnFilters.value = {};
    selected.value = [];
    page.value = 1;
    load();
  },
  { flush: 'sync' },
);
watch(
  [page, size, search, queue, columnFilters, conditions, sortKey, sortAsc, organizationFilter],
  () => {
    if (!editing.value) void load();
  },
  { deep: true, flush: 'post' },
);
let timer: ReturnType<typeof setInterval>;
onMounted(() => {
  load();
  timer = setInterval(() => {
    if (!editing.value && isTasks.value) load();
  }, 60000);
});
onUnmounted(() => {
  equipmentRevision++;
  clearInterval(timer);
  loadVersion++;
  serverList.invalidate();
});
function list() {
  router.push({ name: 'inspection', params: { kind: kind.value } });
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  dailyErrors.value = {};
  try {
    if (standard.value && !(await standardEditor.value?.validate())) return;
    if (!standard.value && !prepareExtension(form.value)) {
      dailyEditorTab.value = 'basic';
      return;
    }
    if (daily.value && form.value.id) {
      const impact = (await inspectionApi.assignmentImpact(form.value)).data.count;
      if (impact && !(await confirmAction(tr('staff.impactConfirm', { count: impact })))) return;
    }
    await inspectionApi.save(kind.value, form.value);
    unsaved.markSaved();
    ElMessage.success(tr('ui.equipment.text0194'));
    busy.value = false;
    list();
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    if (standard.value && (await standardEditor.value?.capture(e))) return;
    if (extensionError(e)) {
      dailyEditorTab.value = 'basic';
      return;
    }
    if (e instanceof ApiError && Object.keys(e.fieldErrors).length) {
      dailyErrors.value = e.fieldErrors;
      const firstField = Object.keys(e.fieldErrors)[0] || '';
      dailyEditorTab.value =
        firstField.startsWith('arrangements') || firstField.startsWith('roster')
          ? 'arrangements'
          : firstField.startsWith('details')
            ? 'basic'
            : ['restDays', 'weeklyRestDays', 'monthlyRestDays', 'holidays', 'workdays'].includes(
                  firstField,
                )
              ? 'calendar'
              : 'basic';
      error.value = Object.values(e.fieldErrors)[0] || e.message;
      return;
    }
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text0195');
  } finally {
    busy.value = false;
  }
}
function open(row?: InspectionDefinition | InspectionTask) {
  dailyDeviceOverrides.value = false;
  dailyEditorTab.value = 'basic';
  if (isTasks.value) {
    router.push({ name: 'inspection-task', params: { id: row!.id }, query: { kind: kind.value } });
    return;
  }
  router.push({
    name: row ? 'inspection-edit' : 'inspection-new',
    params: { kind: kind.value, ...(row ? { id: row.id } : {}) },
  });
}
const exemptionOpen = ref(false),
  exemptionPlan = ref<InspectionDefinition>(),
  exemptionEquipment = ref<string[]>([]),
  exemptionRange = ref<string[]>([]),
  exemptionReason = ref(''),
  exemptionError = ref(''),
  exemptionOptions = ref<SelectorRow[]>([]);
async function openExemption(row: InspectionDefinition | InspectionTask) {
  try {
    const plan = (await inspectionApi.get('daily-plans', row.id)).data;
    exemptionPlan.value = plan;
    exemptionOptions.value = (await resolvePlanning('inspection', plan)).equipment;
    exemptionEquipment.value = [];
    exemptionRange.value = [];
    exemptionReason.value = '';
    exemptionError.value = '';
    exemptionOpen.value = true;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('common.error.requestFailed'));
  }
}
function exemptionPage(q: string, page: number, size: number) {
  const rows = exemptionOptions.value.filter((e) =>
    `${e.code} ${e.name}`.toLowerCase().includes(q.toLowerCase()),
  );
  return Promise.resolve({
    data: rows.slice((page - 1) * size, page * size),
    page: { number: page, size, total: rows.length },
  });
}
async function approveExemption() {
  if (!exemptionPlan.value) return;
  busy.value = true;
  exemptionError.value = '';
  try {
    const result = await inspectionApi.dailyExemption(exemptionPlan.value.id, {
      equipmentIds: exemptionEquipment.value,
      startAt: exemptionRange.value[0] || '',
      endAt: exemptionRange.value[1] || '',
      reason: exemptionReason.value,
      rowVersion: exemptionPlan.value.rowVersion,
    });
    exemptionOpen.value = false;
    ElMessage.success(tr('daily.exemptionCreated', { count: result.data.count }));
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    exemptionError.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
function planMoreActions(row: InspectionDefinition | InspectionTask) {
  return [
    ...(daily.value && auth.can('inspectionDailyTask', '免检')
      ? [{ label: tr('daily.rangeExemption'), run: () => openExemption(row) }]
      : []),
    ...(can('删除')
      ? [{ label: tr('ui.equipment.text0146'), danger: true, run: () => remove(row) }]
      : []),
  ];
}
function copy(row: InspectionDefinition | InspectionTask) {
  router.push({ name: 'inspection-new', params: { kind: kind.value }, query: { copy: row.id } });
}
async function remove(row: InspectionDefinition | InspectionTask) {
  if (!(await confirmAction(tr('ui.equipment.text0277')))) return;
  try {
    await inspectionApi.remove(kind.value, row.id);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0278'));
  }
}
async function exportList() {
  try {
    const exportFields: [string, string][] = [
      ...baseFields.value,
      ...extensionExportColumns.value.map((c) => [c.key, c.label] as [string, string]),
    ];
    const blob = await inspectionApi.exportFile(kind.value, {
      ...listParameters(),
      columns: JSON.stringify(exportFields.map(([key]) => key)),
    });
    const url = URL.createObjectURL(blob),
      link = document.createElement('a');
    link.href = url;
    link.download = inspectionLabels[kind.value] + '.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0185'));
  }
}

function reset() {
  keyword.value = '';
  search.value = '';
  columnFilters.value = {};
  conditions.value = [];
  queue.value = '全部';
}
const dateRange = computed<string[]>({
  get: () => [form.value.startDate, form.value.endDate],
  set: (v) => {
    form.value.startDate = v?.[0] || '';
    form.value.endDate = v?.[1] || '';
  },
});
const dailyProjectOptions = computed(() => {
  const options = new Map<
    string,
    { key: string; label: string; standardId: string; itemId: string }
  >();
  for (const detail of form.value.details)
    for (const ref of detail.projectRefs) {
      const standard = standards.value.find((s) => s.id === ref.standardId);
      const item = standard?.items.find((i) => i.id === ref.itemId);
      const key = `${ref.standardId}:${ref.itemId}`;
      options.set(key, { ...ref, key, label: item ? `${standard?.name} / ${item.name}` : key });
    }
  return [...options.values()];
});
const dailyRosterSlots = computed(() =>
  form.value.arrangements.filter((a) => a.frequency === 'shift').flatMap((a) => a.slots),
);
const dailyHasRoster = computed(() =>
  form.value.arrangements.some((a) => a.personMode === 'roster'),
);
function typeChange() {
  const t = types.value.find((t) => t.id === form.value.typeId);
  if (t) {
    form.value.period = t.period;
    form.value.unit = t.unit;
    if (!daily.value) periodChange();
  }
}
function periodChange() {
  form.value.validHours = form.value.unit === '年' ? form.value.period * 12 : form.value.period;
  form.value.validUnit = form.value.unit === '年' ? '月' : form.value.unit;
}
function toTree(nodes: { id: string; name: string; parentId?: string }[]): any[] {
  const build = (parent: string): any[] =>
    nodes
      .filter(
        (n) =>
          n.parentId === parent ||
          (!parent && (!n.parentId || !nodes.some((p) => p.id === n.parentId))),
      )
      .map((n) => ({ value: n.id, label: n.name, children: build(n.id) }));
  return build('');
}
const departmentNodes = computed(() =>
  choices.value.organizations.map((o) => ({
    ...o,
    parentId: o.parentId || '',
    value: o.id,
    disabled: o.type !== '部门',
  })),
);
const selectedDetails = ref<string[]>([]),
  equipmentOpen = ref(false),
  equipmentSearch = ref(''),
  pickedEquipment = ref<string[]>([]),
  equipmentCategory = ref('');
const equipmentOptions = ref<InspectionChoices['equipment']>([]),
  equipmentPage = ref(1),
  equipmentSize = ref(20),
  equipmentTotal = ref(0),
  equipmentLoading = ref(false),
  equipmentError = ref('');
const equipmentSelectedOnly = ref(false);
const equipmentVisible = computed(() =>
  equipmentSelectedOnly.value
    ? pickedEquipment.value.map(
        (id) =>
          choices.value.equipment.find((row) => row.id === id) || {
            id,
            name: '已选设备不可用',
            code: id,
          },
      )
    : equipmentOptions.value,
);
watch(equipmentSelectedOnly, (value) => {
  if (!value && equipmentOpen.value) void loadEquipmentPage();
});
let equipmentRevision = 0;
async function loadEquipmentPage() {
  if (!equipmentOpen.value || equipmentSelectedOnly.value) return;
  const seq = ++equipmentRevision;
  equipmentLoading.value = true;
  equipmentError.value = '';
  equipmentOptions.value = [];
  try {
    const result = await selector.equipment(
      equipmentSearch.value,
      equipmentPage.value,
      equipmentSize.value,
      equipmentCategory.value,
    );
    if (seq !== equipmentRevision) return;
    equipmentOptions.value = result.data;
    equipmentTotal.value = result.page.total;
    rememberEquipment(result.data);
  } catch (e) {
    if (seq === equipmentRevision)
      equipmentError.value = e instanceof Error ? e.message : tr('ui.equipment.text0279');
  } finally {
    if (seq === equipmentRevision) equipmentLoading.value = false;
  }
}
watch([equipmentPage, equipmentSize, equipmentSearch, equipmentCategory], () => {
  if (equipmentOpen.value) void loadEquipmentPage();
});
watch([equipmentSearch, equipmentCategory], () => {
  equipmentPage.value = 1;
});
watch(equipmentOpen, (open) => {
  if (!open) equipmentRevision++;
});
function equipmentPicker() {
  equipmentSelectedOnly.value = false;
  pickedEquipment.value = [];
  equipmentSearch.value = '';
  equipmentPage.value = 1;
  equipmentOpen.value = true;
  void loadEquipmentPage();
}
function applicable(st: InspectionDefinition, eq: InspectionChoices['equipment'][number]) {
  return standardApplies(st, eq, choices.value.categories);
}
function refsFor(eq: InspectionChoices['equipment'][number]) {
  return standards.value
    .filter((st) => applicable(st, eq) && st.enabled !== false)
    .flatMap((st) => st.items.map((item) => ({ standardId: st.id, itemId: item.id })));
}
async function loadDefaults(ids: string[]) {
  if (!auth.can('inspectionStandard')) return;
  for (let at = 0; at < ids.length; at += 200) {
    const result = await selector.defaults<InspectionDefinition>(ids.slice(at, at + 200));
    standards.value = mergeOptions(standards.value, result.data);
  }
}
async function addEquipment() {
  try {
    await loadDefaults(pickedEquipment.value);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0280'));
    return;
  }
  for (const id of pickedEquipment.value) {
    if (form.value.details.some((d) => d.equipmentId === id)) continue;
    const eq = choices.value.equipment.find((e) => e.id === id)!;
    form.value.details.push({
      key: uuid(),
      equipmentId: id,
      owners: [],
      reviewers: [],
      projectRefs: refsFor(eq),
    });
  }
  equipmentOpen.value = false;
}
function eqOf(detail: InspectionDetail) {
  return choices.value.equipment.find((e) => e.id === detail.equipmentId);
}
function moveDetail(index: number, offset: number) {
  const to = index + offset;
  if (to < 0 || to >= form.value.details.length) return;
  const [row] = form.value.details.splice(index, 1);
  if (row) form.value.details.splice(to, 0, row);
}
const projectOpen = ref(false),
  projectRow = ref<InspectionDetail>(),
  projectStandardId = ref(''),
  showAllStandards = ref(false),
  projectSearch = ref(''),
  projectRefs = ref<string[]>([]);
const {
  rows: projectStandards,
  search: standardSearch,
  page: standardPage,
  total: standardTotal,
  size: standardSize,
  loading: standardLoading,
  error: standardError,
  load: reloadStandards,
} = useStandardSelectorPage<InspectionDefinition>(
  'inspection',
  projectOpen,
  computed(() => projectRow.value?.equipmentId || ''),
  showAllStandards,
  projectStandardId,
  (rows) => {
    standards.value = mergeOptions(standards.value, rows);
  },
);
const projectItems = computed(
  () =>
    standards.value
      .find((st) => st.id === projectStandardId.value)
      ?.items.filter(
        (i) =>
          !projectSearch.value || [i.name, i.content].some((t) => t.includes(projectSearch.value)),
      ) || [],
);
function pickProjects(detail: InspectionDetail) {
  projectRow.value = detail;
  showAllStandards.value = false;
  projectSearch.value = '';
  standardSearch.value = '';
  projectRefs.value = detail.projectRefs.map((r) => r.standardId + '/' + r.itemId);
  projectStandardId.value = projectStandards.value[0]?.id || '';
  projectOpen.value = true;
}
function applyProjects() {
  if (!projectRow.value) return;
  projectRow.value.projectRefs = projectRefs.value.map((ref) => {
    const [standardId, itemId] = ref.split('/');
    return { standardId: standardId!, itemId: itemId! };
  });
  projectOpen.value = false;
}
async function matchProjects() {
  const targets = selectedDetails.value.length
    ? form.value.details.filter((d) => selectedDetails.value.includes(d.key))
    : form.value.details;
  try {
    await loadDefaults(targets.map((row) => row.equipmentId));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0280'));
    return;
  }
  targets.forEach((d) => {
    const eq = eqOf(d);
    if (eq) d.projectRefs = refsFor(eq);
  });
  ElMessage.success(tr('ui.equipment.text0281'));
}
const detailBatch = ref(''),
  detailBatchOpen = ref(false),
  batchUsers = ref<string[]>([]);
function detailCommand(command: string) {
  if (command === 'delete') {
    form.value.details = form.value.details.filter((d) => !selectedDetails.value.includes(d.key));
    selectedDetails.value = [];
    return;
  }
  detailBatch.value = command;
  batchUsers.value = [];
  detailBatchOpen.value = true;
}
function applyDetailBatch() {
  form.value.details
    .filter((d) => selectedDetails.value.includes(d.key))
    .forEach((d) => {
      if (detailBatch.value === 'owners') d.owners = [...batchUsers.value];
      else d.reviewers = [...batchUsers.value];
    });
  detailBatchOpen.value = false;
}
const typeOpen = ref(false),
  typeForm = ref(emptyInspection());
function newType() {
  typeForm.value = emptyInspection();
  typeForm.value.unit = '日';
  typeOpen.value = true;
}
async function saveType() {
  if (busy.value) return;
  if (!typeForm.value.name.trim()) {
    ElMessage.warning(tr('ui.equipment.text0282'));
    return;
  }
  if (!Number.isInteger(typeForm.value.period) || typeForm.value.period < 1) {
    ElMessage.warning(tr('ui.equipment.text0283'));
    return;
  }
  if (
    !Number.isInteger(typeForm.value.sortOrder) ||
    typeForm.value.sortOrder < 0 ||
    typeForm.value.sortOrder > 999999
  ) {
    ElMessage.warning(tr('ui.equipment.text0284'));
    return;
  }
  busy.value = true;
  try {
    await inspectionApi.save('types', typeForm.value);
    types.value = (await inspectionApi.list('types')).data;
    typeForm.value = { ...emptyInspection(), unit: '日' };
    ElMessage.success(tr('ui.equipment.text0285'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
  } finally {
    busy.value = false;
  }
}
async function deleteType(type: InspectionDefinition) {
  if (!(await confirmAction(tr('ui.common.dynamic39', { p0: type.name })))) return;
  try {
    await inspectionApi.remove('types', type.id);
    types.value = (await inspectionApi.list('types')).data;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0278'));
  }
}
const importInput = ref<InstanceType<typeof AppFilePicker>>(),
  rosterInput = ref<InstanceType<typeof AppFilePicker>>();
async function importEquipment(files: File[]) {
  const file = files[0];
  if (!file) return;
  try {
    const data = csvRows((await file.text()).replace(/^\uFEFF/, ''));
    const index = data[0]?.findIndex((h) => ['设备编号', '设备编号'].includes(h)) ?? -1;
    if (index < 0) throw new Error(tr('ui.equipment.text0286'));
    const ids = data.slice(1).map((r) => {
      const eq = choices.value.equipment.find((e) => e.code === r[index]);
      if (!eq) throw new Error(tr('ui.common.dynamic40', { p0: r[index] }));
      return eq.id;
    });
    pickedEquipment.value = ids;
    addEquipment();
    ElMessage.success(tr('ui.equipment.text0287'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0288'));
  } finally {
  }
}
async function importRoster(files: File[]) {
  const file = files[0];
  if (!file) return;
  try {
    const data = csvRows((await file.text()).replace(/^\uFEFF/, ''));
    const headers = data[0] || [];
    const date = headers.indexOf('日期'),
      shift = headers.indexOf('班次'),
      account = headers.indexOf('人员账号');
    if (Math.min(date, shift, account) < 0) throw new Error(tr('ui.equipment.text0289'));
    const result = data.slice(1).map((r) => {
      const s = form.value.shifts.find((s) => s.name === r[shift]);
      if (!s) throw new Error(tr('ui.common.dynamic41', { p0: r[shift] }));
      const users = (r[account] || '').split(/[、;；]/).map((v) => {
        const u = choices.value.users.find((u) => u.account === v.trim());
        if (!u) throw new Error(tr('ui.common.dynamic42', { p0: v }));
        return u.id;
      });
      if (!/^\d{4}-\d{2}-\d{2}$/.test(r[date] || '')) throw new Error(tr('ui.equipment.text0290'));
      return { key: uuid(), date: r[date]!, shiftId: s.id, users };
    });
    form.value.roster.push(...result);
    ElMessage.success(tr('ui.equipment.text0291'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0288'));
  } finally {
  }
}
const reviewOpen = ref(false);
const reviewTasks = computed(
  () => records.value.filter((r) => selected.value.includes(r.id)) as InspectionTask[],
);
function openBatchReview() {
  if (
    reviewTasks.value.some(
      (t) => t.status !== '待审核' || !t.canReview || t.reviewMode === '逐项审核',
    )
  ) {
    ElMessage.warning(tr('ui.equipment.text0292'));
    return;
  }
  reviewOpen.value = true;
}
async function batchReview(body: unknown) {
  busy.value = true;
  try {
    await inspectionApi.batchReview(
      reviewTasks.value.map((t) => ({ ...(body as object), id: t.id, rowVersion: t.rowVersion })),
    );
    reviewOpen.value = false;
    selected.value = [];
    await load();
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0293'));
  } finally {
    busy.value = false;
  }
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => form.value.shifts;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows3 = () => form.value.roster;
type TableColumnRow3 = NonNullable<ReturnType<typeof tableColumnRows3>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows4 = () => form.value.details;
type TableColumnRow4 = NonNullable<ReturnType<typeof tableColumnRows4>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows5 = () => equipmentOptions.value;
type TableColumnRow5 = NonNullable<ReturnType<typeof tableColumnRows5>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows6 = () => projectItems.value;
type TableColumnRow6 = NonNullable<ReturnType<typeof tableColumnRows6>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows7 = () => types.value;
type TableColumnRow7 = NonNullable<ReturnType<typeof tableColumnRows7>>[number];
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
  <section class="content inspection-page" :class="{ 'inspection-editor': editing }">
    <div v-if="!editing" class="task-list-layout" :class="{ 'with-organizations': isTasks }">
      <TaskOrganizationFilter
        v-if="isTasks"
        v-model="organizationFilter"
        :organizations="taskOrganizations"
        :scope-by-records="false"
        :records="records"
      />
      <div class="task-list-content">
        <AppListToolbar class="inspection-toolbar">
          <div class="inspection-toolbar-actions">
            <ElButton v-if="!isTasks && can('新增')" type="primary" @click="open()"
              ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
            ><ElButton v-if="isTasks" :disabled="!selected.length" @click="openBatchReview">{{
              tr('ui.equipment.text0294')
            }}</ElButton
            ><span v-if="selected.length"
              >{{ tr('ui.equipment.text0295') }} {{ selected.length }}
              {{ tr('ui.equipment.text0296') }}</span
            >
          </div>
          <form class="inspection-search" @submit.prevent="search = keyword.trim()">
            <ElSelect
              v-if="isTasks"
              v-model="queue"
              class="inspection-queue-filter"
              :aria-label="tr('ui.equipment.text0203')"
            >
              <ElOption
                v-for="q in ['全部', '我的待执行', '我的待审核']"
                :key="q"
                :value="q"
                :label="q === '全部' ? tr('ui.equipment.text1388') : q"
              />
            </ElSelect>
            <ListToolbarIcons
              v-if="!isTasks"
              mode="filter"
              :filter-open="filterOpen"
              :columns-open="false"
              @toggle-filter="filterOpen = !filterOpen"
            /><ListSearchInput
              v-model="keyword"
              :placeholder="isTasks ? tr('ui.equipment.text0297') : tr('ui.equipment.text0298')"
            /><ElButton
              v-if="isTasks"
              :class="{ active: conditions.length }"
              @click="showFilter($event.currentTarget as HTMLElement)"
              >{{ tr('ui.equipment.text0203')
              }}<span v-if="conditions.length">（{{ conditions.length }}）</span></ElButton
            ><ElButton @click="reset">{{ tr('ui.equipment.text0204') }}</ElButton>
          </form>
        </AppListToolbar>
        <AppliedFilterChips
          editable
          @edit="(condition, anchor) => showFilter(anchor, condition.field)"
          v-if="isTasks"
          :conditions="conditions"
          :fields="filterFields"
          @change="replaceFilters"
        />
        <p v-if="error" class="inspection-error">
          {{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
        </p>
        <div class="inspection-list-card">
          <div class="inspection-list-scroll">
            <AppBusinessTable
              :key="kind"
              v-model:selection-ids="selected"
              :selection-width="48"
              selection-fixed="left"
              selection-align="center"
              :selectable="isTasks"
              :sortable="true"
              :default-sort="kind === 'daily-tasks' ? { prop: 'dueAt', order: 'descending' } : undefined"
              @sort-change="onTableSort"
              :rows="pageRows"
              :loading="loading"
              :pagination="false"
              :fill="true"
              :framed="false"
              class="business-table inspection-list"
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
                v-for="[key, label] in shownFields"
                :prop="key!"
                :min-width="150"
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
                    v-if="filterOpen && !isTasks && !key.startsWith('custom:')"
                    class="table-column-filter"
                    @click.stop
                  >
                    <ElSelect
                      v-if="key === 'status'"
                      v-model="columnFilters[key]"
                      clearable
                      :placeholder="tr('ui.equipment.text0300')"
                      ><ElOption v-for="s in inspectionStates" :key="s" :value="s" /></ElSelect
                    ><ElInput
                      v-else
                      v-model="columnFilters[key]"
                      clearable
                      :placeholder="tr('ui.equipment.text0301')"
                    /></div></template
                ><template #default="{ row: r, $index: tableRowIndex }">
                  <ElButton
                    v-if="key === (kind === 'daily-plans' ? 'name' : 'code')"
                    class="inspection-text-button"
                    @click="open(r)"
                    type="primary"
                    link
                    >{{ value(r, key) }}</ElButton
                  ><ElButton
                    v-else-if="isTasks && key === 'planName' && (r as InspectionTask).planId"
                    link
                    type="primary"
                    @click="previewTaskPlan(r as InspectionTask)"
                    >{{ value(r, key) }}</ElButton
                  ><MaintenanceStatus
                    v-else-if="key === 'status'"
                    :status="String(value(r, key))"
                  /><span
                    v-else-if="key === 'enabled'"
                    class="inspection-enabled"
                    :class="{ disabled: !(r as InspectionDefinition).enabled }"
                    >{{ value(r, key) }}</span
                  ><DepartmentText
                    v-else-if="key === 'departmentName'"
                    :value="String(value(r, key))"
                    :department-id="r.departmentId"
                    :nodes="choices.organizations"
                  /><DepartmentValuesText
                    v-else-if="isDepartmentCustomColumn(customDepartmentDefinitions, key)"
                    :value="String(value(r, key))"
                    :ids="r.customFields?.[key.slice(7)]"
                  /><span v-else :title="String(value(r, key))">{{
                    String(value(r, key)).replace('T', ' ') || ''
                  }}</span>
                </template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn
                class-name="list-action-column"
                fixed="right"
                :width="170"
                :min-width="156"
                :resizable="false"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">
                  <div class="inspection-row-actions">
                    <p v-if="configurationError" class="error" role="alert">
                      {{ configurationError }}
                    </p>
                    <template v-if="isTasks"
                      ><ElButton
                        class="inspection-text-button"
                        @click="open(r)"
                        type="primary"
                        link
                        >{{
                          (r as InspectionTask).canReview &&
                          (r as InspectionTask).status === '待审核'
                            ? tr('ui.equipment.text0294')
                            : (r as InspectionTask).canExecute &&
                                [
                                  tr('ui.equipment.text0302'),
                                  tr('ui.equipment.text0303'),
                                  tr('ui.equipment.text0304'),
                                  tr('ui.equipment.text0305'),
                                  tr('ui.equipment.text0306'),
                                ].includes((r as InspectionTask).status)
                              ? tr('ui.equipment.text0307')
                              : tr('ui.equipment.text0206')
                        }}</ElButton
                      ><ElButton
                        v-if="daily && (r as InspectionTask).canDelete"
                        type="danger"
                        link
                        @click="confirmTaskDelete(r as InspectionTask)"
                        >{{ tr('ui.equipment.text0146') }}</ElButton
                      ></template
                    ><template v-else
                      ><ElButton
                        v-if="can('编辑')"
                        class="inspection-text-button"
                        @click="open(r)"
                        type="primary"
                        link
                      >
                        {{ tr('ui.equipment.text0207') }}</ElButton
                      ><ElButton
                        v-if="can('新增')"
                        class="inspection-text-button"
                        @click="copy(r)"
                        type="primary"
                        link
                      >
                        {{ tr('ui.equipment.text0270') }}</ElButton
                      ><RowMoreActions
                        v-if="
                          daily
                            ? can('编辑') || auth.can('inspectionDailyTask', '免检') || can('删除')
                            : can('删除') && can('编辑') && can('新增')
                        "
                        :actions="planMoreActions(r)"
                      /><ElButton
                        v-else-if="can('删除')"
                        class="inspection-text-button danger"
                        @click="remove(r)"
                        type="danger"
                        link
                      >
                        {{ tr('ui.equipment.text0146') }}
                      </ElButton></template
                    >
                  </div>
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!pageRows.length">
                  {{ loading ? tr('ui.equipment.text0162') : tr('ui.equipment.text0308') }}
                </div></template
              ></AppBusinessTable
            >
          </div>
          <footer class="inspection-list-footer">
            <span
              >{{ tr('ui.equipment.text0309') }} {{ listTotal }}
              {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="listTotal"
            /><ListToolbarIcons
              mode="footer"
              :can-export="can('导出')"
              :filter-open="false"
              :columns-open="columnsOpen"
              @toggle-columns="columnsOpen = !columnsOpen"
              @export="exportList"
              ><template #columns
                ><ElCheckboxGroup v-model="visibleColumns"
                  ><ElCheckbox
                    v-for="[key, label] in fields"
                    :key="key"
                    :disabled="visibleColumns.includes(key) && visibleColumns.length === 1"
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
      <header class="inspection-edit-heading">
        <AppBackButton
          :label="`返回${inspectionLabels[kind]}列表`"
          :disabled="busy"
          @click="list"
        /><span>{{ inspectionLabels[kind] }}</span
        ><span>/</span
        ><strong>{{ form.id ? tr('ui.equipment.text0207') : tr('ui.equipment.text0149') }}</strong
        ><span class="inspection-heading-spacer" /><ElButton text @click="list">{{
          tr('ui.equipment.text0076')
        }}</ElButton>
      </header>
      <ElTabs v-if="daily" v-model="dailyEditorTab" class="daily-plan-tabs">
        <ElTabPane name="basic" :label="tr('ui.equipment.text0077') + ' (' + form.details.length + ')'" />
        <ElTabPane
          name="arrangements"
          :label="tr('daily.ui.whenWho') + ' (' + form.arrangements.length + ')'"
        />
        <ElTabPane name="calendar" :label="tr('daily.ui.calendar')" />
      </ElTabs>
      <div class="inspection-editor-scroll" :class="{ 'daily-plan-editor': daily }">
        <p v-if="daily && dailyEditorTab === 'basic'" class="daily-flow-help">{{ tr('daily.ui.flowHelp') }}</p>
        <p v-if="error" class="inspection-error">{{ error }}</p>
        <InspectionStandardEditor
          v-if="standard"
          ref="standardEditor"
          v-model="form"
          :types="types"
          :choices="choices"
          :departments="departmentNodes"
          :categories="toTree(choices.categories)"
          @equipment="rememberEquipment"
          @add-type="newType"
        />
        <section v-else v-show="!daily || dailyEditorTab === 'basic'" class="inspection-form-card" :class="{ 'periodic-plan-card': !daily && !standard }">
          <div class="inspection-form-grid">
            <h3 v-if="!daily && !standard" class="wide periodic-section-title">{{ tr('ui.equipment.text0077') }}</h3>
            <label
              ><span>{{
                standard ? tr('ui.equipment.text0310') : tr('ui.equipment.text0311')
              }}</span
              ><ElInput
                v-model="form.code"
                :placeholder="tr('ui.equipment.text0312')"
                maxlength="64" /></label
            ><label
              ><span
                ><b>*</b
                >{{ standard ? tr('ui.equipment.text0313') : tr('ui.equipment.text0314') }}</span
              ><ElInput v-model="form.name" maxlength="150" /></label
            ><label
              ><span><b>*</b>{{ tr('ui.equipment.text0098') }}</span
              ><TreeSelect
                v-model="form.departmentId"
                :nodes="departmentNodes"
                :placeholder="tr('ui.equipment.text0315')"
            /></label>
            <template v-if="!standard"
              ><label class="span-two"
                ><span
                  ><b>*</b>{{ tr(daily ? 'daily.plan.validity' : 'output.inspection.label3206') }}</span
                ><ElDatePicker
                  v-model="dateRange"
                  :type="daily ? 'daterange' : 'datetimerange'"
                  :format="daily ? 'YYYY-MM-DD' : 'YYYY-MM-DD HH:mm:ss'"
                  :value-format="daily ? 'YYYY-MM-DD' : 'YYYY-MM-DDTHH:mm:ss'"
                  :start-placeholder="tr(daily ? 'component.el.datepicker.startDate' : 'component.el.datepicker.startTime')"
                  :end-placeholder="tr(daily ? 'component.el.datepicker.endDate' : 'component.el.datepicker.endTime')"
                  range-separator="至" /></label
              ><label
                ><span>{{ tr('ui.equipment.text0216') }}</span
                ><ElSwitch v-model="form.enabled" />
                <div v-if="form.legacyCategoryNames?.length">
                  {{ tr('ui.equipment.text0323')
                  }}<ElTag
                    v-for="name in form.legacyCategoryNames"
                    :key="name"
                    type="warning"
                    closable
                    @close="
                      form.legacyCategoryNames = form.legacyCategoryNames.filter((x) => x !== name)
                    "
                    >{{ name }}</ElTag
                  >
                  {{ tr('ui.equipment.text0324') }}
                </div></label
              >
              <h3 v-if="!daily" class="wide periodic-section-title">{{ tr('inspection.periodic.schedule') }}</h3>
              <label v-if="!daily"
                ><span><b>*</b>{{ tr('ui.equipment.text0325') }}</span
                ><ElSelect v-model="form.typeId" @change="typeChange"
                  ><ElOption
                    v-for="t in types"
                    :key="t.id"
                    :value="t.id"
                    :label="t.name"
                    :disabled="!t.enabled" /></ElSelect
              ></label>
              <template v-if="!daily"
                ><label class="periodic-cycle-field"
                  ><span>{{ tr('ui.equipment.text0326') }}<AppHelpTip :content="tr('inspection.periodic.cycleHelp')" /></span>
                  <div class="inspection-number-unit"><ElInputNumber v-model="form.period" :min="1" :max="10000" :precision="0" controls-position="right" :aria-label="tr('inspection.periodic.cycleValue')" @change="periodChange" /><ElSelect v-model="form.unit" :aria-label="tr('inspection.periodic.cycleUnit')" @change="periodChange"><ElOption v-for="unit in ['日','周','月','年']" :key="unit" :value="unit" :label="unit" /></ElSelect></div></label></template
              ><label v-if="!daily"
                ><span>{{ tr('ui.equipment.text0328') }}</span>
                <DurationInput
                  v-model="form.advanceHours"
                  v-model:unit="form.advanceUnit"
                  :min="0"
                  :max-years="1"
              /></label>
              <ReminderPolicyField v-if="daily" v-model:mode="form.reminderMode" v-model:id="form.reminderPolicyId" :name="form.reminderPolicyName" kind="daily" />
              <h3 v-if="!daily" class="wide periodic-section-title">{{ tr('inspection.periodic.execution') }}</h3>
              <label v-if="!daily"
                ><span>{{
                  form.personMode === '按照排班表'
                    ? tr('ui.equipment.text0334')
                    : tr('ui.equipment.text0102')
                }}</span
                ><PagedChoiceField
                  v-model="form.owners"
                  multiple
                  :options="choices.users"
                  :load-rows="selector.users"
                  :title="tr('ui.equipment.text0335')"
                  :placeholder="tr('ui.equipment.text0336')"
                  @select="rememberPeople" /></label
              >
              <label
                ><span>{{ tr('ui.equipment.text0333') }}</span
                ><ElSelect v-model="form.reviewMode"
                  ><ElOption
                    v-for="v in daily
                      ? ['无需验收', '整机验收']
                      : ['无需验收', '整机验收', '逐项审核']"
                    :key="v"
                    :value="v" /></ElSelect
              ></label>
              <!-- <label
                ><span>设备校验</span
                ><ElSelect v-model="form.scanMode"
                  ><ElOption
                    v-for="v in ['无需扫码', '逐台校验', '校验一台即可提交']"
                    :key="v"
                    :value="v" /></ElSelect
              ></label> -->
              <label v-if="form.reviewMode !== '无需验收'" :class="{ 'span-two': daily }"
                ><span><b>*</b>{{ tr('ui.equipment.text0337') }}</span
                ><PagedChoiceField
                  v-model="form.reviewers"
                  multiple
                  :options="choices.users"
                  :load-rows="selector.users"
                  :title="tr('ui.equipment.text0335')"
                  :placeholder="tr('ui.equipment.text0336')"
                  @select="rememberPeople"
              /></label>
              <!-- <div class="inspection-flags wide">
                <ElCheckbox v-model="form.onlyAbnormal">只需填写异常项</ElCheckbox
                ><ElCheckbox v-model="form.allowLate">过期仍可检查</ElCheckbox
                ><ElCheckbox v-if="daily" v-model="form.skipUnstaffed">未排班不开班</ElCheckbox
                ><ElCheckbox v-if="daily" v-model="form.useShiftEnd">班次结束时任务到期</ElCheckbox
                ><label
                  ><span>随机必拍项目</span
                  ><ElInputNumber
                    v-model="form.randomPhotoCount"
                    :min="0"
                    :max="1000"
                    controls-position="right"
                /></label>
              </div> -->
            </template>
            <h3 v-if="!daily && !standard" class="wide periodic-section-title">{{ tr('daily.plan.reminders') }}</h3>
            <ReminderPolicyField v-if="!daily && !standard" v-model:mode="form.reminderMode" v-model:id="form.reminderPolicyId" :name="form.reminderPolicyName" kind="periodic" />
            <label :class="!daily && !standard ? 'span-two periodic-remark' : 'wide'"
              ><span>{{ tr('ui.equipment.text0177') }}</span
              ><ElInput v-model="form.remark" type="textarea" :rows="2" /></label
            ><label v-if="standard"
              ><span>{{ tr('ui.equipment.text0216') }}</span
              ><ElSwitch v-model="form.enabled"
            /></label>
          </div>
        </section>
        <template v-if="!standard">
          <section v-if="daily" v-show="dailyEditorTab === 'calendar'" class="inspection-form-card">
            <div class="inspection-section-tools">
              <h3>{{ tr('daily.calendar') }}</h3>
            </div>
            <div class="inspection-calendar-grid daily-calendar">
              <ElForm class="inspection-calendar-fields" label-position="top" @submit.prevent>
                <ElFormItem
                  :label="tr('daily.shiftOnly.weeklyRest')"
                  :error="dailyErrors.weeklyRestDays"
                >
                  <ElSelect
                    v-model="form.weeklyRestDays"
                    multiple
                    clearable
                    :placeholder="tr('ui.equipment.text0344')"
                  >
                    <ElOption
                      v-for="d in 7"
                      :key="d"
                      :value="d"
                      :label="tr('daily.weekday.' + d)"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem
                  :label="tr('daily.shiftOnly.monthlyRest')"
                  :error="dailyErrors.monthlyRestDays"
                >
                  <ElSelect
                    v-model="form.monthlyRestDays"
                    multiple
                    clearable
                    :placeholder="tr('ui.equipment.text0344')"
                  >
                    <ElOption
                      v-for="d in 31"
                      :key="d"
                      :value="d"
                      :label="d + tr('ui.equipment.text0352')"
                    />
                  </ElSelect>
                </ElFormItem>
                <ElFormItem :label="tr('ui.equipment.text0353')" :error="dailyErrors.holidays">
                  <ElDatePicker
                    v-model="form.holidays"
                    type="dates"
                    value-format="YYYY-MM-DD"
                    :placeholder="tr('ui.equipment.text0354')"
                /></ElFormItem>
                <ElFormItem :label="tr('daily.workdays')" :error="dailyErrors.workdays">
                  <ElDatePicker v-model="form.workdays" type="dates" value-format="YYYY-MM-DD" />
                </ElFormItem>
                <p>
                  {{ tr('daily.shiftOnly.calendarHelp') }}
                </p>
              </ElForm>
            </div>
          </section>
          <section v-show="!daily || dailyEditorTab === 'basic'" class="inspection-form-card">
            <div class="inspection-section-tools">
              <h3>
                {{ daily ? tr('ui.equipment.text0368') : tr('ui.equipment.text0369') }}
                <span>{{ form.details.length }} {{ tr('ui.equipment.text0370') }}</span>
              </h3>
              <ElButton type="primary" @click="equipmentPicker"
                ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0371') }}</ElButton
              ><ElButton @click="matchProjects">{{ tr('ui.equipment.text0372') }}</ElButton>
              <ElSwitch
                v-if="daily"
                v-model="dailyDeviceOverrides"
                :active-text="tr('daily.ui.deviceExceptions')"
              />
              <!-- <ElButton @click="importInput?.open()">导入设备</ElButton> -->
              <ElDropdown :disabled="!selectedDetails.length" @command="detailCommand"
                ><ElButton :disabled="!selectedDetails.length"
                  >{{ tr('ui.equipment.text0373') }}<AppIcon name="Down" /></ElButton
                ><template #dropdown
                  ><ElDropdownMenu
                    ><ElDropdownItem v-if="daily" command="owners">{{
                      tr('ui.equipment.text0374')
                    }}</ElDropdownItem
                    ><ElDropdownItem command="reviewers">{{
                      tr('ui.equipment.text0375')
                    }}</ElDropdownItem
                    ><ElDropdownItem divided command="delete">{{
                      tr('ui.equipment.text0146')
                    }}</ElDropdownItem></ElDropdownMenu
                  ></template
                ></ElDropdown
              >
            </div>
            <p v-if="daily" class="daily-flow-help">{{ tr('daily.ui.equipmentHelp') }}</p>
            <AppFilePicker ref="importInput" accept=".csv" hidden @select="importEquipment" />
            <div class="inspection-inline-scroll">
              <AppBusinessTable
                v-model:selection-ids="selectedDetails"
                :selection-width="48"
                selection-fixed="left"
                selection-align="center"
                selectable
                row-key="key"
                :rows="form.details"
                :pagination="false"
                :fill="false"
                :framed="false"
                class="business-table inspection-detail-table"
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="55" :width="52"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0376') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">{{
                    index + 1
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="170" class-name="detail-code"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0078') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.code
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="210" :width="190" class-name="detail-name"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0081') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.name
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="160"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0098') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }"
                    ><DepartmentText :value="eqOf(d)?.department" /></template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="150"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0091') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.model
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="190"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0108') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.functionLocation || ''
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="210" :width="260"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0377') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">
                    <DialogChoiceInput
                      :model-value="
                        d.projectRefs.length ? d.projectRefs.length + ' 个检查项目' : ''
                      "
                      :placeholder="tr('ui.equipment.text0378')"
                      :title="tr('ui.equipment.text0379')"
                      @open="pickProjects(d)"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn
                  v-if="
                    daily && (dailyDeviceOverrides || form.details.some((d) => d.owners.length))
                  "
                  :min-width="150"
                  :width="220"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0102') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">
                    <PagedChoiceField
                      v-model="d.owners"
                      multiple
                      :options="choices.users"
                      :load-rows="selector.users"
                      :title="tr('ui.equipment.text0335')"
                      :placeholder="tr('ui.equipment.text0380')"
                      :disabled="false"
                      @select="rememberPeople"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn
                  v-if="
                    !daily ||
                    form.reviewMode !== '无需验收' ||
                    form.details.some((d) => d.reviewers.length)
                  "
                  :min-width="150"
                  :width="220"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0337') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">
                    <PagedChoiceField
                      v-model="d.reviewers"
                      multiple
                      :options="choices.users"
                      :load-rows="selector.users"
                      :title="tr('ui.equipment.text0335')"
                      :placeholder="tr('ui.equipment.text0381')"
                      @select="rememberPeople"
                    /> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn
                  class-name="list-action-column"
                  fixed="right"
                  :width="170"
                  :min-width="156"
                  :resizable="false"
                  ><template #header
                    ><div class="table-header-label">
                      {{ tr('ui.equipment.text0145') }}
                    </div></template
                  ><template #default="{ row: d, $index: index }">
                    <div class="inspection-row-actions">
                      <ElButton
                        v-if="!daily"
                        class="inspection-text-button"
                        :disabled="index === 0"
                        @click="moveDetail(index, -1)"
                        type="primary"
                        link
                      >
                        {{ tr('ui.equipment.text0382') }}</ElButton
                      ><ElButton
                        v-if="!daily"
                        class="inspection-text-button"
                        :disabled="index === form.details.length - 1"
                        @click="moveDetail(index, 1)"
                        type="primary"
                        link
                      >
                        {{ tr('ui.equipment.text0383') }}</ElButton
                      ><ElButton
                        class="inspection-text-button danger"
                        @click="form.details = form.details.filter((i) => i.key !== d.key)"
                        type="danger"
                        link
                      >
                        {{ tr('ui.equipment.text0146') }}
                      </ElButton>
                    </div>
                  </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.details.length">
                    {{ tr('ui.equipment.text0384') }}
                  </div></template
                ></AppBusinessTable
              >
            </div>
          </section>
        </template>
        <section
          v-if="daily"
          v-show="dailyEditorTab === 'basic'"
          class="inspection-form-card daily-reminder-settings"
        >
          <ElCollapse>
            <ElCollapseItem name="reminders" :title="tr('daily.plan.reminders')">
              <ElForm label-width="110px" class="inspection-reminder-grid">
                <ElFormItem
                  v-for="setting in reminderSettings.filter(s=>s.key!=='expireRemindHours')"
                  :key="setting.key"
                  :label="tr(setting.label)"
                >
                  <DurationInput
                    v-model="form[setting.key]"
                    v-model:unit="form[setting.unit]"
                    :min="0"
                    :max-years="1"
                  />
                </ElFormItem>
              <ReminderPolicyField v-model:mode="form.reminderMode" v-model:id="form.reminderPolicyId" :name="form.reminderPolicyName" kind="daily" />
              </ElForm>
            </ElCollapseItem>
          </ElCollapse>
        </section>
        <DailyArrangementEditor
          v-if="daily && !loading"
          v-show="dailyEditorTab === 'arrangements'"
          v-model="form"
          :choices="choices"
          :errors="dailyErrors"
          :project-options="dailyProjectOptions"
          :can-create-team="can('新增')"
          :can-edit-team="can('编辑')"
          @people="rememberPeople"
        />
        <div v-if="!standard" v-show="!daily || dailyEditorTab === 'basic'">
          <BusinessFields
            ref="extension"
            v-model="form"
            @initialize="extensionInitializationClean = !unsaved.dirty.value"
            @ready="extensionInitializationClean && unsaved.markSaved()"
            :object="daily ? 'inspection_daily_plan' : 'inspection_periodic_plan'"
            :is-new="!form.id"
            :errors="extensionErrors"
          />
        </div>
      </div>
      <AppFormFooter
        ><template v-if="!standard" #hint>{{ tr('ui.equipment.text0386') }}</template
        ><ElButton :disabled="busy" @click="list">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">{{
          tr('ui.equipment.text0159')
        }}</ElButton></AppFormFooter
      >
    </template>
    <InspectionPlanPreviewDialog
      v-model="planPreviewOpen"
      :plan-id="previewPlanId"
      :kind="daily ? 'daily-plans' : 'periodic-plans'"
    />
    <InspectionTaskDeleteDialog
      v-model="taskDeleteOpen"
      :task="taskToDelete"
      @deleted="taskDeleted"
    />
    <FilterConditionPopover
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      v-model="advancedOpen"
      :conditions="conditions"
      @confirm="confirmFilter"
      :fields="filterFields"
    />
    <AppDialog v-model="equipmentOpen" :title="tr('ui.equipment.text0220')" width="1000px"
      ><div class="inspection-picker-toolbar">
        <ElButton text @click="equipmentSelectedOnly = !equipmentSelectedOnly"
          >{{
            equipmentSelectedOnly ? tr('ui.equipment.text0387') : tr('ui.equipment.text0388')
          }}（{{ pickedEquipment.length }}）</ElButton
        >
        <ElInput
          v-model="equipmentSearch"
          :placeholder="tr('ui.equipment.text0389')"
          clearable
        /><TreeSelect
          v-model="equipmentCategory"
          :nodes="choices.categories.map((c) => ({ ...c, parentId: c.parentId || '' }))"
          :placeholder="tr('ui.equipment.text0390')"
        />
      </div>
      <div class="inspection-picker-scroll">
        <AppBusinessTable
          v-model:selection-ids="pickedEquipment"
          :selection-width="48"
          selection-fixed="left"
          selection-align="center"
          selectable
          :can-select-row="(e) => !form.details.some((d) => d.equipmentId === e.id)"
          :rows="equipmentVisible"
          :loading="equipmentLoading"
          :error="equipmentError"
          @retry="loadEquipmentPage"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table inspection-picker-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0078') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.code
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="210"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0081') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0091') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.model
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0098') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }"
              ><DepartmentText :value="e.department" /></template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0083') }}</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.category
            }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </div>
      <div v-if="!equipmentSelectedOnly" class="equipment-selector-pagination">
        <span
          >{{ tr('ui.equipment.text0309') }} {{ equipmentTotal }}
          {{ tr('ui.equipment.text0296') }}</span
        ><AppPagination
          v-model:page="equipmentPage"
          v-model:page-size="equipmentSize"
          :total="equipmentTotal"
        />
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="equipmentOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" @click="addEquipment"
            >{{ tr('ui.equipment.text0391') }}{{ pickedEquipment.length }}）</ElButton
          >
        </div></template
      ></AppDialog
    >
    <AppDialog v-model="projectOpen" :title="tr('ui.equipment.text0379')" width="1120px"
      ><div class="inspection-picker-toolbar">
        <ElCheckbox v-model="showAllStandards">{{ tr('ui.equipment.text0392') }}</ElCheckbox
        ><ElInput
          v-model="projectSearch"
          :placeholder="tr('ui.equipment.text0393')"
          clearable
        /><span
          >{{ tr('ui.equipment.text0295') }} {{ projectRefs.length }}
          {{ tr('ui.equipment.text0394') }}</span
        >
      </div>
      <div class="inspection-project-dialog">
        <aside>
          <ListSearchInput v-model="standardSearch" :placeholder="tr('ui.equipment.text0395')" />
          <p v-if="standardLoading">{{ tr('ui.equipment.text0396') }}</p>
          <p v-else-if="standardError">
            {{ standardError
            }}<ElButton link type="primary" @click="reloadStandards">{{
              tr('ui.equipment.text0299')
            }}</ElButton>
          </p>
          <ElButton
            v-for="st in projectStandards"
            :key="st.id"
            :class="{ active: projectStandardId === st.id }"
            @click="projectStandardId = st.id"
            ><strong>{{ st.name }}</strong
            ><span>{{ st.code }}</span></ElButton
          >
          <p v-if="!standardLoading && !standardError && !projectStandards.length">
            {{ tr('ui.equipment.text0397') }}
          </p>
          <div class="standard-picker-page">
            <span
              >{{ tr('ui.equipment.text0309') }} {{ standardTotal }}
              {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="standardPage"
              v-model:page-size="standardSize"
              :total="standardTotal"
            />
          </div>
        </aside>
        <div class="inspection-picker-scroll">
          <AppBusinessTable
            v-model:selection-ids="projectRefs"
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            selectable
            row-key="_selectionKey"
            :rows="
              projectItems.map((i) => ({ ...i, _selectionKey: projectStandardId + '/' + i.id }))
            "
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table inspection-picker-table"
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="210"
              ><template #header
                ><div class="table-header-label">{{ tr('ui.equipment.text0398') }}</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header
                ><div class="table-header-label">{{ tr('ui.equipment.text0399') }}</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.content
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header
                ><div class="table-header-label">{{ tr('ui.equipment.text0325') }}</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                types.find((t) => t.id === i.typeId)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header
                ><div class="table-header-label">{{ tr('ui.equipment.text0400') }}</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.valueType
              }}</template></ElTableColumn
            ><template #empty
              ><div v-if="!projectItems.length">{{ tr('ui.equipment.text0401') }}</div></template
            ></AppBusinessTable
          >
        </div>
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="projectOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" @click="applyProjects">{{
            tr('ui.equipment.text0402')
          }}</ElButton>
        </div></template
      ></AppDialog
    >
    <AppDialog
      v-model="detailBatchOpen"
      :title="detailBatch === 'owners' ? tr('ui.equipment.text0374') : tr('ui.equipment.text0375')"
      ><PagedChoiceField
        v-model="batchUsers"
        multiple
        :options="choices.users"
        :load-rows="selector.users"
        :title="tr('ui.equipment.text0335')"
        :placeholder="tr('ui.equipment.text0403')"
        @select="rememberPeople"
      /><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="detailBatchOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" @click="applyDetailBatch">{{
            tr('ui.equipment.text0402')
          }}</ElButton>
        </div></template
      ></AppDialog
    >
    <AppDialog v-model="typeOpen" :title="tr('ui.equipment.text0325')" width="760px" :busy="busy">
      <div class="inspection-type-list">
        <AppBusinessTable
          :rows="types"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table inspection-picker-table inspection-type-table"
          :row-class="(t, tableRowIndex) => ({ 'is-editing': typeForm.id === t.id })"
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn :min-width="210"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0404') }}</div></template
            ><template #default="{ row: t, $index: tableRowIndex }">{{
              t.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn :min-width="150" :width="100"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0405') }}</div></template
            ><template #default="{ row: t, $index: tableRowIndex }"
              >{{ t.period }} {{ t.unit }}</template
            ></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn :min-width="150" :width="90"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0406') }}</div></template
            ><template #default="{ row: t, $index: tableRowIndex }">{{
              t.sortOrder ?? 0
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
            ><template #header
              ><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
            ><template #default="{ row: t, $index: tableRowIndex }">
              <div class="inspection-row-actions">
                <ElButton
                  class="inspection-text-button"
                  :disabled="busy"
                  v-if="auth.can('inspectionType', '编辑')"
                  @click="typeForm = { ...emptyInspection(), ...t }"
                  native-type="button"
                  type="primary"
                  link
                >
                  {{ tr('ui.equipment.text0207') }}
                </ElButton>
                <ElButton
                  class="inspection-text-button danger"
                  :disabled="busy"
                  v-if="auth.can('inspectionType', '删除')"
                  @click="deleteType(t)"
                  native-type="button"
                  type="danger"
                  link
                >
                  {{ tr('ui.equipment.text0146') }}
                </ElButton>
              </div>
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!types.length">{{ tr('ui.equipment.text0407') }}</div></template
          ></AppBusinessTable
        >
      </div>
      <div class="inspection-type-form">
        <label
          ><span class="required">{{ tr('ui.equipment.text0408') }}</span
          ><ElInput
            v-model="typeForm.name"
            :aria-label="tr('ui.equipment.text0409')"
            :placeholder="tr('ui.equipment.text0410')"
            maxlength="150"
            :disabled="busy"
        /></label>
        <label
          ><span class="required">{{ tr('ui.equipment.text0326') }}</span>
          <div class="inspection-type-period">
            <ElInputNumber
              v-model="typeForm.period"
              :aria-label="tr('ui.equipment.text0411')"
              :min="1"
              :precision="0"
              controls-position="right"
              :disabled="busy"
            />
            <ElSelect
              v-model="typeForm.unit"
              :aria-label="tr('ui.equipment.text0412')"
              :disabled="busy"
              ><ElOption v-for="u in ['日', '周', '月', '年']" :key="u" :value="u" :label="u"
            /></ElSelect></div
        ></label>
        <label
          ><span>{{ tr('ui.equipment.text0406') }}</span
          ><ElInputNumber
            v-model="typeForm.sortOrder"
            :aria-label="tr('ui.equipment.text0413')"
            :min="0"
            :max="999999"
            :precision="0"
            controls-position="right"
            :disabled="busy"
          /><small class="inspection-type-sort-hint">{{
            tr('ui.equipment.text0414')
          }}</small></label
        >
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton
            :disabled="busy"
            @click="
              typeForm.id ? (typeForm = { ...emptyInspection(), unit: '日' }) : (typeOpen = false)
            "
            >{{ typeForm.id ? tr('ui.equipment.text0415') : tr('ui.equipment.text0076') }}</ElButton
          >
          <ElButton
            v-if="auth.can('inspectionType', typeForm.id ? '编辑' : '新增')"
            type="primary"
            :loading="busy"
            @click="saveType"
            >{{ typeForm.id ? tr('ui.equipment.text0159') : tr('ui.equipment.text0149') }}</ElButton
          >
        </div></template
      >
    </AppDialog>
    <InspectionReviewDialog
      v-model="reviewOpen"
      :count="selected.length"
      :busy="busy"
      @submit="batchReview"
    />
    <AppDialog v-model="exemptionOpen" :title="tr('daily.rangeExemption')" :busy="busy"
      ><p>{{ tr('daily.exemptionHelp') }}</p>
      <PagedChoiceField
        v-model="exemptionEquipment"
        multiple
        :options="exemptionOptions"
        :load-rows="exemptionPage"
        :title="tr('daily.equipment')"
      /><ElDatePicker
        v-model="exemptionRange"
        type="datetimerange"
        value-format="YYYY-MM-DDTHH:mm:ss"
      /><ElInput
        v-model="exemptionReason"
        type="textarea"
        :rows="3"
        maxlength="1000"
        :placeholder="tr('daily.reason')"
      />
      <p v-if="exemptionError" class="inspection-error">{{ exemptionError }}</p>
      <template #footer
        ><ElButton @click="exemptionOpen = false">{{ tr('daily.cancel') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="approveExemption">{{
          tr('daily.exempt')
        }}</ElButton></template
      ></AppDialog
    >
  </section>
</template>
<style src="../styles/inspection.css"></style>
<style scoped>
.task-list-layout {
  display: flex;
  flex: 1;
  gap: 12px;
  min-width: 0;
  min-height: 0;
}
.task-list-content {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
  /* gap: 8px; */
}
</style>

<style scoped>
.standard-picker-page {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  align-items: center;
  margin-top: 12px;
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
