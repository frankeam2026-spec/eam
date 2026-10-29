<script setup lang="ts">
import 'element-plus/es/components/form/style/css';
import 'element-plus/es/components/form-item/style/css';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import {
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElSwitch,
  ElButton,
  ElAlert,
  ElMessage,
  ElTableColumn,
  ElTabs,
  ElTabPane,
} from 'element-plus';
import type { FormRules } from 'element-plus';
import { request } from '../api';
import { tr } from '../i18n';
import { useAuthStore } from '../stores/auth';
import { formatDateTime } from '../utils/dateTime';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppPagination from '../components/common/AppPagination.vue';
const auth = useAuthStore(),
  tab = ref('settings'),
  ready = ref(false),
  busy = ref(false),
  error = ref('');
const settings = reactive({
  enabled: false,
  baseUrl: '',
  apiKey: '',
  chatModel: 'qwen-plus',
  embeddingModel: '',
  visionModel: '',
  audioModel: '',
  visionInputPrice: 0,
  visionOutputPrice: 0,
  audioMinutePrice: 0,
  monthlyLimit: 0,
  warningPercent: 80,
  inputPrice: 0,
  outputPrice: 0,
  embeddingPrice: 0,
  rowVersion: 0,
  hasApiKey: false,
  estimatedSpent: 0,
  reservedAmount: 0,
  billingMonth: '',
  budgetWarning: false,
});
const form = ref<InstanceType<typeof ElForm>>();
const rules = computed<FormRules>(() => {
  const result: FormRules = {};
  for (const key of ['chatModel', 'embeddingModel', 'visionModel', 'audioModel'] as const)
    result[key] = [
      {
        validator: (_rule, value, done) => {
          const text = String(value ?? '');
          if (!text && (key === 'chatModel' || key === 'embeddingModel'))
            done(new Error(tr('ai.validation.required')));
          else if (text && !/^[A-Za-z0-9_.:-]{1,128}$/.test(text))
            done(new Error(tr('ai.validation.model')));
          else done();
        },
        trigger: 'blur',
      },
    ];
  result.baseUrl = [
    {
      validator: (_rule, value, done) => {
        try {
          const u = new URL(String(value));
          const tokenPlan =
            u.hostname === 'token-plan.maas.qianwenaiapi.com' &&
            ['/compatible-mode/v1', '/compatible-mode/v1/'].includes(u.pathname) &&
            (!u.port || u.port === '443');
          if (
            String(value).length > 500 ||
            u.protocol !== 'https:' ||
            !(u.hostname.endsWith('.aliyuncs.com') || tokenPlan) ||
            u.username ||
            u.password ||
            u.search ||
            u.hash
          )
            throw Error();
          done();
        } catch {
          done(new Error(tr('ai.validation.address')));
        }
      },
      trigger: 'blur',
    },
  ];
  result.apiKey = [
    {
      validator: (_rule, value, done) => {
        if (settings.enabled && !settings.hasApiKey && !String(value || '').trim())
          done(new Error(tr('ai.keyRequired')));
        else if (String(value || '').length > 1000) done(new Error(tr('ai.validation.keyLength')));
        else done();
      },
      trigger: 'blur',
    },
  ];
  for (const key of fields)
    result[key] = [
      {
        validator: (_rule, value, done) => {
          if (typeof value !== 'number' || !Number.isFinite(value) || value < 0 || value > 1000000)
            done(new Error(tr('ai.validation.number')));
          else if (
            settings.enabled &&
            ['monthlyLimit', 'inputPrice', 'outputPrice', 'embeddingPrice'].includes(key) &&
            value <= 0
          )
            done(new Error(tr('ai.validation.positive')));
          else done();
        },
        trigger: 'change',
      },
    ];
  result.warningPercent = [
    {
      type: 'number',
      required: true,
      min: 1,
      max: 100,
      message: tr('ai.validation.percent'),
      trigger: 'change',
    },
  ];
  return result;
});
const guard = useUnsavedChanges(
  () => settings,
  () => ready.value,
  () => busy.value,
);
const fields = [
  'monthlyLimit',
  'inputPrice',
  'outputPrice',
  'embeddingPrice',
  'visionInputPrice',
  'visionOutputPrice',
  'audioMinutePrice',
] as const;
const modelFields = ['chatModel', 'embeddingModel', 'visionModel', 'audioModel'] as const;
const priceFields = [
  'inputPrice',
  'outputPrice',
  'visionInputPrice',
  'visionOutputPrice',
  'embeddingPrice',
  'audioMinutePrice',
] as const;
const page = ref(1),
  size = ref(20),
  total = ref(0),
  calls = ref<object[]>([]);
