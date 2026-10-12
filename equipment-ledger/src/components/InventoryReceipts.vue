<script setup lang="ts">
import MaterialPicker from './common/MaterialPicker.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { computed, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElTag,
  ElDatePicker,
  ElMessage,
  ElMessageBox,
} from 'element-plus';
import AppBackButton from './common/AppBackButton.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  receiptApi,
  type Receipt,
  type ReceiptChoices,
  type ReceiptLine,
  type InventoryMaster,
} from '../inventory';
const route = useRoute(),
  router = useRouter(),
  auth = useAuthStore();
const editing = computed(() => !!route.params.id);
const empty = (): Receipt => ({
  id: '',
  code: '',
  warehouseId: '',
  supplier: '',
  remark: '',
  status: '草稿',
  lines: [],
});
const form = ref(empty()),
  rows = ref<Receipt[]>([]),
  choices = ref<ReceiptChoices>({
    companies: [],
    users: [],
    warehouses: [],
    materials: [],
    bins: [],
  });
const query = ref(''),
  applied = ref(''),
  page = ref(1),
  size = ref(10),
  total = ref(0),
  busy = ref(false),
  loading = ref(false),
  error = ref('');
const hasBatch = computed(() =>
  form.value.lines.some((line) => line.batchEnabled || material(line)?.batchEnabled),
);
const readOnly = computed(
  () =>
    form.value.status !== '草稿' || !auth.can('inventoryReceipt', form.value.id ? '编辑' : '新增'),
);
const guard = useUnsavedChanges(
  () => form.value,
  () => editing.value && !readOnly.value,
  () => busy.value,
);
const fields: Record<string, string> = {
  code: '入库单号',
  warehouseName: '仓库',
  supplier: '供应商',
  status: '状态',
  actorName: '操作人',
  createdAt: '创建时间',
  confirmedAt: '入库时间',
};
const columns = ref(Object.keys(fields)),
  filterOpen = ref(false),
  columnsOpen = ref(false),
  filters = ref<Record<string, string>>({}),
  exportBusy = ref(false);
