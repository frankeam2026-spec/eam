<script setup lang="ts">import { tr, enumLabel } from '../i18n';

import { computed, nextTick, reactive, ref, watch } from 'vue';
import {
  ElAlert,
  ElButton,
  ElDescriptions,
  ElDescriptionsItem,
  ElEmpty,
  ElForm,
  ElInput,
  ElOption,
  ElSelect,
  ElSkeleton,
  ElTableColumn,
  ElTag,
  ElTree,
  ElTreeSelect,
  ElMessage,
  type FormInstance,
  type FormRules,
  type TreeInstance,
  type TreeNodeData,
} from 'element-plus';
import 'element-plus/es/components/descriptions/style/css';
import 'element-plus/es/components/alert/style/css';
import 'element-plus/es/components/empty/style/css';
import 'element-plus/es/components/skeleton/style/css';
import AppListToolbar from './common/AppListToolbar.vue';
import AppBusinessTable from './common/AppBusinessTable.vue';
import AppDialog from './common/AppDialog.vue';
import AppFormField from './common/AppFormField.vue';
import AppIcon from './common/AppIcon.vue';
import OrganizationIcon from './common/OrganizationIcon.vue';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { confirmAction } from '../services/dialog';
import * as api from '../api';
import type { OrganizationRecord } from '../api';

import { organizationTree } from '../utils/organizationTree';
import DepartmentText from './common/DepartmentText.vue';
import { useOrganizationDisplayStore } from '../stores/organizationDisplay';
const organizationDisplay = useOrganizationDisplayStore();
const props = defineProps<{ apiReady: boolean; grantedActions: Record<string, string[]> }>();
const can = (action: string) => props.grantedActions.org?.includes(action) === true;
const orgs = ref<OrganizationRecord[]>([]);
const loading = ref(false),
  loadError = ref(''),
  search = ref(''),
  selectedId = ref('');
const tree = ref<TreeInstance>();
const expandedKeys = ref<string[]>([]);
const selected = computed(() => orgs.value.find((item) => item.id === selectedId.value));
const children = computed(() => orgs.value.filter((item) => item.parentId === selectedId.value));
const root = computed(() => orgs.value.find((item) => !item.parentId));
const byId = computed(() => new Map(orgs.value.map((item) => [item.id, item])));
const treeData = computed(() => organizationTree(orgs.value));
function ancestors(id: string) {
  const result: OrganizationRecord[] = [],
    seen = new Set<string>();
  let current = byId.value.get(id);
  while (current && !seen.has(current.id)) {
    seen.add(current.id);
    result.unshift(current);
    current = byId.value.get(current.parentId);
  }
  return result;
}
const selectedPath = computed(() =>
  ancestors(selectedId.value)
    .map((item) => item.name)
    .join(' > '),
);
function selectOrg(item: OrganizationRecord) {
  selectedId.value = item.id;
  for (const parent of ancestors(item.id).slice(0, -1)) {
    const node = tree.value?.getNode(parent.id);
    if (node) node.expanded = true;
    rememberExpanded(parent);
  }
  tree.value?.setCurrentKey(item.id);
}
function rememberExpanded(item: OrganizationRecord) {
  if (!expandedKeys.value.includes(item.id)) expandedKeys.value.push(item.id);
}
function rememberCollapsed(item: OrganizationRecord) {
  expandedKeys.value = expandedKeys.value.filter((id) => id !== item.id);
}
function filterNode(value: string, data: TreeNodeData) {
  return (
    !value ||
    `${data.name} ${data.code} ${data.manager} ${data.type}`
      .toLocaleLowerCase()
      .includes(value.trim().toLocaleLowerCase())
  );
}
watch(search, (value) => tree.value?.filter(value));
function expandAll(expanded: boolean) {
  // Use the native tree node state; filtering and indentation remain owned by ElTree.
  for (const item of orgs.value) {
    const node = tree.value?.getNode(item.id);
    if (node) node.expanded = expanded;
  }
  expandedKeys.value = expanded ? orgs.value.map((item) => item.id) : [];
}
let requestVersion = 0;
async function loadOrganizations() {
  const version = ++requestVersion;
  loading.value = true;
  loadError.value = '';
  try {
    const rows = props.apiReady
      ? (await api.listOrganizations()).data
      : JSON.parse(localStorage.getItem('eam-orgs-v1') || '[]');
    if (version !== requestVersion) return;
    const firstLoad = !orgs.value.length;
    orgs.value = Array.isArray(rows) ? rows : [];
    if (props.apiReady) organizationDisplay.remember(orgs.value);
    if (!orgs.value.some((item) => item.id === selectedId.value))
      selectedId.value = root.value?.id || orgs.value[0]?.id || '';
    if (firstLoad) expandedKeys.value = treeData.value.map((item) => item.id);
    loading.value = false;
    await nextTick();
    tree.value?.filter(search.value);
    tree.value?.setCurrentKey(selectedId.value);
  } catch (error) {
    if (version === requestVersion)
      loadError.value = error instanceof Error ? error.message : tr('ui.equipment.text0897');
  } finally {
    if (version === requestVersion) loading.value = false;
  }
}
watch(() => props.apiReady, loadOrganizations, { immediate: true });

