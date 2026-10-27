<script setup lang="ts">
import DepartmentText from './common/DepartmentText.vue';
import { enumLabel } from '../i18n';
import { tr } from '../i18n';

import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
import BusinessFields from './business/BusinessFields.vue';
import { ElTableColumn } from 'element-plus';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import AppAttachments from './common/AppAttachments.vue';
import AppAttachmentList from './common/AppAttachmentList.vue';
import { ElButton, ElSelect, ElOption, ElRadio, ElInput, ElAlert } from 'element-plus';

import TaskAnomalies from './common/TaskAnomalies.vue';
import { confirmSurplus } from '../services/surplusConfirmation';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber } from '../utils/number';
import TaskMaterials from './TaskMaterials.vue';
import { workspaceReturn } from '../utils/workspaceNavigation';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElRate } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppLoadState from './common/AppLoadState.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AcceptanceResultPicker from './common/AcceptanceResultPicker.vue';
import { AcceptanceDecision } from '../config/acceptance';
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
  score = ref(),
  acceptResult = ref<AcceptanceDecision>(AcceptanceDecision.ACCEPT),
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
  ...({} as import('../businessConfig').CustomRecord),
  faultCauseId: '',
  faultTypeId: '',
  repairMethod: '自修',
  content: '',
  processingResultId: '',
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
const legacyReferences = computed(() => {
  const r = order.value;
  if (!r) return [];
  return [
    !r.faultTypeId && r.faultType ? `故障类型：${r.faultType}` : '',
    !r.faultCauseId && r.faultCause ? `故障原因：${r.faultCause}` : '',
    !r.processingResultId && r.processingResult ? `处理结果：${r.processingResult}` : '',
  ].filter(Boolean);
});
const faultNodes = computed(() => base.faultTypeData.filter((x) => x.status === '启用'));
const faultCauses = computed(() => base.faultCauseData.filter((x) => x.status === '启用'));
function requestMaterial() {
  router.push({
    name: 'inventory-requisition-detail',
    params: { id: 'new' },
    query: { taskKind: '维修', taskId: id.value },
  });
}
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
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text1052');
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
    ElMessage.success(tr('ui.equipment.text1085'));
    return true;
  } catch (e) {
    if (extensionError(e)) return;
    if (requestedId !== id.value) return false;
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
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
    ...detail.value?.processingFields,
    faultCauseId: r.faultCauseId || '',
    faultTypeId: r.faultTypeId || '',
    repairMethod: r.repairMethod || '自修',
    content: r.repairAction || '',
    processingResultId:
      r.processingResultId ||
      (r.processingResult ? '' : detail.value?.results.find((x) => x.resolved)?.id) ||
      '',
    prevention: r.preventiveMeasures || '',
    collaborators: (r.collaborators || []).map((u) => ({ ...u })),
    attachments: (r.processAttachments || []).map((a) => ({ ...a })),
  };
  pageMode.value = 'process';
  markSaved();
}
async function submitProcess() {
  if (!prepareExtension(form.value)) return;
  const f = form.value;
  if (!f.faultCauseId || !f.faultTypeId || !f.content.trim() || !f.processingResultId) {
    ElMessage.warning(tr('ui.equipment.text1086'));
    return;
  }
  const surplus = detail.value?.results.some((x) => x.id === f.processingResultId && x.resolved)
    ? await confirmSurplus('维修', id.value)
    : {};
  if (surplus === null) return;
  if (
    await run('process', { ...f, ...surplus, collaboratorIds: f.collaborators.map((x) => x.id) })
  ) {
    markSaved();
    pageMode.value = 'detail';
  }
}
async function openDialog(kind: string, row?: api.MaterialRow | api.InstalledSpare) {
  dialog.value = kind;
  reason.value = '';
  vendor.value = order.value?.outsourceVendor || '';
  comment.value = '';
  // score.value = 5;
  acceptResult.value = AcceptanceDecision.ACCEPT;
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
    ElMessage.warning(tr('ui.equipment.text1087'));
    return;
  }
  let payload: Record<string, unknown> = { reason: reason.value };
  if (action === 'review') {
    if (!comment.value.trim()) {
      ElMessage.warning(tr('ui.equipment.text1088'));
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
      ElMessage.warning(tr('ui.equipment.text1089'));
      return;
    }
    payload = { materialId: materialId.value, quantity: quantity.value };
  }
  if (action === 'remove') payload = { installedId: installedId.value, reason: reason.value };
  if (await run(action === 'review' ? acceptResult.value : action, payload)) dialog.value = '';
}
async function upload(files: File[], target: 'process' | 'review') {
  if (!files.length) return;
  busy.value = true;
  try {
    for (const file of files) {
      const item = (await api.uploadRepairFile(file)).data;
      (target === 'process' ? form.value.attachments : reviewAttachments.value).push(item);
    }
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0926'));
  } finally {
    busy.value = false;
  }
}
const detailLabels: Record<string, string> = {
  after: '新状态',
  description: '故障描述',
  faultType: '故障类型',
  reportedAt: '报修时间',
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
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => spareChanges.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => detail.value?.materials;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
</script>
<template>
  <section class="workflow-page">
    <header class="workflow-header">
      <div>
        <ElButton
          class="workflow-back"
          :title="workspaceReturn(route.query.returnTo) ? tr('ui.equipment.text0418') : tr('ui.equipment.text1090')"
          :disabled="busy"
          @click="router.push(workspaceReturn(route.query.returnTo) || { name: 'repairs' })"
          native-type="button"
          ><AppIcon name="Left" />{{ tr('ui.equipment.text0208') }}</ElButton
        ><strong>{{ pageMode === 'process' ? tr('ui.equipment.text1091') : tr('ui.equipment.text1056') }}</strong
        ><span v-if="order">{{ order.orderNo }}</span
        ><MaintenanceStatus v-if="order" :status="order.status" />
      </div>
      <div v-if="detail && order && pageMode === 'detail'" class="workflow-actions">
        <ElButton
          v-if="detail.canClaim"
          class=""
          :disabled="busy"
          @click="run('claim')"
          type="primary"
        >
          {{ tr('ui.equipment.text1092') }}
        </ElButton>
        <ElButton
          v-if="detail.canDispatch && order.status === '待派工'"
          class=""
          @click="openPicker('dispatch')"
          type="primary"
        >
          {{ tr('ui.equipment.text1053') }}
        </ElButton>
        <ElButton
          v-if="detail.canProcess && order.status === '处理中'"
          class=""
          @click="startProcess"
          type="primary"
        >
          {{ tr('ui.equipment.text0766') }}
        </ElButton>
        <ElButton
          v-if="detail.canProcess && order.status === '挂起'"
          class=""
          @click="openDialog('resume')"
          type="primary"
        >
          {{ tr('ui.equipment.text0422') }}
        </ElButton>
        <ElButton
          v-if="canWork && order.status === '处理中'"
          class=""
          @click="openDialog('suspend')"
        >
          {{ tr('ui.equipment.text0421') }}
        </ElButton>
        <ElButton
          v-if="canWork || (detail.canDispatch && order.status === '待接单')"
          class=""
          @click="openPicker('transfer')"
        >
          {{ tr('ui.equipment.text0423') }}
        </ElButton>
        <ElButton v-if="canWork" class="" @click="openDialog('outsource')">{{ tr('ui.equipment.text1093') }}</ElButton>
        <ElButton v-if="canWork" class="" @click="requestMaterial">{{ tr('ui.equipment.text1094') }}</ElButton>
        <ElButton
          v-if="detail.materials.length && (canWork || detail.canIssue)"
          class=""
          @click="materialPanel = true"
        >
          {{ tr('ui.equipment.text1095') }}
        </ElButton>
        <ElButton
          v-if="detail.canAccept && order.status === '待验收'"
          class=""
          @click="openDialog('review')"
          type="primary"
        >
          {{ tr('ui.equipment.text0763') }}
        </ElButton>
      </div>
    </header>
    <AppLoadState v-if="!detail" :loading="loading" :error="error" @retry="load" />
    <div v-else-if="error" class="workflow-error">
      {{ error }}<ElButton class="" @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </div>
    <div
      v-if="detail && order"
      class="workflow-body"
      :class="{ 'detail-layout': pageMode === 'detail', 'process-layout': pageMode === 'process' }"
    >
      <div class="workflow-main">
        <div class="report-summary">
          <h3 v-if="pageMode === 'process'" class="reference-heading">{{ tr('ui.equipment.text1096') }}</h3>
          <div>
            <small>{{ tr('ui.equipment.text0081') }}</small><strong>{{ order.equipmentName }}</strong>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text0078') }}</small><span>{{ order.equipmentCode }}</span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text0091') }}</small><span>{{ detail.model || '—' }}</span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text1046') }}</small><span>{{ order.assignee || tr('ui.equipment.text1097') }}</span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text1045') }}</small><span>{{ order.reporter }}</span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text1047') }}</small><span>{{ order.reportedAt.replace('T', ' ') }}</span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text0098') }}</small><span><DepartmentText :value="order.department" /></span>
          </div>
          <div>
            <small>{{ tr('ui.equipment.text1042') }}</small><span>{{ order.severity }}</span>
          </div>
          <div class="summary-wide">
            <small>{{ tr('ui.equipment.text1044') }}</small><span>{{ order.description }}</span>
          </div>
          <div class="summary-wide">
            <small>{{ tr('ui.equipment.text1098') }}</small>
            <AppAttachmentList :files="order.attachments" />
          </div>
        </div>
        <BusinessFields
          :model-value="order"
          object="repair_report"
          :category-id="detail.categoryId"
          readonly
        />
        <template v-if="pageMode === 'process'">
          <form class="processing-form" @submit.prevent="submitProcess">
            <h3 class="process-heading">{{ tr('ui.equipment.text1099') }}</h3>
            <ElAlert
              v-if="legacyReferences.length"
              class="full"
              type="warning"
              :closable="false"
              :title="`历史关联待确认（${legacyReferences.join('；')}），请重新选择后提交。`"
            />
            <label
              ><span>{{ tr('ui.equipment.text1100') }} <i>*</i></span
              ><ElSelect
                v-model="form.faultCauseId"
                :aria-label="tr('ui.equipment.text1100')"
                :placeholder="tr('ui.equipment.text1101')"
              >
                <ElOption
                  v-for="cause in faultCauses"
                  :key="cause.id"
                  :value="cause.id"
                  :label="cause.name"
                />
              </ElSelect>
            </label>
            <label
              ><span>{{ tr('ui.equipment.text1040') }} <i>*</i></span
              ><TreeSelect
                v-model="form.faultTypeId"
                :nodes="faultNodes.map((x) => ({ ...x, value: x.id }))"
                :placeholder="tr('ui.equipment.text1061')"
            /></label>
            <label
              ><span>{{ tr('ui.equipment.text1102') }} <i>*</i></span>
              <div class="radio-line">
                <ElRadio v-model="form.repairMethod" :value="'自修'">{{ tr('ui.equipment.text1103') }}</ElRadio
                ><ElRadio v-model="form.repairMethod" :value="'外协'">{{ tr('ui.equipment.text1104') }}</ElRadio>
              </div></label
            >
            <label
              ><span>{{ tr('ui.equipment.text1105') }} <i>*</i></span
              ><ElSelect v-model="form.processingResultId"
                ><ElOption
                  v-for="result in detail.results"
                  :key="result.id"
                  :value="result.id"
                  :label="result.name" /></ElSelect
            ></label>
            <label class="full"
              ><span>{{ tr('ui.equipment.text1106') }} <i>*</i></span
              ><ElInput
                v-model="form.content"
                :rows="4"
                maxlength="20000"
                :placeholder="tr('ui.equipment.text1107')"
                type="textarea"
              />
            </label>
            <label class="full"
              ><span>{{ tr('ui.equipment.text1108') }}</span
              ><ElInput
                v-model="form.prevention"
                :rows="3"
                maxlength="20000"
                :placeholder="tr('ui.equipment.text1109')"
                type="textarea"
              />
            </label>
            <label class="full"
              ><span>{{ tr('ui.equipment.text0887') }}</span>
              <div class="collaborators">
                <ElButton class="" @click="openPicker('collaborators')" native-type="button">
                  {{ tr('ui.equipment.text0335') }}</ElButton
                ><span v-for="user in form.collaborators" :key="user.id"
                  >{{ user.name
                  }}<ElButton
                    @click="form.collaborators = form.collaborators.filter((x) => x.id !== user.id)"
                    native-type="button"
                    ><AppIcon name="Close" /></ElButton
                ></span></div
            ></label>
            <label class="full"
              ><span>{{ tr('ui.equipment.text1110') }}</span>
              <AppAttachments
                :files="form.attachments"
                :uploading="busy"
                @select="upload($event, 'process')"
                @remove="form.attachments = form.attachments.filter((file) => file.id !== $event)"
              />
            </label>
          </form>
        </template>
        <BusinessFields
          v-if="pageMode === 'process'"
          ref="extension"
          v-model="form"
          object="repair_process"
          :category-id="detail.categoryId"
          :errors="extensionErrors"
        />
        <BusinessFields
          v-else-if="detail.processingFields"
          :model-value="detail.processingFields"
          object="repair_process"
          :category-id="detail.categoryId"
          readonly
        />
        <section v-if="pageMode === 'detail' && order.repairAction" class="processing-summary">
          <h3>{{ tr('ui.equipment.text1111') }}</h3>
          <dl>
            <div>
              <dt>{{ tr('ui.equipment.text1100') }}</dt>
              <dd>{{ order.faultCause || '—' }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text1040') }}</dt>
              <dd>{{ order.faultType || '—' }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text1102') }}</dt>
              <dd>{{ order.repairMethod || '—' }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text1105') }}</dt>
              <dd>{{ order.processingResult || '—' }}</dd>
            </div>
            <div class="full">
              <dt>{{ tr('ui.equipment.text1106') }}</dt>
              <dd>{{ order.repairAction }}</dd>
            </div>
            <div class="full">
              <dt>{{ tr('ui.equipment.text1108') }}</dt>
              <dd>{{ order.preventiveMeasures || '—' }}</dd>
            </div>
            <div class="full">
              <dt>{{ tr('ui.equipment.text0887') }}</dt>
              <dd>{{ order.collaborators?.map((x) => x.name).join('、') || '—' }}</dd>
            </div>
          </dl>
          <AppAttachmentList :files="order.processAttachments || []" :show-empty="false" />
        </section>
        <TaskMaterials
          :task-id="id"
          kind="维修"
          :can-work="pageMode === 'process' && !!canWork"
          :can-request="!!canWork"
          @changed="load"
        />
        <TaskAnomalies kind="维修" :task-id="id" :can-record="false" />
        <section
          v-if="detail?.materials?.length || (pageMode === 'detail' && spareChanges.length)"
          class="spare-section"
        >
          <header>
            <h3>{{ tr('ui.equipment.text1112') }}</h3>
            <div>
              <ElButton
                v-if="pageMode === 'process' && canWork && order.status === '处理中'"
                class=""
                :disabled="busy"
                @click="openReplacement"
              >
                {{ tr('ui.equipment.text1112') }}
              </ElButton>
            </div>
          </header>
          <div class="spare-table">
            <AppBusinessTable
              :rows="spareChanges"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="business-table"
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="210"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0168') }}</div></template
                ><template #default="{ row: event, $index: tableRowIndex }">{{
                  event.details.name
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: event, $index: tableRowIndex }">{{
                  event.action === '换上备件' ? tr('ui.equipment.text1113') : tr('ui.equipment.text1114')
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0144') }}</div></template
                ><template #default="{ row: event, $index: tableRowIndex }">{{
                  formatNumber(event.details.quantity)
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="156" :width="240" :resizable="false"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1115') }}</div></template
                ><template #default="{ row: event, $index: tableRowIndex }">{{
                  event.actor
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0174') }}</div></template
                ><template #default="{ row: event, $index: tableRowIndex }">{{
                  event.time
                }}</template></ElTableColumn
              ><template #empty
                ><div v-if="!spareChanges.length">{{ tr('ui.equipment.text1116') }}</div></template
              ></AppBusinessTable
            >
          </div>
        </section>
      </div>
      <section class="timeline-section">
        <h3>{{ tr('ui.equipment.text1117') }}</h3>
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
            <AppAttachmentList :files="eventFiles(event)" :show-empty="false" />
          </li>
          <li v-if="!detail.events.length" class="empty">{{ tr('ui.equipment.text1118') }}</li>
        </ol>
      </section>
    </div>
    <AppFormFooter v-if="pageMode === 'process'">
      <template #hint>{{
        detail?.results.some((x) => x.id === form.processingResultId && x.resolved)
          ? tr('ui.equipment.text1119')
          : tr('ui.equipment.text1120')
      }}</template>
      <ElButton class="" :disabled="busy" @click="cancelProcess" native-type="button">
        {{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton class="" :disabled="busy" @click="submitProcess" type="primary">{{
        busy ? tr('ui.equipment.text0158') : tr('ui.equipment.text0253')
      }}</ElButton>
    </AppFormFooter>
    <RepairUserPicker
      v-model="picker"
      :title="
        pickerPurpose === 'collaborators'
          ? tr('ui.equipment.text0887')
          : pickerPurpose === 'transfer'
            ? tr('ui.equipment.text0423')
            : tr('ui.equipment.text1053')
      "
      :selected="pickerPurpose === 'collaborators' ? form.collaborators.map((x) => x.id) : []"
      :busy="busy"
      @confirm="confirmUsers"
    />
    <AppDialog v-model="materialPanel" :title="tr('ui.equipment.text1095')" width="760px" :busy="busy">
      <div class="material-tools">
        <ElButton
          v-if="canWork"
          class=""
          @click="
            materialPanel = false;
            requestMaterial();
          "
          type="primary"
        >
          {{ tr('ui.equipment.text1121') }}
        </ElButton>
      </div>
      <AppBusinessTable
        :rows="detail?.materials"
        :pagination="false"
        :fill="false"
        :framed="false"
        class="business-table material-table"
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1122') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">
            {{ item.name }}<small>{{ item.code }}</small>
          </template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0650') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">{{
            formatNumber(item.quantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0656') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">{{
            formatNumber(item.issuedQuantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text1123') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">{{
            formatNumber(item.installedQuantity)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0170') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">{{
            enumLabel('status',item.status)
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
          ><template #default="{ row: item, $index: tableRowIndex }">
            <ElButton
              v-if="item.status === '待出库' && canWork"
              class="text-action"
              :disabled="busy"
              @click="run('cancelMaterial', { materialId: item.id })"
              type="primary"
              link
            >
              {{ tr('ui.equipment.text1124') }}
            </ElButton>
          </template></ElTableColumn
        ><template #empty
          ><div v-if="!detail?.materials.length">{{ tr('ui.equipment.text1125') }}</div></template
        ></AppBusinessTable
      >
      <template #footer
        ><ElButton class="" :disabled="busy" @click="materialPanel = false">
          {{ tr('ui.equipment.text0076') }}
        </ElButton></template
      >
    </AppDialog>
    <AppDialog
      :model-value="!!dialog"
      :title="
        (
          {
            review: tr('ui.equipment.text0763'),
            material: tr('ui.equipment.text1121'),
            install: tr('ui.equipment.text1126'),
            replace: tr('ui.equipment.text1112'),
            remove: tr('ui.equipment.text1127'),
            transfer: tr('ui.equipment.text0423'),
            suspend: tr('ui.equipment.text0421'),
            resume: tr('ui.equipment.text1076'),
            outsource: tr('ui.equipment.text1093'),
          } as Record<string, string>
        )[dialog] || ''
      "
      width="520px"
      :busy="busy"
      @update:model-value="!$event && (dialog = '')"
    >
      <form class="action-form" @submit.prevent="submitDialog">
        <template v-if="dialog === 'review'"
          ><label><span>{{ tr('ui.equipment.text1128') }}</span><ElRate v-model="score" :max="5" show-score /></label
          ><label
            ><span>{{ tr('ui.equipment.text0454') }}</span>
            <AcceptanceResultPicker v-model="acceptResult" :disabled="busy" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0177') }} <i>*</i></span
            ><ElInput
              v-model="comment"
              :rows="4"
              maxlength="10000"
              :placeholder="tr('ui.equipment.text1129')"
              type="textarea" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0222') }}</span>
            <AppAttachments
              :files="reviewAttachments"
              :uploading="busy"
              @select="upload($event, 'review')"
              @remove="
                reviewAttachments = reviewAttachments.filter((file) => file.id !== $event)
              " /></label
        ></template>
        <template v-else-if="dialog === 'material'"
          ><label
            ><span>{{ tr('ui.equipment.text1122') }} <i>*</i></span
            ><ElSelect v-model="stockId"
              ><ElOption value="" :label="tr('ui.equipment.text1087')" /><ElOption
                v-for="stock in stockRows"
                :key="stock.id"
                :value="stock.id"
                :label="
                  String(stock.name) +
                  ' · ' +
                  String(stock.code) +
                  tr('ui.equipment.text1130') +
                  String(formatNumber(stock.quantity))
                " /></ElSelect
          ></label>
          <p v-if="!stockRows.length">{{ tr('ui.equipment.text1131') }}</p>
          <label
            ><span>{{ tr('ui.equipment.text0144') }} <i>*</i></span
            ><AppDecimalInput
              :model-value="quantity"
              @update:model-value="quantity = Number($event)" /></label
        ></template>
        <template v-else-if="dialog === 'replace'">
          <div class="replacement-mode">
            <span>{{ tr('ui.equipment.text0145') }}</span><ElRadio v-model="replacementMode" :value="'install'">{{ tr('ui.equipment.text1113') }}</ElRadio
            ><ElRadio v-model="replacementMode" :value="'remove'">{{ tr('ui.equipment.text1114') }}</ElRadio>
          </div>
          <label
            ><span>{{ tr('ui.equipment.text1122') }} <i>*</i></span>
            <ElSelect v-if="replacementMode === 'install'" v-model="materialId"
              ><ElOption value="" :label="tr('ui.equipment.text1132')" /><ElOption
                v-for="item in availableMaterials"
                :key="item.id"
                :value="item.id"
                :label="
                  String(item.name) +
                  ' · ' +
                  String(item.code) +
                  tr('ui.equipment.text1133') +
                  String(formatNumber(Number(item.issuedQuantity) - Number(item.installedQuantity)))
                "
            /></ElSelect>
            <ElSelect v-else v-model="installedId"
              ><ElOption value="" :label="tr('ui.equipment.text1134')" /><ElOption
                v-for="item in detail?.installed"
                :key="item.id"
                :value="item.id"
                :label="
                  String(item.name) +
                  ' · ' +
                  String(item.code) +
                  tr('ui.equipment.text1135') +
                  String(formatNumber(item.quantity))
                "
            /></ElSelect>
          </label>
          <label v-if="replacementMode === 'install'"
            ><span>{{ tr('ui.equipment.text0144') }} <i>*</i></span
            ><AppDecimalInput
              :model-value="quantity"
              @update:model-value="quantity = Number($event)"
          /></label>
          <p v-if="replacementMode === 'install' && !availableMaterials.length">
            {{ tr('ui.equipment.text1136') }}
          </p>
          <p v-if="replacementMode === 'remove'">{{ tr('ui.equipment.text1137') }}</p>
          <label
            ><span>{{ tr('ui.equipment.text0177') }}</span
            ><ElInput
              v-model="reason"
              :rows="3"
              maxlength="2000"
              :placeholder="tr('ui.equipment.text1138')"
              type="textarea"
            />
          </label>
        </template>
        <label v-else-if="dialog === 'install'"
          ><span>{{ tr('ui.equipment.text1139') }} <i>*</i></span
          ><AppDecimalInput :model-value="quantity" @update:model-value="quantity = Number($event)"
        /></label>
        <template v-else
          ><label v-if="dialog === 'outsource'"
            ><span>{{ tr('ui.equipment.text1140') }} <i>*</i></span
            ><ElInput v-model="vendor" maxlength="200" :placeholder="tr('ui.equipment.text1141')" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0177') }} <i v-if="['transfer', 'suspend', 'resume'].includes(dialog)">*</i></span
            ><ElInput
              v-model="reason"
              :rows="4"
              maxlength="2000"
              :placeholder="tr('ui.equipment.text1142')"
              type="textarea" /></label
        ></template>
      </form>
      <template #footer
        ><ElButton class="" :disabled="busy" @click="dialog = ''">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton class="" :disabled="busy" @click="submitDialog" type="primary">{{
          dialog === 'transfer' ? tr('ui.equipment.text0335') : tr('ui.equipment.text0253')
        }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.workflow-back {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 32px;
  padding: 0 10px;
  border: 1px solid var(--border-color, #e0e6ef);
  border-radius: 5px;
  background: var(--surface, #fff);
  color: var(--text, #263246);
  font: inherit;
  cursor: pointer;
}
.workflow-back .app-icon {
  font-size: 16px;
}
.workflow-back:hover {
  color: var(--brand);
  background: var(--brand-soft, #edf2fa);
}
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
.acceptance-result {
  column-gap: 16px;
}
.acceptance-result :deep(.el-radio) {
  margin-right: 0;
}
.collaborators {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  align-items: center;
}
.collaborators > span {
  padding: 5px 8px;
  background: #f1f5fa;
  border-radius: 4px;
}
.collaborators span button {
  border: 0;
  background: none;
  color: var(--text-secondary);
  cursor: pointer;
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
.action-form {
  display: grid;
  gap: 18px;
  font-size: 13px;
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
  display: grid;
  grid-template-columns: minmax(0, 1fr) 300px;
  gap: 16px;
  padding: 8px;
  overflow: hidden;
  background: var(--page-bg, #f5f7fa);
}
.process-layout .workflow-main {
  overflow: auto;
  padding: 0;
}
.process-layout .report-summary,
.process-layout .processing-form {
  background: var(--surface, #fff);
  border: 1px solid var(--border-color, #e4e9f0);
  border-radius: 6px;
  padding: 16px;
}
.process-layout .report-summary {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px 24px;
}
.process-layout .reference-heading,
.process-layout .process-heading {
  grid-column: 1 / -1;
  margin: 0 0 4px;
  padding: 0 0 12px;
  border-bottom: 1px solid var(--border-color, #e4e9f0);
  font-size: 14px;
  color: var(--text);
}
.process-layout .report-summary small {
  width: 64px;
  flex-shrink: 0;
  color: var(--text);
}
.process-layout .report-summary .summary-wide {
  align-items: flex-start;
}
.process-layout .processing-form {
  margin-top: 16px;
  row-gap: 12px;
  column-gap: 24px;
}
.process-layout .processing-form > label > span {
  width: 78px;
  color: var(--text);
}
.process-layout .radio-line {
  min-height: 36px;
  gap: 24px;
}
.process-layout .collaborators {
  min-height: 36px;
}
.process-layout .timeline-section {
  margin: 0;
  padding: 16px;
  border: 1px solid var(--border-color, #e4e9f0);
  border-radius: 6px;
  background: var(--surface, #fff);
  overflow: auto;
}
.process-layout .timeline-section h3 {
  margin-top: 0;
  font-size: 14px;
}
.process-layout .event-title {
  flex-wrap: wrap;
}
.process-layout .event-title time {
  width: 100%;
  margin-left: 0;
}
.process-layout :deep(.task-materials) {
  margin-top: 16px;
}
@media (max-width: 1100px) {
  .process-layout {
    grid-template-columns: minmax(0, 1fr) 260px;
    gap: 12px;
  }
  .process-layout .processing-form {
    grid-template-columns: 1fr;
  }
}
@media (max-width: 800px) {
  .process-layout {
    display: block;
    overflow: auto;
    padding: 12px;
  }
  .process-layout .workflow-main {
    overflow: visible;
  }
  .process-layout .report-summary {
    grid-template-columns: 1fr;
  }
  .process-layout .timeline-section {
    margin-top: 16px;
    overflow: visible;
  }
}
</style>
