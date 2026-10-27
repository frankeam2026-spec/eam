<script setup lang="ts">import { tr } from '../../i18n';

import { ref, watch, onBeforeUnmount, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElButton, ElSelect, ElOption, ElTimeline, ElTimelineItem, ElSkeleton } from 'element-plus';
import 'element-plus/es/components/timeline/style/css';
import 'element-plus/es/components/timeline-item/style/css';
import 'element-plus/es/components/skeleton/style/css';
import AppPagination from './AppPagination.vue';
import AppIcon from './AppIcon.vue';
import {
  equipmentOverviewApi,
  eventLabels,
  type EquipmentEvent,
  type EventState,
} from '../../equipmentOverview';
import { formatDateTime } from '../../utils/dateTime';
const props = defineProps<{ equipmentId: string; full?: boolean }>();
const state = defineModel<EventState>('state', {
  default: () => ({ kind: '', sort: 'desc', page: 1, size: 20 }),
});
const emit = defineEmits<{ all: []; ready: [] }>();
const router = useRouter();
const events = ref<EquipmentEvent[]>([]),
  total = ref(0),
  loading = ref(false),
  error = ref('');
let revision = 0;
onBeforeUnmount(() => revision++);
async function load() {
  const seq = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await equipmentOverviewApi.events(
      props.equipmentId,
      props.full ? state.value : { kind: '', sort: 'desc', page: 1, size: 5 },
    );
    if (seq !== revision) return;
    if (
      props.full &&
      result.total &&
      state.value.page > Math.ceil(result.total / state.value.size)
    ) {
      state.value.page = 1;
      return;
    }
    events.value = result.data;
    total.value = result.total;
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1360');
  } finally {
    if (seq === revision) {
      loading.value = false;
      await nextTick();
      if (seq === revision) emit('ready');
    }
  }
}
watch(
  () => [props.equipmentId, props.full, props.full ? JSON.stringify(state.value) : ''],
  () => {
    events.value = [];
    total.value = 0;
    void load();
  },
  { immediate: true },
);
const colors: Record<string, string> = {
  repair: '#3478f6',
  maintenance: '#12a594',
  inspection: '#9660dc',
  anomaly: '#ee8e31',
  spare: '#269aba',
};
</script>
<template>
  <section class="content equipment-events-panel" :aria-busy="loading">
    <header class="equipment-panel-heading">
      <h3><AppIcon name="Clock" />{{ full ? tr('ui.equipment.text1361') : tr('ui.equipment.text1362') }}</h3>
      <div v-if="full" class="event-controls">
        <ElSelect
          v-model="state.kind"
          :empty-values="[null, undefined]"
          :aria-label="tr('ui.equipment.text1363')"
          @change="state.page = 1"
        >
          <ElOption value="" :label="tr('ui.equipment.text1364')" />
          <ElOption v-for="(label, key) in eventLabels" :key="key" :value="key" :label="label" />
        </ElSelect>
        <ElSelect v-model="state.sort" :aria-label="tr('ui.equipment.text1365')" @change="state.page = 1">
          <ElOption value="desc" :label="tr('ui.equipment.text1366')" /><ElOption value="asc" :label="tr('ui.equipment.text1367')" />
        </ElSelect>
      </div>
      <ElButton v-else link type="primary" @click="emit('all')"
        >{{ tr('ui.equipment.text0892') }}<AppIcon name="Right"
      /></ElButton>
    </header>
    <ElSkeleton v-if="loading" :rows="3" animated />
    <div v-else-if="error" class="equipment-panel-message" role="alert">
      {{ error }} <ElButton link type="primary" @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
    </div>
    <ElTimeline v-else-if="events.length" class="equipment-timeline">
      <ElTimelineItem
        v-for="event in events"
        :key="`${event.kind}-${event.id}`"
        :color="colors[event.kind]"
        :timestamp="formatDateTime(event.time)"
        placement="top"
      >
        <div class="event-line">
          <span class="event-kind" :class="event.kind">{{ eventLabels[event.kind] }}</span
          ><strong>{{ event.actor || tr('ui.equipment.text1368') }}</strong
          ><span class="event-action" :title="event.action">{{ event.action }}</span>
        </div>
        <ElButton
          v-if="event.url"
          link
          type="primary"
          class="event-code"
          @click="router.push(event.url)"
          >{{ event.code }}</ElButton
        ><span v-else class="event-code">{{ event.code }}</span>
      </ElTimelineItem>
    </ElTimeline>
    <p v-else class="equipment-panel-empty">
      {{ full && state.kind ? tr('ui.equipment.text1369') : tr('ui.equipment.text1370') }}
    </p>
    <footer v-if="full && total" class="equipment-events-footer">
      <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
      ><AppPagination v-model:page="state.page" v-model:page-size="state.size" :total="total" />
    </footer>
  </section>
</template>
<style scoped>
.equipment-events-panel {
  min-width: 0;
  /* padding: 16px; */
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface);
  color: var(--text);
}
.equipment-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 14px;
}
.equipment-panel-heading h3 {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  margin: 0;
}
.equipment-panel-heading h3 .app-icon {
  color: var(--brand);
}
.event-controls {
  display: flex;
  gap: 8px;
}
.event-controls .el-select {
  width: 140px;
}
.equipment-timeline {
  padding: 2px 0 0 4px;
  margin: 0;
}
.equipment-timeline :deep(.el-timeline-item) {
  padding-bottom: 14px;
}
.equipment-timeline :deep(.el-timeline-item:last-child) {
  padding-bottom: 0;
}
.equipment-timeline :deep(.el-timeline-item__timestamp) {
  font-size: 12px;
  color: var(--text);
  margin-bottom: 7px;
}
.equipment-timeline :deep(.el-timeline-item__content) {
  font-size: 13px;
  color: var(--text);
}
.event-line {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.event-kind {
  color: #2764d7;
  background: #edf3ff;
  border-radius: 4px;
  padding: 1px 6px;
  white-space: nowrap;
  font-size: 12px;
}
.event-kind.maintenance {
  color: #087e72;
  background: #e7f8f3;
}
.event-kind.inspection {
  color: #8049bc;
  background: #f3edff;
}
.event-kind.anomaly {
  color: #b5640a;
  background: #fff3df;
}
.event-kind.spare {
  color: #167f9a;
  background: #e7f7fb;
}
.event-line strong {
  flex: none;
  font-weight: 500;
}
.event-action {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.event-code {
  margin-top: 6px;
  font-size: 12px;
  line-height: 18px;
}
.equipment-panel-empty,
.equipment-panel-message {
  margin: 0;
  padding: 18px 0;
  font-size: 13px;
}
.equipment-panel-message {
  color: var(--el-color-danger);
}
.equipment-events-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px solid var(--line);
}
@media (max-width: 700px) {
  .equipment-panel-heading {
    flex-wrap: wrap;
  }
  .event-controls .el-select {
    width: 120px;
  }
}
</style>
