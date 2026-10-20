import { currentLocale } from '../i18n';
import { inventoryMenu } from '../inventory';
import { maintenanceMenu, inspectionMenu } from '../rolePermissions';
import { createRouter, createWebHashHistory } from 'vue-router';
import { pinia } from '../stores';
import { useAuthStore } from '../stores/auth';
import { BASE_TABS } from '../stores/baseData';
import { canUseBaseTab } from '../utils/baseDataAccess';

export type AppPage =
  | 'anomalies'
  | 'repair-plans'
  | 'home-templates'
  | 'print-templates'
  | 'business-config'
  | 'languages'
  | 'personalization'
  | 'personal-settings'
  | 'material-custody'
  | 'equipment'
  | 'inspection'
  | 'locations'
  | 'base'
  | 'repairs'
  | 'organization'
  | 'users'
  | 'roles'
  | 'labels'
  | 'workspace'
  | 'home2'
  | 'inventory'
  | 'maintenance';
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../WorkspaceLayout.vue'),
      children: [
        {
          path: 'home/analytics/:key',
          alias: '/home2/analytics/:key',
          name: 'home-analytics-details',
          component: () => import('../pages/HomeAnalyticsDetailPage.vue'),
          meta: { page: 'home2', menu: 'workspace' },
        },
        {
          path: 'home',
          alias: '/home2',
          name: 'home2',
          component: () => import('../pages/HomeComparisonPage.vue'),
          meta: { page: 'home2', menu: 'workspace' },
        },
        {
          path: 'workspace/inbox',
          name: 'workspace-inbox',
          component: () => import('../components/RepairInbox.vue'),
          meta: { page: 'workspace' },
        },
        {
          path: 'personal-settings',
          name: 'personal-settings',
          component: () => import('../pages/PersonalSettingsPage.vue'),
          meta: { page: 'personal-settings' },
        },
        {
          path: 'business-config',
          name: 'business-config',
          component: () => import('../pages/BusinessConfigPage.vue'),
          meta: { page: 'business-config', menu: 'businessConfig' },
        },
        {
          path: 'languages',
          name:'languages',
          component:()=>import('../pages/LanguageManagementPage.vue'),
          meta:{page:'languages',menu:'language'},
        },
        {
          path: 'personalization',
          name: 'personalization',
          component: () => import('../components/PersonalizationSettings.vue'),
          meta: { page: 'personalization', menu: 'personalization' },
        },
        {
          path: 'print-templates',
          name: 'print-templates',
          component: () => import('../pages/PrintTemplatesPage.vue'),
          meta: { page: 'print-templates', menu: 'printTemplate' },
        },
        {
          path: 'home-templates',
          name: 'home-templates',
          component: () => import('../components/HomeTemplates.vue'),
          meta: { page: 'home-templates', menu: 'homeTemplate' },
        },
        {
          path: 'anomalies/:id?',
          name: 'anomalies',
          component: () => import('../components/ExperienceManager.vue'),
          meta: { page: 'anomalies', menu: 'anomaly' },
        },
        {
          path: 'repair-plans/:id?',
          name: 'repair-plans',
          component: () => import('../components/ExperienceManager.vue'),
          meta: { page: 'repair-plans', menu: 'repairPlan' },
        },
        {
          path: 'material-custody',
          name: 'material-custody',
          component: () => import('../components/ExperienceManager.vue'),
          meta: { page: 'material-custody' },
        },
        { path: '', redirect: '/home' },
        {
          path: 'workspace',
          name: 'workspace',
          redirect: (to) => ({
            name: to.query.tab ? 'workspace-inbox' : 'home2',
            query: to.query,
          }),
        },
        {
          path: 'inspection/task/:id',
          name: 'inspection-task',
          component: () => import('../components/InspectionTask.vue'),
          meta: { page: 'inspection' },
        },
        {
          path: 'inspection/:kind(standards|daily-plans|daily-tasks|periodic-plans|periodic-tasks)',
          name: 'inspection',
          component: () => import('../components/InspectionModule.vue'),
          meta: { page: 'inspection', menu: 'inspection' },
        },
        {
          path: 'inspection/:kind(standards|daily-plans|periodic-plans)/new',
          name: 'inspection-new',
          component: () => import('../components/InspectionModule.vue'),
          meta: { page: 'inspection', menu: 'inspection', action: '新增' },
        },
        {
          path: 'inspection/:kind(standards|daily-plans|periodic-plans)/:id/edit',
          name: 'inspection-edit',
          component: () => import('../components/InspectionModule.vue'),
          meta: { page: 'inspection', menu: 'inspection', action: '编辑' },
        },
        {
          path: 'inventory/operations/:kind(returns|transfers|counts|openings|reversals)',
          name: 'inventory-operations',
          component: () => import('../components/InventoryOperations.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/operations/:kind(returns|transfers|counts|openings|reversals)/:id',
          name: 'inventory-operation-detail',
          component: () => import('../components/InventoryOperations.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/queries/:kind(balances|layers|transactions|transit)',
          name: 'inventory-queries',
          component: () => import('../components/InventoryQueries.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/outbounds',
          name: 'inventory-outbounds',
          component: () => import('../components/InventoryOutbounds.vue'),
          meta: { page: 'inventory', menu: 'inventoryOutbound' },
        },
        {
          path: 'inventory/outbounds/:id',
          name: 'inventory-outbound-detail',
          component: () => import('../components/InventoryOutbounds.vue'),
          meta: { page: 'inventory', menu: 'inventoryOutbound' },
        },
        {
          path: 'inventory/requisitions',
          name: 'inventory-requisitions',
          component: () => import('../components/InventoryRequisitions.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/requisitions/:id',
          name: 'inventory-requisition-detail',
          component: () => import('../components/InventoryRequisitions.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/receipts',
          name: 'inventory-receipts',
          component: () => import('../components/InventoryReceipts.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/receipts/:id',
          name: 'inventory-receipt-detail',
          component: () => import('../components/InventoryReceipts.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory/:kind(materials|warehouses|bins)',
          name: 'inventory-masters',
          component: () => import('../components/InventoryMasters.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'inventory',
          name: 'inventory',
          component: () => import('../components/SpareInventory.vue'),
          meta: { page: 'inventory', menu: 'inventoryQuery' },
        },
        {
          path: 'maintenance/plans/:id',
          name: 'maintenance-plan-detail',
          component: () => import('../components/MaintenancePlanDetail.vue'),
          meta: { page: 'maintenance', menu: 'maintenancePlan' },
        },
        {
          path: 'maintenance/task/:id',
          name: 'maintenance-task',
          component: () => import('../components/MaintenanceTask.vue'),
          meta: { page: 'maintenance' },
        },
        {
          path: 'maintenance/:kind(types|standards|plans|tasks)',
          name: 'maintenance',
          component: () => import('../components/MaintenanceModule.vue'),
          meta: { page: 'maintenance', menu: 'maintenance' },
        },
        {
          path: 'maintenance/:kind(types|standards|plans)/new',
          name: 'maintenance-new',
          component: () => import('../components/MaintenanceModule.vue'),
          meta: { page: 'maintenance', menu: 'maintenance', action: '新增' },
        },
        {
          path: 'maintenance/:kind(types|standards|plans)/:id/edit',
          name: 'maintenance-edit',
          component: () => import('../components/MaintenanceModule.vue'),
          meta: { page: 'maintenance', menu: 'maintenance', action: '编辑' },
        },
        {
          path: 'repairs/:id',
          name: 'repair-detail',
          component: () => import('../components/RepairWorkflow.vue'),
          meta: { page: 'repairs' },
        },
        {
          path: 'equipment',
          name: 'equipment',
          component: () => import('../pages/EquipmentLedgerPage.vue'),
          meta: { page: 'equipment', menu: 'equipment' },
        },
        {
          path: 'equipment/import',
          name: 'equipment-import',
          redirect: (to) => ({ name: 'equipment', query: { ...to.query, import: '1' } }),
          meta: { page: 'equipment', menu: 'equipment', action: '导入' },
        },
        {
          path: 'equipment/new',
          name: 'equipment-new',
          component: () => import('../pages/EquipmentLedgerPage.vue'),
          meta: { page: 'equipment', menu: 'equipment', action: '新增' },
        },
        {
          path: 'equipment/:id/edit',
          name: 'equipment-edit',
          component: () => import('../pages/EquipmentLedgerPage.vue'),
          meta: { page: 'equipment', menu: 'equipment', action: '编辑' },
        },
        {
          path: 'equipment/:id/history',
          name: 'equipment-history',
          component: () => import('../pages/EquipmentLedgerPage.vue'),
          meta: { page: 'equipment', menu: 'equipment' },
        },
        {
          path: 'locations',
          name: 'locations',
          component: () => import('../components/LocationManagement.vue'),
          meta: { page: 'locations', menu: 'location' },
        },
        {
          path: 'base',
          name: 'base',
          component: () => import('../pages/BaseDataPage.vue'),
          meta: { page: 'base', menu: 'base' },
        },
        {
          path: 'repairs',
          name: 'repairs',
          component: () => import('../components/RepairWorkOrders.vue'),
          meta: { page: 'repairs', menu: 'repair' },
          props: () => {
            const a = useAuthStore(pinia);
            return { currentUser: a.currentUser, grantedActions: a.grantedActions.repair || [] };
          },
        },
        {
          path: 'labels',
          name: 'labels',
          component: () => import('../components/LabelTemplates.vue'),
          meta: { page: 'labels', menu: 'equipmentTag' },
          props: () => ({ actions: useAuthStore(pinia).grantedActions.equipmentTag || [] }),
        },
        {
          path: 'organization',
          name: 'organization',
          component: () => import('../components/OrganizationManagement.vue'),
          meta: { page: 'organization', menu: 'org' },
          props: () => ({
            apiReady: useAuthStore(pinia).authState === 'ready',
            grantedActions: useAuthStore(pinia).grantedActions,
          }),
        },
        ...(['users', 'roles'] as const).map((page, index) => ({
          path: page,
          name: page,
          component: () => import('../components/SystemManagement.vue'),
          meta: { page, menu: ['user', 'role'][index] },
          props: () => ({
            view: page,
            apiReady: useAuthStore(pinia).authState === 'ready',
            grantedActions: useAuthStore(pinia).grantedActions,
          }),
        })),
        {
          path: 'access-denied',
          name: 'access-denied',
          component: () => import('../components/common/AccessDenied.vue'),
        },
        { path: ':pathMatch(.*)*', redirect: '/equipment' },
      ],
    },
  ],
});
router.beforeEach(async (to) => {
  const auth = useAuthStore(pinia);
  await auth.load();
  if (auth.authState !== 'ready') return true;
  if (
    to.name === 'inventory-operation-detail' &&
    to.params.kind === 'returns' &&
    /^\d+$/.test(String(to.params.id))
  )
    return true;
  if (to.name === 'anomalies' && /^\d+$/.test(String(to.params.id))) return true;
  if (to.name === 'workspace' && to.query.tab) return { name: 'workspace-inbox', query: to.query };
  if (to.name === 'inventory-requisitions' && to.query.queue === 'pending')
    return { name: 'inventory-outbounds', query: { tab: 'pending' } };
  if (to.name === 'base') {
    const visibleTabs = BASE_TABS.filter((tab) => canUseBaseTab(auth.can, tab));
    const requested = String(to.query.tab || (auth.can('base') ? '单位' : '设备分类'));
    if (visibleTabs.includes(requested)) return true;
    if (visibleTabs.length)
      return {
        name: 'base',
        query: { ...to.query, tab: visibleTabs.includes('单位') ? '单位' : visibleTabs[0] },
      };
  }

  const menu =
    to.meta.page === 'inventory' && to.name !== 'inventory'
      ? inventoryMenu(
          String(
            to.params.kind ||
              (String(to.name).includes('outbound')
                ? 'outbounds'
                : String(to.name).includes('requisition')
                  ? 'requisitions'
                  : String(to.name).includes('receipt')
                    ? 'receipts'
                    : 'balances'),
          ),
        )
      : to.meta.menu === 'maintenance'
        ? maintenanceMenu(String(to.params.kind || 'tasks'))
        : to.meta.menu === 'inspection'
          ? inspectionMenu(String(to.params.kind || 'daily-tasks'))
          : (to.meta.menu as string | undefined);
  if (
    to.name === 'inventory-requisition-detail' &&
    (auth.can('repair', '查看') ||
      auth.can('maintenanceTask', '查看') ||
      auth.can('inspectionDailyTask', '查看') ||
      auth.can('inspectionPeriodicTask', '查看'))
  )
    return true;
  if (to.name === 'inventory-outbound-detail' && to.params.id !== 'new') return true;
  if (!menu || auth.can(menu, String(to.meta.action || '查看'))) return true;
  const available = [
    ['equipment', 'equipment'],
    ['repairs', 'repair'],
    ['labels', 'equipmentTag'],
    ['organization', 'org'],
    ['users', 'user'],
    ['roles', 'role'],
    ['locations', 'location'],
    ...['standards', 'daily-plans', 'daily-tasks', 'periodic-plans', 'periodic-tasks'].map(
      (kind) => ['inspection', inspectionMenu(kind), kind],
    ),
    ...['types', 'standards', 'plans', 'tasks'].map((kind) => [
      'maintenance',
      maintenanceMenu(kind),
      kind,
    ]),
    ['workspace', 'workspace'],
    ['base', 'base'],
  ].find(([, key]) => auth.can(key!));
  if (available)
    return {
      name: available[0],
      ...(available[0] === 'maintenance'
        ? { params: { kind: available[2] } }
        : available[0] === 'inspection'
          ? { params: { kind: available[2] } }
          : {}),
    };
  if (auth.can('category')) return { name: 'base', query: { tab: '设备分类' } };
  return { name: 'access-denied' };
});

router.afterEach((to, from, failure) => {
  const auth = useAuthStore(pinia);
  if (
    failure ||
    auth.authState !== 'ready' ||
    !auth.can('workspace') ||
    to.path === from.path ||
    !/^\/(equipment|repairs|maintenance|inspection|anomalies|repair-plans|inventory|organization|users|roles)(\/|$)/.test(
      to.path,
    )
  )
    return;
  fetch('/api/v1/home/recent', {
    method: 'POST',
    credentials: 'same-origin',
    headers: { 'Accept-Language': currentLocale.value, 'Content-Type': 'application/json' },
    body: JSON.stringify({ url: to.path }),
  }).catch(() => {});
});
