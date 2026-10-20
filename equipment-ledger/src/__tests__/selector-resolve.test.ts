import { describe, it, expect, vi } from 'vitest';
import { request } from '../api';
import { resolvePlanning } from '../selectors';
vi.mock('../api', () => ({ request: vi.fn() }));
describe('selected identity hydration', () => {
  it('resolves only selected IDs in bounded batches, retaining IDs outside the first candidate page', async () => {
    vi.mocked(request).mockResolvedValue({ equipment: [], users: [], standards: [] });
    await resolvePlanning('maintenance', {
      details: Array.from({ length: 205 }, (_, i) => ({
        equipmentId: String(i + 1),
        owners: ['73001'],
        projectRefs: [{ standardId: '900', itemId: '001' }],
      })),
    });
    expect(request).toHaveBeenCalledTimes(2);
    const first = JSON.parse(String(vi.mocked(request).mock.calls[0]![1]?.body));
    const last = JSON.parse(String(vi.mocked(request).mock.calls[1]![1]?.body));
    expect(first.equipmentIds).toHaveLength(200);
    expect(last.equipmentIds).toEqual(['201', '202', '203', '204', '205']);
    expect(first.userIds).toEqual(['73001']);
    expect(first.standardIds).toEqual(['900']);
  });
});
