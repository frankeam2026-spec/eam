<script setup lang="ts">
import 'element-plus/es/components/card/style/css';
import 'element-plus/es/components/descriptions/style/css';
import 'element-plus/es/components/descriptions-item/style/css';
import 'element-plus/es/components/radio-group/style/css';
import 'element-plus/es/components/radio-button/style/css';
import 'element-plus/es/components/alert/style/css';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import {
  ElAlert,
  ElButton,
  ElCard,
  ElDescriptions,
  ElDescriptionsItem,
  ElForm,
  ElInput,
  ElMessage,
  ElRadioButton,
  ElRadioGroup,
  ElTag,
  type FormInstance,
  type FormRules,
} from 'element-plus';
import AppBackButton from '../components/common/AppBackButton.vue';
import AppFormField from '../components/common/AppFormField.vue';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import { ApiError, changeOwnPassword, getPersonalSettings } from '../api';
import { useAuthStore } from '../stores/auth';
import { useUiStore, type Theme } from '../stores/ui';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';

const auth = useAuthStore(),
  ui = useUiStore(),
  router = useRouter();
const roles = ref<string[]>([]),
  loading = ref(false),
  loadError = ref('');
const saving = ref(false),
  requestError = ref('');
const passwordForm = ref<FormInstance>();
const password = reactive({ currentPassword: '', newPassword: '', confirmation: '' });
const fieldErrors = reactive<Record<string, string>>({});
const themes: { value: Theme; name: string; color: string }[] = [
  { value: 'blue', name: '蓝色', color: '#205cf5' },
  { value: 'navy', name: '深蓝', color: '#003e96' },
  { value: 'pink', name: '粉色', color: '#ff8ba3' },
];
const busy = computed(() => saving.value || ui.savingTheme);
const { markSaved } = useUnsavedChanges(
  () => password,
  () => !auth.developmentMode,
  () => busy.value,
);
const rules: FormRules = {
  currentPassword: [{ required: true, message: '请输入当前密码', trigger: 'blur' }],
  newPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error('请输入新密码'));
        if (value.length < 12 || new TextEncoder().encode(value).length > 72)
          return callback(new Error('新密码至少12位，且不超过72字节'));
        if (value === password.currentPassword)
          return callback(new Error('新密码不能与当前密码相同'));
        callback();
      },
      trigger: 'blur',
    },
  ],
  confirmation: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error('请再次输入新密码'));
        if (value !== password.newPassword) return callback(new Error('两次输入的新密码不一致'));
        callback();
      },
      trigger: 'blur',
    },
  ],
};
for (const field of Object.keys(password) as Array<keyof typeof password>)
  watch(
    () => password[field],
    () => {
      fieldErrors[field] = '';
      requestError.value = '';
    },
  );
watch(
  () => password.newPassword,
  () => {
    if (password.confirmation)
      void passwordForm.value?.validateField('confirmation').catch(() => {});
  },
);
async function load() {
  loading.value = true;
  loadError.value = '';
  try {
    const { data } = await getPersonalSettings();
    roles.value = data.roles;
  } catch (error) {
    loadError.value = error instanceof Error ? error.message : '个人设置加载失败';
  } finally {
    loading.value = false;
  }
}
async function chooseTheme(value: string | number | boolean | undefined) {
  if (!themes.some((theme) => theme.value === value) || value === ui.palette || busy.value) return;
  try {
    await ui.setTheme(value as Theme);
    ElMessage.success('主题已保存');
  } catch {
    /* Store keeps the failure message and restores the previous theme. */
  }
}
async function retryTheme() {
  if (auth.user) await ui.bindUser(auth.user.tenantId, auth.user.id);
}
function clearPassword() {
  Object.assign(password, { currentPassword: '', newPassword: '', confirmation: '' });
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key];
  passwordForm.value?.clearValidate();
  requestError.value = '';
  markSaved();
}
function back() {
  const previous = router.options.history.state.back;
  if (typeof previous === 'string' && previous.startsWith('/')) router.back();
  else void router.push({ name: 'workspace' });
}
async function savePassword() {
  if (busy.value || auth.developmentMode || !passwordForm.value) return;
  for (const key of Object.keys(fieldErrors)) delete fieldErrors[key];
  if (!(await passwordForm.value.validate().catch(() => false))) return;
  saving.value = true;
  requestError.value = '';
  try {
    await changeOwnPassword(password.currentPassword, password.newPassword);
    clearPassword();
    window.dispatchEvent(new Event('eam-session-expired'));
    auth.authMessage = '密码已修改，请使用新密码重新登录';
    ElMessage.success('密码已修改，请重新登录');
  } catch (error) {
    if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
      Object.assign(fieldErrors, error.fieldErrors);
      const first = Object.keys(error.fieldErrors)[0];
      if (first) passwordForm.value.scrollToField(first);
    } else requestError.value = error instanceof Error ? error.message : '修改密码失败';
  } finally {
    saving.value = false;
  }
}
onMounted(() => {
  markSaved();
  void load();
});
</script>

