<script setup lang="ts">
import { ElButton } from 'element-plus';

import { ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { request } from '../../api';
import type { HomeBlock } from '../../home';
import AppIcon from './AppIcon.vue';
import { formatDateTime } from '../../utils/dateTime';
const props = defineProps<{ block: HomeBlock; editing?: boolean; compact?: boolean }>();
const router = useRouter();
const data = ref<{
  data: Record<string, any>[];
  total: number;
  url?: string;
  counts?: Record<string, number>;
}>({ data: [], total: 0 });
const loading = ref(false),
  error = ref('');
let revision = 0;
async function load() {
  const seq = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await request<typeof data.value>('/home/blocks', {
      method: 'POST',
      body: JSON.stringify(props.block),
    });
    if (seq === revision) data.value = result;
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    if (seq === revision) loading.value = false;
  }
}
watch(() => JSON.stringify(props.block), load, { immediate: true });
</script>
<template>
  <div class="home-widget-content" :class="{ 'compact-widget': compact }" :aria-busy="loading">
    <p v-if="loading" class="widget-state">正在加载…</p>
    <div v-else-if="error" class="widget-state error" role="alert">
      {{ error }}
      <ElButton class="" @click="load"><AppIcon name="Refresh" />重试</ElButton>
    </div>
    <template v-else>
      <div v-if="data.counts" class="widget-stats">
        <div v-for="(count, label) in data.counts" :key="label">
          <span>{{ label }}</span
          ><strong>{{ count }}</strong>
        </div>
        <p v-if="!Object.keys(data.counts).length">暂无任务</p>
      </div>
      <div v-else-if="block.type === 'shortcuts'" class="widget-shortcuts">
        <ElButton v-for="row in data.data" :key="row.id" @click="!editing && router.push(row.url)"
          ><AppIcon name="Right" />{{ row.name }}</ElButton
        >
        <p v-if="!data.data.length">暂无授权入口</p>
      </div>
      <div v-else class="widget-list">
        <ElButton
          v-for="(row, index) in data.data"
          :key="row.id || index"
          :disabled="editing || !row.url"
          @click="router.push(row.url)"
          class="widget-task-button"
          ><div class="widget-row-content">
            <div class="widget-row-heading">
              <span class="widget-code">{{ row.code || row.orderNo }}</span
              ><strong :title="String(row.name || row.materialName || '')">{{
                row.name || row.materialName
              }}</strong
              ><span v-if="row.count !== undefined" class="load-value">{{ row.count }} 项</span>
            </div>
            <p v-if="row.status || row.due || row.time || row.remaining">
              <span v-if="row.status" class="widget-status">{{ row.status }}</span
              ><span
                class="widget-time"
                :title="
                  row.due ? formatDateTime(row.due) : row.time ? formatDateTime(row.time) : ''
                "
                >{{
                  row.due
                    ? formatDateTime(row.due)
                    : row.time
                      ? formatDateTime(row.time)
                      : row.remaining
                        ? Number(row.remaining).toFixed(2) + ' ' + row.unit
                        : ''
                }}</span
              >
            </p>
          </div></ElButton
        >
        <p v-if="!data.data.length" class="widget-state">暂无相关事项</p>
      </div>
      <footer v-if="data.url">
        <span>共 {{ data.total }} 项</span
        ><ElButton :disabled="editing" @click="router.push(data.url)">
          查看全部<AppIcon name="Right"
        /></ElButton>
      </footer>
    </template>
  </div>
</template>
<style scoped>
.compact-widget {
  padding: 0 16px 16px;
}
.compact-widget .widget-state {
  min-height: 40px;
}
.home-widget-content {
  color: var(--text);
}
.widget-state {
  min-height: 120px;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  margin: 0;
}
.error {
  color: var(--danger);
}
.widget-list > button {
  display: flex;
  width: 100%;
  height: auto;
  min-width: 0;
  margin: 0;
  text-align: left;
  padding: 8px 0;
  white-space: nowrap;
  border: 0;
  border-bottom: 1px solid var(--line);
  background: transparent;
  color: var(--text);
  cursor: pointer;
}
.widget-list > button:last-of-type {
  border: 0;
}
.widget-list > button:hover:not(:disabled) {
  background: var(--brand-soft);
}
.widget-list > button:disabled {
  opacity: 1;
  cursor: default;
}
.widget-task-button :deep(> span) {
  width: 100%;
  min-width: 0;
}
.widget-row-content {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
  min-width: 0;
}
.widget-row-heading {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 10px;
}
.widget-list strong {
  min-width: 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.widget-code {
  color: var(--brand);
  flex-shrink: 0;
  white-space: nowrap;
}
.widget-row-content > p {
  display: grid;
  grid-template-columns: 56px minmax(0, 1fr);
  flex: 0 0 220px;
  max-width: 56%;
  min-width: 0;
  gap: 8px;
  margin: 0;
  min-height: 20px;
  align-items: center;
  color: var(--text);
}
.widget-time {
  grid-column: 2;
  text-align: right;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.widget-status {
  text-align: center;
  white-space: nowrap;
  font-size: 12px;
  background: var(--brand-soft);
  color: var(--brand);
  border-radius: 4px;
  padding: 2px 6px;
}
.widget-shortcuts {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 10px;
}
.widget-shortcuts button {
  min-width: 0;
  height: auto;
  margin: 0;
  white-space: normal;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px;
  border: 1px solid var(--line);
  border-radius: 6px;
  background: var(--surface);
  text-align: left;
  cursor: pointer;
}
.widget-shortcuts button :deep(> span) {
  min-width: 0;
  width: 100%;
  justify-content: flex-start;
  gap: 8px;
  text-align: left;
}
.widget-shortcuts button:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.widget-stats {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 12px;
}
.widget-stats > div {
  padding: 12px;
  background: var(--canvas);
  border-radius: 6px;
}
.widget-stats strong {
  display: block;
  font-size: 26px;
  font-weight: 600;
  margin-top: 8px;
}
.load-value {
  margin-left: auto;
  color: var(--brand);
}
footer {
  display: flex;
  justify-content: space-between;
  border-top: 1px solid var(--line);
  padding-top: 10px;
  margin-top: 6px;
}
footer button {
  display: flex;
  align-items: center;
  gap: 5px;
  background: transparent;
  border: 0;
  color: var(--brand);
  cursor: pointer;
}
</style>
