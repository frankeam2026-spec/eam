<script setup lang="ts">import { tr } from '../../i18n';

import { computed, ref, watch, onBeforeUnmount, nextTick } from 'vue';
import { useRouter } from 'vue-router';
import { ElSelect, ElOption, ElButton, ElImage, ElTableColumn, ElSkeleton } from 'element-plus';
import 'element-plus/es/components/image/style/css';
import 'element-plus/es/components/skeleton/style/css';
import type { EquipmentRecord } from '../../api';
import { formatOrganizationPath } from '../../utils/organizationPath';
import { normalizeLocationPath } from '../../utils/locationPath';
import {
  equipmentOverviewApi,
  type EquipmentOverviewData,
  type AttentionItem,
  type AttentionKind,
  type AttentionState,
  type ArchiveTab,
} from '../../equipmentOverview';
import MaintenanceStatus from './MaintenanceStatus.vue';
import EquipmentEvents from './EquipmentEvents.vue';
import EquipmentImage from './EquipmentImage.vue';
import AppBusinessTable from './AppBusinessTable.vue';
import AppPagination from './AppPagination.vue';
import AppIcon from './AppIcon.vue';
import { formatDateTime } from '../../utils/dateTime';
import { formatNumber } from '../../utils/number';
const props = defineProps<{ equipment: EquipmentRecord }>();
const state = defineModel<AttentionState>('state', { required: true });
const emit = defineEmits<{ navigate: [tab: ArchiveTab, section?: string]; ready: [] }>();
const router = useRouter();
const overview = ref<EquipmentOverviewData>(),
  items = ref<AttentionItem[]>([]),
  total = ref(0),
  accessible = ref(true);
const loading = ref(false),
  attentionLoading = ref(false),
  error = ref(''),
  attentionError = ref('');
let revision = 0,
  attentionRevision = 0;
