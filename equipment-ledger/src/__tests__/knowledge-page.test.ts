import { describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import KnowledgePage from '../pages/KnowledgePage.vue';
const request = vi.hoisted(() => vi.fn());
vi.mock('../api', () => ({ request }));
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: () => true }) }));
vi.mock('../i18n', () => ({ tr: (key: string) => key }));

describe('knowledge document list', () => {
  it('passes server records to the shared table and disables its duplicate footer', async () => {
    const row = {
      id: '9007199254740993',
      title: 'P-103',
      companyId: '20',
      status: 'DRAFT',
      parseStatus: 'READY',
      rowVersion: 0,
    };
    request.mockResolvedValue({ data: [row], total: 1 });
    const wrapper = mount(KnowledgePage, {
      global: {
        directives: { loading: () => {} },
        stubs: { AppBusinessTable: true, AppDialog: true, AppPagination: true },
      },
    });
    await flushPromises();
    const table = wrapper.findComponent({ name: 'AppBusinessTable' });
    expect(table.props('rows')).toEqual([row]);
    expect(table.props('pagination')).toBe(false);
    expect(request).toHaveBeenCalledWith('/knowledge/documents?q=&page=1&size=20');
    wrapper.unmount();
  });
});
