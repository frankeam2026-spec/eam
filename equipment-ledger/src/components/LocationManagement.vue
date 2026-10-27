<script setup lang="ts">import { tr, enumLabel } from '../i18n';

import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElSelect, ElOption, ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import RowMoreActions from './common/RowMoreActions.vue';
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import {
  ElButton,
  ElDialog,
  ElInput,
  ElInputNumber,
  ElSwitch,
  ElTree,
  ElMessage,
} from 'element-plus';
import * as api from '../api';
import TreeSelect from './TreeSelect.vue';
import OrganizationIcon from './common/OrganizationIcon.vue';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import AppPagination from './common/AppPagination.vue';
import { useAuthStore } from '../stores/auth';
import { useBaseDataStore } from '../stores/baseData';
import { confirmAction } from '../services/dialog';
const auth = useAuthStore(),
  base = useBaseDataStore();
const can = (action: string) => auth.can('location', action);
const companies = ref<api.OrganizationRecord[]>([]),
  companyId = ref(''),
  companySearch = ref('');
const rows = ref<api.BaseRecord[]>([]),
  loading = ref(false),
  error = ref('');
const query = ref(''),
  search = ref(''),
  filterOpen = ref(false),
  columnsOpen = ref(false);
const filters = reactive({ code: '', name: '', status: '' });
const page = ref(1),
  pageSize = ref(20),
  collapsed = ref(new Set<string>());
const columns = reactive([
  { key: 'code', get label() { return tr('ui.equipment.text0712'); }, show: true },
  { key: 'name', get label() { return tr('ui.equipment.text0713'); }, show: true },
  { key: 'parent', get label() { return tr('ui.equipment.text0714'); }, show: true },
  { key: 'status', get label() { return tr('ui.equipment.text0170'); }, show: true },
  { key: 'sort', get label() { return tr('ui.equipment.text0406'); }, show: true },
]);
const visibleColumns = computed(() => columns.filter((c) => c.show));
const company = computed(() => companies.value.find((c) => c.id === companyId.value));
type CompanyNode = api.OrganizationRecord & { children: CompanyNode[] };
const companyTree = computed(() => {
  const included = new Set<string>();
  companies.value
    .filter(
      (c) =>
        ['公司', '集团'].includes(c.type) &&
        (!companySearch.value || c.name.includes(companySearch.value)),
    )
    .forEach((c) => {
      let node: api.OrganizationRecord | undefined = c;
      while (node && !included.has(node.id)) {
        included.add(node.id);
        node = companies.value.find((p) => p.id === node!.parentId);
      }
    });
  const build = (parentId: string): CompanyNode[] =>
    companies.value
      .filter(
        (c) =>
          included.has(c.id) &&
          (c.parentId === parentId ||
            (!parentId && !companies.value.some((p) => p.id === c.parentId))),
      )
      .map((c) => ({ ...c, children: build(c.id) }));
  return build('');
});
const matches = (r: api.BaseRecord) =>
  (!search.value ||
    [r.name, r.code].some((v) => v.toLowerCase().includes(search.value.toLowerCase()))) &&
  (!filters.code || r.code.includes(filters.code)) &&
  (!filters.name || r.name.includes(filters.name)) &&
  (!filters.status || r.status === filters.status);
