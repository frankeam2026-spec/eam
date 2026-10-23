import { describe, expect, it, vi } from 'vitest';
import { defineComponent, h, nextTick, ref } from 'vue';
import { flushPromises, mount, shallowMount } from '@vue/test-utils';
import { ElPagination } from 'element-plus';
import AppPagination from '../components/common/AppPagination.vue';
import { normalizePageSize } from '../utils/pagination';
import { archiveState } from '../equipmentOverview';
import { useStandardSelectorPage } from '../composables/useStandardSelectorPage';
import { request } from '../api';

vi.mock('../api', () => ({ request: vi.fn() }));

describe('shared list pagination', () => {
  it('offers 20/50/100 and returns to page one when the size changes', () => {
    const wrapper = shallowMount(AppPagination, {
      props: { page: 3, pageSize: 20, total: 350 },
    });
    const pagination = wrapper.findComponent(ElPagination);
    expect(pagination.props('pageSizes')).toEqual([20, 50, 100]);
    pagination.vm.$emit('update:page-size', 100);
    expect(wrapper.emitted('update:pageSize')).toEqual([[100]]);
    expect(wrapper.emitted('update:page')).toEqual([[1]]);
    wrapper.unmount();
  });

  it('restores supported URL and archive choices, falling back from legacy choices', () => {
    expect([20, '50', 100].map(normalizePageSize)).toEqual([20, 50, 100]);
    expect([10, 24, 5, undefined, 'invalid', 999].map(normalizePageSize)).toEqual([
      20, 20, 20, 20, 20, 20,
    ]);
    const saved = archiveState();
    saved.attention.size = 10;
    saved.events.size = 100;
    expect(archiveState(saved).attention.size).toBe(20);
    expect(archiveState(saved).events.size).toBe(100);
  });

  it('requests the new standard selector size from page one and keeps selected items', async () => {
    vi.mocked(request).mockResolvedValue({ data: [{ id: 'selected' }], page: { total: 300 } });
    let selector!: ReturnType<typeof useStandardSelectorPage<{ id: string }>>;
    const selected = ref('selected');
    const wrapper = mount(
      defineComponent({
        setup() {
          selector = useStandardSelectorPage(
            'inspection',
            ref(true),
            ref('device'),
            ref(false),
            selected,
            vi.fn(),
          );
          return () => h('div');
        },
      }),
    );
    selector.page.value = 3;
    await flushPromises();
    selector.size.value = 100;
    await nextTick();
    await flushPromises();
    expect(selector.page.value).toBe(1);
    expect(request).toHaveBeenLastCalledWith(
      '/inspection/selectors/standards?q=&page=1&size=100&equipmentId=device',
    );
    expect(selected.value).toBe('selected');
    wrapper.unmount();
  });
});