function message(e: unknown) {
  return e instanceof Error ? e.message : tr('common.error.requestFailed');
}
async function load() {
  try {
    Object.assign(settings, await request('/ai-settings'));
    settings.apiKey = '';
    ready.value = true;
    guard.markSaved();
  } catch (e) {
    error.value = message(e);
  }
}
async function reload() {
  if (await guard.permitLeave()) await load();
}
async function save() {
  error.value = '';
  if (!(await form.value?.validate().catch(() => false))) return;
  busy.value = true;
  error.value = '';
  try {
    Object.assign(
      settings,
      await request('/ai-settings', { method: 'PUT', body: JSON.stringify(settings) }),
    );
    settings.apiKey = '';
    guard.markSaved();
    ElMessage.success(tr('ai.saved'));
  } catch (e) {
    error.value = message(e);
  } finally {
    busy.value = false;
  }
}
let revision = 0;
async function loadCalls() {
  const current = ++revision;
  try {
    const result = await request<{ data: object[]; total: number }>(
      `/ai-settings/calls?page=${page.value}&size=${size.value}`,
    );
    if (current === revision) {
      calls.value = result.data;
      total.value = result.total;
    }
  } catch (e) {
    if (current === revision) error.value = message(e);
  }
}
watch([tab, page, size], () => {
  if (tab.value === 'calls') void loadCalls();
});
onMounted(load);
</script>
<template>
  <section class="ai-settings">
    <ElTabs v-model="tab"
      ><ElTabPane name="settings" :label="tr('ai.settings')" /><ElTabPane
        name="calls"
        :label="tr('ai.calls')"
    /></ElTabs>
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <div v-if="tab === 'settings'" class="ai-settings-body">
      <ElAlert
        v-if="settings.budgetWarning"
        :title="tr('ai.budgetWarning')"
        type="warning"
        :closable="false"
      />
      <ElForm
        ref="form"
        :model="settings"
        :rules="rules"
        :disabled="busy || !ready || !auth.can('aiSetting', '编辑')"
        label-width="132px"
        label-position="left"
      >
        <section class="ai-section">
          <h3>{{ tr('ai.connection') }}</h3>
          <div class="ai-grid">
            <ElFormItem prop="enabled" :label="tr('ai.enabled')" class="ai-full"
              ><ElSwitch v-model="settings.enabled"
            /></ElFormItem>
            <ElFormItem prop="baseUrl" :label="tr('ai.baseUrl')" class="ai-full"
              ><ElInput v-model="settings.baseUrl" maxlength="500"
            /></ElFormItem>
            <ElFormItem prop="apiKey" :label="tr('ai.apiKey')" class="ai-full"
              ><ElInput
                v-model="settings.apiKey"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="tr(settings.hasApiKey ? 'ai.keyKept' : 'ai.keyRequired')"
            /></ElFormItem>
          </div>
        </section>
        <section class="ai-section">
          <h3>{{ tr('ai.modelConfiguration') }}</h3>
          <div class="ai-grid">
            <ElFormItem
              v-for="field in modelFields"
              :key="field"
              :prop="field"
              :label="tr('ai.' + field)"
              ><ElInput v-model="settings[field]" maxlength="128"
            /></ElFormItem>
          </div>
        </section>
        <section class="ai-section">
          <h3>{{ tr('ai.budget') }}</h3>
          <div class="ai-grid ai-budget-grid">
            <ElFormItem prop="monthlyLimit" :label="tr('ai.monthlyLimit')"
              ><div class="ai-number-control">
                <ElInputNumber
                  v-model="settings.monthlyLimit"
                  :min="0"
                  :max="1000000"
                  :precision="6"
                  :controls="false"
                /><span class="ai-unit">{{ tr('ai.unit.yuan') }}</span>
              </div></ElFormItem
            >
            <ElFormItem prop="warningPercent" :label="tr('ai.warningPercent')"
              ><div class="ai-number-control">
                <ElInputNumber
                  v-model="settings.warningPercent"
                  :min="1"
                  :max="100"
                  :precision="0"
                  :controls="false"
                /><span class="ai-unit">%</span>
              </div></ElFormItem
            >
          </div>
          <div class="ai-grid">
            <ElFormItem
              v-for="field in priceFields"
              :key="field"
              :prop="field"
              :label="tr('ai.priceLabel.' + field)"
              ><div class="ai-number-control">
                <ElInputNumber
                  v-model="settings[field]"
                  :min="0"
                  :max="1000000"
                  :precision="6"
                  :controls="false"
                /><span class="ai-unit">{{
                  tr(field === 'audioMinutePrice' ? 'ai.unit.minute' : 'ai.unit.millionTokens')
                }}</span>
              </div></ElFormItem
            >
          </div>
        </section>
      </ElForm>
      <p>
        {{
          tr('ai.usage', {
            month: settings.billingMonth,
            spent: settings.estimatedSpent,
            reserved: settings.reservedAmount,
          })
        }}
      </p>
      <p>{{ tr('ai.costNotice') }}</p>
    </div>
    <AppFormFooter v-if="tab === 'settings'"
      ><ElButton :disabled="busy" @click="reload">{{ tr('ai.reload') }}</ElButton
      ><ElButton
        v-if="auth.can('aiSetting', '编辑')"
        type="primary"
        :loading="busy"
        :disabled="!ready"
        @click="save"
        >{{ tr('ai.save') }}</ElButton
      ></AppFormFooter
    >
    <AppBusinessTable v-else :rows="calls" :pagination="false" class="ai-calls"
      ><ElTableColumn :label="tr('ai.createdAt')" width="180"
        ><template #default="scope">{{
          formatDateTime(scope.row.createdAt)
        }}</template></ElTableColumn
      ><ElTableColumn prop="purpose" :label="tr('ai.purpose')" width="120" /><ElTableColumn
        prop="modelName"
        :label="tr('ai.model')"
        min-width="180" /><ElTableColumn prop="status" :label="tr('ai.status')" width="120"
        ><template #default="scope">{{
          tr('ai.status.' + scope.row.status)
        }}</template></ElTableColumn
      ><ElTableColumn
        prop="inputTokens"
        :label="tr('ai.inputTokens')"
        align="right"
        width="120" /><ElTableColumn
        prop="outputTokens"
        :label="tr('ai.outputTokens')"
        align="right"
        width="120" /><ElTableColumn
        prop="estimatedCost"
        :label="tr('ai.estimatedCost')"
        align="right"
        width="150" /><ElTableColumn
        prop="elapsedMs"
        :label="tr('ai.elapsedMs')"
        align="right"
        width="130" /><template #footer
        ><footer class="ai-call-footer">
          <AppPagination
            v-model:page="page"
            v-model:page-size="size"
            :total="total"
          /></footer></template
    ></AppBusinessTable>
  </section>
