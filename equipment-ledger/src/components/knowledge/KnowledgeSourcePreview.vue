<script setup lang="ts">
import { computed, ref, watch } from 'vue';
import { ElAlert } from 'element-plus';
import AppDialog from '../common/AppDialog.vue';
import { tr } from '../../i18n';
const props = defineProps<{
  source?: { documentId: string; versionId?: string; pageNo: number; title: string };
}>();
const emit = defineEmits<{ close: [] }>();
const failed = ref(false);
const base = computed(() =>
  props.source
    ? `/api/v1/knowledge/documents/${encodeURIComponent(props.source.documentId)}/versions/${encodeURIComponent(props.source.versionId || '')}`
    : '',
);
watch(
  () => props.source,
  () => (failed.value = false),
);
</script>
<template>
  <AppDialog
    :model-value="!!source"
    :title="source?.title || ''"
    width="960px"
    @update:model-value="emit('close')"
    ><ElAlert
      v-if="failed"
      :title="tr('xiaozhu.sourceUnavailable')"
      type="error"
      :closable="false"
    /><img
      v-if="source?.versionId && !failed"
      class="knowledge-source-page"
      :src="base + '/pages/' + source.pageNo + '/image'"
      :alt="tr('knowledge.page', { page: source.pageNo })"
      @error="failed = true"
    /><template #footer
      ><a
        v-if="source?.versionId"
        :href="base + '/original#page=' + source.pageNo"
        target="_blank"
        rel="noopener"
        >{{ tr('xiaozhu.original') }}</a
      ></template
    ></AppDialog
  >
</template>
<style scoped>
.knowledge-source-page {
  display: block;
  max-width: 100%;
  max-height: 70vh;
  object-fit: contain;
  margin: auto;
}
</style>
