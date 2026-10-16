<script setup lang="ts">
import AppListTabs from './common/AppListTabs.vue';
import { ElMessage } from 'element-plus';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import AppAttachments from './common/AppAttachments.vue';
import { ElButton, ElSelect, ElOption, ElInput, ElCheckbox, ElInputNumber } from 'element-plus';

import type { BusinessSort } from './common/BusinessTable.types';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import RepairUserPicker from './common/RepairUserPicker.vue';
import { useRoute, useRouter, onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
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
const uploading = ref(false);
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
const sortKey = ref<keyof WorkOrder | null>('reportedAt'),
  sortDirection = ref<'asc' | 'desc' | null>('desc');
const widths = ref<Record<string, number>>(readWidths());
const showColumns = ref(false);
const allColumns: { key: keyof WorkOrder; label: string; width: number; required?: boolean }[] = [
  { key: 'orderNo', label: '工单编号', width: 150, required: true },
  { key: 'equipmentCode', label: '设备编号', width: 145 },
  { key: 'equipmentName', label: '设备名称', width: 150 },
  { key: 'status', label: '状态', width: 110 },
  { key: 'description', label: '故障描述', width: 260 },
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
    const saved = JSON.parse(localStorage.getItem('eam-repair-columns') || 'null') as
      string[] | null;
    const stored = saved
      ? [...new Set(saved.map((key) => (key === 'faultAt' ? 'reportedAt' : key)))]
      : null;
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
  return key === 'reportedAt' ? formatListDateTime(row[key]) : String(row[key] ?? '');
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
watch([page, pageSize, activeQueue, sortKey, sortDirection, applied, advancedConditions], () =>
  load(),
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
  advancedConditions.value = [];
  activeQueue.value = 'all';
  search();
}
function onTableSort(sort: BusinessSort) {
  sortKey.value = sort.order ? (sort.prop as keyof WorkOrder) : null;
  sortDirection.value =
    sort.order === 'ascending' ? 'asc' : sort.order === 'descending' ? 'desc' : null;
  page.value = 1;
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
    sortBy: sortKey.value || undefined,
    direction: sortDirection.value || undefined,
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
function notice(message: string, type: 'success' | 'error' | 'info' = 'error') {
  ElMessage({ message: message, type });
}
function openCreate() {
  active.value = {
    id: '',
    orderNo: '',
    equipmentCode: '',
    equipmentName: '',
    department: '',
    faultType: '',
    severity: '',
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
    !f.faultType ||
    !f.severity ||
    !f.reporter ||
    !f.description.trim()
  ) {
    notice('请填写设备、故障类型、故障等级、报修人和故障描述');
    return;
  }
  submitting.value = true;
  try {
    await api.saveRepair(f, isNew.value);
    reportGuard.markSaved();
    await load();
    drawer.value = false;
    notice(isNew.value ? '报修已提交' : '工单已保存', 'success');
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
    notice('工单状态已更新', 'success');
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
    notice('派工完成，等待接单', 'success');
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
const equipmentPage = ref(1),
  equipmentSize = ref(20),
  equipmentTotal = ref(0);
const equipmentKeyword = ref('');
let equipmentRevision = 0;
async function loadEquipmentChoices() {
  if (!equipmentPickerOpen.value) return;
  const revision = ++equipmentRevision;
  equipmentLoading.value = true;
  try {
    const res = await api.listEquipment({
      page: equipmentPage.value,
      size: equipmentSize.value,
      keyword: equipmentKeyword.value,
    });
    if (revision !== equipmentRevision) return;
    equipmentChoices.value = res.data;
    equipmentTotal.value = res.page.total;
  } catch (e) {
    if (revision !== equipmentRevision) return;
    equipmentChoices.value = [];
    equipmentTotal.value = 0;
    notice(e instanceof Error ? e.message : '设备加载失败');
  } finally {
    if (revision === equipmentRevision) equipmentLoading.value = false;
  }
}
function searchEquipment() {
  equipmentKeyword.value = equipmentSearch.value.trim();
  if (equipmentPage.value !== 1) equipmentPage.value = 1;
  else loadEquipmentChoices();
}
function openEquipmentPicker() {
  equipmentSearch.value = equipmentKeyword.value = '';
  equipmentPage.value = 1;
  equipmentPickerOpen.value = true;
  loadEquipmentChoices();
}
watch([equipmentPage, equipmentSize], loadEquipmentChoices);
watch(equipmentPickerOpen, (open) => {
  if (!open) {
    equipmentRevision++;
    equipmentLoading.value = false;
  }
});
const filteredEquipment = computed(() => equipmentChoices.value);
const localImageUrls = ref<Record<string, string>>({});
function attachmentImageUrl(file: RepairAttachment) {
  return localImageUrls.value[file.id] || file.url || file.dataUrl || '';
}
function clearLocalImageUrls() {
  Object.values(localImageUrls.value).forEach(URL.revokeObjectURL);
  localImageUrls.value = {};
}
watch(drawer, (open) => {
  if (!open) clearLocalImageUrls();
});
onUnmounted(clearLocalImageUrls);
const reportAttachmentFiles = computed(() =>
  (active.value?.attachments || []).map((file) => ({
    ...file,
    previewUrl: attachmentImageUrl(file),
  })),
);
function chooseEquipment(item: { id: string; code: string; name: string; department: string }) {
  if (!active.value) return;
  active.value.equipmentCode = item.code;
  active.value.equipmentName = item.name;
  active.value.department = item.department;
  equipmentPickerOpen.value = false;
}
async function addFiles(files: File[]) {
  if (!active.value) return;

  uploading.value = true;
  try {
    for (const file of files) {
      const result = await api.uploadRepairFile(file);
      if (file.type.startsWith('image/'))
        localImageUrls.value[result.data.id] = URL.createObjectURL(file);
      active.value.attachments.push(result.data);
    }
  } catch (e) {
    notice(e instanceof Error ? e.message : '附件上传失败');
  } finally {
    uploading.value = false;
  }
}
function removeAttachment(id: string) {
  if (localImageUrls.value[id]) {
    URL.revokeObjectURL(localImageUrls.value[id]);
    delete localImageUrls.value[id];
  }
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
  endResize();
});
onMounted(() => {
  load();
  loadFaultTypes();
});
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => filteredEquipment.value;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    advancedOpen,
    () => advancedConditions.value,
    (value) => {
      applyAdvanced(value);
    },
  );
</script>

<template>
  <section class="content repair-page">
    <RepairUserPicker
      v-model="dispatchPicker"
      title="派工"
      :busy="submitting"
      @confirm="confirmDispatch"
    />
    <AppListTabs
      class="repair-queues"
      :active="activeQueue"
      :items="
        queues.map((queue) => ({
          key: queue.key,
          label: queue.label,
          count: queueCounts[queue.key],
        }))
      "
      @select="selectQueue($event as QueueKey)"
    />
    <AppListToolbar class="repair-toolbar">
      <div class="repair-actions">
        <ElButton
          v-if="props.grantedActions.includes('报修')"
          class="repair-primary"
          @click="openCreate"
          type="primary"
          ><span><AppIcon name="Plus" /></span>报修
        </ElButton>
      </div>
      <form class="repair-filters" @submit.prevent="search">
        <ListSearchInput
          v-model="query.keyword"
          placeholder="搜索工单编号、设备、故障描述、部门、人员"
        />
        <ElButton
          class="advanced-filter-trigger"
          :class="{ active: advancedConditions.length }"
          @click="showFilter($event.currentTarget as HTMLElement)"
          native-type="button"
        >
          添加筛选<span v-if="advancedConditions.length">
            ({{ advancedConditions.length }})</span
          ></ElButton
        >
        <ElButton class="repair-reset" @click="reset" native-type="button" text>重置</ElButton>
      </form>
    </AppListToolbar>
    <AppliedFilterChips
      editable
      @edit="(condition, anchor) => showFilter(anchor, condition.field)"
      :conditions="advancedConditions"
      :fields="advancedFields"
      @change="replaceFilters"
    />
    <FilterConditionPopover
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      v-model="advancedOpen"
      :fields="advancedFields"
      :conditions="advancedConditions"
      @confirm="confirmFilter"
    />
    <div class="repair-table-card">
      <div class="repair-table-scroll">
        <AppBusinessTable
          :default-sort="{ prop: 'reportedAt', order: 'descending' }"
          :sortable="true"
          @sort-change="onTableSort"
          :rows="pageRows"
          :pagination="false"
          :fill="true"
          :framed="false"
          class="business-table"
          :row-class="(row, tableRowIndex) => ({ selected: selectedRowId === row.id })"
          @column-resize="
            (key, width) => {
              widths[key] = width;
            }
          "
          @row-click="
            (row) => {
              selectedRowId = row.id;
            }
          "
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn
            show-overflow-tooltip
            :label="col.label"
            v-for="col in columns"
            :min-width="150"
            :width="width(col.key)"
            :prop="col.key"
            :key="col.key"
            :sortable="true ? 'custom' : false"
            ><template #header
              ><span class="business-column-label" :title="col.label">{{
                col.label
              }}</span> </template
            ><template #default="{ row: row, $index: tableRowIndex }">
              <ElButton
                v-if="col.key === 'orderNo'"
                class="repair-link"
                @click.stop="openDetail(row)"
                type="primary"
                link
                >{{ row.orderNo }}</ElButton
              ><ElButton
                v-else-if="col.key === 'equipmentName'"
                class="repair-text-link"
                type="primary"
                link
                >{{ row.equipmentName }}</ElButton
              ><span
                v-else-if="col.key === 'status'"
                class="repair-status"
                :class="'s-' + row.status"
                >{{ row.status }}</span
              ><span v-else>{{ listCell(row, col.key) }}</span>
            </template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn
            class-name="repair-row-actions list-action-column"
            fixed="right"
            :width="170"
            :min-width="156"
            :resizable="false"
            ><template #header><div class="table-header-label">操作</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">
              <ElButton
                link
                type="primary"
                v-if="row.status === '待派工' && props.grantedActions.includes('编辑')"
                @click.stop="openEdit(row)"
              >
                编辑</ElButton
              ><ElButton link type="primary" @click.stop="rowAction(row)">{{
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
              }}</ElButton>
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!pageRows.length">
              {{ loading ? '正在加载…' : loadError || '没有符合条件的工单' }}
            </div></template
          ></AppBusinessTable
        >
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
          :columns-open="showColumns"
          @toggle-columns="showColumns = !showColumns"
          @export="exportCsv"
          ><template #columns
            ><ElCheckbox
              v-for="col in allColumns"
              :key="col.key"
              :model-value="visibleKeys.includes(col.key)"
              :disabled="col.required"
              @change="
                visibleKeys = visibleKeys.includes(col.key)
                  ? visibleKeys.filter((k) => k !== col.key)
                  : allColumns
                      .filter((c) => visibleKeys.includes(c.key) || c.key === col.key)
                      .map((c) => c.key)
              "
              >{{ col.label }}</ElCheckbox
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
            <ElButton aria-label="关闭" :disabled="submitting || uploading" @click="closeReport"
              ><AppIcon name="Close"
            /></ElButton>
          </header>
          <div v-if="active" class="repair-form-body">
            <section class="repair-form-group">
              <h3>故障信息</h3>
              <div class="repair-form-grid">
                <label><span>报修人</span><ElInput v-model="active.reporter" readonly /></label>
                <label
                  ><span>设备编号<i>*</i></span>
                  <div class="repair-device-select">
                    <ElInput
                      :model-value="active.equipmentCode"
                      readonly
                      placeholder="请从设备台账选择"
                      @click="!detailMode && openEquipmentPicker()"
                    /><ElButton
                      v-if="!detailMode"
                      class="repair-secondary"
                      @click="openEquipmentPicker"
                      native-type="button"
                    >
                      选择
                    </ElButton>
                  </div></label
                ><label
                  ><span>设备名称<i>*</i></span
                  ><ElInput
                    :model-value="active.equipmentName"
                    readonly
                    placeholder="选择设备后自动带出"
                    @click="!detailMode && openEquipmentPicker()" /></label
                ><label
                  ><span>所属部门</span
                  ><ElInput
                    v-model="active.department"
                    readonly
                    placeholder="选择设备后自动带出" /></label
                ><label
                  ><span>故障类型<i>*</i></span
                  ><TreeSelect
                    v-if="!detailMode"
                    v-model="active.faultType"
                    :nodes="faultTypeNodes"
                    placeholder="请选择故障类型" /><ElInput
                    v-else
                    v-model="active.faultType"
                    readonly /></label
                ><label
                  ><span>故障等级<i>*</i></span
                  ><ElSelect v-model="active.severity" :disabled="detailMode" required
                    ><ElOption value="" disabled :label="'请选择故障等级'" /><ElOption
                      :value="'一般'"
                      :label="'一般'" /><ElOption :value="'紧急'" :label="'紧急'" /><ElOption
                      :value="'特急'"
                      :label="'特急'" /></ElSelect></label
                ><label v-if="!isNew"
                  ><span>维修人</span
                  ><ElInput
                    v-model="active.assignee"
                    :readonly="detailMode && active.status !== '待派工'"
                    placeholder="派工时填写维修人" /></label
                ><label class="repair-span-2"
                  ><span>故障描述<i>*</i></span
                  ><ElInput
                    v-model="active.description"
                    :readonly="detailMode"
                    :rows="3"
                    placeholder="描述故障现象、发生经过及现场情况"
                    type="textarea" /></label
                ><label v-if="!isNew" class="repair-span-2"
                  ><span>维修措施</span
                  ><ElInput
                    v-model="active.repairAction"
                    :readonly="detailMode && active.status !== '处理中'"
                    :rows="3"
                    placeholder="记录检查结果和维修措施"
                    type="textarea" /></label
                ><label v-if="!isNew"
                  ><span>停机时长（分钟）</span
                  ><ElInputNumber
                    v-model="active.downtimeMinutes"
                    :min="0"
                    :readonly="detailMode && active.status !== '处理中'"
                    controls-position="right" /></label
                ><label v-if="detailMode"
                  ><span>当前状态</span><ElInput :model-value="active.status" readonly
                /></label>
              </div>
              <div class="repair-attachment-section">
                <AppAttachments
                  :files="reportAttachmentFiles"
                  title="附件与图片"
                  :readonly="detailMode"
                  :disabled="submitting"
                  :uploading="uploading"
                  @select="addFiles"
                  @remove="removeAttachment"
                />
              </div>
            </section>
            <section class="repair-form-group" v-if="detailMode">
              <h3>处理动作</h3>
              <div class="repair-transition-actions">
                <template v-if="active.status === '待派工'"
                  ><ElButton
                    v-if="props.grantedActions.includes('派工')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'dispatch')"
                    type="primary"
                  >
                    派工并开始处理</ElButton
                  ><ElButton
                    v-if="props.grantedActions.includes('撤回')"
                    class="repair-danger"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'cancel')"
                    type="danger"
                    link
                  >
                    撤销工单
                  </ElButton></template
                ><template v-else-if="active.status === '处理中'"
                  ><ElButton
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'complete')"
                    type="primary"
                  >
                    维修完成，提交验收</ElButton
                  ><ElButton
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-secondary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'suspend')"
                  >
                    挂起
                  </ElButton></template
                ><template v-else-if="active.status === '挂起'"
                  ><ElButton
                    v-if="props.grantedActions.includes('处理')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'resume')"
                    type="primary"
                  >
                    恢复处理
                  </ElButton></template
                ><template v-else-if="active.status === '待验收'"
                  ><ElButton
                    v-if="props.grantedActions.includes('验收')"
                    class="repair-primary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'accept')"
                    type="primary"
                  >
                    验收通过</ElButton
                  ><ElButton
                    v-if="props.grantedActions.includes('验收')"
                    class="repair-secondary"
                    :disabled="submitting || uploading"
                    @click="transition(active, 'return')"
                  >
                    退回维修
                  </ElButton></template
                ><span v-else class="repair-readonly-state">当前工单已{{ active.status }}</span>
              </div>
            </section>
          </div>
          <footer>
            <ElButton
              class="repair-secondary"
              :disabled="submitting || uploading"
              @click="closeReport"
              >{{ detailMode ? '关闭' : '取消' }}</ElButton
            ><ElButton
              v-if="!detailMode"
              class="repair-primary"
              :disabled="submitting || uploading"
              @click="save"
              type="primary"
              >{{ submitting ? '提交中…' : isNew ? '提交' : '保存' }}</ElButton
            >
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
            ><ElButton aria-label="关闭" @click="equipmentPickerOpen = false" native-type="button"
              ><AppIcon name="Close"
            /></ElButton>
          </header>
          <div class="equipment-picker-toolbar">
            <ElInput
              v-model="equipmentSearch"
              autofocus
              placeholder="搜索设备编号、名称或型号"
              @keyup.enter="searchEquipment"
            /><ElButton class="repair-secondary" @click="searchEquipment">查询</ElButton
            ><span>共 {{ equipmentTotal }} 项</span>
          </div>
          <div class="equipment-picker-table">
            <AppBusinessTable
              :rows="filteredEquipment"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              @row-double-click="
                (item) => {
                  chooseEquipment(item);
                }
              "
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">设备编号</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.code
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn :min-width="210"
                ><template #header><div class="table-header-label">设备名称</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">
                  <b>{{ item.name }}</b>
                </template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">设备分类</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.category || ''
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">规格型号</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.model || ''
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">所属部门</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.department || ''
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow2} -->
              <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                ><template #header><div class="table-header-label">操作</div></template
                ><template #default="{ row: item, $index: tableRowIndex }"
                  ><ElButton class="repair-link" @click="chooseEquipment(item)" type="primary" link>
                    选择
                  </ElButton></template
                ></ElTableColumn
              ><template #empty
                ><div v-if="equipmentLoading">正在加载设备台账…</div>
                <div v-else-if="!filteredEquipment.length">没有匹配设备</div></template
              ></AppBusinessTable
            >
          </div>
          <footer>
            <AppPagination
              v-model:page="equipmentPage"
              v-model:page-size="equipmentSize"
              :total="equipmentTotal"
            />
            <ElButton class="repair-secondary" @click="equipmentPickerOpen = false">取消</ElButton>
          </footer>
        </section>
      </div></Transition
    >
  </section>
