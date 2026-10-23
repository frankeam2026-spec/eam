<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue';
import dayjs from 'dayjs';
import {
  ElAlert,
  ElButton,
  ElForm,
  ElFormItem,
  ElInput,
  ElInputNumber,
  ElOption,
  ElSelect,
  ElSwitch,
  ElTableColumn,
  ElTag,
  ElMessage,
  ElDatePicker,
  ElCheckbox,
} from 'element-plus';
import { tr } from '../../i18n';
import { useAuthStore } from '../../stores/auth';
import AppListToolbar from '../common/AppListToolbar.vue';
import AppBusinessTable from '../common/AppBusinessTable.vue';
import AppPagination from '../common/AppPagination.vue';
import AppDialog from '../common/AppDialog.vue';
import ListSearchInput from '../common/ListSearchInput.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import { useUnsavedChanges } from '../../composables/useUnsavedChanges';
import { formatDateTime } from '../../utils/dateTime';
import type { SelectorRow } from '../../selectors';
import {
  notificationApi as api,
  reminderKinds,
  scenes,
  type ReminderKind,
  type ReminderRule,
  type ReminderPolicy,
  type DefaultPolicy,
  type Delivery,
  type NotificationChannels,
  type DeliveryRetention,
} from '../../notifications';
const props = defineProps<{ view: 'policies' | 'channels' | 'deliveries' }>();
const auth = useAuthStore();
const menu = computed(() =>
  props.view === 'policies'
    ? 'notificationPolicy'
    : props.view === 'channels'
      ? 'notificationChannel'
      : 'notificationDelivery',
);
const editable = computed(() =>
  auth.can(menu.value, props.view === 'deliveries' ? '重试' : '编辑'),
);
const kind = ref<ReminderKind>('repair'),
  keyword = ref(''),
  query = ref(''),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  loading = ref(false),
  busy = ref(false),
  error = ref('');
const status = ref(''),
  channel = ref(''),
  policies = ref<ReminderPolicy[]>([]),
  deliveries = ref<Delivery[]>([]),
  defaults = ref<DefaultPolicy[]>([]);
const selectedDefault = ref(''),
  defaultOptions = ref<SelectorRow[]>([]),
  people = ref<SelectorRow[]>([]);
const currentDefault = computed(() => defaults.value.find((d) => d.businessKind === kind.value));
const editor = ref(false),
  draft = ref<ReminderPolicy>({ name: '', businessKind: 'repair', enabled: false, rules: [] }),
  ruleErrors = ref<Record<number, string>>({});
const config = reactive<NotificationChannels>({
  rowVersion: 0,
  systemEnabled: true,
  emailEnabled: false,
  smtpHost: '',
  smtpPort: 587,
  smtpSecurity: 'STARTTLS',
  smtpUsername: '',
  senderAddress: '',
  publicUrl: '',
  appStatus: 'NOT_INTEGRATED',
});
const testAddress = ref('');
const deliveryRange = ref<[string, string]>([
  dayjs().subtract(6, 'day').format('YYYY-MM-DD'),
  dayjs().format('YYYY-MM-DD'),
]);
const deliveryKind = ref(''),
  recipientId = ref(''),
  recipientOptions = ref<SelectorRow[]>([]),
  showInactive = ref(false);
const retentionOpen = ref(false),
  retention = reactive<DeliveryRetention>({ rowVersion: 0, successDays: 90, exceptionDays: 180 });
