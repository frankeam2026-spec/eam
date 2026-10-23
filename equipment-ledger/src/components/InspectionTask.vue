<script setup lang="ts">
import DepartmentText from './common/DepartmentText.vue';
import InspectionTaskSummary from './inspection/InspectionTaskSummary.vue';
import InspectionTaskMetrics from './inspection/InspectionTaskMetrics.vue';
import InspectionPlanPreviewDialog from './inspection/InspectionPlanPreviewDialog.vue';
const planPreviewOpen = ref(false);
import InspectionTaskDeleteDialog from './inspection/InspectionTaskDeleteDialog.vue';
const taskDeleteOpen = ref(false);

import { tr } from '../i18n';

import { acceptanceDecisionLabel } from '../config/acceptance';
import PagedChoiceField from './common/PagedChoiceField.vue';
import { planningSelectors, resolvePlanning, mergeOptions, type SelectorRow } from '../selectors';
const selector = planningSelectors('inspection');

import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
import { ElTableColumn } from 'element-plus';
import TaskAnomalies from './common/TaskAnomalies.vue';
import { confirmSurplus } from '../services/surplusConfirmation';
import AppBusinessTable from './common/AppBusinessTable.vue';
import TaskMaterials from './TaskMaterials.vue';
import { workspaceReturn } from '../utils/workspaceNavigation';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import AppBackButton from './common/AppBackButton.vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton, ElInput, ElCheckbox, ElMessage, ElTag } from 'element-plus';
import {
  inspectionApi,
  inspectionUpload,
  type InspectionTask,
  type InspectionResult,
  type InspectionChoices,
} from '../inspection';
import type { ItemResult } from '../maintenance';
import AppDialog from './common/AppDialog.vue';
import AppLoadState from './common/AppLoadState.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import MaintenanceTaskItems from './common/MaintenanceTaskItems.vue';
import MaintenanceAttachments from './common/MaintenanceAttachments.vue';
import InspectionReviewDialog from './common/InspectionReviewDialog.vue';
import { confirmAction } from '../services/dialog';
const route = useRoute(),
  router = useRouter();
const task = ref<InspectionTask>(),
  draft = ref<InspectionResult>({ items: [], attachments: [], skippedEquipmentIds: [] }),
  error = ref(''),
  busy = ref(false),
  loading = ref(true),
  activeEquipment = ref('');
const choices = ref<InspectionChoices>({
  equipment: [],
  organizations: [],
  users: [],
  categories: [],
});
function rememberPeople(rows: SelectorRow[]) {
  choices.value.users = mergeOptions(choices.value.users, rows);
}
const amending = ref(false);
const correcting = computed(() => amending.value && !!task.value?.canAmendResult);
const editing = computed(() => correcting.value || (!!task.value?.canExecute && task.value.status === '执行中'));
const label = computed(() => (task.value?.kind === 'daily' ? '日常点检' : '周期巡检'));
const active = computed(() => task.value?.equipment.find((e) => e.id === activeEquipment.value));
const equipmentItems = computed(
  () => task.value?.items.filter((i) => i.equipmentId === activeEquipment.value) || [],
);
const results = computed<ItemResult[]>({
  get: () => draft.value.items || [],
  set: (v) => {
    const ids = new Set(equipmentItems.value.map((i) => i.id));
    draft.value.items = [...draft.value.items.filter((i) => !ids.has(i.id)), ...v];
  },
});
const itemCount = (id: string) => task.value?.items.filter((i) => i.equipmentId === id).length || 0;
const checkedCount = (id: string) =>
  draft.value.items.filter((r) => r.id.startsWith(id + '/') && (r.value || r.implicitNormal))
    .length;
const skipped = computed(
  () => draft.value.skippedEquipmentIds?.includes(activeEquipment.value) || false,
);
const scanCode = ref('');
const unsaved = useUnsavedChanges(
  () => ({ draft: draft.value, customFields: task.value?.customFields }),
  () => editing.value,
  () => busy.value,
);
async function refresh() {
  if (await unsaved.permitLeave()) await load();
}
let loadRevision = 0;
async function load() {
  const revision = ++loadRevision;
  const requestedId = String(route.params.id);
  loading.value = true;
  error.value = '';
  try {
    const response = await inspectionApi.task(requestedId);
    const selected = await resolvePlanning('inspection', response.data);
    if (revision !== loadRevision || requestedId !== String(route.params.id)) return;
    task.value = response.data;
    amending.value = false;
    choices.value.users = selected.users;
    const listKind = task.value.kind === 'daily' ? 'daily-tasks' : 'periodic-tasks';
    if (route.query.kind !== listKind)
      await router.replace({ query: { ...route.query, kind: listKind } });
    if (revision !== loadRevision || requestedId !== String(route.params.id)) return;
    draft.value = JSON.parse(
      JSON.stringify(
        Object.assign({ items: [], attachments: [], skippedEquipmentIds: [] }, task.value.result),
      ),
    );
    unsaved.markSaved();
    if (!task.value.equipment.some((e) => e.id === activeEquipment.value))
      activeEquipment.value = task.value.equipment[0]?.id || '';
    applyTaskShortcut();
  } catch (e) {
    if (revision === loadRevision && requestedId === String(route.params.id))
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (revision === loadRevision) loading.value = false;
  }
}
watch(
  () => route.params.id,
  () => {
    activeEquipment.value = '';
    dialog.value = '';
    reviewOpen.value = false;
    task.value = undefined;
    load();
  },
);
onMounted(load);

