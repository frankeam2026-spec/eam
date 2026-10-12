<script setup lang="ts">
defineProps<{ active: string; items: { key: string; label: string }[] }>();
const emit = defineEmits<{ select: [key: string] }>();
</script>
<template>
  <nav class="app-list-tabs" aria-label="业务列表切换">
    <button
      v-for="item in items"
      :key="item.key"
      type="button"
      :class="{ active: active === item.key }"
      :aria-current="active === item.key ? 'page' : undefined"
      @click="emit('select', item.key)"
    >
      {{ item.label }}
    </button>
  </nav>
</template>
<style scoped>
.app-list-tabs {
  display: flex;
  flex: none;
  align-items: center;
  gap: 20px;
  border-bottom: 1px solid var(--el-border-color-light);
}
.app-list-tabs button {
  padding: 8px 4px;
  min-height: 34px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--el-text-color-primary);
  font: inherit;
  font-size: 13px;
  cursor: pointer;
}
.app-list-tabs button:hover {
  color: var(--el-color-primary);
}
.app-list-tabs button.active {
  color: var(--el-color-primary);
  border-bottom-color: var(--el-color-primary);
  font-weight: 700;
}
.app-list-tabs button:focus-visible {
  outline: 2px solid var(--el-color-primary);
  outline-offset: -2px;
}
</style>
