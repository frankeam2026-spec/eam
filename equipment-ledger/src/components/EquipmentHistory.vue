<script setup lang="ts">
import { formatNumber } from '../utils/number';
import AppBackButton from './common/AppBackButton.vue';
import { ref, watch } from 'vue';
import { equipmentHistory, type EquipmentHistory } from '../api';
const props = defineProps<{ id: string }>();
const emit = defineEmits<{ close: [] }>();
const record = ref<EquipmentHistory | null>(null),
  error = ref(''),
  tab = ref('基本信息');
const tabs = ['基本信息', '维修', '保养', '巡检', '备件'];
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
  functionLocation: '功能位置',
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
    record.value = null;
    error.value = '';
    try {
      record.value = await equipmentHistory(id);
    } catch (e) {
      error.value = e instanceof Error ? e.message : '加载失败';
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
  return value ? value.replace('T', ' ').slice(0, 19) : '—';
}
</script>
<template>
  <section class="equipment-history">
    <header>
      <AppBackButton label="返回设备台账" @click="emit('close')" /><strong>设备履历</strong
      ><span v-if="record">{{ record.data.code }} · {{ record.data.name }}</span
      ><button class="history-close" @click="emit('close')">×</button>
    </header>
    <nav>
      <button v-for="item in tabs" :class="{ active: tab === item }" @click="tab = item">
        {{ item }}
      </button>
    </nav>
    <div class="history-body">
      <p v-if="error" class="history-error">{{ error }}</p>
      <p v-else-if="!record">正在加载…</p>
      <template v-else>
        <template v-if="tab === '基本信息'"
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
              <figcaption>设备二维码</figcaption>
            </figure>
          </section>
          <dl class="history-info">
            <div
              v-for="(label, key) in labels"
              :key="key"
              :class="{ wide: key === 'remark' || key === 'technicalParams' }"
            >
              <dt>{{ label }}</dt>
              <dd v-if="key === 'imageName' && record.data.imageFileId">
                <a
                  :href="`/api/v1/equipment/files/${record.data.imageFileId}/preview`"
                  target="_blank"
                  rel="noopener"
                  ><img
                    class="history-equipment-image"
                    :src="`/api/v1/equipment/files/${record.data.imageFileId}/preview`"
                    alt="设备图片，点击查看原图"
                /></a>
              </dd>
              <dd v-else>
                {{
                  key === 'amount'
                    ? formatNumber(record.data.amount)
                    : (record.data as any)[key] || '—'
                }}
              </dd>
            </div>
          </dl>
          <section
            v-for="(label, key) in {
              documents: '设备相关文件',
              children: '子设备',
              relatedSpares: '关联备件',
            }"
            class="history-related"
          >
            <h3>{{ label }}</h3>
            <table class="business-table" v-if="(record.data as any)[key]?.length">
              <thead v-if="key === 'documents'">
                <tr>
                  <th>资料编号</th>
                  <th>资料名称</th>
                  <th>资料类型</th>
                  <th>文档密级</th>
                  <th>数量</th>
                  <th>操作</th>
                </tr>
              </thead>
              <tbody>
                <tr v-for="item in (record.data as any)[key]">
                  <template v-if="key === 'documents'">
                    <td>{{ item.code || '—' }}</td>
                    <td>{{ item.name }}</td>
                    <td>{{ item.type || '—' }}</td>
                    <td>{{ item.security || '普通' }}</td>
                    <td>{{ formatNumber(item.quantity || 1) }}</td>
                    <td>
                      <a
                        v-if="item.id"
                        :href="`/api/v1/equipment/files/${item.id}`"
                        target="_blank"
                        rel="noopener"
                        >下载</a
                      ><span v-else>未上传文件</span>
                    </td>
                  </template>
                  <td v-for="value in key === 'documents' ? [] : item">{{ value || '—' }}</td>
                </tr>
              </tbody>
            </table>
            <p v-else>暂无记录</p>
          </section></template
        >
        <div v-else-if="tab === '备件'" class="history-table">
          <h3>在用备件</h3>
          <table class="business-table">
            <thead>
              <tr>
                <th>备件编号</th>
                <th>备件名称</th>
                <th>规格型号</th>
                <th>数量</th>
                <th>安装时间</th>
                <th>状态</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in record?.installedSpares || []" :key="item.id">
                <td>{{ item.code }}</td>
                <td>{{ item.name }}</td>
                <td>{{ item.model }}</td>
                <td>{{ formatNumber(item.quantity) }}</td>
                <td>{{ item.installedAt }}</td>
                <td>{{ item.status }}</td>
              </tr>
              <tr v-if="!record?.installedSpares?.length">
                <td colspan="6" class="history-empty">暂无在用备件</td>
              </tr>
            </tbody>
          </table>
          <h3 class="spare-history-heading">备件更换记录</h3>
          <table class="business-table">
            <thead>
              <tr>
                <th>工单编号</th>
                <th>时间</th>
                <th>更换内容 / 数量</th>
                <th>执行人</th>
                <th>状态</th>
                <th>说明</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in record.spareChanges" :key="index">
                <td>{{ item.number || '—' }}</td>
                <td>{{ time(item.time) }}</td>
                <td>{{ item.description || '—' }}</td>
                <td>{{ item.person || '—' }}</td>
                <td>{{ item.status || '—' }}</td>
                <td>{{ item.result || '—' }}</td>
              </tr>
              <tr v-if="!record.spareChanges.length">
                <td colspan="6" class="history-empty">暂无备件更换记录</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-else class="history-table">
          <table class="business-table">
            <thead>
              <tr>
                <th>记录编号</th>
                <th>时间</th>
                <th>内容</th>
                <th>执行人</th>
                <th>状态</th>
                <th>结果 / 措施</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(item, index) in records()" :key="index">
                <td>{{ item.number || '—' }}</td>
                <td>{{ time(item.time) }}</td>
                <td>{{ item.description || '—' }}</td>
                <td>{{ item.person || '—' }}</td>
                <td>{{ item.status || '—' }}</td>
                <td>{{ item.result || '—' }}</td>
              </tr>
              <tr v-if="!records().length">
                <td colspan="6" class="history-empty">暂无{{ tab }}记录</td>
              </tr>
            </tbody>
          </table>
        </div>
      </template>
    </div>
  </section>
