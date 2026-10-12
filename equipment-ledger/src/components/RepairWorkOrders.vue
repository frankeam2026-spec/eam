<script setup lang="ts">
import RepairUserPicker from './common/RepairUserPicker.vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import AdvancedFilterDrawer from './common/AdvancedFilterDrawer.vue';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import ListSearchInput from './common/ListSearchInput.vue';
import AppPagination from './common/AppPagination.vue';
import { storeToRefs } from 'pinia';
import { useBaseDataStore } from '../stores/baseData';
import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import * as api from '../api';
import TreeSelect from './TreeSelect.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import type { RepairAttachment } from '../api';

type RepairStatus = '待派工' | '待接单' | '处理中' | '挂起' | '待验收' | '已完成' | '已撤销';
type WorkOrder = {
  id: string;
  orderNo: string;
  equipmentCode: string;
  equipmentName: string;
  department: string;
  faultAt: string;
  faultType: string;
  severity: string;
  description: string;
  reporter: string;
  assignee: string;
  status: RepairStatus;
  repairAction: string;
  downtimeMinutes: number;
  reportedAt: string;
  attachments: RepairAttachment[];
  rowVersion?: number;
};
const props = defineProps<{ currentUser: string; grantedActions: string[] }>();
const equipmentPickerOpen = ref(false),
  equipmentSearch = ref(''),
  equipmentChoices = ref<
    Array<{
      id: string;
      code: string;
      name: string;
      department: string;
      category?: string;
      model?: string;
    }>
  >([]),
  equipmentLoading = ref(false);
const baseData = useBaseDataStore();
const { faultTypeData } = storeToRefs(baseData);
const faultTypeNodes = computed(() => faultTypeData.value.filter((x) => x.status === '启用'));
const fileInput = ref<HTMLInputElement | null>(null),
  uploading = ref(false);
const rows = ref<WorkOrder[]>([]);
const states: RepairStatus[] = ['待派工', '待接单', '处理中', '挂起', '待验收', '已完成', '已撤销'];
type QueueKey = 'owned' | 'pending' | 'mine' | 'accept' | 'created' | 'dispatch' | 'all';
const route = useRoute(),
  router = useRouter();
const activeQueue = ref<QueueKey>(
  ['pending', 'owned', 'mine', 'accept', 'created', 'dispatch', 'all'].includes(
    String(route.query.queue),
  )
    ? (route.query.queue as QueueKey)
    : 'all',
);
const queues: { key: QueueKey; label: string }[] = [
  { key: 'pending', label: '待我接单' },
  { key: 'mine', label: '待我处理' },
  { key: 'accept', label: '待我验收' },
  { key: 'owned', label: '我负责的' },
  { key: 'created', label: '我发起的' },
  { key: 'dispatch', label: '待派工' },
  { key: 'all', label: '全部工单' },
];
const query = reactive({ keyword: '', status: '', severity: '', department: '' });
const applied = ref({ ...query });
const columnFilters = reactive<Record<string, string>>({});
const columnFiltersOpen = ref(false);
const advancedOpen = ref(false);
const advancedConditions = ref<FilterCondition[]>([]);
const advancedFields = computed<FilterField[]>(() => [
  ...[
    ['orderNo', '工单编号'],
    ['equipmentCode', '设备编号'],
    ['equipmentName', '设备名称'],
    ['description', '故障描述'],
    ['department', '所属部门'],
    ['reporter', '报修人'],
    ['assignee', '维修人'],
    ['repairAction', '维修措施'],
    ['acceptanceComment', '验收意见'],
  ].map(([key, label]) => ({ key: key!, label: label!, type: 'text' as const })),
  { key: 'faultType', label: '故障类型', type: 'tree', nodes: faultTypeNodes.value },
  { key: 'status', label: '工单状态', type: 'choice', options: states },
  { key: 'severity', label: '紧急程度', type: 'choice', options: ['一般', '紧急', '特急'] },
  ...[
    ['faultAt', '故障时间'],
    ['reportedAt', '报修时间'],
    ['completedAt', '完成时间'],
  ].map(([key, label]) => ({ key: key!, label: label!, type: 'date' as const })),
  { key: 'downtimeMinutes', label: '停机时长（分钟）', type: 'number' },
]);
function applyAdvanced(conditions: FilterCondition[]) {
  page.value = 1;
  advancedConditions.value = conditions;
}

const page = ref(1),
  pageSize = ref(10),
  drawer = ref(false),
  detailMode = ref(false),
  active = ref<WorkOrder | null>(null),
  isNew = ref(true);
const selectedRowId = ref<string | null>(null);
const dispatchPicker = ref(false),
  dispatchTarget = ref<WorkOrder | null>(null);
const sortKey = ref<keyof WorkOrder>('reportedAt'),
  sortDirection = ref<'asc' | 'desc'>('desc');
const widths = ref<Record<string, number>>(readWidths());
const showColumns = ref(false),
  toast = ref('');
