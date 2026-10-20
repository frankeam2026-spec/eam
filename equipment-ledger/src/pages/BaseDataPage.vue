<script setup lang="ts">import { enumLabel } from '../i18n';
import { tr } from '../i18n';

import AppListToolbar from '../components/common/AppListToolbar.vue';
import { ElMenu, ElMenuItem, ElMenuItemGroup, ElTableColumn } from 'element-plus';
import 'element-plus/es/components/menu/style/css';
import { ElButton, ElSelect, ElOption, ElInput, ElCheckbox, ElInputNumber } from 'element-plus';

import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppIcon from '../components/common/AppIcon.vue';
import RowMoreActions from '../components/common/RowMoreActions.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import ListSearchInput from '../components/common/ListSearchInput.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import AppDialog from '../components/common/AppDialog.vue';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../stores/auth';
import { useBaseDataStore, BASE_DATA_GROUPS } from '../stores/baseData';
import { canUseBaseTab } from '../utils/baseDataAccess';
import * as api from '../api';
import TreeSelect, { type TreeSelectNode } from '../components/TreeSelect.vue';
import { confirmAction } from '../services/dialog';
import ListToolbarIcons from '../components/ListToolbarIcons.vue';

type BaseNode = {
  builtin?: boolean;
  id: string;
  code: string;
  name: string;
  parentId: string;
  status: string;
};

type BaseValue = {
  builtin?: boolean;
  id: string;
  code: string;
  name: string;
  precision?: number;
  status: string;
};

const apiMode = computed(() => auth.authState === 'ready');

const auth = useAuthStore();

const baseData = useBaseDataStore();

const { grantedActions } = storeToRefs(auth);

const {
  categoryData,
  materialCategoryData,
  locationData,
  faultTypeData,
  faultCauseData,
  unitData,
  useStateData,
  sourceData,
  processingResultData,
} = storeToRefs(baseData);

const router = useRouter();

const route = useRoute();

function can(menu: string, action = '查看') {
  return grantedActions.value[menu]?.includes(action) === true;
}

const baseTab = computed({
  get: () => String(route.query.tab || (can('base') ? '单位' : '设备分类')),
  set: (tab) => {
    router.replace({ name: 'base', query: { tab } });
  },
});

const baseDialog = ref(false);

const baseEditId = ref('');

const baseForm = reactive({ code: '', name: '', parentId: '', precision: 0 });

const baseSaving = ref(false);

const baseFormGuard = useUnsavedChanges(
  () => baseForm,
  () => baseDialog.value,
  () => baseSaving.value,
);

async function closeBaseDialog() {
  if (!(await baseFormGuard.permitLeave())) return;
  baseDialog.value = false;
}

const treeData = computed(() =>
  baseTab.value === '设备分类'
    ? categoryData.value
    : baseTab.value === '物料分类'
      ? materialCategoryData.value
      : baseTab.value === '故障类型'
        ? faultTypeData.value
        : locationData.value,
);

const currentBaseValues = computed(() =>
  baseTab.value === '单位'
    ? unitData.value
    : baseTab.value === '使用状态'
      ? useStateData.value
      : baseTab.value === '处理结果'
        ? processingResultData.value
        : baseTab.value === '故障原因'
          ? faultCauseData.value
          : sourceData.value,
);

const baseSearch = ref('');

const appliedBaseSearch = ref('');

const baseColumnFilterOpen = ref(false);

const baseColumnsOpen = ref(false);

const baseColumnFilters = reactive<Record<string, string>>({});

const baseVisibleColumns = ref<string[]>([]);

const isBaseTree = computed(() =>
  ['设备分类', '物料分类', '存放地点', '故障类型'].includes(baseTab.value),
);

function defaultBaseColumns() {
  return isBaseTree.value
    ? ['name', 'code', 'parent']
    : baseTab.value === '单位'
      ? ['name', 'code', 'precision']
      : ['name', 'code', 'status'];
}

function baseCell(item: BaseNode | BaseValue, key: string) {
  if (key === 'parent')
    return (
      treeData.value.find((x) => x.id === ('parentId' in item ? item.parentId : ''))?.name || ''
    );
  if (key === 'precision') return 'precision' in item ? `${item.precision ?? 0} 位` : '';
  return String((item as BaseNode & BaseValue)[key as keyof BaseNode] ?? '');
}

