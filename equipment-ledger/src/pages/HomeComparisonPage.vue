<script setup lang="ts">
import DepartmentText from '../components/common/DepartmentText.vue';
import { tr } from '../i18n';

import { computed, onMounted, onUnmounted, reactive, ref, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import {
  ElAlert,
  ElButton,
  ElTableColumn,
  ElSelect,
  ElOption,
  ElForm,
  ElCheckbox,
  ElCheckboxGroup,
} from 'element-plus';
import { useHomeAnalytics } from '../composables/useHomeAnalytics';
import AnalyticsWidget from '../components/common/AnalyticsWidget.vue';
import AnalyticsPeriodSelect from '../components/common/AnalyticsPeriodSelect.vue';
import { isAnalytics } from '../analytics';
import OverviewSection from '../components/common/OverviewSection.vue';
import HomeWidget from '../components/common/HomeWidget.vue';
import AppFormFooter from '../components/common/AppFormFooter.vue';
import { useOverviewCustomization } from '../composables/useOverviewCustomization';
import { overviewLabels, type OverviewBlock, type OverviewColumn } from '../homeOverview';
import AppIcon from '../components/common/AppIcon.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppListTabs from '../components/common/AppListTabs.vue';
import MaintenanceStatus from '../components/common/MaintenanceStatus.vue';
import { request, workInbox, type InboxItem } from '../api';
import { useAuthStore } from '../stores/auth';
import { useWorkInboxStore } from '../stores/workInbox';
import { formatDateTime } from '../utils/dateTime';

type WorkTab = 'pending' | 'active' | 'today';
type HomeRow = {
  id: string;
  code?: string;
  name?: string;
  equipmentName?: string;
  description?: string;
  status?: string;
  due?: string;
  time?: string;
  url?: string;
};
type BlockResult = { data: HomeRow[]; total: number; url?: string };
type Task = HomeRow & { kind?: string; source?: InboxItem };
const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const inbox = useWorkInboxStore();
const tab = ref<WorkTab>('pending');
const previewing = ref(false);
const loading = ref(false);
const loaded = ref(false);
const customization = useOverviewCustomization();
const {
  state,
  layout,
  editing,
  saving,
  initializing,
  form,
  rules,
  available,
  selectedType,
  dragId,
} = customization;
const analytics = useHomeAnalytics(layout, () => state.value?.selected.id);
const analyticsCatalog = analytics.catalog;
const globalPeriod = computed({
  get: () => layout.value.period || { preset: 'month' },
  set: (value) => {
    layout.value.period = value;
  },
});
const busy = computed(() => saving.value || initializing.value);
const workBlock = computed(() => layout.value.blocks.find((block) => block.type === 'work'));
const workRows = reactive<Record<WorkTab, { data: Task[]; total: number }>>({
  pending: { data: [], total: 0 },
  active: { data: [], total: 0 },
  today: { data: [], total: 0 },
});
const columns = computed<{ key: OverviewColumn; label: string }[]>(() => [
  { key: 'main', label: layout.value.layoutMode === 'analysis' ? '左栏' : '主栏' },
  { key: 'side', label: layout.value.layoutMode === 'analysis' ? '右栏' : '侧栏' },
]);
const configured = (column: OverviewColumn) =>
  layout.value.blocks.filter((block) => block.column === column);
const selectedMetrics = computed(() =>
  metrics.value.filter((metric) =>
    layout.value.metrics.includes(metric.key as 'pending' | 'active' | 'today' | 'notification'),
  ),
);
const sectionIndex = (block: OverviewBlock) =>
  layout.value.blocks.findIndex((value) => value.id === block.id);
const failures = reactive<Record<string, string>>({});
const pending = reactive<{ data: Task[]; total: number }>({ data: [], total: 0 });
const blocks = reactive<Record<string, BlockResult>>({});
const todayLabel = new Intl.DateTimeFormat('zh-CN', {
  month: 'long',
  day: 'numeric',
  weekday: 'long',
}).format(new Date());
const tabs = computed(() => [
  {
    key: 'pending',
    get label() { return tr('ui.equipment.text1700'); },
    count: failures['work-pending'] ? undefined : workRows.pending.total,
  },
  {
    key: 'active',
    get label() { return tr('ui.equipment.text1701'); },
    count: failures['work-active'] ? undefined : workRows.active.total,
  },
  {
    key: 'today',
    get label() { return tr('ui.equipment.text1702'); },
    count: failures['work-today'] ? undefined : workRows.today.total,
  },
]);
const tasks = computed(() => workRows[tab.value].data);
const taskTotal = computed(() => workRows[tab.value].total);
const metrics = computed(() => [
  {
    key: 'pending',
    emphasis: 'primary',
    get label() { return tr('ui.equipment.text1700'); },
    value: failures.pending ? undefined : loaded.value ? pending.total : undefined,
    icon: 'Todo',
    note: '需要我接单、处理或验收',
  },
  {
    key: 'active',
    emphasis: 'standard',
    get label() { return tr('ui.equipment.text1701'); },
    value: blocks['metric-active']?.total,
    icon: 'Tool',
    note: '跟进当前进行中的工作',
  },
  {
    key: 'today',
    emphasis: 'standard',
    get label() { return tr('ui.equipment.text1702'); },
    value: blocks['metric-today']?.total,
    icon: 'Clock',
    note: '查看今天计划到期的任务',
  },
  {
    key: 'notification',
    emphasis: 'standard',
    get label() { return tr('ui.equipment.text1029'); },
    value: inbox.error ? undefined : loaded.value ? inbox.summary.counts.unread : undefined,
    icon: 'Bell',
    note: '需要我知晓的工作消息',
  },
]);
const recent = computed(() => blocks.recent?.data || []);
const shortcuts = computed(() => blocks.shortcuts?.data || []);
const anomalies = computed(() => blocks.anomalies?.data || []);
const shortcutIcons: Record<string, string> = {
  equipment: 'Equipment',
  repair: 'Tool',
  anomaly: 'Warning',
  repairPlan: 'Document',
  maintenanceTask: 'Clock',
  inspectionDailyTask: 'Check',
  inventoryOutbound: 'Warehouse',
  org: 'Organization',
  user: 'User',
  role: 'Permissions',
};
const modules = computed(() => {
  const groups = new Map<string, number>();
  for (const item of inbox.summary.kinds) {
    if (!item.count) continue;
    const label = inbox.moduleName(item.module);
    groups.set(label, (groups.get(label) || 0) + item.count);
  }
  return [...groups].map(([label, count]) => ({ label, count }));
});
async function loadBlock(type: string, section?: OverviewBlock) {
  const key = section ? type : `metric-${type}`;
  try {
    const result = await request<BlockResult>('/home/blocks', {
      method: 'POST',
      body: JSON.stringify({
        id: `home2-${type}`,
        type,
        title: type,
        width: 12,
        limit: 5,
        scope: 'personal',
        ...section,
        ...(type === 'anomalies' ? { status: '待处理' } : {}),
      }),
    });
    blocks[key] = result;
  } catch (error) {
    delete blocks[key];
    failures[key] = error instanceof Error ? error.message : tr('ui.equipment.text0160');
  }
}
async function loadWork() {
  const section = workBlock.value;
  if (!section) return;
  await Promise.allSettled(
    (['pending', 'active', 'today'] as WorkTab[]).map(async (key) => {
      try {
        if (key === 'pending') {
          const result = await workInbox({
            channel: 'todo',
            state: 'pending',
            module: section.module || '',
            page: 1,
            size: section.limit,
          });
          workRows[key] = {
            total: result.total,
            data: result.data.map((row) => ({
              id: row.id,
              code: row.orderNo,
              equipmentName: row.equipmentName,
              name: row.title,
              status: row.status,
              due: row.due,
              time: row.time,
              kind: row.kind,
              source: row,
            })),
          };
        } else {
          const result = await request<BlockResult>(
            '/work-center/tasks?' +
              new URLSearchParams({
                state: key,
                module: section.module || '',
                page: '1',
                size: String(section.limit),
              }),
          );
          workRows[key] = { data: result.data, total: result.total };
        }
      } catch (error) {
        workRows[key] = { data: [], total: 0 };
        failures[`work-${key}`] = error instanceof Error ? error.message : tr('ui.equipment.text1703');
      }
    }),
  );
}
let refreshAgain = false;
async function refresh() {
  void analytics.refresh();
  if (loading.value) {
    refreshAgain = true;
    return;
  }
  loading.value = true;
  for (const key of Object.keys(failures)) delete failures[key];
  try {
    const jobs: Promise<unknown>[] = [
      inbox.refresh(),
      loadBlock('active'),
      loadBlock('today'),
      loadWork(),
    ];
    for (const section of layout.value.blocks) {
      if (['anomalies', 'shortcuts', 'recent'].includes(section.type))
        jobs.push(loadBlock(section.type, section));
    }
    await Promise.allSettled(jobs);
    if (inbox.error) failures.pending = inbox.error;
    else pending.total = inbox.summary.counts.pending;
    loaded.value = true;
  } finally {
    loading.value = false;
    if (refreshAgain) {
      refreshAgain = false;
      void refresh();
    }
  }
}
async function loadTemplate(id?: string) {
  let previous: { returnLayout?: typeof layout.value; templateId?: string } | undefined;
  if (id === undefined && route.query.analysis) {
    try {
      previous =
        JSON.parse(
          sessionStorage.getItem('eam-analysis:' + String(route.query.analysis)) || 'null',
        ) || undefined;
    } catch {
      /* Expired local context. */
    }
  }
  if (await customization.load(id ?? previous?.templateId)) {
    await analytics.initialize();
    if (previous?.returnLayout)
      layout.value = {
        ...previous.returnLayout,
        blocks: previous.returnLayout.blocks.filter(
          (b) =>
            state.value?.catalog.includes(b.type) &&
            (b.scope !== 'team' || auth.can('workspace', '团队工作查看')),
        ),
      };
    if (id !== undefined && route.query.analysis) void router.replace({ name: 'home2' });
    await refresh();
  }
}
async function saveLayout() {
  if (await customization.save()) await refresh();
}
async function cancelLayout() {
  await customization.cancel();
  if (!editing.value) await refresh();
}
watch(editing, (value) => {
  if (!value) previewing.value = false;
});
function changed() {
  if (!editing.value) void refresh();
}
watch(
  () =>
    JSON.stringify(
      layout.value.blocks.map(({ id, type, limit, module, scope }) => ({
        id,
        type,
        limit,
        module,
        scope,
      })),
    ),
  () => {
    if (editing.value) void refresh();
  },
);
function center(next: string = tab.value) {
  void router.push({
    name: 'workspace-inbox',
    query: { tab: next === 'notification' ? 'notification' : next },
  });
}
function chooseMetric(key: string) {
  if (key === 'notification') center(key);
  else if (workBlock.value) {
    tab.value = key as WorkTab;
    document
      .querySelector('[data-home-work]')
      ?.scrollIntoView({ block: 'nearest', behavior: 'smooth' });
  } else center(key);
}
function openTask(row: Task, action = false) {
  if (row.source)
    void router.push(
      inbox.destination(row.source, { actionable: action, returnTo: '/workspace?tab=pending' }),
    );
  else if (row.url) void router.push(row.url);
}
function taskName(row: Task) {
  const name = row.equipmentName || row.name || '';
  return row.code && name.startsWith(row.code)
    ? name.slice(row.code.length).replace(/^[\s·:：-]+/, '')
    : name;
}
function taskKind(row: Task) {
  const value = row.kind || '';
  const moduleName = row.source ? inbox.moduleName(row.source.module) : '';
  return moduleName && value.startsWith(moduleName) ? value.slice(moduleName.length) : value;
}
function taskTime(row: Task) {
  const value = tab.value === 'pending' ? row.time : row.due || row.time;
  return value ? formatDateTime(value).slice(0, 16) : '';
}
function openLink(row: HomeRow) {
  if (row.url) void router.push(row.url);
}
onMounted(() => {
  void loadTemplate();
  window.addEventListener('work-inbox-changed', changed);
  window.addEventListener('repair-inbox-changed', changed);
});
onUnmounted(() => {
  window.removeEventListener('work-inbox-changed', changed);
  window.removeEventListener('repair-inbox-changed', changed);
});
</script>

<template>
  <section class="content overview-page" :aria-busy="loading || busy">
    <ElForm
      ref="form"
      class="overview-scroll"
      :model="layout"
      :rules="rules"
      label-position="top"
      scroll-to-error
      :scroll-into-view-options="{ block: 'center' }"
      @submit.prevent="saveLayout"
    >
      <header class="overview-heading">
        <div>
          <div class="heading-eyebrow">{{ todayLabel }}</div>
          <h1>{{ auth.currentUser }}{{ tr('ui.equipment.text1704') }}</h1>
          <p><DepartmentText :value="auth.currentDepartment" :department-id="auth.user?.orgId" /></p>
        </div>
        <div class="heading-actions">
          <ElSelect
            v-if="state"
            class="template-select"
            :model-value="state.selected.id"
            :disabled="editing || busy || loading"
            :aria-label="tr('ui.equipment.text1705')"
            @update:model-value="loadTemplate(String($event))"
            ><ElOption
              v-for="item in state.templates"
              :key="item.id"
              :value="item.id"
              :label="item.name"
          /></ElSelect>
          <ElButton v-if="!editing" :disabled="busy || loading" @click="refresh"
            ><AppIcon name="Refresh" />{{ tr('ui.equipment.text0424') }}</ElButton
          >
          <ElButton
            v-if="!editing"
            :disabled="!state || busy || loading"
            @click="customization.begin"
            ><AppIcon name="Settings" />{{ tr('ui.equipment.text1706') }}</ElButton
          >
          <ElButton v-if="editing" :disabled="busy" @click="previewing = !previewing">{{
            previewing ? tr('ui.equipment.text1707') : tr('ui.equipment.text1708')
          }}</ElButton>
          <ElButton v-if="!editing" type="primary" @click="center('pending')"
            >{{ tr('ui.equipment.text1709') }}<AppIcon name="Right"
          /></ElButton>
        </div>
      </header>
      <ElAlert
        v-if="customization.error.value"
        type="error"
        :title="customization.error.value"
        :closable="false"
        show-icon
      >
        <ElButton type="primary" link :disabled="busy" @click="loadTemplate(state?.selected.id)"
          >{{ tr('ui.equipment.text1710') }}</ElButton
        >
      </ElAlert>
      <ElAlert
        v-if="state?.updateAvailable"
        type="info"
        :title="tr('ui.equipment.text1711')"
        :closable="false"
        show-icon
      />
      <div v-if="editing && !previewing" class="customize-toolbar">
        <div class="block-library">
          <ElSelect
            v-model="selectedType"
            :placeholder="tr('ui.equipment.text1712')"
            :disabled="busy"
            :aria-label="tr('ui.equipment.text1713')"
            ><ElOption
              v-for="type in available"
              :key="type"
              :value="type"
              :label="overviewLabels[type] || type" /></ElSelect
          ><ElButton
            :disabled="!selectedType || layout.blocks.length >= 12 || busy"
            @click="
              customization.add(analyticsCatalog?.data.find((m) => m.id === selectedType.slice(10)))
            "
            ><AppIcon name="Plus" />{{ tr('ui.equipment.text1564') }}</ElButton
          ><span>{{ tr('ui.equipment.text1714') }}</span>
        </div>
        <div class="metric-options">
          <span>{{ tr('ui.equipment.text1715') }}</span
          ><ElSelect
            v-model="layout.layoutMode"
            :aria-label="tr('ui.equipment.text1716')"
            :disabled="busy"
            style="width: 150px"
            ><ElOption value="work" :label="tr('ui.equipment.text1559')" /><ElOption
              value="analysis"
              :label="tr('ui.equipment.text1560')" /></ElSelect
          ><span>{{ tr('ui.equipment.text1717') }}</span
          ><ElSelect
            v-model="analytics.presetIndex.value"
            :placeholder="tr('ui.equipment.text1718')"
            :aria-label="tr('ui.equipment.text1719')"
            style="width: 190px"
            :disabled="busy"
            ><ElOption
              v-for="(p, i) in analytics.presets.value"
              :key="p.name"
              :value="i"
              :label="p.name" /></ElSelect
          ><ElButton
            :disabled="analytics.presetIndex.value === undefined || busy"
            @click="analytics.applyPreset"
            >{{ tr('ui.equipment.text1562') }}</ElButton
          >
        </div>
        <div class="metric-options">
          <span>{{ tr('ui.equipment.text1720') }}</span
          ><ElCheckboxGroup v-model="layout.metrics" :disabled="busy"
            ><ElCheckbox v-for="metric in metrics" :key="metric.key" :value="metric.key">{{
              metric.label
            }}</ElCheckbox></ElCheckboxGroup
          >
        </div>
      </div>
      <div
        v-if="analyticsCatalog?.data.length && (editing || analytics.analytical.value.length)"
        class="analytics-toolbar"
      >
        <span>{{ tr('ui.equipment.text1298') }}</span><AnalyticsPeriodSelect v-model="globalPeriod" :disabled="busy" />
        <template v-if="analyticsCatalog.teamAllowed"
          ><ElSelect
            v-model="layout.companyIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="tr('ui.equipment.text1721')"
            :aria-label="tr('ui.equipment.text1722')"
            :disabled="busy"
            ><ElOption
              v-for="o in analyticsCatalog.organizations.filter((o) => o.org_type === '公司')"
              :key="o.id"
              :value="o.id"
              :label="o.name" /></ElSelect
          ><ElSelect
            v-model="layout.departmentIds"
            multiple
            filterable
            clearable
            collapse-tags
            collapse-tags-tooltip
            :placeholder="tr('ui.equipment.text1723')"
            :aria-label="tr('ui.equipment.text1724')"
            :disabled="busy"
            ><ElOption
              v-for="o in analyticsCatalog.organizations.filter((o) => o.org_type !== '公司')"
              :key="o.id"
              :value="o.id"
              :label="o.name" /></ElSelect
        ></template>
        <span v-if="analytics.asOf.value" class="analytics-updated"
          >{{ tr('ui.equipment.text1725') }} {{ analytics.asOf.value.replace('T', ' ').slice(0, 19) }} {{ tr('ui.equipment.text1726') }}</span
        >
      </div>
      <div
        v-if="selectedMetrics.length"
        class="summary-grid"
        :style="{ '--metric-count': selectedMetrics.length }"
      >
        <ElButton
          v-for="metric in selectedMetrics"
          :key="metric.key"
          class="summary-card"
          :data-emphasis="metric.emphasis"
          :disabled="editing"
          @click="chooseMetric(metric.key)"
          ><div class="summary-content">
            <div class="summary-label">
              <span>{{ metric.label }}</span
              ><span class="summary-icon" aria-hidden="true"><AppIcon :name="metric.icon" /></span>
            </div>
            <strong>{{ metric.value ?? (loading || initializing ? '…' : tr('ui.equipment.text1727')) }}</strong
            ><span class="summary-note">{{ metric.note }}</span>
          </div></ElButton
        >
      </div>
      <div
        class="overview-columns"
        :class="{ 'analysis-columns': layout.layoutMode === 'analysis' }"
      >
        <div
          v-for="column in columns"
          :key="column.key"
          :class="column.key === 'main' ? 'primary-column' : 'secondary-column'"
          class="overview-column"
          @dragover.prevent
          @drop.self.prevent="customization.drop(dragId, column.key)"
        >
          <div v-if="editing && !previewing" class="column-label">{{ column.label }}</div>
          <OverviewSection
            v-for="(section, index) in configured(column.key)"
            :key="section.id"
            :block="section"
            :organizations="analyticsCatalog?.organizations"
            :metric="analyticsCatalog?.data.find((m) => m.id === section.type.slice(10))"
            :index="sectionIndex(section)"
            :editing="editing && !previewing"
            :busy="busy"
            :first="index === 0"
            :last="index === configured(column.key).length - 1"
            :removable="layout.blocks.length > 1"
            :team-allowed="auth.can('workspace', '团队工作查看')"
            :class="{ 'work-panel': section.type === 'work' }"
            :data-home-work="section.type === 'work' ? true : undefined"
            @update="customization.update"
            @remove="customization.remove"
            @move="customization.move"
            @start="dragId = $event"
            @end="dragId = ''"
            @drop="customization.drop(dragId, column.key, $event)"
          >
            <template #action>
              <ElButton v-if="section.type === 'work'" type="primary" link @click="center()"
                >{{ tr('ui.equipment.text0892') }} {{ taskTotal }} {{ tr('ui.equipment.text0394') }}<AppIcon name="Right"
              /></ElButton>
              <ElButton
                v-else-if="section.type === 'anomalies' && auth.can('anomaly')"
                type="primary"
                link
                @click="router.push('/anomalies')"
                >{{ tr('ui.equipment.text0892') }}<AppIcon name="Right"
              /></ElButton>
            </template>
            <AnalyticsWidget
              v-if="isAnalytics(section)"
              :metric="analyticsCatalog?.data.find((m) => m.id === section.type.slice(10))"
              :result="analytics.results.value[section.id]"
              :top-n="section.topN || 10"
              :interactive="!editing"
              :loading="analytics.loading.value"
              :error="analytics.error.value"
              @retry="analytics.refresh"
              @drill="!editing && analytics.drill(section, $event)"
              @currency="analytics.currency(section, $event)"
            />
            <template v-else-if="section.type === 'work'">
              <AppListTabs :active="tab" :items="tabs" @select="tab = $event as WorkTab" />
              <ElAlert
                v-if="failures['work-' + tab]"
                type="error"
                :title="failures['work-' + tab]"
                :closable="false"
                show-icon
              />
              <AppBusinessTable
                v-else
                :rows="tasks"
                :loading="loading"
                :pagination="false"
                :fill="false"
                :framed="false"
                border-mode="none"
                row-key="id"
              >
                <!-- @vue-generic {Task} -->
                <ElTableColumn :label="tr('ui.equipment.text0137')" prop="code" :width="148" show-overflow-tooltip
                  ><template #default="{ row }"
                    ><ElButton type="primary" link :disabled="editing" @click="openTask(row)">{{
                      row.code
                    }}</ElButton></template
                  ></ElTableColumn
                >
                <!-- @vue-generic {Task} -->
                <ElTableColumn :label="tr('ui.equipment.text1728')" :min-width="150" show-overflow-tooltip
                  ><template #default="{ row }">{{ taskName(row) }}</template></ElTableColumn
                >
                <!-- @vue-generic {Task} -->
                <ElTableColumn
                  :label="tab === 'pending' ? tr('ui.equipment.text1729') : tr('ui.equipment.text0170')"
                  :width="100"
                  align="center"
                  ><template #default="{ row }"
                    ><MaintenanceStatus
                      v-if="tab === 'pending'"
                      :status="taskKind(row)"
                      :title="row.kind" /><MaintenanceStatus
                      v-else
                      :status="row.status || ''" /></template
                ></ElTableColumn>
                <!-- @vue-generic {Task} -->
                <ElTableColumn
                  :label="tab === 'pending' ? tr('ui.equipment.text1730') : tr('ui.equipment.text1731')"
                  :width="138"
                  show-overflow-tooltip
                  ><template #default="{ row }">{{ taskTime(row) }}</template></ElTableColumn
                >
                <!-- @vue-generic {Task} -->
                <ElTableColumn :label="tr('ui.equipment.text0145')" :width="156" fixed="right"
                  ><template #default="{ row }"
                    ><ElButton
                      type="primary"
                      link
                      :disabled="editing"
                      @click="openTask(row, true)"
                      >{{ row.source ? inbox.actionName(row.source) : tr('ui.equipment.text0206') }}</ElButton
                    ></template
                  ></ElTableColumn
                >
                <template #empty
                  ><p class="compact-empty">
                    {{ loading ? tr('ui.equipment.text1732') : tr('ui.equipment.text1733') }}
                  </p></template
                >
              </AppBusinessTable>
              <div v-if="tab === 'pending' && modules.length" class="work-distribution">
                <span>{{ tr('ui.equipment.text1734') }}</span
                ><span v-for="group in modules" :key="group.label"
                  >{{ group.label }} <b>{{ group.count }}</b></span
                >
              </div>
            </template>
            <template v-else-if="section.type === 'recent'">
              <p v-if="failures.recent" class="section-error">{{ failures.recent }}</p>
              <div v-else-if="recent.length" class="recent-links">
                <ElButton
                  v-for="row in recent.slice(0, section.limit)"
                  :key="row.id"
                  :disabled="editing"
                  @click="openLink(row)"
                  ><AppIcon :name="shortcutIcons[row.id] || 'Document'" /><span>{{ row.name }}</span
                  ><AppIcon name="Right"
                /></ElButton>
              </div>
              <p v-else class="compact-empty">{{ tr('ui.equipment.text1735') }}</p>
            </template>
            <template v-else-if="section.type === 'anomalies'">
              <div class="attention-heading">
                <span>{{ tr('ui.equipment.text1736') }}</span
                ><strong>{{
                  failures.anomalies ? tr('ui.equipment.text1727') : (blocks.anomalies?.total ?? '…')
                }}</strong>
              </div>
              <p v-if="failures.anomalies" class="section-error">{{ failures.anomalies }}</p>
              <div v-else-if="anomalies.length" class="attention-list">
                <ElButton
                  v-for="row in anomalies.slice(0, section.limit)"
                  :key="row.id"
                  :title="row.description || row.equipmentName"
                  :disabled="editing"
                  @click="router.push('/anomalies/' + row.id)"
                  ><span class="attention-dot"></span
                  ><span>{{ row.equipmentName || row.name || row.code }}</span
                  ><AppIcon name="Right"
                /></ElButton>
              </div>
              <p v-else class="attention-empty"><AppIcon name="Check" />{{ tr('ui.equipment.text1737') }}</p>
            </template>
            <template v-else-if="section.type === 'shortcuts'">
              <p v-if="failures.shortcuts" class="section-error">{{ failures.shortcuts }}</p>
              <div v-else-if="shortcuts.length" class="shortcut-grid">
                <ElButton
                  v-for="row in shortcuts.slice(0, section.limit)"
                  :key="row.id"
                  :disabled="editing"
                  @click="openLink(row)"
                  ><span class="shortcut-icon"
                    ><AppIcon :name="shortcutIcons[row.id] || 'Document'" /></span
                  ><span>{{ row.name }}</span></ElButton
                >
              </div>
              <p v-else class="compact-empty">{{ tr('ui.equipment.text1738') }}</p>
            </template>
            <HomeWidget v-else :block="section" :editing="editing" compact />
          </OverviewSection>
          <div
            v-if="editing && !previewing"
            class="column-drop-zone"
            @dragover.prevent
            @drop.stop.prevent="customization.drop(dragId, column.key)"
          >
            {{ tr('ui.equipment.text1739') }}{{ column.label }}{{ tr('ui.equipment.text1740') }}
          </div>
        </div>
      </div>
      <p v-if="!initializing && !layout.blocks.length" class="compact-empty">
        {{ tr('ui.equipment.text1741') }}
      </p>
    </ElForm>
    <AppFormFooter v-if="editing"
      ><ElButton :disabled="busy" @click="cancelLayout">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton :disabled="busy" @click="customization.restore">{{ tr('ui.equipment.text1742') }}</ElButton
      ><ElButton
        type="primary"
        :loading="saving"
        :disabled="initializing || loading"
        @click="saveLayout"
        >{{ tr('ui.equipment.text0159') }}</ElButton
      ></AppFormFooter
    >
  </section>
</template>
<style scoped>
.analytics-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding: 12px 14px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
  margin-bottom: 16px;
}
.analytics-toolbar > .el-select {
  width: 170px;
}
.analytics-updated {
  margin-left: auto;
  font-size: 12px;
}
.overview-columns.analysis-columns {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}
.overview-page {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 54px);
  min-height: 0;
  color: var(--text);
  font-size: 13px;
  container-type: inline-size;
}
.overview-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}
.template-select {
  width: 170px;
}
.customize-toolbar {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 6px;
  padding: 12px 16px;
  margin-bottom: 16px;
}
.block-library {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px;
}
.block-library > .el-select {
  width: 180px;
}
.metric-options {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  margin-top: 8px;
}
.metric-options :deep(.el-checkbox) {
  margin-right: 16px;
}
.column-label {
  color: var(--brand);
  font-weight: 700;
  padding: 0 4px;
}
.column-drop-zone {
  border: 1px dashed var(--brand);
  border-radius: 6px;
  padding: 14px;
  text-align: center;
  color: var(--brand);
  background: var(--brand-soft);
}
.overview-column {
  min-height: 48px;
}
.overview-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 20px;
}
.heading-eyebrow {
  color: var(--brand);
  font-size: 13px;
  margin-bottom: 6px;
}
h1 {
  font-size: 20px;
  line-height: 1.5;
  margin: 0 0 5px;
  font-weight: 600;
}
.overview-heading p {
  margin: 0;
}
.heading-actions {
  flex-wrap: wrap;
  justify-content: flex-end;
  max-width: 60%;
  display: flex;
  align-items: center;
  gap: 8px;
  flex-shrink: 0;
}
.heading-actions .el-button {
  margin: 0;
}
.heading-actions :deep(.app-icon) {
  margin: 0 5px;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(var(--metric-count, 4), minmax(0, 1fr));
  gap: 14px;
  margin-bottom: 18px;
}
.summary-card {
  --summary-accent: var(--brand);
  --summary-tint: color-mix(in srgb, var(--brand) 3%, white);
  --summary-border: color-mix(in srgb, var(--brand) 12%, white);
  position: relative;
  width: 100%;
  min-width: 0;
  height: auto;
  margin: 0;
  padding: 18px;
  border: 1px solid var(--summary-border);
  border-radius: 10px;
  color: var(--text);
  background: linear-gradient(120deg, var(--summary-tint), var(--surface) 90%);
  box-shadow: 0 2px 6px #182c4a04;
  white-space: normal;
  transition:
    border-color 160ms ease,
    box-shadow 160ms ease,
    transform 160ms ease;
}
.summary-card[data-emphasis='primary'] {
  --summary-tint: color-mix(in srgb, var(--brand) 11%, white);
  --summary-border: color-mix(in srgb, var(--brand) 26%, white);
}
.summary-card:not(:disabled):hover {
  border-color: color-mix(in srgb, var(--summary-accent) 45%, white);
  box-shadow: 0 6px 18px color-mix(in srgb, var(--summary-accent) 12%, transparent);
  transform: translateY(-2px);
  color: var(--text);
}
.summary-card :deep(> span) {
  width: 100%;
  min-width: 0;
}
.summary-content {
  display: flex;
  flex-direction: column;
  gap: 9px;
  width: 100%;
  min-width: 0;
  text-align: left;
}
.summary-label {
  min-height: 20px;
  padding-right: 44px;
  line-height: 20px;
  font-size: 13px;
  font-weight: 600;
}
.summary-icon {
  position: absolute;
  top: 16px;
  right: 16px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 10px;
  background: color-mix(in srgb, var(--summary-accent) 11%, white);
  color: var(--summary-accent);
}
.summary-icon :deep(.app-icon) {
  --app-icon-size: 21px;
}
.summary-content strong {
  font-size: 34px;
  line-height: 38px;
  color: var(--summary-accent);
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  letter-spacing: -0.5px;
}
.summary-note {
  font-size: 13px;
  font-weight: 400;
  line-height: 20px;
  color: var(--text-secondary, #42526b);
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
@media (prefers-reduced-motion: reduce) {
  .summary-card {
    transition: none;
  }
  .summary-card:not(:disabled):hover {
    transform: none;
  }
}
.overview-columns {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 18px;
  align-items: start;
}
.primary-column,
.secondary-column {
  display: flex;
  flex-direction: column;
  gap: 18px;
  min-width: 0;
}
.work-panel :deep(.app-list-tabs) {
  margin: 0 16px;
}
.work-panel :deep(.business-table-shell) {
  border-radius: 0;
}
.compact-empty {
  margin: 0;
  padding: 20px 16px;
  color: var(--text);
}
.work-distribution {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 16px;
  padding: 12px 16px;
  border-top: 1px solid var(--line);
  font-size: 13px;
}
.work-distribution b {
  color: var(--brand);
  margin-left: 3px;
}
.recent-links {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  padding: 0 16px 16px;
}
.recent-links .el-button {
  margin: 0;
  gap: 8px;
}
.recent-links :deep(.el-button > span) {
  gap: 8px;
}
.shortcut-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px;
  padding: 0 16px 16px;
}
.shortcut-grid .el-button {
  height: 54px;
  min-width: 0;
  width: 100%;
  margin: 0;
  padding: 8px;
  justify-content: flex-start;
  background: var(--canvas);
  border: 1px solid transparent;
}
.shortcut-grid .el-button:hover {
  border-color: var(--brand);
}
.shortcut-grid :deep(.el-button > span) {
  min-width: 0;
  width: 100%;
  gap: 8px;
}
.shortcut-grid :deep(.el-button > span > span:last-child) {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
}
.shortcut-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 30px;
  height: 30px;
  background: var(--surface);
  border-radius: 6px;
  color: var(--brand);
}
.shortcut-icon .app-icon {
  font-size: 18px;
}
.attention-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin: 0 16px;
  padding: 10px 12px;
  border-radius: 4px;
  background: var(--brand-soft);
}
.attention-heading strong {
  font-size: 20px;
  color: var(--brand);
}
.attention-empty {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0;
  padding: 16px;
}
.attention-empty .app-icon {
  color: #16845b;
}
.attention-list {
  padding: 4px 16px 12px;
}
.attention-list .el-button {
  display: flex;
  width: 100%;
  min-width: 0;
  height: 36px;
  margin: 0;
  padding: 0;
  border: 0;
}
.attention-list :deep(.el-button > span) {
  width: 100%;
  min-width: 0;
  justify-content: flex-start;
  gap: 8px;
}
.attention-list :deep(.el-button > span > span:nth-child(2)) {
  min-width: 0;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  text-align: left;
}
.attention-dot {
  width: 6px;
  height: 6px;
  flex-shrink: 0;
  background: #e69a23;
  border-radius: 50%;
}
.section-error {
  margin: 0;
  padding: 12px 16px;
  color: var(--danger);
}
@container (max-width: 980px) {
  .overview-columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .secondary-column {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
}
@media (max-width: 760px) {
  .overview-scroll {
    padding: 16px 12px;
  }
  .heading-actions {
    max-width: 100%;
    justify-content: flex-start;
  }
  .overview-heading {
    align-items: flex-start;
    flex-direction: column;
  }
  .summary-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .secondary-column {
    display: flex;
  }
}
@container (max-width: 900px) {
  .overview-columns.analysis-columns {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
