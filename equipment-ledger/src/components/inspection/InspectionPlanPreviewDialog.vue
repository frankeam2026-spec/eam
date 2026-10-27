<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElTableColumn } from 'element-plus';
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
import { inspectionApi, type InspectionDefinition, type DailyArrangement } from '../../inspection';
import { useAuthStore } from '../../stores/auth';
import AppDialog from '../common/AppDialog.vue';
import AppLoadState from '../common/AppLoadState.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
const open = defineModel<boolean>({ required: true });
const props = defineProps<{ planId: string; kind: 'daily-plans' | 'periodic-plans' }>();
const plan = ref<InspectionDefinition>(),
  loading = ref(false),
  error = ref('');
const router = useRouter(),
  auth = useAuthStore();
let revision = 0;
async function load() {
  const current = ++revision;
  if (!open.value || !props.planId) return;
  plan.value = undefined;
  error.value = '';
  loading.value = true;
  try {
    const result = await inspectionApi.get(props.kind, props.planId);
    if (current === revision) plan.value = result.data;
  } catch (e) {
    if (current === revision)
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(() => [open.value, props.planId, props.kind], load);
function edit() {
  open.value = false;
  router.push({ name: 'inspection-edit', params: { kind: props.kind, id: props.planId } });
}
function shifts(a: DailyArrangement) {
  return (
    a.slots
      .map(
        (s) =>
          `${s.name} ${s.start.slice(0, 5)}–${s.end.slice(0, 5)}${s.end <= s.start ? tr('daily.ui.nextDay') : ''}`,
      )
      .join('；') || tr('daily.shiftOnly.noShifts')
  );
}
</script>
<template>
  <AppDialog v-model="open" :title="tr('inspection.ui.planDetails')" width="1000px">
    <AppLoadState v-if="!plan" :loading="loading" :error="error" @retry="load" />
    <template v-else>
      <div class="inspection-task-summary">
        <div>
          <span>{{ tr('ui.equipment.text0314') }}</span
          ><strong>{{ plan.name }}</strong>
        </div>
        <div>
          <span>{{ tr('inspection.ui.planCode') }}</span
          ><strong>{{ plan.code }}</strong>
        </div>
        <div>
          <span>{{ tr('daily.enabled') }}</span
          ><strong>{{ tr(plan.enabled ? 'daily.enabled' : 'daily.ui.disabled') }}</strong>
        </div>
        <div>
          <span>{{ tr('daily.ui.planDates') }}</span
          ><strong>{{ formatDateTime(plan.startDate) }} — {{ formatDateTime(plan.endDate) }}</strong>
        </div>
      </div>
      <p v-if="plan.remark">{{ plan.remark }}</p>
      <AppBusinessTable
        v-if="kind === 'daily-plans'"
        :rows="plan.arrangements"
        :pagination="false"
        :fill="false"
      >
        <ElTableColumn prop="name" :label="tr('daily.name')" min-width="140" />
        <!-- @vue-generic {DailyArrangement} -->
        <ElTableColumn :label="tr('daily.slots')" min-width="300"
          ><template #default="{ row }">{{ shifts(row) }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('daily.ui.who')" min-width="160"
          ><template #default="{ row }">{{
            tr('daily.mode.' + row.personMode)
          }}</template></ElTableColumn
        >
      </AppBusinessTable>
      <template v-if="kind === 'daily-plans'">
        <p>
          {{ tr('daily.shiftOnly.weeklyRest') }}：{{
            plan.weeklyRestDays.map((d) => tr('daily.weekday.' + d)).join('、') || '—'
          }}
        </p>
        <p>{{ tr('daily.shiftOnly.monthlyRest') }}：{{ plan.monthlyRestDays.join('、') || '—' }}</p>
      </template>
    </template>
    <template #footer>
      <ElButton @click="open = false">{{ tr('daily.close') }}</ElButton>
      <ElButton
        v-if="
          plan &&
          auth.can(
            kind === 'daily-plans' ? 'inspectionDailyPlan' : 'inspectionPeriodicPlan',
            '编辑',
          )
        "
        type="primary"
        @click="edit"
        >{{ tr('ui.equipment.text0207') }}</ElButton
      >
    </template>
  </AppDialog>
</template>
