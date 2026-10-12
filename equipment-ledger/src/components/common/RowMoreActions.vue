<script setup lang="ts">
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
defineProps<{ actions: { label: string; run: () => unknown; danger?: boolean }[] }>();
</script>
<template>
  <ElDropdown trigger="click" placement="bottom-end" popper-class="eam-dropdown"
    ><button class="row-more" aria-label="更多操作" title="更多操作">···</button
    ><template #dropdown
      ><ElDropdownMenu
        ><ElDropdownItem
          v-for="action in actions"
          :key="action.label"
          :class="{ danger: action.danger }"
          @click="action.run"
          >{{ action.label }}</ElDropdownItem
        ></ElDropdownMenu
      ></template
    ></ElDropdown
  >
</template>
<style scoped>
.row-more {
  display: inline-grid;
  place-items: center;
  width: 28px;
  height: 28px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: var(--brand, #205cf5);
  font-size: 16px;
  cursor: pointer;
  vertical-align: middle;
}
.row-more:hover {
  background: var(--brand-soft, #eaf0ff);
}
.danger {
  color: #c74444;
}
</style>