</template>
<style scoped>
.ai-settings {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: var(--surface, #fff);
  border: 1px solid var(--line);
  border-radius: 6px;
  overflow: hidden;
}
.ai-settings > .el-tabs {
  padding: 0 12px;
}
.ai-settings-body {
  flex: 1;
  overflow: auto;
  padding: 16px;
}
.ai-section {
  max-width: 1200px;
  min-width: 0;
  margin-bottom: 24px;
}
.ai-section h3 {
  margin: 0 0 16px;
  padding: 0 0 8px;
  font-size: 14px;
  line-height: 24px;
  border-bottom: 1px solid var(--line);
}
.ai-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 24px;
}
.ai-budget-grid {
  margin-bottom: 16px;
}
.ai-full {
  grid-column: 1 / -1;
}
.ai-grid :deep(.el-form-item) {
  min-width: 0;
  margin-bottom: 0;
  align-items: flex-start;
}
.ai-grid :deep(.el-form-item__label) {
  line-height: 34px;
  height: auto;
}
.ai-grid :deep(.el-form-item__content) {
  min-width: 0;
}
.ai-grid :deep(.el-input) {
  width: 100%;
}
.ai-grid :deep(.el-form-item__error) {
  position: static;
  flex-basis: 100%;
  max-width: 100%;
  line-height: 18px;
  padding-top: 4px;
  overflow-wrap: anywhere;
}
.ai-number-control {
  display: flex;
  align-items: center;
  gap: 8px;
  width: 100%;
  min-width: 0;
}
.ai-number-control :deep(.el-input-number) {
  width: 160px;
  max-width: 100%;
  flex: 0 1 160px;
  min-width: 100px;
}
.ai-unit {
  color: var(--text-secondary);
  font-size: 12px;
  white-space: nowrap;
}
.ai-settings p {
  font-size: 13px;
  line-height: 1.7;
}
.ai-calls {
  flex: 1;
  min-height: 0;
}
.ai-call-footer {
  height: 42px;
  border-top: 1px solid var(--line);
  display: flex;
  align-items: center;
  padding: 0 8px;
}
@media (max-width: 1050px) {
  .ai-grid {
    grid-template-columns: 1fr;
  }
}
</style>
