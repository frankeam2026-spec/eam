import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { defineComponent, computed } from 'vue';
import { createPinia, setActivePinia } from 'pinia';
import { createRouter, createMemoryHistory, matchedRouteKey } from 'vue-router';
import { shallowMount, flushPromises } from '@vue/test-utils';
import EquipmentLedgerPage from '../pages/EquipmentLedgerPage.vue';
import AppPagination from '../components/common/AppPagination.vue';
import ListSearchInput from '../components/common/ListSearchInput.vue';
import { useAuthStore } from '../stores/auth';
import { businessApi } from '../businessConfig';
import * as api from '../api';

const tableStub = defineComponent({
  name: 'AppBusinessTable',
  props: ['rows', 'loading', 'error', 'filtered'],
  template: '<section><slot v-if="!rows.length" name="empty" /></section>',
});
const row = { id: '1', code: 'EQ-1', name: 'Test equipment' } as api.EquipmentRecord;
const result = { data: [row], page: { number: 1, size: 20, total: 80 } };
const wrappers: ReturnType<typeof shallowMount>[] = [];
beforeEach(() => {
  localStorage.clear();
  vi.spyOn(businessApi, 'runtime').mockResolvedValue({ configuration: { fields: [] } } as never);
});
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.restoreAllMocks();
});

async function mountList() {
  const pinia = createPinia();
  setActivePinia(pinia);
  const auth = useAuthStore();
  auth.user = {
    id: 'u1',
    tenantId: 't1',
    name: 'Test',
    account: 'test',
    orgId: 'd1',
    company: 'Company',
    department: 'Department',
  };
  auth.authState = 'ready';
  auth.grantedActions = { equipment: ['查看'] };
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/equipment', name: 'equipment', component: EquipmentLedgerPage }],
  });
  await router.push('/equipment');
  await router.isReady();
  const wrapper = shallowMount(EquipmentLedgerPage, {
    global: {
      plugins: [pinia, router],
      provide: { [matchedRouteKey as symbol]: computed(() => router.currentRoute.value.matched[0]) },
      renderStubDefaultSlot: true,
      stubs: {
        AppBusinessTable: tableStub,
        ElTableColumn: { template: '<span />' },
        ElButton: { template: '<button><slot /></button>' },
      },
    },
  });
  wrappers.push(wrapper);
  await flushPromises();
  return wrapper;
}
describe('equipment ledger query feedback', () => {
  it('uses server pagination with 20 rows and distinguishes applied-query emptiness', async () => {
    const read = vi
      .spyOn(api, 'listEquipment')
      .mockResolvedValue({ data: [], page: { number: 1, size: 20, total: 0 } });
    const wrapper = await mountList();
    expect(wrapper.findComponent(AppPagination).props('pageSizes')).toEqual([20, 50, 100]);
    expect(read).toHaveBeenLastCalledWith(expect.objectContaining({ page: 1, size: 20 }));
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('filtered')).toBe(false);
    expect(wrapper.text()).not.toContain('没有找到匹配的设备');
    expect(wrapper.text()).toContain('当前授权范围内暂无设备');
    expect(wrapper.text()).not.toContain('点击“新增”建立第一条设备档案');
    wrapper.findComponent(ListSearchInput).vm.$emit('update:modelValue', 'missing-equipment');
    await wrapper.find('form.filters').trigger('submit');
    await flushPromises();
    expect(read).toHaveBeenLastCalledWith(
      expect.objectContaining({ keyword: 'missing-equipment', size: 20 }),
    );
    expect(wrapper.text()).toContain('没有找到匹配的设备');
    expect(wrapper.text()).toContain('清除查询条件');
  });
  it('keeps the failed page for explicit retry and does not show a stale result count', async () => {
    const read = vi.spyOn(api, 'listEquipment').mockResolvedValue(result);
    const wrapper = await mountList();
    read.mockRejectedValueOnce(new Error('Test connection failed'));
    wrapper.findComponent(AppPagination).vm.$emit('update:page', 2);
    await flushPromises();
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('error')).toBe('Test connection failed');
    expect(wrapper.find('.result-count').text()).toBe('设备列表加载失败');
    expect(read).toHaveBeenCalledTimes(2);
    await wrapper
      .findAll('button')
      .find((b) => b.text() === '重试')!
      .trigger('click');
    await flushPromises();
    expect(read).toHaveBeenLastCalledWith(expect.objectContaining({ page: 2, size: 20 }));
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('error')).toBe('');
    expect(wrapper.find('.result-count').text()).toContain('80');
  });
  it('ignores late failures from an older query without removing the current result', async () => {
    let rejectOld!: (reason: Error) => void;
    const old = new Promise<typeof result>((_resolve, reject) => {
      rejectOld = reject;
    });
    const read = vi
      .spyOn(api, 'listEquipment')
      .mockImplementationOnce(() => old)
      .mockResolvedValue(result);
    const wrapper = await mountList();
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('loading')).toBe(true);
    wrapper.findComponent(ListSearchInput).vm.$emit('update:modelValue', 'latest');
    await wrapper.find('form.filters').trigger('submit');
    await flushPromises();
    rejectOld(new Error('Stale failure'));
    await flushPromises();
    const table = wrapper.findComponent({ name: 'AppBusinessTable' });
    expect(table.props('rows')).toEqual([row]);
    expect(table.props('loading')).toBe(false);
    expect(table.props('error')).toBe('');
    expect(read).toHaveBeenLastCalledWith(expect.objectContaining({ keyword: 'latest' }));
  });
});
