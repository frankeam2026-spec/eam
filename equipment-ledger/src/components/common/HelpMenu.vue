<script setup lang="ts">import { tr } from '../../i18n';

import AppIcon from './AppIcon.vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton } from 'element-plus';
import AppDialog from './AppDialog.vue';
import { version } from '../../../package.json';

const section = defineModel<string>({ default: '' });
const titles: Record<string, string> = {
  guide: '操作指南',
  feedback: '问题反馈',
  about: '关于系统',
};
</script>

<template>
  <ElDropdown class="help-menu" trigger="click" placement="bottom-end" @command="section = $event">
    <ElButton class="help-trigger" :title="tr('ui.equipment.text1408')" :aria-label="tr('ui.equipment.text1408')" native-type="button"
      ><AppIcon name="Help"
    /></ElButton>
    <template #dropdown>
      <ElDropdownMenu>
        <ElDropdownItem command="guide">{{ tr('ui.equipment.text0050') }}</ElDropdownItem>
        <ElDropdownItem command="feedback">{{ tr('ui.equipment.text1409') }}</ElDropdownItem>
        <ElDropdownItem command="about" divided>{{ tr('ui.equipment.text1410') }}</ElDropdownItem>
      </ElDropdownMenu>
    </template>
  </ElDropdown>
  <AppDialog
    :model-value="Boolean(section)"
    :title="titles[section] || tr('ui.equipment.text1408')"
    width="520px"
    @update:model-value="section = ''"
  >
    <div class="help-content">
      <template v-if="section === 'guide'">
        <h3>{{ tr('ui.equipment.text1411') }}</h3>
        <p>
          {{ tr('ui.equipment.text1412') }}
        </p>
        <h3>{{ tr('ui.equipment.text1413') }}</h3>
        <p>
          {{ tr('ui.equipment.text1414') }}
        </p>
        <h3>{{ tr('ui.equipment.text1415') }}</h3>
        <p>
          {{ tr('ui.equipment.text1416') }}
        </p>
      </template>
      <template v-else-if="section === 'feedback'">
        <p>{{ tr('ui.equipment.text1417') }}</p>
        <ul>
          <li>{{ tr('ui.equipment.text1418') }}</li>
          <li>{{ tr('ui.equipment.text1419') }}</li>
          <li>{{ tr('ui.equipment.text1420') }}</li>
        </ul>
        <p>{{ tr('ui.equipment.text1421') }}</p>
      </template>
      <template v-else-if="section === 'about'">
        <h3>{{ tr('ui.equipment.text1422') }}</h3>
        <p>{{ tr('ui.equipment.text1423') }}{{ version }}</p>
        <p>{{ tr('ui.equipment.text1424') }}</p>
      </template>
    </div>
    <template #footer><ElButton @click="section = ''">{{ tr('ui.equipment.text0076') }}</ElButton></template>
  </AppDialog>
</template>

<style scoped>
.help-menu {
  color: inherit;
}
.help-trigger {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 34px;
  padding: 0 8px;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  cursor: pointer;
}
.help-trigger:hover {
  background: #ffffff20;
}
.help-trigger:focus-visible {
  outline: 2px solid currentColor;
  outline-offset: 2px;
}
.help-trigger .app-icon {
  width: 20px;
  height: 20px;
}
.help-content {
  color: var(--text);
  font-size: 13px;
  line-height: 1.8;
}
.help-content h3 {
  margin: 16px 0 6px;
  font-size: 13px;
  font-weight: 600;
}
.help-content h3:first-child {
  margin-top: 0;
}
.help-content p {
  margin: 0 0 12px;
}
.help-content ul {
  padding-left: 20px;
}
</style>
