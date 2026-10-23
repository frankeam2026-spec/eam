import { afterEach, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { createMemoryHistory, createRouter } from 'vue-router';
import DeviceInspectionDialog from '../components/inspection/DeviceInspectionDialog.vue';
import PagedChoiceField from '../components/common/PagedChoiceField.vue';
import { inspectionApi } from '../inspection';
import { ElInput, ElButton } from 'element-plus';
let wrapper: ReturnType<typeof mount> | undefined;
afterEach(() => wrapper?.unmount());
it('queries the selected equipment when the selector emits select before the model update', async () => {
  const requests = vi
    .spyOn(inspectionApi, 'deviceTasks')
    .mockResolvedValue({ data: [], page: { total: 0 } });
  const actions = vi.spyOn(inspectionApi, 'action');
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [{ path: '/', component: { template: '<div/>' } }],
  });
  wrapper = mount(DeviceInspectionDialog, {
    props: { modelValue: false },
    global: {
      plugins: [router],
      stubs: {
        PagedChoiceField: { props: ['modelValue'], template: '<div/>' },
        AppDialog: {
          props: ['modelValue'],
          template: '<section v-if="modelValue"><slot/></section>',
        },
        AppBusinessTable: true,
        AppPagination: true,
      },
    },
  });
  await wrapper.setProps({ modelValue: true });
  const selector = wrapper.findComponent(PagedChoiceField);
  selector.vm.$emit('select', [{ id: '9012', name: '数控车床' }]);
  selector.vm.$emit('update:modelValue', '9012');
  await flushPromises();
  expect(requests).toHaveBeenLastCalledWith('9012', '', 1, 20);
  expect(wrapper.text()).not.toContain('请扫描设备编号或选择设备');
  wrapper.findComponent(ElInput).vm.$emit('update:modelValue', 'EQ-2026-0001');
  await flushPromises();
  await wrapper
    .findAllComponents(ElButton)
    .find((b) => b.text() === '查询点检任务')!
    .trigger('click');
  await flushPromises();
  expect(requests).toHaveBeenLastCalledWith('', 'EQ-2026-0001', 1, 20);
  expect(actions).not.toHaveBeenCalled();
});
