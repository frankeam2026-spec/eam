<script setup lang="ts">import { tr } from '../../i18n';

import { ElTableColumn } from 'element-plus';
import AppBusinessTable from './AppBusinessTable.vue';
import { ref, watch, computed } from 'vue';
import { ElButton, ElInput, ElSelect, ElOption, ElMessage } from 'element-plus';
import type { RepairAttachment } from '../../api';
import type { MaintenanceItem } from '../../maintenance';
import { inspectionUpload } from '../../inspection';
import AppDialog from './AppDialog.vue';
import AcceptanceResultPicker from './AcceptanceResultPicker.vue';
import { AcceptanceDecision, acceptanceOptions } from '../../config/acceptance';
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
      decision: AcceptanceDecision;
      description: string;
      attachments: RepairAttachment[];
      reviewItems: { id: string; decision: AcceptanceDecision; description: string }[];
    },
  ];
}>();
const decision = ref<AcceptanceDecision>(AcceptanceDecision.ACCEPT),
  description = ref(''),
  attachments = ref<RepairAttachment[]>([]),
  reviews = ref<{ id: string; decision: AcceptanceDecision; description: string }[]>([]);
watch(
  () => props.modelValue,
  (open) => {
    if (open) {
      decision.value = AcceptanceDecision.ACCEPT;
      description.value = '';
      attachments.value = [];
      reviews.value = props.items.map((i) => ({
        id: i.id,
        decision: AcceptanceDecision.ACCEPT,
        description: '',
      }));
    }
  },
);
const rejection = computed(
  () =>
    decision.value === AcceptanceDecision.REJECT ||
    reviews.value.some((r) => r.decision === AcceptanceDecision.REJECT),
);
function submit() {
  if (props.busy) return;
  if (rejection.value && !description.value.trim()) {
    ElMessage.warning(tr('ui.equipment.text1476'));
    return;
  }
  emit('submit', {
    decision: rejection.value ? AcceptanceDecision.REJECT : AcceptanceDecision.ACCEPT,
    description: description.value,
    attachments: attachments.value,
    reviewItems: reviews.value,
  });
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => reviews.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <AppDialog
    :model-value="modelValue"
    :title="count > 1 ? tr('ui.equipment.text1477') + count + tr('ui.equipment.text1478') : tr('ui.equipment.text1479')"
    :width="itemReview ? '900px' : '560px'"
    :busy="busy"
    @update:model-value="emit('update:modelValue', $event)"
    ><div class="inspection-review-form" :inert="busy">
      <label
        ><span>{{ tr('ui.equipment.text0453') }}</span
        ><AcceptanceResultPicker v-model="decision" :disabled="busy" :aria-label="tr('ui.equipment.text0453')"
      /></label>
      <div v-if="itemReview" class="inspection-picker-scroll">
        <AppBusinessTable
          :rows="reviews"
          :pagination="false"
          :fill="false"
          :framed="false"
          class="business-table inspection-picker-table"
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn :min-width="210"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0398') }}</div></template
            ><template #default="{ row: r, $index: index }">{{
              items[index]?.name
            }}</template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0454') }}</div></template
            ><template #default="{ row: r, $index: index }">
              <ElSelect v-model="r.decision"
                ><ElOption
                  v-for="option in acceptanceOptions"
                  :key="option.value"
                  :value="option.value"
                  :label="option.label"
              /></ElSelect> </template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn :min-width="150"
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0177') }}</div></template
            ><template #default="{ row: r, $index: index }"
              ><ElInput v-model="r.description" /></template></ElTableColumn
        ></AppBusinessTable>
      </div>
      <label
        ><span>{{ tr('ui.equipment.text0177') }}</span
        ><ElInput
          v-model="description"
          type="textarea"
          :rows="4"
          :placeholder="rejection ? tr('ui.equipment.text1480') : tr('ui.equipment.text1481')" /></label
      ><label
        ><span>{{ tr('ui.equipment.text0222') }}</span
        ><MaintenanceAttachments v-model="attachments" :upload-file="inspectionUpload"
      /></label>
    </div>
    <template #footer
      ><div class="inspection-dialog-actions">
        <ElButton :disabled="busy" @click="emit('update:modelValue', false)">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="submit">{{ tr('ui.equipment.text0253') }}</ElButton>
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