const matching = computed(() => rows.value.filter(matches));
const isSearching = computed(
  () => !!(search.value || filters.code || filters.name || filters.status),
);
const treeRows = computed(() => {
  const keep = new Set<string>();
  matching.value.forEach((r) => {
    let node: api.BaseRecord | undefined = r;
    while (node && !keep.has(node.id)) {
      keep.add(node.id);
      node = rows.value.find((p) => p.id === node!.parentId);
    }
  });
  const result: (api.BaseRecord & { depth: number; hasChildren: boolean })[] = [];
  const walk = (parentId: string, depth: number, visited: Set<string>) =>
    rows.value
      .filter(
        (r) =>
          r.parentId === parentId || (!parentId && !rows.value.some((p) => p.id === r.parentId)),
      )
      .forEach((r) => {
        if (visited.has(r.id) || !keep.has(r.id)) return;
        result.push({ ...r, depth, hasChildren: rows.value.some((p) => p.parentId === r.id) });
        if (isSearching.value || !collapsed.value.has(r.id))
          walk(r.id, depth + 1, new Set([...visited, r.id]));
      });
  walk('', 0, new Set());
  return result;
});
const pageRows = computed(() =>
  treeRows.value.slice((page.value - 1) * pageSize.value, page.value * pageSize.value),
);
watch(
  [search, () => filters.code, () => filters.name, () => filters.status, pageSize],
  () => (page.value = 1),
);
let requestVersion = 0;
async function loadLocations() {
  const version = ++requestVersion;
  rows.value = [];
  error.value = '';
  page.value = 1;
  collapsed.value = new Set();
  if (!companyId.value) return;
  loading.value = true;
  try {
    const result = await api.listBase('存放地点', companyId.value);
    if (version === requestVersion) rows.value = result.data;
  } catch (e) {
    if (version === requestVersion) error.value = e instanceof Error ? e.message : tr('ui.equipment.text0715');
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}
watch(companyId, () => {
  search.value = '';
  query.value = '';
  Object.assign(filters, { code: '', name: '', status: '' });
  loadLocations();
});
onMounted(async () => {
  try {
    companies.value = (await api.listLocationCompanies()).data;
    companyId.value =
      companies.value.find((c) => ['公司', '集团'].includes(c.type) && c.status === '启用')?.id ||
      '';
  } catch (e) {
    error.value = e instanceof Error ? e.message : tr('ui.equipment.text0716');
  }
});
function chooseCompany(node: CompanyNode) {
  if (['公司', '集团'].includes(node.type)) companyId.value = node.id;
}
function toggle(id: string) {
  const next = new Set(collapsed.value);
  next.has(id) ? next.delete(id) : next.add(id);
  collapsed.value = next;
}
const dialog = ref(false),
  saving = ref(false),
  enabled = ref(true),
  formError = ref('');
const form = reactive<api.BaseRecord>({
  id: '',
  code: '',
  name: '',
  parentId: '',
  companyId: '',
  status: '启用',
  sortNo: 0,
});
const formGuard = useUnsavedChanges(
  () => ({ ...form, enabled: enabled.value }),
  () => dialog.value,
  () => saving.value,
);
onBeforeRouteLeave(() => !saving.value);
onBeforeRouteUpdate(() => !saving.value);
async function closeDialog(done?: () => void) {
  if (saving.value || !(await formGuard.permitLeave())) return;
  if (done) done();
  else dialog.value = false;
}
const parentNodes = computed(() => {
  const excluded = new Set([form.id]);
  let changed = true;
  while (changed) {
    changed = false;
    rows.value.forEach((r) => {
      if (excluded.has(r.parentId) && !excluded.has(r.id)) {
        excluded.add(r.id);
        changed = true;
      }
    });
  }
  return rows.value.filter((r) => !excluded.has(r.id)).map((r) => ({ ...r, value: r.id }));
});
function edit(row?: api.BaseRecord, parentId = '') {
  if (!companyId.value) return;
  Object.assign(
    form,
    { id: '', code: '', name: '', parentId, companyId: companyId.value, status: '启用', sortNo: 0 },
    row || {},
  );
  enabled.value = form.status === '启用';
  formError.value = '';
  formGuard.markSaved();
  dialog.value = true;
}
async function save() {
  if (saving.value) return;
  if (!form.name.trim()) {
    formError.value = tr('ui.equipment.text0717');
    return;
  }
  saving.value = true;
  formError.value = '';
  try {
    await api.saveBase('存放地点', { ...form, status: enabled.value ? '启用' : '停用' }, !form.id);
    formGuard.markSaved();
    dialog.value = false;
    ElMessage.success(tr('ui.equipment.text0718'));
    await loadLocations();
    await base.load('存放地点');
  } catch (e) {
    formError.value = e instanceof Error ? e.message : tr('ui.equipment.text0195');
  } finally {
    saving.value = false;
  }
}
async function remove(row: api.BaseRecord) {
  if (!(await confirmAction(tr('ui.common.dynamic44',{p0:row.name})))) return;
  try {
    await api.deleteBase('存放地点', row.id);
    await loadLocations();
    await base.load('存放地点');
    ElMessage.success(tr('ui.equipment.text0719'));
  } catch (e) {
    ElMessage.error(e instanceof Error ? e.message : tr('ui.equipment.text0278'));
  }
}
function reset() {
  query.value = '';
  search.value = '';
  Object.assign(filters, { code: '', name: '', status: '' });
}
function exportRows() {
  const csv =
    '\uFEFF' +
    [
      ['所属公司', '位置编码', '位置名称', '上级位置', '状态', '排序'],
      ...matching.value.map((r) => [
        company.value?.name,
        r.code,
        r.name,
        rows.value.find((p) => p.id === r.parentId)?.name || '',
        r.status,
        r.sortNo,
      ]),
    ]
      .map((row) => row.map((v) => '"' + String(v ?? '').replaceAll('"', '""') + '"').join(','))
      .join('\r\n');
  const url = URL.createObjectURL(new Blob([csv], { type: 'text/csv;charset=utf-8' }));
  const a = document.createElement('a');
  a.href = url;
  a.download = `${company.value?.name || ''}-存放地点.csv`;
  a.click();
  URL.revokeObjectURL(url);
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageRows.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];
</script>
<template>
  <section class="content locations-page">
    <aside class="location-company-panel">
      <div class="company-panel-heading">{{ tr('ui.equipment.text0561') }}</div>
      <ElInput v-model="companySearch" :placeholder="tr('ui.equipment.text0720')" clearable />
      <ElTree
        :data="companyTree"
        :props="{ label: 'name', children: 'children' }"
        node-key="id"
        :current-node-key="companyId"
        highlight-current
        default-expand-all
        :expand-on-click-node="false"
        @node-click="chooseCompany"
      >
        <template #default="{ data }"
          ><span class="company-node" :class="{ group: data.type === '集团' }"
            ><OrganizationIcon :type="data.type" /><span>{{ data.name }}</span></span
          ></template
        >
      </ElTree>
      <p v-if="!companyTree.length" class="location-empty">{{ tr('ui.equipment.text0721') }}</p>
    </aside>
    <div class="location-content">
      <AppListToolbar class="location-toolbar">
        <div class="location-toolbar-left">
          <ElButton
            v-if="can('新增')"
            type="primary"
            :disabled="!companyId || company?.status === '停用'"
            @click="edit()"
            ><AppIcon name="Plus" /> {{ tr('ui.equipment.text0149') }}</ElButton
          ><span class="selected-company">{{ company?.name || tr('ui.equipment.text0722') }}</span>
        </div>
        <form class="location-search" @submit.prevent="search = query.trim()">
          <ListToolbarIcons
            mode="filter"
            :filter-open="filterOpen"
            :columns-open="false"
            @toggle-filter="filterOpen = !filterOpen"
          /><ListSearchInput v-model="query" :placeholder="tr('ui.equipment.text0723')" /><ElButton
            @click="reset"
            >{{ tr('ui.equipment.text0204') }}</ElButton
          >
        </form>
      </AppListToolbar>
      <div class="location-table-card">
        <div class="location-table-scroll">
          <p v-if="error" class="location-error">
            {{ error }} <ElButton text @click="loadLocations">{{ tr('ui.equipment.text0299') }}</ElButton>
          </p>
          <AppBusinessTable
            :rows="pageRows"
            :pagination="false"
            :fill="true"
            :framed="false"
            class="business-table location-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="c in visibleColumns"
              :min-width="150"
              :prop="String(c)"
              :key="String(c)"
              ><template #header
                ><div class="table-header-label">
                  {{ c.label }}
                </div>
                <div v-if="filterOpen" class="table-column-filter" @click.stop>
                  <ElInput
                    v-if="c.key === 'code' || c.key === 'name'"
                    v-model="filters[c.key]"
                    clearable
                    :placeholder="tr('ui.equipment.text0301')"
                  /><ElSelect v-if="c.key === 'status'" v-model="filters.status"
                    ><ElOption value="" :label="tr('ui.equipment.text0300')" /><ElOption
                      :value="'启用'"
                      :label="tr('ui.equipment.text0216')" /><ElOption :value="'停用'" :label="tr('ui.equipment.text0260')"
                  /></ElSelect></div></template
              ><template #default="{ row: r, $index: tableRowIndex }">
                <template v-if="c.key === 'name'"
                  ><div class="location-node" :style="{ paddingLeft: `${r.depth * 20}px` }">
                    <ElButton
                      class="location-expand"
                      :disabled="!r.hasChildren"
                      :aria-label="collapsed.has(r.id) ? tr('ui.equipment.text0724') : tr('ui.equipment.text0725')"
                      @click="toggle(r.id)"
                      ><AppIcon
                        :name="
                          r.hasChildren ? (collapsed.has(r.id) ? 'Right' : 'Down') : ''
                        " /></ElButton
                    ><span>{{ r.name }}</span>
                  </div></template
                >
                <template v-else-if="c.key === 'parent'">{{
                  rows.find((p) => p.id === r.parentId)?.name || ''
                }}</template
                ><span
                  v-else-if="c.key === 'status'"
                  class="location-status"
                  :class="{ disabled: r.status === '停用' }"
                  >{{ enumLabel('status', r.status) }}</span
                ><template v-else-if="c.key === 'sort'">{{ r.sortNo }}</template
                ><template v-else>{{ r.code }}</template>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              ><template #header><div class="table-header-label">{{ tr('ui.equipment.text0145') }}</div></template
              ><template #default="{ row: r, $index: tableRowIndex }">
                <div class="location-actions">
                  <ElButton link type="primary" v-if="can('新增')" @click="edit(undefined, r.id)"
                    >{{ tr('ui.equipment.text0726') }}</ElButton
                  ><ElButton link type="primary" v-if="can('编辑')" @click="edit(r)">{{ tr('ui.equipment.text0207') }}</ElButton
                  ><RowMoreActions
                    v-if="can('删除') && can('新增') && can('编辑')"
                    :actions="[{ label: '删除', danger: true, run: () => remove(r) }]"
                  /><ElButton
                    v-else-if="can('删除')"
                    class="danger"
                    @click="remove(r)"
                    type="danger"
                    link
                    >{{ tr('ui.equipment.text0146') }}</ElButton
                  >
                </div>
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!pageRows.length">
                {{
                  loading
                    ? tr('ui.equipment.text0162')
                    : companyId
                      ? tr('ui.equipment.text0727')
                      : tr('ui.equipment.text0728')
                }}
              </div></template
            ></AppBusinessTable
          >
        </div>
        <footer class="location-footer">
          <span>{{ tr('ui.equipment.text0309') }} {{ matching.length }} {{ tr('ui.equipment.text0296') }}</span
          ><AppPagination
            v-model:page="page"
            v-model:page-size="pageSize"
            :total="treeRows.length"
          /><ListToolbarIcons
            mode="footer"
            :filter-open="false"
            :columns-open="columnsOpen"
            :can-export="can('导出')"
            @toggle-columns="columnsOpen = !columnsOpen"
            @export="exportRows"
            ><template #columns
              ><ElCheckbox
                v-for="c in columns"
                :key="c.key"
                v-model="c.show"
                :disabled="c.show && visibleColumns.length === 1"
                >{{ c.label }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </div>
    </div>
    <ElDialog
      v-model="dialog"
      :title="form.id ? tr('ui.equipment.text0729') : tr('ui.equipment.text0730')"
      width="580px"
      :close-on-click-modal="false"
      :close-on-press-escape="false"
      :before-close="closeDialog"
    >
      <form class="location-form" :inert="saving || undefined" @submit.prevent="save">
        <label><span>{{ tr('ui.equipment.text0487') }}</span><ElInput :model-value="company?.name" disabled /></label
        ><label
          ><span>{{ tr('ui.equipment.text0712') }}</span
          ><ElInput v-model="form.code" maxlength="40" :placeholder="tr('ui.equipment.text0731')" /></label
        ><label
          ><span><b>*</b> {{ tr('ui.equipment.text0713') }}</span
          ><ElInput v-model="form.name" maxlength="100" :placeholder="tr('ui.equipment.text0717')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text0714') }}</span
          ><TreeSelect
            v-model="form.parentId"
            :nodes="parentNodes"
            :placeholder="tr('ui.equipment.text0732')" /></label
        ><label
          ><span>{{ tr('ui.equipment.text0406') }}</span
          ><ElInputNumber
            v-model="form.sortNo"
            :min="0"
            :max="999999"
            controls-position="right" /></label
        ><label><span>{{ tr('ui.equipment.text0216') }}</span><ElSwitch v-model="enabled" /></label>
        <p v-if="formError" class="location-error">{{ formError }}</p>
      </form>
      <template #footer
        ><div class="location-dialog-buttons">
          <ElButton :disabled="saving" @click="closeDialog()">{{ tr('ui.equipment.text0157') }}</ElButton
          ><ElButton type="primary" :loading="saving" @click="save">{{ tr('ui.equipment.text0159') }}</ElButton>
        </div></template
      >
    </ElDialog>
  </section>
</template>
<style scoped>
.locations-page {
  display: flex;
  flex: 1;
  min-height: 0;
  gap: 12px;
  font-size: 13px;
  color: #263246;
  box-sizing: border-box;
  height: calc(100vh - 54px);
}
.location-company-panel {
  width: 240px;
  flex: none;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  background: white;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 12px;
  gap: 12px;
}
.company-panel-heading {
  font-weight: 700;
}
.location-company-panel :deep(.el-tree) {
  overflow: auto;
  flex: 1;
  color: #263246;
  font-size: 13px;
}
.location-company-panel :deep(.el-tree-node__content) {
  height: auto;
  min-height: 36px;
  padding: 5px 0;
}
.company-node {
  display: flex;
  gap: 7px;
  align-items: center;
  white-space: normal;
  line-height: 20px;
  padding-right: 8px;
}
.company-node :deep(.organization-icon) {
  color: var(--brand, #205cf5);
}
.company-node.group {
  font-weight: 700;
}
.location-content {
  display: flex;
  flex-direction: column;
  min-width: 0;
  flex: 1;
  gap: 12px;
  min-height: 0;
}
.location-toolbar-left,
.location-search {
  display: flex;
  align-items: center;
  gap: 12px;
}
.selected-company {
  font-weight: 600;
}
.location-table-card {
  display: flex;
  flex: 1;
  flex-direction: column;
  min-height: 0;
  border: 1px solid #dfe5ee;
  border-radius: 6px;
  overflow: hidden;
  background: white;
}
.location-table-scroll {
  flex: 1;
  overflow: auto;
  min-height: 0;
}
.location-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  min-width: 820px;
}
.location-table :deep(th) {
  position: sticky;
  top: 0;
  z-index: 2;
}
.location-table .list-action-column {
  min-width: 200px;
  width: 200px;
}
.location-node {
  display: flex;
  align-items: center;
  gap: 6px;
}
.location-expand {
  width: 20px;
  height: 24px;
  border: 0;
  background: none;
  color: #263246;
  cursor: pointer;
  font-size: 18px;
}
.location-expand:disabled {
  cursor: default;
}
.location-status {
  display: inline-block;
  padding: 3px 9px;
  background: #eaf7f0;
  color: #12805c;
  border-radius: 4px;
  font-weight: 600;
}
.location-status.disabled {
  background: #fff2e5;
  color: #a85d00;
}
.location-actions {
  display: flex;
  gap: 14px;
}
.location-actions button {
  background: none;
  border: 0;
  color: var(--brand, #205cf5);
  padding: 0;
  cursor: pointer;
}
.location-actions .danger {
  color: #c53c40;
}
.location-footer {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 8px 14px;
  border-top: 1px solid #e9edf3;
  flex: none;
  background: white;
}
.location-footer :deep(.list-tools) {
  margin-left: auto;
}
.location-empty {
  text-align: center !important;
  padding: 48px !important;
}
.location-error {
  color: #c53c40;
  padding: 8px 12px;
}
.location-form {
  display: grid;
  gap: 16px;
}
.location-form label {
  display: flex;
  align-items: center;
  gap: 14px;
}
.location-form label > span {
  width: 82px;
  flex: none;
}
.location-form label :deep(.el-input),
.location-form label :deep(.tree-select) {
  flex: 1;
}
.location-form b {
  color: #c53c40;
}
.location-dialog-buttons {
  display: flex;
  justify-content: center;
  gap: 20px;
}
.location-dialog-buttons :deep(.el-button + .el-button) {
  margin-left: 0;
}
.location-footer :deep(.list-columns-menu label) {
  display: flex;
  gap: 8px;
  align-items: center;
  padding: 6px;
}
@media (max-width: 1100px) {
  .location-company-panel {
    width: 200px;
  }
  .location-search {
    margin-left: auto;
  }
}
</style>
