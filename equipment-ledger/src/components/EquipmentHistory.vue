<script setup lang="ts">
import 'element-plus/es/components/tabs/style/css';
import { ElTabs, ElTabPane, ElTableColumn, ElTag } from 'element-plus';
import AppAttachmentList from './common/AppAttachmentList.vue';
import EquipmentOverview from './common/EquipmentOverview.vue';
import EquipmentEvents from './common/EquipmentEvents.vue';
import { archiveState, statusType, type ArchiveState, type ArchiveTab } from '../equipmentOverview';
import { useAuthStore } from '../stores/auth';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import { formatNumber } from '../utils/number';
import AppBackButton from './common/AppBackButton.vue';
import { computed, nextTick, ref, watch, onBeforeUnmount } from 'vue';
import { equipmentHistory, type EquipmentHistory } from '../api';
const props = defineProps<{ id: string }>();
const emit = defineEmits<{ close: [] }>();
const record = ref<EquipmentHistory | null>(null),
  error = ref('');
const body = ref<HTMLElement>();
const auth = useAuthStore();
const view = ref<ArchiveState>(archiveState());
const tab = computed({
  get: () => view.value.tab,
  set: (value: ArchiveTab) => {
    view.value.tab = value;
  },
});
const tabs: ArchiveTab[] = ['概览', '基本信息', '维修', '保养', '巡检', '备件', '动态'];
const storageKey = computed(
  () => `eam-equipment-archive:${auth.user?.tenantId}:${auth.user?.id}:${props.id}`,
);
let revision = 0;
let pendingScrollRestore = true;
onBeforeUnmount(() => revision++);
watch(
  view,
  (value) => {
    try {
      sessionStorage.setItem(storageKey.value, JSON.stringify(value));
    } catch {
      /* Unavailable storage only disables restoration. */
    }
  },
  { deep: true },
);
async function restoreScroll() {
  if (!record.value || !pendingScrollRestore) return;
  await nextTick();
  if (!pendingScrollRestore || !body.value) return;
  body.value.scrollTop = view.value.scroll[tab.value] || 0;
  pendingScrollRestore = false;
}
watch(tab, () => {
  pendingScrollRestore = true;
  if (tab.value !== '概览' && tab.value !== '动态') void restoreScroll();
});
function rememberScroll(event: Event) {
  if (record.value && !pendingScrollRestore)
    view.value.scroll[tab.value] = (event.target as HTMLElement).scrollTop;
}
async function navigate(value: ArchiveTab, section?: string) {
  tab.value = value;
  await nextTick();
  const target = section && body.value?.querySelector(`[data-equipment-section="${section}"]`);
  if (target && body.value) {
    // Explicit navigation takes precedence over restoring this tab's last scroll position.
    pendingScrollRestore = false;
    body.value.scrollTo({
      top:
        body.value.scrollTop +
        target.getBoundingClientRect().top -
        body.value.getBoundingClientRect().top -
        16,
      behavior: 'smooth',
    });
  }
}

