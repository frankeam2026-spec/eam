<script setup lang="ts">
import { ElButton, ElInput } from 'element-plus';

import SystemBrand from './common/SystemBrand.vue';
import { defaultBranding } from '../branding';
import { useBrandingStore } from '../stores/branding';
import { reactive, ref, computed, watch } from 'vue';
defineProps<{ state: 'loading' | 'login' | 'unavailable'; message: string }>();
const store = useBrandingStore();
const visual = computed(() => store.branding);
const backgroundFailed = ref(false);
watch(
  () => visual.value.backgroundUrl,
  () => {
    backgroundFailed.value = false;
  },
);
const emit = defineEmits<{
  login: [tenantId: string, account: string, password: string];
  retry: [];
}>();
const form = reactive({ tenantId: '10001', account: '', password: '' });
const submitting = ref(false);
watch(
  () => form.tenantId,
  (id) => {
    if (/^[1-9][0-9]*$/.test(id)) void store.load(id);
  },
  { immediate: true },
);
function submit() {
  if (!form.tenantId.trim() || !form.account.trim() || !form.password) return;
  submitting.value = true;
  emit('login', form.tenantId.trim(), form.account.trim(), form.password);
  window.setTimeout(() => (submitting.value = false), 800);
}
</script>

<template>
  <main class="login-screen">
    <img
      class="login-background"
      :src="backgroundFailed ? defaultBranding.backgroundUrl : visual.backgroundUrl"
      :style="{ objectPosition: visual.backgroundPosition }"
      alt=""
      @error="backgroundFailed = true"
    />
    <SystemBrand class="login-brand" :name="visual.systemName" :logo-url="visual.logoUrl" />
    <section class="login-card">
      <h1>登录系统</h1>
      <p>使用企业账号访问设备与运维数据</p>
      <div v-if="state === 'loading'" class="login-status">正在连接服务…</div>
      <div v-else-if="state === 'unavailable'" class="login-status">
        <span>{{ message || '服务暂时无法连接' }}</span
        ><ElButton @click="emit('retry')" native-type="button">重新连接</ElButton>
      </div>
      <form v-else @submit.prevent="submit">
        <label
          >租户编号<ElInput
            v-model="form.tenantId"
            inputmode="numeric"
            autocomplete="organization"
            placeholder="请输入租户编号" /></label
        ><label
          >登录账号<ElInput
            v-model="form.account"
            autocomplete="username"
            placeholder="请输入账号" /></label
        ><label
          >密码<ElInput
            v-model="form.password"
            type="password"
            autocomplete="current-password"
            placeholder="请输入密码"
        /></label>
        <div v-if="message" class="login-error">{{ message }}</div>
        <ElButton class="login-submit" :disabled="submitting" native-type="submit" type="primary"
          >登录</ElButton
        >
      </form>
    </section>
    <div class="login-foot">企业级设备管理 · 安全访问</div>
  </main>
</template>

<style scoped>
.login-screen {
  position: relative;
  isolation: isolate;
  overflow: hidden;
}
.login-background {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  z-index: -1;
}

.login-screen {
  min-height: 100vh;
  background: linear-gradient(135deg, #f4f7fd, #eaf0fb);
  display: flex;
  flex-direction: column;
  align-items: center;
  color: #273347;
  font-size: 13px;
}
.login-brand {
  align-self: flex-start;
  display: flex;
  align-items: center;
  gap: 11px;
  margin: 24px 32px;
  padding: 10px 14px;
  background: #ffffffeb;
  border-radius: 10px;
  font-size: 17px;
  font-weight: 650;
}
.login-card {
  width: min(420px, calc(100vw - 32px));
  margin: auto 0;
  padding: 32px;
  background: #fff;
  border: 1px solid #e0e7f1;
  border-radius: 10px;
  box-shadow: 0 18px 56px #20375614;
}
.login-card h1 {
  margin: 0 0 6px;
  font-size: 22px;
}
.login-card p {
  margin: 0 0 25px;
  color: #7a879a;
}
.login-card form {
  display: grid;
  gap: 16px;
}
.login-card label {
  display: grid;
  gap: 7px;
  color: #4d5b70;
  font-weight: 550;
}
.login-submit,
.login-status button {
  height: 40px;
  margin-top: 8px;
  border: 0;
  border-radius: 5px;
  background: #205cf5;
  color: white;
  font: inherit;
  font-weight: 600;
  cursor: pointer;
}
.login-submit:disabled {
  opacity: 0.65;
}
.login-error {
  color: #c13e49;
}
.login-status {
  display: grid;
  gap: 14px;
  color: #68778d;
}
.login-foot {
  padding: 24px;
  color: #9ba8ba;
}
</style>
