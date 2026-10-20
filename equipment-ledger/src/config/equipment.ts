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

/** 使用状态独立于设备健康状态；优先识别字典编码，名称兼容历史及自定义字典。 */
const USE_STATUS_TONES: Record<string, string> = {
  NORMAL: 'success',
  STOPPED: 'warning',
  IDLE: 'idle',
  SCRAPPED: 'danger',
};
const USE_STATUS_NAME_TONES: Record<string, string> = {
  在用: 'success',
  正常: 'success',
  闲置: 'idle',
  停用: 'warning',
  封存: 'idle',
  报废: 'danger',
};
const USE_STATUS_STYLES: Record<
  string,
  { color: string; background: string; borderColor: string }
> = {
  success: {
    color: 'var(--equipment-state-success-color)',
    background: 'var(--equipment-state-success-background)',
    borderColor: 'var(--equipment-state-success-border)',
  },
  warning: {
    color: 'var(--equipment-state-warning-color)',
    background: 'var(--equipment-state-warning-background)',
    borderColor: 'var(--equipment-state-warning-border)',
  },
  danger: {
    color: 'var(--equipment-state-danger-color)',
    background: 'var(--equipment-state-danger-background)',
    borderColor: 'var(--equipment-state-danger-border)',
  },
  idle: { color: '#7c3aed', background: '#f3edff', borderColor: '#dfd0ff' },
  other: {
    color: 'var(--el-color-primary)',
    background: 'var(--el-color-primary-light-9)',
    borderColor: 'var(--el-color-primary-light-7)',
  },
};
export function equipmentUseStatusStyle(code: string | undefined, name: string) {
  return USE_STATUS_STYLES[USE_STATUS_TONES[code || ''] || USE_STATUS_NAME_TONES[name] || 'other'];
}