const labels: Record<string, string> = {
  code: '设备编号',
  name: '设备名称',
  assetNo: '资产编号',
  tagCode: '电子标签码',
  serial: '出厂编号',
  category: '设备分类',
  model: '规格型号',
  brand: '品牌',
  unit: '单位',
  company: '所属公司',
  department: '所属部门',
  owner: '负责人',
  functionLocation: '位置',
  state: '设备状态',
  useState: '使用状态',
  source: '设备来源',
  supplier: '供应商',
  amount: '采购金额',
  purchaseDate: '购置日期',
  acquiredAt: '入账日期',
  inServiceDate: '启用日期',
  warranty: '保修期至',
  retirementDate: '预计报废日期',
  level: '设备等级',
  specialEquipment: '特种设备',
  metrologyEquipment: '计量设备',
  depreciationEnabled: '开启折旧',
  technicalParams: '技术参数',
  remark: '备注',
  imageName: '设备图片',
};
watch(
  () => props.id,
  async (id) => {
    const seq = ++revision;
    pendingScrollRestore = true;
    record.value = null;
    error.value = '';
    try {
      view.value = archiveState(JSON.parse(sessionStorage.getItem(storageKey.value) || '{}'));
    } catch {
      view.value = archiveState();
    }
    try {
      const result = await equipmentHistory(id);
      if (seq !== revision) return;
      record.value = result;
      await nextTick();
      if (tab.value !== '概览' && tab.value !== '动态') await restoreScroll();
    } catch (e) {
      if (seq === revision) error.value = e instanceof Error ? e.message : '加载失败';
    }
  },
  { immediate: true },
);
function records() {
  if (!record.value) return [];
  return tab.value === '维修'
    ? record.value.repairs
    : tab.value === '保养'
      ? record.value.maintenance
      : tab.value === '巡检'
        ? record.value.inspections
        : record.value.spareChanges;
}
function time(value: string) {
  return value ? value.replace('T', ' ').slice(0, 19) : '';
}
</script>
<template>
  <section class="equipment-history">
    <header class="equipment-history-heading">
      <AppBackButton label="返回设备台账" @click="emit('close')" />
      <template v-if="record"
        ><h1 :title="record.data.name">{{ record.data.name }}</h1>
        <span class="equipment-history-code">{{ record.data.code }}</span>
        <div class="equipment-history-statuses">
          <ElTag
            v-if="record.data.state"
            :type="statusType(record.data.state)"
            :title="`设备状态：${record.data.state}`"
            :class="['equipment-state', statusType(record.data.state)]"
            >{{ record.data.state }}</ElTag
          ><ElTag
            v-if="record.data.useState"
            :type="statusType(record.data.useState)"
            :title="`使用状态：${record.data.useState}`"
            effect="light"
            >{{ record.data.useState }}</ElTag
          >
        </div></template
      >
      <strong v-else>设备档案</strong>
    </header>
    <ElTabs class="history-tabs" v-model="tab"
      ><ElTabPane v-for="item in tabs" :key="item" :label="item" :name="item"
    /></ElTabs>
    <div ref="body" class="history-body" @scroll="rememberScroll">
      <p v-if="error" class="history-error">{{ error }}</p>
      <p v-else-if="!record">正在加载…</p>
      <template v-else>
        <EquipmentOverview
          v-if="tab === '概览'"
          :equipment="record.data"
          v-model:state="view.attention"
          @navigate="navigate"
          @ready="restoreScroll"
        />
        <EquipmentEvents
          v-else-if="tab === '动态'"
          :equipment-id="id"
          v-model:state="view.events"
          @ready="restoreScroll"
          full
        />
        <template v-else-if="tab === '基本信息'"
          ><section class="history-equipment-identity">
            <div>
              <h3>{{ record.data.name }}</h3>
              <p>{{ record.data.code }}</p>
            </div>
            <figure class="history-qrcode">
              <img
                :src="`/api/v1/equipment/${record.data.id}/qrcode`"
                :alt="`设备二维码：${record.data.code}`"
              />
            </figure>
          </section>
          <dl class="history-info">
            <div
              v-for="(label, key) in labels"
              :key="key"
              :class="{ wide: key === 'remark' || key === 'technicalParams' }"
            >
              <dt>{{ label }}</dt>
              <dd
                v-if="
                  key === 'imageName' && (record.data.images?.length || record.data.imageFileId)
                "
              >
                <AppAttachmentList
                  :files="
                    (record.data.images?.length
                      ? record.data.images
                      : [{ id: record.data.imageFileId!, name: '' }]
                    ).map((image) => ({
                      ...image,
                      isImage: true,
                      url: `/api/v1/equipment/files/${image.id}/preview`,
                    }))
                  "
                />
              </dd>
              <dd v-else>
                {{
                  key === 'amount'
                    ? formatNumber(record.data.amount)
                    : (record.data as any)[key] || ''
                }}
              </dd>
            </div>
          </dl>
          <section
            v-for="(label, key) in {
              documents: '设备相关文件',
              children: '子设备',
              relatedSpares: '设备BOM',
            }"
            class="history-related"
            :data-equipment-section="key"
          >
            <h3>{{ label }}</h3>
            <AppBusinessTable
              :pagination="false"
              :fill="false"
              :framed="false"
              v-if="key === 'documents' &amp;&amp; (record.data as any)[key]?.length"
              :rows="record.data.documents || []"
              class="business-table"
              ><ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">资料编号</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.code || ''
                }}</template></ElTableColumn
              ><ElTableColumn :min-width="210"
                ><template #header><div class="table-header-label">资料名称</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.name
                }}</template></ElTableColumn
              ><ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">资料类型</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.type || ''
                }}</template></ElTableColumn
              ><ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">文档密级</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  item.security || '普通'
                }}</template></ElTableColumn
              ><ElTableColumn :min-width="150"
                ><template #header><div class="table-header-label">数量</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">{{
                  formatNumber(item.quantity || 1)
                }}</template></ElTableColumn
              ><ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
                ><template #header><div class="table-header-label">操作</div></template
                ><template #default="{ row: item, $index: tableRowIndex }">
                  <a
                    v-if="item.id"
                    :href="`/api/v1/equipment/files/${item.id}`"
                    target="_blank"
                    rel="noopener"
                    >下载</a
                  ><span v-else>未上传文件</span>
                </template></ElTableColumn
              ></AppBusinessTable
            ><AppBusinessTable
              v-else-if="(record.data as any)[key]?.length"
              :rows="(record.data as any)[key] as Record<string, unknown>[]"
              :columns="
                key === 'children'
                  ? [
                      { prop: 'code', label: '设备编号', minWidth: 160 },
                      { prop: 'name', label: '设备名称', minWidth: 180 },
                      { prop: 'model', label: '规格型号', minWidth: 150 },
                      { prop: 'brand', label: '品牌', minWidth: 120 },
                    ]
                  : [
                      { prop: 'code', label: '料号', minWidth: 160 },
                      { prop: 'name', label: '物料名称', minWidth: 180 },
                      { prop: 'specification', label: '规格型号', minWidth: 150 },
                      { prop: 'unit', label: '单位', minWidth: 90 },
                      { prop: 'quantity', label: '数量', minWidth: 100 },
                    ]
              "
              :fill="false"
              :pagination="false"
              :framed="false"
            >
              <template #name="{ row }"
                >{{ row.name }}<small v-if="row.legacy">（历史关联待确认）</small></template
              >
            </AppBusinessTable>
            <p v-else>
              {{
                key === 'relatedSpares'
                  ? '暂无设备BOM'
                  : key === 'children'
                    ? '暂无子设备'
                    : '暂无记录'
              }}
            </p>
          </section></template
        >
        <div v-else-if="tab === '备件'" class="history-table">
          <h3>在用备件</h3>
          <AppBusinessTable
            :rows="record.installedSpares"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table"
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">备件编号</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                item.code
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">备件名称</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                item.name
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">规格型号</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                item.model
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">数量</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                formatNumber(item.quantity)
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">安装时间</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                item.installedAt
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">状态</div></template
              ><template #default="{ row: item, $index: tableRowIndex }">{{
                item.status
              }}</template></ElTableColumn
            ><template #empty
              ><div v-if="!record?.installedSpares?.length">暂无在用备件</div></template
            ></AppBusinessTable
          >
          <h3 class="spare-history-heading">备件更换记录</h3>
          <AppBusinessTable
            :rows="record.spareChanges"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table"
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">工单编号</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.number || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">时间</div></template
              ><template #default="{ row: item, $index: index }">{{
                time(item.time)
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">更换内容 / 数量</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.description || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">执行人</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.person || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">状态</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.status || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">说明</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.result || ''
              }}</template></ElTableColumn
            ><template #empty
              ><div v-if="!record.spareChanges.length">暂无备件更换记录</div></template
            ></AppBusinessTable
          >
        </div>
        <div v-else-if="!['概览', '动态'].includes(tab)" class="history-table">
          <AppBusinessTable
            :rows="records()"
            :pagination="false"
            :fill="false"
            :framed="false"
            class="business-table"
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">记录编号</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.number || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">时间</div></template
              ><template #default="{ row: item, $index: index }">{{
                time(item.time)
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="210"
              ><template #header><div class="table-header-label">内容</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.description || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">执行人</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.person || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">状态</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.status || ''
              }}</template></ElTableColumn
            ><ElTableColumn :min-width="150"
              ><template #header><div class="table-header-label">结果 / 措施</div></template
              ><template #default="{ row: item, $index: index }">{{
                item.result || ''
              }}</template></ElTableColumn
            ><template #empty
              ><div v-if="!records().length">暂无{{ tab }}记录</div></template
            ></AppBusinessTable
          >
        </div>
      </template>
    </div>
  </section>
