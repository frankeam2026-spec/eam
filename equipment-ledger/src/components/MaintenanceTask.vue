<script setup lang="ts">import { tr } from '../i18n';

import {
  AcceptanceDecision,
  normalizeAcceptanceDecision,
  acceptanceDecisionLabel,
} from '../config/acceptance';
import PagedChoiceField from './common/PagedChoiceField.vue';
import { planningSelectors, resolvePlanning, mergeOptions, type SelectorRow } from '../selectors';
const selector = planningSelectors('maintenance');

import BusinessFields from './business/BusinessFields.vue';
import { useBusinessExtension } from '../composables/useBusinessExtension';
const { extension, extensionErrors, prepareExtension, extensionError } = useBusinessExtension();
import TaskAnomalies from './common/TaskAnomalies.vue';
import { confirmSurplus } from '../services/surplusConfirmation';
import TaskMaterials from './TaskMaterials.vue';
import { workspaceReturn } from '../utils/workspaceNavigation';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { formatDateTime } from '../utils/dateTime';
import AppBackButton from './common/AppBackButton.vue';
import { computed, ref, watch, nextTick, onBeforeUnmount } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElDatePicker,
  ElSwitch,
  ElTag,
  ElMessage,
  ElRate,
  ElLink,
  ElDrawer,
} from 'element-plus';
import { maintenanceApi, type MaintenanceTask, type ItemResult, type Choice } from '../maintenance';
import type { RepairAttachment } from '../api';
import { useAuthStore } from '../stores/auth';
import AppFormFooter from './common/AppFormFooter.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import MaintenanceTaskItems from './common/MaintenanceTaskItems.vue';
import MaintenanceTaskSummary from './common/MaintenanceTaskSummary.vue';
import MaintenanceTaskTimeline from './common/MaintenanceTaskTimeline.vue';
import AppIcon from './common/AppIcon.vue';
import type { MaintenanceProjectFilter } from '../maintenance';
import AppDialog from './common/AppDialog.vue';
import AppLoadState from './common/AppLoadState.vue';
import MaintenanceAcceptanceDialog from './common/MaintenanceAcceptanceDialog.vue';
import MaintenanceAttachments from './common/MaintenanceAttachments.vue';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore(),
  task = ref<MaintenanceTask>(),
  users = ref<Choice[]>([]),
  error = ref(''),
  busy = ref(false),
  loading = ref(true),
  processing = ref(false),
  dialog = ref(''),
  scanCode = ref('');
const results = ref<ItemResult[]>([]),
  range = ref<string[]>([]),
  description = ref(''),
  collaborators = ref<string[]>([]),
  attachments = ref<RepairAttachment[]>([]),
  reason = ref(''),
  owners = ref<string[]>([]);
const { markSaved, permitLeave } = useUnsavedChanges(
  () => ({
    items: results.value,
    range: range.value,
    description: description.value,
    collaborators: collaborators.value,
    attachments: attachments.value,
    customFields: task.value?.customFields,
  }),
  () => processing.value,
  () => busy.value,
);
async function back() {
  if (busy.value || !(await permitLeave())) return;
  if (processing.value) processing.value = false;
  else
    await router.push(
      workspaceReturn(route.query.returnTo) || { name: 'maintenance', params: { kind: 'tasks' } },
    );
}
const canManage = computed(() => auth.can('maintenanceTask', '编辑')),
  canVoid = computed(() => auth.can('maintenanceTask', '删除'));
