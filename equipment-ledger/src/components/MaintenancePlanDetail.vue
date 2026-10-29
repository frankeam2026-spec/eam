<script setup lang="ts">import { tr } from '../i18n';

import { ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElDescriptions, ElDescriptionsItem, ElTableColumn, ElTag } from 'element-plus';
import { maintenanceApi, type MaintenanceDefinition, type Choice } from '../maintenance';
import { resolvePlanning, type SelectorRow } from '../selectors';
import AppBackButton from './common/AppBackButton.vue';
import AppLoadState from './common/AppLoadState.vue';
import AppBusinessTable from './common/AppBusinessTable.vue';
import BusinessFields from './business/BusinessFields.vue';

const route = useRoute(),
  router = useRouter();
const plan = ref<MaintenanceDefinition>();
const equipment = ref<SelectorRow[]>([]),
  people = ref<SelectorRow[]>([]);
const organizations = ref<Choice[]>([]);
const loading = ref(false),
  error = ref('');
let revision = 0;
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  plan.value = undefined;
  try {
    const { data } = await maintenanceApi.get('plans', String(route.params.id));
    const [selected, choices] = await Promise.all([
      resolvePlanning<MaintenanceDefinition>('maintenance', data),
      maintenanceApi.choices(true),
    ]);
    if (current !== revision) return;
    plan.value = data;
    equipment.value = selected.equipment;
    people.value = selected.users;
    organizations.value = choices.organizations;
  } catch (e) {
    if (current === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0861');
  } finally {
    if (current === revision) loading.value = false;
  }
}
function personNames(ids: string[]) {
  return ids
    .map((id) => people.value.find((p) => p.id === id)?.name || '')
    .filter(Boolean)
    .join('、');
}
function back() {
  if (window.history.state.back) router.back();
  else router.replace('/maintenance/tasks');
}
watch(() => route.params.id, load, { immediate: true });
</script>

<template>
  <section class="maintenance-plan-detail">
    <header class="plan-heading">
      <AppBackButton @click="back" />
      <h1>{{ plan?.name || tr('ui.equipment.text0010') }}</h1>
      <span>{{ plan?.code }}</span>
      <ElTag v-if="plan" :type="plan.enabled ? 'success' : 'info'">{{
        plan.enabled ? tr('ui.equipment.text0216') : tr('ui.equipment.text0260')
      }}</ElTag>
    </header>
    <AppLoadState v-if="loading || error" :loading="loading" :error="error" @retry="load" />
    <div v-else-if="plan" class="plan-content">
      <section class="plan-panel">
        <h2>{{ tr('ui.equipment.text0077') }}</h2>
        <ElDescriptions :column="2" :border="false">
          <ElDescriptionsItem :label="tr('ui.equipment.text0098')">{{
            organizations.find((o) => o.id === plan?.departmentId)?.name
          }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0862')">{{ plan.calculation }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0328')">{{ plan.advanceDays }} {{ tr('ui.equipment.text0806') }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0807')">{{ plan.remindDays }} {{ tr('ui.equipment.text0806') }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0863')">{{
            plan.reviewMode === '无需验收' ? tr('ui.equipment.text0068') : tr('ui.equipment.text0067')
          }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0809')">{{
            plan.scanStart ? tr('ui.equipment.text0067') : tr('ui.equipment.text0068')
          }}</ElDescriptionsItem>
          <ElDescriptionsItem :label="tr('ui.equipment.text0130')" :span="2">{{ plan.remark }}</ElDescriptionsItem>
        </ElDescriptions>
      </section>
      <section class="plan-panel1">
        <h2>{{ tr('ui.equipment.text0368') }}</h2>
        <AppBusinessTable :rows="plan.details" border-mode="header">
          <ElTableColumn :label="tr('ui.equipment.text0864')" min-width="160"
            ><template #default="{ row }">{{
              equipment.find((e) => e.id === row.equipmentId)?.code
            }}</template></ElTableColumn
          >
          <ElTableColumn :label="tr('ui.equipment.text0081')" min-width="180"
            ><template #default="{ row }">{{
              equipment.find((e) => e.id === row.equipmentId)?.name
            }}</template></ElTableColumn
          >
          <ElTableColumn :label="tr('ui.equipment.text0818')" min-width="110"
            ><template #default="{ row }">{{ row.period }}{{ row.unit }}</template></ElTableColumn
          >
          <ElTableColumn prop="nextDate" :label="tr('ui.equipment.text0821')" min-width="150" />
          <ElTableColumn :label="tr('ui.equipment.text0329')" min-width="130"
            ><template #default="{ row }"
              >{{ row.validDuration ?? row.validDays }}
              {{ row.validUnit === '日' || !row.validUnit ? tr('ui.equipment.text0806') : row.validUnit }}</template
            ></ElTableColumn
          >
          <ElTableColumn :label="tr('ui.equipment.text0102')" min-width="140"
            ><template #default="{ row }">{{
              personNames(row.owners || [])
            }}</template></ElTableColumn
          >
          <ElTableColumn v-if="plan.reviewMode !== '无需验收'" :label="tr('ui.equipment.text0813')" min-width="140"
            ><template #default="{ row }">{{
              personNames(row.reviewers?.length ? row.reviewers : plan.reviewers)
            }}</template></ElTableColumn
          >
          <ElTableColumn :label="tr('ui.equipment.text0865')" min-width="110"
            ><template #default="{ row }">{{
              row.projectRefs?.length || 0
            }}</template></ElTableColumn
          >
        </AppBusinessTable>
      </section>
      <BusinessFields v-model="plan" object="maintenance_plan" readonly />
    </div>
  </section>
</template>

<style scoped>
.maintenance-plan-detail {
  height: 100%;
  display: flex;
  flex-direction: column;
  min-height: 0;
  font-size: 13px;
  color: var(--text);
}
.plan-heading {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px 16px;
  background: #fff;
  flex: none;
}
.plan-heading h1 {
  margin: 0;
  font-size: 18px;
}
.plan-content {
  flex: 1;
  min-height: 0;
  padding: 16px;
  overflow: auto;
  display: grid;
  gap: 12px;
}
.plan-panel {
  min-width: 0;
  background: #fff;
  padding: 16px;
  border-radius: 6px;
}
.plan-panel h2 {
  margin: 0 0 16px;
  font-size: 14px;
}
</style>