function baseMatches(item: BaseNode | BaseValue) {
  const term = appliedBaseSearch.value.trim().toLowerCase();
  const text = [
    item.name,
    item.code,
    'parentId' in item ? treeData.value.find((x) => x.id === item.parentId)?.name : '',
    'precision' in item ? String(item.precision) : '',
    item.status,
  ]
    .join(' ')
    .toLowerCase();
  return (
    (!term || text.includes(term)) &&
    Object.entries(baseColumnFilters).every(
      ([key, value]) =>
        !value || baseCell(item, key).toLowerCase().includes(value.trim().toLowerCase()),
    )
  );
}

const filteredTreeRows = computed(() => treeRows.value.filter(baseMatches));

const filteredBaseValues = computed(() => currentBaseValues.value.filter(baseMatches));

function searchBase() {
  appliedBaseSearch.value = baseSearch.value;
}

function resetBase() {
  baseSearch.value = '';
  appliedBaseSearch.value = '';
  Object.keys(baseColumnFilters).forEach((key) => delete baseColumnFilters[key]);
}

function exportBaseCsv() {
  const source = isBaseTree.value ? filteredTreeRows.value : filteredBaseValues.value;
  const keys = baseVisibleColumns.value;
  const headers = keys.map((key) =>
    key === 'name'
      ? baseTab.value === '设备分类'
        ? '分类名称'
        : baseTab.value === '存放地点'
          ? '地点名称'
          : baseTab.value === '故障类型'
            ? '故障类型'
            : '名称'
      : key === 'code'
        ? '编码'
        : key === 'parent'
          ? '上级节点'
          : key === 'precision'
            ? '小数精度'
            : '状态',
  );
  const lines = source.map((item) =>
    keys.map((key) => `\"${baseCell(item, key).replaceAll('\"', '\"\"')}\"`).join(','),
  );
  const blob = new Blob(['\ufeff' + [headers.join(','), ...lines].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = `${baseTab.value}.csv`;
  link.click();
  URL.revokeObjectURL(link.href);
}

function toggleBaseColumn(key: string) {
  baseVisibleColumns.value = baseVisibleColumns.value.includes(key)
    ? baseVisibleColumns.value.filter((x) => x !== key)
    : [...defaultBaseColumns().filter((x) => baseVisibleColumns.value.includes(x) || x === key)];
}

const treeRows = computed(() => {
  const result: Array<BaseNode & { level: number }> = [];
  const add = (parentId: string, level: number) => {
    treeData.value
      .filter((x) => x.parentId === parentId)
      .forEach((item) => {
        result.push({ ...item, level });
        if (expandedTreeIds.value[item.id] !== false) add(item.id, level + 1);
      });
  };
  add('', 0);
  return result;
});

const parentOptions = computed<TreeSelectNode[]>(() =>
  treeData.value
    .filter(
      (item) =>
        item.id !== baseEditId.value &&
        (!baseEditId.value || !isDescendant(item.id, baseEditId.value, treeData.value)),
    )
    .map((item) => ({
      ...item,
      value: item.id,
      status: undefined,
      disabled: item.status === '停用',
    })),
);

const baseGroups = computed(() =>
  BASE_DATA_GROUPS.map((group) => ({
    ...group,
    items: group.items.filter((tab) => canUseBaseTab(can, tab)),
  })).filter((group) => group.items.length),
);
const baseCan = (action: string) => canUseBaseTab(can, baseTab.value, action);

const expandedTreeIds = ref<Record<string, boolean>>({});

function notify(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'success') {
  ElMessage({ message, type });
}

async function refreshBase(tab = baseTab.value) {
  if (!apiMode.value) return;
  try {
    await baseData.load(tab);
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text0042'), 'error');
  }
}

function openBaseCreate(parentId = '') {
  baseEditId.value = '';
  Object.assign(baseForm, { code: '', name: '', parentId, precision: 0 });
  baseFormGuard.markSaved();
  baseDialog.value = true;
}

function openBaseEdit(item: BaseNode | BaseValue) {
  baseEditId.value = item.id;
  Object.assign(baseForm, {
    code: item.code,
    name: item.name,
    parentId: 'parentId' in item ? item.parentId : '',
    precision: 'precision' in item ? item.precision || 0 : 0,
  });
  baseFormGuard.markSaved();
  baseDialog.value = true;
}

async function saveBase() {
  if (baseSaving.value) return;
  if (!baseForm.name.trim()) {
    notify(tr('ui.validation.message36'), 'error');
    return;
  }
  if (
    baseTab.value === '设备分类' ||
    baseTab.value === '物料分类' ||
    baseTab.value === '存放地点' ||
    baseTab.value === '故障类型'
  ) {
    const target = treeData.value;
    if (
      baseForm.code.trim() &&
      target.some((x) => x.code === baseForm.code.trim() && x.id !== baseEditId.value)
    ) {
      notify('编码已存在', 'error');
      return;
    }
    if (
      baseEditId.value &&
      baseForm.parentId &&
      isDescendant(baseForm.parentId, baseEditId.value, target)
    ) {
      notify(tr('ui.validation.message37'), 'error');
      return;
    }
  } else if (
    baseForm.code.trim() &&
    currentBaseValues.value.some(
      (x) => x.code === baseForm.code.trim() && x.id !== baseEditId.value,
    )
  ) {
    notify('编码已存在', 'error');
    return;
  }
  const payload = {
    id: baseEditId.value,
    code: baseForm.code.trim(),
    name: baseForm.name.trim(),
    parentId: baseForm.parentId,
    precision: baseForm.precision,
    status: '启用',
  };
  baseSaving.value = true;
  try {
    if (apiMode.value) {
      const saved = await api.saveBase(baseTab.value, payload, !baseEditId.value);
      const item = {
        ...saved.data,
        parentId: saved.data.parentId || '',
        status: saved.data.status,
      };
      if (baseTab.value === '设备分类')
        categoryData.value = baseEditId.value
          ? categoryData.value.map((x) => (x.id === item.id ? item : x))
          : [...categoryData.value, item];
      else if (baseTab.value === '物料分类')
        materialCategoryData.value = baseEditId.value
          ? materialCategoryData.value.map((x) => (x.id === item.id ? item : x))
          : [...materialCategoryData.value, item];
      else if (baseTab.value === '存放地点')
        locationData.value = baseEditId.value
          ? locationData.value.map((x) => (x.id === item.id ? item : x))
          : [...locationData.value, item];
      else if (baseTab.value === '故障类型')
        faultTypeData.value = baseEditId.value
          ? faultTypeData.value.map((x) => (x.id === item.id ? item : x))
          : [...faultTypeData.value, item];
      else if (baseTab.value === '故障原因')
        faultCauseData.value = baseEditId.value
          ? faultCauseData.value.map((x) => (x.id === item.id ? item : x))
          : [...faultCauseData.value, item];
      else if (baseTab.value === '单位')
        unitData.value = baseEditId.value
          ? unitData.value.map((x) => (x.id === item.id ? item : x))
          : [...unitData.value, item];
      else if (baseTab.value === '使用状态')
        useStateData.value = baseEditId.value
          ? useStateData.value.map((x) => (x.id === item.id ? item : x))
          : [...useStateData.value, item];
      else if (baseTab.value === '处理结果')
        processingResultData.value = baseEditId.value
          ? processingResultData.value.map((x) => (x.id === item.id ? item : x))
          : [...processingResultData.value, item];
      else
        sourceData.value = baseEditId.value
          ? sourceData.value.map((x) => (x.id === item.id ? item : x))
          : [...sourceData.value, item];
    } else if (
      baseTab.value === '设备分类' ||
      baseTab.value === '物料分类' ||
      baseTab.value === '存放地点' ||
      baseTab.value === '故障类型'
    ) {
      const target = treeData.value,
        item = { ...payload, id: baseEditId.value || crypto.randomUUID() };
      if (baseEditId.value) {
        const i = target.findIndex((x) => x.id === baseEditId.value);
        if (i >= 0) target[i] = item;
      } else target.push(item);
    } else {
      const target = currentBaseValues.value,
        item = { ...payload, id: baseEditId.value || crypto.randomUUID() };
      if (baseEditId.value) {
        const i = target.findIndex((x) => x.id === baseEditId.value);
        if (i >= 0) target[i] = item;
      } else target.push(item);
    }
    baseFormGuard.markSaved();
    baseDialog.value = false;
    notify('基础数据已保存');
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text0195'), 'error');
  } finally {
    baseSaving.value = false;
  }
}

function isDescendant(candidateId: string, parentId: string, items: BaseNode[]): boolean {
  const children = items.filter((x) => x.parentId === parentId);
  return children.some((x) => x.id === candidateId || isDescendant(candidateId, x.id, items));
}

async function deleteBase(item: BaseNode | BaseValue) {
  if ('parentId' in item) {
    const target = treeData.value;
    if (target.some((x) => x.parentId === item.id)) {
      notify('该节点下还有子节点，不能删除', 'error');
      return;
    }
  }
  if (!(await confirmAction(tr('ui.common.dynamic50',{p0:item.name})))) return;
  try {
    if (apiMode.value) await api.deleteBase(baseTab.value, item.id);
    if (baseTab.value === '设备分类')
      categoryData.value = categoryData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '物料分类')
      materialCategoryData.value = materialCategoryData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '存放地点')
      locationData.value = locationData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '故障类型')
      faultTypeData.value = faultTypeData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '故障原因')
      faultCauseData.value = faultCauseData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '单位')
      unitData.value = unitData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '使用状态')
      useStateData.value = useStateData.value.filter((x) => x.id !== item.id);
    else if (baseTab.value === '处理结果')
      processingResultData.value = processingResultData.value.filter((x) => x.id !== item.id);
    else sourceData.value = sourceData.value.filter((x) => x.id !== item.id);
    notify('已删除');
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text0278'), 'error');
  }
}

