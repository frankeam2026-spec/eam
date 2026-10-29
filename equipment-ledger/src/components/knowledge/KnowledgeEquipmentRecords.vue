<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElAlert, ElButton, ElTableColumn } from 'element-plus';
import { useRouter } from 'vue-router';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
import { request } from '../../api';
import { tr } from '../../i18n';
import type { EquipmentRecords } from './equipmentRecords';
const props = defineProps<{ result: EquipmentRecords }>();
const emit = defineEmits<{ navigate: [] }>();
const router = useRouter();
const data = ref(props.result),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  error = ref('');
let revision = 0;
onBeforeUnmount(() => revision++);
watch(
  () => props.result,
  (value) => {
    revision++;
    data.value = value;
    page.value = 1;
    busy.value = false;
    error.value = '';
  },
);
watch([page, size], async () => {
  const current = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const query = new URLSearchParams({
      filters: JSON.stringify(props.result.filters),
      page: String(page.value),
      size: String(size.value),
    });
    if (props.result.equipmentId) query.set('equipmentId', props.result.equipmentId);
    const result = await request<EquipmentRecords>(
      '/knowledge/assistant/equipment-records?' + query,
    );
    if (current === revision) data.value = result;
  } catch (e) {
    if (current === revision) {
      data.value = { ...props.result, data: [], total: 0 };
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    }
  } finally {
    if (current === revision) busy.value = false;
  }
});
function openRecord(id: string) {
  void router.push({ name: 'equipment-history', params: { id } });
  emit('navigate');
}
</script>
<template>
  <section class="assistant-equipment">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable
      :rows="data.data"
      :loading="busy"
      :fill="false"
      :pagination="false"
      :max-height="300"
      row-key="id"
    >
      <ElTableColumn :label="tr('output.equipment.field.code')" min-width="140"
        ><template #default="{ row }"
          ><ElButton link type="primary" @click="openRecord(row.id)">{{
            row.code
          }}</ElButton></template
        ></ElTableColumn
      >
      <ElTableColumn
        prop="name"
        :label="tr('output.equipment.field.name')"
        min-width="130"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="model"
        :label="tr('output.equipment.field.model')"
        min-width="120"
        show-overflow-tooltip
      />
      <ElTableColumn prop="state" :label="tr('output.equipment.field.state')" min-width="100" />
      <ElTableColumn
        prop="useState"
        :label="tr('output.equipment.field.useState')"
        min-width="100"
      />
    </AppBusinessTable>
    <div class="assistant-equipment-pagination">
      <AppPagination v-model:page="page" v-model:page-size="size" :total="data.total" />
    </div>
  </section>
</template>
<style scoped>
.assistant-equipment {
  min-width: 0;
  max-width: 100%;
  margin-top: 8px;
}
.assistant-equipment-pagination {
  overflow-x: auto;
  padding-top: 8px;
}
</style>
