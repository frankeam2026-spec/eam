<script setup lang="ts">
import { ref, onMounted, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElButton, ElAlert, ElTableColumn, ElMessage } from 'element-plus';
import AppIcon from '../components/common/AppIcon.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import { analyticsApi, type AnalysisDrill, type AnalysisRow } from '../analytics';
const route = useRoute(),
  router = useRouter();
const drill = ref<AnalysisDrill>(),
  rows = ref<AnalysisRow[]>([]),
  page = ref(1),
  size = ref(20),
  total = ref(0),
  loading = ref(false),
  exporting = ref(false),
  error = ref(''),
  definition = ref(''),
  exportable = ref(false);
let version = 0;
async function load() {
  if (!drill.value) return;
  const v = ++version;
  loading.value = true;
  error.value = '';
  try {
    const r = await analyticsApi.details(drill.value, page.value, size.value);
    if (v !== version) return;
    rows.value = r.data;
    total.value = r.total;
    definition.value = r.definition;
    exportable.value = r.exportable;
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败';
  } finally {
    if (v === version) loading.value = false;
  }
}
async function exportRows() {
  if (!drill.value) return;
  exporting.value = true;
  try {
    await analyticsApi.export(drill.value);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败');
  } finally {
    exporting.value = false;
  }
}
function date(v: unknown) {
  return v ? String(v).replace('T', ' ').slice(0, 19) : '';
}
const labels: Record<string, string> = {
  equipment: '设备用料',
  shared: '任务公共用料',
  legacy_unassigned: '历史未归属',
};
onMounted(() => {
  try {
    drill.value = JSON.parse(
      sessionStorage.getItem('eam-analysis:' + String(route.params.key)) || 'null',
    );
    if (!drill.value) error.value = '分析上下文已失效，请返回首页重新打开明细。';
    else void load();
  } catch {
    error.value = '分析上下文无效，请返回首页重新打开。';
  }
});
watch([page, size], load);
</script>
<template>
  <section class="analysis-detail">
    <header>
      <ElButton
        aria-label="返回首页"
        text
        @click="router.push({ name: 'home2', query: { analysis: String(route.params.key) } })"
        ><AppIcon name="Left"
      /></ElButton>
      <div>
        <h1>{{ drill?.title || '分析明细' }}</h1>
        <span>{{ drill?.subtitle }} · 统计截至 {{ date(drill?.context.asOf) }}</span>
      </div>
      <ElButton v-if="exportable" :loading="exporting" @click="exportRows">导出</ElButton>
    </header>
    <ElAlert v-if="definition" :title="definition" type="info" :closable="false" /><AppBusinessTable
      v-model:page="page"
      v-model:page-size="size"
      :rows="rows"
      :loading="loading"
      :error="error"
      :total="total"
      row-key="source_id"
      @retry="load"
      ><ElTableColumn label="编号" prop="code" min-width="175" show-overflow-tooltip
        ><template #default="{ row }"
          ><ElButton v-if="row.url" type="primary" link @click="router.push(row.url)">{{
            row.code
          }}</ElButton
          ><span v-else>{{ row.code }}</span></template
        ></ElTableColumn
      ><ElTableColumn
        label="设备名称"
        prop="equipment_name"
        min-width="150"
        show-overflow-tooltip
      /><template v-if="drill?.widget.metric.includes('material')"
        ><ElTableColumn label="料号" prop="material_code" min-width="120" /><ElTableColumn
          label="物料名称"
          prop="material_name"
          min-width="150"
          show-overflow-tooltip /><ElTableColumn
          label="数量"
          prop="quantity"
          width="100"
          align="right"
          ><template #default="{ row }">{{
            Number(row.quantity).toFixed(2)
          }}</template></ElTableColumn
        ><ElTableColumn label="单位" prop="unit" width="75" /><ElTableColumn
          label="实际成本"
          prop="amount"
          width="120"
          align="right"
          ><template #default="{ row }">{{
            row.amount == null ? '成本缺失' : Number(row.amount).toFixed(2)
          }}</template></ElTableColumn
        ><ElTableColumn label="币种" prop="currency" width="80" /><ElTableColumn
          label="归属"
          width="135"
          ><template #default="{ row }">{{
            labels[row.attribution] || row.attribution
          }}</template></ElTableColumn
        ><ElTableColumn label="出库分配ID" prop="allocation_id" min-width="180" /><ElTableColumn
          label="成本层ID"
          prop="cost_layer_id"
          min-width="180" /><ElTableColumn
          label="入库来源明细ID"
          prop="receipt_line_id"
          min-width="180" /></template
      ><template v-else
        ><ElTableColumn
          label="部门"
          prop="department_name"
          min-width="130"
          show-overflow-tooltip
        /><ElTableColumn label="负责人" prop="owner_name" min-width="100" /><ElTableColumn
          label="状态"
          prop="status"
          width="100"
        /><ElTableColumn label="提交时间" width="165"
          ><template #default="{ row }">{{ date(row.submitted_at) }}</template></ElTableColumn
        ><ElTableColumn label="验收时间" width="165"
          ><template #default="{ row }">{{ date(row.accepted_at) }}</template></ElTableColumn
        ></template
      ><ElTableColumn label="计划 / 发生时间" width="165"
        ><template #default="{ row }">{{ date(row.occurred_at) }}</template></ElTableColumn
      ></AppBusinessTable
    >
  </section>
</template>
<style scoped>
.analysis-detail {
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  padding: 16px;
  gap: 12px;
  min-height: 0;
}
.analysis-detail > header {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--surface);
  padding: 12px;
}
.analysis-detail h1 {
  font-size: 16px;
  margin: 0 0 6px;
}
.analysis-detail header span {
  font-size: 12px;
}
.analysis-detail header > .el-button:last-child {
  margin-left: auto;
}
.analysis-detail :deep(.business-table) {
  flex: 1;
  min-height: 0;
}
</style>
