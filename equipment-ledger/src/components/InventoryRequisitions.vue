<script setup lang="ts">
import AppListTabs from './common/AppListTabs.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { request } from '../api';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTag,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  requisitionApi,
  type Requisition,
  type RequisitionChoices,
  type RequisitionLine,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const editing = computed(() => !!route.params.id);
const empty = (): Requisition => ({
  id: '',
  code: '',
  warehouseId: '',
  taskKind: '',
  taskId: '',
  remark: '',
  status: '草稿',
  lines: [],
});
const form = ref(empty()),
  choices = ref<RequisitionChoices>({
    companies: [],
    users: [],
    warehouses: [],
    materials: [],
    bins: [],
    tasks: [],
  }),
  rows = ref<Requisition[]>([]);
const busy = ref(false),
  loading = ref(false),
  error = ref(''),
  query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(10),
  total = ref(0),
  filters = ref<Record<string, string>>({}),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  exportBusy = ref(false);
const fields: Record<string, string> = {
  code: '领料单号',
  warehouseName: '仓库',
  taskKind: '任务类型',
  taskCode: '任务编号',
  applicantName: '申请人',
  status: '状态',
  createdAt: '申请时间',
};
const columns = ref(Object.keys(fields));
const pendingOnly = computed(() => route.query.queue === 'pending');
const selectedRequests = ref<string[]>([]),
  mergedBusy = ref(false);