function display(row: Receipt, key: string) {
  return key.endsWith('At')
    ? time(row[key as keyof Receipt] as string)
    : isDecimalField(key)
      ? formatNumber(row[key as keyof Receipt])
      : String(row[key as keyof Receipt] ?? '');
}
function toggleColumn(key: string) {
  if (columns.value.includes(key)) {
    if (columns.value.length > 1) columns.value = columns.value.filter((x) => x !== key);
  } else columns.value = Object.keys(fields).filter((x) => x === key || columns.value.includes(x));
}
async function exportRows() {
  if (exportBusy.value) return;
  exportBusy.value = true;
  try {
    const result = await receiptApi.export(applied.value, filters.value);
    const quote = (value: string) =>
      '"' + (/^[=+@\-]/.test(value) ? "'" : '') + value.replaceAll('"', '""') + '"';
    const content = [
      columns.value.map((x) => quote(fields[x]!)).join(','),
      ...result.data.map((row) => columns.value.map((key) => quote(display(row, key))).join(',')),
    ].join('\r\n');
    const url = URL.createObjectURL(
      new Blob(['\uFEFF' + content], { type: 'text/csv;charset=utf-8' }),
    );
    const link = document.createElement('a');
    link.href = url;
    link.download = '入库单.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    exportBusy.value = false;
  }
}
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
let revision = 0;
function message(e: unknown) {
  return e instanceof Error ? e.message : '操作失败，请重试';
}
function time(s?: string) {
  return s?.replace('T', ' ').slice(0, 19) || '';
}
async function load() {
  const current = ++revision;
  loading.value = true;
  error.value = '';
  try {
    const options = await receiptApi.choices();
    if (current !== revision) return;
    choices.value = options.data;
    if (editing.value) {
      const loaded =
        route.params.id === 'new'
          ? empty()
          : (await receiptApi.detail(String(route.params.id))).data;
      if (current !== revision) return;
      form.value = loaded;
      guard.markSaved();
    } else {
      const result = await receiptApi.list(applied.value, page.value, size.value, filters.value);
      if (current !== revision) return;
      rows.value = result.data;
      total.value = result.page.total;
    }
  } catch (e) {
    if (current === revision) error.value = message(e);
  } finally {
    if (current === revision) loading.value = false;
  }
}
watch(() => route.fullPath, load, { immediate: true });
watch([page, size], () => {
  if (!editing.value) load();
});
async function back() {
  if (await guard.permitLeave()) router.push({ name: 'inventory-receipts' });
}
const materialPickerOpen = ref(false);
const replacingLine = ref<ReceiptLine>();
function add() {
  replacingLine.value = undefined;
  materialPickerOpen.value = true;
}
function replaceMaterial(line: ReceiptLine) {
  replacingLine.value = line;
  materialPickerOpen.value = true;
}
function selectMaterials(selected: InventoryMaster[]) {
  const byId = new Map(choices.value.materials.map((m) => [m.id, m]));
  selected.forEach((m) => byId.set(m.id, m));
  choices.value.materials = [...byId.values()];
  const bins = choices.value.bins.filter(
    (x) => x.warehouseId === form.value.warehouseId && x.enabled,
  );
  for (const m of selected) {
    const line = replacingLine.value || {
      materialId: m.id,
      binId: bins.length === 1 ? bins[0]!.id : '',
      quantity: '1',
      unitPrice: '',
      allocatedFee: '0',
      batchCode: '',
      supplierBatch: '',
      productionDate: '',
      expiryDate: '',
    };
    line.materialId = m.id;
    changeMaterial(line);
    line.materialCode = m.code;
    line.materialName = m.name;
    if (!replacingLine.value) form.value.lines.push(line);
  }
  replacingLine.value = undefined;
}
function material(line: ReceiptLine) {
  return choices.value.materials.find((x) => x.id === line.materialId);
}
function changeMaterial(line: ReceiptLine) {
  line.batchCode = '';
  line.supplierBatch = '';
  line.productionDate = '';
  line.expiryDate = '';
  line.unitPrice = '';
  line.unit = material(line)?.unit;
  line.batchEnabled = material(line)?.batchEnabled;
  line.expiryEnabled = material(line)?.expiryEnabled;
}
function warehouseChanged() {
  const bins = choices.value.bins.filter(
    (x) => x.warehouseId === form.value.warehouseId && x.enabled,
  );
  form.value.lines.forEach((x) => (x.binId = bins.length === 1 ? bins[0]!.id : ''));
}
async function save() {
  if (busy.value || readOnly.value) return;
  if (!form.value.warehouseId) {
    ElMessage.warning('请选择入库仓库');
    return;
  }
  if (!form.value.lines.length) {
    ElMessage.warning('请添加入库明细');
    return;
  }
  if (form.value.lines.some((x) => !x.materialId)) {
    ElMessage.warning('请选择每行的物料');
    return;
  }
  busy.value = true;
  try {
    const result = await receiptApi.save(form.value);
    form.value = result.data;
    guard.markSaved();
    busy.value = false;
    if (route.params.id === 'new')
      await router.replace({ name: 'inventory-receipt-detail', params: { id: result.data.id } });
    ElMessage.success('草稿已保存');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function confirm() {
  if (busy.value || readOnly.value || !form.value.id) return;
  if (guard.dirty.value) {
    ElMessage.warning('请先保存修改，再确认入库');
    return;
  }
  try {
    await ElMessageBox.confirm('确认后将增加库存并记录成本，单据不能直接修改。', '确认入库', {
      confirmButtonText: '确认入库',
      cancelButtonText: '取消',
      closeOnClickModal: false,
      type: 'warning',
    });
  } catch {
    return;
  }
  if (busy.value) return;
  busy.value = true;
  try {
    form.value = (await receiptApi.confirm(form.value)).data;
    guard.markSaved();
    ElMessage.success('已确认入库');
  } catch (e) {
    ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function removeDraft() {
  try {
    await ElMessageBox.confirm('删除这张草稿？已入库单据不能删除。', '删除', {
      confirmButtonText: '删除',
      cancelButtonText: '取消',
      type: 'warning',
    });
    busy.value = true;
    await receiptApi.delete(form.value);
    guard.markSaved();
    router.push({ name: 'inventory-receipts' });
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
async function allocateFees() {
  try {
    const { value } = await ElMessageBox.prompt(
      '按各行不含税采购金额比例分摊，尾差计入最后一行。请先保存草稿。',
      '费用分摊（元）',
      {
        inputPattern: /^\d+(\.\d{1,2})?$/,
        inputErrorMessage: '请输入有效金额',
        confirmButtonText: '分摊',
        cancelButtonText: '取消',
      },
    );
    busy.value = true;
    form.value = (await receiptApi.allocate(form.value, value)).data;
    guard.markSaved();
    ElMessage.success('费用已分摊');
  } catch (e) {
    if (e !== 'cancel' && e !== 'close') ElMessage.error(message(e));
  } finally {
    busy.value = false;
  }
}
</script>
<template>
  <section class="receipts">
    <p v-if="error" class="error" role="alert">
      {{ error }} <ElButton text @click="load">重试</ElButton>
    </p>
    <template v-if="!editing">
      <div class="toolbar">
        <ElButton
          v-if="auth.can('inventoryReceipt', '入库')"
          type="primary"
          @click="router.push({ name: 'inventory-receipt-detail', params: { id: 'new' } })"
          >＋ 新增</ElButton
        >
        <form
          @submit.prevent="
            applied = query;
            page = 1;
            load();
          "
        >
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput v-model="query" placeholder="搜索入库单、仓库、供应商" /><ElButton
            text
            @click="
              query = '';
              applied = '';
              filters = {};
              page = 1;
              load();
            "
            >重置</ElButton
          >
        </form>
      </div>
      <div class="card">
        <div class="scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th v-for="key in columns" :key="key">{{ fields[key] }}</th>
                <th class="action">操作</th>
              </tr>
              <tr v-if="filterOpen">
                <th v-for="key in columns" :key="key">
                  <ElInput v-model="filters[key]" clearable :placeholder="'筛选' + fields[key]" />
                </th>
                <th class="action" />
              </tr>
            </thead>
            <tbody>
              <tr v-for="row in rows" :key="row.id">
                <td v-for="key in columns" :key="key">
                  <ElButton
                    v-if="key === 'code'"
                    type="primary"
                    link
                    @click="
                      router.push({ name: 'inventory-receipt-detail', params: { id: row.id } })
                    "
                    >{{ row.code }}</ElButton
                  ><ElTag
                    v-else-if="key === 'status'"
                    :type="row.status === '已入库' ? 'success' : 'warning'"
                    >{{ row.status }}</ElTag
                  ><span v-else>{{ display(row, key) }}</span>
                </td>
                <td class="action">
                  <ElButton
                    type="primary"
                    link
                    @click="
                      router.push({ name: 'inventory-receipt-detail', params: { id: row.id } })
                    "
                    >{{ row.status === '草稿' ? '编辑' : '查看' }}</ElButton
                  >
                </td>
              </tr>
              <tr v-if="!rows.length">
                <td :colspan="columns.length + 1" class="empty">
                  {{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无入库单' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="list-footer">
          <span>共 {{ total }} 条</span
          ><AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
          <ListToolbarIcons
            class="footer-tools"
            mode="footer"
            :filter-open="filterOpen"
            :columns-open="columnsOpen"
            :can-export="auth.can('inventoryReceipt', '导出')"
            :export-busy="exportBusy"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><label v-for="(label, key) in fields" :key="key"
                ><input
                  type="checkbox"
                  :checked="columns.includes(key)"
                  :disabled="columns.length === 1 && columns.includes(key)"
                  @change="toggleColumn(key)"
                />{{ label }}</label
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </template>
    <template v-else>
      <header>
        <AppBackButton :disabled="busy" @click="back" /><strong>{{
          form.id ? form.code : '新增入库'
        }}</strong
        ><ElTag :type="form.status === '已入库' ? 'success' : 'warning'">{{ form.status }}</ElTag>
      </header>
      <div v-if="!loading && !error" class="editor-scroll" :inert="busy">
        <div class="metadata">
          <label
            >仓库<ElSelect
              v-model="form.warehouseId"
              :disabled="readOnly"
              placeholder="请选择仓库"
              @change="warehouseChanged"
              ><ElOption
                v-for="w in choices.warehouses"
                :key="w.id"
                :label="w.name"
                :value="w.id" /></ElSelect></label
          ><label
            >供应商<ElInput v-model="form.supplier" :disabled="readOnly" maxlength="120" /></label
          ><label class="wide"
            >备注<ElInput v-model="form.remark" :disabled="readOnly" maxlength="1000"
          /></label>
        </div>
        <div class="detail-heading">
          <strong>入库明细</strong
          ><ElButton
            v-if="!readOnly"
            type="primary"
            plain
            :disabled="!form.warehouseId"
            @click="add"
            >＋ 添加物料</ElButton
          ><ElButton
            v-if="!readOnly"
            :disabled="!form.id || guard.dirty.value || busy"
            @click="allocateFees"
            >分摊费用</ElButton
          ><span>不含税单价与分摊费用均以元计</span>
        </div>
        <div class="line-scroll">
          <table class="lines" :class="{ 'batch-lines': hasBatch }">
            <thead>
              <tr>
                <th class="fixed-material">物料</th>
                <th>单位</th>
                <th>库位</th>
                <th>数量</th>
                <th>不含税单价</th>
                <th>分摊费用</th>
                <th>税率（小数）</th>
                <th v-if="hasBatch">批次号</th>
                <th v-if="hasBatch">供应商批次</th>
                <th v-if="hasBatch">生产日期</th>
                <th v-if="hasBatch">有效期至</th>
                <th v-if="readOnly">入库成本</th>
                <th v-if="!readOnly" class="action">操作</th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="(line, index) in form.lines" :key="line.id || index">
                <td class="fixed-material">
                  <span v-if="readOnly">{{ line.materialCode }} {{ line.materialName }}</span
                  ><ElButton
                    v-else
                    text
                    class="material-select-button"
                    @click="replaceMaterial(line)"
                  >
                    {{ line.materialCode || material(line)?.code }}
                    {{ line.materialName || material(line)?.name || '选择物料' }}
                  </ElButton>
                </td>
                <td>{{ line.unit || material(line)?.unit }}</td>
                <td>
                  <span v-if="readOnly">{{ line.binName }}</span
                  ><ElSelect v-else v-model="line.binId" filterable placeholder="选择库位"
                    ><ElOption
                      v-for="b in choices.bins.filter((x) => x.warehouseId === form.warehouseId)"
                      :key="b.id"
                      :value="b.id"
                      :label="b.name"
                  /></ElSelect>
                </td>
                <td>
                  <AppDecimalInput
                    v-model="line.quantity"
                    :readonly="readOnly"
                    inputmode="decimal"
                  />
                </td>
                <td>
                  <AppDecimalInput
                    v-model="line.unitPrice"
                    :readonly="readOnly"
                    inputmode="decimal"
                    placeholder="待确定"
                  />
                </td>
                <td>
                  <AppDecimalInput
                    v-model="line.allocatedFee"
                    :readonly="readOnly"
                    inputmode="decimal"
                  />
                </td>
                <td>
                  <AppDecimalInput
                    v-model="line.taxRate"
                    :readonly="readOnly"
                    inputmode="decimal"
                    placeholder="如 0.13"
                  />
                </td>
                <td v-if="hasBatch">
                  <ElInput
                    v-if="line.batchEnabled || material(line)?.batchEnabled"
                    v-model="line.batchCode"
                    :readonly="readOnly"
                    placeholder="留空自动生成"
                  />
                </td>
                <td v-if="hasBatch">
                  <ElInput
                    v-if="line.batchEnabled || material(line)?.batchEnabled"
                    v-model="line.supplierBatch"
                    :readonly="readOnly"
                  />
                </td>
                <td v-if="hasBatch">
                  <ElDatePicker
                    v-if="!readOnly && material(line)?.batchEnabled"
                    v-model="line.productionDate"
                    value-format="YYYY-MM-DD"
                    type="date"
                  /><span v-else>{{ line.productionDate }}</span>
                </td>
                <td v-if="hasBatch">
                  <ElDatePicker
                    v-if="!readOnly && material(line)?.batchEnabled"
                    v-model="line.expiryDate"
                    value-format="YYYY-MM-DD"
                    type="date"
                  /><span v-else>{{ line.expiryDate }}</span>
                </td>
                <td v-if="readOnly">{{ formatNumber(line.totalCost) }}</td>
                <td v-if="!readOnly" class="action">
                  <ElButton link type="danger" @click="form.lines.splice(index, 1)">删除</ElButton>
                </td>
              </tr>
              <tr v-if="!form.lines.length">
                <td :colspan="hasBatch ? 11 : 7" class="empty">选择仓库后添加入库物料</td>
              </tr>
            </tbody>
          </table>
        </div>
        <div v-if="form.events?.length" class="records">
          <strong>单据记录</strong>
          <p v-for="(event, index) in form.events" :key="index">
            <span class="dot" />{{ event.actorName }} · {{ event.action }}
            <time>{{ time(event.createdAt) }}</time>
          </p>
        </div>
      </div>
      <div v-else-if="loading" class="empty">正在加载…</div>
      <AppFormFooter
        ><ElButton :disabled="busy" @click="back">{{ readOnly ? '返回' : '取消' }}</ElButton
        ><ElButton v-if="!readOnly && !error && !loading" :loading="busy" @click="save"
          >保存草稿</ElButton
        ><ElButton
          v-if="
            !error && !loading && form.status === '草稿' && auth.can('inventoryReceipt', '入库')
          "
          type="primary"
          :disabled="busy || !form.id || guard.dirty.value"
          @click="confirm"
          >确认入库</ElButton
        ><ElButton
          v-if="form.id && form.status === '草稿' && auth.can('inventoryReceipt', '删除')"
          type="danger"
          plain
          :disabled="busy"
          @click="removeDraft"
          >删除</ElButton
        ></AppFormFooter
      >
    </template>
    <MaterialPicker
      v-model="materialPickerOpen"
      :multiple="!replacingLine"
      :load-rows="receiptApi.materials"
      @select="selectMaterials"
    />
  </section>
</template>
<style scoped>
.list-footer > span {
  white-space: nowrap;
  flex-shrink: 0;
}

.receipts {
  display: flex;
  flex-direction: column;
  height: calc(100dvh - 54px);
  min-height: 0;
  padding: 12px;
  font-size: 13px;
  color: var(--text);
  gap: 8px;
}
.toolbar,
.toolbar form,
header,
.detail-heading,
.list-footer {
  display: flex;
  align-items: center;
  gap: 12px;
}
.toolbar {
  margin-bottom: 0;
}
.toolbar form {
  margin-left: auto;
}
header {
  background: #fff;
  padding: 8px 12px;
  border-bottom: 1px solid #e4e9f0;
}
header strong {
  font-size: 15px;
}
.card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #fff;
  border: 1px solid #e4e9f0;
  border-radius: 6px;
  overflow: hidden;
}
.scroll {
  flex: 1;
  overflow: auto;
}
.list-footer {
  height: 46px;
  flex: none;
  padding: 0 12px;
  border-top: 1px solid #e4e9f0;
}
.footer-tools {
  margin-left: auto;
}
.editor-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
  background: #fff;
  padding: 18px;
}
.metadata {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px 24px;
  max-width: 1000px;
}
.metadata label {
  display: grid;
  grid-template-columns: 70px 1fr;
  align-items: center;
  gap: 10px;
}
.wide {
  grid-column: 1/-1;
}
.detail-heading {
  margin: 24px 0 12px;
}
.detail-heading span {
  margin-left: auto;
  color: var(--text-secondary);
}
table {
  border-collapse: separate;
  border-spacing: 0;
  width: 100%;
  min-width: 950px;
}
th,
td {
  text-align: left;
  padding: 12px;
  border-bottom: 1px solid #e4e9f0;
  font-size: 13px;
  white-space: nowrap;
}
th {
  background: #f5f7fb;
  font-weight: 700;
}
td {
  background: #fff;
}
tbody tr:hover td {
  background: #f8faff;
}
.action {
  position: sticky;
  right: 0;
  width: 100px;
  min-width: 100px;
  z-index: 2;
  box-shadow: -1px 0 #e4e9f0;
}
.line-scroll {
  overflow: auto;
  border: 1px solid #e4e9f0;
  border-radius: 5px;
}
.lines {
  min-width: 850px;
}
.batch-lines {
  min-width: 1800px;
}
.lines td {
  padding: 8px;
}
.lines th {
  padding: 12px 8px;
}
.lines td:first-child {
  min-width: 270px;
}
.lines td:nth-child(2) {
  min-width: 60px;
}
.lines td {
  min-width: 130px;
}
.lines :deep(.el-date-editor) {
  width: 150px;
}
.fixed-material {
  position: sticky;
  left: 0;
  z-index: 2;
  box-shadow: 1px 0 #e4e9f0;
}
.empty {
  text-align: center;
  padding: 48px;
}
.records {
  margin-top: 24px;
}
.records p {
  display: flex;
  gap: 10px;
  align-items: center;
}
.records time {
  margin-left: 12px;
}
.dot {
  height: 7px;
  width: 7px;
  border-radius: 50%;
  background: var(--brand);
}
.error {
  color: #c13449;
}
:deep(.el-input),
:deep(.el-select) {
  font-size: 13px;
}
:deep(.el-button) {
  font-size: 13px;
}
</style>
