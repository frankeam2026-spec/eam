<script setup lang="ts">
import { ref, watch } from 'vue';
import { ElButton, ElInput, ElRadioGroup, ElRadioButton, ElRate } from 'element-plus';
import type { TaskResult } from '../../maintenance';
import type { RepairAttachment } from '../../api';
import AppDialog from './AppDialog.vue';
import MaintenanceAttachments from './MaintenanceAttachments.vue';
const props = withDefaults(
  defineProps<{ modelValue: boolean; busy?: boolean; count?: number; caption?: string }>(),
  { busy: false, count: 1, caption: '' },
);
const emit = defineEmits<{
  'update:modelValue': [boolean];
  submit: [NonNullable<TaskResult['acceptance']>];
}>();
const decision = ref('通过'),
  score = ref(5),
  description = ref(''),
  attachments = ref<RepairAttachment[]>([]),
  error = ref('');
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      decision.value = '通过';
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
    error.value = '请选择1至5分的评分';
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
    title="保养验收"
    :busy="busy"
    width="540px"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <p v-if="caption || count > 1" class="acceptance-context">
      {{ caption || `本次验收 ${count} 个保养任务，所填验收结果将应用于所有选中任务。` }}
    </p>
    <div class="acceptance-form">
      <label
        ><span>验收结果</span
        ><ElRadioGroup v-model="decision"
          ><ElRadioButton value="通过">通过</ElRadioButton
          ><ElRadioButton value="打回">打回</ElRadioButton></ElRadioGroup
        ></label
      >
      <label
        ><span>评分</span>
        <div class="acceptance-rating">
          <ElRate v-model="score" /><small>{{ score }} 分</small>
        </div></label
      >
      <label
        ><span>说明</span
        ><ElInput v-model="description" type="textarea" :rows="3" placeholder="填写验收说明"
      /></label>
      <label><span>附件</span><MaintenanceAttachments v-model="attachments" /></label>
      <p v-if="error" class="acceptance-error" role="alert">{{ error }}</p>
    </div>
    <template #footer
      ><ElButton :disabled="busy" @click="emit('update:modelValue', false)">取消</ElButton
      ><ElButton type="primary" :loading="busy" @click="submit">提交</ElButton></template
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
  display: grid;
  gap: 16px;
  font-size: 13px;
}
.acceptance-form label {
  display: grid;
  grid-template-columns: 72px minmax(0, 1fr);
  align-items: start;
  gap: 12px;
}
.acceptance-form label > span {
  color: var(--text-secondary);
  padding-top: 7px;
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
.acceptance-error {
  margin: 0;
  color: #c9535e;
}
</style>
