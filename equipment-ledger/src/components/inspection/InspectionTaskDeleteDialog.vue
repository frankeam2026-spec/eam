<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElButton, ElInput, ElForm, ElFormItem, ElAlert, ElMessage } from 'element-plus';
import { tr } from '../../i18n';
import { inspectionApi, type InspectionTask } from '../../inspection';
import AppDialog from '../common/AppDialog.vue';
const open = defineModel<boolean>({ required: true });
const props = defineProps<{ task?: InspectionTask }>();
const emit = defineEmits<{ deleted: [id: string] }>();
const reason = ref(''),
  fieldError = ref(''),
  error = ref(''),
  busy = ref(false);
watch(open, (value) => {
  if (value) {
    reason.value = '';
    fieldError.value = '';
    error.value = '';
  }
});
async function remove() {
  const task = props.task;
  if (busy.value || !task) return;
  fieldError.value = reason.value.trim() ? '' : tr('daily.delete.reasonRequired');
  if (fieldError.value) return;
  busy.value = true;
  error.value = '';
  try {
    await inspectionApi.action(task.id, 'delete', {
      rowVersion: task.rowVersion,
      description: reason.value.trim(),
    });
    open.value = false;
    ElMessage.success(tr('daily.delete.success'));
    window.dispatchEvent(new Event('work-inbox-changed'));
    emit('deleted', task.id);
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <AppDialog v-model="open" :title="tr('daily.delete.title')" :busy="busy">
    <p>{{ task?.code }} · {{ task?.planName }} · {{ task?.status }}</p>
    <p>{{ tr('daily.delete.help') }}</p>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <ElForm label-position="top" @submit.prevent="remove">
      <ElFormItem :label="tr('daily.delete.reason')" required :error="fieldError">
        <ElInput
          v-model="reason"
          type="textarea"
          :rows="3"
          maxlength="1000"
          show-word-limit
          :disabled="busy"
        />
      </ElFormItem>
    </ElForm>
    <template #footer>
      <ElButton :disabled="busy" @click="open = false">{{ tr('daily.cancel') }}</ElButton>
      <ElButton type="danger" :loading="busy" @click="remove">{{
        tr('ui.equipment.text0146')
      }}</ElButton>
    </template>
  </AppDialog>
</template>
