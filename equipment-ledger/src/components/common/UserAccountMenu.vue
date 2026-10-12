<script setup lang="ts">
import { ref } from 'vue';
import {
  ElDropdown,
  ElDropdownMenu,
  ElDropdownItem,
  ElInput,
  ElButton,
  ElMessage,
} from 'element-plus';
import AppDialog from './AppDialog.vue';
import { useAuthStore } from '../../stores/auth';
import { changeOwnPassword } from '../../api';
const auth = useAuthStore();
const emit = defineEmits<{ logout: [] }>();
const dialog = ref(false),
  busy = ref(false),
  current = ref(''),
  password = ref(''),
  confirm = ref(''),
  error = ref('');
function command(value: string) {
  if (value === 'logout') emit('logout');
  else {
    current.value = '';
    password.value = '';
    confirm.value = '';
    error.value = '';
    dialog.value = true;
  }
}
async function save() {
  if (busy.value) return;
  error.value = '';
  if (!current.value) {
    error.value = '请输入当前密码';
    return;
  }
  if (password.value.length < 12) {
    error.value = '新密码至少12位';
    return;
  }
  if (password.value !== confirm.value) {
    error.value = '两次输入的新密码不一致';
    return;
  }
  busy.value = true;
  try {
    await changeOwnPassword(current.value, password.value);
    dialog.value = false;
    current.value = password.value = confirm.value = '';
    ElMessage.success('密码已修改');
  } catch (e) {
    error.value = e instanceof Error ? e.message : '修改失败';
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <ElDropdown trigger="click" placement="bottom-end" @command="command">
    <button class="account-trigger" type="button" aria-label="用户菜单">
      <span class="account-avatar">{{ auth.currentUser.slice(0, 1) }}</span
      ><span class="account-name"
        ><b>{{ auth.currentUser }}</b
        ><small>{{ auth.currentDepartment || '当前用户' }}</small></span
      ><svg viewBox="0 0 16 16" aria-hidden="true"><path d="m4 6 4 4 4-4" /></svg>
    </button>
    <template #dropdown
      ><ElDropdownMenu
        ><ElDropdownItem command="password">修改密码</ElDropdownItem
        ><ElDropdownItem command="logout" divided>退出</ElDropdownItem></ElDropdownMenu
      ></template
    >
  </ElDropdown>
  <AppDialog v-model="dialog" title="修改密码" :busy="busy" width="460px">
    <form class="password-form" @submit.prevent="save">
      <label
        ><span>当前密码</span
        ><ElInput
          v-model="current"
          type="password"
          show-password
          autocomplete="current-password"
          placeholder="输入当前密码"
      /></label>
      <label
        ><span>新密码</span
        ><ElInput
          v-model="password"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="至少12位，不超过72字节"
      /></label>
      <label
        ><span>确认密码</span
        ><ElInput
          v-model="confirm"
          type="password"
          show-password
          autocomplete="new-password"
          placeholder="再次输入新密码"
      /></label>
      <p v-if="error" class="password-error" role="alert">{{ error }}</p>
    </form>
    <template #footer
      ><ElButton :disabled="busy" @click="dialog = false">取消</ElButton
      ><ElButton type="primary" :loading="busy" @click="save">提交</ElButton></template
    >
  </AppDialog>
</template>
<style scoped>
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
.account-trigger svg {
  width: 14px;
  height: 14px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
}
.password-form {
  display: grid;
  gap: 16px;
}
.password-form label {
  display: grid;
  grid-template-columns: 80px 1fr;
  align-items: center;
  gap: 12px;
  font-size: 13px;
  color: #667387;
}
.password-error {
  margin: 0;
  color: #c9535e;
  font-size: 13px;
}
</style>
