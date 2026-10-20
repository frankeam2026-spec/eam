<script setup lang="ts">import { tr } from '../../i18n';

import { ref, watch } from 'vue';
import { ElButton, ElInput, ElRate, ElForm } from 'element-plus';
import type { TaskResult } from '../../maintenance';
import type { RepairAttachment } from '../../api';
import AppDialog from './AppDialog.vue';
import AppFormField from './AppFormField.vue';
import AcceptanceResultPicker from './AcceptanceResultPicker.vue';
import { AcceptanceDecision } from '../../config/acceptance';
import MaintenanceAttachments from './MaintenanceAttachments.vue';
const props = withDefaults(
  defineProps<{ modelValue: boolean; busy?: boolean; count?: number; caption?: string }>(),
  { busy: false, count: 1, caption: '' },
);
const emit = defineEmits<{
  'update:modelValue': [boolean];
  submit: [NonNullable<TaskResult['acceptance']>];
}>();
const decision = ref<AcceptanceDecision>(AcceptanceDecision.ACCEPT),
  score = ref(),
  description = ref(''),
  attachments = ref<RepairAttachment[]>([]),
  error = ref('');
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      decision.value = AcceptanceDecision.ACCEPT;
      score.value = 5;
      description.value = '';
      attachments.value = [];
      error.value = '';
    }
  },
);
function submit() {
  if (props.busy) return;
  if (!Number.isInteger(score.value) || score.value < 1 || score.value > 5) {
    error.value = tr('ui.equipment.text1483');
    return;
  }
  error.value = '';
  emit('submit', {
    decision: decision.value,
    score: score.value,
    description: description.value,
    attachments: attachments.value,
  });
}
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="tr('ui.equipment.text1484')"
    :busy="busy"
    width="540px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p v-if="caption || count > 1" class="acceptance-context">
      {{ caption || `本次验收 ${count} 个保养任务，所填验收结果将应用于所有选中任务。` }}
    </p>
    <ElForm class="acceptance-form" label-width="96px" :disabled="busy" @submit.prevent="submit">
      <AppFormField :label="tr('ui.equipment.text1485')" prop="decision" required>
        <AcceptanceResultPicker v-model="decision" :disabled="busy" />
      </AppFormField>
      <AppFormField :label="tr('ui.equipment.text1128')" prop="score" required :error="error">
        <div class="acceptance-rating">
          <ElRate v-model="score" @change="error = ''" /><small>{{ score }} {{ tr('ui.equipment.text0439') }}</small>
        </div>
      </AppFormField>
      <AppFormField :label="tr('ui.equipment.text0177')" prop="description">
        <ElInput v-model="description" type="textarea" :rows="3" :placeholder="tr('ui.equipment.text1129')" />
      </AppFormField>
      <AppFormField :label="tr('ui.equipment.text0222')" prop="attachments">
        <MaintenanceAttachments v-model="attachments" />
      </AppFormField>
    </ElForm>
    <template #footer
      ><ElButton :disabled="busy" @click="emit('update:modelValue', false)">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton type="primary" :loading="busy" @click="submit">{{ tr('ui.equipment.text0253') }}</ElButton></template
    >
  </AppDialog>
</template>
<style scoped>
.acceptance-context {
  margin: 0 0 18px;
  padding: 10px 12px;
  border-radius: 4px;
  background: var(--brand-soft);
  color: var(--text-secondary);
  font-size: 13px;
  line-height: 1.6;
}
.acceptance-form {
  font-size: 13px;
}
.acceptance-rating {
  display: flex;
  align-items: center;
  gap: 10px;
}
.acceptance-rating small {
  color: var(--text-secondary);
  font-size: 13px;
}
</style>
