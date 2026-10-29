<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElAlert, ElTableColumn } from 'element-plus';
import AppDialog from '../common/AppDialog.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
import { request } from '../../api';
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
const props = defineProps<{ document?: { id: string; title: string } }>();
const emit = defineEmits<{ close: [] }>();
type Version = {
  id: string;
  versionNo: number;
  parseStatus: string;
  parserVersion: string;
  errorMessage?: string;
  published: boolean;
  createdAt: string;
};
const rows = ref<Version[]>([]),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  error = ref(''),
  loading = ref(false);
let revision = 0;
async function load() {
  const current = ++revision;
  if (!props.document) {
    rows.value = [];
    return;
  }
  loading.value = true;
  error.value = '';
  try {
    const result = await request<{ data: Version[]; total: number }>(
      `/knowledge/documents/${props.document.id}/versions?page=${page.value}&size=${size.value}`,
    );
    if (current === revision) {
      rows.value = result.data;
      total.value = result.total;
    }
  } catch (e) {
    if (current === revision) {
      rows.value = [];
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(
  () => props.document?.id,
  () => {
    page.value = 1;
    void load();
  },
);
watch([page, size], () => void load());
</script>
<template>
  <AppDialog
    :model-value="!!document"
    :title="tr('knowledge.versions') + (document ? ' · ' + document.title : '')"
    width="900px"
    @update:model-value="emit('close')"
  >
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <AppBusinessTable :rows="rows" :loading="loading" :pagination="false" row-key="id">
      <ElTableColumn prop="versionNo" :label="tr('knowledge.versionNo')" width="80" />
      <ElTableColumn :label="tr('knowledge.parseStatus')" width="130"
        ><template #default="{ row }">{{
          tr('knowledge.state.' + row.parseStatus)
        }}</template></ElTableColumn
      >
      <ElTableColumn :label="tr('knowledge.currentPublished')" width="120"
        ><template #default="{ row }">{{
          row.published ? tr('knowledge.state.PUBLISHED') : '—'
        }}</template></ElTableColumn
      >
      <ElTableColumn
        prop="parserVersion"
        :label="tr('knowledge.parserVersion')"
        min-width="180"
        show-overflow-tooltip
      />
      <ElTableColumn :label="tr('knowledge.createdAt')" width="170"
        ><template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template></ElTableColumn
      >
      <ElTableColumn
        prop="errorMessage"
        :label="tr('knowledge.parseError')"
        min-width="200"
        show-overflow-tooltip
      />
    </AppBusinessTable>
    <template #footer
      ><AppPagination v-model:page="page" v-model:page-size="size" :total="total"
    /></template>
  </AppDialog>
</template>
