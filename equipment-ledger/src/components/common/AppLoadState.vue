<script setup lang="ts">import { tr } from '../../i18n';

import AppIcon from './AppIcon.vue';
import { ElButton } from 'element-plus';
defineProps<{ loading: boolean; error?: string }>();
defineEmits<{ retry: [] }>();
</script>

<template>
  <div class="app-load-state" :aria-busy="loading" role="status" aria-live="polite">
    <AppIcon name="Loading" v-if="loading" class="loading-icon" />
    <AppIcon name="Warning" v-else />
    <strong>{{ loading ? tr('ui.equipment.text1341') : tr('ui.equipment.text1342') }}</strong>
    <p>{{ loading ? tr('ui.equipment.text1343') : error || tr('ui.equipment.text1344') }}</p>
    <ElButton v-if="!loading" type="primary" plain @click="$emit('retry')">{{ tr('ui.equipment.text0910') }}</ElButton>
  </div>
</template>

<style scoped>
.app-load-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 12px;
  min-height: 220px;
  padding: 24px;
  color: var(--text);
  font-size: 13px;
  text-align: center;
}
.app-load-state .app-icon {
  width: 32px;
  height: 32px;
}
.app-load-state strong {
  font-size: 13px;
  font-weight: 600;
}
.app-load-state p {
  margin: 0;
  max-width: 560px;
  overflow-wrap: anywhere;
  line-height: 1.6;
}
.loading-icon {
  stroke-dasharray: 42 16;
  animation: load-spin 1s linear infinite;
}
@keyframes load-spin {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .loading-icon {
    animation: none;
  }
}
</style>
