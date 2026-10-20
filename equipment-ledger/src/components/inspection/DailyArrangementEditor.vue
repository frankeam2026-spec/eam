<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTimePicker,
  ElDatePicker,
  ElTabs,
  ElTabPane,
  ElAlert,
  ElMessage,
  ElTableColumn,
} from 'element-plus';
import { tr } from '../../i18n';
import { ApiError } from '../../api';
import {
  emptyArrangement,
  inspectionApi,
  type InspectionDefinition,
  type InspectionChoices,
  type OperationTeam,
  type DailyPreview,
} from '../../inspection';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import AppDialog from '../common/AppDialog.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import { planningSelectors, resolvePlanning, type SelectorRow } from '../../selectors';
const form = defineModel<InspectionDefinition>({ required: true });
const props = defineProps<{
  choices: InspectionChoices;
  errors: Record<string, string>;
  projectOptions: { key: string; label: string; standardId: string; itemId: string }[];
  canCreateTeam: boolean;
  canEditTeam: boolean;
}>();
const emit = defineEmits<{ people: [rows: SelectorRow[]] }>();
const selector = planningSelectors('inspection');
const active = ref('0'),
  teams = ref<OperationTeam[]>([]),
  teamOpen = ref(false),
  teamName = ref(''),
  editingTeam = ref<OperationTeam>(),
  teamMembers = ref<string[]>([]),
  busy = ref(false),
  teamError = ref('');
const current = computed(() => form.value.arrangements?.[Number(active.value)]);
const path = (key: string) => `arrangements.${active.value}.${key}`;
const err = (key: string) => props.errors[path(key)] || '';
const frequencies = ['daily', 'weekly', 'monthly', 'shift', 'slots', 'interval', 'event'] as const;
const modes = ['specified', 'team', 'roster'] as const;
const preview = ref<DailyPreview>(),
  previewOpen = ref(false),
  previewBusy = ref(false),
  previewDays = ref(7),
  previewError = ref('');
