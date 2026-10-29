<script setup lang="ts">
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElButton, ElInput, ElAlert } from 'element-plus';
import AppIcon from '../common/AppIcon.vue';
import KnowledgeRepairRecords from './KnowledgeRepairRecords.vue';
import type { RepairRecords } from './repairRecords';
import KnowledgeEquipmentRecords from './KnowledgeEquipmentRecords.vue';
import type { EquipmentRecords } from './equipmentRecords';
import KnowledgeBusinessQuery from './KnowledgeBusinessQuery.vue';
import KnowledgeWorkInbox, { type InboxResult } from './KnowledgeWorkInbox.vue';
import type { BusinessQuery } from './businessQueries';
import KnowledgeSourcePreview from './KnowledgeSourcePreview.vue';
import PagedChoiceField from '../common/PagedChoiceField.vue';
import { listEquipment, getEquipment } from '../../api';
import type { SelectorRow } from '../../selectors';
import AnalyticsWidget from '../common/AnalyticsWidget.vue';
import type {
  AnalyticsMetric,
  AnalyticsResult,
  AnalyticsWidget as Widget,
  AnalyticsContext,
} from '../../analytics';
import { useRouter } from 'vue-router';
import { request, requestStream } from '../../api';
import { tr } from '../../i18n';
import { useAuthStore } from '../../stores/auth';
const auth = useAuthStore();
const props = defineProps<{ collapsed?: boolean }>();
const emit = defineEmits<{ layout: [state: { open: boolean; overlay: boolean }] }>();
const viewport = ref(window.innerWidth);
const selectedEquipment = ref(''),
  equipmentOptions = ref<SelectorRow[]>([]);
async function equipmentRows(q: string, page: number, size: number) {
  return listEquipment({ q, page, size });
}
function selectEquipment(rows: SelectorRow[]) {
  if (busy.value) return;
  clear();
  equipmentOptions.value = rows;
  selectedEquipment.value = rows[0]?.id || '';
}
const router = useRouter();
const open = ref(false),
  wide = ref(false),
  question = ref(''),
  busy = ref(false),
  error = ref('');
const overlay = computed(() => wide.value || viewport.value <= 600);
watch([open, overlay], () => emit('layout', { open: open.value, overlay: overlay.value }), {
  immediate: true,
});
type Citation = {
  chunkId: string;
  documentId: string;
  versionId?: string;
  hasOriginal?: boolean;
  repairOrderId?: string;
  title: string;
  pageNo: number;
  excerpt: string;
};
const previewSource = ref<Citation>();
type Analysis = {
  metric: AnalyticsMetric;
  result: AnalyticsResult;
  widget: Widget;
  context: AnalyticsContext;
};
type Message = {
  exchangeId?: string;
  role: 'user' | 'assistant';
  text: string;
  citations?: Citation[];
  analytics?: Analysis;
  repairRecords?: RepairRecords;
  equipmentRecords?: EquipmentRecords;
  businessQueries?: BusinessQuery[];
  workInboxes?: InboxResult[];
};
function drill(analysis: Analysis, filter: { key?: string; series?: string }) {
  const key = crypto.randomUUID();
  sessionStorage.setItem(
    'eam-analysis:' + key,
    JSON.stringify({
      widget: analysis.widget,
      context: { ...analysis.context, asOf: analysis.result.asOf },
      filter,
      title: analysis.metric.name,
    }),
  );
  void router.push({ name: 'home-analytics-details', params: { key } });
}
const messages = ref<Message[]>([]),
  history = ref<HTMLElement>();
const conversation = ref(''),
  sessions = ref<{ id: string; title: string }[]>([]),
  showHistory = ref(false),
  historyPage = ref(1),
  sessionTotal = ref(0);
const pendingKeys = new Map<string, string>();
const turnPage = ref(1),
  turnTotal = ref(0);