</template>
<style scoped>
.history-tabs {
  background: var(--surface);
  flex: none;
  padding: 0 24px;
}
.history-tabs :deep(.el-tabs__header) {
  margin: 0;
}
.history-tabs :deep(.el-tabs__item) {
  height: 40px;
  font-size: 13px;
}

.equipment-history {
  position: absolute;
  inset: 48px 0 0;
  z-index: 30;
  background: var(--canvas);
  display: flex;
  flex-direction: column;
  font-size: 13px;
  color: var(--text);
}
.equipment-history > .equipment-history-heading {
  min-height: 60px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e9ef;
}
.equipment-history-heading {
  background: var(--surface);
}
.equipment-history-heading h1 {
  font-size: 18px;
  font-weight: 650;
  margin: 0;
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.equipment-history-code {
  font-size: 13px;
  white-space: nowrap;
  color: var(--text);
}
.equipment-history-statuses {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
  flex: none;
}
.equipment-state.success {
  color: #11934a;
  background: #e8faf0;
  border-color: #d1f2de;
}
.equipment-state.warning {
  color: #c66a00;
  background: #fff1d6;
  border-color: #ffe4ae;
}
.equipment-state.danger {
  color: #e33535;
  background: #ffe8e8;
  border-color: #ffd1d1;
}
.history-body {
  overflow: auto;
  padding: 8px;
  flex: 1;
  min-height: 0;
  min-width: 0;
}
.history-info {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 22px 28px;
  margin: 0;
}
.history-info div {
  display: flex;
  gap: 16px;
  min-width: 0;
}
.history-info dt {
  width: 90px;
  flex-shrink: 0;
  color: #718096;
  text-align: right;
}
.history-info dd {
  margin: 0;
  white-space: pre-wrap;
  overflow-wrap: anywhere;
}
.history-info .wide {
  grid-column: 1/-1;
}
.history-related {
  margin-top: 26px;
  border-top: 1px solid #edf0f4;
  padding-top: 12px;
}
.history-related h3 {
  font-size: 13px;
}
.history-related p {
  color: #94a3b8;
}
.history-table table,
.history-related table {
  width: 100%;
  border-collapse: collapse;
}
.history-error {
  color: #dc3545;
}
.history-equipment-identity {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
  border-bottom: 1px solid #e2e7ee;
  padding: 0 0 14px;
  margin-bottom: 16px;
  color: #263246;
}
.history-equipment-identity h3 {
  margin: 0 0 8px;
  font-size: 16px;
}
.history-equipment-identity p {
  margin: 0;
  font-size: 13px;
}
.history-qrcode {
  margin: 0;
  text-align: center;
  flex: 0 0 auto;
}
.history-qrcode img {
  display: block;
  width: 96px;
  height: 96px;
}
.history-qrcode figcaption {
  font-size: 13px;
  margin-top: 4px;
}
.history-table h3 {
  font-size: 13px;
  margin: 0 0 12px;
}
.history-table .spare-history-heading {
  margin-top: 28px;
}
</style>
