<script setup lang="ts">import { tr } from '../i18n';

import { onUnmounted } from 'vue';
import { useInventoryMasterList } from '../composables/inventory/useInventoryLists';
const { rows, total, refresh: refreshList, invalidate: invalidateList } = useInventoryMasterList();
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
import { computed, ref, watch, toRaw, nextTick } from 'vue';
import { useRoute } from 'vue-router';
import {
  ElButton,
  ElInput,
  ElSelect,
  ElOption,
  ElSwitch,
  ElMessage,
  ElTag,
  ElAlert,
} from 'element-plus';
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
  binPageSize = ref(20),
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
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0462'));
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
    ElMessage.success(tr('ui.equipment.text0463'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
  } finally {
    binBusy.value = false;
  }
}
const page = ref(1),
  size = ref(20),
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
  unitId: '',
  category: '',
  categoryId: '',
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
onUnmounted(() => {
  revision++;
  invalidateList();
});
async function load() {
  const current = ++revision,
    requested = kind.value;
  loading.value = true;
  error.value = '';
  try {
    const [result, options] = await Promise.all([
      refreshList(requested, applied.value, page.value, size.value, filters.value),
      inventoryApi.choices(),
    ]);
    if (current !== revision) return;
    if (!result) return;
    choices.value = options.data;
    if (kind.value === 'materials') {
      const [u, c] = await Promise.all([listBase('单位'), listBase('物料分类')]);
      if (current !== revision) return;
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
      error.value = e instanceof Error ? e.message : tr('ui.equipment.text0160');
    }
  } finally {
    if (current === revision) loading.value = false;
  }
}
const legacyCategory = ref('');
async function edit(row?: InventoryMaster) {
  form.value = row ? structuredClone(toRaw(row)) : empty();
  legacyCategory.value = row && !row.categoryId ? row.category || '' : '';
  open.value = true;
  await nextTick();
  guard.markSaved();
}
async function save() {
  if (busy.value) return;
  if (!form.value.name.trim()) {
    ElMessage.warning(tr('ui.equipment.text0464'));
    return;
  }
  if (kind.value === 'materials' && legacyCategory.value && !form.value.categoryId) {
    ElMessage.warning(tr('ui.equipment.text0465'));
    return;
  }
  busy.value = true;
  try {
    await inventoryApi.save(kind.value, form.value);
    guard.markSaved();
    open.value = false;
    ElMessage.success(tr('ui.equipment.text0194'));
    await load();
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0195'));
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
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0185'));
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
  <section class="content inventory-master-page">
    <AppListToolbar class="master-toolbar">
      <ElButton v-if="auth.can(inventoryMenu(kind), '新增')" type="primary" @click="edit()"
        ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
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
            kind === 'materials' ? tr('ui.equipment.text0466') : `搜索${title}编码、名称`
          "
        /><ElButton text @click="reset">{{ tr('ui.equipment.text0204') }}</ElButton>
      </form>
    </AppListToolbar>
    <p v-if="error" role="alert" class="master-error">
      {{ error }} <ElButton text @click="load">{{ tr('ui.equipment.text0299') }}</ElButton>
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
            ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
            ><template #default="{ row: row, $index: tableRowIndex }">
              <ElButton
                v-if="auth.can(inventoryMenu(kind), '编辑')"
                link
                type="primary"
                @click="edit(row)"
                >{{ tr('ui.equipment.text0207') }}</ElButton
              >
              <ElButton v-if="kind === 'warehouses'" link type="primary" @click="openBins(row)"
                >{{ tr('ui.equipment.text0467') }}</ElButton
              >
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!visible.length">
              {{ loading ? tr('ui.equipment.text0162') : error ? tr('ui.equipment.text0468') : tr('ui.equipment.text0308') }}
            </div></template
          ></AppBusinessTable
        >
      </div>
      <footer>
        <div class="master-paging">
          <span>{{ tr('ui.equipment.text0309') }} {{ total }} {{ tr('ui.equipment.text0296') }}</span
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
      :title="(binWarehouse?.name || '') + tr('ui.equipment.text0469')"
      width="680px"
      :busy="binBusy"
      @update:model-value="
        (value) => {
          if (!value) binWarehouse = undefined;
        }
      "
    >
      <div style="display: flex; gap: 8px; margin-bottom: 12px">
        <ElInput v-model="binCode" :placeholder="tr('ui.equipment.text0470')" maxlength="64" />
        <ElInput v-model="binName" :placeholder="tr('ui.equipment.text0471')" maxlength="120" /><ElButton
          v-if="auth.can('inventoryWarehouse', binEditing ? '编辑' : '新增')"
          type="primary"
          :loading="binBusy"
          @click="saveBin"
          >{{ binEditing ? tr('ui.equipment.text0159') : tr('ui.equipment.text0149') }}</ElButton
        >
      </div>
      <ElInput
        v-model="binRemark"
        :placeholder="tr('ui.equipment.text0130')"
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
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0472') }}</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.code
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="210"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0471') }}</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.name
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn :min-width="150"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0170') }}</div></template
          ><template #default="{ row: bin, $index: tableRowIndex }">{{
            bin.enabled ? tr('ui.equipment.text0216') : tr('ui.equipment.text0260')
          }}</template></ElTableColumn
        ><!-- @vue-generic {TableColumnRow2} -->
        <ElTableColumn fixed="right" :width="170" :min-width="156" :resizable="false"
          ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
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
              >{{ tr('ui.equipment.text0207') }}</ElButton
            ><ElButton
              v-if="auth.can('inventoryWarehouse', '编辑')"
              link
              @click="
                inventoryApi
                  .save('bins', { ...bin, enabled: !bin.enabled })
                  .then(loadBins)
                  .catch((e) => ElMessage.error(e.message))
              "
              >{{ bin.enabled ? tr('ui.equipment.text0260') : tr('ui.equipment.text0216') }}</ElButton
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
          /><ElButton @click="binWarehouse = undefined">{{ tr('ui.equipment.text0076') }}</ElButton>
        </div></template
      >
    </AppDialog>
    <AppDialog
      v-model="open"
      :title="`${form.id ? tr('ui.equipment.text0207') : tr('ui.equipment.text0149')}${title}`"
      width="760px"
      :busy="busy"
      :before-close="guard.permitLeave"
    >
      <ElAlert
        v-if="kind === 'materials' && form.unit && !form.unitId"
        type="warning"
        :closable="false"
        :title="`历史单位待确认：${form.unit}，请重新选择单位。`"
      />
      <ElAlert
        v-if="kind === 'materials' && legacyCategory && !form.categoryId"
        type="warning"
        :closable="false"
        :title="`历史分类待确认：${legacyCategory}`"
        ><ElButton
          link
          type="primary"
          @click="
            legacyCategory = '';
            form.category = '';
          "
          >{{ tr('ui.equipment.text0473') }}</ElButton
        ></ElAlert
      >
      <div class="master-form" :inert="busy">
        <label
          ><span>{{ kind === 'materials' ? tr('ui.equipment.text0154') : tr('ui.equipment.text0474') }}</span
          ><ElInput v-model="form.code" maxlength="64" :placeholder="tr('ui.equipment.text0312')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text0404') }} <b>*</b></span
          ><ElInput v-model="form.name" maxlength="120"
        /></label>
        <template v-if="kind === 'materials'">
          <label><span>{{ tr('ui.equipment.text0091') }}</span><ElInput v-model="form.model" maxlength="200" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0095') }} <b>*</b></span
            ><ElSelect v-model="form.unitId" filterable :placeholder="tr('ui.equipment.text0475')"
              ><ElOption
                v-for="u in units.filter((x) => x.status === '启用')"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></ElSelect></label
          ><label
            ><span>{{ tr('ui.equipment.text0476') }}</span
            ><TreeSelect
              v-model="form.categoryId"
              :nodes="
                materialCategories.map((x) => ({ ...x, parentId: x.parentId || '', value: x.id }))
              "
              :placeholder="tr('ui.equipment.text0477')" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0478') }}</span
            ><ElSelect v-model="form.usageType"
              ><ElOption value="消耗型" /><ElOption value="可安装" /></ElSelect></label
          ><label
            ><span>{{ tr('ui.equipment.text0479') }}</span
            ><AppDecimalInput
              v-model="form.referencePrice"
              :disabled="!auth.can('inventoryMaterial', '编辑参考价')"
              inputmode="decimal"
              :placeholder="tr('ui.equipment.text0480')" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0481') }}</span
            ><ElSelect v-model="form.allocationRule" :placeholder="tr('ui.equipment.text0482')"
              ><ElOption value="" :label="tr('ui.equipment.text0482')" /><ElOption
                value="FIFO"
                :label="tr('ui.equipment.text0483')" /><ElOption value="FEFO" :label="tr('ui.equipment.text0484')" /></ElSelect></label
          ><label><span>{{ tr('ui.equipment.text0485') }}</span><ElSwitch v-model="form.batchEnabled" /></label
          ><label
            ><span>{{ tr('ui.equipment.text0486') }}</span
            ><ElSwitch v-model="form.expiryEnabled" :disabled="!form.batchEnabled"
          /></label>
        </template>
        <template v-if="kind === 'warehouses'">
          <label
            ><span>{{ tr('ui.equipment.text0487') }} <b>*</b></span
            ><ElSelect
              v-model="form.companyId"
              :disabled="!!form.id"
              filterable
              :placeholder="tr('ui.equipment.text0488')"
              ><ElOption
                v-for="c in choices.companies"
                :key="c.id"
                :value="c.id"
                :label="c.name" /></ElSelect></label
          ><label
            ><span>{{ tr('ui.equipment.text0489') }}</span
            ><ElSelect v-model="form.managerId" clearable filterable
              ><ElOption
                v-for="u in managers"
                :key="u.id"
                :value="u.id"
                :label="u.name" /></ElSelect></label
          ><label
            ><span>{{ tr('ui.equipment.text0481') }}</span
            ><ElSelect v-model="form.allocationRule"
              ><ElOption value="FIFO" :label="tr('ui.equipment.text0483')" /><ElOption
                value="FEFO"
                :label="tr('ui.equipment.text0484')" /></ElSelect
          ></label>
        </template>
        <label v-if="kind === 'bins'"
          ><span>{{ tr('ui.equipment.text0490') }} <b>*</b></span
          ><ElSelect v-model="form.warehouseId" :disabled="!!form.id" filterable
            ><ElOption
              v-for="w in choices.warehouses"
              :key="w.id"
              :value="w.id"
              :label="w.name" /></ElSelect
        ></label>
        <label><span>{{ tr('ui.equipment.text0216') }}</span><ElSwitch v-model="form.enabled" /></label
        ><label class="master-wide"
          ><span>{{ tr('ui.equipment.text0130') }}</span
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
          >{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="busy" @click="save">{{ tr('ui.equipment.text0159') }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>
<style scoped>
.inventory-master-page {
  height: calc(100dvh - 54px);
  display: flex;
  flex-direction: column;
  /* gap: 8px; */
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
