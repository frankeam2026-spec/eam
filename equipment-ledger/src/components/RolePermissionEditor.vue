<script setup lang="ts">
import { computed, ref, onMounted } from 'vue';
import { ElCheckbox, ElInput, ElSelect, ElOption, ElTreeSelect, ElButton } from 'element-plus';
import { request } from '../api';
import {
  defaultScope,
  permissionGroups,
  scopeLabel,
  type DataScope,
  type MenuGrant,
  type MenuGrants,
} from '../rolePermissions';

type Org = {
  id: string;
  name: string;
  type: '集团' | '公司' | '部门';
  parentId: string;
  status: '启用' | '停用';
};
const grants = defineModel<MenuGrants>({ required: true });
const props = defineProps<{ orgs: Org[] }>();
const collapsed = ref<string[]>([]);
const search = ref('');
const warehouses = ref<{ id: string; name: string }[]>([]);
const warehouseError = ref('');
onMounted(async () => {
  try {
    warehouses.value = (
      await request<{ data: { id: string; name: string }[] }>('/system/roles/warehouse-choices')
    ).data;
  } catch (e) {
    warehouseError.value = e instanceof Error ? e.message : '仓库加载失败';
  }
});
const visibleGroups = computed(() => {
  const keyword = search.value.trim().toLowerCase();
  return permissionGroups
    .map((group) => ({
      ...group,
      menus: group.menus.filter(
        (menu) =>
          !keyword || group.name.includes(keyword) || menu.name.toLowerCase().includes(keyword),
      ),
    }))
    .filter((group) => group.menus.length);
});
function isCollapsed(name: string) {
  return !search.value.trim() && collapsed.value.includes(name);
}
function allGroups(collapse: boolean) {
  search.value = '';
  collapsed.value = collapse ? permissionGroups.map((group) => group.name) : [];
}
type DepartmentNode = { id: string; label: string; disabled: boolean; children: DepartmentNode[] };
const departmentTree = computed(() => {
  const active = props.orgs.filter((org) => org.status === '启用');
  const ids = new Set(active.map((org) => org.id));
  function branch(parent: string, seen = new Set<string>()): DepartmentNode[] {
    return active
      .filter((org) => org.parentId === parent || (!parent && !ids.has(org.parentId)))
      .filter((org) => !seen.has(org.id))
      .map((org) => ({
        id: org.id,
        label: org.name,
        disabled: org.type !== '部门',
        children: branch(org.id, new Set([...seen, org.id])),
      }));
  }
  return branch('');
});
function toggleGroup(name: string) {
  collapsed.value = collapsed.value.includes(name)
    ? collapsed.value.filter((x) => x !== name)
    : [...collapsed.value, name];
}
function grant(key: string): MenuGrant {
  return grants.value[key] || { actions: [], dataScopes: [], orgIds: [] };
}
function setGrant(key: string, next: MenuGrant) {
  const copy = { ...grants.value };
  if (next.actions.length) copy[key] = next;
  else delete copy[key];
  grants.value = copy;
}
function toggleAction(key: string, action: string) {
  const current = grant(key);
  if (action === '查看' && current.actions.includes(action)) {
    setGrant(key, { actions: [], dataScopes: [], orgIds: [] });
    return;
  }
  const actions = current.actions.includes(action)
    ? current.actions.filter((item) => item !== action)
    : [...current.actions, action];
  if (actions.length && !actions.includes('查看')) actions.unshift('查看');
  setGrant(key, {
    ...current,
    actions,
    dataScopes: actions.length
      ? current.dataScopes.length
        ? current.dataScopes
        : [defaultScope(key)]
      : [],
    orgIds: actions.length ? current.orgIds : [],
  });
}
function toggleAllActions(key: string, actions: readonly string[]) {
  const current = grant(key);
  setGrant(
    key,
    current.actions.length === actions.length
      ? { actions: [], dataScopes: [], orgIds: [] }
      : {
          ...current,
          actions: [...actions],
          dataScopes: current.dataScopes.length ? current.dataScopes : [defaultScope(key)],
        },
  );
}
function setScopes(key: string, values: DataScope[]) {
  const current = grant(key);
  if (!current.actions.length) return;
  const dataScopes =
    values.includes('all') && !current.dataScopes.includes('all')
      ? (['all'] as DataScope[])
      : values.filter((scope) => scope !== 'all');
  setGrant(key, {
    ...current,
    dataScopes,
    orgIds:
      dataScopes.includes('selected') || dataScopes.includes('warehouse') ? current.orgIds : [],
  });
}
function setDepartments(key: string, ids: string[]) {
  setGrant(key, { ...grant(key), orgIds: ids });
}
</script>