</template>

<style scoped>
.repair-toolbar button,
.repair-table-scroll :deep(th),
.repair-table-scroll :deep(td),
.repair-row-actions button,
.repair-link,
.repair-text-link,
.repair-footer,
.repair-footer button {
  font-size: 13px;
}
.repair-page {
  padding-top: 20px;
}
.repair-actions,
.repair-filters {
  display: flex;
  align-items: center;
  gap: 8px;
}
.repair-page button {
  font: inherit;
}
.repair-primary,
.repair-secondary,
.repair-reset,
.repair-footer button {
  height: 34px;
  border-radius: 5px;
  padding: 0 12px;
  white-space: nowrap;
  cursor: pointer;
  font-size: 12px;
}
.repair-primary {
  border: 1px solid var(--brand);
  background: var(--brand);
  color: white;
}
.repair-primary span {
  font-size: 17px;
  margin-right: 4px;
}
.repair-secondary {
  border: 1px solid #dfe3e9;
  background: white;
  color: #465164;
}
.repair-secondary:disabled {
  color: #aab2bf;
  cursor: not-allowed;
}
.repair-reset {
  border: 0;
  background: transparent;
  color: #727e90;
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
.repair-table-scroll :deep(th) {
  position: relative;
  cursor: pointer;
}
.repair-table-scroll :deep(th > span) {
  display: flex;
  justify-content: space-between;
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
.repair-device-select {
  display: flex;
  gap: 7px;
  min-width: 0;
}
.repair-device-select button {
  height: 34px;
  flex: none;
  color: var(--brand);
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
.equipment-picker-table :deep(th) {
  position: sticky;
  top: 0;
}
.equipment-picker-table :deep(tbody tr) {
  cursor: pointer;
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
.repair-form-grid .repair-span-2 {
  grid-column: 1/-1;
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

.sidebar-collapsed .repair-mask {
  left: 64px;
}
@media (max-width: 1250px) {
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
  .repair-filters {
    flex-wrap: wrap;
    justify-content: flex-start;
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
.repair-table-scroll :deep(th),
.repair-table-scroll :deep(td),
.repair-table-scroll :deep(td > *),
.repair-row-actions button,
.repair-link,
.repair-text-link,
.repair-footer,
.repair-footer button,
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
.repair-queues {
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
.repair-table-scroll :deep(tbody tr) {
  cursor: pointer;
}
.equipment-picker-dialog footer {
  justify-content: space-between;
  gap: 16px;
}
</style>