let historyRevision = 0;
async function listSessions() {
  try {
    const result = await request<{ data: { id: string; title: string }[]; total: number }>(
      `/knowledge/assistant/conversations?page=${historyPage.value}&size=20`,
    );
    sessions.value = result.data;
    sessionTotal.value = result.total;
    showHistory.value = true;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  }
}
async function selectSession(id: string, older = false) {
  if (busy.value) return;
  const revision = ++historyRevision;
  error.value = '';
  try {
    const result = await request<{
      equipmentId?: string;
      total: number;
      data: {
        id: string;
        question: string;
        answer: string;
        status: string;
        citations: Citation[];
      }[];
    }>(`/knowledge/assistant/conversations/${id}?page=${older ? turnPage.value + 1 : 1}&size=50`);
    if (revision !== historyRevision) return;
    selectedEquipment.value = result.equipmentId || '';
    equipmentOptions.value = [];
    if (result.equipmentId) {
      const device = await getEquipment(result.equipmentId);
      if (revision !== historyRevision) return;
      equipmentOptions.value = [
        {
          id: device.data.id,
          name: device.data.name,
          code: device.data.code,
          model: device.data.model,
        },
      ];
    }
    conversation.value = id;
    const restored = result.data.flatMap((turn) => [
      { exchangeId: turn.id, role: 'user' as const, text: turn.question },
      {
        exchangeId: turn.id,
        role: 'assistant' as const,
        text: turn.status === 'SUCCEEDED' ? turn.answer : tr('xiaozhu.incomplete'),
        citations: turn.citations,
      },
    ]);
    if (older) {
      const existing = new Set(messages.value.map((m) => m.exchangeId + ':' + m.role));
      messages.value = [
        ...restored.filter((m) => !existing.has(m.exchangeId + ':' + m.role)),
        ...messages.value,
      ];
      turnPage.value++;
    } else {
      messages.value = restored;
      turnPage.value = 1;
    }
    turnTotal.value = result.total;
    showHistory.value = false;
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  }
}
async function deleteSession(id: string) {
  if (busy.value) return;
  try {
    await request(`/knowledge/assistant/conversations/${id}`, { method: 'DELETE' });
    if (conversation.value === id) clear();
    await listSessions();
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
  }
}
const x = ref(12),
  y = ref(Math.max(64, window.innerHeight - 140));
