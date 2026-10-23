<script setup lang="ts">
import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElInput, ElAlert, ElTableColumn } from 'element-plus';
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
import { inspectionApi, type InspectionTask } from '../../inspection';
import { planningSelectors, type SelectorRow } from '../../selectors';
import AppDialog from '../common/AppDialog.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import AppPagination from '../common/AppPagination.vue';
const open = defineModel<boolean>({ required: true });
const props = defineProps<{ equipmentId?: string }>();
const router = useRouter(),
  selector = planningSelectors('inspection');
const code = ref(''),
  equipment = ref(''),
  options = ref<SelectorRow[]>([]),
  rows = ref<InspectionTask[]>([]),
  total = ref(0),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  error = ref('');
let revision = 0;
watch(open, (value) => {
  revision++;
  busy.value = false;
  if (value) {
    equipment.value = props.equipmentId || '';
    code.value = '';
    rows.value = [];
    total.value = 0;
    error.value = '';
    page.value = 1;
    if (equipment.value) void load();
  }
});
watch([page, size], () => {
  if (open.value && (equipment.value || code.value.trim())) void load();
});
async function load() {
  if (!equipment.value && !code.value.trim()) {
    error.value = tr('staff.deviceRequired');
    return;
  }
  const version = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const result = await inspectionApi.deviceTasks(
      equipment.value,
      code.value.trim(),
      page.value,
      size.value,
    );
    if (version === revision) {
      rows.value = result.data;
      total.value = result.page.total;
    }
  } catch (e) {
    if (version === revision) {
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
      rows.value = [];
      total.value = 0;
    }
  } finally {
    if (version === revision) busy.value = false;
  }
}
async function start(row: InspectionTask) {
  if (busy.value) return;
  busy.value = true;
  error.value = '';
  try {
    await inspectionApi.action(row.id, 'start', { rowVersion: row.rowVersion });
    open.value = false;
    await router.push({ name: 'inspection-task', params: { id: row.id } });
  } catch (e) {
    const failure = e instanceof Error ? e.message : tr('common.error.requestFailed');
    await load();
    error.value = failure;
  } finally {
    busy.value = false;
  }
}
function queryCode() {
  equipment.value = '';
  page.value = 1;
  void load();
}
function select(rows: SelectorRow[]) {
  options.value = rows;
  equipment.value = rows[0]?.id || '';
  code.value = '';
  page.value = 1;
  void load();
}
</script>
<template>
  <AppDialog v-model="open" :title="tr('staff.deviceInspect')" width="1080px">
    <p>{{ tr('staff.deviceHelp') }}</p>
    <div class="device-inspection-tools">
      <ElInput
        v-model="code"
        :placeholder="tr('staff.scanCode')"
        clearable
        @keyup.enter="queryCode"
      /><ElButton :loading="busy" @click="queryCode">{{ tr('staff.findTasks') }}</ElButton>
      <PagedChoiceField
        v-model="equipment"
        :options="options"
        :load-rows="inspectionApi.deviceEquipmentOptions"
        :columns="[
          { key: 'code', label: tr('ui.equipment.text0078'), minWidth: 150 },
          { key: 'name', label: tr('ui.equipment.text0081'), minWidth: 220 },
        ]"
        :title="tr('daily.equipment')"
        :placeholder="tr('staff.chooseEquipment')"
        @select="select"
      />
    </div>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable :rows="rows" :loading="busy" :pagination="false" :fill="false">
      <ElTableColumn prop="equipmentName" :label="tr('daily.equipment')" min-width="150" />
      <ElTableColumn prop="arrangementName" :label="tr('daily.title')" min-width="150" />
      <ElTableColumn prop="shiftName" :label="tr('daily.slots')" min-width="120" />
      <ElTableColumn prop="dueAt" :label="tr('daily.startTime')" min-width="175"
        ><template #default="{ row }">{{
          formatDateTime(String(row.dueAt))
        }}</template></ElTableColumn
      >
      <ElTableColumn prop="status" :label="tr('staff.status')" width="100" />
      <ElTableColumn prop="ownerName" :label="tr('staff.actualOperator')" min-width="120" />
      <!-- @vue-generic {InspectionTask} -->
      <ElTableColumn :label="tr('ui.equipment.text0145')" width="170" fixed="right"
        ><template #default="{ row }">
          <ElButton
            v-if="
              row.canExecute && ['待执行', '已过期', '审核不通过', '未开始'].includes(row.status)
            "
            type="primary"
            link
            :disabled="busy"
            @click="start(row)"
            >{{ tr('staff.start') }}</ElButton
          >
          <ElButton
            v-else
            link
            @click="
              open = false;
              router.push({ name: 'inspection-task', params: { id: row.id } });
            "
            >{{
              tr(row.canExecute && row.status === '执行中' ? 'staff.continue' : 'staff.view')
            }}</ElButton
          >
        </template></ElTableColumn
      >
      <template #empty>{{ tr('staff.noTask') }}</template>
    </AppBusinessTable>
    <AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
  </AppDialog>
</template>
<style scoped>
.device-inspection-tools {
  display: grid;
  grid-template-columns: minmax(180px, 1fr) auto minmax(200px, 1fr);
  gap: 12px;
  margin: 12px 0;
}
@media (max-width: 700px) {
  .device-inspection-tools {
    grid-template-columns: 1fr;
  }
}
</style>
