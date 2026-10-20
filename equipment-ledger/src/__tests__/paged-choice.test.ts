import { describe, it, expect, vi } from 'vitest';
import { defineComponent, nextTick } from 'vue';
import { mount, flushPromises } from '@vue/test-utils';
import DialogChoiceInput from '../components/common/DialogChoiceInput.vue';
import PagedChoiceField from '../components/common/PagedChoiceField.vue';
import AppBusinessTable from '../components/common/AppBusinessTable.vue';
import AppPagination from '../components/common/AppPagination.vue';
import ListSearchInput from '../components/common/ListSearchInput.vue';
import type { SelectorPage, SelectorRow } from '../selectors';
const dialog = defineComponent({
  props: ['modelValue'],
  template: '<section v-if="modelValue"><slot/><slot name="footer"/></section>',
});
const table = defineComponent({
  name: 'AppBusinessTable',
  props: ['rows', 'selectionIds'],
  emits: ['update:selectionIds'],
  template: '<div class="candidate-rows">{{JSON.stringify(rows)}}</div>',
});
function picker(
  loadRows: (q: string, page: number, size: number) => Promise<SelectorPage<SelectorRow>>,
) {
  return mount(PagedChoiceField, {
    props: {
      modelValue: ['77'],
      options: [{ id: '77', name: '已有人员' }],
      multiple: true,
      loadRows,
    },
    global: {
      stubs: {
        AppDialog: dialog,
        AppBusinessTable: table,
        AppPagination: true,
        ElTableColumn: true,
        AppIcon: true,
      },
    },
  });
}
async function button(wrapper: ReturnType<typeof picker>, text: string) {
  const found = wrapper.findAll('button').find((b) => b.text().includes(text));
  expect(found).toBeTruthy();
  await found!.trigger('click');
  await flushPromises();
}
describe('paged selector draft and requests', () => {
  it('keeps selected IDs across pages and searches, cancel does not apply, confirmation emits selected rows once', async () => {
    const load = vi.fn(async (q: string, page: number) => ({
      data: [{ id: String(page), name: q || '人员' + page }],
      page: { total: 10005 },
    }));
    const wrapper = picker(load);
    wrapper.findComponent(DialogChoiceInput).vm.$emit('open');
    await nextTick();
    await flushPromises();
    expect(load).toHaveBeenCalledTimes(1);
    expect(load).toHaveBeenLastCalledWith('', 1, 20);
    wrapper
      .findComponent({ name: 'AppBusinessTable' })
      .vm.$emit('update:selectionIds', ['77', '1']);
    await nextTick();
    wrapper.findComponent(AppPagination).vm.$emit('update:page', 2);
    await flushPromises();
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('selectionIds')).toEqual([
      '77',
      '1',
    ]);
    wrapper
      .findComponent({ name: 'AppBusinessTable' })
      .vm.$emit('update:selectionIds', ['77', '1', '2']);
    await nextTick();
    wrapper.findComponent(ListSearchInput).vm.$emit('update:modelValue', '搜索');
    await nextTick();
    await wrapper.find('form').trigger('submit');
    await flushPromises();
    expect(load).toHaveBeenLastCalledWith('搜索', 1, 20);
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('selectionIds')).toEqual([
      '77',
      '1',
      '2',
    ]);
    await button(wrapper, '取消');
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    wrapper.findComponent(DialogChoiceInput).vm.$emit('open');
    await nextTick();
    await flushPromises();
    expect(wrapper.findComponent({ name: 'AppBusinessTable' }).props('selectionIds')).toEqual([
      '77',
    ]);
    wrapper
      .findComponent({ name: 'AppBusinessTable' })
      .vm.$emit('update:selectionIds', ['77', '1']);
    await nextTick();
    await button(wrapper, '确认选择');
    expect(wrapper.emitted('update:modelValue')).toEqual([[['77', '1']]]);
    expect(wrapper.emitted('select')?.[0]?.[0]).toEqual([
      { id: '77', name: '已有人员' },
      { id: '1', name: '人员1' },
    ]);
    wrapper.unmount();
  });
  it('ignores slow old pages and never applies a closed request', async () => {
    let first!: (value: SelectorPage<SelectorRow>) => void;
    const load = vi
      .fn()
      .mockImplementationOnce(
        () =>
          new Promise((resolve) => {
            first = resolve;
          }),
      )
      .mockResolvedValue({ data: [{ id: '2', name: '新页' }], page: { total: 40 } });
    const wrapper = picker(load);
    wrapper.findComponent(DialogChoiceInput).vm.$emit('open');
    await nextTick();
    await nextTick();
    wrapper.findComponent(AppPagination).vm.$emit('update:page', 2);
    await flushPromises();
    expect(wrapper.find('.candidate-rows').text()).toContain('新页');
    first({ data: [{ id: '1', name: '旧页' }], page: { total: 1 } });
    await flushPromises();
    expect(wrapper.find('.candidate-rows').text()).not.toContain('旧页');
    await button(wrapper, '取消');
    expect(wrapper.emitted('update:modelValue')).toBeUndefined();
    wrapper.unmount();
  });
});
