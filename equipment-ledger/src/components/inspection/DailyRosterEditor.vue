<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import {
  ElButton,
  ElRadioGroup,
  ElRadioButton,
  ElDatePicker,
  ElSelect,
  ElOption,
  ElCheckbox,
  ElCheckboxGroup,
  ElForm,
  ElFormItem,
  ElTableColumn,
  ElAlert,
} from 'element-plus';
import { tr } from '../../i18n';
import type {
  DailyArrangement,
  InspectionChoices,
  RegularStaff,
  DateStaff,
} from '../../inspection';
import {
  addDays,
  dateString,
  viewDates,
  staffColumns,
  dateEntry,
  hasDateOverride,
  regularEntry,
  effectiveEntry,
  putDate,
  staffKey,
  copyPreviousWeek,
} from '../../utils/dailyStaffing';
import { planningSelectors, type SelectorRow } from '../../selectors';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppHelpTip from '../common/AppHelpTip.vue';
import AppDialog from '../common/AppDialog.vue';
const a = defineModel<DailyArrangement>({ required: true });
const props = defineProps<{
  choices: InspectionChoices;
  startDate: string;
  endDate: string;
  errors: Record<string, string>;
}>();
const emit = defineEmits<{ people: [rows: SelectorRow[]] }>();
const selector = planningSelectors('inspection');
const section = ref('regular'),
  mode = ref<'week' | 'month'>('week'),
  day = ref(props.startDate || dateString(new Date()));
const dates = computed(() =>
  viewDates(day.value || props.startDate, mode.value).filter(
    (d) => d >= props.startDate && d <= props.endDate,
  ),
);
const columns = computed(() => staffColumns(a.value));
const selectedDates = ref<string[]>([]);
const tableRows = computed(() => dates.value.map((date) => ({ date })));
const history = ref<DateStaff[]>();
const batchOpen = ref(false),
  range = ref<string[]>([]),
  weekdays = ref([1, 2, 3, 4, 5, 6, 7]),
  overwrite = ref(false),
  message = ref('');
const batchPeople = ref<Record<string, string[]>>({}),
  batchNone = ref<Record<string, boolean>>({});
const errorRows = computed(() =>
  Object.entries(props.errors).filter(
    ([key]) => key.includes('regularRoster') || key.includes('dateRoster'),
  ),
);
watch(
  () => props.errors,
  (errors) => {
    if (Object.keys(errors).some((key) => key.includes('dateRoster'))) section.value = 'dates';
  },
  { deep: true },
);
const weekday = (date: string) =>
  tr('daily.weekday.' + (new Date(date + 'T00:00:00').getDay() || 7));
const names = (users: string[]) =>
  users.map((id) => props.choices.users.find((u) => u.id === id)?.name || id).join('、');