<template>
  <div class="role-permission-editor">
    <div class="permission-matrix-head">
      <strong>菜单权限</strong>
      <div class="permission-tools">
        <ElInput v-model="search" placeholder="搜索菜单" clearable aria-label="搜索菜单" />
        <ElButton @click="allGroups(false)">全部展开</ElButton>
        <ElButton @click="allGroups(true)">全部收起</ElButton>
      </div>
    </div>
    <div class="permission-workspace">
      <div class="permission-matrix-scroll">
        <table class="business-table permission-matrix">
          <thead>
            <tr>
              <th>菜单</th>
              <th>功能权限</th>
              <th>数据权限</th>
            </tr>
          </thead>
          <tbody v-for="group in visibleGroups" :key="group.name">
            <tr class="permission-group-row">
              <td colspan="3">
                <button
                  class="permission-group-toggle"
                  :aria-expanded="!isCollapsed(group.name)"
                  @click="toggleGroup(group.name)"
                >
                  <span>{{ isCollapsed(group.name) ? '›' : '⌄' }}</span
                  >{{ group.name }}<small>{{ group.menus.length }} 个菜单</small>
                </button>
              </td>
            </tr>
            <tr
              v-for="menu in isCollapsed(group.name) ? [] : group.menus"
              :key="menu.key"
              :class="{ granted: grant(menu.key).actions.length }"
            >
              <td class="permission-menu-name">
                <strong>{{ menu.name }}</strong>
              </td>
              <td>
                <div class="permission-actions">
                  <ElCheckbox
                    class="permission-all"
                    :model-value="grant(menu.key).actions.length === menu.actions.length"
                    :indeterminate="
                      grant(menu.key).actions.length > 0 &&
                      grant(menu.key).actions.length < menu.actions.length
                    "
                    @change="toggleAllActions(menu.key, menu.actions)"
                    >全选功能</ElCheckbox
                  >
                  <ElCheckbox
                    v-for="action in menu.actions"
                    :key="action"
                    :model-value="grant(menu.key).actions.includes(action)"
                    @change="toggleAction(menu.key, action)"
                    >{{ action }}</ElCheckbox
                  >
                </div>
              </td>
              <td class="permission-scopes">
                <span
                  v-if="menu.scopes.length === 1"
                  class="shared-scope"
                  :class="{ inactive: !grant(menu.key).actions.length }"
                >
                  {{
                    ['base', 'maintenanceType', 'inspectionType', 'location'].includes(menu.key)
                      ? '集团统一维护'
                      : scopeLabel(menu.scopes[0]!, menu.key)
                  }}
                </span>
                <ElSelect
                  v-else
                  :model-value="grant(menu.key).dataScopes"
                  multiple
                  :disabled="!grant(menu.key).actions.length"
                  placeholder="请选择数据范围"
                  :aria-label="`${menu.name}数据范围`"
                  @update:model-value="setScopes(menu.key, $event)"
                >
                  <ElOption
                    v-for="scope in menu.scopes"
                    :key="scope"
                    :value="scope"
                    :label="scopeLabel(scope, menu.key)"
                  />
                </ElSelect>
                <ElSelect
                  v-if="grant(menu.key).dataScopes.includes('warehouse')"
                  :model-value="grant(menu.key).orgIds"
                  multiple
                  filterable
                  placeholder="选择指定仓库"
                  @update:model-value="setDepartments(menu.key, $event)"
                  ><ElOption v-for="w in warehouses" :key="w.id" :value="w.id" :label="w.name"
                /></ElSelect>
                <span
                  v-if="grant(menu.key).dataScopes.includes('warehouse') && warehouseError"
                  role="alert"
                  >{{ warehouseError }}</span
                >
                <ElTreeSelect
                  v-if="grant(menu.key).dataScopes.includes('selected')"
                  class="permission-departments"
                  :data="departmentTree"
                  node-key="id"
                  :model-value="grant(menu.key).orgIds"
                  multiple
                  show-checkbox
                  check-strictly
                  default-expand-all
                  filterable
                  :render-after-expand="false"
                  placeholder="选择指定部门"
                  :aria-label="`${menu.name}指定部门`"
                  @update:model-value="setDepartments(menu.key, $event)"
                />
              </td>
            </tr>
          </tbody>
        </table>
        <div v-if="!visibleGroups.length" class="permission-empty">没有匹配的菜单</div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.permission-workspace {
  min-width: 0;
}
.permission-group-toggle {
  display: flex;
  align-items: center;
  gap: 10px;
  width: 100%;
  border: 0;
  background: transparent;
  padding: 0;
  text-align: left;
  font: inherit;
  font-weight: 600;
  color: var(--text);
  cursor: pointer;
}
.permission-group-toggle span {
  width: 16px;
  color: var(--brand);
}
.permission-group-toggle small {
  margin-left: auto;
  font-size: 13px;
  font-weight: 400;
  color: var(--text-secondary);
}
.role-permission-editor {
  grid-column: 1/-1;
  min-width: 0;
  border: 1px solid #e3e8f0;
  border-radius: 6px;
  background: #fff;
}
.permission-matrix-head {
  min-height: 42px;
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 0 14px;
  border-bottom: 1px solid #e7ebf1;
}
.permission-matrix-head strong {
  color: #2e3b50;
  font-size: 14px;
}
.permission-matrix-scroll {
  min-width: 0;
}
.permission-matrix {
  width: 100%;
  min-width: 850px;
  border-collapse: collapse;
  table-layout: fixed;
  text-align: left;
}
.permission-matrix thead th {
  position: sticky;
  top: 0;
  z-index: 2;
  height: 38px;
  padding: 0 14px;
  background: #f5f7fa;
  border-bottom: 1px solid #e2e7ef;
  color: var(--text);
  font-size: 13px;
}
.permission-matrix th:first-child {
  width: 130px;
}
.permission-matrix th:nth-child(2) {
  width: 40%;
}
.permission-matrix td {
  height: auto;
  padding: 12px 14px;
  border-bottom: 1px solid #edf0f5;
  vertical-align: top;
  color: var(--text);
  font-size: 13px;
  white-space: normal;
}
.permission-matrix .permission-group-row td {
  height: 32px;
  padding: 7px 14px;
  background: var(--brand-soft, #edf3ff);
  color: #6c7a8e;
  font-size: 13px;
  font-weight: 600;
}
.permission-menu-name strong {
  font-weight: 600;
  white-space: nowrap;
}
.permission-matrix tr.granted .permission-menu-name strong {
  color: var(--brand);
}
.permission-all {
  padding-right: 12px;
  border-right: 1px solid #dfe5ed;
  color: #3f4d62;
  font-weight: 600;
}

.permission-matrix-head {
  justify-content: space-between;
  padding: 10px 14px;
  gap: 16px;
}
.permission-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.permission-tools .el-input {
  width: 220px;
}
.permission-tools :deep(.el-button + .el-button) {
  margin-left: 0;
}
.permission-matrix th:nth-child(2) {
  width: 55%;
}
.permission-matrix td {
  vertical-align: middle;
  padding: 14px;
}
.permission-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px 16px;
}
.permission-actions :deep(.el-checkbox) {
  margin-right: 0;
  height: 26px;
}
.permission-actions :deep(.el-checkbox__label) {
  font-size: 13px;
  color: var(--text);
  padding-left: 6px;
}
.permission-scopes .el-select {
  width: 100%;
}
.permission-departments {
  width: 100%;
}
.shared-scope {
  display: inline-block;
  color: var(--text);
}
.shared-scope.inactive {
  opacity: 0.55;
}
.permission-empty {
  padding: 36px;
  text-align: center;
}
.permission-scopes :deep(.el-select__input),
.permission-tools :deep(.el-input__inner) {
  border: 0;
  padding: 0;
  background: transparent;
  box-shadow: none;
  font-size: 13px;
}
.permission-scopes :deep(.el-select__input:focus),
.permission-tools :deep(.el-input__inner:focus) {
  outline: none;
  box-shadow: none;
}
@media (max-width: 1050px) {
  .permission-matrix {
    min-width: 0;
  }
  .permission-matrix th:nth-child(2) {
    width: 48%;
  }
  .permission-matrix th:first-child {
    width: 125px;
  }
}
</style>
