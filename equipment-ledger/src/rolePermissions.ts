import { tr } from './i18n';
import { PermissionAction } from './permissionActions';
export type DataScope =
  | 'all'
  | 'company'
  | 'companyTree'
  | 'companies'
  | 'departmentTree'
  | 'department'
  | 'selected'
  | 'owner'
  | 'warehouse';
export type MenuGrant = { actions: string[]; dataScopes: DataScope[]; orgIds: string[] };
export type MenuGrants = Record<string, MenuGrant>;
export type PermissionMenu = { key: string; name: string; actions: string[]; scopes: DataScope[] };

export const permissionGroups: { name: string; menus: PermissionMenu[] }[] = [
  {get name(){return tr('ai.settings');},menus:[{key:'aiSetting',get name(){return tr('ai.settings');},actions:[PermissionAction.VIEW,PermissionAction.EDIT],scopes:['all']}]},
  {get name(){return tr('knowledge.menu');},menus:[{key:'knowledge',get name(){return tr('knowledge.menu');},actions:[PermissionAction.VIEW,PermissionAction.EDIT,PermissionAction.PUBLISH,PermissionAction.DISABLE],scopes:['all','company','companyTree','companies']}]},
  {
    get name() {
      return tr('ui.common.label1');
    },
    menus: [
      {
        key: 'workspace',
        get name() {
          return tr('ui.common.label1');
        },
        actions: [PermissionAction.VIEW, PermissionAction.VIEW_TEAM_WORK],
        scopes: ['all'],
      },
    ],
  },
  {
    get name() {
      return tr('ui.equipment.text0001');
    },
    menus: [
      {
        key: 'equipment',
        get name() {
          return tr('ui.equipment.text0002');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.IMPORT,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'department',
          'departmentTree',
          'selected',
          'owner',
          'company',
          'companyTree',
          'companies',
        ],
      },
      // { key: 'category', get name(){return tr('ui.equipment.text1082');}, actions: [PermissionAction.VIEW,PermissionAction.CREATE,PermissionAction.EDIT,PermissionAction.DELETE], scopes: ['all'] },
      {
        key: 'equipmentTag',
        get name() {
          return tr('ui.equipment.text0003');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.PRINT,
          PermissionAction.EXPORT,
        ],
        scopes: ['all'],
      },
    ],
  },
  {
    get name() {
      return tr('ui.common.label2');
    },
    menus: [
      {
        key: 'anomaly',
        get name() {
          return tr('ui.equipment.text0005');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.RESOLVE_ANOMALY,
          PermissionAction.REPORT,
          PermissionAction.SCHEDULE_REPAIR,
          PermissionAction.VOID,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'repairPlan',
        get name() {
          return tr('ui.equipment.text0209');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.ENABLE,
          PermissionAction.GENERATE_REPAIR_ORDER,
          PermissionAction.CANCEL,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'materialCustody',
        get name() {
          return tr('ui.equipment.text0025');
        },
        actions: [PermissionAction.VIEW, PermissionAction.RECORD_CUSTODY, PermissionAction.EXPORT],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'repair',
        get name() {
          return tr('ui.equipment.text0006');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.REPORT,
          PermissionAction.EDIT,
          PermissionAction.DISPATCH,
          PermissionAction.PROCESS,
          PermissionAction.ACCEPT,
          PermissionAction.WITHDRAW,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
    ],
  },
  {
    get name() {
      return tr('ui.equipment.text0007');
    },
    menus: [
      {
        key: 'maintenanceType',
        get name() {
          return tr('ui.equipment.text0008');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: ['all'],
      },
      {
        key: 'maintenanceStandard',
        get name() {
          return tr('ui.equipment.text0009');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'maintenancePlan',
        get name() {
          return tr('ui.equipment.text0010');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'maintenanceTask',
        get name() {
          return tr('ui.equipment.text0011');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.EDIT,
          PermissionAction.EXECUTE,
          PermissionAction.ACCEPT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
    ],
  },
  {
    get name() {
      return tr('ui.equipment.text0012');
    },
    menus: [
      {
        key: 'inspectionType',
        get name() {
          return tr('ui.equipment.text0325');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: ['all'],
      },
      {
        key: 'inspectionStandard',
        get name() {
          return tr('ui.equipment.text0013');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'inspectionDailyPlan',
        get name() {
          return tr('ui.equipment.text0014');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'inspectionDailyTask',
        get name() {
          return tr('ui.equipment.text0015');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.EXECUTE,
          PermissionAction.AMEND_RESULT,
          PermissionAction.REVIEW,
          PermissionAction.TRANSFER,
          PermissionAction.EXEMPT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'inspectionPeriodicPlan',
        get name() {
          return tr('ui.equipment.text0016');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
      {
        key: 'inspectionPeriodicTask',
        get name() {
          return tr('ui.equipment.text0017');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.EXECUTE,
          PermissionAction.REVIEW,
          PermissionAction.TRANSFER,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
          'owner',
        ],
      },
    ],
  },
  {
    get name() {
      return tr('ui.equipment.text0018');
    },
    menus: [
      {
        key: 'inventoryMaterial',
        get name() {
          return tr('ui.equipment.text0019');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.IMPORT,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
          PermissionAction.EDIT_REFERENCE_PRICE,
        ],
        scopes: ['all'],
      },
      {
        key: 'inventoryWarehouse',
        get name() {
          return tr('ui.common.label3');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryReceipt',
        get name() {
          return tr('ui.equipment.text0021');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.STOCK_IN,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryOutbound',
        get name() {
          return tr('ui.equipment.text0023');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE_DIRECT_OUTBOUND,
          PermissionAction.EDIT_DRAFT,
          PermissionAction.DELETE_DRAFT,
          PermissionAction.RESERVE,
          PermissionAction.RELEASE_RESERVATION,
          PermissionAction.CONFIRM_ISSUE,
          PermissionAction.CLOSE_REMAINDER,
          PermissionAction.EXPORT,
          PermissionAction.PRINT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryRequisition',
        get name() {
          return tr('ui.equipment.text0022');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryReturn',
        get name() {
          return tr('ui.equipment.text0026');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.STOCK_IN,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryTransfer',
        get name() {
          return tr('ui.equipment.text0027');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.STOCK_OUT,
          PermissionAction.STOCK_IN,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryCount',
        get name() {
          return tr('ui.equipment.text0028');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.STOCK_OUT,
          PermissionAction.STOCK_IN,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryOpening',
        get name() {
          return tr('ui.equipment.text0029');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.STOCK_IN,
          PermissionAction.IMPORT,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryReversal',
        get name() {
          return tr('ui.equipment.text0030');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.REVERSE,
          PermissionAction.EXPORT,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
      {
        key: 'inventoryQuery',
        get name() {
          return tr('ui.equipment.text0024');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.EXPORT,
          PermissionAction.FREEZE,
          PermissionAction.VIEW_AMOUNT,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies', 'warehouse'],
      },
    ],
  },
  {
    get name() {
      return tr('ui.equipment.text0031');
    },
    menus: [
      {
        key: 'printTemplate',
        get name() {
          return tr('ui.equipment.text0035');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
        ],
        scopes: ['all', 'company', 'companyTree', 'companies'],
      },
      {
        key: 'businessConfig',
        get name() {
          return tr('ui.equipment.text0032');
        },
        actions: [PermissionAction.VIEW, PermissionAction.EDIT, PermissionAction.PUBLISH],
        scopes: ['all'],
      },
      {
        key: 'personalization',
        get name() {
          return tr('ui.equipment.text0033');
        },
        actions: [PermissionAction.VIEW],
        scopes: ['all'],
      },
      {
        key: 'language',
        get name() {
          return tr('language.manager.menu');
        },
        actions: [PermissionAction.VIEW],
        scopes: ['all'],
      },
      {key:'workflow',name:'审批流程',actions:[PermissionAction.VIEW,PermissionAction.EDIT,PermissionAction.PUBLISH,PermissionAction.DISABLE,PermissionAction.CONFIGURE_SCRIPT,PermissionAction.PUBLISH_SCRIPT,PermissionAction.TRIAL_SCRIPT],scopes:['all','company','companyTree','companies']},
      {key:'notificationPolicy',get name(){return tr('notify.policies');},actions:[PermissionAction.VIEW,PermissionAction.EDIT],scopes:['all']},
      {key:'notificationChannel',get name(){return tr('notify.channels');},actions:[PermissionAction.VIEW,PermissionAction.EDIT],scopes:['all']},
      {key:'notificationDelivery',get name(){return tr('notify.deliveries');},actions:[PermissionAction.VIEW,PermissionAction.RETRY],scopes:['all']},
      {
        key: 'homeTemplate',
        get name() {
          return tr('ui.equipment.text0034');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.PUBLISH,
          PermissionAction.DISABLE,
        ],
        scopes: ['all'],
      },
      {
        key: 'org',
        get name() {
          return tr('ui.equipment.text0036');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
        ],
      },
      {
        key: 'user',
        get name() {
          return tr('ui.equipment.text0037');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DISABLE,
          PermissionAction.ASSIGN_ROLE,
          PermissionAction.DELETE,
          PermissionAction.IMPORT,
          PermissionAction.EXPORT,
        ],
        scopes: [
          'all',
          'company',
          'companyTree',
          'companies',
          'department',
          'departmentTree',
          'selected',
        ],
      },
      {
        key: 'role',
        get name() {
          return tr('ui.equipment.text0038');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.AUTHORIZE,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: ['all'],
      },
      {
        key: 'location',
        get name() {
          return tr('ui.equipment.text0039');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.EXPORT,
        ],
        scopes: ['all'],
      },
      {
        key: 'base',
        get name() {
          return tr('ui.equipment.text0040');
        },
        actions: [
          PermissionAction.VIEW,
          PermissionAction.CREATE,
          PermissionAction.EDIT,
          PermissionAction.DELETE,
          PermissionAction.IMPORT,
          PermissionAction.EXPORT,
        ],
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
  return scopes.includes('department')
    ? 'department'
    : scopes.includes('company')
      ? 'company'
      : scopes[0];
}

export function scopeLabel(scope: DataScope, menuKey: string): string {
  if (scope === 'warehouse') return '指定仓库';
  if (scope === 'all')
    return menuKey === 'equipment' ? '全部台账' : menuKey === 'repair' ? '全部工单' : '全部数据';
  if (scope === 'company') return '本公司';
  if (scope === 'companyTree') return '本公司及下级公司';
  if (scope === 'companies') return '指定公司';
  if (scope === 'departmentTree') return '本部门及下级';
  if (scope === 'department') return '本部门';
  if (scope === 'selected') return '指定部门';
  return menuKey === 'equipment' ? '我负责的' : menuKey === 'repair' ? '与我相关的' : '我的数据';
}

const oldAction: Record<string, string> = {
  view: PermissionAction.VIEW,
  create: PermissionAction.CREATE,
  edit: PermissionAction.EDIT,
  delete: PermissionAction.DELETE,
  disable: PermissionAction.DISABLE,
  dispatch: PermissionAction.DISPATCH,
  process: PermissionAction.PROCESS,
  accept: PermissionAction.ACCEPT,
  export: PermissionAction.EXPORT,
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
        ? PermissionAction.REPORT
        : key === 'requisition' && rawAction === 'create'
          ? PermissionAction.APPLY
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
