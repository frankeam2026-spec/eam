import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import Records from '../components/knowledge/KnowledgeRepairRecords.vue';
import type { RepairRecords } from '../components/knowledge/repairRecords';
const request = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
describe('assistant repair records', () => {
  it('pages through the authorized endpoint with the original criteria', async () => {
    const result: RepairRecords = {
      data: [],
      total: 30,
      dateField: 'reportedAt',
      criteria: {
        from: '2026-09-29',
        to: '2026-10-29',
        relation: 'owned',
        completed: false,
        equipmentId: '9007199254740993',
      },
    };
    request.mockResolvedValue({ ...result, data: [] });
    const wrapper = mount(Records, {
      props: { result },
      global: { stubs: { AppBusinessTable: true, AppPagination: true } },
    });
    wrapper.getComponent({ name: 'AppPagination' }).vm.$emit('update:page', 2);
    await flushPromises();
    const url = new URL(request.mock.calls[0]![0], 'http://local.test');
    expect(url.pathname).toBe('/knowledge/assistant/repair-records');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('size')).toBe('20');
    expect(url.searchParams.get('relation')).toBe('owned');
    expect(url.searchParams.get('equipmentId')).toBe('9007199254740993');
    expect(wrapper.getComponent({ name: 'AppBusinessTable' }).props('pagination')).toBe(false);
    wrapper.unmount();
  });
});
