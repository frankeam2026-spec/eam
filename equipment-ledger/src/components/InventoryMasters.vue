<script setup lang="ts">
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElCheckboxGroup, ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import AppDecimalInput from './common/AppDecimalInput.vue';
import { formatNumber, isDecimalField } from '../utils/number';

import { listBase, type BaseRecord } from '../api';
import TreeSelect from './TreeSelect.vue';
import { inventoryMenu } from '../inventory';
import { computed, ref, watch } from 'vue';
import { useRoute } from 'vue-router';
import { ElButton, ElInput, ElSelect, ElOption, ElSwitch, ElMessage, ElTag } from 'element-plus';
import AppDialog from './common/AppDialog.vue';
import AppPagination from './common/AppPagination.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { useAuthStore } from '../stores/auth';
import {
  inventoryApi,
  type InventoryMasterKind,
  type InventoryMaster,
  type InventoryChoices,
} from '../inventory';
const route = useRoute(),
  auth = useAuthStore();
const kind = computed(() => String(route.params.kind || 'materials') as InventoryMasterKind);
const title = computed(() => ({ materials: '物料', warehouses: '仓库', bins: '库位' })[kind.value]);
const binWarehouse = ref<InventoryMaster>();
const binRows = ref<InventoryMaster[]>([]),
  binName = ref(''),
  binCode = ref(''),
  binRemark = ref(''),
  binPage = ref(1),
  binPageSize = ref(10),
  binTotal = ref(0),
  binBusy = ref(false),
  binEditing = ref<InventoryMaster>();
async function loadBins() {
  if (!binWarehouse.value) return;
  const result = await inventoryApi.list('bins', '', binPage.value, binPageSize.value, {
    _warehouseId: binWarehouse.value.id,
  });
  binRows.value = result.data;
  binTotal.value = result.page.total;
}
watch([binPage, binPageSize], () => {
  if (binWarehouse.value) loadBins().catch((e) => ElMessage.error(e.message));
});
async function openBins(row: InventoryMaster) {
  binWarehouse.value = row;
  binEditing.value = undefined;
  binName.value = '';
  binCode.value = '';
  binRemark.value = '';
  binPage.value = 1;
  try {
    await loadBins();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '库位加载失败');
  }
}
async function saveBin() {
  if (!binWarehouse.value || !binName.value.trim() || binBusy.value) return;
  binBusy.value = true;
  try {
    await inventoryApi.save('bins', {
      ...(binEditing.value || empty()),
      name: binName.value,
      code: binCode.value,
      remark: binRemark.value,
      warehouseId: binWarehouse.value.id,
    });
    binName.value = '';
    binCode.value = '';
    binRemark.value = '';
    binEditing.value = undefined;
    await loadBins();
    ElMessage.success('库位已保存');
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    binBusy.value = false;
  }
}
const rows = ref<InventoryMaster[]>([]),
  total = ref(0),
  page = ref(1),
  size = ref(10),
  query = ref(''),
  applied = ref('');
const loading = ref(false),
  busy = ref(false),
  error = ref(''),
  open = ref(false),
  columnsOpen = ref(false),
  filterOpen = ref(false);
const choices = ref<InventoryChoices>({ companies: [], users: [], warehouses: [] });
const empty = (): InventoryMaster => ({
  id: '',
  code: '',
  name: '',
  enabled: true,
  remark: '',
  model: '',
  unit: '',
  category: '',
  usageType: '消耗型',
  referencePrice: '',
  batchEnabled: false,
  expiryEnabled: false,
  allocationRule: kind.value === 'materials' ? '' : 'FIFO',
  companyId: '',
  managerId: '',
  warehouseId: '',
});
const form = ref<InventoryMaster>(empty());
const units = ref<BaseRecord[]>([]),
  materialCategories = ref<BaseRecord[]>([]);
