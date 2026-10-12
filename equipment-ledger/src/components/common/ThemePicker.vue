<script setup lang="ts">
import { ref } from 'vue';
import { ElPopover } from 'element-plus';
import type { Theme } from '../../stores/ui';
const palette = defineModel<Theme>({ required: true });
const visible = ref(false);
const themes: { value: Theme; name: string; color: string }[] = [
  { value: 'blue', name: '蓝色', color: '#205cf5' },
  { value: 'navy', name: '深蓝', color: '#003e96' },
  { value: 'pink', name: '粉色', color: '#ff8ba3' },
];
function choose(value: Theme) {
  palette.value = value;
  visible.value = false;
}
</script>
<template>
  <ElPopover v-model:visible="visible" trigger="click" placement="bottom-end" :width="208">
    <template #reference>
      <button
        class="theme-trigger"
        aria-label="主题"
        title="主题"
        :aria-expanded="visible"
        aria-haspopup="dialog"
      >
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M9 3 5 5 2 9l4 3 2-2v11h8V10l2 2 4-3-3-4-4-2c0 3-6 3-6 0Z" />
        </svg>
        <svg class="theme-chevron" viewBox="0 0 20 20" aria-hidden="true">
          <path d="m6 8 4 4 4-4" />
        </svg>
      </button>
    </template>
    <div class="theme-options" role="group" aria-label="选择主题">
      <strong>主题</strong>
      <button
        v-for="theme in themes"
        :key="theme.value"
        :aria-pressed="palette === theme.value"
        :class="{ selected: palette === theme.value }"
        @click="choose(theme.value)"
      >
        <span class="theme-swatch" :style="{ background: theme.color }"></span>
        <span>{{ theme.name }}</span>
        <svg
          v-if="palette === theme.value"
          class="theme-check"
          viewBox="0 0 20 20"
          aria-hidden="true"
        >
          <path d="m4 10 4 4 8-8" />
        </svg>
      </button>
    </div>
  </ElPopover>
</template>
<style scoped>
.theme-trigger {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  height: 34px;
  border: 0;
  border-radius: 4px;
  padding: 0 8px;
  background: transparent;
  color: inherit;
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.theme-trigger:hover,
.theme-trigger[aria-expanded='true'] {
  background: #ffffff20;
}
.theme-trigger svg {
  width: 20px;
  height: 20px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.6;
  stroke-linejoin: round;
  stroke-linecap: round;
}
.theme-trigger .theme-chevron {
  width: 12px;
  height: 12px;
}
.theme-options {
  color: var(--text);
  font-size: 13px;
}
.theme-options strong {
  display: block;
  padding: 4px 8px 10px;
  font-weight: 600;
}
.theme-options button {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  height: 38px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--text);
  font: inherit;
  cursor: pointer;
}
.theme-options button:hover {
  background: #f3f6fb;
}
.theme-options button.selected {
  background: var(--brand-soft);
  color: var(--brand);
}
.theme-swatch {
  width: 20px;
  height: 20px;
  border-radius: 4px;
  flex-shrink: 0;
  box-shadow: inset 0 0 0 1px #00000010;
}
.theme-check {
  margin-left: auto;
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.theme-trigger:focus-visible,
.theme-options button:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
</style>
