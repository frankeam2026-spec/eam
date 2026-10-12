/** 产品内置设备健康状态，租户管理员不能自定义。 */
export const EQUIPMENT_STATES = ['正常', '带病运行', '故障'] as const;
export const EQUIPMENT_TABS = ['全部设备', ...EQUIPMENT_STATES] as const;

export function normalizeEquipmentState(value?: string): string {
  const legacy: Record<string, string> = {
    在用: '正常',
    闲置: '正常',
    维修中: '故障',
    待保养: '带病运行',
  };
  const normalized = legacy[value || ''] || value || '正常';
  return EQUIPMENT_STATES.includes(normalized as (typeof EQUIPMENT_STATES)[number])
    ? normalized
    : '正常';
}
