<script setup lang="ts">
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
function persistTableWidths() {
  localStorage.setItem('eam-inspection-widths-' + kind.value, JSON.stringify(widths.value));
}
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
  csvRows,
  downloadCsv,
  type InspectionKind,
  type InspectionDefinition,
  type InspectionTask,
  type InspectionDetail,
  type InspectionChoices,
  localTime,
} from '../inspection';
import type { MaintenanceItem } from '../maintenance';
import { useAuthStore } from '../stores/auth';
import { confirmAction } from '../services/dialog';
import AppPagination from './common/AppPagination.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppDialog from './common/AppDialog.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import InspectionProjectEditor from './common/InspectionProjectEditor.vue';
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
  size = ref(10),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  advancedOpen = ref(false);
const columnFilters = ref<Record<string, string>>({}),
  conditions = ref<FilterCondition[]>([]),
  selected = ref<string[]>([]),
  visibleColumns = ref<string[]>([]),
  sortKey = ref(''),
  sortAsc = ref(true);
const widths = ref<Record<string, number>>({});
const fields = computed<[string, string][]>(() =>
  isTasks.value
    ? [
        ['code', '任务编号'],
        ['planName', '计划名称'],
        ['equipmentName', '设备名称'],
        ...(daily.value
          ? ([
              ['shiftName', '班次'],
              ['shiftDate', '班次日期'],
            ] as [string, string][])
          : ([['typeName', '巡检类型']] as [string, string][])),
        ['status', '状态'],
        ['dueAt', '计划开始时间'],
        ['expiresAt', '计划结束时间'],
        ['ownerNames', '负责人'],
        ['ownerName', '实际点检人'],
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
              ['startDate', '开始日期'],
              ['endDate', '结束日期'],
              ...(daily.value
                ? [
                    ['personMode', '点检人设置'],
                    ['shiftCount', '班次数'],
                  ]
                : [
                    ['periodLabel', '巡检周期'],
                    ['nextAt', '下次巡检时间'],
                  ]),
            ] as [string, string][])),
        ['remark', '说明'],
        ['createdAt', '创建时间'],
      ],
);
const shownFields = computed(() =>
  fields.value.filter(([key]) => visibleColumns.value.includes(key)),
);
const filterFields = computed<FilterField[]>(() =>
  fields.value.map(([key, label]) => ({
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
    dateOnly: key.endsWith('Date'),
  })),
);
const people = (ids: string[]) =>
  ids.map((id) => choices.value.users.find((u) => u.id === id)?.name || id).join('、');
