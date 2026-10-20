<script setup lang="ts">import { tr } from '../i18n';

import AppIcon from './common/AppIcon.vue';
import { ElPopover, ElButton } from 'element-plus';
const props = withDefaults(
  defineProps<{
    mode?: 'all' | 'filter' | 'footer';
    filterOpen?: boolean;
    columnsOpen: boolean;
    canExport?: boolean;
    exportBusy?: boolean;
  }>(),
  { canExport: true, mode: 'all' },
);
defineEmits<{ toggleFilter: []; toggleColumns: []; export: [] }>();
</script>

<template>
  <div class="list-tools" :class="{ 'list-footer-tools': mode === 'footer' }">
    <ElButton
      v-if="mode !== 'footer'"
      class="list-icon-btn"
      :class="{ active: filterOpen }"
      native-type="button"
      :title="tr('ui.equipment.text0709')"
      :aria-label="tr('ui.equipment.text0709')"
      :aria-pressed="filterOpen"
      @click="$emit('toggleFilter')"
    >
      <AppIcon name="Filter" />
    </ElButton>
    <ElPopover
      v-if="mode !== 'filter'"
      :visible="columnsOpen"
      trigger="click"
      :placement="mode === 'footer' ? 'top-end' : 'bottom-end'"
      :width="190"
      popper-class="eam-columns-popover"
      :teleported="true"
      @update:visible="
        (value) => {
          if (value !== props.columnsOpen) $emit('toggleColumns');
        }
      "
    >
      <template #reference
        ><ElButton
          class="list-icon-btn"
          :class="{ active: columnsOpen }"
          native-type="button"
          :title="tr('ui.equipment.text0710')"
          :aria-label="tr('ui.equipment.text0710')"
          :aria-expanded="columnsOpen"
        >
          <AppIcon name="Columns" /></ElButton
      ></template>
      <div class="list-columns-menu"><slot name="columns" /></div>
    </ElPopover>
    <ElButton
      v-if="canExport && mode !== 'filter'"
      class="list-icon-btn"
      native-type="button"
      :title="tr('ui.equipment.text0711')"
      :aria-label="tr('ui.equipment.text0711')"
      :disabled="exportBusy"
      :aria-busy="exportBusy"
      @click="$emit('export')"
    >
      <AppIcon name="Download" />
    </ElButton>
  </div>
</template>

<style>
.list-tools .el-button + .el-button {
  margin-left: 0;
}
.list-tools {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  flex: none;
}
.list-icon-btn {
  width: 34px;
  height: 34px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0;
  border: 1px solid #dfe3e9;
  border-radius: 5px;
  background: #fff;
  color: #59677a;
  cursor: pointer;
}
.list-icon-btn:hover,
.list-icon-btn.active {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand);
}
.list-icon-btn .app-icon {
  width: 18px;
  height: 18px;
}
.list-columns-menu {
  max-height: min(420px, 70vh);
  overflow: auto;
  padding: 0;
  background: #fff;
}
.list-columns-menu label {
  display: flex;
  align-items: center;
  gap: 8px;
  min-height: 30px;
  white-space: nowrap;
  color: #4f5c70;
  font-size: 13px;
  cursor: pointer;
}
</style>

<style>
.list-footer-tools {
  margin-left: auto;
}
.list-footer-tools .list-icon-btn {
  width: 28px;
  height: 28px;
}
</style>
