/** Task lifecycle colors are semantic and remain unchanged by the user's theme. */
export const TASK_STATUS_PALETTE = {
  waiting: { color: '#a67c00', background: '#fff8d6' },
  active: { color: '#c66a00', background: '#fff1d6' },
  review: { color: '#205cf5', background: '#eaf1ff' },
  complete: { color: '#11934a', background: '#e8faf0' },
  danger: { color: '#e33535', background: '#ffe8e8' },
  neutral: { color: '#606b7b', background: '#f1f3f5' },
} as const;
export type TaskStatusTone = keyof typeof TASK_STATUS_PALETTE;

const statusGroups: Record<TaskStatusTone, readonly string[]> = {
  waiting: ['待派工', '待接单', '待执行', '待处理', '已安排'],
  active: ['处理中', '执行中'],
  review: ['审批中', '待验收', '待审核'],
  complete: ['已完成', '已审核', '已消除'],
  danger: ['故障', '审核不通过', '已过期', '已超期'],
  neutral: ['未开始', '挂起', '已挂起', '已取消', '已撤销', '已作废', '过期作废', '已免检'],
};
const statusTones = new Map<string, TaskStatusTone>(
  Object.entries(statusGroups).flatMap(([tone, statuses]) =>
    statuses.map((status) => [status, tone as TaskStatusTone] as const),
  ),
);
export function taskStatusStyle(status: string) {
  return TASK_STATUS_PALETTE[statusTones.get(status) || 'neutral'];
}
