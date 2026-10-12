export type DataScope = 'all' | 'company' | 'department' | 'selected' | 'owner' | 'warehouse';
export type MenuGrant = { actions: string[]; dataScopes: DataScope[]; orgIds: string[] };
export type MenuGrants = Record<string, MenuGrant>;
export type PermissionMenu = { key: string; name: string; actions: string[]; scopes: DataScope[] };

export const permissionGroups: { name: string; menus: PermissionMenu[] }[] = [
  {
    name: '工作台',
    menus: [{ key: 'workspace', name: '工作台', actions: ['查看'], scopes: ['all'] }],
  },
  {
    name: '设备管理',
    menus: [
      {
        key: 'equipment',
        name: '设备台账',
        actions: ['查看', '新增', '编辑', '删除', '导入', '导出'],
        scopes: ['all', 'department', 'selected', 'owner', 'company'],
      },
      // { key: 'category', name: '设备分类', actions: ['查看','新增','编辑','删除'], scopes: ['all'] },
      {
        key: 'equipmentTag',
        name: '设备标签',
        actions: ['查看', '新增', '编辑', '删除', '打印', '导出'],
        scopes: ['all'],
      },
    ],
  },
  {
    name: '运维管理',
    menus: [
      {
        key: 'repair',
        name: '维修工单',
        actions: ['查看', '报修', '编辑', '派工', '处理', '验收', '撤回', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
    ],
  },
  {
    name: '保养管理',
    menus: [
      {
        key: 'maintenanceType',
        name: '保养类型',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all'],
      },
      {
        key: 'maintenanceStandard',
        name: '保养标准',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'maintenancePlan',
        name: '保养计划',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'maintenanceTask',
        name: '保养任务',
        actions: ['查看', '编辑', '执行', '验收', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
    ],
  },
  {
    name: '点巡检管理',
    menus: [
      {
        key: 'inspectionType',
        name: '巡检类型',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all'],
      },
      {
        key: 'inspectionStandard',
        name: '点巡检标准',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'inspectionDailyPlan',
        name: '日常点检计划',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'inspectionDailyTask',
        name: '日常点检任务',
        actions: ['查看', '执行', '审核', '转派', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'inspectionPeriodicPlan',
        name: '周期巡检计划',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
      {
        key: 'inspectionPeriodicTask',
        name: '周期巡检任务',
        actions: ['查看', '执行', '审核', '转派', '删除', '导出'],
        scopes: ['all', 'company', 'department', 'selected', 'owner'],
      },
    ],
  },
  {
    name: '库存管理',
    menus: [
      {
        key: 'inventoryMaterial',
        name: '物料档案',
        actions: ['查看', '新增', '编辑', '删除', '导入', '导出', '查看金额', '编辑参考价'],
        scopes: ['all'],
      },
      {
        key: 'inventoryWarehouse',
        name: '仓库与库位',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryReceipt',
        name: '入库管理',
        actions: ['查看', '新增', '编辑', '删除', '入库', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryOutbound',
        name: '出库管理',
        actions: [
          '查看',
          '新增直接出库',
          '编辑草稿',
          '删除草稿',
          '预留',
          '释放预留',
          '确认出库',
          '关闭剩余需求',
          '导出',
          '查看金额',
        ],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryRequisition',
        name: '领料申请',
        actions: ['查看', '新增', '编辑', '删除', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryReturn',
        name: '退料管理',
        actions: ['查看', '新增', '编辑', '删除', '入库', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryTransfer',
        name: '调拨管理',
        actions: ['查看', '新增', '编辑', '删除', '出库', '入库', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryCount',
        name: '盘点管理',
        actions: ['查看', '新增', '编辑', '删除', '出库', '入库', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryOpening',
        name: '期初与初始化',
        actions: ['查看', '新增', '编辑', '删除', '入库', '导入', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryReversal',
        name: '单据纠正',
        actions: ['查看', '新增', '编辑', '删除', '冲销', '导出', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
      {
        key: 'inventoryQuery',
        name: '库存查询',
        actions: ['查看', '导出', '冻结', '查看金额'],
        scopes: ['all', 'company', 'warehouse'],
      },
    ],
  },
  {
    name: '系统管理',
    menus: [
      {
        key: 'org',
        name: '组织架构',
        actions: ['查看', '新增', '编辑', '删除'],
        scopes: ['all', 'company', 'department', 'selected'],
      },
      {
        key: 'user',
        name: '用户管理',
        actions: ['查看', '新增', '编辑', '停用', '分配角色', '删除', '导入', '导出'],
        scopes: ['all', 'company', 'department', 'selected'],
      },
      {
        key: 'role',
        name: '角色权限',
        actions: ['查看', '新增', '编辑', '授权', '删除', '导出'],
        scopes: ['all'],
      },
      {
        key: 'location',
        name: '存放地点',
        actions: ['查看', '新增', '编辑', '删除', '导出'],
        scopes: ['all'],
      },
      {
        key: 'base',
        name: '基础数据',
        actions: ['查看', '新增', '编辑', '删除', '导入', '导出'],
        scopes: ['all'],
      },
    ],
  },
];

export const permissionMenus = permissionGroups.flatMap((group) => group.menus);
export const menuByKey: Record<string, PermissionMenu> = Object.fromEntries(
  permissionMenus.map((menu) => [menu.key, menu]),
);
export function defaultScope(menuKey: string): DataScope {
  const scopes = menuByKey[menuKey]?.scopes || ['all'];
  return scopes.includes('department') ? 'department' : scopes[0];
}

export function scopeLabel(scope: DataScope, menuKey: string): string {
  if (scope === 'warehouse') return '指定仓库';
  if (scope === 'all')
    return menuKey === 'equipment' ? '全部台账' : menuKey === 'repair' ? '全部工单' : '全部数据';
  if (scope === 'company') return '本公司及下级';
  if (scope === 'department') return '本部门';
  if (scope === 'selected') return '指定部门';
  return menuKey === 'equipment' ? '我负责的' : menuKey === 'repair' ? '与我相关的' : '我的数据';
}

const oldAction: Record<string, string> = {
  view: '查看',
  create: '新增',
  edit: '编辑',
  delete: '删除',
  disable: '停用',
  dispatch: '派工',
  process: '处理',
  accept: '验收',
  export: '导出',
};
const oldScope: Record<string, DataScope> = {
  全部数据: 'all',
  本公司及下级: 'company',
  本部门: 'department',
  本人: 'owner',
  指定部门: 'selected',
};

export function normalizeMenuGrants(
  value: unknown,
  legacyPermissions: string[] = [],
  legacyScope = '本部门',
  legacyOrgIds: string[] = [],
): MenuGrants {
  if (value && typeof value === 'object' && !Array.isArray(value)) {
    const result: MenuGrants = {};
    const expanded = { ...(value as Record<string, unknown>) };
    for (const parent of ['maintenance', 'inspection', 'inventory']) {
      const previous = expanded[parent] as Partial<MenuGrant> | undefined;
      if (!previous) continue;
      for (const menu of permissionMenus.filter((x) => x.key.startsWith(parent))) {
        if (expanded[menu.key]) continue;
        expanded[menu.key] = {
          ...previous,
          dataScopes: menu.scopes.length === 1 ? ['all'] : previous.dataScopes,
          orgIds: menu.scopes.length === 1 ? [] : previous.orgIds,
        };
      }
    }
    for (const [key, raw] of Object.entries(expanded)) {
      if (!menuByKey[key] || !raw || typeof raw !== 'object') continue;
      const grant = raw as Partial<MenuGrant>;
      const actions = Array.isArray(grant.actions)
        ? grant.actions.filter((action) => menuByKey[key].actions.includes(action))
        : [];
      const scopes = Array.isArray(grant.dataScopes)
        ? grant.dataScopes.filter((scope) => menuByKey[key].scopes.includes(scope))
        : [];
      const dataScopes = scopes.includes('all')
        ? (['all'] as DataScope[])
        : scopes.length
          ? scopes
          : actions.length
            ? [defaultScope(key)]
            : [];
      result[key] = {
        actions,
        dataScopes,
        orgIds:
          (dataScopes.includes('selected') || dataScopes.includes('warehouse')) &&
          Array.isArray(grant.orgIds)
            ? grant.orgIds.map(String)
            : [],
      };
    }
    return result;
  }
  const result: MenuGrants = {};
  for (const permission of legacyPermissions) {
    const [key, rawAction] = permission.split('.');
    if (!menuByKey[key]) continue;
    const action =
      key === 'repair' && rawAction === 'create'
        ? '报修'
        : key === 'requisition' && rawAction === 'create'
          ? '申请'
          : oldAction[rawAction] || rawAction;
    if (!menuByKey[key].actions.includes(action)) continue;
    const scope = oldScope[legacyScope] || defaultScope(key);
    const validScope = menuByKey[key].scopes.includes(scope) ? scope : defaultScope(key);
    result[key] ??= {
      actions: [],
      dataScopes: [validScope],
      orgIds: validScope === 'selected' ? legacyOrgIds.map(String) : [],
    };
    if (!result[key].actions.includes(action)) result[key].actions.push(action);
  }
  if (result.base && !result.location)
    result.location = {
      ...result.base,
      actions: result.base.actions.filter((a) => menuByKey.location!.actions.includes(a)),
      dataScopes: ['all'],
      orgIds: [],
    };
  for (const parent of ['maintenance', 'inspection', 'inventory']) {
    const expanded: Record<string, MenuGrant> = {};
    for (const menu of permissionMenus.filter((x) => x.key.startsWith(parent))) {
      const actions = legacyPermissions
        .filter((p) => p.startsWith(parent + '.'))
        .map((p) => oldAction[p.split('.')[1]!] || p.split('.')[1]!)
        .filter((a) => menu.actions.includes(a));
      if (!actions.length) continue;
      const scope = oldScope[legacyScope] || defaultScope(menu.key);
      const validScope = menu.scopes.includes(scope) ? scope : defaultScope(menu.key);
      expanded[menu.key] = {
        actions: [...new Set(actions)],
        dataScopes: [validScope],
        orgIds: validScope === 'selected' ? legacyOrgIds.map(String) : [],
      };
    }
    Object.assign(result, expanded);
  }
  return result;
}

export const maintenanceMenu = (kind: string) =>
  (
    ({
      types: 'maintenanceType',
      standards: 'maintenanceStandard',
      plans: 'maintenancePlan',
      tasks: 'maintenanceTask',
    }) as Record<string, string>
  )[kind] || 'maintenanceTask';
export const inspectionMenu = (kind: string) =>
  (
    ({
      types: 'inspectionType',
      standards: 'inspectionStandard',
      'daily-plans': 'inspectionDailyPlan',
      'daily-tasks': 'inspectionDailyTask',
      'periodic-plans': 'inspectionPeriodicPlan',
      'periodic-tasks': 'inspectionPeriodicTask',
    }) as Record<string, string>
  )[kind] || 'inspectionDailyTask';
