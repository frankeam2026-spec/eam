<script setup lang="ts">
import { ref, watch, computed } from 'vue';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElRadioGroup,
  ElRadioButton,
  ElMessage,
} from 'element-plus';
import type { RepairAttachment } from '../../api';
import type { MaintenanceItem } from '../../maintenance';
import { inspectionUpload } from '../../inspection';
import AppDialog from './AppDialog.vue';
import MaintenanceAttachments from './MaintenanceAttachments.vue';
const props = withDefaults(
  defineProps<{
    modelValue: boolean;
    busy?: boolean;
    count?: number;
    items?: MaintenanceItem[];
    itemReview?: boolean;
  }>(),
  { busy: false, count: 1, itemReview: false, items: () => [] },
);
const emit = defineEmits<{
  'update:modelValue': [boolean];
  submit: [
    {
      decision: string;
      description: string;
      attachments: RepairAttachment[];
      reviewItems: { id: string; decision: string; description: string }[];
    },
  ];
}>();
const decision = ref('通过'),
  description = ref(''),
  attachments = ref<RepairAttachment[]>([]),
  reviews = ref<{ id: string; decision: string; description: string }[]>([]);
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      decision.value = '通过';
      description.value = '';
      attachments.value = [];
      reviews.value = props.items.map((i) => ({ id: i.id, decision: '通过', description: '' }));
    }
  },
);
const rejection = computed(
  () => decision.value === '打回' || reviews.value.some((r) => r.decision === '打回'),
);
function submit() {
  if (props.busy) return;
  if (rejection.value && !description.value.trim()) {
    ElMessage.warning('打回时请填写说明');
    return;
  }
  emit('submit', {
    decision: rejection.value ? '打回' : '通过',
    description: description.value,
    attachments: attachments.value,
    reviewItems: reviews.value,
  });
}
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="count > 1 ? '审核（' + count + ' 条任务）' : '审核检查结果'"
    :width="itemReview ? '900px' : '560px'"
    :busy="busy"
    @update:model-value="emit('update:modelValue', $event)"
    ><div class="inspection-review-form" :inert="busy">
      <label
        ><span>审核结果</span
        ><ElRadioGroup v-model="decision"
          ><ElRadioButton value="通过">通过</ElRadioButton
          ><ElRadioButton value="打回">打回</ElRadioButton></ElRadioGroup
        ></label
      >
      <div v-if="itemReview" class="inspection-picker-scroll">
        <table class="business-table inspection-picker-table">
          <thead>
            <tr>
              <th>项目名称</th>
              <th>结果</th>
              <th>说明</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="(r, index) in reviews" :key="r.id">
              <td>{{ items[index]?.name }}</td>
              <td>
                <ElSelect v-model="r.decision"
                  ><ElOption value="通过" /><ElOption value="打回"
                /></ElSelect>
              </td>
              <td><ElInput v-model="r.description" /></td>
            </tr>
          </tbody>
        </table>
      </div>
      <label
        ><span>说明</span
        ><ElInput
          v-model="description"
          type="textarea"
          :rows="4"
          :placeholder="rejection ? '请说明需要修改的内容' : '填写审核说明'" /></label
      ><label
        ><span>附件</span
        ><MaintenanceAttachments v-model="attachments" :upload-file="inspectionUpload"
      /></label>
    </div>
    <template #footer
      ><div class="inspection-dialog-actions">
        <ElButton :disabled="busy" @click="emit('update:modelValue', false)">取消</ElButton
        ><ElButton type="primary" :loading="busy" @click="submit">提交</ElButton>
      </div></template
    ></AppDialog
  >
</template>
<style scoped>
.inspection-review-form {
  display: grid;
  gap: 16px;
}
.inspection-review-form > label {
  display: flex;
  align-items: flex-start;
  gap: 16px;
}
.inspection-review-form > label > span {
  width: 68px;
  flex: none;
  padding-top: 6px;
}
.inspection-review-form :deep(.el-textarea) {
  flex: 1;
}
</style>
