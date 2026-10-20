import { describe, it, expect, vi } from 'vitest';
import { maintenanceApi } from '../maintenance';
import {
  useMaintenanceList,
  type MaintenanceListParameters,
} from '../composables/maintenance/useMaintenanceList';
vi.mock('../maintenance', () => ({ maintenanceApi: { listContext: vi.fn(), query: vi.fn() } }));
const parameters: MaintenanceListParameters = {
  page: 1,
  size: 10,
  q: '',
  conditions: '[]',
  filters: '{}',
  sortBy: '',
  direction: 'desc',
  queue: '全部',
  organization: '',
};
describe('maintenance list concurrency', () => {
  it('ignores a slow old result after a newer condition was applied', async () => {
    vi.mocked(maintenanceApi.listContext).mockResolvedValue({ organizations: [] });
    let resolveOld!: (value: Awaited<ReturnType<typeof maintenanceApi.query>>) => void;
    vi.mocked(maintenanceApi.query)
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            resolveOld = resolve;
          }),
      )
      .mockResolvedValueOnce({ data: [], page: { number: 1, size: 10, total: 202 } });
    const list = useMaintenanceList();
    const old = list.refresh('plans', parameters);
    expect(await list.refresh('plans', { ...parameters, q: '新条件' })).toBe(true);
    resolveOld({ data: [], page: { number: 1, size: 10, total: 101 } });
    expect(await old).toBe(false);
    expect(list.total.value).toBe(202);
  });
  it('invalidates an outstanding response when its owner leaves', async () => {
    vi.mocked(maintenanceApi.listContext).mockResolvedValue({ organizations: [] });
    let resolve!: (value: Awaited<ReturnType<typeof maintenanceApi.query>>) => void;
    vi.mocked(maintenanceApi.query).mockImplementationOnce(
      () =>
        new Promise((done) => {
          resolve = done;
        }),
    );
    const list = useMaintenanceList();
    const pending = list.refresh('tasks', parameters);
    list.invalidate();
    resolve({ data: [], page: { number: 1, size: 10, total: 99 } });
    expect(await pending).toBe(false);
    expect(list.total.value).toBe(0);
  });
});
