<script setup lang="ts">import { tr } from '../../i18n';
import DepartmentText from './DepartmentText.vue';

import AppIcon from './AppIcon.vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem, ElButton } from 'element-plus';
import { useRouter } from 'vue-router';
import { useAuthStore } from '../../stores/auth';
const auth = useAuthStore();
const router = useRouter();
const emit = defineEmits<{ logout: [] }>();
function command(value: string) {
  if (value === 'logout') emit('logout');
  else if (value === 'settings') void router.push({ name: 'personal-settings' });
}
</script>
<template>
  <ElDropdown trigger="click" placement="bottom-end" @command="command">
    <ElButton class="account-trigger" :aria-label="tr('ui.equipment.text1598')" native-type="button"
      ><span class="account-avatar">{{ auth.currentUser.slice(0, 1) }}</span
      ><span class="account-name"
        ><b>{{ auth.currentUser }}</b
        ><small><DepartmentText :value="auth.currentDepartment" :department-id="auth.user?.orgId" :empty="tr('ui.equipment.text1599')" /></small></span
      ><AppIcon name="Down"
    /></ElButton>
    <template #dropdown
      ><ElDropdownMenu class="account-dropdown-menu"
        ><ElDropdownItem command="settings">{{ tr('ui.equipment.text1600') }}</ElDropdownItem
        ><ElDropdownItem command="logout" divided>{{ tr('ui.equipment.text1601') }}</ElDropdownItem></ElDropdownMenu
      ></template
    >
  </ElDropdown>
</template>
<style scoped>
.account-dropdown-menu {
  min-width: 220px;
  max-width: calc(100vw - 32px);
  box-sizing: border-box;
}
.account-trigger {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 0 4px;
  border: 0;
  background: transparent;
  color: #fff;
  cursor: pointer;
  font: inherit;
}
.account-trigger:focus-visible {
  outline: 2px solid #fff;
  outline-offset: 4px;
  border-radius: 4px;
}
.account-avatar {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 50%;
  background: #ffffff30;
  color: #fff;
  font-size: 15px;
}
.account-name {
  display: flex;
  flex-direction: column;
  gap: 3px;
  text-align: left;
  font-size: 13px;
}
.account-name small {
  font-size: 11px;
  opacity: 0.75;
}
.account-trigger .app-icon {
  width: 14px;
  height: 14px;
}
</style>
