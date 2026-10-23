<script setup lang="ts">import { tr } from '../i18n';
import DepartmentText from '../components/common/DepartmentText.vue';

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
import LanguageSelect from '../components/common/LanguageSelect.vue';
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
  { value: 'blue', get name() { return tr('common.theme.blue'); }, color: '#205cf5' },
  { value: 'navy', get name() { return tr('common.theme.navy'); }, color: '#003e96' },
  { value: 'pink', get name() { return tr('common.theme.pink'); }, color: '#ff8ba3' },
];
const busy = computed(() => saving.value || ui.savingTheme);
const { markSaved } = useUnsavedChanges(
  () => password,
  () => !auth.developmentMode,
  () => busy.value,
);
const rules: FormRules = {
  currentPassword: [{ required: true, get message() { return tr('ui.equipment.text1743'); }, trigger: 'blur' }],
  newPassword: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error(tr('ui.equipment.text1178')));
        if (value.length < 12 || new TextEncoder().encode(value).length > 72)
          return callback(new Error(tr('ui.equipment.text1744')));
        if (value === password.currentPassword)
          return callback(new Error(tr('ui.equipment.text1745')));
        callback();
      },
      trigger: 'blur',
    },
  ],
  confirmation: [
    {
      validator: (_rule, value: string, callback) => {
        if (!value) return callback(new Error(tr('ui.equipment.text1180')));
        if (value !== password.newPassword) return callback(new Error(tr('ui.equipment.text1746')));
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
    loadError.value = error instanceof Error ? error.message : tr('ui.equipment.text1747');
  } finally {
    loading.value = false;
  }
}
async function chooseTheme(value: string | number | boolean | undefined) {
  if (!themes.some((theme) => theme.value === value) || value === ui.palette || busy.value) return;
  try {
    await ui.setTheme(value as Theme);
    ElMessage.success(tr('ui.equipment.text1748'));
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
    auth.authMessage = tr('ui.equipment.text1749');
    ElMessage.success(tr('ui.equipment.text1750'));
  } catch (error) {
    if (error instanceof ApiError && Object.keys(error.fieldErrors).length) {
      Object.assign(fieldErrors, error.fieldErrors);
      const first = Object.keys(error.fieldErrors)[0];
      if (first) passwordForm.value.scrollToField(first);
    } else requestError.value = error instanceof Error ? error.message : tr('ui.equipment.text1751');
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
      <h1>{{ tr('ui.equipment.text1600') }}</h1>
    </header>
    <div class="settings-scroll">
      <div class="settings-content">
        <ElAlert v-if="loadError" type="error" :closable="false" show-icon>
          <template #title>{{ loadError }}</template>
          <ElButton link type="primary" :loading="loading" @click="load">{{ tr('ui.equipment.text0910') }}</ElButton>
        </ElAlert>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>{{ tr('ui.equipment.text0077') }}</h2></template>
          <ElDescriptions :column="2" size="default" class="profile-details">
            <ElDescriptionsItem :label="tr('ui.equipment.text1168')">{{ auth.currentUser }}</ElDescriptionsItem>
            <ElDescriptionsItem :label="tr('ui.equipment.text1570')">{{ auth.user?.account }}</ElDescriptionsItem>
            <ElDescriptionsItem :label="tr('ui.equipment.text0487')">{{ auth.currentCompany }}</ElDescriptionsItem>
            <ElDescriptionsItem :label="tr('ui.equipment.text0098')"><DepartmentText :value="auth.currentDepartment" :department-id="auth.user?.orgId" /></ElDescriptionsItem>
            <ElDescriptionsItem :label="tr('ui.equipment.text1171')" :span="2">
              <div class="profile-roles">
                <ElTag v-for="role in roles" :key="role">{{ role }}</ElTag>
              </div>
            </ElDescriptionsItem>
          </ElDescriptions>
          <p class="settings-note">{{ tr('ui.equipment.text1752') }}</p>
        </ElCard>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>{{ tr('language.field.displayLanguage') }}</h2></template>
          <LanguageSelect authenticated />
          <p class="settings-note">{{ tr('ui.equipment.text1753') }}</p>
        </ElCard>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>{{ tr('ui.equipment.text1754') }}</h2></template>
          <ElRadioGroup
            :model-value="ui.palette"
            :disabled="busy"
            :aria-label="tr('ui.equipment.text1755')"
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
            {{ ui.savingTheme ? tr('ui.equipment.text1756') : tr('ui.equipment.text1753') }}
          </p>
          <ElAlert v-if="ui.preferenceError" type="error" :closable="false" show-icon>
            <template #title>{{ ui.preferenceError }}</template>
            <ElButton link type="primary" @click="retryTheme">{{ tr('ui.equipment.text0910') }}</ElButton>
          </ElAlert>
        </ElCard>
        <ElCard shadow="never" class="settings-card">
          <template #header><h2>{{ tr('ui.equipment.text1757') }}</h2></template>
          <ElAlert
            v-if="auth.developmentMode"
            class="password-mode-notice"
            type="info"
            :closable="false"
            show-icon
            :title="tr('ui.equipment.text1758')"
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
              :label="tr('ui.equipment.text1759')"
              prop="currentPassword"
              :error="fieldErrors.currentPassword"
              required
            >
              <ElInput
                v-model="password.currentPassword"
                type="password"
                show-password
                autocomplete="current-password"
                :placeholder="tr('ui.equipment.text1743')"
              />
            </AppFormField>
            <AppFormField
              :label="tr('ui.equipment.text1221')"
              prop="newPassword"
              :error="fieldErrors.newPassword"
              required
            >
              <ElInput
                v-model="password.newPassword"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="tr('ui.equipment.text1760')"
              />
            </AppFormField>
            <AppFormField
              :label="tr('ui.equipment.text1223')"
              prop="confirmation"
              :error="fieldErrors.confirmation"
              required
            >
              <ElInput
                v-model="password.confirmation"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="tr('ui.equipment.text1180')"
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
          <p class="settings-note">{{ tr('ui.equipment.text1761') }}</p>
        </ElCard>
      </div>
    </div>
    <AppFormFooter>
      <ElButton :disabled="busy" @click="back">{{ tr('ui.equipment.text0076') }}</ElButton>
      <ElButton
        type="primary"
        :disabled="auth.developmentMode || ui.savingTheme"
        :loading="saving"
        @click="savePassword"
        >{{ tr('ui.equipment.text1757') }}</ElButton
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
