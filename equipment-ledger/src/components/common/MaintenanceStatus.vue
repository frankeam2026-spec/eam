<script setup lang="ts">import { enumLabel } from '../../i18n';

import { computed } from 'vue';
import { taskStatusStyle } from '../../utils/taskStatus';

const props = defineProps<{ status: string }>();
const style = computed(() => taskStatusStyle(props.status));
</script>

<template>
  <span v-if="status" class="maintenance-task-status" :style="style">{{ enumLabel('status',status) }}</span>
</template>

<style scoped>
.maintenance-task-status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  height: 26px;
  padding: 0 8px;
  border-radius: 4px;
  font-size: 13px;
  font-weight: 500;
  line-height: 1;
  white-space: nowrap;
  vertical-align: middle;
}
</style>