onBeforeUnmount(() => {
  revision++;
  attentionRevision++;
});
const kinds: { key: Exclude<AttentionKind, 'all'>; label: string; icon: string }[] = [
  { key: 'repair', get label() { return tr('ui.equipment.text1374'); }, icon: 'Tool' },
  { key: 'maintenance', get label() { return tr('ui.equipment.text1375'); }, icon: 'Clock' },
  { key: 'inspection', get label() { return tr('ui.equipment.text1376'); }, icon: 'Check' },
  { key: 'anomaly', get label() { return tr('ui.equipment.text1377'); }, icon: 'Warning' },
];
const coverUrl = computed(() => {
  const id = props.equipment.images?.[0]?.id || props.equipment.imageFileId;
  return id ? `/api/v1/equipment/files/${id}/preview` : '';
});
const owners = computed(() =>
  props.equipment.owners?.length
    ? props.equipment.owners.map((u) => u.name).join('、')
    : props.equipment.owner,
);
const metadata = computed(() => [
  ['规格型号', props.equipment.model],
  ['设备分类', props.equipment.category],
  ['品牌', props.equipment.brand],
  [
    '所属部门',
    formatOrganizationPath(
      props.equipment.departmentPath ||
        [props.equipment.company, props.equipment.department].filter(Boolean).join(' > '),
    ),
  ],
  ['位置', normalizeLocationPath(props.equipment.functionLocation)],
  ['负责人', owners.value],
]);
const related = computed(() => [
  { key: 'children', get label() { return tr('ui.equipment.text0148'); }, icon: 'Organization', rows: props.equipment.children || [] },
  {
    key: 'relatedSpares',
    get label() { return tr('ui.equipment.text0153'); },
    icon: 'Warehouse',
    rows: props.equipment.relatedSpares || [],
  },
  { key: 'documents', get label() { return tr('ui.equipment.text1378'); }, icon: 'Document', rows: props.equipment.documents || [] },
]);
const eventsReady = ref(false);
watch(
  () => !loading.value && !attentionLoading.value && eventsReady.value,
  async (ready) => {
    if (ready) {
      await nextTick();
      emit('ready');
    }
  },
);
const anomaly = computed(() => state.value.kind === 'anomaly');
async function loadOverview() {
  const seq = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const result = await equipmentOverviewApi.overview(props.equipment.id);
    if (seq === revision) overview.value = result;
  } catch (e) {
    if (seq === revision) error.value = e instanceof Error ? e.message : tr('ui.equipment.text1379');
  } finally {
    if (seq === revision) loading.value = false;
  }
}
async function loadAttention() {
  const seq = ++attentionRevision;
  attentionLoading.value = true;
  attentionError.value = '';
  try {
    const result = await equipmentOverviewApi.attention(props.equipment.id, {
      ...state.value,
      page: state.value.expanded ? state.value.page : 1,
      size: state.value.expanded ? state.value.size : 5,
    });
    if (seq !== attentionRevision) return;
    if (
      state.value.expanded &&
      result.total &&
      state.value.page > Math.ceil(result.total / state.value.size)
    ) {
      state.value.page = 1;
      return;
    }
    items.value = result.data;
    total.value = result.total;
    accessible.value = result.accessible;
  } catch (e) {
    if (seq === attentionRevision)
      attentionError.value = e instanceof Error ? e.message : tr('ui.equipment.text1380');
  } finally {
    if (seq === attentionRevision) attentionLoading.value = false;
  }
}
function selectKind(kind: AttentionKind) {
  state.value = { ...state.value, kind, page: 1 };
}
watch(
  () => props.equipment.id,
  () => {
    overview.value = undefined;
    eventsReady.value = false;
    void loadOverview();
  },
  { immediate: true },
);
watch(
  () => [props.equipment.id, JSON.stringify(state.value)],
  () => {
    items.value = [];
    total.value = 0;
    void loadAttention();
  },
  { immediate: true },
);
</script>
<template>
  <div class="asset-overview">
    <section class="equipment-identity-panel" :aria-label="tr('ui.equipment.text1381')">
      <figure class="equipment-cover">
        <ElImage
          v-if="coverUrl"
          :src="coverUrl"
          :alt="equipment.name"
          fit="contain"
          :preview-src-list="[coverUrl]"
          preview-teleported
        >
          <template #error><EquipmentImage :name="equipment.name" /></template>
        </ElImage>
        <EquipmentImage v-else :name="equipment.name" />
      </figure>
      <dl class="equipment-key-facts">
        <div v-for="[label, value] in metadata" :key="label">
          <dt>{{ label }}</dt>
          <dd :title="value || ''">{{ value || tr('ui.equipment.text1382') }}</dd>
        </div>
      </dl>
      <figure class="equipment-qr-content" :aria-label="tr('ui.equipment.text1383')">
        <img
          :src="`/api/v1/equipment/${equipment.id}/qrcode`"
          :alt="`设备二维码：${equipment.code}`"
        />
        <!-- <figcaption>{{ equipment.code }}</figcaption> -->
      </figure>
    </section>
    <section class="equipment-attention-strip" :aria-label="tr('ui.equipment.text1384')" :aria-busy="loading">
      <template v-if="loading"><ElSkeleton :rows="1" animated /></template>
      <div v-else-if="error" class="equipment-inline-error" role="alert">
        {{ error }}<ElButton link type="primary" @click="loadOverview">{{ tr('ui.equipment.text0299') }}</ElButton>
      </div>
      <template v-for="item in overview ? kinds : []" :key="item.key">
        <ElButton
          v-if="overview?.access[item.key]"
          text
          class="attention-counter"
          :class="[item.key, { selected: state.kind === item.key }]"
          @click="selectKind(item.key)"
        >
          <span class="counter-icon"><AppIcon :name="item.icon" /></span
          ><span class="counter-label">{{ item.label }}</span
          ><strong>{{ overview.counts[item.key] }}</strong>
        </ElButton>
        <div v-else class="attention-counter unavailable" :class="item.key">
          <span class="counter-icon"><AppIcon :name="item.icon" /></span
          ><span class="counter-label">{{ item.label }}</span
          ><strong>{{ tr('ui.equipment.text1385') }}</strong>
        </div>
      </template>
    </section>
    <div class="equipment-overview-columns">
      <div class="equipment-main-column">
        <section class="equipment-panel current-attention" :aria-busy="attentionLoading">
          <header class="equipment-panel-heading">
            <h3>
              <AppIcon name="Todo" />{{ tr('ui.equipment.text1386') }}<span
                v-if="!attentionLoading && accessible"
                class="panel-count"
                >{{ total }}</span
              >
            </h3>
            <div class="equipment-panel-actions">
              <ElSelect
                :model-value="state.kind"
                :aria-label="tr('ui.equipment.text1387')"
                @update:model-value="selectKind($event)"
                ><ElOption value="all" :label="tr('ui.equipment.text1388')" /><ElOption
                  v-for="item in kinds.filter((k) => overview?.access[k.key])"
                  :key="item.key"
                  :value="item.key"
                  :label="item.label"
              /></ElSelect>
              <ElButton
                link
                type="primary"
                @click="state = { ...state, expanded: !state.expanded, page: 1 }"
                >{{ state.expanded ? tr('ui.equipment.text0725') : tr('ui.equipment.text0892')
                }}<AppIcon :name="state.expanded ? 'Up' : 'Right'"
              /></ElButton>
            </div>
          </header>
          <div v-if="attentionError" class="equipment-inline-error" role="alert">
            {{ attentionError }}<ElButton link type="primary" @click="loadAttention">{{ tr('ui.equipment.text0299') }}</ElButton>
          </div>
          <ElSkeleton v-else-if="attentionLoading" :rows="2" animated />
          <p v-else-if="!accessible" class="equipment-panel-empty">{{ tr('ui.equipment.text1389') }}</p>
          <template v-else>
            <AppBusinessTable
              v-if="items.length"
              :rows="items"
              :pagination="false"
              :fill="false"
              :framed="false"
              class="attention-table"
            >
              <ElTableColumn :label="tr('ui.equipment.text1390')" :min-width="210" show-overflow-tooltip
                ><template #default="{ row }"
                  ><div class="attention-identity">
                    <span class="attention-type" :class="row.kind">{{
                      row.kind === 'anomaly' ? tr('ui.equipment.text0210') : row.source
                    }}</span
                    ><ElButton link type="primary" @click="router.push(row.url)">{{
                      row.code
                    }}</ElButton>
                  </div>
                  <div class="attention-summary" :title="row.summary">
                    {{ row.summary }}
                  </div></template
                ></ElTableColumn
              >
              <ElTableColumn :label="tr('ui.equipment.text0170')" :width="90" align="center"
                ><template #default="{ row }"><MaintenanceStatus :status="row.status" /></template
              ></ElTableColumn>
              <ElTableColumn
                v-if="!anomaly"
                :label="tr('ui.equipment.text0102')"
                :width="96"
                show-overflow-tooltip
                prop="owner"
              />
              <ElTableColumn v-if="!anomaly" :label="tr('ui.equipment.text1391')" :width="144"
                ><template #default="{ row }"
                  ><span :class="{ 'overdue-time': row.overdue }">{{
                    row.deadline ? formatDateTime(row.deadline) : ''
                  }}</span
                  ><span v-if="row.overdue" class="overdue-label">{{ tr('ui.equipment.text1392') }}</span></template
                ></ElTableColumn
              >
              <ElTableColumn
                v-if="anomaly"
                :label="tr('ui.equipment.text1393')"
                :width="85"
                prop="source"
                show-overflow-tooltip
              />
              <ElTableColumn v-if="anomaly" :label="tr('ui.equipment.text1394')" :width="160"
                ><template #default="{ row }"
                  ><div>{{ formatDateTime(row.createdAt) }}</div>
                  <div class="attention-summary">{{ row.owner }}</div></template
                ></ElTableColumn
              >
            </AppBusinessTable>
            <p v-else class="equipment-panel-empty">
              {{
                anomaly
                  ? tr('ui.equipment.text1395')
                  : state.kind === 'all'
                    ? tr('ui.equipment.text1396')
                    : tr('ui.equipment.text1397')
              }}
            </p>
            <footer v-if="state.expanded && total" class="equipment-attention-footer">
              <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
              ><AppPagination
                v-model:page="state.page"
                v-model:page-size="state.size"
                :total="total"
              />
            </footer>
          </template>
        </section>
        <EquipmentEvents
          :equipment-id="equipment.id"
          @ready="eventsReady = true"
          @all="emit('navigate', '动态')"
        />
      </div>
      <aside class="equipment-side-column">
        <section class="equipment-panel installed-panel">
          <header class="equipment-panel-heading">
            <h3>
              <AppIcon name="Warehouse" />{{ tr('ui.equipment.text0166') }}<span v-if="overview" class="panel-count">{{
                overview.installedTotal
              }}</span>
            </h3>
            <ElButton link type="primary" @click="emit('navigate', '备件')"
              >{{ tr('ui.equipment.text0892') }}<AppIcon name="Right"
            /></ElButton>
          </header>
          <ElSkeleton v-if="loading" :rows="2" animated />
          <p v-else-if="error" class="equipment-panel-empty">{{ tr('ui.equipment.text1398') }}</p>
          <AppBusinessTable
            v-else-if="overview?.installed.length"
            :rows="overview.installed"
            :pagination="false"
            :fill="false"
            :framed="false"
          >
            <ElTableColumn :label="tr('ui.equipment.text0515')" :min-width="160" show-overflow-tooltip
              ><template #default="{ row }"
                ><div class="installed-name" :title="row.name">{{ row.name }}</div>
                <div class="attention-summary">{{ row.code }}</div></template
              ></ElTableColumn
            >
            <ElTableColumn :label="tr('ui.equipment.text0144')" :width="82" align="right"
              ><template #default="{ row }">{{
                formatNumber(row.quantity)
              }}</template></ElTableColumn
            >
          </AppBusinessTable>
          <p v-else class="equipment-panel-empty">{{ tr('ui.equipment.text0171') }}</p>
        </section>
        <section class="equipment-panel related-panel">
          <header class="equipment-panel-heading">
            <h3><AppIcon name="Document" />{{ tr('ui.equipment.text1399') }}</h3>
          </header>
          <ElButton
            v-for="item in related"
            :key="item.key"
            text
            class="related-entry"
            @click="emit('navigate', '基本信息', item.key)"
            ><span class="related-entry-icon"><AppIcon :name="item.icon" /></span
            ><span class="related-entry-copy"
              ><strong
                >{{ item.label }}<span>{{ item.rows.length }}</span></strong
              ><small
                :title="
                  item.rows
                    .slice(0, 3)
                    .map((r) => r.name)
                    .join('、')
                "
                >{{
                  item.rows.length
                    ? item.rows
                        .slice(0, 3)
                        .map((r) => r.name)
                        .join('、')
                    : `暂无${item.label}`
                }}</small
              ></span
            ><AppIcon name="Right"
          /></ElButton>
        </section>
      </aside>
    </div>
  </div>
