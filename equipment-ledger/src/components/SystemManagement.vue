<script setup lang="ts">
import OrganizationIcon from './common/OrganizationIcon.vue';
import AppDialog from './common/AppDialog.vue';
import TreeSelect from './TreeSelect.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppBackButton from './common/AppBackButton.vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import AppPagination from './common/AppPagination.vue';
import { confirmAction } from '../services/dialog';
import { computed, reactive, ref, watch } from 'vue';
import * as api from '../api';
import AdvancedFilterDrawer from './common/AdvancedFilterDrawer.vue';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { matchesAdvanced } from '../query/advancedFilters';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import RolePermissionEditor from './RolePermissionEditor.vue';
import { normalizeMenuGrants, menuByKey, type MenuGrants } from '../rolePermissions';

type View = 'organization' | 'users' | 'roles';
type Org = {
  id: string;
  name: string;
  code: string;
  type: '集团' | '公司' | '部门';
  parentId: string;
  manager: string;
  status: '启用' | '停用';
};
type User = {
  id: string;
  account: string;
  name: string;
  employeeNo: string;
  phone: string;
  orgId: string;
  roleIds: string[];
  status: '启用' | '停用';
  password?: string;
};
type Role = {
  id: string;
  code: string;
  name: string;
  description: string;
  status: '启用' | '停用';
  menuPermissions: MenuGrants;
  dataScope?: string;
  orgIds?: string[];
  permissions?: string[];
};
const props = defineProps<{
  view: View;
  apiReady: boolean;
  grantedActions: Record<string, string[]>;
}>();
function can(action: string) {
  const menu = props.view === 'organization' ? 'org' : props.view === 'users' ? 'user' : 'role';
  return props.grantedActions[menu]?.includes(action) === true;
}
function normalizeRole(item: any): Role {
  return {
    ...item,
    menuPermissions: normalizeMenuGrants(
      item.menuPermissions,
      item.permissions || [],
      item.dataScope || '本部门',
      item.orgIds || [],
    ),
  };
}
const orgs = ref<Org[]>(load('eam-orgs-v1', []));
const roles = ref<Role[]>(load('eam-roles-v1', []));
const users = ref<User[]>(load('eam-users-v1', []));
const treeExpanded = ref<Record<string, boolean>>({ g1: true, c1: true, c2: true });
const search = ref(''),
  dialog = ref(false),
  saving = ref(false),
  editing = ref(false),
  notice = ref('');
const sysColumnFilterOpen = ref(false),
  sysColumnsOpen = ref(false),
  sysColumnFilters = reactive<Record<string, string>>({}),
  sysPage = ref(1),
  sysPageSize = ref(10);
const userColumns = [
  { key: 'account', label: '登录账号' },
  { key: 'name', label: '姓名' },
  { key: 'employeeNo', label: '工号' },
  { key: 'department', label: '所属部门' },
  { key: 'roles', label: '角色' },
  { key: 'phone', label: '手机号' },
  { key: 'status', label: '状态' },
];
const roleColumns = [
  { key: 'name', label: '角色名称' },
  { key: 'code', label: '角色编码' },
  { key: 'description', label: '描述' },
  { key: 'menuCount', label: '授权菜单' },
  { key: 'userCount', label: '关联用户' },
  { key: 'status', label: '状态' },
];
const advancedOpen = ref(false);
const advancedConditions = ref<FilterCondition[]>([]);
const advancedFields = computed<FilterField[]>(() =>
  userColumns.map((c) => ({
    ...c,
    type: c.key === 'status' ? 'choice' : 'text',
    ...(c.key === 'status' ? { options: ['启用', '停用'] } : {}),
  })),
);
const userVisible = ref(userColumns.map((x) => x.key)),
  roleVisible = ref(roleColumns.map((x) => x.key));
const activeColumns = computed(() => {
  const defs = props.view === 'users' ? userColumns : roleColumns,
    visible = props.view === 'users' ? userVisible.value : roleVisible.value;
  return defs.filter((col) => visible.includes(col.key));
});
const orgTreeVisible = ref(true);
const selectedUsers = ref<string[]>([]);
const userBatchOpen = ref(false),
  userBatchBusy = ref(false);
