<script setup lang="ts">
import { ElTooltip, ElButton } from 'element-plus';
import { tr } from '../../i18n';
import AppIcon from './AppIcon.vue';
defineProps<{ content: string }>();
</script>
<template>
  <ElTooltip
    :content="content"
    placement="top"
    :show-after="150"
    :popper-style="{ maxWidth: '360px', lineHeight: '1.6' }"
  >
    <ElButton
      text
      class="app-help-tip"
      :aria-label="tr('ui.equipment.text0177')"
      :aria-description="content"
      @click.stop
    >
      <AppIcon name="Help" />
    </ElButton>
  </ElTooltip>
</template>
<style scoped>
.app-help-tip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
  width: 24px;
  height: 24px;
  padding: 0;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--el-text-color-secondary);
  cursor: help;
  font-size: 15px;
}
.app-help-tip:hover {
  color: var(--el-color-primary);
  background: var(--el-fill-color-light);
}
.app-help-tip:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: 2px;
}
</style>