const guard = useUnsavedChanges(
  () => form.value,
  () => open.value,
  () => busy.value,
);
const masterColumns: Record<InventoryMasterKind, Record<string, string>> = {
  materials: {
    code: '料号',
    name: '物料名称',
    model: '规格型号',
    unit: '单位',
    category: '分类',
    usageType: '用途',
    referencePrice: '参考价（元）',
    batchEnabled: '批次管理',
    expiryEnabled: '有效期管理',
    enabled: '状态',
  },
  warehouses: {
    code: '仓库编码',
    name: '仓库名称',
    companyId: '所属公司',
    managerId: '仓管',
    allocationRule: '出库规则',
    enabled: '状态',
  },
  bins: { code: '库位编码', name: '库位名称', warehouseId: '所属仓库', enabled: '状态' },
};
const fields = computed(() => masterColumns[kind.value]);
const columns = ref<string[]>([]),
  filters = ref<Record<string, string>>({});
const managers = computed(() =>
  choices.value.users.filter((u) => u.companyId === form.value.companyId),
);
function display(row: InventoryMaster, key: string): string {
  const value = row[key as keyof InventoryMaster];
  if (['batchEnabled', 'expiryEnabled'].includes(key)) return value ? '启用' : '未启用';
  if (key === 'enabled') return value ? '启用' : '停用';
  if (key === 'companyId')
    return (
      (row as InventoryMaster & { companyName?: string }).companyName ||
      choices.value.companies.find((x) => x.id === value)?.name ||
      ''
    );
  if (key === 'managerId')
    return (
      (row as InventoryMaster & { managerName?: string }).managerName ||
      choices.value.users.find((x) => x.id === value)?.name ||
      ''
    );
  if (key === 'warehouseId')
    return (
      (row as InventoryMaster & { warehouseName?: string }).warehouseName ||
      choices.value.warehouses.find((x) => x.id === value)?.name ||
      ''
    );
  if (key === 'allocationRule')
    return (
      ({ FIFO: '先进先出', FEFO: '先到期先出' } as Record<string, string>)[String(value)] ||
      '跟随仓库'
    );
  return isDecimalField(key) ? formatNumber(value) : value == null ? '' : String(value);
}
const visible = computed(() => rows.value);
let revision = 0;
async function load() {
  const current = ++revision,
    requested = kind.value;
  loading.value = true;
  error.value = '';
  try {
    const [result, options] = await Promise.all([
      inventoryApi.list(requested, applied.value, page.value, size.value, filters.value),
      inventoryApi.choices(),
    ]);
    if (current !== revision) return;
    rows.value = result.data;
    total.value = result.page.total;
    choices.value = options.data;
    if (kind.value === 'materials') {
      const [u, c] = await Promise.all([listBase('单位'), listBase('物料分类')]);
      units.value = u.data;
      materialCategories.value = c.data;
    }
    if (page.value > 1 && !rows.value.length) {
      page.value = Math.max(1, Math.ceil(total.value / size.value));
      return;
    }
  } catch (e) {
    if (current === revision) {
      rows.value = [];
      error.value = e instanceof Error ? e.message : '加载失败';
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
function edit(row?: InventoryMaster) {
  form.value = row ? structuredClone(row) : empty();
  guard.markSaved();
  open.value = true;
}
async function save() {
  if (busy.value) return;
  if (!form.value.name.trim()) {
    ElMessage.warning('请输入名称');
    return;
  }
  busy.value = true;
  try {
    await inventoryApi.save(kind.value, form.value);
    guard.markSaved();
    open.value = false;
    ElMessage.success('已保存');
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '保存失败');
  } finally {
    busy.value = false;
  }
}
async function exportRows() {
  try {
    const result = await inventoryApi.export(kind.value, applied.value, filters.value);
    const escape = (value: string) =>
      '"' + (/^[\s]*[=+@-]/.test(value) ? "'" : '') + value.replaceAll('"', '""') + '"';
    const csv =
      '\ufeff' +
      [
        columns.value.map((k) => escape(fields.value[k] || k)).join(','),
        ...result.data.map((r) => columns.value.map((k) => escape(display(r, k))).join(',')),
      ].join('\n');
    const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
    const link = document.createElement('a');
    link.href = url;
    link.download = title.value + '.csv';
    link.click();
    URL.revokeObjectURL(url);
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : '导出失败');
  }
}
function search() {
  applied.value = query.value;
  page.value = 1;
  load();
}
function reset() {
  query.value = '';
  applied.value = '';
  filters.value = {};
  page.value = 1;
  load();
}
watch(
  kind,
  () => {
    open.value = false;
    form.value = empty();
    guard.markSaved();
    page.value = 1;
    query.value = '';
    applied.value = '';
    filters.value = {};
    columns.value = Object.keys(fields.value);
    load();
  },
  { immediate: true },
);
watch(page, () => load());
watch(size, () => {
  page.value = 1;
  load();
});
watch(
  filters,
  () => {
    page.value = 1;
    load();
  },
  { deep: true },
);
watch(columns, (v) => {
  if (!v.length) columns.value = ['code'];
});
watch(
  () => form.value.batchEnabled,
  (v) => {
    if (!v) form.value.expiryEnabled = false;
  },
);
watch(
  () => form.value.companyId,
  () => {
    if (!managers.value.some((u) => u.id === form.value.managerId)) form.value.managerId = '';
  },
);
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => visible.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => binRows.value;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
</script>
<template>
  <section class="inventory-master-page">
    <AppListToolbar class="master-toolbar">
      <ElButton v-if="auth.can(inventoryMenu(kind), '新增')" type="primary" @click="edit()"
        ><AppIcon name="Plus" /> 新增</ElButton
      >
      <form class="master-search" @submit.prevent="search">
        <ListToolbarIcons
          mode="filter"
          :can-export="auth.can(inventoryMenu(kind), '导出')"
          :filter-open="filterOpen"
          :columns-open="columnsOpen"
          @toggle-filter="filterOpen = !filterOpen"
        /><ListSearchInput
          v-model="query"
          :placeholder="
            kind === 'materials' ? '搜索料号、物料名称、型号' : `搜索${title}编码、名称`
          "
        /><ElButton text @click="reset">重置</ElButton>
      </form>
    </AppListToolbar>
    <p v-if="error" role="alert" class="master-error">
      {{ error }} <ElButton text @click="load">重试</ElButton>
    </p>
    <div class="master-card">
      <div class="master-scroll">
        <AppBusinessTable
          :rows="visible"
          :pagination="false"
          :fill="true"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn
            show-overflow-tooltip
            v-for="key in columns"
            :min-width="150"
            :prop="String(key)"
            :key="String(key)"
            ><template #header
              ><div class="table-header-label">{{ fields[key] }}</div>
              <div v-if="filterOpen" class="table-column-filter" @click.stop>
                <ElInput
                  v-model="filters[key]"
                  clearable
                  :placeholder="`筛选${fields[key]}`"
                /></div></template
            ><template #default="{ row: row, $index: tableRowIndex }">
              <ElTag v-if="key === 'enabled'" :type="row.enabled ? 'success' : 'info'">{{
                display(row, key)
              }}</ElTag
              ><span v-else>{{ display(row, key) }}</span>
            </template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow1} -->
          <ElTableColumn
            class-name="master-action"
            fixed="right"
            :width="170"
            :min-width="156"
            :resizable="false"
            ><template #header><div class="table-header-label">操作</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">
              <ElButton
                v-if="auth.can(inventoryMenu(kind), '编辑')"
                link
                type="primary"
                @click="edit(row)"
                >编辑</ElButton
              >
              <ElButton v-if="kind === 'warehouses'" link type="primary" @click="openBins(row)"
                >库位</ElButton
              >
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!visible.length">
              {{ loading ? '正在加载…' : error ? '加载失败，请重试' : '暂无数据' }}
            </div></template
          ></AppBusinessTable
        >
      </div>
      <footer>
        <div class="master-paging">
          <span>共 {{ total }} 条</span
          ><AppPagination v-model:page="page" v-model:page-size="size" :total="total" />
        </div>
        <ListToolbarIcons
          mode="footer"
          :can-export="auth.can(inventoryMenu(kind), '导出')"
          :filter-open="filterOpen"
          :columns-open="columnsOpen"
          @toggle-columns="columnsOpen = !columnsOpen"
          @export="exportRows"
          ><template #columns
            ><ElCheckboxGroup v-model="columns"
              ><ElCheckbox v-for="(label, key) in fields" :key="key" :value="key">{{
                label
              }}</ElCheckbox></ElCheckboxGroup
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </div>
    <AppDialog
      :model-value="!!binWarehouse"
      :title="(binWarehouse?.name || '') + ' · 库位'"
      width="680px"
      :busy="binBusy"
      @update:model-value="
        (value) => {
          if (!value) binWarehouse = undefined;
        }
      "
    >
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <ElInput v-model="binCode" placeholder="库位编码，留空自动生成" maxlength="64" />
        <ElInput v-model="binName" placeholder="库位名称" maxlength="120" /><ElButton
          v-if="auth.can('inventoryWarehouse', binEditing ? '编辑' : '新增')"
          type="primary"
          :loading="binBusy"
          @click="saveBin"
          >{{ binEditing ? '保存' : '新增' }}</ElButton
        >
      </div>
      <ElInput
        v-model="binRemark"
        placeholder="备注"
        maxlength="1000"
        style="margin-bottom: 12px"
      />
      <AppBusinessTable
        :rows="binRows"
        :pagination="false"
        :fill="false"
        :framed="false"
        class="business-table"
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">库位编码</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.code
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="210"
          ><template #header><div class="table-header-label">库位名称</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.name
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">状态</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.enabled ? '启用' : '停用'
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
          ><template #header><div class="table-header-label">操作</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">
            <ElButton
              v-if="auth.can('inventoryWarehouse', '编辑')"
              link
              type="primary"
              @click="
                binEditing = bin;
                binName = bin.name;
                binCode = bin.code;
                binRemark = bin.remark;
              "
              >编辑</ElButton
            ><ElButton
              v-if="auth.can('inventoryWarehouse', '编辑')"
              link
              @click="
                inventoryApi
                  .save('bins', { ...bin, enabled: !bin.enabled })
                  .then(loadBins)
                  .catch((e) => ElMessage.error(e.message))
              "
              >{{ bin.enabled ? '停用' : '启用' }}</ElButton
            >
          </template></ElTableColumn
        ></AppBusinessTable
      >
      <template #footer
        ><div style="display: flex; gap: 12px; align-items: center; justify-content: space-between">
          <AppPagination
            v-model:page="binPage"
            v-model:page-size="binPageSize"
            :total="binTotal"
          /><ElButton @click="binWarehouse = undefined">关闭</ElButton>
        </div></template
      >
    </AppDialog>
    <AppDialog
      v-model="open"
      :title="`${form.id ? '编辑' : '新增'}${title}`"
      width="760px"
      :busy="busy"
      :before-close="guard.permitLeave"
    >
      <div class="master-form" :inert="busy">
        <label
          ><span>{{ kind === 'materials' ? '料号' : '编码' }}</span
          ><ElInput v-model="form.code" maxlength="64" placeholder="留空自动生成" /></label
        ><label
          ><span>名称 <b>*</b></span
          ><ElInput v-model="form.name" maxlength="120"
        /></label>
        <template v-if="kind === 'materials'">
          <label><span>规格型号</span><ElInput v-model="form.model" maxlength="200" /></label
          ><label
            ><span>单位 <b>*</b></span
            ><ElSelect v-model="form.unit" filterable placeholder="选择单位"
              ><ElOption
                v-for="u in units.filter((x) => x.status === '启用')"
                :key="u.id"
                :value="u.name"
                :label="u.name" /></ElSelect></label
          ><label
            ><span>分类</span
            ><TreeSelect
              v-model="form.category"
              :nodes="materialCategories.map((x) => ({ ...x, parentId: x.parentId || '' }))"
              placeholder="选择物料分类" /></label
          ><label
            ><span>用途</span
            ><ElSelect v-model="form.usageType"
              ><ElOption value="消耗型" /><ElOption value="可安装" /></ElSelect></label
          ><label
            ><span>参考价（元）</span
            ><AppDecimalInput
              v-model="form.referencePrice"
              :disabled="!auth.can('inventoryMaterial', '编辑参考价')"
              inputmode="decimal"
              placeholder="未填写表示未知" /></label
          ><label
            ><span>出库规则</span
            ><ElSelect v-model="form.allocationRule" placeholder="跟随仓库"
              ><ElOption value="" label="跟随仓库" /><ElOption
                value="FIFO"
                label="先进先出" /><ElOption value="FEFO" label="先到期先出" /></ElSelect></label
          ><label><span>批次管理</span><ElSwitch v-model="form.batchEnabled" /></label
          ><label
            ><span>有效期管理</span
            ><ElSwitch v-model="form.expiryEnabled" :disabled="!form.batchEnabled"
          /></label>
        </template>
        <template v-if="kind === 'warehouses'">
          <label
            ><span>所属公司 <b>*</b></span
            ><ElSelect
              v-model="form.companyId"
              :disabled="!!form.id"
              filterable
              placeholder="选择公司"
              ><ElOption
                v-for="c in choices.companies"
                :key="c.id"
                :value="c.id"
                :label="c.name" /></ElSelect></label
          ><label
            ><span>仓管</span
            ><ElSelect v-model="form.managerId" clearable filterable
              ><ElOption
                v-for="u in managers"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></ElSelect></label
          ><label
            ><span>出库规则</span
            ><ElSelect v-model="form.allocationRule"
              ><ElOption value="FIFO" label="先进先出" /><ElOption
                value="FEFO"
                label="先到期先出" /></ElSelect
          ></label>
        </template>
        <label v-if="kind === 'bins'"
          ><span>所属仓库 <b>*</b></span
          ><ElSelect v-model="form.warehouseId" :disabled="!!form.id" filterable
            ><ElOption
              v-for="w in choices.warehouses"
              :key="w.id"
              :value="w.id"
              :label="w.name" /></ElSelect
        ></label>
        <label><span>启用</span><ElSwitch v-model="form.enabled" /></label
        ><label class="master-wide"
          ><span>备注</span
          ><ElInput
            v-model="form.remark"
            type="textarea"
            :rows="3"
            maxlength="1000"
            show-word-limit
        /></label>
      </div>
      <template #footer
        ><ElButton
          :disabled="busy"
          @click="
            async () => {
              if (await guard.permitLeave()) open = false;
            }
          "
          >取消</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">保存</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.inventory-master-page {
  height: calc(100dvh - 54px);
  padding: 8px;
  display: flex;
  flex-direction: column;
  gap: 8px;
  color: var(--text);
  font-size: 13px;
  min-height: 0;
}
.master-search,
.master-paging {
  display: flex;
  align-items: center;
  gap: 12px;
}
.master-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: white;
  border: 1px solid var(--border, #dfe5ee);
  border-radius: 6px;
  overflow: hidden;
}
.master-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.master-card table {
  width: 100%;
  min-width: 1000px;
  table-layout: fixed;
}
.master-action {
  position: sticky;
  right: 0;
  width: 100px;
  background: white;
  box-shadow: -1px 0 0 #e1e7ef;
  z-index: 1;
}
.master-card :deep(thead .master-action) {
  background: #f5f7fa;
  z-index: 2;
}
.master-card footer {
  height: 46px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 14px;
  border-top: 1px solid #e1e7ef;
}
.master-form {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 24px;
}
.master-form label {
  display: flex;
  align-items: center;
  gap: 12px;
  min-width: 0;
}
.master-form label > span {
  width: 86px;
  flex: none;
  text-align: right;
  color: var(--text-secondary);
}
.master-form :deep(.el-select),
.master-form :deep(.el-input) {
  flex: 1;
  min-width: 0;
}
.master-form b,
.master-error {
  color: #d32f3f;
}
.master-wide {
  grid-column: 1/-1;
}
.master-error {
  margin: 0;
}
@media (max-width: 850px) {
  .master-form {
    grid-template-columns: 1fr;
  }
  .master-search {
    width: 100%;
  }
}
</style>