</template>
<style scoped>
.asset-overview {
  container-type: inline-size;
  display: grid;
  gap: 12px;
  color: var(--text);
}
.equipment-identity-panel {
  display: flex;
  align-items: center;
  gap: 22px;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 8px;
}
.equipment-cover {
  position: relative;
  width: 120px;
  height: 112px;
  flex: none;
  margin: 0;
  border-radius: 7px;
  background: #f3f6fb;
  overflow: hidden;
}
.equipment-cover > .el-image {
  display: block;
  width: 100%;
  height: 100%;
  cursor: zoom-in;
}
.equipment-cover :deep(.equipment-image) {
  display: flex;
  width: 100%;
  height: 100%;
  border: 0;
  border-radius: 0;
}
.equipment-cover :deep(.equipment-image .app-icon) {
  --app-icon-size: 40px;
  padding: 28px;
  box-sizing: border-box;
}
.equipment-key-facts {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px 22px;
  margin: 0;
}
.equipment-key-facts div {
  min-width: 0;
}
.equipment-key-facts dt {
  margin-bottom: 5px;
  font-size: 12px;
  color: var(--text);
}
.equipment-key-facts dd {
  margin: 0;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.equipment-qr-content {
  display: grid;
  justify-items: center;
  flex: 0 0 128px;
  gap: 6px;
  margin: 0 0 0 auto;
  color: var(--text);
}
.equipment-qr-content img {
  display: block;
  width: 112px;
  height: 112px;
  background: #fff;
  image-rendering: pixelated;
}
.equipment-qr-content figcaption {
  font-size: 12px;
  line-height: 18px;
}
.equipment-attention-strip {
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  display: flex;
  min-height: 66px;
  padding: 4px 8px;
  gap: 4px;
}
.attention-counter.el-button {
  height: auto;
  min-width: 0;
  flex: 1;
  padding: 10px 12px;
  margin: 0;
  border-radius: 6px;
  color: var(--text);
}
.attention-counter :deep(> span) {
  display: flex;
  align-items: center;
  width: 100%;
  gap: 10px;
}
.counter-icon {
  display: grid;
  place-items: center;
  width: 32px;
  height: 32px;
  border-radius: 8px;
  background: #edf3ff;
  color: #3478f6;
  flex: none;
}
.maintenance .counter-icon {
  background: #e8f9f3;
  color: #0d9b85;
}
.inspection .counter-icon {
  background: #f3edff;
  color: #9660dc;
}
.anomaly .counter-icon {
  background: #fff2df;
  color: #de851f;
}
.counter-label {
  white-space: nowrap;
}
.attention-counter strong {
  margin-left: auto;
  font-size: 23px;
  line-height: 28px;
  font-weight: 650;
}
.attention-counter.selected {
  background: var(--brand-soft);
  box-shadow: inset 0 0 0 1px var(--brand);
}
.attention-counter.unavailable {
  display: flex;
  flex: 1;
  gap: 10px;
  align-items: center;
  min-width: 0;
  padding: 10px 12px;
  color: var(--text);
}
.attention-counter.unavailable strong {
  font-size: 12px;
  font-weight: 400;
}
.equipment-overview-columns {
  display: grid;
  grid-template-columns: minmax(0, 2fr) minmax(280px, 1fr);
  gap: 12px;
  align-items: start;
}
.equipment-main-column,
.equipment-side-column {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.equipment-panel {
  min-width: 0;
  background: var(--surface);
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 16px;
}
.equipment-panel-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  margin-bottom: 12px;
}
.equipment-panel-heading h3 {
  display: flex;
  align-items: center;
  flex: none;
  gap: 7px;
  font-size: 14px;
  margin: 0;
}
.equipment-panel-heading h3 > .app-icon {
  color: var(--brand);
}
.panel-count {
  color: var(--text);
  font-size: 12px;
  background: var(--canvas);
  border-radius: 10px;
  padding: 1px 6px;
  font-weight: 500;
}
.equipment-panel-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.equipment-panel-actions .el-select {
  width: 132px;
}
.equipment-panel-heading .el-button {
  font-size: 12px;
}
.equipment-panel-empty {
  padding: 16px 0;
  margin: 0;
  line-height: 22px;
}
.equipment-inline-error {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  padding: 12px;
  gap: 10px;
  color: var(--el-color-danger);
}
.attention-identity {
  display: flex;
  align-items: center;
  gap: 6px;
  min-width: 0;
}
.attention-identity .el-button {
  font-size: 13px;
}
.attention-type {
  font-size: 11px;
  line-height: 18px;
  color: #2764d7;
  flex: none;
}
.attention-type.maintenance {
  color: #087e72;
}
.attention-type.inspection {
  color: #8049bc;
}
.attention-type.anomaly {
  color: #b5640a;
}
.attention-summary,
.installed-name {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
}
.attention-summary {
  font-size: 12px;
  line-height: 20px;
}
.equipment-attention-footer {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  border-top: 1px solid var(--line);
  padding-top: 12px;
  margin-top: 12px;
}
.overdue-time {
  color: var(--el-color-danger);
}
.overdue-label {
  display: block;
  color: var(--el-color-danger);
  font-size: 12px;
  line-height: 16px;
}
.related-entry.el-button {
  display: flex;
  height: auto;
  width: 100%;
  padding: 12px 0;
  margin: 0;
  border-bottom: 1px solid var(--line);
  border-radius: 0;
  text-align: left;
  color: var(--text);
}
.related-entry:last-child {
  border-bottom: 0;
  padding-bottom: 0;
}
.related-entry:first-of-type {
  padding-top: 2px;
}
.related-entry :deep(> span) {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  gap: 10px;
}
.related-entry-icon {
  display: grid;
  place-items: center;
  color: var(--brand);
  background: var(--brand-soft);
  width: 30px;
  height: 30px;
  border-radius: 6px;
  flex: none;
}
.related-entry-copy {
  display: grid;
  gap: 6px;
  flex: 1;
  min-width: 0;
}
.related-entry-copy strong {
  font-size: 13px;
  font-weight: 500;
}
.related-entry-copy strong span {
  margin-left: 8px;
}
.related-entry-copy small {
  overflow: hidden;
  white-space: nowrap;
  text-overflow: ellipsis;
  font-size: 12px;
}
@container (max-width: 959px) {
  .equipment-overview-columns {
    grid-template-columns: minmax(0, 1fr);
  }
  .equipment-side-column {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    align-items: start;
  }
  .equipment-key-facts {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 12px 16px;
  }
  .equipment-identity-panel {
    gap: 16px;
  }
  .equipment-cover {
    width: 112px;
    height: 112px;
  }
  .counter-label {
    font-size: 12px;
  }
  .attention-counter.el-button {
    padding: 8px;
  }
}
@container (max-width: 680px) {
  .equipment-identity-panel {
    flex-wrap: wrap;
  }
  .equipment-key-facts {
    flex-basis: calc(100% - 140px);
  }
  .equipment-attention-strip {
    display: grid;
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .equipment-side-column {
    grid-template-columns: minmax(0, 1fr);
  }
  .equipment-panel-heading {
    flex-wrap: wrap;
  }
}
</style>
