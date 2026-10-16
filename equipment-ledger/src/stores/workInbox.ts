import { ref } from 'vue';
import { defineStore } from 'pinia';
import { workInboxSummary, readWorkInbox, type InboxItem, type InboxSummary } from '../api';
import { workspaceReturn } from '../utils/workspaceNavigation';

export const useWorkInboxStore = defineStore('work-inbox', () => {
  const summary = ref<InboxSummary>({ counts: { pending: 0, completed: 0, unread: 0 }, kinds: [] });
  const error = ref('');
  let pending: Promise<void> | undefined;
  async function refresh() {
    if (pending) return pending;
    pending = (async () => {
      try {
        summary.value = await workInboxSummary();
        error.value = '';
      } catch (e) {
        error.value = e instanceof Error ? e.message : '收件箱加载失败';
      }
    })().finally(() => {
      pending = undefined;
    });
    return pending;
  }
  async function read(row?: InboxItem) {
    await readWorkInbox(row);
    await refresh();
    window.dispatchEvent(new Event('work-inbox-changed'));
  }
  function destination(row: InboxItem, options: { actionable?: boolean; returnTo?: string } = {}) {
    const returnTo = workspaceReturn(options.returnTo);
    if (row.module === 'inventory' && row.kind === '待出库' && !Number(row.done))
      return {
        name: 'inventory-outbound-detail',
        params: { id: 'new' },
        query: { requests: row.orderId, tab: 'pending', ...(returnTo ? { returnTo } : {}) },
      };
    return {
      name:
        row.module === 'anomaly'
          ? 'anomalies'
          : row.module === 'repairPlan'
            ? 'repair-plans'
            : row.module === 'inventoryOutbound'
              ? 'inventory-outbound-detail'
              : ['inventoryTransfer', 'inventoryReturn'].includes(row.module || '')
                ? 'inventory-operation-detail'
                : row.module === 'inventory'
                  ? 'inventory-requisition-detail'
                  : row.module === 'inspection'
                    ? 'inspection-task'
                    : row.module === 'maintenance'
                      ? 'maintenance-task'
                      : 'repair-detail',
      params: {
        id: row.orderId,
        ...(row.module === 'inventoryTransfer'
          ? { kind: 'transfers' }
          : row.module === 'inventoryReturn'
            ? { kind: 'returns' }
            : {}),
      },
      query: {
        ...(returnTo ? { returnTo } : {}),
        ...(options.actionable && !Number(row.done) ? { taskAction: actionName(row) } : {}),
      },
    };
  }
  function moduleName(module?: string) {
    if (module === 'anomaly') return '异常';
    if (module === 'repairPlan') return '维修计划';
    return module === 'inventory' ||
      module === 'inventoryTransfer' ||
      module === 'inventoryReturn' ||
      module === 'inventoryOutbound'
      ? '库存'
      : module === 'inspection'
        ? '点巡检'
        : module === 'maintenance'
          ? '保养'
          : '维修';
  }
  function actionName(row: InboxItem) {
    if (Number(row.done)) return '查看';
    if (row.kind === '等待接单') return '跟进';
    if (row.kind.includes('派工')) return '派工';
    if (row.kind.includes('接单')) return '接单';
    if (row.kind.includes('审核')) return '审核';
    if (row.kind.includes('验收')) return '验收';
    if (row.kind.includes('待执行')) return '执行';
    if (row.kind.includes('收货')) return '收货';
    if (row.kind.includes('发料')) return '发料';
    if (row.kind.includes('出库')) return '出库';
    return '处理';
  }
  return { summary, error, refresh, read, destination, moduleName, actionName };
});