<template>
  <section class="personal-settings-page">
    <header class="settings-heading">
      <AppBackButton :disabled="busy" @click="back" />
      <h1>个人设置</h1>
    </header>
    <div class="settings-scroll">
      <div class="settings-content">
        <ElAlert v-if="loadError" type="error" :closable="false" show-icon>
          <template #title>{{ loadError }}</template>
          <ElButton link type="primary" :loading="loading" @click="load">重新加载</ElButton>
        </ElAlert>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>基本信息</h2></template>
          <ElDescriptions :column="2" size="default" class="profile-details">
            <ElDescriptionsItem label="姓名">{{ auth.currentUser }}</ElDescriptionsItem>
            <ElDescriptionsItem label="账号">{{ auth.user?.account }}</ElDescriptionsItem>
            <ElDescriptionsItem label="所属公司">{{ auth.currentCompany }}</ElDescriptionsItem>
            <ElDescriptionsItem label="所属部门">{{ auth.currentDepartment }}</ElDescriptionsItem>
            <ElDescriptionsItem label="角色" :span="2">
              <div class="profile-roles">
                <ElTag v-for="role in roles" :key="role">{{ role }}</ElTag>
              </div>
            </ElDescriptionsItem>
          </ElDescriptions>
          <p class="settings-note">基本信息由管理员维护。</p>
        </ElCard>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>主题皮肤</h2></template>
          <ElRadioGroup
            :model-value="ui.palette"
            :disabled="busy"
            aria-label="选择主题"
            @update:model-value="chooseTheme"
          >
            <ElRadioButton v-for="theme in themes" :key="theme.value" :value="theme.value">
              <span class="theme-option"
                ><span class="theme-color" :style="{ backgroundColor: theme.color }"></span
                >{{ theme.name }}</span
              >
            </ElRadioButton>
          </ElRadioGroup>
          <p class="settings-note">
            {{ ui.savingTheme ? '正在保存主题…' : '选择后自动保存，仅对当前账号生效。' }}
          </p>
          <ElAlert v-if="ui.preferenceError" type="error" :closable="false" show-icon>
            <template #title>{{ ui.preferenceError }}</template>
            <ElButton link type="primary" @click="retryTheme">重新加载</ElButton>
          </ElAlert>
        </ElCard>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>修改密码</h2></template>
          <ElAlert
            v-if="auth.developmentMode"
            class="password-mode-notice"
            type="info"
            :closable="false"
            show-icon
            title="当前为开发免登录模式，暂不支持修改密码。启用正式登录后可使用。"
          />
          <ElForm
            ref="passwordForm"
            :model="password"
            :rules="rules"
            label-width="100px"
            :disabled="auth.developmentMode || saving"
            :scroll-to-error="true"
            class="settings-password-form"
            @submit.prevent="savePassword"
          >
            <AppFormField
              label="当前密码"
              prop="currentPassword"
              :error="fieldErrors.currentPassword"
              required
            >
              <ElInput
                v-model="password.currentPassword"
                type="password"
                show-password
                autocomplete="current-password"
                placeholder="请输入当前密码"
              />
            </AppFormField>
            <AppFormField
              label="新密码"
              prop="newPassword"
              :error="fieldErrors.newPassword"
              required
            >
              <ElInput
                v-model="password.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="至少12位，且不超过72字节"
              />
            </AppFormField>
            <AppFormField
              label="确认密码"
              prop="confirmation"
              :error="fieldErrors.confirmation"
              required
            >
              <ElInput
                v-model="password.confirmation"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="请再次输入新密码"
                @keyup.enter="savePassword"
              />
            </AppFormField>
          </ElForm>
          <ElAlert
            v-if="requestError"
            type="error"
            :closable="false"
            show-icon
            :title="requestError"
          />
          <p class="settings-note">修改成功后需要重新登录，其他设备上的登录也将失效。</p>
        </ElCard>
      </div>
    </div>
    <AppFormFooter>
      <ElButton :disabled="busy" @click="back">关闭</ElButton>
      <ElButton
        type="primary"
        :disabled="auth.developmentMode || ui.savingTheme"
        :loading="saving"
        @click="savePassword"
        >修改密码</ElButton
      >
    </AppFormFooter>
  </section>
</template>

<style scoped>
.personal-settings-page {
  height: calc(100dvh - 54px);
  min-height: 0;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  color: var(--text);
  font-size: 13px;
}
.settings-heading {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 12px 20px;
  background: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}
.settings-heading h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
}
.settings-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 16px 20px;
}
.settings-content {
  width: 100%;
  max-width: 960px;
  margin: 0 auto;
  display: grid;
  gap: 16px;
}
.settings-card {
  --el-card-padding: 18px;
}
.settings-card h2 {
  font-size: 14px;
  margin: 0;
  font-weight: 600;
}
.settings-note {
  margin: 12px 0 0;
  font-size: 13px;
  line-height: 1.6;
}
.profile-roles {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.theme-option {
  display: inline-flex;
  align-items: center;
  gap: 10px;
}
.theme-color {
  width: 18px;
  height: 18px;
  border-radius: 4px;
  border: 1px solid #00000014;
}
.settings-password-form {
  width: 100%;
  max-width: 560px;
}
.settings-password-form :deep(.el-form-item) {
  margin-bottom: 22px;
}
.password-mode-notice {
  margin-bottom: 18px;
}
@media (max-width: 600px) {
  .settings-heading {
    padding: 10px 12px;
  }
  .settings-scroll {
    padding: 12px;
  }
  .settings-password-form :deep(.el-form-item__label) {
    width: 80px !important;
  }
}
</style>
