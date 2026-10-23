import { afterEach, describe, expect, it, vi } from 'vitest';
import { mount, flushPromises } from '@vue/test-utils';
import { reactive } from 'vue';
import InspectionStandardEditor from '../components/inspection/InspectionStandardEditor.vue';
import AppFormField from '../components/common/AppFormField.vue';
import { emptyInspection } from '../inspection';
import { emptyItem } from '../maintenance';
import { ApiError } from '../api';

const wrappers: ReturnType<typeof mount>[] = [];
afterEach(() => wrappers.splice(0).forEach((wrapper) => wrapper.unmount()));
function editor() {
  HTMLElement.prototype.scrollIntoView = vi.fn();
  const model = reactive(emptyInspection());
  const wrapper = mount(InspectionStandardEditor, {
    props: {
      modelValue: model,
      types: [],
      choices: { equipment: [], organizations: [], users: [], categories: [] },
      departments: [],
      categories: [],
    },
    global: {
      stubs: {
        PagedChoiceField: true,
        TreeSelect: true,
        MaintenanceAttachments: true,
        AppDialog: true,
      },
    },
  });
  wrappers.push(wrapper);
  const field = (path: string) =>
    wrapper.findAllComponents(AppFormField).find((item) => item.props('prop') === path)!;
  return { model, wrapper, field };
}

describe('inspection standard Element Plus validation', () => {
  it('shows only the missing fields at their controls and clears a corrected name', async () => {
    const { wrapper, field } = editor();
    expect(await wrapper.vm.validate()).toBe(false);
    await vi.waitFor(() =>
      expect(field('name').find('.el-form-item__error').text()).toBe('请填写名称'),
    );
    expect(field('departmentId').classes()).toContain('is-error');
    expect(field('items').classes()).toContain('is-error');
    expect(field('code').classes()).not.toContain('is-error');
    expect(field('requirement').classes()).not.toContain('is-error');
    expect(wrapper.find('.inspection-error').exists()).toBe(false);
    await field('name').find('input').setValue('检查标准');
    await flushPromises();
    await vi.waitFor(() => expect(field('name').classes()).not.toContain('is-error'));
    expect(field('departmentId').classes()).toContain('is-error');
  }, 15000);

  it('locates row errors and stops requiring options after switching to a text result', async () => {
    const { model, wrapper, field } = editor();
    model.name = '检查标准';
    model.departmentId = 'department';
    model.items = [emptyItem('type')];
    await flushPromises();
    expect(await wrapper.vm.validate()).toBe(false);
    await vi.waitFor(() =>
      expect(
        field('items.0.content').find('.el-form-item__content .el-form-item__error').text(),
      ).toBe('请填写方法及基准'),
    );
    expect(field('items.0.name').classes()).toContain('is-error');
    expect(field('items.0.options').classes()).toContain('is-error');
    expect(field('items.0.normalOptions').classes()).toContain('is-error');
    model.items[0]!.name = '检查项目';
    model.items[0]!.content = '检查外观';
    model.items[0]!.valueType = '文本';
    await flushPromises();
    expect(await wrapper.vm.validate()).toBe(true);
    expect(field('items.0.options').classes()).not.toContain('is-error');
    expect(model.items[0]!.content).toBe('检查外观');
  }, 15000);

  it('maps server field errors and clears only the field the user corrects', async () => {
    const { wrapper, field } = editor();
    expect(
      await wrapper.vm.capture(
        new ApiError('invalid', 400, { name: '名称重复', departmentId: '部门已失效' }),
      ),
    ).toBe(true);
    await vi.waitFor(() =>
      expect(field('name').find('.el-form-item__error').text()).toBe('名称重复'),
    );
    await field('name').find('input').setValue('新名称');
    await flushPromises();
    await vi.waitFor(() => expect(field('name').classes()).not.toContain('is-error'));
    expect(field('departmentId').classes()).toContain('is-error');
    expect(await wrapper.vm.capture(new Error('network'))).toBe(false);
  }, 15000);
});
