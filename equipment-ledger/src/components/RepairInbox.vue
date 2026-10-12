<script setup lang="ts">
import { computed, nextTick, onMounted, onUnmounted, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import AppPagination from './common/AppPagination.vue';
import MaintenanceStatus from './common/MaintenanceStatus.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { workInbox, type InboxItem } from '../api';
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
      'maintenance',
      'inspection',
      'inventory',
      'inventoryTransfer',
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
  size = ref([10, 20, 50, 100].includes(Number(route.query.size)) ? Number(route.query.size) : 10),
  total = ref(0),
  loading = ref(false),
  error = ref('');
const columnsOpen = ref(false),
  columns = ref(['orderNo', 'equipmentName', 'kind', 'status', 'time']);
const labels: Record<string, string> = {
  title: '任务说明',
  module: '模块',
  orderNo: '任务编号',
  equipmentName: '设备名称',
  kind: '待办事项',
  status: '业务状态',
  time: '到达时间',
};
const moduleGroups = computed(() =>
  ['repair', 'maintenance', 'inspection', 'inventory']
    .map((key) => ({
      key,
      name: inbox.moduleName(key),
      items: inbox.summary.kinds
        .filter(
          (item) =>
            item.module === key ||
            (key === 'inventory' &&
              ['inventoryTransfer', 'inventoryOutbound'].includes(item.module || '')),
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
  tab.value === 'notification'
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
  const request = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const data = await workInbox({
      channel: tab.value === 'notification' ? 'notification' : 'todo',
      state: tab.value === 'notification' ? readState.value : tab.value,
      module: module.value,
      kind: kind.value,
      search: query.value,
      page: page.value,
      size: size.value,
    });
    if (request === revision) {
      rows.value = data.data;
      total.value = data.total;
      const lastPage = Math.max(1, Math.ceil(data.total / size.value));
      if (page.value > lastPage) page.value = lastPage;
    }
  } catch (e) {
    if (request === revision) error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    if (request === revision) loading.value = false;
  }
}
function selectTab(value: string) {
  router.replace({ name: 'workspace', query: { tab: value } });
}
let applyingRoute = false;
let routeRevision = 0;
watch(
  () => route.query,
  async (values) => {
    const revision = ++routeRevision;
    applyingRoute = true;
    const selected = String(values.tab || 'pending');
    tab.value = ['pending', 'completed', 'notification'].includes(selected) ? selected : 'pending';
    module.value = [
      'repair',
      'maintenance',
      'inspection',
      'inventory',
      'inventoryTransfer',
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
    size.value = [10, 20, 50, 100].includes(Number(values.size)) ? Number(values.size) : 10;
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
    if (applyingRoute || route.name !== 'workspace') return;
    router.replace({
      name: 'workspace',
      query: {
        tab: tab.value,
        ...(module.value ? { module: module.value } : {}),
        ...(kind.value ? { kind: kind.value } : {}),
        ...(query.value ? { q: query.value } : {}),
        ...(readState.value === 'unread' ? { read: 'unread' } : {}),
        ...(page.value > 1 ? { page: String(page.value) } : {}),
        ...(size.value !== 10 ? { size: String(size.value) } : {}),
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
      ElMessage.warning('提醒未能标为已读');
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
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  }
}
async function markRead(row: InboxItem) {
  try {
    await inbox.read(row);
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
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
    : key === 'time'
      ? formatDateTime(row.time)
      : row[key as keyof InboxItem];
}
async function exportRows() {
  try {
    const all: InboxItem[] = [];
    let p = 1;
    do {
      const data = await workInbox({
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
        columns.value.map((c) => labels[c]),
        ...all.map((r) => columns.value.map((c) => String(cell(r, c) ?? ''))),
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
    ElMessage.error(e instanceof Error ? e.message : '导出失败');
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
</script>
<template>
  <section class="inbox-page">
    <nav class="inbox-tabs" aria-label="工作事项">
      <button :class="{ active: tab === 'pending' }" @click="selectTab('pending')">
        待办 <span>{{ inbox.summary.counts.pending }}</span>
      </button>
      <button :class="{ active: tab === 'completed' }" @click="selectTab('completed')">
        已办 <span>{{ inbox.summary.counts.completed }}</span>
      </button>
      <button :class="{ active: tab === 'notification' }" @click="selectTab('notification')">
        提醒 <span>{{ inbox.summary.counts.unread }}</span>
      </button>
    </nav>
    <div class="inbox-workspace">
      <aside class="task-navigation" aria-label="事项分类">
        <header>{{ tab === 'notification' ? '提醒分类' : '任务分类' }}</header>
        <template v-if="tab !== 'notification'">
          <button
            class="queue-all"
            :class="{ selected: !module && !kind }"
            @click="selectCategory()"
          >
            <span>{{ tab === 'completed' ? '全部已办' : '全部待办' }}</span>
            <b>{{
              tab === 'completed' ? inbox.summary.counts.completed : inbox.summary.counts.pending
            }}</b>
          </button>
          <section v-for="group in moduleGroups" :key="group.key" class="queue-group">
            <button
              class="queue-module"
              :class="{ selected: module === group.key && !kind }"
              @click="selectCategory(group.key)"
            >
              <span><i :class="group.key"></i>{{ group.name }}</span
              ><b>{{ group.count }}</b>
            </button>
            <button
              v-for="item in group.items"
              :key="item.kind"
              class="queue-kind"
              :class="{ selected: module === group.key && kind === item.kind }"
              @click="selectCategory(group.key, item.kind)"
            >
              <span>{{ item.kind }}</span
              ><b>{{ item.count }}</b>
            </button>
          </section>
        </template>
        <template v-else>
          <button :class="{ selected: readState === 'all' }" @click="readState = 'all'">
            全部提醒
          </button>
          <button :class="{ selected: readState === 'unread' }" @click="readState = 'unread'">
            未读提醒 <b>{{ inbox.summary.counts.unread }}</b>
          </button>
          <section class="queue-group">
            <button :class="{ selected: !module }" @click="module = ''">全部模块</button>
            <button
              v-for="group in moduleGroups"
              :key="group.key"
              :class="{ selected: module === group.key }"
              @click="module = group.key"
            >
              <span><i :class="group.key"></i>{{ group.name }}</span>
            </button>
          </section>
        </template>
      </aside>
      <div class="task-results">
        <div class="inbox-toolbar">
          <div class="queue-context">
            <strong>{{ currentContext }}</strong
            ><span>{{ total }} 条</span>
          </div>
          <div class="inbox-search">
            <ListSearchInput
              v-model="search"
              placeholder="搜索编号、设备、事项"
              @submit="searchRows"
            />
            <button class="text-action" @click="resetFilters">重置</button>
            <button
              class="text-action refresh-action"
              :disabled="loading"
              @click="refresh"
              aria-label="刷新任务"
            >
              <svg viewBox="0 0 20 20" aria-hidden="true">
                <path d="M16 7a6 6 0 1 0 0 6M16 3v4h-4" /></svg
              >刷新
            </button>
            <button
              v-if="tab === 'notification' && inbox.summary.counts.unread"
              class="text-action"
              @click="readAll"
            >
              全部已读
            </button>
          </div>
        </div>
        <p v-if="error || inbox.error" class="inbox-error" role="alert">
          {{ error || inbox.error }}
        </p>
        <div class="inbox-card" :aria-busy="loading">
          <div class="inbox-scroll">
            <table class="business-table" v-if="tab !== 'notification'">
              <thead>
                <tr>
                  <th v-for="col in columns" :key="col" :class="`col-${col}`">{{ labels[col] }}</th>
                  <th class="list-action-column">操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="row in rows" :key="row.module + ':' + row.id">
                  <td v-for="col in columns" :key="col" :class="`col-${col}`">
                    <button v-if="col === 'orderNo'" class="text-action" @click="open(row)">
                      {{ row.orderNo }}</button
                    ><span v-else-if="col === 'kind'" class="task-kind" :class="row.module">{{
                      row.kind
                    }}</span
                    ><span
                      v-else-if="col === 'title'"
                      class="task-description"
                      :title="row.title"
                      >{{ row.title }}</span
                    ><MaintenanceStatus v-else-if="col === 'status'" :status="row.status" /><span
                      v-else
                      >{{ cell(row, col) }}</span
                    >
                  </td>
                  <td class="list-action-column">
                    <button
                      class="text-action task-open"
                      :class="{ primary: tab === 'pending' }"
                      @click="open(row, true)"
                    >
                      {{ tab === 'completed' ? '查看' : inbox.actionName(row) }}
                    </button>
                  </td>
                </tr>
                <tr v-if="!rows.length">
                  <td :colspan="columns.length + 1" class="inbox-empty">
                    {{
                      loading
                        ? '加载中…'
                        : error
                          ? '加载失败'
                          : tab === 'pending'
                            ? '当前没有待办事项'
                            : '暂无已办事项'
                    }}
                  </td>
                </tr>
              </tbody>
            </table>
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
                  ><button v-if="!Number(row.isRead)" class="text-action" @click="markRead(row)">
                    标为已读</button
                  ><button class="text-action" @click="open(row)">查看详情</button>
                </footer>
              </article>
              <p v-if="!rows.length" class="inbox-empty">{{ loading ? '加载中…' : '暂无提醒' }}</p>
            </div>
          </div>
          <footer class="inbox-footer">
            <span>共 {{ total }} 条</span
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
                ><label v-for="(label, key) in labels" :key="key"
                  ><input
                    v-model="columns"
                    type="checkbox"
                    :value="key"
                    :disabled="key === 'orderNo'"
                  />{{ label }}</label
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
  height: calc(100dvh - 54px);
  min-height: 0;
  padding: 12px;
  color: var(--text);
  font-size: 13px;
}
.inbox-tabs {
  display: flex;
  gap: 24px;
  border-bottom: 1px solid #e3e8f0;
  margin-bottom: 14px;
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
.inbox-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  margin-bottom: 12px;
}
.inbox-search {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 14px;
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
.inbox-card th {
  position: sticky;
  top: 0;
  z-index: 1;
  background: #f5f7fa;
  color: var(--text);
  text-align: left;
  font-weight: 700;
}
.inbox-card th,
.inbox-card td {
  padding: 10px 12px;
  height: 44px;
  border-bottom: 1px solid #edf0f4;
  white-space: nowrap;
}
.inbox-card tbody tr:hover td {
  background: #f8faff;
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
  min-height: 0;
  overflow: auto;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #fff;
  padding: 8px;
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
.task-navigation button b {
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
.inbox-toolbar {
  min-height: 36px;
  margin-bottom: 10px;
}
.inbox-search {
  gap: 12px;
}
.refresh-action {
  display: inline-flex;
  align-items: center;
  gap: 4px;
}
.refresh-action svg {
  width: 16px;
  height: 16px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.5;
}
.refresh-action:disabled {
  opacity: 0.55;
  cursor: default;
}
.inbox-card .col-orderNo {
  width: 160px;
}
.inbox-card .col-equipmentName {
  width: 140px;
}
.inbox-card .col-module {
  width: 75px;
}
.inbox-card .col-kind {
  width: 135px;
}
.inbox-card .col-status {
  width: 95px;
}
.inbox-card .col-time {
  width: 172px;
}
.inbox-card .col-title {
  width: auto;
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