async function previewMerged(ids = selectedRequests.value) {
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { requests: ids.join(','), tab: 'pending' },
  });
}
function selectRequest(id: string, checked: boolean) {
  selectedRequests.value = checked
    ? [...new Set([...selectedRequests.value, id])]
    : selectedRequests.value.filter((x) => x !== id);
}
const readOnly = computed(
  () =>
    form.value.status !== '草稿' ||
    (!auth.can('inventoryRequisition', form.value.id ? '编辑' : '新增') && !form.value.taskId) ||
    (!!form.value.id && form.value.applicantId !== auth.user?.id),
);
const taskValue = computed({
  get: () =>
    form.value.taskKind && form.value.taskId ? form.value.taskKind + ':' + form.value.taskId : '',
  set: (value: string) => {
    const [kind, id] = value.split(':');
    form.value.taskKind = kind || '';
    form.value.taskId = id || '';
    form.value.warehouseId = '';
  },
});
const selectedTask = computed(() =>
  choices.value.tasks.find((t) => t.id === form.value.taskId && t.kind === form.value.taskKind),
);
const warehouses = computed(() =>
  choices.value.warehouses.filter((w) => w.companyId === selectedTask.value?.companyId),
);
const canReserve = computed(
  () =>
    auth.can('inventoryOutbound', '预留') &&
    ['待预留', '部分预留', '部分发料'].includes(form.value.status),
);
const canRelease = computed(
  () =>
    auth.can('inventoryOutbound', '释放预留') &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status) &&
    !!form.value.reservations?.length,
);
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && !readOnly.value,
  () => busy.value,
);
const canCancel = computed(
  () =>
    !!form.value.id &&
    ['草稿', '待预留', '部分预留', '已预留'].includes(form.value.status) &&
    form.value.lines.every((line) => Number(line.issuedQuantity || 0) === 0) &&
    (form.value.applicantId === auth.user?.id || auth.can('inventoryOutbound', '关闭剩余需求')),
);
const canIssue = computed(
  () =>
    auth.can('inventoryOutbound', '确认出库') &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status),
);
function openIssue() {
  router.push({
    name: 'inventory-outbound-detail',
    params: { id: 'new' },
    query: { requests: form.value.id, tab: 'pending' },
  });
}
const canCloseRemaining = computed(
  () =>
    !!form.value.id &&
    ['待预留', '部分预留', '已预留', '部分发料'].includes(form.value.status) &&
    (form.value.applicantId === auth.user?.id || auth.can('inventoryOutbound', '关闭剩余需求')),
);
async function closeRemaining() {
  try {
    const { value } = await ElMessageBox.prompt(
      '关闭后释放未发数量的预留，已出库记录保留。',
      '关闭剩余需求',
      {
        inputPlaceholder: '填写关闭原因',
        inputValidator: (v) => !!v?.trim() || '请填写原因',
        confirmButtonText: '关闭剩余',
        cancelButtonText: '取消',
        closeOnClickModal: false,
      },
    );
    busy.value = true;
    form.value = (
      await request<{ data: Requisition }>(
        `/inventory/requisitions/${form.value.id}/close-remaining`,
        {
          method: 'POST',
          body: JSON.stringify({ rowVersion: form.value.rowVersion, reason: value }),
        },
      )
    ).data;
    guard.markSaved();
    ElMessage.success('剩余需求已关闭');
  } catch (e) {
    if (e instanceof Error) ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
let revision = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : '操作失败，请重试';
}
function display(row: Requisition, key: string) {
  const v = String(row[key as keyof Requisition] ?? '');
  return key.endsWith('At')
    ? v.replace('T', ' ').slice(0, 19)
    : isDecimalField(key)
      ? formatNumber(v)
      : v;
}
function tone(status: string) {
  return status === '已完成'
    ? 'success'
    : status === '已预留'
      ? 'primary'
      : status === '已取消'
        ? 'info'
        : 'warning';
}
function number(value?: string) {
  return formatNumber(value);
}
function material(line: RequisitionLine) {
  return choices.value.materials.find((m) => m.id === line.materialId);
}
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (editing.value) {
      const options = await requisitionApi.choices();
      if (current !== revision) return;
      choices.value = options.data;
      const result =
        route.params.id === 'new'
          ? empty()
          : (await requisitionApi.detail(String(route.params.id))).data;
      if (current !== revision) return;
      form.value = result;
      if (route.params.id === 'new') {
        form.value.taskKind = String(route.query.taskKind || '');
        form.value.taskId = String(route.query.taskId || '');
      }
      guard.markSaved();
    } else {
      const result = await requisitionApi.list(
        applied.value,
        { ...filters.value, ...(pendingOnly.value ? { pendingOut: '1' } : {}) },
        page.value,
        size.value,
      );
      if (current !== revision) return;
      rows.value = result.data;
      total.value = result.page.total;
    }
  } catch (e) {
    if (current === revision) {
      error.value = message(e);
      rows.value = [];
      total.value = 0;
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(
  () => route.fullPath,
  () => {
    selectedRequests.value = [];
    page.value = 1;
    load();
  },
  { immediate: true },
);
watch([page, size], () => {
  if (!editing.value) load();
});
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
function search() {
  applied.value = query.value;
  page.value = 1;
  load();
}
function reset() {
  query.value = '';
  applied.value = '';
  filters.value = {};
  page.value = 1;
  load();
}
async function back() {
  if (await guard.permitLeave()) router.push({ name: 'inventory-requisitions' });
}
function add() {
  form.value.lines.push({ materialId: '', quantity: '1' });
}
async function save() {
  if (busy.value || readOnly.value) return;
  busy.value = true;
  try {
    const result = await requisitionApi.save(form.value);
    form.value = result.data;
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({
        name: 'inventory-requisition-detail',
        params: { id: result.data.id },
      });
    ElMessage.success('草稿已保存');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function action(type: 'submit' | 'reserve' | 'release' | 'cancel') {
  if (busy.value) return;
  if (guard.dirty.value) {
    ElMessage.warning('请先保存修改');
    return;
  }
  if (type === 'release' || type === 'cancel') {
    try {
      await ElMessageBox.confirm(
        type === 'cancel'
          ? '取消申请并释放已预留库存？'
          : '释放后，这些库存将重新可供其他任务使用。',
        type === 'cancel' ? '取消申请' : '释放预留',
        {
          confirmButtonText: type === 'cancel' ? '取消申请' : '释放预留',
          cancelButtonText: '取消',
          closeOnClickModal: false,
          type: 'warning',
        },
      );
    } catch {
      return;
    }
  }
  if (busy.value) return;
  busy.value = true;
  try {
    form.value = (await requisitionApi.action(form.value, type)).data;
    guard.markSaved();
    if (type === 'reserve' && form.value.status !== '已预留')
      ElMessage.warning(
        form.value.reservations?.length
          ? '库存不足，已预留当前可用数量，剩余数量可稍后继续预留'
          : '暂无可预留库存',
      );
    else
      ElMessage.success(
        { submit: '申请已提交', reserve: '预留完成', release: '已释放预留', cancel: '申请已取消' }[
          type
        ],
      );
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
function toggle(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((x) => x !== key);
  } else columns.value = Object.keys(fields).filter((x) => x === key || columns.value.includes(x));
}
async function exportRows() {
  if (exportBusy.value) return;
  exportBusy.value = true;
  try {
    const result = await requisitionApi.export(applied.value, {
      ...filters.value,
      ...(pendingOnly.value ? { pendingOut: '1' } : {}),
    });
    const quote = (s: string) =>
      '"' + (/^[=+@\-\t\r]/.test(s) ? "'" : '') + s.replaceAll('"', '""') + '"';
    const content = [
      columns.value.map((x) => quote(fields[x]!)).join(','),
      ...result.data.map((row) => columns.value.map((key) => quote(display(row, key))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = '领料申请.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
const poolOpen = ref(false),
  poolRows = ref<Record<string, string>[]>([]);
async function openPool() {
  try {
    poolRows.value = (await requisitionApi.pool(form.value.id)).data.map((v) => ({
      ...v,
      quantity: '0',
    }));
    poolOpen.value = true;
  } catch (e) {
    ElMessage.error(message(e));
  }
}
async function manualReserve() {
  busy.value = true;
  try {
    const selections = poolRows.value.filter((v) => Number(v.quantity) > 0);
    form.value = (await requisitionApi.manualReserve(form.value, selections)).data;
    guard.markSaved();
    poolOpen.value = false;
    ElMessage.success('所选库存已预留');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除这张领料草稿？', '删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    busy.value = true;
    await requisitionApi.delete(form.value);
    guard.markSaved();
    router.push({ name: 'inventory-requisitions' });
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="requisition-page">
    <p v-if="error" class="error" role="alert">
      {{ error }} <ElButton text @click="load">重试</ElButton>
    </p>
    <template v-if="!editing">
      <AppListTabs
        :active="pendingOnly ? 'pending' : 'requests'"
        :items="[
          { key: 'requests', label: '领料申请' },
          ...(auth.can('inventoryOutbound', '确认出库')
            ? [{ key: 'pending', label: '待出库' }]
            : []),
        ]"
        @select="
          (key) =>
            router.push(
              key === 'pending'
                ? { name: 'inventory-outbounds', query: { tab: 'pending' } }
                : { name: 'inventory-requisitions' },
            )
        "
      />
      <div class="toolbar">
        <ElButton
          v-if="pendingOnly && auth.can('inventoryOutbound', '确认出库')"
          type="primary"
          :disabled="!selectedRequests.length"
          :loading="mergedBusy"
          @click="previewMerged()"
          >{{ selectedRequests.length > 1 ? '合并出库' : '出库' }}</ElButton
        >
        <ElButton
          v-if="!pendingOnly && auth.can('inventoryRequisition', '新增')"
          type="primary"
          @click="router.push({ name: 'inventory-requisition-detail', params: { id: 'new' } })"
          >＋ 新增</ElButton
        >
        <form @submit.prevent="search">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput
            v-model="query"
            placeholder="搜索领料单、任务、申请人、仓库"
          /><ElButton text @click="reset">重置</ElButton>
        </form>
      </div>
      <div class="card" :aria-busy="loading">
        <div class="scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th v-if="pendingOnly" style="width: 40px">
                  <input
                    type="checkbox"
                    aria-label="选择当前页"
                    :checked="!!rows.length && rows.every((x) => selectedRequests.includes(x.id))"
                    @change="
                      (event) => {
                        selectedRequests = (event.target as HTMLInputElement).checked
                          ? rows.map((x) => x.id)
                          : [];
                      }
                    "
                  />
                </th>
                <th v-for="key in columns" :key="key">{{ fields[key] }}</th>
                <th class="action">操作</th>
              </tr>
              <tr v-if="filterOpen">
                <th v-if="pendingOnly" />
                <th v-for="key in columns" :key="key">
                  <ElInput v-model="filters[key]" :placeholder="'筛选' + fields[key]" clearable />
                </th>
                <th class="action" />
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td v-if="pendingOnly">
                  <input
                    type="checkbox"
                    :aria-label="'选择' + row.code"
                    :checked="selectedRequests.includes(row.id)"
                    @change="(e) => selectRequest(row.id, (e.target as HTMLInputElement).checked)"
                  />
                </td>
                <td v-for="key in columns" :key="key">
                  <ElTag v-if="key === 'status'" :type="tone(row.status)">{{ row.status }}</ElTag
                  ><ElButton
                    v-else-if="key === 'code'"
                    link
                    type="primary"
                    @click="
                      router.push({ name: 'inventory-requisition-detail', params: { id: row.id } })
                    "
                    >{{ row.code }}</ElButton
                  ><span v-else>{{ display(row, key) }}</span>
                </td>
                <td class="action">
                  <ElButton
                    v-if="pendingOnly && auth.can('inventoryOutbound', '确认出库')"
                    link
                    type="primary"
                    :disabled="mergedBusy"
                    @click="previewMerged([row.id])"
                    >出库</ElButton
                  >
                  <ElButton
                    link
                    type="primary"
                    @click="
                      router.push({ name: 'inventory-requisition-detail', params: { id: row.id } })
                    "
                    >{{ row.status === '草稿' ? '编辑' : '查看' }}</ElButton
                  >
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td :colspan="columns.length + 1 + (pendingOnly ? 1 : 0)" class="empty">
                  {{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无领料申请' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="list-footer">
          <span>共 {{ total }} 条</span
          ><AppPagination
            v-model:page="page"
            v-model:page-size="size"
            :total="total"
          /><ListToolbarIcons
            class="footer-tools"
            mode="footer"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            :can-export="auth.can('inventoryRequisition', '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><label v-for="(label, key) in fields" :key="key"
                ><input
                  type="checkbox"
                  :checked="columns.includes(key)"
                  :disabled="columns.length === 1 && columns.includes(key)"
                  @change="toggle(key)"
                />{{ label }}</label
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </template>
    <template v-else>
      <header>
        <AppBackButton :disabled="busy" @click="back" /><strong>{{
          form.id ? form.code : '新增领料申请'
        }}</strong
        ><ElTag :type="tone(form.status)">{{ form.status }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >关联任务<span v-if="readOnly">{{ form.taskKind }} · {{ form.taskCode }}</span
            ><ElSelect
              v-else
              v-model="taskValue"
              filterable
              placeholder="选择维修、保养或点巡检任务"
              ><ElOption
                v-for="t in choices.tasks"
                :key="t.kind + ':' + t.id"
                :value="t.kind + ':' + t.id"
                :label="t.kind + ' · ' + t.code + ' ' + t.name" /></ElSelect></label
          ><label
            >仓库<span v-if="readOnly">{{
              choices.warehouses.find((w) => w.id === form.warehouseId)?.name || form.warehouseName
            }}</span
            ><ElSelect
              v-else
              v-model="form.warehouseId"
              filterable
              placeholder="请选择任务所属公司仓库"
              ><ElOption
                v-for="w in warehouses"
                :key="w.id"
                :value="w.id"
                :label="w.name" /></ElSelect></label
          ><label
            >申请人<span>{{ form.applicantName || auth.currentUser }}</span></label
          ><label
            >备注<ElInput v-model="form.remark" :readonly="readOnly" maxlength="1000"
          /></label>
        </div>
        <div class="detail-heading">
          <strong>领料明细</strong
          ><ElButton
            v-if="!readOnly"
            type="primary"
            plain
            :disabled="!form.warehouseId"
            @click="add"
            >＋ 添加物料</ElButton
          >
        </div>
        <div class="line-scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th class="material">物料</th>
                <th>单位</th>
                <th class="numeric">申请数量</th>
                <th v-if="readOnly" class="numeric">预留数量</th>
                <th v-if="readOnly" class="numeric">已发数量</th>
                <th v-if="!readOnly" class="action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, index) in form.lines" :key="line.id || index">
                <td>
                  <span v-if="readOnly">{{ line.materialCode }} {{ line.materialName }}</span
                  ><ElSelect v-else v-model="line.materialId" filterable placeholder="选择物料"
                    ><ElOption
                      v-for="m in choices.materials"
                      :key="m.id"
                      :value="m.id"
                      :label="m.code + ' ' + m.name"
                  /></ElSelect>
                </td>
                <td>{{ line.unit || material(line)?.unit }}</td>
                <td class="numeric">
                  <span v-if="readOnly">{{ number(line.quantity) }}</span
                  ><AppDecimalInput v-else v-model="line.quantity" inputmode="decimal" />
                </td>
                <td v-if="readOnly" class="numeric">{{ number(line.reservedQuantity) }}</td>
                <td v-if="readOnly" class="numeric">{{ number(line.issuedQuantity) }}</td>
                <td v-if="!readOnly" class="action">
                  <ElButton link type="danger" @click="form.lines.splice(index, 1)">删除</ElButton>
                </td>
              </tr>
              <tr v-if="!form.lines.length">
                <td :colspan="readOnly ? 5 : 4" class="empty">添加本任务需要的物料及数量</td>
              </tr>
            </tbody>
          </table>
        </div>
        <template v-if="form.reservations?.length"
          ><div class="detail-heading">
            <strong>预留明细</strong><span>预留数量不扣减在库数量</span>
          </div>
          <div class="line-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th>库位</th>
                  <th>批次号</th>
                  <th class="numeric">预留数量</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in form.reservations" :key="r.id">
                  <td>{{ r.materialName }}</td>
                  <td>{{ r.binName }}</td>
                  <td>{{ r.batchCode }}</td>
                  <td class="numeric">{{ number(r.remainingQuantity) }}</td>
                </tr>
              </tbody>
            </table>
          </div></template
        >
        <div v-for="issue in form.issues" :key="issue.id" class="issue-history">
          <div class="detail-heading">
            <strong>{{ issue.code }}</strong
            ><ElTag :type="issue.reversed ? 'info' : 'success'">{{
              issue.reversed ? '已冲销' : '已出库'
            }}</ElTag
            ><span v-if="issue.groupCode && issue.groupCode !== issue.code"
              >合并单号：{{ issue.groupCode }}</span
            ><span
              >{{ issue.actorName }} · {{ issue.createdAt.replace('T', ' ').slice(0, 19) }}</span
            >
          </div>
          <div class="line-scroll">
            <table class="business-table">
              <thead>
                <tr>
                  <th>物料</th>
                  <th>库位</th>
                  <th>批次号</th>
                  <th class="numeric">发料数量</th>
                  <th class="numeric">金额</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="a in issue.allocations" :key="a.id">
                  <td>{{ a.materialName }}</td>
                  <td>{{ a.binName }}</td>
                  <td>{{ a.batchCode }}</td>
                  <td class="numeric">{{ number(a.quantity) }}</td>
                  <td class="numeric">{{ formatNumber(a.amount) }}</td>
                </tr>
              </tbody>
            </table>
          </div>
          <p v-if="auth.can('inventoryRequisition', '查看金额')" class="issue-total">
            合计 ¥ {{ formatNumber(issue.totalAmount) }}
          </p>
        </div>
        <div v-if="form.events?.length" class="records">
          <strong>单据记录</strong>
          <p v-for="(event, index) in form.events" :key="index">
            <span class="dot" />{{ event.actorName }} · {{ event.action
            }}<span v-if="event.reason"> · {{ event.reason }}</span
            ><time>{{ event.createdAt.replace('T', ' ').slice(0, 19) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readOnly ? '返回' : '取消' }}</ElButton
        ><template v-if="!error && !loading"
          ><ElButton v-if="!readOnly" :loading="busy" @click="save">保存草稿</ElButton
          ><ElButton
            v-if="!readOnly"
            type="primary"
            :disabled="busy || !form.id || guard.dirty.value"
            @click="action('submit')"
            >提交</ElButton
          ><ElButton v-if="canCloseRemaining && !canCancel" :disabled="busy" @click="closeRemaining"
            >关闭剩余需求</ElButton
          ><ElButton v-if="canCancel" :disabled="busy" @click="action('cancel')">取消申请</ElButton
          ><ElButton v-if="canRelease" :disabled="busy" @click="action('release')"
            >释放预留</ElButton
          ><ElButton v-if="canIssue" type="primary" :disabled="busy" @click="openIssue"
            >出库</ElButton
          ><ElButton
            v-if="form.id && form.status === '草稿' && auth.can('inventoryRequisition', '删除')"
            type="danger"
            plain
            :disabled="busy"
            @click="removeDraft"
            >删除</ElButton
          ><ElButton v-if="canReserve" :disabled="busy" @click="openPool">选择批次</ElButton
          ><ElButton v-if="canReserve" type="primary" :loading="busy" @click="action('reserve')">{{
            form.status === '部分预留' ? '继续预留' : '预留库存'
          }}</ElButton></template
        ></AppFormFooter
      >
    </template>
    <AppDialog
      v-model="poolOpen"
      title="选择库位与批次"
      width="min(860px,calc(100vw - 32px))"
      :busy="busy"
    >
      <p class="issue-hint">填写本次预留数量；留为 0 的行不预留。也可关闭窗口后使用自动预留。</p>
      <div class="line-scroll">
        <table class="issue-table">
          <thead>
            <tr>
              <th>物料</th>
              <th>库位</th>
              <th>批次</th>
              <th>有效期</th>
              <th>可用数量</th>
              <th>预留数量</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="row in poolRows" :key="row.lineId + ':' + row.balanceId">
              <td>{{ row.materialName }}</td>
              <td>{{ row.binName }}</td>
              <td>{{ row.batchCode }}</td>
              <td>{{ row.expiryDate }}</td>
              <td>{{ number(row.availableQuantity) }}</td>
              <td><AppDecimalInput v-model="row.quantity" inputmode="decimal" /></td>
            </tr>
            <tr v-if="!poolRows.length">
              <td colspan="6" class="empty">暂无可用库存</td>
            </tr>
          </tbody>
        </table>
      </div>
      <template #footer
        ><ElButton :disabled="busy" @click="poolOpen = false">取消</ElButton
        ><ElButton
          type="primary"
          :loading="busy"
          :disabled="!poolRows.some((v) => Number(v.quantity) > 0)"
          @click="manualReserve"
          >预留</ElButton
        ></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.list-footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}

.issue-content {
  max-height: 55vh;
  overflow: auto;
}
.issue-table {
  min-width: 660px;
}
.issue-hint {
  margin: 0 0 16px;
  color: var(--text);
  line-height: 1.6;
}
.issue-buttons {
  display: flex;
  justify-content: center;
  gap: 16px;
}
.issue-buttons :deep(.el-button) {
  margin-left: 0;
  min-width: 100px;
}
.issue-total {
  text-align: right;
  font-weight: 700;
  color: var(--brand);
}
.requisition-page {
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  min-height: 0;
  font-size: 13px;
  color: var(--text);
}
.toolbar,
.toolbar form,
header,
.list-footer,
.detail-heading {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar {
  margin-bottom: 0;
}
.toolbar form {
  margin-left: auto;
}
.card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  background: white;
  overflow: hidden;
}
.scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
table {
  width: 100%;
  min-width: 850px;
  border-collapse: separate;
  border-spacing: 0;
  table-layout: fixed;
}
th,
td {
  padding: 12px;
  font-size: 13px;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  border-bottom: 1px solid #e4e9f0;
}
th {
  background: #f5f7fb;
  font-weight: 700;
}
td {
  background: white;
}
tbody tr:hover td {
  background: #f8faff;
}
.action {
  position: sticky;
  right: 0;
  width: 100px;
  z-index: 1;
  box-shadow: -1px 0 #e4e9f0;
}
.list-footer {
  height: 46px;
  padding: 0 12px;
  flex: none;
  border-top: 1px solid #e4e9f0;
}
.footer-tools {
  margin-left: auto;
}
header {
  padding: 8px 12px;
  background: #fff;
  border-bottom: 1px solid #e4e9f0;
}
header strong {
  font-size: 15px;
}
.editor-scroll {
  flex: 1;
  min-height: 0;
  background: white;
  padding: 18px;
  overflow: auto;
}
.metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 24px;
}
.metadata label {
  display: grid;
  grid-template-columns: 70px 1fr;
  gap: 10px;
  align-items: center;
}
.detail-heading {
  margin: 24px 0 12px;
}
.detail-heading span {
  margin-left: auto;
}
.line-scroll {
  overflow: auto;
  border: 1px solid #e4e9f0;
  border-radius: 5px;
}
.material {
  width: 40%;
}
.numeric {
  text-align: right;
  font-variant-numeric: tabular-nums;
}
.empty {
  text-align: center;
  padding: 40px;
}
.records {
  margin-top: 24px;
}
.records p {
  display: flex;
  align-items: center;
  gap: 10px;
}
.dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--brand);
}
.records time {
  margin-left: 12px;
}
.error {
  color: #c13449;
  margin: 0;
}
:deep(.el-input),
:deep(.el-select),
:deep(.el-button) {
  font-size: 13px;
}
@media (max-width: 850px) {
  .metadata {
    grid-template-columns: 1fr;
  }
  .toolbar {
    flex-wrap: wrap;
  }
  .toolbar form {
    margin-left: 0;
    max-width: 100%;
  }
  .editor-scroll {
    padding: 14px;
  }
}
</style>
