<script setup lang="ts">
import { ElButton } from 'element-plus';
import { tr } from '../../i18n';
import { formatDateTime } from '../../utils/dateTime';
import type { InspectionTask, InspectionChoices } from '../../inspection';
defineProps<{ task: InspectionTask; users: InspectionChoices['users'] }>();
defineEmits<{ plan: [] }>();
</script>
<template>
  <div v-if="task.kind === 'daily'" class="inspection-task-summary inspection-task-device-summary">
    <div>
      <span>{{ tr('ui.equipment.text0081') }}</span
      ><strong>{{ task.equipment[0]?.name || task.equipmentName || '—' }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0078') }}</span
      ><strong>{{ task.equipment[0]?.code || task.equipmentCode || '—' }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0091') }}</span
      ><strong>{{ task.equipment[0]?.model || '—' }}</strong>
    </div>
  </div>
  <div class="inspection-task-summary">
    <div>
      <span>{{ tr('ui.equipment.text0314') }}</span
      ><ElButton v-if="task.planId" link type="primary" @click="$emit('plan')">{{
        task.planName
      }}</ElButton
      ><strong v-else>{{ task.planName }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0235') }}</span
      ><strong>{{ formatDateTime(task.dueAt) }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0426') }}</span
      ><strong>{{ formatDateTime(task.expiresAt) }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0102') }}</span
      ><strong>{{
        task.owners.map((id) => users.find((u) => u.id === id)?.name || id).join('、') ||
        tr('ui.equipment.text0427')
      }}</strong>
    </div>
    <div v-if="task.kind !== 'daily'">
      <span>{{ tr('ui.equipment.text0325') }}</span
      ><strong>{{ task.typeName || tr('ui.equipment.text0428') }}</strong>
    </div>
    <div v-else>
      <span>{{ tr('ui.equipment.text0365') }}</span
      ><strong>{{ task.shiftDate }} · {{ task.shiftName }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0429') }}</span
      ><strong>{{ task.reviewMode }}</strong>
    </div>
  </div>
  <div class="inspection-task-summary inspection-task-execution-summary">
    <div>
      <span>{{ tr('inspection.ui.inspector') }}</span
      ><strong>{{ task.ownerName || '—' }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0431') }}</span
      ><strong>{{ formatDateTime(task.result.startedAt) }}</strong>
    </div>
    <div>
      <span>{{ tr('ui.equipment.text0432') }}</span
      ><strong>{{ formatDateTime(task.result.finishedAt) }}</strong>
    </div>
  </div>
</template>
<style scoped>
.inspection-task-device-summary {
  margin-bottom: 16px;
  padding-bottom: 16px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.inspection-task-execution-summary {
  margin-top: 12px;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
@media (max-width: 900px) {
  .inspection-task-execution-summary {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
