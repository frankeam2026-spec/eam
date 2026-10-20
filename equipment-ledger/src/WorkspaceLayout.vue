<script setup lang="ts">import { tr } from './i18n';

import { canUseBaseTab } from './utils/baseDataAccess';
import { ElButton, ElInput, ElMenu, ElSubMenu, ElMenuItem, ElIcon } from 'element-plus';
import 'element-plus/es/components/menu/style/css';
import 'element-plus/es/components/menu-item/style/css';
import 'element-plus/es/components/sub-menu/style/css';
import 'element-plus/es/components/icon/style/css';

import SystemBrand from './components/common/SystemBrand.vue';
import { useBrandingStore } from './stores/branding';
import AppIcon from './components/common/AppIcon.vue';
import { inventoryMenu } from './inventory';
import { maintenanceMenu, inspectionMenu } from './rolePermissions';
import RepairNotificationBell from './components/common/RepairNotificationBell.vue';
import HelpMenu from './components/common/HelpMenu.vue';
import UserAccountMenu from './components/common/UserAccountMenu.vue';
import { computed, onMounted, onUnmounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { storeToRefs } from 'pinia';
import { useRoute, useRouter } from 'vue-router';
import { useAuthStore } from './stores/auth';
import { useUiStore } from './stores/ui';
import { useBaseDataStore, BASE_TABS } from './stores/baseData';
import type { AppPage } from './router';
import * as api from './api';
import LoginScreen from './components/LoginScreen.vue';

const auth = useAuthStore();

const ui = useUiStore();

const baseData = useBaseDataStore();

const { palette, collapsed: desktopCollapsed } = storeToRefs(ui);

const compactQuery = window.matchMedia('(max-width:1060px)');

const compactViewport = ref(compactQuery.matches);

const compactExpanded = ref(false);

const collapsed = computed({
  get: () => (compactViewport.value ? !compactExpanded.value : desktopCollapsed.value),
  set: (value: boolean) => {
    if (compactViewport.value) compactExpanded.value = !value;
    else desktopCollapsed.value = value;
  },
});

function navigationViewportChanged(event: MediaQueryListEvent) {
  compactViewport.value = event.matches;
  compactExpanded.value = false;
}

const helpSection = ref('');

const brandingStore = useBrandingStore();

const productName = computed(() => brandingStore.branding.systemName);

const { authState, authMessage, grantedActions } = storeToRefs(auth);

const router = useRouter();

const route = useRoute();

function can(menu: string, action = '查看') {
  return grantedActions.value[menu]?.includes(action) === true;
}

function canMenu(item: MenuItem) {
  const experienceMenus: Record<string, string> = {
    anomalies: 'anomaly',
    'repair-plans': 'repairPlan',
    'home-templates': 'homeTemplate',
    'print-templates': 'printTemplate',
    'business-config': 'businessConfig',
    personalization: 'personalization',
    languages: 'language',
    'material-custody': 'materialCustody',
  };
  if (item.page && experienceMenus[item.page]) return can(experienceMenus[item.page]!);
  if (item.page === 'base')
    return item.baseTab ? canUseBaseTab(can, item.baseTab) : can('base') || can('category');
  if (item.maintenanceKind === 'pending-issues' && !can('inventoryOutbound', '确认出库'))
    return false;
  const key =
    item.page === 'inventory'
      ? inventoryMenu(item.maintenanceKind || 'balances')
      : item.page === 'inspection'
        ? inspectionMenu(item.maintenanceKind || 'daily-tasks')
        : item.page === 'maintenance'
          ? maintenanceMenu(item.maintenanceKind || 'tasks')
          : item.page === 'labels'
            ? 'equipmentTag'
            : item.page === 'equipment'
              ? 'equipment'
              : item.page === 'repairs'
                ? 'repair'
                : item.page === 'organization'
                  ? 'org'
                  : item.page === 'users'
                    ? 'user'
                    : item.page === 'roles'
                      ? 'role'
                      : item.page === 'locations'
                        ? 'location'
                        : item.page === 'workspace'
                          ? 'workspace'
                          : null;
  return key ? can(key) : false;
}

const currentPage = computed<AppPage>({
  get: () => route.meta.page as AppPage,
  set: (page) => {
    router.push(
      page === 'maintenance'
        ? { name: page, params: { kind: 'tasks' } }
        : page === 'inspection'
          ? { name: page, params: { kind: 'daily-tasks' } }
          : { name: page },
    );
  },
});

type MenuItem = {
  label: string;
  icon: string;
  page?: AppPage;
  baseTab?: string;
  maintenanceKind?: string;
};

type MenuGroup = { id: string; label: string; icon: string; items: MenuItem[] };

const navGroups: MenuGroup[] = [
  {
    id: 'assets',
    get label() { return tr('ui.equipment.text0001'); },
    icon: 'Equipment',
    items: [
      { get label() { return tr('ui.equipment.text0002'); }, icon: 'Equipment', page: 'equipment' },
      { get label() { return tr('ui.equipment.text0003'); }, icon: 'Document', page: 'labels' },
    ],
  },
  {
    id: 'operations',
    get label() { return tr('ui.equipment.text0004'); },
    icon: 'Tool',
    items: [
      { get label() { return tr('ui.equipment.text0005'); }, icon: 'Warning', page: 'anomalies' },
      // { label: '维修计划', icon: 'Clock', page: 'repair-plans' },
      { get label() { return tr('ui.equipment.text0006'); }, icon: 'Tool', page: 'repairs' },
    ],
  },
  {
    id: 'maintenance',
    get label() { return tr('ui.equipment.text0007'); },
    icon: 'Clock',
    items: [
      { get label() { return tr('ui.equipment.text0008'); }, icon: 'Clock', page: 'maintenance', maintenanceKind: 'types' },
      { get label() { return tr('ui.equipment.text0009'); }, icon: 'Document', page: 'maintenance', maintenanceKind: 'standards' },
      { get label() { return tr('ui.equipment.text0010'); }, icon: 'Grid', page: 'maintenance', maintenanceKind: 'plans' },
      { get label() { return tr('ui.equipment.text0011'); }, icon: 'Check', page: 'maintenance', maintenanceKind: 'tasks' },
    ],
  },
  {
    id: 'inspection',
    get label() { return tr('ui.equipment.text0012'); },
    icon: 'Check',
    items: [
      { get label() { return tr('ui.equipment.text0013'); }, icon: 'Document', page: 'inspection', maintenanceKind: 'standards' },
      { get label() { return tr('ui.equipment.text0014'); }, icon: 'Grid', page: 'inspection', maintenanceKind: 'daily-plans' },
      { get label() { return tr('ui.equipment.text0015'); }, icon: 'Check', page: 'inspection', maintenanceKind: 'daily-tasks' },
      {
        get label() { return tr('ui.equipment.text0016'); },
        icon: 'Clock',
        page: 'inspection',
        maintenanceKind: 'periodic-plans',
      },
      {
        get label() { return tr('ui.equipment.text0017'); },
        icon: 'Check',
        page: 'inspection',
        maintenanceKind: 'periodic-tasks',
      },
    ],
  },
  {
    id: 'spares',
    get label() { return tr('ui.equipment.text0018'); },
    icon: 'Warehouse',
    items: [
      { get label() { return tr('ui.equipment.text0019'); }, icon: 'Document', page: 'inventory', maintenanceKind: 'materials' },
      { get label() { return tr('ui.equipment.text0020'); }, icon: 'Warehouse', page: 'inventory', maintenanceKind: 'warehouses' },
      { get label() { return tr('ui.equipment.text0021'); }, icon: 'Download', page: 'inventory', maintenanceKind: 'receipts' },
      { get label() { return tr('ui.equipment.text0022'); }, icon: 'Right', page: 'inventory', maintenanceKind: 'requisitions' },
      { get label() { return tr('ui.equipment.text0023'); }, icon: 'Right', page: 'inventory', maintenanceKind: 'outbounds' },
      { get label() { return tr('ui.equipment.text0024'); }, icon: 'Grid', page: 'inventory', maintenanceKind: 'balances' },
      { get label() { return tr('ui.equipment.text0025'); }, icon: 'Warehouse', page: 'material-custody' },
      { get label() { return tr('ui.equipment.text0026'); }, icon: 'Return', page: 'inventory', maintenanceKind: 'returns' },
      { get label() { return tr('ui.equipment.text0027'); }, icon: 'Transfer', page: 'inventory', maintenanceKind: 'transfers' },
      { get label() { return tr('ui.equipment.text0028'); }, icon: 'Document', page: 'inventory', maintenanceKind: 'counts' },
      { get label() { return tr('ui.equipment.text0029'); }, icon: 'Warehouse', page: 'inventory', maintenanceKind: 'openings' },
      { get label() { return tr('ui.equipment.text0030'); }, icon: 'Undo', page: 'inventory', maintenanceKind: 'reversals' },
    ],
  },
  {
    id: 'system',
    get label() { return tr('ui.equipment.text0031'); },
    icon: 'Settings',
    items: [
      { get label() { return tr('ui.equipment.text0032'); }, icon: 'Settings', page: 'business-config' },
      { get label() { return tr('ui.equipment.text0033'); }, icon: 'Settings', page: 'personalization' },
      { get label(){ return tr('language.manager.menu'); }, icon:'Settings',page:'languages' },
      { get label() { return tr('ui.equipment.text0034'); }, icon: 'Grid', page: 'home-templates' },
      { get label() { return tr('ui.equipment.text0035'); }, icon: 'Printer', page: 'print-templates' },
      { get label() { return tr('ui.equipment.text0036'); }, icon: 'Organization', page: 'organization' },
      { get label() { return tr('ui.equipment.text0037'); }, icon: 'User', page: 'users' },
      { get label() { return tr('ui.equipment.text0038'); }, icon: 'Permissions', page: 'roles' },
      { get label() { return tr('ui.equipment.text0039'); }, icon: 'Location', page: 'locations' },
      { get label() { return tr('ui.equipment.text0040'); }, icon: 'Grid', page: 'base' },
    ],
  },
];

const menuSearch = ref('');

const matchingNavGroups = computed(() =>
  navGroups
    .map((group) => ({
      ...group,
      items: group.items.filter(
        (item) =>
          canMenu(item) &&
          (collapsed.value ||
            !menuSearch.value.trim() ||
            (group.label + item.label).includes(menuSearch.value.trim())),
      ),
    }))
    .filter((group) => group.items.length),
);

const openedNavGroups = ref(navGroups.map((group) => group.id));
function menuItemIndex(item: MenuItem) {
  return [item.page, item.maintenanceKind, item.baseTab].filter(Boolean).join(':');
}
const activeMenuIndex = computed(() => {
  if (currentPage.value === 'home2') return 'home';
  if (currentPage.value === 'workspace') return 'workspace';
  const item = navGroups.flatMap((group) => group.items).find(navItemActive);
  return item ? menuItemIndex(item) : '';
});
const defaultOpenedGroups = computed(() =>
  menuSearch.value.trim()
    ? matchingNavGroups.value.map((group) => group.id)
    : openedNavGroups.value,
);
function menuGroupOpened(index: string) {
  if (collapsed.value || menuSearch.value.trim()) return;
  if (!openedNavGroups.value.includes(index)) openedNavGroups.value.push(index);
}
function menuGroupClosed(index: string) {
  if (collapsed.value || menuSearch.value.trim()) return;
  openedNavGroups.value = openedNavGroups.value.filter((key) => key !== index);
}
function selectMenu(index: string) {
  if (index === 'home' || index === 'workspace') {
    if (can('workspace'))
      void router.push({
        name: index === 'home' ? 'home2' : 'workspace-inbox',
      });
    return;
  }
  const item = navGroups
    .flatMap((group) => group.items)
    .find((item) => menuItemIndex(item) === index);
  if (item) activateNav(item);
}

function navItemActive(item: MenuItem) {
  return (
    item.page === currentPage.value &&
    (item.page !== 'inventory' || !!item.maintenanceKind || route.name === 'inventory') &&
    (!item.baseTab || baseTab.value === item.baseTab) &&
    (!item.maintenanceKind ||
      route.params.kind === item.maintenanceKind ||
      (item.maintenanceKind === 'pending-issues' &&
        route.name === 'inventory-requisitions' &&
        route.query.queue === 'pending') ||
      (['requisitions', 'pending-issues'].includes(item.maintenanceKind || '') &&
        String(route.name).startsWith('inventory-requisition') &&
        (item.maintenanceKind === 'pending-issues'
          ? route.query.queue === 'pending'
          : route.query.queue !== 'pending')) ||
      (item.maintenanceKind === 'outbounds' &&
        String(route.name).startsWith('inventory-outbound')) ||
      (item.maintenanceKind === 'balances' && route.name === 'inventory-queries') ||
      (item.maintenanceKind === 'receipts' && String(route.name).startsWith('inventory-receipt')) ||
      (item.maintenanceKind === 'tasks' && route.name === 'maintenance-task') ||
      (route.name === 'inspection-task' && route.query.kind === item.maintenanceKind))
  );
}

function activateNav(item: MenuItem) {
  if (!canMenu(item)) return;
  if (item.page)
    router.push({
      name:
        item.page === 'inventory' && item.maintenanceKind
          ? ['returns', 'transfers', 'counts', 'openings', 'reversals'].includes(
              item.maintenanceKind,
            )
            ? 'inventory-operations'
            : ['outbounds', 'pending-issues'].includes(item.maintenanceKind || '')
              ? 'inventory-outbounds'
              : item.maintenanceKind === 'receipts'
                ? 'inventory-receipts'
                : ['requisitions', 'pending-issues'].includes(item.maintenanceKind || '')
                  ? 'inventory-requisitions'
                  : item.maintenanceKind === 'balances'
                    ? 'inventory-queries'
                    : 'inventory-masters'
          : item.page,
      params:
        item.page === 'maintenance' ||
        item.page === 'inspection' ||
        (item.page === 'inventory' && item.maintenanceKind)
          ? { kind: item.maintenanceKind || 'tasks' }
          : {},
      query:
        item.maintenanceKind === 'pending-issues'
          ? { queue: 'pending' }
          : item.page === 'base'
            ? { tab: item.baseTab || '单位' }
            : {},
    });
  else notify(`${item.label}模块正在开发中`, 'info');
}

const baseTab = computed({
  get: () => String(route.query.tab || (can('base') ? '单位' : '设备分类')),
  set: (tab) => {
    router.replace({ name: 'base', query: { tab } });
  },
});

async function permissionsChanged() {
  await auth.load(true);
  if (auth.authState === 'ready') await connectBackend();
}

function sessionExpired() {
  auth.expire();
  baseData.clear();
}

async function initialize() {
  await auth.load();
  if (auth.authState === 'ready') await connectBackend();
}

async function signIn(tenantId: string, account: string, password: string) {
  try {
    await api.login(tenantId, account, password);
    await initialize();
  } catch (error) {
    authState.value = 'login';
    authMessage.value = error instanceof Error ? error.message : tr('ui.equipment.text0041');
  }
}

async function signOut() {
  try {
    await api.logout();
  } finally {
    sessionExpired();
    authMessage.value = '';
  }
}

function notify(message: string, type: 'success' | 'error' | 'warning' | 'info' = 'success') {
  ElMessage({ message, type });
}

async function connectBackend() {
  await Promise.all(
    [...BASE_TABS, '存放地点'].map(async (tab) => {
      if (
        !canUseBaseTab(can, tab) &&
        !can('equipment') &&
        !(['故障类型', '故障原因'].includes(tab) && can('repair'))
      )
        return;
      try {
        await baseData.load(tab);
      } catch (error) {
        notify(error instanceof Error ? error.message : tr('ui.equipment.text0042'), 'error');
      }
    }),
  );
}

function toggleSidebar() {
  collapsed.value = !collapsed.value;
}

watch(
  () => auth.user?.tenantId,
  (tenant) => {
    if (tenant) void brandingStore.load(tenant);
  },
  { immediate: true },
);
onMounted(() => {
  document.documentElement.dataset.theme = palette.value;
  compactQuery.addEventListener('change', navigationViewportChanged);
  window.addEventListener('eam-session-expired', sessionExpired);
  window.addEventListener('eam-permissions-changed', permissionsChanged);
  void initialize();
});
onUnmounted(() => {
  compactQuery.removeEventListener('change', navigationViewportChanged);
  window.removeEventListener('eam-session-expired', sessionExpired);
  window.removeEventListener('eam-permissions-changed', permissionsChanged);
});
</script>

<template>
  <div v-if="authState === 'ready'" class="app-shell" :class="{ 'sidebar-collapsed': collapsed }">
    <aside class="sidebar">
      <div class="sidebar-tools">
        <ElButton
          class="collapse"
          :aria-label="collapsed ? tr('ui.equipment.text0043') : tr('ui.equipment.text0044')"
          :title="collapsed ? tr('ui.equipment.text0043') : tr('ui.equipment.text0044')"
          @click="toggleSidebar"
          ><AppIcon name="HamburgerButton"
        /></ElButton>
      </div>
      <div v-if="!collapsed" class="navigation-search">
        <ElInput v-model="menuSearch" :placeholder="tr('ui.equipment.text0045')" :aria-label="tr('ui.equipment.text0045')" /><ElButton
          v-if="menuSearch"
          :aria-label="tr('ui.equipment.text0046')"
          @click="menuSearch = ''"
          ><AppIcon name="Close"
        /></ElButton>
      </div>
      <ElMenu
        :key="`${collapsed}-${menuSearch.trim()}`"
        class="app-navigation"
        :aria-label="tr('ui.equipment.text0047')"
        :collapse="collapsed"
        :collapse-transition="false"
        :unique-opened="true"
        :default-active="activeMenuIndex"
        popper-class="app-navigation-popup"
        popper-effect="light"
        @select="selectMenu"
        @open="menuGroupOpened"
        @close="menuGroupClosed"
      >
        <ElMenuItem
          v-if="
            can('workspace') &&
            (collapsed || !menuSearch.trim() || '首页'.includes(menuSearch.trim()))
          "
          index="home"
          :aria-label="tr('ui.equipment.text0048')"
        >
          <ElIcon><AppIcon name="Grid" /></ElIcon>
          <template #title>{{ tr('ui.equipment.text0048') }}</template>
        </ElMenuItem>
        <ElMenuItem
          v-if="
            can('workspace') &&
            (collapsed || !menuSearch.trim() || '工作台'.includes(menuSearch.trim()))
          "
          index="workspace"
          :aria-label="tr('ui.equipment.text0049')"
        >
          <ElIcon><AppIcon name="Todo" /></ElIcon>
          <template #title>{{ tr('ui.equipment.text0049') }}</template>
        </ElMenuItem>
        <ElSubMenu
          v-for="group in matchingNavGroups"
          :key="group.id"
          :index="group.id"
          :aria-label="group.label"
        >
          <template #title>
            <ElIcon><AppIcon :name="group.icon" /></ElIcon>
            <span>{{ group.label }}</span>
          </template>
          <ElMenuItem
            v-for="item in group.items"
            :key="menuItemIndex(item)"
            :index="menuItemIndex(item)"
          >
            <template #title>{{ item.label }}</template>
          </ElMenuItem>
        </ElSubMenu>
      </ElMenu>
      <ElButton
        :title="tr('ui.equipment.text0050')"
        :aria-label="tr('ui.equipment.text0051')"
        @click="helpSection = 'guide'"
        class="sidebar-bottom"
        native-type="button"
        ><div class="help-icon">
          <AppIcon name="Help" />
        </div>
        <div class="help-copy"><b>{{ tr('ui.equipment.text0052') }}</b><small>{{ tr('ui.equipment.text0050') }}</small></div>
        <span class="chevron" aria-hidden="true"> <AppIcon name="Right" /> </span
      ></ElButton>
    </aside>
    <main class="main-area">
      <header class="topbar">
        <div class="top-left">
          <ElButton
            class="mobile-menu"
            :aria-label="collapsed ? tr('ui.equipment.text0053') : tr('ui.equipment.text0044')"
            @click="toggleSidebar"
            ><AppIcon name="Menu"
          /></ElButton>
          <SystemBrand
            class="product-brand"
            :name="productName"
            :logo-url="brandingStore.branding.logoUrl"
          />
        </div>
        <div class="top-actions">
          <RepairNotificationBell />
          <HelpMenu v-model="helpSection" />
          <span class="top-divider"></span>
          <UserAccountMenu @logout="signOut" />
        </div>
      </header>
      <RouterView />
    </main>
  </div>
  <LoginScreen
    v-else
    :state="authState"
    :message="authMessage"
    @login="signIn"
    @retry="initialize"
  />
</template>

<style scoped>
.navigation-search {
  display: flex;
  align-items: center;
  margin: 6px 10px 10px;
  border: 0;
  padding: 0;
}

.navigation-search button {
  border: 0;
  background: transparent;
  color: inherit;
  cursor: pointer;
}

.sidebar-bottom {
  border-right: 0;
  border-bottom: 0;
  border-left: 0;
  background: transparent;
  color: var(--text);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.sidebar-bottom b,
.sidebar-bottom small {
  color: inherit;
  font-size: 13px;
}

.sidebar-bottom:hover {
  background: var(--brand-soft);
  color: var(--brand);
}

.sidebar-bottom:focus-visible {
  outline: 2px solid var(--brand);
  outline-offset: -2px;
}

.main-area {
  position: relative;
}
/* Use native menu selection, folding and popups; only set product dimensions and theme. */
.app-navigation {
  --el-menu-item-height: 34px;
  --el-menu-sub-item-height: 32px;
  --el-menu-base-level-padding: 10px;
  --el-menu-level-padding: 20px;
  --el-menu-text-color: var(--text);
  --el-menu-active-color: var(--brand);
  --el-menu-hover-bg-color: var(--brand-soft);
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: 8px 4px;
  border-right: 0;
  overflow-y: auto;
  overflow-x: hidden;
}
.app-navigation :deep(.el-menu-item),
.app-navigation :deep(.el-sub-menu__title) {
  font-size: 13px;
  border-radius: 5px;
}
.app-navigation :deep(.el-menu-item.is-active) {
  background: var(--brand-soft);
}
.app-navigation.el-menu--collapse {
  --el-menu-item-height: 44px;
  --el-menu-base-level-padding: 20px;
  padding: 8px 0;
}
.sidebar-bottom {
  justify-content: flex-start;
  margin-left: 0;
}
.sidebar-bottom :deep(> span) {
  display: flex;
  align-items: center;
  width: 100%;
  min-width: 0;
  gap: 8px;
}
.sidebar-bottom .help-icon {
  flex: none;
}
.sidebar-bottom .help-copy {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 3px;
}
.sidebar-collapsed .sidebar-bottom :deep(> span) {
  justify-content: center;
  gap: 0;
}
.sidebar-collapsed .sidebar-bottom .help-copy,
.sidebar-collapsed .sidebar-bottom .chevron {
  display: none;
}
</style>