const key = computed(() => `eam-xiaozhu-position:${auth.user?.tenantId}:${auth.user?.id}`);
let gesture: { x: number; y: number; left: number; top: number; moved: boolean } | undefined;
let dragged = false;
let anchored = true;
let controller: AbortController | undefined;
function clamp() {
  viewport.value = window.innerWidth;
  if (anchored) {
    x.value = 12;
    y.value = window.innerHeight - 112;
  }
  x.value = Math.max(8, Math.min(x.value, window.innerWidth - 90));
  y.value = Math.max(64, Math.min(y.value, window.innerHeight - 100));
}
function down(e: PointerEvent) {
  if (e.button !== 0) return;
  gesture = { x: e.clientX, y: e.clientY, left: x.value, top: y.value, moved: false };
  (e.currentTarget as HTMLElement).setPointerCapture(e.pointerId);
}
function move(e: PointerEvent) {
  if (!gesture) return;
  const dx = e.clientX - gesture.x,
    dy = e.clientY - gesture.y;
  if (Math.abs(dx) + Math.abs(dy) > 6) gesture.moved = true;
  if (gesture.moved) {
    anchored = false;
    x.value = gesture.left + dx;
    y.value = gesture.top + dy;
    clamp();
  }
}
function up() {
  if (!gesture) return;
  dragged = gesture.moved;
  gesture = undefined;
  if (dragged) {
    x.value = x.value < window.innerWidth / 2 ? 12 : window.innerWidth - 90;
    clamp();
    try {
      localStorage.setItem(key.value, JSON.stringify({ x: x.value, y: y.value }));
    } catch {}
  }
}
function toggle() {
  if (dragged) {
    dragged = false;
    return;
  }
  open.value = !open.value;
}
function stop() {
  controller?.abort();
  controller = undefined;
  busy.value = false;
}
async function ask() {
  if (busy.value || !question.value.trim()) return;
  const text = question.value.trim();
  const lastMessage = messages.value.at(-1);
  if (lastMessage?.role !== 'user' || lastMessage.text !== text || lastMessage.exchangeId)
    messages.value.push({ role: 'user', text });
  question.value = '';
  error.value = '';
  busy.value = true;
  const active = new AbortController();
  controller = active;
  try {
    if (!conversation.value) {
      const created = await request<{ id: string }>('/knowledge/assistant/conversations', {
        method: 'POST',
        signal: active.signal,
      });
      if (active.signal.aborted) return;
      conversation.value = created.id;
      if (selectedEquipment.value) {
        try {
          await request(`/knowledge/assistant/conversations/${created.id}/equipment`, {
            method: 'PUT',
            signal: active.signal,
            body: JSON.stringify({ equipmentId: selectedEquipment.value }),
          });
        } catch (e) {
          conversation.value = '';
          throw e;
        }
      }
    }
    const pendingKey = conversation.value + ':' + text;
    let requestKey = pendingKeys.get(pendingKey);
    if (!requestKey) {
      requestKey = crypto.randomUUID();
      pendingKeys.set(pendingKey, requestKey);
    }
    const answer = await requestStream<{
      id?: string;
      answer: string;
      citations: Citation[];
      analytics?: Analysis;
      repairRecords?: RepairRecords;
      equipmentRecords?: EquipmentRecords;
      businessQueries?: BusinessQuery[];
      workInboxes?: InboxResult[];
    }>(`/knowledge/assistant/conversations/${conversation.value}/stream`, {
      method: 'POST',
      signal: active.signal,
      body: JSON.stringify({
        question: text,
        requestKey,
        queryContext:
          [...messages.value]
            .reverse()
            .find((m) => m.role === 'assistant')
            ?.businessQueries?.map((result) => result.plan) || [],
      }),
    });
    if (!active.signal.aborted) {
      pendingKeys.delete(pendingKey);
      const last = messages.value.at(-1);
      if (last?.role === 'user') last.exchangeId = answer.id;
      messages.value.push({
        exchangeId: answer.id,
        role: 'assistant',
        text: answer.answer,
        citations: answer.citations,
        analytics: answer.analytics,
        repairRecords: answer.repairRecords,
        equipmentRecords: answer.equipmentRecords,
        businessQueries: answer.businessQueries,
        workInboxes: answer.workInboxes,
      });
    }
  } catch (e) {
    if (!active.signal.aborted) {
      if (
        e &&
        typeof e === 'object' &&
        (('status' in e && e.status === 422) || ('code' in e && e.code === 'AI_TURN_FAILED'))
      )
        pendingKeys.delete(conversation.value + ':' + text);
      error.value = e instanceof Error ? e.message : tr('common.error.requestFailed');
      question.value = text;
    }
  } finally {
    if (controller === active) {
      busy.value = false;
      controller = undefined;
    }
    await nextTick();
    if (history.value) history.value.scrollTop = history.value.scrollHeight;
  }
}
function clear() {
  turnPage.value = 1;
  turnTotal.value = 0;
  historyRevision++;
  stop();
  messages.value = [];
  error.value = '';
  conversation.value = '';
  showHistory.value = false;
  selectedEquipment.value = '';
  equipmentOptions.value = [];
  question.value = '';
}
onMounted(() => {
  try {
    const p = JSON.parse(localStorage.getItem(key.value) || 'null');
    if (p && Number.isFinite(p.x) && Number.isFinite(p.y)) {
      anchored = false;
      x.value = p.x;
      y.value = p.y;
    }
  } catch {}
  clamp();
  window.addEventListener('resize', clamp);
});
onBeforeUnmount(() => {
  emit('layout', { open: false, overlay: false });
  stop();
  window.removeEventListener('resize', clamp);
});
</script>
<template>
  <ElButton
    v-show="!open"
    class="xiaozhu-float"
    :style="{ left: x + 'px', top: y + 'px' }"
    :aria-label="tr('xiaozhu.open')"
    @pointerdown="down"
    @pointermove="move"
    @pointerup="up"
    @pointercancel="gesture = undefined"
    @click="toggle"
  >
    <AppIcon name="Help" /><span v-if="!props.collapsed">{{ tr('xiaozhu.name') }}</span>
  </ElButton>
  <aside
    v-if="open"
    class="xiaozhu-panel"
    :class="{ 'is-wide': wide }"
    :aria-label="tr('xiaozhu.name')"
  >
    <header>
      <strong>{{ tr('xiaozhu.name') }}</strong>
      <div>
        <ElButton text :disabled="busy" @click="clear">{{ tr('xiaozhu.new') }}</ElButton
        ><ElButton text @click="wide = !wide">{{
          tr(wide ? 'xiaozhu.restore' : 'xiaozhu.expand')
        }}</ElButton
        ><ElButton text :aria-label="tr('xiaozhu.close')" @click="open = false"
          ><AppIcon name="Close"
        /></ElButton>
      </div>
    </header>
    <div ref="history" class="xiaozhu-messages" aria-live="polite">
      <ElButton
        v-if="conversation && turnPage * 50 < turnTotal"
        text
        :disabled="busy"
        @click="selectSession(conversation, true)"
        >{{ tr('xiaozhu.older') }}</ElButton
      >
      <PagedChoiceField
        :model-value="selectedEquipment"
        :options="equipmentOptions"
        :disabled="busy"
        :load-rows="equipmentRows"
        :placeholder="tr('xiaozhu.chooseEquipment')"
        :columns="[
          { key: 'code', label: tr('xiaozhu.equipmentCode') },
          { key: 'name', label: tr('xiaozhu.equipmentName') },
          { key: 'model', label: tr('xiaozhu.equipmentModel') },
        ]"
        @select="selectEquipment"
      />
      <small>{{ tr('xiaozhu.contextNotice') }}</small>
      <ElButton text :disabled="busy" @click="listSessions">{{ tr('xiaozhu.history') }}</ElButton>
      <section v-if="showHistory">
        <div v-for="s in sessions" :key="s.id">
          <ElButton text :disabled="busy" @click="selectSession(s.id)">{{ s.title }}</ElButton
          ><ElButton text :disabled="busy" @click="deleteSession(s.id)">{{
            tr('xiaozhu.delete')
          }}</ElButton>
        </div>
        <ElButton
          :disabled="historyPage <= 1"
          @click="
            historyPage--;
            listSessions();
          "
          >{{ tr('xiaozhu.previous') }}</ElButton
        ><ElButton
          :disabled="historyPage * 20 >= sessionTotal"
          @click="
            historyPage++;
            listSessions();
          "
          >{{ tr('xiaozhu.next') }}</ElButton
        >
      </section>
      <p v-if="!messages.length" class="xiaozhu-intro">{{ tr('xiaozhu.intro') }}</p>
      <article v-for="(m, index) in messages" :key="index" :class="m.role">
        <b>{{ tr(m.role === 'user' ? 'xiaozhu.you' : 'xiaozhu.name') }}</b>
        <p>{{ m.text }}</p>
        <KnowledgeWorkInbox
          v-for="(result, resultIndex) in m.workInboxes"
          :key="'inbox-' + resultIndex"
          :result="result"
          @navigate="open = false"
        />
        <KnowledgeBusinessQuery
          v-for="(result, resultIndex) in m.businessQueries"
          :key="resultIndex"
          :result="result"
          @navigate="open = false"
        />
        <KnowledgeEquipmentRecords
          v-if="m.equipmentRecords"
          :result="m.equipmentRecords"
          @navigate="open = false"
        />
        <KnowledgeRepairRecords
          v-if="m.repairRecords"
          :result="m.repairRecords"
          @navigate="open = false"
        />
        <AnalyticsWidget
          v-if="m.analytics"
          :metric="m.analytics.metric"
          :result="m.analytics.result"
          :interactive="true"
          @drill="drill(m.analytics!, $event)"
        />
        <details v-for="source in m.citations" :key="source.chunkId">
          <summary>
            {{ source.title }} ·
            {{
              source.hasOriginal === false
                ? tr('knowledge.case')
                : tr('knowledge.page', { page: source.pageNo })
            }}
          </summary>
          <p>{{ source.excerpt }}</p>
          <ElButton
            v-if="source.versionId && source.hasOriginal !== false"
            text
            @click="previewSource = source"
            >{{ tr('xiaozhu.viewPage') }}</ElButton
          >
          <ElButton
            v-if="source.repairOrderId"
            text
            @click="router.push({ name: 'repair-detail', params: { id: source.repairOrderId } })"
            >{{ tr('knowledge.sourceOrder') }}</ElButton
          >
        </details>
      </article>
      <p v-if="busy">{{ tr('xiaozhu.thinking') }}</p>
      <ElAlert v-if="error" :title="error" type="error" :closable="false" />
    </div>
    <footer>
      <small>{{ tr('xiaozhu.notice') }}</small
      ><ElInput
        v-model="question"
        type="textarea"
        :rows="3"
        maxlength="2000"
        :disabled="busy"
        :placeholder="tr('xiaozhu.placeholder')"
        @keydown.ctrl.enter.prevent="ask"
      />
      <div>
        <ElButton v-if="busy" @click="stop">{{ tr('xiaozhu.stop') }}</ElButton
        ><ElButton type="primary" :disabled="busy || !question.trim()" @click="ask">{{
          tr('xiaozhu.send')
        }}</ElButton>
      </div>
    </footer>
  </aside>
  <KnowledgeSourcePreview :source="previewSource" @close="previewSource = undefined" />
