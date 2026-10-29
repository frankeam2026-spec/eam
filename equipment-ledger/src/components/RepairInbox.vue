<script setup lang="ts">import { tr } from '../i18n';
import { normalizePageSize } from '../utils/pagination';

import AppListToolbar from './common/AppListToolbar.vue';
import AppListTabs from './common/AppListTabs.vue';
import { ElTableColumn } from 'element-plus';
import { ElButton, ElCheckboxGroup, ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppPagination from './common/AppPagination.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
const projectedCounts = ref<Record<string, number>>({});
import { workInbox, request, type InboxItem } from '../api';
import { formatDateTime } from '../utils/dateTime';
import { useWorkInboxStore } from '../stores/workInbox';
const route = useRoute(),
  router = useRouter(),
  inbox = useWorkInboxStore();
const rows = ref<InboxItem[]>([]),
  tab = ref(String(route.query.tab || 'pending')),
  module = ref(
    [
      'repair',
      'anomaly',
      'repairPlan',
      'maintenance',
      'inspection',
      'inventory',
      'inventoryTransfer',
      'inventoryReturn',
      'inventoryOutbound',
    ].includes(String(route.query.module))
      ? String(route.query.module)
      : '',
  ),
  kind = ref(String(route.query.kind || '')),
  search = ref(String(route.query.q || '')),
  query = ref(String(route.query.q || '')),
  readState = ref(route.query.read === 'unread' ? 'unread' : 'all'),
  page = ref(Math.max(1, Number(route.query.page) || 1)),
  size = ref(normalizePageSize(route.query.size)),
  total = ref(0),
  loading = ref(false),
  error = ref('');
const columnsOpen = ref(false),
  columns = ref(['orderNo', 'planName', 'shiftName', 'equipmentName', 'kind', 'status', 'owner', 'due', 'time']);
const labels: Record<string, string> = {
  get title(){return tr('ui.common.dynamic51');},
  module: '模块',
  orderNo: '任务编号',
  equipmentName: '设备名称',
  get planName(){return tr('ui.equipment.text0314');},
  get shiftName(){return tr('daily.slots');},
  kind: '待办事项',
  status: '业务状态',
  time: '到达时间',
  owner: '责任人',
  due: '计划时间',
};
const projected = computed(() => ['active', 'created', 'team', 'today'].includes(tab.value));
const availableLabels = computed(() => Object.fromEntries(
  Object.entries(labels).filter(([key]) =>
    !['planName', 'shiftName'].includes(key) || (module.value === 'inspection' && !projected.value),
  ),
));
const displayColumns = computed(() =>
  columns.value.filter((c) => c in availableLabels.value && (projected.value || !['owner', 'due'].includes(c))),
);
const moduleGroups = computed(() =>
  projected.value
    ? ['repair', 'maintenance', 'inspection'].map((key) => ({
        key,
        name: inbox.moduleName(key),
        count: projectedCounts.value[key] || 0,
        items: [],
      }))
    : ['repair', 'maintenance', 'inspection', 'inventory']
        .map((key) => ({
          key,
          name: inbox.moduleName(key),
          items: inbox.summary.kinds
            .filter(
              (item) =>
                item.module === key ||
                (key === 'repair' && ['anomaly', 'repairPlan'].includes(item.module || '')) ||
                (key === 'inventory' &&
                  ['inventoryTransfer', 'inventoryReturn', 'inventoryOutbound'].includes(
                    item.module || '',
                  )),
            )
            .map((item) => ({
              ...item,
              count: tab.value === 'completed' ? item.completed : item.count,
            }))
            .filter((item) => item.count > 0),
        }))
        .map((group) => ({
          ...group,
          count: group.items.reduce((sum, item) => sum + Number(item.count), 0),
        })),
);
const currentContext = computed(() =>
  projected.value
    ? { today: '今日任务', active: '我正在做', created: '我发起的事项', team: '团队工作' }[
        tab.value
      ] || '工作事项'
    : tab.value === 'notification'
      ? readState.value === 'unread'
        ? '未读提醒'
        : '全部提醒'
      : kind.value ||
        (module.value
          ? inbox.moduleName(module.value) + (tab.value === 'completed' ? '已办' : '待办')
          : tab.value === 'completed'
            ? '全部已办'
            : '全部待办'),
);
function selectCategory(nextModule = '', nextKind = '') {
  module.value = nextModule;
  kind.value = nextKind;
}
function resetFilters() {
  search.value = '';
  query.value = '';
  readState.value = 'all';
  selectCategory();
  page.value = 1;
  load();
}
let revision = 0;
async function load() {
  const sequence = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const data = projected.value
      ? await request<{ data: InboxItem[]; total: number; moduleCounts: Record<string, number> }>(
          '/work-center/tasks?' +
            new URLSearchParams({
              state: tab.value,
              status: String(route.query.status || ''),
              companyId: String(route.query.companyId || ''),
              departmentId: String(route.query.departmentId || ''),
              module: module.value,
              search: query.value,
              page: String(page.value),
              size: String(size.value),
            }),
        )
      : await workInbox({
          channel: tab.value === 'notification' ? 'notification' : 'todo',
          state: tab.value === 'notification' ? readState.value : tab.value,
          module: module.value,
          kind: kind.value,
          search: query.value,
          page: page.value,
          size: size.value,
        });
    if (sequence === revision) {
      if ('moduleCounts' in data)
        projectedCounts.value = (data as { moduleCounts: Record<string, number> }).moduleCounts;
      rows.value = data.data;
      total.value = data.total;
      const lastPage = Math.max(1, Math.ceil(data.total / size.value));
      if (page.value > lastPage) page.value = lastPage;
    }
  } catch (e) {
    if (sequence === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
  } finally {
    if (sequence === revision) loading.value = false;
  }
}
function selectTab(value: string) {
  router.replace({ name: 'workspace-inbox', query: { tab: value } });
}
let applyingRoute = false;
let routeRevision = 0;
watch(
  () => route.query,
  async (values) => {
    const revision = ++routeRevision;
    applyingRoute = true;
    const selected = String(values.tab || 'pending');
    tab.value = [
      'pending',
      'active',
      'created',
      'team',
      'today',
      'completed',
      'notification',
    ].includes(selected)
      ? selected
      : 'pending';
    module.value = [
      'repair',
      'anomaly',
      'repairPlan',
      'maintenance',
      'inspection',
      'inventory',
      'inventoryTransfer',
      'inventoryReturn',
      'inventoryOutbound',
    ].includes(String(values.module))
      ? String(values.module)
      : '';
    kind.value = tab.value === 'notification' ? '' : String(values.kind || '');
    query.value = String(values.q || '');
    search.value = query.value;
    readState.value = values.read === 'unread' ? 'unread' : 'all';
    const requestedPage = Number(values.page);
    page.value = Number.isSafeInteger(requestedPage) && requestedPage > 0 ? requestedPage : 1;
    size.value = normalizePageSize(values.size);
    await nextTick();
    if (revision !== routeRevision) return;
    applyingRoute = false;
    load();
  },
  { immediate: true },
);
watch([module, kind, readState], () => {
  if (applyingRoute) return;
  page.value = 1;
});
watch(size, () => {
  if (!applyingRoute) page.value = 1;
});
watch(
  [tab, module, kind, query, readState, page, size],
  () => {
    if (applyingRoute || route.name !== 'workspace-inbox') return;
    router.replace({
      name: 'workspace-inbox',
      query: {
        tab: tab.value,
        ...(module.value ? { module: module.value } : {}),
        ...(kind.value ? { kind: kind.value } : {}),
        ...(query.value ? { q: query.value } : {}),
        ...(readState.value === 'unread' ? { read: 'unread' } : {}),
        ...(page.value > 1 ? { page: String(page.value) } : {}),
        ...(size.value !== 20 ? { size: String(size.value) } : {}),
      },
    });
  },
  { flush: 'post' },
);
async function refresh() {
  await inbox.refresh();
  await load();
}
async function open(row: InboxItem, actionable = false) {
  if (tab.value === 'notification') {
    try {
      await inbox.read(row);
    } catch {
      ElMessage.warning(tr('ui.equipment.text1021'));
    }
  }
  const returnQuery = new URLSearchParams({
    tab: tab.value,
    module: module.value,
    kind: kind.value,
    q: query.value,
    page: String(page.value),
    size: String(size.value),
    read: readState.value,
  });
  router.push(
    inbox.destination(row, {
      actionable: actionable && tab.value === 'pending',
      returnTo: '/workspace?' + returnQuery,
    }),
  );
}
async function readAll() {
  try {
    await inbox.read();
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
  }
}
async function markRead(row: InboxItem) {
  try {
    await inbox.read(row);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0197'));
  }
}
function searchRows() {
  query.value = search.value.trim();
  page.value = 1;
  load();
}
function cell(row: InboxItem, key: string) {
  return key === 'module'
    ? inbox.moduleName(row.module)
    : key === 'due'
      ? formatDateTime(row.due)
      : key === 'time'
        ? formatDateTime(row.time)
        : row[key as keyof InboxItem];
}
async function exportRows() {
  try {
    const all: InboxItem[] = [];
    let p = 1;
    do {
      const data = projected.value
        ? await request<{ data: InboxItem[]; total: number }>(
            '/work-center/tasks?' +
              new URLSearchParams({
                state: tab.value,
                status: String(route.query.status || ''),
                companyId: String(route.query.companyId || ''),
                departmentId: String(route.query.departmentId || ''),
                module: module.value,
                search: query.value,
                page: String(p++),
                size: '100',
              }),
          )
        : await workInbox({
            channel: tab.value === 'notification' ? 'notification' : 'todo',
            state: tab.value === 'notification' ? readState.value : tab.value,
            module: module.value,
            kind: kind.value,
            search: query.value,
            page: p++,
            size: 100,
          });
      all.push(...data.data);
      if (all.length >= data.total || !data.data.length) break;
    } while (true);
    const csv =
      '\ufeff' +
      [
        displayColumns.value.map((c) => labels[c]),
        ...all.map((r) => displayColumns.value.map((c) => String(cell(r, c) ?? ''))),
      ]
        .map((r) =>
          r
            .map((v) => '"' + (/^[\s]*[=+@-]/.test(v) ? "'" : '') + v.replaceAll('"', '""') + '"')
            .join(','),
        )
        .join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const a = document.createElement('a');
    a.href = url;
    a.download = tab.value === 'notification' ? '提醒.csv' : '待办.csv';
    a.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0185'));
  }
}
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  inbox.refresh();
  timer = setInterval(refresh, 30000);
  window.addEventListener('repair-inbox-changed', refresh);
  window.addEventListener('work-inbox-changed', refresh);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
  window.removeEventListener('repair-inbox-changed', refresh);
  window.removeEventListener('work-inbox-changed', refresh);
});
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => rows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="inbox-page">
    <AppListTabs
      class="inbox-tabs"
      :active="tab"
      :items="
        ['pending', 'completed', 'created'].includes(tab)
          ? [
              { key: 'pending', label: '待办', count: inbox.summary.counts.pending },
              { key: 'completed', label: '已办', count: inbox.summary.counts.completed }
            ]
          : [{ key: tab, label: currentContext }]
      "
      @select="selectTab"
    />
    <div class="inbox-workspace">
      <aside class="task-navigation" :aria-label="tr('ui.equipment.text1022')">
        <header>{{ tab === 'notification' ? tr('ui.equipment.text1023') : tr('ui.equipment.text1024') }}</header>
        <template v-if="tab !== 'notification'">
          <ElButton
            class="queue-all"
            :class="{ selected: !module && !kind }"
            @click="selectCategory()"
            ><span>{{
              projected ? tr('ui.equipment.text1025') : tab === 'completed' ? tr('ui.equipment.text1026') : tr('ui.equipment.text1027')
            }}</span
            ><b>{{
              projected
                ? total
                : tab === 'completed'
                  ? inbox.summary.counts.completed
                  : inbox.summary.counts.pending
            }}</b></ElButton
          >
          <section v-for="group in moduleGroups" :key="group.key" class="queue-group">
            <ElButton
              class="queue-module"
              :class="{ selected: module === group.key && !kind }"
              @click="selectCategory(group.key)"
              ><span><i :class="group.key"></i>{{ group.name }}</span
              ><b>{{ group.count }}</b></ElButton
            >
            <ElButton
              v-for="item in group.items"
              :key="item.kind"
              class="queue-kind"
              :class="{ selected: module === group.key && kind === item.kind }"
              @click="selectCategory(group.key, item.kind)"
              ><span>{{ item.kind }}</span
              ><b>{{ item.count }}</b></ElButton
            >
          </section>
        </template>
        <template v-else>
          <ElButton :class="{ selected: readState === 'all' }" @click="readState = 'all'">
            {{ tr('ui.equipment.text1028') }}
          </ElButton>
          <ElButton :class="{ selected: readState === 'unread' }" @click="readState = 'unread'">
            {{ tr('ui.equipment.text1029') }} <b>{{ inbox.summary.counts.unread }}</b></ElButton
          >
          <section class="queue-group">
            <ElButton :class="{ selected: !module }" @click="module = ''">{{ tr('ui.equipment.text1030') }}</ElButton>
            <ElButton
              v-for="group in moduleGroups"
              :key="group.key"
              :class="{ selected: module === group.key }"
              @click="module = group.key"
              ><span><i :class="group.key"></i>{{ group.name }}</span></ElButton
            >
          </section>
        </template>
      </aside>
      <div class="task-results">
        <AppListToolbar class="inbox-toolbar">
          <div class="queue-context">
            <strong>{{ currentContext }}</strong
            ><span>{{ total }} {{ tr('ui.equipment.text0296') }}</span>
          </div>
          <form class="inbox-search" @submit.prevent="searchRows">
            <ListSearchInput v-model="search" :placeholder="tr('ui.equipment.text1031')" />
            <ElButton
              class="text-action"
              @click="resetFilters"
              native-type="button"
              type="primary"
              link
              >{{ tr('ui.equipment.text0204') }}</ElButton
            >
            <ElButton
              class="text-action refresh-action"
              :disabled="loading"
              @click="refresh"
              :aria-label="tr('ui.equipment.text1032')"
              native-type="button"
              type="primary"
              link
              ><AppIcon name="Refresh" />{{ tr('ui.equipment.text0424') }}
            </ElButton>
            <ElButton
              v-if="tab === 'notification' && inbox.summary.counts.unread"
              class="text-action"
              @click="readAll"
              type="primary"
              link
            >
              {{ tr('ui.equipment.text1033') }}
            </ElButton>
          </form>
        </AppListToolbar>
        <p v-if="error || inbox.error" class="inbox-error" role="alert">
          {{ error || inbox.error }}
        </p>
        <div class="inbox-card" :aria-busy="loading">
          <div class="inbox-scroll">
            <AppBusinessTable
              :rows="rows"
              :pagination="false"
              :fill="true"
              :framed="false"
              class="business-table"
              v-if="tab !== 'notification'"
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn
                v-for="col in displayColumns"
                :min-width="150"
                :prop="col"
                :key="col"
                show-overflow-tooltip
                ><template #header
                  ><div class="table-header-label">{{ labels[col] }}</div></template
                ><template #default="{ row: row, $index: tableRowIndex }">
                  <ElButton
                    v-if="col === 'orderNo'"
                    class="text-action"
                    @click="open(row)"
                    type="primary"
                    link
                    >{{ row.orderNo }}</ElButton
                  ><span v-else-if="col === 'kind'" class="task-kind" :class="row.module">{{
                    row.kind
                  }}</span
                  ><span v-else-if="col === 'title'" class="task-description" :title="row.title">{{
                    row.title
                  }}</span
                  ><MaintenanceStatus v-else-if="col === 'status'" :status="row.status" /><span
                    v-else
                    >{{ cell(row, col) }}</span
                  >
                </template></ElTableColumn
              ><!-- @vue-generic {TableColumnRow1} -->
              <ElTableColumn
                class-name="list-action-column"
                fixed="right"
                :width="170"
                :min-width="156"
                :resizable="false"
                ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
                ><template #default="{ row: row, $index: tableRowIndex }">
                  <ElButton
                    class="text-action task-open"
                    :class="{ primary: tab === 'pending' }"
                    @click="open(row, true)"
                    type="primary"
                    link
                    >{{
                      projected || tab === 'completed' ? tr('ui.equipment.text0206') : inbox.actionName(row)
                    }}</ElButton
                  >
                </template></ElTableColumn
              ><template #empty
                ><div v-if="!rows.length">
                  {{
                    loading
                      ? tr('ui.equipment.text0396')
                      : error
                        ? tr('ui.equipment.text0160')
                        : tab === 'pending'
                          ? tr('ui.equipment.text1034')
                          : tr('ui.equipment.text1035')
                  }}
                </div></template
              ></AppBusinessTable
            >
            <div v-else class="reminder-list">
              <article
                v-for="row in rows"
                :key="row.module + ':' + row.id"
                :class="{ unread: !Number(row.isRead) }"
              >
                <div>
                  <span class="notice-dot" :class="{ read: Number(row.isRead) }"></span
                  ><strong>{{ inbox.moduleName(row.module) }} · {{ row.kind }}</strong
                  ><time>{{ formatDateTime(row.time) }}</time>
                </div>
                <p>
                  {{ row.title
                  }}<template v-if="!row.title.includes(row.kind)"> · {{ row.kind }}</template>
                </p>
                <footer>
                  <span>{{ row.orderNo }} · {{ row.equipmentName }}</span
                  ><ElButton
                    v-if="!Number(row.isRead)"
                    class="text-action"
                    @click="markRead(row)"
                    type="primary"
                    link
                  >
                    {{ tr('ui.equipment.text1036') }}</ElButton
                  ><ElButton class="text-action" @click="open(row)" type="primary" link
                    >{{ tr('ui.equipment.text1037') }}</ElButton
                  >
                </footer>
              </article>
              <p v-if="!rows.length" class="inbox-empty">{{ loading ? tr('ui.equipment.text0396') : tr('ui.equipment.text1038') }}</p>
            </div>
          </div>
          <footer class="inbox-footer">
            <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
            ><AppPagination
              v-model:page="page"
              v-model:page-size="size"
              :total="total"
            /><ListToolbarIcons
              class="inbox-footer-tools"
              mode="footer"
              :filter-open="false"
              :columns-open="columnsOpen"
              @toggle-columns="columnsOpen = !columnsOpen"
              @export="exportRows"
              ><template #columns
                ><ElCheckboxGroup v-model="columns"
                  ><ElCheckbox
                    v-for="(label, key) in availableLabels"
                    :key="key"
                    :disabled="key === 'orderNo'"
                    :value="key"
                    >{{ label }}</ElCheckbox
                  ></ElCheckboxGroup
                ></template
              ></ListToolbarIcons
            >
          </footer>
        </div>
      </div>
    </div>
  </section>
