<script setup lang="ts">
import {
  ElMessage,
  ElTree,
  ElTreeSelect,
  ElForm,
  ElTag,
  ElAlert,
  type TreeInstance,
  type TreeNodeData,
  type FormInstance,
  type FormRules,
} from 'element-plus';
import 'element-plus/es/components/alert/style/css';
import AppFormField from './common/AppFormField.vue';
import { organizationTree } from '../utils/organizationTree';
import AppListToolbar from './common/AppListToolbar.vue';
import { ElTableColumn } from 'element-plus';
import { ElButton, ElInput, ElSelect, ElOption, ElCheckbox } from 'element-plus';

import AppBusinessTable from './common/AppBusinessTable.vue';
import AppIcon from './common/AppIcon.vue';
import OrganizationIcon from './common/OrganizationIcon.vue';
import AppDialog from './common/AppDialog.vue';
import AppFormFooter from './common/AppFormFooter.vue';
import AppBackButton from './common/AppBackButton.vue';
import { ElDropdown, ElDropdownMenu, ElDropdownItem } from 'element-plus';
import { useUnsavedChanges } from '../composables/useUnsavedChanges';
import { onBeforeRouteLeave, onBeforeRouteUpdate } from 'vue-router';
import AppPagination from './common/AppPagination.vue';
import { confirmAction } from '../services/dialog';
import { computed, nextTick, reactive, ref, watch } from 'vue';
import * as api from '../api';
import FilterConditionPopover from './common/FilterConditionPopover.vue';
import { useConditionPopover } from '../composables/useConditionPopover';
import AppliedFilterChips from './common/AppliedFilterChips.vue';
import { matchesAdvanced } from '../query/advancedFilters';
import type { FilterCondition, FilterField } from '../query/advancedFilters';
import ListSearchInput from './common/ListSearchInput.vue';
import ListToolbarIcons from './ListToolbarIcons.vue';
import RolePermissionEditor from './RolePermissionEditor.vue';
import { normalizeMenuGrants, menuByKey, type MenuGrants } from '../rolePermissions';

type View = 'users' | 'roles';
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
  const menu = props.view === 'users' ? 'user' : 'role';
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
const userOrgTree = ref<TreeInstance>();
const orgSearch = ref('');
const userOrgNodes = computed(() => organizationTree(orgs.value));
const userDepartmentNodes = computed(() =>
  organizationTree(orgs.value.map((item) => ({ ...item, disabled: item.status !== '启用' }))),
);
const usersLoading = ref(false),
  usersError = ref(''),
  orgError = ref('');
const canAssignRoles = computed(
  () => can('分配角色') && props.grantedActions.role?.includes('授权'),
);
function filterOrganization(value: string, data: TreeNodeData) {
  return (
    !value ||
    `${data.name} ${data.code}`.toLocaleLowerCase().includes(value.trim().toLocaleLowerCase())
  );
}
watch(orgSearch, (value) => userOrgTree.value?.filter(value));
const search = ref(''),
  dialog = ref(false),
  saving = ref(false),
  editing = ref(false);
const sysColumnFilterOpen = ref(false),
  sysColumnsOpen = ref(false),
  sysColumnFilters = reactive<Record<string, string>>({}),
  sysPage = ref(1),
  sysPageSize = ref(10);