function description(date: string, slot: string) {
  const rule = effectiveEntry(a.value, date, slot);
  if (!rule) return tr('staff.missing');
  if (rule.unassigned) return tr('staff.unassigned');
  return names(rule.users) || tr('staff.missing');
}
function initializeRegular() {
  a.value.regularRoster ??= [];
  for (const c of columns.value)
    if (!a.value.regularRoster.some((r) => r.slotId === c.id && r.weekday === 0))
      a.value.regularRoster.push({
        key: staffKey(),
        weekday: 0,
        slotId: c.id,
        start: '00:00:00',
        end: '00:00:00',
        users: [],
        unassigned: false,
      });
}
function addRule() {
  a.value.regularRoster ??= [];
  a.value.regularRoster.push({
    key: staffKey(),
    weekday: 0,
    slotId: columns.value[0]?.id || '',
    start: '00:00:00',
    end: '00:00:00',
    users: [],
    unassigned: false,
  });
}
function snapshot() {
  history.value = structuredClone(a.value.dateRoster.map((r) => ({ ...r, users: [...r.users] })));
}
function restore(date: string, slot: string) {
  snapshot();
  a.value.dateRoster = a.value.dateRoster.filter(
    (r) => !(r.date === date && r.slotId === slot && r.start === r.end),
  );
}
function editCell(date: string, slot: string, users: string[]) {
  snapshot();
  if (users.length) putDate(a.value, date, slot, users);
  else a.value.dateRoster = a.value.dateRoster.filter((r) => r !== dateEntry(a.value, date, slot));
}
function noStaff(date: string, slot: string) {
  snapshot();
  putDate(a.value, date, slot, [], true);
}
function openBatch() {
  range.value = [dates.value[0] || props.startDate, dates.value.at(-1) || props.endDate];
  message.value = '';
  batchPeople.value = Object.fromEntries(columns.value.map((c) => [c.id, []]));
  batchNone.value = Object.fromEntries(columns.value.map((c) => [c.id, false]));
  batchOpen.value = true;
}
const chosenDates = computed(() => {
  if (range.value?.length !== 2 || !range.value[0] || !range.value[1]) return [];
  const result: string[] = [];
  for (
    let date = range.value[0], i = 0;
    date <= range.value[1] && i < 367;
    date = addDays(date, 1), i++
  )
    if (
      date >= props.startDate &&
      date <= props.endDate &&
      weekdays.value.includes(new Date(date + 'T00:00:00').getDay() || 7) &&
      (!selectedDates.value.length || selectedDates.value.includes(date))
    )
      result.push(date);
  return result;
});
const batchCount = computed(() =>
  chosenDates.value.reduce(
    (n, date) =>
      n +
      columns.value.filter((c) => overwrite.value || !hasDateOverride(a.value, date, c.id)).length,
    0,
  ),
);
function applyBatch() {
  if (
    range.value.length === 2 &&
    Number(new Date(range.value[1] + 'T00:00:00')) -
      Number(new Date(range.value[0] + 'T00:00:00')) >
      366 * 86400000
  ) {
    message.value = tr('staff.limit');
    return;
  }
  if (!chosenDates.value.length || !batchCount.value) {
    message.value = tr('staff.noDates');
    return;
  }
  if (columns.value.some((c) => !batchNone.value[c.id] && !batchPeople.value[c.id]?.length)) {
    message.value = tr('staff.choosePeople');
    return;
  }
  if (a.value.dateRoster.length + batchCount.value > 3000) {
    message.value = tr('staff.limit');
    return;
  }
  snapshot();
  for (const date of chosenDates.value)
    for (const c of columns.value)
      if (overwrite.value || !hasDateOverride(a.value, date, c.id))
        putDate(
          a.value,
          date,
          c.id,
          batchNone.value[c.id] ? [] : batchPeople.value[c.id] || [],
          !!batchNone.value[c.id],
        );
  batchOpen.value = false;
}
function copyWeek() {
  const updates = copyPreviousWeek(
    a.value,
    dates.value.filter((d) => addDays(d, -7) >= props.startDate),
    false,
  );
  if (!updates.length) {
    message.value = tr('staff.noCopy');
    return;
  }
  if (a.value.dateRoster.length + updates.length > 3000) {
    message.value = tr('staff.limit');
    return;
  }
  snapshot();
  for (const row of updates) putDate(a.value, row.date, row.slotId, row.users, row.unassigned);
}
function move(amount: number) {
  if (mode.value === 'week') day.value = addDays(day.value, amount * 7);
  else {
    const date = new Date(day.value + 'T00:00:00');
    date.setDate(1);
    date.setMonth(date.getMonth() + amount);
    day.value = dateString(date);
  }
  selectedDates.value = [];
}
function undo() {
  if (history.value) {
    a.value.dateRoster = history.value;
    history.value = undefined;
  }
}
</script>
<template>
  <section class="content daily-staffing">
    <ElAlert
      v-for="[key, error] in errorRows"
      :key="key"
      :title="error"
      type="error"
      :closable="false"
    />
    <div class="staff-section-heading">
      <ElRadioGroup v-model="section"
        ><ElRadioButton value="regular">{{ tr('staff.regular') }}</ElRadioButton
        ><ElRadioButton value="dates">{{ tr('staff.dates') }}</ElRadioButton></ElRadioGroup
      ><AppHelpTip :content="tr('staff.help')" />
    </div>
    <template v-if="section === 'regular'">
      <div class="staff-tools">
        <ElButton type="primary" @click="initializeRegular">{{ tr('staff.useShifts') }}</ElButton>
        <ElButton @click="addRule">{{ tr('staff.addRule') }}</ElButton
        ><AppHelpTip :content="tr('staff.ruleHelp')" />
      </div>
      <AppBusinessTable
        :rows="a.regularRoster || []"
        row-key="key"
        :pagination="false"
        :fill="false"
      >
        <!-- @vue-generic {RegularStaff} -->
        <ElTableColumn :label="tr('staff.weekday')" width="140"
          ><template #default="{ row }"
            ><ElSelect v-model="row.weekday"
              ><ElOption :value="0" :label="tr('staff.everyDay')" /><ElOption
                v-for="d in 7"
                :key="d"
                :value="d"
                :label="tr('daily.weekday.' + d)" /></ElSelect></template
        ></ElTableColumn>
        <!-- @vue-generic {RegularStaff} -->
        <ElTableColumn :label="tr('daily.slots')" min-width="220"
          ><template #default="{ row }"
            ><ElSelect v-model="row.slotId"
              ><ElOption
                v-for="c in columns"
                :key="c.id"
                :value="c.id"
                :label="`${c.name} ${c.start.slice(0, 5)}–${c.end.slice(0, 5)}${c.end <= c.start ? tr('daily.ui.nextDay') : ''}`" /></ElSelect></template
        ></ElTableColumn>
        <!-- @vue-generic {RegularStaff} -->
        <ElTableColumn :label="tr('daily.operators')" min-width="230"
          ><template #default="{ row }"
            ><PagedChoiceField
              v-model="row.users"
              multiple
              :options="choices.users"
              :load-rows="selector.users"
              :disabled="row.unassigned"
              :title="tr('daily.operators')"
              @select="emit('people', $event)" /></template
        ></ElTableColumn>
        <!-- @vue-generic {RegularStaff} -->
        <ElTableColumn :label="tr('staff.unassigned')" width="125"
          ><template #default="{ row }"
            ><ElCheckbox v-model="row.unassigned" @change="row.unassigned && (row.users = [])">{{
              tr('staff.unassigned')
            }}</ElCheckbox></template
          ></ElTableColumn
        >
        <!-- @vue-generic {RegularStaff} -->
        <ElTableColumn :label="tr('ui.equipment.text0145')" width="156" fixed="right"
          ><template #default="{ row }"
            ><ElButton
              type="danger"
              link
              @click="a.regularRoster = a.regularRoster.filter((r) => r.key !== row.key)"
              >{{ tr('daily.remove') }}</ElButton
            ></template
          ></ElTableColumn
        >
      </AppBusinessTable>
    </template>
    <template v-else>
      <div class="staff-tools">
        <ElRadioGroup v-model="mode" @change="selectedDates = []"
          ><ElRadioButton value="week">{{ tr('staff.week') }}</ElRadioButton
          ><ElRadioButton value="month">{{ tr('staff.month') }}</ElRadioButton></ElRadioGroup
        >
        <ElButton @click="move(-1)">{{ tr('staff.previous') }}</ElButton
        ><ElDatePicker
          v-model="day"
          type="date"
          value-format="YYYY-MM-DD"
          :clearable="false"
          @change="selectedDates = []"
        /><ElButton @click="move(1)">{{ tr('staff.next') }}</ElButton>
        <ElButton type="primary" @click="openBatch">{{ tr('staff.batch') }}</ElButton
        ><ElButton v-if="mode === 'week'" @click="copyWeek">{{ tr('staff.copyWeek') }}</ElButton
        ><ElButton :disabled="!history" @click="undo">{{ tr('staff.undo') }}</ElButton>
        <AppHelpTip :content="tr('staff.dateHelp')" />
      </div>
      <ElAlert v-if="message" :title="message" type="warning" :closable="false" />
      <AppBusinessTable
        v-model:selection-ids="selectedDates"
        :rows="tableRows"
        row-key="date"
        selectable
        :pagination="false"
        :fill="false"
      >
        <ElTableColumn prop="date" :label="tr('daily.businessDate')" width="180" fixed="left"
          ><template #default="{ row }"
            >{{ row.date }} {{ weekday(String(row.date)) }}</template
          ></ElTableColumn
        >
        <ElTableColumn
          v-for="c in columns"
          :key="c.id"
          :label="c.name || tr('staff.wholeDay')"
          min-width="230"
          ><template #default="{ row }">
            <div
              class="staff-cell"
              :class="{ 'staff-missing': !effectiveEntry(a, String(row.date), c.id)?.users.length }"
            >
              <span>{{ description(String(row.date), c.id) }}</span>
              <PagedChoiceField
                :model-value="dateEntry(a, String(row.date), c.id)?.users || []"
                multiple
                :options="choices.users"
                :load-rows="selector.users"
                :title="tr('staff.adjustPeople')"
                :placeholder="tr('staff.inherit')"
                @update:model-value="editCell(String(row.date), c.id, $event as string[])"
                @select="emit('people', $event)"
              />
              <div>
                <ElButton link @click="noStaff(String(row.date), c.id)">{{
                  tr('staff.unassigned')
                }}</ElButton
                ><ElButton
                  v-if="dateEntry(a, String(row.date), c.id)"
                  link
                  @click="restore(String(row.date), c.id)"
                  >{{ tr('staff.restore') }}</ElButton
                >
              </div>
            </div>
          </template></ElTableColumn
        >
      </AppBusinessTable>
    </template>
    <AppDialog v-model="batchOpen" :title="tr('staff.batch')" width="800px">
      <ElForm label-width="110px"
        ><ElFormItem :label="tr('staff.range')"
          ><ElDatePicker
            v-model="range"
            type="daterange"
            value-format="YYYY-MM-DD"
            :disabled-date="
              (date: Date) => dateString(date) < startDate || dateString(date) > endDate
            "
        /></ElFormItem>
        <ElFormItem :label="tr('staff.weekday')"
          ><ElCheckboxGroup v-model="weekdays"
            ><ElCheckbox v-for="d in 7" :key="d" :value="d">{{
              tr('daily.weekday.' + d)
            }}</ElCheckbox></ElCheckboxGroup
          ></ElFormItem
        >
        <ElFormItem v-for="c in columns" :key="c.id" :label="c.name || tr('staff.wholeDay')"
          ><PagedChoiceField
            v-model="batchPeople[c.id]"
            multiple
            :options="choices.users"
            :load-rows="selector.users"
            :disabled="!!batchNone[c.id]"
            :title="tr('daily.operators')"
            @select="emit('people', $event)"
          /><ElCheckbox v-model="batchNone[c.id]">{{
            tr('staff.unassigned')
          }}</ElCheckbox></ElFormItem
        >
        <ElFormItem
          ><ElCheckbox v-model="overwrite">{{ tr('staff.overwrite') }}</ElCheckbox></ElFormItem
        >
      </ElForm>
      <p>
        {{
          tr('staff.batchSummary', {
            dates: chosenDates.length,
            count: batchCount,
            selected: selectedDates.length,
          })
        }}
      </p>
      <ElAlert v-if="message" :title="message" type="warning" :closable="false" />
      <template #footer
        ><ElButton @click="batchOpen = false">{{ tr('daily.cancel') }}</ElButton
        ><ElButton type="primary" @click="applyBatch">{{ tr('staff.apply') }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.daily-staffing {
  margin: 12px 0;
  min-width: 0;
}
.staff-section-heading {
  display: flex;
  align-items: center;
  gap: 8px;
}
.staff-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 0;
}
.staff-tools :deep(.el-date-editor) {
  max-width: 180px;
}
.staff-cell {
  display: grid;
  gap: 6px;
  padding: 4px 0;
}
.staff-missing > span {
  color: var(--el-color-warning-dark-2);
}
.daily-staffing :deep(.el-select) {
  width: 100%;
}
</style>
