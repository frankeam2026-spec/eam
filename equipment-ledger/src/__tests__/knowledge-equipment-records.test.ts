import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import Records from '../components/knowledge/KnowledgeEquipmentRecords.vue';
import type { EquipmentRecords } from '../components/knowledge/equipmentRecords';
const request = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));

describe('assistant equipment records', () => {
  it('preserves filters and selected equipment on database pagination', async () => {
    const result: EquipmentRecords = {
      data: [],
      total: 80,
      filters: [{ field: 'useState', operator: 'eq', value: '闲置' }],
      equipmentId: '9007199254740993',
    };
    request.mockResolvedValue(result);
    const wrapper = mount(Records, {
      props: { result },
      global: { stubs: { AppBusinessTable: true, AppPagination: true } },
    });
    wrapper.getComponent({ name: 'AppPagination' }).vm.$emit('update:page', 2);
    await flushPromises();
    const url = new URL(request.mock.calls[0]![0], 'http://local.test');
    expect(url.pathname).toBe('/knowledge/assistant/equipment-records');
    expect(url.searchParams.get('page')).toBe('2');
    expect(url.searchParams.get('size')).toBe('20');
    expect(url.searchParams.get('equipmentId')).toBe(result.equipmentId);
    expect(JSON.parse(url.searchParams.get('filters')!)).toEqual(result.filters);
    expect(wrapper.getComponent({ name: 'AppBusinessTable' }).props('pagination')).toBe(false);
    wrapper.unmount();
  });
});