const dialog = ref(false),
  saving = ref(false),
  editing = ref(false),
  removing = ref(false);
const formRef = ref<FormInstance>();
const form = reactive<OrganizationRecord>({
  id: '',
  name: '',
  code: '',
  type: '公司',
  parentId: '',
  manager: '',
  status: '启用',
});
const guard = useUnsavedChanges(
  () => form,
  () => dialog.value,
  () => saving.value,
);
const original = computed(() => byId.value.get(form.id));
const types = computed<OrganizationRecord['type'][]>(() =>
  original.value
    ? original.value.type === '部门'
      ? ['部门']
      : ['公司', '集团']
    : ['公司', '集团', '部门'],
);
function companyOf(id: string) {
  return ancestors(id)
    .reverse()
    .find((item) => item.type !== '部门')?.id;
}
function parentAllowed(item: OrganizationRecord) {
  return (
    item.status === '启用' &&
    item.id !== form.id &&
    !ancestors(item.id).some((parent) => parent.id === form.id) &&
    (form.type === '部门' || item.type !== '部门') &&
    (!original.value ||
      original.value.type !== '部门' ||
      companyOf(item.id) === companyOf(original.value.id))
  );
}
const parentNodes = computed(() =>
  organizationTree(orgs.value.map((item) => ({ ...item, disabled: !parentAllowed(item) }))),
);
const parentRequired = computed(
  () => form.type === '部门' || !!(root.value && root.value.id !== form.id),
);
const rules: FormRules<OrganizationRecord> = {
  name: [{ required: true, whitespace: true, get message() { return tr('ui.equipment.text0898'); }, trigger: 'blur' }],
  code: [
    { required: true, whitespace: true, get message() { return tr('ui.equipment.text0899'); }, trigger: 'blur' },
    {
      validator: (_rule, value, done) =>
        done(
          orgs.value.some((item) => item.id !== form.id && item.code === String(value).trim())
            ? new Error(tr('ui.equipment.text0900'))
            : undefined,
        ),
      trigger: 'blur',
    },
  ],
  type: [{ required: true, get message() { return tr('ui.equipment.text0901'); }, trigger: 'change' }],
  parentId: [
    {
      validator: (_rule, value, done) => {
        if (!value && parentRequired.value) done(new Error(tr('ui.equipment.text0902')));
        else if (value && !orgs.value.some((item) => item.id === value && parentAllowed(item)))
          done(new Error(tr('ui.equipment.text0903')));
        else done();
      },
      trigger: 'change',
    },
  ],
};
watch(
  () => form.type,
  () => {
    if (
      form.parentId &&
      !orgs.value.some((item) => item.id === form.parentId && parentAllowed(item))
    )
      form.parentId = '';
    formRef.value?.clearValidate(['type', 'parentId']);
  },
);
async function openEditor(item?: OrganizationRecord) {
  if (!can(item ? '编辑' : '新增')) return;
  editing.value = !!item;
  Object.assign(
    form,
    item
      ? { ...item }
      : {
          id: '',
          name: '',
          code: '',
          type: root.value ? '部门' : '公司',
          parentId: selected.value?.id || root.value?.id || '',
          manager: '',
          status: '启用',
        },
  );
  guard.markSaved();
  dialog.value = true;
  await nextTick();
  formRef.value?.clearValidate();
}
async function closeEditor() {
  if (await guard.permitLeave()) dialog.value = false;
}
async function save() {
  if (
    saving.value ||
    !can(editing.value ? '编辑' : '新增') ||
    !(await formRef.value?.validate().catch(() => false))
  )
    return;
  saving.value = true;
  try {
    const payload = {
      ...form,
      name: form.name.trim(),
      code: form.code.trim(),
      manager: form.manager.trim(),
    };
    const result = props.apiReady
      ? (await api.saveOrganization(payload, !editing.value)).data
      : { ...payload, id: payload.id || crypto.randomUUID() };
    orgs.value = editing.value
      ? orgs.value.map((item) => (item.id === result.id ? result : item))
      : [...orgs.value, result];
    if (!props.apiReady) localStorage.setItem('eam-orgs-v1', JSON.stringify(orgs.value));
    guard.markSaved();
    dialog.value = false;
    await nextTick();
    selectOrg(result);
    ElMessage.success(tr('ui.equipment.text0194'));
    if (props.apiReady) window.dispatchEvent(new Event('eam-permissions-changed'));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : tr('ui.equipment.text0195'));
  } finally {
    saving.value = false;
  }
}
async function removeSelected() {
  const item = selected.value;
  if (!item || !can('删除') || removing.value) return;
  if (children.value.length) {
    ElMessage.error(tr('ui.equipment.text0904'));
    return;
  }
  if (!(await confirmAction(tr('ui.common.dynamic50',{p0:item.name})))) return;
  removing.value = true;
  try {
    if (props.apiReady) await api.deleteOrganization(item.id);
    else {
      const users: api.SystemUserRecord[] = JSON.parse(
        localStorage.getItem('eam-users-v1') || '[]',
      );
      if (users.some((user) => user.orgId === item.id)) throw new Error(tr('ui.equipment.text0905'));
    }
    orgs.value = orgs.value.filter((row) => row.id !== item.id);
    if (!props.apiReady) localStorage.setItem('eam-orgs-v1', JSON.stringify(orgs.value));
    selectedId.value = byId.value.has(item.parentId) ? item.parentId : orgs.value[0]?.id || '';
    tree.value?.setCurrentKey(selectedId.value);
    ElMessage.success(tr('ui.equipment.text0906'));
    if (props.apiReady) window.dispatchEvent(new Event('eam-permissions-changed'));
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : tr('ui.equipment.text0278'));
  } finally {
    removing.value = false;
  }
}
</script>