const timelineOpen = ref(false);
const taskBody = ref<HTMLElement>();
const narrowBody = ref(true);
const timelinePreferenceKey = computed(() =>
  auth.user ? `eam:${auth.user.tenantId}:${auth.user.id}:maintenance-task:timeline` : '',
);
watch(
  timelinePreferenceKey,
  (key) => {
    try {
      timelineOpen.value = !!key && localStorage.getItem(key) === 'open';
    } catch {
      timelineOpen.value = false;
    }
  },
  { immediate: true },
);
watch(timelineOpen, (open) => {
  if (!timelinePreferenceKey.value) return;
  try {
    localStorage.setItem(timelinePreferenceKey.value, open ? 'open' : 'closed');
  } catch {
    /* Storage can be disabled; folding still works for this page. */
  }
});
let bodyObserver: ResizeObserver | undefined;
watch(taskBody, (element) => {
  bodyObserver?.disconnect();
  if (!element) return;
  narrowBody.value = element.clientWidth < 1100;
  bodyObserver = new ResizeObserver(([entry]) => {
    if (entry) narrowBody.value = entry.contentRect.width < 1100;
  });
  bodyObserver.observe(element);
});
onBeforeUnmount(() => bodyObserver?.disconnect());
const projectFilter = ref<MaintenanceProjectFilter>('all');
const projectSection = ref<HTMLElement>();
const projectFilterLabel = computed(() =>
  projectFilter.value === 'completed'
    ? '已完成项目'
    : projectFilter.value === 'abnormal'
      ? '异常项目'
      : '',
);
const visibleProjectIds = computed(() => {
  const statistics = task.value?.statistics;
  if (!statistics || projectFilter.value === 'all') return undefined;
  return projectFilter.value === 'completed'
    ? statistics.completedItemIds
    : statistics.abnormalItemIds;
});
async function filterProjects(value: MaintenanceProjectFilter) {
  projectFilter.value = value;
  await nextTick();
  projectSection.value?.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
}
const abnormalCount = computed(() => results.value.filter((r) => abnormal(r)).length);
function rememberPeople(rows: SelectorRow[]) {
  users.value = mergeOptions(users.value, rows);
}
const names = (ids: string[] = []) =>
  ids.map((id) => users.value.find((u) => u.id === id)?.name || id).join('、');
