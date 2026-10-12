<script setup lang="ts">
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber } from '../utils/number';
import TaskMaterials from './TaskMaterials.vue';
import { workspaceReturn } from '../utils/workspaceNavigation';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import AppBackButton from './common/AppBackButton.vue';
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElRate } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppLoadState from './common/AppLoadState.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import RepairUserPicker from './common/RepairUserPicker.vue';
import TreeSelect from './TreeSelect.vue';
import { useBaseDataStore } from '../stores/baseData';
import * as api from '../api';
const route = useRoute(),
  router = useRouter(),
  base = useBaseDataStore();
const detail = ref<api.WorkflowDetail | null>(null),
  busy = ref(false),
  loading = ref(true),
  error = ref(''),
  pageMode = ref<'detail' | 'process'>('detail');
const order = computed(() => detail.value?.order),
  id = computed(() => String(route.params.id));
const picker = ref(false),
  pickerPurpose = ref<'dispatch' | 'transfer' | 'collaborators'>('dispatch'),
  dialog = ref('');
const reason = ref(''),
  vendor = ref(''),
  score = ref(5),
  acceptResult = ref('accept'),
  comment = ref(''),
  reviewAttachments = ref<api.RepairAttachment[]>([]);
const stockRows = ref<api.StockRow[]>([]),
  stockId = ref(''),
  quantity = ref(1),
  materialId = ref(''),
  installedId = ref(''),
  replacementMode = ref<'install' | 'remove'>('install'),
  materialPanel = ref(false);
