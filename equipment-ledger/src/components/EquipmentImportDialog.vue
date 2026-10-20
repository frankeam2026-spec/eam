<script setup lang="ts">import { tr } from '../i18n';

import { ref } from 'vue';
import AppDialog from './common/AppDialog.vue';
import ImportBatchPanel from './common/ImportBatchPanel.vue';
import { importApi, type ImportResultRow } from '../imports';
import { useAuthStore } from '../stores/auth';
defineProps<{ modelValue: boolean; batchId: string }>();
defineEmits<{
  'update:modelValue': [value: boolean];
  'batch-change': [id: string];
  'open-record': [row: ImportResultRow];
}>();
const auth = useAuthStore();
const api = importApi('/equipment/imports');
const busy = ref(false);
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="tr('ui.equipment.text0184')"
    width="1100px"
    :busy="busy"
    class="equipment-import-dialog"
    @update:model-value="$emit('update:modelValue', $event)"
  >
    <ImportBatchPanel
      v-if="modelValue"
      :api="api"
      :object-label="tr('ui.equipment.text0002')"
      presentation="dialog"
      :batch-id="batchId"
      :can-view-results="auth.can('equipment', '查看')"
      @busy-change="busy = $event"
      @batch-change="$emit('batch-change', $event)"
      @back="$emit('update:modelValue', false)"
      @open-record="$emit('open-record', $event)"
    />
  </AppDialog>
</template>
<style>
.equipment-import-dialog .el-dialog__body {
  overflow: hidden;
}
</style>