function back() {
  const target = workspaceReturn(route.query.returnTo);
  if (target) {
    router.push(target);
    return;
  }
  router.push({
    name: 'inspection',
    params: { kind: task.value?.kind === 'daily' ? 'daily-tasks' : 'periodic-tasks' },
  });
}
async function action(name: string, body: Record<string, unknown> = {}) {
  if (!task.value || busy.value) return;
  const requestedId = task.value.id;
  const rowVersion = task.value.rowVersion;
  if (['draft', 'submit'].includes(name)) {
    if (name === 'submit' && !prepareExtension(task.value)) return;
    body = {
      ...body,
      customFields: extension.value?.prepare(),
      customSchemaVersion: task.value.customSchemaVersion,
    };
  }
  busy.value = true;
  error.value = '';
  try {
    const localDraft = JSON.parse(JSON.stringify(draft.value)) as InspectionResult;
    const result = await inspectionApi.action(requestedId, name, {
      rowVersion,
      ...body,
    });
    window.dispatchEvent(new Event('work-inbox-changed'));
    if (requestedId !== String(route.params.id)) return;
    if (name === 'delete') {
      unsaved.markSaved();
      busy.value = false;
      back();
      return;
    }
    task.value = result.data;
    if (name === 'amend-result') amending.value = false;
    if (name === 'scan') {
      draft.value = { ...localDraft, scannedEquipmentIds: task.value.result.scannedEquipmentIds };
      scanCode.value = '';
    } else
      draft.value = JSON.parse(
        JSON.stringify(
          Object.assign({ items: [], attachments: [], skippedEquipmentIds: [] }, task.value.result),
        ),
      );
    unsaved.markSaved();
    ElMessage.success(
      name === 'draft' ? '结果已保存' : name === 'scan' ? '设备已校验' : '操作成功',
    );
    dialog.value = '';
    reviewOpen.value = false;
  } catch (e) {
    if (extensionError(e)) return;
    if (requestedId === String(route.params.id))
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0197');
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (!(await confirmAction(tr('ui.equipment.text0416')))) return;
  const surplus = await confirmSurplus('点巡检', String(route.params.id));
  if (surplus === null) return;
  action('submit', { ...draft.value, ...surplus });
}
function cancelCorrection() {
  if (!task.value) return;
  draft.value = JSON.parse(JSON.stringify(Object.assign(
    { items: [], attachments: [], skippedEquipmentIds: [] }, task.value.result,
  )));
  amending.value = false;
  unsaved.markSaved();
}
function setSkipped(value: boolean) {
  const selected = new Set(draft.value.skippedEquipmentIds || []);
  value ? selected.add(activeEquipment.value) : selected.delete(activeEquipment.value);
  draft.value.skippedEquipmentIds = [...selected];
}
const dialog = ref(''),
  description = ref(''),
  owners = ref<string[]>([]),
  reviewOpen = ref(false);
function applyTaskShortcut() {
  const current = task.value;
  if (
    current &&
    current.id === String(route.params.id) &&
    route.query.taskAction === '审核' &&
    current.canReview &&
    current.status === '待审核'
  )
    reviewOpen.value = true;
}
watch(
  () => route.query.taskAction,
  () => {
    if (!loading.value) applyTaskShortcut();
  },
);
function openDialog(name: string) {
  dialog.value = name;
  description.value = '';
  owners.value = [];
}
const dialogOpen = computed({
  get: () => !!dialog.value,
  set: (v: boolean) => {
    if (!v) dialog.value = '';
  },
});
const dialogTitle = computed(
  () =>
    ({
      transfer: '转派',
      suspend: '挂起',
      void: '作废',
      delete: '删除任务',
      exempt: tr('daily.exempt'),
    })[dialog.value] || '',
);
function confirmDialog() {
  if (dialog.value === 'transfer') {
    if (!owners.value.length) {
      ElMessage.warning(tr('ui.equipment.text0417'));
      return;
    }
    action('transfer', { owners: owners.value, description: description.value });
  } else action(dialog.value, { description: description.value });
}
function review(body: Record<string, unknown>) {
  action('review', body);
}
const reviewItems = computed(
  () =>
    task.value?.items.filter((i) => !draft.value.skippedEquipmentIds?.includes(i.equipmentId)) ||
    [],
);
function eventDetails(details: Record<string, unknown>) {
  return Object.entries(details)
    .filter(([key, v]) => !['修改前', '修改后'].includes(key) && v !== '' && v != null)
    .map(([key, v]) => `${key}：${Array.isArray(v) ? v.join('、') : String(v).replace('T', ' ')}`)
    .join('；');
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => draft.value.reviewItems;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="inspection-page inspection-task-page">
    <header class="inspection-edit-heading">
      <AppBackButton
        :label="
          workspaceReturn(route.query.returnTo)
            ? tr('ui.equipment.text0418')
            : `返回${label}任务列表`
        "
        :disabled="busy"
        @click="back"
      /><span>{{ label }}{{ tr('ui.equipment.text0419') }}</span
      ><span>/</span><strong>{{ task?.code || tr('ui.equipment.text0420') }}</strong
      ><MaintenanceStatus v-if="task" :status="task.status" /><span
        class="inspection-heading-spacer"
      />
      <div v-if="task" class="inspection-task-header-actions">
        <ElButton
          v-if="task.canExecute && task.status === '执行中'"
          @click="openDialog('suspend')"
          >{{ tr('ui.equipment.text0421') }}</ElButton
        ><ElButton v-if="task.canExecute && task.status === '挂起'" @click="action('resume')">{{
          tr('ui.equipment.text0422')
        }}</ElButton
        ><ElButton
          v-if="
            task.canTransfer &&
            ['未开始', '待执行', '执行中', '挂起', '已过期', '审核不通过'].includes(task.status)
          "
          @click="openDialog('transfer')"
          >{{ tr('ui.equipment.text0423') }}</ElButton
        ><ElButton
          v-if="
            task.canVoid &&
            !['已完成', '已审核', '已作废', '过期作废', '已免检'].includes(task.status)
          "
          @click="openDialog('void')"
          >{{ tr('ui.equipment.text0215') }}</ElButton
        ><ElButton v-if="task.canExempt" @click="openDialog('exempt')">{{
          tr('daily.exempt')
        }}</ElButton
        ><ElButton
          v-if="task.canDelete"
          type="danger"
          plain
          @click="task.kind === 'daily' ? (taskDeleteOpen = true) : openDialog('delete')"
          >{{ tr('ui.equipment.text0146') }}</ElButton
        >
      </div>
      <ElButton text :disabled="busy" @click="refresh">{{ tr('ui.equipment.text0424') }}</ElButton>
    </header>
    <p v-if="error && task" class="inspection-error" role="alert">{{ error }}</p>
    <template v-if="task"
      ><div class="inspection-task-layout">
        <div class="inspection-task-main">
          <section class="inspection-form-card inspection-task-overview">
            <!-- <p v-if="task.arrangementName">{{ tr('daily.name') }}：{{ task.arrangementName }}</p> -->
            <p v-if="task.assignmentMissing" class="inspection-error">
              {{ tr('daily.unstaffedHelp') }}
            </p>
            <p v-if="task.exemptionReason">{{ tr('daily.exempt') }}：{{ task.exemptionReason }}</p>
            <h3 class="inspection-section-title">{{ tr('ui.equipment.text0425') }}</h3>
            <InspectionTaskSummary
              :task="task"
              :users="choices.users"
              @plan="planPreviewOpen = true"
            />
            <p v-if="task.remark">{{ task.remark }}</p>
            <InspectionTaskMetrics
              :task="task"
              :results="editing ? draft.items : undefined"
              :skipped-equipment-ids="editing ? draft.skippedEquipmentIds : undefined"
            />
            
          </section>
          <div class="inspection-task-equipment-layout">
            <aside v-if="task.equipment.length > 1" class="inspection-route-panel">
              <h3>{{ tr('ui.equipment.text0369') }}</h3>
              <ElButton
                v-for="(e, index) in task.equipment"
                :key="e.id"
                :class="{ active: activeEquipment === e.id }"
                @click="activeEquipment = e.id"
                class="app-content-button"
                ><span class="inspection-route-index">{{ index + 1 }}</span>
                <div>
                  <strong>{{ e.name }}</strong
                  ><span>{{ e.code }}</span
                  ><small>{{
                    draft.skippedEquipmentIds?.includes(e.id)
                      ? tr('ui.equipment.text0442')
                      : checkedCount(e.id) + ' / ' + itemCount(e.id) + tr('ui.equipment.text0443')
                  }}</small>
                </div></ElButton
              >
            </aside>
            <section class="inspection-form-card inspection-task-projects">
              <div class="inspection-equipment-heading">
                <div>
                  <h3>{{ active?.name }}</h3>
                  <span>{{ active?.code }} · {{ active?.model || '—' }}</span>
                </div>
                <span
                  ><DepartmentText :value="active?.department" /> ·
                  {{ active?.functionLocation || '—' }}</span
                ><ElCheckbox
                  v-if="editing && !correcting"
                  :model-value="skipped"
                  @update:model-value="setSkipped(Boolean($event))"
                  >{{ tr('ui.equipment.text0444') }}</ElCheckbox
                >
              </div>
              <div
                v-if="editing && !correcting && task.scanMode !== '无需扫码'"
                class="inspection-device-verification"
              >
                <ElInput
                  v-model="scanCode"
                  :placeholder="tr('ui.equipment.text0445')"
                  @keyup.enter="action('scan', { equipmentCode: scanCode })"
                /><ElButton :loading="busy" @click="action('scan', { equipmentCode: scanCode })">{{
                  tr('ui.equipment.text0446')
                }}</ElButton
                ><ElTag
                  v-if="draft.scannedEquipmentIds?.includes(activeEquipment)"
                  type="success"
                  >{{ tr('ui.equipment.text0447') }}</ElTag
                ><span>{{ task.scanMode }}</span>
              </div>
              <p v-if="task.onlyAbnormal" class="inspection-hint">
                {{ tr('ui.equipment.text0448') }}
              </p>
              <p v-if="skipped" class="inspection-hint">{{ tr('ui.equipment.text0449') }}</p>
              <MaintenanceTaskItems
                mode="inspection"
                v-model="results"
                :items="equipmentItems"
                :readonly="!editing || skipped"
                hide-module
                compact
                :label="tr('ui.equipment.text0450')"
                :upload-file="inspectionUpload"
              />
            </section>
          </div>
          <section class="inspection-form-card inspection-task-description">
            <label
              ><span>{{ tr('ui.equipment.text0451') }}</span
              ><ElInput v-if="editing" v-model="draft.description" type="textarea" :rows="3" />
              <p v-else>{{ draft.description || '—' }}</p></label
            ><label v-if="draft.skippedEquipmentIds?.length"
              ><span>{{ tr('ui.equipment.text0452') }}</span
              ><ElInput v-if="editing && !correcting" v-model="draft.skipReason" type="textarea" :rows="2" />
              <p v-else>{{ draft.skipReason || '—' }}</p></label
            ><label
              ><span>{{ tr('ui.equipment.text0222') }}</span
              ><MaintenanceAttachments
                v-model="draft.attachments!"
                :readonly="!editing"
                :upload-file="inspectionUpload"
            /></label>
          </section>
          <section
            v-if="task.reviewMode !== '无需验收' && draft.review"
            class="inspection-form-card"
          >
            <h3>{{ tr('ui.equipment.text0453') }}</h3>
            <div class="inspection-task-summary">
              <div>
                <span>{{ tr('ui.equipment.text0454') }}</span
                ><strong>{{ acceptanceDecisionLabel(draft.review.decision) }}</strong>
              </div>
              <div>
                <span>{{ tr('ui.equipment.text0337') }}</span
                ><strong>{{ draft.review.reviewer }}</strong>
              </div>
              <div>
                <span>{{ tr('ui.equipment.text0455') }}</span
                ><strong>{{ formatDateTime(draft.review.reviewedAt) }}</strong>
              </div>
            </div>
            <p>{{ draft.review.description || '—' }}</p>
            <MaintenanceAttachments :model-value="draft.review.attachments || []" readonly />
            <AppBusinessTable
              :rows="draft.reviewItems"
              :pagination="false"
              :fill="false"
              :framed="false"
              v-if="draft.reviewItems?.length"
              class="business-table inspection-picker-table"
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="210"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0398') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  task.items.find((i) => i.id === r.id)?.name
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0453') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  acceptanceDecisionLabel(r.decision)
                }}</template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn :min-width="150"
                ><template #header
                  ><div class="table-header-label">{{ tr('ui.equipment.text0177') }}</div></template
                ><template #default="{ row: r, $index: tableRowIndex }">{{
                  r.description || ''
                }}</template></ElTableColumn
              ></AppBusinessTable
            >
          </section>
          <TaskAnomalies
            v-if="task.kind !== 'daily'"
            kind="点巡检"
            :task-id="String(route.params.id)"
            :can-record="editing"
          />
          <TaskMaterials
            v-if="task.kind !== 'daily'"
            :task-id="String(task.id)"
            kind="点巡检"
            :can-work="!!task.canExecute && ['执行中', '挂起'].includes(task.status)"
          />
          <BusinessFields
            ref="extension"
            v-model="task"
            :object="task.kind === 'daily' ? 'inspection_daily_task' : 'inspection_periodic_task'"
            :readonly="!editing || correcting"
            :errors="extensionErrors"
          />
        </div>
        <aside class="inspection-timeline">
          <h3>{{ label }}{{ tr('ui.equipment.text0456') }}</h3>
          <ol>
            <li v-for="(event, index) in task.events" :key="index">
              <div class="inspection-timeline-dot" />
              <strong>{{ event.action }}</strong
              ><span>{{ event.actor }}</span
              ><time>{{ formatDateTime(event.time) }}</time>
              <p v-if="eventDetails(event.details)">{{ eventDetails(event.details) }}</p>
            </li>
          </ol>
        </aside>
      </div>

      <AppFormFooter
        ><template #hint>{{
          editing ? tr('ui.equipment.text0457') : tr('ui.equipment.text0458')
        }}</template
        ><ElButton :disabled="busy" @click="back">{{
          editing ? tr('ui.equipment.text0157') : tr('ui.equipment.text0208')
        }}</ElButton
        ><ElButton
          v-if="
            task.canExecute && ['未开始', '待执行', '已过期', '审核不通过'].includes(task.status)
          "
          type="primary"
          :loading="busy"
          @click="action('start')"
          >{{
            task.status === '审核不通过' ? tr('ui.equipment.text0459') : tr('ui.equipment.text0460')
          }}</ElButton
        ><ElButton v-if="task.canAmendResult && !amending" type="primary" @click="amending = true">{{
          tr('ui.equipment.text0459')
        }}</ElButton
        ><ElButton v-if="correcting" :disabled="busy" @click="cancelCorrection">{{
          tr('ui.equipment.text0415')
        }}</ElButton
        ><ElButton v-if="correcting" type="primary" :loading="busy" @click="action('amend-result', { ...draft })">{{
          tr('ui.equipment.text0159')
        }}</ElButton
        ><ElButton v-if="editing && !correcting" :loading="busy" @click="action('draft', { ...draft })">{{
          tr('ui.equipment.text0159')
        }}</ElButton
        ><ElButton v-if="editing && !correcting" type="primary" :loading="busy" @click="submit">{{
          tr('ui.equipment.text0253')
        }}</ElButton
        ><ElButton
          v-if="task.canReview && task.status === '待审核'"
          type="primary"
          @click="reviewOpen = true"
          >{{ tr('ui.equipment.text0294') }}</ElButton
        ></AppFormFooter
      >
    </template>
    <AppLoadState v-else :loading="loading" :error="error" @retry="load" />
    <InspectionTaskDeleteDialog
      v-model="taskDeleteOpen"
      :task="task"
      @deleted="
        unsaved.markSaved();
        back();
      "
    />
    <AppDialog v-model="dialogOpen" :title="dialogTitle" :busy="busy"
      ><PagedChoiceField
        v-model="owners"
        v-if="dialog === 'transfer'"
        :placeholder="tr('ui.equipment.text0227')"
        multiple
        :options="choices.users"
        :load-rows="selector.users"
        :title="tr('ui.equipment.text0335')"
        @select="rememberPeople"
      /><ElInput
        v-model="description"
        type="textarea"
        :rows="4"
        :placeholder="tr('ui.equipment.text0461')"
      /><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton :disabled="busy" @click="dialog = ''">{{
            tr('ui.equipment.text0157')
          }}</ElButton
          ><ElButton type="primary" :loading="busy" @click="confirmDialog">{{
            tr('ui.equipment.text0253')
          }}</ElButton>
        </div></template
      ></AppDialog
    >
    <InspectionPlanPreviewDialog
      v-if="task"
      v-model="planPreviewOpen"
      :plan-id="task.planId"
      :kind="task.kind === 'daily' ? 'daily-plans' : 'periodic-plans'"
    />
    <InspectionReviewDialog
      v-model="reviewOpen"
      :busy="busy"
      :items="reviewItems"
      :item-review="task?.reviewMode === '逐项审核'"
      @submit="review"
    />
  </section>
</template>
<style src="../styles/inspection.css"></style>