const allColumns: { key: keyof WorkOrder; label: string; width: number; required?: boolean }[] = [
  { key: 'orderNo', label: '工单编号', width: 150, required: true },
  { key: 'equipmentCode', label: '设备编号', width: 145 },
  { key: 'equipmentName', label: '设备名称', width: 150, required: true },
  { key: 'status', label: '状态', width: 110 },
  { key: 'description', label: '故障描述', width: 260 },
  { key: 'faultAt', label: '故障时间', width: 155 },
  { key: 'department', label: '所属部门', width: 130 },
  { key: 'reporter', label: '报修人', width: 95 },
  { key: 'assignee', label: '维修人', width: 95 },
  { key: 'severity', label: '紧急程度', width: 100 },
  { key: 'reportedAt', label: '报修时间', width: 155 },
];
const visibleKeys = ref<string[]>(readColumns());
function readWidths() {
  try {
    return JSON.parse(localStorage.getItem('eam-repair-widths') || '{}');
  } catch {
    return {};
  }
}
function readColumns() {
  try {
    const stored = JSON.parse(localStorage.getItem('eam-repair-columns') || 'null') as
      string[] | null;
    if (!stored) return allColumns.slice(0, 10).map((c) => c.key);
    if (!localStorage.getItem('eam-repair-status-column-v2')) {
      localStorage.setItem('eam-repair-status-column-v2', '1');
      return stored.includes('status') ? stored : [...stored, 'status'];
    }
    return stored;
  } catch {
    return allColumns.slice(0, 10).map((c) => c.key);
  }
}
watch(widths, (v) => localStorage.setItem('eam-repair-widths', JSON.stringify(v)), { deep: true });
watch(visibleKeys, (v) => localStorage.setItem('eam-repair-columns', JSON.stringify(v)), {
  deep: true,
});
const filterSnapshot = ref('{}');
let filterTimer: ReturnType<typeof setTimeout> | undefined;
watch(
  columnFilters,
  () => {
    clearTimeout(filterTimer);
    filterTimer = setTimeout(() => {
      page.value = 1;
      filterSnapshot.value = JSON.stringify(columnFilters);
    }, 300);
  },
  { deep: true },
);
const columns = computed(() => allColumns.filter((c) => visibleKeys.value.includes(c.key)));
function formatListDateTime(value: string) {
  if (!value) return '';
  if (/(?:Z|[+-]\d{2}:\d{2})$/i.test(value)) {
    const date = new Date(value);
    if (!Number.isNaN(date.getTime())) {
      const pad = (n: number) => String(n).padStart(2, '0');
      return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`;
    }
  }
  return value.replace('T', ' ').slice(0, 16);
}
function listCell(row: WorkOrder, key: keyof WorkOrder) {
  return key === 'faultAt' || key === 'reportedAt'
    ? formatListDateTime(row[key])
    : String(row[key] ?? '');
}
const queueCounts = ref<Record<QueueKey, number>>({
  owned: 0,
  pending: 0,
  mine: 0,
  accept: 0,
  created: 0,
  dispatch: 0,
  all: 0,
});
const total = ref(0);
const loading = ref(false);
const loadError = ref('');
const pageRows = computed(() => rows.value);
watch(
  [
    page,
    pageSize,
    activeQueue,
    sortKey,
    sortDirection,
    applied,
    filterSnapshot,
    advancedConditions,
  ],
  () => load(),
);
function search() {
  applied.value = { ...query };
  page.value = 1;
}
function selectQueue(key: QueueKey) {
  activeQueue.value = key;
  page.value = 1;
}
function reset() {
  Object.assign(query, { keyword: '', status: '', severity: '', department: '' });
  Object.keys(columnFilters).forEach((key) => delete columnFilters[key]);
  advancedConditions.value = [];
  activeQueue.value = 'all';
  search();
}
function sortBy(key: keyof WorkOrder) {
  if (sortKey.value === key) sortDirection.value = sortDirection.value === 'asc' ? 'desc' : 'asc';
  else {
    sortKey.value = key;
    sortDirection.value = 'asc';
  }
}
function width(key: keyof WorkOrder) {
  return widths.value[key] || allColumns.find((c) => c.key === key)?.width || 120;
}
let resize: { key: keyof WorkOrder; startX: number; startWidth: number } | null = null;
function startResize(e: PointerEvent, key: keyof WorkOrder) {
  resize = { key, startX: e.clientX, startWidth: width(key) };
  window.addEventListener('pointermove', moveResize);
  window.addEventListener('pointerup', endResize, { once: true });
}
function moveResize(e: PointerEvent) {
  if (resize)
    widths.value[resize.key] = Math.max(
      90,
      Math.min(540, resize.startWidth + e.clientX - resize.startX),
    );
}
function endResize() {
  resize = null;
  window.removeEventListener('pointermove', moveResize);
}
const exporting = ref(false);
function requestParams() {
  return {
    ...applied.value,
    queue: activeQueue.value,
    sortBy: sortKey.value,
    direction: sortDirection.value,
    filters: filterSnapshot.value,
    advanced: JSON.stringify(advancedConditions.value),
  };
}
async function exportCsv() {
  if (exporting.value) return;
  exporting.value = true;
  try {
    const blob = await api.exportRepairs({
      ...requestParams(),
      columns: columns.value.map((c) => c.key).join(','),
    });
    const link = document.createElement('a');
    const url = URL.createObjectURL(blob);
    link.href = url;
    link.download = '维修工单.csv';
    link.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  } catch (e) {
    notice(e instanceof Error ? e.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}
function notice(message: string) {
  toast.value = message;
  window.setTimeout(() => (toast.value = ''), 2200);
}
function openCreate() {
  const now = new Date();
  const faultAt = new Date(now.getTime() - now.getTimezoneOffset() * 60000)
    .toISOString()
    .slice(0, 16);
  active.value = {
    id: '',
    orderNo: '',
    equipmentCode: '',
    equipmentName: '',
    department: '',
    faultAt,
    faultType: '',
    severity: '一般',
    description: '',
    reporter: props.currentUser,
    assignee: '',
    status: '待派工',
    repairAction: '',
    downtimeMinutes: 0,
    reportedAt: '',
    attachments: [],
  };
  isNew.value = true;
  detailMode.value = false;
  reportGuard.markSaved();
  drawer.value = true;
}
function openEdit(row: WorkOrder) {
  active.value = JSON.parse(JSON.stringify({ ...row, attachments: row.attachments || [] }));
  isNew.value = false;
  detailMode.value = false;
  reportGuard.markSaved();
  drawer.value = true;
}
function openDetail(row: WorkOrder) {
  router.push({ name: 'repair-detail', params: { id: row.id } });
}

const submitting = ref(false);
const reportGuard = useUnsavedChanges(
  () => active.value,
  () => drawer.value && !detailMode.value,
  () => submitting.value || uploading.value,
);
onBeforeRouteLeave(() => !(submitting.value || uploading.value));
onBeforeRouteUpdate(() => !(submitting.value || uploading.value));
async function closeReport() {
  if (submitting.value || uploading.value || !(await reportGuard.permitLeave())) return;
  equipmentPickerOpen.value = false;
  drawer.value = false;
}
async function save() {
  if (!active.value || submitting.value || uploading.value) return;
  const f = active.value;
  if (
    !f.equipmentCode ||
    !f.equipmentName ||
    !f.faultAt ||
    !f.faultType ||
    !f.reporter ||
    !f.description.trim()
  ) {
    notice('请填写设备、故障时间、故障类型、报修人和故障描述');
    return;
  }
  submitting.value = true;
  try {
    await api.saveRepair(f, isNew.value);
    reportGuard.markSaved();
    await load();
    drawer.value = false;
    notice(isNew.value ? '报修已提交' : '工单已保存');
  } catch (e) {
    notice(e instanceof Error ? e.message : '保存失败');
  } finally {
    submitting.value = false;
  }
}
async function transition(row: WorkOrder, action: string) {
  if (submitting.value || uploading.value) return;
  if (action === 'dispatch' && !row.assignee.trim()) {
    notice('派工前请填写维修人');
    return;
  }
  if (action === 'complete' && !row.repairAction.trim()) {
    notice('请记录维修措施后再提交验收');
    return;
  }
  submitting.value = true;
  try {
    const res = await api.transitionRepair(
      row.id,
      action,
      row.assignee,
      row.repairAction,
      row.downtimeMinutes,
      row.rowVersion,
    );
    await load();
    active.value = { ...res.data };
    notice('工单状态已更新');
  } catch (e) {
    notice(e instanceof Error ? e.message : '操作失败');
  } finally {
    submitting.value = false;
  }
}
function rowAction(row: WorkOrder) {
  if (row.status === '待派工' && props.grantedActions.includes('派工')) {
    dispatchTarget.value = row;
    dispatchPicker.value = true;
    return;
  }
  router.push({ name: 'repair-detail', params: { id: row.id } });
}
async function confirmDispatch(ids: string[]) {
  if (!dispatchTarget.value || submitting.value) return;
  submitting.value = true;
  try {
    await api.repairWorkflowAction(dispatchTarget.value.id, dispatchTarget.value.rowVersion, {
      action: 'dispatch',
      userIds: ids,
    });
    dispatchPicker.value = false;
    await load();
    window.dispatchEvent(new Event('repair-inbox-changed'));
    notice('派工完成，等待接单');
  } catch (e) {
    notice(e instanceof Error ? e.message : '派工失败');
    await load();
  } finally {
    submitting.value = false;
  }
}

async function loadFaultTypes() {
  try {
    await baseData.load('故障类型');
  } catch (e) {
    notice(e instanceof Error ? e.message : '故障类型加载失败');
  }
}
async function openEquipmentPicker() {
  equipmentPickerOpen.value = true;
  equipmentSearch.value = '';
  equipmentLoading.value = true;
  try {
    const res = await api.listEquipment({ page: 1, size: 10000 });
    equipmentChoices.value = res.data.map((x) => ({
      id: x.id,
      code: x.code,
      name: x.name,
      department: x.department,
      category: x.category,
      model: x.model,
    }));
  } catch (e) {
    equipmentChoices.value = [];
    notice(e instanceof Error ? e.message : '设备加载失败');
  } finally {
    equipmentLoading.value = false;
  }
}
const filteredEquipment = computed(() => {
  const key = equipmentSearch.value.trim().toLocaleLowerCase();
  return equipmentChoices.value.filter(
    (x) =>
      !key ||
      [x.code, x.name, x.department, x.category, x.model]
        .join(' ')
        .toLocaleLowerCase()
        .includes(key),
  );
});
function chooseEquipment(item: { id: string; code: string; name: string; department: string }) {
  if (!active.value) return;
  active.value.equipmentCode = item.code;
  active.value.equipmentName = item.name;
  active.value.department = item.department;
  equipmentPickerOpen.value = false;
}
function openFilePicker() {
  fileInput.value?.click();
}
async function addFiles(event: Event) {
  if (!active.value) return;
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  uploading.value = true;
  try {
    for (const file of files) {
      const result = await api.uploadRepairFile(file);
      active.value.attachments.push(result.data);
    }
  } catch (e) {
    notice(e instanceof Error ? e.message : '附件上传失败');
  } finally {
    uploading.value = false;
    input.value = '';
  }
}
function removeAttachment(id: string) {
  if (active.value)
    active.value.attachments = active.value.attachments.filter((file) => file.id !== id);
}
let requestSequence = 0;
async function load() {
  const sequence = ++requestSequence;
  loading.value = true;
  loadError.value = '';
  try {
    const res = await api.listRepairs({
      ...requestParams(),
      page: page.value,
      size: pageSize.value,
    });
    if (sequence !== requestSequence) return;
    if (
      !res.counts ||
      queues.some(({ key }) => !Number.isSafeInteger(res.counts[key]) || res.counts[key] < 0)
    ) {
      throw new Error('工单接口版本不匹配，请更新后端并刷新页面');
    }
    rows.value = res.data;
    total.value = res.page.total;
    queueCounts.value = res.counts;
    const lastPage = Math.max(1, Math.ceil(total.value / pageSize.value));
    if (page.value > lastPage) page.value = lastPage;
  } catch (e) {
    if (sequence !== requestSequence) return;
    rows.value = [];
    total.value = 0;
    queueCounts.value = {
      owned: 0,
      pending: 0,
      mine: 0,
      accept: 0,
      created: 0,
      dispatch: 0,
      all: 0,
    };
    loadError.value = e instanceof Error ? e.message : '工单加载失败';
    notice(loadError.value);
  } finally {
    if (sequence === requestSequence) loading.value = false;
  }
}
onUnmounted(() => {
  ++requestSequence;
  clearTimeout(filterTimer);
  endResize();
});
onMounted(() => {
  load();
  loadFaultTypes();
});
</script>

<template>
  <section class="content repair-page">
    <RepairUserPicker
      v-model="dispatchPicker"
      title="派工"
      :busy="submitting"
      @confirm="confirmDispatch"
    />
    <nav class="repair-queues" aria-label="工单快捷队列">
      <button
        v-for="queue in queues"
        :key="queue.key"
        :class="{ active: activeQueue === queue.key }"
        @click="selectQueue(queue.key)"
      >
        {{ queue.label }}<span>{{ queueCounts[queue.key] }}</span>
      </button>
    </nav>
    <div class="repair-toolbar">
      <div class="repair-actions">
        <button
          v-if="props.grantedActions.includes('报修')"
          class="repair-primary"
          @click="openCreate"
        >
          <span>＋</span>报修
        </button>
      </div>
      <form class="repair-filters" @submit.prevent="search">
        <ListToolbarIcons
          mode="filter"
          :can-export="props.grantedActions.includes('导出')"
          :export-busy="exporting"
          :filter-open="columnFiltersOpen"
          :columns-open="showColumns"
          @toggle-filter="columnFiltersOpen = !columnFiltersOpen"
          @toggle-columns="showColumns = !showColumns"
          @export="exportCsv"
        /><ListSearchInput
          v-model="query.keyword"
          placeholder="搜索工单编号、设备、故障描述、部门、人员"
        />
        <button
          type="button"
          class="advanced-filter-trigger"
          :class="{ active: advancedConditions.length }"
          @click="advancedOpen = true"
        >
          筛选器<span v-if="advancedConditions.length"> ({{ advancedConditions.length }})</span>
        </button>
        <button class="repair-reset" type="button" @click="reset">重置</button>
      </form>
    </div>
    <AppliedFilterChips
      :conditions="advancedConditions"
      :fields="advancedFields"
      @change="applyAdvanced"
    />
    <AdvancedFilterDrawer
      v-model="advancedOpen"
      :fields="advancedFields"
      :conditions="advancedConditions"
      @apply="applyAdvanced"
    />
    <div class="repair-table-card">
      <div class="repair-table-scroll">
        <table class="business-table">
          <thead>
            <tr>
              <th
                v-for="col in columns"
                :key="col.key"
                :style="{ width: width(col.key) + 'px', minWidth: width(col.key) + 'px' }"
                @click="sortBy(col.key)"
              >
                <span
                  >{{ col.label }}
                  <i>{{
                    sortKey === col.key ? (sortDirection === 'asc' ? '↑' : '↓') : '↕'
                  }}</i></span
                ><b
                  class="repair-resizer"
                  @click.stop
                  @pointerdown.stop.prevent="startResize($event, col.key)"
                ></b>
              </th>
              <th class="repair-action-head list-action-column">操作</th>
            </tr>
            <tr v-if="columnFiltersOpen" class="repair-filter-row">
              <th v-for="col in columns" :key="col.key">
                <select
                  v-if="col.key === 'status'"
                  v-model="columnFilters[col.key]"
                  aria-label="按工单状态筛选"
                >
                  <option value="">全部状态</option>
                  <option v-for="s in states" :key="s">{{ s }}</option></select
                ><select
                  v-else-if="col.key === 'severity'"
                  v-model="columnFilters[col.key]"
                  aria-label="按紧急程度筛选"
                >
                  <option value="">全部程度</option>
                  <option>一般</option>
                  <option>紧急</option>
                  <option>特急</option></select
                ><input
                  v-else
                  v-model="columnFilters[col.key]"
                  :aria-label="`按${col.label}筛选`"
                  :placeholder="`筛选${col.label}`"
                  @click.stop
                />
              </th>
              <th class="list-action-column"></th>
            </tr>
          </thead>
          <tbody>
            <tr
              v-for="row in pageRows"
              :key="row.id"
              :class="{ selected: selectedRowId === row.id }"
              @click="selectedRowId = row.id"
            >
              <td
                v-for="col in columns"
                :key="col.key"
                :style="{
                  width: width(col.key) + 'px',
                  minWidth: width(col.key) + 'px',
                  maxWidth: width(col.key) + 'px',
                }"
              >
                <button
                  v-if="col.key === 'orderNo'"
                  class="repair-link"
                  @click.stop="openDetail(row)"
                >
                  {{ row.orderNo }}</button
                ><button
                  v-else-if="col.key === 'equipmentName'"
                  class="repair-text-link"
                  @click.stop="openDetail(row)"
                >
                  {{ row.equipmentName }}</button
                ><span
                  v-else-if="col.key === 'status'"
                  class="repair-status"
                  :class="'s-' + row.status"
                  >{{ row.status }}</span
                ><span v-else>{{ listCell(row, col.key) }}</span>
              </td>
              <td class="repair-row-actions list-action-column">
                <button
                  v-if="row.status === '待派工' && props.grantedActions.includes('编辑')"
                  @click.stop="openEdit(row)"
                >
                  编辑</button
                ><button @click.stop="rowAction(row)">
                  {{
                    row.status === '待派工'
                      ? props.grantedActions.includes('派工')
                        ? '派工'
                        : '查看'
                      : row.status === '待接单'
                        ? activeQueue === 'pending'
                          ? '接单'
                          : '查看'
                        : row.status === '处理中' || row.status === '挂起'
                          ? '处理'
                          : row.status === '待验收'
                            ? '验收'
                            : '查看'
                  }}
                </button>
              </td>
            </tr>
            <tr v-if="!pageRows.length">
              <td :colspan="columns.length + 1" class="repair-empty">
                {{ loading ? '正在加载…' : loadError || '没有符合条件的工单' }}
              </td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="repair-footer">
        <span
          >共 <b>{{ total }}</b> 条</span
        >
        <AppPagination v-model:page="page" v-model:page-size="pageSize" :total="total" />
        <ListToolbarIcons
          mode="footer"
          :can-export="props.grantedActions.includes('导出')"
          :export-busy="exporting"
          :filter-open="columnFiltersOpen"
          :columns-open="showColumns"
          @toggle-filter="columnFiltersOpen = !columnFiltersOpen"
          @toggle-columns="showColumns = !showColumns"
          @export="exportCsv"
          ><template #columns
            ><label v-for="col in allColumns" :key="col.key"
              ><input
                type="checkbox"
                :checked="visibleKeys.includes(col.key)"
                :disabled="col.required"
                @change="
                  visibleKeys = visibleKeys.includes(col.key)
                    ? visibleKeys.filter((k) => k !== col.key)
                    : allColumns
                        .filter((c) => visibleKeys.includes(c.key) || c.key === col.key)
                        .map((c) => c.key)
                "
              />{{ col.label }}</label
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </div>
    <Transition name="fade"
      ><div v-if="drawer" class="repair-mask">
        <section class="repair-drawer" role="dialog" aria-modal="true">
          <header>
            <div>
              <span>维修工单</span><i>/</i>
              <h2>{{ detailMode ? '工单详情' : isNew ? '报修' : '编辑工单' }}</h2>
              <b v-if="active?.orderNo">{{ active.orderNo }}</b>
            </div>
            <button aria-label="关闭" :disabled="submitting || uploading" @click="closeReport">
              ×
            </button>
          </header>
          <div v-if="active" class="repair-form-body">
            <section class="repair-form-group">
              <h3>故障信息</h3>
              <div class="repair-form-grid">
                <label
                  ><span>设备编号<i>*</i></span>
                  <div class="repair-device-select">
                    <input
                      :value="active.equipmentCode"
                      readonly
                      placeholder="请从设备台账选择"
                      @click="!detailMode && openEquipmentPicker()"
                    /><button
                      v-if="!detailMode"
                      class="repair-secondary"
                      type="button"
                      @click="openEquipmentPicker"
                    >
                      选择
                    </button>
                  </div></label
                ><label
                  ><span>设备名称<i>*</i></span
                  ><input
                    :value="active.equipmentName"
                    readonly
                    placeholder="选择设备后自动带出"
                    @click="!detailMode && openEquipmentPicker()" /></label
                ><label
                  ><span>所属部门</span
                  ><input
                    v-model="active.department"
                    readonly
                    placeholder="选择设备后自动带出" /></label
                ><label
                  ><span>故障时间<i>*</i></span
                  ><input
                    v-model="active.faultAt"
                    type="datetime-local"
                    :readonly="detailMode" /></label
                ><label
                  ><span>故障类型<i>*</i></span
                  ><TreeSelect
                    v-if="!detailMode"
                    v-model="active.faultType"
                    :nodes="faultTypeNodes"
                    placeholder="请选择故障类型" /><input
                    v-else
                    v-model="active.faultType"
                    readonly /></label
                ><label
                  ><span>紧急程度</span
                  ><select v-model="active.severity" :disabled="detailMode">
                    <option>一般</option>
                    <option>紧急</option>
                    <option>特急</option>
                  </select></label
                ><label><span>报修人</span><input v-model="active.reporter" readonly /></label
                ><label v-if="!isNew"
                  ><span>维修人</span
                  ><input
                    v-model="active.assignee"
                    :readonly="detailMode && active.status !== '待派工'"
                    placeholder="派工时填写维修人" /></label
                ><label class="repair-span-2"
                  ><span>故障描述<i>*</i></span
                  ><textarea
                    v-model="active.description"
                    :readonly="detailMode"
                    rows="3"
                    placeholder="描述故障现象、发生经过及现场情况"
                  /></label
                ><label v-if="!isNew" class="repair-span-2"
                  ><span>维修措施</span
                  ><textarea
                    v-model="active.repairAction"
                    :readonly="detailMode && active.status !== '处理中'"
                    rows="3"
                    placeholder="记录检查结果和维修措施"
                  /></label
                ><label v-if="!isNew"
                  ><span>停机时长（分钟）</span
                  ><input
                    v-model.number="active.downtimeMinutes"
                    type="number"
                    min="0"
                    :readonly="detailMode && active.status !== '处理中'" /></label
                ><label v-if="detailMode"
                  ><span>当前状态</span><input :value="active.status" readonly
                /></label>
              </div>
              <div class="repair-attachment-section">
                <div class="repair-attachment-toolbar">
                  <button
                    v-if="!detailMode"
                    class="repair-secondary"
                    type="button"
                    :disabled="uploading"
                    @click="openFilePicker"
                  >
                    {{ uploading ? '上传中…' : '＋ 上传附件/图片' }}</button
                  ><small>支持图片、PDF、Office 文档，单个文件不超过 20MB</small>
                </div>
                <input
                  ref="fileInput"
                  class="repair-hidden-file"
                  type="file"
                  multiple
                  accept="image/*,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip"
                  @change="addFiles"
                />
                <div v-if="active.attachments.length" class="repair-attachment-list">
                  <div
                    v-for="file in active.attachments"
                    :key="file.id"
                    class="repair-attachment-item"
                  >
                    <span class="repair-file-icon">{{
                      file.contentType.startsWith('image/') ? '▧' : '▤'
                    }}</span
                    ><a :href="file.url || file.dataUrl || '#'" target="_blank" rel="noopener"
                      ><img
                        v-if="file.contentType.startsWith('image/')"
                        :src="file.url || file.dataUrl"
                        alt=""
                      /><span>{{ file.name }}</span></a
                    ><small>{{ (file.size / 1024).toFixed(0) }} KB</small
                    ><button v-if="!detailMode" type="button" @click="removeAttachment(file.id)">
                      删除
                    </button>
                  </div>
                </div>
                <div v-else class="repair-attachment-empty">暂无附件</div>
              </div>
            </section>
            <section class="repair-form-group" v-if="detailMode">
              <h3>处理动作</h3>
              <div class="repair-transition-actions">
                <template v-if="active.status === '待派工'"
                  ><button
                    v-if="props.grantedActions.includes('派工')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'dispatch')"
                  >
                    派工并开始处理</button
                  ><button
                    v-if="props.grantedActions.includes('撤回')"
                    class="repair-danger"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'cancel')"
                  >
                    撤销工单
                  </button></template
                ><template v-else-if="active.status === '处理中'"
                  ><button
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'complete')"
                  >
                    维修完成，提交验收</button
                  ><button
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-secondary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'suspend')"
                  >
                    挂起
                  </button></template
                ><template v-else-if="active.status === '挂起'"
                  ><button
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'resume')"
                  >
                    恢复处理
                  </button></template
                ><template v-else-if="active.status === '待验收'"
                  ><button
                    v-if="props.grantedActions.includes('验收')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'accept')"
                  >
                    验收通过</button
                  ><button
                    v-if="props.grantedActions.includes('验收')"
                    class="repair-secondary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'return')"
                  >
                    退回维修
                  </button></template
                ><span v-else class="repair-readonly-state">当前工单已{{ active.status }}</span>
              </div>
            </section>
          </div>
          <footer>
            <button
              class="repair-secondary"
              :disabled="submitting || uploading"
              @click="closeReport"
            >
              {{ detailMode ? '关闭' : '取消' }}</button
            ><button
              v-if="!detailMode"
              class="repair-primary"
              :disabled="submitting || uploading"
              @click="save"
            >
              {{ submitting ? '提交中…' : isNew ? '提交' : '保存' }}
            </button>
          </footer>
        </section>
      </div></Transition
    >
    <Transition name="fade"
      ><div
        v-if="equipmentPickerOpen"
        class="equipment-picker-mask"
        @click.self="equipmentPickerOpen = false"
      >
        <section class="equipment-picker-dialog" role="dialog" aria-modal="true">
          <header>
            <b>选择设备</b
            ><button type="button" aria-label="关闭" @click="equipmentPickerOpen = false">×</button>
          </header>
          <div class="equipment-picker-toolbar">
            <input
              v-model="equipmentSearch"
              autofocus
              placeholder="搜索设备编号、名称、部门、分类或型号"
            /><span>{{ filteredEquipment.length }} 项</span>
          </div>
          <div class="equipment-picker-table">
            <table class="business-table">
              <thead>
                <tr>
                  <th>设备编号</th>
                  <th>设备名称</th>
                  <th>设备分类</th>
                  <th>规格型号</th>
                  <th>所属部门</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr
                  v-for="item in filteredEquipment"
                  :key="item.id"
                  @dblclick="chooseEquipment(item)"
                >
                  <td>{{ item.code }}</td>
                  <td>
                    <b>{{ item.name }}</b>
                  </td>
                  <td>{{ item.category || '' }}</td>
                  <td>{{ item.model || '' }}</td>
                  <td>{{ item.department || '' }}</td>
                  <td><button class="repair-link" @click="chooseEquipment(item)">选择</button></td>
                </tr>
                <tr v-if="equipmentLoading">
                  <td colspan="6" class="repair-empty">正在加载设备台账…</td>
                </tr>
                <tr v-else-if="!filteredEquipment.length">
                  <td colspan="6" class="repair-empty">没有匹配设备</td>
                </tr>
              </tbody>
            </table>
          </div>
          <footer>
            <button class="repair-secondary" @click="equipmentPickerOpen = false">取消</button>
          </footer>
        </section>
      </div></Transition
    >
    <Transition name="toast"
      ><div v-if="toast" class="repair-toast">{{ toast }}</div></Transition
    >
  </section>
</template>

<style scoped>
.repair-toolbar button,
.repair-filters input,
.repair-filters select,
.repair-advanced-panel,
.repair-column-menu,
.repair-table-scroll th,
.repair-table-scroll td,
.repair-row-actions button,
.repair-link,
.repair-text-link,
.repair-footer,
.repair-footer button,
.repair-footer select,
.repair-footer input {
  font-size: 13px;
}
.repair-page {
  padding-top: 20px;
}
.repair-heading {
  height: 34px;
  margin-bottom: 12px;
}
.repair-heading h1 {
  font-size: 19px;
  margin: 0;
  font-weight: 650;
}
.repair-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 14px;
  margin-bottom: 12px;
  min-height: 36px;
}
.repair-actions,
.repair-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.repair-page button,
.repair-page input,
.repair-page select,
.repair-page textarea {
  font: inherit;
}
.repair-primary,
.repair-secondary,
.repair-search,
.repair-advanced,
.repair-reset,
.repair-footer button,
.repair-columns {
  height: 34px;
  border-radius: 5px;
  padding: 0 12px;
  white-space: nowrap;
  cursor: pointer;
  font-size: 12px;
}
.repair-primary,
.repair-search {
  border: 1px solid var(--brand);
  background: var(--brand);
  color: white;
}
.repair-primary span {
  font-size: 17px;
  margin-right: 4px;
}
.repair-secondary,
.repair-advanced,
.repair-columns {
  border: 1px solid #dfe3e9;
  background: white;
  color: #465164;
}
.repair-secondary:disabled {
  color: #aab2bf;
  cursor: not-allowed;
}
.repair-search {
  height: 34px;
}
.repair-reset {
  border: 0;
  background: transparent;
  color: #727e90;
}
.repair-filters > input,
.repair-filters > select {
  height: 34px;
  border: 1px solid #dfe3e9;
  border-radius: 5px;
  background: #fff;
  padding: 0 9px;
  font-size: 12px;
  color: #4d596a;
}
.repair-filters > input {
  width: 250px;
}
.repair-filters > select {
  width: 120px;
}
.repair-advanced-wrap,
.repair-column-wrap {
  position: relative;
}
.repair-advanced-panel,
.repair-column-menu {
  position: absolute;
  top: 39px;
  right: 0;
  z-index: 8;
  width: 260px;
  background: #fff;
  border: 1px solid #e4e8ee;
  border-radius: 6px;
  box-shadow: var(--shadow);
  padding: 12px;
}
.repair-advanced-panel label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 8px;
  margin: 6px 0;
  font-size: 12px;
}
.repair-advanced-panel input,
.repair-advanced-panel select {
  height: 30px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  padding: 0 7px;
}
.repair-advanced-panel footer {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 12px;
}
.repair-advanced-panel footer > button:first-child {
  height: 32px;
  border: 0;
  background: none;
  color: #6c7889;
}
.repair-table-card {
  background: #fff;
  border: 1px solid #e7eaf0;
  border-radius: 7px;
}
.repair-table-scroll {
  border-radius: 7px 7px 0 0;
}
.repair-table-scroll table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  min-width: 1050px;
  text-align: left;
  table-layout: fixed;
}
.repair-table-scroll th {
  height: 40px;
  position: relative;
  background: #f8f9fb;
  border-bottom: 1px solid #e9ecf1;
  color: #697486;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
  padding: 0 12px;
  cursor: pointer;
}
.repair-table-scroll th > span {
  display: flex;
  justify-content: space-between;
}
.repair-table-scroll th i {
  font-style: normal;
  color: #aab2bf;
}
.repair-table-scroll th .repair-resizer {
  position: absolute;
  right: 0;
  top: 0;
  width: 6px;
  height: 100%;
  cursor: col-resize;
}
.repair-table-scroll th .repair-resizer:hover {
  background: color-mix(in srgb, var(--brand) 30%, transparent);
}
.repair-table-scroll td {
  height: 46px;
  border-bottom: 1px solid #f0f1f4;
  padding: 0 12px;
  color: #4f5969;
  font-size: 12px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.repair-table-scroll tbody tr:hover td {
  background: #fafbfd;
}
.repair-check {
  width: 42px !important;
  text-align: center;
}
.repair-action-head {
  width: 130px;
}
.repair-row-actions {
  width: 130px !important;
}
.repair-row-actions button,
.repair-link,
.repair-text-link {
  padding: 0;
  border: 0;
  background: none;
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  margin-right: 12px;
}
.repair-text-link {
  color: #3f4b5c;
  font-weight: 600;
}
.repair-status {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  white-space: nowrap;
}
.s-待派工 {
  background: #fff5de;
  color: #ae7100;
}
.s-待接单 {
  background: #f1eaff;
  color: #7953b3;
}
.s-处理中 {
  background: #eaf1ff;
  color: #205cf5;
}
.s-挂起 {
  background: #f1f2f4;
  color: #727e90;
}
.s-待验收 {
  background: #e9f8fb;
  color: #087b91;
}
.s-已完成 {
  background: #eaf7f1;
  color: #16845b;
}
.s-已撤销 {
  background: #fff0f1;
  color: #bb5663;
}
.repair-empty {
  height: 120px !important;
  text-align: center;
  color: #8b95a5 !important;
}
.repair-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  color: #778294;
  font-size: 12px;
}
.repair-footer > div {
  display: flex;
  align-items: center;
  gap: 8px;
}
.repair-footer select,
.repair-footer input {
  height: 30px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: white;
  padding: 0 7px;
  color: #596476;
}
.repair-footer button {
  min-width: 30px;
  border: 1px solid #dfe3e9;
  background: #fff;
  color: #596476;
}
.repair-footer button:disabled {
  color: #c6ccd5;
}
.repair-footer .current-page {
  padding: 7px 11px;
  background: var(--brand);
  color: #fff;
  border-radius: 4px;
  font-size: 12px;
}
.repair-footer label {
  display: flex;
  align-items: center;
  gap: 5px;
}
.repair-footer input {
  width: 42px;
}
.repair-columns {
  font-size: 11px;
}
.repair-column-menu {
  display: grid;
  gap: 8px;
  width: 170px;
  top: auto;
  bottom: 38px;
}
.repair-column-menu label {
  display: flex;
  gap: 7px;
  align-items: center;
  font-size: 12px;
}
.repair-device-select {
  display: flex;
  gap: 7px;
  min-width: 0;
}
.repair-device-select input {
  flex: 1;
  min-width: 0;
}
.repair-device-select button {
  height: 34px;
  flex: none;
  color: var(--brand);
}
.repair-attachment-toolbar {
  display: flex;
  align-items: center;
  gap: 10px;
}
.repair-attachment-toolbar small {
  color: #8993a2;
  font-size: 11px;
}
.repair-hidden-file {
  display: none !important;
}
.repair-attachment-list {
  display: grid;
  gap: 5px;
  margin-top: 10px;
}
.repair-attachment-item {
  display: flex;
  align-items: center;
  gap: 9px;
  min-height: 34px;
  padding: 5px 9px;
  border: 1px solid #edf0f4;
  border-radius: 4px;
  color: #59677a;
  font-size: 12px;
}
.repair-attachment-item a {
  color: var(--brand);
  text-decoration: none;
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8px;
  overflow: hidden;
}
.repair-attachment-item a span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.repair-attachment-item a img {
  width: 28px;
  height: 28px;
  border-radius: 3px;
  object-fit: cover;
  flex: none;
}
.repair-attachment-item small {
  color: #8993a2;
}
.repair-attachment-item button {
  border: 0;
  background: none;
  color: #c74444;
  cursor: pointer;
  font-size: 11px;
}
.repair-file-icon {
  color: var(--brand);
}
.repair-attachment-empty {
  padding: 12px;
  color: #9aa4b2;
  font-size: 12px;
}
.equipment-picker-mask {
  position: fixed;
  inset: 0;
  z-index: 45;
  background: #17243b66;
  display: grid;
  place-items: center;
  padding: 24px;
}
.equipment-picker-dialog {
  width: min(1040px, 96vw);
  max-height: min(760px, 90vh);
  background: white;
  border-radius: 7px;
  box-shadow: 0 16px 50px #10213b38;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.equipment-picker-dialog > header {
  height: 50px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 16px;
  border-bottom: 1px solid #e9ecf1;
}
.equipment-picker-dialog > header b {
  font-size: 15px;
}
.equipment-picker-dialog > header button {
  border: 0;
  background: none;
  font-size: 22px;
  color: #7b8797;
  cursor: pointer;
}
.equipment-picker-toolbar {
  height: 52px;
  flex: none;
  padding: 8px 14px;
  display: flex;
  align-items: center;
  gap: 12px;
}
.equipment-picker-toolbar input {
  height: 34px;
  flex: 1;
  border: 1px solid #dfe3e9;
  border-radius: 5px;
  padding: 0 10px;
  font-size: 12px;
}
.equipment-picker-toolbar span {
  font-size: 12px;
  color: #8792a1;
}
.equipment-picker-table {
  overflow: auto;
  flex: 1;
}
.equipment-picker-table table {
  width: 100%;
  min-width: 700px;
  border-collapse: collapse;
  text-align: left;
}
.equipment-picker-table th {
  height: 38px;
  position: sticky;
  top: 0;
  background: #f7f8fa;
  border-bottom: 1px solid #e8ebf0;
  padding: 0 12px;
  color: #697486;
  font-size: 12px;
}
.equipment-picker-table td {
  height: 42px;
  border-bottom: 1px solid #f0f1f4;
  padding: 0 12px;
  font-size: 12px;
  color: #4e5a6c;
}
.equipment-picker-table tbody tr {
  cursor: pointer;
}
.equipment-picker-table tbody tr:hover td {
  background: #f6f8fc;
}
.equipment-picker-dialog > footer {
  height: 50px;
  flex: none;
  border-top: 1px solid #e9ecf1;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  padding: 0 14px;
}
.repair-mask {
  position: fixed;
  inset: 56px 0 0 var(--navigation-expanded-width);
  z-index: 20;
  background: rgba(24, 36, 57, 0.25);
  display: flex;
  justify-content: flex-end;
}
.repair-drawer {
  width: min(760px, calc(100vw - 240px));
  height: 100%;
  background: #f6f7f9;
  display: flex;
  flex-direction: column;
  box-shadow: -8px 0 26px #18243f18;
}
.repair-drawer > header {
  height: 52px;
  flex: none;
  background: #fff;
  border-bottom: 1px solid #e5e9ef;
  padding: 0 16px;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.repair-drawer > header > div {
  display: flex;
  align-items: center;
  gap: 9px;
}
.repair-drawer > header span {
  color: #8792a1;
  font-size: 13px;
}
.repair-drawer > header i {
  color: #c1c8d2;
  font-style: normal;
}
.repair-drawer > header h2 {
  font-size: 16px;
  margin: 0;
}
.repair-drawer > header b {
  font-size: 12px;
  color: #647188;
  font-weight: 500;
  margin-left: 8px;
}
.repair-drawer > header > button {
  border: 0;
  background: none;
  color: #7b8797;
  font-size: 22px;
  cursor: pointer;
}
.repair-form-body {
  padding: 10px 12px 18px;
  overflow: auto;
  flex: 1;
}
.repair-form-group {
  background: white;
  border: 1px solid #edf0f4;
  border-radius: 5px;
  padding: 13px 14px;
  margin-bottom: 10px;
}
.repair-form-group h3 {
  font-size: 14px;
  margin: 0 0 12px;
  padding-bottom: 8px;
  border-bottom: 1px solid #edf0f4;
}
.repair-form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 15px 16px;
}
.repair-form-grid label {
  display: grid;
  grid-template-columns: 112px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: #59677a;
}
.repair-form-grid label > span {
  text-align: right;
  white-space: nowrap;
}
.repair-form-grid label i {
  color: #db4b57;
  font-style: normal;
  margin-left: 3px;
}
.repair-form-grid input,
.repair-form-grid select,
.repair-form-grid textarea {
  width: 100%;
  min-width: 0;
  border: 1px solid #d9dfe8;
  border-radius: 5px;
  background: #fff;
  height: 34px;
  padding: 0 8px;
  font-size: 13px;
  color: #344155;
}
.repair-form-grid textarea {
  height: auto;
  min-height: 72px;
  padding: 7px;
  resize: vertical;
}
.repair-form-grid .repair-span-2 {
  grid-column: 1/-1;
}
.repair-form-grid .repair-span-2 textarea {
  min-height: 70px;
}
.repair-form-grid input[readonly],
.repair-form-grid select:disabled,
.repair-form-grid textarea[readonly] {
  background: #f6f7f9;
  color: #606c7d;
}
.repair-transition-actions {
  display: flex;
  gap: 8px;
  align-items: center;
}
.repair-danger {
  height: 34px;
  border: 1px solid #f1c6c6;
  border-radius: 5px;
  padding: 0 12px;
  background: #fff8f8;
  color: #c74444;
  cursor: pointer;
}
.repair-readonly-state {
  color: #7c8797;
  font-size: 13px;
}
.repair-drawer > footer {
  height: 54px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 16px;
  padding: 0 14px;
  background: #fff;
  border-top: 1px solid #e5e9ef;
}
.repair-drawer > footer > button {
  min-width: 100px;
  height: 36px;
}
.repair-toast {
  position: fixed;
  right: 24px;
  bottom: 24px;
  z-index: 40;
  background: #25344b;
  color: white;
  padding: 10px 16px;
  border-radius: 5px;
  box-shadow: var(--shadow);
  font-size: 13px;
}
.sidebar-collapsed .repair-mask {
  left: 64px;
}
@media (max-width: 1250px) {
  .repair-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .repair-filters {
    width: 100%;
    justify-content: flex-end;
  }
  .repair-mask {
    left: 64px;
  }
  .repair-drawer {
    width: min(760px, calc(100vw - 80px));
  }
}
@media (max-width: 700px) {
  .repair-toolbar {
    gap: 8px;
  }
  .repair-filters {
    flex-wrap: wrap;
    justify-content: flex-start;
  }
  .repair-filters > input {
    width: 100%;
  }
  .repair-form-grid {
    grid-template-columns: 1fr;
  }
  .repair-form-grid .repair-span-2 {
    grid-column: auto;
  }
  .repair-mask {
    inset: 56px 0 0;
  }
  .repair-drawer {
    width: 100%;
  }
  .repair-footer {
    align-items: flex-start;
    height: auto;
    padding: 10px;
    gap: 8px;
    flex-direction: column;
  }
  .repair-footer > div {
    flex-wrap: wrap;
  }
}
.repair-table-scroll th,
.repair-table-scroll td,
.repair-table-scroll td > *,
.repair-row-actions button,
.repair-link,
.repair-text-link,
.repair-footer,
.repair-footer button,
.repair-footer select,
.repair-footer input,
.repair-filters input,
.repair-filters select,
.repair-toolbar button {
  font-size: 13px !important;
}
.repair-attachment-section {
  margin-top: 14px;
  padding-top: 12px;
  border-top: 1px solid #edf0f4;
}
.repair-page {
  height: calc(100vh - 54px);
  min-height: 360px;
  display: flex;
  flex-direction: column;
  padding: 14px 12px 0;
}
.repair-heading,
.repair-queues,
.repair-toolbar {
  flex: none;
}
.repair-table-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.repair-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.repair-footer {
  position: static;
  flex: none;
  width: auto;
  height: 58px;
  border-radius: 0 0 6px 6px;
  border-top: 1px solid #e1e6ed;
  box-shadow: none;
}
.repair-queues {
  height: 36px;
  display: flex;
  align-items: stretch;
  gap: 22px;
  margin: 0 0 10px;
  border-bottom: 1px solid #e3e7ed;
}
.repair-queues button {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 0 7px;
  border: 0;
  background: transparent;
  color: #637084;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.repair-queues button:hover {
  color: var(--brand);
}
.repair-queues button.active {
  color: var(--brand);
  font-weight: 600;
}
.repair-queues button.active:after {
  content: '';
  position: absolute;
  left: 4px;
  right: 4px;
  bottom: -1px;
  height: 2px;
  border-radius: 2px;
  background: var(--brand);
}
.repair-queues button span {
  min-width: 18px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 5px;
  border-radius: 9px;
  background: #eef1f5;
  color: #687588;
  font-size: 11px;
  font-weight: 500;
}
.repair-queues button.active span {
  background: var(--brand-soft);
  color: var(--brand);
}
@media (max-width: 700px) {
  .repair-footer {
    left: 10px;
    right: 10px;
    bottom: 0;
  }
}
.repair-view-wrap {
  position: relative;
  flex: none;
}
.repair-view-trigger,
.repair-column-filter {
  height: 34px;
  padding: 0 11px;
  border: 1px solid #dfe3e9;
  border-radius: 5px;
  background: #fff;
  color: #536176;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.repair-view-trigger:hover,
.repair-view-trigger.active,
.repair-column-filter:hover,
.repair-column-filter.active {
  border-color: color-mix(in srgb, var(--brand) 45%, #dfe3e9);
  color: var(--brand);
  background: #fff;
}
.repair-view-trigger span {
  max-width: 110px;
  overflow: hidden;
  text-overflow: ellipsis;
  vertical-align: bottom;
}
.repair-view-menu {
  position: absolute;
  z-index: 12;
  top: 39px;
  left: 0;
  width: 250px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e2e7ee;
  border-radius: 6px;
  box-shadow: 0 10px 28px rgba(16, 30, 51, 0.14);
}
.repair-view-menu-title {
  padding: 5px 7px 8px;
  color: #687588;
  font-size: 12px;
  font-weight: 600;
}
.repair-saved-view {
  width: 100%;
  height: 34px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #4d5b70;
  text-align: left;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.repair-saved-view:hover,
.repair-saved-view.active {
  background: var(--brand-soft);
  color: var(--brand);
}
.repair-saved-view i {
  font-style: normal;
  color: #97a2b1;
  font-size: 16px;
}
.repair-view-empty {
  padding: 8px;
  color: #8d98a7;
  font-size: 12px;
}
.repair-view-menu form {
  display: flex;
  gap: 6px;
  padding: 8px 3px 2px;
  border-top: 1px solid #edf0f4;
}
.repair-view-menu form input {
  min-width: 0;
  width: 1px;
  flex: 1;
  height: 30px;
  padding: 0 7px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  font-size: 12px;
}
.repair-view-menu form .repair-primary {
  height: 30px;
  padding: 0 8px;
  font-size: 11px;
}
.repair-filter-row th {
  height: 38px;
  padding: 4px 6px;
  background: #fff;
  border-bottom: 1px solid #e5e9ef;
  cursor: default;
}
.repair-filter-row input,
.repair-filter-row select {
  width: 100%;
  height: 27px;
  min-width: 0;
  padding: 0 6px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: #fff;
  color: #536176;
  font: inherit;
  font-size: 12px;
}
.repair-filter-row input::placeholder {
  color: #a0a9b6;
}
.repair-table-scroll tbody tr {
  cursor: pointer;
}
.repair-table-scroll tbody tr.selected td {
  background: #eef4ff;
}
.repair-table-scroll tbody tr.selected:hover td {
  background: #e8f0ff;
}
</style>
