import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { ElButton, ElMessage } from 'element-plus';
import InspectionTaskDeleteDialog from '../components/inspection/InspectionTaskDeleteDialog.vue';
import { inspectionApi, type InspectionTask } from '../inspection';

const wrappers: ReturnType<typeof mount>[] = [];
afterEach(() => {
  wrappers.splice(0).forEach((w) => w.unmount());
  vi.restoreAllMocks();
});
function dialog() {
  const wrapper = mount(InspectionTaskDeleteDialog, {
    props: {
      modelValue: true,
      task: {
        id: '42',
        code: 'DJ-42',
        planName: '白班检查',
        status: '待执行',
        rowVersion: 7,
      } as InspectionTask,
    },
    global: {
      stubs: { AppDialog: { template: '<section><slot /><slot name="footer" /></section>' } },
    },
  });
  wrappers.push(wrapper);
  const remove = () =>
    wrapper
      .findAllComponents(ElButton)
      .find((b) => b.text() === '删除')!
      .trigger('click');
  return { wrapper, remove };
}
describe('inspection task deletion', () => {
  it('validates the reason beside the field and cancellation makes no request', async () => {
    const request = vi.spyOn(inspectionApi, 'action');
    const { wrapper, remove } = dialog();
    await remove();
    await flushPromises();
    await vi.waitFor(() => expect(wrapper.find('.el-form-item__error').text()).toBe('请填写删除原因'));
    expect(request).not.toHaveBeenCalled();
    await wrapper
      .findAllComponents(ElButton)
      .find((b) => b.text() === '取消')!
      .trigger('click');
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]]);
    expect(request).not.toHaveBeenCalled();
  });
  it('submits the task version and reason once, then asks the caller to refresh', async () => {
    const request = vi
      .spyOn(inspectionApi, 'action')
      .mockResolvedValue({ data: {} as InspectionTask });
    vi.spyOn(ElMessage, 'success').mockImplementation(() => ({ close: () => {} }));
    const { wrapper, remove } = dialog();
    await wrapper.find('textarea').setValue('  计划调整  ');
    await remove();
    await flushPromises();
    expect(request).toHaveBeenCalledExactlyOnceWith('42', 'delete', {
      rowVersion: 7,
      description: '计划调整',
    });
    expect(wrapper.emitted('deleted')).toEqual([['42']]);
    expect(wrapper.emitted('update:modelValue')).toEqual([[false]]);
  });
  it('keeps the dialog open and reports stale or newly started task failures', async () => {
    vi.spyOn(inspectionApi, 'action').mockRejectedValue(new Error('任务已被其他人修改，请刷新'));
    const { wrapper, remove } = dialog();
    await wrapper.find('textarea').setValue('计划调整');
    await remove();
    await flushPromises();
    expect(wrapper.text()).toContain('任务已被其他人修改，请刷新');
    expect(wrapper.emitted('deleted')).toBeUndefined();
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
  });
});