</template>
<style scoped>
.xiaozhu-float {
  height: auto;
  position: fixed;
  z-index: 1800;
  display: flex;
  align-items: center;
  gap: 8px;
  border: 1px solid var(--brand);
  background: var(--surface, #fff);
  color: var(--brand);
  border-radius: 24px;
  padding: 12px;
  box-shadow: 0 3px 12px #0002;
  cursor: grab;
  touch-action: none;
  font: inherit;
}
.xiaozhu-float:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: 3px;
}
.xiaozhu-panel {
  position: fixed;
  right: 16px;
  bottom: 16px;
  width: min(440px, calc(100vw - 32px));
  height: 600px;
  max-height: calc(100dvh - 88px);
  z-index: 1801;
  display: flex;
  flex-direction: column;
  background: var(--surface, #fff);
  color: var(--text);
  border: 1px solid var(--line);
  border-radius: 8px;
  box-shadow: 0 4px 20px #0002;
}
.xiaozhu-panel.is-wide {
  inset: 8px;
  width: auto;
  height: auto;
  max-height: none;
}
.xiaozhu-panel header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  border-bottom: 1px solid var(--line);
}
.xiaozhu-messages {
  flex: 1;
  overflow: auto;
  min-height: 0;
  padding: 16px;
}
.xiaozhu-messages article {
  padding: 12px 0;
  border-bottom: 1px solid var(--line);
}
.xiaozhu-messages p {
  white-space: pre-wrap;
  overflow-wrap: anywhere;
  line-height: 1.7;
  margin: 8px 0;
}
.xiaozhu-messages .user {
  color: var(--brand);
}
.xiaozhu-messages details {
  margin-top: 8px;
  border-left: 2px solid var(--brand);
  padding-left: 8px;
}
.xiaozhu-messages summary {
  cursor: pointer;
}
.xiaozhu-panel footer {
  padding: 12px;
  border-top: 1px solid var(--line);
  display: grid;
  gap: 8px;
}
.xiaozhu-panel footer > div:last-child {
  text-align: right;
}
.xiaozhu-panel small {
  line-height: 1.6;
  color: var(--text-secondary);
}
@media (max-width: 600px) {
  .xiaozhu-panel,
  .xiaozhu-panel.is-wide {
    inset: 0;
    width: 100%;
    height: 100dvh;
    max-height: none;
    border-radius: 0;
  }
}
</style>
