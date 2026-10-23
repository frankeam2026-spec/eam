import { afterEach, describe, expect, it, vi } from 'vitest';
import { emptyInspection, inspectionApi, type InspectionTask } from '../inspection';
import { useInspectionList } from '../composables/inspection/useInspectionList';
import type { MaintenanceListParameters } from '../composables/maintenance/useMaintenanceList';

const parameters: MaintenanceListParameters = {
  page: 1,
  size: 20,
  q: '',
  conditions: '[]',
  filters: '{}',
  sortBy: '',
  direction: 'desc',
  queue: '全部',
  organization: '',
};
type Result = Awaited<ReturnType<typeof inspectionApi.query>>;
const plans: Result = {
  data: ['plan-1', 'plan-2'].map((id) => ({ ...emptyInspection(), id })),
  page: { number: 1, size: 20, total: 2 },
};
const tasks: Result = {
  data: ['task-1', 'task-2', 'task-3'].map((id) => ({ id }) as InspectionTask),
  page: { number: 1, size: 20, total: 3 },
};
afterEach(() => vi.restoreAllMocks());

describe('inspection resource switching', () => {
  it('clears old plans, counts and organization nodes while task data is loading', async () => {
    vi.spyOn(inspectionApi, 'listContext').mockResolvedValue({
      organizations: [{ id: 'org', name: 'plan department' }],
    });
    let complete!: (result: Result) => void;
    vi.spyOn(inspectionApi, 'query')
      .mockResolvedValueOnce(plans)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            complete = resolve;
          }),
      );
    const list = useInspectionList();
    await list.refresh('daily-plans', parameters);
    expect(list.rows.value).toHaveLength(2);
    expect(list.total.value).toBe(2);
    list.reset();
    const pending = list.refresh('daily-tasks', parameters);
    expect(list.rows.value).toEqual([]);
    expect(list.total.value).toBe(0);
    expect(list.organizations.value).toEqual([]);
    complete(tasks);
    expect(await pending).toBe(true);
    expect(list.rows.value.map((row) => row.id)).toEqual(['task-1', 'task-2', 'task-3']);
    expect(list.total.value).toBe(3);
  });

  it('does not restore a late plan response over the new task page', async () => {
    vi.spyOn(inspectionApi, 'listContext').mockResolvedValue({ organizations: [] });
    let completeOld!: (result: Result) => void;
    vi.spyOn(inspectionApi, 'query')
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            completeOld = resolve;
          }),
      )
      .mockResolvedValueOnce(tasks);
    const list = useInspectionList();
    const oldRequest = list.refresh('daily-plans', parameters);
    list.reset();
    await list.refresh('daily-tasks', parameters);
    completeOld(plans);
    expect(await oldRequest).toBe(false);
    expect(list.rows.value.map((row) => row.id)).toEqual(['task-1', 'task-2', 'task-3']);
    expect(list.total.value).toBe(3);
  });
});