function value(row: InspectionDefinition | InspectionTask, key: string): string | number {
  const r = row as any;
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
  return r[key] ?? '';
}
const organizationFilter = ref('');
const taskOrganizations = ref<InspectionChoices['organizations']>([]);
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
  let result = records.value.filter((r) => {
    if (isTasks.value) {
      if (!matchesOrganization(r)) return false;
      const t = r as InspectionTask;
      if (
        queue.value === '我的待执行' &&
        !(t.canExecute && ['未开始', '待执行', '执行中', '已过期', '审核不通过'].includes(t.status))
      )
        return false;
      if (queue.value === '我的待审核' && !(t.canReview && t.status === '待审核')) return false;
    }
    if (
      search.value &&
      !fields.value.some(([key]) =>
        String(value(r, key)).toLowerCase().includes(search.value.toLowerCase()),
      )
    )
      return false;
    if (
      !Object.entries(columnFilters.value).every(
        ([key, term]) => !term || String(value(r, key)).toLowerCase().includes(term.toLowerCase()),
      )
    )
      return false;
    return matchesAdvanced(conditions.value, (key) => value(r, key));
  });
  if (sortKey.value)
    result = [...result].sort(
      (a, b) =>
        String(value(a, sortKey.value)).localeCompare(String(value(b, sortKey.value)), 'zh-CN', {
          numeric: true,
        }) * (sortAsc.value ? 1 : -1),
    );
  return result;
});
const pageRows = computed(() =>
  filtered.value.slice((page.value - 1) * size.value, page.value * size.value),
);
watch(
  [search, queue, columnFilters, conditions, size],
  () => {
    page.value = 1;
    selected.value = [];
  },
  { deep: true },
);
watch(
  visibleColumns,
  () =>
    localStorage.setItem(
      'eam-inspection-columns-' + kind.value,
      JSON.stringify(visibleColumns.value),
    ),
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
  if (!editing.value) {
    try {
      const saved: string[] = JSON.parse(
        localStorage.getItem('eam-inspection-columns-' + kind.value) || '[]',
      );
      visibleColumns.value = saved.length
        ? saved
        : fields.value.slice(0, isTasks.value ? 10 : fields.value.length).map(([k]) => k);
    } catch {
      visibleColumns.value = fields.value.map(([k]) => k);
    }
  }
  error.value = '';
  try {
    const [choiceResult, standardResult, typeResult] = await Promise.all([
      inspectionApi.choices(),
      isTasks.value || !auth.can('inspectionStandard')
        ? Promise.resolve({ data: [] as InspectionDefinition[] })
        : inspectionApi.list('standards'),
      isTasks.value || !auth.can('inspectionType')
        ? Promise.resolve({ data: [] as InspectionDefinition[] })
        : inspectionApi.list('types'),
    ]);
    if (version !== loadVersion) return;
    choices.value = choiceResult;
    standards.value = standardResult.data;
    types.value = typeResult.data;
    if (editing.value) {
      const id = String(route.params.id || route.query.copy || '');
      form.value = id
        ? { ...emptyInspection(), ...(await inspectionApi.get(kind.value, id)).data }
        : emptyInspection();
      if (route.query.copy) {
        form.value.id = '';
        delete form.value.rowVersion;
        form.value.code = '';
        form.value.name += '（副本）';
      }
      if (!form.value.departmentId) form.value.departmentId = auth.user?.orgId || '';
      if (!form.value.typeId)
        form.value.typeId =
          types.value.find((t) => t.unit === '周' && t.period === 1)?.id ||
          types.value[0]?.id ||
          '';
      if (!id && kind.value === 'periodic-plans') typeChange();
      if (!form.value.owners.length && auth.user) form.value.owners = [auth.user.id];
      if (!form.value.items) form.value.items = [];
      selectedDetails.value = [];
      unsaved.markSaved();
    } else {
      if (isTasks.value) {
        const result = await inspectionApi.tasks(daily.value ? 'daily' : 'periodic');
        if (version !== loadVersion) return;
        records.value = result.data;
        taskOrganizations.value = result.organizations;
      } else {
        records.value = (await inspectionApi.list(kind.value)).data;
      }
      let saved: string[] = [];
      try {
        saved = JSON.parse(localStorage.getItem('eam-inspection-columns-' + kind.value) || '[]');
        widths.value = JSON.parse(
          localStorage.getItem('eam-inspection-widths-' + kind.value) || '{}',
        );
      } catch {}
      visibleColumns.value = saved.length
        ? saved
        : fields.value.slice(0, isTasks.value ? 10 : fields.value.length).map(([k]) => k);
    }
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    if (version === loadVersion) loading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    keyword.value = '';
    search.value = '';
    queue.value = '全部';
    conditions.value = [];
    columnFilters.value = {};
    selected.value = [];
    page.value = 1;
    load();
  },
);
let timer: ReturnType<typeof setInterval>;
onMounted(() => {
  load();
  timer = setInterval(() => {
    if (!editing.value && isTasks.value) load();
  }, 60000);
});
onUnmounted(() => clearInterval(timer));
function list() {
  router.push({ name: 'inspection', params: { kind: kind.value } });
}
async function save() {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await inspectionApi.save(kind.value, form.value);
    unsaved.markSaved();
    ElMessage.success('已保存');
    busy.value = false;
    list();
    window.dispatchEvent(new Event('work-inbox-changed'));
  } catch (e) {
    error.value = e instanceof Error ? e.message : '保存失败';
  } finally {
    busy.value = false;
  }
}
function open(row?: InspectionDefinition | InspectionTask) {
  if (isTasks.value) {
    router.push({ name: 'inspection-task', params: { id: row!.id }, query: { kind: kind.value } });
    return;
  }
  router.push({
    name: row ? 'inspection-edit' : 'inspection-new',
    params: { kind: kind.value, ...(row ? { id: row.id } : {}) },
  });
}
function copy(row: InspectionDefinition | InspectionTask) {
  router.push({ name: 'inspection-new', params: { kind: kind.value }, query: { copy: row.id } });
}
async function remove(row: InspectionDefinition | InspectionTask) {
  if (!(await confirmAction('删除此记录？'))) return;
  try {
    await inspectionApi.remove(kind.value, row.id);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败');
  }
}
async function exportList() {
  try {
    const ids = filtered.value.map((r) => r.id);
    const rows = (await inspectionApi.export(kind.value, ids)).data;
    const byId = new Map(rows.map((r) => [r.id, r]));
    downloadCsv(inspectionLabels[kind.value], [
      fields.value.map(([, label]) => label),
      ...ids.map((id) =>
        fields.value.map(([key]) => String(value(byId.get(id)!, key)).replace('T', ' ')),
      ),
    ]);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败');
  }
}
function resize(event: MouseEvent, key: string) {
  event.preventDefault();
  event.stopPropagation();
  const start = event.clientX,
    width =
      (event.currentTarget as HTMLElement).parentElement?.getBoundingClientRect().width || 160;
  const move = (e: MouseEvent) => (widths.value[key] = Math.max(90, width + e.clientX - start));
  const stop = () => {
    window.removeEventListener('mousemove', move);
    window.removeEventListener('mouseup', stop);
    localStorage.setItem('eam-inspection-widths-' + kind.value, JSON.stringify(widths.value));
  };
  window.addEventListener('mousemove', move);
  window.addEventListener('mouseup', stop);
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
function typeChange() {
  const t = types.value.find((t) => t.id === form.value.typeId);
  if (t) {
    form.value.period = t.period;
    form.value.unit = t.unit;
    form.value.validHours = t.unit === '年' ? t.period * 12 : t.period;
    form.value.validUnit = t.unit === '年' ? '月' : t.unit;
  }
}
function toTree(nodes: { id: string; name: string; parentId?: string }[]): any[] {
  const build = (parent: string): any[] =>
    nodes
      .filter(
        (n) =>
          n.parentId === parent ||
          (!parent && (!n.parentId || !nodes.some((p) => p.id === n.parentId))),
      )
      .map((n) => ({ value: n.name, label: n.name, children: build(n.id) }));
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
const equipmentOptions = computed(() =>
  choices.value.equipment.filter(
    (e) =>
      (!equipmentSearch.value ||
        [e.code, e.name, e.model].some((v) => v?.includes(equipmentSearch.value))) &&
      (!equipmentCategory.value || e.category === equipmentCategory.value),
  ),
);
function equipmentPicker() {
  pickedEquipment.value = [];
  equipmentSearch.value = '';
  equipmentOpen.value = true;
}
function applicable(st: InspectionDefinition, eq: InspectionChoices['equipment'][number]) {
  if (!st.equipmentIds.length && !st.categoryNames.length) return true;
  if (st.equipmentIds.includes(eq.id)) return true;
  const seen = new Set<string>();
  let c = choices.value.categories.find((c) => c.name === eq.category);
  while (c && !seen.has(c.id)) {
    if (st.categoryNames.includes(c.name)) return true;
    seen.add(c.id);
    c = choices.value.categories.find((p) => p.id === c!.parentId);
  }
  return st.categoryNames.includes(eq.category || '');
}
function refsFor(eq: InspectionChoices['equipment'][number]) {
  return standards.value
    .filter((st) => applicable(st, eq) && st.enabled !== false)
    .flatMap((st) => st.items.map((item) => ({ standardId: st.id, itemId: item.id })));
}
function addEquipment() {
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
const projectStandards = computed(() =>
  standards.value.filter(
    (st) =>
      showAllStandards.value ||
      (projectRow.value && eqOf(projectRow.value) && applicable(st, eqOf(projectRow.value)!)),
  ),
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
function matchProjects() {
  const targets = selectedDetails.value.length
    ? form.value.details.filter((d) => selectedDetails.value.includes(d.key))
    : form.value.details;
  targets.forEach((d) => {
    const eq = eqOf(d);
    if (eq) d.projectRefs = refsFor(eq);
  });
  ElMessage.success('已按设备及类别匹配检查项目');
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
    ElMessage.warning('请输入巡检类型名称');
    return;
  }
  if (!Number.isInteger(typeForm.value.period) || typeForm.value.period < 1) {
    ElMessage.warning('巡检周期需为大于零的整数');
    return;
  }
  if (
    !Number.isInteger(typeForm.value.sortOrder) ||
    typeForm.value.sortOrder < 0 ||
    typeForm.value.sortOrder > 999999
  ) {
    ElMessage.warning('排序需为 0～999999 的整数');
    return;
  }
  busy.value = true;
  try {
    await inspectionApi.save('types', typeForm.value);
    types.value = (await inspectionApi.list('types')).data;
    typeForm.value = { ...emptyInspection(), unit: '日' };
    ElMessage.success('巡检类型已保存');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    busy.value = false;
  }
}
async function deleteType(type: InspectionDefinition) {
  if (!(await confirmAction(`删除巡检类型“${type.name}”？`))) return;
  try {
    await inspectionApi.remove('types', type.id);
    types.value = (await inspectionApi.list('types')).data;
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '删除失败');
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
    if (index < 0) throw new Error('CSV 需包含“设备编号”或“设备编号”表头');
    const ids = data.slice(1).map((r) => {
      const eq = choices.value.equipment.find((e) => e.code === r[index]);
      if (!eq) throw new Error(`设备不存在或无权限：${r[index]}`);
      return eq.id;
    });
    pickedEquipment.value = ids;
    addEquipment();
    ElMessage.success('设备已导入并匹配检查项目');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败');
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
    if (Math.min(date, shift, account) < 0) throw new Error('CSV 表头需要：日期、班次、人员账号');
    const result = data.slice(1).map((r) => {
      const s = form.value.shifts.find((s) => s.name === r[shift]);
      if (!s) throw new Error(`班次不存在：${r[shift]}`);
      const users = (r[account] || '').split(/[、;；]/).map((v) => {
        const u = choices.value.users.find((u) => u.account === v.trim());
        if (!u) throw new Error(`人员账号不存在：${v}`);
        return u.id;
      });
      if (!/^\d{4}-\d{2}-\d{2}$/.test(r[date] || '')) throw new Error('日期格式为 YYYY-MM-DD');
      return { key: uuid(), date: r[date]!, shiftId: s.id, users };
    });
    form.value.roster.push(...result);
    ElMessage.success('排班已导入');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导入失败');
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
    ElMessage.warning('批量审核只支持您可审核的整机审核任务，逐项审核请进入详情');
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
    ElMessage.error(e instanceof Error ? e.message : '审核失败');
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
  <section class="inspection-page" :class="{ 'inspection-editor': editing }">
    <div v-if="!editing" class="task-list-layout" :class="{ 'with-organizations': isTasks }">
      <TaskOrganizationFilter
        v-if="isTasks"
        v-model="organizationFilter"
        :organizations="taskOrganizations"
        :scope-by-records="false"
        :records="records"
      />
      <div class="task-list-content">
        <div v-if="isTasks" class="inspection-queue">
          <ElButton
            v-for="q in ['全部', '我的待执行', '我的待审核']"
            :key="q"
            :class="{ active: queue === q }"
            @click="queue = q"
            >{{ q }}</ElButton
          >
        </div>
        <AppListToolbar class="inspection-toolbar">
          <div class="inspection-toolbar-actions">
            <ElButton v-if="!isTasks && can('新增')" type="primary" @click="open()"
              ><AppIcon name="Plus" /> 新增</ElButton
            ><ElButton v-if="isTasks" :disabled="!selected.length" @click="openBatchReview"
              >审核</ElButton
            ><span v-if="selected.length">已选 {{ selected.length }} 条</span>
          </div>
          <form class="inspection-search" @submit.prevent="search = keyword.trim()">
            <ListToolbarIcons
              v-if="!isTasks"
              mode="filter"
              :filter-open="filterOpen"
              :columns-open="false"
              @toggle-filter="filterOpen = !filterOpen"
            /><ListSearchInput
              v-model="keyword"
              :placeholder="
                isTasks ? '搜索任务编号、计划、设备、负责人' : '搜索编号、名称、所属部门'
              "
            /><ElButton
              v-if="isTasks"
              :class="{ active: conditions.length }"
              @click="showFilter($event.currentTarget as HTMLElement)"
              >添加筛选<span v-if="conditions.length">（{{ conditions.length }}）</span></ElButton
            ><ElButton @click="reset">重置</ElButton>
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
          {{ error }} <ElButton text @click="load">重试</ElButton>
        </p>
        <div class="inspection-list-card">
          <div class="inspection-list-scroll">
            <AppBusinessTable
              v-model:selection-ids="selected"
              :selection-width="48"
              selection-fixed="left"
              selection-align="center"
              :selectable="isTasks"
              :sortable="true"
              @sort-change="onTableSort"
              :rows="pageRows"
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
                :sortable="true ? 'custom' : false"
                ><template #header
                  ><span class="business-column-label" :title="label">{{ label }}</span>
                  <div v-if="filterOpen && !isTasks" class="table-column-filter" @click.stop>
                    <ElSelect
                      v-if="key === 'status'"
                      v-model="columnFilters[key]"
                      clearable
                      placeholder="全部"
                      ><ElOption v-for="s in inspectionStates" :key="s" :value="s" /></ElSelect
                    ><ElInput
                      v-else
                      v-model="columnFilters[key]"
                      clearable
                      placeholder="筛选"
                    /></div></template
                ><template #default="{ row: r, $index: tableRowIndex }">
                  <ElButton
                    v-if="key === 'code'"
                    class="inspection-text-button"
                    @click="open(r)"
                    type="primary"
                    link
                    >{{ value(r, key) }}</ElButton
                  ><MaintenanceStatus
                    v-else-if="key === 'status'"
                    :status="String(value(r, key))"
                  /><span
                    v-else-if="key === 'enabled'"
                    class="inspection-enabled"
                    :class="{ disabled: !(r as InspectionDefinition).enabled }"
                    >{{ value(r, key) }}</span
                  ><span v-else :title="String(value(r, key))">{{
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
                ><template #header><div class="table-header-label">操作</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">
                  <div class="inspection-row-actions">
                    <template v-if="isTasks"
                      ><ElButton
                        class="inspection-text-button"
                        @click="open(r)"
                        type="primary"
                        link
                        >{{
                          (r as InspectionTask).canReview &&
                          (r as InspectionTask).status === '待审核'
                            ? '审核'
                            : (r as InspectionTask).canExecute &&
                                ['未开始', '待执行', '执行中', '审核不通过', '已过期'].includes(
                                  (r as InspectionTask).status,
                                )
                              ? '执行'
                              : '查看'
                        }}</ElButton
                      ></template
                    ><template v-else
                      ><ElButton
                        v-if="can('编辑')"
                        class="inspection-text-button"
                        @click="open(r)"
                        type="primary"
                        link
                      >
                        编辑</ElButton
                      ><ElButton
                        v-if="can('新增')"
                        class="inspection-text-button"
                        @click="copy(r)"
                        type="primary"
                        link
                      >
                        复制</ElButton
                      ><RowMoreActions
                        v-if="can('删除') && can('编辑') && can('新增')"
                        :actions="[{ label: '删除', danger: true, run: () => remove(r) }]"
                      /><ElButton
                        v-else-if="can('删除')"
                        class="inspection-text-button danger"
                        @click="remove(r)"
                        type="danger"
                        link
                      >
                        删除
                      </ElButton></template
                    >
                  </div>
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!pageRows.length">
                  {{ loading ? '正在加载…' : '暂无数据' }}
                </div></template
              ></AppBusinessTable
            >
          </div>
          <footer class="inspection-list-footer">
            <span>共 {{ filtered.length }} 条</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="filtered.length"
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
        ><span>/</span><strong>{{ form.id ? '编辑' : '新增' }}</strong
        ><span class="inspection-heading-spacer" /><ElButton text @click="list">关闭</ElButton>
      </header>
      <div class="inspection-editor-scroll">
        <p v-if="error" class="inspection-error">{{ error }}</p>
        <section class="inspection-form-card">
          <div class="inspection-form-grid">
            <label
              ><span>{{ standard ? '标准编号' : '计划编号' }}</span
              ><ElInput v-model="form.code" placeholder="留空自动生成" maxlength="64" /></label
            ><label
              ><span><b>*</b>{{ standard ? '标准名称' : '计划名称' }}</span
              ><ElInput v-model="form.name" maxlength="150" /></label
            ><label
              ><span><b>*</b>所属部门</span
              ><TreeSelect
                v-model="form.departmentId"
                :nodes="departmentNodes"
                placeholder="请选择部门"
            /></label>
            <template v-if="standard"
              ><label class="wide"
                ><span>要求</span
                ><ElInput
                  v-model="form.requirement"
                  type="textarea"
                  :rows="2"
                  placeholder="说明检查要求" /></label
              ><label class="wide"
                ><span>适用设备类别</span
                ><ElTreeSelect
                  v-model="form.categoryNames"
                  :data="toTree(choices.categories)"
                  multiple
                  check-strictly
                  filterable
                  clearable
                  placeholder="不限制类别" /></label
              ><label class="wide"
                ><span>适用设备</span
                ><ElSelect v-model="form.equipmentIds" multiple filterable placeholder="不限制设备"
                  ><ElOption
                    v-for="e in choices.equipment"
                    :key="e.id"
                    :value="e.id"
                    :label="e.code + ' · ' + e.name" /></ElSelect></label
            ></template>
            <template v-else
              ><label class="span-two"
                ><span><b>*</b>计划日期</span
                ><ElDatePicker
                  v-model="dateRange"
                  type="daterange"
                  value-format="YYYY-MM-DD"
                  start-placeholder="开始日期"
                  end-placeholder="结束日期"
                  range-separator="至" /></label
              ><label><span>启用</span><ElSwitch v-model="form.enabled" /></label>
              <template v-if="!daily"
                ><label
                  ><span><b>*</b>巡检类型</span
                  ><ElSelect v-model="form.typeId" @change="typeChange"
                    ><ElOption
                      v-for="t in types"
                      :key="t.id"
                      :value="t.id"
                      :label="t.name" /></ElSelect></label
                ><label
                  ><span><b>*</b>巡检周期</span>
                  <div class="inspection-number-unit">
                    <ElInputNumber
                      v-model="form.period"
                      :min="1"
                      :max="10000"
                      controls-position="right"
                    /><ElSelect v-model="form.unit"
                      ><ElOption v-for="u in ['日', '周', '月', '年']" :key="u" :value="u"
                    /></ElSelect></div></label
                ><label
                  ><span><b>*</b>下次巡检时间</span
                  ><ElDatePicker
                    v-model="form.nextAt"
                    type="datetime"
                    value-format="YYYY-MM-DDTHH:mm:ss" /></label></template
              ><label
                ><span>提前生成</span>
                <DurationInput
                  v-model="form.advanceHours"
                  v-model:unit="form.advanceUnit"
                  :min="0"
                  :max-years="1"
              /></label>
              <label
                ><span>任务有效时长</span>
                <DurationInput
                  v-model="form.validHours"
                  v-model:unit="form.validUnit"
                  :min="1"
                  :max-years="10" /></label
              ><label
                ><span>开始前提醒</span>
                <DurationInput
                  v-model="form.remindHours"
                  v-model:unit="form.remindUnit"
                  :min="0"
                  :max-years="1" /></label
              ><label
                ><span>过期前提醒</span>
                <DurationInput
                  v-model="form.expireRemindHours"
                  v-model:unit="form.expireRemindUnit"
                  :min="0"
                  :max-years="1"
              /></label>
              <label v-if="daily"
                ><span>点检人设置</span
                ><ElSelect v-model="form.personMode"
                  ><ElOption
                    v-for="v in ['任何人可点检', '指定负责人', '按照排班表']"
                    :key="v"
                    :value="v" /></ElSelect></label
              ><label
                ><span>结果审核</span
                ><ElSelect v-model="form.reviewMode"
                  ><ElOption
                    v-for="v in ['无需审核', '整机审核', '逐项审核']"
                    :key="v"
                    :value="v" /></ElSelect></label
              ><label
                ><span>设备校验</span
                ><ElSelect v-model="form.scanMode"
                  ><ElOption
                    v-for="v in ['无需扫码', '逐台校验', '校验一台即可提交']"
                    :key="v"
                    :value="v" /></ElSelect
              ></label>
              <label class="span-two" v-if="!daily || form.personMode !== '任何人可点检'"
                ><span>{{ form.personMode === '按照排班表' ? '未排班负责人' : '负责人' }}</span
                ><ElSelect v-model="form.owners" multiple filterable
                  ><ElOption
                    v-for="u in choices.users"
                    :key="u.id"
                    :value="u.id"
                    :label="u.name + ' · ' + u.department" /></ElSelect></label
              ><label v-if="form.reviewMode !== '无需审核'" class="span-two"
                ><span><b>*</b>审核人</span
                ><ElSelect v-model="form.reviewers" multiple filterable
                  ><ElOption
                    v-for="u in choices.users"
                    :key="u.id"
                    :value="u.id"
                    :label="u.name + ' · ' + u.department" /></ElSelect
              ></label>
              <div class="inspection-flags wide">
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
              </div>
            </template>
            <label class="wide"
              ><span>说明</span><ElInput v-model="form.remark" type="textarea" :rows="2" /></label
            ><label v-if="standard"><span>启用</span><ElSwitch v-model="form.enabled" /></label>
          </div>
        </section>
        <InspectionProjectEditor
          v-if="standard"
          v-model="form.items"
          :types="types"
          @add-type="newType"
        />
        <template v-else>
          <section v-if="daily" class="inspection-form-card">
            <div class="inspection-section-tools">
              <h3>工作日与班次</h3>
              <ElButton
                @click="
                  form.shifts.push({
                    id: uuid(),
                    name: '',
                    start: '08:00:00',
                    end: '20:00:00',
                  })
                "
                ><AppIcon name="Plus" /> 新增班次</ElButton
              >
            </div>
            <div class="inspection-calendar-grid">
              <div class="inspection-calendar-fields">
                <label
                  ><span>休息日设置</span
                  ><ElRadioGroup v-model="form.restCycle" @change="form.restDays = []"
                    ><ElRadioButton value="按周">按周</ElRadioButton
                    ><ElRadioButton value="按月">按月</ElRadioButton></ElRadioGroup
                  ></label
                ><label
                  ><span>不开班日期</span
                  ><ElSelect v-model="form.restDays" multiple placeholder="选择休息日"
                    ><ElOption
                      v-for="d in form.restCycle === '按周' ? 7 : 31"
                      :key="d"
                      :value="d"
                      :label="
                        form.restCycle === '按周'
                          ? ['周一', '周二', '周三', '周四', '周五', '周六', '周日'][d - 1]
                          : d + '日'
                      " /></ElSelect></label
                ><label
                  ><span>节假日</span
                  ><ElDatePicker
                    v-model="form.holidays"
                    type="dates"
                    value-format="YYYY-MM-DD"
                    placeholder="选择不开班的日期"
                /></label>
                <p>
                  班次跨午夜时，结束时间计入次日。默认在班次开始时生成任务；提前生成或开始前提醒可提前创建未开始任务。
                </p>
              </div>
              <div class="inspection-inline-scroll">
                <AppBusinessTable
                  :rows="form.shifts"
                  :pagination="false"
                  :fill="false"
                  :framed="false"
                  class="business-table inspection-shift-table"
                  ><!-- @vue-generic {TableColumnRow2} -->
                  <ElTableColumn :min-width="210"
                    ><template #header><div class="table-header-label">班次名称</div></template
                    ><template #default="{ row: s, $index: tableRowIndex }"
                      ><ElInput v-model="s.name" placeholder="例如白班" /></template></ElTableColumn
                  ><!-- @vue-generic {TableColumnRow2} -->
                  <ElTableColumn :min-width="150"
                    ><template #header><div class="table-header-label">开始时间</div></template
                    ><template #default="{ row: s, $index: tableRowIndex }"
                      ><ElInput v-model="s.start" type="time" /></template></ElTableColumn
                  ><!-- @vue-generic {TableColumnRow2} -->
                  <ElTableColumn :min-width="150"
                    ><template #header><div class="table-header-label">结束时间</div></template
                    ><template #default="{ row: s, $index: tableRowIndex }"
                      ><ElInput v-model="s.end" type="time" /></template></ElTableColumn
                  ><!-- @vue-generic {TableColumnRow2} -->
                  <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                    ><template #header><div class="table-header-label">操作</div></template
                    ><template #default="{ row: s, $index: tableRowIndex }">
                      <ElButton
                        class="inspection-text-button danger"
                        @click="form.shifts = form.shifts.filter((i) => i.id !== s.id)"
                        type="danger"
                        link
                      >
                        删除
                      </ElButton>
                    </template></ElTableColumn
                  ></AppBusinessTable
                >
              </div>
            </div>
          </section>
          <section v-if="daily && form.personMode === '按照排班表'" class="inspection-form-card">
            <div class="inspection-section-tools">
              <h3>人员排班</h3>
              <ElButton
                @click="
                  form.roster.push({
                    key: uuid(),
                    date: form.startDate,
                    shiftId: form.shifts[0]?.id || '',
                    users: [],
                  })
                "
                ><AppIcon name="Plus" /> 新增排班</ElButton
              ><ElButton @click="rosterInput?.open()">导入排班</ElButton
              ><ElButton
                @click="
                  downloadCsv('排班导入模板', [
                    ['日期', '班次', '人员账号'],
                    [form.startDate, form.shifts[0]?.name || '白班', auth.user?.account || 'admin'],
                  ])
                "
                >下载模板</ElButton
              >
            </div>
            <AppFilePicker ref="rosterInput" accept=".csv" hidden @select="importRoster" />
            <div class="inspection-inline-scroll">
              <AppBusinessTable
                :rows="form.roster"
                :pagination="false"
                :fill="false"
                :framed="false"
                class="business-table inspection-roster-table"
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">日期</div></template
                  ><template #default="{ row: r, $index: tableRowIndex }"
                    ><ElDatePicker
                      v-model="r.date"
                      type="date"
                      value-format="YYYY-MM-DD" /></template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">班次</div></template
                  ><template #default="{ row: r, $index: tableRowIndex }">
                    <ElSelect v-model="r.shiftId"
                      ><ElOption v-for="s in form.shifts" :key="s.id" :value="s.id" :label="s.name"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn :min-width="150"
                  ><template #header><div class="table-header-label">排班人员</div></template
                  ><template #default="{ row: r, $index: tableRowIndex }">
                    <ElSelect v-model="r.users" multiple filterable
                      ><ElOption
                        v-for="u in choices.users"
                        :key="u.id"
                        :value="u.id"
                        :label="u.name + ' · ' + u.department"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow3} -->
                <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                  ><template #header><div class="table-header-label">操作</div></template
                  ><template #default="{ row: r, $index: tableRowIndex }">
                    <ElButton
                      class="inspection-text-button danger"
                      @click="form.roster = form.roster.filter((i) => i.key !== r.key)"
                      type="danger"
                      link
                    >
                      删除
                    </ElButton>
                  </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.roster.length">暂无排班，可新增或导入</div></template
                ></AppBusinessTable
              >
            </div>
          </section>
          <section class="inspection-form-card">
            <div class="inspection-section-tools">
              <h3>
                {{ daily ? '计划设备' : '巡检路线' }} <span>{{ form.details.length }} 台</span>
              </h3>
              <ElButton type="primary" @click="equipmentPicker"
                ><AppIcon name="Plus" /> 添加设备</ElButton
              ><ElButton @click="matchProjects">匹配检查项目</ElButton
              ><ElButton @click="importInput?.open()">导入设备</ElButton
              ><ElDropdown :disabled="!selectedDetails.length" @command="detailCommand"
                ><ElButton :disabled="!selectedDetails.length"
                  >批量操作<AppIcon name="Down" /></ElButton
                ><template #dropdown
                  ><ElDropdownMenu
                    ><ElDropdownItem v-if="daily" command="owners">设置负责人</ElDropdownItem
                    ><ElDropdownItem command="reviewers">设置审核人</ElDropdownItem
                    ><ElDropdownItem divided command="delete">删除</ElDropdownItem></ElDropdownMenu
                  ></template
                ></ElDropdown
              >
            </div>
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
                  ><template #header><div class="table-header-label">序号</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    index + 1
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="170" class-name="detail-code"
                  ><template #header><div class="table-header-label">设备编号</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.code
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="210" :width="190" class-name="detail-name"
                  ><template #header><div class="table-header-label">设备名称</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.name
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="160"
                  ><template #header><div class="table-header-label">所属部门</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.department
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="150"
                  ><template #header><div class="table-header-label">规格型号</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.model
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="190"
                  ><template #header><div class="table-header-label">位置</div></template
                  ><template #default="{ row: d, $index: index }">{{
                    eqOf(d)?.functionLocation || ''
                  }}</template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="210" :width="260"
                  ><template #header><div class="table-header-label">检查项目</div></template
                  ><template #default="{ row: d, $index: index }">
                    <ElButton class="inspection-project-picker" @click="pickProjects(d)"
                      >{{
                        d.projectRefs.length
                          ? d.projectRefs.length + ' 个检查项目'
                          : '请选择检查项目'
                      }}<span>选择 <AppIcon name="Right" /></span
                    ></ElButton> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn v-if="daily" :min-width="150" :width="220"
                  ><template #header><div class="table-header-label">负责人</div></template
                  ><template #default="{ row: d, $index: index }">
                    <ElSelect
                      v-model="d.owners"
                      multiple
                      filterable
                      placeholder="沿用计划负责人"
                      :disabled="form.personMode !== '指定负责人'"
                      ><ElOption
                        v-for="u in choices.users"
                        :key="u.id"
                        :value="u.id"
                        :label="u.name"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn :min-width="150" :width="220"
                  ><template #header><div class="table-header-label">审核人</div></template
                  ><template #default="{ row: d, $index: index }">
                    <ElSelect v-model="d.reviewers" multiple filterable placeholder="沿用计划审核人"
                      ><ElOption
                        v-for="u in choices.users"
                        :key="u.id"
                        :value="u.id"
                        :label="u.name"
                    /></ElSelect> </template></ElTableColumn
                ><!-- @vue-generic {TableColumnRow4} -->
                <ElTableColumn
                  class-name="list-action-column"
                  fixed="right"
                  :width="170"
                  :min-width="156"
                  :resizable="false"
                  ><template #header><div class="table-header-label">操作</div></template
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
                        上移</ElButton
                      ><ElButton
                        v-if="!daily"
                        class="inspection-text-button"
                        :disabled="index === form.details.length - 1"
                        @click="moveDetail(index, 1)"
                        type="primary"
                        link
                      >
                        下移</ElButton
                      ><ElButton
                        class="inspection-text-button danger"
                        @click="form.details = form.details.filter((i) => i.key !== d.key)"
                        type="danger"
                        link
                      >
                        删除
                      </ElButton>
                    </div>
                  </template></ElTableColumn
                ><template #empty
                  ><div v-if="!form.details.length">
                    请添加设备，系统将匹配设备关联的检查项目
                  </div></template
                ></AppBusinessTable
              >
            </div>
          </section>
        </template>
      </div>
      <AppFormFooter
        ><template #hint>{{
          standard ? '项目名称、方法及基准为必填项' : '保存时检查到期任务，后续由后台自动生成'
        }}</template
        ><ElButton :disabled="busy" @click="list">取消</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">保存</ElButton></AppFormFooter
      >
    </template>
    <FilterConditionPopover
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      v-model="advancedOpen"
      :conditions="conditions"
      @confirm="confirmFilter"
      :fields="filterFields"
    />
    <AppDialog v-model="equipmentOpen" title="选择设备" width="1000px"
      ><div class="inspection-picker-toolbar">
        <ElInput
          v-model="equipmentSearch"
          placeholder="搜索设备编号、名称、型号"
          clearable
        /><TreeSelect
          v-model="equipmentCategory"
          :nodes="choices.categories.map((c) => ({ ...c, parentId: c.parentId || '' }))"
          placeholder="设备类别"
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
          :rows="equipmentOptions"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table inspection-picker-table"
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">设备编号</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.code
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="210"
            ><template #header><div class="table-header-label">设备名称</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">规格型号</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.model
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">所属部门</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.department
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow5} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">类别</div></template
            ><template #default="{ row: e, $index: tableRowIndex }">{{
              e.category
            }}</template></ElTableColumn
          ></AppBusinessTable
        >
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="equipmentOpen = false">取消</ElButton
          ><ElButton type="primary" @click="addEquipment"
            >添加（{{ pickedEquipment.length }}）</ElButton
          >
        </div></template
      ></AppDialog
    >
    <AppDialog v-model="projectOpen" title="选择检查项目" width="1120px"
      ><div class="inspection-picker-toolbar">
        <ElCheckbox v-model="showAllStandards">查看所有标准</ElCheckbox
        ><ElInput v-model="projectSearch" placeholder="搜索项目名称、检查方法" clearable /><span
          >已选 {{ projectRefs.length }} 项</span
        >
      </div>
      <div class="inspection-project-dialog">
        <aside>
          <ElButton
            v-for="st in projectStandards"
            :key="st.id"
            :class="{ active: projectStandardId === st.id }"
            @click="projectStandardId = st.id"
            ><strong>{{ st.name }}</strong
            ><span>{{ st.code }}</span></ElButton
          >
          <p v-if="!projectStandards.length">无关联标准，可勾选“查看所有标准”</p>
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
              ><template #header><div class="table-header-label">项目名称</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">方法及基准</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.content
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">巡检类型</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                types.find((t) => t.id === i.typeId)?.name
              }}</template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow6} -->
            <ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">结果类型</div></template
              ><template #default="{ row: i, $index: tableRowIndex }">{{
                i.valueType
              }}</template></ElTableColumn
            ><template #empty
              ><div v-if="!projectItems.length">此标准无匹配的检查项目</div></template
            ></AppBusinessTable
          >
        </div>
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="projectOpen = false">取消</ElButton
          ><ElButton type="primary" @click="applyProjects">确定</ElButton>
        </div></template
      ></AppDialog
    >
    <AppDialog
      v-model="detailBatchOpen"
      :title="detailBatch === 'owners' ? '设置负责人' : '设置审核人'"
      ><ElSelect v-model="batchUsers" multiple filterable placeholder="留空沿用计划设置"
        ><ElOption
          v-for="u in choices.users"
          :key="u.id"
          :value="u.id"
          :label="u.name + ' · ' + u.department" /></ElSelect
      ><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton @click="detailBatchOpen = false">取消</ElButton
          ><ElButton type="primary" @click="applyDetailBatch">确定</ElButton>
        </div></template
      ></AppDialog
    >
    <AppDialog v-model="typeOpen" title="巡检类型" width="760px" :busy="busy">
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
            ><template #header><div class="table-header-label">名称</div></template
            ><template #default="{ row: t, $index: tableRowIndex }">{{
              t.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn :min-width="150" :width="100"
            ><template #header><div class="table-header-label">周期</div></template
            ><template #default="{ row: t, $index: tableRowIndex }"
              >{{ t.period }} {{ t.unit }}</template
            ></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn :min-width="150" :width="90"
            ><template #header><div class="table-header-label">排序</div></template
            ><template #default="{ row: t, $index: tableRowIndex }">{{
              t.sortOrder ?? 0
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow7} -->
          <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
            ><template #header><div class="table-header-label">操作</div></template
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
                  编辑
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
                  删除
                </ElButton>
              </div>
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!types.length">暂无巡检类型</div></template
          ></AppBusinessTable
        >
      </div>
      <div class="inspection-type-form">
        <label
          ><span class="required">类型名称</span
          ><ElInput
            v-model="typeForm.name"
            aria-label="巡检类型名称"
            placeholder="输入类型名称"
            maxlength="150"
            :disabled="busy"
        /></label>
        <label
          ><span class="required">巡检周期</span>
          <div class="inspection-type-period">
            <ElInputNumber
              v-model="typeForm.period"
              aria-label="巡检周期数值"
              :min="1"
              :precision="0"
              controls-position="right"
              :disabled="busy"
            />
            <ElSelect v-model="typeForm.unit" aria-label="巡检周期单位" :disabled="busy"
              ><ElOption v-for="u in ['日', '周', '月', '年']" :key="u" :value="u" :label="u"
            /></ElSelect></div
        ></label>
        <label
          ><span>排序</span
          ><ElInputNumber
            v-model="typeForm.sortOrder"
            aria-label="巡检类型排序"
            :min="0"
            :max="999999"
            :precision="0"
            controls-position="right"
            :disabled="busy"
          /><small class="inspection-type-sort-hint">数值越小越靠前</small></label
        >
      </div>
      <template #footer
        ><div class="inspection-dialog-actions">
          <ElButton
            :disabled="busy"
            @click="
              typeForm.id ? (typeForm = { ...emptyInspection(), unit: '日' }) : (typeOpen = false)
            "
            >{{ typeForm.id ? '取消编辑' : '关闭' }}</ElButton
          >
          <ElButton
            v-if="auth.can('inspectionType', typeForm.id ? '编辑' : '新增')"
            type="primary"
            :loading="busy"
            @click="saveType"
            >{{ typeForm.id ? '保存' : '新增' }}</ElButton
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
  gap: 8px;
}
</style>