</template>
<style scoped>
.equipment-history {
  position: absolute;
  inset: 48px 0 0;
  z-index: 30;
  background: #fff;
  display: flex;
  flex-direction: column;
  font-size: 13px;
  color: #334155;
}
.equipment-history header {
  height: 48px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 16px;
  border-bottom: 1px solid #e5e9ef;
}
.equipment-history header span {
  color: #64748b;
}
.history-close {
  border: 0;
  background: transparent;
  font-size: 24px;
  color: #64748b;
  cursor: pointer;
}
.history-close {
  margin-left: auto;
}
.equipment-history nav {
  display: flex;
  gap: 24px;
  padding: 0 20px;
  border-bottom: 1px solid #e5e9ef;
}
.equipment-history nav button {
  height: 44px;
  border: 0;
  border-bottom: 2px solid transparent;
  background: none;
  color: #64748b;
  font: inherit;
  cursor: pointer;
}
.equipment-history nav button.active {
  color: var(--primary, #245bfa);
  border-color: var(--primary, #245bfa);
}
.history-body {
  overflow: auto;
  padding: 20px;
  flex: 1;
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
.history-related p,
.history-empty {
  color: #94a3b8;
}
.history-table table,
.history-related table {
  width: 100%;
  border-collapse: collapse;
}
.history-table th {
  background: #f5f7fa;
  text-align: left;
  font-weight: 600;
}
.history-table td,
.history-table th,
.history-related td {
  padding: 12px;
  border-bottom: 1px solid #e9edf3;
}
.history-empty {
  text-align: center;
  padding: 48px !important;
}
.history-error {
  color: #dc3545;
}
.history-equipment-image {
  display: block;
  max-width: 180px;
  max-height: 140px;
  object-fit: contain;
  border: 1px solid #e2e7ee;
  border-radius: 6px;
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
