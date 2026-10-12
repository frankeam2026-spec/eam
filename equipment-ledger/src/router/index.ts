import { inventoryMenu } from '../inventory';
import { maintenanceMenu, inspectionMenu } from '../rolePermissions';
import { defineComponent } from 'vue';
import { createRouter, createWebHashHistory } from 'vue-router';
import { pinia } from '../stores';
import { useAuthStore } from '../stores/auth';

export type AppPage =
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
  | 'inventory'
  | 'maintenance';
const InlinePage = defineComponent({ render: () => null });
export const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      component: () => import('../WorkspaceLayout.vue'),
      children: [
        { path: '', redirect: '/equipment' },
        {
          path: 'workspace',
          name: 'workspace',
          component: () => import('../components/RepairInbox.vue'),
          meta: { page: 'workspace' },
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
          component: InlinePage,
          meta: { page: 'equipment', menu: 'equipment' },
        },
        {
          path: 'equipment/new',
          name: 'equipment-new',
          component: InlinePage,
          meta: { page: 'equipment', menu: 'equipment', action: '新增' },
        },
        {
          path: 'equipment/:id/edit',
          name: 'equipment-edit',
          component: InlinePage,
          meta: { page: 'equipment', menu: 'equipment', action: '编辑' },
        },
        {
          path: 'equipment/:id/history',
          name: 'equipment-history',
          component: InlinePage,
          meta: { page: 'equipment', menu: 'equipment' },
        },
        {
          path: 'locations',
          name: 'locations',
          component: () => import('../components/LocationManagement.vue'),
          meta: { page: 'locations', menu: 'location' },
        },
        { path: 'base', name: 'base', component: InlinePage, meta: { page: 'base', menu: 'base' } },
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
        ...(['organization', 'users', 'roles'] as const).map((page, index) => ({
          path: page,
          name: page,
          component: () => import('../components/SystemManagement.vue'),
          meta: { page, menu: ['org', 'user', 'role'][index] },
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
  if (to.name === 'inventory-requisitions' && to.query.queue === 'pending')
    return { name: 'inventory-outbounds', query: { tab: 'pending' } };
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
          : to.name === 'base'
            ? to.query.tab === '设备分类'
              ? 'category'
              : 'base'
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
