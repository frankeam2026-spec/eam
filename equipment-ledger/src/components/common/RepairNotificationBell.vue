<script setup lang="ts">
import { ElButton, ElBadge } from 'element-plus';
import 'element-plus/es/components/badge/style/css';

import AppIcon from './AppIcon.vue';
import { ref, onMounted, onUnmounted } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElPopover, ElMessage } from 'element-plus';
import { workInbox, type InboxItem } from '../../api';
import { useWorkInboxStore } from '../../stores/workInbox';
import { formatDateTime } from '../../utils/dateTime';
const router = useRouter(),
  route = useRoute(),
  inbox = useWorkInboxStore();
const visible = ref(''),
  rows = ref<InboxItem[]>([]),
  busy = ref(false),
  error = ref('');
let requestRevision = 0;
async function show(channel: string) {
  const revision = ++requestRevision;
  visible.value = channel;
  rows.value = [];
  error.value = '';
  busy.value = true;
  await inbox.refresh();
  try {
    const data = await workInbox({
      channel,
      state: channel === 'todo' ? 'pending' : 'all',
      page: 1,
      size: 8,
    });
    if (revision === requestRevision && visible.value === channel) rows.value = data.data;
  } catch (e) {
    if (revision === requestRevision && visible.value === channel)
      error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    if (revision === requestRevision) busy.value = false;
  }
}
async function open(row: InboxItem) {
  const channel = visible.value;
  if (channel === 'notification') {
    try {
      await inbox.read(row);
    } catch {
      ElMessage.warning('提醒未能标为已读');
    }
  }
  visible.value = '';
  router.push(
    inbox.destination(row, {
      actionable: channel === 'todo',
      returnTo:
        route.name === 'workspace'
          ? route.fullPath
          : '/workspace?tab=' + (channel === 'todo' ? 'pending' : 'notification'),
    }),
  );
}
async function readAll() {
  try {
    await inbox.read();
    await show('notification');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '操作失败');
  }
}
function center(channel: string) {
  visible.value = '';
  router.push({
    name: 'workspace',
    query: { tab: channel === 'todo' ? 'pending' : 'notification' },
  });
}
function changed() {
  inbox.refresh();
  if (visible.value) show(visible.value);
}
let timer: ReturnType<typeof setInterval> | undefined;
onMounted(() => {
  inbox.refresh();
  timer = setInterval(changed, 30000);
  window.addEventListener('repair-inbox-changed', changed);
  window.addEventListener('work-inbox-changed', changed);
});
onUnmounted(() => {
  if (timer) clearInterval(timer);
  window.removeEventListener('repair-inbox-changed', changed);
  window.removeEventListener('work-inbox-changed', changed);
});
</script>
<template>
  <div class="work-inbox-tools">
    <ElPopover
      v-for="channel in ['todo', 'notification']"
      :key="channel"
      :visible="visible === channel"
      placement="bottom-end"
      :width="400"
      trigger="click"
      popper-class="eam-notification-popover"
      @update:visible="(value) => (value ? show(channel) : visible === channel && (visible = ''))"
    >
      <template #reference>
        <ElButton
          class="inbox-tool"
          circle
          :aria-label="channel === 'todo' ? '待办' : '提醒'"
          :title="channel === 'todo' ? '需要我处理的事项' : '需要我知道的消息'"
        >
          <ElBadge
            class="inbox-count"
            :value="channel === 'todo' ? inbox.summary.counts.pending : inbox.summary.counts.unread"
            :max="99"
            :show-zero="false"
          >
            <AppIcon :name="channel === 'notification' ? 'Remind' : 'Todo'" />
          </ElBadge>
        </ElButton>
      </template>
      <div class="notification-title">
        <strong>{{ channel === 'todo' ? '待办' : '提醒' }}</strong
        ><ElButton
          v-if="channel === 'notification' && inbox.summary.counts.unread"
          @click="readAll"
        >
          全部已读</ElButton
        ><ElButton @click="center(channel)">查看全部</ElButton>
      </div>
      <div class="notification-items">
        <p v-if="busy">加载中…</p>
        <p v-else-if="error" role="alert">{{ error }}</p>
        <ElButton
          v-for="item in rows"
          v-else
          :key="item.module + ':' + item.id"
          @click="open(item)"
          class="app-content-button"
          ><div class="notification-row-head">
            <i v-if="channel === 'notification' && !Number(item.isRead)" class="unread-dot"></i
            ><b>{{ inbox.moduleName(item.module) }} · {{ item.kind }}</b
            ><span v-if="channel === 'todo'" class="task-action"
              >{{ inbox.actionName(item) }} <AppIcon name="Right"
            /></span>
          </div>
          <span v-if="channel === 'notification'"
            >{{ item.title
            }}<template v-if="!item.title.includes(item.kind)"> · {{ item.kind }}</template></span
          ><span v-else>{{ item.orderNo }} · {{ item.equipmentName }}</span
          ><small>{{ formatDateTime(item.time) }}</small></ElButton
        >
        <p v-if="!busy && !error && !rows.length">
          {{ channel === 'todo' ? '当前没有待办事项' : '暂无提醒' }}
        </p>
      </div>
    </ElPopover>
  </div>
</template>
<style scoped>
.work-inbox-tools {
  display: flex;
  align-items: center;
  gap: 12px;
}
.inbox-tool {
  display: flex;
  align-items: center;
  gap: 5px;
  width: 36px;
  padding: 0;
  height: 34px;
  --el-button-text-color: currentColor;
  --el-button-hover-text-color: currentColor;
  --el-button-hover-bg-color: #ffffff20;
  --el-fill-color-light: #ffffff20;
  --el-fill-color: #ffffff30;
  border: 0;
  border-radius: 4px;
  background: transparent;
  color: inherit;
  font-size: 13px;
  cursor: pointer;
}
.inbox-tool:hover {
  background: #ffffff20;
}
.inbox-tool .app-icon {
  width: 20px;
  height: 20px;
}
.inbox-count {
  --el-badge-size: 16px;
  --el-badge-font-size: 10px;
  --el-badge-padding: 4px;
  display: flex;
  align-items: center;
}
.inbox-count :deep(.el-badge__content) {
  border-color: var(--brand);
}
.notification-title {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 4px 0 12px;
  border-bottom: 1px solid #e4e9f0;
  font-size: 13px;
}
.notification-title strong {
  margin-right: auto;
}
.notification-title button {
  background: transparent;
  border: 0;
  color: var(--brand);
  cursor: pointer;
  font-size: 13px;
}
.notification-items {
  max-height: 420px;
  overflow: auto;
}
.notification-items > button {
  height: auto;
  min-height: 76px;
  margin-left: 0;
  white-space: normal;
  align-items: flex-start;
  display: flex;
  flex-direction: column;
  width: 100%;
  gap: 7px;
  padding: 12px 6px;
  text-align: left;
  border: 0;
  border-bottom: 1px solid #edf0f4;
  background: transparent;
  cursor: pointer;
  color: #4d5a6e;
  font-size: 13px;
  line-height: 1.5;
}
.notification-items > button:hover {
  background: #f5f7fc;
}
.notification-row-head {
  display: flex;
  align-items: center;
  gap: 6px;
  width: 100%;
}
.notification-row-head b {
  color: #344156;
  font-weight: 600;
}
.task-action {
  color: var(--brand);
  margin-left: auto;
}
.unread-dot {
  width: 6px;
  height: 6px;
  border-radius: 50%;
  background: var(--brand);
  flex: none;
}
.notification-items small,
.notification-items p {
  color: #94a3b8;
}
</style>
