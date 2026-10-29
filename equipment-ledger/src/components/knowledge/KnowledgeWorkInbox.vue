<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue';
import { ElAlert, ElButton, ElTableColumn } from 'element-plus';
import { useRouter } from 'vue-router';
import { workInbox, type InboxItem } from '../../api';
import { useWorkInboxStore } from '../../stores/workInbox';
import { tr } from '../../i18n';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
export type InboxResult = {
  data: InboxItem[];
  total: number;
  criteria: { module: string; kind: string; state: string; search: string };
};
const props = defineProps<{ result: InboxResult }>();
const emit = defineEmits<{ navigate: [] }>();
const store = useWorkInboxStore(),
  router = useRouter();
const data = ref(props.result),
  page = ref(1),
  size = ref(20),
  busy = ref(false),
  error = ref('');
let revision = 0;
onBeforeUnmount(() => revision++);
watch([page, size], async () => {
  const current = ++revision;
  busy.value = true;
  error.value = '';
  try {
    const result = await workInbox({
      channel: 'todo',
      ...props.result.criteria,
      page: page.value,
      size: size.value,
    });
    if (current === revision) data.value = { ...result, criteria: props.result.criteria };
  } catch (e) {
    if (current === revision) {
      data.value = { ...data.value, data: [], total: 0 };
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    }
  } finally {
    if (current === revision) busy.value = false;
  }
});
function open(row: InboxItem) {
  void router.push(store.destination(row));
  emit('navigate');
}
</script>
<template>
  <section class="assistant-inbox">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable
      :rows="data.data"
      :loading="busy"
      :fill="false"
      :pagination="false"
      :max-height="300"
    >
      <ElTableColumn :label="tr('ui.equipment.text0137')" min-width="160">
        <template #default="{ row }"
          ><ElButton link type="primary" @click="open(row as InboxItem)">{{
            row.orderNo || row.title
          }}</ElButton></template
        >
      </ElTableColumn>
      <ElTableColumn
        prop="kind"
        :label="tr('ui.equipment.text0142')"
        min-width="110"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="equipmentName"
        :label="tr('ui.equipment.text0081')"
        min-width="130"
        show-overflow-tooltip
      />
      <ElTableColumn
        prop="status"
        :label="tr('ui.equipment.text0170')"
        min-width="100"
        show-overflow-tooltip
      />
      <ElTableColumn prop="time" :label="tr('ui.equipment.text0174')" min-width="160" />
    </AppBusinessTable>
    <AppPagination v-model:page="page" v-model:page-size="size" :total="data.total" />
  </section>
</template>
<style scoped>
.assistant-inbox {
  margin-top: 8px;
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 8px;
  overflow-x: auto;
}
</style>
