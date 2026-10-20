<script setup lang="ts">import { tr } from '../../i18n';

import { ElButton } from 'element-plus';

import AppIcon from './AppIcon.vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
defineProps<{ actions: { label: string; run: () => unknown; danger?: boolean }[] }>();
</script>
<template>
  <ElDropdown trigger="click" placement="bottom-end" popper-class="eam-dropdown"
    ><ElButton class="row-more" :aria-label="tr('ui.equipment.text0673')" :title="tr('ui.equipment.text0673')"
      ><AppIcon name="More" /></ElButton
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