const userBatch = reactive({
  operation: 'department' as 'department' | 'password',
  orgId: '',
  password: '',
  confirmPassword: '',
});
const userBatchError = ref('');
const batchGuard = useUnsavedChanges(
  () => userBatch,
  () => userBatchOpen.value,
  () => userBatchBusy.value,
);
const allPageUsersSelected = computed(
  () =>
    pageUsers.value.length > 0 && pageUsers.value.every((u) => selectedUsers.value.includes(u.id)),
);
function togglePageUsers() {
  const ids = pageUsers.value.map((u) => u.id);
  selectedUsers.value = allPageUsersSelected.value
    ? selectedUsers.value.filter((id) => !ids.includes(id))
    : [...new Set([...selectedUsers.value, ...ids])];
}
function openUserBatch(operation: 'department' | 'password') {
  Object.assign(userBatch, { operation, orgId: '', password: '', confirmPassword: '' });
  userBatchError.value = '';
  batchGuard.markSaved();
  userBatchOpen.value = true;
}
async function closeUserBatch() {
  if (!(await batchGuard.permitLeave())) return;
  userBatchOpen.value = false;
  userBatch.password = userBatch.confirmPassword = '';
}
async function submitUserBatch() {
  if (userBatchBusy.value) return;
  if (!selectedUsers.value.length || selectedUsers.value.length > 200) {
    userBatchError.value = '请选择 1 至 200 个用户';
    return;
  }
  if (userBatch.operation === 'department' && !userBatch.orgId) {
    userBatchError.value = '请选择部门';
    return;
  }
  if (
    userBatch.operation === 'password' &&
    (userBatch.password.length < 12 ||
      userBatch.password.length > 72 ||
      userBatch.password !== userBatch.confirmPassword)
  ) {
    userBatchError.value = '密码需为 12 至 72 位，两次输入必须一致';
    return;
  }
  userBatchBusy.value = true;
  userBatchError.value = '';
  try {
    const result = await api.batchSystemUsers(
      [...selectedUsers.value],
      userBatch.operation,
      userBatch.operation === 'department'
        ? { orgId: userBatch.orgId }
        : { password: userBatch.password },
    );
    const updated = new Map(result.data.map((u) => [u.id, u]));
    users.value = users.value.map((u) => updated.get(u.id) || u);
    batchGuard.markSaved();
    userBatchOpen.value = false;
    userBatch.password = userBatch.confirmPassword = '';
    selectedUsers.value = [];
    flash('批量操作已完成');
  } catch (e) {
    userBatchError.value = e instanceof Error ? e.message : '操作失败';
  } finally {
    userBatchBusy.value = false;
  }
}
const selectedOrg = ref('');
const orgForm = reactive<Org>({
  id: '',
  name: '',
  code: '',
  type: '部门',
  parentId: '',
  manager: '',
  status: '启用',
});
const groupOrg = computed(() => orgs.value.find((x) => x.type === '集团'));
const orgTypes = computed(() =>
  groupOrg.value && groupOrg.value.id !== orgForm.id ? ['公司', '部门'] : ['集团', '公司', '部门'],
);
const orgParents = computed(() =>
  orgs.value.filter(
    (x) =>
      x.id !== orgForm.id &&
      !isWithin(x.id, orgForm.id) &&
      (orgForm.type === '公司'
        ? x.type === '集团'
        : orgForm.type === '部门' && ['公司', '部门'].includes(x.type)),
  ),
);
watch(
  () => orgForm.type,
  () => {
    if (orgForm.type === '集团') orgForm.parentId = '';
    else if (!orgParents.value.some((x) => x.id === orgForm.parentId))
      orgForm.parentId = orgForm.type === '公司' ? groupOrg.value?.id || '' : '';
  },
  { flush: 'sync' },
);
const userForm = reactive<User>({
  id: '',
  account: '',
  name: '',
  employeeNo: '',
  phone: '',
  orgId: '',
  roleIds: [],
  status: '启用',
});
const roleForm = reactive<Role>({
  id: '',
  code: '',
  name: '',
  description: '',
  status: '启用',
  menuPermissions: {},
});
const formGuard = useUnsavedChanges(
  () => (props.view === 'organization' ? orgForm : props.view === 'users' ? userForm : roleForm),
  () => dialog.value,
  () => saving.value,
);
onBeforeRouteLeave(() => !saving.value);
onBeforeRouteUpdate(() => !saving.value);
async function closeDialog() {
  if (saving.value || !(await formGuard.permitLeave())) return;
  dialog.value = false;
}
const visibleOrgRows = computed(() => {
  const out: Array<Org & { level: number }> = [];
  const walk = (parentId: string, level: number) =>
    orgs.value
      .filter((x) => x.parentId === parentId)
      .forEach((item) => {
        out.push({ ...item, level });
        if (treeExpanded.value[item.id] !== false) walk(item.id, level + 1);
      });
  walk('', 0);
  const term = search.value.trim().toLowerCase();
  return term
    ? out.filter((item) =>
        `${item.name} ${item.code} ${item.manager} ${item.type}`.toLowerCase().includes(term),
      )
    : out;
});
function userCell(item: User, key: string) {
  if (key === 'department') return orgName(item.orgId);
  if (key === 'roles') return roleNames(item.roleIds);
  return String(item[key as keyof User] ?? '');
}
function roleCell(item: Role, key: string) {
  if (key === 'userCount')
    return String(users.value.filter((user) => user.roleIds.includes(item.id)).length);
  if (key === 'menuCount')
    return `${Object.values(item.menuPermissions).filter((grant) => grant.actions.length).length} 个菜单`;
  return String(item[key as keyof Role] ?? '');
}
function matchesColumn(value: string, key: string) {
  const filter = sysColumnFilters[key]?.trim().toLocaleLowerCase();
  return !filter || value.toLocaleLowerCase().includes(filter);
}
const filteredUsers = computed(() =>
  users.value.filter(
    (u) =>
      (!search.value ||
        `${u.account} ${u.name} ${u.employeeNo} ${u.phone} ${orgName(u.orgId)} ${roleNames(u.roleIds)} ${u.status}`
          .toLowerCase()
          .includes(search.value.toLowerCase())) &&
      (!selectedOrg.value || isWithin(u.orgId, selectedOrg.value)) &&
      userColumns.every((col) => matchesColumn(userCell(u, col.key), col.key)) &&
      matchesAdvanced(advancedConditions.value, (key) => userCell(u, key)),
  ),
);
const filteredRoles = computed(() =>
  roles.value.filter(
    (r) =>
      (!search.value ||
        `${r.code} ${r.name} ${r.description} ${Object.keys(r.menuPermissions)
          .map((key) => menuByKey[key]?.name || key)
          .join(' ')} ${r.status}`
          .toLowerCase()
          .includes(search.value.toLowerCase())) &&
      roleColumns.every((col) => matchesColumn(roleCell(r, col.key), col.key)),
  ),
);
const pageUsers = computed(() =>
  filteredUsers.value.slice(
    (sysPage.value - 1) * sysPageSize.value,
    sysPage.value * sysPageSize.value,
  ),
);
const pageRoles = computed(() =>
  filteredRoles.value.slice(
    (sysPage.value - 1) * sysPageSize.value,
    sysPage.value * sysPageSize.value,
  ),
);
const sysPageCount = computed(() =>
  Math.max(
    1,
    Math.ceil(
      (props.view === 'users' ? filteredUsers.value.length : filteredRoles.value.length) /
        sysPageSize.value,
    ),
  ),
);
function toggleSysColumn(key: string) {
  const target = props.view === 'users' ? userVisible : roleVisible;
  target.value = target.value.includes(key)
    ? target.value.filter((x) => x !== key)
    : [
        ...(props.view === 'users' ? userColumns : roleColumns)
          .filter((col) => target.value.includes(col.key) || col.key === key)
          .map((col) => col.key),
      ];
}
function resetSysList() {
  selectedUsers.value = [];
  advancedConditions.value = [];
  search.value = '';
  selectedOrg.value = '';
  Object.keys(sysColumnFilters).forEach((key) => delete sysColumnFilters[key]);
  sysPage.value = 1;
}
function exportSysList() {
  const rows = props.view === 'users' ? filteredUsers.value : filteredRoles.value;
  const headers = activeColumns.value.map((col) => col.label);
  const values = rows.map((row) =>
    activeColumns.value
      .map((col) => {
        const value =
          props.view === 'users' ? userCell(row as User, col.key) : roleCell(row as Role, col.key);
        return `\"${value.replaceAll('\"', '\"\"')}\"`;
      })
      .join(','),
  );
  const blob = new Blob(['\ufeff' + [headers.join(','), ...values].join('\r\n')], {
    type: 'text/csv;charset=utf-8',
  });
  const link = document.createElement('a');
  link.href = URL.createObjectURL(blob);
  link.download = props.view === 'users' ? '用户列表.csv' : '角色列表.csv';
  link.click();
  URL.revokeObjectURL(link.href);
}
const orgOptions = computed(() =>
  orgs.value.filter((x) => x.type === '部门' && x.status === '启用'),
);
watch(orgs, (v) => localStorage.setItem('eam-orgs-v1', JSON.stringify(v)), { deep: true });
watch(users, (v) => localStorage.setItem('eam-users-v1', JSON.stringify(v)), { deep: true });
watch(roles, (v) => localStorage.setItem('eam-roles-v1', JSON.stringify(v)), { deep: true });
watch(
  () => props.view,
  () => {
    dialog.value = false;
    resetSysList();
    sysColumnFilterOpen.value = false;
    sysColumnsOpen.value = false;
  },
);
watch(
  [search, selectedOrg, sysColumnFilters],
  () => {
    sysPage.value = 1;
  },
  { deep: true },
);
watch(sysPageCount, (count) => {
  if (sysPage.value > count) sysPage.value = count;
});
watch(
  () => props.apiReady,
  (ready) => {
    if (ready) {
      orgs.value = [];
      users.value = [];
      roles.value = [];
      loadRemote();
    }
  },
  { immediate: true },
);
function load<T>(key: string, fallback: T): T {
  try {
    return JSON.parse(localStorage.getItem(key) || 'null') || fallback;
  } catch {
    return fallback;
  }
}
function isWithin(id: string, parentId: string): boolean {
  const visited = new Set<string>();
  let current = id;
  while (current && !visited.has(current)) {
    if (current === parentId) return true;
    visited.add(current);
    current = orgs.value.find((x) => x.id === current)?.parentId || '';
  }
  return false;
}
function orgName(id: string) {
  return orgs.value.find((x) => x.id === id)?.name || '';
}
function roleNames(ids: string[]) {
  return (
    ids
      .map((id) => roles.value.find((r) => r.id === id)?.name)
      .filter(Boolean)
      .join('、') || ''
  );
}
function openOrg(parent?: Org, item?: Org) {
  if (!parent && !item && groupOrg.value) parent = groupOrg.value;
  Object.assign(
    orgForm,
    item
      ? { ...item }
      : {
          id: '',
          name: '',
          code: '',
          type: parent?.type === '集团' ? '公司' : parent ? '部门' : '集团',
          parentId: parent?.id || '',
          manager: '',
          status: '启用',
        },
  );
  editing.value = !!item;
  formGuard.markSaved();
  dialog.value = true;
}
function openUser(item?: User) {
  Object.assign(
    userForm,
    item
      ? { ...item, password: '', roleIds: [...item.roleIds] }
      : {
          id: '',
          account: '',
          name: '',
          employeeNo: '',
          phone: '',
          password: '',
          orgId: selectedOrg.value || '',
          roleIds: [],
          status: '启用',
        },
  );
  editing.value = !!item;
  formGuard.markSaved();
  dialog.value = true;
}
const copyRoleId = ref('');
async function copyRolePermissions() {
  const source = roles.value.find((role) => role.id === copyRoleId.value);
  if (!source) return;
  if (
    Object.keys(roleForm.menuPermissions).length &&
    !(await confirmAction(
      `使用“${source.name}”的权限覆盖当前权限配置？角色名称、编码和描述保持不变。`,
    ))
  )
    return;
  roleForm.menuPermissions = structuredClone(normalizeRole(source).menuPermissions);
  flash(`已复制“${source.name}”的权限，请保存生效`);
}
function openRole(item?: Role) {
  copyRoleId.value = '';
  Object.assign(
    roleForm,
    item
      ? {
          ...normalizeRole(item),
          menuPermissions: structuredClone(normalizeRole(item).menuPermissions),
        }
      : { id: '', code: '', name: '', description: '', status: '启用', menuPermissions: {} },
  );
  editing.value = !!item;
  formGuard.markSaved();
  dialog.value = true;
}
async function save() {
  if (saving.value) return;
  saving.value = true;
  try {
    if (props.view === 'organization') {
      if (orgForm.type === '集团' && groupOrg.value && groupOrg.value.id !== orgForm.id) {
        flash('组织架构只能有一个集团');
        return;
      }
      if (orgForm.type !== '集团' && !orgParents.value.some((x) => x.id === orgForm.parentId)) {
        flash('请选择符合层级的上级组织');
        return;
      }
      if (!orgForm.code.trim() || !orgForm.name.trim()) {
        flash('请填写组织编码和名称');
        return;
      }
      const duplicate = orgs.value.some((x) => x.code === orgForm.code && x.id !== orgForm.id);
      if (duplicate) {
        flash('组织编码已存在');
        return;
      }
      if (props.apiReady) {
        const result = await api.saveOrganization(orgForm, !editing.value);
        const item = result.data;
        if (editing.value) orgs.value = orgs.value.map((x) => (x.id === item.id ? item : x));
        else orgs.value.push(item);
      } else if (editing.value)
        orgs.value = orgs.value.map((x) => (x.id === orgForm.id ? { ...orgForm } : x));
      else orgs.value.push({ ...orgForm, id: crypto.randomUUID() });
    } else if (props.view === 'users') {
      if (!userForm.account.trim() || !userForm.name.trim() || !userForm.orgId) {
        flash('请填写账号、姓名并选择所属部门');
        return;
      }
      if (users.value.some((x) => x.account === userForm.account && x.id !== userForm.id)) {
        flash('登录账号已存在');
        return;
      }
      if (props.apiReady) {
        const result = await api.saveSystemUser(userForm, !editing.value);
        const item = result.data;
        if (editing.value) users.value = users.value.map((x) => (x.id === item.id ? item : x));
        else users.value.push(item);
      } else if (editing.value)
        users.value = users.value.map((x) =>
          x.id === userForm.id ? { ...userForm, roleIds: [...userForm.roleIds] } : x,
        );
      else
        users.value.push({ ...userForm, id: crypto.randomUUID(), roleIds: [...userForm.roleIds] });
    } else {
      if (!roleForm.code.trim() || !roleForm.name.trim()) {
        flash('请填写角色编码和名称');
        return;
      }
      const grants = Object.entries(roleForm.menuPermissions).filter(
        ([, grant]) => grant.actions.length,
      );
      if (!grants.length) {
        flash('请至少配置一个菜单的权限');
        return;
      }
      for (const [key, grant] of grants) {
        if (!grant.dataScopes.length) {
          flash(`请设置${menuByKey[key]?.name || key}的数据权限`);
          return;
        }
        if (grant.dataScopes.includes('selected') && !grant.orgIds.length) {
          flash(`请为${menuByKey[key]?.name || key}选择部门`);
          return;
        }
      }
      if (props.apiReady) {
        const result = await api.saveSystemRole(roleForm, !editing.value);
        const item = normalizeRole(result.data);
        if (editing.value) roles.value = roles.value.map((x) => (x.id === item.id ? item : x));
        else roles.value.push(item);
      } else if (editing.value)
        roles.value = roles.value.map((x) =>
          x.id === roleForm.id ? JSON.parse(JSON.stringify(roleForm)) : x,
        );
      else roles.value.push({ ...JSON.parse(JSON.stringify(roleForm)), id: crypto.randomUUID() });
    }
    formGuard.markSaved();
    dialog.value = false;
    flash('已保存');
  } catch (error) {
    flash(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
  }
}
async function removeOrg(item: Org) {
  if (orgs.value.some((x) => x.parentId === item.id)) {
    flash('请先移除下级组织');
    return;
  }
  if (users.value.some((x) => x.orgId === item.id)) {
    flash('该部门仍有用户，不能删除');
    return;
  }
  if (await confirmAction(`确认删除“${item.name}”？`)) {
    try {
      if (props.apiReady) await api.deleteOrganization(item.id);
      orgs.value = orgs.value.filter((x) => x.id !== item.id);
    } catch (error) {
      flash(error instanceof Error ? error.message : '删除失败');
    }
  }
}
async function removeRole(item: Role) {
  if (users.value.some((u) => u.roleIds.includes(item.id))) {
    flash('该角色仍分配给用户，不能删除');
    return;
  }
  if (await confirmAction(`确认删除角色“${item.name}”？`)) {
    try {
      if (props.apiReady) await api.deleteSystemRole(item.id);
      roles.value = roles.value.filter((x) => x.id !== item.id);
    } catch (error) {
      flash(error instanceof Error ? error.message : '删除失败');
    }
  }
}
async function toggleUserStatus(item: User) {
  const updated = { ...item, status: item.status === '启用' ? '停用' : '启用' } as User;
  try {
    if (props.apiReady) {
      const result = await api.setSystemUserStatus(item.id, updated.status);
      Object.assign(item, result.data);
    } else item.status = updated.status;
  } catch (error) {
    flash(error instanceof Error ? error.message : '状态更新失败');
  }
}
function flash(msg: string) {
  notice.value = msg;
  window.setTimeout(() => (notice.value = ''), 2200);
}
async function loadRemote() {
  const [orgResult, userResult, roleResult] = await Promise.allSettled([
    api.listOrganizations(),
    api.listSystemUsers(),
    api.listSystemRoles(),
  ]);
  if (orgResult.status === 'fulfilled') orgs.value = orgResult.value.data;
  if (userResult.status === 'fulfilled') users.value = userResult.value.data;
  if (roleResult.status === 'fulfilled') roles.value = roleResult.value.data.map(normalizeRole);
}
</script>

<template>
  <section class="content sys-page">
    <div class="sys-toolbar">
      <div class="sys-actions">
        <button
          v-if="can('新增')"
          class="sys-primary"
          @click="
            view === 'organization'
              ? openOrg(orgs.find((x) => x.id === selectedOrg))
              : view === 'users'
                ? openUser()
                : openRole()
          "
        >
          ＋
          {{
            view === 'organization' ? '新增组织' : view === 'users' ? '新增用户' : '新增角色'
          }}</button
        ><ElDropdown
          v-if="view === 'users' && can('编辑')"
          trigger="click"
          @command="openUserBatch"
        >
          <button class="sys-secondary" :disabled="!selectedUsers.length">
            批量操作<span v-if="selectedUsers.length">（{{ selectedUsers.length }}）</span>⌄
          </button>
          <template #dropdown
            ><ElDropdownMenu
              ><ElDropdownItem command="department">更换部门</ElDropdownItem
              ><ElDropdownItem command="password">重置密码</ElDropdownItem></ElDropdownMenu
            ></template
          > </ElDropdown
        ><button
          v-if="view === 'organization' && !groupOrg"
          class="sys-secondary"
          @click="openOrg()"
        >
          新增集团</button
        ><button v-if="view === 'users'" class="sys-secondary" @click="flash('用户导入模板已准备')">
          导入
        </button>
      </div>
      <form v-if="view !== 'organization'" class="sys-filters" @submit.prevent="sysPage = 1">
        <ListToolbarIcons
          mode="filter"
          :can-export="can('导出')"
          :filter-open="sysColumnFilterOpen"
          :columns-open="sysColumnsOpen"
          @toggle-filter="sysColumnFilterOpen = !sysColumnFilterOpen"
          @toggle-columns="sysColumnsOpen = !sysColumnsOpen"
          @export="exportSysList"
        /><ListSearchInput
          v-model="search"
          :placeholder="
            view === 'users'
              ? '搜索账号、姓名、工号、手机号、部门、角色'
              : '搜索角色名称、编码、描述、菜单'
          "
        />
        <button
          v-if="view === 'users'"
          class="advanced-filter-trigger"
          :class="{ active: advancedConditions.length }"
          type="button"
          @click="advancedOpen = true"
        >
          筛选器<span v-if="advancedConditions.length"> ({{ advancedConditions.length }})</span>
        </button>
        <button class="sys-reset" type="button" @click="resetSysList">重置</button>
      </form>
      <form v-else class="sys-filters" @submit.prevent>
        <input v-model="search" placeholder="搜索组织名称、编码、负责人" />
      </form>
    </div>
    <AppliedFilterChips
      v-if="view === 'users'"
      :conditions="advancedConditions"
      :fields="advancedFields"
      @change="
        advancedConditions = $event;
        sysPage = 1;
      "
    />
    <AdvancedFilterDrawer
      v-if="view === 'users'"
      v-model="advancedOpen"
      :conditions="advancedConditions"
      :fields="advancedFields"
      @apply="
        advancedConditions = $event;
        sysPage = 1;
      "
    />
    <div v-if="view === 'organization'" class="org-layout">
      <article class="sys-card org-card">
        <header>
          <b>组织树</b><span>{{ orgs.length }} 个组织</span>
        </header>
        <div class="org-tree">
          <div
            v-for="item in visibleOrgRows"
            :key="item.id"
            class="org-row"
            :class="{ selected: selectedOrg === item.id }"
            :style="{ '--level': item.level }"
            @click="selectedOrg = item.id"
          >
            <button
              class="tree-toggle"
              @click.stop="treeExpanded[item.id] = treeExpanded[item.id] === false ? true : false"
            >
              {{
                !orgs.some((child) => child.parentId === item.id)
                  ? '·'
                  : treeExpanded[item.id] === false
                    ? '›'
                    : '⌄'
              }}</button
            ><span class="org-kind"><OrganizationIcon :type="item.type" /></span
            ><span class="org-name">{{ item.name }}</span
            ><span class="org-code">{{ item.code }}</span
            ><button class="org-inline-action" title="新增下级" @click.stop="openOrg(item)">
              ＋</button
            ><button class="org-inline-action" title="编辑" @click.stop="openOrg(undefined, item)">
              编辑</button
            ><button class="org-inline-action danger" title="删除" @click.stop="removeOrg(item)">
              删除
            </button>
          </div>
        </div>
      </article>
      <article class="sys-card org-detail">
        <header>
          <b>{{ orgs.find((x) => x.id === selectedOrg)?.name || '组织信息' }}</b
          ><button class="sys-secondary" @click="openOrg(orgs.find((x) => x.id === selectedOrg))">
            新增下级
          </button>
        </header>
        <dl v-if="orgs.find((x) => x.id === selectedOrg)">
          <dt>组织类型</dt>
          <dd>{{ orgs.find((x) => x.id === selectedOrg)?.type }}</dd>
          <dt>组织编码</dt>
          <dd>{{ orgs.find((x) => x.id === selectedOrg)?.code }}</dd>
          <dt>上级组织</dt>
          <dd>{{ orgName(orgs.find((x) => x.id === selectedOrg)?.parentId || '') }}</dd>
          <dt>负责人</dt>
          <dd>{{ orgs.find((x) => x.id === selectedOrg)?.manager || '' }}</dd>
          <dt>状态</dt>
          <dd>{{ orgs.find((x) => x.id === selectedOrg)?.status }}</dd>
          <dt>下级组织</dt>
          <dd>{{ orgs.filter((x) => x.parentId === selectedOrg).length }} 个</dd>
        </dl>
        <div v-else class="sys-empty">选择左侧组织查看详情</div>
      </article>
    </div>
    <div
      v-else-if="view === 'users'"
      class="users-layout"
      :class="{ 'tree-hidden': !orgTreeVisible }"
    >
      <article v-if="orgTreeVisible" class="sys-card user-org-filter">
        <header><b>组织部门</b><button @click="selectedOrg = ''">全部</button></header>
        <div class="org-tree">
          <div
            v-for="item in visibleOrgRows"
            :key="item.id"
            class="org-row"
            :class="{ selected: selectedOrg === item.id }"
            :style="{ '--level': item.level }"
            @click="selectedOrg = item.id"
          >
            <button
              class="tree-toggle"
              @click.stop="treeExpanded[item.id] = treeExpanded[item.id] === false ? true : false"
            >
              {{
                !orgs.some((child) => child.parentId === item.id)
                  ? '·'
                  : treeExpanded[item.id] === false
                    ? '›'
                    : '⌄'
              }}</button
            ><span class="org-kind"><OrganizationIcon :type="item.type" /></span
            ><span class="org-name">{{ item.name }}</span>
          </div>
        </div>
      </article>
      <button
        class="org-panel-toggle"
        :class="{ collapsed: !orgTreeVisible }"
        :aria-label="orgTreeVisible ? '收起组织部门' : '展开组织部门'"
        :title="orgTreeVisible ? '收起组织部门' : '展开组织部门'"
        @click="orgTreeVisible = !orgTreeVisible"
      >
        <svg viewBox="0 0 16 16" aria-hidden="true" :class="{ flipped: !orgTreeVisible }">
          <path d="m9.75 3.5-4.5 4.5 4.5 4.5" />
        </svg>
      </button>
      <article class="sys-card sys-table-card">
        <div class="sys-table-scroll">
          <table class="business-table">
            <thead>
              <tr>
                <th class="sys-select-col">
                  <input
                    type="checkbox"
                    :checked="allPageUsersSelected"
                    aria-label="选择本页用户"
                    @change="togglePageUsers"
                  />
                </th>
                <th v-for="col in activeColumns" :key="col.key">{{ col.label }}</th>
                <th class="action-col list-action-column">操作</th>
              </tr>
              <tr v-if="sysColumnFilterOpen" class="sys-filter-row">
                <th class="sys-select-col"></th>
                <th v-for="col in activeColumns" :key="col.key">
                  <select v-if="col.key === 'status'" v-model="sysColumnFilters[col.key]">
                    <option value="">全部状态</option>
                    <option>启用</option>
                    <option>停用</option></select
                  ><input
                    v-else
                    v-model="sysColumnFilters[col.key]"
                    :placeholder="`筛选${col.label}`"
                  />
                </th>
                <th class="list-action-column"></th>
              </tr>
            </thead>
            <tbody>
              <tr v-for="item in pageUsers" :key="item.id">
                <td class="sys-select-col">
                  <input
                    v-model="selectedUsers"
                    type="checkbox"
                    :value="item.id"
                    :aria-label="`选择${item.name}`"
                  />
                </td>
                <td v-for="col in activeColumns" :key="col.key">
                  <span
                    v-if="col.key === 'status'"
                    class="sys-status"
                    :class="item.status === '启用' ? 'on' : 'off'"
                    >{{ item.status }}</span
                  ><b v-else-if="col.key === 'name'">{{ userCell(item, col.key) }}</b
                  ><span
                    v-else
                    :class="
                      col.key === 'account' ? 'blue-link' : col.key === 'roles' ? 'role-cell' : ''
                    "
                    >{{ userCell(item, col.key) }}</span
                  >
                </td>
                <td class="action-col list-action-column">
                  <button v-if="can('编辑')" @click="openUser(item)">编辑</button
                  ><button v-if="can('停用')" @click="toggleUserStatus(item)">
                    {{ item.status === '启用' ? '停用' : '启用' }}
                  </button>
                </td>
              </tr>
              <tr v-if="!pageUsers.length">
                <td :colspan="activeColumns.length + 2" class="sys-empty">没有匹配的用户</td>
              </tr>
            </tbody>
          </table>
        </div>
        <footer class="sys-footer">
          <span>共 {{ filteredUsers.length }} 条用户</span>
          <AppPagination
            v-model:page="sysPage"
            v-model:page-size="sysPageSize"
            :total="filteredUsers.length"
          />
          <ListToolbarIcons
            mode="footer"
            :can-export="can('导出')"
            :filter-open="sysColumnFilterOpen"
            :columns-open="sysColumnsOpen"
            @toggle-filter="sysColumnFilterOpen = !sysColumnFilterOpen"
            @toggle-columns="sysColumnsOpen = !sysColumnsOpen"
            @export="exportSysList"
            ><template #columns
              ><label v-for="col in view === 'users' ? userColumns : roleColumns" :key="col.key"
                ><input
                  type="checkbox"
                  :checked="(view === 'users' ? userVisible : roleVisible).includes(col.key)"
                  @change="toggleSysColumn(col.key)"
                />{{ col.label }}</label
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </article>
    </div>
    <article v-else class="sys-card sys-table-card">
      <div class="sys-table-scroll">
        <table class="business-table">
          <thead>
            <tr>
              <th v-for="col in activeColumns" :key="col.key">{{ col.label }}</th>
              <th class="action-col list-action-column">操作</th>
            </tr>
            <tr v-if="sysColumnFilterOpen" class="sys-filter-row">
              <th v-for="col in activeColumns" :key="col.key">
                <select v-if="col.key === 'status'" v-model="sysColumnFilters[col.key]">
                  <option value="">全部状态</option>
                  <option>启用</option>
                  <option>停用</option></select
                ><input
                  v-else
                  v-model="sysColumnFilters[col.key]"
                  :placeholder="`筛选${col.label}`"
                />
              </th>
              <th class="list-action-column"></th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="item in pageRoles" :key="item.id">
              <td v-for="col in activeColumns" :key="col.key">
                <span
                  v-if="col.key === 'status'"
                  class="sys-status"
                  :class="item.status === '启用' ? 'on' : 'off'"
                  >{{ item.status }}</span
                ><b v-else-if="col.key === 'name'">{{ roleCell(item, col.key) }}</b
                ><span v-else>{{ roleCell(item, col.key) }}</span>
              </td>
              <td class="action-col list-action-column">
                <button v-if="can('编辑') && can('授权')" @click="openRole(item)">配置权限</button
                ><button v-if="can('删除')" class="danger" @click="removeRole(item)">删除</button>
              </td>
            </tr>
            <tr v-if="!pageRoles.length">
              <td :colspan="activeColumns.length + 1" class="sys-empty">没有匹配的角色</td>
            </tr>
          </tbody>
        </table>
      </div>
      <footer class="sys-footer">
        <span>共 {{ filteredRoles.length }} 个角色</span>
        <AppPagination
          v-model:page="sysPage"
          v-model:page-size="sysPageSize"
          :total="filteredRoles.length"
        />
        <ListToolbarIcons
          mode="footer"
          :can-export="can('导出')"
          :filter-open="sysColumnFilterOpen"
          :columns-open="sysColumnsOpen"
          @toggle-filter="sysColumnFilterOpen = !sysColumnFilterOpen"
          @toggle-columns="sysColumnsOpen = !sysColumnsOpen"
          @export="exportSysList"
          ><template #columns
            ><label v-for="col in roleColumns" :key="col.key"
              ><input
                type="checkbox"
                :checked="roleVisible.includes(col.key)"
                @change="toggleSysColumn(col.key)"
              />{{ col.label }}</label
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </article>
    <Transition name="fade"
      ><div v-if="dialog" class="sys-mask" :class="{ 'role-page': view === 'roles' }">
        <section class="sys-dialog" :class="{ 'role-dialog': view === 'roles' }">
          <header>
            <AppBackButton
              v-if="view === 'roles'"
              label="返回角色权限"
              :disabled="saving"
              @click="closeDialog"
            />
            <b
              >{{ editing ? '编辑' : '新增'
              }}{{ view === 'organization' ? '组织' : view === 'users' ? '用户' : '角色' }}</b
            ><button
              v-if="view !== 'roles'"
              aria-label="关闭"
              :disabled="saving"
              @click="closeDialog"
            >
              ×
            </button>
          </header>
          <div class="sys-dialog-body" v-if="view === 'organization'" :inert="saving || undefined">
            <label
              ><span>组织类型</span
              ><select v-model="orgForm.type">
                <option v-for="type in orgTypes" :key="type">{{ type }}</option>
              </select></label
            ><label
              ><span>组织名称<i>*</i></span
              ><input v-model="orgForm.name" placeholder="请输入组织名称" /></label
            ><label
              ><span>组织编码<i>*</i></span
              ><input v-model="orgForm.code" placeholder="请输入唯一编码" /></label
            ><label
              ><span>上级组织</span
              ><select v-model="orgForm.parentId" :disabled="orgForm.type === '集团'">
                <option value="">
                  {{ orgForm.type === '集团' ? '无（顶级组织）' : '请选择上级组织' }}
                </option>
                <option v-for="item in orgParents" :key="item.id" :value="item.id">
                  {{ item.name }} · {{ item.type }}
                </option>
              </select></label
            ><label
              ><span>负责人</span
              ><input v-model="orgForm.manager" placeholder="请输入负责人" /></label
            ><label
              ><span>状态</span
              ><select v-model="orgForm.status">
                <option>启用</option>
                <option>停用</option>
              </select></label
            >
          </div>
          <div class="sys-dialog-body" v-else-if="view === 'users'" :inert="saving || undefined">
            <label
              ><span>登录账号<i>*</i></span
              ><input v-model="userForm.account" placeholder="用于登录，保存后不可重复" /></label
            ><label
              ><span>姓名<i>*</i></span
              ><input v-model="userForm.name" placeholder="请输入员工姓名" /></label
            ><label
              ><span>工号</span
              ><input v-model="userForm.employeeNo" placeholder="请输入工号" /></label
            ><label
              ><span>手机号</span
              ><input v-model="userForm.phone" placeholder="请输入手机号" /></label
            ><label
              ><span>{{ editing ? '重置密码' : '初始密码' }}<i v-if="!editing">*</i></span
              ><input
                v-model="userForm.password"
                type="password"
                autocomplete="new-password"
                :placeholder="
                  editing ? '留空则不修改，至少 12 位' : '请输入至少 12 位密码'
                " /></label
            ><label
              ><span>所属部门<i>*</i></span
              ><select v-model="userForm.orgId">
                <option value="">请选择部门</option>
                <option v-for="item in orgOptions" :key="item.id" :value="item.id">
                  {{ orgName(item.parentId) }} / {{ item.name }}
                </option>
              </select></label
            ><label
              ><span>状态</span
              ><select v-model="userForm.status">
                <option>启用</option>
                <option>停用</option>
              </select></label
            >
            <fieldset>
              <legend>角色（可多选）</legend>
              <label
                v-for="role in roles.filter((x) => x.status === '启用')"
                :key="role.id"
                class="check-option"
                ><input v-model="userForm.roleIds" type="checkbox" :value="role.id" />{{
                  role.name
                }}</label
              >
            </fieldset>
          </div>
          <div class="sys-dialog-body role-editor" v-else :inert="saving || undefined">
            <div class="role-basics">
              <label
                ><span>角色名称<i>*</i></span
                ><input v-model="roleForm.name" placeholder="例如：设备管理员" /></label
              ><label
                ><span>角色编码<i>*</i></span
                ><input v-model="roleForm.code" placeholder="唯一编码" /></label
              ><label
                ><span>角色描述</span
                ><input v-model="roleForm.description" placeholder="说明角色职责" /></label
              ><label
                ><span>状态</span
                ><select v-model="roleForm.status">
                  <option>启用</option>
                  <option>停用</option>
                </select></label
              >
            </div>
            <div class="role-copy-tools">
              <span>复制角色权限</span>
              <select v-model="copyRoleId" aria-label="选择来源角色">
                <option value="">选择已有角色</option>
                <option
                  v-for="role in roles.filter((item) => item.id !== roleForm.id)"
                  :key="role.id"
                  :value="role.id"
                >
                  {{ role.name }}
                </option>
              </select>
              <button
                class="sys-secondary"
                :disabled="!copyRoleId || saving"
                @click="copyRolePermissions"
              >
                复制权限
              </button>
            </div>
            <RolePermissionEditor v-model="roleForm.menuPermissions" :orgs="orgs" />
          </div>
          <AppFormFooter v-if="view === 'roles'">
            <button class="sys-secondary" :disabled="saving" @click="closeDialog">取消</button>
            <button class="sys-primary" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </AppFormFooter>
          <footer v-else>
            <button class="sys-secondary" :disabled="saving" @click="closeDialog">取消</button
            ><button class="sys-primary" :disabled="saving" @click="save">
              {{ saving ? '保存中…' : '保存' }}
            </button>
          </footer>
        </section>
      </div></Transition
    >
    <Transition name="toast"
      ><div v-if="notice" class="sys-notice">{{ notice }}</div></Transition
    >
    <AppDialog
      v-model="userBatchOpen"
      :title="userBatch.operation === 'department' ? '更换部门' : '重置密码'"
      :busy="userBatchBusy"
      :before-close="batchGuard.permitLeave"
    >
      <div class="sys-user-batch" :inert="userBatchBusy || undefined">
        <p>已选择 {{ selectedUsers.length }} 个用户</p>
        <TreeSelect
          v-if="userBatch.operation === 'department'"
          v-model="userBatch.orgId"
          :nodes="orgs.map((o) => ({ ...o, value: o.id, disabled: o.type !== '部门' }))"
          path-value
          placeholder="请选择部门"
        />
        <template v-else
          ><p>重置后所选用户的登录会话将失效，请通过合适的渠道告知新密码。</p>
          <label
            >新密码<input
              v-model="userBatch.password"
              type="password"
              autocomplete="new-password"
              placeholder="12 至 72 位" /></label
          ><label
            >确认密码<input
              v-model="userBatch.confirmPassword"
              type="password"
              autocomplete="new-password"
              placeholder="再次输入新密码" /></label
        ></template>
        <p v-if="userBatchError" class="sys-batch-error" role="alert">{{ userBatchError }}</p>
      </div>
      <template #footer
        ><button class="sys-secondary" :disabled="userBatchBusy" @click="closeUserBatch">
          取消</button
        ><button class="sys-primary" :disabled="userBatchBusy" @click="submitUserBatch">
          {{ userBatchBusy ? '提交中…' : '提交' }}
        </button></template
      >
    </AppDialog>
  </section>
</template>

<style scoped>
.sys-page {
  position: relative;
}
.sys-mask.role-page {
  position: absolute;
  inset: 0;
  padding: 0;
  background: #fff;
  display: flex;
  z-index: 20;
}
.role-page .sys-dialog.role-dialog {
  width: 100%;
  height: 100%;
  max-height: none;
  border-radius: 0;
  box-shadow: none;
}
.role-page .sys-dialog > header {
  justify-content: flex-start;
  gap: 12px;
  flex: none;
}
.role-page .sys-dialog > header :deep(.app-back-button) {
  width: 32px;
  height: 32px;
}
.role-page .sys-dialog-body.role-editor {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.role-page .sys-dialog > footer:not(.page-form-footer) {
  flex: none;
}
.sys-select-col {
  width: 42px;
  min-width: 42px;
  text-align: center;
}
.sys-user-batch {
  display: grid;
  gap: 14px;
  font-size: 13px;
  color: var(--text-secondary);
}
.sys-user-batch p {
  margin: 0;
  line-height: 1.6;
}
.sys-user-batch label {
  display: grid;
  gap: 8px;
}
.sys-user-batch input {
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--el-border-color);
  border-radius: 4px;
  font: inherit;
}
.sys-batch-error {
  color: var(--danger);
}
.sys-reset {
  border: 0;
  background: transparent;
  color: #687487;
  font: inherit;
  cursor: pointer;
}
.sys-filters .sys-primary {
  height: 34px;
  padding: 0 12px;
}
.sys-secondary.active {
  border-color: var(--brand);
  background: var(--brand-soft);
  color: var(--brand);
}
.sys-filter-row input,
.sys-filter-row select {
  font: inherit;
}
.sys-pagination {
  height: 100%;
  position: relative;
}
.sys-pagination > select {
  height: 28px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: white;
  color: #59677a;
  padding: 0 5px;
  font: inherit;
  font-size: 12px;
}
.sys-pagination > button {
  height: 28px;
  min-width: 28px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: white;
  color: #59677a;
  cursor: pointer;
}
.sys-pagination > button:disabled {
  color: #c3cad4;
}
.sys-pagination > b {
  font-size: 12px;
  font-weight: 500;
  white-space: nowrap;
}
.sys-columns {
  position: relative;
}
.sys-columns > button {
  height: 30px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: #fff;
  color: #526074;
  padding: 0 8px;
  font: inherit;
  font-size: 12px;
  cursor: pointer;
}
.sys-columns-menu {
  position: absolute;
  right: 0;
  bottom: 36px;
  z-index: 12;
  width: 150px;
  padding: 8px;
  background: #fff;
  border: 1px solid #e0e5ec;
  border-radius: 5px;
  box-shadow: 0 8px 24px #15244022;
  display: grid;
  gap: 4px;
}
.sys-columns-menu label {
  display: flex;
  align-items: center;
  gap: 6px;
  height: 25px;
  color: #4d596a;
  font-size: 12px;
}
.sys-columns-menu input {
  accent-color: var(--brand);
}
@media (max-width: 900px) {
  .users-layout {
    gap: 8px;
  }
  .org-layout {
    grid-template-columns: 1fr;
  }
  .org-detail {
    display: none;
  }
  .sys-toolbar {
    align-items: flex-start;
    flex-wrap: wrap;
  }
  .sys-filters {
    margin-left: auto;
  }
}
.sys-page {
  height: calc(100vh - 56px);
  min-height: 540px;
  max-width: none;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  padding: 16px 18px 12px;
}
.sys-heading {
  height: 34px;
  flex: none;
  display: flex;
  align-items: center;
  gap: 14px;
  margin-bottom: 10px;
}
.sys-heading h1 {
  margin: 0;
  font-size: 19px;
  line-height: 26px;
  font-weight: 650;
  color: #273347;
}
.sys-heading > span {
  font-size: 12px;
  color: #8b96a6;
}
.sys-toolbar {
  min-height: 36px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 10px;
}
.sys-actions,
.sys-filters {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
}
.sys-filters {
  margin-left: auto;
}
.sys-primary,
.sys-secondary {
  height: 34px;
  flex: none;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 12px;
  border-radius: 5px;
  font: inherit;
  font-size: 13px;
  white-space: nowrap;
  cursor: pointer;
}
.sys-primary {
  border: 1px solid var(--brand);
  background: var(--brand);
  color: #fff;
}
.sys-primary:hover {
  filter: brightness(0.95);
}
.sys-secondary {
  border: 1px solid #dce2ea;
  background: #fff;
  color: #4e5b70;
}
.sys-secondary:hover {
  border-color: var(--brand);
  color: var(--brand);
}
.sys-filters > input {
  width: 280px;
  height: 34px;
  min-width: 150px;
  padding: 0 10px;
  border: 1px solid #dce2ea;
  border-radius: 5px;
  background: #fff;
  color: #354258;
  font-size: 13px;
}
.sys-filters > input::placeholder {
  color: #9ba6b5;
}
.sys-reset {
  height: 34px;
  padding: 0 6px;
  font-size: 13px;
}
.sys-card {
  min-width: 0;
  min-height: 0;
  background: #fff;
  border: 1px solid #e2e7ee;
  border-radius: 6px;
  box-shadow: 0 1px 2px #1d2d4608;
}
.org-layout {
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: minmax(430px, 1.25fr) minmax(340px, 0.75fr);
  gap: 12px;
}
.org-card,
.org-detail,
.user-org-filter {
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sys-card > header {
  height: 46px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  padding: 0 16px;
  border-bottom: 1px solid #e8ecf1;
}
.sys-card > header b {
  font-size: 14px;
  font-weight: 600;
  color: #344156;
}
.sys-card > header span {
  font-size: 12px;
  color: #8a96a6;
}
.sys-card > header button:not(.sys-secondary) {
  border: 0;
  background: transparent;
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.org-tree {
  flex: 1;
  min-height: 0;
  overflow: auto;
  padding: 8px;
}
.org-row {
  min-height: 39px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 9px 3px calc(9px + var(--level) * 22px);
  border-radius: 4px;
  color: #4e5c70;
  font-size: 13px;
  cursor: pointer;
}
.org-row:hover {
  background: #f4f7fb;
}
.org-row.selected {
  background: var(--brand-soft);
  color: var(--brand);
}
.tree-toggle {
  width: 17px;
  height: 22px;
  flex: none;
  padding: 0;
  border: 0;
  background: transparent;
  color: #8a96a7;
  font-size: 17px;
  cursor: pointer;
}
.org-kind {
  width: 18px;
  flex: none;
  text-align: center;
  color: var(--brand);
  font-size: 15px;
}
.org-name {
  min-width: 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  font-weight: 500;
}
.org-code {
  margin-left: auto;
  color: #93a0af;
  font-size: 12px;
  white-space: nowrap;
}
.org-inline-action {
  flex: none;
  border: 0;
  background: transparent;
  color: var(--brand);
  font-size: 12px;
  cursor: pointer;
  white-space: nowrap;
}
.org-inline-action.danger {
  color: #c9535e;
}
.org-detail dl {
  display: grid;
  grid-template-columns: 116px minmax(0, 1fr);
  gap: 0;
  margin: 0;
  padding: 8px 18px;
}
.org-detail dt,
.org-detail dd {
  min-height: 44px;
  display: flex;
  align-items: center;
  margin: 0;
  border-bottom: 1px solid #eff1f4;
  font-size: 13px;
}
.org-detail dt {
  color: #8290a2;
}
.org-detail dd {
  color: #354258;
}
.org-detail .sys-empty {
  padding: 42px 16px;
  text-align: center;
}
.users-layout {
  --org-panel-width: 260px;
  position: relative;
  flex: 1;
  min-height: 0;
  display: grid;
  grid-template-columns: var(--org-panel-width) minmax(0, 1fr);
  gap: 12px;
}
.users-layout.tree-hidden {
  grid-template-columns: minmax(0, 1fr);
}
.user-org-filter .org-row {
  padding-right: 6px;
}
.user-org-filter .org-name {
  font-size: 13px;
}
.org-panel-toggle {
  position: absolute;
  top: 50%;
  left: calc(var(--org-panel-width) + 5px);
  z-index: 3;
  width: 20px;
  height: 44px;
  display: grid;
  place-items: center;
  padding: 0;
  border: 1px solid #dbe2ec;
  border-radius: 0 5px 5px 0;
  background: #fff;
  color: #718099;
  transform: translate(-50%, -50%);
  cursor: pointer;
  box-shadow: 1px 1px 4px #20305016;
}
.org-panel-toggle:hover {
  color: var(--brand);
  border-color: var(--brand);
}
.org-panel-toggle.collapsed {
  left: 0;
  transform: translate(0, -50%);
}
.org-panel-toggle svg {
  width: 13px;
  height: 13px;
  fill: none;
  stroke: currentColor;
  stroke-width: 1.8;
  stroke-linecap: round;
  stroke-linejoin: round;
}
.org-panel-toggle svg.flipped {
  transform: rotate(180deg);
}
.sys-table-card {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}
.sys-table-scroll {
  flex: 1;
  min-height: 0;
  overflow: auto;
}
.sys-table-scroll table {
  width: 100%;
  min-width: 850px;
  border-spacing: 0;
  table-layout: auto;
}
.sys-table-scroll thead th {
  position: sticky;
  top: 0;
  z-index: 1;
  height: 42px;
  padding: 0 13px;
  background: #f5f7fa;
  border-bottom: 1px solid #e5eaf0;
  color: #667387;
  font-size: 13px;
  font-weight: 600;
  text-align: left;
}
.sys-table-scroll tbody td {
  height: 48px;
  padding: 0 13px;
  border-bottom: 1px solid #edf0f4;
  color: #4d5a6e;
  font-size: 13px;
}
.sys-table-scroll tbody tr:hover td {
  background: #f8faff;
}
.sys-table-scroll tbody td b {
  color: #344156;
  font-size: 13px;
  font-weight: 600;
}
.sys-table-scroll .action-col {
  width: 118px;
  white-space: nowrap;
}
.action-col button {
  padding: 0;
  border: 0;
  background: transparent;
  color: var(--brand);
  font-size: 13px;
  cursor: pointer;
}
.action-col button + button {
  margin-left: 13px;
  color: #667387;
}
.action-col button.danger {
  color: #c9535e;
}
.blue-link {
  color: var(--brand);
  font-weight: 600;
}
.role-cell {
  max-width: 240px;
  display: inline-block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  vertical-align: middle;
}
.sys-status {
  display: inline-flex;
  align-items: center;
  padding: 4px 9px;
  border-radius: 4px;
  font-size: 12px;
  font-weight: 600;
}
.sys-status.on {
  background: #e9f6ef;
  color: #16855a;
}
.sys-status.off {
  background: #f1f2f4;
  color: #748092;
}
.sys-empty {
  padding: 28px !important;
  color: #96a1af !important;
  text-align: center;
}
.sys-footer {
  height: 48px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 0 14px;
  border-top: 1px solid #e7ebf0;
  color: #788598;
  font-size: 13px;
}
.sys-pagination {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 7px;
}
.sys-filter-row th {
  top: 42px !important;
  z-index: 1;
  height: 38px !important;
  padding: 4px 6px !important;
  background: #fff !important;
}
.sys-filter-row input,
.sys-filter-row select {
  width: 100%;
  height: 28px;
  min-width: 0;
  padding: 0 6px;
  border: 1px solid #dfe3e9;
  border-radius: 4px;
  background: #fff;
  color: #536176;
  font-size: 12px;
}
.sys-mask {
  position: fixed;
  inset: 56px 0 0;
  z-index: 30;
  display: grid;
  place-items: center;
  padding: 24px;
  background: #17243b66;
}
.sys-dialog {
  width: min(650px, calc(100vw - 48px));
  max-height: min(790px, calc(100vh - 90px));
  display: flex;
  flex-direction: column;
  overflow: hidden;
  border-radius: 7px;
  background: #fff;
  box-shadow: 0 18px 50px #0f1c3240;
}
.sys-dialog > header,
.sys-dialog > footer:not(.page-form-footer) {
  height: 52px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
}
.sys-dialog > header {
  border-bottom: 1px solid #e8ecf1;
}
.sys-dialog > header b {
  font-size: 16px;
  color: #2e3b50;
}
.sys-dialog > header button {
  border: 0;
  background: transparent;
  color: #8a96a7;
  font-size: 22px;
  cursor: pointer;
}
.sys-dialog > footer:not(.page-form-footer) {
  justify-content: center;
  gap: 16px;
  border-top: 1px solid #e8ecf1;
}
.sys-dialog > footer:not(.page-form-footer) > button {
  min-width: 100px;
  height: 36px;
}
.sys-dialog-body {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 18px;
  padding: 22px;
}
.sys-dialog-body > label {
  min-width: 0;
  display: grid;
  grid-template-columns: 88px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: #59677a;
  font-size: 13px;
}
.sys-dialog-body > label > span {
  text-align: right;
  white-space: nowrap;
}
.sys-dialog-body i {
  color: #d54552;
  font-style: normal;
}
.sys-dialog-body input:not([type='checkbox']):not([type='radio']),
.sys-dialog-body select {
  width: 100%;
  min-width: 0;
  height: 35px;
  padding: 0 9px;
  border: 1px solid #dbe1ea;
  border-radius: 5px;
  background: #fff;
  color: #354258;
  font-size: 13px;
}
.sys-dialog-body fieldset {
  grid-column: 1/-1;
  min-width: 0;
  margin: 0;
  padding: 14px;
  border: 1px solid #e7ebf1;
  border-radius: 5px;
}
.sys-dialog-body legend {
  padding: 0 5px;
  color: #344156;
  font-size: 13px;
  font-weight: 600;
}
.sys-dialog-body .check-option,
.scope-options label,
.scope-departments label {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  margin: 5px 18px 5px 0;
  color: #536176;
  font-size: 13px;
}
.sys-dialog-body input[type='checkbox'],
.sys-dialog-body input[type='radio'] {
  accent-color: var(--brand);
}
.role-editor {
  grid-template-columns: 1fr 1fr;
}
.role-editor > label:nth-child(3) {
  grid-column: 1/-1;
}
.permission-row {
  display: flex;
  align-items: center;
  gap: 16px;
  min-height: 36px;
  border-bottom: 1px solid #f0f2f5;
  color: #536176;
  font-size: 13px;
}
.permission-row:last-child {
  border-bottom: 0;
}
.permission-row b {
  width: 90px;
  flex: none;
  color: #46546a;
  font-weight: 600;
}
.permission-row label {
  display: inline-flex;
  align-items: center;
  gap: 5px;
  white-space: nowrap;
}
.scope-options {
  display: flex;
  flex-wrap: wrap;
}
.scope-departments {
  padding-top: 7px;
  border-top: 1px solid #edf0f4;
}
.sys-notice {
  position: fixed;
  right: 22px;
  bottom: 22px;
  z-index: 50;
  padding: 10px 15px;
  border-radius: 5px;
  background: #29384e;
  color: #fff;
  font-size: 13px;
  box-shadow: 0 8px 24px #1828402b;
}
.role-dialog {
  width: min(1220px, calc(100vw - 48px));
  max-height: calc(100vh - 84px);
}
.role-editor {
  display: flex;
  flex-direction: column;
  gap: 16px;
  padding: 16px;
  overflow: auto;
}
.role-basics {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 13px 18px;
  flex: none;
}
.role-basics label {
  display: grid;
  grid-template-columns: 78px minmax(0, 1fr);
  align-items: center;
  gap: 8px;
  color: #59677a;
  font-size: 13px;
}
.role-basics label > span {
  text-align: right;
  white-space: nowrap;
}
.role-basics i {
  color: #d54552;
  font-style: normal;
}
.role-basics input,
.role-basics select {
  width: 100%;
  height: 35px;
  min-width: 0;
  padding: 0 9px;
  border: 1px solid #dbe1ea;
  border-radius: 5px;
  background: #fff;
  color: #354258;
  font-size: 13px;
}
@media (max-width: 1150px) {
  .sys-toolbar {
    flex-wrap: wrap;
  }
  .sys-filters {
    width: 100%;
    justify-content: flex-end;
  }
  .org-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .org-detail {
    display: none;
  }
}
@media (max-width: 900px) {
  .users-layout {
    --org-panel-width: 210px;
    grid-template-columns: var(--org-panel-width) minmax(0, 1fr);
  }
}

.role-copy-tools {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--text);
}
.role-copy-tools > span {
  white-space: nowrap;
  flex-shrink: 0;
}
.role-copy-tools select {
  flex: 0 1 280px;
  min-width: 200px;
  height: 34px;
  border: 1px solid #dbe1ea;
  border-radius: 5px;
  padding: 0 9px;
  font: inherit;
  color: var(--text);
  background: #fff;
}
.role-basics label {
  color: var(--text);
}
</style>
