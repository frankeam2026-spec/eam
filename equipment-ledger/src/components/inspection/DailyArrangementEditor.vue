<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue';
import {
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTimePicker,
  ElCollapse,
  ElCollapseItem,
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
  type DailyArrangement,
} from '../../inspection';
import DailyRosterEditor from './DailyRosterEditor.vue';
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
const editorOpen = ref(false);
const expanded = ref<string[]>([]);
const timingExpanded = ref<string[]>([]);
function edit(index: number) {
  active.value = String(index);
  expanded.value = [];
  timingExpanded.value = [];
  editorOpen.value = true;
}
function timeSummary(a: DailyArrangement) {
  return (
    a.slots
      .map(
        (s) =>
          `${s.name || tr('daily.slotName')} ${(s.start || '').slice(0, 5)}–${(s.end || '').slice(0, 5)}${s.end <= s.start ? tr('daily.ui.nextDay') : ''}`,
      )
      .join('；') || tr('daily.shiftOnly.noShifts')
  );
}
function scopeSummary(a: DailyArrangement) {
  return tr('daily.ui.scopeSummary', {
    equipment: a.equipmentIds.length || form.value.details.length,
    projects: a.projectRefs.length
      ? tr('daily.ui.projectCount', { count: a.projectRefs.length })
      : tr('daily.allProjects'),
  });
}
function peopleSummary(a: DailyArrangement) {
  const defaults = defaultPeopleSummary(a);
  const count = form.value.details.filter(
    (d) => d.owners.length && (!a.equipmentIds.length || a.equipmentIds.includes(d.equipmentId)),
  ).length;
  return count ? tr('daily.ui.peopleOverrides', { people: defaults, count }) : defaults;
}
function defaultPeopleSummary(a: DailyArrangement) {
  if (a.personMode === 'open') return tr('staff.open');
  if (a.personMode === 'roster') return tr('daily.mode.roster');
  if (a.personMode === 'team')
    return teams.value.find((t) => t.id === a.teamId)?.name || tr('daily.ui.chooseTeam');
  return (
    a.owners.map((id) => props.choices.users.find((u) => u.id === id)?.name || id).join('、') ||
    tr('daily.ui.choosePeople')
  );
}
const hasOverrides = computed(() => form.value.details.some((d) => d.owners.length));
const path = (key: string) => `arrangements.${active.value}.${key}`;
const err = (key: string) => props.errors[path(key)] || '';
const modes = ['open', 'specified', 'team', 'roster'] as const;
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
    if (match && form.value.arrangements[Number(match[1])]) {
      active.value = match[1]!;
      editorOpen.value = true;
      expanded.value = ['scope'];
      timingExpanded.value = ['timing'];
    }
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
  edit(form.value.arrangements.length - 1);
}
function remove() {
  form.value.arrangements.splice(Number(active.value), 1);
  active.value = String(Math.max(0, Number(active.value) - 1));
  editorOpen.value = false;
}
function addSlot() {
  if (current.value) current.value.frequency = 'shift';
  current.value?.slots.push({
    id: crypto.randomUUID().replaceAll('-', ''),
    name: '',
    start: '08:00:00',
    end: '16:00:00',
  });
}
function removeSlot(index: number) {
  const a = current.value;
  const slot = a?.slots[index];
  if (!a || !slot) return;
  a.slots.splice(index, 1);
  a.regularRoster = (a.regularRoster || []).filter((row) => row.slotId !== slot.id);
  a.dateRoster = (a.dateRoster || []).filter((row) => row.slotId !== slot.id);
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
  <section class="daily-arrangements">
    <div class="inspection-form-card arrangement-list">
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
      <p class="daily-help">{{ tr('daily.ui.intro') }}</p>
      <AppBusinessTable :rows="form.arrangements" :pagination="false" :fill="false">
        <ElTableColumn :label="tr('daily.name')" min-width="160">
          <template #default="{ row }"
            >{{ row.name || tr('daily.unnamed') }} ·
            {{ tr(row.enabled ? 'daily.enabled' : 'daily.ui.disabled') }}</template
          >
        </ElTableColumn>
        <!-- @vue-generic {DailyArrangement} -->
        <ElTableColumn :label="tr('daily.ui.what')" min-width="210"
          ><template #default="{ row }">{{ scopeSummary(row) }}</template></ElTableColumn
        >
        <!-- @vue-generic {DailyArrangement} -->
        <ElTableColumn :label="tr('daily.ui.when')" min-width="300"
          ><template #default="{ row }">{{ timeSummary(row) }}</template></ElTableColumn
        >
        <!-- @vue-generic {DailyArrangement} -->
        <ElTableColumn :label="tr('daily.ui.who')" min-width="170"
          ><template #default="{ row }">{{ peopleSummary(row) }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('ui.equipment.text0145')" width="100" fixed="right"
          ><template #default="{ $index }"
            ><ElButton link type="primary" @click="edit($index)">{{
              tr('ui.equipment.text0207')
            }}</ElButton></template
          ></ElTableColumn
        >
        <template #empty>{{ tr('daily.ui.empty') }}</template>
      </AppBusinessTable>
    </div>
    <div v-if="editorOpen && current" class="inspection-form-card arrangement-editor">
      <div class="inspection-section-tools arrangement-editor-heading">
        <h3>{{ tr('ui.equipment.text0207') }}：{{ current.name || tr('daily.unnamed') }}</h3>
        <ElButton @click="editorOpen = false">{{ tr('daily.ui.collapse') }}</ElButton>
      </div>
      <p class="arrangement-summary" aria-live="polite">
        {{ scopeSummary(current) }} · {{ timeSummary(current) }} · {{ peopleSummary(current) }}
      </p>
      <p class="daily-help">
        {{ tr('daily.ui.planStartSummary', { date: form.startDate }) }} ·
        {{ tr(current.allowLate ? 'daily.allowLate' : 'daily.ui.noLate') }}
      </p>
      <template v-for="a in [current]" :key="active">
        <ElForm label-width="110px" class="daily-grid" @submit.prevent>
          <ElFormItem :label="tr('daily.name')" required :error="err('name')"
            ><ElInput v-model="a.name" maxlength="150"
          /></ElFormItem>
          <ElFormItem :label="tr('daily.enabled')"
            ><ElSwitch v-model="a.enabled" /><ElButton type="danger" link @click="remove">{{
              tr('daily.remove')
            }}</ElButton></ElFormItem
          >
          <ElCollapse v-model="expanded" class="full"
            ><ElCollapseItem name="scope" :title="tr('daily.ui.scopeSettings')">
              <p class="daily-help">{{ scopeSummary(a) }} · {{ tr('daily.ui.scopeHelp') }}</p>
              <div class="daily-grid">
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
                /></ElFormItem></div></ElCollapseItem
          ></ElCollapse>
          <h4 class="daily-group-title full">{{ tr('daily.ui.when') }}</h4>
          <p class="daily-help full">{{ tr('daily.shiftOnly.help') }}</p>
          <ElAlert
            v-if="!['shift', 'slots'].includes(a.frequency)"
            class="full"
            :title="tr('daily.shiftOnly.legacy')"
            type="warning"
            :closable="false"
          />
          <ElFormItem
            class="full"
            :label="tr('daily.slots')"
            :error="err('slots') || err('frequency')"
            ><div class="slots">
              <p v-if="!a.slots.length" class="daily-help">{{ tr('daily.shiftOnly.noShifts') }}</p>
              <div v-for="(s, n) in a.slots" :key="s.id" class="slot">
                <ElInput
                  v-model="s.name"
                  :placeholder="tr('daily.slotName')"
                  maxlength="100"
                /><ElTimePicker v-model="s.start" value-format="HH:mm:ss" /><span>—</span
                ><ElTimePicker v-model="s.end" value-format="HH:mm:ss" /><ElButton
                  type="danger"
                  link
                  @click="removeSlot(n)"
                  >{{ tr('daily.remove') }}</ElButton
                >
              </div>
              <ElButton @click="addSlot">{{ tr('daily.addSlot') }}</ElButton>
            </div></ElFormItem
          >
          <h4 class="daily-group-title full">{{ tr('daily.ui.who') }}</h4>
          <ElFormItem :label="tr('daily.personMode')" required :error="err('personMode')"
            ><ElSelect v-model="a.personMode"
              ><ElOption
                v-for="m in modes"
                :key="m"
                :value="m"
                :label="tr('daily.mode.' + m)" /></ElSelect
          ></ElFormItem>
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
          <ElFormItem
            :label="tr('daily.followUp')"
            :required="a.personMode !== 'open'"
            :error="err('followUpUserId')"
            ><PagedChoiceField
              v-model="a.followUpUserId"
              :options="choices.users"
              :load-rows="selector.users"
              :title="tr('daily.followUp')"
              @select="emit('people', $event)"
          /></ElFormItem>
          <p v-if="a.personMode === 'open'" class="daily-help full">{{ tr('staff.openHelp') }}</p>
          <DailyRosterEditor
            v-if="
              a.personMode === 'roster' &&
              ['shift', 'slots'].includes(a.frequency) &&
              a.slots.length
            "
            v-model="form.arrangements[Number(active)]!"
            class="full"
            :choices="choices"
            :start-date="form.startDate"
            :end-date="form.endDate"
            :errors="
              Object.fromEntries(
                Object.entries(errors).filter(([key]) =>
                  key.startsWith('arrangements.' + active + '.'),
                ),
              )
            "
            @people="emit('people', $event)"
          />
          <p class="daily-help full">{{ tr('daily.ui.followUpHelp') }}</p>
          <ElAlert
            v-if="hasOverrides"
            class="full"
            :title="tr('daily.ui.overrideHelp')"
            type="warning"
            :closable="false"
          />
          <ElCollapse v-model="timingExpanded" class="full"
            ><ElCollapseItem name="timing" :title="tr('daily.ui.moreSettings')"
              ><div class="daily-grid">
                <ElFormItem :label="tr('daily.allowLate')"
                  ><ElSwitch v-model="a.allowLate"
                /></ElFormItem>
              </div>
              <p class="daily-help">{{ tr('daily.ui.shiftOvernightHelp') }}</p></ElCollapseItem
            ></ElCollapse
          >
        </ElForm>
      </template>
      <p class="daily-help">{{ tr('daily.ui.saveHint') }}</p>
    </div>
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
  grid-template-columns: repeat(3, minmax(0, 1fr));
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
.daily-arrangements {
  display: grid;
  gap: 24px;
  min-width: 0;
}
.arrangement-list,
.arrangement-editor {
  min-width: 0;
  border: 1px solid var(--el-border-color);
  border-radius: 6px;
  background: var(--el-bg-color);
}
.arrangement-editor-heading {
  margin: -16px -16px 16px;
  padding: 12px 16px;
  border-bottom: 1px solid var(--el-border-color);
  border-radius: 6px 6px 0 0;
  background: var(--el-fill-color-light);
}
.arrangement-editor-heading h3,
.arrangement-list .inspection-section-tools h3 {
  margin: 0 auto 0 0;
}
.arrangement-summary {
  margin: 0 0 8px;
  padding: 12px;
  line-height: 1.8;
  background: var(--el-fill-color-light);
  color: var(--el-text-color-primary);
  overflow-wrap: anywhere;
}
.daily-grid > .el-collapse {
  margin-bottom: 16px;
}
.slots {
  width: 100%;
  display: grid;
  gap: 8px;
}
.slot {
  display: grid;
  max-width: 880px;
  grid-template-columns: minmax(90px, 1fr) minmax(110px, 1fr) auto minmax(110px, 1fr) auto;
  gap: 8px;
  align-items: center;
}
.slots > .el-button {
  justify-self: start;
  margin-left: 0;
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
.daily-grid .el-button {
  margin-left: 8px;
}
@media (max-width: 1400px) {
  .daily-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .daily-grid {
    grid-template-columns: minmax(0, 1fr);
  }
  .slot {
    grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  }
  .slot > .el-input,
  .slot > .el-button {
    grid-column: 1 / -1;
  }
  .slot > .el-button {
    justify-self: start;
  }
  .slot :deep(.el-date-editor) {
    min-width: 0;
    width: 100%;
  }
}
</style>
