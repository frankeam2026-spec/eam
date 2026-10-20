import { describe, it, expect, vi } from 'vitest';
import { defineComponent, ref, nextTick } from 'vue';
import { flushPromises, mount } from '@vue/test-utils';
import { ElForm, ElInput } from 'element-plus';
import AppFormField from '../components/common/AppFormField.vue';
describe('Element Plus field error contract', () => {
  it('shows one error inside the control content rather than above the page', async () => {
    vi.useFakeTimers();
    const wrapper = mount(
      defineComponent({
        components: { ElForm, ElInput, AppFormField },
        setup: () => ({ model: ref({ unitId: '' }), error: ref('') }),
        template:
          '<ElForm :model="model"><AppFormField label="单位" prop="unitId" :error="error"><ElInput v-model="model.unitId" /></AppFormField></ElForm>',
      }),
    );
    (wrapper.vm as unknown as { error: string }).error = '请选择单位';
    await nextTick();
    await flushPromises();
    await vi.advanceTimersByTimeAsync(150);
    await nextTick();
    expect(wrapper.findAll('.el-form-item__error')).toHaveLength(1);
    expect(wrapper.find('.el-form-item__content .el-form-item__error').text()).toBe('请选择单位');
    wrapper.unmount();
    vi.useRealTimers();
  });
});