function now() {
  const d = new Date(),
    p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}:${p(d.getSeconds())}`;
}
let loadRevision = 0;
function clearTask() {
  task.value = undefined;
  projectFilter.value = 'all';
}
async function load(): Promise<boolean> {
  const revision = ++loadRevision;
  const requestedId = String(route.params.id);
  loading.value = true;
  error.value = '';
  try {
    const d = await maintenanceApi.task(requestedId);
    const c = await resolvePlanning('maintenance', d.data);
    if (revision !== loadRevision || requestedId !== String(route.params.id)) return false;
    task.value = d.data;
    users.value = c.users;
    return true;
  } catch (e) {
    if (revision === loadRevision && requestedId === String(route.params.id))
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
    return false;
  } finally {
    if (revision === loadRevision) loading.value = false;
  }
}
watch(
  [() => route.params.id, () => route.query.taskAction],
  async () => {
    processing.value = false;
    dialog.value = '';
    clearTask();
    if (!(await load())) return;
    if (
      route.query.taskAction === '验收' &&
      task.value?.canAccept &&
      task.value.status === '待验收'
    )
      openAction('accept');
    if (
      ['处理', '执行'].includes(String(route.query.taskAction)) &&
      task.value?.canExecute &&
      task.value.status === '执行中'
    )
      startProcess();
    if (
      route.query.taskAction === '执行' &&
      task.value?.canExecute &&
      task.value.status === '待执行'
    )
      openAction('start');
  },
  { immediate: true },
);
function abnormal(r: ItemResult) {
  const item = task.value?.items.find((i) => i.id === r.id);
  if (!item || !r.value.trim()) return false;
  if (item.valueType === '选项') return !item.normalOptions.includes(r.value);
  if (item.valueType === '数值') {
    const n = Number(r.value);
    return (
      !Number.isFinite(n) ||
      (item.lower !== '' && n < Number(item.lower)) ||
      (item.upper !== '' && n > Number(item.upper))
    );
  }
  return false;
}
function startProcess() {
  if (
    !task.value ||
    busy.value ||
    processing.value ||
    !task.value.canExecute ||
    task.value.status !== '执行中'
  )
    return;
  const saved = JSON.parse(JSON.stringify(task.value.result)) as MaintenanceTask['result'];
  results.value = task.value.items.map((i) => {
    const previous = saved.items?.find((v) => v.id === i.id);
    let value = '';
    if (task.value?.autoFill === '正常值') {
      if (i.valueType === '选项') value = i.normalOptions[0] || '';
      else if (i.valueType === '数值' && i.lower !== '' && i.upper !== '')
        value = String((Number(i.lower) + Number(i.upper)) / 2);
    }
    return { id: i.id, value, resolved: false, measure: '', remark: '', images: [], ...previous };
  });
  range.value = [saved.startAt || saved.startedAt || now(), now()];
  description.value = saved.description || '';
  collaborators.value = saved.collaborators || [];
  attachments.value = saved.attachments || [];
  processing.value = true;
  projectFilter.value = 'all';
  markSaved();
}
async function action(name: string, body: Record<string, unknown> = {}) {
  if (!task.value || busy.value) return;
  const requestedId = task.value.id;
  const rowVersion = task.value.rowVersion;
  busy.value = true;
  try {
    const response = await maintenanceApi.action(requestedId, name, {
      rowVersion,
      ...body,
    });
    window.dispatchEvent(new Event('repair-inbox-changed'));
    if (requestedId !== String(route.params.id)) return;
    task.value = response.data;
    dialog.value = '';
    markSaved();
    processing.value = false;
    ElMessage.success(tr('ui.equipment.text0196'));
  } catch (e) {
    if (extensionError(e)) return;
    if (requestedId === String(route.params.id))
      ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
  } finally {
    busy.value = false;
  }
}
function openAction(name: string) {
  reason.value = '';
  scanCode.value = '';
  owners.value = task.value?.owners || [];
  dialog.value = name;
}
function submitDialog() {
  if (dialog.value === 'transfer') action('transfer', { owners: owners.value });
  else if (dialog.value === 'start') action('start', { equipmentCode: scanCode.value });
  else action(dialog.value, { description: reason.value });
}
async function submit() {
  if (!task.value || !prepareExtension(task.value)) return;
  if (range.value.length !== 2) {
    ElMessage.error(tr('ui.equipment.text0866'));
    return;
  }
  const surplus = await confirmSurplus('保养', String(route.params.id));
  if (surplus === null) return;
  action('process', {
    customFields: task.value.customFields,
    customSchemaVersion: task.value.customSchemaVersion,
    ...surplus,
    startAt: range.value[0],
    endAt: range.value[1],
    description: description.value,
    collaborators: collaborators.value,
    attachments: attachments.value,
    items: results.value,
  });
}
const dialogTitle = computed(
  () =>
    ({
      accept: '保养验收',
      transfer: '转派保养任务',
      suspend: '挂起任务',
      void: '作废任务',
      start: '确认设备',
    })[dialog.value] || '任务操作',
);
</script>
<template>
  <section class="maintenance-task-page">
    <header class="maintenance-task-head">
      <div>
        <AppBackButton
          :label="
            processing
              ? tr('ui.equipment.text0867')
              : workspaceReturn(route.query.returnTo)
                ? tr('ui.equipment.text0418')
                : tr('ui.equipment.text0868')
          "
          :disabled="busy"
          @click="back"
        /><strong>{{ processing ? tr('ui.equipment.text0869') : tr('ui.equipment.text0011') }}</strong
        ><span>{{ task?.code }}</span> <MaintenanceStatus v-if="task" :status="task.status" />
      </div>
      <div v-if="task" class="task-header-actions">
        <ElButton
          :type="timelineOpen ? 'primary' : 'default'"
          plain
          :aria-expanded="timelineOpen"
          :aria-controls="
            narrowBody ? 'maintenance-task-timeline-drawer' : 'maintenance-task-timeline'
          "
          @click="timelineOpen = !timelineOpen"
        >
          <AppIcon name="Clock" />{{ tr('ui.equipment.text0456') }}
        </ElButton>
        <template v-if="!processing">
          <ElButton
            v-if="task.canExecute && task.status === '待执行'"
            type="primary"
            :loading="busy"
            @click="task.scanStart ? openAction('start') : action('start')"
            >{{ tr('ui.equipment.text0870') }}</ElButton
          ><ElButton
            v-if="task.canExecute && task.status === '执行中'"
            type="primary"
            @click="startProcess"
            >{{ tr('ui.equipment.text0766') }}</ElButton
          ><ElButton
            v-if="task.canAccept && task.status === '待验收'"
            type="primary"
            @click="openAction('accept')"
            >{{ tr('ui.equipment.text0763') }}</ElButton
          ><ElButton
            v-if="task.canExecute && task.status === '执行中'"
            @click="openAction('suspend')"
            >{{ tr('ui.equipment.text0421') }}</ElButton
          ><ElButton v-if="task.canExecute && task.status === '挂起'" @click="action('resume')"
            >{{ tr('ui.equipment.text0422') }}</ElButton
          ><ElButton
            v-if="canManage && ['待执行', '执行中', '挂起'].includes(task.status)"
            @click="openAction('transfer')"
            >{{ tr('ui.equipment.text0423') }}</ElButton
          ><ElButton
            v-if="canVoid && !['已完成', '已作废', '过期作废'].includes(task.status)"
            @click="openAction('void')"
            >{{ tr('ui.equipment.text0215') }}</ElButton
          >
        </template>
      </div>
    </header>
    <AppLoadState v-if="!task" :loading="loading" :error="error" @retry="load" />
    <p v-else-if="error" class="maintenance-error" role="alert">{{ error }}</p>
    <div
      v-if="task"
      ref="taskBody"
      class="maintenance-task-body"
      :class="{ 'timeline-expanded': timelineOpen && !narrowBody }"
    >
      <main class="maintenance-task-main">
        <MaintenanceTaskSummary
          v-if="!processing && task.statistics"
          :statistics="task.statistics"
          :model-value="projectFilter"
          @update:model-value="filterProjects"
        />

        <section class="task-equipment">
          <!-- <div>
            <h2>{{ task.equipmentName }}</h2>
            <span>{{ task.equipmentCode }} · {{ task.model || '—' }}</span>
          </div> -->
          <dl>
            <div>
              <dt>{{ tr('ui.equipment.text0871') }}</dt>
              <dd>
                <ElLink
                  v-if="task.equipmentId && auth.can('equipment', '查看')"
                  type="primary"
                  :underline="false"
                  :href="
                    router.resolve({ name: 'equipment-history', params: { id: task.equipmentId } })
                      .href
                  "
                  @click.prevent="
                    router.push({ name: 'equipment-history', params: { id: task.equipmentId } })
                  "
                  >{{ task.equipmentCode }}</ElLink
                >
                <template v-else>{{ task.equipmentCode }}</template>
              </dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0872') }}</dt>
              <dd>{{ task.equipmentName }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0873') }}</dt>
              <dd>{{ task.model }}</dd>
            </div>

            <div>
              <dt>{{ tr('ui.equipment.text0874') }}</dt>
              <dd>{{ task.planName }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0875') }}</dt>
              <dd>{{ task.typeName }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0876') }}</dt>
              <dd>{{ task.dueDate }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0877') }}</dt>
              <dd>{{ task.department }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0878') }}</dt>
              <dd>{{ task.ownerId ? names([task.ownerId]) : names(task.owners) }}</dd>
            </div>
            <div>
              <dt>{{ tr('ui.equipment.text0879') }}</dt>
              <dd>{{ task.reviewMode === '无需验收' ? tr('ui.equipment.text0880') : names(task.reviewers) }}</dd>
            </div>

            <div>
              <dt>{{ tr('ui.equipment.text0881') }}</dt>
              <dd>{{ task.result.description }}</dd>
            </div>

            <div>
              <dt>{{ tr('ui.equipment.text0882') }}</dt>
              <dd> {{ formatDateTime(task.result.startAt) }} {{ tr('ui.equipment.text0883') }}
              {{ formatDateTime(task.result.endAt) }}</dd>
            </div>

            <div>
              <dt>{{ tr('ui.equipment.text0884') }}</dt>
              <dd>{{ names(task.result.collaborators) }}</dd>
            </div>

            <MaintenanceAttachments :model-value="task.result.attachments || []" readonly />
          </dl>
        </section>

        <template v-if="processing"
          ><section class="task-form">
            <h3>{{ tr('ui.equipment.text0885') }}</h3>
            <div class="task-form-grid">
              <label class="wide"
                ><span class="required">{{ tr('ui.equipment.text0886') }}</span
                ><ElDatePicker
                  v-model="range"
                  type="datetimerange"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  range-separator="至"
                  start-placeholder="开始时间"
                  end-placeholder="结束时间" /></label
              ><label class="wide"
                ><span>{{ tr('ui.equipment.text0887') }}</span
                ><PagedChoiceField
                  v-model="collaborators"
                  :placeholder="tr('ui.equipment.text0888')"
                  multiple
                  :options="users"
                  :load-rows="selector.users"
                  :title="tr('ui.equipment.text0335')"
                  @select="rememberPeople" /></label
              ><label class="wide"
                ><span>{{ tr('ui.equipment.text0889') }}</span
                ><ElInput
                  v-model="description"
                  type="textarea"
                  :rows="3"
                  :placeholder="tr('ui.equipment.text0890')" /></label
              ><label class="wide"
                ><span>{{ tr('ui.equipment.text0222') }}</span><MaintenanceAttachments v-model="attachments"
              /></label>
            </div>
          </section>
          <section class="task-items">
            <div class="task-section-title">
              <h3>
                {{ tr('ui.equipment.text0775') }} <small>{{ task.items.length }} {{ tr('ui.equipment.text0394') }}</small>
              </h3>
              <ElTag v-if="abnormalCount" type="danger">{{ abnormalCount }} {{ tr('ui.equipment.text0891') }}</ElTag>
            </div>
            <MaintenanceTaskItems v-model="results" :items="task.items" wide-layout /></section
        ></template>
        <template v-else
          ><section ref="projectSection" class="task-form">
            <div class="task-section-title">
              <h3>{{ tr('ui.equipment.text0775') }}</h3>
              <div v-if="projectFilter !== 'all'" class="project-filter-actions">
                <ElTag>{{ projectFilterLabel }}</ElTag
                ><ElButton link type="primary" @click="projectFilter = 'all'">{{ tr('ui.equipment.text0892') }}</ElButton>
              </div>
            </div>
            <MaintenanceTaskItems
              :items="task.items"
              :model-value="task.result.items || []"
              :visible-item-ids="visibleProjectIds"
              wide-layout
              readonly
            />
          </section>
          <section
            v-if="task.result.description || task.result.attachments?.length"
            class="task-form"
          >

          </section>
          <!-- <section v-if="task.result.acceptance" class="task-form">
            <h3>验收结果</h3>
            <div class="task-section-title">
              <ElTag :type="normalizeAcceptanceDecision(task.result.acceptance.decision) === AcceptanceDecision.ACCEPT ? 'success' : 'danger'">{{
                acceptanceDecisionLabel(task.result.acceptance.decision)
              }}</ElTag
              ><ElRate :model-value="task.result.acceptance.score" disabled />
            </div>
            <p>{{ task.result.acceptance.description || '—' }}</p>
            <MaintenanceAttachments
              :model-value="task.result.acceptance.attachments"
              readonly
            /></section
        > -->
        </template>
        <TaskMaterials
          v-if="task"
          :task-id="String(task.id)"
          kind="保养"
          :can-work="!!task.canExecute && ['执行中', '挂起'].includes(task.status)"
        />
        <TaskAnomalies kind="保养" :task-id="String(route.params.id)" :can-record="processing" />
        <BusinessFields
          ref="extension"
          v-model="task"
          object="maintenance_task"
          :readonly="!processing"
          :errors="extensionErrors"
        />
      </main>
      <aside
        v-if="timelineOpen && !narrowBody"
        id="maintenance-task-timeline"
        class="maintenance-timeline"
      >
        <div class="timeline-heading">
          <h3>{{ tr('ui.equipment.text0893') }}</h3>
          <ElButton
            text
            :aria-label="tr('ui.equipment.text0894')"
            :title="tr('ui.equipment.text0895')"
            @click="timelineOpen = false"
            ><AppIcon name="Close"
          /></ElButton>
        </div>
        <MaintenanceTaskTimeline :events="task.events" />
      </aside>
      <ElDrawer
        :model-value="timelineOpen && narrowBody"
        :title="tr('ui.equipment.text0893')"
        size="min(340px, 100vw)"
        :close-on-click-modal="false"
        :close-on-press-escape="false"
        append-to-body
        @update:model-value="timelineOpen = $event"
      >
        <div id="maintenance-task-timeline-drawer">
          <MaintenanceTaskTimeline :events="task.events" />
        </div>
      </ElDrawer>
    </div>

    <AppFormFooter v-if="processing">
      <ElButton :disabled="busy" @click="back">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton type="primary" :loading="busy" @click="submit">{{ tr('ui.equipment.text0253') }}</ElButton></AppFormFooter
    >
    <MaintenanceAcceptanceDialog
      :model-value="dialog === 'accept'"
      :busy="busy"
      :caption="task ? task.code + ' · ' + task.equipmentName : ''"
      @update:model-value="
        (value) => {
          if (!value) dialog = '';
        }
      "
      @submit="(value) => action('accept', value)"
    />
    <AppDialog
      :model-value="!!dialog && dialog !== 'accept'"
      :title="dialogTitle"
      :busy="busy"
      @update:model-value="
        (v) => {
          if (!v) dialog = '';
        }
      "
      ><div class="task-dialog-form">
        <label v-if="dialog === 'transfer'"
          ><span>{{ tr('ui.equipment.text0811') }}</span
          ><PagedChoiceField
            v-model="owners"
            multiple
            :options="users"
            :load-rows="selector.users"
            :title="tr('ui.equipment.text0335')"
            @select="rememberPeople" /></label
        ><label v-else-if="dialog === 'start'"
          ><span>{{ tr('ui.equipment.text0078') }}</span
          ><ElInput v-model="scanCode" :placeholder="tr('ui.equipment.text0896')" /></label
        ><label v-if="dialog !== 'transfer' && dialog !== 'start'"
          ><span>{{ tr('ui.equipment.text0177') }}</span
          ><ElInput v-model="reason" type="textarea" :rows="3" :placeholder="tr('ui.equipment.text0770')"
        /></label>
      </div>
      <template #footer
        ><ElButton @click="dialog = ''">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="submitDialog">{{ tr('ui.equipment.text0253') }}</ElButton></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.project-filter-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.maintenance-task-page small {
  font-size: 13px;
}
.maintenance-task-page {
  height: calc(100vh - 54px);
  display: flex;
  flex-direction: column;
  font-size: 13px;
  color: #334155;
  overflow: hidden;
  background: #fff;
}
.maintenance-task-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  height: 50px;
  min-height: 50px;
  padding: 0 16px;
  border-bottom: 1px solid #e2e8f0;
}
.maintenance-task-head > div {
  display: flex;
  align-items: center;
  gap: 12px;
}
.maintenance-task-head strong {
  font-size: 14px;
}
.maintenance-task-head span {
  color: var(--text-secondary);
}
.maintenance-task-body {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  flex: 1;
  min-height: 0;
  overflow: hidden;
}
.maintenance-task-body.timeline-expanded {
  grid-template-columns: minmax(0, 1fr) 280px;
}
.task-header-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
}
.maintenance-task-main {
  overflow: auto;
  min-width: 0;
  padding: 16px;
}
.task-equipment {
  /* border-bottom: 1px solid #e9eef5; */
  padding-bottom: 16px;
}
.task-equipment h2 {
  font-size: 16px;
  margin: 0 0 8px;
}
.task-equipment span {
  color: var(--text-secondary);
}
.task-equipment dl {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 20px;
  margin: 24px 0 0;
}
.task-equipment dl > div {
  display: flex;
  gap: 12px;
}
.task-equipment dt {
  color: var(--text-secondary);
  flex: none;
}
.task-equipment dd {
  margin: 0;
  line-height: 1.5;
}
.task-form {
  padding: 22px 0;
  border-bottom: 1px solid #e9eef5;
}
.task-form h3,
.task-items h3,
.maintenance-timeline h3 {
  font-size: 14px;
  font-weight: 600;
  margin: 0 0 20px;
}
.task-form h3:before,
.task-items h3:before {
  content: '';
  display: inline-block;
  width: 3px;
  height: 13px;
  margin-right: 8px;
  background: var(--brand);
  border-radius: 2px;
}
.task-items {
  padding-top: 22px;
}
.task-form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 24px;
}
.task-form-grid label {
  display: flex;
  gap: 12px;
  align-items: center;
  min-width: 0;
}
.task-form-grid label > span {
  width: 78px;
  flex: none;
  text-align: right;
  color: var(--text-secondary);
}
.task-form-grid label > :last-child {
  flex: 1;
  min-width: 0;
}
.task-form-grid .wide {
  grid-column: 1/-1;
}
.required:before {
  content: '*';
  color: #e35166;
  margin-right: 4px;
}
.task-section-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}
.task-section-title h3 {
  margin: 0;
}
.task-section-title small {
  color: var(--text-secondary);
  font-weight: 400;
}
.item-reference {
  color: var(--text-secondary);
  font-size: 13px;
  margin-top: 14px;
  line-height: 1.6;
}
.maintenance-timeline {
  overflow: auto;
  padding: 22px 20px;
  border-left: 1px solid #e7edf4;
  background: #fafbfd;
}
.timeline-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20px;
}
.timeline-heading h3 {
  margin: 0;
}
.task-dialog-form {
  display: grid;
  gap: 20px;
}
.task-dialog-form label {
  display: flex;
  gap: 16px;
  align-items: center;
}
.task-dialog-form label > span {
  width: 65px;
  flex: none;
  color: var(--text-secondary);
}
.task-dialog-form label > :last-child {
  flex: 1;
  min-width: 0;
}
.maintenance-error {
  color: #d34255;
  padding: 12px;
  background: #fff1f2;
}
.maintenance-task-page :deep(.el-button + .el-button) {
  margin-left: 0;
}
.maintenance-task-page :deep(.el-input__inner),
.maintenance-task-page :deep(.el-textarea__inner) {
  font-size: 13px;
}
.maintenance-task-page :deep(.el-date-editor) {
  width: 100%;
}
@media (max-width: 1100px) {
  .task-equipment dl {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 800px) {
  .maintenance-task-body {
    display: block;
    overflow: auto;
  }
  .maintenance-task-main {
    overflow: visible;
    padding: 16px;
  }
  .maintenance-timeline {
    overflow: visible;
    border-left: 0;
    border-top: 1px solid #e2e8f0;
  }
  .task-form-grid {
    grid-template-columns: 1fr;
  }
  .maintenance-task-head {
    height: auto;
    padding: 10px;
    flex-wrap: wrap;
  }
  .maintenance-task-head > div {
    flex-wrap: wrap;
  }
  .task-equipment dl {
    grid-template-columns: 1fr;
  }
}
</style>
