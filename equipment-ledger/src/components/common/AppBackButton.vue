<script setup lang="ts">
withDefaults(defineProps<{ label?: string; disabled?: boolean }>(), {
  label: '返回',
  disabled: false,
});
</script>
<template>
  <button
    class="app-back-button"
    type="button"
    :aria-label="label"
    :title="label"
    :disabled="disabled"
  >
    <span aria-hidden="true">‹</span>
  </button>
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