let previewRevision = 0;
watch(
  () => props.errors,
  (errors) => {
    const match = Object.keys(errors)
      .find((key) => /^arrangements\.\d+\./.test(key))
      ?.match(/^arrangements\.(\d+)\./);
    if (match && form.value.arrangements[Number(match[1])]) active.value = match[1]!;
  },
);
const selectedProjects = computed({
  get: () => current.value?.projectRefs.map((r) => `${r.standardId}:${r.itemId}`) || [],
  set: (keys: string[]) => {
    if (current.value)
      current.value.projectRefs = props.projectOptions
        .filter((p) => keys.includes(p.key))
        .map((p) => ({ standardId: p.standardId, itemId: p.itemId }));
  },
});
function equipmentPage(q: string, page: number, size: number) {
  const filtered = equipment.value.filter((e) =>
    `${e.code} ${e.name}`.toLowerCase().includes(q.toLowerCase()),
  );
  return Promise.resolve({
    data: filtered.slice((page - 1) * size, page * size),
    page: { number: page, size, total: filtered.length },
  });
}
function projectPage(q: string, page: number, size: number) {
  const filtered = props.projectOptions
    .filter((p) => p.label.toLowerCase().includes(q.toLowerCase()))
    .map((p) => ({ id: p.key, name: p.label }));
  return Promise.resolve({
    data: filtered.slice((page - 1) * size, page * size),
    page: { number: page, size, total: filtered.length },
  });
}
const equipment = computed(() =>
  form.value.details.map((d) => ({
    id: d.equipmentId,
    name: props.choices.equipment.find((e) => e.id === d.equipmentId)?.name || d.equipmentId,
    code: props.choices.equipment.find((e) => e.id === d.equipmentId)?.code || '',
  })),
);
function add() {
  const a = emptyArrangement(form.value.startDate);
  a.owners = [...form.value.owners];
  a.followUpUserId = form.value.owners[0] || '';
  form.value.arrangements.push(a);
  active.value = String(form.value.arrangements.length - 1);
}
function remove() {
  form.value.arrangements.splice(Number(active.value), 1);
  active.value = String(Math.max(0, Number(active.value) - 1));
}
function addSlot() {
  current.value?.slots.push({
    id: crypto.randomUUID().replaceAll('-', ''),
    name: '',
    start: '08:00:00',
    end: '16:00:00',
  });
}
async function loadTeams() {
  try {
    teams.value = (await inspectionApi.operationTeams()).data;
  } catch (e) {
    teamError.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  }
}
function newTeam() {
  editingTeam.value = undefined;
  teamName.value = '';
  teamMembers.value = [];
  teamError.value = '';
  teamOpen.value = true;
}
async function editTeam() {
  const team = teams.value.find((t) => t.id === current.value?.teamId);
  if (!team) return;
  editingTeam.value = team;
  teamName.value = team.name;
  teamMembers.value = [...team.members];
  teamError.value = '';
  teamOpen.value = true;
  try {
    emit('people', (await resolvePlanning('inspection', { owners: team.members })).users);
  } catch (e) {
    teamError.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  }
}
async function createTeam() {
  busy.value = true;
  teamError.value = '';
  try {
    const team = (
      await (editingTeam.value
        ? inspectionApi.updateOperationTeam(editingTeam.value.id, {
            name: teamName.value,
            members: teamMembers.value,
            rowVersion: editingTeam.value.rowVersion || 0,
          })
        : inspectionApi.createOperationTeam({
            name: teamName.value,
            departmentId: form.value.departmentId,
            members: teamMembers.value,
          }))
    ).data;
    teams.value = [...teams.value.filter((t) => t.id !== team.id), team];
    if (current.value) current.value.teamId = team.id;
    teamOpen.value = false;
  } catch (e) {
    teamError.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
async function showPreview() {
  const revision = ++previewRevision;
  preview.value = undefined;
  previewOpen.value = true;
  previewBusy.value = true;
  previewError.value = '';
  try {
    const result = await inspectionApi.dailyPreview({
      ...form.value,
      previewDays: previewDays.value,
    });
    if (revision === previewRevision) preview.value = result.data;
  } catch (e) {
    if (revision === previewRevision)
      previewError.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    if (e instanceof ApiError) ElMessage.error(Object.values(e.fieldErrors)[0] || e.message);
  } finally {
    if (revision === previewRevision) previewBusy.value = false;
  }
}
onMounted(() => {
  if (!form.value.arrangements) form.value.arrangements = [];
  if (!form.value.workdays) form.value.workdays = [];
  void loadTeams();
});
</script>
<template>
  <section class="inspection-form-card daily-arrangements">
    <div class="inspection-section-tools">
      <h3>{{ tr('daily.title') }}</h3>
      <ElButton @click="add">{{ tr('daily.add') }}</ElButton
      ><ElSelect v-model="previewDays" class="preview-period"
        ><ElOption :value="7" :label="tr('daily.sevenDays')" /><ElOption
          :value="30"
          :label="tr('daily.thirtyDays')" /></ElSelect
      ><ElButton :loading="previewBusy" @click="showPreview">{{ tr('daily.preview') }}</ElButton>
    </div>
    <ElAlert
      v-if="errors.arrangements"
      :title="errors.arrangements"
      type="error"
      :closable="false"
    />
    <p v-if="!form.arrangements.length" class="daily-empty">{{ tr('daily.help') }}</p>
    <ElTabs v-if="form.arrangements.length" v-model="active" class="arrangement-tabs">
      <ElTabPane
        v-for="(a, index) in form.arrangements"
        :key="index"
        :name="String(index)"
        :label="a.name || tr('daily.unnamed')"
      >
        <ElForm label-width="110px" class="daily-grid" @submit.prevent>
          <h4 class="daily-group-title full">{{ tr('daily.section.scope') }}</h4>
          <ElFormItem :label="tr('daily.name')" required :error="err('name')"
            ><ElInput v-model="a.name" maxlength="150"
          /></ElFormItem>
          <ElFormItem :label="tr('daily.enabled')"
            ><ElSwitch v-model="a.enabled" /><ElButton type="danger" link @click="remove">{{
              tr('daily.remove')
            }}</ElButton></ElFormItem
          >
          <ElFormItem :label="tr('daily.equipment')" :error="err('equipmentIds')"
            ><PagedChoiceField
              v-model="a.equipmentIds"
              multiple
              :options="equipment"
              :load-rows="equipmentPage"
              :title="tr('daily.equipment')"
              :placeholder="tr('daily.allEquipment')"
          /></ElFormItem>
          <ElFormItem :label="tr('daily.projects')" :error="err('projectRefs')"
            ><PagedChoiceField
              v-model="selectedProjects"
              multiple
              :options="projectOptions.map((p) => ({ id: p.key, name: p.label }))"
              :load-rows="projectPage"
              :title="tr('daily.projects')"
              :placeholder="tr('daily.allProjects')"
          /></ElFormItem>
          <h4 class="daily-group-title full">{{ tr('daily.section.time') }}</h4>
          <ElFormItem :label="tr('daily.field.frequency')" required :error="err('frequency')"
            ><ElSelect
              v-model="a.frequency"
              @change="
                a.personMode === 'roster' && a.frequency !== 'shift' && (a.personMode = 'specified')
              "
              ><ElOption
                v-for="f in frequencies"
                :key="f"
                :value="f"
                :label="tr('daily.frequency.' + f)" /></ElSelect
          ></ElFormItem>
          <ElFormItem :label="tr('daily.anchorDate')" required :error="err('anchorDate')"
            ><ElDatePicker v-model="a.anchorDate" type="date" value-format="YYYY-MM-DD"
          /></ElFormItem>
          <ElFormItem
            v-if="['daily', 'weekly', 'monthly', 'interval'].includes(a.frequency)"
            :label="
              tr(
                a.frequency === 'interval' ? 'daily.intervalMinutes' : 'daily.every.' + a.frequency,
              )
            "
            :error="err('intervalCount')"
            ><ElInputNumber
              v-model="a.intervalCount"
              :min="1"
              :max="10000"
              :precision="0"
              controls-position="right"
          /></ElFormItem>
          <ElFormItem
            v-if="['weekly', 'monthly'].includes(a.frequency)"
            :label="tr('daily.days')"
            :error="err('days')"
            ><ElSelect v-model="a.days" multiple collapse-tags
              ><ElOption
                v-for="d in a.frequency === 'weekly' ? 7 : 31"
                :key="d"
                :value="d"
                :label="
                  a.frequency === 'weekly' ? tr('daily.weekday.' + d) : String(d)
                " /></ElSelect
          ></ElFormItem>
          <ElFormItem
            v-if="!['shift', 'slots', 'event'].includes(a.frequency)"
            :label="tr('daily.startTime')"
            :error="err('startTime')"
            ><ElTimePicker v-model="a.startTime" value-format="HH:mm:ss"
          /></ElFormItem>
          <ElFormItem
            v-if="!['shift', 'slots', 'event'].includes(a.frequency)"
            :label="tr('daily.endTime')"
            ><ElTimePicker v-model="a.endTime" value-format="HH:mm:ss"
          /></ElFormItem>
          <ElFormItem
            v-if="['interval', 'event'].includes(a.frequency)"
            :label="tr('daily.windowMinutes')"
            :error="err('windowMinutes')"
            ><ElInputNumber
              v-model="a.windowMinutes"
              :min="1"
              :max="10080"
              :precision="0"
              controls-position="right"
          /></ElFormItem>
          <ElFormItem v-if="a.frequency === 'event'" :label="tr('daily.eventType')"
            ><ElSelect v-model="a.eventType"
              ><ElOption
                v-for="e in ['startup', 'shutdown', 'changeover', 'temporary']"
                :key="e"
                :value="e"
                :label="tr('daily.event.' + e)" /></ElSelect
          ></ElFormItem>
          <ElFormItem
            v-if="['shift', 'slots'].includes(a.frequency)"
            class="full"
            :label="tr('daily.slots')"
            :error="err('slots')"
            ><div class="slots">
              <div v-for="(s, n) in a.slots" :key="s.id" class="slot">
                <ElInput
                  v-model="s.name"
                  :placeholder="tr('daily.slotName')"
                  maxlength="100"
                /><ElTimePicker v-model="s.start" value-format="HH:mm:ss" /><span>—</span
                ><ElTimePicker v-model="s.end" value-format="HH:mm:ss" /><ElButton
                  type="danger"
                  link
                  @click="a.slots.splice(n, 1)"
                  >{{ tr('daily.remove') }}</ElButton
                >
              </div>
              <ElButton @click="addSlot">{{ tr('daily.addSlot') }}</ElButton>
            </div></ElFormItem
          >
          <h4 class="daily-group-title full">{{ tr('daily.section.people') }}</h4>
          <ElFormItem :label="tr('daily.personMode')" required :error="err('personMode')"
            ><ElSelect v-model="a.personMode"
              ><ElOption
                v-for="m in modes"
                :key="m"
                :value="m"
                :disabled="m === 'roster' && a.frequency !== 'shift'"
                :label="tr('daily.mode.' + m)" /></ElSelect
          ></ElFormItem>
          <ElFormItem :label="tr('daily.followUp')" required :error="err('followUpUserId')"
            ><PagedChoiceField
              v-model="a.followUpUserId"
              :options="choices.users"
              :load-rows="selector.users"
              :title="tr('daily.followUp')"
              @select="emit('people', $event)"
          /></ElFormItem>
          <ElFormItem
            v-if="a.personMode === 'specified'"
            :label="tr('daily.operators')"
            required
            :error="err('owners')"
            ><PagedChoiceField
              v-model="a.owners"
              multiple
              :options="choices.users"
              :load-rows="selector.users"
              :title="tr('daily.operators')"
              @select="emit('people', $event)"
          /></ElFormItem>
          <ElFormItem
            v-if="a.personMode === 'team'"
            class="full"
            :label="tr('daily.team')"
            required
            :error="err('teamId')"
            ><div class="daily-team-field">
              <ElSelect v-model="a.teamId" filterable
                ><ElOption v-for="t in teams" :key="t.id" :value="t.id" :label="t.name" /></ElSelect
              ><ElButton v-if="canCreateTeam" link type="primary" @click="newTeam">{{
                tr('daily.newTeam')
              }}</ElButton
              ><ElButton v-if="canEditTeam && a.teamId" link type="primary" @click="editTeam">{{
                tr('ui.equipment.text0207')
              }}</ElButton>
            </div></ElFormItem
          >
          <ElFormItem :label="tr('daily.allowLate')"><ElSwitch v-model="a.allowLate" /></ElFormItem>
          <p class="daily-help full">{{ tr('daily.peopleHelp') }}</p>
        </ElForm>
      </ElTabPane>
    </ElTabs>
    <AppDialog
      v-model="teamOpen"
      :title="editingTeam ? tr('daily.team') : tr('daily.newTeam')"
      :busy="busy"
      ><ElForm label-width="90px"
        ><ElFormItem :label="tr('daily.name')" required
          ><ElInput v-model="teamName" maxlength="150" /></ElFormItem
        ><ElFormItem :label="tr('daily.operators')" required
          ><PagedChoiceField
            v-model="teamMembers"
            multiple
            :options="choices.users"
            :load-rows="selector.users"
            :title="tr('daily.operators')"
            @select="emit('people', $event)" /></ElFormItem
        ><ElAlert v-if="teamError" :title="teamError" type="error" :closable="false" /></ElForm
      ><template #footer
        ><ElButton @click="teamOpen = false">{{ tr('daily.cancel') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="createTeam">{{
          tr('daily.save')
        }}</ElButton></template
      ></AppDialog
    >
    <AppDialog v-model="previewOpen" :title="tr('daily.preview')" width="900px"
      ><ElAlert v-if="previewError" :title="previewError" type="error" :closable="false" />
      <p v-if="previewBusy">{{ tr('daily.loading') }}</p>
      <template v-if="preview"
        ><p>
          {{ tr('daily.previewSummary', { total: preview.total, unstaffed: preview.unstaffed }) }}
        </p>
        <p v-if="preview.total > preview.limit">
          {{ tr('daily.previewLimit', { limit: preview.limit }) }}
        </p>
        <AppBusinessTable :rows="preview.rows" :pagination="false" :fill="false"
          ><ElTableColumn
            prop="arrangementName"
            :label="tr('daily.name')"
            min-width="130"
            show-overflow-tooltip /><ElTableColumn
            :label="tr('daily.equipment')"
            min-width="160"
            show-overflow-tooltip
            ><template #default="{ row }">{{
              equipment.find((e) => e.id === row.equipmentId)?.name || row.equipmentId
            }}</template></ElTableColumn
          ><ElTableColumn
            prop="businessDate"
            :label="tr('daily.businessDate')"
            width="120" /><ElTableColumn
            prop="start"
            :label="tr('daily.startTime')"
            min-width="180" /><ElTableColumn
            prop="end"
            :label="tr('daily.endTime')"
            min-width="180" /><ElTableColumn
            prop="candidateCount"
            :label="tr('daily.candidates')"
            width="100" /></AppBusinessTable></template
      ><template #footer
        ><ElButton @click="previewOpen = false">{{ tr('daily.close') }}</ElButton></template
      ></AppDialog
    >
  </section>
</template>
<style scoped>
.daily-help {
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.6;
  margin: 0 0 12px;
}
.daily-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  column-gap: 24px;
}
.daily-grid .full {
  grid-column: 1/-1;
}
.daily-grid :deep(.el-form-item) {
  margin-bottom: 12px;
}
.daily-grid :deep(.el-select),
.daily-grid :deep(.el-date-editor) {
  width: 100%;
  min-width: 0;
}
.daily-grid :deep(.el-form-item__content) {
  min-width: 0;
}
.arrangement-tabs :deep(.el-tabs__item) {
  max-width: 160px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.slots {
  width: 100%;
  display: grid;
  gap: 8px;
}
.slot {
  display: grid;
  grid-template-columns: minmax(90px, 1fr) minmax(110px, 1fr) auto minmax(110px, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.preview-period {
  width: 130px;
}
.daily-group-title {
  margin: 4px 0 14px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
  color: var(--el-text-color-primary);
  font-size: 13px;
  font-weight: 600;
}
.daily-group-title:first-child {
  margin-top: 0;
}
.daily-team-field {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}
.daily-team-field .el-select {
  flex: 1;
}
.daily-team-field .el-button {
  flex: none;
}
.daily-empty {
  margin: 0;
  padding: 24px 12px;
  text-align: center;
  color: var(--el-text-color-secondary);
}
.daily-grid :deep(.el-input-number) {
  width: 100%;
}
.arrangement-tabs :deep(.el-tabs__content) {
  min-width: 0;
}
.daily-grid .el-button {
  margin-left: 8px;
}
@media (max-width: 900px) {
  .daily-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
