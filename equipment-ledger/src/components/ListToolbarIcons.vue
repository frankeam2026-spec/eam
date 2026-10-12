<script setup lang="ts">
import { ElPopover } from 'element-plus';
const props = withDefaults(
  defineProps<{
    mode?: 'all' | 'filter' | 'footer';
    filterOpen: boolean;
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
    <button
      v-if="mode !== 'footer'"
      class="list-icon-btn"
      :class="{ active: filterOpen }"
      type="button"
      title="列筛选"
      aria-label="列筛选"
      :aria-pressed="filterOpen"
      @click="$emit('toggleFilter')"
    >
      <svg viewBox="0 0 20 20" aria-hidden="true">
        <path d="M3 4h14l-5.5 6.2v4.3l-3 1.5v-5.8L3 4Z" />
      </svg>
    </button>
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
        ><button
          class="list-icon-btn"
          :class="{ active: columnsOpen }"
          type="button"
          title="设置列"
          aria-label="设置列"
          :aria-expanded="columnsOpen"
        >
          <svg viewBox="0 0 20 20" aria-hidden="true">
            <rect x="2.5" y="3" width="15" height="14" rx="1.5" />
            <path d="M7.5 3v14M12.5 3v14" />
          </svg></button
      ></template>
      <div class="list-columns-menu"><slot name="columns" /></div>
    </ElPopover>
    <button
      v-if="canExport && mode !== 'filter'"
      class="list-icon-btn"
      type="button"
      title="导出"
      aria-label="导出"
      :disabled="exportBusy"
      :aria-busy="exportBusy"
      @click="$emit('export')"
    >
      <svg viewBox="0 0 20 20" aria-hidden="true">
        <path d="M10 2.5v9m-3.5-3.5L10 11.5l3.5-3.5M3.5 13v4h13v-4" />
      </svg>
    </button>
  </div>
</template>

<style>
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
.list-icon-btn svg {
  width: 18px;
  height: 18px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.list-columns-anchor {
  position: relative;
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
.list-columns-menu input[type='checkbox'] {
  accent-color: var(--brand);
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
