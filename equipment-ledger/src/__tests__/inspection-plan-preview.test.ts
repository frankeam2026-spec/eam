import { afterEach, describe, expect, it, vi } from 'vitest';
import { flushPromises, mount } from '@vue/test-utils';
import InspectionPlanPreviewDialog from '../components/inspection/InspectionPlanPreviewDialog.vue';
import { emptyInspection, inspectionApi } from '../inspection';
vi.mock('../stores/auth', () => ({ useAuthStore: () => ({ can: () => false }) }));
vi.mock('vue-router', () => ({ useRouter: () => ({ push: vi.fn() }) }));
afterEach(() => vi.restoreAllMocks());

describe('task to plan read-only navigation', () => {
  it('loads the actual referenced plan without requiring edit permission', async () => {
    const request = vi
      .spyOn(inspectionApi, 'get')
      .mockResolvedValue({ data: { ...emptyInspection(), name: '实时计划名称', code: 'DP-17' } });
    const wrapper = mount(InspectionPlanPreviewDialog, {
      props: { modelValue: false, planId: '17', kind: 'daily-plans' },
      global: {
        stubs: {
          AppDialog: { template: '<section><slot/><slot name="footer"/></section>' },
          AppBusinessTable: true,
        },
      },
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();
    expect(request).toHaveBeenCalledExactlyOnceWith('daily-plans', '17');
    expect(wrapper.text()).toContain('实时计划名称');
    expect(wrapper.text()).toContain('DP-17');
    expect(wrapper.text()).not.toContain('编辑');
    wrapper.unmount();
  });
  it('shows an access failure without rendering the previous plan', async () => {
    vi.spyOn(inspectionApi, 'get').mockRejectedValue(new Error('无权查看此计划'));
    const wrapper = mount(InspectionPlanPreviewDialog, {
      props: { modelValue: false, planId: '17', kind: 'daily-plans' },
      global: {
        stubs: { AppDialog: { template: '<section><slot/><slot name="footer"/></section>' } },
      },
    });
    await wrapper.setProps({ modelValue: true });
    await flushPromises();
    expect(wrapper.text()).toContain('无权查看此计划');
    expect(wrapper.text()).not.toContain('DP-17');
    wrapper.unmount();
  });
});