const form = ref({
  faultCause: '',
  faultType: '',
  repairMethod: '自修',
  content: '',
  result: '已解决',
  prevention: '',
  collaborators: [] as { id: string; name: string }[],
  attachments: [] as api.RepairAttachment[],
});
const { markSaved, permitLeave } = useUnsavedChanges(
  () => form.value,
  () => pageMode.value === 'process',
  () => busy.value,
);
async function cancelProcess() {
  if (busy.value || !(await permitLeave())) return;
  pageMode.value = 'detail';
}
const faultNodes = computed(() => base.faultTypeData.filter((x) => x.status === '启用'));
const faultCauses = computed(() => base.faultCauseData.filter((x) => x.status === '启用'));
const timelineEvents = computed(() => [...(detail.value?.events || [])].reverse());
const canWork = computed(
  () => detail.value?.canProcess && ['处理中', '挂起'].includes(order.value?.status || ''),
);
const availableMaterials = computed(() =>
  (detail.value?.materials || []).filter(
    (item) => Number(item.issuedQuantity) > Number(item.installedQuantity),
  ),
);
const spareChanges = computed(() =>
  (detail.value?.events || []).filter((event) => ['换上备件', '换下备件'].includes(event.action)),
);
const replacementLimit = computed(() => {
  const item = availableMaterials.value.find((x) => x.id === materialId.value);
  return item ? Number(item.issuedQuantity) - Number(item.installedQuantity) : 0;
});
function openReplacement() {
  replacementMode.value = 'install';
  materialId.value = '';
  installedId.value = '';
  quantity.value = 1;
  reason.value = '';
  dialog.value = 'replace';
}
let loadRevision = 0;
function clearDetail() {
  detail.value = null;
}
async function load(): Promise<boolean> {
  const revision = ++loadRevision;
  const requestedId = id.value;
  loading.value = true;
  error.value = '';
  try {
    const response = await api.repairWorkflow(requestedId);
    if (revision !== loadRevision || requestedId !== id.value) return false;
    detail.value = response.data;
    return true;
  } catch (e) {
    if (revision === loadRevision && requestedId === id.value)
      error.value = e instanceof Error ? e.message : '工单加载失败';
    return false;
  } finally {
    if (revision === loadRevision) loading.value = false;
  }
}
watch(
  [id, () => route.query.taskAction, () => route.query.mode],
  async () => {
    pageMode.value = 'detail';
    clearDetail();
    dialog.value = '';
    picker.value = false;
    materialPanel.value = false;
    if (!(await load())) return;
    if (
      (route.query.mode === 'dispatch' || route.query.taskAction === '派工') &&
      detail.value?.canDispatch &&
      order.value?.status === '待派工'
    )
      openPicker('dispatch');
    if (
      route.query.taskAction === '处理' &&
      detail.value?.canProcess &&
      order.value?.status === '处理中'
    )
      await startProcess();
    if (
      route.query.taskAction === '验收' &&
      detail.value?.canAccept &&
      order.value?.status === '待验收'
    )
      await openDialog('review');
  },
  { immediate: true },
);
async function run(action: string, extra: Record<string, unknown> = {}) {
  if (busy.value || !order.value) return false;
  const requestedId = id.value;
  const rowVersion = order.value.rowVersion;
  busy.value = true;
  try {
    await api.repairWorkflowAction(requestedId, rowVersion, { action, ...extra });
    window.dispatchEvent(new Event('repair-inbox-changed'));
    if (requestedId !== id.value) return false;
    await load();
    ElMessage.success('操作已保存');
    return true;
  } catch (e) {
    if (requestedId !== id.value) return false;
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
    await load();
    return false;
  } finally {
    busy.value = false;
  }
}
function openPicker(purpose: typeof pickerPurpose.value) {
  pickerPurpose.value = purpose;
  reason.value = '';
  picker.value = true;
}
async function confirmUsers(ids: string[]) {
  if (pickerPurpose.value === 'collaborators') {
    const users = (await api.repairUserChoices()).data;
    form.value.collaborators = users
      .filter((x) => ids.includes(x.id))
      .map((x) => ({ id: x.id, name: x.name }));
    picker.value = false;
    return;
  }
  if (await run(pickerPurpose.value, { userIds: ids, reason: reason.value })) picker.value = false;
}
async function startProcess() {
  if (!order.value || busy.value || pageMode.value === 'process') return;
  const r = order.value;
  const requestedId = id.value;
  await Promise.all([base.load('故障类型'), base.load('故障原因')]);
  if (requestedId !== id.value || order.value !== r || !detail.value?.canProcess) return;
  form.value = {
    faultCause: r.faultCause || '',
    faultType: r.faultType,
    repairMethod: r.repairMethod || '自修',
    content: r.repairAction || '',
    result: r.processingResult || '已解决',
    prevention: r.preventiveMeasures || '',
    collaborators: (r.collaborators || []).map((u) => ({ ...u })),
    attachments: (r.processAttachments || []).map((a) => ({ ...a })),
  };
  pageMode.value = 'process';
  markSaved();
}
async function submitProcess() {
  const f = form.value;
  if (!f.faultCause.trim() || !f.faultType || !f.content.trim() || !f.result) {
    ElMessage.warning('请填写必填项');
    return;
  }
  if (await run('process', { ...f, collaboratorIds: f.collaborators.map((x) => x.id) })) {
    markSaved();
    pageMode.value = 'detail';
  }
}
async function openDialog(kind: string, row?: api.MaterialRow | api.InstalledSpare) {
  dialog.value = kind;
  reason.value = '';
  vendor.value = order.value?.outsourceVendor || '';
  comment.value = '';
  score.value = 5;
  acceptResult.value = 'accept';
  reviewAttachments.value = [];
  quantity.value = 1;
  if (kind === 'material') {
    stockId.value = '';
    try {
      stockRows.value = (await api.repairStocks(id.value)).data;
    } catch (e) {
      ElMessage.error(String(e));
    }
  }
  if (kind === 'install' && row) {
    materialId.value = row.id;
    quantity.value =
      Number((row as api.MaterialRow).issuedQuantity) -
      Number((row as api.MaterialRow).installedQuantity);
  }
  if (kind === 'remove' && row) installedId.value = row.id;
}
async function submitDialog() {
  const action = dialog.value === 'replace' ? replacementMode.value : dialog.value;
  if (
    dialog.value === 'replace' &&
    !(action === 'install' ? materialId.value : installedId.value)
  ) {
    ElMessage.warning('请选择备件');
    return;
  }
  let payload: Record<string, unknown> = { reason: reason.value };
  if (action === 'review') {
    if (!comment.value.trim()) {
      ElMessage.warning('请填写验收说明');
      return;
    }
    payload = { score: score.value, comment: comment.value, attachments: reviewAttachments.value };
  }
  if (action === 'outsource') payload = { vendor: vendor.value, reason: reason.value };
  if (action === 'material') payload = { stockId: stockId.value, quantity: quantity.value };
  if (action === 'install') {
    if (
      !(quantity.value > 0) ||
      (dialog.value === 'replace' && quantity.value > replacementLimit.value)
    ) {
      ElMessage.warning('换上数量必须大于 0，且不能超过已出库的可用数量');
      return;
    }
    payload = { materialId: materialId.value, quantity: quantity.value };
  }
  if (action === 'remove') payload = { installedId: installedId.value, reason: reason.value };
  if (await run(action === 'review' ? acceptResult.value : action, payload)) dialog.value = '';
}
async function upload(event: Event, target: 'process' | 'review') {
  const input = event.target as HTMLInputElement;
  const files = Array.from(input.files || []);
  if (!files.length) return;
  busy.value = true;
  try {
    for (const file of files) {
      const item = (await api.uploadRepairFile(file)).data;
      (target === 'process' ? form.value.attachments : reviewAttachments.value).push(item);
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '上传失败');
  } finally {
    input.value = '';
    busy.value = false;
  }
}
const detailLabels: Record<string, string> = {
  after: '新状态',
  description: '故障描述',
  faultType: '故障类型',
  faultAt: '故障时间',
  recipients: '派给',
  reason: '说明',
  faultCause: '故障原因',
  repairMethod: '维修方式',
  content: '处理内容',
  result: '结果',
  prevention: '预防措施',
  collaborators: '协同处理人',
  score: '评分',
  comment: '验收说明',
  vendor: '外协单位',
  name: '备件',
  quantity: '数量',
};
function eventValue(value: unknown): string {
  if (Array.isArray(value))
    return value
      .map((x) =>
        typeof x === 'object' && x !== null
          ? String((x as { name?: string }).name || '')
          : String(x),
      )
      .join('、');
  return String(value ?? '').replace('T', ' ');
}
function eventLines(event: api.WorkflowEvent) {
  return Object.entries(event.details || {})
    .filter(
      ([key, value]) =>
        detailLabels[key] &&
        value !== '' &&
        value != null &&
        !(Array.isArray(value) && !value.length),
    )
    .map(([key, value]) => ({ label: detailLabels[key], value: eventValue(value) }));
}
function eventFiles(event: api.WorkflowEvent) {
  return Array.isArray(event.details?.attachments)
    ? (event.details.attachments as api.RepairAttachment[])
    : [];
}
</script>
<template>
  <section class="workflow-page">
    <header class="workflow-header">
      <div>
        <AppBackButton
          :label="workspaceReturn(route.query.returnTo) ? '返回工作台' : '返回工单列表'"
          :disabled="busy"
          @click="router.push(workspaceReturn(route.query.returnTo) || { name: 'repairs' })"
        /><strong>{{ pageMode === 'process' ? '处理工单' : '工单详情' }}</strong
        ><span v-if="order">{{ order.orderNo }}</span
        ><b v-if="order" class="order-status">{{ order.status }}</b>
      </div>
      <div v-if="detail && order && pageMode === 'detail'" class="workflow-actions">
        <button v-if="detail.canClaim" class="btn primary" :disabled="busy" @click="run('claim')">
          接单
        </button>
        <button
          v-if="detail.canDispatch && order.status === '待派工'"
          class="btn primary"
          @click="openPicker('dispatch')"
        >
          派工
        </button>
        <button
          v-if="detail.canProcess && order.status === '处理中'"
          class="btn primary"
          @click="startProcess"
        >
          处理
        </button>
        <button
          v-if="detail.canProcess && order.status === '挂起'"
          class="btn primary"
          @click="openDialog('resume')"
        >
          恢复
        </button>
        <button
          v-if="canWork && order.status === '处理中'"
          class="btn secondary"
          @click="openDialog('suspend')"
        >
          挂起
        </button>
        <button
          v-if="canWork || (detail.canDispatch && order.status === '待接单')"
          class="btn secondary"
          @click="openPicker('transfer')"
        >
          转派
        </button>
        <button v-if="canWork" class="btn secondary" @click="openDialog('outsource')">委外</button>
        <button v-if="canWork" class="btn secondary" @click="openDialog('material')">领料</button>
        <button
          v-if="canWork || (detail.canIssue && ['处理中', '挂起'].includes(order.status))"
          class="btn secondary"
          @click="materialPanel = true"
        >
          领料管理
        </button>
        <button
          v-if="detail.canAccept && order.status === '待验收'"
          class="btn primary"
          @click="openDialog('review')"
        >
          验收
        </button>
      </div>
    </header>
    <AppLoadState v-if="!detail" :loading="loading" :error="error" @retry="load" />
    <div v-else-if="error" class="workflow-error">
      {{ error }}<button class="btn secondary" @click="load">重试</button>
    </div>
    <div
      v-if="detail && order"
      class="workflow-body"
      :class="{ 'detail-layout': pageMode === 'detail', 'process-layout': pageMode === 'process' }"
    >
      <div class="workflow-main">
        <div class="report-summary">
          <h3 v-if="pageMode === 'process'" class="reference-heading">报修信息</h3>
          <div>
            <small>设备名称</small><strong>{{ order.equipmentName }}</strong>
          </div>
          <div>
            <small>设备编号</small><span>{{ order.equipmentCode }}</span>
          </div>
          <div>
            <small>规格型号</small><span>{{ detail.model || '—' }}</span>
          </div>
          <div>
            <small>维修人</small><span>{{ order.assignee || '待派工' }}</span>
          </div>
          <div>
            <small>报修人</small><span>{{ order.reporter }}</span>
          </div>
          <div>
            <small>故障时间</small><span>{{ order.faultAt.replace('T', ' ') }}</span>
          </div>
          <div>
            <small>所属部门</small><span>{{ order.department }}</span>
          </div>
          <div>
            <small>紧急程度</small><span>{{ order.severity }}</span>
          </div>
          <div class="summary-wide">
            <small>故障描述</small><span>{{ order.description }}</span>
          </div>
          <div v-if="order.attachments.length" class="summary-wide">
            <small>报修附件</small>
            <div class="file-links">
              <a
                v-for="file in order.attachments"
                :key="file.id"
                :href="file.url"
                target="_blank"
                rel="noopener"
                >{{ file.name }}</a
              >
            </div>
          </div>
        </div>
        <template v-if="pageMode === 'process'">
          <form class="processing-form" @submit.prevent="submitProcess">
            <h3 class="process-heading">维修处理</h3>
            <label
              ><span>故障原因 <i>*</i></span
              ><select v-model="form.faultCause" aria-label="故障原因">
                <option value="">请选择故障原因</option>
                <option
                  v-if="form.faultCause && !faultCauses.some((x) => x.name === form.faultCause)"
                  :value="form.faultCause"
                  disabled
                >
                  {{ form.faultCause }}（历史值）
                </option>
                <option v-for="cause in faultCauses" :key="cause.id" :value="cause.name">
                  {{ cause.name }}
                </option>
              </select>
            </label>
            <label
              ><span>故障类型 <i>*</i></span
              ><TreeSelect
                v-model="form.faultType"
                :nodes="faultNodes"
                placeholder="请选择故障类型"
            /></label>
            <label
              ><span>维修方式 <i>*</i></span>
              <div class="radio-line">
                <label><input v-model="form.repairMethod" type="radio" value="自修" />自修</label
                ><label><input v-model="form.repairMethod" type="radio" value="外协" />外协</label>
              </div></label
            >
            <label
              ><span>处理结果 <i>*</i></span
              ><select v-model="form.result">
                <option v-for="result in detail.results" :key="result">{{ result }}</option>
              </select></label
            >
            <label class="full"
              ><span>处理内容 <i>*</i></span
              ><textarea
                v-model="form.content"
                rows="4"
                maxlength="20000"
                placeholder="记录本次处理内容"
              />
            </label>
            <label class="full"
              ><span>预防措施</span
              ><textarea
                v-model="form.prevention"
                rows="3"
                maxlength="20000"
                placeholder="填写后续预防措施"
              />
            </label>
            <label class="full"
              ><span>协同处理人</span>
              <div class="collaborators">
                <button type="button" class="btn secondary" @click="openPicker('collaborators')">
                  选择人员</button
                ><span v-for="user in form.collaborators" :key="user.id"
                  >{{ user.name
                  }}<button
                    type="button"
                    @click="form.collaborators = form.collaborators.filter((x) => x.id !== user.id)"
                  >
                    ×
                  </button></span
                >
              </div></label
            >
            <label class="full"
              ><span>附件 / 图片</span>
              <div class="upload-area">
                <label class="btn secondary"
                  ><input
                    type="file"
                    multiple
                    hidden
                    accept=".jpg,.jpeg,.png,.gif,.webp,.pdf,.doc,.docx,.xls,.xlsx,.txt,.zip,.csv"
                    :disabled="busy"
                    @change="upload($event, 'process')"
                  />上传</label
                ><span v-for="file in form.attachments" :key="file.id"
                  >{{ file.name
                  }}<button
                    type="button"
                    @click="form.attachments = form.attachments.filter((x) => x.id !== file.id)"
                  >
                    ×
                  </button></span
                >
              </div></label
            >
          </form>
        </template>
        <section v-if="pageMode === 'detail' && order.repairAction" class="processing-summary">
          <h3>处理信息</h3>
          <dl>
            <div>
              <dt>故障原因</dt>
              <dd>{{ order.faultCause || '—' }}</dd>
            </div>
            <div>
              <dt>故障类型</dt>
              <dd>{{ order.faultType || '—' }}</dd>
            </div>
            <div>
              <dt>维修方式</dt>
              <dd>{{ order.repairMethod || '—' }}</dd>
            </div>
            <div>
              <dt>处理结果</dt>
              <dd>{{ order.processingResult || '—' }}</dd>
            </div>
            <div class="full">
              <dt>处理内容</dt>
              <dd>{{ order.repairAction }}</dd>
            </div>
            <div class="full">
              <dt>预防措施</dt>
              <dd>{{ order.preventiveMeasures || '—' }}</dd>
            </div>
            <div class="full">
              <dt>协同处理人</dt>
              <dd>{{ order.collaborators?.map((x) => x.name).join('、') || '—' }}</dd>
            </div>
          </dl>
          <div class="file-links">
            <a
              v-for="file in order.processAttachments"
              :key="file.id"
              :href="file.url"
              target="_blank"
              rel="noopener"
              >{{ file.name }}</a
            >
          </div>
        </section>
        <TaskMaterials
          :task-id="id"
          kind="维修"
          :can-work="pageMode === 'process' && !!canWork"
          :can-request="!!canWork"
          @changed="load"
        />
        <section v-if="detail?.materials?.length || spareChanges.length" class="spare-section">
          <header>
            <h3>更换备件</h3>
            <div>
              <button
                v-if="pageMode === 'process' && canWork && order.status === '处理中'"
                class="btn secondary"
                :disabled="busy"
                @click="openReplacement"
              >
                更换备件
              </button>
            </div>
          </header>
          <div class="spare-table">
            <table class="business-table">
              <thead>
                <tr>
                  <th>备件名称</th>
                  <th>操作</th>
                  <th>数量</th>
                  <th>操作人</th>
                  <th>时间</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="event in spareChanges" :key="event.id">
                  <td>{{ event.details.name }}</td>
                  <td>{{ event.action === '换上备件' ? '换上' : '换下' }}</td>
                  <td>{{ formatNumber(event.details.quantity) }}</td>
                  <td>{{ event.actor }}</td>
                  <td>{{ event.time }}</td>
                </tr>
                <tr v-if="!spareChanges.length">
                  <td colspan="5" class="empty">本次工单暂无备件更换</td>
                </tr>
              </tbody>
            </table>
          </div>
        </section>
      </div>
      <section v-if="pageMode === 'detail'" class="timeline-section">
        <h3>维修时间轴</h3>
        <ol class="workflow-timeline">
          <li v-for="event in timelineEvents" :key="event.id">
            <div class="event-title">
              <strong>{{ event.action }}</strong
              ><span>{{ event.actor }}</span
              ><time>{{ event.time }}</time>
            </div>
            <dl>
              <div v-for="line in eventLines(event)" :key="line.label">
                <dt>{{ line.label }}</dt>
                <dd>{{ line.value }}</dd>
              </div>
            </dl>
            <div class="file-links">
              <a
                v-for="file in eventFiles(event)"
                :key="file.id"
                :href="file.url"
                target="_blank"
                rel="noopener"
                >{{ file.name }}</a
              >
            </div>
          </li>
          <li v-if="!detail.events.length" class="empty">暂无维修记录</li>
        </ol>
      </section>
    </div>
    <AppFormFooter v-if="pageMode === 'process'">
      <template #hint>{{
        form.result === '已解决' ? '提交后进入验收环节' : '提交后继续处理工单'
      }}</template>
      <button type="button" class="btn secondary" :disabled="busy" @click="cancelProcess">
        取消</button
      ><button class="btn primary" :disabled="busy" @click="submitProcess">
        {{ busy ? '保存中…' : '提交' }}
      </button>
    </AppFormFooter>
    <RepairUserPicker
      v-model="picker"
      :title="
        pickerPurpose === 'collaborators'
          ? '协同处理人'
          : pickerPurpose === 'transfer'
            ? '转派'
            : '派工'
      "
      :selected="pickerPurpose === 'collaborators' ? form.collaborators.map((x) => x.id) : []"
      :busy="busy"
      @confirm="confirmUsers"
    />
    <AppDialog v-model="materialPanel" title="领料" width="760px" :busy="busy">
      <div class="material-tools">
        <button
          v-if="canWork"
          class="btn primary"
          @click="
            materialPanel = false;
            openDialog('material');
          "
        >
          领料
        </button>
      </div>
      <table class="business-table material-table">
        <thead>
          <tr>
            <th>备件</th>
            <th>申请数量</th>
            <th>已出库</th>
            <th>已换上</th>
            <th>状态</th>
            <th>操作</th>
          </tr>
        </thead>
        <tbody>
          <tr v-for="item in detail?.materials" :key="item.id">
            <td>
              {{ item.name }}<small>{{ item.code }}</small>
            </td>
            <td>{{ formatNumber(item.quantity) }}</td>
            <td>{{ formatNumber(item.issuedQuantity) }}</td>
            <td>{{ formatNumber(item.installedQuantity) }}</td>
            <td>{{ item.status }}</td>
            <td>
              <button
                v-if="item.status === '待出库' && canWork"
                class="text-action"
                :disabled="busy"
                @click="run('cancelMaterial', { materialId: item.id })"
              >
                撤回
              </button>
              <button
                v-if="item.status === '待出库' && detail?.canIssue"
                class="text-action"
                :disabled="busy"
                @click="run('issue', { materialId: item.id })"
              >
                出库
              </button>
            </td>
          </tr>
          <tr v-if="!detail?.materials.length">
            <td colspan="6" class="empty">暂无申请</td>
          </tr>
        </tbody>
      </table>
      <template #footer
        ><button class="btn secondary" :disabled="busy" @click="materialPanel = false">
          关闭
        </button></template
      >
    </AppDialog>
    <AppDialog
      :model-value="!!dialog"
      :title="
        (
          {
            review: '验收',
            material: '领料',
            install: '换上备件',
            replace: '更换备件',
            remove: '换下备件',
            transfer: '转派',
            suspend: '挂起',
            resume: '恢复处理',
            outsource: '委外',
          } as Record<string, string>
        )[dialog] || ''
      "
      width="520px"
      :busy="busy"
      @update:model-value="!$event && (dialog = '')"
    >
      <form class="action-form" @submit.prevent="submitDialog">
        <template v-if="dialog === 'review'"
          ><label><span>评分</span><ElRate v-model="score" :max="5" show-score /></label
          ><label
            ><span>结果</span>
            <div class="radio-line">
              <label><input v-model="acceptResult" type="radio" value="accept" />通过</label
              ><label><input v-model="acceptResult" type="radio" value="return" />打回</label>
            </div></label
          ><label
            ><span>说明 <i>*</i></span
            ><textarea
              v-model="comment"
              rows="4"
              maxlength="10000"
              placeholder="填写验收说明"
            /></label
          ><label
            ><span>附件</span>
            <div class="upload-area">
              <label class="btn secondary"
                ><input
                  type="file"
                  multiple
                  hidden
                  :disabled="busy"
                  @change="upload($event, 'review')"
                />上传</label
              ><span v-for="file in reviewAttachments" :key="file.id"
                >{{ file.name
                }}<button
                  type="button"
                  @click="reviewAttachments = reviewAttachments.filter((x) => x.id !== file.id)"
                >
                  ×
                </button></span
              >
            </div></label
          ></template
        >
        <template v-else-if="dialog === 'material'"
          ><label
            ><span>备件 <i>*</i></span
            ><select v-model="stockId">
              <option value="">请选择备件</option>
              <option v-for="stock in stockRows" :key="stock.id" :value="stock.id">
                {{ stock.name }} · {{ stock.code }} · 库存 {{ formatNumber(stock.quantity) }}
              </option>
            </select></label
          >
          <p v-if="!stockRows.length">本公司暂无库存备件，请先在仓库与库存中维护。</p>
          <label
            ><span>数量 <i>*</i></span
            ><AppDecimalInput
              :model-value="quantity"
              @update:model-value="quantity = Number($event)" /></label
        ></template>
        <template v-else-if="dialog === 'replace'">
          <div class="replacement-mode">
            <span>操作</span
            ><label><input v-model="replacementMode" type="radio" value="install" />换上</label
            ><label><input v-model="replacementMode" type="radio" value="remove" />换下</label>
          </div>
          <label
            ><span>备件 <i>*</i></span>
            <select v-if="replacementMode === 'install'" v-model="materialId">
              <option value="">请选择已出库备件</option>
              <option v-for="item in availableMaterials" :key="item.id" :value="item.id">
                {{ item.name }} · {{ item.code }} · 可用
                {{ formatNumber(Number(item.issuedQuantity) - Number(item.installedQuantity)) }}
              </option>
            </select>
            <select v-else v-model="installedId">
              <option value="">请选择需要换下的备件</option>
              <option v-for="item in detail?.installed" :key="item.id" :value="item.id">
                {{ item.name }} · {{ item.code }} · 数量 {{ formatNumber(item.quantity) }}
              </option>
            </select>
          </label>
          <label v-if="replacementMode === 'install'"
            ><span>数量 <i>*</i></span
            ><AppDecimalInput
              :model-value="quantity"
              @update:model-value="quantity = Number($event)"
          /></label>
          <p v-if="replacementMode === 'install' && !availableMaterials.length">
            暂无可用备件，请先领料并由仓管执行出库。
          </p>
          <p v-if="replacementMode === 'remove'">换下所选安装记录的全部数量，不自动退回库存。</p>
          <label
            ><span>说明</span
            ><textarea v-model="reason" rows="3" maxlength="2000" placeholder="填写更换说明" />
          </label>
        </template>
        <label v-else-if="dialog === 'install'"
          ><span>安装数量 <i>*</i></span
          ><AppDecimalInput :model-value="quantity" @update:model-value="quantity = Number($event)"
        /></label>
        <template v-else
          ><label v-if="dialog === 'outsource'"
            ><span>外协单位 <i>*</i></span
            ><input v-model="vendor" maxlength="200" placeholder="填写外协单位" /></label
          ><label
            ><span>说明 <i v-if="['transfer', 'suspend', 'resume'].includes(dialog)">*</i></span
            ><textarea
              v-model="reason"
              rows="4"
              maxlength="2000"
              placeholder="填写操作说明"
            /></label
        ></template>
      </form>
      <template #footer
        ><button class="btn secondary" :disabled="busy" @click="dialog = ''">取消</button
        ><button class="btn primary" :disabled="busy" @click="submitDialog">
          {{ dialog === 'transfer' ? '选择人员' : '提交' }}
        </button></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.workflow-page small {
  font-size: 13px;
}
.workflow-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 54px);
  min-height: 0;
  font-size: 13px;
  color: #334155;
  background: #fff;
}
.workflow-header {
  height: 52px;
  flex-shrink: 0;
  padding: 0 16px;
  border-bottom: 1px solid #e4e9f0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
.workflow-header > div {
  display: flex;
  align-items: center;
  gap: 10px;
}
.workflow-header strong {
  font-size: 15px;
  color: #273347;
}
.workflow-header span {
  color: var(--text-secondary);
}
.order-status {
  font-weight: 500;
  background: #eef3ff;
  color: var(--primary, #2458f5);
  padding: 4px 8px;
  border-radius: 3px;
}
.workflow-actions {
  flex-wrap: wrap;
}
.workflow-actions .btn {
  height: 30px;
  padding: 0 12px;
  font-size: 13px;
}
.workflow-body {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px 24px;
}
.report-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16px 24px;
  padding-bottom: 20px;
  border-bottom: 1px solid #e6ebf2;
}
.report-summary > div {
  display: flex;
  gap: 12px;
  align-items: baseline;
}
.report-summary small {
  color: var(--text-secondary);
  white-space: nowrap;
  font-size: 13px;
}
.report-summary span,
.report-summary strong {
  overflow-wrap: anywhere;
  white-space: pre-wrap;
}
.summary-wide {
  grid-column: 1/-1;
}
.file-links {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
}
.file-links a {
  color: var(--primary, #2458f5);
  text-decoration: none;
}
.processing-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px 32px;
  padding-top: 24px;
}
.processing-form > label,
.action-form > label {
  display: flex;
  align-items: flex-start;
  gap: 12px;
}
.processing-form > label > span,
.action-form > label > span {
  width: 86px;
  flex-shrink: 0;
  text-align: right;
  padding-top: 8px;
  color: var(--text-secondary);
}
.full {
  grid-column: 1/-1;
}
.workflow-page input:not([type='radio']),
.workflow-page select,
.workflow-page textarea {
  padding: 8px 10px;
  border: 1px solid #d8e1ed;
  border-radius: 4px;
  font: inherit;
  min-width: 0;
  color: #334155;
  background: #fff;
}
.workflow-page input:focus,
.workflow-page select:focus,
.workflow-page textarea:focus {
  outline: 2px solid #2458f51c;
  border-color: var(--primary, #2458f5);
}
.processing-form textarea,
.processing-form select,
.processing-form :deep(.tree-select) {
  flex: 1;
  width: auto;
  min-width: 0;
}
.processing-form i,
.action-form i {
  color: #e25353;
  font-style: normal;
}
.radio-line {
  display: flex;
  gap: 24px;
  align-items: center;
  min-height: 34px;
}
.radio-line label {
  display: flex;
  align-items: center;
  gap: 6px;
}
.radio-line input {
  accent-color: var(--primary, #2458f5);
}
.collaborators,
.upload-area {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.upload-area > span,
.collaborators > span {
  padding: 5px 8px;
  background: #f1f5fa;
  border-radius: 4px;
}
.upload-area button,
.collaborators span button {
  border: 0;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
}
.upload-area > label {
  cursor: pointer;
  display: inline-flex;
  align-items: center;
}
.spare-section,
.timeline-section {
  margin-top: 24px;
}
.spare-section > header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.workflow-page h3 {
  font-size: 14px;
  margin: 0 0 12px;
  color: #273347;
}
.spare-table table {
  width: 100%;
  min-width: 0;
  table-layout: fixed;
  border: 1px solid #e4e9f0;
}
.spare-table th,
.spare-table td {
  padding: 9px 10px;
  font-size: 13px;
}
.spare-table th {
  background: #f5f7fa;
}
.empty {
  text-align: center;
  color: var(--text-secondary);
  padding: 18px;
}
.text-action {
  border: 0;
  background: none;
  color: var(--primary, #2458f5);
  font: inherit;
  cursor: pointer;
  padding: 0 8px 0 0;
}
.workflow-timeline {
  padding: 0 0 0 18px;
  margin: 0;
  list-style: none;
}
.workflow-timeline li {
  position: relative;
  border-left: 1px solid #dbe3ef;
  padding: 0 0 22px 22px;
}
.workflow-timeline li:before {
  content: '';
  position: absolute;
  width: 8px;
  height: 8px;
  background: var(--primary, #2458f5);
  border: 3px solid #edf2ff;
  box-sizing: content-box;
  border-radius: 50%;
  left: -7px;
  top: 0;
}
.event-title {
  display: flex;
  align-items: center;
  gap: 12px;
  line-height: 18px;
}
.event-title strong {
  color: #334155;
}
.event-title span {
  color: var(--text-secondary);
}
.event-title time {
  color: var(--text-secondary);
}
.workflow-timeline dl {
  margin: 9px 0 0;
}
.workflow-timeline dl > div {
  display: flex;
  gap: 12px;
  line-height: 1.7;
}
.workflow-timeline dt {
  color: var(--text-secondary);
  min-width: 78px;
}
.workflow-timeline dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  max-width: 1000px;
}
.workflow-timeline .file-links {
  margin-top: 8px;
}
.action-form {
  display: grid;
  gap: 18px;
  font-size: 13px;
}
.action-form textarea,
.action-form select,
.action-form input:not([type='radio']),
.action-form .upload-area {
  flex: 1;
  min-width: 0;
}
.action-form textarea,
.action-form select,
.action-form input:not([type='radio']) {
  font: inherit;
  border: 1px solid #d8e1ed;
  border-radius: 4px;
  padding: 8px 10px;
}
.action-form p {
  color: var(--text-secondary);
  margin: 0;
}
.workflow-error {
  padding: 24px;
  color: #d54c4c;
  display: flex;
  gap: 12px;
}
@media (max-width: 1000px) {
  .report-summary {
    grid-template-columns: 1fr 1fr;
  }
  .workflow-header {
    height: auto;
    min-height: 52px;
    padding: 10px;
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .processing-form {
    grid-template-columns: 1fr;
  }
  .full {
    grid-column: auto;
  }
}
@media (max-width: 600px) {
  .report-summary {
    grid-template-columns: 1fr;
  }
  .summary-wide {
    grid-column: auto;
  }
  .workflow-body {
    padding: 12px;
  }
}

.workflow-main {
  min-width: 0;
}
.detail-layout {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 380px;
  gap: 0;
  overflow: hidden;
  padding: 0;
}
.detail-layout .workflow-main {
  overflow: auto;
  padding: 20px;
}
.detail-layout .report-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.detail-layout .timeline-section {
  margin: 0;
  padding: 20px 18px;
  border-left: 1px solid #e5eaf1;
  background: #fafbfe;
  overflow: auto;
}
.detail-layout .event-title {
  flex-wrap: wrap;
}
.detail-layout .event-title time {
  width: 100%;
  margin-left: 0;
}
.processing-summary {
  margin-top: 24px;
}
.processing-summary dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18px 24px;
}
.processing-summary dl > div {
  display: flex;
  gap: 12px;
}
.processing-summary dt {
  width: 74px;
  flex-shrink: 0;
  color: var(--text-secondary);
}
.processing-summary dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.spare-section header > div {
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 12px;
}
.material-tools {
  margin-bottom: 12px;
}
.material-table {
  width: 100%;
  border-collapse: collapse;
  font-size: 13px;
}
.material-table td,
.material-table th {
  padding: 10px;
  border-bottom: 1px solid #e5eaf1;
  text-align: left;
}
.material-table th {
  background: #f5f7fa;
}
.material-table small {
  display: block;
  color: var(--text-secondary);
}
.replacement-mode {
  display: flex;
  gap: 20px;
  align-items: center;
}
.replacement-mode > span {
  width: 86px;
  text-align: right;
  color: var(--text-secondary);
}
.replacement-mode label {
  display: flex;
  gap: 6px;
  align-items: center;
}
@media (max-width: 1100px) {
  .detail-layout {
    grid-template-columns: minmax(0, 1fr) 320px;
  }
}
@media (max-width: 800px) {
  .detail-layout {
    display: block;
    overflow: auto;
  }
  .detail-layout .timeline-section {
    border-left: 0;
    border-top: 1px solid #e5eaf1;
  }
  .detail-layout .report-summary,
  .processing-summary dl {
    grid-template-columns: 1fr;
  }
}

.process-layout {
  padding: 0;
  background: #fff;
}
.process-layout .workflow-main {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 320px;
  gap: 0 28px;
  padding: 24px;
  align-items: start;
}
.process-layout .report-summary {
  grid-column: 2;
  grid-row: 1 / 3;
  grid-template-columns: 1fr;
  gap: 18px;
  border-bottom: 0;
  border-left: 1px solid #e5eaf1;
  padding: 0 0 24px 24px;
  position: sticky;
  top: 24px;
}
.process-layout .report-summary .summary-wide {
  flex-direction: column;
  gap: 8px;
}
.process-layout .report-summary strong {
  color: #273347;
}
.process-layout .reference-heading {
  margin-bottom: 0;
}
.process-layout .processing-form {
  grid-column: 1;
  grid-row: 1;
  padding: 0;
  gap: 22px 24px;
}
.process-layout .process-heading {
  grid-column: 1 / -1;
  padding-bottom: 14px;
  margin: 0;
  border-bottom: 1px solid #e5eaf1;
}
.process-layout .spare-section {
  grid-column: 1;
  grid-row: 2;
  margin-top: 26px;
  border-top: 1px solid #e5eaf1;
  padding-top: 20px;
}
.process-layout textarea {
  line-height: 1.65;
  resize: vertical;
}
.process-layout .radio-line {
  background: #f5f7fb;
  border-radius: 5px;
  padding: 0 12px;
  gap: 22px;
  flex: 1;
}
.process-layout .collaborators {
  min-height: 36px;
}
.process-layout .upload-area > label {
  border-style: dashed;
  padding: 0 16px;
}
@media (max-width: 1100px) {
  .process-layout .workflow-main {
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 0 20px;
    padding: 20px;
  }
  .process-layout .processing-form {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 800px) {
  .process-layout .workflow-main {
    display: flex;
    flex-direction: column;
    gap: 24px;
    padding: 16px;
  }
  .process-layout .report-summary {
    position: static;
    border-left: 0;
    border-bottom: 1px solid #e5eaf1;
    padding: 0 0 20px;
    width: 100%;
  }
  .process-layout .processing-form,
  .process-layout .spare-section {
    width: 100%;
  }
}

.process-layout .process-heading,
.process-layout .reference-heading {
  position: relative;
  padding-left: 12px;
}
.process-layout .process-heading::before,
.process-layout .reference-heading::before {
  content: '';
  position: absolute;
  left: 0;
  top: 2px;
  height: 14px;
  width: 3px;
  border-radius: 2px;
  background: var(--primary, #2458f5);
}
</style>