<template>
  <section class="content organization-management">
    <AppListToolbar>
      <ElButton
        v-if="can('新增')"
        type="primary"
        :disabled="loading || !!loadError"
        @click="openEditor()"
        ><AppIcon name="Plus" />{{ tr('ui.equipment.text0907') }}</ElButton
      >
      <div class="organization-tools">
        <ElInput
          v-model="search"
          :placeholder="tr('ui.equipment.text0908')"
          clearable
          :aria-label="tr('ui.equipment.text0909')"
          ><template #prefix><AppIcon name="Search" /></template
        ></ElInput>
        <ElButton :loading="loading" @click="loadOrganizations"
          ><AppIcon name="Refresh" />{{ tr('ui.equipment.text0424') }}</ElButton
        >
      </div>
    </AppListToolbar>
    <ElAlert v-if="loadError" :title="loadError" type="error" show-icon :closable="false"
      ><ElButton text type="primary" @click="loadOrganizations">{{ tr('ui.equipment.text0910') }}</ElButton></ElAlert
    >
    <ElSkeleton v-else-if="loading" :rows="8" animated class="organization-loading" />
    <div v-else class="organization-layout">
      <section class="organization-panel tree-panel">
        <div class="organization-panel-header">
          <strong
            >{{ tr('ui.equipment.text0036') }}<span class="organization-count">{{ orgs.length }}</span></strong
          >
          <div class="organization-actions">
            <ElButton text @click="expandAll(true)">{{ tr('ui.equipment.text0724') }}</ElButton
            ><ElButton text @click="expandAll(false)">{{ tr('ui.equipment.text0725') }}</ElButton>
          </div>
        </div>
        <div class="organization-tree-scroll">
          <ElTree
            ref="tree"
            :data="treeData"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :default-expanded-keys="expandedKeys"
            :auto-expand-parent="false"
            :current-node-key="selectedId"
            :filter-node-method="filterNode"
            :expand-on-click-node="false"
            highlight-current
            :empty-text="tr('ui.equipment.text0911')"
            @node-click="selectOrg"
            @node-expand="rememberExpanded"
            @node-collapse="rememberCollapsed"
          >
            <template #default="{ data }"
              ><span class="organization-node"
                ><OrganizationIcon :type="data.type" /><span
                  class="organization-node-name"
                  :title="data.name"
                  >{{ data.name }}</span
                ><ElTag v-if="data.status === '停用'" type="info" size="small">{{ tr('ui.equipment.text0260') }}</ElTag
                ><span class="organization-node-code" :title="data.code">{{
                  data.code
                }}</span></span
              ></template
            >
          </ElTree>
        </div>
      </section>
      <section class="organization-panel detail-panel">
        <template v-if="selected">
          <div class="organization-detail-identity">
            <OrganizationIcon :type="selected.type" />
            <div>
              <h2>{{ selected.name }}</h2>
              <span>{{ selected.code }}</span>
            </div>
            <ElTag :type="selected.status === '启用' ? 'success' : 'info'">{{
              selected.status
            }}</ElTag>
          </div>
          <div class="organization-detail-actions">
            <ElButton v-if="can('新增')" @click="openEditor()"
              ><AppIcon name="Plus" />{{ tr('ui.equipment.text0726') }}</ElButton
            ><ElButton v-if="can('编辑')" @click="openEditor(selected)">{{ tr('ui.equipment.text0207') }}</ElButton
            ><ElButton
              v-if="can('删除')"
              type="danger"
              plain
              :loading="removing"
              @click="removeSelected"
              >{{ tr('ui.equipment.text0146') }}</ElButton
            >
          </div>
          <div class="organization-detail-scroll">
            <ElDescriptions :column="1" border label-width="96px">
              <ElDescriptionsItem :label="tr('ui.equipment.text0912')">{{ selected.type }}</ElDescriptionsItem>
              <ElDescriptionsItem :label="tr('ui.equipment.text0913')">{{
                byId.get(selected.parentId)?.name || (selected.parentId ? '' : tr('ui.equipment.text0914'))
              }}</ElDescriptionsItem>
              <ElDescriptionsItem :label="tr('ui.equipment.text0915')"
                ><DepartmentText class="organization-path" :value="selectedPath" /></ElDescriptionsItem
              >
              <ElDescriptionsItem :label="tr('ui.equipment.text0102')">{{ selected.manager }}</ElDescriptionsItem>
              <ElDescriptionsItem :label="tr('ui.equipment.text0916')">{{ children.length }} {{ tr('ui.equipment.text0917') }}</ElDescriptionsItem>
            </ElDescriptions>
            <div class="organization-children-heading">
              <strong>{{ tr('ui.equipment.text0916') }}</strong><span>{{ children.length }} {{ tr('ui.equipment.text0917') }}</span>
            </div>
            <AppBusinessTable
              v-if="children.length"
              :rows="children"
              row-key="id"
              :fill="false"
              :pagination="false"
              :max-height="280"
              @row-click="selectOrg"
            >
              <ElTableColumn prop="name" :label="tr('ui.equipment.text0918')" min-width="160" show-overflow-tooltip
                ><template #default="{ row }"
                  ><ElButton type="primary" link @click.stop="selectOrg(byId.get(row.id)!)">{{
                    row.name
                  }}</ElButton></template
                ></ElTableColumn
              >
              <ElTableColumn prop="code" :label="tr('ui.equipment.text0919')" min-width="100" show-overflow-tooltip />
              <ElTableColumn prop="type" :label="tr('ui.equipment.text0142')" width="75" />
              <ElTableColumn :formatter="(_row:unknown,_column:unknown,value:unknown)=>enumLabel('status',value)" prop="status" :label="tr('ui.equipment.text0170')" width="80" align="center"
                ><template #default="{ row }"
                  ><ElTag :type="row.status === '启用' ? 'success' : 'info'" size="small">{{
                    enumLabel('status',row.status)
                  }}</ElTag></template
                ></ElTableColumn
              >
            </AppBusinessTable>
            <p v-else class="organization-empty-note">{{ tr('ui.equipment.text0920') }}</p>
          </div>
        </template>
        <ElEmpty v-else description="选择左侧组织查看详情" :image-size="72" />
      </section>
    </div>
    <AppDialog
      v-model="dialog"
      :title="editing ? tr('ui.equipment.text0921') : tr('ui.equipment.text0907')"
      :busy="saving"
      :before-close="guard.permitLeave"
    >
      <ElForm
        ref="formRef"
        :model="form"
        :rules="rules"
        label-width="96px"
        class="organization-form"
        :disabled="saving"
        @submit.prevent="save"
      >
        <AppFormField :label="tr('ui.equipment.text0912')" prop="type"
          ><ElSelect v-model="form.type"
            ><ElOption v-for="type in types" :key="type" :label="enumLabel('systemOption', type)" :value="type" /></ElSelect
        ></AppFormField>
        <AppFormField :label="tr('ui.equipment.text0913')" prop="parentId" :required="parentRequired"
          ><ElTreeSelect
            v-model="form.parentId"
            :data="parentNodes"
            node-key="id"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            check-strictly
            filterable
            clearable
            :render-after-expand="false"
            :placeholder="parentRequired ? tr('ui.equipment.text0902') : tr('ui.equipment.text0914')"
        /></AppFormField>
        <AppFormField :label="tr('ui.equipment.text0918')" prop="name"
          ><ElInput v-model="form.name" :placeholder="tr('ui.equipment.text0898')"
        /></AppFormField>
        <AppFormField :label="tr('ui.equipment.text0919')" prop="code"
          ><ElInput v-model="form.code" :placeholder="tr('ui.equipment.text0922')"
        /></AppFormField>
        <AppFormField :label="tr('ui.equipment.text0102')" prop="manager"
          ><ElInput v-model="form.manager" :placeholder="tr('ui.equipment.text0923')"
        /></AppFormField>
        <AppFormField :label="tr('ui.equipment.text0170')" prop="status"
          ><ElSelect v-model="form.status"
            ><ElOption :label="tr('ui.equipment.text0216')" value="启用" /><ElOption :label="tr('ui.equipment.text0260')" value="停用" /></ElSelect
        ></AppFormField>
      </ElForm>
      <template #footer
        ><ElButton :disabled="saving" @click="closeEditor">{{ tr('ui.equipment.text0157') }}</ElButton
        ><ElButton type="primary" :loading="saving" @click="save">{{ tr('ui.equipment.text0159') }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>

<style scoped>
.organization-management {
  height: 100%;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.organization-tools,
.organization-actions,
.organization-detail-actions {
  display: flex;
  align-items: center;
  gap: 8px;
}
.organization-tools {
  margin-left: auto;
}
.organization-tools > .el-input {
  width: 300px;
}
.organization-layout {
  display: grid;
  grid-template-columns: minmax(300px, 2fr) minmax(420px, 3fr);
  gap: 12px;
  flex: 1;
  min-height: 0;
}
.organization-panel {
  background: var(--el-bg-color);
  border: 1px solid var(--el-border-color-light);
  border-radius: 6px;
  min-width: 0;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.organization-panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 8px;
  padding: 10px 12px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
.organization-panel-header strong,
.organization-children-heading strong {
  font-size: 14px;
}
.organization-count {
  margin-left: 8px;
  font-size: 13px;
  font-weight: normal;
}
.organization-actions :deep(.el-button),
.organization-detail-actions :deep(.el-button) {
  margin-left: 0;
}
.organization-tree-scroll {
  padding: 8px;
  overflow: auto;
  min-height: 0;
  flex: 1;
}
.organization-tree-scroll :deep(.el-tree) {
  --el-tree-node-content-height: 38px;
  --el-tree-text-color: var(--el-text-color-primary);
  font-size: 13px;
}
.organization-tree-scroll :deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--el-color-primary);
}
.organization-node {
  display: flex;
  align-items: center;
  gap: 8px;
  flex: 1;
  min-width: 0;
  padding-right: 8px;
}
.organization-node :deep(.organization-icon),
.organization-detail-identity > :deep(.organization-icon) {
  color: var(--el-color-primary);
}
.organization-node-name {
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.organization-node-code {
  margin-left: auto;
  max-width: 100px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-size: 12px;
}
.organization-detail-identity {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: 16px 16px 12px;
}
.organization-detail-identity > div {
  min-width: 0;
  flex: 1;
  overflow-wrap: anywhere;
}
.organization-detail-identity h2 {
  margin: 0 0 4px;
  font-size: 16px;
  font-weight: 600;
}
.organization-detail-actions {
  padding: 0 16px 16px;
}
.organization-detail-scroll {
  padding: 0 16px 16px;
  overflow: auto;
  min-height: 0;
}
.organization-detail-scroll :deep(.el-descriptions__label),
.organization-detail-scroll :deep(.el-descriptions__content) {
  font-size: 13px;
  color: var(--el-text-color-primary);
}
.organization-path {
  overflow-wrap: anywhere;
}
.organization-children-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin: 20px 0 10px;
}
.organization-empty-note {
  margin: 8px 0;
}
.organization-form :deep(.el-select) {
  width: 100%;
}
.organization-loading {
  background: var(--el-bg-color);
  padding: 20px;
}
@media (max-width: 800px) {
  .organization-management {
    overflow: auto;
  }
  .organization-layout {
    display: flex;
    flex-direction: column;
    flex: none;
  }
  .tree-panel {
    max-height: 340px;
  }
  .organization-tools {
    flex-wrap: wrap;
    width: 100%;
  }
  .organization-tools > .el-input {
    flex: 1;
    width: auto;
    min-width: 180px;
  }
}
</style>
