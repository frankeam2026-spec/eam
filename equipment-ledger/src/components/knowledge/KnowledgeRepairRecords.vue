<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElButton, ElTableColumn, ElAlert } from 'element-plus';
import { useRouter } from 'vue-router';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import MaintenanceStatus from '../common/MaintenanceStatus.vue';
import AppPagination from '../common/AppPagination.vue';
import { request } from '../../api';
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
import type { RepairRecords } from './repairRecords';
const props = defineProps<{ result: RepairRecords }>();
const emit = defineEmits<{ navigate: [] }>();
function openRecord(id: string) {
  void router.push({ name: 'repair-detail', params: { id } });
  emit('navigate');
}
const router = useRouter(),
  data = ref(props.result),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  error = ref('');
let revision = 0;
watch(
  () => props.result,
  (value) => {
    revision++;
    data.value = value;
    page.value = 1;
  },
);
watch([page, size], async () => {
  const current = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const c = props.result.criteria;
    const query = new URLSearchParams({
      from: c.from,
      to: c.to,
      relation: c.relation,
      completed: String(c.completed),
      page: String(page.value),
      size: String(size.value),
    });
    if (c.equipmentId) query.set('equipmentId', c.equipmentId);
    const response = await request<RepairRecords>('/knowledge/assistant/repair-records?' + query);
    if (current === revision) data.value = response;
  } catch (e) {
    if (current === revision) {
      data.value = { ...props.result, data: [], total: 0 };
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    }
  } finally {
    if (current === revision) busy.value = false;
  }
});
</script>
<template>
  <section class="assistant-repairs">
    <p>
      {{ result.criteria.from }} ~ {{ result.criteria.to }} ·
      {{
        tr(
          result.dateField === 'completedAt'
            ? 'xiaozhu.repairs.completedAt'
            : 'xiaozhu.repairs.reportedAt',
        )
      }}
    </p>
    <small
      >{{ tr('xiaozhu.repairs.scope') }} ·
      {{ tr('xiaozhu.repairs.total', { count: data.total }) }} ·
      {{ tr('xiaozhu.repairs.sort') }}</small
    >
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable
      :rows="data.data"
      :loading="busy"
      :fill="false"
      :pagination="false"
      :max-height="300"
      row-key="id"
    >
      <ElTableColumn :label="tr('xiaozhu.repairs.orderNo')" min-width="155"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="openRecord(row.id)">{{
            row.orderNo
          }}</ElButton></template
        ></ElTableColumn
      >
      <ElTableColumn
        prop="equipmentName"
        :label="tr('xiaozhu.equipmentName')"
        min-width="130"
        show-overflow-tooltip
      />
      <ElTableColumn :label="tr('xiaozhu.repairs.status')" width="100"
        ><template #default="{ row }"><MaintenanceStatus :status="row.status" /></template
      ></ElTableColumn>
      <ElTableColumn
        :label="
          tr(
            result.dateField === 'completedAt'
              ? 'xiaozhu.repairs.completedAt'
              : 'xiaozhu.repairs.reportedAt',
          )
        "
        width="170"
        ><template #default="{ row }">{{
          formatDateTime(row[result.dateField] || '')
        }}</template></ElTableColumn
      >
    </AppBusinessTable>
    <div class="assistant-repairs-pagination">
      <AppPagination v-model:page="page" v-model:page-size="size" :total="data.total" />
    </div>
  </section>
</template>
<style scoped>
.assistant-repairs {
  min-width: 0;
  max-width: 100%;
  margin-top: 8px;
}
.assistant-repairs > p {
  font-size: 12px;
  line-height: 1.6;
}
.assistant-repairs > small {
  display: block;
  margin-bottom: 8px;
  color: var(--text-secondary);
}
.assistant-repairs-pagination {
  overflow-x: auto;
  padding-top: 8px;
}
</style>