</template>
<style scoped>
.inbox-page {
  display: flex;
  flex-direction: column;
  height: 100%;
  min-height: 0;
  color: var(--text);
  font-size: 13px;
}
.inbox-tabs {
  display: flex;
  /* gap: 24px; */
  border-bottom: 1px solid #e3e8f0;
  /* margin-bottom: 14px; */
}
.inbox-tabs button {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 3px 12px;
  background: transparent;
  border: 0;
  border-bottom: 2px solid transparent;
  color: var(--text);
  font: inherit;
  cursor: pointer;
}
.inbox-tabs button.active {
  color: var(--brand);
  border-bottom-color: var(--brand);
  font-weight: 700;
}
.inbox-tabs span {
  padding: 1px 7px;
  border-radius: 10px;
  background: #e8edf5;
  font-size: 13px;
}
.inbox-tabs .active span {
  background: var(--brand-soft);
}
.inbox-search {
  margin-left: auto;
  display: flex;
  align-items: center;
}
.inbox-error {
  margin: 0 0 10px;
  padding: 10px;
  color: #c9535e;
  background: #fff0f1;
}
.inbox-card {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-height: 0;
  background: #fff;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  overflow: hidden;
}
.inbox-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.inbox-card table {
  width: 100%;
  min-width: 860px;
  table-layout: fixed;
  border-spacing: 0;
}
.inbox-card :deep(th) {
  position: sticky;
  top: 0;
  z-index: 1;
}
.text-action {
  border: 0;
  background: transparent;
  color: var(--brand);
  padding: 0;
  font: inherit;
  cursor: pointer;
}
.task-kind {
  display: inline-block;
  padding: 3px 8px;
  border-radius: 4px;
  color: var(--brand);
  background: var(--brand-soft);
}
.inbox-empty {
  text-align: center;
  height: 130px !important;
  color: var(--text);
  padding: 30px;
}
.inbox-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  flex: none;
  height: 42px;
  padding: 0 14px;
  border-top: 1px solid #e3e8f0;
  color: var(--text);
}
.inbox-footer-tools {
  margin-left: auto;
}
.reminder-list article {
  padding: 18px 22px;
  border-bottom: 1px solid #edf0f4;
}
.reminder-list article.unread {
  background: #f8faff;
}
.reminder-list article > div {
  display: flex;
  align-items: center;
  gap: 9px;
}
.reminder-list strong {
  font-weight: 600;
  color: #344156;
}
.reminder-list time {
  margin-left: auto;
  color: var(--text);
}
.reminder-list p {
  margin: 12px 0;
  line-height: 1.7;
}
.reminder-list footer {
  display: flex;
  gap: 16px;
  align-items: center;
}
.reminder-list footer > span {
  margin-right: auto;
  color: var(--text);
}
.notice-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
}
.notice-dot.read {
  background: #cbd5e1;
}
.inbox-workspace {
  display: flex;
  gap: 12px;
  flex: 1;
  min-height: 0;
}
.task-navigation {
  flex: 0 0 208px;
  min-width: 0;
  min-height: 0;
  overflow-x: hidden;
  overflow-y: auto;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #fff;
  padding: 8px;
  /* margin-top: 9px; */
}
.task-navigation header {
  padding: 10px 10px 14px;
  font-weight: 700;
}
.task-navigation button {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
  min-width: 0;
  margin: 0;
  border: 0;
  border-radius: 4px;
  padding: 10px;
  background: transparent;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
  gap: 8px;
}
.task-navigation button:hover {
  background: #f4f7fc;
}
.task-navigation button.selected {
  color: var(--brand);
  background: var(--brand-soft);
  font-weight: 600;
}
.task-navigation button span {
  display: flex;
  align-items: center;
  gap: 8px;
}
.task-navigation button :deep(> span) {
  width: 100%;
  min-width: 0;
  justify-content: space-between;
}
.task-navigation button b {
  flex-shrink: 0;
  font-size: 13px;
  font-weight: 600;
}
.queue-group {
  border-top: 1px solid #edf0f4;
  margin-top: 8px;
  padding-top: 8px;
}
.task-navigation .queue-kind {
  padding-left: 26px;
}
.queue-module {
  font-weight: 600 !important;
}
.queue-module i,
.task-navigation i {
  width: 7px;
  height: 7px;
  border-radius: 2px;
  background: var(--brand);
}
.task-navigation i.maintenance {
  background: #09846e;
}
.task-navigation i.inspection {
  background: #7855b1;
}
.task-results {
  display: flex;
  flex-direction: column;
  flex: 1;
  min-width: 0;
  min-height: 0;
}
.queue-context {
  display: flex;
  align-items: center;
  gap: 10px;
}
.queue-context strong {
  font-weight: 600;
}
.queue-context > span {
  font-size: 13px;
}
.inbox-search {
  gap: 12px;
}
.refresh-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.refresh-action .app-icon {
  width: 16px;
  height: 16px;
  stroke-width: 1.5;
}
.refresh-action:disabled {
  opacity: 0.55;
  cursor: default;
}
.inbox-card .list-action-column {
  width: 88px;
  min-width: 88px;
}
.task-description {
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.task-kind.maintenance {
  color: #087964;
  background: #e8f6f0;
}
.task-kind.inspection {
  color: #6c47a0;
  background: #f1ebfa;
}
.task-open.primary {
  border: 1px solid #d9e5ff;
  background: var(--brand-soft);
  border-radius: 4px;
  min-width: 52px;
  height: 28px;
  padding: 0 10px;
}
.task-open.primary:hover {
  background: var(--brand);
  color: #fff;
  border-color: var(--brand);
}
@media (max-width: 1100px) {
  .task-navigation {
    flex-basis: 176px;
  }
  .inbox-search {
    width: 100%;
    margin-left: 0;
  }
}
@media (max-width: 700px) {
  .inbox-workspace {
    flex-direction: column;
  }
  .task-navigation {
    flex: none;
    max-height: 160px;
  }
}
</style>
