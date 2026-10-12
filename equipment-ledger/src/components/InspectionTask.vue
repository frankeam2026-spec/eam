<script setup lang="ts">
import TaskMaterials from './TaskMaterials.vue';
import { workspaceReturn } from '../utils/workspaceNavigation';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import AppBackButton from './common/AppBackButton.vue';
import { computed, onMounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton, ElInput, ElSelect, ElOption, ElCheckbox, ElMessage, ElTag } from 'element-plus';
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
const editing = computed(() => !!task.value?.canExecute && task.value.status === '执行中');
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
  () => draft.value,
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
    if (revision !== loadRevision || requestedId !== String(route.params.id)) return;
    task.value = response.data;
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
      error.value = e instanceof Error ? e.message : '加载失败';
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
onMounted(async () => {
  await load();
  try {
    choices.value = await inspectionApi.choices();
  } catch {}
});
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
    if (name === 'scan') {
      draft.value = { ...localDraft, scannedEquipmentIds: task.value.result.scannedEquipmentIds };
      scanCode.value = '';
    } else
      draft.value = JSON.parse(
        JSON.stringify(
          Object.assign({ items: [], attachments: [], skippedEquipmentIds: [] }, task.value.result),
        ),
      );
    unsaved.markSaved(
      Object.assign({ items: [], attachments: [], skippedEquipmentIds: [] }, task.value.result),
    );
    ElMessage.success(
      name === 'draft' ? '结果已保存' : name === 'scan' ? '设备已校验' : '操作成功',
    );
    dialog.value = '';
    reviewOpen.value = false;
  } catch (e) {
    if (requestedId === String(route.params.id))
      error.value = e instanceof Error ? e.message : '操作失败';
  } finally {
    busy.value = false;
  }
}
async function submit() {
  if (await confirmAction('提交检查结果？')) action('submit', { ...draft.value });
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
    ({ transfer: '转派', suspend: '挂起', void: '作废', delete: '删除任务' })[dialog.value] || '',
);
function confirmDialog() {
  if (dialog.value === 'transfer') {
    if (!owners.value.length) {
      ElMessage.warning('请选择负责人');
      return;
    }
    action('transfer', { owners: owners.value });
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
    .filter(([, v]) => v !== '' && v != null)
    .map(([key, v]) => `${key}：${Array.isArray(v) ? v.join('、') : String(v).replace('T', ' ')}`)
    .join('；');
}
</script>
<template>
  <section class="inspection-page inspection-task-page">
    <header class="inspection-edit-heading">
      <AppBackButton
        :label="workspaceReturn(route.query.returnTo) ? '返回工作台' : `返回${label}任务列表`"
        :disabled="busy"
        @click="back"
      /><span>{{ label }}任务</span><span>/</span><strong>{{ task?.code || '加载中' }}</strong
      ><MaintenanceStatus v-if="task" :status="task.status" /><span
        class="inspection-heading-spacer"
      />
      <div v-if="task" class="inspection-task-header-actions">
        <ElButton v-if="task.canExecute && task.status === '执行中'" @click="openDialog('suspend')"
          >挂起</ElButton
        ><ElButton v-if="task.canExecute && task.status === '挂起'" @click="action('resume')"
          >恢复</ElButton
        ><ElButton
          v-if="
            task.canTransfer &&
            ['未开始', '待执行', '执行中', '挂起', '已过期', '审核不通过'].includes(task.status)
          "
          @click="openDialog('transfer')"
          >转派</ElButton
        ><ElButton
          v-if="task.canVoid && !['已完成', '已审核', '已作废', '过期作废'].includes(task.status)"
          @click="openDialog('void')"
          >作废</ElButton
        ><ElButton v-if="task.canVoid" type="danger" plain @click="openDialog('delete')"
          >删除</ElButton
        >
      </div>
      <ElButton text :disabled="busy" @click="refresh">刷新</ElButton>
    </header>
    <p v-if="error && task" class="inspection-error" role="alert">{{ error }}</p>
    <template v-if="task"
      ><div class="inspection-task-layout">
        <div class="inspection-task-main">
          <section class="inspection-form-card inspection-task-overview">
            <h3 class="inspection-section-title">任务信息</h3>
            <div class="inspection-task-summary">
              <div>
                <span>计划名称</span><strong>{{ task.planName }}</strong>
              </div>
              <div>
                <span>计划开始</span><strong>{{ formatDateTime(task.dueAt) }}</strong>
              </div>
              <div>
                <span>计划结束</span><strong>{{ formatDateTime(task.expiresAt) }}</strong>
              </div>
              <div>
                <span>负责人</span
                ><strong>{{
                  task.owners
                    .map((id) => choices.users.find((u) => u.id === id)?.name || id)
                    .join('、') || '任何人可点检'
                }}</strong>
              </div>
              <div v-if="task.kind === 'daily'">
                <span>班次</span><strong>{{ task.shiftDate }} · {{ task.shiftName }}</strong>
              </div>
              <div>
                <span>审核方式</span><strong>{{ task.reviewMode }}</strong>
              </div>
              <div>
                <span>实际执行人</span><strong>{{ task.ownerName || '—' }}</strong>
              </div>
              <div>
                <span>实际开始</span><strong>{{ formatDateTime(task.result.startedAt) }}</strong>
              </div>
              <div>
                <span>实际完成</span><strong>{{ formatDateTime(task.result.finishedAt) }}</strong>
              </div>
            </div>
            <div class="inspection-task-metrics">
              <span
                >应检设备 <b>{{ task.equipmentCount }}</b></span
              ><span
                >应检项目 <b>{{ task.expected }}</b></span
              ><span
                >已检 <b>{{ task.checked }}</b></span
              ><span
                >未检 <b>{{ task.missing }}</b></span
              ><span
                >异常 <b class="danger">{{ task.abnormal }}</b></span
              ><span
                >跳过 <b>{{ task.skipped }}</b></span
              ><span
                >工时
                <b
                  >{{ Math.floor(task.workSeconds / 60) }} 分 {{ task.workSeconds % 60 }} 秒</b
                ></span
              ><ElTag v-if="task.overdue" type="warning">已超过计划结束时间</ElTag>
            </div>
            <p v-if="task.remark">{{ task.remark }}</p>
          </section>
          <div class="inspection-task-equipment-layout">
            <aside v-if="task.equipment.length > 1" class="inspection-route-panel">
              <h3>巡检路线</h3>
              <button
                v-for="(e, index) in task.equipment"
                :key="e.id"
                :class="{ active: activeEquipment === e.id }"
                @click="activeEquipment = e.id"
              >
                <span class="inspection-route-index">{{ index + 1 }}</span>
                <div>
                  <strong>{{ e.name }}</strong
                  ><span>{{ e.code }}</span
                  ><small>{{
                    draft.skippedEquipmentIds?.includes(e.id)
                      ? '已跳过'
                      : checkedCount(e.id) + ' / ' + itemCount(e.id) + ' 项'
                  }}</small>
                </div>
              </button>
            </aside>
            <section class="inspection-form-card inspection-task-projects">
              <div class="inspection-equipment-heading">
                <div>
                  <h3>{{ active?.name }}</h3>
                  <span>{{ active?.code }} · {{ active?.model || '—' }}</span>
                </div>
                <span>{{ active?.department }} · {{ active?.functionLocation || '—' }}</span
                ><ElCheckbox
                  v-if="editing"
                  :model-value="skipped"
                  @update:model-value="setSkipped(Boolean($event))"
                  >跳过此设备</ElCheckbox
                >
              </div>
              <div
                v-if="editing && task.scanMode !== '无需扫码'"
                class="inspection-device-verification"
              >
                <ElInput
                  v-model="scanCode"
                  placeholder="输入或扫码设备编号"
                  @keyup.enter="action('scan', { equipmentCode: scanCode })"
                /><ElButton :loading="busy" @click="action('scan', { equipmentCode: scanCode })"
                  >校验设备</ElButton
                ><ElTag v-if="draft.scannedEquipmentIds?.includes(activeEquipment)" type="success"
                  >已校验</ElTag
                ><span>{{ task.scanMode }}</span>
              </div>
              <p v-if="task.onlyAbnormal" class="inspection-hint">
                仅填报异常项；未填项目在提交时记录为正常，必须拍照的项目仍需上传图片。
              </p>
              <p v-if="skipped" class="inspection-hint">此设备已跳过，提交时需要填写跳过原因。</p>
              <MaintenanceTaskItems
                v-model="results"
                :items="equipmentItems"
                :readonly="!editing || skipped"
                hide-module
                compact
                label="检查"
                :upload-file="inspectionUpload"
              />
            </section>
          </div>
          <section class="inspection-form-card inspection-task-description">
            <label
              ><span>检查说明</span
              ><ElInput v-if="editing" v-model="draft.description" type="textarea" :rows="3" />
              <p v-else>{{ draft.description || '—' }}</p></label
            ><label v-if="draft.skippedEquipmentIds?.length"
              ><span>跳过原因</span
              ><ElInput v-if="editing" v-model="draft.skipReason" type="textarea" :rows="2" />
              <p v-else>{{ draft.skipReason || '—' }}</p></label
            ><label
              ><span>附件</span
              ><MaintenanceAttachments
                v-model="draft.attachments!"
                :readonly="!editing"
                :upload-file="inspectionUpload"
            /></label>
          </section>
          <section v-if="draft.review" class="inspection-form-card">
            <h3>审核结果</h3>
            <div class="inspection-task-summary">
              <div>
                <span>结果</span><strong>{{ draft.review.decision }}</strong>
              </div>
              <div>
                <span>审核人</span><strong>{{ draft.review.reviewer }}</strong>
              </div>
              <div>
                <span>审核时间</span><strong>{{ formatDateTime(draft.review.reviewedAt) }}</strong>
              </div>
            </div>
            <p>{{ draft.review.description || '—' }}</p>
            <MaintenanceAttachments :model-value="draft.review.attachments || []" readonly />
            <table v-if="draft.reviewItems?.length" class="business-table inspection-picker-table">
              <thead>
                <tr>
                  <th>项目名称</th>
                  <th>审核结果</th>
                  <th>说明</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="r in draft.reviewItems" :key="r.id">
                  <td>{{ task.items.find((i) => i.id === r.id)?.name }}</td>
                  <td>{{ r.decision }}</td>
                  <td>{{ r.description || '' }}</td>
                </tr>
              </tbody>
            </table>
          </section>
          <TaskMaterials
            v-if="task"
            :task-id="String(task.id)"
            kind="点巡检"
            :can-work="!!task.canExecute && ['执行中', '挂起'].includes(task.status)"
          />
        </div>
        <aside class="inspection-timeline">
          <h3>{{ label }}时间轴</h3>
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
          editing ? '检查结果可先保存，全部完成后提交' : '检查结果及操作记录保留在任务中'
        }}</template
        ><ElButton :disabled="busy" @click="back">{{ editing ? '取消' : '返回' }}</ElButton
        ><ElButton
          v-if="
            task.canExecute && ['未开始', '待执行', '已过期', '审核不通过'].includes(task.status)
          "
          type="primary"
          :loading="busy"
          @click="action('start')"
          >{{ task.status === '审核不通过' ? '修改结果' : '开始执行' }}</ElButton
        ><ElButton v-if="editing" :loading="busy" @click="action('draft', { ...draft })"
          >保存</ElButton
        ><ElButton v-if="editing" type="primary" :loading="busy" @click="submit">提交</ElButton
        ><ElButton
          v-if="task.canReview && task.status === '待审核'"
          type="primary"
          @click="reviewOpen = true"
          >审核</ElButton
        ></AppFormFooter
      >
    </template>
    <AppLoadState v-else :loading="loading" :error="error" @retry="load" />
    <AppDialog v-model="dialogOpen" :title="dialogTitle" :busy="busy"
      ><ElSelect
        v-if="dialog === 'transfer'"
        v-model="owners"
        multiple
        filterable
        placeholder="选择负责人"
        ><ElOption
          v-for="u in choices.users"
          :key="u.id"
          :value="u.id"
          :label="u.name + ' · ' + u.department" /></ElSelect
      ><ElInput
        v-else
        v-model="description"
        type="textarea"
        :rows="4"
        placeholder="请填写原因"
      /><template #footer
        ><div class="inspection-dialog-actions">
          <ElButton :disabled="busy" @click="dialog = ''">取消</ElButton
          ><ElButton type="primary" :loading="busy" @click="confirmDialog">提交</ElButton>
        </div></template
      ></AppDialog
    >
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