async function toggleBaseStatus(item: BaseValue) {
  const status = item.status === '启用' ? '停用' : '启用';
  try {
    if (apiMode.value) await api.changeBaseStatus(baseTab.value, item, status);
    item.status = status;
  } catch (error) {
    notify(error instanceof Error ? error.message : tr('ui.equipment.text1190'), 'error');
  }
}

if (!baseVisibleColumns.value.length) baseVisibleColumns.value = defaultBaseColumns();
watch(baseTab, () => {
  baseSearch.value = '';
  appliedBaseSearch.value = '';
  baseVisibleColumns.value = defaultBaseColumns();
  Object.keys(baseColumnFilters).forEach((key) => delete baseColumnFilters[key]);
});
watch(baseTab, () => {
  baseDialog.value = false;
  void refreshBase();
});
onMounted(() => {
  void refreshBase();
});
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => filteredTreeRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => filteredBaseValues.value;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
</script>

<template>
  <section class="content base-page">
    <aside class="base-data-navigation">
      <ElMenu :default-active="baseTab" :aria-label="tr('ui.equipment.text1622')" @select="baseTab = $event">
        <ElMenuItemGroup v-for="group in baseGroups" :key="group.key" :title="group.label">
          <ElMenuItem v-for="tab in group.items" :key="tab" :index="tab">{{ tab }}</ElMenuItem>
        </ElMenuItemGroup>
      </ElMenu>
    </aside>
    <div class="base-workspace">
      <AppListToolbar class="base-toolbar">
        <div class="base-actions">
          <ElButton v-if="baseCan('新增')" class="" @click="openBaseCreate()" type="primary"
            ><span class="plus"> <AppIcon name="Plus" /> </span>{{ tr('ui.equipment.text0149') }}{{
              baseTab === '设备分类'
                ? tr('ui.equipment.text0476')
                : baseTab === '存放地点'
                  ? tr('ui.equipment.text1623')
                  : baseTab === '故障类型'
                    ? tr('ui.equipment.text1040')
                    : baseTab === '单位'
                      ? tr('ui.equipment.text0095')
                      : baseTab === '使用状态'
                        ? tr('ui.equipment.text0112')
                        : baseTab
            }}</ElButton
          >
        </div>
        <form class="base-list-filters" @submit.prevent="searchBase">
          <ListToolbarIcons
            mode="filter"
            :can-export="baseCan('导出')"
            :filter-open="baseColumnFilterOpen"
            :columns-open="baseColumnsOpen"
            @toggle-filter="baseColumnFilterOpen = !baseColumnFilterOpen"
            @toggle-columns="baseColumnsOpen = !baseColumnsOpen"
            @export="exportBaseCsv"
          />
          <ListSearchInput
            v-model="baseSearch"
            :placeholder="`搜索${baseTab}名称、编码${isBaseTree ? tr('ui.equipment.text1624') : ''}`"
          />
          <ElButton class="base-reset" @click="resetBase" native-type="button" text>{{ tr('ui.equipment.text0204') }}</ElButton>
        </form>
      </AppListToolbar>
      <div class="base-card">
        <div class="base-table-scroll">
          <AppBusinessTable
            :rows="filteredTreeRows"
            :pagination="false"
            :fill="true"
            :framed="false"
            v-if="isBaseTree"
            class="business-table base-table"
          >
            <!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="key in baseVisibleColumns"
              :min-width="210"
              :prop="String(key)"
              :key="key"
              ><template #header>
                <div class="table-header-label">
                  {{
                    key === 'name'
                      ? baseTab === '设备分类'
                        ? tr('ui.equipment.text1625')
                        : baseTab === '存放地点'
                          ? tr('ui.equipment.text1626')
                          : baseTab === '故障类型'
                            ? tr('ui.equipment.text1040')
                            : tr('ui.equipment.text0404')
                      : key === 'code'
                        ? tr('ui.equipment.text0474')
                        : key === 'parent'
                          ? tr('ui.equipment.text1627')
                          : key === 'precision'
                            ? tr('ui.equipment.text1628')
                            : tr('ui.equipment.text0170')
                  }}
                </div>
                <div v-if="baseColumnFilterOpen" class="table-column-filter" @click.stop>
                  <ElSelect v-if="key === 'status'" v-model="baseColumnFilters[key]"
                    ><ElOption value="" :label="tr('ui.equipment.text1205')" /><ElOption
                      :value="'启用'"
                      :label="tr('ui.equipment.text0216')" /><ElOption :value="'停用'" :label="tr('ui.equipment.text0260')" /></ElSelect
                  ><ElInput
                    v-else
                    v-model="baseColumnFilters[key]"
                    :placeholder="`筛选${key === 'name' ? tr('ui.equipment.text0404') : key === 'code' ? tr('ui.equipment.text0474') : key === 'parent' ? tr('ui.equipment.text1627') : tr('ui.equipment.text1629')}`"
                  />
                </div> </template
              ><template #default="{ row: item, $index: tableRowIndex }">
                <div
                  v-if="key === 'name'"
                  class="tree-label"
                  :style="{ paddingLeft: item.level * 22 + 12 + 'px' }"
                >
                  <ElButton
                    v-if="treeData.some((node) => node.parentId === item.id)"
                    class="tree-toggle"
                    :aria-label="expandedTreeIds[item.id] === false ? tr('ui.equipment.text0724') : tr('ui.equipment.text0725')"
                    @click.stop="
                      expandedTreeIds[item.id] = expandedTreeIds[item.id] === false ? true : false
                    "
                    ><AppIcon
                      :name="expandedTreeIds[item.id] === false ? 'Right' : 'Down'" /></ElButton
                  ><span v-else class="tree-spacer"></span><span class="tree-node-dot"></span
                  ><b>{{ item.name }}</b>
                </div>
                <template v-else>{{ baseCell(item, key) }}</template>
              </template></ElTableColumn
            >
            <!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="base-row-actions list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header> <div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div> </template
              ><template #default="{ row: item, $index: tableRowIndex }">
                <ElButton
                  v-if="baseCan('新增')"
                  class="text-action"
                  @click="openBaseCreate(item.id)"
                  type="primary"
                  link
                  >{{ tr('ui.equipment.text0726') }}</ElButton
                ><ElButton
                  v-if="baseCan('编辑') && !item.builtin"
                  class="text-action"
                  @click="openBaseEdit(item)"
                  type="primary"
                  link
                  >{{ tr('ui.equipment.text0207') }}</ElButton
                >
                <RowMoreActions
                  v-if="(isBaseTree || baseTab !== '单位') && baseCan('删除') && !item.builtin"
                  :actions="[{ label: '删除', danger: true, run: () => deleteBase(item) }]"
                /><ElButton
                  v-else-if="!isBaseTree && baseTab === '单位' && baseCan('删除')"
                  class="text-action danger-action"
                  @click="deleteBase(item)"
                  type="danger"
                  link
                >
                  {{ tr('ui.equipment.text0146') }}
                </ElButton>
              </template></ElTableColumn
            >
          </AppBusinessTable>
          <AppBusinessTable
            :rows="filteredBaseValues"
            :pagination="false"
            :fill="true"
            :framed="false"
            v-else
            class="business-table base-table"
          >
            <!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="key in baseVisibleColumns"
              :min-width="210"
              :prop="String(key)"
              :key="key"
              ><template #header>
                <div class="table-header-label">
                  {{
                    key === 'name'
                      ? baseTab === '设备分类'
                        ? tr('ui.equipment.text1625')
                        : baseTab === '存放地点'
                          ? tr('ui.equipment.text1626')
                          : baseTab === '故障类型'
                            ? tr('ui.equipment.text1040')
                            : tr('ui.equipment.text0404')
                      : key === 'code'
                        ? tr('ui.equipment.text0474')
                        : key === 'parent'
                          ? tr('ui.equipment.text1627')
                          : key === 'precision'
                            ? tr('ui.equipment.text1628')
                            : tr('ui.equipment.text0170')
                  }}
                </div>
                <div v-if="baseColumnFilterOpen" class="table-column-filter" @click.stop>
                  <ElSelect v-if="key === 'status'" v-model="baseColumnFilters[key]"
                    ><ElOption value="" :label="tr('ui.equipment.text1205')" /><ElOption
                      :value="'启用'"
                      :label="tr('ui.equipment.text0216')" /><ElOption :value="'停用'" :label="tr('ui.equipment.text0260')" /></ElSelect
                  ><ElInput
                    v-else
                    v-model="baseColumnFilters[key]"
                    :placeholder="`筛选${key === 'name' ? tr('ui.equipment.text0404') : key === 'code' ? tr('ui.equipment.text0474') : key === 'parent' ? tr('ui.equipment.text1627') : tr('ui.equipment.text1629')}`"
                  />
                </div> </template
              ><template #default="{ row: item, $index: tableRowIndex }">
                <b v-if="key === 'name'" class="base-value-name">{{ item.name }}</b
                ><span
                  v-else-if="key === 'status'"
                  class="status"
                  :class="item.status === '启用' ? 'success' : 'muted'"
                  ><i></i>{{ enumLabel('status',item.status) }}</span
                ><template v-else>{{ baseCell(item, key) }}</template>
              </template></ElTableColumn
            >
            <!-- @vue-generic {TableColumnRow2} -->
            <ElTableColumn
              class-name="base-row-actions list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header> <div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div> </template
              ><template #default="{ row: item, $index: tableRowIndex }">
                <ElButton
                  v-if="baseCan('编辑') && !item.builtin"
                  class="text-action"
                  @click="openBaseEdit(item)"
                  type="primary"
                  link
                  >{{ tr('ui.equipment.text0207') }}</ElButton
                ><ElButton
                  v-if="baseTab !== '单位' && baseCan('编辑') && !item.builtin"
                  class="text-action"
                  @click="toggleBaseStatus(item)"
                  type="primary"
                  link
                  >{{ item.status === '启用' ? tr('ui.equipment.text0260') : tr('ui.equipment.text0216') }}</ElButton
                >
                <RowMoreActions
                  v-if="(isBaseTree || baseTab !== '单位') && baseCan('删除') && !item.builtin"
                  :actions="[{ label: '删除', danger: true, run: () => deleteBase(item) }]"
                /><ElButton
                  v-else-if="!isBaseTree && baseTab === '单位' && baseCan('删除')"
                  class="text-action danger-action"
                  @click="deleteBase(item)"
                  type="danger"
                  link
                >
                  {{ tr('ui.equipment.text0146') }}
                </ElButton>
              </template></ElTableColumn
            >
          </AppBusinessTable>
        </div>
        <footer class="base-footer">
          <span>{{ tr('ui.equipment.text0309') }} {{ isBaseTree ? filteredTreeRows.length : filteredBaseValues.length }} {{ tr('ui.equipment.text0394') }}</span>
          <ListToolbarIcons
            mode="footer"
            :can-export="baseCan('导出')"
            :filter-open="baseColumnFilterOpen"
            :columns-open="baseColumnsOpen"
            @toggle-filter="baseColumnFilterOpen = !baseColumnFilterOpen"
            @toggle-columns="baseColumnsOpen = !baseColumnsOpen"
            @export="exportBaseCsv"
            ><template #columns
              ><ElCheckbox
                v-for="key in defaultBaseColumns()"
                :key="key"
                :model-value="baseVisibleColumns.includes(key)"
                @change="toggleBaseColumn(key)"
                >{{
                  key === 'name'
                    ? tr('ui.equipment.text0404')
                    : key === 'code'
                      ? tr('ui.equipment.text0474')
                      : key === 'parent'
                        ? tr('ui.equipment.text1627')
                        : key === 'precision'
                          ? tr('ui.equipment.text1628')
                          : tr('ui.equipment.text0170')
                }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </div>
  </section>

  <AppDialog
    v-model="baseDialog"
    :busy="baseSaving"
    :before-close="baseFormGuard.permitLeave"
    :title="`${baseEditId ? tr('ui.equipment.text0207') : tr('ui.equipment.text0149')}${baseTab}`"
    width="480px"
  >
    <div class="base-dialog-body" :inert="baseSaving || undefined">
      <label
        ><span>{{ tr('ui.equipment.text0474') }}</span
        ><ElInput v-model="baseForm.code" :placeholder="tr('ui.equipment.text1630')" maxlength="40" /></label
      ><label
        ><span>{{ tr('ui.equipment.text0404') }} <i>*</i></span
        ><ElInput v-model="baseForm.name" :placeholder="tr('ui.equipment.text0464')" maxlength="60" /></label
      ><label
        v-if="
          baseTab === '设备分类' ||
          baseTab === '物料分类' ||
          baseTab === '存放地点' ||
          baseTab === '故障类型'
        "
        ><span>{{ tr('ui.equipment.text1627') }}</span>
        <TreeSelect
          v-model="baseForm.parentId"
          :nodes="parentOptions"
          :placeholder="tr('ui.equipment.text1631')"
        /> </label
      ><label v-if="baseTab === '单位'"
        ><span>{{ tr('ui.equipment.text1628') }}</span
        ><ElInputNumber v-model="baseForm.precision" :min="0" :max="6" controls-position="right"
      /></label>
    </div>
    <template #footer
      ><ElButton class="" :disabled="baseSaving" @click="closeBaseDialog">{{ tr('ui.equipment.text0157') }}</ElButton
      ><ElButton class="" :disabled="baseSaving" @click="saveBase" type="primary">{{
        baseSaving ? tr('ui.equipment.text0158') : tr('ui.equipment.text0159')
      }}</ElButton></template
    >
  </AppDialog>
</template>

<style scoped>
.base-page {
  display: grid;
  grid-template-columns: 172px minmax(0, 1fr);
  gap: 12px;
}
.base-data-navigation {
  min-height: 0;
  overflow: auto;
  padding: 6px;
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
}
.base-data-navigation :deep(.el-menu) {
  --el-menu-item-height: 34px;
  --el-menu-text-color: var(--el-text-color-primary);
  border-right: 0;
}
.base-data-navigation :deep(.el-menu-item-group__title) {
  padding: 10px 12px 6px;
  font-size: 13px;
  font-weight: 600;
  color: var(--el-text-color-primary);
}
.base-data-navigation :deep(.el-menu-item) {
  padding-left: 24px;
  font-size: 13px;
  border-radius: 4px;
}
.base-data-navigation :deep(.el-menu-item.is-active) {
  background: var(--el-color-primary-light-9);
}
.base-workspace {
  display: flex;
  flex-direction: column;
  min-width: 0;
  min-height: 0;
}
.base-toolbar {
  margin-top: 0;
}
@media (max-width: 800px) {
  .base-page {
    grid-template-columns: 140px minmax(0, 1fr);
    gap: 8px;
  }
}
</style>