type ListColumn = {
  key: string;
  label: string;
  minWidth?: number;
  align?: 'left' | 'center' | 'right';
};
const userColumns: ListColumn[] = [
  { key: 'account', label: '登录账号', minWidth: 130 },
  { key: 'name', label: '姓名', minWidth: 100 },
  { key: 'employeeNo', label: '工号', minWidth: 100 },
  { key: 'department', label: '所属组织', minWidth: 160 },
  { key: 'roles', label: '角色', minWidth: 170 },
  { key: 'phone', label: '手机号', minWidth: 130 },
  { key: 'status', label: '状态', minWidth: 80, align: 'center' },
];
const roleColumns: ListColumn[] = [
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
watch([orgTreeVisible, userOrgNodes], async () => {
  await nextTick();
  userOrgTree.value?.filter(orgSearch.value);
});
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
const batchFormRef = ref<FormInstance>();
const batchRules: FormRules<typeof userBatch> = {
  orgId: [{ required: true, message: '请选择部门', trigger: 'change' }],
  password: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    { min: 12, max: 72, message: '密码长度需为 12 至 72 位', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value, done) =>
        done(value !== userBatch.password ? new Error('两次输入的密码不一致') : undefined),
      trigger: 'blur',
    },
  ],
};
const batchGuard = useUnsavedChanges(
  () => userBatch,
  () => userBatchOpen.value,
  () => userBatchBusy.value,
);
async function openUserBatch(operation: 'department' | 'password') {
  if (!can('编辑') || !selectedUsers.value.length) return;
  Object.assign(userBatch, { operation, orgId: '', password: '', confirmPassword: '' });
  userBatchError.value = '';
  batchGuard.markSaved();
  userBatchOpen.value = true;
  await nextTick();
  batchFormRef.value?.clearValidate();
}
async function closeUserBatch() {
  if (!(await batchGuard.permitLeave())) return;
  userBatchOpen.value = false;
  userBatch.password = userBatch.confirmPassword = '';
}
async function submitUserBatch() {
  if (userBatchBusy.value || !can('编辑')) return;
  if (!selectedUsers.value.length || selectedUsers.value.length > 200) {
    userBatchError.value = '请选择 1 至 200 个用户';
    return;
  }
  if (!(await batchFormRef.value?.validate().catch(() => false))) return;
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
    flash('批量操作已完成', 'success');
  } catch (e) {
    userBatchError.value = e instanceof Error ? e.message : '操作失败';
  } finally {
    userBatchBusy.value = false;
  }
}
const selectedOrg = ref('');
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
const userFormRef = ref<FormInstance>();
const userRules: FormRules<User> = {
  account: [
    { required: true, whitespace: true, message: '请输入登录账号', trigger: 'blur' },
    {
      validator: (_rule, value, done) =>
        done(
          users.value.some(
            (item) => item.id !== userForm.id && item.account === String(value).trim(),
          )
            ? new Error('登录账号已存在')
            : undefined,
        ),
      trigger: 'blur',
    },
  ],
  name: [{ required: true, whitespace: true, message: '请输入姓名', trigger: 'blur' }],
  orgId: [
    { required: true, message: '请选择所属组织', trigger: 'change' },
    {
      validator: (_rule, value, done) =>
        done(
          value && !orgs.value.some((item) => item.id === value && item.status === '启用')
            ? new Error('请选择启用的组织')
            : undefined,
        ),
      trigger: 'change',
    },
  ],
  password: [
    {
      validator: (_rule, value, done) => {
        if (!String(value || '').trim() && !editing.value) done(new Error('请输入初始密码'));
        else if (value && (value.length < 12 || value.length > 72))
          done(new Error('密码长度需为 12 至 72 位'));
        else done();
      },
      trigger: 'blur',
    },
  ],
};
const userRoleOptions = computed(() =>
  roles.value.filter((role) => role.status === '启用' || userForm.roleIds.includes(role.id)),
);
const roleForm = reactive<Role>({
  id: '',
  code: '',
  name: '',
  description: '',
  status: '启用',
  menuPermissions: {},
});
const formGuard = useUnsavedChanges(
  () => (props.view === 'users' ? userForm : roleForm),
  () => dialog.value,
  () => saving.value,
);
onBeforeRouteLeave(() => !saving.value);
onBeforeRouteUpdate(() => !saving.value);
async function closeDialog() {
  if (saving.value || !(await formGuard.permitLeave())) return;
  dialog.value = false;
}
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
    selectedUsers.value = [];
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
async function openUser(item?: User) {
  if (!can(item ? '编辑' : '新增')) return;
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
  await nextTick();
  userFormRef.value?.clearValidate();
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
  flash(`已复制“${source.name}”的权限，请保存生效`, 'info');
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
  if (
    props.view === 'users' &&
    (!can(editing.value ? '编辑' : '新增') ||
      !(await userFormRef.value?.validate().catch(() => false)))
  )
    return;
  saving.value = true;
  try {
    if (props.view === 'users') {
      if (props.apiReady) {
        const result = await api.saveSystemUser(userForm, !editing.value);
        const item = result.data;
        if (editing.value) users.value = users.value.map((x) => (x.id === item.id ? item : x));
        else users.value.push(item);
      } else {
        const { password: _password, ...item } = userForm;
        if (editing.value)
          users.value = users.value.map((user) =>
            user.id === item.id ? { ...item, roleIds: [...item.roleIds] } : user,
          );
        else users.value.push({ ...item, id: crypto.randomUUID(), roleIds: [...item.roleIds] });
      }
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
    flash('已保存', 'success');
    if (props.apiReady) window.dispatchEvent(new Event('eam-permissions-changed'));
  } catch (error) {
    flash(error instanceof Error ? error.message : '保存失败');
  } finally {
    saving.value = false;
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
function flash(msg: string, type: 'success' | 'error' | 'info' = 'error') {
  ElMessage({ message: msg, type });
}
async function loadRemote() {
  usersLoading.value = true;
  usersError.value = orgError.value = '';
  const [orgResult, userResult, roleResult] = await Promise.allSettled([
    api.listOrganizations(),
    api.listSystemUsers(),
    api.listSystemRoles(),
  ]);
  if (orgResult.status === 'fulfilled') orgs.value = orgResult.value.data;
  else
    orgError.value = orgResult.reason instanceof Error ? orgResult.reason.message : '组织加载失败';
  if (userResult.status === 'fulfilled') users.value = userResult.value.data;
  else
    usersError.value =
      userResult.reason instanceof Error ? userResult.reason.message : '用户加载失败';
  if (roleResult.status === 'fulfilled') roles.value = roleResult.value.data.map(normalizeRole);
  usersLoading.value = false;
  await nextTick();
  userOrgTree.value?.filter(orgSearch.value);
}
// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows1 = () => pageUsers.value;
type TableColumnRow1 = NonNullable<ReturnType<typeof tableColumnRows1>>[number];

// Preserve the row type while using native ElTableColumn slots.
const tableColumnRows2 = () => pageRoles.value;
type TableColumnRow2 = NonNullable<ReturnType<typeof tableColumnRows2>>[number];
const { filterAnchor, editingFilter, showFilter, replaceFilters, confirmFilter } =
  useConditionPopover(
    advancedOpen,
    () => advancedConditions.value,
    (value) => {
      advancedConditions.value = value;
      sysPage.value = 1;
      selectedUsers.value = [];
    },
  );
</script>

<template>
  <section class="content sys-page">
    <AppListToolbar class="sys-toolbar">
      <div class="sys-actions">
        <ElButton
          v-if="can('新增')"
          :class="view === 'roles' ? 'sys-primary' : undefined"
          @click="view === 'users' ? openUser() : openRole()"
          type="primary"
          ><AppIcon name="Plus" /> {{ view === 'users' ? '新增用户' : '新增角色' }}</ElButton
        ><ElDropdown
          v-if="view === 'users' && can('编辑')"
          trigger="click"
          @command="openUserBatch"
        >
          <ElButton :disabled="!selectedUsers.length">
            批量操作<span v-if="selectedUsers.length">（{{ selectedUsers.length }}）</span
            ><AppIcon name="Down"
          /></ElButton>
          <template #dropdown
            ><ElDropdownMenu
              ><ElDropdownItem command="department">更换部门</ElDropdownItem
              ><ElDropdownItem command="password">重置密码</ElDropdownItem></ElDropdownMenu
            ></template
          > </ElDropdown
        ><ElButton
          v-if="view === 'users' && can('导入')"
          @click="flash('用户导入模板已准备', 'info')"
        >
          导入
        </ElButton>
      </div>
      <form class="sys-filters" @submit.prevent="sysPage = 1">
        <ListToolbarIcons
          v-if="view !== 'users'"
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
        <ElButton
          v-if="view === 'users'"
          class="advanced-filter-trigger"
          :class="{ active: advancedConditions.length }"
          @click="showFilter($event.currentTarget as HTMLElement)"
          native-type="button"
        >
          添加筛选<span v-if="advancedConditions.length">
            ({{ advancedConditions.length }})</span
          ></ElButton
        >
        <ElButton class="sys-reset" @click="resetSysList" native-type="button" text>重置</ElButton>
      </form>
    </AppListToolbar>
    <AppliedFilterChips
      editable
      @edit="(condition, anchor) => showFilter(anchor, condition.field)"
      v-if="view === 'users'"
      :conditions="advancedConditions"
      :fields="advancedFields"
      @change="replaceFilters"
    />
    <FilterConditionPopover
      :anchor="filterAnchor"
      :field-key="editingFilter"
      :placement="editingFilter ? 'bottom-start' : 'bottom-end'"
      v-if="view === 'users'"
      v-model="advancedOpen"
      :conditions="advancedConditions"
      :fields="advancedFields"
      @confirm="confirmFilter"
    />
    <div v-if="view === 'users'" class="users-layout" :class="{ 'tree-hidden': !orgTreeVisible }">
      <article v-if="orgTreeVisible" class="sys-card user-org-filter">
        <header><b>组织部门</b><ElButton @click="selectedOrg = ''">全部</ElButton></header>
        <ElInput
          v-model="orgSearch"
          class="user-org-search"
          placeholder="搜索公司、部门"
          clearable
          aria-label="搜索公司、部门"
          ><template #prefix><AppIcon name="Search" /></template
        ></ElInput>
        <ElAlert v-if="orgError" :title="orgError" type="error" :closable="false"
          ><ElButton text type="primary" @click="loadRemote">重试</ElButton></ElAlert
        >
        <div v-else class="org-tree">
          <ElTree
            ref="userOrgTree"
            :data="userOrgNodes"
            node-key="id"
            :props="{ label: 'name', children: 'children' }"
            :current-node-key="selectedOrg"
            :filter-node-method="filterOrganization"
            default-expand-all
            highlight-current
            :expand-on-click-node="false"
            :empty-text="usersLoading ? '加载中…' : '暂无匹配组织'"
            @node-click="(item: Org) => (selectedOrg = item.id)"
          >
            <template #default="{ data }"
              ><span class="user-org-node"
                ><OrganizationIcon :type="data.type" /><span :title="data.name">{{
                  data.name
                }}</span></span
              ></template
            >
          </ElTree>
        </div>
      </article>
      <ElButton
        class="org-panel-toggle"
        :class="{ collapsed: !orgTreeVisible }"
        :aria-label="orgTreeVisible ? '收起组织部门' : '展开组织部门'"
        :title="orgTreeVisible ? '收起组织部门' : '展开组织部门'"
        @click="orgTreeVisible = !orgTreeVisible"
        ><AppIcon name="Left" :class="{ flipped: !orgTreeVisible }"
      /></ElButton>
      <article class="sys-card sys-table-card">
        <div class="sys-table-scroll">
          <AppBusinessTable
            v-model:selection-ids="selectedUsers"
            :selection-width="48"
            selection-fixed="left"
            selection-align="center"
            selectable
            :rows="pageUsers"
            :loading="usersLoading"
            :error="usersError"
            @retry="loadRemote"
            :pagination="false"
            :fill="true"
            :framed="false"
            class="business-table"
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              show-overflow-tooltip
              v-for="col in activeColumns"
              :min-width="col.minWidth || 150"
              :label="col.label"
              :align="col.align || 'left'"
              :prop="col.key"
              :key="col.key"
              ><template #default="{ row: item, $index: tableRowIndex }">
                <ElTag
                  v-if="col.key === 'status'"
                  :type="item.status === '启用' ? 'success' : 'info'"
                  size="small"
                  >{{ item.status }}</ElTag
                ><span v-else>{{ userCell(item, col.key) }}</span>
              </template></ElTableColumn
            ><!-- @vue-generic {TableColumnRow1} -->
            <ElTableColumn
              class-name="action-col list-action-column"
              fixed="right"
              :width="170"
              :min-width="156"
              :resizable="false"
              label="操作"
              ><template #default="{ row: item, $index: tableRowIndex }">
                <ElButton link type="primary" v-if="can('编辑')" @click="openUser(item)"
                  >编辑</ElButton
                ><ElButton link type="primary" v-if="can('停用')" @click="toggleUserStatus(item)">{{
                  item.status === '启用' ? '停用' : '启用'
                }}</ElButton>
              </template></ElTableColumn
            ><template #empty
              ><div v-if="!pageUsers.length">没有匹配的用户</div></template
            ></AppBusinessTable
          >
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
              ><ElCheckbox
                v-for="col in view === 'users' ? userColumns : roleColumns"
                :key="col.key"
                :model-value="(view === 'users' ? userVisible : roleVisible).includes(col.key)"
                @change="toggleSysColumn(col.key)"
                >{{ col.label }}</ElCheckbox
              ></template
            ></ListToolbarIcons
          >
        </footer>
      </article>
    </div>
    <article v-else class="sys-card sys-table-card">
      <div class="sys-table-scroll">
        <AppBusinessTable
          :rows="pageRoles"
          :pagination="false"
          :fill="true"
          :framed="false"
          class="business-table"
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn
            show-overflow-tooltip
            v-for="col in activeColumns"
            :min-width="150"
            :prop="col.key"
            :key="col.key"
            ><template #header
              ><div class="table-header-label">{{ col.label }}</div>
              <div v-if="sysColumnFilterOpen" class="table-column-filter" @click.stop>
                <ElSelect v-if="col.key === 'status'" v-model="sysColumnFilters[col.key]"
                  ><ElOption value="" :label="'全部状态'" /><ElOption
                    :value="'启用'"
                    :label="'启用'" /><ElOption :value="'停用'" :label="'停用'" /></ElSelect
                ><ElInput
                  v-else
                  v-model="sysColumnFilters[col.key]"
                  :placeholder="`筛选${col.label}`"
                /></div></template
            ><template #default="{ row: item, $index: tableRowIndex }">
              <span
                v-if="col.key === 'status'"
                class="sys-status"
                :class="item.status === '启用' ? 'on' : 'off'"
                >{{ item.status }}</span
              ><b v-else-if="col.key === 'name'">{{ roleCell(item, col.key) }}</b
              ><span v-else>{{ roleCell(item, col.key) }}</span>
            </template></ElTableColumn
          ><!-- @vue-generic {TableColumnRow2} -->
          <ElTableColumn
            class-name="action-col list-action-column"
            fixed="right"
            :width="170"
            :min-width="156"
            :resizable="false"
            ><template #header><div class="table-header-label">操作</div></template
            ><template #default="{ row: item, $index: tableRowIndex }">
              <ElButton
                link
                type="primary"
                v-if="can('编辑') && can('授权')"
                @click="openRole(item)"
                >配置权限</ElButton
              ><ElButton
                v-if="can('删除')"
                class="danger"
                @click="removeRole(item)"
                type="danger"
                link
                >删除</ElButton
              >
            </template></ElTableColumn
          ><template #empty
            ><div v-if="!pageRoles.length">没有匹配的角色</div></template
          ></AppBusinessTable
        >
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
            ><ElCheckbox
              v-for="col in roleColumns"
              :key="col.key"
              :model-value="roleVisible.includes(col.key)"
              @change="toggleSysColumn(col.key)"
              >{{ col.label }}</ElCheckbox
            ></template
          ></ListToolbarIcons
        >
      </footer>
    </article>
    <Transition name="fade"
      ><div v-if="dialog && view === 'roles'" class="sys-mask role-page">
        <section class="sys-dialog" :class="{ 'role-dialog': view === 'roles' }">
          <header>
            <AppBackButton
              v-if="view === 'roles'"
              label="返回角色权限"
              :disabled="saving"
              @click="closeDialog"
            />
            <b>{{ editing ? '编辑' : '新增' }}角色</b>
          </header>
          <div class="sys-dialog-body role-editor" :inert="saving || undefined">
            <div class="role-basics">
              <label
                ><span>角色名称<i>*</i></span
                ><ElInput v-model="roleForm.name" placeholder="例如：设备管理员" /></label
              ><label
                ><span>角色编码<i>*</i></span
                ><ElInput v-model="roleForm.code" placeholder="唯一编码" /></label
              ><label
                ><span>角色描述</span
                ><ElInput v-model="roleForm.description" placeholder="说明角色职责" /></label
              ><label
                ><span>状态</span
                ><ElSelect v-model="roleForm.status"
                  ><ElOption :value="'启用'" :label="'启用'" /><ElOption
                    :value="'停用'"
                    :label="'停用'" /></ElSelect
              ></label>
            </div>
            <div class="role-copy-tools">
              <span>复制角色权限</span>
              <ElSelect v-model="copyRoleId" aria-label="选择来源角色"
                ><ElOption value="" :label="'选择已有角色'" /><ElOption
                  v-for="role in roles.filter((item) => item.id !== roleForm.id)"
                  :key="role.id"
                  :value="role.id"
                  :label="String(role.name)"
              /></ElSelect>
              <ElButton
                class="sys-secondary"
                :disabled="!copyRoleId || saving"
                @click="copyRolePermissions"
              >
                复制权限
              </ElButton>
            </div>
            <RolePermissionEditor v-model="roleForm.menuPermissions" :orgs="orgs" />
          </div>
          <AppFormFooter v-if="view === 'roles'">
            <ElButton class="sys-secondary" :disabled="saving" @click="closeDialog">取消</ElButton>
            <ElButton class="sys-primary" :disabled="saving" @click="save" type="primary">{{
              saving ? '保存中…' : '保存'
            }}</ElButton>
          </AppFormFooter>
        </section>
      </div></Transition
    >

    <AppDialog
      v-if="view === 'users'"
      v-model="dialog"
      :title="editing ? '编辑用户' : '新增用户'"
      :busy="saving"
      :before-close="formGuard.permitLeave"
      width="680px"
    >
      <ElForm
        ref="userFormRef"
        :model="userForm"
        :rules="userRules"
        label-width="88px"
        :disabled="saving"
        class="user-edit-form"
        @submit.prevent="save"
      >
        <AppFormField label="登录账号" prop="account"
          ><ElInput v-model="userForm.account" placeholder="请输入登录账号" autocomplete="off"
        /></AppFormField>
        <AppFormField label="姓名" prop="name"
          ><ElInput v-model="userForm.name" placeholder="请输入姓名"
        /></AppFormField>
        <AppFormField label="工号" prop="employeeNo"
          ><ElInput v-model="userForm.employeeNo" placeholder="请输入工号"
        /></AppFormField>
        <AppFormField label="手机号" prop="phone"
          ><ElInput v-model="userForm.phone" placeholder="请输入手机号"
        /></AppFormField>
        <AppFormField label="所属组织" prop="orgId" class="user-form-wide"
          ><ElTreeSelect
            v-model="userForm.orgId"
            :data="userDepartmentNodes"
            node-key="id"
            :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
            check-strictly
            filterable
            clearable
            placeholder="请选择公司或部门"
        /></AppFormField>
        <AppFormField
          :label="editing ? '重置密码' : '初始密码'"
          prop="password"
          :required="!editing"
          class="user-form-wide"
          ><ElInput
            v-model="userForm.password"
            type="password"
            show-password
            autocomplete="new-password"
            :placeholder="editing ? '留空不修改；12 至 72 位' : '请输入 12 至 72 位密码'"
        /></AppFormField>
        <AppFormField label="状态" prop="status"
          ><ElSelect v-model="userForm.status"
            ><ElOption label="启用" value="启用" /><ElOption label="停用" value="停用" /></ElSelect
        ></AppFormField>
        <AppFormField label="角色" prop="roleIds"
          ><ElSelect
            v-if="canAssignRoles"
            v-model="userForm.roleIds"
            multiple
            filterable
            collapse-tags
            collapse-tags-tooltip
            :max-collapse-tags="1"
            placeholder="请选择角色（可多选）"
            ><ElOption
              v-for="role in userRoleOptions"
              :key="role.id"
              :label="role.name"
              :value="role.id"
              :disabled="role.status !== '启用'" /></ElSelect
          ><span v-else>{{ roleNames(userForm.roleIds) || '未分配角色' }}</span></AppFormField
        >
      </ElForm>
      <template #footer
        ><ElButton :disabled="saving" @click="closeDialog">取消</ElButton
        ><ElButton type="primary" :loading="saving" @click="save">保存</ElButton></template
      >
    </AppDialog>

    <AppDialog
      v-model="userBatchOpen"
      :title="userBatch.operation === 'department' ? '更换部门' : '重置密码'"
      :busy="userBatchBusy"
      :before-close="batchGuard.permitLeave"
    >
      <div class="sys-user-batch">
        <p>已选择 {{ selectedUsers.length }} 个用户</p>
        <ElAlert
          v-if="userBatchError"
          :title="userBatchError"
          type="error"
          :closable="false"
          show-icon
        />
        <ElForm
          ref="batchFormRef"
          :model="userBatch"
          :rules="batchRules"
          label-width="88px"
          :disabled="userBatchBusy"
          @submit.prevent="submitUserBatch"
        >
          <AppFormField v-if="userBatch.operation === 'department'" label="所属部门" prop="orgId"
            ><ElTreeSelect
              v-model="userBatch.orgId"
              :data="userDepartmentNodes"
              node-key="id"
              :props="{ label: 'name', children: 'children', disabled: 'disabled' }"
              check-strictly
              filterable
              clearable
              placeholder="请选择部门"
          /></AppFormField>
          <template v-else>
            <p>重置后所选用户的登录会话将失效，请通过合适的渠道告知新密码。</p>
            <AppFormField label="新密码" prop="password"
              ><ElInput
                v-model="userBatch.password"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="12 至 72 位"
            /></AppFormField>
            <AppFormField label="确认密码" prop="confirmPassword"
              ><ElInput
                v-model="userBatch.confirmPassword"
                type="password"
                show-password
                autocomplete="new-password"
                placeholder="再次输入新密码"
            /></AppFormField>
          </template>
        </ElForm>
      </div>
      <template #footer
        ><ElButton :disabled="userBatchBusy" @click="closeUserBatch"> 取消</ElButton
        ><ElButton :loading="userBatchBusy" @click="submitUserBatch" type="primary">{{
          userBatchBusy ? '提交中…' : '提交'
        }}</ElButton></template
      >
    </AppDialog>
  </section>
</template>

<style scoped>
.user-org-search {
  margin: 10px;
  width: calc(100% - 20px);
}
.user-org-filter :deep(.el-tree) {
  --el-tree-node-content-height: 36px;
  --el-tree-text-color: var(--el-text-color-primary);
  font-size: 13px;
}
.user-org-filter :deep(.el-tree-node.is-current > .el-tree-node__content) {
  color: var(--el-color-primary);
}
.user-org-node {
  display: flex;
  align-items: center;
  gap: 7px;
  min-width: 0;
  padding-right: 8px;
}
.user-org-node :deep(.organization-icon) {
  color: var(--el-color-primary);
}
.user-org-node > span {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-edit-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(0, 1fr);
  column-gap: 24px;
}
.user-edit-form :deep(.el-select) {
  width: 100%;
}
.user-form-wide {
  grid-column: 1 / -1;
}
@media (max-width: 600px) {
  .user-edit-form {
    grid-template-columns: minmax(0, 1fr);
  }
}

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
@media (max-width: 900px) {
  .users-layout {
    gap: 8px;
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
.org-panel-toggle .app-icon {
  width: 13px;
  height: 13px;
}
.org-panel-toggle .app-icon.flipped {
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
.sys-table-scroll :deep(thead th) {
  position: sticky;
  top: 0;
  z-index: 1;
}
.sys-table-scroll :deep(tbody td b) {
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
.sys-dialog > header {
  height: 52px;
  flex: none;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 18px;
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
.sys-dialog-body {
  min-height: 0;
  overflow: auto;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16px 18px;
  padding: 22px;
}
.sys-dialog-body i {
  color: #d54552;
  font-style: normal;
}
.role-editor {
  grid-template-columns: 1fr 1fr;
}
.role-editor > label:nth-child(3) {
  grid-column: 1/-1;
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
@media (max-width: 1150px) {
  .sys-filters {
    width: 100%;
    justify-content: flex-end;
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
.role-basics label {
  color: var(--text);
}
</style>