const retentionEditable = computed(() => auth.can('notificationChannel', '编辑'));
async function openRetention() {
  try {
    Object.assign(retention, (await api.retention()).data);
    retentionOpen.value = true;
  } catch (e) {
    failure(e);
  }
}
async function saveRetention() {
  busy.value = true;
  try {
    Object.assign(retention, (await api.saveRetention({ ...retention })).data);
    retentionOpen.value = false;
    ElMessage.success(tr('notify.saved'));
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
const unsaved = useUnsavedChanges(
  () => draft.value,
  () => editor.value,
  () => busy.value,
);
let revision = 0;
function remember(rows: SelectorRow[]) {
  people.value = [...new Map([...people.value, ...rows].map((p) => [p.id, p])).values()];
}
function failure(e: unknown) {
  error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
}
async function load() {
  const seq = ++revision;
  loading.value = true;
  error.value = '';
  try {
    if (props.view === 'policies') {
      const [list, defs] = await Promise.all([
        api.policies(kind.value, query.value, page.value, size.value),
        api.defaults(),
      ]);
      if (seq !== revision) return;
      policies.value = list.data;
      total.value = list.total;
      defaults.value = defs.data;
      selectedDefault.value = currentDefault.value?.policyId || '';
      const p = selectedDefault.value ? (await api.policy(selectedDefault.value)).data : undefined;
      if (seq !== revision) return;
      defaultOptions.value = p ? [{ id: p.id!, name: p.name }] : [];
    } else if (props.view === 'channels') {
      const result = await api.channels();
      if (seq === revision) Object.assign(config, result.data, { password: '' });
    } else {
      const result = await api.deliveries(
        status.value,
        channel.value,
        query.value,
        page.value,
        size.value,
        {
          from: deliveryRange.value[0],
          to: deliveryRange.value[1],
          businessKind: deliveryKind.value,
          recipientId: recipientId.value,
          showInactive: showInactive.value,
        },
      );
      if (seq === revision) {
        deliveries.value = result.data;
        total.value = result.total;
      }
    }
  } catch (e) {
    if (seq === revision) failure(e);
  } finally {
    if (seq === revision) loading.value = false;
  }
}
watch(
  () => props.view,
  () => {
    page.value = 1;
    query.value = '';
    keyword.value = '';
    policies.value = [];
    deliveries.value = [];
    editor.value = false;
    void load();
  },
  { immediate: true },
);
watch([kind, status, channel, size], () => {
  page.value = 1;
  void load();
});
watch(page, () => void load());
watch([deliveryRange, deliveryKind, recipientId, showInactive], () => {
  if (props.view !== 'deliveries') return;
  page.value = 1;
  void load();
});
async function edit(row?: ReminderPolicy) {
  error.value = '';
  ruleErrors.value = {};
  try {
    draft.value = row?.id
      ? (await api.policy(row.id)).data
      : { name: '', businessKind: kind.value, enabled: false, rules: [] };
    people.value = (draft.value.people || []).map((p) => ({ id: p.userId, name: p.name }));
    editor.value = true;
    unsaved.markSaved();
  } catch (e) {
    failure(e);
  }
}
function addRule(rule?: ReminderRule) {
  draft.value.rules.push(
    rule
      ? { ...JSON.parse(JSON.stringify(rule)), id: undefined }
      : {
          scene: scenes(draft.value.businessKind)[0]!,
          value: 30,
          unit: 'MINUTE',
          enabled: true,
          recipients: [],
          cc: [],
        },
  );
}
async function savePolicy() {
  ruleErrors.value = {};
  if (!draft.value.name.trim()) {
    ElMessage.warning(tr('notify.nameRequired'));
    return;
  }
  draft.value.rules.forEach((r, i) => {
    if (r.enabled && !r.recipients.length) ruleErrors.value[i] = tr('notify.recipientRequired');
  });
  if (Object.keys(ruleErrors.value).length) return;
  busy.value = true;
  try {
    await api.savePolicy(draft.value);
    unsaved.markSaved();
    editor.value = false;
    ElMessage.success(tr('notify.saved'));
    await load();
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
async function saveDefault(policyId: string | null) {
  busy.value = true;
  try {
    defaults.value = (
      await api.setDefault(kind.value, policyId, currentDefault.value?.rowVersion || 0)
    ).data;
    selectedDefault.value = currentDefault.value?.policyId || '';
    await load();
    ElMessage.success(tr('notify.saved'));
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
async function saveConfig() {
  busy.value = true;
  try {
    Object.assign(config, (await api.saveChannels(config)).data, { password: '' });
    ElMessage.success(tr('notify.saved'));
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
async function testEmail() {
  busy.value = true;
  try {
    await api.testEmail(testAddress.value);
    ElMessage.success(tr('notify.testSent'));
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
async function retry(row: Delivery) {
  busy.value = true;
  try {
    await api.retry(row);
    await load();
  } catch (e) {
    failure(e);
  } finally {
    busy.value = false;
  }
}
function search() {
  page.value = 1;
  query.value = keyword.value.trim();
  void load();
}
const userColumns = computed(() => [
  { key: 'name', label: tr('ui.equipment.text1168'), minWidth: 180 },
  { key: 'email', label: tr('notify.email'), minWidth: 220 },
]);
</script>
<template>
  <section class="notification-page">
    <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    <template v-if="view === 'channels'">
      <ElForm
        class="notification-channel-form"
        label-position="top"
        :disabled="!editable || busy || loading"
        @submit.prevent="saveConfig"
      >
        <section class="notification-config-section" aria-labelledby="notification-channel-title">
          <header class="notification-section-heading">
            <h3 id="notification-channel-title">{{ tr('notify.channelOverview') }}</h3>
            <p>{{ tr('notify.channelScope') }}</p>
          </header>
          <div class="notification-channel-overview">
            <div class="notification-channel-summary">
              <div class="notification-channel-title">
                <strong>{{ tr('notify.system') }}</strong
                ><ElTag type="success" effect="light">{{ tr('notify.alwaysEnabled') }}</ElTag>
              </div>
              <p>{{ tr('notify.systemHelp') }}</p>
            </div>
            <div class="notification-channel-summary">
              <div class="notification-channel-title">
                <strong>{{ tr('notify.channel.EMAIL') }}</strong
                ><ElSwitch v-model="config.emailEnabled" :aria-label="tr('notify.emailEnabled')" />
              </div>
              <p>{{ tr('notify.emailHelp') }}</p>
            </div>
            <div class="notification-channel-summary">
              <div class="notification-channel-title">
                <strong>{{ tr('notify.app') }}</strong
                ><ElTag type="info" effect="plain">{{ tr('notify.notIntegrated') }}</ElTag>
              </div>
              <p>{{ tr('notify.appHelp') }}</p>
            </div>
          </div>
        </section>
        <section class="notification-config-section" aria-labelledby="notification-access-title">
          <header class="notification-section-heading">
            <h3 id="notification-access-title">{{ tr('notify.systemAccess') }}</h3>
            <p>{{ tr('notify.publicUrlHelp') }}</p>
          </header>
          <ElFormItem class="notification-public-url" :label="tr('notify.publicUrl')">
            <ElInput v-model="config.publicUrl" placeholder="https://eam.example.com" />
          </ElFormItem>
        </section>
        <section class="notification-config-section" aria-labelledby="notification-email-title">
          <header class="notification-section-heading">
            <h3 id="notification-email-title">{{ tr('notify.emailService') }}</h3>
            <p>{{ tr('notify.smtpHelp') }}</p>
          </header>
          <div class="notification-smtp-fields">
            <ElFormItem :label="tr('notify.smtpHost')"
              ><ElInput v-model="config.smtpHost"
            /></ElFormItem>
            <ElFormItem :label="tr('notify.smtpPort')"
              ><ElInputNumber v-model="config.smtpPort" :min="1" :max="65535"
            /></ElFormItem>
            <ElFormItem :label="tr('notify.smtpSecurity')"
              ><ElSelect v-model="config.smtpSecurity"
                ><ElOption
                  v-for="s in ['NONE', 'STARTTLS', 'SSL']"
                  :key="s"
                  :value="s"
                  :label="tr('notify.security.' + s)" /></ElSelect
            ></ElFormItem>
            <ElFormItem :label="tr('notify.smtpUser')"
              ><ElInput v-model="config.smtpUsername" autocomplete="off"
            /></ElFormItem>
            <ElFormItem :label="tr('notify.smtpPassword')"
              ><ElInput
                v-model="config.password"
                type="password"
                show-password
                autocomplete="new-password"
                :placeholder="config.hasPassword ? tr('notify.passwordKept') : ''"
            /></ElFormItem>
            <ElFormItem :label="tr('notify.sender')"
              ><ElInput v-model="config.senderAddress"
            /></ElFormItem>
          </div>
          <div class="notification-email-test">
            <header class="notification-section-heading">
              <h3>{{ tr('notify.testEmailSection') }}</h3>
              <p>{{ tr('notify.testEmailHelp') }}</p>
            </header>
            <div class="notification-test-fields">
              <ElFormItem :label="tr('notify.testAddress')"
                ><ElInput v-model="testAddress"
              /></ElFormItem>
              <ElButton :loading="busy" :disabled="!testAddress" @click="testEmail">{{
                tr('notify.testEmail')
              }}</ElButton>
            </div>
          </div>
        </section>
        <footer class="notification-channel-actions">
          <ElButton type="primary" :loading="busy" @click="saveConfig">{{
            tr('ui.equipment.text0159')
          }}</ElButton>
        </footer>
      </ElForm>
    </template>
    <template v-else>
      <AppListToolbar>
        <div class="notification-actions">
          <ElButton v-if="view === 'policies' && editable" type="primary" @click="edit()">{{
            tr('notify.addPolicy')
          }}</ElButton>
          <ElButton v-if="view === 'deliveries'" @click="openRetention">{{
            tr('notify.retentionSettings')
          }}</ElButton>
        </div>
        <form @submit.prevent="search">
          <ElSelect v-if="view === 'policies'" v-model="kind" class="notification-filter"
            ><ElOption
              v-for="k in reminderKinds"
              :key="k"
              :value="k"
              :label="tr('notify.kind.' + k)"
          /></ElSelect>
          <template v-else
            ><ElSelect
              v-model="channel"
              clearable
              :placeholder="tr('notify.channelLabel')"
              class="notification-filter"
              ><ElOption
                v-for="c in ['SYSTEM', 'EMAIL', 'APP']"
                :key="c"
                :value="c"
                :label="tr('notify.channel.' + c)" /></ElSelect
            ><ElSelect
              v-model="status"
              clearable
              :placeholder="tr('notify.sendStatus')"
              class="notification-filter"
              ><ElOption
                v-for="s in [
                  'PENDING',
                  'SENDING',
                  'SENT',
                  'FAILED',
                  'UNKNOWN',
                  'MISSING_ADDRESS',
                  'DISABLED',
                  'NOT_INTEGRATED',
                  'CANCELLED',
                ]"
                :key="s"
                :value="s"
                :label="tr('notify.status.' + s)" /></ElSelect
          ></template>
          <ListSearchInput v-model="keyword" :placeholder="tr('notify.search')" /><ElButton
            @click="load"
            >{{ tr('ui.equipment.text0424') }}</ElButton
          >
        </form>
      </AppListToolbar>
      <div v-if="view === 'policies'" class="notification-default">
        <span
          >{{ tr('notify.currentDefault')
          }}{{ defaultOptions[0]?.name || tr('notify.noDefault') }}</span
        >
        <ElButton
          v-if="editable && selectedDefault"
          link
          type="primary"
          :loading="busy"
          @click="saveDefault(null)"
          >{{ tr('notify.clearDefault') }}</ElButton
        >
        <span class="notification-help">{{ tr('notify.defaultHelp') }}</span>
      </div>
      <div v-if="view === 'deliveries'" class="notification-delivery-filters">
        <ElDatePicker
          v-model="deliveryRange"
          type="daterange"
          value-format="YYYY-MM-DD"
          :clearable="false"
          :start-placeholder="tr('notify.startDate')"
          :end-placeholder="tr('notify.endDate')"
          :aria-label="tr('notify.generatedDate')"
        />
        <ElSelect
          v-model="deliveryKind"
          clearable
          :placeholder="tr('notify.business')"
          class="notification-filter"
          ><ElOption v-for="k in reminderKinds" :key="k" :value="k" :label="tr('notify.kind.' + k)"
        /></ElSelect>
        <PagedChoiceField
          v-model="recipientId"
          clearable
          :placeholder="tr('notify.recipient')"
          :options="recipientOptions"
          :load-rows="api.deliveryRecipients"
          :title="tr('notify.recipient')"
          :columns="[{ key: 'name', label: tr('notify.recipient'), minWidth: 180 }]"
          @select="recipientOptions = $event"
        />
        <ElCheckbox v-model="showInactive">{{ tr('notify.showInactive') }}</ElCheckbox>
      </div>
      <AppBusinessTable
        v-if="view === 'policies'"
        :rows="policies"
        :loading="loading"
        :pagination="false"
        :fill="true"
      >
        <ElTableColumn
          prop="name"
          :label="tr('notify.policyName')"
          min-width="220"
          show-overflow-tooltip
          ><template #default="{ row }"
            ><span>{{ row.name }}</span
            ><ElTag
              v-if="row.id === selectedDefault"
              class="notification-default-badge"
              size="small"
              >{{ tr('notify.defaultBadge') }}</ElTag
            ></template
          ></ElTableColumn
        >
        <ElTableColumn :label="tr('notify.business')" min-width="150"
          ><template #default="{ row }">{{
            tr('notify.kind.' + row.businessKind)
          }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('ui.equipment.text0170')" width="110"
          ><template #default="{ row }"
            ><ElTag :type="row.enabled ? 'success' : 'info'">{{
              tr(row.enabled ? 'notify.enabled' : 'notify.draft')
            }}</ElTag></template
          ></ElTableColumn
        >
        <ElTableColumn :label="tr('ui.equipment.text0145')" width="200" fixed="right"
          ><template #default="{ row }"
            ><ElButton link type="primary" @click="edit(row as unknown as ReminderPolicy)">{{
              tr(editable ? 'ui.equipment.text0207' : 'notify.view')
            }}</ElButton
            ><ElButton
              v-if="editable"
              link
              type="primary"
              :disabled="!row.enabled || row.id === selectedDefault || busy"
              @click="saveDefault(String(row.id))"
              >{{
                tr(row.id === selectedDefault ? 'notify.defaultBadge' : 'notify.makeDefault')
              }}</ElButton
            ></template
          ></ElTableColumn
        >
      </AppBusinessTable>
      <AppBusinessTable
        v-else
        :rows="deliveries"
        :loading="loading"
        :pagination="false"
        :fill="true"
      >
        <ElTableColumn :label="tr('notify.generatedDate')" width="170"
          ><template #default="{ row }">{{
            formatDateTime(row.createdAt)
          }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('notify.business')" width="115"
          ><template #default="{ row }">{{
            reminderKinds.includes(row.businessKind as ReminderKind)
              ? tr('notify.kind.' + row.businessKind)
              : tr('notify.otherBusiness')
          }}</template></ElTableColumn
        >
        <ElTableColumn
          prop="subject"
          :label="tr('notify.subject')"
          min-width="250"
          show-overflow-tooltip
        />
        <ElTableColumn prop="recipient" :label="tr('notify.recipient')" min-width="120" />
        <ElTableColumn :label="tr('notify.recipientKind')" width="90"
          ><template #default="{ row }">{{
            tr(row.recipientKind === 'CC' ? 'notify.cc' : 'notify.to')
          }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('notify.channelLabel')" width="110"
          ><template #default="{ row }">{{
            tr('notify.channel.' + row.channel)
          }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('notify.sendStatus')" min-width="130"
          ><template #default="{ row }">{{
            tr('notify.status.' + row.status)
          }}</template></ElTableColumn
        >
        <ElTableColumn prop="attempts" :label="tr('notify.attempts')" width="100" />
        <ElTableColumn
          prop="lastError"
          :label="tr('notify.reason')"
          min-width="220"
          show-overflow-tooltip
        />
        <ElTableColumn :label="tr('notify.sentAt')" min-width="170"
          ><template #default="{ row }">{{ formatDateTime(row.sentAt) }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('notify.readAt')" min-width="170"
          ><template #default="{ row }">{{ formatDateTime(row.readAt) }}</template></ElTableColumn
        >
        <ElTableColumn :label="tr('ui.equipment.text0145')" width="100" fixed="right"
          ><template #default="{ row }"
            ><ElButton
              v-if="
                editable &&
                row.channel !== 'APP' &&
                ['FAILED', 'MISSING_ADDRESS', 'DISABLED'].includes(row.status)
              "
              link
              type="primary"
              :disabled="busy"
              @click="retry(row as unknown as Delivery)"
              >{{ tr('notify.retry') }}</ElButton
            ></template
          ></ElTableColumn
        >
      </AppBusinessTable>
      <AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
    </template>
    <AppDialog v-model="retentionOpen" :title="tr('notify.retentionSettings')" width="560px">
      <ElAlert :title="tr('notify.retentionHelp')" type="info" :closable="false" />
      <ElForm
        class="notification-retention-form"
        label-position="top"
        :disabled="!retentionEditable || busy"
      >
        <ElFormItem :label="tr('notify.successDays')"
          ><ElInputNumber v-model="retention.successDays" :min="1" :max="3650" /><span
            class="notification-days-unit"
            >{{ tr('notify.unit.DAY') }}</span
          ></ElFormItem
        >
        <ElFormItem :label="tr('notify.exceptionDays')"
          ><ElInputNumber v-model="retention.exceptionDays" :min="1" :max="3650" /><span
            class="notification-days-unit"
            >{{ tr('notify.unit.DAY') }}</span
          ></ElFormItem
        >
        <p class="notification-help">{{ tr('notify.retentionSchedule') }}</p>
      </ElForm>
      <template #footer
        ><ElButton @click="retentionOpen = false">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton v-if="retentionEditable" type="primary" :loading="busy" @click="saveRetention">{{
          tr('ui.equipment.text0159')
        }}</ElButton></template
      >
    </AppDialog>
    <AppDialog
      v-model="editor"
      :title="tr('notify.policyEditor')"
      width="min(1440px, 96vw)"
      :busy="busy"
      :before-close="unsaved.permitLeave"
    >
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
      <ElForm label-position="top" :disabled="!editable || busy">
        <div class="notification-policy-header">
          <ElFormItem :label="tr('notify.policyName')" required
            ><ElInput v-model="draft.name" maxlength="150" /></ElFormItem
          ><ElFormItem :label="tr('notify.business')"
            ><ElSelect v-model="draft.businessKind" disabled
              ><ElOption
                v-for="k in reminderKinds"
                :key="k"
                :value="k"
                :label="tr('notify.kind.' + k)" /></ElSelect></ElFormItem
          ><ElFormItem :label="tr('notify.enabled')"
            ><ElSwitch v-model="draft.enabled"
          /></ElFormItem>
        </div>
        <ElAlert :title="tr('notify.ruleHelp')" type="info" :closable="false" />
        <AppBusinessTable :rows="draft.rules" :pagination="false" :fill="false">
          <ElTableColumn :label="tr('notify.sceneLabel')" min-width="220"
            ><template #default="{ row }"
              ><ElSelect v-model="row.scene"
                ><ElOption
                  v-for="s in scenes(draft.businessKind)"
                  :key="s"
                  :value="s"
                  :label="tr('notify.scene.' + s)" /></ElSelect></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('notify.duration')" width="140"
            ><template #default="{ row }"
              ><ElInputNumber
                v-model="row.value"
                :min="0"
                :max="525600"
                :precision="0"
                controls-position="right" /></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('notify.unitLabel')" width="110"
            ><template #default="{ row }"
              ><ElSelect v-model="row.unit"
                ><ElOption
                  v-for="u in ['MINUTE', 'HOUR', 'DAY']"
                  :key="u"
                  :value="u"
                  :label="tr('notify.unit.' + u)" /></ElSelect></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('notify.to')" min-width="230"
            ><template #default="{ row, $index }"
              ><ElFormItem :error="ruleErrors[$index]" required
                ><PagedChoiceField
                  v-model="row.recipients"
                  multiple
                  :options="people"
                  :load-rows="api.users"
                  :columns="userColumns"
                  :title="tr('notify.to')"
                  @select="remember" /></ElFormItem></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('notify.cc')" min-width="230"
            ><template #default="{ row }"
              ><PagedChoiceField
                v-model="row.cc"
                multiple
                :options="people"
                :load-rows="api.users"
                :columns="userColumns"
                :title="tr('notify.cc')"
                @select="remember" /></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('notify.enabled')" width="80"
            ><template #default="{ row }"><ElSwitch v-model="row.enabled" /></template
          ></ElTableColumn>
          <ElTableColumn :label="tr('ui.equipment.text0145')" width="140" fixed="right"
            ><template #default="{ row, $index }"
              ><ElButton
                link
                type="primary"
                :disabled="!editable"
                @click="addRule(row as unknown as ReminderRule)"
                >{{ tr('notify.copy') }}</ElButton
              ><ElButton
                link
                type="danger"
                :disabled="!editable"
                @click="draft.rules.splice($index, 1)"
                >{{ tr('ui.equipment.text0146') }}</ElButton
              ></template
            ></ElTableColumn
          >
        </AppBusinessTable>
        <ElButton v-if="editable" @click="addRule()">{{ tr('notify.addRule') }}</ElButton>
      </ElForm>
      <template #footer
        ><ElButton
          :disabled="busy"
          @click="
            async () => {
              if (await unsaved.permitLeave()) editor = false;
            }
          "
          >{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton v-if="editable" type="primary" :loading="busy" @click="savePolicy">{{
          tr('ui.equipment.text0159')
        }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.notification-page {
  height: calc(100dvh - 54px);
  min-height: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
  padding: 12px;
  overflow: auto;
}
.notification-actions,
.notification-default {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.notification-default {
  padding: 8px 12px;
  background: var(--el-bg-color);
  border-radius: 6px;
}
.notification-default :deep(.dialog-choice-input) {
  width: 230px;
}
.notification-default-badge {
  margin-left: 8px;
}
.notification-delivery-filters {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  background: var(--el-bg-color);
  padding: 12px;
  border-radius: 6px;
}
.notification-delivery-filters :deep(.el-date-editor) {
  max-width: 320px;
  flex: none;
}
.notification-delivery-filters :deep(.dialog-choice-input) {
  width: 180px;
}
.notification-retention-form {
  padding-top: 16px;
}
.notification-days-unit {
  margin-left: 8px;
}
.notification-help {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}
.notification-filter {
  width: 160px;
}
.notification-channel-form {
  display: grid;
  gap: 16px;
  width: 100%;
  max-width: 1280px;
  margin: 0 auto;
}
.notification-config-section {
  min-width: 0;
  padding: 20px 24px;
  border: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
  border-radius: 6px;
}
.notification-section-heading {
  margin-bottom: 16px;
}
.notification-section-heading h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.notification-section-heading p,
.notification-channel-summary p {
  margin: 6px 0 0;
  font-size: 12px;
  line-height: 1.6;
  color: var(--el-text-color-secondary);
}
.notification-channel-overview {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
}
.notification-channel-summary {
  padding: 0 24px;
  border-left: 1px solid var(--el-border-color-lighter);
}
.notification-channel-summary:first-child {
  padding-left: 0;
  border-left: 0;
}
.notification-channel-title {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 32px;
}
.notification-channel-title strong {
  font-size: 13px;
  font-weight: 500;
}
.notification-public-url {
  max-width: 600px;
  margin-bottom: 0;
}
.notification-smtp-fields {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 0 24px;
}
.notification-smtp-fields :deep(.el-input-number) {
  width: 100%;
}
.notification-channel-actions {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 12px;
  position: sticky;
  bottom: 0;
  z-index: 2;
  border-top: 1px solid var(--el-border-color-lighter);
  background: var(--el-bg-color);
}
.notification-channel-actions :deep(.el-button) {
  min-width: 88px;
}
.notification-email-test {
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}
.notification-test-fields {
  display: flex;
  align-items: flex-end;
  gap: 12px;
  flex-wrap: wrap;
}
.notification-email-test :deep(.el-form-item) {
  width: 340px;
  max-width: 100%;
  margin-bottom: 0;
}
.notification-policy-header {
  display: grid;
  grid-template-columns: 2fr 1fr 100px;
  gap: 24px;
}
:deep(.el-input-number),
:deep(.el-select) {
  max-width: 100%;
}
:deep(.el-table .el-form-item) {
  margin: 0;
}
@media (max-width: 850px) {
  .notification-smtp-fields {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .notification-config-section {
    padding: 16px;
  }
  .notification-policy-header {
    grid-template-columns: 1fr;
    gap: 0;
  }
}
@media (max-width: 600px) {
  .notification-smtp-fields,
  .notification-channel-overview {
    grid-template-columns: 1fr;
  }
  .notification-channel-summary,
  .notification-channel-summary:first-child {
    padding: 12px 0;
    border-left: 0;
    border-bottom: 1px solid var(--el-border-color-lighter);
  }
  .notification-channel-summary:last-child {
    border-bottom: 0;
    padding-bottom: 0;
  }
}
</style>
