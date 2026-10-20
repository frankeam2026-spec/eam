<script setup lang="ts">
import { tr } from '../../i18n';

import { ElButton } from 'element-plus';
import AppIcon from './AppIcon.vue';
withDefaults(defineProps<{ label?: string; disabled?: boolean }>(), {
  label: '',
  disabled: false,
});
</script>
<template>
  <ElButton
    class="app-back-button"
    native-type="button"
    :aria-label="label || tr('ui.equipment.text0208')"
    :title="label || tr('ui.equipment.text0208')"
    :disabled="disabled"
  >
    <span aria-hidden="true"><AppIcon name="Left" /></span>
  </ElButton>
</template>
<style scoped>
.app-back-button {
  width: 32px;
  height: 32px;
  padding: 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: 0 0 32px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: #263246;
  font-size: 24px;
  line-height: 1;
  cursor: pointer;
}
.app-back-button:hover {
  background: #edf2fa;
  color: var(--brand, #205cf5);
}
.app-back-button:focus-visible {
  outline: 2px solid var(--brand, #205cf5);
  outline-offset: 2px;
}
.app-back-button:disabled {
  opacity: 0.45;
  cursor: not-allowed;
}
</style>
